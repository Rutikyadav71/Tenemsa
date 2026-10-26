package com.tenemsa.platform.project.issue.dto;

import com.tenemsa.platform.project.issue.model.IssueStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /api/issues/{id}/status (moving a card on the board). */
public record IssueStatusRequest(@NotNull(message = "Status is required.") IssueStatus status) {}
