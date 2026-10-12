# TPM — Task & Project Management (mini Jira / Trello)

[![CI](https://github.com/ZIDAN44/tpm/actions/workflows/ci.yml/badge.svg)](https://github.com/ZIDAN44/tpm/actions/workflows/ci.yml)
![Java 27](https://img.shields.io/badge/Java-27-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f)
![React 19](https://img.shields.io/badge/React-19-61dafb)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791)

A real-time Kanban project-management app: projects, boards with drag & drop, task assignment, comments,
deadlines, e-mail + in-app notifications, an audit log, and **live board updates over WebSockets**.

**🔗 Live demo:** https://YOUR-APP.vercel.app  ·  **📘 API docs (Swagger):** https://YOUR-APP.vercel.app/swagger-ui

> **Log in instantly** — the login page has one-click demo accounts (password `Demo@1234` for all):
>
> | Account | Role | What to try |
> |---|---|---|
> | `alice@demo.tpm` | Owner of *Website Relaunch* | Everything, incl. members & columns |
> | `bob@demo.tpm` | Member | Drag cards, comment, edit tasks |
> | `carol@demo.tpm` | Project admin | Manage columns / WIP limits / members |
> | `victor@demo.tpm` | Viewer | Read-only — the API returns 403 on writes |
> | `admin@demo.tpm` | System admin | Admin dashboard, user management |
>
> 💡 Open two browsers (e.g. Alice in one, Bob in an incognito window) and move a card — it moves on the other screen instantly, and you'll see who is viewing the board.
> The free backend sleeps when idle, so the very first request can take ~30 s (the login page tells you when it's waking up).

| Board (light) | Board (dark) |
|---|---|
| ![Board](docs/screenshots/board-light.png) | ![Board dark](docs/screenshots/board-dark.png) |

| Task details, comments & history | List view: server-side search, sort, pagination |
|---|---|
| ![Task](docs/screenshots/task-modal.png) | ![List](docs/screenshots/list.png) |

| Login with demo accounts | Dashboard | Audit log | Mobile |
|---|---|---|---|
| ![Login](docs/screenshots/login.png) | ![Dashboard](docs/screenshots/dashboard.png) | ![Activity](docs/screenshots/activity.png) | ![Mobile](docs/screenshots/mobile-board.png) |

---

## Features

**Product**
- Projects with per-project roles: **Owner / Admin / Member / Viewer**, plus a global **System Admin**
- Kanban board: drag & drop within and across columns (mouse, touch and **keyboard** accessible), configurable columns, **WIP limits**, "done" columns
- Tasks: human-friendly keys (`WEB-42`), priority, assignee, due date, description, comments, per-task history
- **Live collaboration**: board changes, comment counts and "who's viewing" presence are pushed over STOMP/WebSocket
- Notifications: in-app inbox with live toasts + **HTML e-mails** (assignment, new comment, due soon / overdue)
- Deadline reminders job, list view with **server-side search / filters / sorting / pagination**, project activity feed
- Light/dark theme, responsive layout, one-click demo login, nightly demo reset

**Engineering**
- REST API with **OpenAPI/Swagger UI**, DTOs + **Bean Validation**, **RFC 9457 Problem Details** for every error
- **JWT** access tokens (Spring Security OAuth2 resource server) + **rotating refresh tokens** with reuse detection
- **Role-based access control** at two levels (global roles + project roles, enforced in `@PreAuthorize` and services)
- **Audit log** written in the same transaction as each change
- **Caching** (Spring Cache: Caffeine by default, Redis with one env var) with after-commit eviction
- **Optimistic locking** (`@Version`) → `409 Conflict` instead of lost updates
- **Rate limiting** (token bucket) on login/registration
- Flyway migrations, PostgreSQL trigram index for search, Testcontainers integration tests, GitHub Actions CI, Docker

## Architecture

```mermaid
flowchart LR
    subgraph Browser
        UI[React 19 SPA<br/>TanStack Query · dnd-kit]
    end
    subgraph Vercel
        Static[Static assets]
        RW[Rewrites /api/*]
    end
    subgraph "Render (Docker, Java 27)"
        API[Spring Boot 4.1<br/>REST + Security]
        WS[STOMP broker<br/>/ws]
        Jobs[Scheduled jobs<br/>reminders · demo reset]
        Cache[(Caffeine / Redis)]
    end
    DB[(PostgreSQL<br/>Neon)]
    SMTP[[SMTP<br/>Brevo/Resend/Mailpit]]

    UI -- HTTPS --> Static
    UI -- "fetch /api (same-origin, httpOnly refresh cookie)" --> RW --> API
    UI <-. "WebSocket (JWT in CONNECT frame)" .-> WS
    API --> DB
    API --> Cache
    API -- "after commit" --> WS
    API -- "after commit, async" --> SMTP
    Jobs --> DB
```

**Request → event flow for a card move**

```mermaid
sequenceDiagram
    participant A as Alice (browser)
    participant API as TaskService
    participant DB as PostgreSQL
    participant B as Bob (browser)
    A->>A: optimistic re-render (client computes temporary key)
    A->>API: POST /api/tasks/7/move {columnId, afterTaskId}
    API->>DB: SELECT ... FOR UPDATE (target column row)
    API->>DB: read anchor key + successor key
    API->>DB: UPDATE tasks SET column, position = between(a, b)
    API->>DB: INSERT activity_logs (same transaction)
    API-->>A: 200 TaskSummary (authoritative key)
    Note over API: transaction commits
    API--)B: STOMP /topic/projects/1 TASK_MOVED
    B->>B: upsert card into cached board
```

## Key design decisions (good interview talking points)

| Problem | Solution | Where |
|---|---|---|
| Re-ordering cards without rewriting the whole column | **Fractional indexing** (LexoRank-style base-62 keys). Moving a card = 1 row update. Keys sort with `COLLATE "C"` so DB order = byte order. Rebalance if keys ever get long. | `FractionalIndex.java`, `V1__init.sql` |
| Two people dropping cards into the same slot | Lock the **target column row** (`SELECT … FOR UPDATE`), then compute the key from the anchor and its *current* successor. Server is authoritative; the client only sends `afterTaskId`. Proven by a 20-thread concurrency test. | `TaskService.move`, `BoardIntegrationTest` |
| Two people editing the same task | **Optimistic locking**: client sends the `version` it loaded; mismatch → `409` with a "Reload" action in the UI, plus a live "someone else updated this" banner. | `TaskService.update`, `TaskModal.tsx` |
| Never notify about changes that later roll back | Domain events published inside the transaction, delivered with `@TransactionalEventListener(AFTER_COMMIT)`; e-mail on virtual threads via `@Async`. | `RealtimePublisher`, `NotificationService` |
| Securing WebSockets | Browsers can't set headers on the WS handshake → JWT in the STOMP `CONNECT` frame, validated by the same `JwtDecoder`; every `SUBSCRIBE` checked against project membership. | `StompAuthInterceptor` |
| Stolen refresh tokens | Refresh tokens are random, stored **hashed (SHA-256)**, rotated on every use; replaying a rotated token revokes the whole token family (OAuth 2.0 Security BCP). A 10 s grace window avoids false alarms when two tabs refresh at once. | `AuthService.refresh` |
| Where to keep tokens in the browser | Access token **in memory only**; refresh token in an **httpOnly, SameSite, path-scoped cookie**. Vercel rewrites make `/api` same-origin so the cookie is first-party. | `api.ts`, `vercel.json` |
| Hot permission checks | "role of user U in project P" is cached (Caffeine/Redis) and **evicted after commit** to avoid re-caching stale data. | `MemberRoleCache` |
| Brute-force login | Per-IP **token bucket** (burst 10, refill 10/min) in a bounded Caffeine map; `429` + `Retry-After`. | `TokenBucket`, `AuthRateLimitFilter` |
| Deadline reminders on multiple instances | `SELECT … FOR UPDATE SKIP LOCKED` + `reminder_sent_at` → idempotent, no double e-mails, no blocking. | `DeadlineReminderJob` |
| N+1 queries | Board = 4 queries total (project, columns, tasks+assignees via fetch join, grouped comment counts). Dashboard counts via grouped queries. `open-in-view` disabled. | `ProjectService.board` |
| Search | `lower(title) LIKE %q%` backed by a **pg_trgm GIN index**; sort whitelist prevents sorting on arbitrary properties. | `TaskSpecifications`, migration |
| User enumeration / timing | Same error for unknown e-mail and wrong password, and a dummy bcrypt check for unknown e-mails. | `AuthService.login` |

Modern Java used: records for DTOs/config, pattern-matching `switch` (incl. `case null`), unnamed variables (`_`),
text blocks, `List.getFirst()`, and **virtual threads** (`spring.threads.virtual.enabled=true`).

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 27, Spring Boot 4.1 (Web MVC, Security, OAuth2 Resource Server, Data JPA / Hibernate 7, Validation, WebSocket/STOMP, Mail, Cache, Actuator), Flyway, springdoc-openapi 3 |
| Database | PostgreSQL 18 (Neon / Supabase in prod) |
| Cache | Caffeine (default) or Redis (`CACHE_TYPE=redis`) |
| Frontend | React 19, TypeScript 7 (native compiler), Vite 8, Tailwind CSS 4, TanStack Query 5, React Router 8, dnd-kit, STOMP.js |
| Testing | JUnit 5, Mockito, AssertJ, Spring MockMvc, Testcontainers (Postgres), Vitest; JaCoCo coverage |
| Ops | Docker (multi-stage, layered, non-root), docker-compose, GitHub Actions (SHA-pinned) + Dependabot, git hooks, Render + Vercel |

## Run it locally

**Option A — everything in Docker** (only Docker required):

```bash
docker compose up --build
```

- App: http://localhost:3000 · Swagger: http://localhost:3000/swagger-ui · Caught e-mails (Mailpit): http://localhost:8025

**Option B — dev mode** (JDK 27, Node 26):

```bash
docker compose up -d postgres mailpit
cd backend && MAIL_ENABLED=true ./mvnw spring-boot:run
cd frontend && npm install && npm run dev       # http://localhost:5173 (proxies /api and /ws)
```

Optional Redis cache: `docker compose --profile redis up -d redis` and start the backend with `CACHE_TYPE=redis REDIS_ENABLED=true`.

## Tests

```bash
cd backend && ./mvnw verify     # 55 tests: unit, web-slice, Testcontainers integration (needs Docker)
cd frontend && npm test && npm run lint && npm run build
```

Highlights: property-based test for fractional indexing (random inserts stay sorted), token-bucket timing with a
fake clock, refresh-token reuse detection, RBAC/validation/Problem-Details on the web layer, and an end-to-end test
that logs in, fires **20 concurrent drag-and-drops** at the same slot and asserts no duplicate keys, and connects a
real **STOMP client** to assert the `TASK_CREATED` event is pushed after commit.

## Deploy (free tier): Render + Vercel

1. **Backend + database — [Render](https://render.com)**: *New → Blueprint* → pick this repo. [`render.yaml`](render.yaml)
   creates a free Postgres and the Docker web service (Singapore). Database credentials are injected via
   `fromDatabase` and `JWT_SECRET` is generated by Render, so no secret is typed by hand. When prompted, set
   `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` to your Vercel URL (CORS is needed even though `/api` is proxied:
   browsers still send the `Origin` header, and the WebSocket handshake checks it).
   - Free Render Postgres expires after 30 days. For a permanent database create one on [Neon](https://neon.tech)
     and set its connection string as `DATABASE_URL` (the app accepts standard `postgres://user:pass@host/db?sslmode=require` URLs).
   - Free services sleep after 15 min without HTTP or WebSocket traffic; the first request then takes ~1 min.
2. **Frontend — [Vercel](https://vercel.com)**: import the repo, **Root Directory = `frontend`** (framework: Vite).
   - [`frontend/vercel.json`](frontend/vercel.json) proxies `/api`, `/swagger-ui` and health checks to the Render host
     so the refresh cookie is first-party.
   - Environment variable: `VITE_WS_URL=wss://<render-host>/ws` (Vercel doesn't proxy WebSockets).
3. **E-mail (optional)**: create a free SMTP account (Brevo, Resend, Mailjet…) and set on Render
   `MAIL_ENABLED=true SMTP_HOST SMTP_PORT=587 SMTP_USERNAME SMTP_PASSWORD SMTP_AUTH=true SMTP_STARTTLS=true MAIL_FROM`.

### Configuration reference

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | — | `postgres://user:pass@host:port/db` (Render/Neon/Supabase style); ignored if `DB_URL` is set |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | Explicit JDBC connection |
| `JWT_SECRET` | dev-only value | Base64 HMAC key for access tokens (**set in prod**) |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `15m` / `7d` | Token lifetimes |
| `CORS_ALLOWED_ORIGINS` | localhost | Comma-separated origins (also used for the WebSocket handshake) |
| `FRONTEND_URL` | `http://localhost:5173` | Used for links in e-mails |
| `COOKIE_SECURE` / `COOKIE_SAME_SITE` | `false` / `Lax` | Refresh-cookie flags |
| `CACHE_TYPE` | `caffeine` | `redis` to use `REDIS_URL` |
| `MAIL_ENABLED`, `SMTP_*`, `MAIL_FROM` | disabled (logs only) | Outgoing e-mail |
| `SEED_DEMO_DATA` | `true` | Create demo accounts & projects on first start |
| `DEMO_RESET_CRON` | `-` (off) | e.g. `0 0 3 * * *` to wipe & re-seed nightly |
| `REMINDER_CRON` | every 15 min | Deadline reminder job |
| `RATE_LIMIT_CAPACITY` / `RATE_LIMIT_REFILL` | `10` / `10` per min | Login/register token bucket |

## API overview

Full interactive docs at `/swagger-ui`. Main resources:

| Method & path | Description |
|---|---|
| `POST /api/auth/register · login · refresh · logout` | Auth (refresh token in httpOnly cookie) |
| `GET /api/projects` · `POST /api/projects` | My projects (with counts) · create |
| `GET /api/projects/{id}/board` | Columns with ordered cards + members |
| `GET /api/projects/{id}/tasks?q=&priority=&assigneeId=&columnId=&overdue=&page=&size=&sort=` | Search |
| `POST /api/projects/{id}/tasks` · `GET/PUT/DELETE /api/tasks/{id}` | Task CRUD (PUT requires `version`) |
| `POST /api/tasks/{id}/move` | Drag & drop: `{columnId, afterTaskId}` |
| `GET/POST /api/tasks/{id}/comments` · `PUT/DELETE /api/comments/{id}` | Comments |
| `…/columns`, `…/members`, `…/members/{id}/transfer-ownership` | Board & membership management |
| `GET /api/projects/{id}/activity?taskId=` | Audit log |
| `GET /api/notifications` · `…/unread-count` · `…/read-all` | Notification inbox |
| `GET /api/admin/users` · `PATCH /api/admin/users/{id}` · `GET /api/admin/stats` | System admin |
| STOMP `/ws` → `/topic/projects/{id}`, `/topic/projects/{id}/presence`, `/user/queue/notifications` | Real-time |

Errors always look like:

```json
{ "type": "about:blank", "title": "Validation failed", "status": 400,
  "detail": "One or more fields are invalid.", "instance": "/api/projects/1/tasks",
  "errors": { "title": "must not be blank" } }
```

## Project structure

```
backend/                     Spring Boot app (package-by-feature)
  src/main/java/com/tpm/
    auth/        JWT issuing, refresh-token rotation, login/register
    project/     projects, members, columns, RBAC (ProjectAccess), role cache
    task/        tasks, drag & drop ordering, search specifications
    comment/     comments
    activity/    audit log
    notification/ in-app + e-mail notifications, deadline reminder job
    realtime/    STOMP auth, board events, presence
    common/      FractionalIndex, TokenBucket, errors, paging
    config/      security, websocket, OpenAPI, rate limiting
    seed/        demo accounts & data
  src/main/resources/db/migration/   Flyway SQL
frontend/                    React SPA (Vite)
  src/lib/       API client, auth, realtime, board state reducers
  src/components/ Board (dnd-kit), TaskModal, settings, UI kit
  src/pages/     Login, Dashboard, Project (board/list/activity/settings), Admin, Profile
docker-compose.yml · render.yaml · .github/workflows/ci.yml
```

## Contributing & git workflow

```bash
git config core.hooksPath .githooks   # once per clone
```

| Hook | What it does |
|---|---|
| `commit-msg` | Enforces [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/): `type(scope): summary`, header ≤ 72 chars |
| `pre-commit` | Staged files only: blocks `.env`/key files, obvious secrets, conflict markers, files > 1 MB; lints staged frontend code |
| `pre-push` | Runs frontend typecheck + tests and backend unit/web tests for the parts you changed (CI runs everything) |

Commits are small and atomic (one logical change, each one builds), written in the imperative mood.
Guidance for AI coding agents lives in [AGENTS.md](AGENTS.md) (with nested [backend](backend/AGENTS.md) /
[frontend](frontend/AGENTS.md) files); [CLAUDE.md](CLAUDE.md) imports it.

## Known limitations / next steps

- The STOMP broker and presence are in-memory (single instance). To scale out: `enableStompBrokerRelay` with RabbitMQ, presence + rate-limit buckets in Redis.
- The access token stays valid until it expires (15 min) after logout/disable; a short TTL is the trade-off for stateless auth (a Redis deny-list would close the gap).
- Rate limiting keys on the client IP from `X-Forwarded-For`; behind proxies you don't control, use the proxy's trusted client-IP header instead.
- Ideas: labels & sub-tasks, file attachments (S3), @mentions, sprints/burndown charts, full-text search with `tsvector`.
