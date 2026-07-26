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
| `email` | varchar(255), unique globally | Not yet scoped per-company — see architecture doc's known-debt note on why. |
| `password` | varchar | BCrypt hash. Never serialized in API responses (`@JsonProperty(WRITE_ONLY)`). |
| `role` | varchar(20) | Enum stored as string: `SUPER_ADMIN`, `OWNER`, `HR`, `MANAGER`, `EMPLOYEE`, `INTERN`. |
| `department` | varchar(100), nullable | |
| `salary` | numeric(12,2), nullable | `BigDecimal` — deliberately not a primitive number type, for currency precision. |
| `profile_image_url` | varchar(500), nullable | |
| `frozen` | boolean | Frozen accounts can't log in. |
| `primary_owner` | boolean | The founding Owner of a company; can't be frozen/deleted by other admins. |
| `created_at`, `updated_at` | timestamp | Auto-populated (`@CreationTimestamp`/`@UpdateTimestamp`). |
| `created_by`, `updated_by` | bigint, nullable | Columns exist; not yet populated anywhere (needs a "who's calling" context threaded through every write path — Phase 3). |

### `attendance`
| Column | Type | Notes |
|---|---|---|
| `id` | bigint, PK | |
| `emp_id` | integer | Business key, see note above. |
| `date` | date | |
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

There's no Flyway/Liquibase yet — schema changes go through `ddl-auto=update` plus a hand-written, manually-run SQL script for anything that isn't a safe additive change. `backend/migration-phase1.sql` is the template for how this project handles that: back up first, deploy code so Hibernate creates new empty tables, run the migration script to move/transform data, verify row counts, smoke-test, *then* optionally drop old tables (as a separate, deliberately-not-automatic step).

This works for a small team moving through phases sequentially, but it doesn't scale to multiple contributors or CI/CD without becoming risky. **Introducing Flyway (or Liquibase) is a recommended near-term addition**, likely alongside whichever phase adds automated testing/CI — versioned, repeatable migrations are worth the setup cost before this gets much further from a solo project.

## Indexes

Deliberately added where query patterns actually need them, not everywhere:
- `users.company_id` — every tenant-scoped query filters on this.
- `users.email` — the login lookup path.

No indexes yet on `attendance.emp_id`/`date`, `leaves.emp_id`, or `chat_messages.sender_id`/`receiver_id`, despite these being frequently queried — fine at current data volumes, worth revisiting if attendance/chat history queries start showing up slow in practice.
