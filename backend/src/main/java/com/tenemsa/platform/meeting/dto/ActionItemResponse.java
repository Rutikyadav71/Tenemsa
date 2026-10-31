package com.tenemsa.platform.meeting.dto;

import com.tenemsa.platform.meeting.model.MeetingActionItem;
import com.tenemsa.platform.project.issue.model.IssuePriority;

import java.time.LocalDate;

public record ActionItemResponse(Long id, String title, String description, String ownerName, Integer ownerEmpId,
                                 LocalDate dueDate, IssuePriority priority, Long issueId, String issueKey) {
    public static ActionItemResponse from(MeetingActionItem a) {
        return new ActionItemResponse(a.getId(), a.getTitle(), a.getDescription(), a.getOwnerName(), a.getOwnerEmpId(),
                a.getDueDate(), a.getPriority(), a.getIssueId(), a.getIssueKey());
    }
}
