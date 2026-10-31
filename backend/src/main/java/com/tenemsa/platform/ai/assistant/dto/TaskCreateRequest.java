package com.tenemsa.platform.ai.assistant.dto;

import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The tasks a person kept (and possibly edited) after reviewing the AI's suggestions. */
public record TaskCreateRequest(
        @NotNull(message = "Choose a project.") Long projectId,
        @NotEmpty(message = "Select at least one task.") @Size(max = 15, message = "At most 15 tasks at a time.")
        List<@Valid TaskDraft> tasks
) {
    public record TaskDraft(
            @NotBlank(message = "Every task needs a title.") @Size(max = 200, message = "Title is too long (200 characters at most).")
            String title,
            @Size(max = 10000, message = "Description is too long.") String description,
            IssueType type,
            IssuePriority priority,
            @Min(0) @Max(100) Integer storyPoints,
            @Size(max = 9) List<@Size(max = 30) String> labels
    ) {}
}
