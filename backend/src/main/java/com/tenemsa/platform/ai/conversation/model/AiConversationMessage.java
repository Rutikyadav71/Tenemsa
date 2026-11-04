package com.tenemsa.platform.ai.conversation.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One turn of a saved chat. {@code metadata} is a small JSON string (citations, facts used) shown with the answer. */
@Entity
@Table(name = "ai_conversation_messages", indexes = @Index(name = "idx_ai_conv_msg_conv", columnList = "conversation_id, id"))
public class AiConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 12)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long v) { this.conversationId = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String v) { this.metadata = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
