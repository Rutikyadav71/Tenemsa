package com.tenemsa.platform.security;

/**
 * @PreAuthorize values must be compile-time constants, so these live here
 * instead of being repeated as literal strings across a dozen controllers.
 *
 * RBAC design phase 3b: most checks that used to be role-based
 * (hasAnyRole('OWNER','SUPER_ADMIN')) are now permission-based
 * (hasAuthority('leave:approve:any')) — see docs/rbac-design.md §5 for why,
 * and com.tenemsa.platform.security.rbac.Permission for the actual
 * capability catalog. Permission strings are used directly in
 * @PreAuthorize annotations at each call site (they're already
 * self-describing constants via the enum), not re-wrapped here.
 *
 * What's left here are genuine role-IDENTITY checks — cases where the rule
 * really is "only an Owner may do this" rather than "whoever holds some
 * capability may do this" (e.g. only an Owner creates other Admins; that's
 * who they are, not a capability grantable to a hypothetical custom role
 * later). Mixing these into the Permission enum would misrepresent them as
 * capabilities when they're actually fixed hierarchy facts.
 */
public final class SecurityRoles {

    private SecurityRoles() {}

    /** Owner or platform Super Admin — the two roles with no ceiling on company-scoped actions. */
    public static final String IS_OWNER = "hasAnyRole('OWNER','SUPER_ADMIN')";

    /** Owner or Admin — the two roles with genuine administrative-tier access, distinct from HR/Manager's narrower scopes. */
    public static final String IS_OWNER_OR_ADMIN = "hasAnyRole('OWNER','ADMIN','SUPER_ADMIN')";
}
