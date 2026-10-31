package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The JSON the model must return for the task generator. Deliberately lenient; the service normalises every field. */
public record TaskAiOutput(@NotNull @Size(max = 40) List<AiTask> tasks) {
    public record AiTask(String title, String description, String type, String priority, Integer storyPoints,
                         List<String> labels) {}
}
