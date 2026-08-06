package com.tenemsa.platform.model;

/**
 * Full role hierarchy:
 *
 *   SUPER_ADMIN (platform, future)
 *   OWNER  — company owner, full control, creates Admins + HR
 *     ├── ADMIN — technical/operational config, creates Managers, NOT payroll/HR-primary
 *     └── HR    — employee lifecycle, creates Employees, leave/attendance/payroll
 *           └── MANAGER — own-team scope only, cannot create users
 *                 ├── EMPLOYEE
 *                 └── INTERN — same as Employee minus payroll/confidential reports
 *
 * ADMIN added as its own value here (RBAC design phase 3a) — it was
 * previously conflated with OWNER, meaning "company owner" and "technical
 * admin" were indistinguishable. See docs/rbac-design.md for the full
 * design rationale. What each role can actually DO lives in
 * RolePermissionRegistry / RoleAssignmentRegistry, not here — this enum is
 * intentionally just the list of values.
 */
public enum Role {
    SUPER_ADMIN,
    OWNER,
    ADMIN,
    HR,
    MANAGER,
    EMPLOYEE,
    INTERN
}
