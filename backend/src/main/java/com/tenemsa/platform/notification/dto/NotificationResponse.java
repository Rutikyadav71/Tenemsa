package com.tenemsa.platform.notification.dto;

import com.tenemsa.platform.notification.model.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String message, String type, Long leaveId, boolean read, LocalDateTime createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.getType(), n.getLeaveId(), n.isRead(), n.getCreatedAt());
    }
}
