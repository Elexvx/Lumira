#!/usr/bin/env bash
set -euo pipefail

umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BACKUP_DIR_INPUT="${1:-}"
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
    if ! declare -p "${key}" >/dev/null 2>&1; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done < "${file}"
}

if [[ -z "${BACKUP_DIR_INPUT}" || ! -d "${BACKUP_DIR_INPUT}" ]]; then
  echo "Usage: RESTORE_TARGET_DATABASE=<database> RESTORE_CONFIRM=<token> deploy/restore-platform.sh <backup-directory>" >&2
  exit 1
fi

BACKUP_DIR="$(cd "${BACKUP_DIR_INPUT}" && pwd -P)"
MANIFEST_PATH="${BACKUP_DIR}/manifest.json"
CHECKSUM_PATH="${BACKUP_DIR}/SHA256SUMS"
COMPLETE_PATH="${BACKUP_DIR}/.complete"

[[ -f "${MANIFEST_PATH}" ]] || die "manifest.json is missing from ${BACKUP_DIR}."
[[ -f "${CHECKSUM_PATH}" ]] || die "SHA256SUMS is missing from ${BACKUP_DIR}."
[[ -f "${COMPLETE_PATH}" ]] || die ".complete is missing; refusing an incomplete backup."
[[ ! -e "${BACKUP_DIR}/deploy.env.snapshot" ]] || die "Legacy deploy.env.snapshot contains secrets and is not accepted."
NODE_COMMAND="node"
NODE_MANIFEST_PATH="${MANIFEST_PATH}"
NODE_CHECKSUM_PATH="${CHECKSUM_PATH}"
NODE_COMPLETE_PATH="${COMPLETE_PATH}"
if ! command -v "${NODE_COMMAND}" >/dev/null 2>&1; then
  if command -v node.exe >/dev/null 2>&1 && command -v wslpath >/dev/null 2>&1; then
    NODE_COMMAND="node.exe"
    NODE_MANIFEST_PATH="$(wslpath -w "${MANIFEST_PATH}")"
    NODE_CHECKSUM_PATH="$(wslpath -w "${CHECKSUM_PATH}")"
    NODE_COMPLETE_PATH="$(wslpath -w "${COMPLETE_PATH}")"
  else
    die "Node.js is required to validate the machine-readable backup manifest."
  fi
fi

MANIFEST_FIELDS="$(mktemp)"
cleanup_manifest_fields() {
  rm -f -- "${MANIFEST_FIELDS:-}"
}
trap cleanup_manifest_fields EXIT
trap 'cleanup_manifest_fields; exit 130' INT
trap 'cleanup_manifest_fields; exit 143' TERM

"${NODE_COMMAND}" - "${NODE_MANIFEST_PATH}" "${NODE_CHECKSUM_PATH}" "${NODE_COMPLETE_PATH}" > "${MANIFEST_FIELDS}" <<'NODE'
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const [manifestPath, checksumPath, completePath] = process.argv.slice(2);
const fail = (message) => {
  process.stderr.write(`ERROR: ${message}\n`);
  process.exit(1);
};

const backupRoot = fs.realpathSync(path.dirname(manifestPath));
const backupPrefix = `${backupRoot}${path.sep}`;
const requireRegularNonSymlink = (file, label) => {
  let info;
  try { info = fs.lstatSync(file); } catch { fail(`${label} is missing.`); }
  if (info.isSymbolicLink() || !info.isFile()) fail(`${label} must be a regular non-symlink file.`);
  const realFile = fs.realpathSync(file);
  if (!realFile.startsWith(backupPrefix)) fail(`${label} resolves outside the backup directory.`);
  return info;
};
requireRegularNonSymlink(manifestPath, 'manifest.json');
requireRegularNonSymlink(checksumPath, 'SHA256SUMS');
requireRegularNonSymlink(completePath, '.complete');

let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
} catch (error) {
  fail(`manifest.json is not valid JSON: ${error.message}`);
}

if (manifest.schemaVersion !== 1) fail('manifest schemaVersion must equal 1.');
if (manifest.status !== 'complete') fail('manifest status must equal complete.');
if (manifest.secretsIncluded !== false) fail('manifest must explicitly declare secretsIncluded=false.');
if (typeof manifest.backupId !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(manifest.backupId)) fail('manifest backupId is invalid.');
if (typeof manifest.createdAt !== 'string' || !Number.isFinite(Date.parse(manifest.createdAt))) fail('manifest createdAt is invalid.');
if (typeof manifest.databaseName !== 'string' || !/^[A-Za-z0-9_]+$/.test(manifest.databaseName)) fail('manifest databaseName is invalid.');
const artifacts = new Map();
const validateArtifact = (name, value, required = false) => {
  if (value === null && !required) return;
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`manifest ${name} artifact is missing or invalid.`);
  if (typeof value.path !== 'string' || path.posix.basename(value.path) !== value.path || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(value.path)) fail(`manifest ${name}.path must be a safe relative filename.`);
  if (typeof value.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(value.sha256)) fail(`manifest ${name}.sha256 is invalid.`);
  if (!Number.isSafeInteger(value.size) || value.size < 0) fail(`manifest ${name}.size is invalid.`);
  if (artifacts.has(value.path)) fail(`manifest contains duplicate artifact path ${value.path}.`);
  artifacts.set(value.path, { name, ...value });
};
validateArtifact('mysql', manifest.mysql, true);
validateArtifact('redis', manifest.redis);
validateArtifact('fileStorage', manifest.fileStorage);
validateArtifact('pluginStorage', manifest.pluginStorage);
if (!Number.isSafeInteger(manifest.mysql.tableCount) || manifest.mysql.tableCount < 0) fail('manifest mysql.tableCount is invalid.');
if (typeof manifest.mysql.schemaFingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(manifest.mysql.schemaFingerprint)) fail('manifest mysql.schemaFingerprint is invalid.');
for (const name of ['serverVersion', 'serverUuid', 'gtidExecuted', 'binlogFile', 'binlogPosition', 'databaseVersion']) {
  if (manifest.mysql[name] !== null && typeof manifest.mysql[name] !== 'string') fail(`manifest mysql.${name} must be a string or null.`);
}

const checksumLines = fs.readFileSync(checksumPath, 'utf8').split(/\r?\n/).filter(Boolean);
if (checksumLines.length !== artifacts.size) fail('SHA256SUMS must list every manifest artifact exactly once and no extra files.');
const seen = new Set();
for (const line of checksumLines) {
  const match = /^([a-f0-9]{64})  ([A-Za-z0-9][A-Za-z0-9._-]*)$/.exec(line);
  if (!match) fail('SHA256SUMS contains a non-standard or unsafe entry.');
  const [, expectedHash, relativePath] = match;
  const artifact = artifacts.get(relativePath);
  if (!artifact || seen.has(relativePath)) fail(`SHA256SUMS contains an unexpected or duplicate entry: ${relativePath}.`);
  if (artifact.sha256 !== expectedHash) fail(`Manifest and SHA256SUMS disagree for ${relativePath}.`);
  const absolutePath = path.join(path.dirname(manifestPath), relativePath);
  const stat = requireRegularNonSymlink(absolutePath, `Artifact ${relativePath}`);
  if (stat.size !== artifact.size) fail(`Artifact size mismatch: ${relativePath}.`);
  const hash = crypto.createHash('sha256');
  const descriptor = fs.openSync(absolutePath, 'r');
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  try {
    let bytesRead;
    while ((bytesRead = fs.readSync(descriptor, buffer, 0, buffer.length, null)) > 0) hash.update(buffer.subarray(0, bytesRead));
  } finally {
    fs.closeSync(descriptor);
  }
  const actualHash = hash.digest('hex');
  if (actualHash !== expectedHash) fail(`Artifact SHA-256 mismatch: ${relativePath}.`);
  seen.add(relativePath);
}

const values = [
  manifest.backupId,
  manifest.createdAt,
  manifest.databaseName,
  manifest.mysql.serverVersion ?? '',
  manifest.mysql.gtidExecuted ?? '',
  manifest.mysql.binlogFile ?? '',
  manifest.mysql.binlogPosition ?? '',
  manifest.mysql.databaseVersion ?? '',
  String(manifest.mysql.tableCount),
  manifest.mysql.schemaFingerprint,
  manifest.mysql.path,
  manifest.redis?.path ?? '',
  manifest.fileStorage?.path ?? '',
  manifest.pluginStorage?.path ?? '',
];
for (const value of values) process.stdout.write(`${value}\0`);
NODE

MANIFEST_VALUES=()
while IFS= read -r -d '' manifest_value; do
  MANIFEST_VALUES+=("${manifest_value}")
done < "${MANIFEST_FIELDS}"
[[ "${#MANIFEST_VALUES[@]}" -eq 14 ]] || die "Unable to read required manifest fields."
BACKUP_ID="${MANIFEST_VALUES[0]}"
BACKUP_CREATED_AT="${MANIFEST_VALUES[1]}"
SOURCE_DATABASE="${MANIFEST_VALUES[2]}"
SOURCE_SERVER_VERSION="${MANIFEST_VALUES[3]}"
SOURCE_GTID="${MANIFEST_VALUES[4]}"
SOURCE_BINLOG_FILE="${MANIFEST_VALUES[5]}"
SOURCE_BINLOG_POSITION="${MANIFEST_VALUES[6]}"
SOURCE_DATABASE_VERSION="${MANIFEST_VALUES[7]}"
SOURCE_TABLE_COUNT="${MANIFEST_VALUES[8]}"
SOURCE_SCHEMA_FINGERPRINT="${MANIFEST_VALUES[9]}"
MYSQL_DUMP_REL="${MANIFEST_VALUES[10]}"
REDIS_REL="${MANIFEST_VALUES[11]}"
FILE_STORAGE_REL="${MANIFEST_VALUES[12]}"
PLUGIN_STORAGE_REL="${MANIFEST_VALUES[13]}"
MYSQL_DUMP_PATH="${BACKUP_DIR}/${MYSQL_DUMP_REL}"

RESTORE_MODE="${RESTORE_MODE:-isolated}"
RESTORE_TARGET_DATABASE="${RESTORE_TARGET_DATABASE:-}"
RESTORE_CONFIRM="${RESTORE_CONFIRM:-}"
RESTORE_RECREATE_TARGET="${RESTORE_RECREATE_TARGET:-0}"
RESTORE_WRITES_PAUSED="${RESTORE_WRITES_PAUSED:-0}"
RESTORE_REDIS="${RESTORE_REDIS:-0}"
RESTORE_FILE_STORAGE="${RESTORE_FILE_STORAGE:-0}"
RESTORE_PLUGIN_STORAGE="${RESTORE_PLUGIN_STORAGE:-0}"
RESTORE_ALLOW_SERVER_MAJOR_MISMATCH="${RESTORE_ALLOW_SERVER_MAJOR_MISMATCH:-0}"

[[ "${RESTORE_MODE}" == "isolated" || "${RESTORE_MODE}" == "production" ]] || die "RESTORE_MODE must be isolated or production."
[[ "${RESTORE_TARGET_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || die "RESTORE_TARGET_DATABASE is required and must contain only letters, digits, or underscores."

if [[ "${RESTORE_MODE}" == "isolated" ]]; then
  [[ "${RESTORE_TARGET_DATABASE}" != "${SOURCE_DATABASE}" ]] || die "An isolated restore must target a database different from ${SOURCE_DATABASE}."
  REQUIRED_CONFIRM="RESTORE_ISOLATED:${RESTORE_TARGET_DATABASE}"
  ! is_true "${RESTORE_REDIS}" || die "Redis restoration is only available in production restore mode."
  ! is_true "${RESTORE_FILE_STORAGE}" || die "File-storage restoration is only available in production restore mode."
  ! is_true "${RESTORE_PLUGIN_STORAGE}" || die "Plugin-storage restoration is only available in production restore mode."
else
  REQUIRED_CONFIRM="RESTORE_PRODUCTION:${RESTORE_TARGET_DATABASE}"
  if ! is_true "${DRY_RUN}"; then
    is_true "${RESTORE_RECREATE_TARGET}" || die "Production restoration requires RESTORE_RECREATE_TARGET=1."
    is_true "${RESTORE_WRITES_PAUSED}" || die "Production restoration requires RESTORE_WRITES_PAUSED=1 after application writes are actually stopped."
  fi
fi

if is_true "${RESTORE_REDIS}"; then
  [[ -n "${REDIS_REL}" ]] || die "RESTORE_REDIS=1 but the manifest has no Redis artifact."
fi
if is_true "${RESTORE_FILE_STORAGE}"; then
  [[ -n "${FILE_STORAGE_REL}" ]] || die "RESTORE_FILE_STORAGE=1 but the manifest has no file-storage artifact."
fi
if is_true "${RESTORE_PLUGIN_STORAGE}"; then
  [[ -n "${PLUGIN_STORAGE_REL}" ]] || die "RESTORE_PLUGIN_STORAGE=1 but the manifest has no plugin-storage artifact."
fi

if is_true "${DRY_RUN}"; then
  echo "Backup validation passed: ${BACKUP_ID} (${BACKUP_CREATED_AT})"
  echo "[dry-run] Restore mode: ${RESTORE_MODE}"
  echo "[dry-run] Source database: ${SOURCE_DATABASE}; target database: ${RESTORE_TARGET_DATABASE}"
  echo "[dry-run] Required confirmation for a real restore: ${REQUIRED_CONFIRM}"
  echo "[dry-run] Verified manifest.json, SHA256SUMS, .complete, artifact sizes, and artifact SHA-256 values."
  echo "[dry-run] No MySQL, Redis, file-storage, plugin-storage, or environment writes were performed."
  exit 0
fi

[[ "${RESTORE_CONFIRM}" == "${REQUIRED_CONFIRM}" ]] || die "Set RESTORE_CONFIRM=${REQUIRED_CONFIRM} to confirm this exact target."
[[ -f "${ENV_FILE}" ]] || die "deploy/.env not found. Restore deployment configuration through the separate secret-management process first."
load_env_file "${ENV_FILE}"
command -v docker >/dev/null 2>&1 || die "docker is required to perform a restore."
command -v sha256sum >/dev/null 2>&1 || die "sha256sum is required to validate the restored schema."

COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.prod.yml"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis-runtime}"
MYSQL_CLIENT_IMAGE="${MYSQL_CLIENT_IMAGE:-mysql:8.4}"
DB_BACKUP_NETWORK="${DB_BACKUP_NETWORK:-deploy_data-network}"
DB_URL="${DB_URL:-}"
DB_HOST="${DB_HOST:-}"
DB_PORT="${DB_PORT:-}"
APPLICATION_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-}}"
if [[ "${DB_URL}" == jdbc:mysql://* ]]; then
  DB_ENDPOINT="${DB_URL#jdbc:mysql://}"
  DB_AUTHORITY="${DB_ENDPOINT%%/*}"
  DB_PATH="${DB_ENDPOINT#*/}"
  [[ -n "${DB_HOST}" ]] || DB_HOST="${DB_AUTHORITY%%:*}"
  if [[ -z "${DB_PORT}" ]]; then
    DB_PORT="${DB_AUTHORITY##*:}"
    [[ "${DB_PORT}" == "${DB_AUTHORITY}" ]] && DB_PORT=3306
  fi
  if [[ "${DB_PATH}" != "${DB_ENDPOINT}" ]]; then
    APPLICATION_DATABASE="${DB_PATH%%\?*}"
  fi
fi
DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
APPLICATION_DATABASE="${APPLICATION_DATABASE:-saas}"
[[ "${APPLICATION_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || die "Configured application database name is invalid."
if [[ "${RESTORE_MODE}" == "production" && "${RESTORE_TARGET_DATABASE}" != "${APPLICATION_DATABASE}" ]]; then
  die "Production restore target ${RESTORE_TARGET_DATABASE} does not match the application database ${APPLICATION_DATABASE}; use isolated mode for a side database."
fi
MYSQL_USER="${MYSQL_RESTORE_USERNAME:-}"
MYSQL_PASSWORD="${MYSQL_RESTORE_PASSWORD:-}"
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
[[ -n "${MYSQL_USER}" ]] || die "MySQL restore username is required."
[[ -n "${MYSQL_PASSWORD}" ]] || die "MYSQL_RESTORE_PASSWORD is required; restore never falls back to the application account."
[[ "${MYSQL_USER,,}" != "root" ]] || die "MYSQL_RESTORE_USERNAME must not be root."
[[ "${MYSQL_USER}" != "${DB_USERNAME:-}" ]] || die "MYSQL_RESTORE_USERNAME must not share the application account."
[[ "${DB_PORT}" =~ ^[0-9]+$ ]] || die "MySQL port must be numeric."
[[ "${MYSQL_SSL_MODE}" =~ ^(DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY)$ ]] || die "MYSQL_SSL_MODE must be DISABLED, PREFERRED, REQUIRED, VERIFY_CA, or VERIFY_IDENTITY."
if [[ -n "${MYSQL_SSL_CA_FILE}" ]]; then
  if [[ "${MYSQL_SSL_CA_FILE}" != /* ]]; then
    MYSQL_SSL_CA_FILE="${ROOT_DIR}/${MYSQL_SSL_CA_FILE}"
  fi
  [[ -f "${MYSQL_SSL_CA_FILE}" && -r "${MYSQL_SSL_CA_FILE}" ]] || die "MYSQL_SSL_CA_FILE must reference a readable CA file."
fi

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
  echo "WARNING: TLS is explicitly disabled for the external MySQL restore connection. Set MYSQL_SSL_MODE=VERIFY_IDENTITY for managed MySQL." >&2
fi

mysql_client() {
  local database="$1"
  shift
  if [[ "${MYSQL_MODE}" == "compose" ]]; then
    if [[ -n "${database}" ]]; then
      MYSQL_PWD="${MYSQL_PASSWORD}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD "${MYSQL_SERVICE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -u"${MYSQL_USER}" "$@" "${database}"
    else
      MYSQL_PWD="${MYSQL_PASSWORD}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD "${MYSQL_SERVICE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -u"${MYSQL_USER}" "$@"
    fi
  else
    if [[ -n "${database}" ]]; then
      MYSQL_PWD="${MYSQL_PASSWORD}" docker run --rm --network "${DB_BACKUP_NETWORK}" \
        --add-host host.docker.internal:host-gateway -e MYSQL_PWD -i "${MYSQL_DOCKER_MOUNT_ARGS[@]}" "${MYSQL_CLIENT_IMAGE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -h"${DB_HOST}" -P"${DB_PORT}" -u"${MYSQL_USER}" "$@" "${database}"
    else
      MYSQL_PWD="${MYSQL_PASSWORD}" docker run --rm --network "${DB_BACKUP_NETWORK}" \
        --add-host host.docker.internal:host-gateway -e MYSQL_PWD -i "${MYSQL_DOCKER_MOUNT_ARGS[@]}" "${MYSQL_CLIENT_IMAGE}" \
        mysql --batch --raw --skip-column-names "${MYSQL_TLS_ARGS[@]}" -h"${DB_HOST}" -P"${DB_PORT}" -u"${MYSQL_USER}" "$@"
    fi
  fi
}

mysql_import() {
  local database="$1"
  local input="$2"
  mysql_client "${database}" --default-character-set=utf8mb4 < "${input}"
}

mysql_scalar() {
  local database="$1"
  local query="$2"
  local result
  result="$(mysql_client "${database}" --execute "${query}")" || return 1
  printf '%s' "${result%%$'\n'*}"
}

TARGET_SERVER_VERSION="$(mysql_scalar "" "SELECT @@version;")" || die "Unable to connect to the restore target with the restore account."
if [[ -n "${SOURCE_SERVER_VERSION}" ]] && ! is_true "${RESTORE_ALLOW_SERVER_MAJOR_MISMATCH}"; then
  SOURCE_SERVER_MAJOR="${SOURCE_SERVER_VERSION%%.*}"
  TARGET_SERVER_MAJOR="${TARGET_SERVER_VERSION%%.*}"
  [[ "${SOURCE_SERVER_MAJOR}" == "${TARGET_SERVER_MAJOR}" ]] || die "Source MySQL major version ${SOURCE_SERVER_MAJOR} differs from target ${TARGET_SERVER_MAJOR}. Set RESTORE_ALLOW_SERVER_MAJOR_MISMATCH=1 only after compatibility testing."
fi

TARGET_EXISTS="$(mysql_scalar "" "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '${RESTORE_TARGET_DATABASE}';")" \
  || die "Unable to inspect the restore target database."
[[ "${TARGET_EXISTS}" =~ ^[01]$ ]] || die "MySQL returned an invalid target-existence result."

ISOLATED_CREATED=0
REDIS_STOPPED=0
cleanup_restore() {
  if [[ "${REDIS_STOPPED:-0}" == "1" ]]; then
    docker compose -f "${COMPOSE_FILE}" start "${REDIS_SERVICE}" >/dev/null 2>&1 || true
  fi
  if [[ "${ISOLATED_CREATED:-0}" == "1" ]]; then
    mysql_client "" --execute "DROP DATABASE IF EXISTS \`${RESTORE_TARGET_DATABASE}\`;" >/dev/null 2>&1 || true
  fi
  cleanup_manifest_fields
}
trap cleanup_restore EXIT
trap 'cleanup_restore; exit 130' INT
trap 'cleanup_restore; exit 143' TERM

if [[ "${RESTORE_MODE}" == "isolated" ]]; then
  [[ "${TARGET_EXISTS}" == "0" ]] || die "Isolated target database ${RESTORE_TARGET_DATABASE} already exists; refusing to merge into it."
  mysql_client "" --execute "CREATE DATABASE \`${RESTORE_TARGET_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  ISOLATED_CREATED=1
else
  echo "Recreating confirmed production target database ${RESTORE_TARGET_DATABASE}..."
  mysql_client "" --execute "DROP DATABASE IF EXISTS \`${RESTORE_TARGET_DATABASE}\`; CREATE DATABASE \`${RESTORE_TARGET_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

echo "Restoring MySQL dump into ${RESTORE_TARGET_DATABASE}..."
mysql_import "${RESTORE_TARGET_DATABASE}" "${MYSQL_DUMP_PATH}"

RESTORED_TABLE_COUNT="$(mysql_scalar "${RESTORE_TARGET_DATABASE}" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';")" \
  || die "Unable to validate restored MySQL table count."
[[ "${RESTORED_TABLE_COUNT}" == "${SOURCE_TABLE_COUNT}" ]] || die "Restored table count ${RESTORED_TABLE_COUNT} does not match manifest count ${SOURCE_TABLE_COUNT}."

RESTORED_SCHEMA_FINGERPRINT="$(
  mysql_client "" --execute "SELECT CONCAT_WS(CHAR(9), table_name, ordinal_position, column_name, column_type, is_nullable, IFNULL(column_default, '<NULL>'), extra) FROM information_schema.columns WHERE table_schema = '${RESTORE_TARGET_DATABASE}' ORDER BY table_name, ordinal_position;" \
    | sha256sum | awk '{print $1}'
)" || die "Unable to validate the restored MySQL schema fingerprint."
[[ "${RESTORED_SCHEMA_FINGERPRINT}" == "${SOURCE_SCHEMA_FINGERPRINT}" ]] || die "Restored schema fingerprint does not match the backup manifest."

if [[ -n "${SOURCE_DATABASE_VERSION}" ]]; then
  RESTORED_DATABASE_VERSION="$(mysql_scalar "${RESTORE_TARGET_DATABASE}" "SELECT config_value FROM sys_config WHERE config_key = 'platform.database.version' AND deleted = 0 LIMIT 1;")" \
    || die "Unable to validate restored database version metadata."
  [[ "${RESTORED_DATABASE_VERSION}" == "${SOURCE_DATABASE_VERSION}" ]] || die "Restored database version metadata does not match the backup manifest."
fi

validate_archive() {
  local archive="$1"
  local required_prefix="$2"
  local listing entry
  listing="$(mktemp)"
  tar -tzf "${archive}" > "${listing}"
  while IFS= read -r entry; do
    [[ -n "${entry}" ]] || continue
    [[ "${entry}" != /* && "${entry}" != *"../"* && "${entry}" != ".." ]] || die "Unsafe archive entry rejected: ${entry}"
    [[ "${entry}" == "${required_prefix}" || "${entry}" == "${required_prefix}/"* ]] || die "Archive entry is outside ${required_prefix}: ${entry}"
  done < "${listing}"
  rm -f -- "${listing}"
}

if is_true "${RESTORE_REDIS}"; then
  echo "Restoring authenticated Redis RDB..."
  REDIS_CONTAINER_ID="$(docker compose -f "${COMPOSE_FILE}" ps -aq "${REDIS_SERVICE}" 2>/dev/null || true)"
  [[ -n "${REDIS_CONTAINER_ID}" ]] || die "Redis service container was not found."
  REDIS_IMAGE="$(docker inspect -f '{{.Config.Image}}' "${REDIS_CONTAINER_ID}")"
  REDIS_VALIDATION_PATH="/tmp/lumira-restore-${BACKUP_ID}.rdb"
  docker cp "${BACKUP_DIR}/${REDIS_REL}" "${REDIS_CONTAINER_ID}:${REDIS_VALIDATION_PATH}"
  docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" sh -eu -c '
    if [ -n "${REDIS_PASSWORD:-}" ]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; fi
    redis-cli --no-auth-warning PING >/dev/null
    redis-check-rdb "$1" >/dev/null
    rm -f -- "$1"
  ' -- "${REDIS_VALIDATION_PATH}"
  docker compose -f "${COMPOSE_FILE}" stop "${REDIS_SERVICE}"
  REDIS_STOPPED=1
  docker run --rm --volumes-from "${REDIS_CONTAINER_ID}" --entrypoint sh "${REDIS_IMAGE}" -eu -c \
    'rm -rf -- /data/appendonlydir; rm -f -- /data/appendonly.aof /data/dump.rdb'
  docker cp "${BACKUP_DIR}/${REDIS_REL}" "${REDIS_CONTAINER_ID}:/data/dump.rdb"
  docker run --rm --volumes-from "${REDIS_CONTAINER_ID}" --entrypoint sh "${REDIS_IMAGE}" -eu -c \
    'chown redis:redis /data/dump.rdb 2>/dev/null || true; chmod 600 /data/dump.rdb'
  docker compose -f "${COMPOSE_FILE}" start "${REDIS_SERVICE}"
  REDIS_STOPPED=0
  REDIS_READY=0
  for _ in $(seq 1 30); do
    if docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" sh -eu -c '
      if [ -n "${REDIS_PASSWORD:-}" ]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; fi
      redis-cli --no-auth-warning PING
    ' 2>/dev/null | grep -qx PONG; then
      REDIS_READY=1
      break
    fi
    sleep 1
  done
  [[ "${REDIS_READY}" == "1" ]] || die "Redis did not become ready after restoring the authenticated RDB."
fi

if is_true "${RESTORE_FILE_STORAGE}"; then
  validate_archive "${BACKUP_DIR}/${FILE_STORAGE_REL}" "data/uploads"
  tar -C "${ROOT_DIR}" -xzf "${BACKUP_DIR}/${FILE_STORAGE_REL}"
fi
if is_true "${RESTORE_PLUGIN_STORAGE}"; then
  validate_archive "${BACKUP_DIR}/${PLUGIN_STORAGE_REL}" "data/plugins"
  tar -C "${ROOT_DIR}" -xzf "${BACKUP_DIR}/${PLUGIN_STORAGE_REL}"
fi

ISOLATED_CREATED=0
trap cleanup_manifest_fields EXIT
trap 'cleanup_manifest_fields; exit 130' INT
trap 'cleanup_manifest_fields; exit 143' TERM
echo "Restore validation passed: tables=${RESTORED_TABLE_COUNT}, schema=${RESTORED_SCHEMA_FINGERPRINT}, MySQL=${TARGET_SERVER_VERSION}"
echo "Restore completed from ${BACKUP_DIR} into ${RESTORE_TARGET_DATABASE} (${RESTORE_MODE})"
