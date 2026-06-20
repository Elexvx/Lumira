#!/usr/bin/env bash
set -euo pipefail

# Lumira DDD release blocker closure commands.
# Generated at: 2026-06-20T19:42:26.704Z
# Status: NOT_READY
# Release gate blockers: 148
# Default mode lists runnable closure items. Set DDD_RELEASE_CLOSURE_EXECUTE=1 to execute commands.
# Use DDD_RELEASE_CLOSURE_ORDER, DDD_RELEASE_CLOSURE_OWNER, DDD_RELEASE_CLOSURE_PRIORITY, or DDD_RELEASE_CLOSURE_KIND to filter.
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [[ -z "${LUMIRA_REPO_ROOT:-}" ]]; then
  if [[ -f "bin/ddd-release-readiness-summary.mjs" ]]; then
    LUMIRA_REPO_ROOT=$(pwd)
  else
    LUMIRA_REPO_ROOT=$(cd "${SCRIPT_DIR}/../../.." && pwd)
  fi
fi
export LUMIRA_REPO_ROOT
cd "${LUMIRA_REPO_ROOT}"

DDD_RELEASE_CLOSURE_ORDER="${DDD_RELEASE_CLOSURE_ORDER:-}"
DDD_RELEASE_CLOSURE_OWNER="${DDD_RELEASE_CLOSURE_OWNER:-}"
DDD_RELEASE_CLOSURE_PRIORITY="${DDD_RELEASE_CLOSURE_PRIORITY:-}"
DDD_RELEASE_CLOSURE_KIND="${DDD_RELEASE_CLOSURE_KIND:-}"
DDD_RELEASE_CLOSURE_DETAIL="${DDD_RELEASE_CLOSURE_DETAIL:-}"
DDD_RELEASE_CLOSURE_CHECK_ENV="${DDD_RELEASE_CLOSURE_CHECK_ENV:-}"
DDD_RELEASE_CLOSURE_EXECUTE="${DDD_RELEASE_CLOSURE_EXECUTE:-}"
DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR="${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR:-}"
DDD_RELEASE_CLOSURE_MATCHED=0
DDD_RELEASE_CLOSURE_COMMAND_FAILURES=0
if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" == "1" || "${DDD_RELEASE_CLOSURE_EXECUTE}" == "true" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  if [[ -z "${DDD_RELEASE_ENV_FILE:-}" ]]; then
    echo "DDD_RELEASE_ENV_FILE is required when executing or checking closure env." >&2
    exit 1
  fi
  if [[ ! -f "${DDD_RELEASE_ENV_FILE}" ]]; then
    echo "DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}" >&2
    exit 1
  fi
  if [[ "${DDD_RELEASE_ENV_FILE}" == *"release-env-missing.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-closure-wave-env.template.env" || "${DDD_RELEASE_ENV_FILE}" == *"release-final-owner-queue-env.template.env" ]]; then
    echo "Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}" >&2
    exit 1
  fi
  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' "${DDD_RELEASE_ENV_FILE}" 2>/dev/null || node -e "const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));" "${DDD_RELEASE_ENV_FILE}")
  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then
    echo "Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600." >&2
    exit 1
  fi
  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1
fi
safe_load_release_env_file() {
  local exports
  if ! exports=$(node --input-type=module -e 'import fs from '\''node:fs'\''; import path from '\''node:path'\''; const [file, permissionCheckedArg] = process.argv.slice(1); const templateNames = new Set(['\''release-env-missing.template.env'\'', '\''release-closure-wave-env.template.env'\'', '\''release-final-owner-queue-env.template.env'\'', '\''release-env-canonical-fill.template.env'\'']); if (templateNames.has(path.basename(file))) {   console.error(`[ddd-release-env][template-refused] file=${file}`);   process.exit(1); } const permissionAlreadyChecked = permissionCheckedArg === '\''1'\'' || permissionCheckedArg === '\''true'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''1'\'' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '\''true'\''; const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777; if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {   console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '\''0'\'')} required=600`);   process.exit(1); } const text = fs.readFileSync(file, '\''utf8'\''); const quote = (value) => `'\''${String(value).replace(/'\''/g, `'\''\\'\'''\''`)}'\''`; let lineNumber = 0; for (const line of text.split(/\r?\n/)) {   lineNumber += 1;   const trimmed = line.trim();   if (!trimmed || trimmed.startsWith('\''#'\'')) continue;   const match = trimmed.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);   if (!match) {     console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);     process.exit(1);   }   let value = match[2].trim();   const quoted = value.match(/^(['\''\"])(.*)\1$/s);   if (quoted) value = quoted[2];   console.log(`export ${match[1]}=${quote(value)}`); }' "$DDD_RELEASE_ENV_FILE" "${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then
    return 1
  fi
  eval "${exports}"
}
if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" == "1" || "${DDD_RELEASE_CLOSURE_EXECUTE}" == "true" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  safe_load_release_env_file
fi
matches_closure_filter() {
  local order="$1"
  local owner="$2"
  local priority="$3"
  local kind="$4"
  if [[ -n "${DDD_RELEASE_CLOSURE_ORDER}" && "${DDD_RELEASE_CLOSURE_ORDER}" != "${order}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_OWNER}" && "${DDD_RELEASE_CLOSURE_OWNER}" != "${owner}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_PRIORITY}" && "${DDD_RELEASE_CLOSURE_PRIORITY}" != "${priority}" ]]; then return 1; fi
  if [[ -n "${DDD_RELEASE_CLOSURE_KIND}" && "${DDD_RELEASE_CLOSURE_KIND}" != "${kind}" ]]; then return 1; fi
  return 0
}
check_closure_env() {
  local order="$1"
  local owner="$2"
  shift 2
  local missing=0
  local key
  if [[ "$#" -eq 0 ]]; then
    echo "[ddd-release-closure][env-ok] order=${order} owner=${owner} requiredEnv=none"
    return 0
  fi
  for key in "$@"; do
    if [[ -z "${!key:-}" ]]; then
      echo "[ddd-release-closure][env-missing] order=${order} owner=${owner} key=${key}" >&2
      missing=1
    fi
  done
  if [[ "${missing}" == "0" ]]; then
    echo "[ddd-release-closure][env-ok] order=${order} owner=${owner}"
  fi
  return "${missing}"
}
run_closure_command() {
  local command="$1"
  if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" != "1" && "${DDD_RELEASE_CLOSURE_EXECUTE}" != "true" ]]; then
    echo "[ddd-release-closure][dry-run] ${command}"
    return 0
  fi
  set +e
  bash -lc "${command}"
  local status=$?
  set -e
  if [[ "${status}" != "0" ]]; then
    echo "[ddd-release-closure][command-failed] status=${status} command=${command}" >&2
    DDD_RELEASE_CLOSURE_COMMAND_FAILURES=$((DDD_RELEASE_CLOSURE_COMMAND_FAILURES + 1))
    if [[ "${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" == "1" || "${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" == "true" ]]; then
      echo "[ddd-release-closure][command-failed] continuing because DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}" >&2
      return 0
    fi
    return "${status}"
  fi
  return 0
}

if [[ "${DDD_RELEASE_CLOSURE_DETAIL}" == "1" || "${DDD_RELEASE_CLOSURE_DETAIL}" == "true" ]]; then
  DDD_RELEASE_CLOSURE_DETAIL_MATCHED=0
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=1'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-blocker-1'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=lumira-server: docker build failed: #5 DONE 0.3s

#4 [internal] load metadata for docker.io/library/eclipse-temurin:21-jre
#4 DONE 0.8s

#6 [internal] load .dockerignore
#6 transferring context: 309B 0.0s done
#6 DONE 0.1s

#7 [stage-1 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603
#7 resolve docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 0.1s done
#7 sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 2.28kB / 2.28kB 1.7s done
#7 sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 159B / 159B 3.6s done
#7 sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 388B / 388B 2.0s done
#7 sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 20.12MB / 20.12MB 6.9s done
#7 sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 53.12MB / 53.12MB 9.0s done
#7 sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 41.56MB / 41.56MB 7.8s done
#7 extracting sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 1.1s done
#7 extracting sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 0.1s done
#7 extracting sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 0.5s done
#7 extracting sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 0.9s done
#7 extracting sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 0.0s done
#7 extracting sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 0.0s done
#7 DONE 14.7s

#8 [stage-1 2/5] WORKDIR /app
#8 DONE 0.3s

#9 [stage-1 3/5] RUN addgroup --system app     && adduser --system --ingroup app app     && mkdir -p /tmp/nacos /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging     && chown -R app:app /tmp/nacos /tmp/sentinel /data
#9 DONE 3.3s

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 resolve docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 0.1s done
#10 sha256:583816d0be0cf3bcb3dfd452a52a7616ed7d9f22fe7f9c7be06c6d39baf0dd92 155B / 155B 1.9s done
#10 sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1 32B / 32B 2.0s done
#10 sha256:8167b4f972e8721f72bf03a1fdc669b803dfc262b27a60d62ee7486548e1c565 853B / 853B 2.0s done
#10 sha256:8583823b44413993005cb4de17065cb25a621db74ca76da9e356a8cbe97a6ff9 9.24MB / 9.24MB 5.0s done
#10 sha256:54b92ed1102d0a97c89567511b8a3e40e6283e43958991cc108b411f7eec78e4 158B / 158B 1.9s done
#10 sha256:8349365ad94cf3ebc9ff663af386f8e662102fd7528d7d5638df47ab9d044df7 22.54MB / 22.54MB 9.4s done
#10 sha256:b7f312f519fbac7fa8ab5e034ea3afc3f2f0e15c1b4f93c20f0ef6bdf5e3ba72 22.96MB / 22.96MB 11.3s done
#10 sha256:388658fb69f54e5682104e6b0cf9b8753587e33278a43c2254254fb595999c52 157.84MB / 157.84MB 26.3s done
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 96.5s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 87.2s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 101.6s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 92.3s
#10 ...

#11 [internal] load build context
#11 transferring context: 20.64MB 10.0s
#11 ...

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 106.7s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 97.4s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 114.7s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 105.4s
#10 ...

#11 [internal] load build context
#11 transferring context: 338.84MB 18.4s done
#11 DONE 18.4s

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 119.8s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 110.5s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 124.9s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 115.6s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 130.0s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 120.7s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 135.1s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 125.8s
#10 DONE 145.1s

#12 [builder  2/21] WORKDIR /workspace
#12 ERROR: short read: expected 29724688 bytes but got 0: unexpected EOF
------
 > [builder  2/21] WORKDIR /workspace:
------
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
    echo '- node bin/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=2'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-blocker-2'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
    echo '- node bin/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '3' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=3'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-image-frontend-failed'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
    echo '- node bin/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '4' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=4'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=docker-image-lumira-server-failed'
    echo 'batch=p0-docker-release-infra'
    echo 'reason=docker build failed: #5 DONE 0.3s

#4 [internal] load metadata for docker.io/library/eclipse-temurin:21-jre
#4 DONE 0.8s

#6 [internal] load .dockerignore
#6 transferring context: 309B 0.0s done
#6 DONE 0.1s

#7 [stage-1 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603
#7 resolve docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 0.1s done
#7 sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 2.28kB / 2.28kB 1.7s done
#7 sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 159B / 159B 3.6s done
#7 sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 388B / 388B 2.0s done
#7 sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 20.12MB / 20.12MB 6.9s done
#7 sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 53.12MB / 53.12MB 9.0s done
#7 sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 41.56MB / 41.56MB 7.8s done
#7 extracting sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 1.1s done
#7 extracting sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 0.1s done
#7 extracting sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 0.5s done
#7 extracting sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 0.9s done
#7 extracting sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 0.0s done
#7 extracting sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 0.0s done
#7 DONE 14.7s

#8 [stage-1 2/5] WORKDIR /app
#8 DONE 0.3s

#9 [stage-1 3/5] RUN addgroup --system app     && adduser --system --ingroup app app     && mkdir -p /tmp/nacos /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging     && chown -R app:app /tmp/nacos /tmp/sentinel /data
#9 DONE 3.3s

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 resolve docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 0.1s done
#10 sha256:583816d0be0cf3bcb3dfd452a52a7616ed7d9f22fe7f9c7be06c6d39baf0dd92 155B / 155B 1.9s done
#10 sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1 32B / 32B 2.0s done
#10 sha256:8167b4f972e8721f72bf03a1fdc669b803dfc262b27a60d62ee7486548e1c565 853B / 853B 2.0s done
#10 sha256:8583823b44413993005cb4de17065cb25a621db74ca76da9e356a8cbe97a6ff9 9.24MB / 9.24MB 5.0s done
#10 sha256:54b92ed1102d0a97c89567511b8a3e40e6283e43958991cc108b411f7eec78e4 158B / 158B 1.9s done
#10 sha256:8349365ad94cf3ebc9ff663af386f8e662102fd7528d7d5638df47ab9d044df7 22.54MB / 22.54MB 9.4s done
#10 sha256:b7f312f519fbac7fa8ab5e034ea3afc3f2f0e15c1b4f93c20f0ef6bdf5e3ba72 22.96MB / 22.96MB 11.3s done
#10 sha256:388658fb69f54e5682104e6b0cf9b8753587e33278a43c2254254fb595999c52 157.84MB / 157.84MB 26.3s done
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 96.5s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 87.2s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 101.6s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 92.3s
#10 ...

#11 [internal] load build context
#11 transferring context: 20.64MB 10.0s
#11 ...

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 106.7s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 97.4s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 114.7s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 105.4s
#10 ...

#11 [internal] load build context
#11 transferring context: 338.84MB 18.4s done
#11 DONE 18.4s

#10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 119.8s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 110.5s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 124.9s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 115.6s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 130.0s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 120.7s
#10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 135.1s
#10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 125.8s
#10 DONE 145.1s

#12 [builder  2/21] WORKDIR /workspace
#12 ERROR: short read: expected 29724688 bytes but got 0: unexpected EOF
------
 > [builder  2/21] WORKDIR /workspace:
------
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF'
    echo 'envKeys=DDD_DOCKER_BUILD_STRICT;DDD_DOCKER_COMMAND'
    echo "commands:"
    echo '- DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
    echo '- node bin/ddd-docker-build-evidence.mjs'
  fi
  if matches_closure_filter '5' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=5'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-1'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.strict must be true for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node bin/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '6' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=6'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-2'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.https must be true for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node bin/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '7' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=7'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-3'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.localOnly must be false for strict release evidence'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node bin/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '8' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=8'
    echo 'owner=release-infra'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=runtime-readiness-contract-4'
    echo 'batch=p0-runtime-readiness-release-infra'
    echo 'reason=runtime readiness productionEquivalence.deploymentEvidence is required'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_EVIDENCE_OPERATOR;DDD_RELEASE_CANDIDATE;LUMIRA_BASE_URL'
    echo "commands:"
    echo '- node bin/ddd-runtime-readiness-smoke.mjs'
  fi
  if matches_closure_filter '9' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=9'
    echo 'owner=lumira-ui'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=manifest-missing-lumira-ui-frontend-smoke-json'
    echo 'batch=p0-manifest-lumira-ui'
    echo 'reason=missing artifact lumira-ui/frontend-smoke.json'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_FRONTEND_EXPECT_DEPLOYED;DDD_RELEASE_CANDIDATE;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
    echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  fi
  if matches_closure_filter '10' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=10'
    echo 'owner=lumira-ui'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=manifest-missing-lumira-ui-lumira-ui-build-evidence-json'
    echo 'batch=p0-manifest-lumira-ui'
    echo 'reason=missing artifact lumira-ui/lumira-ui-build-evidence.json'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_FRONTEND_EXPECT_DEPLOYED;DDD_RELEASE_CANDIDATE;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
    echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  fi
  if matches_closure_filter '11' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=11'
    echo 'owner=lumira-ui'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=manifest-missing-lumira-ui-lumira-ui-static-evidence-json'
    echo 'batch=p0-manifest-lumira-ui'
    echo 'reason=missing artifact lumira-ui/lumira-ui-static-evidence.json'
    echo 'envKeys=DDD_EVIDENCE_ENVIRONMENT;DDD_FRONTEND_EXPECT_DEPLOYED;DDD_RELEASE_CANDIDATE;PLAYWRIGHT_BASE_URL'
    echo "commands:"
    echo '- DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
    echo '- DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
  fi
  if matches_closure_filter '12' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=12'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-1'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.strict must be true for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '13' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=13'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-actual-shape-2'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.https must be true for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '14' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=14'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-3'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '15' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=15'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_LOCAL'
    echo 'id=performance-actual-shape-4'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=authenticated performance actual productionEquivalence.deploymentEvidence is required'
    echo 'envKeys=none'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '16' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=16'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-5'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=strict release baseline requires baselineType=authenticated-runtime'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '17' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=17'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-6'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=acceptedAt must be an ISO timestamp'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '18' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=18'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-7'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=acceptedBy is required'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '19' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=19'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-8'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=sourceArtifact is required'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if matches_closure_filter '20' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1
    echo 'order=20'
    echo 'owner=release-performance'
    echo 'priority=P0'
    echo 'closureKind=RUN_NOW_WITH_REAL_ENV'
    echo 'id=performance-baseline-metadata-9'
    echo 'batch=p0-authenticated-performance-release-performance'
    echo 'reason=sourceSha256 must be a SHA-256 hex digest'
    echo 'envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY;DDD_AUTH_PERF_BASELINE_ENVIRONMENT;DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT;DDD_RELEASE_CANDIDATE'
    echo "commands:"
    echo '- node bin/ddd-authenticated-performance-smoke.mjs'
    echo '- node bin/ddd-promote-performance-baseline.mjs'
  fi
  if [[ "${DDD_RELEASE_CLOSURE_DETAIL_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  exit 0
fi

if [[ "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "1" || "${DDD_RELEASE_CLOSURE_CHECK_ENV}" == "true" ]]; then
  DDD_RELEASE_CLOSURE_ENV_MATCHED=0
  DDD_RELEASE_CLOSURE_ENV_FAILED=0
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '1' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '2' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '3' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '3' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '4' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '4' 'release-infra' 'DDD_DOCKER_BUILD_STRICT' 'DDD_DOCKER_COMMAND' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '5' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '5' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '6' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '6' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '7' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '7' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '8' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '8' 'release-infra' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_EVIDENCE_OPERATOR' 'DDD_RELEASE_CANDIDATE' 'LUMIRA_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '9' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '9' 'lumira-ui' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '10' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '10' 'lumira-ui' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '11' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '11' 'lumira-ui' 'DDD_EVIDENCE_ENVIRONMENT' 'DDD_FRONTEND_EXPECT_DEPLOYED' 'DDD_RELEASE_CANDIDATE' 'PLAYWRIGHT_BASE_URL' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '12' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '12' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '13' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '13' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '14' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '14' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '15' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '15' 'release-performance' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '16' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '16' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '17' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '17' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '18' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '18' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '19' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '19' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if matches_closure_filter '20' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    DDD_RELEASE_CLOSURE_ENV_MATCHED=1
    check_closure_env '20' 'release-performance' 'DDD_AUTH_PERF_BASELINE_ACCEPTED_BY' 'DDD_AUTH_PERF_BASELINE_ENVIRONMENT' 'DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT' 'DDD_RELEASE_CANDIDATE' || DDD_RELEASE_CLOSURE_ENV_FAILED=1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_ENV_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_ENV_FAILED}" != "0" ]]; then exit 1; fi
  exit 0
fi

if [[ "${DDD_RELEASE_CLOSURE_EXECUTE}" != "1" && "${DDD_RELEASE_CLOSURE_EXECUTE}" != "true" ]]; then
  echo "Runnable release blocker closure waves:"
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '1 P0 owner=release-infra batch=p0-docker-release-infra items=1;2;3;4'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-infra' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '2 P0 owner=release-infra batch=p0-runtime-readiness-release-infra items=5;6;7;8'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'lumira-ui' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '3 P0 owner=lumira-ui batch=p0-manifest-lumira-ui items=9;10;11'
  fi
  if [[ ( -z "${DDD_RELEASE_CLOSURE_OWNER}" || "${DDD_RELEASE_CLOSURE_OWNER}" == 'release-performance' ) && ( -z "${DDD_RELEASE_CLOSURE_PRIORITY}" || "${DDD_RELEASE_CLOSURE_PRIORITY}" == 'P0' ) ]]; then
    echo '4 P0 owner=release-performance batch=p0-authenticated-performance-release-performance items=12;13;14;15;16;17;18;19;20'
  fi
  echo ""
  echo "Runnable release blocker closure items:"
  if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '1 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-blocker-1 batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '2 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-blocker-2 batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '3' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '3 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-image-frontend-failed batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '4' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '4 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-image-lumira-server-failed batch=p0-docker-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '5' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '5 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-1 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '6' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '6 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-2 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '7' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '7 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-3 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '8' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '8 P0 RUN_NOW_WITH_REAL_ENV owner=release-infra id=runtime-readiness-contract-4 batch=p0-runtime-readiness-release-infra'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '9' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '9 P0 RUN_NOW_WITH_REAL_ENV owner=lumira-ui id=manifest-missing-lumira-ui-frontend-smoke-json batch=p0-manifest-lumira-ui'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '10' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '10 P0 RUN_NOW_WITH_REAL_ENV owner=lumira-ui id=manifest-missing-lumira-ui-lumira-ui-build-evidence-json batch=p0-manifest-lumira-ui'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '11' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '11 P0 RUN_NOW_WITH_REAL_ENV owner=lumira-ui id=manifest-missing-lumira-ui-lumira-ui-static-evidence-json batch=p0-manifest-lumira-ui'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '12' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '12 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-1 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '13' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '13 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-actual-shape-2 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '14' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '14 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-3 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '15' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
    echo '15 P0 RUN_NOW_LOCAL owner=release-performance id=performance-actual-shape-4 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '16' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '16 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-5 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '17' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '17 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-6 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '18' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '18 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-7 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '19' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '19 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-8 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if matches_closure_filter '20' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
    echo '20 P0 RUN_NOW_WITH_REAL_ENV owner=release-performance id=performance-baseline-metadata-9 batch=p0-authenticated-performance-release-performance'
    DDD_RELEASE_CLOSURE_MATCHED=1
  fi
  if [[ "${DDD_RELEASE_CLOSURE_MATCHED}" != "1" ]]; then
    echo "No runnable closure item matched the requested filters." >&2
    exit 1
  fi
  exit 0
fi

if matches_closure_filter '1' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=1 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-blocker-1'
# Reason: lumira-server: docker build failed: #5 DONE 0.3s  #4 [internal] load metadata for docker.io/library/eclipse-temurin:21-jre #4 DONE 0.8s  #6 [internal] load .dockerignore #6 transferring context: 309B 0.0s done #6 DONE 0.1s  #7 [stage-1 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 #7 resolve docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 0.1s done #7 sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 2.28kB / 2.28kB 1.7s done #7 sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 159B / 159B 3.6s done #7 sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 388B / 388B 2.0s done #7 sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 20.12MB / 20.12MB 6.9s done #7 sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 53.12MB / 53.12MB 9.0s done #7 sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 41.56MB / 41.56MB 7.8s done #7 extracting sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 1.1s done #7 extracting sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 0.1s done #7 extracting sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 0.5s done #7 extracting sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 0.9s done #7 extracting sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 0.0s done #7 extracting sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 0.0s done #7 DONE 14.7s  #8 [stage-1 2/5] WORKDIR /app #8 DONE 0.3s  #9 [stage-1 3/5] RUN addgroup --system app     && adduser --system --ingroup app app     && mkdir -p /tmp/nacos /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging     && chown -R app:app /tmp/nacos /tmp/sentinel /data #9 DONE 3.3s  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 resolve docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 0.1s done #10 sha256:583816d0be0cf3bcb3dfd452a52a7616ed7d9f22fe7f9c7be06c6d39baf0dd92 155B / 155B 1.9s done #10 sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1 32B / 32B 2.0s done #10 sha256:8167b4f972e8721f72bf03a1fdc669b803dfc262b27a60d62ee7486548e1c565 853B / 853B 2.0s done #10 sha256:8583823b44413993005cb4de17065cb25a621db74ca76da9e356a8cbe97a6ff9 9.24MB / 9.24MB 5.0s done #10 sha256:54b92ed1102d0a97c89567511b8a3e40e6283e43958991cc108b411f7eec78e4 158B / 158B 1.9s done #10 sha256:8349365ad94cf3ebc9ff663af386f8e662102fd7528d7d5638df47ab9d044df7 22.54MB / 22.54MB 9.4s done #10 sha256:b7f312f519fbac7fa8ab5e034ea3afc3f2f0e15c1b4f93c20f0ef6bdf5e3ba72 22.96MB / 22.96MB 11.3s done #10 sha256:388658fb69f54e5682104e6b0cf9b8753587e33278a43c2254254fb595999c52 157.84MB / 157.84MB 26.3s done #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 96.5s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 87.2s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 101.6s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 92.3s #10 ...  #11 [internal] load build context #11 transferring context: 20.64MB 10.0s #11 ...  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 106.7s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 97.4s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 114.7s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 105.4s #10 ...  #11 [internal] load build context #11 transferring context: 338.84MB 18.4s done #11 DONE 18.4s  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 119.8s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 110.5s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 124.9s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 115.6s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 130.0s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 120.7s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 135.1s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 125.8s #10 DONE 145.1s  #12 [builder  2/21] WORKDIR /workspace #12 ERROR: short read: expected 29724688 bytes but got 0: unexpected EOF ------  > [builder  2/21] WORKDIR /workspace: ------ ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
  run_closure_command 'node bin/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '2' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=2 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-blocker-2'
# Reason: frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
  run_closure_command 'node bin/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '3' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=3 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-image-frontend-failed'
# Reason: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
  run_closure_command 'node bin/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '4' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=4 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=docker-image-lumira-server-failed'
# Reason: docker build failed: #5 DONE 0.3s  #4 [internal] load metadata for docker.io/library/eclipse-temurin:21-jre #4 DONE 0.8s  #6 [internal] load .dockerignore #6 transferring context: 309B 0.0s done #6 DONE 0.1s  #7 [stage-1 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 #7 resolve docker.io/library/eclipse-temurin:21-jre@sha256:8ec353b20d3aab0758572236b81b967c7077c40c4d0819ce97f9a1329d684603 0.1s done #7 sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 2.28kB / 2.28kB 1.7s done #7 sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 159B / 159B 3.6s done #7 sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 388B / 388B 2.0s done #7 sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 20.12MB / 20.12MB 6.9s done #7 sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 53.12MB / 53.12MB 9.0s done #7 sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 41.56MB / 41.56MB 7.8s done #7 extracting sha256:81e2f2053c8fa702b6863110b55c09e67f6adeb78b4672745958c4d8b3d056c5 1.1s done #7 extracting sha256:d1f56e4c7f2f2a1415c59803638274d488a73b61a8e1f9cbd9cb280327e8d21e 0.1s done #7 extracting sha256:615a4ff2c6307fd0c5e826eee696ae3f0033453e344616ea7fb5f682b3ccfb9d 0.5s done #7 extracting sha256:7852e663f18cd4bf5da0f535caacba2bd355d89d8b4df3868e0b59dba43d2cf5 0.9s done #7 extracting sha256:860799bd61a18c5249afc515d19d73e68f1a67387c6d87f7bc06d1a09ec03694 0.0s done #7 extracting sha256:dde47f424aaad69abbdd8df6625b8780c63c1ae7ab0cda40553c8afb70ab0a0a 0.0s done #7 DONE 14.7s  #8 [stage-1 2/5] WORKDIR /app #8 DONE 0.3s  #9 [stage-1 3/5] RUN addgroup --system app     && adduser --system --ingroup app app     && mkdir -p /tmp/nacos /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging     && chown -R app:app /tmp/nacos /tmp/sentinel /data #9 DONE 3.3s  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 resolve docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 0.1s done #10 sha256:583816d0be0cf3bcb3dfd452a52a7616ed7d9f22fe7f9c7be06c6d39baf0dd92 155B / 155B 1.9s done #10 sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1 32B / 32B 2.0s done #10 sha256:8167b4f972e8721f72bf03a1fdc669b803dfc262b27a60d62ee7486548e1c565 853B / 853B 2.0s done #10 sha256:8583823b44413993005cb4de17065cb25a621db74ca76da9e356a8cbe97a6ff9 9.24MB / 9.24MB 5.0s done #10 sha256:54b92ed1102d0a97c89567511b8a3e40e6283e43958991cc108b411f7eec78e4 158B / 158B 1.9s done #10 sha256:8349365ad94cf3ebc9ff663af386f8e662102fd7528d7d5638df47ab9d044df7 22.54MB / 22.54MB 9.4s done #10 sha256:b7f312f519fbac7fa8ab5e034ea3afc3f2f0e15c1b4f93c20f0ef6bdf5e3ba72 22.96MB / 22.96MB 11.3s done #10 sha256:388658fb69f54e5682104e6b0cf9b8753587e33278a43c2254254fb595999c52 157.84MB / 157.84MB 26.3s done #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 96.5s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 87.2s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 101.6s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 92.3s #10 ...  #11 [internal] load build context #11 transferring context: 20.64MB 10.0s #11 ...  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 106.7s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 97.4s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 114.7s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 105.4s #10 ...  #11 [internal] load build context #11 transferring context: 338.84MB 18.4s done #11 DONE 18.4s  #10 [builder  1/21] FROM docker.io/library/maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 119.8s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 110.5s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 124.9s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 115.6s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 130.0s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 120.7s #10 sha256:919c22d58535d0a293684e9385c199a76f0a3c4cdfdb02257191974dc2dabfa1 0B / 2.28kB 135.1s #10 sha256:20043066d3d5c78b45520c5707319835ac7d1f3d7f0dded0138ea0897d6a3188 0B / 29.72MB 125.8s #10 DONE 145.1s  #12 [builder  2/21] WORKDIR /workspace #12 ERROR: short read: expected 29724688 bytes but got 0: unexpected EOF ------  > [builder  2/21] WORKDIR /workspace: ------ ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF
# Expected artifacts: artifacts/ddd/build/docker-image-evidence.json
  run_closure_command 'DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs'
  run_closure_command 'node bin/ddd-docker-build-evidence.mjs'
fi

if matches_closure_filter '5' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=5 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-1'
# Reason: runtime readiness productionEquivalence.strict must be true for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node bin/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '6' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=6 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-2'
# Reason: runtime readiness productionEquivalence.https must be true for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node bin/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '7' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=7 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-3'
# Reason: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node bin/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '8' 'release-infra' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=8 owner=release-infra priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=runtime-readiness-contract-4'
# Reason: runtime readiness productionEquivalence.deploymentEvidence is required
# Expected artifacts: artifacts/ddd/readiness/summary.json
  run_closure_command 'node bin/ddd-runtime-readiness-smoke.mjs'
fi

if matches_closure_filter '9' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=9 owner=lumira-ui priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=manifest-missing-lumira-ui-frontend-smoke-json'
# Reason: missing artifact lumira-ui/frontend-smoke.json
# Expected artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json; artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json; artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json; artifacts/ddd/release/evidence-manifest.json
  run_closure_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
  run_closure_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

if matches_closure_filter '10' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=10 owner=lumira-ui priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=manifest-missing-lumira-ui-lumira-ui-build-evidence-json'
# Reason: missing artifact lumira-ui/lumira-ui-build-evidence.json
# Expected artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json; artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json; artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json; artifacts/ddd/release/evidence-manifest.json
  run_closure_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
  run_closure_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

if matches_closure_filter '11' 'lumira-ui' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=11 owner=lumira-ui priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=manifest-missing-lumira-ui-lumira-ui-static-evidence-json'
# Reason: missing artifact lumira-ui/lumira-ui-static-evidence.json
# Expected artifacts: artifacts/ddd/lumira-ui/frontend-smoke.json; artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json; artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json; artifacts/ddd/release/evidence-manifest.json
  run_closure_command 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
  run_closure_command 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs'
fi

if matches_closure_filter '12' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=12 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-1'
# Reason: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '13' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=13 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-actual-shape-2'
# Reason: authenticated performance actual productionEquivalence.https must be true for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '14' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=14 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-3'
# Reason: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '15' 'release-performance' 'P0' 'RUN_NOW_LOCAL'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=15 owner=release-performance priority=P0 kind=RUN_NOW_LOCAL id=performance-actual-shape-4'
# Reason: authenticated performance actual productionEquivalence.deploymentEvidence is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '16' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=16 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-5'
# Reason: strict release baseline requires baselineType=authenticated-runtime
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '17' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=17 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-6'
# Reason: acceptedAt must be an ISO timestamp
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '18' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=18 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-7'
# Reason: acceptedBy is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '19' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=19 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-8'
# Reason: sourceArtifact is required
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if matches_closure_filter '20' 'release-performance' 'P0' 'RUN_NOW_WITH_REAL_ENV'; then
  DDD_RELEASE_CLOSURE_MATCHED=1
  echo '[ddd-release-closure] running order=20 owner=release-performance priority=P0 kind=RUN_NOW_WITH_REAL_ENV id=performance-baseline-metadata-9'
# Reason: sourceSha256 must be a SHA-256 hex digest
# Expected artifacts: artifacts/ddd/performance/authenticated-runtime-actual.json; artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json; artifacts/ddd/performance/authenticated-runtime-baseline.json
  run_closure_command 'node bin/ddd-authenticated-performance-smoke.mjs'
  run_closure_command 'node bin/ddd-promote-performance-baseline.mjs'
fi

if [[ "${DDD_RELEASE_CLOSURE_MATCHED}" != "1" ]]; then
  echo "No runnable closure item matched the requested filters." >&2
  exit 1
fi

# After closure commands refresh artifacts, rerun:
run_closure_command 'node bin/ddd-release-evidence-gate.mjs'
run_closure_command 'node bin/ddd-release-readiness-summary.mjs'
run_closure_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'
if [[ "${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}" != "0" ]]; then
  echo "[ddd-release-closure][completed-with-failures] commandFailures=${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}" >&2
  exit 1
fi
