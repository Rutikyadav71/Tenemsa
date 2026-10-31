package com.tenemsa.platform.ai.dto;

import java.util.List;

public record AiUsageResponse(int days, long totalRequests, long successful, long failed, long rateLimited,
                              long inputTokens, long outputTokens, long averageLatencyMs,
                              List<FeatureUsage> byFeature, List<DayUsage> byDay,
                              Double estimatedCostUsd) {

    public record FeatureUsage(String feature, long requests, long inputTokens, long outputTokens) {}

    public record DayUsage(String date, long requests, long failed) {}
}
