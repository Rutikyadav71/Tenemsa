package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** days: size of the "recent activity" window for a status update (default 7). sprintId: for a retro (default: latest completed sprint). */
public record ReportRequest(
        @NotNull(message = "Choose the type of report.") ReportType type,
        Long sprintId,
        @Min(value = 1, message = "Days must be at least 1.") @Max(value = 30, message = "Days can be at most 30.") Integer days
) {}
