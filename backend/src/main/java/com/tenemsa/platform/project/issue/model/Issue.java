package com.tenemsa.platform.project.issue.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One unit of work (task, bug or story) on a project board. */
@Entity
@Table(
        name = "issues",
        uniqueConstraints = @UniqueConstraint(name = "uk_issues_project_number", columnNames = {"project_id", "issue_number"}),
        indexes = {
                @Index(name = "idx_issues_company_project", columnList = "company_id, project_id"),
                @Index(name = "idx_issues_company_assignee", columnList = "company_id, assignee_emp_id"),
                @Index(name = "idx_issues_company_sprint", columnList = "company_id, sprint_id")
        }
)
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // Position inside the project: the "12" in ACME-12.
    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueType type = IssueType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssuePriority priority = IssuePriority.MEDIUM;

    @Column(name = "assignee_emp_id")
    private Integer assigneeEmpId;

    @Column(name = "reporter_emp_id")
    private Integer reporterEmpId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // Null = in the backlog.
    @Column(name = "sprint_id")
    private Long sprintId;

    @Column(name = "story_points")
    private Integer storyPoints;

    // Comma-separated, already normalised by IssueService (lower case, at most 10).
    @Column(length = 500)
    private String labels;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Issue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public int getIssueNumber() { return issueNumber; }
    public void setIssueNumber(int issueNumber) { this.issueNumber = issueNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public IssueType getType() { return type; }
    public void setType(IssueType type) { this.type = type; }
    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }
    public IssuePriority getPriority() { return priority; }
    public void setPriority(IssuePriority priority) { this.priority = priority; }
    public Integer getAssigneeEmpId() { return assigneeEmpId; }
    public void setAssigneeEmpId(Integer assigneeEmpId) { this.assigneeEmpId = assigneeEmpId; }
    public Integer getReporterEmpId() { return reporterEmpId; }
    public void setReporterEmpId(Integer reporterEmpId) { this.reporterEmpId = reporterEmpId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getSprintId() { return sprintId; }
    public void setSprintId(Long sprintId) { this.sprintId = sprintId; }
    public Integer getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }
    public String getLabels() { return labels; }
    public void setLabels(String labels) { this.labels = labels; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
