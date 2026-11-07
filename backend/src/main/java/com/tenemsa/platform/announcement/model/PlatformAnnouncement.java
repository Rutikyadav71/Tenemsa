package com.tenemsa.platform.announcement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A notice from the Tenemsa system administrators to companies. Created only from the platform console. */
@Entity
@Table(name = "platform_announcements")
public class PlatformAnnouncement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    /** INFO, WARNING or CRITICAL. */
    @Column(nullable = false, length = 10) private String severity;
    /** ALL (every active company) or SELECTED (the ids in companyIds). */
    @Column(nullable = false, length = 10) private String audience;
    @Column(name = "company_ids", columnDefinition = "TEXT") private String companyIds;
    @Column(name = "send_email", nullable = false) private boolean sendEmail;
    @Column(name = "created_by", nullable = false, length = 255) private String createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean withdrawn;
    @Column(name = "companies_count", nullable = false) private int companiesCount;
    @Column(name = "emails_sent", nullable = false) private int emailsSent;

    public Long getId() { return id; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getMessage() { return message; } public void setMessage(String v) { message = v; }
    public String getSeverity() { return severity; } public void setSeverity(String v) { severity = v; }
    public String getAudience() { return audience; } public void setAudience(String v) { audience = v; }
    public String getCompanyIds() { return companyIds; } public void setCompanyIds(String v) { companyIds = v; }
    public boolean isSendEmail() { return sendEmail; } public void setSendEmail(boolean v) { sendEmail = v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public boolean isWithdrawn() { return withdrawn; } public void setWithdrawn(boolean v) { withdrawn = v; }
    public int getCompaniesCount() { return companiesCount; } public void setCompaniesCount(int v) { companiesCount = v; }
    public int getEmailsSent() { return emailsSent; } public void setEmailsSent(int v) { emailsSent = v; }
}
