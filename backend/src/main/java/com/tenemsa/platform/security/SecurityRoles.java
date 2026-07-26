package com.tenemsa.platform.security;

/**
 * @PreAuthorize values must be compile-time constants, so these live here
 * instead of being repeated as literal strings across a dozen controllers.
 *
 * Today this only distinguishes two tiers — admin-equivalent (OWNER,
 * SUPER_ADMIN) vs. everyone else (HR, MANAGER, EMPLOYEE, INTERN) — because
 * that's the only distinction any existing endpoint actually needs. The full
 * six-role hierarchy already exists in the Role enum; Phase 3 replaces these
 * static checks with a dynamic permission lookup once there's real
 * role-specific business logic (e.g. an HR-only payroll endpoint) to justify
 * it. Introducing fine-grained checks before any endpoint needs them would
 * just be unused complexity.
 */
public final class SecurityRoles {

    private SecurityRoles() {}

    public static final String IS_ADMIN = "hasAnyRole('OWNER','SUPER_ADMIN')";
}
