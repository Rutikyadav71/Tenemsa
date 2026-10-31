package com.tenemsa.platform.meeting.dto;

import java.util.List;

public record CreateIssuesResponse(List<Created> created) {
    public record Created(Long actionItemId, Long issueId, String issueKey) {}
}
