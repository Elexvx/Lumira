#!/usr/bin/env bash
set -euo pipefail

# Host-side textfile collector for the filesystem that contains Docker data.
# It reads only df(1) metadata and atomically publishes Prometheus gauges; no
# host filesystem is mounted into a monitoring container.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
METRICS_DIR="${LUMIRA_HOST_METRICS_DIR:-${DEPLOY_DIR}/.generated/backup-metrics}"
MOUNT_PATH="${LUMIRA_HOST_DISK_PATH:-/var/lib/docker}"
OUTPUT_FILE="${METRICS_DIR}/lumira-host-disk.prom"

[[ "${METRICS_DIR}" == /* ]] || METRICS_DIR="${DEPLOY_DIR}/${METRICS_DIR}"
mkdir -p -- "${METRICS_DIR}"
METRICS_DIR="$(cd -- "${METRICS_DIR}" && pwd -P)"
[[ "${METRICS_DIR}" != "/" ]] || { echo "metrics directory must not be /" >&2; exit 64; }
[[ -e "${MOUNT_PATH}" ]] || MOUNT_PATH="/"

read -r TOTAL_KB USED_KB AVAILABLE_KB USED_PERCENT < <(
  df -Pk -- "${MOUNT_PATH}" | awk 'NR == 2 { gsub(/%/, "", $5); print $2, $3, $4, $5 }'
)
[[ "${TOTAL_KB}" =~ ^[0-9]+$ && "${USED_KB}" =~ ^[0-9]+$ && "${AVAILABLE_KB}" =~ ^[0-9]+$ && "${USED_PERCENT}" =~ ^[0-9]+$ ]] \
  || { echo "df returned invalid disk metrics" >&2; exit 65; }

NOW_EPOCH="$(date +%s)"
TEMP_FILE="$(mktemp "${METRICS_DIR}/.lumira-host-disk.XXXXXX")"
trap 'rm -f -- "${TEMP_FILE}"' EXIT

{
  echo '# HELP lumira_host_disk_usage_percent Host filesystem usage percentage for the Lumira Docker data path.'
  echo '# TYPE lumira_host_disk_usage_percent gauge'
  echo "lumira_host_disk_usage_percent ${USED_PERCENT}"
  echo '# HELP lumira_host_disk_total_bytes Host filesystem total bytes for the Lumira Docker data path.'
  echo '# TYPE lumira_host_disk_total_bytes gauge'
  echo "lumira_host_disk_total_bytes $((TOTAL_KB * 1024))"
  echo '# HELP lumira_host_disk_available_bytes Host filesystem available bytes for the Lumira Docker data path.'
  echo '# TYPE lumira_host_disk_available_bytes gauge'
  echo "lumira_host_disk_available_bytes $((AVAILABLE_KB * 1024))"
  echo '# HELP lumira_host_disk_metrics_timestamp_seconds Unix timestamp of the last host disk metric collection.'
  echo '# TYPE lumira_host_disk_metrics_timestamp_seconds gauge'
  echo "lumira_host_disk_metrics_timestamp_seconds ${NOW_EPOCH}"
} > "${TEMP_FILE}"

chmod 0644 "${TEMP_FILE}"
mv -f -- "${TEMP_FILE}" "${OUTPUT_FILE}"
trap - EXIT
