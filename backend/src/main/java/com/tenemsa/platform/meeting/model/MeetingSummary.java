package com.tenemsa.platform.meeting.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An AI-generated summary of one meeting. The original notes are kept (source_text) so a reviewer can
 * compare the summary against what was actually written. Decisions, open questions and action items are
 * in their own tables (MeetingPoint, MeetingActionItem) so later features can query them.
 */
@Entity
@Table(name = "meeting_summaries", indexes = {
        @Index(name = "idx_meetings_company_created", columnList = "company_id, created_at")
})
public class MeetingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "created_by_emp_id")
    private Integer createdByEmpId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "meeting_date")
    private LocalDate meetingDate;

    /** Free text pointing at where the notes came from (a link, a recording name, a calendar event). */
    @Column(name = "source_ref", length = 300)
    private String sourceRef;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.DRAFT;

    /** Set when action items were turned into issues of this project. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(length = 80)
    private String model;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(name = "ai_request_log_id")
    private Long aiRequestLogId;

    @Column(name = "approved_by_emp_id")
    private Integer approvedByEmpId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Integer getCreatedByEmpId() { return createdByEmpId; }
    public void setCreatedByEmpId(Integer createdByEmpId) { this.createdByEmpId = createdByEmpId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public void setMeetingDate(LocalDate meetingDate) { this.meetingDate = meetingDate; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public MeetingStatus getStatus() { return status; }
    public void setStatus(MeetingStatus status) { this.status = status; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Long getAiRequestLogId() { return aiRequestLogId; }
    public void setAiRequestLogId(Long aiRequestLogId) { this.aiRequestLogId = aiRequestLogId; }
    public Integer getApprovedByEmpId() { return approvedByEmpId; }
    public void setApprovedByEmpId(Integer approvedByEmpId) { this.approvedByEmpId = approvedByEmpId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
