package com.tenemsa.platform.ai.assistant.dto;

import com.tenemsa.platform.project.issue.dto.IssueResponse;

import java.util.List;

public record TaskCreateResponse(List<IssueResponse> created) {}
