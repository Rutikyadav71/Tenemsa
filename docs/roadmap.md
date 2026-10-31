# Roadmap

Tenemsa is being built in phases, each reviewed and approved before the next starts — not built all at once. This document tracks what's actually done versus planned, and notes where the plan changed from the original design as real findings came up.

## Status summary

| Phase | Focus | Status |
|---|---|---|
| 0 | Security foundation | ✅ Complete |
| 1 | Multi-tenant foundation (User/Company unification) | ✅ Complete |
| — | Frontend TypeScript migration | ✅ Complete (moved up from Phase 9 — see note below) |
| — | Repository cleanup & professional structure | ✅ Complete |
| 2 | Enterprise authentication | ✅ Complete |
| 3 | RBAC, exception handling, DTO/mapper pattern | ✅ Complete |
| 4a | Tenant isolation (`company_id` everywhere, WebSocket auth) | ✅ Complete |
| 4b | Company registration, workspace UI, Tenemsa branding | ✅ Complete |
| 4c | Audit logging, per-company email uniqueness, invite flow, cross-tenant tests | ✅ Complete |
| 4d | Request validation, rate limiting on public endpoints, ownership checks (chat, leave apply), leave/chat isolation tests | ✅ Complete |
| 4e | Flyway migrations, validation on entity-bound endpoints (employee/team/attendance), team and attendance isolation tests | ✅ Complete |
| 4f | Entity-id isolation hardening (`@TenantId`/RLS evaluation), shared rate-limit store, Leave/Notification DTOs | Planned |
| 5a | Project management: projects, issues, kanban board, comments (Flyway V3) | ✅ Complete |
| 5b | Sprints, backlog, story points, labels, sprint complete with carry-over (Flyway V4) | ✅ Complete |
| 5c | Project membership / private projects, issue links | Deferred, to be discussed |
| 6a | AI foundation (provider abstraction, prompts, quotas, usage log) + meeting summaries to issues (Flyway V5) | ✅ Built, awaiting first real-model test |
| 6b | Role-aware sign-in and menus: one login for everyone, HR admin sign-in, permissions in login response, workspace by role | ✅ Built |
| 6c | Task generator, message writer, project status and sprint retro, HR assistant, per-company AI switch, cost estimate, prompt contract tests (Flyway V6). Completes Phase 6 apart from the parked leave predictor | ✅ Built, awaiting first real-model test |
| 7 to 10 | See "Revised roadmap: Phase 6 onward" below | Proposed |

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

**Resolved in 4a/4b:** (historical note) `Attendance`, `Leave`, `Team`, `Notification`, and `ChatMessage` don't carry `company_id` yet. Safe today only because the app still bootstraps a single default company — Phase 4 needs to add this before real multi-company registration goes live. See `docs/architecture.md`.

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

## Revised roadmap: Phase 6 onward (proposal, awaiting approval)

Phases 0-5 are complete and stable and are not reopened. The old Phase 9 (frontend rebuild) is already done, and the old Phase 10 (DevOps) is deferred and not scheduled.

| Phase | Scope | Mostly uses |
|---|---|---|
| 6 | AI foundation (Spring AI + Gemini behind our own interface, prompt templates, structured output, usage/audit records, limits) and first AI features: meeting intelligence, task generator, communication writer, project assistant, HR assistant; reconnect the ML leave predictor | Spring AI; plain Java for data lookups |
| 7 | RAG knowledge base: upload, extract, chunk, embed, vector search, grounded answers with citations, tenant-scoped retrieval | Spring AI + vector store (decision needed) |
| 8 | Document management, per-user notifications (in-app + email), role dashboards and analytics | Plain Java; AI only for summaries |
| 9 | Workflow automation: triggers, conditions, registered actions, execution records, idempotency; deterministic rules first, controlled agents later | Plain Java first; Spring AI tool calling last |
| 10 | Product polish toward Jira-level UX (drag-and-drop persistence, saved filters, AI assistant entry points, automation centre, accessibility) | Existing frontend |

Dependencies: 6 before everything AI; 7 needs 6 and document storage from 8, so 8's document upload comes first or is built minimally inside 7; 9 needs notifications from 8; agents in 9 need 6's tool framework and audit.

### Decisions taken (Phase 6 start)
- **RAG runs on PostgreSQL with pgvector.** MySQL stays supported for local development only; production (e.g. Render) uses PostgreSQL. Features needing vectors (Phase 7) are PostgreSQL-only.
- **Leave predictor is parked.** `ml-service` and `leave_type` are untouched until explicitly requested.
- **Gemini via Spring AI** behind our own `AiClient` interface.

### Open decisions before Phase 6 code (now resolved, kept for history)
1. **Vector store vs. database support.** The app runs on PostgreSQL or MySQL by profile. pgvector is PostgreSQL only. Options: make RAG require PostgreSQL, or standardise on PostgreSQL. Decide before Phase 7; it does not block Phase 6.
2. **Spring AI version.** Spring AI 1.1.x (1.1.8 as of June 2026) is built on Spring Boot 3.5, matching this project. 2.0 is not recommended yet. Confirm the Gemini starter artifact when adding the dependency.
3. **Leave predictor.** `ml-service` is a Flask/scikit-learn service (logistic regression) that is called by `LeaveService` through a hardcoded URL. The backend sends a `leave_type`, but the `Leave` entity has no such column, so it is probably broken today. Options: add `leave_type` to leave requests, or retrain without it.
4. **Scope of Phase 6.** Smallest useful milestone below, then one feature at a time.

### Phase 6 first milestone (6a): AI foundation plus meeting summaries
Acceptance: a Manager pastes meeting notes, gets a structured summary (decisions, action items with owner/deadline, open questions), edits it, and explicitly creates draft issues in a project. Failures (timeout, bad output, quota) show a clear message. Every call is audited with latency and token counts. Another company can never see the result.

Likely files: `ai/AiClient` (our interface), `ai/GeminiAiClient`, `ai/PromptTemplates`, `model/AiRequestLog`, `AiService`, `MeetingSummaryService`, `AiController`, DTOs, Flyway V5, `pom.xml` Spring AI BOM, `application.properties` (key from env only), frontend `MeetingSummaryPage`. Tests with a mocked `AiClient`: structured-output parsing, invalid output handling, cross-company access, permission checks, rate limit, issue creation only after confirmation.

## Design decisions made along the way

Documented here since "why" matters as much as "what" for a project meant to demonstrate real engineering judgment:

- **Stayed on PostgreSQL, not MySQL** (original planning docs mentioned MySQL) — the codebase was already on Postgres with a working driver setup, and Postgres's JSON/full-text search support is useful for the planned Search feature without needing Elasticsearch.
- **Chat routing uses a fixed two-value tag, not the six-tier Role** — a deliberate protocol-layer decision, not an oversight. Extending chat to route by full role hierarchy is a real, separate piece of future work, not a one-line change.
- **`createdBy`/`updatedBy` columns exist but aren't populated yet** — added during Phase 1's schema work since the table was already being touched, but actually wiring "who's calling" through every write path is Phase 3 scope, not bundled in early to avoid a half-finished audit trail.

### Package-by-feature refactor
After Phase 5b the backend moved from package-by-layer to package-by-feature (see "Backend structure" in `architecture.md`). Pure file moves plus package/import updates; no behaviour change. All later phases follow the feature layout.

### Phase 6a delivered
Backend `ai` and `meeting` features, Flyway V5, frontend "Meeting AI" and "AI Usage" pages. Guardrails: AI off by default (`AI_PROVIDER=none`), key only on the server, quotas per user per hour and per company per day, hard timeout plus one retry, JSON validated with one repair attempt, usage logged without storing prompts or answers, notes treated as untrusted input, a human approves before anything is created, and issues are created with the caller's own permissions.

Ideas for later (6b+): email/announcement writer with confirmation, stand-up and sprint-retrospective drafts, HR assistant over the user's own leave/attendance, prompt evaluation test set, per-company AI on/off switch, cost estimate on the usage page, RAG on pgvector (Phase 7).

### Phase 6c delivered (Phase 6 complete)
- **Task generator** (`/api/ai/tasks/generate`, `/create`): suggests tasks from a goal, cleans the output (type, priority, points, labels), flags likely duplicates; issues exist only after a person confirms, labelled `ai-draft`.
- **Message writer** (`/api/ai/writer/draft`): announcement, reminder, feedback and similar drafts; nothing is stored or sent.
- **Project reports** (`/api/ai/projects/{id}/report`): status update or sprint retrospective. The system computes every number; the model writes it up; the numbers are returned as `basedOn`.
- **HR assistant** (`/api/ai/hr-assistant/ask`): answers questions about the caller's OWN leave and attendance. Held by every role (`ai:assistant:self`). No tools, no policy data, no other people's records.
- **Company AI switch** (`PUT /api/companies/me/ai`, Flyway V6) and **cost estimate** on the usage page (`ai.price-*` settings).
- **Prompt contract test** guards the safety rules and JSON shape of every prompt file.
- **Parked:** the leave predictor (`ml-service`, `leave_type`) until you ask for it.
- **Ready for Phase 7:** the gateway, prompts, quotas, usage log and permission model are in place; RAG adds embeddings and retrieval on PostgreSQL/pgvector.
