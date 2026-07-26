package com.tenemsa.platform.model;

/**
 * Full role hierarchy. Only two tiers are enforced by @PreAuthorize checks
 * as of Phase 1 — OWNER/SUPER_ADMIN (admin-equivalent) and everything else
 * (HR/MANAGER/EMPLOYEE/INTERN, employee-equivalent) — because no role-specific
 * business logic exists yet to tell HR apart from a Manager. The distinct
 * values exist now so Phase 3's dynamic permission layer has something real
 * to attach to, instead of introducing another breaking schema change later.
 */
public enum Role {
    SUPER_ADMIN, // platform-level, spans companies (reserved for future platform admin tooling)
    OWNER,       // company owner — full control within their own company
    HR,
    MANAGER,
    EMPLOYEE,
    INTERN
}
