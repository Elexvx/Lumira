# DDD Release Blocker Map

Generated at: 2026-06-19T06:54:03.604Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 94
Category count: 17
Owner count: 10
Total blockers: 94

## Owners

### release-infra

- Blockers: 21
- Categories: configuration=6, docker=3, production-equivalent-runtime=11, runtime-freshness=1
- Ready batches: p0-release-env-lint-release-infra, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
- Sample blockers:
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
  - [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
  - [runtime-freshness] runtime-readiness-freshness: checkedAt is 72.3h old; limit=24h

### release-owner

- Blockers: 13
- Categories: manifest=2, manifest-provenance=3, orchestrator=2, other=5, rollback-drill=1
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [other] physical-split-readiness-freshness: generatedAt is 57.7h old; limit=24h
  - [other] backend-test-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [other] backend-build-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [orchestrator] release-evidence-orchestrator-freshness: generatedAt is 57.1h old; limit=24h
  - [orchestrator] release-evidence-orchestrator: strict release requires run mode report, got plan

### release-performance

- Blockers: 13
- Categories: performance-baseline=7, performance-freshness=1, production-equivalent-runtime=5
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Sample blockers:
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
  - [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.deploymentEvidence is required
  - [performance-freshness] authenticated-performance-freshness: checkedAt is 57.8h old; limit=24h

### frontend

- Blockers: 12
- Categories: frontend-smoke=12
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Commands:
  - `node scripts/ddd-frontend-playwright-smoke.mjs`
  - `node scripts/ddd-frontend-smoke-evidence.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/frontend/frontend-smoke.json`
  - `artifacts/ddd/frontend/playwright-smoke-results.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [frontend-smoke] release-evidence-orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL
  - [frontend-smoke] frontend-smoke-freshness: generatedAt is 57.8h old; limit=24h
  - [frontend-smoke] frontend-smoke-production-equivalence: strict frontend smoke deploymentEvidence is required
  - [frontend-smoke] frontend-smoke-environment-strict: strict release requires production-equivalent non-local evidence
  - [frontend-smoke] frontend-smoke-environment: frontend smoke productionEquivalence.strict must be true for strict release evidence

### ai

- Blockers: 10
- Categories: ai-runtime=10
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Sample blockers:
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.strict must be true for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.https must be true for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.localOnly must be false for strict release evidence
  - [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.deploymentEvidence is required
  - [ai-runtime] ai-runtime-freshness: checkedAt is 57.8h old; limit=24h

### file-owner

- Blockers: 7
- Categories: business-e2e-freshness=1, production-equivalent-runtime=6
- Ready batches: none
- Blocked batches: p1-business-e2e-file-owner, p1-rollback-file-owner
- Commands:
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] file-processing-freshness: finishedAt is 131.7h old; limit=24h
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
  - `node scripts/ddd-job-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] job-e2e-freshness: checkedAt is 131.4h old; limit=24h
  - [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence
  - [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E deploymentEvidence is required
  - [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.localOnly must be false for strict release evidence

### payment-owner

- Blockers: 7
- Categories: business-e2e-freshness=1, production-equivalent-runtime=6
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-payment-owner, p1-rollback-payment-owner
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [business-e2e-freshness] payment-webhook-freshness: finishedAt is 131.6h old; limit=24h
  - [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E requires HTTPS baseUrl evidence
  - [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E deploymentEvidence is required
  - [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.https must be true for strict release evidence
  - [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.localOnly must be false for strict release evidence

### database

- Blockers: 3
- Categories: explain-plan=1, migration=2
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [migration] migration-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [migration] release-evidence-orchestrator-preflight-migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE
  - [explain-plan] explain-evidence-strict: strict release requires production-scale EXPLAIN artifacts

### platform-events

- Blockers: 1
- Categories: outbox-state-machine=1
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Sample blockers:
  - [outbox-state-machine] outbox-replay-dead-letter-freshness: generatedAt is 91.7h old; limit=24h

## Categories

### production-equivalent-runtime

- Blockers: 34
- Owners: file-owner=6, job-owner=6, payment-owner=6, release-infra=11, release-performance=5
- Ready batches: p0-release-env-lint-release-infra, p0-release-config-payment-owner, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra, p0-authenticated-performance-release-performance
- Blocked batches: p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-file-owner, p1-rollback-job-owner, p1-rollback-payment-owner
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
  - `node scripts/ddd-job-e2e-smoke.mjs`
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
  - [release-infra] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
  - [release-infra] runtime-readiness-production-equivalence: strict runtime readiness deploymentEvidence is required

### frontend-smoke

- Blockers: 12
- Owners: frontend=12
- Ready batches: none
- Blocked batches: p1-frontend-smoke-frontend, p3-orchestrator-frontend
- Commands:
  - `node scripts/ddd-frontend-playwright-smoke.mjs`
  - `node scripts/ddd-frontend-smoke-evidence.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/frontend/frontend-smoke.json`
  - `artifacts/ddd/frontend/playwright-smoke-results.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [frontend] release-evidence-orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL
  - [frontend] frontend-smoke-freshness: generatedAt is 57.8h old; limit=24h
  - [frontend] frontend-smoke-production-equivalence: strict frontend smoke deploymentEvidence is required
  - [frontend] frontend-smoke-environment-strict: strict release requires production-equivalent non-local evidence
  - [frontend] frontend-smoke-environment: frontend smoke productionEquivalence.strict must be true for strict release evidence

### ai-runtime

- Blockers: 10
- Owners: ai=10
- Ready batches: none
- Blocked batches: p1-ai-runtime-ai
- Commands:
  - `node scripts/ddd-ai-runtime-drill.mjs`
- Expected artifacts:
  - `artifacts/ddd/ai/ai-runtime-drill.json`
- Sample blockers:
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.strict must be true for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.https must be true for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.localOnly must be false for strict release evidence
  - [ai] ai-runtime-drill: AI runtime productionEquivalence.deploymentEvidence is required
  - [ai] ai-runtime-freshness: checkedAt is 57.8h old; limit=24h

### performance-baseline

- Blockers: 7
- Owners: release-performance=7
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
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

### configuration

- Blockers: 6
- Owners: release-infra=6
- Ready batches: p0-release-env-lint-release-infra, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
- Sample blockers:
  - [release-infra] release-env-lint-freshness: generatedAt is 48.9h old; limit=24h
  - [release-infra] release-env-lint: status=FAIL, blockers=156
  - [release-infra] release-env-lint-placeholders: unresolvedTemplateKeys=93
  - [release-infra] release-env-lint-config: releaseConfigBlockers=63
  - [release-infra] release-config-evidence-freshness: generatedAt is 48.2h old; limit=24h

### other

- Blockers: 5
- Owners: release-owner=5
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] physical-split-readiness-freshness: generatedAt is 57.7h old; limit=24h
  - [release-owner] backend-test-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [release-owner] backend-build-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [release-owner] frontend-build-evidence-freshness: generatedAt is 91.8h old; limit=24h
  - [release-owner] frontend-static-evidence-freshness: generatedAt is 91.8h old; limit=24h

### business-e2e-freshness

- Blockers: 3
- Owners: file-owner=1, job-owner=1, payment-owner=1
- Ready batches: p0-release-config-payment-owner
- Blocked batches: p1-business-e2e-file-owner, p1-business-e2e-job-owner, p1-business-e2e-payment-owner, p1-rollback-file-owner, p1-rollback-job-owner, p1-rollback-payment-owner
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
  - `node scripts/ddd-file-processing-e2e-smoke.mjs`
  - `node scripts/ddd-job-e2e-smoke.mjs`
  - `node scripts/ddd-payment-webhook-e2e-smoke.mjs`
  - `node scripts/ddd-rollback-deferral-template.mjs`
  - `DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs`
  - `node scripts/ddd-rollback-drill-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/file/file-processing-e2e.json`
  - `artifacts/ddd/jobs/job-e2e-smoke.json`
  - `artifacts/ddd/payment/payment-webhook-e2e.json`
  - `artifacts/ddd/rollback/rollback-drill.json`
- Sample blockers:
  - [file-owner] file-processing-freshness: finishedAt is 131.7h old; limit=24h
  - [payment-owner] payment-webhook-freshness: finishedAt is 131.6h old; limit=24h
  - [job-owner] job-e2e-freshness: checkedAt is 131.4h old; limit=24h

### docker

- Blockers: 3
- Owners: release-infra=3
- Ready batches: p0-release-env-lint-release-infra, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
- Sample blockers:
  - [release-infra] docker-build-evidence-freshness: generatedAt is 56.9h old; limit=24h
  - [release-infra] docker-build-evidence: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
  - [release-infra] docker-build-evidence: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1

### manifest-provenance

- Blockers: 3
- Owners: release-owner=3
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-manifest-provenance: manifest provenance sourceEnvironment is required
  - [release-owner] release-evidence-manifest-provenance: manifest provenance releaseCandidate is required
  - [release-owner] release-evidence-manifest-provenance: manifest provenance evidenceOperator is required

### manifest

- Blockers: 2
- Owners: release-owner=2
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-manifest: manifest blockers length mismatch: declared=1, actual=4
  - [release-owner] release-evidence-manifest: missing EXPLAIN files in evidence manifest

### migration

- Blockers: 2
- Owners: database=2
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [database] migration-evidence-freshness: generatedAt is 57.7h old; limit=24h
  - [database] release-evidence-orchestrator-preflight-migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE

### orchestrator

- Blockers: 2
- Owners: release-owner=2
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] release-evidence-orchestrator-freshness: generatedAt is 57.1h old; limit=24h
  - [release-owner] release-evidence-orchestrator: strict release requires run mode report, got plan

### explain-plan

- Blockers: 1
- Owners: database=1
- Ready batches: none
- Blocked batches: p2-explain-database, p3-orchestrator-database
- Commands:
  - `node scripts/ddd-collect-explain.mjs`
  - `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `tmp/ddd-explain/*.json`
  - `artifacts/ddd/release/explain-gate-report.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [database] explain-evidence-strict: strict release requires production-scale EXPLAIN artifacts

### outbox-state-machine

- Blockers: 1
- Owners: platform-events=1
- Ready batches: p0-release-config-platform-events
- Blocked batches: none
- Commands:
  - `node scripts/ddd-release-config-evidence.mjs`
- Expected artifacts:
  - `artifacts/ddd/config/release-config-evidence.json`
- Sample blockers:
  - [platform-events] outbox-replay-dead-letter-freshness: generatedAt is 91.7h old; limit=24h

### performance-freshness

- Blockers: 1
- Owners: release-performance=1
- Ready batches: p0-authenticated-performance-release-performance
- Blocked batches: none
- Commands:
  - `node scripts/ddd-authenticated-performance-smoke.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
- Expected artifacts:
  - `artifacts/ddd/performance/authenticated-runtime-actual.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline.json`
  - `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`
- Sample blockers:
  - [release-performance] authenticated-performance-freshness: checkedAt is 57.8h old; limit=24h

### rollback-drill

- Blockers: 1
- Owners: release-owner=1
- Ready batches: p0-manifest-release-owner
- Blocked batches: p3-orchestrator-release-owner
- Commands:
  - `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-promote-performance-baseline.mjs`
  - `DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs`
  - `node scripts/ddd-release-evidence-orchestrator.mjs`
  - `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict`
- Expected artifacts:
  - `artifacts/ddd/release/evidence-manifest.json`
  - `artifacts/ddd/release/orchestrator-report.json`
  - `artifacts/ddd/release/release-evidence-gate.json`
  - `artifacts/ddd/release/readiness-summary.json`
- Sample blockers:
  - [release-owner] rollback-drill-freshness: generatedAt is 72.2h old; limit=24h

### runtime-freshness

- Blockers: 1
- Owners: release-infra=1
- Ready batches: p0-release-env-lint-release-infra, p0-release-config-release-infra, p0-docker-release-infra, p0-runtime-readiness-release-infra
- Blocked batches: none
- Commands:
  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`
  - `node scripts/ddd-release-config-evidence.mjs`
  - `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-docker-build-evidence.mjs`
  - `node scripts/ddd-runtime-readiness-smoke.mjs`
- Expected artifacts:
  - `artifacts/ddd/release/release-env-lint.json`
  - `artifacts/ddd/config/release-config-evidence.json`
  - `artifacts/ddd/build/docker-image-evidence.json`
  - `artifacts/ddd/readiness/summary.json`
- Sample blockers:
  - [release-infra] runtime-readiness-freshness: checkedAt is 72.3h old; limit=24h

