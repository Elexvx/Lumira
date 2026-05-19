#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${1:-}"
ENV_FILE="${ROOT_DIR}/deploy/.env"
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

if [[ -z "${BACKUP_DIR}" || ! -d "${BACKUP_DIR}" ]]; then
  echo "Usage: deploy/restore-platform.sh <backup-directory>" >&2
  exit 1
fi
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "deploy/.env not found. Restore the deployment environment first." >&2
  exit 1
fi

load_env_file "${ENV_FILE}"

COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.prod.yml"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${DB_NAME:-saas_platform}}"
MYSQL_USER="${MYSQL_USER:-${DB_USER:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"

MYSQL_DUMP="$(find "${BACKUP_DIR}" -maxdepth 1 -name 'mysql-*.sql' | head -1)"
if [[ -z "${MYSQL_DUMP}" ]]; then
  echo "MySQL dump not found in ${BACKUP_DIR}" >&2
  exit 1
fi

echo "Restoring MySQL from ${MYSQL_DUMP}..."
if [[ "${DRY_RUN}" == "1" || "${DRY_RUN}" == "true" ]]; then
  run docker compose -f "${COMPOSE_FILE}" exec -T "${MYSQL_SERVICE}" \
    sh -c "MYSQL_PWD='***' mysql -u'${MYSQL_USER}' '${MYSQL_DATABASE}' < ${MYSQL_DUMP}"
else
  docker compose -f "${COMPOSE_FILE}" exec -T "${MYSQL_SERVICE}" \
  sh -c "MYSQL_PWD='${MYSQL_PASSWORD}' mysql -u'${MYSQL_USER}' '${MYSQL_DATABASE}'" \
  < "${MYSQL_DUMP}"
fi

if [[ -f "${BACKUP_DIR}/redis-dump.rdb" ]]; then
  echo "Restoring Redis dump..."
  run docker compose -f "${COMPOSE_FILE}" cp "${BACKUP_DIR}/redis-dump.rdb" "${REDIS_SERVICE}:/data/dump.rdb"
  run docker compose -f "${COMPOSE_FILE}" restart "${REDIS_SERVICE}"
fi

if [[ -f "${BACKUP_DIR}/file-storage.tgz" ]]; then
  echo "Restoring uploaded files..."
  run tar -C "${ROOT_DIR}" -xzf "${BACKUP_DIR}/file-storage.tgz"
fi
if [[ -f "${BACKUP_DIR}/plugin-storage.tgz" ]]; then
  echo "Restoring plugin files..."
  run tar -C "${ROOT_DIR}" -xzf "${BACKUP_DIR}/plugin-storage.tgz"
fi

echo "Restore completed from ${BACKUP_DIR}"
