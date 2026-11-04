package com.tenemsa.platform.auth.dto;

import java.time.LocalDateTime;

/** One signed-in device. {@code device} is the browser's own description (User-Agent), best effort only. */
public record SessionResponse(Long id, String device, LocalDateTime signedInAt, LocalDateTime expiresAt, boolean current) {}
