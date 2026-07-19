#!/usr/bin/env sh

set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
container_name="lumira-db-bootstrap-${GITHUB_RUN_ID:-local}-$$"
password='lumira_bootstrap_contract_only'

cleanup() {
  docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker run -d \
  --name "$container_name" \
  -e MYSQL_ROOT_PASSWORD="$password" \
  -e MYSQL_DATABASE=saas \
  -v "$repo_root/lumira-backend/sql/saas.sql:/docker-entrypoint-initdb.d/001-saas.sql:ro" \
  mysql:8.4 >/dev/null

attempt=0
result=''
while [ "$attempt" -lt 90 ]; do
  result=$(docker exec "$container_name" mysql -N -uroot -p"$password" -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='saas' AND table_name='aiadc_activity_registration'; SELECT COUNT(*) FROM saas.sys_dict_type WHERE dict_code IN ('aiadc_activity_locale','aiadc_activity_status','aiadc_activity_public_status') AND deleted=0;" \
    2>/dev/null || true)
  if [ "$result" = "1
3" ]; then
    break
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$container_name" 2>/dev/null || true)" != 'true' ]; then
    docker logs --tail 120 "$container_name"
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 2
done

if [ "$attempt" -ge 90 ]; then
  docker logs --tail 120 "$container_name"
  echo 'Fresh database bootstrap did not become ready.' >&2
  exit 1
fi

if [ "$result" != "1
3" ]; then
  echo "Unexpected fresh database contract result: $result" >&2
  exit 1
fi

echo 'Fresh database bootstrap contract passed.'
