package com.tenemsa.platform.security.rbac;

/**
 * Fine-grained capabilities, checked via @PreAuthorize("hasAuthority(...)")
 * instead of role names directly. See docs/rbac-design.md §5 for why:
 * short version, roles overlap in ways that don't map cleanly to
 * hasAnyRole() chains (Admin and HR both need report:view and nothing else
 * overlaps), and role-only checks can't support company-custom roles later
 * (§6 of the design doc) since a custom role isn't any of these Java enum
 * values at all.
 *
 * getKey() is the actual GrantedAuthority string — lowercase, colon-separated
 * ("leave:approve"), not the Java-convention enum name, so it reads the same
 * whether it came from this static registry (today) or a future
 * database-backed one (see RolePermissionRegistry's class comment).
 */
public enum Permission {
    COMPANY_MANAGE("company:manage"),
    // Phase 4c: read the company audit trail (Owner and Admin).
    AUDIT_VIEW("audit:view"),
    SUBSCRIPTION_MANAGE("subscription:manage"),

    // user:create is intentionally coarse-grained here — WHICH roles a
    // caller may assign is a separate question, answered by
    // RoleAssignmentRegistry, not by this permission existing or not.
    USER_CREATE("user:create"),
    USER_UPDATE("user:update"),
    USER_DELETE("user:delete"),
    USER_FREEZE("user:freeze"),
    // Added during controller wiring (3c) — missing from the initial
    // catalog. "Can create/edit employees" and "can see the employee list"
    // turned out to be genuinely separate capabilities once EmployeeController
    // was actually implemented: a Manager needs to see their own team's
    // members without being able to create or edit anyone.
    USER_VIEW_ANY("user:view:any"),
    USER_VIEW_TEAM("user:view:team"),

    DEPARTMENT_MANAGE("department:manage"),
    TEAM_MANAGE_ANY("team:manage:any"),
    TEAM_MANAGE_OWN("team:manage:own"),

    LEAVE_APPROVE_ANY("leave:approve:any"),
    LEAVE_APPROVE_TEAM("leave:approve:team"),
    LEAVE_APPLY_SELF("leave:apply:self"),
    LEAVE_VIEW_ANY("leave:view:any"),
    LEAVE_VIEW_TEAM("leave:view:team"),

    ATTENDANCE_MANAGE_ANY("attendance:manage:any"),
    ATTENDANCE_VIEW_TEAM("attendance:view:team"),
    ATTENDANCE_MARK_SELF("attendance:mark:self"),

    PAYROLL_MANAGE("payroll:manage"),
    PAYROLL_VIEW_SELF("payroll:view:self"),

    REPORT_VIEW_COMPANY("report:view:company"),
    REPORT_VIEW_TEAM("report:view:team"),

    DOCUMENT_UPLOAD("document:upload"),
    CHAT_USE("chat:use"),
    PROFILE_UPDATE_SELF("profile:update:self");

    private final String key;

    Permission(String key) { this.key = key; }

    public String getKey() { return key; }
}
