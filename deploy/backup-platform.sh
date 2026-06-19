#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/deploy/.env"
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/lumira}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${BACKUP_ROOT}/${STAMP}"
DRY_RUN="${DRY_RUN:-0}"

run() {
  if [[ "${DRY_RUN}" == "1" || "${DRY_RUN}" == "true" ]]; then
    printf '[dry-run] %q ' "$@"
    printf '\n'
    return 0
  fi
  "$@"
}

load_env_file() {
  local file="$1"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" != *=* ]] && continue
    local key="${line%%=*}"
    local value="${line#*=}"
    key="$(echo "${key}" | xargs)"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    export "${key}=${value}"
  done < "${file}"
}

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "deploy/.env not found. Run the deployment initializer first." >&2
  exit 1
fi

load_env_file "${ENV_FILE}"

run mkdir -p "${OUT_DIR}"

COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.prod.yml"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-saas_platform}}"
MYSQL_USER="${MYSQL_USER:-${DB_USER:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"

echo "Creating MySQL backup..."
if [[ "${DRY_RUN}" == "1" || "${DRY_RUN}" == "true" ]]; then
  run docker compose -f "${COMPOSE_FILE}" exec -T "${MYSQL_SERVICE}" \
    sh -c "MYSQL_PWD='***' mysqldump --single-transaction --routines --triggers -u'${MYSQL_USER}' '${MYSQL_DATABASE}'"
else
  docker compose -f "${COMPOSE_FILE}" exec -T "${MYSQL_SERVICE}" \
  sh -c "MYSQL_PWD='${MYSQL_PASSWORD}' mysqldump --single-transaction --routines --triggers -u'${MYSQL_USER}' '${MYSQL_DATABASE}'" \
  > "${OUT_DIR}/mysql-${MYSQL_DATABASE}.sql"
fi

echo "Creating Redis backup..."
run docker compose -f "${COMPOSE_FILE}" exec -T "${REDIS_SERVICE}" redis-cli --rdb /data/dump.rdb
run docker compose -f "${COMPOSE_FILE}" cp "${REDIS_SERVICE}:/data/dump.rdb" "${OUT_DIR}/redis-dump.rdb"

echo "Archiving uploaded files and plugins..."
if [[ -d "${ROOT_DIR}/data/uploads" ]]; then
  run tar -C "${ROOT_DIR}" -czf "${OUT_DIR}/file-storage.tgz" data/uploads
else
  echo "Skipping uploaded files archive: data/uploads not found"
fi
if [[ -d "${ROOT_DIR}/data/plugins" ]]; then
  run tar -C "${ROOT_DIR}" -czf "${OUT_DIR}/plugin-storage.tgz" data/plugins
else
  echo "Skipping plugin files archive: data/plugins not found"
fi
run install -m 600 "${ENV_FILE}" "${OUT_DIR}/deploy.env.snapshot"

if [[ "${DRY_RUN}" == "1" || "${DRY_RUN}" == "true" ]]; then
  echo "[dry-run] write ${OUT_DIR}/MANIFEST.txt"
else
  cat > "${OUT_DIR}/MANIFEST.txt" <<EOF
backup_time=${STAMP}
mysql_database=${MYSQL_DATABASE}
compose_file=${COMPOSE_FILE}
restore_command=deploy/restore-platform.sh ${OUT_DIR}
EOF
fi

echo "Backup completed: ${OUT_DIR}"
