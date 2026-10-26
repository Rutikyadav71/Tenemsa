package com.tenemsa.platform.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/invitations/accept. name is optional (defaults to the invited name). */
public record AcceptInvitationRequest(
        @NotBlank(message = "Invitation token is required.") @Size(max = 200, message = "Invalid invitation token.")
        String token,
        @Size(max = 150, message = "Name is too long.")
        String name,
        @NotBlank(message = "Password is required.") @Size(max = 128, message = "Password is too long.")
        String password
) {}
