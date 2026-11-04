package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code conversationId} is optional: leave it out to start a new saved chat, send it to continue one. */
public record HrAskRequest(
        @NotBlank(message = "Type your question.") @Size(min = 3, max = 500, message = "Questions can be 3 to 500 characters.")
        String question,
        Long conversationId) {}
