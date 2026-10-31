package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HrAiOutput(@NotBlank @Size(max = 3000) String answer, @Size(max = 12) List<String> factsUsed) {}
