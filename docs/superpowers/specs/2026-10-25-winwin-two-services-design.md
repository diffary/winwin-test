# WinWin.travel test task: two Spring Boot services — design

## Goal
Two Spring Boot apps run via docker-compose. auth-api handles register/login (JWT, BCrypt, Postgres) and a protected /api/process endpoint that calls data-api with a shared header, stores a processing log row, returns the result. data-api exposes /api/transform guarded by X-Internal-Token.

## Constraints
- Java 17, Spring Boot 3.3.x, Maven. Local machine has only Java 8 and no Maven, so builds happen inside Docker (multi-stage). README still lists the `mvn -f ...` commands from the task as an alternative.
- Keep it simple: no Flyway, no mappers, no service-layer ceremony beyond what makes sense.
- Never log secrets or tokens.

## Repository layout
```
winwin-test/
  auth-api/        Spring Boot: web, security, data-jpa, validation, postgres, jjwt
  data-api/        Spring Boot: web, validation
  docker-compose.yml
  .env.example
  README.md
  smoke.sh         curl walkthrough: register -> login -> process -> 403 check
```

## auth-api (port 8080)
Packages: `com.winwin.authapi` with `config`, `auth`, `process`, `user`, `log` (or flat `auth`/`process`).

Entities (schema created by `schema.sql`, `spring.sql.init.mode=always`, JPA `ddl-auto=validate`):
- `users(id uuid pk, email varchar unique not null, password_hash varchar not null)`
- `processing_log(id uuid pk, user_id uuid not null references users, input_text text, output_text text, created_at timestamptz not null)`

Endpoints:
- `POST /api/auth/register {email,password}` → 201 `{id,email}`; 400 on invalid body (email format, password blank); 409 if email taken.
- `POST /api/auth/login {email,password}` → 200 `{token}`; 401 on bad credentials (same message for unknown email and wrong password).
- `POST /api/process {text}` (Bearer required) → 200 `{result}`; 400 if text blank; 401 without/invalid token; 502 `{error}` if data-api unreachable or returns non-2xx.

Components:
- `JwtService`: HS256 via jjwt, subject = user id, `exp` = now + 1h. Secret from `JWT_SECRET` (min 32 bytes).
- `JwtAuthFilter` (OncePerRequestFilter): parses Bearer header, sets `UsernamePasswordAuthenticationToken` with principal = user id (UUID). Invalid token → just no auth; Security returns 401 via entry point.
- `SecurityConfig`: stateless, CSRF off, permitAll `/api/auth/**`, authenticated for the rest, BCryptPasswordEncoder bean, 401 entry point returning JSON.
- `DataApiClient`: `RestClient` with base URL from `DATA_API_URL` (default `http://data-api:8081`), adds `X-Internal-Token` from `INTERNAL_TOKEN`. Throws `DataApiException` on any failure.
- `ProcessController`: calls client, saves `ProcessingLog`, returns result.
- `GlobalExceptionHandler` (@RestControllerAdvice): validation → 400, `EmailTakenException` → 409, `BadCredentialsException` → 401, `DataApiException` → 502.

Config via env: `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `INTERNAL_TOKEN`, `DATA_API_URL`.

## data-api (port 8081)
- `InternalTokenFilter` (OncePerRequestFilter): compares `X-Internal-Token` header to `INTERNAL_TOKEN` env with constant-time comparison; missing/mismatch → 403 JSON `{error}`. Applies to `/api/**`.
- `TransformController`: `POST /api/transform {text}` → 200 `{result}` where result = reversed text uppercased. 400 on blank text.

## Docker
- Each service: multi-stage Dockerfile (`maven:3.9-eclipse-temurin-17` build with dependency layer cached, then `eclipse-temurin:17-jre`). Non-root user.
- `docker-compose.yml`: `postgres:16-alpine` with healthcheck (`pg_isready`), named volume; `auth-api` depends_on postgres healthy and data-api; `data-api` standalone. Ports 8080:8080, 8081:8081. All secrets from `.env` (compose reads it automatically); `.env.example` committed, `.env` git-ignored.

## Error handling
JSON error body `{ "error": "..." }` everywhere. No stack traces to client. Logs never include password, token, or internal header.

## Testing
- data-api: `@WebMvcTest`-style or `@SpringBootTest` + MockMvc: 403 without header, 403 wrong header, 200 with header and correct transform.
- auth-api: unit test for `JwtService` (issue → parse returns same subject; tampered token rejected). Context load not required.
- Manual: `smoke.sh` covers acceptance criteria incl. direct call to data-api without token → 403 and a `psql` query showing the log row.

## Out of scope
Refresh tokens, roles, rate limiting, Flyway, OpenAPI, CI.
