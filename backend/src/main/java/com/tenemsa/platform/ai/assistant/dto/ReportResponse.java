package com.tenemsa.platform.ai.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

/** basedOn: the numbers the system computed itself, shown so a reader can check the AI's wording against them. */
public record ReportResponse(ReportType type, String projectKey, String headline, String summary,
                             List<String> highlights, List<String> risks, List<String> nextSteps,
                             List<String> basedOn, String model, String promptVersion, LocalDateTime generatedAt) {}
