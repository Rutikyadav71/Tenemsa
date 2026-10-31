package com.tenemsa.platform.meeting.model;

import com.tenemsa.platform.project.issue.model.IssuePriority;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A task the AI found in the notes, as edited by a person. owner_name is the name as written in the notes;
 * owner_emp_id is filled when it matches exactly one employee of the company (or when the reviewer picks one).
 * Once converted, issue_id / issue_key point at the issue, and the item can never be converted twice.
 */
@Entity
@Table(name = "meeting_action_items", indexes = {
        @Index(name = "idx_meeting_actions_meeting", columnList = "company_id, meeting_id")
})
public class MeetingActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(name = "owner_emp_id")
    private Integer ownerEmpId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssuePriority priority = IssuePriority.MEDIUM;

    @Column(name = "sort_order", nullable = false)
    private int position;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "issue_key", length = 40)
    private String issueKey;

    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    public boolean isConverted() { return issueId != null; }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getMeetingId() { return meetingId; }
    public void setMeetingId(Long meetingId) { this.meetingId = meetingId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Integer getOwnerEmpId() { return ownerEmpId; }
    public void setOwnerEmpId(Integer ownerEmpId) { this.ownerEmpId = ownerEmpId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public IssuePriority getPriority() { return priority; }
    public void setPriority(IssuePriority priority) { this.priority = priority; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }
    public LocalDateTime getConvertedAt() { return convertedAt; }
    public void setConvertedAt(LocalDateTime convertedAt) { this.convertedAt = convertedAt; }
}
