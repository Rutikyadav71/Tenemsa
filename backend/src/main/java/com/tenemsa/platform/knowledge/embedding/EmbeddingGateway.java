package com.tenemsa.platform.knowledge.embedding;

import com.tenemsa.platform.ai.exception.AiLimitExceededException;
import com.tenemsa.platform.ai.exception.AiProviderException;
import com.tenemsa.platform.ai.exception.AiUnavailableException;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.model.AiRequestLog;
import com.tenemsa.platform.ai.model.AiRequestStatus;
import com.tenemsa.platform.ai.repository.AiRequestLogRepository;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.knowledge.config.KbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The door for every embedding call, like AiGateway is for chat. It takes the company explicitly (indexing runs
 * on a background thread with no logged-in user), checks the company switch and the daily cap, sends texts in
 * batches with one retry, verifies the vectors, and writes one usage row per batch. Texts are never logged.
 */
@Service
public class EmbeddingGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGateway.class);

    private final EmbeddingClient client;
    private final KbProperties props;
    private final AiRequestLogRepository logs;
    private final CompanyRepository companies;
    private final TransactionTemplate requiresNew;

    public EmbeddingGateway(EmbeddingClient client, KbProperties props, AiRequestLogRepository logs,
                            CompanyRepository companies, PlatformTransactionManager txManager) {
        this.client = client;
        this.props = props;
        this.logs = logs;
        this.companies = companies;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isAvailable() { return client.isAvailable(); }
    public String modelName() { return client.modelName(); }

    /** Fails fast (503) when embeddings cannot run, so callers do not store work that can never finish. */
    public void requireReady(Long companyId) {
        if (!client.isAvailable()) client.embed(List.of("x"), EmbeddingClient.Task.QUERY); // throws the helpful message
        if (!companies.findById(companyId).map(c -> c.isAiEnabled()).orElse(false)) {
            throw new AiUnavailableException("AI features are switched off for your company. An owner or admin can turn them on in Company settings.");
        }
    }

    public List<float[]> embed(Long companyId, Integer empId, List<String> texts, EmbeddingClient.Task task) {
        requireReady(companyId);
        List<float[]> out = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, props.getEmbedBatchSize());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + batchSize));
            long used = logs.countEmbeddingsByCompany(companyId, LocalDateTime.now().minusHours(24));
            if (used >= props.getEmbedPerCompanyPerDay()) {
                record(companyId, empId, AiRequestStatus.RATE_LIMITED, 0, 0, "QUOTA");
                throw new AiLimitExceededException("Your company has reached its daily limit for indexing and searching documents.");
            }
            out.addAll(embedBatch(companyId, empId, batch, task));
        }
        return out;
    }

    private List<float[]> embedBatch(Long companyId, Integer empId, List<String> batch, EmbeddingClient.Task task) {
        long started = System.nanoTime();
        int tokens = batch.stream().mapToInt(t -> t.length() / 4 + 1).sum(); // rough: the API does not report usage
        AiProviderException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                List<float[]> vectors = client.embed(batch, task);
                if (vectors.size() != batch.size()) throw new AiProviderException("The embedding service returned the wrong number of vectors.", null);
                for (float[] v : vectors) {
                    if (v.length != client.dimensions()) {
                        throw new AiProviderException("The embedding vector has the wrong size. Check KB_EMBEDDING_DIMENSIONS.", null);
                    }
                }
                record(companyId, empId, AiRequestStatus.SUCCESS, elapsedMs(started), tokens, null);
                return vectors;
            } catch (AiProviderException e) {
                last = e;
                log.warn("Embedding batch failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt == 1) sleepQuietly(400);
            }
        }
        record(companyId, empId, AiRequestStatus.FAILED, elapsedMs(started), tokens, "PROVIDER_ERROR");
        throw new AiProviderException("The embedding service could not be reached. Please try again in a moment.",
                last == null ? null : last.getCause());
    }

    private void record(Long companyId, Integer empId, AiRequestStatus status, long ms, int tokens, String error) {
        try {
            AiRequestLog row = new AiRequestLog();
            row.setCompanyId(companyId);
            row.setEmpId(empId);
            row.setFeature(AiFeature.KB_EMBEDDING.name());
            row.setProvider(client.providerName());
            row.setModel(client.modelName());
            row.setStatus(status.name());
            row.setLatencyMs(ms);
            row.setInputTokens(tokens);
            row.setErrorCode(error);
            requiresNew.execute(s -> logs.save(row).getId());
        } catch (Exception e) {
            log.warn("Could not write the embedding usage log: {}", e.toString());
        }
    }

    private static long elapsedMs(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
