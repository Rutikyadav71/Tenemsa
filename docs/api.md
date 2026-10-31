# API Reference

Base URL: `http://localhost:8080` (local) — configurable via `VITE_API_BASE_URL` on the frontend side, see [`docs/deployment.md`](deployment.md).

All endpoints except `/api/auth/login`, `/api/admin/login`, `/api/companies/register`, `/api/companies/lookup`, `/api/invitations/preview`, and `/api/invitations/accept` require a `Authorization: Bearer <token>` header. **Admin-equivalent** below means `OWNER` or `SUPER_ADMIN` role (see [`docs/architecture.md`](architecture.md#role-hierarchy)).

No OpenAPI/Swagger spec exists yet (roadmap item). This is a hand-maintained reference — keep it in sync with controller changes.

---

## Audit — `/api/audit`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/audit?page=0&size=25&action=` | `audit:view` (Owner, Admin) | Newest first, caller's company only. Returns `{ content, page, size, totalElements, totalPages }`; each entry has `id, actorId, actorName, action, entityType, entityId, details, ipAddress, createdAt`. `size` is capped at 100. |
| GET | `/api/audit/actions` | `audit:view` | The list of action names for filtering. |

## Invitations — `/api/invitations`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/invitations` | `user:create` | Body `{ email, name, role, department? }`. The role must be one the caller may assign (Owner: Admin/HR/Manager/Employee/Intern; Admin: Manager; HR: Employee/Intern). `201` with the invitation; `400 {message}` for a duplicate member, bad email or admin cap; `403` for a role the caller cannot assign. Re-inviting an address revokes the earlier pending link. |
| GET | `/api/invitations` | `user:create` | Pending invitations for the caller's company. |
| DELETE | `/api/invitations/{id}` | `user:create` | Revoke a pending invitation; another company's id returns `400`. |
| GET | `/api/invitations/preview?token=` | Public | `{ companyName, companySlug, email, name, role }`; `404` if the link is invalid, used or expired. |
| POST | `/api/invitations/accept` | Public | Body `{ token, name?, password }`. Creates the account (email already verified), consumes the token and returns `201` with an `AuthResponse`. |

## Errors, validation and rate limits

- **Validation:** request bodies for sign-in, forgot/resend, company registration, invitations, employees, teams and attendance marking are validated (`@Valid`). Malformed JSON or an unknown enum value also returns `400`. A failure returns `400` with `{ message, fieldErrors }`, where `message` is the first field error ("Company name is required.").
- **Rate limits (per client IP, `429` with a `Retry-After` header and `{ message }`):** sign-in (`/api/auth/login` and `/api/admin/login` share one budget) 10 per minute; `POST /api/companies/register` 5 per hour; `GET /api/companies/lookup` 30 per minute; `forgot-password` and `resend-verification` (shared) 5 per 10 minutes; `reset-password` 10 per 10 minutes; invitation `preview` and `accept` (shared) 20 per minute.

## Companies — `/api/companies`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/companies/register` | Public | Body `{ companyName, ownerName, email, password }`. Creates the company (unique slug derived from the name) and its Owner (`primaryOwner=true`, `empId=1`), returns `201` with an `AuthResponse` (signed in, includes `companySlug`). `400 {message}` on validation, weak password or duplicate email. |
| GET | `/api/companies/lookup?slug=` | Public | `{ name, slug }` for the "find your workspace" screen; `404 {message}` if unknown. Exposes only name and slug. |
| GET | `/api/companies/me` | Any signed-in user | `{ id, name, slug, createdAt, memberCount }` for the caller's company (tenant from the JWT). |
| PUT | `/api/companies/me` | `company:manage` (Owner, Admin) | Body `{ name }`. Renames the company; the slug never changes. |

## Auth — `/api/auth`, `/api/admin`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Login for any role. Returns JWT + profile fields. |
| POST | `/api/admin/register-by-admin` | Admin | Add another admin-equivalent user. Capped at 4 per company. |
| POST | `/api/admin/login` | Public | Login restricted to admin-equivalent roles. Optional `workspace` (slug) in the body: if given and it differs from the user's company, login fails with a generic error. |
| GET | `/api/admin/all` | Admin | List all admin-equivalent users. |
| PUT | `/api/admin/{id}/freeze` | Admin (primary owner only) | Freeze another admin's account. |
| PUT | `/api/admin/{id}/unfreeze` | Admin (primary owner only) | Unfreeze. |
| DELETE | `/api/admin/{id}` | Admin (primary owner only) | Remove an admin. Cannot remove the primary owner. |

## Employees — `/api/employees`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/employees` | Admin | List all users in the caller's company. |
| GET | `/api/employees/{empId}` | Admin, or self | Get one employee by business `empId`. |
| POST | `/api/employees` | Admin | Body `{ empId?, name, email, password (6-128), role, department?, salary? }`. `empId` is optional (next free number if omitted; `400` if taken). Only these fields are read. Sends a welcome email if `app.email.enabled=true`. |
| PUT | `/api/employees/{empId}` | Admin | Body `{ name, email, password?, department?, salary? }`. A blank password keeps the current one. Role is not editable via this endpoint. |
| DELETE | `/api/employees/{empId}` | Admin | Delete one employee. |
| GET | `/api/employees/search?keyword=` | Admin | Search by name/email within the caller's company. |
| POST | `/api/employees/delete-multiple` | Admin | Bulk delete by a list of `empId`s. |

## Leave — `/api/leaves`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/leaves` | Any authenticated | Apply for leave. `empId` must be the caller's own, unless the caller holds `leave:approve:any` (Owner, HR); otherwise `403`. |
| GET | `/api/leaves` | Admin | List all leave requests. |
| GET | `/api/leaves/employee/{empId}` | Admin, or self | Leave history for one employee. |
| GET | `/api/leaves/{id}` | Admin, or self (checked post-fetch) | Get one leave request. |
| PUT | `/api/leaves/{id}` | Admin | Update status directly. |
| PUT | `/api/leaves/{id}/approve` | Admin | Approve. |
| PUT | `/api/leaves/{id}/reject` | Admin | Reject. |
| DELETE | `/api/leaves/{id}` | Admin | Delete a leave record. |
| GET | `/api/leaves/admin/export` | Admin | Export all leave data — feeds the ML training pipeline. |
| GET | `/api/leaves/admin/{id}/prediction` | Admin | Get an ML approval-likelihood prediction for one leave request (proxies to `ml-service`). |

## Attendance — `/api/attendance`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/attendance/checkin/{empId}` | Admin, or self | Self check-in for today. |
| POST | `/api/attendance/checkout/{empId}` | Admin, or self | Self check-out; computes work hours. |
| GET | `/api/attendance/today/{empId}` | Admin, or self | Today's record. |
| GET | `/api/attendance/employee/{empId}?start=&end=` | Admin, or self | Date-range history for one employee. |
| GET | `/api/attendance/date/{date}` | Admin | All employees' attendance for one date. |
| GET | `/api/attendance/range?start=&end=` | Admin | All employees' attendance across a date range. |
| POST | `/api/attendance/mark` | Admin | Body `{ empId, date, status, checkIn?, checkOut?, notes? }`. The employee must exist in the caller's company; check-out must be after check-in; work hours are computed by the server. |
| GET | `/api/attendance/summary/{empId}?month=&year=` | Admin, or self | Monthly aggregate stats (present/late/absent/hours/rate). |

## Projects and issues — `/api/projects`, `/api/issues`

Everything is scoped to the caller's company; an id from another company returns `404`. Validation errors return `400 { message }`.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/projects` | `project:view` (everyone) | Projects with `totalIssues` / `doneIssues`. |
| POST | `/api/projects` | `project:manage` (Owner, Admin, Manager) | `{ key (2-10 capitals/digits), name, description?, leadEmpId? }` → `201`. Key is unique per company and immutable. |
| GET | `/api/projects/{id}` | `project:view` | |
| PUT | `/api/projects/{id}` | `project:manage` | `{ name, description?, leadEmpId?, status? }`. `status: ARCHIVED` makes the project read-only. |
| DELETE | `/api/projects/{id}` | `project:manage` | Only archived projects; removes their issues and comments. `204`. |
| GET | `/api/projects/people` | `project:view` | `[{ empId, name, role }]` for assignee pickers (no emails). |
| GET | `/api/projects/{id}/issues?status=&assignee=&q=` | `project:view` | Filters are optional. |
| POST | `/api/projects/{id}/issues` | `issue:create` (everyone) | `{ title, description?, type?, priority?, assigneeEmpId?, dueDate? }` → `201` with `key` such as `ACME-3`. |
| GET | `/api/issues/mine` | `project:view` | Open issues assigned to the caller. |
| GET | `/api/issues/{id}` | `project:view` | |
| PUT | `/api/issues/{id}` | manager, reporter or assignee | Same body as create. `403` otherwise. |
| PATCH | `/api/issues/{id}/status` | manager, reporter or assignee | `{ status }`: moving a card on the board. |
| DELETE | `/api/issues/{id}` | manager, reporter or assignee | Also deletes its comments. |
| GET / POST | `/api/issues/{id}/comments` | `project:view` / `issue:create` | POST `{ body }` → `201`. Not allowed on archived projects. |

### Sprints and planning (Phase 5b)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/projects/{id}/sprints` | `project:view` | Each sprint carries live `totalIssues`, `doneIssues`, `totalPoints`, `donePoints`. |
| POST | `/api/projects/{id}/sprints` | `project:manage` | `{ name, goal?, startDate?, endDate? }` → `201`, status `PLANNED`. End date cannot be before start date. |
| PUT | `/api/sprints/{id}` | `project:manage` | Not allowed once `COMPLETED`. |
| POST | `/api/sprints/{id}/start` | `project:manage` | Only `PLANNED`; only one `ACTIVE` sprint per project (else `400`). Missing dates default to today and +14 days. |
| POST | `/api/sprints/{id}/complete` | `project:manage` | Optional `{ moveUnfinishedTo }` (a `PLANNED` sprint of the same project); unfinished issues go there or back to the backlog. Stores `completedCount` / `carriedOverCount`. |
| DELETE | `/api/sprints/{id}` | `project:manage` | Only `PLANNED`; its issues return to the backlog. `204`. |
| PATCH | `/api/issues/{id}/sprint` | `issue:manage:any` | `{ sprintId }`, `null` = backlog. Same project only; completed sprints are frozen in both directions. |

Issue create/update bodies also accept `storyPoints` (0-100) and `labels` (up to 10, each 1-30 chars of `a-z 0-9 space _ -`, lower-cased and de-duplicated). Issue responses add `sprintId`, `sprintName`, `storyPoints`, `labels`. New issues start in the backlog.

## Teams — `/api/teams`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/teams` | Any authenticated | List all teams. |
| GET | `/api/teams/{id}` | Any authenticated | Get one team. |
| GET | `/api/teams/employee/{empId}` | Any authenticated | Teams a given employee belongs to. |
| POST | `/api/teams` | Admin | Body `{ name, description?, projectName?, deadline?, status?, memberIds?, teamLeadId? }`. Members and lead must exist in the caller's company (`400` otherwise). |
| PUT | `/api/teams/{id}` | Admin, or the team's lead | Same body as create. |
| DELETE | `/api/teams/{id}` | Admin | Delete a team. |
| POST | `/api/teams/{id}/members/{empId}` | Admin | Add a member. |
| DELETE | `/api/teams/{id}/members/{empId}` | Admin | Remove a member. |

## Notifications — `/api/notifications`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/notifications` | Any authenticated | All notifications, newest first. Not yet company- or user-scoped — see architecture doc's known-debt section. |
| PUT | `/api/notifications/mark-all-read` | Any authenticated | Marks every notification read. |

## Uploads — `/api/upload`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/upload/profile/{empId}` | Admin, or self | Upload an employee's profile photo (Cloudinary). |
| POST | `/api/upload/admin-profile/{adminId}` | Admin | Upload an admin's profile photo. |

## Chat — `/api/chat`, `/api/chat-users`, WebSocket `/ws`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/chat-users` | Any authenticated | Company-scoped, chat-eligible users, split into `employees`/`admins`. `role` in the response is a fixed `"ADMIN"`/`"USER"` routing tag — **not** the six-tier `Role` enum (see architecture doc). |
| GET | `/api/chat/history?id1=&role1=&id2=&role2=` | Participant | Message history between two participants. `403` unless the caller is one of the two (admin-tier users are `("ADMIN", database id)`, everyone else `("USER", empId)`). |
| GET | `/api/chat/unread?...` | Receiver | Unread count; `403` unless the receiver is the caller. |
| PUT | `/api/chat/read?...` | Receiver | Mark a thread read; `403` unless the receiver is the caller. |
| DELETE | `/api/chat/message/{id}` | Sender | Soft-delete a message. `404` for anyone but the sender. |
| PUT | `/api/chat/message/{id}/react` | Participant | Toggle an emoji reaction. `404` for non-participants. |
| PUT | `/api/chat/message/{id}/star` | Participant | Toggle starred. `404` for non-participants. |
| GET | `/api/chat/starred?...` | Participant | Starred messages between two participants. |
| DELETE | `/api/chat/delete?...` | Participant | Delete an entire conversation. |
| POST | `/api/chat/attachments` | Any authenticated | Multipart `file` (pdf, doc/docx, xls/xlsx, ppt/pptx, txt, md, csv, zip, png, jpg, gif, webp, mp3, wav, mp4; 10 MB). Type comes from the extension and the first bytes must match it; HTML/SVG/scripts/executables are refused with a readable `message`. Returns `{id, fileName, contentType, sizeBytes, image}`. Send the message with `attachmentId` to deliver it. |
| GET | `/api/chat/attachments/{id}` | The two people in the chat (the uploader until it is sent) | The exact stored file. Images are `inline`, everything else `attachment` under its original name; add `?download=true` to force a download. `nosniff`, `no-store`. `404` for anyone else, other companies, or after the message is deleted. |
| WS | `/ws` (STOMP/SockJS) | JWT via connect | Real-time message delivery. A frame may carry `attachmentId`; name, type and size are copied from the stored file by the server, and an attachment can be sent once, by its uploader. Client subscribes to `/user/{ROLE}_{id}/queue/messages`; publishes to `/app/chat.send`. |

## Misc

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness check — returns `"OK"`. |

## AI and meetings (Phase 6a)

Requires `ai:use` (Owner, Admin, HR, Manager) unless noted. Everything is scoped to the caller's company.

| Method | Path | Description |
|---|---|---|
| GET | `/api/ai/status` | Whether AI is enabled, provider/model, caller's remaining quota. |
| GET | `/api/ai/usage?days=30` | Requests, failures, tokens, latency by feature and day. Needs `ai:usage:view` (Owner, Admin). |
| GET | `/api/meetings` | Summaries the caller may see (own, or all with `meeting:manage:any`). |
| POST | `/api/meetings` | Body `{title, meetingDate?, sourceRef?, notes}` (notes 30 to 20000 chars). Calls the model, saves a DRAFT. `201`. |
| GET | `/api/meetings/{id}` | One summary with decisions, action items, open questions, original notes. `404` if not visible. |
| PUT | `/api/meetings/{id}` | Edit a DRAFT (`409`/`400` once approved). |
| POST | `/api/meetings/{id}/approve` | DRAFT to APPROVED (locks it). |
| POST | `/api/meetings/{id}/create-issues` | Body `{projectId, actionItemIds}`. Needs `issue:create`. All or nothing; each item converts once. |
| DELETE | `/api/meetings/{id}` | Creator or `meeting:manage:any`. `204`. |

AI error codes: `503` AI disabled or not configured, `429` quota reached, `504` model timeout, `502` provider failure or unusable output.

## Sign-in and workspaces (Phase 6b)

Login responses (`/api/auth/login`, `/api/admin/login`, refresh, `/api/auth/me`) now include `permissions`, the role's permission strings. The UI uses them only to build menus; the server still checks every request.

- `/api/auth/login` accepts every role. `/api/admin/login` accepts Owner, Admin, Super admin and HR (`401` for Manager, Employee, Intern).
- Workspace by role: Owner, Admin, HR use `/admin`; Manager, Employee, Intern use `/user`. Either sign-in page sends the person to their own workspace.
- Chat keeps its own two-value tag (Owner/Admin: `ADMIN`, everyone else including HR: `USER`).

## AI assistants (Phase 6c)

| Method | Path | Needs | Description |
|---|---|---|---|
| POST | `/api/ai/tasks/generate` | `ai:use`, `issue:create` | Body `{projectId, goal (15-4000 chars), maxTasks (1-15)}`. Returns suggestions only; nothing is created. |
| POST | `/api/ai/tasks/create` | `ai:use`, `issue:create` | Body `{projectId, tasks[]}`. Creates the reviewed tasks, all or nothing, labelled `ai-draft`. `201`. |
| POST | `/api/ai/writer/draft` | `ai:use` | Body `{kind, audience, tone, points}`. Returns `{subject, body}`. Nothing is stored or sent. |
| POST | `/api/ai/projects/{id}/report` | `ai:use`, `project:view` | Body `{type: STATUS_UPDATE or SPRINT_RETRO, days?, sprintId?}`. Returns the report plus `basedOn`. `404` outside your company. |
| POST | `/api/ai/hr-assistant/ask` | `ai:assistant:self` | Body `{question}`. Answers from the caller's own leave and attendance only. |
| PUT | `/api/companies/me/ai` | `company:manage` | Body `{enabled}`. Switches AI off or on for the whole company. While off, every AI call answers `503`. |

`GET /api/ai/status` also returns `companyEnabled`; `GET /api/ai/usage` returns `estimatedCostUsd` (null unless `AI_PRICE_*` are set).

## Knowledge base (Phase 7)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/kb/status` | `kb:ask` | `{available, companyEnabled, vectorStore, embeddingModel, readyDocuments}`; `readyDocuments` counts only what the caller may read. |
| POST | `/api/kb/ask` | `kb:ask` | Body `{question}` (3 to 500 chars). Returns `{found, answer, sources[{n, documentId, title, snippet, score}]}`. `found=false` when nothing relevant is found or the answer cites nothing valid. |
| GET | `/api/kb/documents` | `kb:manage` | Documents of the caller's company with status, chunk count and `stale`. |
| GET | `/api/kb/documents/{id}` | `kb:manage` | `404` outside your company. |
| POST | `/api/kb/documents` | `kb:manage` | Multipart `file` (pdf, docx, txt, md, csv, log, json), optional `title`, `audience` (`ALL_STAFF` or `MANAGEMENT`). `201`; indexing continues in the background (poll status). |
| POST | `/api/kb/documents/text` | `kb:manage` | Body `{title, audience, text}`. `201`. |
| POST | `/api/kb/documents/{id}/reindex` | `kb:manage` | Re-embeds with the current model; also retries a FAILED document. |
| DELETE | `/api/kb/documents/{id}` | `kb:manage` | `204`; removes the document and its passages. |

Errors: `400` duplicate/too short/too long/unsupported file, `403` missing permission, `404` other company, `429` embedding quota, `503` AI off (server or company), `502` provider failure.

## Team leave approval and notifications (Phase 8a)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/leaves/team?status=PENDING\|APPROVED\|REJECTED\|ALL` | `leave:approve:team`, `leave:view:team` or `leave:approve:any` | Default `PENDING`. Manager: requests of people on teams they lead (never their own). Owner/Admin/HR (`leave:view:any`): whole company. Each row has `clashesWith` (others off on overlapping days), `decidedByName`, `decisionNote`, `canDecide`. |
| PUT | `/api/leaves/{id}/approve` | `leave:approve:any`, or `leave:approve:team` for someone on a team you lead | Body optional `{note}` (300 chars). Only PENDING requests; `400` if already decided. Records who decided and when; notifies the employee. A manager gets `403` on their own request. |
| PUT | `/api/leaves/{id}/reject` | same | Body `{note}` is required (3 to 300 chars): a rejection must say why. |
| GET | `/api/notifications` | any signed-in user | Only the caller's notifications (100 newest). People with `leave:approve:any` also see older company-wide ones. Fields: `id, message, type, leaveId, read, createdAt`. |
| GET | `/api/notifications/unread-count` | any | `{unread}`. |
| PUT | `/api/notifications/mark-all-read`, `/api/notifications/{id}/read` | any | Affects only the caller's own; someone else's id gives `404`. |
| GET / PUT | `/api/notifications/settings` | any | `{emailEnabled}`. In-app notifications are always on; email is on by default and can be switched off per person. |

Who is notified: a new request goes to the leads of the applicant's teams and everyone whose role approves any leave (not the applicant). A decision (or removal) goes to the applicant.

## Documents (Phase 8b)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/documents?category=` | `document:view` (every role) | Only documents the caller may read: ALL_STAFF for everyone, MANAGEMENT for roles with `kb:read:management`, PRIVATE for the employee it is for and for holders of `document:upload`. |
| GET | `/api/documents/{id}` | `document:view` | A document you may not read answers `404`, the same as one that does not exist. |
| GET | `/api/documents/{id}/download` | `document:view` | Served as an attachment with `X-Content-Type-Options: nosniff` and `Cache-Control: private, no-store`. The content type comes from the stored file type, never from the client. |
| POST | `/api/documents` | `document:upload` (Owner, HR) | Multipart `file` (pdf, docx, xlsx, txt, md, csv, png, jpg; 10 MB), `title?`, `category` (POLICY, HANDBOOK, CONTRACT, FORM, PERSONAL, OTHER), `visibility`, `ownerEmpId` (required for PRIVATE), `indexForAi` (also add to the knowledge base; needs `kb:manage`; not for PRIVATE). The file's first bytes must match its extension. `400` over the company storage limit. If AI indexing fails the upload still succeeds and `indexNote` says why. A PRIVATE document notifies its employee. |
| DELETE | `/api/documents/{id}` | `document:upload` | `204`; also removes the knowledge-base copy. |

## Analytics (Phase 8c)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/api/analytics/overview` | `report:view:company` or `report:view:team` | Owner/Admin/HR: `scope=COMPANY` with headcount, by department and role, leave (pending, off today, approved days per month for 6 months, status counts), attendance for 30 days, task counts by status and overdue. Manager: `scope=TEAM`, only people on teams they lead; `byRole` is empty and `projects` is null. Employees get `403`. No salary or payroll data. |
| GET | `/api/analytics/me` | any signed-in user | Your own leave days this year, pending requests, 30-day attendance rate, open and overdue tasks, unread notifications. |
