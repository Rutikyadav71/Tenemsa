package com.tenemsa.platform.security.rbac;

import com.tenemsa.platform.model.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.tenemsa.platform.security.rbac.Permission.*;

/**
 * Role -> Permission set, Phase A of docs/rbac-design.md (static, in code,
 * no schema change). Deliberately the ONLY place this mapping lives —
 * JwtFilter reads it to build authorities, nothing else should hardcode
 * "if role == X" business logic anywhere in the app. When Phase B
 * (database-backed roles/permissions, for company-custom roles) happens,
 * this class's permissionsFor() method is the one call site that changes —
 * everywhere else (JwtFilter, every @PreAuthorize annotation) is unaffected,
 * which is the entire point of routing permission checks through here
 * instead of role checks directly.
 */
public final class RolePermissionRegistry {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        // SUPER_ADMIN: platform-level, reserved for future cross-company
        // tooling. Deliberately NOT granted every company-scoped permission
        // here — a platform admin acting on a specific company's data is a
        // different, not-yet-built concern (impersonation/support tooling),
        // not something that should fall out of "SUPER_ADMIN has everything"
        // by accident.
        MATRIX.put(Role.SUPER_ADMIN, EnumSet.noneOf(Permission.class));

        MATRIX.put(Role.OWNER, EnumSet.allOf(Permission.class));

        MATRIX.put(Role.ADMIN, EnumSet.of(
                USER_CREATE, USER_UPDATE, USER_FREEZE, USER_VIEW_ANY, // creation target restricted to Manager only — enforced by RoleAssignmentRegistry, not here
                DEPARTMENT_MANAGE, TEAM_MANAGE_ANY,
                COMPANY_MANAGE, // spec: "Manage company settings (except subscription)"
        AUDIT_VIEW,
                REPORT_VIEW_COMPANY,
                CHAT_USE, PROFILE_UPDATE_SELF, ATTENDANCE_MARK_SELF, LEAVE_APPLY_SELF, PAYROLL_VIEW_SELF
                // Deliberately NOT: user:delete, leave:approve:*, attendance:manage:any,
                // payroll:manage, document:upload — spec: "Admin is NOT primarily
                // responsible for HR activities."
        ));

        MATRIX.put(Role.HR, EnumSet.of(
                USER_CREATE, USER_UPDATE, USER_DELETE, USER_VIEW_ANY, // creation target restricted to Employee/Intern only — RoleAssignmentRegistry
                LEAVE_APPROVE_ANY, LEAVE_VIEW_ANY, LEAVE_APPLY_SELF,
                ATTENDANCE_MANAGE_ANY, ATTENDANCE_MARK_SELF,
                PAYROLL_MANAGE, PAYROLL_VIEW_SELF,
                DOCUMENT_UPLOAD,
                REPORT_VIEW_COMPANY,
                CHAT_USE, PROFILE_UPDATE_SELF
                // Deliberately NOT: subscription:manage, user:delete-of-Owner
                // (enforced separately — see AdminController's primaryOwner
                // check, unrelated to this permission layer), company:manage
        ));

        MATRIX.put(Role.MANAGER, EnumSet.of(
                TEAM_MANAGE_OWN, USER_VIEW_TEAM,
                LEAVE_APPROVE_TEAM, LEAVE_VIEW_TEAM, LEAVE_APPLY_SELF,
                ATTENDANCE_VIEW_TEAM, ATTENDANCE_MARK_SELF,
                REPORT_VIEW_TEAM,
                PAYROLL_VIEW_SELF,
                CHAT_USE, PROFILE_UPDATE_SELF
                // Deliberately NOT: user:create — spec: "Managers cannot create users"
        ));

        MATRIX.put(Role.EMPLOYEE, EnumSet.of(
                LEAVE_APPLY_SELF, ATTENDANCE_MARK_SELF, PAYROLL_VIEW_SELF,
                CHAT_USE, PROFILE_UPDATE_SELF
        ));

        MATRIX.put(Role.INTERN, EnumSet.of(
                LEAVE_APPLY_SELF, ATTENDANCE_MARK_SELF,
                CHAT_USE, PROFILE_UPDATE_SELF
                // Deliberately NOT PAYROLL_VIEW_SELF — spec: "No payroll" for Intern
        ));
    }

    private RolePermissionRegistry() {}

    public static Set<Permission> permissionsFor(Role role) {
        return MATRIX.getOrDefault(role, Set.of());
    }
}
