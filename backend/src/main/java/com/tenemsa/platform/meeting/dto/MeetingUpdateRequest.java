package com.tenemsa.platform.meeting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Body of PUT /api/meetings/{id}: the reviewer's corrected version of everything the AI produced. */
public record MeetingUpdateRequest(
        @NotBlank(message = "A title is required.") @Size(max = 200, message = "Title is too long (200 characters at most).")
        String title,
        LocalDate meetingDate,
        @NotBlank(message = "The summary cannot be empty.") @Size(max = 4000, message = "Summary is too long (4000 characters at most).")
        String summary,
        @Size(max = 50, message = "At most 50 decisions.")
        List<@NotBlank(message = "A decision cannot be empty.") @Size(max = 1000, message = "A decision is too long (1000 characters at most).") String> decisions,
        @Size(max = 50, message = "At most 50 open questions.")
        List<@NotBlank(message = "An open question cannot be empty.") @Size(max = 1000, message = "An open question is too long (1000 characters at most).") String> openQuestions,
        @Valid @Size(max = 50, message = "At most 50 action items.")
        List<ActionItemInput> actionItems
) {}
