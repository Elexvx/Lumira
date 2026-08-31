#!/usr/bin/env bash
set -euo pipefail

# Idempotent role convergence for bundled MySQL entrypoint and managed MySQL.
# Supply credentials through the environment or a secret manager; this file
# intentionally contains no passwords. The six usernames are fixed so grants
# cannot be redirected to attacker-controlled identifiers.

required=(
  DB_PASSWORD
  DB_MIGRATION_PASSWORD
  MYSQL_BACKUP_PASSWORD
  MYSQL_RESTORE_PASSWORD
  XXL_JOB_DB_PASSWORD
  MYSQLD_EXPORTER_PASSWORD
)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: ${name} is required to initialize database roles." >&2
    exit 1
  fi
done

MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ADMIN_USERNAME="${MYSQL_ADMIN_USERNAME:-root}"
MYSQL_ADMIN_PASSWORD="${MYSQL_ADMIN_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-saas}"
XXL_JOB_DATABASE="${XXL_JOB_DATABASE:-xxl_job}"
MYSQL_ACCOUNT_HOST="${MYSQL_ACCOUNT_HOST:-%}"

[[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'ERROR: MYSQL_DATABASE is invalid.' >&2; exit 1; }
[[ "${XXL_JOB_DATABASE}" == "xxl_job" ]] || { echo 'ERROR: XXL_JOB_DATABASE must be xxl_job.' >&2; exit 1; }
[[ "${MYSQL_ACCOUNT_HOST}" =~ ^[A-Za-z0-9_.:%-]+$ ]] || { echo 'ERROR: MYSQL_ACCOUNT_HOST is invalid.' >&2; exit 1; }
[[ -n "${MYSQL_ADMIN_PASSWORD}" ]] || { echo 'ERROR: MYSQL_ADMIN_PASSWORD or MYSQL_ROOT_PASSWORD is required.' >&2; exit 1; }

b64() {
  printf '%s' "$1" | base64 | tr -d '\r\n'
}

sql_password() {
  local user="$1" encoded="$2"
  printf '%s\n' \
    "SET @lumira_password = CONVERT(FROM_BASE64('${encoded}') USING utf8mb4);" \
    "SET @lumira_sql = CONCAT('CREATE USER IF NOT EXISTS \`${user}\`@\`${MYSQL_ACCOUNT_HOST}\` IDENTIFIED BY ', QUOTE(@lumira_password));" \
    'PREPARE lumira_stmt FROM @lumira_sql; EXECUTE lumira_stmt; DEALLOCATE PREPARE lumira_stmt;' \
    "SET @lumira_sql = CONCAT('ALTER USER \`${user}\`@\`${MYSQL_ACCOUNT_HOST}\` IDENTIFIED BY ', QUOTE(@lumira_password));" \
    'PREPARE lumira_stmt FROM @lumira_sql; EXECUTE lumira_stmt; DEALLOCATE PREPARE lumira_stmt;'
}

sql_file="$(mktemp)"
trap 'rm -f -- "${sql_file}"' EXIT
chmod 600 "${sql_file}"
{
  printf 'CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n' "${MYSQL_DATABASE}"
  printf 'CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n' "${XXL_JOB_DATABASE}"
  sql_password lumira_app "$(b64 "${DB_PASSWORD}")"
  sql_password lumira_migrator "$(b64 "${DB_MIGRATION_PASSWORD}")"
  sql_password lumira_backup "$(b64 "${MYSQL_BACKUP_PASSWORD}")"
  sql_password lumira_restore "$(b64 "${MYSQL_RESTORE_PASSWORD}")"
  sql_password xxl_job "$(b64 "${XXL_JOB_DB_PASSWORD}")"
  sql_password exporter "$(b64 "${MYSQLD_EXPORTER_PASSWORD}")"

  for user in lumira_app lumira_migrator lumira_backup lumira_restore xxl_job exporter; do
    printf 'REVOKE ALL PRIVILEGES, GRANT OPTION FROM `%s`@`%s`;\n' "${user}" "${MYSQL_ACCOUNT_HOST}"
  done
  printf 'GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON `%s`.* TO `lumira_app`@`%s`;\n' "${MYSQL_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT ALL PRIVILEGES ON `%s`.* TO `lumira_migrator`@`%s`;\n' "${MYSQL_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON `%s`.* TO `lumira_backup`@`%s`;\n' "${MYSQL_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT PROCESS, RELOAD, REPLICATION CLIENT ON *.* TO `lumira_backup`@`%s`;\n' "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT ALL PRIVILEGES ON `%s`.* TO `lumira_restore`@`%s`;\n' "${MYSQL_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT ALL PRIVILEGES ON `%s`.* TO `xxl_job`@`%s`;\n' "${XXL_JOB_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT PROCESS, REPLICATION CLIENT ON *.* TO `exporter`@`%s`;\n' "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT SELECT ON `%s`.* TO `exporter`@`%s`;\n' "${MYSQL_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT SELECT ON `%s`.* TO `exporter`@`%s`;\n' "${XXL_JOB_DATABASE}" "${MYSQL_ACCOUNT_HOST}"
  printf 'GRANT SELECT ON `performance_schema`.* TO `exporter`@`%s`;\n' "${MYSQL_ACCOUNT_HOST}"
  printf 'FLUSH PRIVILEGES;\n'
} > "${sql_file}"

mysql_connection=(--user="${MYSQL_ADMIN_USERNAME}" --default-character-set=utf8mb4)
if [[ -n "${MYSQL_HOST}" ]]; then
  mysql_connection+=(--protocol=tcp --host="${MYSQL_HOST}" --port="${MYSQL_PORT}")
fi
MYSQL_PWD="${MYSQL_ADMIN_PASSWORD}" mysql "${mysql_connection[@]}" < "${sql_file}"

echo 'Lumira database roles converged without embedding passwords in repository SQL.'
