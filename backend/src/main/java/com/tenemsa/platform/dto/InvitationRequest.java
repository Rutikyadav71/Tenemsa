package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/invitations. */
public record InvitationRequest(
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email is too long.")
        String email,
        @NotBlank(message = "Name is required.") @Size(max = 150, message = "Name is too long.")
        String name,
        @NotNull(message = "Role is required.")
        Role role,
        @Size(max = 100, message = "Department is too long.")
        String department
) {}
