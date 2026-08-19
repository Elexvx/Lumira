#!/usr/bin/env bash
set -euo pipefail

umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENV_FILE="${ROOT_DIR}/deploy/.env"
DRY_RUN="${DRY_RUN:-0}"

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

load_env_file() {
  local file="$1"
  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" != *=* ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    # Explicit caller environment is authoritative for one-shot safeguards.
    if ! declare -p "${key}" >/dev/null 2>&1; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done < "${file}"
}

[[ -f "${ENV_FILE}" ]] || die "deploy/.env not found. Run the deployment initializer first."
load_env_file "${ENV_FILE}"

BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/lumira}"
BACKUP_ID="${BACKUP_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-0}"
BACKUP_UPLOAD_HOOK="${BACKUP_UPLOAD_HOOK:-}"
BACKUP_METRICS_FILE="${BACKUP_METRICS_FILE:-}"
BACKUP_ALLOW_EMPTY_DATABASE="${BACKUP_ALLOW_EMPTY_DATABASE:-0}"
BACKUP_REDIS="${BACKUP_REDIS:-1}"
BACKUP_MYSQL_READY_ATTEMPTS="${BACKUP_MYSQL_READY_ATTEMPTS:-12}"
BACKUP_MYSQL_READY_INTERVAL_SECONDS="${BACKUP_MYSQL_READY_INTERVAL_SECONDS:-5}"
BACKUP_REDIS_READY_ATTEMPTS="${BACKUP_REDIS_READY_ATTEMPTS:-12}"
BACKUP_REDIS_READY_INTERVAL_SECONDS="${BACKUP_REDIS_READY_INTERVAL_SECONDS:-5}"

[[ "${BACKUP_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || die "BACKUP_ID contains unsupported characters."
[[ "${BACKUP_RETENTION_DAYS}" =~ ^[0-9]+$ ]] || die "BACKUP_RETENTION_DAYS must be a non-negative integer."
[[ "${BACKUP_MYSQL_READY_ATTEMPTS}" =~ ^[1-9][0-9]*$ ]] || die "BACKUP_MYSQL_READY_ATTEMPTS must be a positive integer."
[[ "${BACKUP_MYSQL_READY_INTERVAL_SECONDS}" =~ ^[0-9]+$ ]] || die "BACKUP_MYSQL_READY_INTERVAL_SECONDS must be a non-negative integer."
[[ "${BACKUP_REDIS_READY_ATTEMPTS}" =~ ^[1-9][0-9]*$ ]] || die "BACKUP_REDIS_READY_ATTEMPTS must be a positive integer."
[[ "${BACKUP_REDIS_READY_INTERVAL_SECONDS}" =~ ^[0-9]+$ ]] || die "BACKUP_REDIS_READY_INTERVAL_SECONDS must be a non-negative integer."
[[ -n "${BACKUP_ROOT}" && "${BACKUP_ROOT}" != "/" ]] || die "BACKUP_ROOT must not be empty or '/'."

COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.prod.yml"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
MYSQL_CLIENT_IMAGE="${MYSQL_CLIENT_IMAGE:-mysql:8.4}"
DB_BACKUP_NETWORK="${DB_BACKUP_NETWORK:-1panel-network}"
DB_URL="${DB_URL:-}"

MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-}}"
DB_HOST="${DB_HOST:-}"
DB_PORT="${DB_PORT:-}"
if [[ "${DB_URL}" == jdbc:mysql://* ]]; then
  DB_ENDPOINT="${DB_URL#jdbc:mysql://}"
  DB_AUTHORITY="${DB_ENDPOINT%%/*}"
  DB_PATH="${DB_ENDPOINT#*/}"
  [[ -n "${DB_HOST}" ]] || DB_HOST="${DB_AUTHORITY%%:*}"
  if [[ -z "${DB_PORT}" ]]; then
    DB_PORT="${DB_AUTHORITY##*:}"
    [[ "${DB_PORT}" == "${DB_AUTHORITY}" ]] && DB_PORT=3306
  fi
  if [[ -z "${MYSQL_DATABASE}" && "${DB_PATH}" != "${DB_ENDPOINT}" ]]; then
    MYSQL_DATABASE="${DB_PATH%%\?*}"
  fi
fi
MYSQL_DATABASE="${MYSQL_DATABASE:-saas}"
DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
MYSQL_USER="${MYSQL_BACKUP_USERNAME:-${MYSQL_USER:-${DB_USERNAME:-${DB_USER:-root}}}}"
MYSQL_PASSWORD="${MYSQL_BACKUP_PASSWORD:-${MYSQL_PASSWORD:-${DB_PASSWORD:-}}}"
MYSQL_SSL_MODE="${MYSQL_SSL_MODE:-}"
MYSQL_SSL_CA_FILE="${MYSQL_SSL_CA_FILE:-}"

if [[ -z "${MYSQL_SSL_MODE}" ]]; then
  if [[ "${DB_URL}" =~ [\?\&]sslMode=([^\&]+) ]]; then
    MYSQL_SSL_MODE="${BASH_REMATCH[1]}"
  elif [[ "${DB_URL}" =~ [\?\&]useSSL=false ]]; then
    MYSQL_SSL_MODE="DISABLED"
  elif [[ "${DB_URL}" =~ [\?\&]useSSL=true ]]; then
    MYSQL_SSL_MODE="REQUIRED"
  else
    MYSQL_SSL_MODE="PREFERRED"
  fi
fi
MYSQL_SSL_MODE="${MYSQL_SSL_MODE^^}"

[[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || die "MySQL database name must contain only letters, digits, or underscores."
[[ "${DB_PORT}" =~ ^[0-9]+$ ]] || die "MySQL port must be numeric."
[[ -n "${MYSQL_USER}" ]] || die "MySQL backup username is required."
[[ "${MYSQL_SSL_MODE}" =~ ^(DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY)$ ]] || die "MYSQL_SSL_MODE must be DISABLED, PREFERRED, REQUIRED, VERIFY_CA, or VERIFY_IDENTITY."
if [[ -n "${MYSQL_SSL_CA_FILE}" ]]; then
  if [[ "${MYSQL_SSL_CA_FILE}" != /* ]]; then
    MYSQL_SSL_CA_FILE="${ROOT_DIR}/${MYSQL_SSL_CA_FILE}"
  fi
  [[ -f "${MYSQL_SSL_CA_FILE}" && -r "${MYSQL_SSL_CA_FILE}" ]] || die "MYSQL_SSL_CA_FILE must reference a readable CA file."
fi

if is_true "${DRY_RUN}"; then
  if [[ "${BACKUP_ROOT}" == /* ]]; then
    PLANNED_ROOT="${BACKUP_ROOT}"
  else
    PLANNED_ROOT="${PWD}/${BACKUP_ROOT}"
  fi
  echo "[dry-run] Validate MySQL connectivity and source metadata for database ${MYSQL_DATABASE}."
  echo "[dry-run] Create a logical MySQL dump, authenticated Redis RDB, manifest.json, SHA256SUMS, and .complete."
  echo "[dry-run] No database, Redis, filesystem, retention, metrics, or upload writes were performed."
  echo "Backup dry run completed (no backup created): ${PLANNED_ROOT}/${BACKUP_ID}"
  exit 0
fi

command -v docker >/dev/null 2>&1 || die "docker is required to create a production backup."
command -v sha256sum >/dev/null 2>&1 || die "sha256sum is required to create backup checksums."
command -v tar >/dev/null 2>&1 || die "tar is required to archive file storage."

mkdir -p -- "${BACKUP_ROOT}"
BACKUP_ROOT="$(cd "${BACKUP_ROOT}" && pwd -P)"
[[ "${BACKUP_ROOT}" != "/" ]] || die "Resolved BACKUP_ROOT must not be '/'."
OUT_DIR="${BACKUP_ROOT}/${BACKUP_ID}"
STAGING_DIR="${BACKUP_ROOT}/.${BACKUP_ID}.incomplete.$$"
[[ ! -e "${OUT_DIR}" ]] || die "Backup destination already exists: ${OUT_DIR}"
[[ ! -e "${STAGING_DIR}" ]] || die "Backup staging directory already exists: ${STAGING_DIR}"
mkdir -m 700 -- "${STAGING_DIR}"

cleanup_staging() {
  if [[ -n "${STAGING_DIR:-}" && -d "${STAGING_DIR}" && "${STAGING_DIR}" == "${BACKUP_ROOT}/."*.incomplete.* ]]; then
    rm -rf -- "${STAGING_DIR}"
  fi
}
trap cleanup_staging EXIT
trap 'cleanup_staging; exit 130' INT
trap 'cleanup_staging; exit 143' TERM

MYSQL_MODE=external
MYSQL_CONTAINER_ID="$(docker compose -f "${COMPOSE_FILE}" ps -q "${MYSQL_SERVICE}" 2>/dev/null || true)"
if [[ -n "${MYSQL_CONTAINER_ID}" ]] \
  && [[ "$(docker inspect -f '{{.State.Running}}' "${MYSQL_CONTAINER_ID}" 2>/dev/null || true)" == "true" ]] \
  && [[ "${DB_HOST}" == "${MYSQL_SERVICE}" || "${DB_HOST}" == "mysql" || "${DB_HOST}" == "lumira-mysql" ]]; then
  MYSQL_MODE=compose
fi

declare -a MYSQL_TLS_ARGS=("--ssl-mode=${MYSQL_SSL_MODE}")
declare -a MYSQL_DOCKER_MOUNT_ARGS=()
if [[ -n "${MYSQL_SSL_CA_FILE}" ]]; then
  [[ "${MYSQL_MODE}" != "compose" ]] || die "MYSQL_SSL_CA_FILE is for the external MySQL client container; the local compose MySQL service does not need a host CA mount."
  MYSQL_SSL_CA_FILE="$(cd "$(dirname "${MYSQL_SSL_CA_FILE}")" && pwd -P)/$(basename "${MYSQL_SSL_CA_FILE}")"
  MYSQL_DOCKER_MOUNT_ARGS=(-v "${MYSQL_SSL_CA_FILE}:/run/lumira/mysql-ca.pem:ro")
  MYSQL_TLS_ARGS+=("--ssl-ca=/run/lumira/mysql-ca.pem")
fi
if [[ "${MYSQL_MODE}" != "compose" && "${MYSQL_SSL_MODE}" == "DISABLED" ]]; then
  echo "WARNING: TLS is explicitly disabled for the external MySQL backup connection. Set MYSQL_SSL_MODE=VERIFY_IDENTITY for managed MySQL." >&2
fi

mysql_client() {
  local database="$1"
  shift
  if [[ "${MYSQL_MODE}" == "compose" ]]; then
    # The official image uses a temporary --skip-networking server while it
    # initializes a fresh data directory. TCP avoids treating that short-lived
    # socket-only server as the final migration target.
    if [[ -n "${database}" ]]; then
      MYSQL_PWD="${MYSQL_PASSWORD}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD "${MYSQL_SERVICE}" \
        mysql --batch --raw --skip-column-names --protocol=TCP --get-server-public-key -h127.0.0.1 -P3306 \
        "${MYSQL_TLS_ARGS[@]}" -u"${MYSQL_USER}" "$@" "${database}"
    else
      MYSQL_PWD="${MYSQL_PASSWORD}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD "${MYSQL_SERVICE}" \
        mysql --batch --raw --skip-column-names --protocol=TCP --get-server-public-key -h127.0.0.1 -P3306 \
        "${MYSQL_TLS_ARGS[@]}" -u"${MYSQL_USER}" "$@"
    fi
  else
    if [[ -n "${database}" ]]; then
      MYSQL_PWD="${MYSQL_PASSWORD}" docker run --rm --network "${DB_BACKUP_NETWORK}" \
        --add-host host.docker.internal:host-gateway -e MYSQL_PWD "${MYSQL_DOCKER_MOUNT_ARGS[@]}" "${MYSQL_CLIENT_IMAGE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -h"${DB_HOST}" -P"${DB_PORT}" -u"${MYSQL_USER}" \
        "$@" "${database}"
    else
      MYSQL_PWD="${MYSQL_PASSWORD}" docker run --rm --network "${DB_BACKUP_NETWORK}" \
        --add-host host.docker.internal:host-gateway -e MYSQL_PWD "${MYSQL_DOCKER_MOUNT_ARGS[@]}" "${MYSQL_CLIENT_IMAGE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -h"${DB_HOST}" -P"${DB_PORT}" -u"${MYSQL_USER}" "$@"
    fi
  fi
}

mysql_dump() {
  local output="$1"
  if [[ "${MYSQL_MODE}" == "compose" ]]; then
    MYSQL_PWD="${MYSQL_PASSWORD}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD "${MYSQL_SERVICE}" \
      mysqldump --single-transaction --quick --routines --triggers --events --hex-blob \
      --default-character-set=utf8mb4 --no-tablespaces --set-gtid-purged=OFF \
      --protocol=TCP --get-server-public-key -h127.0.0.1 -P3306 "${MYSQL_TLS_ARGS[@]}" \
      -u"${MYSQL_USER}" "${MYSQL_DATABASE}" > "${output}"
  else
    MYSQL_PWD="${MYSQL_PASSWORD}" docker run --rm --network "${DB_BACKUP_NETWORK}" \
      --add-host host.docker.internal:host-gateway -e MYSQL_PWD "${MYSQL_DOCKER_MOUNT_ARGS[@]}" "${MYSQL_CLIENT_IMAGE}" \
      mysqldump --single-transaction --quick --routines --triggers --events --hex-blob \
      --default-character-set=utf8mb4 --no-tablespaces --set-gtid-purged=OFF \
      "${MYSQL_TLS_ARGS[@]}" -h"${DB_HOST}" -P"${DB_PORT}" \
      -u"${MYSQL_USER}" "${MYSQL_DATABASE}" > "${output}"
  fi
}

mysql_scalar_required() {
  local database="$1"
  local query="$2"
  local result
  result="$(mysql_client "${database}" --execute "${query}")" || return 1
  printf '%s' "${result%%$'\n'*}"
}

mysql_scalar_optional() {
  local database="$1"
  local query="$2"
  local result
  if result="$(mysql_client "${database}" --execute "${query}" 2>/dev/null)"; then
    result="${result%%$'\n'*}"
    [[ "${result}" != "NULL" ]] && printf '%s' "${result}"
  fi
}

echo "Validating MySQL source and collecting metadata..."
TABLE_COUNT=""
MYSQL_READY_ERROR="${STAGING_DIR}/.mysql-ready-error"
for ((attempt = 1; attempt <= BACKUP_MYSQL_READY_ATTEMPTS; attempt++)); do
  if TABLE_COUNT="$(mysql_scalar_required "${MYSQL_DATABASE}" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';" 2>"${MYSQL_READY_ERROR}")"; then
    break
  fi
  MYSQL_ERROR="$(<"${MYSQL_READY_ERROR}")"
  # docker compose exec can exit without stderr when the official image stops
  # its socket-only initialization server. Treat only that empty result and
  # explicit connection/readiness errors as transient; authentication,
  # permission, and SQL errors still fail closed below.
  if [[ -z "${MYSQL_ERROR//[[:space:]]/}" ]] || [[ "${MYSQL_ERROR}" =~ ERROR[[:space:]]+(2002|2003|2005|2013|1049) ]] || [[ "${MYSQL_ERROR}" =~ ([Cc]an.t[[:space:]]connect|[Cc]onnection[[:space:]]refused|server[[:space:]]has[[:space:]]gone[[:space:]]away|is[[:space:]]restarting|is[[:space:]]not[[:space:]]running) ]]; then
    if [[ "${attempt}" -lt "${BACKUP_MYSQL_READY_ATTEMPTS}" ]]; then
      echo "MySQL is not ready yet (${attempt}/${BACKUP_MYSQL_READY_ATTEMPTS}); retrying..."
      sleep "${BACKUP_MYSQL_READY_INTERVAL_SECONDS}"
      continue
    fi
  else
    printf '%s\n' "${MYSQL_ERROR}" >&2
    die "MySQL metadata query failed with a non-readiness error; not retrying authentication, permission, or SQL failures."
  fi
done
rm -f -- "${MYSQL_READY_ERROR}"
[[ -n "${TABLE_COUNT}" ]] || die "MySQL did not become ready after ${BACKUP_MYSQL_READY_ATTEMPTS} attempts."
[[ "${TABLE_COUNT}" =~ ^[0-9]+$ ]] || die "MySQL returned an invalid table count."
if [[ "${TABLE_COUNT}" == "0" ]] && ! is_true "${BACKUP_ALLOW_EMPTY_DATABASE}"; then
  die "Database ${MYSQL_DATABASE} contains no base tables. Set BACKUP_ALLOW_EMPTY_DATABASE=1 only for a confirmed first deployment."
fi

SERVER_VERSION="$(mysql_scalar_optional "" "SELECT @@version;")"
SERVER_UUID="$(mysql_scalar_optional "" "SELECT @@server_uuid;")"
GTID_EXECUTED="$(mysql_scalar_optional "" "SELECT @@GLOBAL.gtid_executed;")"
DATABASE_VERSION="$(mysql_scalar_optional "${MYSQL_DATABASE}" "SELECT config_value FROM sys_config WHERE config_key = 'platform.database.version' AND deleted = 0 LIMIT 1;")"

BINLOG_STATUS=""
if BINLOG_STATUS="$(mysql_client "" --execute "SHOW BINARY LOG STATUS;" 2>/dev/null)"; then
  :
elif BINLOG_STATUS="$(mysql_client "" --execute "SHOW MASTER STATUS;" 2>/dev/null)"; then
  :
else
  BINLOG_STATUS=""
fi
BINLOG_STATUS="${BINLOG_STATUS%%$'\n'*}"
IFS=$'\t' read -r BINLOG_FILE BINLOG_POSITION _ <<< "${BINLOG_STATUS}"
BINLOG_FILE="${BINLOG_FILE:-}"
BINLOG_POSITION="${BINLOG_POSITION:-}"

SCHEMA_FINGERPRINT="$(
  mysql_client "" --execute "SELECT CONCAT_WS(CHAR(9), table_name, ordinal_position, column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), extra) FROM information_schema.columns WHERE table_schema = '${MYSQL_DATABASE}' ORDER BY table_name, ordinal_position;" \
    | sha256sum | awk '{print $1}'
)" || die "Unable to calculate the MySQL schema fingerprint."
[[ "${SCHEMA_FINGERPRINT}" =~ ^[0-9a-f]{64}$ ]] || die "Unable to calculate a valid MySQL schema fingerprint."

MYSQL_DUMP_REL="mysql-${MYSQL_DATABASE}.sql"
MYSQL_DUMP_PATH="${STAGING_DIR}/${MYSQL_DUMP_REL}"
echo "Creating MySQL logical backup..."
mysql_dump "${MYSQL_DUMP_PATH}"
[[ -s "${MYSQL_DUMP_PATH}" ]] || die "MySQL dump is empty."
chmod 600 "${MYSQL_DUMP_PATH}"

declare -a ARTIFACT_PATHS=("${MYSQL_DUMP_REL}")

REDIS_REL=""
if is_true "${BACKUP_REDIS}"; then
  echo "Creating authenticated Redis backup..."
  REDIS_CONTAINER_ID=""
  REDIS_READY=0
  REDIS_READY_ERROR="${STAGING_DIR}/.redis-ready-error"
  for ((attempt = 1; attempt <= BACKUP_REDIS_READY_ATTEMPTS; attempt++)); do
    REDIS_CONTAINER_ID="$(docker compose -f "${COMPOSE_FILE}" ps -q "${REDIS_SERVICE}" 2>/dev/null || true)"
    if [[ -n "${REDIS_CONTAINER_ID}" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "${REDIS_CONTAINER_ID}" 2>/dev/null || true)" == "true" ]]; then
      if REDIS_PING="$(docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" sh -eu -c '
        if [ -n "${REDIS_PASSWORD:-}" ]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; fi
        redis-cli --no-auth-warning --raw PING
      ' 2>"${REDIS_READY_ERROR}")" && [[ "${REDIS_PING}" == "PONG" ]]; then
        REDIS_READY=1
        break
      fi
      REDIS_ERROR="$(<"${REDIS_READY_ERROR}") ${REDIS_PING:-}"
      if [[ "${REDIS_ERROR}" =~ (WRONGPASS|NOAUTH|AUTH[[:space:]]failed) ]]; then
        printf '%s\n' "${REDIS_ERROR}" >&2
        die "Redis authentication failed; not retrying an invalid credential."
      fi
    fi
    if [[ "${attempt}" -lt "${BACKUP_REDIS_READY_ATTEMPTS}" ]]; then
      echo "Redis is not ready yet (${attempt}/${BACKUP_REDIS_READY_ATTEMPTS}); retrying..."
      sleep "${BACKUP_REDIS_READY_INTERVAL_SECONDS}"
    fi
  done
  rm -f -- "${REDIS_READY_ERROR}"
  [[ "${REDIS_READY}" == "1" ]] || die "Redis did not become ready after ${BACKUP_REDIS_READY_ATTEMPTS} attempts. Set BACKUP_REDIS=0 only when Redis is intentionally out of scope."
  REDIS_CONTAINER_BACKUP="/data/.lumira-backup-${BACKUP_ID}.rdb"
  cleanup_redis_temp() {
    docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" rm -f -- "${REDIS_CONTAINER_BACKUP}" >/dev/null 2>&1 || true
  }
  cleanup_redis_and_staging() {
    cleanup_redis_temp
    cleanup_staging
  }
  trap cleanup_redis_and_staging EXIT
  trap 'cleanup_redis_and_staging; exit 130' INT
  trap 'cleanup_redis_and_staging; exit 143' TERM
  docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" sh -eu -c '
    if [ -n "${REDIS_PASSWORD:-}" ]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; fi
    redis-cli --no-auth-warning PING >/dev/null
    redis-cli --no-auth-warning --rdb "$1"
    redis-check-rdb "$1" >/dev/null
  ' -- "${REDIS_CONTAINER_BACKUP}"
  REDIS_REL="redis-dump.rdb"
  docker compose -f "${COMPOSE_FILE}" cp "${REDIS_SERVICE}:${REDIS_CONTAINER_BACKUP}" "${STAGING_DIR}/${REDIS_REL}"
  cleanup_redis_temp
  chmod 600 "${STAGING_DIR}/${REDIS_REL}"
  ARTIFACT_PATHS+=("${REDIS_REL}")
fi

echo "Archiving uploaded files and plugins..."
FILE_STORAGE_REL=""
if [[ -d "${ROOT_DIR}/data/uploads" ]]; then
  FILE_STORAGE_REL="file-storage.tgz"
  tar -C "${ROOT_DIR}" -czf "${STAGING_DIR}/${FILE_STORAGE_REL}" data/uploads
  chmod 600 "${STAGING_DIR}/${FILE_STORAGE_REL}"
  ARTIFACT_PATHS+=("${FILE_STORAGE_REL}")
else
  echo "Skipping uploaded files archive: data/uploads not found"
fi

PLUGIN_STORAGE_REL=""
if [[ -d "${ROOT_DIR}/data/plugins" ]]; then
  PLUGIN_STORAGE_REL="plugin-storage.tgz"
  tar -C "${ROOT_DIR}" -czf "${STAGING_DIR}/${PLUGIN_STORAGE_REL}" data/plugins
  chmod 600 "${STAGING_DIR}/${PLUGIN_STORAGE_REL}"
  ARTIFACT_PATHS+=("${PLUGIN_STORAGE_REL}")
else
  echo "Skipping plugin files archive: data/plugins not found"
fi

declare -a ARTIFACT_HASHES=()
declare -a ARTIFACT_SIZES=()
: > "${STAGING_DIR}/SHA256SUMS"
for relative_path in "${ARTIFACT_PATHS[@]}"; do
  artifact_hash="$(sha256sum "${STAGING_DIR}/${relative_path}" | awk '{print $1}')"
  artifact_size="$(wc -c < "${STAGING_DIR}/${relative_path}" | tr -d '[:space:]')"
  [[ "${artifact_hash}" =~ ^[0-9a-f]{64}$ ]] || die "Invalid SHA-256 for ${relative_path}."
  [[ "${artifact_size}" =~ ^[0-9]+$ ]] || die "Invalid size for ${relative_path}."
  ARTIFACT_HASHES+=("${artifact_hash}")
  ARTIFACT_SIZES+=("${artifact_size}")
  printf '%s  %s\n' "${artifact_hash}" "${relative_path}" >> "${STAGING_DIR}/SHA256SUMS"
done
chmod 600 "${STAGING_DIR}/SHA256SUMS"

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "${value}"
}

json_string_or_null() {
  if [[ -n "${1:-}" ]]; then
    printf '"%s"' "$(json_escape "$1")"
  else
    printf 'null'
  fi
}

artifact_json_or_null() {
  local relative_path="$1"
  local index
  if [[ -z "${relative_path}" ]]; then
    printf 'null'
    return
  fi
  for index in "${!ARTIFACT_PATHS[@]}"; do
    if [[ "${ARTIFACT_PATHS[$index]}" == "${relative_path}" ]]; then
      printf '{"path":"%s","sha256":"%s","size":%s}' \
        "$(json_escape "${relative_path}")" "${ARTIFACT_HASHES[$index]}" "${ARTIFACT_SIZES[$index]}"
      return
    fi
  done
  die "Artifact metadata missing for ${relative_path}."
}

CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
MYSQL_JSON="$(printf '{"path":"%s","sha256":"%s","size":%s,"serverVersion":%s,"serverUuid":%s,"gtidExecuted":%s,"binlogFile":%s,"binlogPosition":%s,"databaseVersion":%s,"tableCount":%s,"schemaFingerprint":"%s"}' \
  "$(json_escape "${MYSQL_DUMP_REL}")" "${ARTIFACT_HASHES[0]}" "${ARTIFACT_SIZES[0]}" \
  "$(json_string_or_null "${SERVER_VERSION}")" "$(json_string_or_null "${SERVER_UUID}")" \
  "$(json_string_or_null "${GTID_EXECUTED}")" "$(json_string_or_null "${BINLOG_FILE}")" \
  "$(json_string_or_null "${BINLOG_POSITION}")" "$(json_string_or_null "${DATABASE_VERSION}")" \
  "${TABLE_COUNT}" "${SCHEMA_FINGERPRINT}")"
REDIS_JSON="$(artifact_json_or_null "${REDIS_REL}")"
FILE_STORAGE_JSON="$(artifact_json_or_null "${FILE_STORAGE_REL}")"
PLUGIN_STORAGE_JSON="$(artifact_json_or_null "${PLUGIN_STORAGE_REL}")"

cat > "${STAGING_DIR}/manifest.json" <<EOF
{
  "schemaVersion": 1,
  "status": "complete",
  "secretsIncluded": false,
  "backupId": "$(json_escape "${BACKUP_ID}")",
  "createdAt": "${CREATED_AT}",
  "databaseName": "$(json_escape "${MYSQL_DATABASE}")",
  "mysql": ${MYSQL_JSON},
  "redis": ${REDIS_JSON},
  "fileStorage": ${FILE_STORAGE_JSON},
  "pluginStorage": ${PLUGIN_STORAGE_JSON}
}
EOF
chmod 600 "${STAGING_DIR}/manifest.json"
: > "${STAGING_DIR}/.complete"
chmod 600 "${STAGING_DIR}/.complete"

mv -- "${STAGING_DIR}" "${OUT_DIR}"
STAGING_DIR=""
trap - EXIT INT TERM

apply_retention() {
  local retention_days="$1"
  local candidate candidate_real
  [[ "${retention_days}" -gt 0 ]] || return 0
  while IFS= read -r -d '' candidate; do
    [[ "$(basename "${candidate}")" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || continue
    [[ -f "${candidate}/.complete" && -f "${candidate}/manifest.json" ]] || continue
    candidate_real="$(cd "${candidate}" && pwd -P)"
    [[ "${candidate_real}" == "${BACKUP_ROOT}/"* && "${candidate_real}" != "${OUT_DIR}" ]] || continue
    rm -rf -- "${candidate_real}"
  done < <(find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime "+$((retention_days - 1))" -print0)
}

apply_retention "${BACKUP_RETENTION_DAYS}"

if [[ -n "${BACKUP_UPLOAD_HOOK}" ]]; then
  command -v "${BACKUP_UPLOAD_HOOK}" >/dev/null 2>&1 || die "BACKUP_UPLOAD_HOOK is not executable or not on PATH."
  "${BACKUP_UPLOAD_HOOK}" "${OUT_DIR}" "${OUT_DIR}/manifest.json"
fi

if [[ -n "${BACKUP_METRICS_FILE}" ]]; then
  METRICS_WAS_RELATIVE=0
  if [[ "${BACKUP_METRICS_FILE}" != /* ]]; then
    METRICS_WAS_RELATIVE=1
    [[ "/${BACKUP_METRICS_FILE}/" != *"/../"* ]] || die "Relative BACKUP_METRICS_FILE must not contain '..' path segments."
    METRICS_COMPONENT_ROOT="${ROOT_DIR}"
    IFS='/' read -r -a METRICS_COMPONENTS <<< "$(dirname "${BACKUP_METRICS_FILE}")"
    for component in "${METRICS_COMPONENTS[@]}"; do
      [[ -z "${component}" || "${component}" == "." ]] && continue
      METRICS_COMPONENT_ROOT="${METRICS_COMPONENT_ROOT}/${component}"
      [[ ! -L "${METRICS_COMPONENT_ROOT}" ]] || die "Relative BACKUP_METRICS_FILE parent directories must not be symbolic links."
      [[ ! -e "${METRICS_COMPONENT_ROOT}" || -d "${METRICS_COMPONENT_ROOT}" ]] || die "BACKUP_METRICS_FILE parent path is not a directory."
    done
    BACKUP_METRICS_FILE="${ROOT_DIR}/${BACKUP_METRICS_FILE}"
  fi
  METRICS_BASENAME="$(basename "${BACKUP_METRICS_FILE}")"
  [[ "${METRICS_BASENAME}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*\.prom$ ]] || die "BACKUP_METRICS_FILE must end in a safe .prom filename."
  [[ ! -L "${BACKUP_METRICS_FILE}" && ! -d "${BACKUP_METRICS_FILE}" ]] || die "BACKUP_METRICS_FILE must not be a symbolic link or directory."
  METRICS_DIR="$(dirname "${BACKUP_METRICS_FILE}")"
  mkdir -p -- "${METRICS_DIR}"
  METRICS_DIR="$(cd "${METRICS_DIR}" && pwd -P)"
  if [[ "${METRICS_WAS_RELATIVE}" == "1" ]]; then
    [[ "${METRICS_DIR}" == "${ROOT_DIR}" || "${METRICS_DIR}" == "${ROOT_DIR}/"* ]] || die "Relative BACKUP_METRICS_FILE must remain inside ROOT_DIR."
  fi
  BACKUP_METRICS_FILE="${METRICS_DIR}/${METRICS_BASENAME}"
  [[ ! -L "${BACKUP_METRICS_FILE}" && ! -d "${BACKUP_METRICS_FILE}" ]] || die "Resolved BACKUP_METRICS_FILE must not be a symbolic link or directory."
  METRICS_TMP="${METRICS_DIR}/.${METRICS_BASENAME}.tmp.$$"
  [[ ! -e "${METRICS_TMP}" && ! -L "${METRICS_TMP}" ]] || die "Temporary backup metrics path already exists."
  NOW_EPOCH="$(date -u +%s)"
  cat > "${METRICS_TMP}" <<EOF
# HELP lumira_mysql_backup_last_success_timestamp_seconds Unix timestamp of the last successful Lumira MySQL backup.
# TYPE lumira_mysql_backup_last_success_timestamp_seconds gauge
lumira_mysql_backup_last_success_timestamp_seconds ${NOW_EPOCH}
# HELP lumira_mysql_backup_dump_bytes Size in bytes of the last successful Lumira MySQL logical dump.
# TYPE lumira_mysql_backup_dump_bytes gauge
lumira_mysql_backup_dump_bytes ${ARTIFACT_SIZES[0]}
# HELP lumira_mysql_backup_info Identity of the last successful Lumira MySQL backup.
# TYPE lumira_mysql_backup_info gauge
lumira_mysql_backup_info{backup_id="$(json_escape "${BACKUP_ID}")",database="$(json_escape "${MYSQL_DATABASE}")"} 1
EOF
  chmod 644 "${METRICS_TMP}"
  mv -f -- "${METRICS_TMP}" "${BACKUP_METRICS_FILE}"
fi

echo "Backup completed: ${OUT_DIR}"
