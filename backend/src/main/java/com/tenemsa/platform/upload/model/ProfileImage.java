package com.tenemsa.platform.upload.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A profile photo kept in the database, used when Cloudinary is not set up. One row per person. */
@Entity
@Table(name = "profile_images")
public class ProfileImage {
    /** Random, unguessable id used in the public picture address. Changes with every new upload. */
    @Id @Column(name = "token", length = 40) private String token;
    @Column(name = "company_id", nullable = false) private Long companyId;
    /** Who the photo belongs to, e.g. "emp:5" or "user:12". */
    @Column(name = "owner_key", nullable = false, length = 40) private String ownerKey;
    @Column(name = "content_type", nullable = false, length = 40) private String contentType;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, length = 4_194_304)
    private byte[] content;

    public ProfileImage() {}
    public ProfileImage(String token, Long companyId, String ownerKey, String contentType, byte[] content) {
        this.token = token; this.companyId = companyId; this.ownerKey = ownerKey; this.contentType = contentType; this.content = content;
    }
    public String getToken() { return token; }
    public Long getCompanyId() { return companyId; }
    public String getOwnerKey() { return ownerKey; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
