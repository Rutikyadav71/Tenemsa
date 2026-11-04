package com.tenemsa.platform.ai.conversation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ConversationDtos {
    private ConversationDtos() {}

    public record Summary(Long id, String kind, String title, LocalDateTime updatedAt) {}

    public record Message(Long id, String role, String content, JsonNode details, LocalDateTime createdAt) {}

    public record Detail(Long id, String kind, String title, LocalDateTime updatedAt, List<Message> messages) {}

    public record RenameRequest(@NotBlank(message = "Give the chat a name.") @Size(max = 80, message = "Names can be up to 80 characters.") String title) {}
}
