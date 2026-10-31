package com.tenemsa.platform.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingsRequest(@NotNull(message = "Say whether you want email notifications.") Boolean emailEnabled) {}
