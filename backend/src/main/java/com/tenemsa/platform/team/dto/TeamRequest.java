package com.tenemsa.platform.team.dto;

import com.tenemsa.platform.team.model.TeamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Body of POST /api/teams and PUT /api/teams/{id}. The company is always taken from the login. */
public record TeamRequest(
        @NotBlank(message = "Team name is required.") @Size(max = 150, message = "Team name is too long.")
        String name,
        @Size(max = 255, message = "Description is too long (255 characters at most).")
        String description,
        @Size(max = 150, message = "Project name is too long.")
        String projectName,
        LocalDate deadline,
        TeamStatus status,
        @Size(max = 500, message = "A team can have at most 500 members.")
        List<@NotNull(message = "Member IDs cannot be empty.") @Positive(message = "Member IDs must be positive numbers.") Integer> memberIds,
        @Positive(message = "Team lead ID must be a positive number.")
        Integer teamLeadId
) {}
