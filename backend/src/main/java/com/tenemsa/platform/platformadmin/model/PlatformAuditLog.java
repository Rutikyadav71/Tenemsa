package com.tenemsa.platform.platformadmin.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Everything a platform administrator does, kept apart from the per-company audit trail. */
@Entity
@Table(name = "platform_audit_logs")
public class PlatformAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "admin_id") private Long adminId;
    @Column(name = "admin_email", length = 255) private String adminEmail;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "company_name", length = 150) private String companyName;
    @Column(length = 500) private String details;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; } public void setAdminId(Long v) { adminId = v; }
    public String getAdminEmail() { return adminEmail; } public void setAdminEmail(String v) { adminEmail = v; }
    public String getAction() { return action; } public void setAction(String v) { action = v; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getCompanyName() { return companyName; } public void setCompanyName(String v) { companyName = v; }
    public String getDetails() { return details; } public void setDetails(String v) { details = v == null || v.length() <= 500 ? v : v.substring(0, 500); }
    public String getIpAddress() { return ipAddress; } public void setIpAddress(String v) { ipAddress = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
