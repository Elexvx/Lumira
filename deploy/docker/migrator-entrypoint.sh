#!/bin/sh
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

baseline_version_file=/opt/lumira/saas-baseline-version.txt
if [ ! -r "$baseline_version_file" ]; then
  echo "Database baseline version file is missing: $baseline_version_file" >&2
  exit 1
fi
database_baseline_version=$(tr -d '[:space:]' < "$baseline_version_file")
case "$database_baseline_version" in
  ''|*[!0-9]*)
    echo "Database baseline version is invalid: $database_baseline_version" >&2
    exit 1
    ;;
esac

set -- migrate
if [ -n "${DATABASE_TARGET_VERSION:-}" ]; then
  set -- -target="$DATABASE_TARGET_VERSION" "$@"
fi

flyway \
  -url="$DB_URL" \
  -user="$DB_USERNAME" \
  -password="$DB_PASSWORD" \
  -initSql="SELECT id, status, target_commit FROM platform_update_task LIMIT 0" \
  -baselineOnMigrate=true \
  -baselineVersion="$database_baseline_version" \
  -table=lumira_platform_update_schema_history \
  -connectRetries=20 \
  -validateMigrationNaming=true \
  "$@"

exec java -jar /opt/lumira/lumira-bootstrap-admin.jar
