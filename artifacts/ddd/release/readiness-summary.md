# DDD Release Readiness Summary

Generated at: 2026-06-19T18:09:18.921Z
Status: NOT_READY
Release gate mode: strict
Release gate blockers: 94
Release gate warnings: 8

## Missing Manifest Artifacts

- Manifest status: FAIL
- Manifest optional artifacts: 11
- Optional owner queue run report: not present
- Owner queue env init receipt: PRESENT; permissionSafe=true; mode=600; unresolvedTemplateKeys=110
- missing artifact lumira-ui/lumira-ui-build-evidence.json
- missing artifact lumira-ui/lumira-ui-static-evidence.json
- missing artifact lumira-ui/frontend-smoke.json
- optional artifact release/release-unblock-brief.json: finalOwnerQueueFastPath.commands must include readiness summary refresh
- optional artifact release/release-unblock-brief.json: handoffReferences migration-evidence-handoff command must be DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs
- optional artifact release/release-unblock-brief.json: handoffReferences rollback-deferral-owner-handoff command must be node bin/ddd-rollback-deferral-template.mjs
- optional artifact release/release-unblock-brief.json: handoffReferences release-env-owner-input-packet command must be node bin/ddd-release-env-owner-input-packet-contract.mjs
- optional artifact release/release-unblock-brief.json: handoffReferences release-owner-input-receipt command must be node bin/ddd-release-owner-input-receipt-contract.mjs
- optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for migration-evidence-handoff
- optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for rollback-deferral-owner-handoff
- optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-env-owner-input-packet
- optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-owner-input-receipt
- actionPlan: owner=database pendingItems=2 envKeys=DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_VALIDATED
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-migration-evidence-handoff-command-must-be-ddd-migration-check-env-true-node-bin-ddd-migration-evidence-mjs; owner=database; reason=optional artifact release/release-unblock-brief.json: handoffReferences migration-evidence-handoff command must be DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; artifact=optional artifact release/release-unblock-brief.json: handoffReferences migration-evidence-handoff command must be DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs; action=Run fresh/upgrade migration drills and regenerate migration evidence before rebuilding the release manifest.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-migration-evidence-handoff; owner=database; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for migration-evidence-handoff; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; artifact=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for migration-evidence-handoff; action=Run fresh/upgrade migration drills and regenerate migration evidence before rebuilding the release manifest.
- actionPlan: owner=lumira-ui pendingItems=3 envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_RELEASE_CANDIDATE,PLAYWRIGHT_BASE_URL
- manifestAction: manifest-missing-lumira-ui-frontend-smoke-json; owner=lumira-ui; reason=missing artifact lumira-ui/frontend-smoke.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; artifact=lumira-ui/frontend-smoke.json; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- manifestAction: manifest-missing-lumira-ui-lumira-ui-build-evidence-json; owner=lumira-ui; reason=missing artifact lumira-ui/lumira-ui-build-evidence.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; artifact=lumira-ui/lumira-ui-build-evidence.json; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- manifestAction: manifest-missing-lumira-ui-lumira-ui-static-evidence-json; owner=lumira-ui; reason=missing artifact lumira-ui/lumira-ui-static-evidence.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; artifact=lumira-ui/lumira-ui-static-evidence.json; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- actionPlan: owner=release-owner pendingItems=7 envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_RELEASE_MANIFEST_STRICT
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-finalownerqueuefastpath-commands-must-include-readiness-summary-refresh; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: finalOwnerQueueFastPath.commands must include readiness summary refresh; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: finalOwnerQueueFastPath.commands must include readiness summary refresh; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-env-owner-input-packet-command-must-be-node-bin-ddd-release-env-owner-input-packet-contract-mjs; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: handoffReferences release-env-owner-input-packet command must be node bin/ddd-release-env-owner-input-packet-contract.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: handoffReferences release-env-owner-input-packet command must be node bin/ddd-release-env-owner-input-packet-contract.mjs; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-owner-input-receipt-command-must-be-node-bin-ddd-release-owner-input-receipt-contract-mjs; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: handoffReferences release-owner-input-receipt command must be node bin/ddd-release-owner-input-receipt-contract.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: handoffReferences release-owner-input-receipt command must be node bin/ddd-release-owner-input-receipt-contract.mjs; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-rollback-deferral-owner-handoff-command-must-be-node-bin-ddd-rollback-deferral-template-mjs; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: handoffReferences rollback-deferral-owner-handoff command must be node bin/ddd-rollback-deferral-template.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: handoffReferences rollback-deferral-owner-handoff command must be node bin/ddd-rollback-deferral-template.mjs; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-env-owner-input-packet; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-env-owner-input-packet; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-env-owner-input-packet; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-owner-input-receipt; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-owner-input-receipt; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-owner-input-receipt; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
- manifestAction: manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-rollback-deferral-owner-handoff; owner=release-owner; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for rollback-deferral-owner-handoff; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; artifact=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for rollback-deferral-owner-handoff; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.

## Owner Action Rollup

- owner=ai pendingItems=3 collapsedItems=1 sources=ai-runtime=3 collapsedSources=orchestrator=1 envKeys=BASE_URL,DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE,DDD_AI_EXPECT_PROVIDER_REMOTE,DEPLOY_CHECK_BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_AI_OWNER_FILE_BASE_URL,LUMIRA_AI_OWNER_IAM_BASE_URL,LUMIRA_AI_OWNER_PLATFORM_BASE_URL,LUMIRA_AI_PROVIDER,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL,LUMIRA_BASE_URL
  - ownerAction: source=ai-runtime; id=ai-owner-gateway; reason=ownerGateway status=CONFIGURED configuredOwners=0; envKeys=DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE,LUMIRA_AI_OWNER_IAM_BASE_URL,LUMIRA_AI_OWNER_FILE_BASE_URL,LUMIRA_AI_OWNER_PLATFORM_BASE_URL; action=Configure and verify remote AI owner gateways for IAM/File/Platform integrations.
  - ownerAction: source=ai-runtime; id=ai-provider-runtime; reason=provider status=CONFIGURED remoteConfigured=false; envKeys=DDD_AI_EXPECT_PROVIDER_REMOTE,LUMIRA_AI_PROVIDER,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY; action=Configure and verify a remote AI provider runtime; strict release must not rely on local fallback.
  - ownerAction: source=ai-runtime; id=ai-runtime-base-url; reason=missing production-equivalent AI base URL; envKeys=LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; action=Run AI runtime drill against an HTTPS non-local AI runtime base URL.
  - ownerActionCollapsed: source=orchestrator; id=orchestrator-preflight-ai-runtime-base-url; coveredBy=ai-runtime:ai-runtime-base-url; reason=missing AI runtime base URL; envKeys=LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL
- owner=ai-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=AI; reason=AI rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence. Required evidence: AI provider disablement or fallback configuration evidence; knowledge index job pause/resume command or job output; document index rebuild or retry evidence; degraded chat/search transcript after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=auth-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=Auth; reason=Auth rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence. Required evidence: login smoke result after adapter rollback; session TTL compatibility evidence; forced logout or keepalive behavior evidence; auth readiness/health response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=database pendingItems=5 collapsedItems=0 sources=explain=2,manifest=2,orchestrator=1 collapsedSources=none envKeys=DDD_EVIDENCE_OPERATOR,DDD_EXPLAIN_DATABASE,DDD_EXPLAIN_DIR,DDD_EXPLAIN_ENVIRONMENT,DDD_EXPLAIN_STRICT,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_RELEASE_CANDIDATE,MYSQL_CLI,MYSQL_DATABASE,MYSQL_HOST,MYSQL_PASSWORD,MYSQL_PORT,MYSQL_USER
  - ownerAction: source=explain; id=message-archive-total.json; reason=[plan] message-archive-total.json: archive_candidates does not report an index key for access_type=ALL | [plan] message-archive-total.json: archive_candidates uses full scan access_type=ALL; envKeys=DDD_EXPLAIN_DIR,DDD_EXPLAIN_STRICT,DDD_EXPLAIN_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR,MYSQL_CLI,MYSQL_HOST,MYSQL_PORT,MYSQL_USER,MYSQL_PASSWORD,MYSQL_DATABASE,DDD_EXPLAIN_DATABASE; action=Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
  - ownerAction: source=explain; id=message-unread-count.json; reason=[plan] message-unread-count.json: unread_candidates does not report an index key for access_type=ALL | [plan] message-unread-count.json: unread_candidates uses full scan access_type=ALL; envKeys=DDD_EXPLAIN_DIR,DDD_EXPLAIN_STRICT,DDD_EXPLAIN_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR,MYSQL_CLI,MYSQL_HOST,MYSQL_PORT,MYSQL_USER,MYSQL_PASSWORD,MYSQL_DATABASE,DDD_EXPLAIN_DATABASE; action=Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-migration-evidence-handoff-command-must-be-ddd-migration-check-env-true-node-bin-ddd-migration-evidence-mjs; reason=optional artifact release/release-unblock-brief.json: handoffReferences migration-evidence-handoff command must be DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; action=Run fresh/upgrade migration drills and regenerate migration evidence before rebuilding the release manifest.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-migration-evidence-handoff; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for migration-evidence-handoff; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; action=Run fresh/upgrade migration drills and regenerate migration evidence before rebuilding the release manifest.
  - ownerAction: source=orchestrator; id=orchestrator-preflight-migration-runtime-evidence; reason=missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- owner=file-owner pendingItems=2 collapsedItems=0 sources=business-e2e=1,rollback=1 collapsedSources=none envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN,LUMIRA_UPLOAD_STORAGE_ROOT
  - ownerAction: source=business-e2e; id=file-processing-production-equivalence; reason=strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,LUMIRA_UPLOAD_STORAGE_ROOT,LUMIRA_JOB_INTERNAL_TOKEN; action=Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node bin/ddd-file-processing-e2e-smoke.mjs`.
  - ownerAction: source=rollback; id=File; reason=File rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence. Required evidence: file processing pause/resume command or job output; stable object-key read evidence after rollback; processing task rerun by id with final state; storage artifact or upload row proving access continuity. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=iam-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=IAM; reason=IAM rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence. Required evidence: permission snapshot version before and after rollback; cache invalidation or version bump evidence; IAM v2 readiness/health response after rollback; audit entry or command log for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=job-owner pendingItems=2 collapsedItems=0 sources=business-e2e=1,rollback=1 collapsedSources=none envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN
  - ownerAction: source=business-e2e; id=job-e2e-production-equivalence; reason=strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE; action=Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node bin/ddd-job-e2e-smoke.mjs`.
  - ownerAction: source=rollback; id=Job; reason=Job rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence. Required evidence: XXL-JOB handler disablement or dashboard evidence; manual owner internal endpoint fallback result; internal job token provenance or redacted request evidence; job readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=localization-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=Localization; reason=Localization rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence. Required evidence: localization release id before and after rollback; runtime bundle cache clear evidence; bundle request or metrics proving rolled-back release is served; localization audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=lumira-ui pendingItems=3 collapsedItems=0 sources=manifest=3 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_RELEASE_CANDIDATE,PLAYWRIGHT_BASE_URL
  - ownerAction: source=manifest; id=manifest-missing-lumira-ui-frontend-smoke-json; reason=missing artifact lumira-ui/frontend-smoke.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
  - ownerAction: source=manifest; id=manifest-missing-lumira-ui-lumira-ui-build-evidence-json; reason=missing artifact lumira-ui/lumira-ui-build-evidence.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
  - ownerAction: source=manifest; id=manifest-missing-lumira-ui-lumira-ui-static-evidence-json; reason=missing artifact lumira-ui/lumira-ui-static-evidence.json; envKeys=PLAYWRIGHT_BASE_URL,DDD_FRONTEND_EXPECT_DEPLOYED,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE; action=Run deployed frontend smoke and regenerate lumira-ui evidence before rebuilding the release manifest.
- owner=message-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=Message; reason=Message rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence. Required evidence: message relay pause/resume command or job output; delivery fallback evidence for at least one notice; idempotent replay result with duplicate-safe state; message readiness/metrics response after rollback. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=payment-owner pendingItems=2 collapsedItems=0 sources=business-e2e=1,rollback=1 collapsedSources=none envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,PAYMENT_PUBLIC_BASE_URL
  - ownerAction: source=business-e2e; id=payment-webhook-production-equivalence; reason=strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,PAYMENT_PUBLIC_BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE; action=Regenerate Payment webhook E2E smoke against an HTTPS non-local webhook URL with provider sandbox or deployment evidence using `node bin/ddd-payment-webhook-e2e-smoke.mjs`.
  - ownerAction: source=rollback; id=Payment; reason=Payment rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence. Required evidence: payment provider route fallback configuration evidence; webhook idempotent replay result; order status trace before and after replay; webhook metrics or audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=platform-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=Platform; reason=Platform rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence. Required evidence: runtime appearance/config version before and after rollback; cache clear or version invalidation evidence; bootstrap response using the rolled-back config; platform audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=plugin-owner pendingItems=1 collapsedItems=0 sources=rollback=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_STRICT
  - ownerAction: source=rollback; id=Plugin; reason=Plugin rollback drill is DEFERRED with approved deferral evidence; envKeys=DDD_ROLLBACK_DRILL_FILE,DDD_ROLLBACK_DRILL_CHECK_ENV,DDD_ROLLBACK_DRILL_HANDOFF_FILE,DDD_ROLLBACK_DRILL_DEFERRAL_FILE,DDD_ROLLBACK_DRILL_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence. Required evidence: tenant plugin disable or version rollback command output; bootstrap projection rebuild evidence; tenant plugin projection row before and after rollback; plugin audit entry for the rollback action. If the drill is not safely exercisable, generate a reviewed deferral input with `node bin/ddd-rollback-deferral-template.mjs`, fill real approval evidence, then run `node bin/ddd-rollback-drill-evidence.mjs`.
- owner=release-infra pendingItems=10 collapsedItems=0 sources=docker=4,orchestrator=2,runtime-readiness=4 collapsedSources=none envKeys=BASE_URL,DDD_DOCKER_BUILD_STRICT,DDD_DOCKER_COMMAND,DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DEPLOY_CHECK_BASE_URL,FRONTEND_BASE_URL,LUMIRA_BASE_URL,PLAYWRIGHT_BASE_URL
  - ownerAction: source=docker; id=docker-blocker-1; reason=lumira-server: docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
  - ownerAction: source=docker; id=docker-blocker-2; reason=frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
  - ownerAction: source=docker; id=docker-image-frontend-failed; reason=docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
  - ownerAction: source=docker; id=docker-image-lumira-server-failed; reason=docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
  - ownerAction: source=orchestrator; id=orchestrator-preflight-backend-runtime-base-url; reason=missing backend runtime base URL; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
  - ownerAction: source=orchestrator; id=orchestrator-preflight-frontend-runtime-base-url; reason=missing deployed frontend base URL; envKeys=PLAYWRIGHT_BASE_URL,FRONTEND_BASE_URL; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
  - ownerAction: source=runtime-readiness; id=runtime-readiness-contract-1; reason=runtime readiness productionEquivalence.strict must be true for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
  - ownerAction: source=runtime-readiness; id=runtime-readiness-contract-2; reason=runtime readiness productionEquivalence.https must be true for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
  - ownerAction: source=runtime-readiness; id=runtime-readiness-contract-3; reason=runtime readiness productionEquivalence.localOnly must be false for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
  - ownerAction: source=runtime-readiness; id=runtime-readiness-contract-4; reason=runtime readiness productionEquivalence.deploymentEvidence is required; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- owner=release-owner pendingItems=8 collapsedItems=0 sources=manifest=7,orchestrator=1 collapsedSources=none envKeys=DDD_EVIDENCE_ENVIRONMENT,DDD_EVIDENCE_OPERATOR,DDD_RELEASE_CANDIDATE,DDD_RELEASE_EVIDENCE_STRICT,DDD_RELEASE_MANIFEST_STRICT
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-finalownerqueuefastpath-commands-must-include-readiness-summary-refresh; reason=optional artifact release/release-unblock-brief.json: finalOwnerQueueFastPath.commands must include readiness summary refresh; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-env-owner-input-packet-command-must-be-node-bin-ddd-release-env-owner-input-packet-contract-mjs; reason=optional artifact release/release-unblock-brief.json: handoffReferences release-env-owner-input-packet command must be node bin/ddd-release-env-owner-input-packet-contract.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-release-owner-input-receipt-command-must-be-node-bin-ddd-release-owner-input-receipt-contract-mjs; reason=optional artifact release/release-unblock-brief.json: handoffReferences release-owner-input-receipt command must be node bin/ddd-release-owner-input-receipt-contract.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-handoffreferences-rollback-deferral-owner-handoff-command-must-be-node-bin-ddd-rollback-deferral-template-mjs; reason=optional artifact release/release-unblock-brief.json: handoffReferences rollback-deferral-owner-handoff command must be node bin/ddd-rollback-deferral-template.mjs; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-env-owner-input-packet; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-env-owner-input-packet; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-release-owner-input-receipt; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for release-owner-input-receipt; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=manifest; id=manifest-missing-optional-artifact-release-release-unblock-brief-json-markdown-evidence-handoffs-must-include-required-command-for-rollback-deferral-owner-handoff; reason=optional artifact release/release-unblock-brief.json: markdown evidence handoffs must include required command for rollback-deferral-owner-handoff; envKeys=DDD_RELEASE_MANIFEST_STRICT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Regenerate the missing evidence artifact, then rerun `node bin/ddd-release-evidence-manifest.mjs`.
  - ownerAction: source=orchestrator; id=orchestrator-run-mode; reason=strict release requires run mode report, got plan; envKeys=DDD_RELEASE_EVIDENCE_STRICT; action=Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node bin/ddd-release-evidence-orchestrator.mjs`.
- owner=release-performance pendingItems=9 collapsedItems=0 sources=authenticated-performance=9 collapsedSources=none envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE
  - ownerAction: source=authenticated-performance; id=performance-actual-shape-1; reason=authenticated performance actual productionEquivalence.strict must be true for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
  - ownerAction: source=authenticated-performance; id=performance-actual-shape-2; reason=authenticated performance actual productionEquivalence.https must be true for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
  - ownerAction: source=authenticated-performance; id=performance-actual-shape-3; reason=authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
  - ownerAction: source=authenticated-performance; id=performance-actual-shape-4; reason=authenticated performance actual productionEquivalence.deploymentEvidence is required; action=Fix authenticated performance actual artifact shape and rerun the smoke.
  - ownerAction: source=authenticated-performance; id=performance-baseline-metadata-5; reason=strict release baseline requires baselineType=authenticated-runtime; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
  - ownerAction: source=authenticated-performance; id=performance-baseline-metadata-6; reason=acceptedAt must be an ISO timestamp; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
  - ownerAction: source=authenticated-performance; id=performance-baseline-metadata-7; reason=acceptedBy is required; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
  - ownerAction: source=authenticated-performance; id=performance-baseline-metadata-8; reason=sourceArtifact is required; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
  - ownerAction: source=authenticated-performance; id=performance-baseline-metadata-9; reason=sourceSha256 must be a SHA-256 hex digest; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.

## Actions By Category

### ai-runtime
- Owner: ai
  Action: Run `DDD_AI_EXPECT_PROVIDER_REMOTE=true DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true node bin/ddd-ai-runtime-drill.mjs` against production-equivalent AI runtime.

### business-e2e-freshness
- Owner: file-owner
  Action: Regenerate File processing E2E evidence within the release freshness window against the production-equivalent environment.
- Owner: payment-owner
  Action: Regenerate Payment webhook E2E evidence within the release freshness window against the production-equivalent environment.
- Owner: job-owner
  Action: Regenerate Job E2E evidence within the release freshness window against the production-equivalent environment.

### configuration
- Owner: release-infra
  Action: Replace all release env placeholders in `DDD_RELEASE_ENV_FILE`, run `DDD_RELEASE_ENV_FILE=.env.release.local node bin/ddd-release-env-file-lint.mjs`, then regenerate config evidence with `node bin/ddd-release-config-evidence.mjs`.
- Owner: release-infra
  Action: Generate production-equivalent config evidence with `DDD_RELEASE_ENV_FILE=.env.release DDD_RELEASE_CONFIG_STRICT=true node bin/ddd-release-config-evidence.mjs`.

### docker
- Owner: release-infra
  Action: Start Docker daemon or run `node bin/ddd-docker-build-evidence.mjs` in CI with Docker Buildx available.

### explain-plan
- Owner: database
  Action: Collect production-equivalent MySQL `EXPLAIN FORMAT=JSON` artifacts for all required hot paths and ensure no full scans or missing hotspot indexes remain.

### frontend-smoke
- Owner: lumira-ui
  Action: Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node bin/ddd-frontend-smoke-evidence.mjs`.

### manifest
- Owner: release-owner
  Action: Regenerate all missing evidence artifacts, then run `node bin/ddd-release-evidence-manifest.mjs`.

### manifest-provenance
- Owner: release-owner
  Action: Regenerate the manifest with `DDD_RELEASE_MANIFEST_STRICT=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR` after regenerating provenance-bearing artifacts.

### migration
- Owner: database
  Action: Run fresh database and old database upgrade Flyway drills, then regenerate migration evidence with fresh/upgrade flags.

### orchestrator
- Owner: release-owner
  Action: Run `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict` with real provenance and keep the generated `artifacts/ddd/release/orchestrator-report.json` with the release evidence bundle.

### other
- Owner: release-owner
  Action: Inspect the strict release gate blocker and attach an owner-specific remediation.

### outbox-state-machine
- Owner: platform-events
  Action: Run `DDD_OUTBOX_SMOKE_STRICT=true node bin/ddd-outbox-replay-dead-letter-smoke.mjs` after exporting real provenance, then confirm every owner relay report is present with zero failures and errors.

### performance-baseline
- Owner: release-performance
  Action: Run authenticated performance smoke against production-equivalent URL, then promote the accepted actual with `bin/ddd-promote-performance-baseline.mjs`.

### performance-freshness
- Owner: release-performance
  Action: Regenerate authenticated performance evidence within the release freshness window, then rerun baseline comparison and promotion.

### production-equivalent-runtime
- Owner: release-infra
  Action: Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.
- Owner: release-infra
  Action: Regenerate the runtime artifact against an HTTPS non-local production-equivalent URL.
- Owner: release-performance
  Action: Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.
- Owner: file-owner
  Action: Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend URL with real File storage and job token evidence.
- Owner: payment-owner
  Action: Regenerate Payment webhook E2E smoke against an HTTPS non-local production-equivalent webhook URL with provider sandbox or deployment evidence.
- Owner: job-owner
  Action: Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token.
- Owner: release-infra
  Action: Set `LUMIRA_BASE_URL`, `DEPLOY_CHECK_BASE_URL`, or `BASE_URL` to an HTTPS non-local backend URL before running the strict release orchestrator.

### rollback-drill
- Owner: release-owner
  Action: Run `node bin/ddd-init-rollback-drill.mjs`, fill real PASS/DEFERRED evidence for every context, then run `node bin/ddd-rollback-drill-evidence.mjs`.

### runtime-freshness
- Owner: release-infra
  Action: Regenerate runtime readiness within the release freshness window against the production-equivalent HTTPS backend, then rerun the strict release gate.

## Runtime Readiness Diagnostics

- baseUrl: http://127.0.0.1:8080
- localOnly: true
- productionEquivalence: strict=false https=false localOnly=true deploymentEvidence=missing
- checks: 30/30
- failures: 0
- contractIssue: runtime readiness productionEquivalence.strict must be true for strict release evidence
- contractIssue: runtime readiness productionEquivalence.https must be true for strict release evidence
- contractIssue: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- contractIssue: runtime readiness productionEquivalence.deploymentEvidence is required
- iam: ready=true
- auth: ready=true
- platform: ready=true
- message: ready=true
- files: ready=true
- plugins: ready=true
- localization: ready=true
- payment: ready=true
- ai: ready=true
- job: ready=true
- actionPlan: owner=release-infra pendingItems=4
- runtimeAction: runtime-readiness-contract-1; owner=release-infra; reason=runtime readiness productionEquivalence.strict must be true for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- runtimeAction: runtime-readiness-contract-2; owner=release-infra; reason=runtime readiness productionEquivalence.https must be true for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- runtimeAction: runtime-readiness-contract-3; owner=release-infra; reason=runtime readiness productionEquivalence.localOnly must be false for strict release evidence; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.
- runtimeAction: runtime-readiness-contract-4; owner=release-infra; reason=runtime readiness productionEquivalence.deploymentEvidence is required; envKeys=LUMIRA_BASE_URL,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_CANDIDATE,DDD_EVIDENCE_OPERATOR; action=Fix runtime readiness artifact contract issues and regenerate summary.json with `node bin/ddd-runtime-readiness-smoke.mjs`.

## Release Env Lint

- status: PASS inputKind=release-env-file envFile=.env.release.local keys=83 blockers=0 primaryBlockers=0
- envFileSecurity: checked=true mode=666 permissionSafe=true permissionCheckSkipped=true reason=env-file requiredMode=600
- unresolvedTemplateKeys: 0
- releaseConfigBlockers: 0
- releaseConfigBlockersFromPlaceholders: 0
- releaseConfigBlockersAfterPlaceholders: 0
- actionPlan: owner=release-infra pendingItems=0 envKeys=none

## Release Config Blockers

- status: PASS inputKind=release-env-file envFile=.env.release.local envFileExists=true
- blockerSummary: blockers=0 primaryBlockers=0 fromPlaceholders=0 afterPlaceholders=0
- coverage: required=46 runtimePresent=46 envFile=46 template=46 workflow=46
- missingRuntimeRequiredChecks: 0
- None
- configContractIssue: release config coverageMatrix missing runtime.lumira-ui base url
- configContractIssue: release config coverageMatrix unknown runtime.frontend base url

## Authenticated Performance Diagnostics

- actualBaseUrl: http://127.0.0.1:8080
- actualLocalOnly: true
- authenticatedPerformanceActualProductionEquivalence: strict=false https=false localOnly=true deploymentEvidence=missing
- actualFailed: 0
- actualP95: 29
- actualUpload: status=200 elapsedMs=45.49
- actualEndpointCount: 9
- actualShapeIssue: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- actualShapeIssue: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- actualShapeIssue: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- actualShapeIssue: authenticated performance actual productionEquivalence.deploymentEvidence is required
- baselineBaseUrl: http://127.0.0.1:8080
- baselineLocalOnly: true
- baselineP95: 88
- baselineUploadElapsedMs: 85.88
- baselineEndpointCount: 9
- baselineMetadataIssue: strict release baseline requires baselineType=authenticated-runtime
- baselineMetadataIssue: acceptedAt must be an ISO timestamp
- baselineMetadataIssue: acceptedBy is required
- baselineMetadataIssue: sourceArtifact is required
- baselineMetadataIssue: sourceSha256 must be a SHA-256 hex digest
- baselinePromotion: status=missing sourceFile=missing outputFile=missing
- baselinePromotionSource: sourceArtifact=missing sourceSha256=missing
- baselinePromotionEnv: acceptedBy=missing sourceEnvironment=local-evidence-audit releaseCandidate=local-ddd-release-audit
- actionPlan: owner=release-performance pendingItems=9
- performanceAction: performance-actual-shape-1; owner=release-performance; reason=authenticated performance actual productionEquivalence.strict must be true for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
- performanceAction: performance-actual-shape-2; owner=release-performance; reason=authenticated performance actual productionEquivalence.https must be true for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
- performanceAction: performance-actual-shape-3; owner=release-performance; reason=authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence; action=Fix authenticated performance actual artifact shape and rerun the smoke.
- performanceAction: performance-actual-shape-4; owner=release-performance; reason=authenticated performance actual productionEquivalence.deploymentEvidence is required; action=Fix authenticated performance actual artifact shape and rerun the smoke.
- performanceAction: performance-baseline-metadata-5; owner=release-performance; reason=strict release baseline requires baselineType=authenticated-runtime; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- performanceAction: performance-baseline-metadata-6; owner=release-performance; reason=acceptedAt must be an ISO timestamp; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- performanceAction: performance-baseline-metadata-7; owner=release-performance; reason=acceptedBy is required; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- performanceAction: performance-baseline-metadata-8; owner=release-performance; reason=sourceArtifact is required; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.
- performanceAction: performance-baseline-metadata-9; owner=release-performance; reason=sourceSha256 must be a SHA-256 hex digest; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; action=Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.

## Business Runtime E2E Diagnostics

- fileProcessing: status=PASS localOnly=true uploadMs=109.64 fileId=3
- fileProcessingProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing
- fileProcessingProductionEquivalenceIssue: strict file processing E2E requires HTTPS baseUrl evidence
- fileProcessingProductionEquivalenceIssue: strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080
- fileTask: SECURITY_SCAN=SUCCEEDED
- fileTask: TEXT_EXTRACT=SUCCEEDED
- fileTask: AI_PARSE=SUCCEEDED
- fileArtifact: SECURITY_SCAN_RESULT=true
- fileArtifact: TEXT_CONTENT=true
- fileArtifact: AI_PARSE_READY=true
- fileIssue: file processing productionEquivalence.https must be true for strict release evidence
- fileIssue: file processing productionEquivalence.localOnly must be false for strict release evidence
- fileIssue: file processing productionEquivalence.deploymentEvidence is required
- fileIssue: file processing productionEquivalence.issues must be empty for strict release evidence
- paymentWebhook: status=PASS localOnly=true orderStatus=PAID providerConfigured=true
- paymentWebhookProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing
- paymentWebhookProductionEquivalenceIssue: strict payment webhook E2E requires HTTPS baseUrl evidence
- paymentWebhookProductionEquivalenceIssue: strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080
- paymentWebhook.first: processed=true signatureValid=true elapsedMs=21.63
- paymentWebhook.duplicate: processed=true signatureValid=true elapsedMs=7.05
- paymentWebhook.nonceReplay: processed=false signatureValid=false elapsedMs=10.08
- paymentWebhook.badSignature: processed=false signatureValid=false elapsedMs=13.19
- paymentIssue: payment webhook productionEquivalence.https must be true for strict release evidence
- paymentIssue: payment webhook productionEquivalence.localOnly must be false for strict release evidence
- paymentIssue: payment webhook productionEquivalence.deploymentEvidence is required
- paymentIssue: payment webhook productionEquivalence.issues must be empty for strict release evidence
- jobE2e: localOnly=true unauthorizedStatus=401 failed=0 endpointCount=9
- jobE2eProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing
- jobE2eProductionEquivalenceIssue: strict job E2E requires HTTPS baseUrl evidence
- jobE2eProductionEquivalenceIssue: strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080
- jobEndpoint: platform-outbox-relay present=true status=200 dataType=boolean expected=boolean
- jobEndpoint: platform-online-session-heartbeat present=true status=200 dataType=boolean expected=boolean
- jobEndpoint: ai-knowledge-index present=true status=200 dataType=number expected=number
- jobEndpoint: message-heartbeat present=true status=200 dataType=boolean expected=boolean
- jobEndpoint: message-outbox-relay present=true status=200 dataType=number expected=number
- jobEndpoint: file-outbox-relay present=true status=200 dataType=number expected=number
- jobEndpoint: file-processing-run present=true status=200 dataType=number expected=number
- jobEndpoint: payment-outbox-relay present=true status=200 dataType=number expected=number
- jobEndpoint: plugin-outbox-relay present=true status=200 dataType=number expected=number
- jobOutboxOwnershipDelta: 0
- jobIssue: job E2E productionEquivalence.https must be true for strict release evidence
- jobIssue: job E2E productionEquivalence.localOnly must be false for strict release evidence
- jobIssue: job E2E productionEquivalence.deploymentEvidence is required
- jobIssue: job E2E productionEquivalence.issues must be empty for strict release evidence
- actionPlan: owner=file-owner pendingItems=1 envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN,LUMIRA_UPLOAD_STORAGE_ROOT
- businessAction: file-processing-production-equivalence; owner=file-owner; reason=strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,LUMIRA_UPLOAD_STORAGE_ROOT,LUMIRA_JOB_INTERNAL_TOKEN; action=Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node bin/ddd-file-processing-e2e-smoke.mjs`.
- actionPlan: owner=job-owner pendingItems=1 envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN
- businessAction: job-e2e-production-equivalence; owner=job-owner; reason=strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,LUMIRA_JOB_INTERNAL_TOKEN,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE; action=Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node bin/ddd-job-e2e-smoke.mjs`.
- actionPlan: owner=payment-owner pendingItems=1 envKeys=BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL,PAYMENT_PUBLIC_BASE_URL
- businessAction: payment-webhook-production-equivalence; owner=payment-owner; reason=strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL,PAYMENT_PUBLIC_BASE_URL,DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE; action=Regenerate Payment webhook E2E smoke against an HTTPS non-local webhook URL with provider sandbox or deployment evidence using `node bin/ddd-payment-webhook-e2e-smoke.mjs`.

## Docker Diagnostics

- status: FAIL
- command: docker
- cliStatus: 0
- cliVersion: Docker version 29.5.3, build d1c06ef
- daemonStatus: 0
- images: passed=0 failed=2 skipped=0
- image lumira-server: status=FAIL dockerfile=deploy/docker/service.Dockerfile tag=lumira/lumira-server:ddd-evidence-1781855618086 expectedPort=8080/tcp nonRoot=true
  staticDockerfile: status=PASS exists=true sha256=b028411f0ed5fb4bdb8a45a40d1764b118d25069179b14150dd6b6dc38494fe4
  blockers: docker build failed: #5 DONE 0.3s

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
  action: Inspect Docker build blockers and rebuild the image evidence artifact.
- image frontend: status=FAIL dockerfile=deploy/docker/frontend.Dockerfile tag=lumira/frontend:ddd-evidence-1781855618086 expectedPort=80/tcp nonRoot=false
  staticDockerfile: status=PASS exists=true sha256=43dc2013cd3f3595bbbba2b76c74e873fdd7bec13826e30fbc4a3a2fd01f1f78
  blockers: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT
  action: Inspect Docker build blockers and rebuild the image evidence artifact.
- actionPlan: owner=release-infra pendingItems=4 envKeys=DDD_DOCKER_BUILD_STRICT,DDD_DOCKER_COMMAND
- dockerAction: docker-blocker-1; owner=release-infra; reason=lumira-server: docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- dockerAction: docker-blocker-2; owner=release-infra; reason=frontend: docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=Resolve Docker image evidence blocker and rerun `node bin/ddd-docker-build-evidence.mjs`; for Docker Hub/network failures set `DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, and `DDD_DOCKER_NGINX_IMAGE` to trusted registry mirror images. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- dockerAction: docker-image-frontend-failed; owner=release-infra; reason=docker build failed after 3 attempt(s) with transient registry/network error: spawnSync cmd.exe ETIMEDOUT; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; image=frontend; dockerfile=deploy/docker/frontend.Dockerfile; action=Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- dockerAction: docker-image-lumira-server-failed; owner=release-infra; reason=docker build failed: #5 DONE 0.3s

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
ERROR: failed to build: failed to solve: failed to compute cache key: short read: expected 29724688 bytes but got 0: unexpected EOF; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; image=lumira-server; dockerfile=deploy/docker/service.Dockerfile; action=Fix Docker image build/inspect failure and regenerate image evidence with `DDD_DOCKER_BUILD_STRICT=true node bin/ddd-docker-build-evidence.mjs`; for transient registry failures configure `DDD_DOCKER_*_IMAGE` mirror overrides and rerun. Mirror retry example: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs. If CI already built the release candidate images, use explicit inspect-only evidence instead: DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs.
- remediation: transientRegistryFailure=true dockerUnavailable=false
- dockerTransientImage: frontend; attempts=3; retries=2; dockerfile=deploy/docker/frontend.Dockerfile
- dockerRemediationAction: docker-registry-mirror-retry; owner=release-infra; envKeys=DDD_DOCKER_COMMAND_TIMEOUT_MS,DDD_DOCKER_BUILD_RETRIES,DDD_DOCKER_MAVEN_IMAGE,DDD_DOCKER_JRE_IMAGE,DDD_DOCKER_NODE_IMAGE,DDD_DOCKER_NGINX_IMAGE; action=Rerun Docker evidence with registry-local mirror images and a higher retry budget.; exampleCommand=DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_COMMAND_TIMEOUT_MS=1800000 DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine node scripts/ddd-docker-build-evidence.mjs
- dockerRemediationAction: docker-existing-image-inspect; owner=release-infra; envKeys=DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE,DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE,DDD_DOCKER_EXISTING_FRONTEND_IMAGE; action=If CI already built and pushed the release candidate images, pull them and rerun Docker evidence in explicit inspect-only mode.; exampleCommand=DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs
- None

## Frontend Smoke Missing Flows

- Missing frontend smoke artifact

## Migration Runtime Evidence

- freshDatabaseValidated: true
- upgradeDatabaseValidated: true
- runtimeReady: true
- environment: local-fast-track-validation
- releaseCandidate: local-ddd-release-audit
- freshDatabaseEvidence: artifacts/ddd/migration/fresh-db-drill.json
- upgradeDatabaseEvidence: artifacts/ddd/migration/upgrade-db-drill.json
- proof fresh-database: validated=true; evidence=artifacts/ddd/migration/fresh-db-drill.json; required=Flyway log plus schema-history artifact from an empty production-equivalent database.; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE
- proof upgrade-database: validated=true; evidence=artifacts/ddd/migration/upgrade-db-drill.json; required=Before/after schema-history artifact plus Flyway log from a copy of the previous production schema.; envKeys=DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- fresh-database-drill: PASS; owner=database; evidence=artifacts/ddd/migration/fresh-db-drill.json; action=Run Flyway against an empty production-equivalent database and archive schema history plus Flyway logs.; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE
- upgrade-database-drill: PASS; owner=database; evidence=artifacts/ddd/migration/upgrade-db-drill.json; action=Run Flyway against a copy of the previous production schema and archive before/after schema history plus Flyway logs.; envKeys=DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- migration-environment: PASS; owner=release-infra; evidence=local-fast-track-validation; action=Set the production-equivalent migration environment name before generating evidence.; envKeys=DDD_MIGRATION_ENVIRONMENT,DDD_EVIDENCE_ENVIRONMENT,DDD_RELEASE_ENVIRONMENT
- migration-release-candidate: PASS; owner=release-infra; evidence=local-ddd-release-audit; action=Set the immutable release candidate or commit SHA for the migration drill.; envKeys=DDD_RELEASE_CANDIDATE,GITHUB_SHA
- migration-operator: PASS; owner=release-owner; evidence=codex-local-audit; action=Record the operator or CI actor who executed the migration drill.; envKeys=DDD_MIGRATION_OPERATOR,DDD_EVIDENCE_OPERATOR,GITHUB_ACTOR
- migration-completed-at: PASS; owner=release-owner; evidence=2026-06-16T06:55:00.000Z; action=Record the ISO timestamp when both migration drills completed.; envKeys=DDD_MIGRATION_COMPLETED_AT

## EXPLAIN Evidence Diagnostics

- gateReport: present=true status=PASS blockers=0 generatedAt=2026-06-19T14:43:03.891Z
- dir: tmp\ddd-explain
- files: 8
- missingRequiredFiles: 0
- legacyPlanImports: 0
- actionPlan: owner=database pendingFiles=2
- explainAction: message-archive-total.json; reasons=[plan] message-archive-total.json: archive_candidates does not report an index key for access_type=ALL | [plan] message-archive-total.json: archive_candidates uses full scan access_type=ALL; command=Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
- explainAction: message-unread-count.json; reasons=[plan] message-unread-count.json: unread_candidates does not report an index key for access_type=ALL | [plan] message-unread-count.json: unread_candidates uses full scan access_type=ALL; command=Run production-equivalent MySQL EXPLAIN collection with `node bin/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs`.
- explainFile: ai-knowledge-index-retry.json queryName=ai-knowledge-index-retry sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainFile: message-archive-total.json queryName=message-archive-total sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=2
- explainFile: message-unread-count.json queryName=message-unread-count sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=2
- explainFile: message-visible-list.json queryName=message-visible-list sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainFile: platform-outbox-owner-relay-file.json queryName=platform-outbox-owner-relay-file sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainFile: platform-outbox-owner-relay-message.json queryName=platform-outbox-owner-relay-message sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainFile: platform-runtime-appearance.json queryName=platform-runtime-appearance sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainFile: plugin-bootstrap.json queryName=plugin-bootstrap sourceEnvironment=production-equivalent releaseCandidate=671eed88ed53 legacy=false issues=0
- explainIssue: [plan] message-archive-total.json: archive_candidates uses full scan access_type=ALL
- explainIssue: [plan] message-archive-total.json: archive_candidates does not report an index key for access_type=ALL
- explainIssue: [plan] message-unread-count.json: unread_candidates uses full scan access_type=ALL
- explainIssue: [plan] message-unread-count.json: unread_candidates does not report an index key for access_type=ALL

## AI Runtime Diagnostics

- status: PASS
- baseUrl: http://127.0.0.1:8080
- localOnly: true
- aiRuntimeProductionEquivalence: strict=false https=false localOnly=true deploymentEvidence=missing
- providerRemoteConfigured: false
- ownerGatewayConfiguredOwners: 0
- actionPlan: owner=ai pendingItems=3
- aiAction: ai-owner-gateway; owner=ai; reason=ownerGateway status=CONFIGURED configuredOwners=0; envKeys=DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE,LUMIRA_AI_OWNER_IAM_BASE_URL,LUMIRA_AI_OWNER_FILE_BASE_URL,LUMIRA_AI_OWNER_PLATFORM_BASE_URL; action=Configure and verify remote AI owner gateways for IAM/File/Platform integrations.
- aiAction: ai-provider-runtime; owner=ai; reason=provider status=CONFIGURED remoteConfigured=false; envKeys=DDD_AI_EXPECT_PROVIDER_REMOTE,LUMIRA_AI_PROVIDER,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL,LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY; action=Configure and verify a remote AI provider runtime; strict release must not rely on local fallback.
- aiAction: ai-runtime-base-url; owner=ai; reason=missing production-equivalent AI base URL; envKeys=LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; action=Run AI runtime drill against an HTTPS non-local AI runtime base URL.
- failureCategories: none
- failureOwners: none
- failures: none

## Rollback Drill Contexts

- status: PASS
- environment: local-evidence-audit
- releaseVersion: local-ddd-release-audit
- summary: ready=10/10 pass=0 deferred=10 missing=0 blockers=0
- ownerPlan: ai-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: auth-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: file-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: iam-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: job-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: localization-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: message-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: payment-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: platform-owner pending=0 ready=1 missingEvidence=0
- ownerPlan: plugin-owner pending=0 ready=1 missingEvidence=0
- IAM: DEFERRED; owner=iam-owner; reason=IAM rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise permission snapshot rollback, cache invalidation, and IAM v2-to-v1 adapter fallback; attach readiness, audit, and cache evidence.
- Auth: DEFERRED; owner=auth-owner; reason=Auth rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise auth adapter rollback with session TTL compatibility, login smoke, and forced logout/keepalive evidence.
- Platform: DEFERRED; owner=platform-owner; reason=Platform rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise platform config/runtime appearance rollback and cache clear; attach bootstrap/config version and audit evidence.
- Message: DEFERRED; owner=message-owner; reason=Message rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise message relay pause, monolith-compatible delivery fallback, and idempotent replay; attach relay and message state evidence.
- File: DEFERRED; owner=file-owner; reason=File rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise file processing pause, stable object-key access, and task rerun by id; attach upload, processing row, and storage evidence.
- Plugin: DEFERRED; owner=plugin-owner; reason=Plugin rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise tenant plugin disable/version rollback and bootstrap projection rebuild; attach audit and tenant projection evidence.
- Localization: DEFERRED; owner=localization-owner; reason=Localization rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise localization release rollback and runtime bundle cache clear; attach release id, bundle metrics, and audit evidence.
- Payment: DEFERRED; owner=payment-owner; reason=Payment rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise payment webhook route fallback and idempotent event replay; attach provider routing, webhook metrics, and order trace evidence.
- AI: DEFERRED; owner=ai-owner; reason=AI rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise AI provider disablement, knowledge index job pause, and document index rebuild/degraded chat transcript evidence.
- Job: DEFERRED; owner=job-owner; reason=Job rollback drill is DEFERRED with approved deferral evidence; evidence=artifacts/ddd/rollback/rollback-drill-handoff.md; action=Exercise XXL-JOB handler disablement and manual owner internal endpoint fallback; attach dashboard, token, and endpoint evidence.

## Orchestrator Preflight

- mode: plan
- status: FAIL
- blockers: 4
- warnings: 0
- selectedSteps: 26; executedResults: 0
- action: Run `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict` after resolving preflight blockers.
- actionPlan: owner=ai pendingItems=1 envKeys=BASE_URL,DEPLOY_CHECK_BASE_URL,LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL
- orchestratorAction: orchestrator-preflight-ai-runtime-base-url; owner=ai; reason=missing AI runtime base URL; envKeys=LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; checkId=ai-runtime-base-url; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- actionPlan: owner=database pendingItems=1 envKeys=DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_VALIDATED
- orchestratorAction: orchestrator-preflight-migration-runtime-evidence; owner=database; reason=missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE; checkId=migration-runtime-evidence; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- actionPlan: owner=release-infra pendingItems=2 envKeys=BASE_URL,DEPLOY_CHECK_BASE_URL,FRONTEND_BASE_URL,LUMIRA_BASE_URL,PLAYWRIGHT_BASE_URL
- orchestratorAction: orchestrator-preflight-backend-runtime-base-url; owner=release-infra; reason=missing backend runtime base URL; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; checkId=backend-runtime-base-url; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- orchestratorAction: orchestrator-preflight-frontend-runtime-base-url; owner=release-infra; reason=missing deployed frontend base URL; envKeys=PLAYWRIGHT_BASE_URL,FRONTEND_BASE_URL; checkId=frontend-runtime-base-url; action=Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node bin/ddd-release-evidence-orchestrator.mjs`.
- actionPlan: owner=release-owner pendingItems=1 envKeys=DDD_RELEASE_EVIDENCE_STRICT
- orchestratorAction: orchestrator-run-mode; owner=release-owner; reason=strict release requires run mode report, got plan; envKeys=DDD_RELEASE_EVIDENCE_STRICT; action=Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node bin/ddd-release-evidence-orchestrator.mjs`.
- backend-runtime-base-url: missing backend runtime base URL; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL
- ai-runtime-base-url: missing AI runtime base URL; envKeys=LUMIRA_AI_BASE_URL,LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL
- frontend-runtime-base-url: missing deployed frontend base URL; envKeys=PLAYWRIGHT_BASE_URL,FRONTEND_BASE_URL
- migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE,DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- step release-env-file-lint: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step release-config-evidence: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step backend-tests: not-run; flags=heavy; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step backend-test-evidence: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step backend-build-evidence: not-run; flags=heavy; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step docker-build-evidence: not-run; flags=heavy; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step frontend-static-evidence: not-run; flags=heavy; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step frontend-build-evidence: not-run; flags=heavy; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step migration-evidence: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step runtime-readiness: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step authenticated-performance: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step authenticated-performance-baseline: not-run; flags=disabled,optional; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_PROMOTE,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_EVIDENCE_STRICT
- step file-processing: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step payment-webhook: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step outbox-replay-dead-letter: not-run; envKeys=DDD_OUTBOX_SMOKE_STRICT,DDD_RELEASE_EVIDENCE_STRICT
- step job-e2e: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step ai-runtime: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step frontend-playwright-smoke: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step frontend-smoke: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step rollback-drill: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step explain-gate: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step physical-split: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT,DDD_SPLIT_STRICT
- step manifest-provenance-preflight: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT,DDD_RELEASE_MANIFEST_CHECK_ENV
- step manifest: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step release-gate: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT
- step readiness-summary: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT

## Raw Blockers

- [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.strict must be true for strict release evidence
- [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.https must be true for strict release evidence
- [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.localOnly must be false for strict release evidence
- [production-equivalent-runtime] runtime-readiness-summary: runtime readiness productionEquivalence.deploymentEvidence is required
- [runtime-freshness] runtime-readiness-freshness: checkedAt is 72.3h old; limit=24h
- [production-equivalent-runtime] runtime-readiness-production-equivalence: strict runtime readiness deploymentEvidence is required
- [production-equivalent-runtime] runtime-readiness-environment-strict: strict release requires production-equivalent non-local evidence
- [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.strict must be true for strict release evidence
- [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.https must be true for strict release evidence
- [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.localOnly must be false for strict release evidence
- [production-equivalent-runtime] authenticated-performance-shape: authenticated performance actual productionEquivalence.deploymentEvidence is required
- [performance-freshness] authenticated-performance-freshness: checkedAt is 57.8h old; limit=24h
- [production-equivalent-runtime] authenticated-performance-production-equivalence: strict authenticated performance actual deploymentEvidence is required
- [production-equivalent-runtime] authenticated-performance-environment-strict: strict release requires production-equivalent non-local evidence
- [performance-baseline] authenticated-performance-baseline-environment: strict release requires a non-local baseline baseUrl, got http://127.0.0.1:8080
- [performance-baseline] authenticated-performance-baseline-metadata: strict release baseline requires baselineType=authenticated-runtime
- [performance-baseline] authenticated-performance-baseline-metadata: acceptedAt must be an ISO timestamp
- [performance-baseline] authenticated-performance-baseline-metadata: acceptedBy is required
- [performance-baseline] authenticated-performance-baseline-metadata: sourceArtifact is required
- [performance-baseline] authenticated-performance-baseline-metadata: sourceSha256 must be a SHA-256 hex digest
- [performance-baseline] authenticated-performance-baseline-strict: strict release requires authenticated performance baseline comparison
- [business-e2e-freshness] file-processing-freshness: finishedAt is 131.7h old; limit=24h
- [production-equivalent-runtime] file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence
- [production-equivalent-runtime] file-processing-production-equivalence: strict file processing E2E deploymentEvidence is required
- [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.https must be true for strict release evidence
- [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.localOnly must be false for strict release evidence
- [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.deploymentEvidence is required
- [production-equivalent-runtime] file-processing-e2e: file processing productionEquivalence.issues must be empty for strict release evidence
- [production-equivalent-runtime] file-processing-environment-strict: strict release requires production-equivalent non-local evidence
- [business-e2e-freshness] payment-webhook-freshness: finishedAt is 131.6h old; limit=24h
- [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E requires HTTPS baseUrl evidence
- [production-equivalent-runtime] payment-webhook-production-equivalence: strict payment webhook E2E deploymentEvidence is required
- [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.https must be true for strict release evidence
- [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.localOnly must be false for strict release evidence
- [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.deploymentEvidence is required
- [production-equivalent-runtime] payment-webhook-e2e: payment webhook productionEquivalence.issues must be empty for strict release evidence
- [production-equivalent-runtime] payment-webhook-environment-strict: strict release requires production-equivalent non-local evidence
- [outbox-state-machine] outbox-replay-dead-letter-freshness: generatedAt is 91.7h old; limit=24h
- [business-e2e-freshness] job-e2e-freshness: checkedAt is 131.4h old; limit=24h
- [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E requires HTTPS baseUrl evidence
- [production-equivalent-runtime] job-e2e-production-equivalence: strict job E2E deploymentEvidence is required
- [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.https must be true for strict release evidence
- [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.localOnly must be false for strict release evidence
- [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.deploymentEvidence is required
- [production-equivalent-runtime] job-e2e-smoke: job E2E productionEquivalence.issues must be empty for strict release evidence
- [production-equivalent-runtime] job-e2e-environment-strict: strict release requires production-equivalent non-local evidence
- [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.strict must be true for strict release evidence
- [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.https must be true for strict release evidence
- [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.localOnly must be false for strict release evidence
- [ai-runtime] ai-runtime-drill: AI runtime productionEquivalence.deploymentEvidence is required
- [ai-runtime] ai-runtime-freshness: checkedAt is 57.8h old; limit=24h
- [ai-runtime] ai-runtime-production-equivalence: strict AI runtime drill deploymentEvidence is required
- [ai-runtime] ai-runtime-environment-strict: strict release requires production-equivalent non-local evidence
- [ai-runtime] ai-runtime-drill-provider: strict release requires DDD_AI_EXPECT_PROVIDER_REMOTE=true evidence
- [ai-runtime] ai-runtime-drill-owner-gateway: strict release requires DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true evidence
- [other] physical-split-readiness-freshness: generatedAt is 57.7h old; limit=24h
- [other] backend-test-evidence-freshness: generatedAt is 57.7h old; limit=24h
- [other] backend-build-evidence-freshness: generatedAt is 57.7h old; limit=24h
- [docker] docker-build-evidence-freshness: generatedAt is 56.9h old; limit=24h
- [docker] docker-build-evidence: lumira-server: docker build failed after 3 attempt(s) with transient registry/network error status 1
- [docker] docker-build-evidence: frontend: docker build failed after 3 attempt(s) with transient registry/network error status 1
- [migration] migration-evidence-freshness: generatedAt is 57.7h old; limit=24h
- [configuration] release-env-lint-freshness: generatedAt is 48.9h old; limit=24h
- [configuration] release-env-lint: status=FAIL, blockers=156
- [configuration] release-env-lint-placeholders: unresolvedTemplateKeys=93
- [configuration] release-env-lint-config: releaseConfigBlockers=63
- [configuration] release-config-evidence-freshness: generatedAt is 48.2h old; limit=24h
- [configuration] release-config-evidence: status=FAIL, blockers=63
- [orchestrator] release-evidence-orchestrator-freshness: generatedAt is 57.1h old; limit=24h
- [production-equivalent-runtime] release-evidence-orchestrator-preflight-backend-runtime-base-url: missing backend runtime base URL
- [ai-runtime] release-evidence-orchestrator-preflight-ai-runtime-base-url: missing AI runtime base URL
- [orchestrator] release-evidence-orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL
- [migration] release-evidence-orchestrator-preflight-migration-runtime-evidence: missing migration drill env: DDD_MIGRATION_FRESH_DB_VALIDATED, DDD_MIGRATION_UPGRADE_DB_VALIDATED, DDD_MIGRATION_FRESH_DB_EVIDENCE, DDD_MIGRATION_UPGRADE_DB_EVIDENCE
- [orchestrator] release-evidence-orchestrator: strict release requires run mode report, got plan
- [manifest-provenance] release-evidence-manifest-provenance: manifest provenance sourceEnvironment is required
- [manifest-provenance] release-evidence-manifest-provenance: manifest provenance releaseCandidate is required
- [manifest-provenance] release-evidence-manifest-provenance: manifest provenance evidenceOperator is required
- [manifest] release-evidence-manifest: manifest blockers length mismatch: declared=1, actual=4
- [manifest] release-evidence-manifest: missing EXPLAIN files in evidence manifest
- [other] frontend-build-evidence-freshness: generatedAt is 91.8h old; limit=24h
- [other] frontend-static-evidence-freshness: generatedAt is 91.8h old; limit=24h
- [frontend-smoke] frontend-smoke-freshness: generatedAt is 57.8h old; limit=24h
- [frontend-smoke] frontend-smoke-production-equivalence: strict frontend smoke deploymentEvidence is required
- [frontend-smoke] frontend-smoke-environment-strict: strict release requires production-equivalent non-local evidence
- [frontend-smoke] frontend-smoke-environment: frontend smoke productionEquivalence.strict must be true for strict release evidence
- [frontend-smoke] frontend-smoke-environment: frontend smoke productionEquivalence.https must be true for strict release evidence
- [frontend-smoke] frontend-smoke-environment: frontend smoke productionEquivalence.localOnly must be false for strict release evidence
- [frontend-smoke] frontend-smoke-environment: frontend smoke productionEquivalence.deploymentEvidence is required
- [frontend-smoke] frontend-smoke-environment: strict release requires DDD_FRONTEND_EXPECT_DEPLOYED=true evidence
- [frontend-smoke] frontend-smoke-environment: strict release requires HTTPS frontend baseURL evidence
- [frontend-smoke] frontend-smoke-environment: artifact is local-only: http://127.0.0.1:8000
- [frontend-smoke] frontend-smoke: frontend smoke blockers length mismatch: declared=0, actual=7
- [rollback-drill] rollback-drill-freshness: generatedAt is 72.2h old; limit=24h
- [explain-plan] explain-evidence-strict: strict release requires production-scale EXPLAIN artifacts
