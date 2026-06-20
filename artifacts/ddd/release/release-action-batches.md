# DDD Release Action Batches

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 148
Batch count: 22
Total pending items: 42

## Execution Notes

- Batches are ordered by release priority, source order, and owner.
- Batch `id`, `dependsOn`, and `canRunImmediately` define the machine-readable execution graph.
- P0 batches can run immediately; P1/P2/P3 batches should wait until their dependencies meet exit criteria and the release gate is rerun.
- Commands are hints extracted from action text; environment evidence still has to be real and production-equivalent.
- The current release gate remains authoritative after every batch; strict mode is required for final release approval.

## Batches

### 1. P0 docker -> release-infra

- Batch id: p0-docker-release-infra
- Depends on: none
- Can run immediately: true
- Pending items: 4
- Env keys: DDD_DOCKER_BUILD_STRICT, DDD_DOCKER_COMMAND
- Env check groups:
  - `DDD_DOCKER_BUILD_STRICT=DDD_DOCKER_BUILD_STRICT`
  - `DDD_DOCKER_COMMAND=DDD_DOCKER_COMMAND`
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
- Exit criteria:
  - Docker CLI and daemon are available in the evidence runner.
  - Required lumira-server and lumira-ui images are built, inspected, and not skipped.
  - Clear this batch before running downstream runtime-heavy evidence.

- docker-blocker-1: lumira-server: docker build failed: #5 DONE 0.3s

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
- docker-blocker-2: frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
- docker-image-frontend-failed: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
- docker-image-lumira-server-failed: docker build failed: #5 DONE 0.3s

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

### 2. P0 runtime-readiness -> release-infra

- Batch id: p0-runtime-readiness-release-infra
- Depends on: none
- Can run immediately: true
- Pending items: 4
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, LUMIRA_BASE_URL
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`
- Exit criteria:
  - Runtime readiness is generated from an HTTPS non-local backend base URL.
  - All 30 owner readiness/health/metrics checks pass.
  - Clear this batch before running downstream runtime-heavy evidence.

- runtime-readiness-contract-1: runtime readiness productionEquivalence.strict must be true for strict release evidence
- runtime-readiness-contract-2: runtime readiness productionEquivalence.https must be true for strict release evidence
- runtime-readiness-contract-3: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- runtime-readiness-contract-4: runtime readiness productionEquivalence.deploymentEvidence is required

### 3. P0 manifest -> lumira-ui

- Batch id: p0-manifest-lumira-ui
- Depends on: none
- Can run immediately: true
- Pending items: 3
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_FRONTEND_EXPECT_DEPLOYED, DDD_RELEASE_CANDIDATE, PLAYWRIGHT_BASE_URL
- Env check groups:
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_FRONTEND_EXPECT_DEPLOYED=DDD_FRONTEND_EXPECT_DEPLOYED`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
- Exit criteria:
  - All required release evidence artifacts are present and checksummed.
  - Clear this batch before running downstream runtime-heavy evidence.

- manifest-missing-lumira-ui-frontend-smoke-json: missing artifact lumira-ui/frontend-smoke.json
- manifest-missing-lumira-ui-lumira-ui-build-evidence-json: missing artifact lumira-ui/lumira-ui-build-evidence.json
- manifest-missing-lumira-ui-lumira-ui-static-evidence-json: missing artifact lumira-ui/lumira-ui-static-evidence.json

### 4. P0 authenticated-performance -> release-performance

- Batch id: p0-authenticated-performance-release-performance
- Depends on: none
- Can run immediately: true
- Pending items: 9
- Env keys: DDD_AUTH_PERF_BASELINE_ACCEPTED_BY, DDD_AUTH_PERF_BASELINE_ENVIRONMENT, DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT, DDD_RELEASE_CANDIDATE
- Env check groups:
  - `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY`
  - `DDD_AUTH_PERF_BASELINE_ENVIRONMENT=DDD_AUTH_PERF_BASELINE_ENVIRONMENT`
  - `DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Exit criteria:
  - Authenticated performance actual is generated from a production-equivalent HTTPS backend.
  - Accepted baseline exists and current p95/upload metrics do not regress beyond the configured threshold.
  - Clear this batch before running downstream runtime-heavy evidence.

- performance-actual-shape-1: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- performance-actual-shape-2: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- performance-actual-shape-3: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- performance-actual-shape-4: authenticated performance actual productionEquivalence.deploymentEvidence is required
- performance-baseline-metadata-5: strict release baseline requires baselineType=authenticated-runtime
- performance-baseline-metadata-6: acceptedAt must be an ISO timestamp
- performance-baseline-metadata-7: acceptedBy is required
- performance-baseline-metadata-8: sourceArtifact is required
- performance-baseline-metadata-9: sourceSha256 must be a SHA-256 hex digest

### 5. P1 ai-runtime -> ai

- Batch id: p1-ai-runtime-ai
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 3
- Env keys: 12 keys
  - BASE_URL, DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE, DDD_AI_EXPECT_PROVIDER_REMOTE, DEPLOY_CHECK_BASE_URL
  - LUMIRA_AI_BASE_URL, LUMIRA_AI_OWNER_FILE_BASE_URL, LUMIRA_AI_OWNER_IAM_BASE_URL, LUMIRA_AI_OWNER_PLATFORM_BASE_URL
  - LUMIRA_AI_PROVIDER, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY, LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL, LUMIRA_BASE_URL
- Env check groups: 11 groups
  - `BASE_URL=BASE_URL`
  - `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE`
  - `DDD_AI_EXPECT_PROVIDER_REMOTE=DDD_AI_EXPECT_PROVIDER_REMOTE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `AI_SERVICE_BASE_URL=AI_SERVICE_BASE_URL|LUMIRA_AI_BASE_URL|LUMIRA_AI_SERVICE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL=LUMIRA_AI_OWNER_FILE_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL=LUMIRA_AI_OWNER_IAM_BASE_URL|LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL`
  - `LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL=LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL|LUMIRA_AI_OWNER_PLATFORM_BASE_URL`
  - `LUMIRA_AI_PROVIDER=LUMIRA_AI_PROVIDER`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=LUMIRA_AI_PROVIDER_API_KEY|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY`
  - `LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL=LUMIRA_AI_PROVIDER_BASE_URL|LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL`
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Exit criteria:
  - AI runtime drill uses HTTPS non-local base URL with remote provider and owner gateway expectations enabled.
  - Provider is not local fallback and owner gateway has configured owner integrations.

- ai-owner-gateway: ownerGateway status=CONFIGURED configuredOwners=0
- ai-provider-runtime: provider status=CONFIGURED remoteConfigured=false
- ai-runtime-base-url: missing production-equivalent AI base URL

### 6. P1 business-e2e -> file-owner

- Batch id: p1-business-e2e-file-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN, LUMIRA_UPLOAD_STORAGE_ROOT
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
  - `UPLOAD_STORAGE_ROOT=LUMIRA_UPLOAD_STORAGE_ROOT|UPLOAD_STORAGE_ROOT`
- Commands:
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080

### 7. P1 business-e2e -> job-owner

- Batch id: p1-business-e2e-job-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: BASE_URL, DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE, DEPLOY_CHECK_BASE_URL, LUMIRA_BASE_URL, LUMIRA_JOB_INTERNAL_TOKEN
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `SAAS_JOB_INTERNAL_TOKEN=DDD_JOB_INTERNAL_TOKEN|LUMIRA_JOB_INTERNAL_TOKEN|SAAS_JOB_INTERNAL_TOKEN`
- Commands:
  - `node bin/ddd-job-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080

### 8. P1 business-e2e -> payment-owner

- Batch id: p1-business-e2e-payment-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: LUMIRA_BASE_URL, PAYMENT_PUBLIC_BASE_URL
- Env check groups:
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `PAYMENT_PUBLIC_BASE_URL=PAYMENT_PUBLIC_BASE_URL`
- Commands:
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
- Exit criteria:
  - File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.
  - Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.

- payment-webhook-artifact: missing payment webhook artifact artifacts\ddd\payment\payment-webhook-e2e.json

### 9. P1 rollback -> ai-owner

- Batch id: p1-rollback-ai-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- AI: AI rollback drill is DEFERRED with approved deferral evidence

### 10. P1 rollback -> auth-owner

- Batch id: p1-rollback-auth-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Auth: Auth rollback drill is DEFERRED with approved deferral evidence

### 11. P1 rollback -> file-owner

- Batch id: p1-rollback-file-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- File: File rollback drill is DEFERRED with approved deferral evidence

### 12. P1 rollback -> iam-owner

- Batch id: p1-rollback-iam-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- IAM: IAM rollback drill is DEFERRED with approved deferral evidence

### 13. P1 rollback -> job-owner

- Batch id: p1-rollback-job-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Job: Job rollback drill is DEFERRED with approved deferral evidence

### 14. P1 rollback -> localization-owner

- Batch id: p1-rollback-localization-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Localization: Localization rollback drill is DEFERRED with approved deferral evidence

### 15. P1 rollback -> message-owner

- Batch id: p1-rollback-message-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Message: Message rollback drill is DEFERRED with approved deferral evidence

### 16. P1 rollback -> payment-owner

- Batch id: p1-rollback-payment-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Payment: Payment rollback drill is DEFERRED with approved deferral evidence

### 17. P1 rollback -> platform-owner

- Batch id: p1-rollback-platform-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Platform: Platform rollback drill is DEFERRED with approved deferral evidence

### 18. P1 rollback -> plugin-owner

- Batch id: p1-rollback-plugin-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_EVIDENCE_ENVIRONMENT, DDD_EVIDENCE_OPERATOR, DDD_RELEASE_CANDIDATE, DDD_ROLLBACK_DRILL_CHECK_ENV, DDD_ROLLBACK_DRILL_DEFERRAL_FILE, DDD_ROLLBACK_DRILL_FILE, DDD_ROLLBACK_DRILL_HANDOFF_FILE, DDD_ROLLBACK_DRILL_STRICT
- Env check groups: 8 groups
  - `DDD_EVIDENCE_ENVIRONMENT=DDD_EVIDENCE_ENVIRONMENT`
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=DDD_ROLLBACK_DRILL_CHECK_ENV`
  - `DDD_ROLLBACK_DRILL_DEFERRAL_FILE=DDD_ROLLBACK_DRILL_DEFERRAL_FILE`
  - `DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE`
  - `DDD_ROLLBACK_DRILL_HANDOFF_FILE=DDD_ROLLBACK_DRILL_HANDOFF_FILE`
  - `DDD_ROLLBACK_DRILL_STRICT=DDD_ROLLBACK_DRILL_STRICT`
- Commands:
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/rollback/rollback-drill.json`
- Exit criteria:
  - Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.

- Plugin: Plugin rollback drill is DEFERRED with approved deferral evidence

### 19. P2 explain -> database

- Batch id: p2-explain-database
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner
- Can run immediately: false
- Pending items: 2
- Env keys: 12 keys
  - DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE, DDD_EXPLAIN_DIR, DDD_EXPLAIN_ENVIRONMENT
  - DDD_EXPLAIN_STRICT, DDD_RELEASE_CANDIDATE, MYSQL_CLI, MYSQL_DATABASE
  - MYSQL_HOST, MYSQL_PASSWORD, MYSQL_PORT, MYSQL_USER
- Env check groups: 12 groups
  - `DDD_EVIDENCE_OPERATOR=DDD_EVIDENCE_OPERATOR`
  - `DDD_EXPLAIN_DATABASE=DDD_EXPLAIN_DATABASE`
  - `DDD_EXPLAIN_DIR=DDD_EXPLAIN_DIR`
  - `DDD_EXPLAIN_ENVIRONMENT=DDD_EXPLAIN_ENVIRONMENT`
  - `DDD_EXPLAIN_STRICT=DDD_EXPLAIN_STRICT`
  - `DDD_RELEASE_CANDIDATE=DDD_RELEASE_CANDIDATE`
  - `MYSQL_CLI=MYSQL_CLI`
  - `MYSQL_DATABASE=MYSQL_DATABASE`
  - `MYSQL_HOST=MYSQL_HOST`
  - `DB_PASSWORD=DB_PASSWORD|MYSQL_PASSWORD|SPRING_DATASOURCE_PASSWORD`
  - `MYSQL_PORT=MYSQL_PORT`
  - `DB_USERNAME=DB_USERNAME|MYSQL_USER|SPRING_DATASOURCE_USERNAME`
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
- Exit criteria:
  - Production-equivalent MySQL EXPLAIN artifacts are freshly collected for every required hot path.
  - Strict explain gate has no full scans, legacy imports, missing indexes, or contract issues.

- message-archive-total.json: [plan] message-archive-total.json: archive_candidates does not report an index key for access_type=ALL | [plan] message-archive-total.json: archive_candidates uses full scan access_type=ALL
- message-unread-count.json: [plan] message-unread-count.json: unread_candidates does not report an index key for access_type=ALL | [plan] message-unread-count.json: unread_candidates uses full scan access_type=ALL

### 20. P3 orchestrator -> database

- Batch id: p3-orchestrator-database
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_VALIDATED
- Env check groups:
  - `DDD_MIGRATION_FRESH_DB_EVIDENCE=DDD_MIGRATION_FRESH_DB_EVIDENCE`
  - `DDD_MIGRATION_FRESH_DB_VALIDATED=DDD_MIGRATION_FRESH_DB_VALIDATED`
  - `DDD_MIGRATION_UPGRADE_DB_EVIDENCE=DDD_MIGRATION_UPGRADE_DB_EVIDENCE`
  - `DDD_MIGRATION_UPGRADE_DB_VALIDATED=DDD_MIGRATION_UPGRADE_DB_VALIDATED`
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-preflight-migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE

### 21. P3 orchestrator -> release-infra

- Batch id: p3-orchestrator-release-infra
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 2
- Env keys: BASE_URL, DEPLOY_CHECK_BASE_URL, FRONTEND_BASE_URL, LUMIRA_BASE_URL, PLAYWRIGHT_BASE_URL
- Env check groups:
  - `BASE_URL=BASE_URL`
  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`
  - `PLAYWRIGHT_BASE_URL=FRONTEND_BASE_URL|PLAYWRIGHT_BASE_URL`
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-preflight-backend-runtime-base-url: missing backend runtime base URL
- orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL

### 22. P3 orchestrator -> release-owner

- Batch id: p3-orchestrator-release-owner
- Depends on: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-manifest-lumira-ui, p0-authenticated-performance-release-performance, p1-ai-runtime-ai, p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-ai-owner, p1-rollback-auth-owner, p1-rollback-file-owner, p1-rollback-iam-owner, p1-rollback-job-owner, p1-rollback-localization-owner, p1-rollback-message-owner, p1-rollback-payment-owner, p1-rollback-platform-owner, p1-rollback-plugin-owner, p2-explain-database
- Can run immediately: false
- Pending items: 1
- Env keys: DDD_RELEASE_EVIDENCE_STRICT
- Env check groups:
  - `DDD_RELEASE_EVIDENCE_STRICT=DDD_RELEASE_EVIDENCE_STRICT`
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Exit criteria:
  - Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.
  - Final strict release gate and readiness summary report zero blockers.
  - Run only after all prerequisite evidence batches are clean.

- orchestrator-run-mode: strict release requires run mode report, got plan

