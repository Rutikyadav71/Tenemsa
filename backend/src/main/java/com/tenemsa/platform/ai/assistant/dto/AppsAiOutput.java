package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AppsAiOutput(@NotBlank @Size(max = 3500) String answer, @Size(max = 6) List<String> sourcesUsed) {}
