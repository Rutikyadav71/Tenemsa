# Tenemsa

**Tenemsa** is a multi-tenant, AI-augmented business operating system — HR, attendance, leave management, projects and sprints, meetings, documents, a company knowledge base, real-time chat and "Tenemsa AI" assistants — that many companies can run side by side with fully separated data.

It started as a single-company Employee Management System and has been rebuilt, phase by phase, into a multi-tenant SaaS: tenant isolation, a role and permission model, hardened authentication, an AI layer, and a complete redesigned interface.

> **Status:** Phases 0–9 are complete (the platform, security, projects, AI, knowledge base, documents, analytics and the full UI/UX redesign). The leave-prediction service is parked. See [`docs/roadmap.md`](docs/roadmap.md) for the full history and ideas for what comes next.

### What it does today

- **Workspaces and people:** company registration, invitations, per-company email, roles (Owner, Admin, HR, Manager, Employee, Intern) with fine-grained permissions, audit log.
- **Sign-in and security:** short-lived access tokens, rotating refresh tokens, email verification, password reset, Google sign-in, change password, signed-in devices, rate limiting.
- **HR:** attendance, leave requests with manager/HR decisions, notifications (in-app and email), teams, documents with visibility rules.
- **Work:** projects, issues, kanban board, backlog, sprints with carry-over, comments.
- **Tenemsa AI (off unless a key is configured):** meeting summaries to tasks, task generator, message writer, project reports, HR assistant, knowledge-base questions with saved private chats, usage and cost page, per-company switch.
- **Collaboration:** real-time chat with files, insights/analytics dashboards.
- **Interface:** one responsive shell for every role, light and dark themes, command palette, accessibility-checked.

---

## Repository structure

This is a monorepo with three independently deployable components:

```
Tenemsa/
├── backend/       Spring Boot 3 / Java 21 REST + WebSocket API
├── frontend/      React 19 + TypeScript + Vite SPA
├── platform-console/  Separate hidden admin app for the two system administrators (see docs/platform-admin.md)
├── ml-service/    Flask microservice — leave-approval prediction model
└── docs/          Architecture, API reference, schema, deployment, roadmap
```

Each has its own `README.md` with component-specific setup instructions. This file covers the project as a whole.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, PostgreSQL (or MySQL) |
| Frontend | React 19, TypeScript, Vite, Recharts, STOMP/SockJS |
| ML service | Python, Flask, scikit-learn |
| Auth | JWT access tokens, rotating refresh tokens, BCrypt, Google OAuth2 |
| AI | Pluggable provider behind one gateway, RAG on PostgreSQL/pgvector |
| Real-time | WebSocket (STOMP) for chat and live notifications |

See [`docs/architecture.md`](docs/architecture.md) for how these fit together, and [`docs/database.md`](docs/database.md) for the schema.

## Quick start (local development)

You'll run three processes: a database (PostgreSQL by default, MySQL also supported), the backend, and the frontend. The ML service is parked and not needed.

### 1. Database
Create an empty PostgreSQL database (14+; hosted free tiers work too). To use MySQL instead, set `SPRING_PROFILES_ACTIVE=mysql` in `backend/.env`. The tables are created by Flyway on first start.

### 2. Backend
```bash
cd backend
cp .env.example .env   # fill in DB credentials, generate a JWT_SECRET (see below)
./mvnw spring-boot:run
```
Generate a real secret rather than leaving `JWT_SECRET` blank:
```bash
openssl rand -base64 64
```
The backend fails to start without one — that's intentional, not a bug.

Flyway creates and upgrades the schema on startup. The old `migration-phase*.sql` files are only for databases that predate Phase 4 and can be ignored on a new database.

**Want lots of test data?** One extra command fills the database with many demo companies and everything in them — see [`docs/seed-data.md`](docs/seed-data.md):
```bash
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--tenemsa.seed.enabled=true"
```
Then sign in as `owner@demo-001.test` with password `Demo@1234`.

### 3. Frontend
```bash
cd frontend
cp .env.example .env   # defaults to http://localhost:8080, matching the backend above
npm install
npm run dev
```

### 4. ML service (parked, optional)
```bash
cd ml-service
pip install -r requirements.txt
python app.py
```
Runs on port 5000 by default, which is what the backend's leave-prediction endpoints expect.

Full details, including Docker and free-tier deployment options, are in [`docs/deployment.md`](docs/deployment.md).

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — system design, request flow, multi-tenancy model
- [`docs/api.md`](docs/api.md) — REST API reference by controller
- [`docs/database.md`](docs/database.md) — schema, relationships, migration strategy
- [`docs/deployment.md`](docs/deployment.md) — environment variables, hosting options, AI setup
- [`docs/frontend.md`](docs/frontend.md) — design system, shell, page conventions
- [`docs/rbac-design.md`](docs/rbac-design.md) — roles and permissions
- [`docs/seed-data.md`](docs/seed-data.md) — demo data for large-scale testing, accounts and checklist
- [`docs/roadmap.md`](docs/roadmap.md) — phase-by-phase plan and current progress

## License

Not yet licensed for public reuse. Add a `LICENSE` file (MIT is a common default for portfolio/open-source projects) before treating this as open source in practice.
