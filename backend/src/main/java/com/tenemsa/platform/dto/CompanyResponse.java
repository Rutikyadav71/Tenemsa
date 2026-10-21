package com.tenemsa.platform.dto;

import java.time.LocalDateTime;

/** What the UI may know about a company. Never exposes other tenants' data. */
public record CompanyResponse(
        Long id,
        String name,
        String slug,
        LocalDateTime createdAt,
        long memberCount
) {}
