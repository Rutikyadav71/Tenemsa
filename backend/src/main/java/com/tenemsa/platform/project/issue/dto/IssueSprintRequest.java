package com.tenemsa.platform.dto;

/** Body of PATCH /api/issues/{id}/sprint. A null sprintId sends the issue back to the backlog. */
public record IssueSprintRequest(Long sprintId) {}
