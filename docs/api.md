# API Reference

Base URL: `http://localhost:8080` (local) — configurable via `VITE_API_BASE_URL` on the frontend side, see [`docs/deployment.md`](deployment.md).

All endpoints except `/api/auth/login`, `/api/admin/login`, `/api/admin/register`, and `/api/admin/exists` require a `Authorization: Bearer <token>` header. **Admin-equivalent** below means `OWNER` or `SUPER_ADMIN` role (see [`docs/architecture.md`](architecture.md#role-hierarchy)).

No OpenAPI/Swagger spec exists yet (roadmap item). This is a hand-maintained reference — keep it in sync with controller changes.

---

## Auth — `/api/auth`, `/api/admin`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Login for any role. Returns JWT + profile fields. |
| POST | `/api/admin/register` | Public | Bootstrap the first Owner account. Only works once (per company) — returns 403 if an admin already exists. |
| POST | `/api/admin/register-by-admin` | Admin | Add another admin-equivalent user. Capped at 4 per company. |
| POST | `/api/admin/login` | Public | Login restricted to admin-equivalent roles. |
| GET | `/api/admin/exists` | Public | `{ exists, full, firstTime }` — used by the frontend to decide whether to show the bootstrap registration form. |
| GET | `/api/admin/all` | Admin | List all admin-equivalent users. |
| PUT | `/api/admin/{id}/freeze` | Admin (primary owner only) | Freeze another admin's account. |
| PUT | `/api/admin/{id}/unfreeze` | Admin (primary owner only) | Unfreeze. |
| DELETE | `/api/admin/{id}` | Admin (primary owner only) | Remove an admin. Cannot remove the primary owner. |

## Employees — `/api/employees`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/employees` | Admin | List all users in the caller's company. |
| GET | `/api/employees/{empId}` | Admin, or self | Get one employee by business `empId`. |
| POST | `/api/employees` | Admin | Create an employee. Sends a welcome email with credentials if `app.email.enabled=true`. |
| PUT | `/api/employees/{empId}` | Admin | Update name/department/email/salary/password. Role is not editable via this endpoint. |
| DELETE | `/api/employees/{empId}` | Admin | Delete one employee. |
| GET | `/api/employees/search?keyword=` | Admin | Search by name/email within the caller's company. |
| POST | `/api/employees/delete-multiple` | Admin | Bulk delete by a list of `empId`s. |

## Leave — `/api/leaves`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/leaves` | Any authenticated | Apply for leave (self). |
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
| POST | `/api/attendance/mark` | Admin | Manually mark/correct an employee's attendance. |
| GET | `/api/attendance/summary/{empId}?month=&year=` | Admin, or self | Monthly aggregate stats (present/late/absent/hours/rate). |

## Teams — `/api/teams`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/teams` | Any authenticated | List all teams. |
| GET | `/api/teams/{id}` | Any authenticated | Get one team. |
| GET | `/api/teams/employee/{empId}` | Any authenticated | Teams a given employee belongs to. |
| POST | `/api/teams` | Admin | Create a team. |
| PUT | `/api/teams/{id}` | Admin | Update a team. |
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
| GET | `/api/chat/history?id1=&role1=&id2=&role2=` | Any authenticated | Message history between two participants. |
| GET | `/api/chat/unread?...` | Any authenticated | Unread count between two participants. |
| PUT | `/api/chat/read?...` | Any authenticated | Mark a thread read. |
| DELETE | `/api/chat/message/{id}` | Any authenticated ⚠️ | Soft-delete a message. No ownership check yet — see known debt. |
| PUT | `/api/chat/message/{id}/react` | Any authenticated ⚠️ | Toggle an emoji reaction. Same ownership gap. |
| PUT | `/api/chat/message/{id}/star` | Any authenticated ⚠️ | Toggle starred. Same gap. |
| GET | `/api/chat/starred?...` | Any authenticated | Starred messages between two participants. |
| DELETE | `/api/chat/delete?...` | Any authenticated | Delete an entire conversation. |
| WS | `/ws` (STOMP/SockJS) | JWT via connect | Real-time message delivery. Client subscribes to `/user/{ROLE}_{id}/queue/messages`; publishes to `/app/chat.send`. |

## Misc

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | Public | Liveness check — returns `"OK"`. |
