#!/bin/sh
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

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
  -baselineVersion=202607140000 \
  -table=lumira_platform_update_schema_history \
  -connectRetries=20 \
  -validateMigrationNaming=true \
  "$@"

exec java -jar /opt/lumira/lumira-bootstrap-admin.jar
