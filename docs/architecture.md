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

## Backend layering

```
Controller  →  Service  →  Repository  →  PostgreSQL
```

- **Controller** (`controller/`) — HTTP/WebSocket entry points. Handles request/response shape and `@PreAuthorize` role checks. Should not contain business logic.
- **Service** (`service/`) — business logic. Where authorization *decisions* that depend on data (not just role) get made — e.g. "is this the primary owner."
- **Repository** (`repository/`) — Spring Data JPA interfaces. Query methods are scoped by `company_id` wherever tenant isolation matters (see [Multi-tenancy](#multi-tenancy) below).
- **Security** (`security/`) — JWT generation/validation (`JwtUtil`, `JwtFilter`), the CORS/auth filter chain (`SecurityConfig`), and small helpers (`AuthContext` for reading the current request's tenant context without a DB round-trip; `EmployeeSecurity` for "is this the caller's own record" checks used in `@PreAuthorize`).

This isn't microservices-ready yet, but the layering means it *could* be split along controller boundaries later — each controller's dependencies are already scoped to its own service/repository, not shared mutable state.

## Multi-tenancy

Every company is a row in `companies`. Every `User` belongs to exactly one company via `company_id`, and role-based access is checked at the method level (`@PreAuthorize`) rather than the URL level, so the same endpoint serves every tenant.

**What's tenant-scoped today:** `User` — the core entity everything else keys off. JWTs carry `companyId` as a claim (set at login, read by `JwtFilter` into `AuthContext`), so per-request tenant context costs zero extra DB queries.

**What's not tenant-scoped yet:** `Attendance`, `Leave`, `Team`, `Notification`, and `ChatMessage` don't carry `company_id` yet. This is safe *today* only because the application still bootstraps a single default company (see `CompanyRepository.findFirstByOrderByIdAsc` / `findBySlug("default")` — both explicitly marked as temporary in code comments). Adding real multi-company registration (Phase 4 on the roadmap) requires adding `company_id` to these tables first, with full query-level enforcement via the Specification pattern. Until then, treat this as effectively single-tenant in production.

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

## ML service

`ml-service/` is a separate Flask process, not a Spring dependency. The backend calls it over plain HTTP (`LeaveService` → `http://localhost:5000/predict-leave`) and treats a failed/unreachable ML service as a soft failure — leave management still works without it, predictions just aren't available. This is why it's documented as optional in the quick-start.

## Known architectural debt

Documented here rather than hidden, since a portfolio project's value is partly in showing you know what's incomplete:

- No API versioning yet (`/api/...`, not `/api/v1/...`).
- No global `@ControllerAdvice` exception handler — errors currently return whatever each controller's catch block decides, inconsistently.
- No pagination on list endpoints (`GET /api/employees` returns everything).
- No caching layer.
- No automated tests beyond the Spring Boot context-load smoke test.

All of these are realistic Phase 3+ items, not oversights specific to any one phase.

One specific gap worth flagging on its own: `ChatRestController`'s message-level endpoints (`delete`, `react`, `star`) check authentication but not *ownership* — any authenticated user can currently delete, react to, or star any message by ID, not just messages in their own conversations. Found while writing the API reference below rather than fixed here, since this pass is scoped to cleanup and documentation, not new security remediation — but it's the same class of gap fixed elsewhere in earlier phases (see `EmployeeSecurity.isSelf`), and should be closed the same way before this goes further.
