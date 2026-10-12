# AGENTS.md — backend

Spring Boot 4.1 on **Java 27** (see root [AGENTS.md](../AGENTS.md) for repo-wide rules).

## Commands

```bash
./mvnw spring-boot:run                       # needs Postgres on :5432 (docker compose up -d postgres)
./mvnw -B verify                             # all tests + JaCoCo report (target/site/jacoco); Docker required
./mvnw test -Dtest='!BoardIntegrationTest'   # fast: unit + web-slice tests only
./mvnw -B clean package -DskipTests          # jar; prefer `clean` — stale incremental builds have broken the jar before
```

If several JDKs are installed, point `JAVA_HOME` at JDK 27 (the enforcer plugin fails the build otherwise).

## Layout (package-by-feature, `com.tpm`)

`auth` · `user` · `project` (projects, members, columns, `ProjectAccess`, `MemberRoleCache`) · `task` · `comment` ·
`activity` (audit log) · `notification` (in-app, e-mail, deadline job) · `realtime` (STOMP auth, events, presence) ·
`common` (`FractionalIndex`, `TokenBucket`, `Exceptions`, `GlobalExceptionHandler`, `PageResponse`) · `config` · `seed`.

## Spring Boot 4 specifics (don't regress)

- Starters are modular: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, per-technology `*-test` starters.
- Jackson 3: packages are `tools.jackson.*` (annotations stay `com.fasterxml.jackson.annotation`).
- Test annotations: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; use `@MockitoBean`
  (`@MockBean` no longer exists).
- `@EnableCaching/@EnableAsync/@EnableScheduling` live in `config/InfrastructureConfig`, not on the main class,
  so test slices stay light.
- Testcontainers 2: `org.testcontainers.postgresql.PostgreSQLContainer` + `@ServiceConnection`.
- Byte Buddy runs with `-Dnet.bytebuddy.experimental=true` (set in surefire and the Dockerfile) for Java 27 class files.

## Coding rules

- DTOs are records in `*Dtos` classes; entities never leave the service layer (`open-in-view` is off).
- Services are `@Transactional`; map to DTOs inside the transaction. Avoid N+1 — use fetch joins / grouped queries.
- Throw `Exceptions.NotFoundException / ConflictException / BadRequestException`; security failures are
  `AccessDeniedException`.
- Paging endpoints return `PageResponse` and whitelist sort properties.
- Use modern Java where it reads better: records, `switch` patterns, `_` for unused lambda params, text blocks.
- Every behavioural change gets a test: unit (Mockito) for logic, `@WebMvcTest` for HTTP contract,
  `BoardIntegrationTest` style for cross-cutting flows.
