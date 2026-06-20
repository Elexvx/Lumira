# DDD Release Blocker Map

Generated at: 2026-06-20T19:42:26.704Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 148
Category count: 17
Owner count: 10
Total blockers: 148

## Owners

### release-owner

- Blockers: 58
- Categories: manifest=3, manifest-provenance=4, orchestrator=16, other=35
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [other] physical-split-readiness: IAM module must be services/lumira-system, got services/system-service
  - [other] physical-split-readiness: Auth module must be services/lumira-auth, got services/auth-service
  - [other] physical-split-readiness: Platform module must be services/lumira-system, got services/system-service
  - [other] physical-split-readiness: Message module must be services/lumira-message, got services/message-service
  - [other] physical-split-readiness: File module must be services/lumira-file, got services/file-service

### database

- Blockers: 22
- Categories: explain-plan=4, migration=18
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [migration] migration-evidence-freshness: generatedAt is 69.4h old; limit=24h
  - [migration] migration-evidence: unknown migration location services/system-service/src/main/resources/db/migration
  - [migration] migration-evidence: unknown migration location services/auth-service/src/main/resources/db/migration/auth
  - [migration] migration-evidence: unknown migration location services/message-service/src/main/resources/db/migration/message
  - [migration] migration-evidence: unknown migration location services/file-service/src/main/resources/db/migration/file

### release-infra

- Blockers: 21
- Categories: configuration=2, docker=4, docker-provenance=3, production-equivalent-runtime=11, runtime-freshness=1
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
  - [runtime-freshness] runtime-readiness-freshness: checkedAt is 84h old; limit=24h

### release-performance

- Blockers: 13
- Categories: performance-baseline=7, performance-freshness=1, production-equivalent-runtime=5
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Sample blockers:
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.deploymentEvidence is required
  - [performance-freshness] authenticated-performance-freshness: checkedAt is 69.5h old; limit=24h

### ai

- Blockers: 10
- Categories: ai-runtime=10
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Sample blockers:
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.strict must be true for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.https must be true for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.localOnly must be false for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.deploymentEvidence is required
  - [ai-runtime] ai-runtime-freshness: checkedAt is 69.5h old; limit=24h

### file-owner

- Blockers: 7
- Categories: business-e2e-freshness=1, production-equivalent-runtime=6
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Commands:
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] file-processing-freshness: finishedAt is 143.5h old; limit=24h
  - [production-equivalent-runtime] file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence
  - [production-equivalent-runtime] file-processing-production-equivalence: strict file processing E2E deploymentEvidence is required
  - [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.localOnly must be false for strict release evidence

### job-owner

- Blockers: 7
- Categories: business-e2e-freshness=1, production-equivalent-runtime=6
- Ready batches: none
- Blocked batches: p1-business-e2e-job-owner, p1-rollback-job-owner
- Commands:
  - `node bin/ddd-job-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] job-e2e-freshness: checkedAt is 143.1h old; limit=24h
  - [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence
  - [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E deploymentEvidence is required
  - [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.localOnly must be false for strict release evidence

### payment-owner

- Blockers: 7
- Categories: business-e2e-freshness=1, production-equivalent-runtime=6
- Ready batches: none
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Commands:
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] payment-webhook-freshness: finishedAt is 143.3h old; limit=24h
  - [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E requires HTTPS baseUrl evidence
  - [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E deploymentEvidence is required
  - [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.localOnly must be false for strict release evidence

### lumira-ui

- Blockers: 2
- Categories: frontend-smoke=2
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
- Sample blockers:
  - [frontend-smoke] release-evidence-manifest: missing artifact lumira-ui/frontend-smoke.json
  - [frontend-smoke] frontend-smoke-strict: strict release requires deployed frontend smoke evidence

### platform-events

- Blockers: 1
- Categories: outbox-state-machine=1
- Ready batches: none
- Blocked batches: none
- Sample blockers:
  - [outbox-state-machine] outbox-replay-dead-letter-freshness: generatedAt is 103.4h old; limit=24h

## Categories

### other

- Blockers: 35
- Owners: release-owner=35
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] physical-split-readiness: IAM module must be services/lumira-system, got services/system-service
  - [release-owner] physical-split-readiness: Auth module must be services/lumira-auth, got services/auth-service
  - [release-owner] physical-split-readiness: Platform module must be services/lumira-system, got services/system-service
  - [release-owner] physical-split-readiness: Message module must be services/lumira-message, got services/message-service
  - [release-owner] physical-split-readiness: File module must be services/lumira-file, got services/file-service

### production-equivalent-runtime

- Blockers: 34
- Owners: file-owner=6, job-owner=6, payment-owner=6, release-infra=11, release-performance=5
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-authenticated-performance-release-performance
- Blocked batches: p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-file-owner, p1-rollback-job-owner, p1-rollback-payment-owner, p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
  - `node bin/ddd-job-e2e-smoke.mjs`
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
  - [release-infra] runtime-readiness-production-equivalence: strict runtime readiness deploymentEvidence is required

### migration

- Blockers: 18
- Owners: database=18
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [database] migration-evidence-freshness: generatedAt is 69.4h old; limit=24h
  - [database] migration-evidence: unknown migration location services/system-service/src/main/resources/db/migration
  - [database] migration-evidence: unknown migration location services/auth-service/src/main/resources/db/migration/auth
  - [database] migration-evidence: unknown migration location services/message-service/src/main/resources/db/migration/message
  - [database] migration-evidence: unknown migration location services/file-service/src/main/resources/db/migration/file

### orchestrator

- Blockers: 16
- Owners: release-owner=16
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-orchestrator-freshness: generatedAt is 68.8h old; limit=24h
  - [release-owner] release-evidence-orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL
  - [release-owner] release-evidence-orchestrator: strict release requires run mode report, got plan
  - [release-owner] release-evidence-orchestrator: unexpected orchestrator preflight check frontend-runtime-base-url
  - [release-owner] release-evidence-orchestrator: unexpected orchestrator preflight check frontend-deployed-expectation

### ai-runtime

- Blockers: 10
- Owners: ai=10
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Commands:
  - `node bin/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Sample blockers:
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.strict must be true for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.https must be true for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.localOnly must be false for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.deploymentEvidence is required
  - [ai] ai-runtime-freshness: checkedAt is 69.5h old; limit=24h

### performance-baseline

- Blockers: 7
- Owners: release-performance=7
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Sample blockers:
  - [release-performance] authenticated-performance-baseline-environment: strict release requires a non-local baseline baseUrl, got http://127.0.0.1:8080
  - [release-performance] authenticated-performance-baseline-metadata: strict release baseline requires baselineType=authenticated-runtime
  - [release-performance] authenticated-performance-baseline-metadata: acceptedAt must be an ISO timestamp
  - [release-performance] authenticated-performance-baseline-metadata: acceptedBy is required
  - [release-performance] authenticated-performance-baseline-metadata: sourceArtifact is required

### docker

- Blockers: 4
- Owners: release-infra=4
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-infra] docker-build-evidence: lumira-server: docker build failed: #5 DONE 0.3s

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
  - [release-infra] docker-build-evidence: frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
  - [release-infra] docker-build-evidence: missing image report lumira-ui
  - [release-infra] docker-build-evidence: unknown image report frontend

### explain-plan

- Blockers: 4
- Owners: database=4
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node bin/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [database] explain-evidence: message-archive-total.json: archive_candidates uses full scan access_type=ALL
  - [database] explain-evidence: message-archive-total.json: archive_candidates does not report an index key for access_type=ALL
  - [database] explain-evidence: message-unread-count.json: unread_candidates uses full scan access_type=ALL
  - [database] explain-evidence: message-unread-count.json: unread_candidates does not report an index key for access_type=ALL

### manifest-provenance

- Blockers: 4
- Owners: release-owner=4
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-manifest-provenance: manifest provenance sourceEnvironment is required
  - [release-owner] release-evidence-manifest-provenance: manifest provenance releaseCandidate is required
  - [release-owner] release-evidence-manifest-provenance: manifest provenance evidenceOperator is required
  - [release-owner] release-evidence-manifest: artifact provenance issues=1

### business-e2e-freshness

- Blockers: 3
- Owners: file-owner=1, job-owner=1, payment-owner=1
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-file-owner, p1-rollback-job-owner, p1-rollback-payment-owner
- Commands:
  - `node bin/ddd-file-processing-e2e-smoke.mjs`
  - `node bin/ddd-job-e2e-smoke.mjs`
  - `node bin/ddd-payment-webhook-e2e-smoke.mjs`
  - `node bin/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs`
  - `node bin/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [file-owner] file-processing-freshness: finishedAt is 143.5h old; limit=24h
  - [payment-owner] payment-webhook-freshness: finishedAt is 143.3h old; limit=24h
  - [job-owner] job-e2e-freshness: checkedAt is 143.1h old; limit=24h

### docker-provenance

- Blockers: 3
- Owners: release-infra=3
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-infra] docker-build-evidence-provenance: sourceEnvironment is required
  - [release-infra] docker-build-evidence-provenance: releaseCandidate is required
  - [release-infra] docker-build-evidence-provenance: evidenceOperator is required

### manifest

- Blockers: 3
- Owners: release-owner=3
- Ready batches: none
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-manifest: missing artifact lumira-ui/lumira-ui-build-evidence.json
  - [release-owner] release-evidence-manifest: missing artifact lumira-ui/lumira-ui-static-evidence.json
  - [release-owner] release-evidence-manifest: manifest blockers length mismatch: declared=3, actual=9

### configuration

- Blockers: 2
- Owners: release-infra=2
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-infra] release-config-evidence: release config coverageMatrix missing runtime.lumira-ui base url
  - [release-infra] release-config-evidence: release config coverageMatrix unknown runtime.frontend base url

### frontend-smoke

- Blockers: 2
- Owners: lumira-ui=2
- Ready batches: p0-manifest-lumira-ui
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node bin/ddd-release-evidence-manifest.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/lumira-ui/frontend-smoke.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`
  - `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`
- Sample blockers:
  - [lumira-ui] release-evidence-manifest: missing artifact lumira-ui/frontend-smoke.json
  - [lumira-ui] frontend-smoke-strict: strict release requires deployed frontend smoke evidence

### outbox-state-machine

- Blockers: 1
- Owners: platform-events=1
- Ready batches: none
- Blocked batches: none
- Sample blockers:
  - [platform-events] outbox-replay-dead-letter-freshness: generatedAt is 103.4h old; limit=24h

### performance-freshness

- Blockers: 1
- Owners: release-performance=1
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node bin/ddd-authenticated-performance-smoke.mjs`
  - `node bin/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Sample blockers:
  - [release-performance] authenticated-performance-freshness: checkedAt is 69.5h old; limit=24h

### runtime-freshness

- Blockers: 1
- Owners: release-infra=1
- Ready batches: p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: p3-orchestrator-release-infra
- Commands:
  - `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-docker-build-evidence.mjs`
  - `node bin/ddd-runtime-readiness-smoke.mjs`
  - `node bin/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-infra] runtime-readiness-freshness: checkedAt is 84h old; limit=24h

