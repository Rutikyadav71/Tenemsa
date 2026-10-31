package com.tenemsa.platform.knowledge.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One uploaded document. The original file is not kept; its text is stored as chunks. */
@Entity
@Table(name = "kb_documents", indexes = {
        @Index(name = "idx_kb_documents_company", columnList = "company_id, created_at"),
        @Index(name = "idx_kb_documents_hash", columnList = "company_id, content_hash")
})
public class KbDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentAudience audience = DocumentAudience.ALL_STAFF;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PROCESSING;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "embedding_model", length = 80)
    private String embeddingModel;

    @Column(name = "uploaded_by_emp_id")
    private Integer uploadedByEmpId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }
    public DocumentAudience getAudience() { return audience; }
    public void setAudience(DocumentAudience v) { this.audience = v; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus v) { this.status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String v) { this.contentHash = v; }
    public int getCharCount() { return charCount; }
    public void setCharCount(int v) { this.charCount = v; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int v) { this.chunkCount = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
    public Integer getUploadedByEmpId() { return uploadedByEmpId; }
    public void setUploadedByEmpId(Integer v) { this.uploadedByEmpId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
