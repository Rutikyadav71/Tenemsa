package com.tenemsa.platform.project.issue.dto;

import com.tenemsa.platform.project.issue.model.IssueComment;

import java.time.LocalDateTime;

public record CommentResponse(Long id, Integer authorEmpId, String authorName, String body, LocalDateTime createdAt) {
    public static CommentResponse from(IssueComment c) {
        return new CommentResponse(c.getId(), c.getAuthorEmpId(), c.getAuthorName(), c.getBody(), c.getCreatedAt());
    }
}
