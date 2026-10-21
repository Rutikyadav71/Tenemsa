package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Invitation;

import java.time.LocalDateTime;

public record InvitationResponse(Long id, String email, String name, String role, String department,
                                 String status, LocalDateTime expiresAt, LocalDateTime createdAt) {
    public static InvitationResponse from(Invitation i) {
        return new InvitationResponse(i.getId(), i.getEmail(), i.getName(), i.getRole(), i.getDepartment(),
                i.getStatus(), i.getExpiresAt(), i.getCreatedAt());
    }
}
