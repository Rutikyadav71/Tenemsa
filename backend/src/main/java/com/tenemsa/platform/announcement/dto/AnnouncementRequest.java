package com.tenemsa.platform.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Body of POST /api/announcements and /preview. audience is ALL, ROLES, DEPARTMENTS or PEOPLE. */
public record AnnouncementRequest(
        @NotBlank(message = "Add a subject.") @Size(max = 200, message = "The subject is too long.") String subject,
        @NotBlank(message = "Write a message.") @Size(max = 5000, message = "The message is too long (5,000 characters at most).") String body,
        @NotNull(message = "Choose who should receive it.") String audience,
        List<String> roles,
        List<String> departments,
        List<Integer> empIds,
        boolean sendEmail
) {}
