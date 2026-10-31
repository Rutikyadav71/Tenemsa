# Phase 6 Commits — AI foundation, role-aware sign-in, AI assistants

Branch: `feature/phase-6-ai-and-role-routing`  
Author: Rutikyadav71

---

## What Phase 6 contains

| Sub-phase | Scope |
|-----------|-------|
| **6a** | Spring AI + Gemini behind an `AiClient` interface, `AiGateway` (company/user quotas, timeout, one retry, JSON validation with repair, usage log without prompts or answers), AI meeting summaries to reviewed issues, AI Usage page. AI is off by default (`AI_PROVIDER=none`). |
| **6b** | One sign-in for every role, login returns role permissions, HR may use the admin sign-in, Owner/Admin/HR go to `/admin`, Manager/Employee/Intern to `/user`, menus filtered by permission. |
| **6c** | Task generator, message writer, project status and sprint-retro reports, HR assistant (own data only), per-company AI switch, optional cost estimate, prompt contract test. |

---

## Path aliases

| Alias | Expanded path |
|-------|--------------|
| `B`   | `backend/src/main/java/com/tenemsa/platform` |
| `R`   | `backend/src/main/resources` |
| `T`   | `backend/src/test/java/com/tenemsa/platform` |
| `F`   | `frontend/src` |

---

## Commit log (16 commits, in order)

### Commit 1 — `build: add Spring AI BOM and Google GenAI starter`
**Files:** `backend/pom.xml`

- Added `spring-ai.version` property (`1.1.8`) to `<properties>`.
- Added Spring AI BOM to `<dependencyManagement>` (`spring-ai-bom`, type `pom`, scope `import`).
- Added `spring-ai-starter-model-google-genai` dependency (version from BOM).

---

### Commit 2 — `db: add AI usage log, meeting tables and company AI switch (V5, V6)`
**Files:**
- `R/db/migration/postgresql/V5__ai_and_meetings.sql`
- `R/db/migration/mysql/V5__ai_and_meetings.sql`
- `R/db/migration/postgresql/V6__company_ai_switch.sql`
- `R/db/migration/mysql/V6__company_ai_switch.sql`

V5 creates:
- `ai_request_log` — one row per AI call: `id`, `company_id`, `user_id`, `feature`, `status`, `input_tokens`, `output_tokens`, `cost_usd_estimate`, `created_at`. No prompts or answers stored.
- `meeting` — `id`, `company_id`, `organizer_id`, `title`, `occurred_at`, `participants`, `transcript`, `status` (PENDING/SUMMARISED/APPROVED/REJECTED), `created_at`.
- `meeting_summary` — `id`, `meeting_id`, `raw_json`, `reviewed_by`, `reviewed_at`.

V6 adds `ai_enabled BOOLEAN NOT NULL DEFAULT FALSE` to `company`.

---

### Commit 3 — `feat(security): AI, meeting and assistant permissions, audit actions, AI error mapping`
**Files:**
- `B/security/rbac/Permission.java`
- `B/security/rbac/RolePermissionRegistry.java`
- `B/audit/model/AuditAction.java`
- `B/common/GlobalExceptionHandler.java`

New permissions: `AI_USE`, `AI_USAGE_VIEW`, `MEETING_MANAGE_ANY`, `AI_ASSISTANT_SELF`.  
New audit actions: `MEETING_CREATED`, `MEETING_SUMMARISED`, `MEETING_APPROVED`, `MEETING_REJECTED`, `AI_QUOTA_EXCEEDED`.  
Exception handler: maps `AiLimitExceededException` → 429, `AiUnavailableException` → 503, `AiTimeoutException` → 504, `AiOutputException` → 422.

---

### Commit 4 — `feat(ai): provider abstraction and gateway with quotas, timeout, retry, validation, usage log, cost estimate`
**Files:** `B/ai/**` (everything under `ai/` except `ai/assistant/`), `R/prompts/meeting-summary.v1.txt`

Key classes:
- `AiClient` interface — single `complete(prompt)` contract.
- `SpringAiClient` — delegates to Spring AI `ChatClient`; extracts raw text.
- `DisabledAiClient` — throws `AiUnavailableException` immediately.
- `AiConfig` — `@Bean` that picks `SpringAiClient` when `ai.provider=google-genai`, else `DisabledAiClient`.
- `AiProperties` — `@ConfigurationProperties("ai")` for all `ai.*` settings.
- `AiGateway` — company-enabled check, per-user-per-hour + per-company-per-day quota via `AiRequestLogRepository`, timeout (`ai.timeout-seconds`), one retry on transient error, JSON-repair (`fixJson`), usage log write, optional cost estimate.
- `AiResult` — record wrapping `content`, `inputTokens`, `outputTokens`, `costUsdEstimate`.
- `PromptCatalog` — loads `classpath:prompts/*.txt` by key, interpolates `{{key}}` variables.
- `PromptInput` — builder for prompt variables.
- `AiRequestLog` / `AiRequestStatus` / `AiFeature` — JPA entity and enums.
- `AiUsageService` — admin/HR usage queries.
- `AiController` — `GET /api/ai/status`, `GET /api/ai/usage`.
- `AiUsageResponse` / `AiStatusResponse` — response DTOs.
- Exceptions: `AiLimitExceededException`, `AiUnavailableException`, `AiTimeoutException`, `AiOutputException`, `AiProviderException`.
- `R/prompts/meeting-summary.v1.txt` — structured prompt for meeting summarisation.

---

### Commit 5 — `feat(company): per-company AI on/off switch`
**Files:**
- `B/company/model/Company.java`
- `B/company/dto/CompanyResponse.java`
- `B/company/controller/CompanyController.java`
- `B/company/service/CompanyService.java`

- `Company` entity: new `aiEnabled` boolean field (default `false`).
- `CompanyResponse`: expose `aiEnabled`.
- `CompanyController`: `PATCH /api/company/ai-switch` (Owner only, `company:manage`).
- `CompanyService`: `toggleAiSwitch(companyId, enabled)`.

---

### Commit 6 — `feat(meeting): AI meeting summaries with review, approval and issue creation`
**Files:** `B/meeting/**`

Entities: `Meeting`, `MeetingStatus`, `MeetingSummary`.  
Repositories: `MeetingRepository`, `MeetingSummaryRepository`.  
Service: `MeetingService` — create meeting, request AI summary (`AiGateway`), parse `MeetingAiOutput`, persist summary, manager/admin review (approve → create issues, reject), list.  
Controller: `MeetingController` — CRUD + `POST /api/meetings/{id}/summarise`, `POST /api/meetings/{id}/approve`, `POST /api/meetings/{id}/reject`.  
DTOs: `MeetingRequest`, `MeetingResponse`, `MeetingAiOutput`, `SummaryReviewRequest`.

---

### Commit 7 — `feat(ai): task generator, message writer, project reports, HR assistant`
**Files:** `B/ai/assistant/**`, `R/prompts/task-generator.v1.txt`, `R/prompts/communication-writer.v1.txt`, `R/prompts/project-report.v1.txt`, `R/prompts/hr-assistant.v1.txt`

- `TaskGeneratorService` — takes project context, returns up to N `SuggestedTask` items; optionally persists them as real issues.
- `CommunicationWriterService` — takes kind (`EMAIL|SLACK|ANNOUNCEMENT`), tone, draft; returns polished text.
- `ProjectReportService` — takes report type (`STATUS|SPRINT_RETRO`), sprint/project data; returns markdown report.
- `HrAssistantService` — answers HR questions scoped strictly to the calling user's own leave and attendance data.
- `AssistantController` — `POST /api/ai/tasks/generate`, `POST /api/ai/tasks/create`, `POST /api/ai/communicate`, `POST /api/ai/report`, `POST /api/ai/hr-ask`.
- All prompt files under `R/prompts/`.

---

### Commit 8 — `feat(auth): return role permissions on login and let HR use the admin sign-in`
**Files:**
- `B/auth/dto/AuthResponse.java`
- `B/auth/service/AuthService.java`

- `AuthResponse` adds `permissions: Set<String>` and `portalHint: String` (`"admin"` or `"user"`).
- `AuthService.login()` resolves permissions from `RolePermissionRegistry`, adds `portalHint` based on role:
  - Owner/Admin/HR → `"admin"`
  - Manager/Employee/Intern → `"user"`
- HR `POST /api/auth/admin/login` now accepted (both `/login` and `/admin/login` delegate to the same service method).

---

### Commit 9 — `config: AI settings, off by default`
**Files:**
- `R/application.properties`
- `backend/.env.example`
- `backend/src/test/resources/application-test.properties`

- `application.properties`: added `ai.*` block, `spring.ai.model.chat`, `spring.ai.google.genai.api-key`.
- `.env.example`: added `AI_PROVIDER`, `AI_MODEL`, `GEMINI_API_KEY` with comments.
- `application-test.properties`: `ai.provider=none`, no key required.

---

### Commit 10 — `test: meetings, AI failures and limits, AI disabled, assistants, prompt contract, login routing`
**Files:**
- `T/MeetingIntelligenceTest.java`
- `T/AiFailureAndLimitsTest.java`
- `T/AiDisabledTest.java`
- `T/AiAssistantsTest.java`
- `T/PromptContractTest.java`
- `T/LoginRoutingTest.java`

Integration tests using `@SpringBootTest` + H2 + `ai.provider=none`:
- **MeetingIntelligenceTest** — create meeting, stub AI, assert summary persisted, approve/reject paths, issue creation from approved summary.
- **AiFailureAndLimitsTest** — per-user hourly quota, per-company daily quota, timeout, AI output parse failure → 422.
- **AiDisabledTest** — all AI endpoints return 503 when `ai.provider=none`.
- **AiAssistantsTest** — task generator, communication writer, project report, HR assistant (own data only, scope boundary).
- **PromptContractTest** — every `prompts/*.txt` file loads without error, contains expected placeholders.
- **LoginRoutingTest** — login response carries `permissions`, HR login via `/admin/login` succeeds, `portalHint` correct per role.

---

### Commit 11 — `feat(frontend): shared types, role helpers, permissions in local storage`
**Files:**
- `F/shared/types/models.ts`
- `F/shared/lib/roles.ts`
- `F/shared/lib/authStorage.ts`

- `models.ts`: adds `AuthResponse` with `permissions`, `portalHint`; `AiUsageEntry`, `MeetingSummary`, etc.
- `roles.ts`: `isAdminPortal(role)`, `isUserPortal(role)`, `hasPermission(permission)` helpers.
- `authStorage.ts`: stores/retrieves `token`, `role`, `permissions` (as JSON array), `portalHint` from `localStorage`.

---

### Commit 12 — `feat(frontend): AI services, meeting AI, task generator, report, writer, assistant and usage pages`
**Files:** `F/features/ai/**`, `F/features/meetings/**`

- `aiService.ts` — typed `axios` wrappers for all `/api/ai/*` endpoints.
- `meetingService.ts` — CRUD + summarise/approve/reject.
- `MeetingsPage.tsx`, `MeetingDetailPage.tsx` — meeting list and detail with AI summary panel.
- `AiUsagePage.tsx` — admin/HR view of usage logs and cost estimates.
- `AssistantPage.tsx` — HR assistant chat UI.
- `WriterPage.tsx` — communication writer form.
- `TaskGeneratorModal.tsx` — modal invoked from board to generate tasks.
- `ProjectReportModal.tsx` — modal to generate status or retro report.

---

### Commit 13 — `feat(frontend): AI buttons on the board and company AI switch`
**Files:**
- `F/features/projects/pages/ProjectBoardPage.tsx`
- `F/features/company/pages/CompanySettings.tsx`

- `ProjectBoardPage.tsx`: "Generate Tasks" button (requires `ai:use`) opens `TaskGeneratorModal`; "Report" button opens `ProjectReportModal`.
- `CompanySettings.tsx`: AI enabled/disabled toggle (requires `company:manage`, Owner only).

---

### Commit 14 — `feat(frontend): sign-in pages route by role, dashboard sections load independently`
**Files:**
- `F/features/auth/pages/AuthPage.tsx`
- `F/features/auth/pages/AdminAuthPage.tsx`
- `F/features/dashboard/pages/AdminDashboard.tsx`

- `AuthPage.tsx` (employee portal): after login, reads `portalHint`; if `"admin"` redirects to `/admin/dashboard`.
- `AdminAuthPage.tsx`: HR can now log in here; redirects based on `portalHint`.
- `AdminDashboard.tsx`: each dashboard section (stats, recent activity, AI usage) uses independent `useEffect` so a single slow call doesn't block others.

---

### Commit 15 — `feat(frontend): workspace guard by role, permission-filtered menus, AI routes`
**Files:**
- `F/app/App.tsx`
- `F/layouts/admin/AdminSidebar.tsx`
- `F/layouts/user/UserSidebar.tsx`

- `App.tsx`: `<WorkspaceGuard>` reads `portalHint` and role; redirects to the correct portal; adds AI routes (`/admin/ai/usage`, `/admin/ai/assistant`, `/admin/ai/writer`, `/admin/meetings`, `/user/ai/assistant`, `/user/ai/writer`).
- `AdminSidebar.tsx`: menu items filtered by `hasPermission()`; AI section shown only when `ai:use` or `ai:usage:view`.
- `UserSidebar.tsx`: same for user portal; AI section for `ai:assistant:self`.

---

### Commit 16 — `docs: Phase 6 (AI foundation, role routing, AI assistants)`
**Files:**
- `docs/api.md`
- `docs/architecture.md`
- `docs/database.md`
- `docs/deployment.md`
- `docs/roadmap.md`
- `PHASE-6-COMMITS.md`

Updated docs covering:
- New API endpoints (`/api/ai/*`, `/api/meetings/*`).
- Architecture diagram updated for `AiGateway`, `AiClient` abstraction.
- Database schema V5/V6 documented.
- Deployment guide: `GEMINI_API_KEY`, `AI_PROVIDER` env vars.
- Roadmap: Phase 6 marked complete, Phase 7 (predictive analytics) outlined.

---

## File count

| Layer | New files | Modified files |
|-------|-----------|---------------|
| Backend Java | ~55 | 8 |
| Backend resources | 7 | 2 |
| Frontend TypeScript/TSX | ~35 | 8 |
| Tests | 6 | 0 |
| Docs / root | 6 | 0 |
| **Total** | **~109** | **18** |

Grand total ≈ **118 files** across 16 commits.
