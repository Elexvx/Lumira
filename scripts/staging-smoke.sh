#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/artifacts/staging"
OUT_FILE="${OUT_DIR}/staging-smoke-evidence.json"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.prod.yml}"
API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8000}"
STATUS="PASS"
FAILED_STEPS=()
COMMANDS=()
RESULTS=()
COOKIE_JAR="$(mktemp)"
LOGIN_RESPONSE_FILE="$(mktemp)"
AUTH_HEADER=()

cleanup() {
  rm -f "${COOKIE_JAR}" "${LOGIN_RESPONSE_FILE}" 2>/dev/null || true
}
trap cleanup EXIT

json_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\r//g'
}

json_array() {
  local first=1
  printf '['
  while IFS= read -r item; do
    [ -z "${item}" ] && continue
    if [ "${first}" -eq 0 ]; then printf ','; fi
    first=0
    printf '"%s"' "$(json_string "${item}")"
  done
  printf ']'
}

mkdir -p "${OUT_DIR}"

add_result() {
  local step="$1"; local status="$2"; local detail="$3"
  detail="$(printf '%s' "${detail}" | tr '\r\n' '  ')"
  RESULTS+=("{\"step\":\"${step}\",\"status\":\"${status}\",\"detail\":\"$(json_string "${detail}")\"}")
  if [ "${status}" != "PASS" ]; then
    STATUS="FAIL"
    FAILED_STEPS+=("${step}")
  fi
}

run_check() {
  local step="$1"; shift
  COMMANDS+=("$*")
  if output="$("$@" 2>&1)"; then
    add_result "${step}" "PASS" "$(echo "$output" | tail -c 500)"
  else
    add_result "${step}" "FAIL" "$(echo "$output" | tail -c 500)"
  fi
}

run_expect_http_status() {
  local step="$1"; local expected="$2"; shift 2
  COMMANDS+=("$*")
  local response_file status
  response_file="$(mktemp)"
  status="$("$@" -o "${response_file}" -w "%{http_code}" 2>/tmp/lumira-smoke-curl-error || true)"
  local body error_detail
  body="$(tail -c 500 "${response_file}" 2>/dev/null || true)"
  error_detail="$(tail -c 500 /tmp/lumira-smoke-curl-error 2>/dev/null || true)"
  rm -f "${response_file}" /tmp/lumira-smoke-curl-error
  if printf '%s' "${expected}" | tr ',' '\n' | grep -qx "${status}"; then
    add_result "${step}" "PASS" "http ${status} ${body}"
  else
    add_result "${step}" "FAIL" "expected ${expected}, got ${status}; ${body} ${error_detail}"
  fi
}

extract_json_field() {
  local file="$1"; local field="$2"
  case "${field}" in
    data.accessToken)
      grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' "${file}" | head -n 1 | cut -d '"' -f4
      ;;
    data.publicKey)
      grep -o '"publicKey"[[:space:]]*:[[:space:]]*"[^"]*"' "${file}" | head -n 1 | cut -d '"' -f4
      ;;
    *)
      return 1
      ;;
  esac
}

encrypt_login_password() {
  local plain="$1"
  local key_file cipher_file
  key_file="$(mktemp)"
  cipher_file="$(mktemp)"
  if ! curl -fsS "${API_BASE_URL}/api/v2/auth/login-encryption-key" -o "${key_file}" >/dev/null 2>&1; then
    rm -f "${key_file}" "${cipher_file}"
    return 1
  fi
  local public_key der_file plain_file encrypted_file
  public_key="$(extract_json_field "${key_file}" "data.publicKey")"
  if [ -z "${public_key}" ]; then
    echo "missing publicKey in login encryption response" >&2
    rm -f "${key_file}" "${cipher_file}"
    return 1
  fi
  der_file="$(mktemp)"
  plain_file="$(mktemp)"
  encrypted_file="$(mktemp)"
  if ! printf '%s' "${public_key}" | base64 -d > "${der_file}" 2>/dev/null; then
    echo "failed to decode login public key" >&2
    rm -f "${key_file}" "${cipher_file}" "${der_file}" "${plain_file}" "${encrypted_file}"
    return 1
  fi
  printf '%s' "${plain}" > "${plain_file}"
  if ! openssl pkeyutl -encrypt -pubin -keyform DER -inkey "${der_file}" \
    -pkeyopt rsa_padding_mode:oaep \
    -pkeyopt rsa_oaep_md:sha256 \
    -pkeyopt rsa_mgf1_md:sha256 \
    -in "${plain_file}" -out "${encrypted_file}" >/dev/null
  then
    rm -f "${key_file}" "${cipher_file}" "${der_file}" "${plain_file}" "${encrypted_file}"
    return 1
  fi
  base64 -w 0 "${encrypted_file}" > "${cipher_file}"
  cat "${cipher_file}"
  rm -f "${key_file}" "${cipher_file}" "${der_file}" "${plain_file}" "${encrypted_file}"
}

csrf_token() {
  awk '$6 == "csrf_token" { value=$7 } END { print value }' "${COOKIE_JAR}" 2>/dev/null
}

cookie_value() {
  local name="$1"
  awk -v name="${name}" '$6 == name { value=$7 } END { print value }' "${COOKIE_JAR}" 2>/dev/null
}

auth_curl() {
  curl -b "${COOKIE_JAR}" "${AUTH_HEADER[@]}" "$@"
}

run_check "compose-config" docker compose -f "${ROOT_DIR}/${COMPOSE_FILE}" config

if [ "${START_STAGING:-0}" = "1" ]; then
  run_check "ensure-1panel-network" sh -c "docker network inspect 1panel-network >/dev/null 2>&1 || docker network create 1panel-network"
  if [ "${RESET_STAGING:-0}" = "1" ]; then
    run_check "compose-reset" docker compose -f "${ROOT_DIR}/${COMPOSE_FILE}" down -v --remove-orphans
    run_check "reset-profile-mysql" sh -c "docker rm -f lumira-mysql >/dev/null 2>&1 || true; docker volume rm deploy_mysql_data >/dev/null 2>&1 || true"
  fi
  if [ "${BUILD_STAGING:-0}" = "1" ]; then
    run_check "compose-up" docker compose -f "${ROOT_DIR}/${COMPOSE_FILE}" up -d --build mysql redis lumira-server api-proxy lumira-ui
  else
    run_check "compose-up" docker compose -f "${ROOT_DIR}/${COMPOSE_FILE}" up -d mysql redis lumira-server api-proxy lumira-ui
  fi
  if [ "${STAGING_SEED_ADMIN:-0}" = "1" ]; then
    db_ready="false"
    for _ in $(seq 1 60); do
      if docker exec lumira-mysql mysql -uroot -p"${DB_PASSWORD}" saas -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='saas' AND table_name IN ('sys_user','iam_user_credential');" 2>/dev/null | grep -q '^2$'; then
        db_ready="true"
        break
      fi
      sleep 2
    done
    if [ "${db_ready}" = "true" ]; then
      add_result "wait-db-ready" "PASS" "auth credential tables became ready"
    else
      add_result "wait-db-ready" "FAIL" "auth credential tables did not become ready within 120 seconds"
    fi
    admin_hash="${STAGING_ADMIN_BCRYPT_HASH:-\$2a\$10\$dxw53ThYEb3a2eSFWj2jrOY1dkVK6RhbZej.9OLE9PCM3C2z17Vp6}"
    admin_hash_sql="$(printf '%s' "${admin_hash}" | sed 's/\$/\\$/g')"
    COMMANDS+=("docker exec lumira-mysql mysql -uroot -p<redacted> saas -e <seed-staging-admin-password>")
    if output="$(sh -c "docker exec lumira-mysql mysql -uroot -p\"${DB_PASSWORD}\" saas -e \"UPDATE sys_user SET password_hash='${admin_hash_sql}', updated_at=CURRENT_TIMESTAMP WHERE username='admin'; UPDATE iam_user_credential SET credential_secret='${admin_hash_sql}', updated_at=CURRENT_TIMESTAMP WHERE user_id=1001 AND credential_type='PASSWORD';\"" 2>&1)"; then
      add_result "seed-admin-password" "PASS" "staging admin password hash updated"
    else
      add_result "seed-admin-password" "FAIL" "$(echo "$output" | tail -c 500)"
    fi
  fi
fi

if [ "${START_STAGING:-0}" = "1" ]; then
  ready="false"
  for _ in $(seq 1 60); do
    if curl -fsS "${API_BASE_URL}/api/health" >/dev/null 2>&1; then
      ready="true"
      break
    fi
    sleep 2
  done
  if [ "${ready}" = "true" ]; then
    add_result "wait-health-ready" "PASS" "health endpoint became ready"
  else
    add_result "wait-health-ready" "FAIL" "health endpoint did not become ready within 120 seconds"
  fi
fi

run_check "health" curl -fsS "${API_BASE_URL}/api/health"
run_check "version" curl -fsS "${API_BASE_URL}/api/version"
login_password="admin"
if [ "${STAGING_SEED_ADMIN:-0}" = "1" ]; then
  login_password="E2eAdmin123!"
fi
if encrypted_password="$(encrypt_login_password "${STAGING_LOGIN_PASSWORD:-${login_password}}")"; then
  login_username="${STAGING_LOGIN_USERNAME:-admin}"
  login_payload="{\"username\":\"$(json_string "${login_username}")\",\"password\":\"$(json_string "${encrypted_password}")\"}"
  COMMANDS+=("curl -fsS -c ${COOKIE_JAR} -X POST ${API_BASE_URL}/api/v2/auth/login -H Content-Type: application/json -d <encrypted-login-payload>")
  if curl -fsS -c "${COOKIE_JAR}" -X POST "${API_BASE_URL}/api/v2/auth/login" -H "Content-Type: application/json" -d "${STAGING_LOGIN_PAYLOAD:-${login_payload}}" -o "${LOGIN_RESPONSE_FILE}" 2>/tmp/lumira-smoke-login-error; then
    access_token="$(extract_json_field "${LOGIN_RESPONSE_FILE}" "data.accessToken" || true)"
    if [ -n "${access_token}" ]; then
      AUTH_HEADER=(-H "Authorization: Bearer ${access_token}")
    fi
    add_result "login" "PASS" "$(tail -c 500 "${LOGIN_RESPONSE_FILE}")"
  else
    add_result "login" "FAIL" "$(tail -c 500 /tmp/lumira-smoke-login-error 2>/dev/null) $(tail -c 500 "${LOGIN_RESPONSE_FILE}" 2>/dev/null)"
  fi
  rm -f /tmp/lumira-smoke-login-error
else
  add_result "login" "FAIL" "failed to fetch login encryption key or encrypt password"
fi
run_check "current-user" auth_curl -fsS "${API_BASE_URL}/api/v2/auth/current-user"
csrf="$(csrf_token)"
refresh_token_cookie="$(cookie_value refresh_token)"
if [ -n "${csrf}" ] && [ -n "${refresh_token_cookie}" ]; then
  COMMANDS+=("curl -fsS -c <cookie-jar> -X POST ${API_BASE_URL}/api/v2/auth/refresh-token -H Cookie:<redacted-refresh-csrf> -H X-CSRF-Token:<redacted>")
  if output="$(curl -fsS -c "${COOKIE_JAR}" -X POST "${API_BASE_URL}/api/v2/auth/refresh-token" -H "Cookie: refresh_token=${refresh_token_cookie}; csrf_token=${csrf}" -H "X-CSRF-Token: ${csrf}" 2>&1)"; then
    if printf '%s' "${output}" | grep -q '"accessToken"'; then
      add_result "auth-refresh" "PASS" "refresh succeeded and returned a new access token"
    else
      add_result "auth-refresh" "FAIL" "refresh response did not include accessToken"
    fi
  else
    add_result "auth-refresh" "FAIL" "$(echo "$output" | tail -c 500)"
  fi
else
  add_result "auth-refresh" "FAIL" "refresh_token or csrf_token cookie was not set by login"
fi
run_check "file-list" auth_curl -fsS "${API_BASE_URL}/api/v2/files"
run_check "ai-tools-list" auth_curl -fsS "${API_BASE_URL}/api/v2/ai/tools"
run_check "ai-readonly-propose" auth_curl -fsS -X POST "${API_BASE_URL}/api/v2/ai/tools/propose" -H "Content-Type: application/json" -d '{"toolCode":"system.user.search","arguments":{"keyword":"admin","limit":1},"message":"staging smoke read-only user search"}'
run_expect_http_status "payment-webhook-invalid-signature" "400,401,403" curl -sS -X POST "${API_BASE_URL}/api/v2/payment/webhooks/stripe" -H "Content-Type: application/json" -H "Stripe-Signature: invalid" -d '{"appId":"staging-smoke","id":"evt_staging_smoke","type":"payment_intent.succeeded"}'
run_expect_http_status "plugin-gateway-without-permission" "401,403" curl -sS "${API_BASE_URL}/api/p/smoke/health"

GIT_COMMIT_SHA="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
COMMANDS_JSON="$(printf '%s\n' "${COMMANDS[@]:-}" | sed '/^$/d' | json_array)"
FAILED_JSON="$(printf '%s\n' "${FAILED_STEPS[@]:-}" | sed '/^$/d' | json_array)"
RESULTS_JSON="$(printf '[%s]' "$(IFS=,; echo "${RESULTS[*]}")")"
cat > "${OUT_FILE}" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "gitCommitSha": "${GIT_COMMIT_SHA}",
  "composeFile": "${COMPOSE_FILE}",
  "envProfile": "${ENV_PROFILE:-staging}",
  "commands": ${COMMANDS_JSON},
  "results": ${RESULTS_JSON},
  "failedSteps": ${FAILED_JSON},
  "status": "${STATUS}"
}
EOF

[ "${STATUS}" = "PASS" ]
