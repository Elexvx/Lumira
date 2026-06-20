# DDD Release Closure Wave Blocker Map

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Recommendation: NO_GO_STRICT
No auto waivers: true
Waves: 4
Mapped actions: 20
Candidate blocker hints: 15
Non-artifact blocker hints: 0

Candidate blockers are traceability hints only. The strict release evidence gate remains authoritative.

## Wave 1. release-infra / p0-docker-release-infra

- Priority: P0
- Sources: docker
- Category hints: docker
- Item ids: docker-blocker-1, docker-blocker-2, docker-image-frontend-failed, docker-image-lumira-server-failed
- Candidate blocker hints: 4
- Candidate blockers:
  - [docker] docker-build-evidence: lumira-server: docker build failed: #5 DONE 0.3s

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
  - [docker] docker-build-evidence: frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
  - [docker] docker-build-evidence: missing image report lumira-ui
  - [docker] docker-build-evidence: unknown image report frontend
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 2. release-infra / p0-runtime-readiness-release-infra

- Priority: P0
- Sources: runtime-readiness
- Category hints: production-equivalent-runtime
- Item ids: runtime-readiness-contract-1, runtime-readiness-contract-2, runtime-readiness-contract-3, runtime-readiness-contract-4
- Candidate blocker hints: 11
- Candidate blockers:
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
  - [production-equivalent-runtime] runtime-readiness-production-equivalence: strict runtime readiness deploymentEvidence is required
  - [production-equivalent-runtime] runtime-readiness-environment-strict: strict release requires production-equivalent non-local evidence
  - [production-equivalent-runtime] authenticated-performance-environment-strict: strict release requires production-equivalent non-local evidence
  - [production-equivalent-runtime] file-processing-environment-strict: strict release requires production-equivalent non-local evidence
  - [production-equivalent-runtime] payment-webhook-environment-strict: strict release requires production-equivalent non-local evidence
  - [production-equivalent-runtime] job-e2e-environment-strict: strict release requires production-equivalent non-local evidence
  - [production-equivalent-runtime] release-evidence-orchestrator-preflight-backend-runtime-base-url: missing backend runtime base URL
- Commands:
  - `node bin/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/readiness/summary.json`
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 3. lumira-ui / p0-manifest-lumira-ui

- Priority: P0
- Sources: manifest
- Category hints: manifest
- Item ids: manifest-missing-lumira-ui-frontend-smoke-json, manifest-missing-lumira-ui-lumira-ui-build-evidence-json, manifest-missing-lumira-ui-lumira-ui-static-evidence-json
- Candidate blocker hints: 0
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
  - `artifacts/ddd/release/evidence-manifest.json`
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

## Wave 4. release-performance / p0-authenticated-performance-release-performance

- Priority: P0
- Sources: authenticated-performance
- Category hints: authenticated-performance
- Item ids: performance-actual-shape-1, performance-actual-shape-2, performance-actual-shape-3, performance-actual-shape-4, performance-baseline-metadata-5, performance-baseline-metadata-6, performance-baseline-metadata-7, performance-baseline-metadata-8, performance-baseline-metadata-9
- Candidate blocker hints: 0
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
- Rerun commands:
  - `node bin/ddd-release-evidence-gate.mjs`
  - `node bin/ddd-release-readiness-summary.mjs`

