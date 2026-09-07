#!/bin/sh
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

plugin_migration_mode=${PLUGIN_MIGRATION_MODE:-platform}
case "$plugin_migration_mode" in
  plugin-approve)
    exec java -jar /opt/lumira/lumira-plugin-migrator.jar approve
    ;;
  plugin-execute)
    exec java -jar /opt/lumira/lumira-plugin-migrator.jar execute
    ;;
  platform)
    ;;
  *)
    echo "Unsupported PLUGIN_MIGRATION_MODE: $plugin_migration_mode" >&2
    exit 1
    ;;
esac

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

# This is the only production path that executes approved plugin DDL. It runs
# in the one-shot migrator container with DB_USERNAME=lumira_migrator; no
# application, Async, or Job runtime receives this database identity.
java -jar /opt/lumira/lumira-plugin-migrator.jar execute

exec java -jar /opt/lumira/lumira-bootstrap-admin.jar
