# Phase 8 (combined 8a + 8b + 8c): team leave approval, notifications, documents, analytics

Branch: `feature/phase-8-leave-notifications-documents-analytics` (create from the branch that holds Phase 7)

## How to apply (for Antigravity or any agent): no code changes needed

1. Create the branch above from the Phase 7 state.
2. Source of truth for file contents: `Tenemsa-Phase8-full.zip` (the finished Phase 8 project, paths under `Tenemsa/`).
3. For each commit below, copy the listed paths from the zip into the working tree **exactly as they are** (overwrite or add), stage only those paths, and commit with the given message. Never edit file contents, and never merge or reconcile anything: several files changed in more than one sub-phase (for example `App.tsx`, both sidebars, `models.ts`, `application.properties`, the docs), and here each one is committed ONCE in its final state.
4. Do not commit any earlier `PHASE-8A/8B/8C` guides; this file replaces them.
5. Every commit message ends with these two trailer lines:

```
push by Rutikyadav71
```

All paths below are relative to `Tenemsa/`. `B` = `backend/src/main/java/com/tenemsa/platform`, `R` = `backend/src/main/resources`, `T` = `backend/src/test/java/com/tenemsa/platform`, `F` = `frontend/src`. A trailing `/**` means every file under that folder in the zip.

## Commits (in this order)

| # | Files | Message |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/{postgresql,mysql}/V8__leave_decisions_and_notifications.sql`, `backend/src/main/resources/db/migration/{postgresql,mysql}/V9__documents.sql` | db: leave decisions, personal notifications, email preferences, document tables (V8, V9) |
| 2 | `B/security/rbac/Permission.java`, `B/security/rbac/RolePermissionRegistry.java`, `B/audit/model/AuditAction.java` | feat(security): document:view permission for every role, document audit actions |
| 3 | `B/notification/**`, `B/mail/EmailService.java` | feat(notification): personal notifications with optional email, preferences and read state |
| 4 | `B/leave/**` | feat(leave): team leave list, decisions with note, no self-approval, recorded decider, personal notifications |
| 5 | `B/document/**` | feat(document): document storage with visibility rules, safe downloads, storage limit, knowledge-base link |
| 6 | `B/analytics/**`, `B/project/issue/repository/IssueRepository.java` | feat(analytics): role dashboards and personal summary |
| 7 | `R/application.properties`, `backend/.env.example` | config: document storage limit setting |
| 8 | `T/TeamLeaveApprovalTest.java`, `T/DocumentStorageTest.java`, `T/DocumentQuotaTest.java`, `T/AnalyticsTest.java` | test: team leave approval, notifications, documents, storage limit, analytics |
| 9 | `F/shared/types/models.ts`, `F/features/leave/**`, `F/features/notifications/**`, `F/layouts/user/UserNavbar.tsx` | feat(frontend): Team Leave page, personal notifications with email switch, shared types |
| 10 | `F/features/documents/**`, `F/features/analytics/**` | feat(frontend): Documents and Insights pages |
| 11 | `F/app/App.tsx`, `F/layouts/admin/AdminSidebar.tsx`, `F/layouts/user/UserSidebar.tsx` | feat(frontend): routes and menu items for Team Leave, Documents and Insights |
| 12 | `docs/*.md`, `PHASE-8-COMMITS.md` | docs: Phase 8 (team leave, notifications, documents, analytics) |

Total: 63 files across the 12 commits (plus this guide). After the last commit, `git diff` between the branch and the Phase 8 zip must be empty.

## What Phase 8 contains

- **8a:** Manager Team Leave screen (own teams only, never their own leave, pending requests only, a rejection needs a reason, who/when/note recorded, who else is off shown). Notifications are personal; email copy is optional per person. Flyway V8.
- **8b:** Document storage of the original files with category and visibility (all staff, management only, one employee), type and content checks, safe downloads, a per-company storage limit, and an option to also add a document to the AI knowledge base. Flyway V9.
- **8c:** Insights page: personal summary for everyone; company or team dashboards (headcount, leave, attendance, tasks). No salary or payroll data.

## Verify

1. `cd backend && mvn clean test` should report **101 tests, 0 failures** (82 before Phase 8, +6 team leave and notifications, +8 documents, +1 storage limit, +4 analytics).
2. `cd frontend && npx tsc --noEmit -p . && npx vite build` should pass.
3. Try it: as a Manager who leads a team, decide an Employee's leave; as Owner or HR, upload a private document for an Employee and open Insights; as the Employee, check Notifications, Documents and Insights.

Notes: Flyway V8 and V9 exist for both PostgreSQL and MySQL. Files are stored in the database (see `docs/deployment.md`).
