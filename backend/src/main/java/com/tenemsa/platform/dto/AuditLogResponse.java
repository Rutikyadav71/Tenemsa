package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorName(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
    }
}
