package com.tenemsa.platform.connections.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Whether a company allows an app to be connected. Off until an owner or admin turns it on. */
@Entity
@Table(name = "company_apps", uniqueConstraints = @UniqueConstraint(name = "uq_company_apps", columnNames = {"company_id", "provider"}))
public class CompanyApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getProvider() { return provider; } public void setProvider(String v) { provider = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; updatedAt = LocalDateTime.now(); }
}
