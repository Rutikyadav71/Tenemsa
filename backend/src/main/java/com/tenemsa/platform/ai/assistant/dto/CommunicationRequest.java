package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommunicationRequest(
        @NotNull(message = "Choose the kind of message.") CommunicationKind kind,
        @NotBlank(message = "Say who the message is for.") @Size(max = 200, message = "Audience is too long (200 characters at most).")
        String audience,
        @NotNull(message = "Choose a tone.") CommunicationTone tone,
        @NotBlank(message = "Write the key points.")
        @Size(min = 15, max = 3000, message = "Write the key points in 15 to 3000 characters.")
        String points
) {}
