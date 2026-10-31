package com.tenemsa.platform.chat.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A file sent in chat. Metadata and bytes live in separate tables so message history never loads file contents. */
@Entity
@Table(name = "chat_attachments", indexes = @Index(name = "idx_chat_att_company", columnList = "company_id"))
public class ChatAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "uploader_tag", nullable = false, length = 10) private String uploaderTag;
    @Column(name = "uploader_id", nullable = false) private Integer uploaderId;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "content_type", nullable = false, length = 120) private String contentType;
    @Column(name = "size_bytes", nullable = false) private Long sizeBytes;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    /** Set once the attachment is sent in a message; an attachment can be sent only once. */
    @Column(name = "message_id") private Long messageId;

    public ChatAttachment() {}

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getUploaderTag() { return uploaderTag; } public void setUploaderTag(String v) { uploaderTag = v; }
    public Integer getUploaderId() { return uploaderId; } public void setUploaderId(Integer v) { uploaderId = v; }
    public String getFileName() { return fileName; } public void setFileName(String v) { fileName = v; }
    public String getContentType() { return contentType; } public void setContentType(String v) { contentType = v; }
    public Long getSizeBytes() { return sizeBytes; } public void setSizeBytes(Long v) { sizeBytes = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getMessageId() { return messageId; } public void setMessageId(Long v) { messageId = v; }
}
