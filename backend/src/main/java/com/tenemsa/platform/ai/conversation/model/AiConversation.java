package com.tenemsa.platform.ai.conversation.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One saved AI chat. Private to its owner: every lookup filters by company AND user. */
@Entity
@Table(name = "ai_conversations", indexes = @Index(name = "idx_ai_conv_owner", columnList = "company_id, user_id, kind, updated_at"))
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
