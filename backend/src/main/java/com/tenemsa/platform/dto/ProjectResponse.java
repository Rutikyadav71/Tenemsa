package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Project;
import com.tenemsa.platform.model.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectResponse(Long id, String key, String name, String description, ProjectStatus status,
                              Integer leadEmpId, String leadName, long totalIssues, long doneIssues,
                              LocalDateTime createdAt) {
    public static ProjectResponse from(Project p, String leadName, long total, long done) {
        return new ProjectResponse(p.getId(), p.getProjectKey(), p.getName(), p.getDescription(), p.getStatus(),
                p.getLeadEmpId(), leadName, total, done, p.getCreatedAt());
    }
}
