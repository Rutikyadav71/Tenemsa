package com.tenemsa.platform.announcement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One message a company sent to some of its own people. Kept as history. */
@Entity
@Table(name = "announcements", indexes = @Index(name = "idx_announcements_company", columnList = "company_id, created_at"))
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "sent_by_emp_id") private Integer sentByEmpId;
    @Column(name = "sent_by_name", nullable = false, length = 150) private String sentByName;
    @Column(nullable = false, length = 200) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "audience_label", nullable = false, length = 250) private String audienceLabel;
    @Column(nullable = false) private int recipients;
    @Column(name = "send_email", nullable = false) private boolean sendEmail;
    @Column(name = "emails_sent", nullable = false) private int emailsSent;
    @Column(name = "emails_failed", nullable = false) private int emailsFailed;
    /** SENDING while emails go out in the background, then DONE. */
    @Column(nullable = false, length = 12) private String status = "SENDING";
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public Integer getSentByEmpId() { return sentByEmpId; } public void setSentByEmpId(Integer v) { sentByEmpId = v; }
    public String getSentByName() { return sentByName; } public void setSentByName(String v) { sentByName = v; }
    public String getSubject() { return subject; } public void setSubject(String v) { subject = v; }
    public String getBody() { return body; } public void setBody(String v) { body = v; }
    public String getAudienceLabel() { return audienceLabel; } public void setAudienceLabel(String v) { audienceLabel = v; }
    public int getRecipients() { return recipients; } public void setRecipients(int v) { recipients = v; }
    public boolean isSendEmail() { return sendEmail; } public void setSendEmail(boolean v) { sendEmail = v; }
    public int getEmailsSent() { return emailsSent; } public void setEmailsSent(int v) { emailsSent = v; }
    public int getEmailsFailed() { return emailsFailed; } public void setEmailsFailed(int v) { emailsFailed = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
