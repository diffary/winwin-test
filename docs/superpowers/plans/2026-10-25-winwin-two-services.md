# WinWin two-service test task — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two Spring Boot apps (auth-api, data-api) + Postgres running via docker-compose; JWT auth, protected /api/process that calls data-api with a shared header and logs to Postgres.

**Architecture:** auth-api = Spring Web + Security + JPA, stateless JWT filter, RestClient to data-api. data-api = Spring Web + one header filter + one controller. Schema via schema.sql. Builds happen inside multi-stage Dockerfiles (local machine has no JDK 17 / Maven); tests run via `docker run maven ...`.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Maven, Postgres 16, jjwt 0.12.6, Docker Compose.

Spec: `docs/superpowers/specs/2026-10-25-winwin-two-services-design.md`

**Running Maven without a local install** (used in every test step, from repo root):
```bash
MVN='docker run --rm -v "$PWD":/w -v m2cache:/root/.m2 -w /w maven:3.9-eclipse-temurin-17 mvn -q'
```

---

## File structure

```
data-api/
  pom.xml
  Dockerfile
  src/main/java/com/winwin/dataapi/DataApiApplication.java
  src/main/java/com/winwin/dataapi/InternalTokenFilter.java      header check -> 403
  src/main/java/com/winwin/dataapi/TransformController.java      POST /api/transform
  src/main/resources/application.yml
  src/test/java/com/winwin/dataapi/TransformControllerTest.java
auth-api/
  pom.xml
  Dockerfile
  src/main/java/com/winwin/authapi/AuthApiApplication.java
  src/main/java/com/winwin/authapi/user/User.java
  src/main/java/com/winwin/authapi/user/UserRepository.java
  src/main/java/com/winwin/authapi/auth/JwtService.java
  src/main/java/com/winwin/authapi/auth/JwtAuthFilter.java
  src/main/java/com/winwin/authapi/auth/SecurityConfig.java
  src/main/java/com/winwin/authapi/auth/AuthDtos.java
  src/main/java/com/winwin/authapi/auth/AuthController.java
  src/main/java/com/winwin/authapi/process/ProcessingLog.java
  src/main/java/com/winwin/authapi/process/ProcessingLogRepository.java
  src/main/java/com/winwin/authapi/process/DataApiClient.java
  src/main/java/com/winwin/authapi/process/ProcessController.java
  src/main/java/com/winwin/authapi/common/ApiExceptions.java
  src/main/java/com/winwin/authapi/common/GlobalExceptionHandler.java
  src/main/resources/application.yml
  src/main/resources/schema.sql
  src/test/java/com/winwin/authapi/auth/JwtServiceTest.java
docker-compose.yml
.env.example
README.md
smoke.sh
```

---

### Task 1: data-api — skeleton, InternalTokenFilter, TransformController (TDD)

- [ ] Step 1: `data-api/pom.xml` — spring-boot-starter-parent 3.3.5, java 17, deps: starter-web, starter-validation, starter-test (test). spring-boot-maven-plugin.
- [ ] Step 2: `application.yml`: `server.port: 8081`, `app.internal-token: ${INTERNAL_TOKEN}`.
- [ ] Step 3: Write `TransformControllerTest` (`@SpringBootTest(properties="INTERNAL_TOKEN=test-secret") @AutoConfigureMockMvc`): 403 without header, 403 wrong header, 200 + `$.result == "OLLEH"` for "hello", 400 for blank text.
- [ ] Step 4: `DataApiApplication`. Run `$MVN -f data-api/pom.xml test` → FAIL (compile / 404).
- [ ] Step 5: `InternalTokenFilter extends OncePerRequestFilter`, `@Component`, reads `app.internal-token`, compares with `MessageDigest.isEqual`, on mismatch writes 403 JSON `{"error":"invalid or missing internal token"}`.
- [ ] Step 6: `TransformController`: records `TransformRequest(@NotBlank String text)`, `TransformResponse(String result)`; result = reverse + uppercase; `@ExceptionHandler(MethodArgumentNotValidException)` → 400 `{"error":"text must not be blank"}`.
- [ ] Step 7: Run tests → PASS.
- [ ] Step 8: `Dockerfile` multi-stage: `maven:3.9-eclipse-temurin-17` (copy pom, `dependency:go-offline`, copy src, `package -DskipTests`) → `eclipse-temurin:17-jre`, non-root user, `EXPOSE 8081`, `ENTRYPOINT java -jar app.jar`.
- [ ] Step 9: Commit `feat(data-api): transform endpoint guarded by internal token`.

### Task 2: auth-api — skeleton, entities, schema, JwtService (TDD)

- [ ] Step 1: `auth-api/pom.xml` — deps: web, security, data-jpa, validation, postgresql (runtime), jjwt-api / jjwt-impl (runtime) / jjwt-jackson (runtime) 0.12.6, starter-test.
- [ ] Step 2: `application.yml` (datasource from `POSTGRES_URL/USER/PASSWORD`, `spring.sql.init.mode=always`, `jpa.hibernate.ddl-auto=validate`, `open-in-view=false`, `app.jwt-secret`, `app.jwt-ttl-seconds=3600`, `app.internal-token`, `app.data-api-url=${DATA_API_URL:http://data-api:8081}`) and `schema.sql` (users, processing_log with `CREATE TABLE IF NOT EXISTS`).
- [ ] Step 3: `AuthApiApplication`, `User` entity (`users`), `UserRepository` (`findByEmail`, `existsByEmail`), `ProcessingLog` entity (`processing_log`), `ProcessingLogRepository`.
- [ ] Step 4: `JwtServiceTest`: issue → parseUserId returns same id; tampered token → empty; token from other secret → empty. Run → compile FAIL.
- [ ] Step 5: `JwtService` (HS256 via `Keys.hmacShaKeyFor`, subject = user id, exp = now + ttl; `parseUserId` returns `Optional<UUID>`, catches `JwtException | IllegalArgumentException`).
- [ ] Step 6: Run `$MVN -f auth-api/pom.xml test` → PASS. Commit `feat(auth-api): skeleton, entities, JwtService`.

### Task 3: auth-api — security, register/login, exception handler

- [ ] Step 1: `common/ApiExceptions` with `EmailTakenException`, `DataApiException`.
- [ ] Step 2: `common/GlobalExceptionHandler` (`@RestControllerAdvice`): validation → 400, EmailTaken → 409, BadCredentials → 401 "invalid email or password", DataApi → 502 "processing service unavailable" (log warn without body/tokens). Body always `{"error": ...}`.
- [ ] Step 3: `auth/JwtAuthFilter` (OncePerRequestFilter): parse `Authorization: Bearer`, set `UsernamePasswordAuthenticationToken(userId, null, [])` in SecurityContext when valid.
- [ ] Step 4: `auth/SecurityConfig`: csrf off, stateless, permitAll `/api/auth/**`, anyRequest authenticated, JSON 401 entry point, add JwtAuthFilter before `UsernamePasswordAuthenticationFilter`, `BCryptPasswordEncoder` bean.
- [ ] Step 5: `auth/AuthDtos` (`Credentials(@NotBlank @Email email, @NotBlank password)`, `RegisterResponse(id,email)`, `TokenResponse(token)`) and `auth/AuthController` (register → 201, lower-cases email, 409 if exists; login → `TokenResponse`, BadCredentials if user missing or password mismatch).
- [ ] Step 6: `$MVN -f auth-api/pom.xml compile` → SUCCESS. Commit `feat(auth-api): JWT security and register/login`.

### Task 4: auth-api — DataApiClient, ProcessController, Dockerfile

- [ ] Step 1: `process/DataApiClient`: `RestClient` with baseUrl `app.data-api-url` and default header `X-Internal-Token`; `transform(text)` posts `{text}` to `/api/transform`, returns `result`; wraps `RestClientException` / empty body into `DataApiException`.
- [ ] Step 2: `process/ProcessController`: `POST /api/process`, `ProcessRequest(@NotBlank text)`, `@AuthenticationPrincipal UUID userId`; calls client, saves `ProcessingLog`, returns `{result}`.
- [ ] Step 3: `Dockerfile` same as data-api, `EXPOSE 8080`.
- [ ] Step 4: `$MVN -f auth-api/pom.xml test` → PASS. Commit `feat(auth-api): /api/process calling data-api and logging`.

### Task 5: compose, env, README, smoke script, end-to-end check

- [ ] Step 1: `.env.example` (POSTGRES_USER/PASSWORD/DB, JWT_SECRET ≥ 32 chars, INTERNAL_TOKEN). `.env` git-ignored.
- [ ] Step 2: `docker-compose.yml`: postgres:16-alpine with pg_isready healthcheck + volume; data-api (8081); auth-api (8080) depends_on postgres healthy + data-api started; env wiring per spec.
- [ ] Step 3: `smoke.sh`: register → login → process (expect `OLLEH`) → direct data-api without token (expect 403) → psql count of processing_log.
- [ ] Step 4: `README.md`: what/how to run (Docker only), curls from task, smoke script, psql check, optional local Maven commands, env table, short design notes, how to run unit tests via docker maven.
- [ ] Step 5: `cp .env.example .env && docker compose up -d --build`, run `./smoke.sh`, fix issues. Commit `feat: docker-compose, README, smoke script`.
