package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/projects/{id}. The key is not editable. Status ARCHIVED makes the project read-only. */
public record ProjectUpdateRequest(
        @NotBlank(message = "Project name is required.") @Size(max = 150, message = "Project name is too long.")
        String name,
        @Size(max = 1000, message = "Description is too long (1000 characters at most).")
        String description,
        @Positive(message = "Lead must be a valid employee number.")
        Integer leadEmpId,
        ProjectStatus status
) {}
