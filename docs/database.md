# Database

PostgreSQL. Schema is Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update`) — there's no Flyway/Liquibase yet (see [Migration strategy](#migration-strategy) below for why that matters).

## Entity-relationship overview

```
companies ──┬─< users
            │      └─< (attendance, leaves, teams, notifications, chat_messages —
            │           NOT yet linked by company_id; see note below)
            │
users ──────┼─< attendance        (by emp_id, business key — not a FK)
            ├─< leaves            (by emp_id, business key — not a FK)
            ├─< team_members      (by emp_id, business key — not a FK)
            └─< chat_messages     (by sender_id/receiver_id — not FKs)
```

**Important:** `attendance.emp_id`, `leaves.emp_id`, `team_members.emp_id`, and `chat_messages.sender_id`/`receiver_id` are **not** real foreign keys to `users` — they're plain integer columns matched against `users.emp_id` at the application layer. This was already true before the multi-tenant migration and hasn't been changed. It means referential integrity for these relationships is enforced by application code, not the database — worth knowing if you're debugging orphaned records.

## Tables

### `companies`
| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `name` | varchar(150) | |
| `slug` | varchar(100), unique | Bootstrap company uses `"default"` — see architecture doc. |
| `created_at` | timestamp | |

### `users`
The unified table replacing the old separate `employees`/`admins` tables (Phase 1). See `migration-phase1.sql` for how existing data was moved over.

| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `company_id` | bigint, FK → companies, indexed | |
| `emp_id` | integer, nullable | Business-facing employee number. Unique **per company**, not globally — `UNIQUE(company_id, emp_id)`. |
| `name` | varchar(150) | |
| `email` | varchar(255) | Unique **per company**, `UNIQUE(company_id, email)` (Phase 4c). The same address can belong to several workspaces. |
| `password` | varchar | BCrypt hash. Never serialized in API responses (`@JsonProperty(WRITE_ONLY)`). |
| `role` | varchar(20) | Enum stored as string: `SUPER_ADMIN`, `OWNER`, `HR`, `MANAGER`, `EMPLOYEE`, `INTERN`. |
| `department` | varchar(100), nullable | |
| `salary` | numeric(12,2), nullable | `BigDecimal` — deliberately not a primitive number type, for currency precision. |
| `profile_image_url` | varchar(500), nullable | |
| `frozen` | boolean | Frozen accounts can't log in. |
| `primary_owner` | boolean | The founding Owner of a company; can't be frozen/deleted by other admins. |
| `created_at`, `updated_at` | timestamp | Auto-populated (`@CreationTimestamp`/`@UpdateTimestamp`). |
| `created_by`, `updated_by` | bigint, nullable | Columns exist; not yet populated anywhere (needs a "who's calling" context threaded through every write path — Phase 3). |

### `audit_logs`

Append-only trail of who did what in a company (Phase 4c). Columns: `id`, `company_id` (indexed with `created_at` and with `action`), `actor_id` (not a foreign key, so history survives deleting the user), `actor_name`, `action` (plain text, see `AuditAction`), `entity_type`, `entity_id`, `details` (≤500), `ip_address`, `created_at`. The application only inserts; it never updates or deletes rows.

### `invitations`

Email invitations to join a company (Phase 4c). Columns: `id`, `company_id`, `email`, `name`, `role` (text), `department`, `token_hash` (unique SHA-256; the raw token only exists in the email), `status` (`PENDING`/`ACCEPTED`/`REVOKED`), `invited_by_id`, `expires_at` (7 days), `accepted_at`, `created_at`.

### `attendance`
| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `company_id` | bigint, FK → companies, NOT NULL | Tenant. Added in Phase 4. |
| `emp_id` | integer | Business key, see note above. |
| `date` | date | Unique together with `company_id` + `emp_id`. |
| `check_in`, `check_out` | time, nullable | |
| `status` | varchar | `PRESENT`, `LATE`, `ABSENT`, `HALF_DAY`, `ON_LEAVE`. |
| `work_hours` | double, nullable | Computed at checkout. |
| `notes` | text, nullable | |
| `marked_by` | varchar, nullable | `"SELF"` for self check-in/out, or the marking admin's email for manual corrections. |

### `leaves`
| Column | Type | Notes |
|---|---|---|
| `id` | int, PK | |
| `emp_id` | int | Business key. |
| `start_date`, `end_date` | date | |
| `reason` | text, nullable | |
| `status` | varchar | `PENDING`, `APPROVED`, `REJECTED`. |

**Known gap:** there is no `leave_type` column, but `ml-service`'s `/predict-leave` endpoint requires a `leave_type` input feature. `/api/leaves/admin/{id}/prediction` is very likely non-functional as a result — this predates the current phase of work and hasn't been fixed yet. Worth confirming and either adding the column or adjusting the ML service before relying on that feature.

### `projects`, `issues`, `issue_comments` (Phase 5a)

| Table | Key columns |
|---|---|
| `projects` | `company_id`, `project_key` (unique per company, immutable), `name`, `description`, `status` (`ACTIVE`/`ARCHIVED`), `lead_emp_id`, `next_issue_number` |
| `issues` | `company_id`, `project_id` (FK), `issue_number` (unique per project), `title`, `description`, `type` (`TASK`/`BUG`/`STORY`), `status` (`TODO`/`IN_PROGRESS`/`IN_REVIEW`/`DONE`), `priority` (`LOW`..`URGENT`), `assignee_emp_id`, `reporter_emp_id`, `due_date` |
| `issue_comments` | `company_id`, `issue_id` (FK), `author_emp_id`, `author_name` (copied at write time), `body` |

Created by `db/migration/*/V3__projects_and_issues.sql`.

### `teams` / `team_members`
| Column | Type | Notes |
|---|---|---|
| `teams.id` | bigint, PK | |
| `teams.name`, `description`, `project_name` | varchar/text | |
| `teams.status` | varchar | `ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED`. |
| `teams.deadline`, `created_date` | date | |
| `teams.team_lead_id` | integer, nullable | Business key. |
| `team_members.team_id` | bigint, FK → teams | Real FK (this one's an `@ElementCollection` table). |
| `team_members.emp_id` | integer | Business key, not FK'd to `users`. |

### `notifications`
| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `message` | text | |
| `created_at` | timestamp | |
| `read` | boolean | |

Not scoped to a company or user yet — every notification is currently visible to every authenticated user who calls `GET /api/notifications`. The frontend filters client-side by matching text patterns in the message (e.g. `Emp ID: {empId}`), which is fragile and not a real access boundary.

### `chat_messages`
| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `sender_id`, `receiver_id` | integer | Business keys, not FK'd. |
| `sender_role`, `receiver_role` | varchar(10) | **Not** the `Role` enum — a fixed `"ADMIN"`/`"USER"` routing tag. See architecture doc. |
| `message` | text | |
| `timestamp` | timestamp | |
| `status` | varchar(20) | `SENT`, `DELIVERED`, `READ`, `DELETED`. |
| `reaction` | varchar(10), nullable | Single emoji. |
| `starred` | boolean | |

## Migration strategy

**Flyway (Phase 4e).** The schema is now defined by versioned SQL files, one folder per database:

```
backend/src/main/resources/db/migration/postgresql/V1__baseline_schema.sql
backend/src/main/resources/db/migration/postgresql/V2__drop_global_email_unique.sql
backend/src/main/resources/db/migration/mysql/V1__baseline_schema.sql
backend/src/main/resources/db/migration/mysql/V2__drop_global_email_unique.sql
```

Flyway runs at startup, before Hibernate, and records what it applied in the `flyway_schema_history` table.

- **Fresh database:** V1 creates every table, then V2 runs (a no-op).
- **Existing database** (created earlier by `ddl-auto=update`): Flyway sees tables but no history, so it *baselines* at version 1 (V1 is not run, nothing is re-created) and runs V2, which drops the old global `uk_users_email` constraint if it is still there. This replaces the manual `migration-phase4c.sql` step.
- **From now on** every schema change is a NEW file (`V3__...sql`, `V4__...sql`) for both databases. Never edit a file that has already been applied; Flyway checks a checksum and refuses to start if it changed.
- **Switch Hibernate to validate-only** once every environment has started with Flyway: set `DDL_AUTO=validate`. Hibernate then only checks that the entities match the schema and never alters it. It defaults to `update` so the first rollout cannot fail on a small type difference; if `validate` reports a mismatch, fix it with a new migration.
- **Back up first** on any real database before the first Flyway start.
- The `migration-phase1/4/4c.sql` scripts are kept as history and are not needed for new setups.

The tests use an in-memory H2 database built from the entities, so Flyway is switched off in the `test` profile.

## Phase 4: tenant isolation

`attendance`, `leaves`, `teams`, `notifications` and `chat_messages` each gained `company_id BIGINT NOT NULL` (FK to `companies`, indexed). `attendance`'s unique key changed from `(emp_id, date)` to `(company_id, emp_id, date)`. **Run `backend/migration-phase4.sql` before starting the Phase 4 backend against an existing database** — Hibernate's `ddl-auto=update` cannot add a NOT NULL column to a populated table. The script backfills existing rows into the single pre-existing company, refuses to run if more than one company exists, and runs in one transaction.

## Phase 4c: per-company email

`users.email` is now unique per company. `ddl-auto=update` adds `uk_users_company_email` but never drops the old global `uk_users_email`, so an **existing** database must run the one-line statement in `backend/migration-phase4c.sql` (MySQL and PostgreSQL variants included). A fresh database needs nothing. `audit_logs` and `invitations` are created automatically.

## Indexes

Deliberately added where query patterns actually need them, not everywhere:
- `users.company_id` — every tenant-scoped query filters on this.
- `users.email` — the login lookup path.

Each tenant table also has an index on `company_id` (Phase 4). Still no composite indexes on `attendance.emp_id`/`date`, `leaves.emp_id`, or `chat_messages.sender_id`/`receiver_id`, despite these being frequently queried — fine at current data volumes, worth revisiting if attendance/chat history queries start showing up slow in practice.

## AI and meetings (Flyway V5)

- `ai_request_logs`: one row per model call (company, employee, feature, prompt version, provider, model, status, latency, token counts, error code). Never stores prompt text or answers. Indexed by (company_id, created_at) and (company_id, emp_id, created_at); used for quotas and usage reports.
- `meeting_summaries`: title, date, source text, summary, status (DRAFT/APPROVED), model, prompt version, link to the log row, approver.
- `meeting_points`: decisions and open questions (`content`, `sort_order`).
- `meeting_action_items`: title, description, owner (name plus matched employee id), due date, priority, `issue_id`/`issue_key` once converted.

## Company AI switch (Flyway V6)

`companies.ai_enabled BOOLEAN NOT NULL DEFAULT TRUE`. Checked on every AI call by `AiGateway`.

## Knowledge base (Flyway V7)

- `kb_documents`: company, title, file name, `audience` (ALL_STAFF/MANAGEMENT), `status` (PROCESSING/READY/FAILED), error message, `content_hash` (sha256, duplicate check per company), char and chunk counts, `embedding_model`, uploader.
- `kb_chunks`: company, document, `chunk_index`, `content`, `embedding_json` (portable vector as JSON text), `embedding_model`.
- PostgreSQL only: if `CREATE EXTENSION vector` succeeds, V7 adds `kb_chunks.embedding vector(768)` and an HNSW cosine index. If it fails (not installed or no permission) the migration still succeeds and search uses the scan fallback.
- Every query filters by `company_id` first; audience and READY status are part of the search SQL.

## Leave decisions and personal notifications (Flyway V8)

- `leaves`: `decided_by_emp_id`, `decided_at`, `decision_note` (set by approve/reject).
- `notifications`: `recipient_emp_id` (NULL = older company-wide row), `type`; index `(company_id, recipient_emp_id, created_at)`.
- `notification_preferences`: `(company_id, emp_id)` unique, `email_enabled` (default true).

## Documents (Flyway V9)

- `documents`: company, title, `category`, `visibility`, `owner_emp_id` (PRIVATE only), file name, content type, size, uploader, `kb_document_id` (knowledge-base copy), created time.
- `document_files`: `document_id` primary key, `content` (BYTEA on PostgreSQL, LONGBLOB on MySQL). Kept apart from the metadata so lists never load file contents.
- Files live in the database for now (up to 200 MB per company, `DOCUMENTS_MAX_COMPANY_BYTES`). Moving to object storage later only changes `DocumentService`.

## Chat attachments (Flyway V10)

- `chat_messages` gains `attachment_id`, `attachment_name`, `attachment_type`, `attachment_size` (copied from the stored file by the server).
- `chat_attachments`: company, uploader (`uploader_tag` + `uploader_id`), file name, content type, size, `message_id` (set when sent; an attachment is sent once).
- `chat_attachment_files`: `attachment_id` primary key, `content` (BYTEA / LONGBLOB). Deleting a message or a conversation deletes its files.
