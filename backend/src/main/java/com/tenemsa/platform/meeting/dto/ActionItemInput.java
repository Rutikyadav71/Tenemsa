package com.tenemsa.platform.meeting.dto;

import com.tenemsa.platform.project.issue.model.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One action item inside an edit request. id = null adds a new item; an existing item missing from the list is removed. */
public record ActionItemInput(
        Long id,
        @NotBlank(message = "Every action item needs a title.") @Size(max = 200, message = "Action item title is too long (200 characters at most).")
        String title,
        @Size(max = 2000, message = "Action item description is too long (2000 characters at most).")
        String description,
        @Size(max = 150, message = "Owner name is too long.")
        String ownerName,
        @Positive(message = "Owner must be a valid employee number.")
        Integer ownerEmpId,
        LocalDate dueDate,
        IssuePriority priority
) {}
