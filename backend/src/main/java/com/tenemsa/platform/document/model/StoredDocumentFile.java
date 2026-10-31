package com.tenemsa.platform.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The file bytes (up to the 10 MB upload limit), one row per document. */
@Entity
@Table(name = "document_files")
public class StoredDocumentFile {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = 16_777_216)
    private byte[] content;

    public StoredDocumentFile() {}

    public StoredDocumentFile(Long documentId, byte[] content) {
        this.documentId = documentId;
        this.content = content;
    }

    public Long getDocumentId() { return documentId; }
    public byte[] getContent() { return content; }
}
