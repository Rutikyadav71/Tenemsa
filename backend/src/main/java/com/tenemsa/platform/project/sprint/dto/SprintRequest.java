package com.tenemsa.platform.project.sprint.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body of POST /api/projects/{id}/sprints and PUT /api/sprints/{id}. Dates are optional until the sprint starts. */
public record SprintRequest(
        @NotBlank(message = "Sprint name is required.") @Size(max = 100, message = "Sprint name is too long.")
        String name,
        @Size(max = 500, message = "Goal is too long (500 characters at most).")
        String goal,
        LocalDate startDate,
        LocalDate endDate
) {
    @AssertTrue(message = "The end date cannot be before the start date.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
