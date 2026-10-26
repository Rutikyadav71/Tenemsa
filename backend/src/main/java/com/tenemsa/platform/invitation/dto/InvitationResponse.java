package com.tenemsa.platform.invitation.dto;

import com.tenemsa.platform.invitation.model.Invitation;

import java.time.LocalDateTime;

public record InvitationResponse(Long id, String email, String name, String role, String department,
                                 String status, LocalDateTime expiresAt, LocalDateTime createdAt,
                                 Boolean emailSent) {
    /** For listings: whether the email went out is only known at send time, so it is null here. */
    public static InvitationResponse from(Invitation i) {
        return from(i, null);
    }

    public static InvitationResponse from(Invitation i, Boolean emailSent) {
        return new InvitationResponse(i.getId(), i.getEmail(), i.getName(), i.getRole(), i.getDepartment(),
                i.getStatus(), i.getExpiresAt(), i.getCreatedAt(), emailSent);
    }
}
