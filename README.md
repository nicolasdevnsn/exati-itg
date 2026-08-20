# exati-itg

Spring Boot 3 REST API for the Exati ITG platform.

## Stack

| Layer | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4.x |
| Build | Gradle (Kotlin DSL) |
| Persistence | Spring Data JPA + H2 (in-memory, dev/test); swap to Postgres/MySQL later |
| Migrations | Flyway (source of truth — `ddl-auto: none`) |
| API docs | Springdoc OpenAPI 3 → Swagger UI at `/swagger-ui.html` |
| Ops | Spring Boot Actuator + Micrometer + Prometheus endpoint |
| Validation | Jakarta Validation (`spring-boot-starter-validation`) |
| Errors | RFC 7807 `application/problem+json` via `GlobalExceptionHandler` |
| Boilerplate | Lombok where useful; Java records for DTOs |
| Tests | JUnit 5 + Spring Boot Test + MockMvc |

## Run

Requires JDK 21 on the path.

```bash
./gradlew bootRun
```

First run will fetch dependencies and provision a Gradle wrapper if needed
(use `gradle wrapper --gradle-version 8.11` if `gradlew` is missing).

## Endpoints out of the box

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | open | Create user, returns `{tokenType, accessToken, expiresAt, username}` (201) |
| `POST` | `/api/v1/auth/login`    | open | Exchange username+password for a fresh JWT |
| `GET`  | `/api/v1/ping`          | **Bearer JWT** | Sample resource — returns `{"message":"pong","timestamp":"..."}` |
| `GET`  | `/actuator/health`      | open | Liveness/readiness |
| `GET`  | `/actuator/info`        | open | Build info |
| `GET`  | `/actuator/metrics`     | Bearer JWT | Micrometer metrics |
| `GET`  | `/actuator/prometheus`  | open | Prometheus scrape endpoint |
| `GET`  | `/swagger-ui.html`      | open | Interactive API docs (click **Authorize** to attach a token) |
| `GET`  | `/v3/api-docs`          | open | OpenAPI 3 JSON |
| `GET`  | `/h2-console`           | open | H2 DB console (dev profile only) |

## Authentication

JWT bearer tokens signed with HS256. Users live in the `users` table
(BCrypt-hashed passwords), seeded via Flyway migration `V2__users.sql`.

### Cycle

1. `POST /api/v1/auth/register` with `{"username":"alice","password":"…"}`
   → `201 Created` with an `accessToken`.
2. `POST /api/v1/auth/login` with the same credentials → `200 OK` with a fresh
   `accessToken` (existing tokens remain valid until they expire).
3. Send `Authorization: Bearer <accessToken>` on every protected request.
   Default lifetime is 60 minutes.

### Quick demo with curl

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password-strong-1"}' \
  | jq -r .accessToken)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/ping
```

### Configuration

| Property | Env var | Default | Notes |
|---|---|---|---|
| `app.jwt.secret`             | `APP_JWT_SECRET`             | dev-only placeholder | Must be ≥ 32 bytes (256 bits) for HS256. **Override in production.** |
| `app.jwt.expiration-minutes` | `APP_JWT_EXPIRATION_MINUTES` | `60`                 | Token lifetime in minutes. |

### Error model

Authentication failures return RFC 7807 `application/problem+json` exactly like
business errors:

```json
{ "type":"about:blank", "title":"Unauthorized", "status":401,
  "detail":"Authentication required.", "timestamp":"…" }
```

## Test

```bash
./gradlew test
```

`PingControllerTests` exercises the sample endpoint end-to-end with `MockMvc`.
`ExatiItgApplicationTests` is a smoke test confirming the Spring context starts.

## Project layout

```
src/main/java/com/exati/itg/
  ExatiItgApplication.java       # @SpringBootApplication entrypoint
  api/                           # @RestController + HTTP-edge code
    AuthController.java
    JwtAuthFilter.java
    PingController.java
    dto/                         # DTOs (Java records)
      AuthResponse.java
      LoginRequest.java
      PingResponse.java
      RegisterRequest.java
  domain/                        # JPA entities
    User.java
  repository/                    # Spring Data repositories
    UserRepository.java
  service/                       # business logic, constructor-injected
    AppUserDetailsService.java
    AuthService.java
    JwtService.java
    PingService.java
  config/                        # @Configuration beans
    OpenApiConfig.java
    SecurityConfig.java
  exception/                     # error model
    ApiException.java
    GlobalExceptionHandler.java
src/main/resources/
  application.yml                # base config (active: dev)
  application-dev.yml            # dev-profile overrides — verbose logging
  db/migration/                  # Flyway SQL — V<n>__<name>.sql
    V1__init.sql
    V2__users.sql
src/test/java/com/exati/itg/
  ExatiItgApplicationTests.java
  api/AuthControllerTests.java
  api/PingControllerTests.java
```

## Conventions

- **Constructor injection** only (`@RequiredArgsConstructor` from Lombok).
  No `@Autowired` on fields. No setter injection.
- **DTOs are records.** Entities use Lombok where boilerplate is heavy.
- **Flyway is the schema authority.** Hibernate `ddl-auto` is `none`. Every
  schema change is a new `V<n>__description.sql` file — never edit a
  previously-applied migration.
- **Errors are RFC 7807.** Throw `ApiException.notFound("…")` /
  `ApiException.badRequest("…")` from services; `GlobalExceptionHandler` maps
  it to a `ProblemDetail` body with `timestamp`.
- **`open-in-view: false`** — no lazy loading in the controller layer.
  Services materialise everything they return.
- **Profile-based config.** `dev` is the default; override via
  `SPRING_PROFILES_ACTIVE=prod` and an `application-prod.yml`.

## Swapping H2 for Postgres / MySQL later

1. Replace the `runtimeOnly("com.h2database:h2")` line in `build.gradle.kts`
   with `runtimeOnly("org.postgresql:postgresql")` (and add
   `implementation("org.flywaydb:flyway-database-postgresql")`).
2. Update `spring.datasource.url`, `username`, `password` in
   `application.yml` (or a new `application-prod.yml`).
3. Existing Flyway migrations in `db/migration/` apply unchanged for any
   SQL that's portable. Re-write H2-specific SQL if needed.

## Adding the first real endpoint

1. Add `db/migration/V2__<your_table>.sql`.
2. Create an `@Entity` under `com.exati.itg.domain` (new package).
3. Create a `JpaRepository` under `com.exati.itg.repository`.
4. Create a record DTO under `com.exati.itg.api.dto`.
5. Create a `@Service` under `com.exati.itg.service`.
6. Create a `@RestController` under `com.exati.itg.api`.
7. Add a `MockMvc` test under `src/test/java/com/exati/itg/api`.

## What's deliberately not in this scaffold

- **Refresh tokens / token revocation.** The current JWT is stateless and
  single-token; add a `refresh_tokens` table and rotate when needed.
- **Role-based authorization on routes.** Users are seeded with `ROLE_USER`;
  add `@PreAuthorize("hasRole('ADMIN')")` (or matcher-based rules in
  `SecurityConfig`) when admin endpoints appear.
- **Docker / Compose.** Trivial to add once you commit to Postgres/MySQL.
- **CI config.** Pick GitHub Actions / GitLab CI / Jenkins based on the org.
- **Testcontainers.** Add when you move off H2.
