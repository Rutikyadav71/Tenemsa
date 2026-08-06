package com.tenemsa.platform.security.rbac;

import com.tenemsa.platform.model.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * "Can create a user at all" (Permission.USER_CREATE) and "can create a
 * user of THIS specific role" are different questions — HR having
 * user:create doesn't mean HR can create another HR account or an Admin.
 * This is exactly the distinction your spec draws explicitly:
 *   Owner creates Admins + HR
 *   Admin creates Managers
 *   HR creates Employees (Interns bundled in — same onboarding lifecycle)
 *   Manager creates nobody
 *
 * A generic "has user:create" check can't express this on its own; it needs
 * this second, target-role-aware table. Both EmployeeController.create()
 * and AdminController.registerByAdmin() consult this before writing a row.
 */
public final class RoleAssignmentRegistry {

    private static final Map<Role, Set<Role>> ASSIGNABLE = new EnumMap<>(Role.class);

    static {
        ASSIGNABLE.put(Role.OWNER, EnumSet.of(Role.ADMIN, Role.HR, Role.MANAGER, Role.EMPLOYEE, Role.INTERN));
        ASSIGNABLE.put(Role.ADMIN, EnumSet.of(Role.MANAGER));
        ASSIGNABLE.put(Role.HR, EnumSet.of(Role.EMPLOYEE, Role.INTERN));
        ASSIGNABLE.put(Role.MANAGER, EnumSet.noneOf(Role.class)); // spec: cannot create users
        ASSIGNABLE.put(Role.EMPLOYEE, EnumSet.noneOf(Role.class));
        ASSIGNABLE.put(Role.INTERN, EnumSet.noneOf(Role.class));
        ASSIGNABLE.put(Role.SUPER_ADMIN, EnumSet.noneOf(Role.class)); // platform role, not a company account creator
    }

    private RoleAssignmentRegistry() {}

    public static boolean canAssign(Role creatorRole, Role targetRole) {
        return ASSIGNABLE.getOrDefault(creatorRole, Set.of()).contains(targetRole);
    }

    public static Set<Role> assignableRolesFor(Role creatorRole) {
        return ASSIGNABLE.getOrDefault(creatorRole, Set.of());
    }
}
