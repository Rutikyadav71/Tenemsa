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
| POST | `/api/upload/chat` | Any authenticated | Upload a file/image attachment for chat. |

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
| WS | `/ws` (STOMP/SockJS) | JWT via connect | Real-time message delivery. Client subscribes to `/user/{ROLE}_{id}/queue/messages`; publishes to `/app/chat.send`. |

## Misc

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness check — returns `"OK"`. |
