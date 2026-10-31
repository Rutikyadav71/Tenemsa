package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunicationAiOutput(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 6000) String body) {}
