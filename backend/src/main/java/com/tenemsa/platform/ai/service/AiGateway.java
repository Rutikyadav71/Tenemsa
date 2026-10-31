package com.tenemsa.platform.ai.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tenemsa.platform.ai.config.AiProperties;
import com.tenemsa.platform.ai.exception.AiLimitExceededException;
import com.tenemsa.platform.ai.exception.AiOutputException;
import com.tenemsa.platform.ai.exception.AiProviderException;
import com.tenemsa.platform.ai.exception.AiTimeoutException;
import com.tenemsa.platform.ai.exception.AiUnavailableException;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.model.AiRequestLog;
import com.tenemsa.platform.ai.model.AiRequestStatus;
import com.tenemsa.platform.ai.provider.AiClient;
import com.tenemsa.platform.ai.repository.AiRequestLogRepository;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * The single door every AI feature goes through. In order it:
 *  1. checks AI is available,
 *  2. enforces the per-user (hourly) and per-company (daily) quotas,
 *  3. calls the provider on a bounded thread pool with a hard timeout and one retry on provider errors,
 *  4. parses the answer as JSON into the feature's record and validates it (Bean Validation),
 *     asking the model once to repair invalid output,
 *  5. writes an ai_request_logs row (latency, tokens, status, error code, never the prompt or answer).
 * Features supply a prompt and a target type and know nothing about providers, threads or quotas.
 * Do not call this from inside a database transaction: a model call can take many seconds.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    /** What a feature asks for. promptVersion is stored with the log so output quality can be traced to a prompt. */
    public record AiCall<T>(AiFeature feature, String promptVersion, String system, String user, Class<T> type) {}

    private final AiClient client;
    private final AiProperties props;
    private final AiRequestLogRepository logs;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final AuthContext authContext;
    private final Validator validator;
    private final ExecutorService executor;
    private final TransactionTemplate requiresNew;
    private final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public AiGateway(AiClient client, AiProperties props, AiRequestLogRepository logs, UserRepository users,
                     CompanyRepository companies, AuthContext authContext, Validator validator, ExecutorService aiExecutor,
                     PlatformTransactionManager txManager) {
        this.client = client;
        this.props = props;
        this.logs = logs;
        this.users = users;
        this.companies = companies;
        this.authContext = authContext;
        this.validator = validator;
        this.executor = aiExecutor;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isAvailable() { return client.isAvailable(); }

    public <T> AiResult<T> generate(AiCall<T> call) {
        if (!client.isAvailable()) {
            // The disabled client carries the helpful message.
            client.complete("", "", props.getModel(), 0);
        }
        Long companyId = authContext.currentCompanyId();
        if (!companies.findById(companyId).map(c -> c.isAiEnabled()).orElse(false)) {
            throw new AiUnavailableException("AI features are switched off for your company. An owner or admin can turn them on in Company settings.");
        }
        Integer empId = currentEmpId(companyId);

        enforceQuota(call, companyId, empId);

        long started = System.nanoTime();
        int attempts = 0;
        String user = call.user();
        Integer inTokens = null, outTokens = null;
        try {
            while (true) {
                attempts++;
                AiClient.AiCompletion completion = callWithTimeoutAndRetry(call.system(), user);
                inTokens = add(inTokens, completion.inputTokens());
                outTokens = add(outTokens, completion.outputTokens());
                String problem;
                try {
                    T parsed = parse(completion.text(), call.type());
                    Set<ConstraintViolation<T>> violations = validator.validate(parsed);
                    if (violations.isEmpty()) {
                        long ms = elapsedMs(started);
                        Long id = record(call, companyId, empId, AiRequestStatus.SUCCESS, ms, inTokens, outTokens, null);
                        return new AiResult<>(parsed, completion.model(), call.promptVersion(), inTokens, outTokens, ms, id);
                    }
                    problem = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .limit(5).collect(Collectors.joining("; "));
                } catch (AiOutputException e) {
                    problem = e.getMessage();
                }
                if (attempts > props.getMaxOutputRepairs()) {
                    throw new AiOutputException("The AI answer could not be understood. Please try again.");
                }
                user = call.user() + "\n\nYour previous reply was rejected: " + problem
                        + ". Reply again with ONLY one valid JSON object that follows the required format.";
            }
        } catch (AiOutputException e) {
            record(call, companyId, empId, AiRequestStatus.INVALID_OUTPUT, elapsedMs(started), inTokens, outTokens, "INVALID_OUTPUT");
            throw e;
        } catch (AiTimeoutException e) {
            record(call, companyId, empId, AiRequestStatus.TIMEOUT, elapsedMs(started), inTokens, outTokens, "TIMEOUT");
            throw e;
        } catch (AiProviderException e) {
            record(call, companyId, empId, AiRequestStatus.FAILED, elapsedMs(started), inTokens, outTokens,
                    e.getCause() == null ? "PROVIDER_ERROR" : e.getCause().getClass().getSimpleName());
            throw e;
        }
    }

    // ---- quotas --------------------------------------------------------------------------

    private <T> void enforceQuota(AiCall<T> call, Long companyId, Integer empId) {
        String refused = AiRequestStatus.RATE_LIMITED.name();
        long perUser = empId == null ? 0 : logs.countByCompanyIdAndEmpIdAndCreatedAtAfterAndStatusNot(
                companyId, empId, LocalDateTime.now().minusHours(1), refused);
        long perCompany = logs.countByCompanyIdAndCreatedAtAfterAndStatusNot(
                companyId, LocalDateTime.now().minusHours(24), refused);
        String message = null;
        if (perUser >= props.getPerUserPerHour()) {
            message = "You have reached the limit of " + props.getPerUserPerHour() + " AI requests per hour. Try again later.";
        } else if (perCompany >= props.getPerCompanyPerDay()) {
            message = "Your company has reached its daily limit of " + props.getPerCompanyPerDay() + " AI requests.";
        }
        if (message != null) {
            record(call, companyId, empId, AiRequestStatus.RATE_LIMITED, 0, null, null, "QUOTA");
            throw new AiLimitExceededException(message);
        }
    }

    // ---- provider call -------------------------------------------------------------------

    private AiClient.AiCompletion callWithTimeoutAndRetry(String system, String user) {
        AiProviderException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return callWithTimeout(system, user);
            } catch (AiProviderException e) {
                last = e;
                log.warn("AI provider call failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt == 1) sleepQuietly(400);
            }
        }
        throw new AiProviderException("The AI service could not be reached. Please try again in a moment.",
                last == null ? null : last.getCause());
    }

    private AiClient.AiCompletion callWithTimeout(String system, String user) {
        Future<AiClient.AiCompletion> future;
        try {
            future = executor.submit(() -> client.complete(system, user, props.getModel(), props.getTemperature()));
        } catch (RejectedExecutionException e) {
            throw new AiProviderException("The AI service is busy. Please try again in a moment.", e);
        }
        try {
            return future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiTimeoutException("The AI service took too long to answer. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AiProviderException("The AI request was interrupted.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AiUnavailableException u) throw u;
            if (cause instanceof AiProviderException p) throw p;
            throw new AiProviderException("The AI provider call failed.", cause);
        }
    }

    // ---- parsing -------------------------------------------------------------------------

    private <T> T parse(String text, Class<T> type) {
        if (text == null || text.isBlank()) throw new AiOutputException("empty answer");
        String json = text.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = json.indexOf('{'), end = json.lastIndexOf('}');
        if (start < 0 || end <= start) throw new AiOutputException("no JSON object found");
        try {
            return mapper.readValue(json.substring(start, end + 1), type);
        } catch (Exception e) {
            throw new AiOutputException("the JSON did not match the required format");
        }
    }

    // ---- logging -------------------------------------------------------------------------

    private <T> Long record(AiCall<T> call, Long companyId, Integer empId, AiRequestStatus status, long latencyMs,
                            Integer in, Integer out, String errorCode) {
        try {
            AiRequestLog row = new AiRequestLog();
            row.setCompanyId(companyId);
            row.setEmpId(empId);
            row.setFeature(call.feature().name());
            row.setPromptVersion(call.promptVersion());
            row.setProvider(client.providerName());
            row.setModel(props.getModel());
            row.setStatus(status.name());
            row.setLatencyMs(latencyMs);
            row.setInputTokens(in);
            row.setOutputTokens(out);
            row.setErrorCode(errorCode == null ? null : errorCode.substring(0, Math.min(80, errorCode.length())));
            return requiresNew.execute(s -> logs.save(row).getId());
        } catch (Exception e) {
            // Usage logging must never break the feature itself.
            log.warn("Could not write the AI usage log: {}", e.toString());
            return null;
        }
    }

    private Integer currentEmpId(Long companyId) {
        String email = authContext.currentEmail();
        if (email == null || companyId == null) return null;
        return users.findByEmailAndCompanyId(email, companyId).map(User::getEmpId).orElse(null);
    }

    private static Integer add(Integer a, Integer b) {
        if (a == null) return b;
        return b == null ? a : a + b;
    }

    private static long elapsedMs(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
