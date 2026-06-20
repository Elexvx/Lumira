# DDD Release Blocker Closure Plan

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
releaseEnvFileCutoverSafe: false
RUN_NOW_LOCAL: 3
RUN_NOW_WITH_REAL_ENV: 17
WAIT_FOR_DEPENDENCIES: 22
Runnable waves: 4
Owner input receipt status: PENDING_OWNER_INPUT
Owner input receipt cutover ready: false
Owner input receipt required inputs: 0
Owner input receipt pending owners: 0

## Owner Input Receipt

- Status: PENDING_OWNER_INPUT
- Cutover ready: false
- Required owner inputs: 0
- Owners: 0
- Pending owners: 0
- Missing criteria:
  - releaseEnvReadinessStatus
- Pending owner inputs:
  - none

## Runnable Waves

### Wave 1. release-infra / p0-docker-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 1, 2, 3, 4
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

### Wave 2. release-infra / p0-runtime-readiness-release-infra

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 5, 6, 7, 8
- Item ids: runtime-readiness-contract-1, runtime-readiness-contract-2, runtime-readiness-contract-3, runtime-readiness-contract-4
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

### Wave 3. lumira-ui / p0-manifest-lumira-ui

- Priority: P0
- Closure kinds: RUN_NOW_WITH_REAL_ENV
- Item orders: 9, 10, 11
- Item ids: manifest-missing-lumira-ui-frontend-smoke-json, manifest-missing-lumira-ui-lumira-ui-build-evidence-json, manifest-missing-lumira-ui-lumira-ui-static-evidence-json
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`

### Wave 4. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Closure kinds: RUN_NOW_LOCAL, RUN_NOW_WITH_REAL_ENV
- Item orders: 12, 13, 14, 15, 16, 17, 18, 19, 20
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## Items

## 1. release-infra / docker-blocker-1

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: lumira-server: docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF
- Action: Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 2. release-infra / docker-blocker-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
- Action: Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 3. release-infra / docker-image-frontend-failed

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
- Action: Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 4. release-infra / docker-image-lumira-server-failed

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: docker
- Batch: p0-docker-release-infra (ready)
- Dependencies: none
- Reason: docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF
- Action: Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`

## 5. release-infra / runtime-readiness-contract-1

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.strict must be true for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 6. release-infra / runtime-readiness-contract-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.https must be true for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 7. release-infra / runtime-readiness-contract-3

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 8. release-infra / runtime-readiness-contract-4

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: runtime-readiness
- Batch: p0-runtime-readiness-release-infra (ready)
- Dependencies: none
- Reason: runtime readiness productionEquivalence.deploymentEvidence is required
- Action: Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`

## 9. lumira-ui / manifest-missing-lumira-ui-frontend-smoke-json

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: manifest
- Batch: p0-manifest-lumira-ui (ready)
- Dependencies: none
- Reason: missing artifact lumira-ui/frontend-smoke.json
- Action: Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`

## 10. lumira-ui / manifest-missing-lumira-ui-lumira-ui-build-evidence-json

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: manifest
- Batch: p0-manifest-lumira-ui (ready)
- Dependencies: none
- Reason: missing artifact lumira-ui/lumira-ui-build-evidence.json
- Action: Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`

## 11. lumira-ui / manifest-missing-lumira-ui-lumira-ui-static-evidence-json

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: manifest
- Batch: p0-manifest-lumira-ui (ready)
- Dependencies: none
- Reason: missing artifact lumira-ui/lumira-ui-static-evidence.json
- Action: Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`

## 12. release-performance / performance-actual-shape-1

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 13. release-performance / performance-actual-shape-2

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 14. release-performance / performance-actual-shape-3

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 15. release-performance / performance-actual-shape-4

- Closure kind: RUN_NOW_LOCAL
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: authenticated performance actual productionEquivalence.deploymentEvidence is required
- Action: Fix authenticated performance actual artifact shape and rerun the smoke.
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 16. release-performance / performance-baseline-metadata-5

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: strict release baseline requires baselineType=authenticated-runtime
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 17. release-performance / performance-baseline-metadata-6

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: acceptedAt must be an ISO timestamp
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 18. release-performance / performance-baseline-metadata-7

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: acceptedBy is required
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 19. release-performance / performance-baseline-metadata-8

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: sourceArtifact is required
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 20. release-performance / performance-baseline-metadata-9

- Closure kind: RUN_NOW_WITH_REAL_ENV
- Priority: P0
- Source: authenticated-performance
- Batch: p0-authenticated-performance-release-performance (ready)
- Dependencies: none
- Reason: sourceSha256 must be a SHA-256 hex digest
- Action: Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`

## 21. ai / ai-owner-gateway

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: ownerGateway status=CONFIGURED configuredOwners=0
- Action: Configure and verify remote AI owner gateways for IAM/File/Platform integrations.
- Env keys: DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 22. ai / ai-provider-runtime

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: provider status=CONFIGURED remoteConfigured=false
- Action: Configure and verify a remote AI provider runtime; strict release must not rely on local fallback.
- Env keys: DDD_AI_EXPECT_PROVIDER_REMOTE, LUMIRA_AI_PROVIDER, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 23. ai / ai-runtime-base-url

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: ai-runtime
- Batch: p1-ai-runtime-ai (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: missing production-equivalent AI base URL
- Action: Run AI runtime drill against an HTTPS non-local AI runtime base URL.
- Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, LUMIRA_AI_BASE_URL, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`

## 24. file-owner / file-processing-production-equivalence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-file-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Action: Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node bin/ddd-file-processing-e2e-smoke.mjs`.
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT
- Commands:
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`

## 25. job-owner / job-e2e-production-equivalence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-job-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080
- Action: Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node bin/ddd-job-e2e-smoke.mjs`.
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN
- Commands:
  - `node bin/ddd-job-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`

## 26. payment-owner / payment-webhook-artifact

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: business-e2e
- Batch: p1-business-e2e-payment-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: missing payment webhook artifact artifacts\ddd\payment\payment-webhook-e2e.json
- Action: Run payment webhook E2E smoke with `node bin/ddd-payment-webhook-e2e-smoke.mjs` and attach payment-webhook-e2e.json evidence.
- Env keys: LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL
- Commands:
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`

## 27. ai-owner / AI

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-ai-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: AI rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence. Required evidence: AI provider disablement or fallback configuration evidence; knowledge index job pause/resume command or job output; document index rebuild or retry evidence; degraded chat/search transcript after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 28. auth-owner / Auth

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-auth-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Auth rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. Required evidence: login smoke result after adapter rollback; session TTL compatibility evidence; forced logout or keepalive behavior evidence; auth readiness/health response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 29. file-owner / File

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-file-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: File rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence. Required evidence: file processing pause/resume command or job output; stable object-key read evidence after rollback; processing task rerun by id with final state; storage artifact or upload row proving access continuity. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 30. iam-owner / IAM

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-iam-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: IAM rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. Required evidence: permission snapshot version before and after rollback; cache invalidation or version bump evidence; IAM v2 readiness/health response after rollback; audit entry or command log for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 31. job-owner / Job

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-job-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Job rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence. Required evidence: XXL-JOB handler disablement or dashboard evidence; manual owner internal endpoint fallback result; internal job token provenance or redacted request evidence; job readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 32. localization-owner / Localization

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-localization-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Localization rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. Required evidence: localization release id before and after rollback; runtime bundle cache clear evidence; bundle request or metrics proving rolled-back release is served; localization audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 33. message-owner / Message

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-message-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Message rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. Required evidence: message relay pause/resume command or job output; delivery fallback evidence for at least one notice; idempotent replay result with duplicate-safe state; message readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 34. payment-owner / Payment

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-payment-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Payment rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence. Required evidence: payment provider route fallback configuration evidence; webhook idempotent replay result; order status trace before and after replay; webhook metrics or audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 35. platform-owner / Platform

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-platform-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Platform rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. Required evidence: runtime appearance/config version before and after rollback; cache clear or version invalidation evidence; bootstrap response using the rolled-back config; platform audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 36. plugin-owner / Plugin

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P1
- Source: rollback
- Batch: p1-rollback-plugin-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Reason: Plugin rollback drill is DEFERRED with approved deferral evidence
- Action: Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. Required evidence: tenant plugin disable or version rollback command output; bootstrap projection rebuild evidence; tenant plugin projection row before and after rollback; plugin audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`

## 37. database / message-archive-total.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: [plan] message-archive-total.json: archive_candidates does not report an index key for access_type=ALL | [plan] message-archive-total.json: archive_candidates uses full scan access_type=ALL
- Action: Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 38. database / message-unread-count.json

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P2
- Source: explain
- Batch: p2-explain-database (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Reason: [plan] message-unread-count.json: unread_candidates does not report an index key for access_type=ALL | [plan] message-unread-count.json: unread_candidates uses full scan access_type=ALL
- Action: Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
- Env keys: DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT, DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE, MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/explain-gate-report.json`
  - `tmp/ddd-explain/*.json`

## 39. database / orchestrator-preflight-migration-runtime-evidence

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-database (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- Action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

## 40. release-infra / orchestrator-preflight-backend-runtime-base-url

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-release-infra (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: missing backend runtime base URL
- Action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

## 41. release-infra / orchestrator-preflight-frontend-runtime-base-url

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-release-infra (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: missing deployed frontend base URL
- Action: Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- Env keys: FRONTEND_BASE_URL, PLAYWRIGHT_BASE_URL
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

## 42. release-owner / orchestrator-run-mode

- Closure kind: WAIT_FOR_DEPENDENCIES
- Priority: P3
- Source: orchestrator
- Batch: p3-orchestrator-release-owner (blocked)
- Dependencies: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Reason: strict release requires run mode report, got plan
- Action: Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node bin/ddd-release-evidence-orchestrator.mjs`.
- Env keys: DDD_RELEASE_EVIDENCE_STRICT
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/readiness-summary.json`
  - `artifacts/ddd/release/release-evidence-gate.json`

