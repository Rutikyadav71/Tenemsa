package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportAiOutput(@NotBlank @Size(max = 300) String headline, @NotBlank @Size(max = 3000) String summary,
                             @Size(max = 12) List<String> highlights, @Size(max = 12) List<String> risks,
                             @Size(max = 12) List<String> nextSteps) {}
