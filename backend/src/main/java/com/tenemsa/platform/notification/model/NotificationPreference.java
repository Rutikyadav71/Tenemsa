package com.tenemsa.platform.notification.model;

import jakarta.persistence.*;

/** Per-person choice: also get notifications by email? In-app notifications are always on. */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "emp_id"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "emp_id", nullable = false)
    private Integer empId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Integer getEmpId() { return empId; }
    public void setEmpId(Integer v) { this.empId = v; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean v) { this.emailEnabled = v; }
}
