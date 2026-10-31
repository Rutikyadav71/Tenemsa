package com.tenemsa.platform.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The bytes of a chat attachment (up to the 10 MB upload limit). */
@Entity
@Table(name = "chat_attachment_files")
public class ChatAttachmentFile {
    @Id @Column(name = "attachment_id") private Long attachmentId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = 16_777_216)
    private byte[] content;

    public ChatAttachmentFile() {}
    public ChatAttachmentFile(Long attachmentId, byte[] content) { this.attachmentId = attachmentId; this.content = content; }

    public Long getAttachmentId() { return attachmentId; }
    public byte[] getContent() { return content; }
}
