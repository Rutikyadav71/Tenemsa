package com.tenemsa.platform.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body of POST /api/meetings. The notes are the only thing sent to the AI model. */
public record MeetingCreateRequest(
        @NotBlank(message = "A title is required.") @Size(max = 200, message = "Title is too long (200 characters at most).")
        String title,
        LocalDate meetingDate,
        @Size(max = 300, message = "Source reference is too long (300 characters at most).")
        String sourceRef,
        @NotBlank(message = "Paste the meeting notes first.") @Size(min = 30, message = "The notes are too short to summarise (at least 30 characters).")
        String notes
) {}
