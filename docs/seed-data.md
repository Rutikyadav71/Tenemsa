# Demo seed data

A one-command way to fill Tenemsa with a **lot** of realistic data — many companies, each with its own people, attendance, leave, projects, meetings, documents, chat and more — so you can test every screen at scale and see where a feature breaks, slows down or looks wrong.

It lives in `backend/src/main/java/com/tenemsa/platform/seed/DemoDataSeeder.java`. It is **off by default** and never runs in normal startup or in the test suite.

> **How it was checked:** the seeder's SQL was run against a real PostgreSQL 16 database built from migrations V1–V12 (50 companies, about 4,200 users, 140,000 attendance rows, 17,000 issues loaded in about 5 seconds; re-run, reset and size options all worked). It has **not** been run through Spring Boot/Maven or against MySQL, because the build tools could not be downloaded in the environment it was written in. The SQL is plain, so MySQL should work, but run it once and tell us if anything fails.

---

## 1. Run it

From the `backend` folder. Your normal `.env` (database settings) is used as usual.

**Windows PowerShell**
```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--tenemsa.seed.enabled=true"
```

**macOS / Linux / Git Bash**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--tenemsa.seed.enabled=true"
```

The app starts, creates the data, prints the login details and **exits**. Start the app normally afterwards (`./mvnw spring-boot:run`) and log in.

Your IDE works too: add `--tenemsa.seed.enabled=true` as a program argument for `TenemsaApplication`.

### Options

Add them to the same arguments string, separated by spaces.

| Option | Default | What it does |
|---|---|---|
| `--tenemsa.seed.enabled=true` | off | Required. Without it nothing happens. |
| `--tenemsa.seed.companies=N` | 20 | How many demo companies to create (`demo-001` … `demo-N`). |
| `--tenemsa.seed.employees=N` | 30 | Base company size. Companies are mixed small to big: 0.5×, 1×, 1×, 1.5×, 3× this number in rotation (so 30 gives companies of 15, 30, 30, 45, 90 people). |
| `--tenemsa.seed.attendance-days=N` | 45 | How many days back attendance is filled (weekdays only). |
| `--tenemsa.seed.reset=true` | off | First deletes every demo company (slug starting `demo-`) and all its data, then seeds again. |
| `--tenemsa.seed.companies=0 --tenemsa.seed.reset=true` | | Only delete the demo data. |
| `--tenemsa.seed.exit=false` | true | Keep the app running after seeding instead of exiting. |
| `--tenemsa.seed.random=N` | 42 | Random seed. Same options + same number = identical data. |
| `--tenemsa.seed.allow-production=true` | off | The seeder refuses to run if the `prod` or `production` Spring profile is active. This overrides that. Don't. |

Examples:
```bash
# default: 20 companies, about 750 people
--tenemsa.seed.enabled=true

# a heavier run: 50 companies, about 4,200 people, ~140,000 attendance rows
--tenemsa.seed.enabled=true --tenemsa.seed.companies=50 --tenemsa.seed.employees=60

# start fresh
--tenemsa.seed.enabled=true --tenemsa.seed.reset=true

# remove all demo data, add nothing
--tenemsa.seed.enabled=true --tenemsa.seed.reset=true --tenemsa.seed.companies=0
```

### Safe to re-run
A company that already exists (same slug) is **skipped**, never duplicated or overwritten. Your own real companies are never touched: reset only deletes slugs that start with `demo-`. Each company is created in its own transaction, so a failure leaves no half-built company.

---

## 2. Logging in

Every demo account has the same password: **`Demo@1234`**.

Log in with the workspace slug (or just the email) as usual. In company `demo-001` (and the same pattern in every `demo-NNN`):

| Email | Role | Good for testing |
|---|---|---|
| `owner@demo-001.test` | OWNER | Everything: settings, audit log, AI usage, admin management |
| `admin@demo-001.test` | ADMIN | Technical admin screens, no payroll |
| `hr@demo-001.test` | HR | Leave decisions for everyone, attendance, people |
| `manager@demo-001.test` | MANAGER | Team leaves (own team only), projects |
| `employee@demo-001.test` | EMPLOYEE | The personal view: my attendance, my leave, my tasks |
| `intern@demo-001.test` | INTERN | Reduced permissions |

The very small first company (`demo-001`, 15 people by default) is the easiest to look at; `demo-005` (90 people) is the heavy one. Other people have emails like `asha.nair17@demo-001.test` (name plus employee number).

**Cross-tenant check:** log in as `owner@demo-001.test` and as `owner@demo-002.test`. Neither should ever see the other's people, projects, chat, documents or audit entries.

---

## 3. What gets created in each company

| Area | What is seeded | What it lets you test |
|---|---|---|
| Company | Name, slug `demo-NNN`, created 17–27 months ago. Every 7th company has the **AI switch off**. | Workspace picker, company-level AI off state (`demo-007`, `demo-014`…) |
| People | Size follows the `employees` option (at least 6). 1 owner, 1–2 admins, HR (1 per 25), managers (1 per 8), ~8% interns, the rest employees. Departments, salaries by role, ~2% **frozen** accounts, join dates spread over time. | Lists with pagination and search, role badges, frozen state, headcount analytics |
| Sessions | 1–3 signed-in devices for owners, admins and HR (Chrome, iPhone, Firefox labels). The tokens are not usable to sign in. | Settings → Security device list, sign out one device |
| Teams | One team per manager (up to ~3 + size/12) with members and a mix of ACTIVE / ON_HOLD / COMPLETED / CANCELLED, deadlines past and future. | Team screens, team-scoped leave approval |
| Attendance | Every person, every weekday for the last 45 days: PRESENT 76%, LATE, HALF_DAY, ON_LEAVE, ABSENT, with check-in/out times and hours. ~25% have not checked in yet today and today's checked-in rows have no check-out. | Attendance tables, monthly summary, attendance rate analytics, check-in/out buttons, **volume** |
| Leave | 0–4 requests per person, from 90 days ago to 30 days ahead. PENDING, APPROVED, REJECTED with decider, time and note. Several people are off on overlapping dates. | Team Leaves queue, clash warnings, rejection reasons, "off today", leave analytics |
| Notifications | Leave requested/decided (some read, some unread), welcome, issue assigned. Email preferences switched off for ~12% of people. | Bell and unread count, notification settings |
| Projects | 3 to 12 per company with keys (WEB, APP, BILL…), a lead, the last one ARCHIVED in larger companies. | Projects list, archived state |
| Sprints | 1–3 COMPLETED, 1 ACTIVE, 1 PLANNED per project, with completed and carried-over counts. | Sprint board, backlog, sprint complete |
| Issues | 20–60 per project: TASK/BUG/STORY, all four statuses, four priorities, story points, labels, assignees (some unassigned), **deliberately overdue due dates**, backlog and in-sprint issues. | Kanban board, backlog, filters, overdue counts, issue numbering |
| Comments | 1–3 comments on about 45% of issues. | Issue detail |
| Meetings | One per ~5 people: DRAFT and APPROVED, with decisions, open questions and action items (priorities, owners, due dates). | Meetings list, approve flow, convert to issues |
| AI usage | ~6 request-log rows per person over 45 days (all seven features, some FAILED, INVALID_OUTPUT, TIMEOUT, RATE_LIMITED). Companies with AI off have far fewer. | AI usage page charts and totals |
| Saved AI chats | About 1 in 4 people have one or two private chats (HR assistant / Knowledge base). The answers are clearly marked as seeded. | Chat history list, rename, delete, privacy between users |
| Knowledge base | 4–8 documents (READY, one PROCESSING, one FAILED, ALL_STAFF and MANAGEMENT audiences) with text chunks. | List states, error messages, audience filtering |
| Documents | 6–15: policies, handbooks, contracts, payslips. PRIVATE ones belong to one person. | Visibility rules (all staff / management / private), download |
| Chat | Many one-to-one conversations with several messages each, some read some unread, a few reactions and stars. | Chat list, unread counts, history, ADMIN-vs-USER routing |
| Invitations | 3–8: pending, accepted, revoked, and pending-but-expired. | Invite management and expiry |
| Audit log | ~6 entries per person over 90 days across 15 action types with IPs. | Audit page paging and filters, action list |

---

## 4. Known limits of the seeded data (so they don't look like bugs)

- **No real AI answers.** The AI screens read the seeded history and usage, but asking a *new* question needs a real AI key (see `docs/deployment.md`). Seeded chat answers say they are demo answers.
- **Knowledge-base chunks have no embeddings.** The documents show up and filter correctly, but "Ask" will not find anything in them. Upload a real file to test search.
- **Documents are tiny text files**, not real PDFs or Word files, and there are no chat attachments or profile pictures.
- **Email is not sent.** Seeded notifications are written directly; nothing goes to a mail server.
- **Attendance and leave are independent.** An approved leave does not automatically produce an ON_LEAVE attendance row, so numbers can disagree slightly.
- **Today-relative dates.** Everything is generated relative to the day you run it. Run it again with `reset=true` on another day to refresh the dates.
- **Passwords** are all `Demo@1234`. Never run this against a database holding real people.

---

## 5. A testing checklist that uses this data

1. **Scale:** open the Employees list in `demo-005` (90 people), Attendance for the whole company, the Audit log (500+ rows). Note slow pages and anything not paginated.
2. **Roles:** repeat the same screens as owner, HR, manager, employee, intern. Note buttons that appear without permission.
3. **Leave:** as `manager@demo-001.test` open Team Leaves; approve one, reject one (a reason is required). Then check the employee's notifications.
4. **Projects:** open a project board with 40+ issues; drag cards; try the backlog; complete the active sprint and see carry-over.
5. **Isolation:** sign in to two companies in two browsers; try changing ids in URLs.
6. **AI off:** sign in to `owner@demo-007.test`; every AI screen should explain that AI is switched off.
7. **Frozen users:** find a frozen person (Employees list) and try to sign in as them.
8. **Analytics:** compare dashboards across a small and a large company.
9. **Reset:** run with `reset=true` and check no `demo-` data remains and real companies are untouched.

Write down what you find; it becomes the input for the next phase.

---

## 6. How it works (for developers)

- `DemoDataSeeder` is an `ApplicationRunner` guarded by `@ConditionalOnProperty("tenemsa.seed.enabled")`.
- It uses `JdbcTemplate` batch inserts (1,000 rows per batch) with plain SQL and one precomputed BCrypt hash shared by all accounts, which is why it is fast.
- A fixed `Random(seed*1000 + companyNumber)` makes the output repeatable.
- Ids it needs later (users, projects, issues…) are read back with `SELECT` after each insert, so the code does not depend on database-specific `RETURNING`.
- Chat identity follows the real app: OWNER/ADMIN are tagged `ADMIN` and use `users.id`; everyone else is `USER` and uses `emp_id`.
- **When you add a table or a required column, add it to the seeder and to `wipe()`** so reset keeps working. `wipe()` deletes children before parents.
