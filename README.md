# Tenemsa

**Tenemsa** is a multi-tenant, AI-augmented business operating system — HR, attendance, leave management, team/project collaboration, and real-time chat, evolving toward a full SaaS platform multiple companies can run on independently.

It started as a single-company Employee Management System and is being rebuilt, phase by phase, into a production-grade multi-tenant application: proper tenant isolation, an expanded role hierarchy, hardened authentication, and — later in the roadmap — an AI assistant and document-aware knowledge base.

> **Status:** Active development. Phases 0–1 complete (see [`docs/roadmap.md`](docs/roadmap.md) for what that means and what's next).

---

## Repository structure

This is a monorepo with three independently deployable components:

```
Tenemsa/
├── backend/       Spring Boot 3 / Java 21 REST + WebSocket API
├── frontend/      React 19 + TypeScript + Vite SPA
├── ml-service/    Flask microservice — leave-approval prediction model
└── docs/          Architecture, API reference, schema, deployment, roadmap
```

Each has its own `README.md` with component-specific setup instructions. This file covers the project as a whole.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL |
| Frontend | React 19, TypeScript, Vite, Recharts, STOMP/SockJS |
| ML service | Python, Flask, scikit-learn |
| Auth | JWT (access tokens), BCrypt password hashing |
| Real-time | WebSocket (STOMP) for chat and live notifications |

See [`docs/architecture.md`](docs/architecture.md) for how these fit together, and [`docs/database.md`](docs/database.md) for the schema.

## Quick start (local development)

You'll run three processes: PostgreSQL, the backend, and the frontend. The ML service is optional — leave-prediction features degrade gracefully without it.

### 1. Database
Create a local PostgreSQL database. Any Postgres 14+ works; free-tier hosted Postgres (Supabase, Neon, Railway) works too if you'd rather not run it locally.

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

If this is a fresh database, run [`backend/migration-phase1.sql`](backend/migration-phase1.sql) after the first startup (it needs the tables Hibernate creates on boot). Read the comments at the top of that file before running it — the order matters.

### 3. Frontend
```bash
cd frontend
cp .env.example .env   # defaults to http://localhost:8080, matching the backend above
npm install
npm run dev
```

### 4. ML service (optional)
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
- [`docs/deployment.md`](docs/deployment.md) — environment variables, Docker, hosting options
- [`docs/roadmap.md`](docs/roadmap.md) — phase-by-phase plan and current progress

## License

Not yet licensed for public reuse. Add a `LICENSE` file (MIT is a common default for portfolio/open-source projects) before treating this as open source in practice.
