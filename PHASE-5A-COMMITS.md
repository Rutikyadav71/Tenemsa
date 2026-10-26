# Phase 5a — Project management (projects, issues, kanban board, comments)

## What changed

### Backend
- **V3 Flyway migration** (`db/migration/*/V3__projects_and_issues.sql`): creates `projects`, `issues`, `issue_comments` tables for PostgreSQL and MySQL.
- **Entities**: `Project`, `Issue`, `IssueComment`, `IssuePriority`, `IssueStatus`, `IssueType`, `ProjectStatus` (all carry `company_id`).
- **Repositories**: `ProjectRepository`, `IssueRepository`, `IssueCommentRepository` (all company-scoped queries).
- **DTOs**: `ProjectRequest`, `ProjectUpdateRequest`, `ProjectResponse`, `IssueRequest`, `IssueResponse`, `IssueStatusRequest`, `CommentRequest`, `CommentResponse`, `PersonResponse`.
- **Exception**: `ResourceNotFoundException` → maps to HTTP 404.
- **Services**: `ProjectService` (create/get/list/update/archive/delete), `IssueService` (CRUD, per-project numbering with row lock, per-record edit rules, comments), `EmployeeNameLookup` (shared name resolution).
- **Controllers**: `ProjectController`, `IssueController`.
- **Permissions**: `project:view` (everyone), `project:manage` (Owner/Admin/Manager), `issue:create` (everyone), `issue:manage:any` (Owner/Admin/Manager); everyone else can edit only issues they reported or are assigned to.
- **GlobalExceptionHandler** extended: `ResourceNotFoundException` → 404, `MethodArgumentTypeMismatchException` → 400.
- **CORS**: `PATCH` added to allowed methods.
- **`AuditAction`**: `PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_DELETED`, `ISSUE_DELETED`.

### Invitation email fix
- `EmailService` now catches both `MessagingException` and Spring's unchecked `MailException`; failed delivery no longer rolls back the saved invitation.
- `InvitationResponse` and `InvitationController` expose `emailSent` flag.
- `InvitationsPage.tsx` shows a warning when `emailSent === false`.

### Frontend
- `ProjectsPage.tsx`: list all projects, create new project (managers only), shows progress bar.
- `ProjectBoardPage.tsx`: kanban board (drag-and-drop columns), issue detail modal with comments.
- `App.tsx`, `AdminSidebar.tsx`, `UserSidebar.tsx`: project routes and nav entries.
- `models.ts`: `Project`, `Issue`, `IssueComment`, `Person`, `IssueInput` types.
- `projectService.ts`: all project/issue/comment API calls.

### Tests
- `InvitationEmailFailureTest.java`: verifies that an SMTP failure returns `emailSent: false` but still 201.
- `ProjectManagementTest.java`: company isolation, per-project numbering, validation, role rules, archive/delete rules.

## Commits in this branch (Phase 5a)
1. `fix(invitations): a failed email no longer rolls back the invitation`
2. `feat(invitations): warn in the UI when the email could not be sent; add test`
3. `feat(db): V3 migration for projects, issues, issue_comments (PostgreSQL + MySQL)`
4. `feat(projects): Project, Issue, IssueComment entities, enums and company-scoped repositories`
5. `feat(projects): request/response records with validation; ResourceNotFoundException`
6. `feat(projects): ProjectService and IssueService (per-project numbering with row lock, per-record edit rules, archive/delete rules)`
7. `feat(projects): REST controllers, project:*/issue:* permissions per role, 404 and bad-parameter handlers, CORS PATCH`
8. `feat(projects): projects list, kanban board with drag and drop, issue detail with comments (admin and employee layouts)`
9. `test(projects): company isolation, numbering, validation, role rules, archive/delete`
10. `docs: Phase 5a (API, schema, architecture, roadmap)`
