#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { releaseConfigGroups } from "./ddd-release-config-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const workflowFile = path.join(repoRoot, ".github", "workflows", "ddd-release-evidence.yml");
const ciWorkflowFile = path.join(repoRoot, ".github", "workflows", "ci.yml");
const templateFile = path.join(repoRoot, "docs", "36-ddd-release-env-template.env");
const frontendPackageFile = path.join(repoRoot, "frontend", "package.json");
const frontendVitestConfigFile = path.join(repoRoot, "frontend", "vitest.config.ts");
const frontendPlaywrightConfigFile = path.join(repoRoot, "frontend", "playwright.config.ts");

function envKeysFromTemplate(file) {
  return new Set(fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => line.slice(0, line.indexOf("=")).trim()));
}

function envKeysFromWorkflow(file) {
  const text = fs.readFileSync(file, "utf8");
  const keys = new Set();
  for (const match of text.matchAll(/^\s{6,}([A-Z][A-Z0-9_]+):\s/mg)) {
    keys.add(match[1]);
  }
  for (const match of text.matchAll(/\b([A-Z][A-Z0-9_]+)\b/g)) {
    if (text.slice(Math.max(0, match.index - 20), match.index).includes("secrets.")
      || text.slice(Math.max(0, match.index - 20), match.index).includes("inputs.")
      || text.slice(Math.max(0, match.index - 20), match.index).includes("$")) {
      keys.add(match[1]);
    }
  }
  return keys;
}

function requiredConfigRequirements() {
  return releaseConfigGroups.flatMap((group) => group.requirements
    .filter((requirement) => requirement.required !== false)
    .map((requirement) => ({
      group: group.name,
      name: requirement.name,
      keys: requirement.keys,
    })));
}

function scriptTestFiles(dir = path.join(repoRoot, "scripts")) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...scriptTestFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".test.mjs")) {
      files.push(path.relative(repoRoot, fullPath).split(path.sep).join("/"));
    }
  }
  return files.sort();
}

function firstMissingCoverage(keys, targetName) {
  const missing = [];
  for (const requirement of requiredConfigRequirements()) {
    if (!requirement.keys.some((key) => keys.has(key))) {
      missing.push(`${requirement.group}.${requirement.name}: ${targetName} must define one of ${requirement.keys.join(" or ")}`);
    }
  }
  return missing;
}

const templateKeys = envKeysFromTemplate(templateFile);
const workflowKeys = envKeysFromWorkflow(workflowFile);

assert.deepEqual(firstMissingCoverage(templateKeys, "release env template"), []);
assert.deepEqual(firstMissingCoverage(workflowKeys, "release evidence workflow"), []);

const workflowText = fs.readFileSync(workflowFile, "utf8");
const ciWorkflowText = fs.readFileSync(ciWorkflowFile, "utf8");
const frontendPackageJson = JSON.parse(fs.readFileSync(frontendPackageFile, "utf8"));
const frontendVitestConfigText = fs.readFileSync(frontendVitestConfigFile, "utf8");
const frontendPlaywrightConfigText = fs.readFileSync(frontendPlaywrightConfigFile, "utf8");
const missingCiScriptTests = scriptTestFiles()
  .filter((file) => !ciWorkflowText.includes(`node ${file}`));
assert.deepEqual(missingCiScriptTests, [], "CI must run every scripts/*.test.mjs file");
const requiredDddGateJavaTests = [
  "InternalServiceTokenAuthFilterTest",
  "JwtTokenParserTest",
  "FieldCryptoServiceTest",
  "DataPermissionResolverTest",
  "TraceIdFilterTest",
  "RuntimeSecurityPropertiesValidatorTest",
  "DefaultAdminPasswordBaselineTest",
  "RedisStreamPlatformEventDispatcherTest",
  "PlatformEventPublisherTest",
  "MessageNoticeMapperHotPathSqlTest",
  "MessageEventFactoryTest",
  "MessageEventDeliveryServiceTest",
  "MessageWebSocketTicketServiceTest",
  "MessageSessionHandshakeInterceptorTest",
  "PluginArchitectureContractTest",
  "PluginRuntimeSecurityPolicyTest",
  "PluginPersistenceServiceTest",
  "PaymentOutboxRelayTest",
  "PaymentManagementAppServiceTest",
];
const missingDddGateJavaTests = requiredDddGateJavaTests
  .filter((testName) => !ciWorkflowText.includes(testName));
assert.deepEqual(missingDddGateJavaTests, [], "DDD CI gate must explicitly run high-risk Java contract tests");
assert(ciWorkflowText.includes("working-directory: frontend"), "frontend CI job must run from frontend directory");
assert(ciWorkflowText.includes("pnpm install --frozen-lockfile"), "frontend CI must install with the frozen lockfile");
assert(ciWorkflowText.includes("pnpm run lint"), "frontend CI must run lint");
assert(ciWorkflowText.includes("pnpm run typecheck"), "frontend CI must run typecheck");
assert(ciWorkflowText.includes("pnpm run test"), "frontend CI must run unit tests");
assert.equal(frontendPackageJson.scripts?.test, "vitest run", "frontend test script must run Vitest");
assert.equal(frontendPackageJson.scripts?.["test:e2e:smoke"], "playwright test --grep @smoke", "frontend smoke e2e script must keep @smoke filtering");
assert(frontendVitestConfigText.includes("src/**/*.test.ts"), "Vitest config must include TypeScript unit tests");
assert(frontendVitestConfigText.includes("src/**/*.test.tsx"), "Vitest config must include TSX unit tests");
assert(frontendPlaywrightConfigText.includes("forbidOnly: Boolean(process.env.CI)"), "Playwright config must forbid test.only in CI");
assert(frontendPlaywrightConfigText.includes("retries: process.env.CI ? 2 : 0"), "Playwright config must retry transient CI smoke failures");
assert(frontendPlaywrightConfigText.includes("trace: 'retain-on-failure'"), "Playwright config must retain traces for release debugging");
const prepareIndex = workflowText.indexOf("Prepare release evidence environment file");
const preflightIndex = workflowText.indexOf("Preflight release configuration evidence");
const installIndex = workflowText.indexOf("Install frontend dependencies");
const dockerIndex = workflowText.indexOf("docker/setup-buildx-action");
const orchestratorIndex = workflowText.indexOf("Run release evidence orchestrator");
const refreshFinalIndex = workflowText.indexOf("Refresh final go no-go packet");
const unblockBriefIndex = workflowText.indexOf("Generate release unblock brief");
const validateUnblockBriefContractIndex = workflowText.indexOf("Validate release unblock brief contract");
const validateExecutionRunReportIndex = workflowText.indexOf("Validate release execution run report");
const validateExplainGateReportIndex = workflowText.indexOf("Validate EXPLAIN gate report");
const validateArtifactIntegrityContractIndex = workflowText.indexOf("Validate release artifact integrity contract");
const validateArtifactIntegrityGateContractIndex = workflowText.indexOf("Validate release artifact integrity gate contract");
const validateArtifactPathLeakContractIndex = workflowText.indexOf("Validate release artifact path leak contract");
const validateCutoverContractIndex = workflowText.indexOf("Validate release cutover contract");
const validateFinalGoNoGoGateContractIndex = workflowText.indexOf("Validate release final go no-go gate contract");
const validatePreflightGateContractIndex = workflowText.indexOf("Validate release preflight gate contract");
const validateFinalOwnerQueueContractIndex = workflowText.indexOf("Validate release final owner queue contract");
const validateClosureWaveReceiptsContractIndex = workflowText.indexOf("Validate release closure wave receipts contract");
const validateNextActionQueueContractIndex = workflowText.indexOf("Validate release next action queue contract");
const validateExecutionQueueContractIndex = workflowText.indexOf("Validate release execution queue contract");
const validateCommandCatalogContractIndex = workflowText.indexOf("Validate release command catalog contract");
const validateOwnerHandoffContractIndex = workflowText.indexOf("Validate release owner handoff contract");
const validateEnvOwnerMatrixContractIndex = workflowText.indexOf("Validate release env owner matrix contract");
const validateEnvFillPriorityContractIndex = workflowText.indexOf("Validate release env fill priority contract");
const validateEnvCanonicalFillContractIndex = workflowText.indexOf("Validate release env canonical fill contract");
const validateEnvReadinessRedactedContractIndex = workflowText.indexOf("Validate release env readiness redacted contract");
const validateEnvOwnerHandoffRedactedContractIndex = workflowText.indexOf("Validate release env owner handoff redacted contract");
const validateEnvOwnerTemplatesContractIndex = workflowText.indexOf("Validate release env owner templates contract");
const validateEnvOwnerTemplatesMergeContractIndex = workflowText.indexOf("Validate release env owner templates merge contract");
const validateEnvBootstrapContractIndex = workflowText.indexOf("Validate release env bootstrap contract");
const validateEnvReadinessGateContractIndex = workflowText.indexOf("Validate release env readiness gate contract");
const validateEnvAliasSyncContractIndex = workflowText.indexOf("Validate release env alias sync contract");
const validateEnvCanonicalLintContractIndex = workflowText.indexOf("Validate release env canonical lint contract");
const validateEnvCanonicalMergeContractIndex = workflowText.indexOf("Validate release env canonical merge contract");
const validateEnvFileLintContractIndex = workflowText.indexOf("Validate release env file lint contract");
const validateReleaseEvidenceGateContractIndex = workflowText.indexOf("Validate release evidence gate contract");
const capturePreflightIndex = workflowText.indexOf("Capture release preflight output");
const validatePreflightCaptureIndex = workflowText.indexOf("Validate release preflight capture");
const refreshManifestAfterPreflightIndex = workflowText.indexOf("Refresh release manifest after preflight capture");
const uploadIndex = workflowText.indexOf("Upload release evidence artifacts");
const appendSummaryIndex = workflowText.indexOf("Append release readiness summary");
const enforceIndex = workflowText.indexOf("Enforce release evidence result");

assert(prepareIndex >= 0, "workflow must prepare release evidence env file");
assert(preflightIndex > prepareIndex, "config preflight must run after env file preparation");
assert(preflightIndex < installIndex, "config preflight must run before frontend dependency installation");
assert(preflightIndex < dockerIndex, "config preflight must run before Docker setup");
assert(refreshFinalIndex > orchestratorIndex, "final go/no-go packet must refresh after orchestrator runs");
assert(refreshFinalIndex < uploadIndex, "final go/no-go packet must refresh before artifacts are uploaded");
assert(unblockBriefIndex > refreshFinalIndex, "release unblock brief must generate after final packet refresh");
assert(unblockBriefIndex < validateArtifactIntegrityContractIndex, "release unblock brief must generate before artifact validation");
assert(unblockBriefIndex < uploadIndex, "release unblock brief must generate before artifacts are uploaded");
assert(validateUnblockBriefContractIndex > unblockBriefIndex, "release unblock brief contract must run after brief generation");
assert(validateUnblockBriefContractIndex < validateArtifactIntegrityContractIndex, "release unblock brief contract must run before artifact validation");
assert(validateExecutionRunReportIndex > validateUnblockBriefContractIndex, "release execution run report contract must run after unblock brief validation");
assert(validateExecutionRunReportIndex < validateArtifactIntegrityContractIndex, "release execution run report contract must run before artifact validation");
assert(validateArtifactIntegrityContractIndex > refreshFinalIndex, "release artifact integrity contract must run after final packet refresh");
assert(validateArtifactIntegrityContractIndex < validateExplainGateReportIndex, "release artifact integrity contract must run before EXPLAIN validation");
assert(validateArtifactIntegrityGateContractIndex > validateArtifactIntegrityContractIndex, "release artifact integrity gate contract must run after artifact integrity contract");
assert(validateArtifactIntegrityGateContractIndex < validateExplainGateReportIndex, "release artifact integrity gate contract must run before EXPLAIN validation");
assert(validateArtifactPathLeakContractIndex > validateArtifactIntegrityGateContractIndex, "release artifact path leak contract must run after artifact integrity gate contract");
assert(validateArtifactPathLeakContractIndex < validateCutoverContractIndex, "release artifact path leak contract must run before cutover validation");
assert(validateCutoverContractIndex > validateArtifactPathLeakContractIndex, "release cutover contract must run after artifact path leak contract");
assert(validateCutoverContractIndex < validateExplainGateReportIndex, "release cutover contract must run before EXPLAIN validation");
assert(validateFinalGoNoGoGateContractIndex > validateCutoverContractIndex, "release final go no-go gate contract must run after cutover contract");
assert(validateFinalGoNoGoGateContractIndex < validateExplainGateReportIndex, "release final go no-go gate contract must run before EXPLAIN validation");
assert(validatePreflightGateContractIndex > validateFinalGoNoGoGateContractIndex, "release preflight gate contract must run after final go no-go gate contract");
assert(validatePreflightGateContractIndex < validateExplainGateReportIndex, "release preflight gate contract must run before EXPLAIN validation");
assert(validateFinalOwnerQueueContractIndex > validatePreflightGateContractIndex, "release final owner queue contract must run after preflight gate contract");
assert(validateFinalOwnerQueueContractIndex < validateExplainGateReportIndex, "release final owner queue contract must run before EXPLAIN validation");
assert(validateClosureWaveReceiptsContractIndex > validateFinalOwnerQueueContractIndex, "release closure wave receipts contract must run after final owner queue contract");
assert(validateClosureWaveReceiptsContractIndex < validateExplainGateReportIndex, "release closure wave receipts contract must run before EXPLAIN validation");
assert(validateNextActionQueueContractIndex > validateClosureWaveReceiptsContractIndex, "release next action queue contract must run after closure wave receipts contract");
assert(validateNextActionQueueContractIndex < validateExplainGateReportIndex, "release next action queue contract must run before EXPLAIN validation");
assert(validateExecutionQueueContractIndex > validateNextActionQueueContractIndex, "release execution queue contract must run after next action queue contract");
assert(validateExecutionQueueContractIndex < validateExplainGateReportIndex, "release execution queue contract must run before EXPLAIN validation");
assert(validateCommandCatalogContractIndex > validateExecutionQueueContractIndex, "release command catalog contract must run after execution queue contract");
assert(validateCommandCatalogContractIndex < validateExplainGateReportIndex, "release command catalog contract must run before EXPLAIN validation");
assert(validateOwnerHandoffContractIndex > validateCommandCatalogContractIndex, "release owner handoff contract must run after command catalog contract");
assert(validateOwnerHandoffContractIndex < validateExplainGateReportIndex, "release owner handoff contract must run before EXPLAIN validation");
assert(validateEnvOwnerMatrixContractIndex > validateOwnerHandoffContractIndex, "release env owner matrix contract must run after owner handoff contract");
assert(validateEnvOwnerMatrixContractIndex < validateExplainGateReportIndex, "release env owner matrix contract must run before EXPLAIN validation");
assert(validateEnvFillPriorityContractIndex > validateEnvOwnerMatrixContractIndex, "release env fill priority contract must run after env owner matrix contract");
assert(validateEnvFillPriorityContractIndex < validateExplainGateReportIndex, "release env fill priority contract must run before EXPLAIN validation");
assert(validateEnvCanonicalFillContractIndex > validateEnvFillPriorityContractIndex, "release env canonical fill contract must run after env fill priority contract");
assert(validateEnvCanonicalFillContractIndex < validateExplainGateReportIndex, "release env canonical fill contract must run before EXPLAIN validation");
assert(validateEnvReadinessRedactedContractIndex > validateEnvCanonicalFillContractIndex, "release env readiness redacted contract must run after env canonical fill contract");
assert(validateEnvReadinessRedactedContractIndex < validateExplainGateReportIndex, "release env readiness redacted contract must run before EXPLAIN validation");
assert(validateEnvOwnerHandoffRedactedContractIndex > validateEnvReadinessRedactedContractIndex, "release env owner handoff redacted contract must run after env readiness redacted contract");
assert(validateEnvOwnerHandoffRedactedContractIndex < validateExplainGateReportIndex, "release env owner handoff redacted contract must run before EXPLAIN validation");
assert(validateEnvOwnerTemplatesContractIndex > validateEnvOwnerHandoffRedactedContractIndex, "release env owner templates contract must run after env owner handoff redacted contract");
assert(validateEnvOwnerTemplatesContractIndex < validateExplainGateReportIndex, "release env owner templates contract must run before EXPLAIN validation");
assert(validateEnvOwnerTemplatesMergeContractIndex > validateEnvOwnerTemplatesContractIndex, "release env owner templates merge contract must run after env owner templates contract");
assert(validateEnvOwnerTemplatesMergeContractIndex < validateExplainGateReportIndex, "release env owner templates merge contract must run before EXPLAIN validation");
assert(validateEnvBootstrapContractIndex > validateEnvOwnerTemplatesMergeContractIndex, "release env bootstrap contract must run after env owner templates merge contract");
assert(validateEnvBootstrapContractIndex < validateExplainGateReportIndex, "release env bootstrap contract must run before EXPLAIN validation");
assert(validateEnvReadinessGateContractIndex > validateEnvBootstrapContractIndex, "release env readiness gate contract must run after env bootstrap contract");
assert(validateEnvReadinessGateContractIndex < validateExplainGateReportIndex, "release env readiness gate contract must run before EXPLAIN validation");
assert(validateEnvAliasSyncContractIndex > validateEnvReadinessGateContractIndex, "release env alias sync contract must run after env readiness gate contract");
assert(validateEnvAliasSyncContractIndex < validateExplainGateReportIndex, "release env alias sync contract must run before EXPLAIN validation");
assert(validateEnvCanonicalLintContractIndex > validateEnvAliasSyncContractIndex, "release env canonical lint contract must run after env alias sync contract");
assert(validateEnvCanonicalLintContractIndex < validateExplainGateReportIndex, "release env canonical lint contract must run before EXPLAIN validation");
assert(validateEnvCanonicalMergeContractIndex > validateEnvCanonicalLintContractIndex, "release env canonical merge contract must run after env canonical lint contract");
assert(validateEnvCanonicalMergeContractIndex < validateExplainGateReportIndex, "release env canonical merge contract must run before EXPLAIN validation");
assert(validateEnvFileLintContractIndex > validateEnvCanonicalMergeContractIndex, "release env file lint contract must run after env canonical merge contract");
assert(validateEnvFileLintContractIndex < validateExplainGateReportIndex, "release env file lint contract must run before EXPLAIN validation");
assert(validateReleaseEvidenceGateContractIndex > validateEnvFileLintContractIndex, "release evidence gate contract must run after env file lint contract");
assert(validateReleaseEvidenceGateContractIndex < validateExplainGateReportIndex, "release evidence gate contract must run before EXPLAIN validation");
assert(validateExplainGateReportIndex > refreshFinalIndex, "EXPLAIN gate report validation must run after final packet refresh");
assert(validateExplainGateReportIndex < capturePreflightIndex, "EXPLAIN gate report validation must run before release preflight capture");
assert(capturePreflightIndex < validatePreflightCaptureIndex, "release preflight output must be validated after capture");
assert(validatePreflightCaptureIndex < uploadIndex, "release preflight capture must be validated before artifacts are uploaded");
assert(refreshManifestAfterPreflightIndex > validatePreflightCaptureIndex, "release manifest must refresh after preflight capture mutates contract reports");
assert(refreshManifestAfterPreflightIndex < uploadIndex, "release manifest must refresh before artifacts are uploaded");
assert(appendSummaryIndex > uploadIndex, "workflow summary should append after artifacts are uploaded");
assert(enforceIndex > appendSummaryIndex, "release enforcement must run after summaries are appended");
assert(workflowText.includes("release-final-go-no-go.md"), "workflow summary must include final go/no-go markdown");
assert(workflowText.includes("node scripts/ddd-release-unblock-brief.mjs"), "workflow must generate release unblock brief");
assert(workflowText.includes("node scripts/ddd-release-unblock-brief-contract.mjs"), "workflow must validate release unblock brief contract");
assert(workflowText.includes("release-unblock-brief.md"), "workflow summary must include release unblock brief markdown");
assert(workflowText.includes("No release unblock brief was generated."), "workflow summary must explain missing unblock brief");
assert(workflowText.includes("## Release Preflight"), "workflow summary must include a release preflight section");
assert(workflowText.includes("Command: `bash artifacts/ddd/release/release-preflight-gate.sh`"), "workflow summary must show the release preflight command");
assert(workflowText.includes("Strict command: `DDD_RELEASE_PREFLIGHT_ENFORCE=1 bash artifacts/ddd/release/release-preflight-gate.sh`"), "workflow summary must show the strict release preflight command");
assert(workflowText.includes("Report: `artifacts/ddd/release/release-preflight-report.json`"), "workflow summary must show the release preflight JSON report path");
assert(workflowText.includes("Capture contract: `artifacts/ddd/release/release-preflight-capture-contract.json`"), "workflow summary must show the release preflight capture contract path");
assert(workflowText.includes("Strict report: `artifacts/ddd/release/release-preflight-strict-report.json`"), "workflow summary must show the strict release preflight JSON report path");
assert(workflowText.includes("Strict output: `artifacts/ddd/release/release-preflight-strict-output.txt`"), "workflow summary must show the strict release preflight output path");
assert(workflowText.includes("release-preflight-strict-status.txt"), "workflow must persist strict release preflight status");
assert(workflowText.includes("### Release Preflight Output"), "workflow summary must include default release preflight output");
assert(workflowText.includes("release-preflight-output.txt"), "workflow must persist default release preflight output as an artifact");
assert(workflowText.includes("bash artifacts/ddd/release/release-preflight-gate.sh > artifacts/ddd/release/release-preflight-output.txt 2>&1"), "workflow must execute default release preflight into an artifact file");
assert(workflowText.includes("DDD_RELEASE_PREFLIGHT_REPORT=artifacts/ddd/release/release-preflight-strict-report.json"), "workflow must execute strict release preflight with a separate report path");
assert(workflowText.includes("artifacts/ddd/release/release-preflight-strict-output.txt 2>&1"), "workflow must persist strict release preflight output as an artifact");
assert(workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-output.txt > artifacts/ddd/release/release-preflight-output.redacted.tmp"), "workflow must redact default preflight output before upload");
assert(workflowText.includes("mv artifacts/ddd/release/release-preflight-output.redacted.tmp artifacts/ddd/release/release-preflight-output.txt"), "workflow must replace default preflight output with redacted content before upload");
assert(workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-strict-output.txt > artifacts/ddd/release/release-preflight-strict-output.redacted.tmp"), "workflow must redact strict preflight output before upload");
assert(workflowText.includes("mv artifacts/ddd/release/release-preflight-strict-output.redacted.tmp artifacts/ddd/release/release-preflight-strict-output.txt"), "workflow must replace strict preflight output with redacted content before upload");
assert(workflowText.includes("node scripts/ddd-release-preflight-capture-contract.mjs"), "workflow must validate release preflight capture files with the shared contract");
assert(
  /DDD_RELEASE_MANIFEST_CHECK_ENV=true\s*\\\s*\r?\n\s*node scripts\/ddd-release-evidence-manifest\.mjs/.test(workflowText),
  "workflow must refresh manifest provenance preflight before final pre-upload manifest refresh",
);
assert(workflowText.includes("DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false"), "workflow must refresh manifest after preflight capture without hiding blockers");
assert(workflowText.includes("DDD_RELEASE_MANIFEST_STRICT=\"${{ inputs.strict }}\""), "workflow must preserve strict/advisory manifest mode when refreshing after preflight capture");
assert(workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-output.txt >> \"$GITHUB_STEP_SUMMARY\""), "workflow summary must append redacted saved release preflight output");
assert(workflowText.includes("release-artifact-integrity.md"), "workflow summary must include release artifact integrity markdown");
assert(workflowText.includes("node scripts/ddd-release-artifact-integrity-contract.mjs"), "workflow must validate release artifact integrity packet");
assert(workflowText.includes("node scripts/ddd-release-artifact-integrity-gate-contract.mjs"), "workflow must validate release artifact integrity gate contract");
assert(workflowText.includes("node scripts/ddd-release-config-owner-input-reconciliation.mjs"), "workflow must validate release config owner input reconciliation");
assert(workflowText.includes("node scripts/ddd-release-owner-input-receipt.mjs"), "workflow must generate release owner input receipt");
assert(workflowText.includes("node scripts/ddd-release-owner-input-receipt-contract.mjs"), "workflow must validate release owner input receipt contract");
assert(
  workflowText.indexOf("Validate release artifact path leak contract after owner receipt") > workflowText.indexOf("Validate release owner input receipt contract"),
  "workflow must run path leak contract again after release owner input receipt exists",
);
assert(workflowText.includes("node scripts/ddd-release-cutover-contract.mjs"), "workflow must validate release cutover contract");
assert(workflowText.includes("node scripts/ddd-release-final-go-no-go-gate-contract.mjs"), "workflow must validate release final go no-go gate contract");
assert(workflowText.includes("node scripts/ddd-release-preflight-gate-contract.mjs"), "workflow must validate release preflight gate contract");
assert(workflowText.includes("node scripts/ddd-release-final-owner-queue-contract.mjs"), "workflow must validate release final owner queue contract");
assert(workflowText.includes("node scripts/ddd-release-closure-wave-receipts-contract.mjs"), "workflow must validate release closure wave receipts contract");
assert(workflowText.includes("node scripts/ddd-release-next-action-queue-contract.mjs"), "workflow must validate release next action queue contract");
assert(workflowText.includes("node scripts/ddd-release-execution-queue-contract.mjs"), "workflow must validate release execution queue contract");
assert(workflowText.includes("node scripts/ddd-release-command-catalog-contract.mjs"), "workflow must validate release command catalog contract");
assert(workflowText.includes("node scripts/ddd-release-owner-handoff-contract.mjs"), "workflow must validate release owner handoff contract");
assert(workflowText.includes("node scripts/ddd-release-env-owner-matrix-contract.mjs"), "workflow must validate release env owner matrix contract");
assert(workflowText.includes("node scripts/ddd-release-env-fill-priority-contract.mjs"), "workflow must validate release env fill priority contract");
assert(workflowText.includes("node scripts/ddd-release-env-canonical-fill-contract.mjs"), "workflow must validate release env canonical fill contract");
assert(workflowText.includes("node scripts/ddd-release-env-readiness-redacted-contract.mjs"), "workflow must validate release env readiness redacted contract");
assert(workflowText.includes("node scripts/ddd-release-env-owner-handoff-redacted-contract.mjs"), "workflow must validate release env owner handoff redacted contract");
assert(workflowText.includes("node scripts/ddd-release-env-owner-templates-contract.mjs"), "workflow must validate release env owner templates contract");
assert(workflowText.includes("node scripts/ddd-release-env-owner-templates-merge-contract.mjs"), "workflow must validate release env owner templates merge contract");
assert(workflowText.includes("node scripts/ddd-release-env-bootstrap-contract.mjs"), "workflow must validate release env bootstrap contract");
assert(workflowText.includes("node scripts/ddd-release-env-readiness-gate-contract.mjs"), "workflow must validate release env readiness gate contract");
assert(workflowText.includes("node scripts/ddd-release-env-alias-sync-contract.mjs"), "workflow must validate release env alias sync contract");
assert(workflowText.includes("node scripts/ddd-release-env-canonical-lint-contract.mjs"), "workflow must validate release env canonical lint contract");
assert(workflowText.includes("node scripts/ddd-release-env-canonical-merge-contract.mjs"), "workflow must validate release env canonical merge contract");
assert(workflowText.includes("node scripts/ddd-release-env-file-lint-contract.mjs"), "workflow must validate release env file lint contract");
assert(workflowText.includes("node scripts/ddd-release-evidence-gate-contract.mjs"), "workflow must validate release evidence gate contract");
assert(workflowText.includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1 bash artifacts/ddd/release/release-preflight-gate.sh"), "strict workflow must enforce release preflight gate");
assert(workflowText.includes("release-preflight-gate.sh"), "workflow must use the one-command release preflight entrypoint");
assert(workflowText.includes("Validate final owner queue run report"), "workflow must validate the final owner queue run report when present");
assert(workflowText.includes("node scripts/ddd-final-owner-queue-run-report-contract.mjs"), "workflow must run the final owner queue run report contract");
assert(workflowText.includes("Validate release execution run report"), "workflow must validate the release execution run report when present");
assert(workflowText.includes("node scripts/ddd-release-execution-run-report-contract.mjs"), "workflow must run the release execution run report contract");
assert(workflowText.includes("Validate EXPLAIN gate report"), "workflow must validate the EXPLAIN gate report when present");
assert(workflowText.includes("node scripts/ddd-explain-gate-report-contract.mjs"), "workflow must run the EXPLAIN gate report contract");
assert(workflowText.includes("node scripts/ddd-final-owner-queue-run-report-summary.mjs"), "workflow must append the final owner queue run report summary");
assert(workflowText.includes("node scripts/ddd-release-execution-run-report-summary.mjs"), "workflow must append the release execution run report summary");
assert(workflowText.includes("node scripts/ddd-release-next-action-run-report-summary.mjs"), "workflow must append the release next action run report summary");
assert(workflowText.includes("node scripts/ddd-release-redact-output.mjs < artifacts/ddd/release/release-preflight-output.txt >> \"$GITHUB_STEP_SUMMARY\""), "workflow must redact release preflight output before appending to GitHub Step Summary");
assert(ciWorkflowText.includes("node --check scripts/ddd-explain-gate.mjs"), "CI syntax checks must cover the EXPLAIN gate");
assert(ciWorkflowText.includes("node --check scripts/ddd-explain-gate.test.mjs"), "CI syntax checks must cover the EXPLAIN gate test");
assert(ciWorkflowText.includes("node scripts/ddd-explain-gate.test.mjs"), "CI tests must run the EXPLAIN gate test");
assert(ciWorkflowText.includes("node --check scripts/ddd-collect-explain.test.mjs"), "CI syntax checks must cover EXPLAIN collection contract test");
assert(ciWorkflowText.includes("node scripts/ddd-collect-explain.test.mjs"), "CI tests must run EXPLAIN collection contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-explain-gate-report-contract.mjs"), "CI syntax checks must cover the EXPLAIN gate report contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-explain-gate-report-contract.test.mjs"), "CI syntax checks must cover the EXPLAIN gate report contract test");
assert(ciWorkflowText.includes("node scripts/ddd-explain-gate-report-contract.test.mjs"), "CI tests must run the EXPLAIN gate report contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-preflight-capture-contract.mjs"), "CI syntax checks must cover release preflight capture contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-preflight-capture-contract.test.mjs"), "CI syntax checks must cover release preflight capture contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-preflight-capture-contract.test.mjs"), "CI tests must run release preflight capture contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-integrity-contract.mjs"), "CI syntax checks must cover release artifact integrity contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-integrity-contract.test.mjs"), "CI syntax checks must cover release artifact integrity contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-artifact-integrity-contract.test.mjs"), "CI tests must run release artifact integrity contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-integrity-gate-contract.mjs"), "CI syntax checks must cover release artifact integrity gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-integrity-gate-contract.test.mjs"), "CI syntax checks must cover release artifact integrity gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-artifact-integrity-gate-contract.test.mjs"), "CI tests must run release artifact integrity gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-path-leak-contract.mjs"), "CI syntax checks must cover release artifact path leak contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-artifact-path-leak-contract.test.mjs"), "CI syntax checks must cover release artifact path leak contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-artifact-path-leak-contract.test.mjs"), "CI tests must run release artifact path leak contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-config-owner-input-reconciliation.mjs"), "CI syntax checks must cover release config owner input reconciliation");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-config-owner-input-reconciliation.test.mjs"), "CI syntax checks must cover release config owner input reconciliation test");
assert(ciWorkflowText.includes("node scripts/ddd-release-config-owner-input-reconciliation.test.mjs"), "CI tests must run release config owner input reconciliation test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-owner-input-receipt.mjs"), "CI syntax checks must cover release owner input receipt");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-owner-input-receipt-contract.mjs"), "CI syntax checks must cover release owner input receipt contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-owner-input-receipt.test.mjs"), "CI syntax checks must cover release owner input receipt test");
assert(ciWorkflowText.includes("node scripts/ddd-release-owner-input-receipt.test.mjs"), "CI tests must run release owner input receipt test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-cutover-contract.mjs"), "CI syntax checks must cover release cutover contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-cutover-contract.test.mjs"), "CI syntax checks must cover release cutover contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-cutover-contract.test.mjs"), "CI tests must run release cutover contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-final-go-no-go-gate-contract.mjs"), "CI syntax checks must cover release final go no-go gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-final-go-no-go-gate-contract.test.mjs"), "CI syntax checks must cover release final go no-go gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-final-go-no-go-gate-contract.test.mjs"), "CI tests must run release final go no-go gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-preflight-gate-contract.mjs"), "CI syntax checks must cover release preflight gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-preflight-gate-contract.test.mjs"), "CI syntax checks must cover release preflight gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-preflight-gate-contract.test.mjs"), "CI tests must run release preflight gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-final-owner-queue-contract.mjs"), "CI syntax checks must cover release final owner queue contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-final-owner-queue-contract.test.mjs"), "CI syntax checks must cover release final owner queue contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-final-owner-queue-contract.test.mjs"), "CI tests must run release final owner queue contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-closure-wave-receipts-contract.mjs"), "CI syntax checks must cover release closure wave receipts contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-closure-wave-receipts-contract.test.mjs"), "CI syntax checks must cover release closure wave receipts contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-closure-wave-receipts-contract.test.mjs"), "CI tests must run release closure wave receipts contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-queue-contract.mjs"), "CI syntax checks must cover release next action queue contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-queue-contract.test.mjs"), "CI syntax checks must cover release next action queue contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-next-action-queue-contract.test.mjs"), "CI tests must run release next action queue contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-execution-queue-contract.mjs"), "CI syntax checks must cover release execution queue contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-execution-queue-contract.test.mjs"), "CI syntax checks must cover release execution queue contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-execution-queue-contract.test.mjs"), "CI tests must run release execution queue contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-command-catalog-contract.mjs"), "CI syntax checks must cover release command catalog contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-command-catalog-contract.test.mjs"), "CI syntax checks must cover release command catalog contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-command-catalog-contract.test.mjs"), "CI tests must run release command catalog contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-owner-handoff-contract.mjs"), "CI syntax checks must cover release owner handoff contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-owner-handoff-contract.test.mjs"), "CI syntax checks must cover release owner handoff contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-owner-handoff-contract.test.mjs"), "CI tests must run release owner handoff contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-matrix-contract.mjs"), "CI syntax checks must cover release env owner matrix contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-matrix-contract.test.mjs"), "CI syntax checks must cover release env owner matrix contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-matrix-contract.test.mjs"), "CI tests must run release env owner matrix contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-fill-priority-contract.mjs"), "CI syntax checks must cover release env fill priority contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-fill-priority-contract.test.mjs"), "CI syntax checks must cover release env fill priority contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-fill-priority-contract.test.mjs"), "CI tests must run release env fill priority contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-fill-contract.mjs"), "CI syntax checks must cover release env canonical fill contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-fill-contract.test.mjs"), "CI syntax checks must cover release env canonical fill contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-canonical-fill-contract.test.mjs"), "CI tests must run release env canonical fill contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-readiness-redacted-contract.mjs"), "CI syntax checks must cover release env readiness redacted contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-readiness-redacted-contract.test.mjs"), "CI syntax checks must cover release env readiness redacted contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-readiness-redacted-contract.test.mjs"), "CI tests must run release env readiness redacted contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-handoff-redacted-contract.mjs"), "CI syntax checks must cover release env owner handoff redacted contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-handoff-redacted-contract.test.mjs"), "CI syntax checks must cover release env owner handoff redacted contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-handoff-redacted-contract.test.mjs"), "CI tests must run release env owner handoff redacted contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-input-packet-contract.mjs"), "CI syntax checks must cover release env owner input packet contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-input-packet-contract.test.mjs"), "CI syntax checks must cover release env owner input packet contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-input-packet-contract.test.mjs"), "CI tests must run release env owner input packet contract test");
assert(workflowText.includes("node scripts/ddd-release-env-owner-input-packet-contract.mjs"), "release evidence workflow must validate release env owner input packet contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-contract.mjs"), "CI syntax checks must cover release env owner templates contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-contract.test.mjs"), "CI syntax checks must cover release env owner templates contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-templates-contract.test.mjs"), "CI tests must run release env owner templates contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-merge-contract.mjs"), "CI syntax checks must cover release env owner templates merge contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-merge-contract.test.mjs"), "CI syntax checks must cover release env owner templates merge contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-templates-merge-contract.test.mjs"), "CI tests must run release env owner templates merge contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-bootstrap-contract.mjs"), "CI syntax checks must cover release env bootstrap contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-bootstrap-contract.test.mjs"), "CI syntax checks must cover release env bootstrap contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-bootstrap-contract.test.mjs"), "CI tests must run release env bootstrap contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-readiness-gate-contract.mjs"), "CI syntax checks must cover release env readiness gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-readiness-gate-contract.test.mjs"), "CI syntax checks must cover release env readiness gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-readiness-gate-contract.test.mjs"), "CI tests must run release env readiness gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-alias-sync-contract.mjs"), "CI syntax checks must cover release env alias sync contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-alias-sync-contract.test.mjs"), "CI syntax checks must cover release env alias sync contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-alias-sync-contract.test.mjs"), "CI tests must run release env alias sync contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-lint-contract.mjs"), "CI syntax checks must cover release env canonical lint contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-lint-contract.test.mjs"), "CI syntax checks must cover release env canonical lint contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-canonical-lint-contract.test.mjs"), "CI tests must run release env canonical lint contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-merge-contract.mjs"), "CI syntax checks must cover release env canonical merge contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-merge-contract.test.mjs"), "CI syntax checks must cover release env canonical merge contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-canonical-merge-contract.test.mjs"), "CI tests must run release env canonical merge contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-file-lint-contract.mjs"), "CI syntax checks must cover release env file lint contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-file-lint-contract.test.mjs"), "CI syntax checks must cover release env file lint contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-file-lint-contract.test.mjs"), "CI tests must run release env file lint contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-merge.mjs"), "CI syntax checks must cover release env owner templates merge");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-owner-templates-merge.test.mjs"), "CI syntax checks must cover release env owner templates merge test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-owner-templates-merge.test.mjs"), "CI tests must run release env owner templates merge test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-alias-sync.mjs"), "CI syntax checks must cover release env alias sync");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-alias-sync.test.mjs"), "CI syntax checks must cover release env alias sync test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-alias-sync.test.mjs"), "CI tests must run release env alias sync test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-lint.mjs"), "CI syntax checks must cover release env canonical lint");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-lint.test.mjs"), "CI syntax checks must cover release env canonical lint test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-canonical-lint.test.mjs"), "CI tests must run release env canonical lint test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-merge.mjs"), "CI syntax checks must cover release env canonical merge");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-env-canonical-merge.test.mjs"), "CI syntax checks must cover release env canonical merge test");
assert(ciWorkflowText.includes("node scripts/ddd-release-env-canonical-merge.test.mjs"), "CI tests must run release env canonical merge test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-evidence-manifest.test.mjs"), "CI syntax checks must cover release evidence manifest test");
assert(ciWorkflowText.includes("node scripts/ddd-release-evidence-manifest.test.mjs"), "CI tests must run release evidence manifest test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-gate-contract.mjs"), "CI syntax checks must cover release gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-gate-contract.test.mjs"), "CI syntax checks must cover release gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-gate-contract.test.mjs"), "CI tests must run release gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-evidence-gate.test.mjs"), "CI syntax checks must cover release evidence gate test");
assert(ciWorkflowText.includes("node scripts/ddd-release-evidence-gate.test.mjs"), "CI tests must run release evidence gate test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-evidence-gate-contract.mjs"), "CI syntax checks must cover release evidence gate contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-evidence-gate-contract.test.mjs"), "CI syntax checks must cover release evidence gate contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-evidence-gate-contract.test.mjs"), "CI tests must run release evidence gate contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-unblock-brief-contract.mjs"), "CI syntax checks must cover release unblock brief contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-unblock-brief-contract.test.mjs"), "CI syntax checks must cover release unblock brief contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-unblock-brief-contract.test.mjs"), "CI tests must run release unblock brief contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-run-report-contract.mjs"), "CI syntax checks must cover release next action run report contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-run-report-contract.test.mjs"), "CI syntax checks must cover release next action run report contract test");
assert(ciWorkflowText.includes("node scripts/ddd-release-next-action-run-report-contract.test.mjs"), "CI tests must run release next action run report contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-run-report-summary.mjs"), "CI syntax checks must cover release next action run report summary");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-next-action-run-report-summary.test.mjs"), "CI syntax checks must cover release next action run report summary test");
assert(ciWorkflowText.includes("node scripts/ddd-release-next-action-run-report-summary.test.mjs"), "CI tests must run release next action run report summary test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-execution-run-report-summary.mjs"), "CI syntax checks must cover release execution run report summary");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-execution-run-report-summary.test.mjs"), "CI syntax checks must cover release execution run report summary test");
assert(ciWorkflowText.includes("node scripts/ddd-release-execution-run-report-summary.test.mjs"), "CI tests must run release execution run report summary test");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-redact-output.mjs"), "CI syntax checks must cover release output redaction");
assert(ciWorkflowText.includes("node --check scripts/ddd-release-redact-output.test.mjs"), "CI syntax checks must cover release output redaction test");
assert(ciWorkflowText.includes("node scripts/ddd-release-redact-output.test.mjs"), "CI tests must run release output redaction test");
assert(ciWorkflowText.includes("node --check scripts/ddd-ai-runtime-contract.mjs"), "CI syntax checks must cover AI runtime contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-ai-runtime-contract.test.mjs"), "CI syntax checks must cover AI runtime contract test");
assert(ciWorkflowText.includes("node scripts/ddd-ai-runtime-contract.test.mjs"), "CI tests must run AI runtime contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-final-owner-queue-run-report-contract.mjs"), "CI syntax checks must cover final owner queue run report contract");
assert(ciWorkflowText.includes("node --check scripts/ddd-final-owner-queue-run-report-contract.test.mjs"), "CI syntax checks must cover final owner queue run report contract test");
assert(ciWorkflowText.includes("node scripts/ddd-final-owner-queue-run-report-contract.test.mjs"), "CI tests must run final owner queue run report contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-final-owner-queue-run-report-summary.mjs"), "CI syntax checks must cover final owner queue run report summary");
assert(ciWorkflowText.includes("node --check scripts/ddd-final-owner-queue-run-report-summary.test.mjs"), "CI syntax checks must cover final owner queue run report summary test");
assert(ciWorkflowText.includes("node scripts/ddd-final-owner-queue-run-report-summary.test.mjs"), "CI tests must run final owner queue run report summary test");
assert(ciWorkflowText.includes("node --check scripts/ddd-physical-split-contract.test.mjs"), "CI syntax checks must cover physical split contract test");
assert(ciWorkflowText.includes("node --check scripts/ddd-physical-split-gate.test.mjs"), "CI syntax checks must cover physical split gate test");
assert(ciWorkflowText.includes("node scripts/ddd-physical-split-contract.test.mjs"), "CI tests must run physical split contract test");
assert(ciWorkflowText.includes("node scripts/ddd-physical-split-gate.test.mjs"), "CI tests must run physical split gate test");
assert(ciWorkflowText.includes("node --check scripts/ddd-runtime-readiness-smoke.test.mjs"), "CI syntax checks must cover runtime readiness smoke test");
assert(ciWorkflowText.includes("node scripts/ddd-runtime-readiness-smoke.test.mjs"), "CI tests must run runtime readiness smoke test");

console.log("[ddd-release-config-sync.test] ok");
