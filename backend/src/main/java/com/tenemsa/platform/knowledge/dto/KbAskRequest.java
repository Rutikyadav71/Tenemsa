package com.tenemsa.platform.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KbAskRequest(
        @NotBlank(message = "Type your question.") @Size(min = 3, max = 500, message = "Questions can be 3 to 500 characters.") String question) {}
