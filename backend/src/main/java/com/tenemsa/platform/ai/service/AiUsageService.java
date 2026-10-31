package com.tenemsa.platform.ai.service;

import com.tenemsa.platform.ai.config.AiProperties;
import com.tenemsa.platform.ai.dto.AiStatusResponse;
import com.tenemsa.platform.ai.dto.AiUsageResponse;
import com.tenemsa.platform.ai.model.AiRequestLog;
import com.tenemsa.platform.ai.model.AiRequestStatus;
import com.tenemsa.platform.ai.provider.AiClient;
import com.tenemsa.platform.ai.repository.AiRequestLogRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only views over ai_request_logs: the caller's remaining quota, and company usage for administrators. */
@Service
public class AiUsageService {

    private final AiRequestLogRepository logs;
    private final UserRepository users;
    private final AuthContext authContext;
    private final AiProperties props;
    private final AiClient client;
    private final com.tenemsa.platform.company.repository.CompanyRepository companies;

    public AiUsageService(AiRequestLogRepository logs, UserRepository users, AuthContext authContext,
                          AiProperties props, AiClient client,
                          com.tenemsa.platform.company.repository.CompanyRepository companies) {
        this.logs = logs;
        this.users = users;
        this.authContext = authContext;
        this.props = props;
        this.client = client;
        this.companies = companies;
    }

    public AiStatusResponse status() {
        Long companyId = authContext.currentCompanyId();
        Integer empId = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId).map(User::getEmpId).orElse(null);
        String refused = AiRequestStatus.RATE_LIMITED.name();
        long usedUser = empId == null ? 0 : logs.countByCompanyIdAndEmpIdAndCreatedAtAfterAndStatusNot(
                companyId, empId, LocalDateTime.now().minusHours(1), refused);
        long usedCompany = logs.countByCompanyIdAndCreatedAtAfterAndStatusNot(
                companyId, LocalDateTime.now().minusHours(24), refused);
        boolean companyEnabled = companies.findById(companyId).map(c -> c.isAiEnabled()).orElse(false);
        return new AiStatusResponse(client.isAvailable() && companyEnabled, client.providerName(), props.getModel(),
                (int) Math.max(0, props.getPerUserPerHour() - usedUser),
                (int) Math.max(0, props.getPerCompanyPerDay() - usedCompany), companyEnabled);
    }

    /** Rough cost from the configured price list; null when no prices are set. Token counts come from the provider. */
    private Double estimateCost(long inputTokens, long outputTokens) {
        if (props.getPriceInputPerMillionUsd() <= 0 && props.getPriceOutputPerMillionUsd() <= 0) return null;
        double cost = inputTokens / 1_000_000.0 * props.getPriceInputPerMillionUsd()
                + outputTokens / 1_000_000.0 * props.getPriceOutputPerMillionUsd();
        return Math.round(cost * 10000.0) / 10000.0;
    }

    public AiUsageResponse usage(int days) {
        int window = Math.min(Math.max(days, 1), 90);
        Long companyId = authContext.currentCompanyId();
        List<AiRequestLog> rows = logs.findSince(companyId, LocalDateTime.now().minusDays(window));

        long success = 0, failed = 0, limited = 0, in = 0, out = 0, latencySum = 0, latencyCount = 0;
        Map<String, long[]> byFeature = new TreeMap<>();   // [requests, in, out]
        Map<LocalDate, long[]> byDay = new TreeMap<>();    // [requests, failed]
        for (AiRequestLog r : rows) {
            boolean ok = AiRequestStatus.SUCCESS.name().equals(r.getStatus());
            boolean refused = AiRequestStatus.RATE_LIMITED.name().equals(r.getStatus());
            if (ok) success++; else if (refused) limited++; else failed++;
            in += r.getInputTokens() == null ? 0 : r.getInputTokens();
            out += r.getOutputTokens() == null ? 0 : r.getOutputTokens();
            if (!refused && r.getLatencyMs() != null) { latencySum += r.getLatencyMs(); latencyCount++; }
            long[] f = byFeature.computeIfAbsent(r.getFeature(), k -> new long[3]);
            f[0]++; f[1] += r.getInputTokens() == null ? 0 : r.getInputTokens(); f[2] += r.getOutputTokens() == null ? 0 : r.getOutputTokens();
            long[] d = byDay.computeIfAbsent(r.getCreatedAt().toLocalDate(), k -> new long[2]);
            d[0]++; if (!ok && !refused) d[1]++;
        }
        List<AiUsageResponse.FeatureUsage> features = byFeature.entrySet().stream()
                .map(e -> new AiUsageResponse.FeatureUsage(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        List<AiUsageResponse.DayUsage> daily = byDay.entrySet().stream()
                .map(e -> new AiUsageResponse.DayUsage(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();
        return new AiUsageResponse(window, rows.size(), success, failed, limited, in, out,
                latencyCount == 0 ? 0 : latencySum / latencyCount, features, daily, estimateCost(in, out));
    }
}
