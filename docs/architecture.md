# Architecture

## Overview

Tenemsa is a layered monolith today, deliberately structured so it can be pulled apart into services later without a rewrite. Three deployable components talk to each other over HTTP/WebSocket:

```
┌─────────────────┐        HTTPS/REST         ┌──────────────────────┐
│  React Frontend  │ ───────────────────────▶ │   Spring Boot API     │
│  (TypeScript)    │ ◀─────────────────────── │   (backend/)          │
└─────────────────┘        WebSocket (STOMP)   └──────────┬───────────┘
                                                            │
                                                    ┌───────┴────────┐
                                                    │                │
                                             ┌──────▼──────┐  ┌──────▼───────┐
                                             │ PostgreSQL   │  │ Flask ML svc │
                                             │              │  │ (ml-service) │
                                             └──────────────┘  └──────────────┘
```

The frontend never talks to Postgres or the ML service directly — everything goes through the Spring Boot API, which is the single source of truth for authorization and tenant boundaries.

## Backend structure: package by feature

Code is grouped by what it does, not by technical layer. Each feature folder under `com.tenemsa.platform` holds everything for that feature, split into `controller/`, `service/`, `repository/`, `model/` (entities and enums) and `dto/` sub-folders, for example `leave/controller/LeaveController`, `leave/service/LeaveService`, `leave/repository/LeaveRepository`, `leave/model/Leave`. Very small features (`mail`, `common`) stay flat:

```
com.tenemsa.platform
├── TenemsaApplication
├── auth/          login, tokens, password reset, email verification
├── company/       company registration and workspace lookup
├── employee/      User entity, roles, employee CRUD, admin accounts
├── invitation/    invite-by-email flow
├── audit/         audit trail
├── attendance/  leave/  team/  notification/  chat/
├── project/       projects        (project/issue = issues and comments, project/sprint = sprints)
├── upload/        file uploads (Cloudinary)
├── mail/          EmailService
├── common/        GlobalExceptionHandler, ResourceNotFoundException, HealthController
├── security/      JWT, filters, AuthContext, rate limiting; security/rbac = permissions and role registries
└── config/        framework wiring (Jackson, OpenAPI, OAuth2 client, security beans)
```

Rules for new code:
- A new feature gets its own folder (for example `workflow/`, `ai/`) with the same five sub-folders, not a new class in a layer folder.
- Inside a feature the roles are still separate: the controller handles HTTP and `@PreAuthorize`, the service holds business rules, the repository is a Spring Data interface scoped by `company_id`, DTOs are records. Controllers never touch repositories of another feature directly; they call that feature's service.
- Shared cross-cutting code lives in `security`, `common` or `config`. Do not put feature logic there.
- One feature may depend on another's service or entity, but avoid circular dependencies between feature folders. `project`, `project.issue` and `project.sprint` depend on each other on purpose because they form one domain.
- Flyway migrations, tests and the frontend are unchanged by this layout.

### Frontend structure

```
frontend/src
├── main.tsx
├── app/            App.tsx (routes and layouts)
├── styles/         global CSS
├── layouts/        admin/ and user/ navbar + sidebar
├── shared/         api/ (axios instance), lib/ (auth storage, logout), types/models.ts, components/
└── features/<feature>/
        pages/        route-level screens
        components/   pieces used only by this feature
        services/     API calls for this feature
```
Features: `home`, `auth`, `company`, `employees`, `dashboard`, `leave`, `attendance`, `team`, `projects`, `invitations`, `audit`, `chat`, `notifications`. A new feature (for example `workflow`, `ai`) gets its own `features/<name>/` folder with the same three sub-folders. A feature may import from `shared/` and `layouts/`; avoid importing one feature's pages from another (shared pieces move to `shared/components`).

This isn't microservices-ready yet, but feature folders are the natural seams if it is ever split: each feature's dependencies are already scoped to its own service and repository.

## Multi-tenancy

Every company is a row in `companies`. Every `User` belongs to exactly one company via `company_id`, and role-based access is checked at the method level (`@PreAuthorize`) rather than the URL level, so the same endpoint serves every tenant.

**Tenant-scoped (Phase 4):** `User`, `Attendance`, `Leave`, `Team`, `Notification` and `ChatMessage` all carry a `company_id` column. JWTs carry `companyId` as a claim (set at login, read by `JwtFilter` into `AuthContext`), so per-request tenant context costs zero extra DB queries.

**How isolation is enforced.** `empId` is only unique *within* a company (company A's employee 101 and company B's employee 101 are different people), so:

1. Every repository finder for these entities takes `companyId` as its first argument (`findByCompanyIdAndEmpId`, `findByIdAndCompanyId`, …). There is deliberately no unscoped `findAll()` / `findById()` left on a tenant entity's call path.
2. Controllers and services read the tenant from `AuthContext.currentCompanyId()` (the JWT), **never from the request body or URL**. On create, the server stamps `companyId` itself, overwriting anything the client sent.
3. By-id lookups (`/api/leaves/{id}`, `/api/teams/{id}`, admin freeze/delete, profile upload) resolve with `…AndCompanyId`, so guessing another tenant's id yields "not found" instead of their data.
4. Scope resolvers used in `@PreAuthorize` (`TeamSecurity`, `LeaveSecurity`) are themselves company-scoped, so a team lead in company A can never "manage" an employee id that happens to exist in company B.

**Bugs this closed** (all were real cross-tenant exposures once a second company existed): `GET /api/notifications` returned every company's notifications; `PUT /api/notifications/mark-all-read` flipped every company's; leave/team/attendance/chat lookups by id or empId were global; `AdminController` freeze/unfreeze/delete and `UploadController.admin-profile` used raw `userRepository.findById`.

**Why manual scoping and not Hibernate `@TenantId`:** `@TenantId` filters automatically, which is the stronger long-term guarantee (a forgotten `companyId` argument becomes impossible). It was not used here because it needs a `CurrentTenantIdentifierResolver` wired to the request thread and interacts with the WebSocket and scheduled-job paths, which have no HTTP request. Manual scoping is explicit, greppable and testable today; moving to `@TenantId` (or PostgreSQL row-level security as a second line of defence) is the planned hardening step.

**Company lifecycle (Phase 4b).** The single default company is gone. `POST /api/companies/register` creates a company and its Owner in one transaction (`CompanyService.register`): the slug is generated from the name (NFD-normalised, lowercase, hyphenated, 3–40 chars, reserved words blocked, numeric suffix on collision) and is immutable so workspace URLs and bookmarks never break. New users get a per-company `empId` via `UserRepository.nextEmpId(companyId)`. The sign-in flow is workspace-first: `/workspace` looks the company up by slug (`GET /api/companies/lookup`, public, name and slug only), then `/auth/employee?workspace=slug` or `/auth/admin?workspace=slug` posts the credentials with `workspace`, and `AuthService.login` rejects a mismatch with the same generic error as a bad password. The last workspace is remembered in `localStorage` as a convenience only. Public registration and lookup have no rate limiting yet, so workspace names can be enumerated; this is an accepted risk until Phase 4c.

**Per-company email (Phase 4c).** `users` is unique on `(company_id, email)`, so one person can hold accounts in several workspaces. Everything that identifies the caller from the JWT now uses `findByEmailAndCompanyId(email, companyId)`, because the token's email alone is no longer unique. The pre-authentication flows resolve the workspace themselves: login filters candidate accounts by the chosen workspace and by password; if the password matches accounts in more than one workspace and no workspace was given it asks the user to choose one (this is only said after the password is proven, so it cannot be used to probe); forgot-password and resend-verification take an optional `workspace` and otherwise act on every matching account; Google sign-in works only while the address maps to exactly one account.

**Audit trail (Phase 4c).** `AuditService` writes one `audit_logs` row per sensitive action: sign-in (password and Google), failed sign-in for a known account, password reset, company registration and rename, user create/update/delete/freeze/unfreeze, leave approve/reject/delete, and invitation sent/accepted/revoked. Each write runs in its own transaction and swallows its own failures, so auditing can never break the action it records. `GET /api/audit` needs `audit:view` (Owner and Admin) and is scoped to the caller's company.

**Invitations (Phase 4c).** `POST /api/invitations` (needs `user:create`, and the target role must pass `RoleAssignmentRegistry` for the inviter, so an invitation can never grant more than a manual create) stores a hashed single-use token and emails `/accept-invite?token=`. The public `preview` and `accept` endpoints use the token as the credential. Accepting creates the user with a per-company `empId` and a verified email (the link proves mailbox ownership), consumes the token and signs the user in. Re-inviting an address revokes the earlier link. The older "add employee with a temporary password" form still works.

**Tests.** `CrossTenantIsolationTest` (`@SpringBootTest` + MockMvc + in-memory H2, `@ActiveProfiles("test")`) drives the real HTTP stack: employee list and by-empId lookups, company endpoints, audit and invitation isolation, shared email across workspaces, wrong-workspace login, and the invitation lifecycle (preview, one-time accept, expiry). Run with `./mvnw test`.

**Hardening (Phase 4d).**
- *Ownership.* `POST /api/leaves` now requires `empId` to be the caller's own unless they hold `leave:approve:any`. Every `/api/chat` endpoint derives the caller's chat identity from the JWT (`("ADMIN", database id)` for admin-tier users, `("USER", empId)` for everyone else, see `ChatIdentity`) and requires them to be a participant: history, starred, unread, mark-read and conversation delete return `403` otherwise, and message-level actions return `404` (sender only for delete). Note this means Owners and Admins can no longer read employees' private conversations through the API.
- *Validation.* `spring-boot-starter-validation` with `@Valid` on the public and credential-bearing request bodies; password length is capped at 128 so oversized input cannot be used to burn BCrypt time.
- *Rate limiting.* `RateLimitFilter` applies fixed-window per-IP limits to the unauthenticated endpoints (limits are in `docs/api.md`). It runs just after Spring Security's chain so CORS headers are already set on the `429`. State is in memory (per instance); a shared store such as Redis is the upgrade if the backend is scaled out. Behind a proxy set `RATE_LIMIT_TRUST_PROXY=true` (see `docs/deployment.md`).
- *Tests.* `OwnershipAndValidationTest` covers leave ownership and leave isolation between companies, chat privacy (including the Owner and another company), and validation messages; `RateLimitTest` covers the limiter with its own database.

**Phase 4e.**
- *Request DTOs.* `POST/PUT /api/employees`, `POST/PUT /api/teams` and `POST /api/attendance/mark` now take dedicated request records (`EmployeeCreateRequest`, `EmployeeUpdateRequest`, `TeamRequest`, `AttendanceMarkRequest`) with Bean Validation, instead of JPA entities. This also closes a mass-assignment hole: a client could previously set `primaryOwner`, `frozen`, `emailVerified` or `companyId` in the employee body. Work hours are computed by the server, never taken from the client.
- *Referential checks.* Team members and the team lead, and the employee an attendance record is marked for, must exist in the caller's own company (`400` otherwise). A requested `empId` that is already taken is a clear `400`; when none is given the next free number is assigned.
- *Errors.* Malformed JSON or an unknown enum value now returns `400` instead of `500`.
- *Flyway.* Versioned migrations per database, with automatic baselining of existing databases (see `docs/database.md`).
- *Tests.* `TeamAttendanceEmployeeTest` covers team and attendance isolation (including the same `empId` and date in two companies), validation, server-computed hours, mass-assignment and `empId` handling.

**Phase 5a: project management.** `projects`, `issues` and `issue_comments` (all carry `company_id`; created by Flyway `V3`). Issues are numbered per project (`ACME-1`, `ACME-2`): the project row is locked while the next number is taken, and `(project_id, issue_number)` is unique as a backstop. Requests and responses are DTOs. Permissions: everyone can view projects and raise issues (`project:view`, `issue:create`); Owner, Admin and Manager can create/edit/archive projects (`project:manage`) and change any issue (`issue:manage:any`); anyone else can change only issues they reported or are assigned to (checked per record in `IssueService`). Archived projects are read-only and only archived projects can be deleted. A record from another company is always a `404`. Every company member sees every project for now; per-project membership is deferred.

**Phase 5b: sprints.** `sprints` belong to a project; issues point at a sprint with a nullable `sprint_id` (null = backlog). `SprintService` enforces the lifecycle: PLANNED, then ACTIVE (one per project, guarded by the project row lock), then COMPLETED. Completing moves unfinished issues to another planned sprint or the backlog and stores a count snapshot; completed sprints and their issues are frozen. Planning (create/start/complete/delete sprints, moving issues in and out) needs `project:manage` / `issue:manage:any`, so it is a Manager/Admin/Owner decision; everyone can see sprints. Labels and story points are plain issue fields validated in `IssueService`.

**Also in this change:** an email delivery failure (for example a wrong SMTP password) no longer fails the request. `EmailService` catches Spring's unchecked `MailException`, and the invitation response carries `emailSent` so the UI can warn that the link was not delivered. `PATCH` is allowed by CORS.

**Still open:** `Leave` and `Notification` are still bound as entities on a few endpoints; rate limiting is per instance; tenant scoping is manual (Hibernate `@TenantId` or PostgreSQL row-level security are the hardening options); the Flyway `DDL_AUTO` default is still `update` until `validate` has been proven in each environment.

## Role hierarchy

```
SUPER_ADMIN   (platform-level, reserved for future cross-company tooling)
OWNER         (company owner — full control within their own company)
HR
MANAGER
EMPLOYEE
INTERN
```

Only two tiers are actually enforced today: `OWNER`/`SUPER_ADMIN` (admin-equivalent, via `SecurityRoles.IS_ADMIN`) vs. everyone else. The four-way split between HR/Manager/Employee/Intern exists in the schema and JWT but has no differentiated business logic yet — that's Phase 3 (dynamic permissions), once there's an actual HR-only or Manager-only endpoint to justify the complexity.

## Authentication

- JWT access tokens, HMAC-SHA256, secret from `JWT_SECRET` env var (never hardcoded — see git history if you're wondering why that matters).
- Token claims: subject (email), `role`, `companyId`.
- No refresh tokens yet, no logout-token-invalidation yet — both are Phase 2.
- Login is split across two endpoints for historical reasons that are now enforced rather than accidental: `/api/auth/login` (any role) and `/api/admin/login` (requires an admin-equivalent role, checked explicitly in `AuthService.adminLogin` — this check became necessary specifically because employees and admins now share one `users` table instead of two).

## Real-time layer

WebSocket chat and notifications run over STOMP (`/ws` endpoint, SockJS fallback). One detail worth knowing if you're extending chat: message routing uses a **fixed two-value tag** (`ADMIN`/`USER`) to build queue destinations (`/user/{tag}_{id}/queue/messages`) — this is a routing-layer convention, *not* the six-tier `Role` enum. Conflating the two breaks message delivery silently (a real bug caught during development — see `ChatUserController`'s comments for the full explanation). If you ever extend chat to route by the full role hierarchy, that's a deliberate protocol change, not a one-line fix.

**WebSocket authentication (Phase 4).** HTTP security never sees frames inside an open WebSocket, and `/ws/**` is `permitAll` for the handshake, so authentication happens at the STOMP layer in `StompAuthChannelInterceptor`:

- `CONNECT` must carry `Authorization: Bearer <access token>` as a STOMP header (the frontend's `chatService.ts` sends it). Invalid, unknown or frozen users are rejected.
- The verified identity (`companyId`, user id, `empId`, chat tag) is stored in the STOMP session. `ChatController` uses *that*, not the payload, to check the sender, and verifies the receiver exists in the same company with the claimed tag.
- `SUBSCRIBE` is only allowed to the caller's own queue. Queue names include the company — `/user/c{companyId}_{ADMIN|USER}_{id}/queue/messages` — because `USER_101` exists in every company and would otherwise be one shared queue.

Role-to-tag mapping lives in one place (`ChatIdentity.tagFor`): OWNER/ADMIN/SUPER_ADMIN → `ADMIN`, everyone else → `USER`. Known gap: `ChatRestController` delete/react/star are company-scoped but don't yet check that the caller is a participant in the message.

## ML service

`ml-service/` is a separate Flask process, not a Spring dependency. The backend calls it over plain HTTP (`leave.LeaveService` → `http://localhost:5000/predict-leave`) and treats a failed/unreachable ML service as a soft failure — leave management still works without it, predictions just aren't available. This is why it's documented as optional in the quick-start.

## Known architectural debt

Documented here rather than hidden, since a portfolio project's value is partly in showing you know what's incomplete:

- No API versioning yet (`/api/...`, not `/api/v1/...`).
- No global `@ControllerAdvice` exception handler — errors currently return whatever each controller's catch block decides, inconsistently.
- No pagination on list endpoints (`GET /api/employees` returns everything).
- No caching layer.
- No automated tests beyond the Spring Boot context-load smoke test.

All of these are realistic Phase 3+ items, not oversights specific to any one phase.

One specific gap worth flagging on its own: `ChatRestController`'s message-level endpoints (`delete`, `react`, `star`) check authentication but not *ownership* — any authenticated user can currently delete, react to, or star any message by ID, not just messages in their own conversations. Found while writing the API reference below rather than fixed here, since this pass is scoped to cleanup and documentation, not new security remediation — but it's the same class of gap fixed elsewhere in earlier phases (see `EmployeeSecurity.isSelf`), and should be closed the same way before this goes further.
