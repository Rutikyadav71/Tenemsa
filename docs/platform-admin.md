# Platform console (system administrators)

A separate, hidden web app for the two people who run Tenemsa itself. It shows every company as a customer (names, owner contact, counts, usage, status) and lets you freeze, unfreeze or close a company. It is **not** part of the main site and the main frontend contains none of its code.

## How it is kept apart

| Layer | What protects it |
|---|---|
| Code | Lives in `platform-console/` at the repo root. Never built or deployed with `frontend/`. Run it on your own computer. |
| Backend switch | `PLATFORM_ENABLED=false` (default) means the controllers, services and filter are not even created; every `/api/platform/**` URL answers 404. |
| Identity | Separate `platform_admins` table, separate token (`typ=platform`, signed with `PLATFORM_JWT_SECRET`). A company token gets 401 on `/api/platform/**` and a console token gets 401 on every company API. |
| Registration | Needs `PLATFORM_SETUP_KEY`. At most two accounts: the database itself refuses a third (unique slot 1 or 2 plus a CHECK constraint). After two, the register page and API are closed for good. |
| Sign-in | Password (12+ characters) plus authenticator-app code (TOTP). Replay of a used code is refused; 5 failures lock the account for 15 minutes; login and register are rate limited. |
| Network | Optional `PLATFORM_LOCALHOST_ONLY=true` accepts console requests only from the same machine. |
| Audit | Every sign-in, refusal and company action is written to `platform_audit_logs`. |

## What you can and cannot see

Visible: company name, workspace name, owner name and email, status, created date, head-counts by role, counts of projects, issues, leave requests, meetings, documents, storage, AI request and token counts, sign-in counts and last sign-in, your own action history.

Not visible, by design: chat messages, document contents, salaries, saved AI conversations, employee personal data. There is no "sign in as the company" feature.

## Setup (local)

1. Generate two secrets (for example `openssl rand -base64 48`) and add to `backend/.env`:
   ```
   PLATFORM_ENABLED=true
   PLATFORM_JWT_SECRET=<secret 1, different from JWT_SECRET>
   PLATFORM_SETUP_KEY=<secret 2>
   PLATFORM_LOCALHOST_ONLY=true
   CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174
   ```
2. Start the backend. Flyway applies `V13__platform_admin.sql`.
3. Console:
   ```
   cd platform-console
   npm install
   npm run dev          # http://localhost:5174
   ```
   `VITE_API_BASE_URL` defaults to `http://localhost:8080`.
4. Open `/register`, enter name, email, password and the setup key. Scan the QR (or type the setup key) into an authenticator app. Then sign in and confirm with the first code.
5. The second administrator repeats step 4 on their own machine or later.
6. **Remove `PLATFORM_SETUP_KEY`** from `.env` afterwards. Registration is already closed after two accounts; removing the key makes it impossible to reopen by accident.
7. When you do not need the console, set `PLATFORM_ENABLED=false` and restart.

Keep a backup of each administrator's authenticator setup key somewhere safe. There is no reset flow: with two administrators, one can still sign in if the other loses a phone, but a lost authenticator needs a database fix.

## Running it against a deployed backend

Possible, but the recommended way is still "console on your laptop, backend switch on only when needed". If you do enable it on a deployed backend: use long random secrets, keep `PLATFORM_LOCALHOST_ONLY=false` only behind HTTPS, add the console origin to `CORS_ALLOWED_ORIGINS`, and turn `PLATFORM_ENABLED` off again afterwards.

## Company status

| Status | Effect |
|---|---|
| ACTIVE | Normal. |
| FROZEN | Nobody in the company can sign in, refresh, use an API call or the chat socket. All refresh tokens are revoked at once. Data is kept. Unfreeze restores access. |
| DELETED (closed) | Same lock-out. Nothing is deleted from the database. Close needs a reason and typing the workspace name. Restore brings it back. |

Enforced by `CompanyAccessGuard` (15 s cache, evicted on change) at login, refresh, every request, Google sign-in and chat. The main login page shows the reason text to the person trying to sign in.

## Pages

Dashboard (totals, weekly growth, newest companies) · Companies (search, status filter, sort, pages, CSV export) · Company detail (owner, people, usage, actions, history) · Audit log (filter by action) · Administrators · System (database, memory, table sizes).

## API

All under `/api/platform`, console token required except `auth/*`.

`POST auth/register` · `POST auth/login` · `POST auth/confirm-2fa` · `GET auth/status` · `GET me` · `GET overview` · `GET companies` · `GET companies/{id}` · `POST companies/{id}/freeze|unfreeze|close|restore` · `PUT companies/{id}/ai` · `POST companies/{id}/resend-owner-verification` · `GET audit` · `GET admins` · `GET system`.

## Not built yet

Announcements to all companies, plans and limits, billing. Impersonation is intentionally left out.

## Tests

`PlatformAdminTest` covers the TOTP test vector, the two-admin lock, sign-in with 2FA and replay refusal, company tokens refused, and freeze → locked out → unfreeze → close → audit. It was written but could not be run in the build sandbox (no Maven); run `mvn test` locally.

## Announcements and plan limits

The console now has an **Announcements** page (notices to all or chosen companies, optional email to owners and admins) and a **Plan and limits** action on each company (plan label, most people, AI requests per day). Details in [announcements.md](announcements.md). Audit actions added: `ANNOUNCEMENT_SENT`, `ANNOUNCEMENT_WITHDRAWN`, `COMPANY_LIMITS_SET`.

API additions: `GET/POST /api/platform/announcements`, `POST /api/platform/announcements/{id}/withdraw`, `PUT /api/platform/companies/{id}/limits`.
