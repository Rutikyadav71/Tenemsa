package com.tenemsa.platform.document.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Metadata of an uploaded file. The bytes live in StoredDocumentFile so listing never loads them. */
@Entity
@Table(name = "documents", indexes = @Index(name = "idx_documents_company", columnList = "company_id, created_at"))
public class StoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentCategory category = DocumentCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentVisibility visibility = DocumentVisibility.ALL_STAFF;

    /** Set only for PRIVATE documents: the employee it concerns. */
    @Column(name = "owner_emp_id")
    private Integer ownerEmpId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by_emp_id")
    private Integer uploadedByEmpId;

    /** The matching knowledge-base document when "use for AI answers" was chosen. */
    @Column(name = "kb_document_id")
    private Long kbDocumentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public DocumentCategory getCategory() { return category; }
    public void setCategory(DocumentCategory v) { this.category = v; }
    public DocumentVisibility getVisibility() { return visibility; }
    public void setVisibility(DocumentVisibility v) { this.visibility = v; }
    public Integer getOwnerEmpId() { return ownerEmpId; }
    public void setOwnerEmpId(Integer v) { this.ownerEmpId = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public Integer getUploadedByEmpId() { return uploadedByEmpId; }
    public void setUploadedByEmpId(Integer v) { this.uploadedByEmpId = v; }
    public Long getKbDocumentId() { return kbDocumentId; }
    public void setKbDocumentId(Long v) { this.kbDocumentId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
