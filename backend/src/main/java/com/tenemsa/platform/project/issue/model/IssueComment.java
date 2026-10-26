package com.tenemsa.platform.project.issue.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "issue_comments",
        indexes = @Index(name = "idx_issue_comments_company_issue", columnList = "company_id, issue_id")
)
public class IssueComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "author_emp_id")
    private Integer authorEmpId;

    // Copied at write time so the comment still reads correctly if the author is later deleted.
    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public IssueComment() {}

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getAuthorEmpId() { return authorEmpId; }
    public void setAuthorEmpId(Integer authorEmpId) { this.authorEmpId = authorEmpId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
