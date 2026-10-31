package com.tenemsa.platform.meeting.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body of POST /api/meetings/{id}/create-issues. actionItemIds empty or missing = every item not yet converted. */
public record CreateIssuesRequest(
        @NotNull(message = "Choose the project the issues should be created in.") Long projectId,
        List<Long> actionItemIds
) {}
