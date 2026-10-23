package com.tenemsa.platform.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A body of work inside one company. Issues belong to a project and are numbered
 * per project (ACME-1, ACME-2, ...), the way Jira does it.
 */
@Entity
@Table(
        name = "projects",
        uniqueConstraints = @UniqueConstraint(name = "uk_projects_company_key", columnNames = {"company_id", "project_key"}),
        indexes = @Index(name = "idx_projects_company", columnList = "company_id")
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    // Short immutable prefix for issue keys, unique within the company.
    @Column(name = "project_key", nullable = false, length = 10)
    private String projectKey;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    // Business employee number (per company) of the person leading the project.
    @Column(name = "lead_emp_id")
    private Integer leadEmpId;

    // The number the NEXT issue gets. Only changed while the row is locked (see IssueService).
    @Column(name = "next_issue_number", nullable = false)
    private int nextIssueNumber = 1;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Project() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Integer getLeadEmpId() { return leadEmpId; }
    public void setLeadEmpId(Integer leadEmpId) { this.leadEmpId = leadEmpId; }
    public int getNextIssueNumber() { return nextIssueNumber; }
    public void setNextIssueNumber(int nextIssueNumber) { this.nextIssueNumber = nextIssueNumber; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
