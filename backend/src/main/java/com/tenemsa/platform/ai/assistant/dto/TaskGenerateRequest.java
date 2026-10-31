package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskGenerateRequest(
        @NotNull(message = "Choose a project.") Long projectId,
        @NotBlank(message = "Describe the goal.")
        @Size(min = 15, max = 4000, message = "Describe the goal in 15 to 4000 characters.")
        String goal,
        @Min(value = 1, message = "Ask for at least 1 task.") @Max(value = 15, message = "Ask for at most 15 tasks.")
        Integer maxTasks
) {}
