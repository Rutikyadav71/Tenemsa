# Roadmap

Tenemsa is being built in phases, each reviewed and approved before the next starts — not built all at once. This document tracks what's actually done versus planned, and notes where the plan changed from the original design as real findings came up.

## Status summary

| Phase | Focus | Status |
|---|---|---|
| 0 | Security foundation | ✅ Complete |
| 1 | Multi-tenant foundation (User/Company unification) | ✅ Complete |
| — | Frontend TypeScript migration | ✅ Complete (moved up from Phase 9 — see note below) |
| — | Repository cleanup & professional structure | ✅ Complete |
| 2 | Enterprise authentication | 🔜 Next |
| 3 | RBAC, exception handling, DTO/mapper pattern | Planned |
| 4 | Company registration, tenant isolation, audit logging | Planned |
| 5 | Project management module (Jira-style) | Planned |
| 6 | AI assistant layer | Planned |
| 7 | RAG knowledge base | Planned |
| 8 | Document management, notifications, dashboards | Planned |
| 9 | Frontend visual rebuild (Tailwind/shadcn, dark mode) | Planned |
| 10 | DevOps (Docker Compose, CI/CD, monitoring) | Planned |

## Completed

### Phase 0 — Security foundation
- Externalized the JWT secret (was hardcoded in source).
- Fixed missing `@PreAuthorize` checks that let any authenticated employee create/update/delete/search other employees.
- Consolidated CORS configuration — found and removed **two** separate hardcoded CORS sources during this work (one during the original pass, a second independent `CorsFilter` bean found later during repository cleanup that had been silently running in parallel).
- Added `.env.example`, fixed `.gitignore` to actually exclude `.env`.

### Phase 1 — Multi-tenant foundation
- Merged the separate `Employee` and `Admin` entities into one `User` entity, owned by a new `Company` entity via `company_id`.
- Expanded `Role` from a binary `USER`/`ADMIN` to the full hierarchy (`SUPER_ADMIN`, `OWNER`, `HR`, `MANAGER`, `EMPLOYEE`, `INTERN`) — only two tiers are enforced today; the rest exist for Phase 3.
- JWT now carries `companyId` as a claim, avoiding a DB lookup per request just to know tenant context.
- Wrote a staged, reviewable SQL migration (`backend/migration-phase1.sql`) rather than relying on `ddl-auto=update` alone for a change this size.
- Found and fixed several authorization gaps uncovered while touching this code: `AttendanceController` and `TeamController` had no role checks on write operations at all; `UploadController` let any authenticated user overwrite anyone else's profile photo; `LeaveController` was missing checks on approve/reject/delete and on the ML-training-data export.
- A subtle bug caught before shipping: making `empId` unique per-company instead of globally (correct for multi-tenancy) would have silently broken `EmployeeSecurity.isSelf()`, which resolved users by `empId` first — two different companies' employees sharing an `empId` would have collided. Fixed by resolving the caller by email first instead.

**Known, deliberate limitation:** `Attendance`, `Leave`, `Team`, `Notification`, and `ChatMessage` don't carry `company_id` yet. Safe today only because the app still bootstraps a single default company — Phase 4 needs to add this before real multi-company registration goes live. See `docs/architecture.md`.

### Frontend: TypeScript migration (moved up from Phase 9)
Originally planned as part of the Phase 9 visual rebuild, moved earlier once ongoing backend changes (role hierarchy expansion, DTO reshaping) started actively breaking the JS frontend in ways a type system would have caught immediately. Converting once, now, alongside the Phase 1 backend changes was less total work than converting twice.

- All 33 frontend files converted `.jsx`/`.js` → `.tsx`/`.ts`.
- Central domain types (`src/types/models.ts`) mirroring the backend entities — including a deliberately separate `ChatRoleTag` type, kept distinct from the six-tier `Role` enum specifically because conflating them almost broke WebSocket chat delivery during the Phase 1 frontend sync.
- `tsconfig.json` uses moderate strictness (`strict: true` with `noImplicitAny` relaxed) — a deliberate choice, documented in the config itself, to avoid a same-day full conversion forcing rushed `any` typing everywhere just to reach green.
- Verified with both `tsc --noEmit` (zero errors) and a full `npm run build` (succeeds).

### Repository cleanup & professional structure
- Backend package renamed `com.rutik.ems` → `com.tenemsa.platform`; main class renamed to `TenemsaApplication`.
- Restructured into a monorepo: `backend/`, `frontend/`, `ml-service/`, `docs/`.
- Removed dead code found during the pass: an unused `AppConfig.java` (missing `@Configuration`, so its bean never actually registered — a duplicate in `SecurityConfig` was doing the real work), an orphaned `ChatUserDTO.java`, an unused `EmailService.java`.
- Fixed a genuinely broken Maven wrapper (`.mvn/wrapper/maven-wrapper.properties` was missing entirely).
- Removed two stray nested `.git` directories (frontend and ml-service) that would have broken git operations at the repo root.
- Removed a dead, unreferenced duplicate training-dataset CSV in `ml-service/`.

## Next: Phase 2 — Enterprise authentication

- Refresh tokens + rotation.
- Logout from one device / logout from all devices (requires token invalidation, which doesn't exist yet — JWTs are currently stateless and can't be revoked before expiry).
- Email verification, forgot/reset password flows.
- Google OAuth2 login, architected so GitHub/Microsoft can be added later without rework.

## Planned phases (unchanged from original design unless noted)

**Phase 3 — RBAC & backend hygiene:** dynamic permission checks differentiating HR/Manager/Employee/Intern (not just the current two-tier admin/non-admin split), global `@ControllerAdvice` exception handling, DTO/mapper pattern across all controllers (several still return entities directly), pagination/filtering/sorting, Swagger/OpenAPI.

**Phase 4 — Tenant isolation & company lifecycle:** add `company_id` to `Attendance`/`Leave`/`Team`/`Notification`/`ChatMessage` with full query-level enforcement (Specification pattern), replace the single-default-company bootstrap with real company registration + invite-employee flow, audit logging, scope `email` uniqueness per-company instead of globally.

**Phase 5 — Project management:** Jira-style projects/tasks/sprints/kanban.

**Phase 6 — AI assistant layer:** LLM abstraction interface (Gemini first, swappable), meeting summaries, task generation, email drafting — plus properly wiring the existing `ml-service` leave-predictor into the new architecture (and fixing the `leave_type` schema gap documented in `docs/database.md` first).

**Phase 7 — RAG knowledge base:** document upload, embeddings, vector search, company-scoped policy Q&A.

**Phase 8 — Document management, notifications, dashboards:** Drive-style file management, real-time + email notifications (properly scoped per-user/company this time), richer analytics dashboards.

**Phase 9 — Frontend visual rebuild:** Tailwind + shadcn/ui, dark mode, from the current Bootstrap-based UI. (TypeScript itself is already done — see above — this phase is purely visual/component-library now.)

**Phase 10 — DevOps:** Docker Compose for the full local stack, GitHub Actions CI/CD, monitoring (Prometheus/Grafana), and — given the schema now changes phase over phase — introducing Flyway or Liquibase for versioned migrations instead of the current hand-run SQL script approach.

## Design decisions made along the way

Documented here since "why" matters as much as "what" for a project meant to demonstrate real engineering judgment:

- **Stayed on PostgreSQL, not MySQL** (original planning docs mentioned MySQL) — the codebase was already on Postgres with a working driver setup, and Postgres's JSON/full-text search support is useful for the planned Search feature without needing Elasticsearch.
- **Chat routing uses a fixed two-value tag, not the six-tier Role** — a deliberate protocol-layer decision, not an oversight. Extending chat to route by full role hierarchy is a real, separate piece of future work, not a one-line change.
- **`createdBy`/`updatedBy` columns exist but aren't populated yet** — added during Phase 1's schema work since the table was already being touched, but actually wiring "who's calling" through every write path is Phase 3 scope, not bundled in early to avoid a half-finished audit trail.
