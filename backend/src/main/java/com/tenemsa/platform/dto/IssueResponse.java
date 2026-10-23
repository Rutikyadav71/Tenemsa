package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Issue;
import com.tenemsa.platform.model.IssuePriority;
import com.tenemsa.platform.model.IssueStatus;
import com.tenemsa.platform.model.IssueType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** "key" is the human-facing id, e.g. ACME-12. */
public record IssueResponse(Long id, String key, Long projectId, String projectKey, int issueNumber, String title,
                            String description, IssueType type, IssueStatus status, IssuePriority priority,
                            Integer assigneeEmpId, String assigneeName, Integer reporterEmpId, String reporterName,
                            LocalDate dueDate,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static IssueResponse from(Issue i, String projectKey, String assigneeName, String reporterName) {
        return new IssueResponse(i.getId(), projectKey + "-" + i.getIssueNumber(), i.getProjectId(), projectKey,
                i.getIssueNumber(), i.getTitle(), i.getDescription(), i.getType(), i.getStatus(), i.getPriority(),
                i.getAssigneeEmpId(), assigneeName, i.getReporterEmpId(), reporterName,
                i.getDueDate(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
