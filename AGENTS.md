# AGENTS.md

Guidance for AI coding agents working in this repository. Humans: see [README.md](README.md).
Subprojects have their own, more specific files — the closest `AGENTS.md` to the file you edit wins:
[backend/AGENTS.md](backend/AGENTS.md) · [frontend/AGENTS.md](frontend/AGENTS.md).

## Project overview

TPM is a real-time Kanban task & project manager (mini Jira/Trello).

| Part | Stack | Path |
|---|---|---|
| API | Java 27, Spring Boot 4.1, PostgreSQL, Flyway, STOMP/WebSocket | `backend/` |
| Web app | React 19, TypeScript 7, Vite 8, Tailwind 4, TanStack Query, dnd-kit | `frontend/` |
| Ops | Docker, docker-compose, GitHub Actions, Render (API) + Vercel (web) + Neon (DB) | repo root |

## Setup

Requirements: JDK 27, Node 26 (`frontend/.nvmrc`), Docker (Postgres, Mailpit, Testcontainers).

```bash
git config core.hooksPath .githooks          # once per clone: enables commit-msg / pre-commit / pre-push hooks
docker compose up -d postgres mailpit        # local infra
cd backend && ./mvnw spring-boot:run         # API on :8080 (seeds demo data)
cd frontend && npm ci && npm run dev         # web on :5173, proxies /api and /ws to :8080
```

Demo logins (password `Demo@1234`): `alice@demo.tpm` (owner), `bob@demo.tpm` (member), `carol@demo.tpm` (admin),
`victor@demo.tpm` (viewer), `admin@demo.tpm` (system admin).

## Verify before you finish

Run what CI runs (`.github/workflows/ci.yml`) for every part you touched:

```bash
cd backend  && ./mvnw -B verify                                         # unit + web-slice + Testcontainers tests
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

Integration tests need a running Docker daemon. Never mark work done with failing or skipped tests.

## Conventions

- **Commits:** [Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/) —
  `type(scope): imperative summary` (≤ 72 chars), types `feat fix docs style refactor perf test build ci chore revert`,
  scopes such as `auth`, `tasks`, `board`, `realtime`, `frontend`, `backend`, `deps`. One logical change per commit;
  each commit should build. The `commit-msg` hook enforces the format.
- **API contract:** backend DTOs (Java records) and `frontend/src/lib/types.ts` must change together.
- **Errors:** the API always answers with RFC 9457 Problem Details; add new exception types to
  `common/Exceptions` + `GlobalExceptionHandler`, never return ad-hoc error bodies.
- **Database:** schema changes only through a new Flyway migration `backend/src/main/resources/db/migration/V<n>__<desc>.sql`.
  Never edit an applied migration.
- **Docs:** update `README.md` when behaviour, configuration or endpoints change.

## Architecture rules that are easy to break

1. **Ordering uses fractional indexing** (`FractionalIndex` / `frontend/src/lib/fractional.ts`). Position columns are
   `COLLATE "C"`; compare keys byte-wise, never with locale collation. Keys must not end in `0`.
2. **Moves are server-authoritative:** clients send `{columnId, afterTaskId}`; the server locks the column row and
   computes the key. Don't accept raw positions from clients.
3. **Side effects run after commit:** publish events inside the transaction; WebSocket pushes and e-mails use
   `@TransactionalEventListener` (after commit). Audit-log writes stay in the same transaction (`Propagation.MANDATORY`).
4. **Authorization:** every project-scoped operation calls `ProjectAccess.require(projectId, role)` or uses
   `@PreAuthorize("@projectAccess...")`. Membership changes must call `MemberRoleCache.evictAfterCommit`.
5. **Optimistic locking:** task updates must carry `version`; keep the 409 path working.
6. **Auth tokens:** access token in memory only on the client; refresh token only in the httpOnly cookie, stored
   hashed server-side. Never log tokens or put them in URLs.

## Security & secrets

- Never commit `.env` files, real credentials, or `JWT_SECRET` values (the `pre-commit` hook blocks common cases).
- Demo accounts (`*@demo.tpm`) are protected server-side; keep that behaviour.
- New endpoints are authenticated by default (`SecurityConfig`); justify any `permitAll`.

## Dependency updates

- Spring Boot's BOM decides most backend versions; only override for security patches (document why in `pom.xml`).
- Check changelogs for every major bump; run the full verification above afterwards.
- GitHub Actions are pinned to commit SHAs (update with `npx actions-up`); Dependabot proposes weekly updates.
