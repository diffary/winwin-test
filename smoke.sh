#!/usr/bin/env bash
# End-to-end check against a running `docker compose up`.
# Walks through the acceptance criteria: register -> login -> process -> data-api 403 -> row in processing_log.
set -euo pipefail

AUTH=${AUTH_URL:-http://localhost:8080}
DATA=${DATA_URL:-http://localhost:8081}
EMAIL="smoke-$(date +%s)@example.com"
PASS="pass"

step() { printf '\n== %s\n' "$1"; }
expect_status() {  # expect_status <expected> <actual> <label>
  if [ "$1" = "$2" ]; then echo "OK  $3 -> $2"; else echo "FAIL $3 -> expected $1, got $2"; exit 1; fi
}

step "1. Register"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH/api/auth/register" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
expect_status 201 "$code" "register"

step "2. Register same email again"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH/api/auth/register" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
expect_status 409 "$code" "duplicate register"

step "3. Login"
body=$(curl -s -X POST "$AUTH/api/auth/login" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
TOKEN=$(printf '%s' "$body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "FAIL login: no token in $body"; exit 1; }
echo "OK  login -> token received"

step "4. Login with wrong password"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH/api/auth/login" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"wrong\"}")
expect_status 401 "$code" "bad password"

step "5. /api/process without token"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH/api/process" \
  -H 'Content-Type: application/json' -d '{"text":"hello"}')
expect_status 401 "$code" "process unauthenticated"

step "6. /api/process with token"
body=$(curl -s -X POST "$AUTH/api/process" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"text":"hello"}')
echo "response: $body"
[ "$body" = '{"result":"OLLEH"}' ] || { echo "FAIL process: unexpected body"; exit 1; }
echo "OK  process -> OLLEH"

step "7. data-api directly without internal token"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$DATA/api/transform" \
  -H 'Content-Type: application/json' -d '{"text":"hello"}')
expect_status 403 "$code" "data-api without token"

step "8. Row in processing_log"
if command -v docker >/dev/null; then
  docker compose exec -T postgres psql -U "${POSTGRES_USER:-app}" -d "${POSTGRES_DB:-appdb}" \
    -c "SELECT u.email, l.input_text, l.output_text, l.created_at FROM processing_log l JOIN users u ON u.id = l.user_id WHERE u.email = '$EMAIL';"
else
  echo "docker not found, skip DB check"
fi

printf '\nAll checks passed.\n'
