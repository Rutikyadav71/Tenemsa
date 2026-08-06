# RBAC Architecture Design

**Status: design only, not implemented.** This document exists to be reviewed and approved before any code changes — per your explicit instruction, nothing here has been built yet.

## 0. A gap found before anything else

Your role hierarchy:
```
Owner → { Admin, HR → Manager → { Employee, Intern } }
```

The current `Role` enum (`backend/.../model/Role.java`):
```java
public enum Role {
    SUPER_ADMIN, OWNER, HR, MANAGER, EMPLOYEE, INTERN
}
```

**There's no `ADMIN` value.** `OWNER` currently carries all admin-equivalent power in every `@PreAuthorize` check (`SecurityRoles.IS_ADMIN` = `hasAnyRole('OWNER','SUPER_ADMIN')`), which conflates two things your spec explicitly separates: the Owner (subscription, full control, creates Admins/HR) and the Admin (technical/operational config, cannot touch subscription or delete the Owner). This needs a real enum value added — `ADMIN` between `OWNER` and `HR` — not a workaround layered on top of the current two. Covered in the migration plan below (§7).

---

## 1 & 2. Database structure — enum-based vs. database-driven, and why hybrid

Neither pure answer fits well on its own:

- **Pure enum** (what exists today) is fast, type-safe, and needs no extra queries — but it's fixed at compile time. You explicitly want custom company roles later ("Finance Manager", "Recruiter"), which a Java enum structurally cannot support.
- **Pure database-driven** (roles and permissions as rows, assigned per company) supports custom roles from day one — but it's real scope: new tables, a permission-management UI, a lookup on every request (or a caching layer to avoid one), and it solves a problem you don't have yet (no company has asked for a custom role; none can, since there's no UI for it).

**Recommendation: phased, with the phase-1 design built so phase-2 doesn't require touching controllers.**

### Phase A (this round) — enum roles + enum permissions + a static registry

```java
public enum Role { SUPER_ADMIN, OWNER, ADMIN, HR, MANAGER, EMPLOYEE, INTERN }

public enum Permission {
    USER_CREATE, USER_UPDATE, USER_DELETE, USER_FREEZE,
    DEPARTMENT_MANAGE, TEAM_MANAGE,
    LEAVE_APPROVE, LEAVE_VIEW_ALL, LEAVE_VIEW_TEAM,
    ATTENDANCE_MANAGE, ATTENDANCE_VIEW_ALL, ATTENDANCE_VIEW_TEAM,
    PAYROLL_MANAGE, PAYROLL_VIEW,
    COMPANY_MANAGE, SUBSCRIPTION_MANAGE,
    REPORT_VIEW, DOCUMENT_UPLOAD, CHAT_USE, PROFILE_UPDATE_SELF
    // full catalog in §4
}

public final class RolePermissionRegistry {
    private static final Map<Role, Set<Permission>> MATRIX = Map.of(
        Role.OWNER, EnumSet.allOf(Permission.class),
        Role.ADMIN, EnumSet.of(USER_CREATE /* managers only, see §3 */, DEPARTMENT_MANAGE,
                                TEAM_MANAGE, USER_FREEZE, REPORT_VIEW, ...),
        Role.HR,    EnumSet.of(USER_CREATE, USER_UPDATE, USER_DELETE, LEAVE_APPROVE,
                                ATTENDANCE_MANAGE, PAYROLL_MANAGE, DOCUMENT_UPLOAD, ...),
        Role.MANAGER, EnumSet.of(LEAVE_APPROVE /* team-scoped, see §3 */, LEAVE_VIEW_TEAM,
                                  ATTENDANCE_VIEW_TEAM, TEAM_MANAGE /* own team only */, ...),
        Role.EMPLOYEE, EnumSet.of(PROFILE_UPDATE_SELF, CHAT_USE, ...),
        Role.INTERN,   EnumSet.of(PROFILE_UPDATE_SELF, CHAT_USE, ...) // no PAYROLL_VIEW
    );
    public static Set<Permission> permissionsFor(Role role) { return MATRIX.get(role); }
}
```

No schema migration, no new tables, testable in isolation, and — this is the part that makes it scale — **controllers never reference `Role` or this matrix directly.** They check permission strings (`@PreAuthorize("hasAuthority('leave:approve')")`). Where those strings come from (a static Java map today, a database query in Phase B) is invisible to every controller. That's what makes Phase B additive later instead of a rewrite.

### Phase B (later, only once a real customer needs a custom role)

```sql
CREATE TABLE roles (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NULL REFERENCES companies(id), -- NULL = system role (Owner/Admin/HR/...), non-null = a company's custom role
    name VARCHAR(100) NOT NULL,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP
);

CREATE TABLE permissions (
    id BIGINT PRIMARY KEY,
    permission_key VARCHAR(100) UNIQUE NOT NULL, -- 'leave:approve'
    category VARCHAR(50),                         -- 'leave' — for grouping in an admin UI
    description TEXT
);

CREATE TABLE role_permissions (
    role_id BIGINT REFERENCES roles(id),
    permission_id BIGINT REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);
```

`User.role` (the enum column) is replaced by `User.role_id` (FK to `roles`). Migration seeds one `roles` row per enum value with `company_id = NULL, is_system_role = true`, so existing users map 1:1 with zero data loss. The permission-lookup call site (one method, not a scattered pattern) swaps from `RolePermissionRegistry.permissionsFor(role)` to a repository query — everything above that call site (JwtFilter, `@PreAuthorize` annotations, controllers) is unchanged.

---

## 3. How Spring Security should enforce this

Two genuinely separate concerns, both mandatory, neither a substitute for the other:

**a) "Can this role do X at all" — permission-based `@PreAuthorize`.**

`JwtFilter` currently builds one `GrantedAuthority` from the JWT's `role` claim (`ROLE_OWNER`, etc.). It needs to expand that into the full permission set at request time:

```java
Role role = Role.valueOf(jwtUtil.extractRole(token));
List<GrantedAuthority> authorities = RolePermissionRegistry.permissionsFor(role).stream()
    .map(p -> new SimpleGrantedAuthority(p.getKey())) // "leave:approve"
    .collect(toList());
authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())); // keep coarse role check available too
```

Controllers move from `@PreAuthorize(SecurityRoles.IS_ADMIN)` to `@PreAuthorize("hasAuthority('user:create')")`. `SecurityRoles` doesn't disappear — coarse "any admin-tier role" checks are still legitimate for a handful of things (freezing another admin, company-wide settings) — it just stops being the *only* tool.

**b) "Is X actually within this caller's scope" — object-level checks, same pattern as `EmployeeSecurity.isSelf()` already in the codebase.**

Permission checks alone don't express "Manager can approve leave, but only for their own team." `leave:approve` for a Manager is a *narrower* grant than `leave:approve` for HR — same permission string, different scope. This needs a resolver bean, same shape as the existing one:

```java
@Component
public class TeamSecurity {
    // Manager approving leave/attendance: is the target employee on a team this Manager leads?
    public boolean managesEmployee(int targetEmpId) { ... }
}
```

```java
@PreAuthorize("hasAuthority('leave:approve') and " +
              "(hasRole('HR') or hasRole('OWNER') or @teamSecurity.managesEmployee(#empId))")
```

This is the same architectural pattern your codebase already uses (`@employeeSecurity.isSelf(#empId)`), extended rather than replaced.

**c) Tenant isolation is a third, still-separate concern — and it's currently incomplete.**

Your spec says "every database query must always be restricted by companyId" and "this rule is mandatory across every module." Today, `User` is properly scoped (`company_id` + JOIN FETCH everywhere, per Phase 1–3 work). **`Attendance`, `Leave`, `Team`, `Notification`, and `ChatMessage` are not** — documented as a known gap since Phase 1 (`docs/architecture.md`), safe only because the app is still functionally single-tenant (one bootstrap "default" company, no real multi-company registration exists yet).

Given Hibernate 6.6 is already in use here, the industry-standard fix is Hibernate's native **`@TenantId`** (available since 6.4) rather than hand-adding `company_id` filters to every repository method one at a time:

```java
@Entity
public class Leave {
    @TenantId
    private Long companyId;
    // ...
}
```

```java
hibernate.tenant_identifier_resolver=com.tenemsa.platform.security.CurrentCompanyResolver
```

With this configured, Hibernate automatically appends `WHERE company_id = :currentTenant` to **every** query against that entity — including ones a future developer writes without remembering to scope it manually. This is meaningfully stronger than "we always remember to add `.findByCompanyId(...)`" — the discipline earlier phases established — because it makes forgetting *structurally impossible* rather than a code-review responsibility. This is Phase 4 work (it needs the entities getting `company_id` for the first time, which is already the headline Phase 4 item), but the RBAC layer above should be designed assuming it's coming, not designed around its absence.

---

## 4. API → permission mapping (initial catalog)

| Permission | Owner | Admin | HR | Manager | Employee | Intern |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `company:manage` | ✅ | | | | | |
| `subscription:manage` | ✅ | | | | | |
| `user:create` (Admin/HR/Manager tier) | ✅ | ✅ | | | | |
| `user:create` (Employee/Intern tier) | ✅ | ✅ | ✅ | | | |
| `user:update` | ✅ | ✅ | ✅ | | | |
| `user:delete` | ✅ | | ✅ | | | |
| `user:freeze` | ✅ | ✅ | | | | |
| `department:manage` | ✅ | ✅ | | | | |
| `team:manage` (any team) | ✅ | ✅ | | | | |
| `team:manage` (own team only) | | | | ✅ | | |
| `leave:approve` (any employee) | ✅ | | ✅ | | | |
| `leave:approve` (own team only) | | | | ✅ | | |
| `leave:apply` (self) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (limited policy) |
| `attendance:manage` (any employee) | ✅ | | ✅ | | | |
| `attendance:view` (own team) | | | | ✅ | | |
| `attendance:mark` (self) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `payroll:manage` | ✅ | | ✅ | | | |
| `payroll:view` (own) | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ *(spec: no payroll)* |
| `report:view` (company-wide) | ✅ | ✅ | ✅ | | | |
| `report:view` (team-only) | | | | ✅ | | |
| `document:upload` (employee docs) | ✅ | | ✅ | | | |
| `chat:use` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `profile:update` (self) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

Not exhaustive — this is the shape, refined against your actual controller list (`docs/api.md`) once this design is approved.

---

## 5. Permissions vs. `ROLE_ADMIN`/`ROLE_HR` only

Permissions, matching your suggestion — for reasons specific to what your spec actually asks for, not just "best practice" in the abstract:

- **Admin and HR both need `report:view` but almost nothing else overlaps.** Role-only checks force either duplicated `hasAnyRole('ADMIN','HR')` scattered everywhere they happen to overlap, or a role hierarchy hack. Permission strings just... overlap where they overlap.
- **"No payroll for Intern" is a permission absence, not a role fact.** `EnumSet.of(...)` either includes `PAYROLL_VIEW` or it doesn't — there's no separate `if (role == INTERN)` branch to keep in sync with the rest of the system as it grows.
- **Custom roles (§6) are structurally impossible with role-only checks** — a `hasRole('ADMIN')` check has no way to grant a future "Finance Manager" custom role access to `payroll:manage` without also grantng it everything else `ADMIN` has. Permission-based checks don't care what the role is called, only what's in its permission set.

---

## 6. Scaling to custom per-company roles

Covered mechanically in §1/§2 (Phase B schema). The parts worth calling out explicitly:

- **System roles are protected.** `is_system_role = true` rows (the 7 in your hierarchy) should never be editable or deletable via any company-facing API — only the permission set for a *custom* role is company-configurable.
- **A company can only grant permissions from the platform's fixed permission catalog** — the `permissions` table itself is platform-managed (added via migration when Tenemsa adds a new feature), never company-writable. Companies compose from a fixed vocabulary; they don't invent new capabilities.
- **Every custom role is still company-scoped** (`roles.company_id`), so "Finance Manager" at Company A and "Finance Manager" at Company B are different rows with independently configured permission sets, even if a company later clones another's naming.

---

## 7. Proposed phased implementation plan (not started — for your approval)

| Step | Scope |
|---|---|
| **3a** | Add `ADMIN` to the `Role` enum (with a migration for any existing seeded data), define the `Permission` enum + `RolePermissionRegistry`, update `JwtFilter` to expand role → permission-authorities |
| **3b** | Migrate every `@PreAuthorize` from role-based to permission-based, add the `TeamSecurity` scope resolver for Manager-own-team checks |
| **3c** | Update the permission catalog against the *actual* current controller list (refining §4 above with real endpoint names) |
| **Phase 4** | `@TenantId` + `company_id` on `Attendance`/`Leave`/`Team`/`Notification`/`ChatMessage`, real company registration (this is also where "company selection" from your very first message finally gets built) |
| **Later, on demand** | Phase B schema (DB-driven roles/permissions) — only once a real "custom role" requirement exists, not speculatively |

Waiting for your go-ahead on this shape before writing any of 3a onward.
