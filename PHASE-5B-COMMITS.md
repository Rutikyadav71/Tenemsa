# Phase 5b — Sprints, backlog, story points, labels

## What changed

### Backend
- **V4 Flyway migration** (`db/migration/*/V4__sprints.sql`):
  - Adds `sprints` table (`id`, `company_id`, `project_id`, `name`, `goal`, `status`, `start_date`, `end_date`, `started_at`, `completed_at`, `completed_count`, `carried_over_count`, `created_at`, `updated_at`).
  - Alters `issues` table to add `sprint_id`, `story_points`, `labels` (and foreign key constraint to `sprints`).
- **Entities & Enums**:
  - `Sprint`, `SprintStatus` (`PLANNED`, `ACTIVE`, `COMPLETED`).
  - `Issue` updated with `sprintId`, `storyPoints`, and comma-separated `labels`.
  - `AuditAction` extended with `SPRINT_STARTED`, `SPRINT_COMPLETED`.
- **Repository**:
  - `SprintRepository` with company/project scoped queries and batch methods.
- **DTOs**:
  - `SprintRequest`, `SprintResponse`, `SprintCompleteRequest`, `IssueSprintRequest`.
  - `IssueRequest` and `IssueResponse` updated to support `storyPoints`, `labels`, and sprint metadata.
- **Services**:
  - `SprintService`: handles full lifecycle (create, update, delete, start with single-active guard, complete with carry-over of unfinished issues to backlog or another sprint).
  - `IssueService`: normalises labels (lowercase, trimmed, max 10 labels, regex validated) and populates sprint names.
  - `ProjectService`: cascades sprint deletion on project deletion.
- **Controllers**:
  - `SprintController`: REST endpoints under `/api/projects/{projectId}/sprints` and `/api/sprints/{id}`, plus `/api/issues/{id}/sprint`.

### Frontend
- **Types**:
  - `Sprint`, `SprintInput`, `SprintStatus` in `models.ts`.
  - Updated `Issue` and `IssueInput` with `sprintId`, `sprintName`, `storyPoints`, `labels`.
- **Service layer**:
  - `projectService.ts` methods: `listSprints`, `createSprint`, `updateSprint`, `deleteSprint`, `startSprint`, `completeSprint`, `assignToSprint`.
- **Pages & Components**:
  - `ProjectBacklogPage.tsx`: backlog view and sprint planning, drag and drop, sprint lifecycle management (start/complete/edit).
  - `ProjectBoardPage.tsx`: sprint dropdown filter, story point badge, labels rendering on cards, and sprint-aware issue modal.
  - `App.tsx`: routing for `projects/:id/backlog`.

### Tests
- `SprintManagementTest.java`: company isolation, single active sprint constraint, issue assignment, carry-over behavior on completion, role-based permissions, validation rules.

## Commits in this branch (Phase 5b)
1. `feat(db): V4 migration for sprints, story points, labels`
2. `feat(model): add Sprint entity, SprintStatus and issue planning fields`
3. `feat(api): sprint DTOs and issue DTO changes (points, labels, sprint)`
4. `feat(service): SprintService lifecycle, carry-over and issue assignment; label normalisation`
5. `feat(api): SprintController endpoints`
6. `test: SprintManagementTest (isolation, lifecycle, roles, validation)`
7. `feat(ui): backlog and sprint planning page`
8. `feat(ui): sprint filter, labels and story points on the board`
9. `docs: Phase 5b API, database, architecture and roadmap`
