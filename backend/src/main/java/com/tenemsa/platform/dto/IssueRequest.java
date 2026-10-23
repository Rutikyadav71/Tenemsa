package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.IssuePriority;
import com.tenemsa.platform.model.IssueType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Body of POST /api/projects/{id}/issues and PUT /api/issues/{id}. Status is changed separately. */
public record IssueRequest(
        @NotBlank(message = "Title is required.") @Size(max = 200, message = "Title is too long (200 characters at most).")
        String title,
        @Size(max = 10000, message = "Description is too long (10000 characters at most).")
        String description,
        IssueType type,
        IssuePriority priority,
        @Positive(message = "Assignee must be a valid employee number.")
        Integer assigneeEmpId,
        LocalDate dueDate
) {}
