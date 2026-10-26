package com.tenemsa.platform.project.issue.dto;

import com.tenemsa.platform.project.issue.model.Issue;
import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueStatus;
import com.tenemsa.platform.project.issue.model.IssueType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** "key" is the human-facing id, e.g. ACME-12. */
public record IssueResponse(Long id, String key, Long projectId, String projectKey, int issueNumber, String title,
                            String description, IssueType type, IssueStatus status, IssuePriority priority,
                            Integer assigneeEmpId, String assigneeName, Integer reporterEmpId, String reporterName,
                            LocalDate dueDate, Long sprintId, String sprintName, Integer storyPoints, List<String> labels,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static IssueResponse from(Issue i, String projectKey, String assigneeName, String reporterName, String sprintName) {
        List<String> labelList = i.getLabels() == null || i.getLabels().isBlank()
                ? List.of() : Arrays.asList(i.getLabels().split(","));
        return new IssueResponse(i.getId(), projectKey + "-" + i.getIssueNumber(), i.getProjectId(), projectKey,
                i.getIssueNumber(), i.getTitle(), i.getDescription(), i.getType(), i.getStatus(), i.getPriority(),
                i.getAssigneeEmpId(), assigneeName, i.getReporterEmpId(), reporterName,
                i.getDueDate(), i.getSprintId(), sprintName, i.getStoryPoints(), labelList,
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
