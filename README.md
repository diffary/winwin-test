# Two Spring Boot services with Postgres and Docker

Test task for the Backend Engineer position at WinWin.travel.

- **auth-api** (port 8080): register / login with BCrypt + JWT, protected `POST /api/process` that calls data-api and logs the result to Postgres.
- **data-api** (port 8081): `POST /api/transform`, accepts requests only with a valid `X-Internal-Token` header. Transform = reverse + uppercase.
- **postgres**: `users` and `processing_log` tables, created by `auth-api/src/main/resources/schema.sql` on startup.

```
client ──► auth-api:8080 ──► data-api:8081
               │
               ▼
           postgres:5432
```

## Requirements

Docker with Compose v2. Nothing else: both services are built inside multi-stage Dockerfiles, so a local JDK or Maven is optional.

## Run

```bash
cp .env.example .env          # adjust secrets if you like
docker compose up -d --build
docker compose logs -f auth-api   # wait for "Started AuthApiApplication"
```

Stop and remove everything, including the database volume:

```bash
docker compose down -v
```

## Test by hand

```bash
# Register -> 201
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -d '{"email":"a@a.com","password":"pass"}'

# Login -> 200 { "token": "..." }
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"email":"a@a.com","password":"pass"}'

# Process -> 200 { "result": "OLLEH" }
curl -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"text":"hello"}'

# data-api without the internal token -> 403
curl -i -X POST http://localhost:8081/api/transform \
  -H "Content-Type: application/json" -d '{"text":"hello"}'

# Log row in Postgres
docker compose exec postgres psql -U app -d appdb \
  -c "SELECT user_id, input_text, output_text, created_at FROM processing_log;"
```

On Windows cmd.exe, escape the JSON quotes like in the task statement: `-d "{\"email\":\"a@a.com\",\"password\":\"pass\"}"`.

## Smoke test

`smoke.sh` runs the whole acceptance flow against the running stack (register, duplicate register, login, wrong password, unauthenticated process, process, data-api without token, DB row):

```bash
./smoke.sh
```

## API

| Method | Path | Auth | Response |
|---|---|---|---|
| POST | `/api/auth/register` `{email, password}` | none | 201 `{id, email}`; 400 invalid body; 409 email taken |
| POST | `/api/auth/login` `{email, password}` | none | 200 `{token}`; 401 wrong credentials |
| POST | `/api/process` `{text}` | `Authorization: Bearer <jwt>` | 200 `{result}`; 400 blank text; 401 no/invalid token; 502 data-api unavailable |
| POST | `/api/transform` `{text}` (data-api) | `X-Internal-Token` | 200 `{result}`; 400 blank text; 403 missing/invalid token |

All errors have the shape `{"error": "message"}`.

## Configuration

All configuration comes from environment variables (see `.env.example`).

| Variable | Used by | Meaning |
|---|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | postgres, auth-api | database credentials |
| `POSTGRES_URL` | auth-api | JDBC URL, set in compose to `jdbc:postgresql://postgres:5432/${POSTGRES_DB}` |
| `JWT_SECRET` | auth-api | HS256 signing key, at least 32 characters |
| `INTERNAL_TOKEN` | auth-api, data-api | shared secret sent in `X-Internal-Token` |
| `DATA_API_URL` | auth-api | defaults to `http://data-api:8081` |

## Build and test without Docker Compose

With a local JDK 17 and Maven:

```bash
mvn -f auth-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml test
mvn -f auth-api/pom.xml test
```

Without a local JDK, run Maven from a container:

```bash
docker run --rm -v "$PWD":/w -v m2cache:/root/.m2 -w /w maven:3.9-eclipse-temurin-17 mvn -f data-api/pom.xml test
docker run --rm -v "$PWD":/w -v m2cache:/root/.m2 -w /w maven:3.9-eclipse-temurin-17 mvn -f auth-api/pom.xml test
```

Unit tests cover the data-api endpoint (403 without / with wrong token, 200 with the right one, 400 for blank text) and `JwtService` (round-trip, tampered token, foreign secret, expired token).

## Design notes

- **Stateless JWT.** `JwtAuthFilter` reads the Bearer token, `JwtService` verifies the HS256 signature and expiry and puts the user id into the security context. No sessions, no DB lookup per request.
- **Passwords** are stored as BCrypt hashes. Login returns the same 401 for unknown email and wrong password.
- **Internal token** is compared in constant time in `InternalTokenFilter`; a mismatch gives 403 before the controller runs.
- **Failure of data-api** (down, timeout, 403) is mapped to 502 with a generic message; the log row is written only after a successful transform.
- **Schema** is managed by `schema.sql` (`CREATE TABLE IF NOT EXISTS`); Hibernate runs with `ddl-auto=validate` so entities and tables can't silently drift apart. Flyway would be the next step for real migrations, but is out of scope here.
- **Secrets** are never logged: the exception handler logs only the exception message of the data-api failure, and the request bodies are not logged anywhere.
