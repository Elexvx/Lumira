#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/artifacts/migration"
OUT_FILE="${OUT_DIR}/migration-check-evidence.json"
COMMIT_SHA="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
MYSQL_CONTAINER="lumira-migration-check-mysql-$$"
NETWORK="lumira-migration-check-$$"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-LumiraMigrationCheck_ChangeMe_123456!}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
STATUS="PASS"
FAILED_STEPS=()
COMMANDS=()

mkdir -p "${OUT_DIR}"

cleanup() {
  if [ "${KEEP_MIGRATION_DB:-0}" = "1" ]; then
    return
  fi
  docker rm -f "${MYSQL_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

record_fail() {
  STATUS="FAIL"
  FAILED_STEPS+=("$1")
}

run_cmd() {
  COMMANDS+=("$*")
  "$@"
}

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

docker network create "${NETWORK}" >/dev/null
run_cmd docker run -d --name "${MYSQL_CONTAINER}" --network "${NETWORK}" \
  -e MYSQL_ROOT_PASSWORD="${MYSQL_PASSWORD}" \
  -e MYSQL_DATABASE=lumira_migration_check \
  "${MYSQL_IMAGE}" >/dev/null

for i in {1..60}; do
  if docker exec "${MYSQL_CONTAINER}" mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_PASSWORD}" --silent >/dev/null 2>&1; then
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    record_fail "mysql-ready-timeout"
  fi
done

MIGRATION_ROOT="${ROOT_DIR}/lumira-backend/services"
mysql_exec() {
  if [[ "${1:-}" == -* ]]; then
    docker exec "${MYSQL_CONTAINER}" mysql -h127.0.0.1 -uroot -p"${MYSQL_PASSWORD}" "$@"
    return
  fi
  local database="$1"
  shift
  docker exec "${MYSQL_CONTAINER}" mysql -h127.0.0.1 -uroot -p"${MYSQL_PASSWORD}" "$@" "${database}"
}

mysql_file_exec() {
  local database="$1"
  local file="$2"
  docker exec -i "${MYSQL_CONTAINER}" mysql -h127.0.0.1 -uroot -p"${MYSQL_PASSWORD}" "${database}" < "${file}"
}

migration_files() {
  local roots=("${MIGRATION_ROOT}/lumira-system/src/main/resources/db/migration")
  if [ "${MIGRATION_SCOPE:-system}" = "all" ]; then
    roots+=(
      "${MIGRATION_ROOT}/lumira-auth/src/main/resources/db/migration/auth"
      "${MIGRATION_ROOT}/lumira-ai/src/main/resources/db/migration/ai"
      "${MIGRATION_ROOT}/lumira-file/src/main/resources/db/migration/file"
      "${MIGRATION_ROOT}/lumira-payment/src/main/resources/db/migration/payment"
      "${MIGRATION_ROOT}/lumira-plugin/src/main/resources/db/migration/plugin"
      "${MIGRATION_ROOT}/lumira-localization/src/main/resources/db/migration/localization"
      "${MIGRATION_ROOT}/lumira-message/src/main/resources/db/migration/message"
    )
  fi
  find "${roots[@]}" -maxdepth 1 -type f -name 'V*__*.sql' | sort -V
}

apply_migrations() {
  local database="$1"
  mysql_exec "${database}" -e "CREATE TABLE IF NOT EXISTS flyway_schema_history (installed_rank INT AUTO_INCREMENT PRIMARY KEY, version VARCHAR(50), description VARCHAR(200), script VARCHAR(255) NOT NULL UNIQUE, installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP, success BOOLEAN NOT NULL DEFAULT TRUE);" || return 1
  local file base version description applied tmp script_key
  while IFS= read -r file; do
    [ -z "${file}" ] && continue
    base="$(basename "${file}")"
    script_key="${file#${ROOT_DIR}/}"
    version="${base#V}"
    version="${version%%__*}"
    description="${base#*__}"
    description="${description%.sql}"
    applied="$(mysql_exec "${database}" -N -e "SELECT COUNT(*) FROM flyway_schema_history WHERE script='${script_key}' AND success=TRUE;" | tr -d '\r')"
    if [ "${applied}" = "1" ]; then
      continue
    fi
    if ! mysql_file_exec "${database}" "${file}"; then
      echo "Migration failed: ${file}" >&2
      return 1
    fi
    mysql_exec "${database}" -e "INSERT INTO flyway_schema_history(version, description, script, success) VALUES ('${version}', '${description}', '${script_key}', TRUE);" || return 1
  done < <(migration_files)
}

mysql_exec -e "DROP DATABASE IF EXISTS lumira_fresh; CREATE DATABASE lumira_fresh CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ! run_cmd apply_migrations lumira_fresh; then
  record_fail "fresh-migration"
fi
if ! run_cmd apply_migrations lumira_fresh; then
  record_fail "repeat-migration"
fi

mysql_exec -e "DROP DATABASE IF EXISTS lumira_upgrade; CREATE DATABASE lumira_upgrade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql_exec lumira_upgrade -e "CREATE TABLE legacy_release_marker (id BIGINT PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP); INSERT INTO legacy_release_marker(id) VALUES (1);"
if ! run_cmd apply_migrations lumira_upgrade; then
  record_fail "upgrade-migration"
fi

CHECK_SQL="
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lumira_fresh' AND table_name IN ('ai_employee_tool_grant','payment_provider_config','security_audit_event');
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='lumira_fresh' AND table_name='file_processing_task' AND column_name='claim_token';
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='lumira_fresh' AND table_name='platform_event_outbox' AND column_name='claim_token';
SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='lumira_fresh' AND table_name IN ('file_processing_task','platform_event_outbox','security_audit_event');
"
CHECK_OUTPUT="$(mysql_exec lumira_fresh -N -e "${CHECK_SQL}" || true)"
CRITICAL_TABLE_COUNT="$(echo "${CHECK_OUTPUT}" | sed -n '1p' | tr -d '\r')"
FILE_CLAIM_COUNT="$(echo "${CHECK_OUTPUT}" | sed -n '2p' | tr -d '\r')"
OUTBOX_CLAIM_COUNT="$(echo "${CHECK_OUTPUT}" | sed -n '3p' | tr -d '\r')"
CRITICAL_INDEX_COUNT="$(echo "${CHECK_OUTPUT}" | sed -n '4p' | tr -d '\r')"
if [ "${CRITICAL_TABLE_COUNT}" != "3" ]; then record_fail "critical-tables"; fi
if [ "${FILE_CLAIM_COUNT}" != "1" ]; then record_fail "file-processing-claim-token"; fi
if [ "${OUTBOX_CLAIM_COUNT}" != "1" ]; then record_fail "outbox-claim-token"; fi
if [ "${CRITICAL_INDEX_COUNT:-0}" = "0" ]; then record_fail "critical-indexes"; fi

COMMANDS_JSON="$(printf '%s\n' "${COMMANDS[@]:-}" | sed '/^$/d' | json_array)"
FAILED_JSON="$(printf '%s\n' "${FAILED_STEPS[@]:-}" | sed '/^$/d' | json_array)"
cat > "${OUT_FILE}" <<EOF
{
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "gitCommitSha": "${COMMIT_SHA}",
  "status": "${STATUS}",
  "checks": ["fresh migration","upgrade migration","repeat migration","critical tables","critical columns","critical indexes"],
  "counts": {
    "criticalTables": "${CRITICAL_TABLE_COUNT:-}",
    "fileProcessingClaimToken": "${FILE_CLAIM_COUNT:-}",
    "platformOutboxClaimToken": "${OUTBOX_CLAIM_COUNT:-}",
    "criticalIndexes": "${CRITICAL_INDEX_COUNT:-}"
  },
  "commands": ${COMMANDS_JSON},
  "failedSteps": ${FAILED_JSON}
}
EOF

[ "${STATUS}" = "PASS" ]
