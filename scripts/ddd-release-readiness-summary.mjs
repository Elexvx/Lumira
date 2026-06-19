#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import os from "node:os";
import {
  compareAuthenticatedPerformance,
  requiredPerformanceBaselineEvidenceChecklist,
  validateAuthenticatedPerformanceBaselineMetadata,
  validateAuthenticatedPerformanceShape,
} from "./ddd-performance-evidence-contract.mjs";
import {
  requiredFileProcessingArtifacts,
  requiredFileProcessingTasks,
  validateFileProcessingArtifact,
  validatePaymentWebhookArtifact,
} from "./ddd-business-e2e-evidence-contract.mjs";
import {
  requiredJobSmokeEndpoints,
  validateJobE2eArtifact,
} from "./ddd-outbox-job-evidence-contract.mjs";
import {
  buildProductionEquivalenceEvidence,
} from "./ddd-release-evidence-utils.mjs";
import {
  releaseConfigGroups,
  validateReleaseConfigArtifact,
} from "./ddd-release-config-contract.mjs";
import {
  buildRollbackDrillSummary,
  rollbackContextRemediation,
} from "./ddd-rollback-drill-contract.mjs";
import {
  expectedRuntimeReadinessChecks,
  runtimeReadinessContexts,
  runtimeReadinessSuffixes,
  validateRuntimeReadinessArtifact,
} from "./ddd-runtime-readiness-contract.mjs";
import {
  missingRequiredExplainFiles,
  validateExplainArtifact,
} from "./ddd-explain-evidence-contract.mjs";
import { validateReleaseGateArtifact } from "./ddd-release-gate-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const homeDir = os.homedir();
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const outputDir = process.env.DDD_RELEASE_READINESS_DIR
  ? path.resolve(process.env.DDD_RELEASE_READINESS_DIR)
  : path.join(artifactRoot, "release");
const jsonOutput = path.join(outputDir, "readiness-summary.json");
const markdownOutput = path.join(outputDir, "readiness-summary.md");
const ownerActionRollupOutput = path.join(outputDir, "owner-action-rollup.json");
const ownerActionRollupCsvOutput = path.join(outputDir, "owner-action-rollup.csv");
const ownerActionRollupMarkdownOutput = path.join(outputDir, "owner-action-rollup.md");
const sourceActionRollupOutput = path.join(outputDir, "source-action-rollup.json");
const sourceActionRollupCsvOutput = path.join(outputDir, "source-action-rollup.csv");
const sourceActionRollupMarkdownOutput = path.join(outputDir, "source-action-rollup.md");
const releaseBlockerMapOutput = path.join(outputDir, "release-blocker-map.json");
const releaseBlockerMapCsvOutput = path.join(outputDir, "release-blocker-map.csv");
const releaseBlockerMapMarkdownOutput = path.join(outputDir, "release-blocker-map.md");
const releaseFastTrackOutput = path.join(outputDir, "release-fast-track.json");
const releaseCutoverChecklistCsvOutput = path.join(outputDir, "release-cutover-checklist.csv");
const releaseCutoverOwnerMatrixOutput = path.join(outputDir, "release-cutover-owner-matrix.json");
const releaseCutoverOwnerMatrixCsvOutput = path.join(outputDir, "release-cutover-owner-matrix.csv");
const releaseCutoverOwnerMatrixMarkdownOutput = path.join(outputDir, "release-cutover-owner-matrix.md");
const releaseSprintBoardOutput = path.join(outputDir, "release-sprint-board.json");
const releaseSprintBoardCsvOutput = path.join(outputDir, "release-sprint-board.csv");
const releaseSprintBoardMarkdownOutput = path.join(outputDir, "release-sprint-board.md");
const releaseCommandCatalogOutput = path.join(outputDir, "release-command-catalog.json");
const releaseCommandCatalogCsvOutput = path.join(outputDir, "release-command-catalog.csv");
const releaseCommandCatalogMarkdownOutput = path.join(outputDir, "release-command-catalog.md");
const releaseOwnerHandoffOutput = path.join(outputDir, "release-owner-handoff.json");
const releaseOwnerHandoffCsvOutput = path.join(outputDir, "release-owner-handoff.csv");
const releaseOwnerHandoffMarkdownOutput = path.join(outputDir, "release-owner-handoff.md");
const releaseOwnerReceiptsOutput = path.join(outputDir, "release-owner-receipts.json");
const releaseOwnerReceiptsCsvOutput = path.join(outputDir, "release-owner-receipts.csv");
const releaseOwnerReceiptsMarkdownOutput = path.join(outputDir, "release-owner-receipts.md");
const releaseNextActionQueueOutput = path.join(outputDir, "release-next-action-queue.json");
const releaseNextActionQueueCsvOutput = path.join(outputDir, "release-next-action-queue.csv");
const releaseNextActionQueueMarkdownOutput = path.join(outputDir, "release-next-action-queue.md");
const releaseNextActionCommandsOutput = path.join(outputDir, "release-next-action-commands.sh");
const releaseBlockerClosurePlanOutput = path.join(outputDir, "release-blocker-closure-plan.json");
const releaseBlockerClosurePlanCsvOutput = path.join(outputDir, "release-blocker-closure-plan.csv");
const releaseBlockerClosurePlanMarkdownOutput = path.join(outputDir, "release-blocker-closure-plan.md");
const releaseBlockerClosureCommandsOutput = path.join(outputDir, "release-blocker-closure-commands.sh");
const releaseClosureWaveEnvMatrixOutput = path.join(outputDir, "release-closure-wave-env-matrix.json");
const releaseClosureWaveEnvMatrixCsvOutput = path.join(outputDir, "release-closure-wave-env-matrix.csv");
const releaseClosureWaveEnvMatrixMarkdownOutput = path.join(outputDir, "release-closure-wave-env-matrix.md");
const releaseClosureWaveEnvTemplateOutput = path.join(outputDir, "release-closure-wave-env.template.env");
const releaseClosureWaveReceiptsOutput = path.join(outputDir, "release-closure-wave-receipts.json");
const releaseClosureWaveReceiptsCsvOutput = path.join(outputDir, "release-closure-wave-receipts.csv");
const releaseClosureWaveReceiptsMarkdownOutput = path.join(outputDir, "release-closure-wave-receipts.md");
const releaseClosureWaveBlockerMapOutput = path.join(outputDir, "release-closure-wave-blocker-map.json");
const releaseClosureWaveBlockerMapCsvOutput = path.join(outputDir, "release-closure-wave-blocker-map.csv");
const releaseClosureWaveBlockerMapMarkdownOutput = path.join(outputDir, "release-closure-wave-blocker-map.md");
const releasePerformanceBaselineClosureOutput = path.join(outputDir, "release-performance-baseline-closure.json");
const releasePerformanceBaselineClosureMarkdownOutput = path.join(outputDir, "release-performance-baseline-closure.md");
const releasePerformanceBaselineCommandsOutput = path.join(outputDir, "release-performance-baseline-commands.sh");
const releaseFinalGoNoGoOutput = path.join(outputDir, "release-final-go-no-go.json");
const releaseFinalGoNoGoCsvOutput = path.join(outputDir, "release-final-go-no-go.csv");
const releaseFinalGoNoGoMarkdownOutput = path.join(outputDir, "release-final-go-no-go.md");
const releaseFinalGoNoGoGateOutput = path.join(outputDir, "release-final-go-no-go-gate.sh");
const releasePreflightGateOutput = path.join(outputDir, "release-preflight-gate.sh");
const releaseFinalOwnerQueueOutput = path.join(outputDir, "release-final-owner-queue.json");
const releaseFinalOwnerQueueCsvOutput = path.join(outputDir, "release-final-owner-queue.csv");
const releaseFinalOwnerQueueMarkdownOutput = path.join(outputDir, "release-final-owner-queue.md");
const releaseFinalOwnerQueueCommandsOutput = path.join(outputDir, "release-final-owner-queue-commands.sh");
const releaseFinalOwnerQueueEnvTemplateOutput = path.join(outputDir, "release-final-owner-queue-env.template.env");
const releaseFinalOwnerQueueEnvInitOutput = path.join(outputDir, "release-final-owner-queue-env-init.sh");
const releaseEnvBootstrapOutput = path.join(outputDir, "release-env-bootstrap.sh");
const releaseFastTrackMarkdownOutput = path.join(outputDir, "release-fast-track.md");
const releaseActionPriorityOutput = path.join(outputDir, "release-action-priority.json");
const releaseActionPriorityCsvOutput = path.join(outputDir, "release-action-priority.csv");
const releaseActionPriorityMarkdownOutput = path.join(outputDir, "release-action-priority.md");
const releaseActionBatchesOutput = path.join(outputDir, "release-action-batches.json");
const releaseActionBatchesCsvOutput = path.join(outputDir, "release-action-batches.csv");
const releaseActionBatchesMarkdownOutput = path.join(outputDir, "release-action-batches.md");
const releaseActionDependencyGraphOutput = path.join(outputDir, "release-action-dependency-graph.json");
const releaseActionDependencyGraphMarkdownOutput = path.join(outputDir, "release-action-dependency-graph.md");
const finalReadinessSummaryCommand = "node scripts/ddd-release-readiness-summary.mjs";
const finalGoNoGoEnforceCommand = "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh";
const releaseEnvSafeDefaultsCommand = "node scripts/ddd-release-env-safe-defaults.mjs";
const releaseProvenanceDefaultsCommand = "node scripts/ddd-release-provenance-defaults.mjs";

function portableDisplayPath(value) {
  if (!value || typeof value !== "string") return value;
  const normalized = path.normalize(value);
  if (path.isAbsolute(value)) {
    if (normalized === repoRoot) return ".";
    if (normalized.startsWith(`${repoRoot}${path.sep}`)) {
      return path.relative(repoRoot, normalized);
    }
    if (homeDir && homeDir !== "/" && normalized === homeDir) return "~";
    if (homeDir && homeDir !== "/" && normalized.startsWith(`${homeDir}${path.sep}`)) {
      return `~/${path.relative(homeDir, normalized)}`;
    }
  }
  return value;
}
const releaseExecutionQueueOutput = path.join(outputDir, "release-execution-queue.json");
const releaseExecutionQueueCsvOutput = path.join(outputDir, "release-execution-queue.csv");
const releaseExecutionQueueMarkdownOutput = path.join(outputDir, "release-execution-queue.md");
const releaseExecutionCommandsOutput = path.join(outputDir, "release-execution-commands.sh");
const releaseMissingEnvOutput = path.join(outputDir, "release-env-missing.json");
const releaseEnvOwnerMatrixOutput = path.join(outputDir, "release-env-owner-matrix.json");
const releaseEnvOwnerMatrixCsvOutput = path.join(outputDir, "release-env-owner-matrix.csv");
const releaseEnvOwnerMatrixMarkdownOutput = path.join(outputDir, "release-env-owner-matrix.md");
const releaseEnvFillPriorityOutput = path.join(outputDir, "release-env-fill-priority.json");
const releaseEnvFillPriorityCsvOutput = path.join(outputDir, "release-env-fill-priority.csv");
const releaseEnvFillPriorityMarkdownOutput = path.join(outputDir, "release-env-fill-priority.md");
const releaseEnvCanonicalFillOutput = path.join(outputDir, "release-env-canonical-fill.json");
const releaseEnvCanonicalFillCsvOutput = path.join(outputDir, "release-env-canonical-fill.csv");
const releaseEnvCanonicalFillMarkdownOutput = path.join(outputDir, "release-env-canonical-fill.md");
const releaseEnvCanonicalFillTemplateOutput = path.join(outputDir, "release-env-canonical-fill.template.env");
const releaseEnvReadinessRedactedOutput = path.join(outputDir, "release-env-readiness-redacted.json");
const releaseEnvReadinessRedactedCsvOutput = path.join(outputDir, "release-env-readiness-redacted.csv");
const releaseEnvReadinessRedactedMarkdownOutput = path.join(outputDir, "release-env-readiness-redacted.md");
const releaseEnvReadinessGateOutput = path.join(outputDir, "release-env-readiness-gate.sh");
const releaseEnvOwnerHandoffRedactedOutput = path.join(outputDir, "release-env-owner-handoff-redacted.json");
const releaseEnvOwnerHandoffRedactedCsvOutput = path.join(outputDir, "release-env-owner-handoff-redacted.csv");
const releaseEnvOwnerHandoffRedactedMarkdownOutput = path.join(outputDir, "release-env-owner-handoff-redacted.md");
const releaseEnvOwnerHandoffRedactedDir = path.join(outputDir, "release-env-owner-handoff-redacted");
const releaseEnvOwnerInputPacketOutput = path.join(outputDir, "release-env-owner-input-packet.json");
const releaseEnvOwnerInputPacketCsvOutput = path.join(outputDir, "release-env-owner-input-packet.csv");
const releaseEnvOwnerInputPacketMarkdownOutput = path.join(outputDir, "release-env-owner-input-packet.md");
const releaseEnvOwnerInputPacketDir = path.join(outputDir, "release-env-owner-input-packet");
const releaseConfigOwnerInputReconciliationOutput = path.join(outputDir, "release-config-owner-input-reconciliation.json");
const releaseOwnerInputReceiptOutput = path.join(outputDir, "release-owner-input-receipt.json");
const releaseOwnerInputReceiptCsvOutput = path.join(outputDir, "release-owner-input-receipt.csv");
const releaseOwnerInputReceiptItemsCsvOutput = path.join(outputDir, "release-owner-input-receipt-items.csv");
const releaseOwnerInputReceiptItemsMarkdownOutput = path.join(outputDir, "release-owner-input-receipt-items.md");
const releaseOwnerInputReceiptItemsDir = path.join(outputDir, "release-owner-input-receipt-items");
const releaseOwnerInputReceiptMarkdownOutput = path.join(outputDir, "release-owner-input-receipt.md");
const releaseEnvOwnerHandoffOutput = path.join(outputDir, "release-env-owner-handoff.json");
const releaseEnvOwnerHandoffCsvOutput = path.join(outputDir, "release-env-owner-handoff.csv");
const releaseEnvOwnerHandoffMarkdownOutput = path.join(outputDir, "release-env-owner-handoff.md");
const releaseEnvOwnerTemplatesOutput = path.join(outputDir, "release-env-owner-templates.json");
const releaseEnvOwnerTemplatesMarkdownOutput = path.join(outputDir, "release-env-owner-templates.md");
const releaseEnvOwnerTemplatesDir = path.join(outputDir, "release-env-owner-templates");
const releaseMissingEnvTemplateOutput = path.join(outputDir, "release-env-missing.template.env");
const releaseArtifactIntegrityOutput = path.join(outputDir, "release-artifact-integrity.json");
const releaseArtifactIntegrityMarkdownOutput = path.join(outputDir, "release-artifact-integrity.md");
const releaseArtifactIntegrityGateOutput = path.join(outputDir, "release-artifact-integrity-gate.sh");

const finalOwnerQueueSafeEnvDefaults = new Map([
  ["DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE", "true"],
  ["DDD_AI_EXPECT_PROVIDER_REMOTE", "true"],
  ["DDD_DOCKER_BUILD_STRICT", "true"],
  ["DDD_DOCKER_COMMAND", "docker"],
  ["DDD_EVIDENCE_ENVIRONMENT", "production-equivalent"],
  ["DDD_EXPLAIN_DIR", "tmp/ddd-explain"],
  ["DDD_EXPLAIN_ENVIRONMENT", "production-equivalent"],
  ["DDD_EXPLAIN_STRICT", "true"],
  ["DDD_FRONTEND_EXPECT_DEPLOYED", "true"],
  ["DDD_MIGRATION_ENVIRONMENT", "production-equivalent"],
  ["DDD_RELEASE_EVIDENCE_STRICT", "true"],
  ["DDD_RELEASE_MANIFEST_STRICT", "true"],
  ["DDD_ROLLBACK_DRILL_DEFERRAL_FILE", "artifacts/ddd/rollback/rollback-deferral.json"],
  ["DDD_ROLLBACK_DRILL_FILE", "artifacts/ddd/rollback/rollback-drill.json"],
  ["DDD_ROLLBACK_DRILL_STRICT", "true"],
  ["LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED", "true"],
  ["LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED", "true"],
  ["LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED", "true"],
  ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED", "true"],
  ["LUMIRA_AI_PROVIDER_ENABLED", "true"],
  ["MYSQL_CLI", "mysql"],
]);

const dockerBuildEvidenceEnvKeys = [
  "DDD_DOCKER_COMMAND",
  "DDD_DOCKER_BUILD_STRICT",
];

const dockerBuildMirrorHint = "`DDD_DOCKER_MAVEN_IMAGE`, `DDD_DOCKER_JRE_IMAGE`, `DDD_DOCKER_NODE_IMAGE`, `DDD_DOCKER_NGINX_IMAGE`, and `DDD_DOCKER_BUILD_RETRIES`";

const dockerExistingImageInspectExampleCommand = [
  "DDD_DOCKER_BUILD_STRICT=true",
  "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url>",
  "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate>",
  "DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate>",
  "node scripts/ddd-docker-build-evidence.mjs",
].join(" ");

const prioritySourceOrder = [
  "release-env-lint",
  "release-config",
  "orchestrator",
  "docker",
  "runtime-readiness",
  "migration",
  "manifest",
  "authenticated-performance",
  "ai-runtime",
  "frontend-smoke",
  "business-e2e",
  "rollback",
  "explain",
];

const prioritySourceTiers = {
  "release-env-lint": "P0",
  "release-config": "P0",
  orchestrator: "P3",
  docker: "P0",
  "runtime-readiness": "P0",
  migration: "P0",
  manifest: "P0",
  "authenticated-performance": "P0",
  "ai-runtime": "P1",
  "frontend-smoke": "P1",
  "business-e2e": "P1",
  rollback: "P1",
  explain: "P2",
};

const fastTrackSourcePolicies = {
  "release-env-lint": {
    lane: "environment",
    safetyClass: "non-waivable",
    acceleration: "Provide a completed DDD_RELEASE_ENV_FILE, then run env-check-only before expensive evidence collection.",
  },
  "release-config": {
    lane: "environment",
    safetyClass: "non-waivable",
    acceleration: "Resolve config matrix blockers from the same completed release env file.",
  },
  docker: {
    lane: "deployable-image",
    safetyClass: "required-before-cutover",
    acceleration: "Run image build/inspect in CI or a host with Docker daemon available.",
  },
  "runtime-readiness": {
    lane: "production-equivalence",
    safetyClass: "non-waivable",
    acceleration: "Point runtime smoke at the HTTPS non-local backend and capture owner readiness/health/metrics.",
  },
  migration: {
    lane: "data-safety",
    safetyClass: "non-waivable",
    acceleration: "Run fresh database and previous-schema upgrade drills in parallel with runtime smoke.",
  },
  manifest: {
    lane: "evidence-integrity",
    safetyClass: "required-before-cutover",
    acceleration: "Regenerate the manifest after all prerequisite artifacts exist.",
  },
  "authenticated-performance": {
    lane: "performance",
    safetyClass: "non-waivable",
    acceleration: "Run authenticated performance against production-equivalent HTTPS and promote the accepted baseline.",
  },
  "ai-runtime": {
    lane: "runtime-acceptance",
    safetyClass: "non-waivable",
    acceleration: "Use remote provider and owner gateway settings in the same production-equivalent environment.",
  },
  "frontend-smoke": {
    lane: "frontend-acceptance",
    safetyClass: "required-before-cutover",
    acceleration: "Run deployed Playwright smoke and evidence conversion from the deployed frontend URL.",
  },
  "business-e2e": {
    lane: "business-acceptance",
    safetyClass: "non-waivable",
    acceleration: "Run File, Job, and Payment E2E owner checks in parallel once runtime env is ready.",
  },
  rollback: {
    lane: "rollback-safety",
    safetyClass: "non-waivable",
    acceleration: "Use PASS drills where possible; use DEFERRED only with approved, unexpired risk acceptance.",
  },
  explain: {
    lane: "database-performance",
    safetyClass: "non-waivable",
    acceleration: "Collect fresh EXPLAIN artifacts from production-equivalent MySQL after migrations are applied.",
  },
  orchestrator: {
    lane: "final-verification",
    safetyClass: "final-recheck",
    acceleration: "Run strict orchestrator only after P0/P1/P2 evidence batches are clean.",
  },
};

const releaseEnvTemplateControlKeys = new Set([
  "DDD_RELEASE_ENV_FILE",
]);
const releaseConfigCanonicalEnvKeyByAlias = buildReleaseConfigCanonicalEnvKeyByAlias();
const releaseConfigEnvAliasesByCanonical = buildReleaseConfigEnvAliasesByCanonical();

function buildReleaseConfigCanonicalEnvKeyByAlias() {
  const canonicalEnvKeyByAlias = new Map();
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements) {
      const canonicalKey = requirement.keys?.[0];
      for (const envKey of (requirement.keys || []).filter(Boolean)) {
        if (!canonicalEnvKeyByAlias.has(envKey)) {
          canonicalEnvKeyByAlias.set(envKey, canonicalKey || envKey);
        }
      }
    }
  }
  return canonicalEnvKeyByAlias;
}

function buildReleaseConfigEnvAliasesByCanonical() {
  const aliasesByCanonical = new Map();
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements) {
      const keys = (requirement.keys || []).filter(Boolean);
      const canonicalKey = keys[0];
      if (!canonicalKey) {
        continue;
      }
      if (!aliasesByCanonical.has(canonicalKey)) {
        aliasesByCanonical.set(canonicalKey, []);
      }
      const aliases = aliasesByCanonical.get(canonicalKey);
      for (const envKey of keys) {
        if (!aliases.includes(envKey)) {
          aliases.push(envKey);
        }
      }
    }
  }
  return aliasesByCanonical;
}

function canonicalReleaseEnvTemplateKey(envKey) {
  if (releaseEnvTemplateControlKeys.has(envKey)) {
    return envKey;
  }
  return releaseConfigCanonicalEnvKeyByAlias.get(envKey) || envKey;
}

function readJson(relativePath) {
  const file = path.join(artifactRoot, relativePath);
  if (!fs.existsSync(file)) {
    return { file, relativePath, missing: true, data: null, modifiedAt: null };
  }
  const stat = fs.statSync(file);
  const modifiedAt = stat.mtime.toISOString();
  try {
    return { file, relativePath, missing: false, data: JSON.parse(fs.readFileSync(file, "utf8")), modifiedAt };
  } catch (error) {
    return { file, relativePath, missing: false, invalid: error.message, data: null, modifiedAt };
  }
}

function stableGeneratedAtForFile(file, body) {
  if (!fs.existsSync(file)) {
    return new Date().toISOString();
  }
  try {
    const existing = JSON.parse(fs.readFileSync(file, "utf8"));
    const { generatedAt, ...existingBody } = existing && typeof existing === "object" && !Array.isArray(existing)
      ? existing
      : {};
    if (JSON.stringify(existingBody) === JSON.stringify(body)) {
      return generatedAt || new Date().toISOString();
    }
  } catch {
    // Regenerate the timestamp when the previous file is unreadable.
  }
  return new Date().toISOString();
}

function portablePath(filePath) {
  if (!filePath) {
    return null;
  }
  const absolutePath = path.resolve(filePath);
  return absolutePath === repoRoot || absolutePath.startsWith(`${repoRoot}${path.sep}`)
    ? path.relative(repoRoot, absolutePath) || "."
    : filePath;
}

function portableValue(value) {
  if (typeof value === "string") {
    return value.split(`${repoRoot}${path.sep}`).join("");
  }
  if (Array.isArray(value)) {
    return value.map((item) => portableValue(item));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, portableValue(item)]));
  }
  return value;
}

function redactReleaseEnvCommandForDisplay(value) {
  return String(value || "")
    .replace(/(DDD_RELEASE_ENV_FILE=)(?:"[^"`\s|]+"|'[^'`\s|]+'|[^\s`|]+)/g, "$1<release-env-file>")
    .replace(/(^|\s)(?:[^\s`|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=\s|`|\)|,|$)/g, "$1<release-env-file>");
}

function redactReleaseEnvValueForDisplay(value) {
  if (typeof value === "string") {
    return redactReleaseEnvCommandForDisplay(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => redactReleaseEnvValueForDisplay(item));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, redactReleaseEnvValueForDisplay(item)]),
    );
  }
  return value;
}

function redactedDisplayCommands(commands = []) {
  return orderedUniqueStrings((commands || []).map(redactReleaseEnvCommandForDisplay));
}

function releaseDisplayActionItem(item = {}) {
  return {
    ...item,
    reason: redactReleaseEnvCommandForDisplay(item.reason || ""),
    detail: redactReleaseEnvCommandForDisplay(item.detail || ""),
    action: redactReleaseEnvCommandForDisplay(item.action || ""),
  };
}

function releaseDisplayBatch(batch = {}) {
  return {
    ...batch,
    commands: redactedDisplayCommands(batch.commands || []),
    items: (batch.items || []).map(releaseDisplayActionItem),
  };
}

function jsonPortable(value) {
  return `${JSON.stringify(portableValue(value), null, 2)}\n`;
}

function artifactInputSummary(read, { statusPath = "status" } = {}) {
  const data = read?.data || null;
  return {
    file: portablePath(read?.file),
    relativePath: read?.relativePath || null,
    present: read?.missing !== true && !read?.invalid,
    missing: read?.missing === true,
    invalid: read?.invalid || null,
    modifiedAt: read?.modifiedAt || null,
    generatedAt: data?.generatedAt || data?.checkedAt || null,
    status: statusPath === "summary.status"
      ? data?.summary?.status || null
      : statusPath === "reportStatus"
        ? data?.reportStatus || null
        : data?.status || null,
    blockers: Array.isArray(data?.blockers)
      ? data.blockers.length
      : Number.isInteger(data?.summary?.blockers)
        ? data.summary.blockers
        : Number.isInteger(data?.blockerCount) ? data.blockerCount : null,
    warnings: Array.isArray(data?.warnings) ? data.warnings.length : (data?.summary?.warnings ?? null),
  };
}

function releaseGateBlockerEntries(gateData) {
  const blockers = Array.isArray(gateData?.blockers) ? gateData.blockers : [];
  const details = Array.isArray(gateData?.blockerDetails) ? gateData.blockerDetails : [];
  return blockers.map((blocker, index) => {
    const detail = details[index] || {};
    const check = typeof detail.check === "string" && detail.check.trim()
      ? detail.check.trim()
      : String(blocker).split(":")[0]?.trim() || null;
    const message = typeof detail.detail === "string" && detail.detail.trim()
      ? detail.detail.trim()
      : String(blocker).includes(":")
        ? String(blocker).slice(String(blocker).indexOf(":") + 1).trim()
        : String(blocker);
    return {
      blocker,
      check,
      detail: message,
      file: detail.file ?? null,
      structured: Boolean(detail.check || detail.detail || detail.file),
    };
  });
}

function classify(blockerEntry) {
  const blocker = typeof blockerEntry === "string" ? blockerEntry : blockerEntry?.blocker;
  const check = typeof blockerEntry === "string" ? "" : (blockerEntry?.check || "");
  const detail = typeof blockerEntry === "string" ? "" : (blockerEntry?.detail || "");
  const text = `${check}: ${detail} ${String(blocker)}`;
  if (text.includes("runtime-readiness-freshness")) {
    return {
      category: "runtime-freshness",
      owner: "release-infra",
      action: "Regenerate runtime readiness within the release freshness window against the production-equivalent HTTPS backend, then rerun the strict release gate.",
    };
  }
  if (text.includes("authenticated-performance-freshness")) {
    return {
      category: "performance-freshness",
      owner: "release-performance",
      action: "Regenerate authenticated performance evidence within the release freshness window, then rerun baseline comparison and promotion.",
    };
  }
  if (text.includes("file-processing-freshness")) {
    return {
      category: "business-e2e-freshness",
      owner: "file-owner",
      action: "Regenerate File processing E2E evidence within the release freshness window against the production-equivalent environment.",
    };
  }
  if (text.includes("payment-webhook-freshness")) {
    return {
      category: "business-e2e-freshness",
      owner: "payment-owner",
      action: "Regenerate Payment webhook E2E evidence within the release freshness window against the production-equivalent environment.",
    };
  }
  if (text.includes("job-e2e-freshness")) {
    return {
      category: "business-e2e-freshness",
      owner: "job-owner",
      action: "Regenerate Job E2E evidence within the release freshness window against the production-equivalent environment.",
    };
  }
  if (text.includes("migration-evidence-runtime")) {
    return {
      category: "migration-runtime-evidence",
      owner: "database",
      action: "Regenerate migration evidence with `DDD_MIGRATION_ENVIRONMENT`, `DDD_MIGRATION_OPERATOR`, `DDD_MIGRATION_COMPLETED_AT`, `DDD_MIGRATION_FRESH_DB_EVIDENCE`, and `DDD_MIGRATION_UPGRADE_DB_EVIDENCE` after both Flyway drills finish.",
    };
  }
  if (text.includes("migration-evidence: status=FAIL")) {
    return {
      category: "migration",
      owner: "database",
      action: "Run both fresh database and previous-schema upgrade Flyway drills with concrete evidence, then regenerate `artifacts/ddd/migration/migration-evidence.json`.",
    };
  }
  if (text.includes("explain-evidence-metadata")) {
    return {
      category: "explain-metadata",
      owner: "database",
      action: "Regenerate EXPLAIN artifacts with `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR` by running `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.",
    };
  }
  if (text.includes("explain-evidence")) {
    return {
      category: "explain-plan",
      owner: "database",
      action: "Collect production-equivalent MySQL `EXPLAIN FORMAT=JSON` artifacts for all required hot paths and ensure no full scans or missing hotspot indexes remain.",
    };
  }
  if (text.includes("migration-evidence-fresh-db")) {
    return {
      category: "migration-fresh-db",
      owner: "database",
      action: "Run the full Flyway migration against an empty production-equivalent database and save schema history/log evidence before setting `DDD_MIGRATION_FRESH_DB_VALIDATED=true`.",
    };
  }
  if (text.includes("migration-evidence-upgrade-db")) {
    return {
      category: "migration-upgrade-db",
      owner: "database",
      action: "Run the Flyway upgrade drill against a copy of the previous production schema and save schema history/log evidence before setting `DDD_MIGRATION_UPGRADE_DB_VALIDATED=true`.",
    };
  }
  if (text.includes("rollback-drill: environment")
    || text.includes("rollback-drill: releaseVersion")
    || text.includes("rollback-drill: operator")) {
    return {
      category: "rollback-metadata",
      owner: "release-owner",
      action: "Initialize or update rollback evidence with `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`, then run `node scripts/ddd-rollback-drill-evidence.mjs`.",
    };
  }
  if (text.includes("rollback-drill:") && text.includes("status must be PASS or DEFERRED")) {
    const context = rollbackContextFromBlocker(text);
    if (context) {
      const remediation = rollbackContextRemediation(context);
      return {
        category: "rollback-context-drills",
        owner: remediation.owner,
        action: remediation.action,
      };
    }
    return {
      category: "rollback-context-drills",
      owner: "release-owner",
      action: "For each listed bounded context, replace TODO/MISSING with real `PASS` drill evidence or justified `DEFERRED` risk acceptance in `artifacts/ddd/rollback/rollback-drill.json`.",
    };
  }
  if (text.includes("docker-build-evidence-provenance")) {
    return {
      category: "docker-provenance",
      owner: "release-infra",
      action: "Regenerate Docker image evidence with `DDD_DOCKER_BUILD_STRICT=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`.",
    };
  }
  if (text.includes("outbox-replay-dead-letter-provenance")) {
    return {
      category: "outbox-provenance",
      owner: "platform-events",
      action: "Regenerate Outbox replay/dead-letter evidence with `DDD_OUTBOX_SMOKE_STRICT=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`.",
    };
  }
  if (text.includes("outbox-replay-dead-letter")) {
    return {
      category: "outbox-state-machine",
      owner: "platform-events",
      action: "Run `DDD_OUTBOX_SMOKE_STRICT=true node scripts/ddd-outbox-replay-dead-letter-smoke.mjs` after exporting real provenance, then confirm every owner relay report is present with zero failures and errors.",
    };
  }
  if (text.includes("backend-build-evidence-provenance")
    || text.includes("backend-test-evidence-provenance")
    || text.includes("frontend-build-evidence-provenance")
    || text.includes("frontend-static-evidence-provenance")) {
    return {
      category: "build-test-provenance",
      owner: "release-infra",
      action: "Regenerate build/test evidence with `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; use the owner-specific strict flag when running a single script.",
    };
  }
  if (text.includes("docker") || text.includes("Docker")) {
    return {
      category: "docker",
      owner: "release-infra",
      action: "Start Docker daemon or run `node scripts/ddd-docker-build-evidence.mjs` in CI with Docker Buildx available.",
    };
  }
  if (text.includes("baseline")) {
    return {
      category: "performance-baseline",
      owner: "release-performance",
      action: "Run authenticated performance smoke against production-equivalent URL, then promote the accepted actual with `scripts/ddd-promote-performance-baseline.mjs`.",
    };
  }
  if (text.includes("rollback")) {
    return {
      category: "rollback-drill",
      owner: "release-owner",
      action: "Run `node scripts/ddd-init-rollback-drill.mjs`, fill real PASS/DEFERRED evidence for every context, then run `node scripts/ddd-rollback-drill-evidence.mjs`.",
    };
  }
  if (text.includes("release-env-lint")) {
    return {
      category: "configuration",
      owner: "release-infra",
      action: "Replace all release env placeholders in `DDD_RELEASE_ENV_FILE`, run `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`, then regenerate config evidence with `node scripts/ddd-release-config-evidence.mjs`.",
    };
  }
  if (text.includes("release-config")) {
    return {
      category: "configuration",
      owner: "release-infra",
      action: "Generate production-equivalent config evidence with `DDD_RELEASE_ENV_FILE=.env.release DDD_RELEASE_CONFIG_STRICT=true node scripts/ddd-release-config-evidence.mjs`.",
    };
  }
  if (text.includes("frontend-smoke")) {
    return {
      category: "frontend-smoke",
      owner: "frontend",
      action: "Run deployed frontend smoke with HTTPS `PLAYWRIGHT_BASE_URL`, `DDD_FRONTEND_EXPECT_DEPLOYED=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`; then convert it with `node scripts/ddd-frontend-smoke-evidence.mjs`.",
    };
  }
  if (text.includes("ai-runtime")) {
    return {
      category: "ai-runtime",
      owner: "ai",
      action: "Run `DDD_AI_EXPECT_PROVIDER_REMOTE=true DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true node scripts/ddd-ai-runtime-drill.mjs` against production-equivalent AI runtime.",
    };
  }
  if (text.includes("authenticated-performance-production-equivalence")
    || text.includes("authenticated-performance-shape")) {
    return {
      category: "production-equivalent-runtime",
      owner: "release-performance",
      action: "Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL, then rerun baseline comparison or promotion.",
    };
  }
  if (text.includes("file-processing-production-equivalence")
    || text.includes("file-processing-e2e") && text.includes("productionEquivalence")) {
    return {
      category: "production-equivalent-runtime",
      owner: "file-owner",
      action: "Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend URL with real File storage and job token evidence.",
    };
  }
  if (text.includes("payment-webhook-production-equivalence")
    || text.includes("payment-webhook-e2e") && text.includes("productionEquivalence")) {
    return {
      category: "production-equivalent-runtime",
      owner: "payment-owner",
      action: "Regenerate Payment webhook E2E smoke against an HTTPS non-local production-equivalent webhook URL with provider sandbox or deployment evidence.",
    };
  }
  if (text.includes("job-e2e-production-equivalence")
    || text.includes("job-e2e-smoke") && text.includes("productionEquivalence")) {
    return {
      category: "production-equivalent-runtime",
      owner: "job-owner",
      action: "Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token.",
    };
  }
  if (text.includes("migration")) {
    return {
      category: "migration",
      owner: "database",
      action: "Run fresh database and old database upgrade Flyway drills, then regenerate migration evidence with fresh/upgrade flags.",
    };
  }
  if (text.includes("release-evidence-manifest-provenance")
    || text.includes("artifact provenance issues")) {
    return {
      category: "manifest-provenance",
      owner: "release-owner",
      action: "Regenerate the manifest with `DDD_RELEASE_MANIFEST_STRICT=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR` after regenerating provenance-bearing artifacts.",
    };
  }
  if (text.includes("release-evidence-orchestrator-preflight-backend-runtime-base-url")) {
    return {
      category: "production-equivalent-runtime",
      owner: "release-infra",
      action: "Set `LUMIRA_BASE_URL`, `DEPLOY_CHECK_BASE_URL`, or `BASE_URL` to an HTTPS non-local backend URL before running the strict release orchestrator.",
    };
  }
  if (text.includes("release-evidence-orchestrator-preflight-frontend-runtime-base-url")
    || text.includes("release-evidence-orchestrator-preflight-frontend-deployed-expectation")) {
    return {
      category: "frontend-smoke",
      owner: "frontend",
      action: "Set deployed HTTPS `PLAYWRIGHT_BASE_URL` and `DDD_FRONTEND_EXPECT_DEPLOYED=true`, then rerun the strict release orchestrator/frontend smoke.",
    };
  }
  if (text.includes("release-evidence-orchestrator-preflight-ai-provider-remote-expectation")
    || text.includes("release-evidence-orchestrator-preflight-ai-owner-gateway-remote-expectation")
    || text.includes("release-evidence-orchestrator-preflight-ai-runtime-base-url")) {
    return {
      category: "ai-runtime",
      owner: "ai",
      action: "Set production-equivalent AI base URL and remote provider/owner-gateway expectations, then rerun `scripts/ddd-ai-runtime-drill.mjs` or the strict orchestrator.",
    };
  }
  if (text.includes("release-evidence-orchestrator")) {
    return {
      category: "orchestrator",
      owner: "release-owner",
      action: "Run `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict` with real provenance and keep the generated `artifacts/ddd/release/orchestrator-report.json` with the release evidence bundle.",
    };
  }
  if (text.includes("physical-split-readiness-provenance")) {
    return {
      category: "split-provenance",
      owner: "architecture",
      action: "Regenerate physical split readiness with `DDD_SPLIT_STRICT=true`, `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR`.",
    };
  }
  if (text.includes("provenance")) {
    return {
      category: "runtime-provenance",
      owner: "release-infra",
      action: "Export `DDD_EVIDENCE_ENVIRONMENT`, `DDD_RELEASE_CANDIDATE`, and `DDD_EVIDENCE_OPERATOR` before regenerating runtime smoke artifacts.",
    };
  }
  if (text.includes("runtime-readiness-summary") && text.includes("productionEquivalence")) {
    return {
      category: "production-equivalent-runtime",
      owner: "release-infra",
      action: "Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL so the artifact includes structured `productionEquivalence` evidence.",
    };
  }
  if (text.includes("localhost")
    || text.includes("non-local")
    || text.includes("production-equivalent")
    || text.includes("production-equivalence")
    || text.includes("productionEquivalence")) {
    return {
      category: "production-equivalent-runtime",
      owner: "release-infra",
      action: "Regenerate the runtime artifact against an HTTPS non-local production-equivalent URL.",
    };
  }
  if (text.includes("manifest")) {
    return {
      category: "manifest",
      owner: "release-owner",
      action: "Regenerate all missing evidence artifacts, then run `node scripts/ddd-release-evidence-manifest.mjs`.",
    };
  }
  return {
    category: "other",
    owner: "release-owner",
    action: "Inspect the strict release gate blocker and attach an owner-specific remediation.",
  };
}

function rollbackContextFromBlocker(text) {
  const match = String(text).match(/rollback-drill:\s+([A-Za-z]+)\s+status must be PASS or DEFERRED/);
  return match?.[1] || null;
}

function groupBy(items, key) {
  const groups = new Map();
  for (const item of items) {
    const value = item[key];
    if (!groups.has(value)) {
      groups.set(value, []);
    }
    groups.get(value).push(item);
  }
  return Object.fromEntries([...groups.entries()].sort(([left], [right]) => left.localeCompare(right)));
}

function manifestArtifactOwner(artifactPath = "") {
  if (artifactPath.includes("authenticated-runtime-baseline")) {
    return "release-performance";
  }
  if (artifactPath.includes("frontend")) {
    return "frontend";
  }
  if (artifactPath.includes("migration")) {
    return "database";
  }
  if (artifactPath.includes("docker") || artifactPath.includes("build/")) {
    return "release-infra";
  }
  if (artifactPath.includes("ai/")) {
    return "ai";
  }
  if (artifactPath.includes("rollback")) {
    return "release-owner";
  }
  return "release-owner";
}

function manifestArtifactAction(artifactPath = "") {
  if (artifactPath.includes("authenticated-runtime-baseline")) {
    return "Run authenticated performance smoke against production-equivalent URL, then promote the accepted actual with `node scripts/ddd-promote-performance-baseline.mjs`.";
  }
  if (artifactPath.includes("frontend")) {
    return "Run deployed frontend smoke and regenerate frontend evidence before rebuilding the release manifest.";
  }
  if (artifactPath.includes("migration")) {
    return "Run fresh/upgrade migration drills and regenerate migration evidence before rebuilding the release manifest.";
  }
  if (artifactPath.includes("docker") || artifactPath.includes("build/")) {
    return "Generate Docker build evidence in a Docker-capable CI runner before rebuilding the release manifest.";
  }
  return "Regenerate the missing evidence artifact, then rerun `node scripts/ddd-release-evidence-manifest.mjs`.";
}

function manifestArtifactEnvKeys(artifactPath = "") {
  if (artifactPath.includes("authenticated-runtime-baseline")) {
    return [
      "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
      "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
      "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
      "DDD_RELEASE_CANDIDATE",
    ];
  }
  if (artifactPath.includes("frontend")) {
    return ["PLAYWRIGHT_BASE_URL", "DDD_FRONTEND_EXPECT_DEPLOYED", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE"];
  }
  if (artifactPath.includes("migration")) {
    return ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"];
  }
  if (artifactPath.includes("docker") || artifactPath.includes("build/")) {
    return dockerBuildEvidenceEnvKeys;
  }
  return ["DDD_RELEASE_MANIFEST_STRICT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"];
}

function manifestActionPlan(missingArtifacts = []) {
  const byOwner = new Map();
  const add = (owner, id, action, envKeys = [], reason = null, extra = {}) => {
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        pendingItems: 0,
        envKeys: [],
        items: [],
      });
    }
    const plan = byOwner.get(owner);
    if (plan.items.some((item) => item.id === id)) {
      return;
    }
    plan.pendingItems += 1;
    plan.items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
    for (const envKey of envKeys || []) {
      if (!plan.envKeys.includes(envKey)) {
        plan.envKeys.push(envKey);
      }
    }
  };
  for (const blocker of missingArtifacts || []) {
    const artifactPath = String(blocker).replace(/^missing artifact\s+/i, "");
    const owner = manifestArtifactOwner(artifactPath);
    add(
      owner,
      `manifest-missing-${artifactPath.replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "").toLowerCase() || "artifact"}`,
      manifestArtifactAction(artifactPath),
      manifestArtifactEnvKeys(artifactPath),
      blocker,
      { artifact: artifactPath },
    );
  }
  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          items: plan.items.sort((left, right) => left.id.localeCompare(right.id)),
        },
      ]),
  );
}

function ownerActionRollup(summary) {
  const byOwner = new Map();
  const rootCauseByOwner = new Map();
  const collapseReleaseConfigToEnvFile = summary.diagnostics?.releaseEnvLint?.generatedMissingTemplate === true
    || !summary.diagnostics?.releaseEnvLint?.envFile
    || summary.diagnostics?.releaseEnvLint?.inputKind === "generated-missing-template"
    || summary.diagnostics?.releaseEnvLint?.inputKind === "missing-release-env-file"
    || summary.diagnostics?.releaseEnvLint?.inputKind === "process-environment-only";
  const ensureOwner = (owner) => {
    const key = owner || "release-owner";
    if (!byOwner.has(key)) {
      byOwner.set(key, {
        owner: key,
        pendingItems: 0,
        collapsedItems: 0,
        envKeys: [],
        sources: {},
        collapsedSources: {},
        items: [],
        collapsed: [],
      });
    }
    return byOwner.get(key);
  };
  const reasonForItem = (item = {}) => {
    if (item.reason || item.detail) {
      return item.reason || item.detail;
    }
    if (Array.isArray(item.reasons) && item.reasons.length > 0) {
      return item.reasons.filter(Boolean).join(" | ");
    }
    return null;
  };
  const rootCauseKeyFor = (owner, source, item = {}) => {
    const id = String(item.id || item.file || item.context || item.check || item.group || "");
    const checkId = String(item.checkId || "").trim();
    const normalized = checkId || id.replace(/^orchestrator-preflight-/, "");
    const byCheck = {
      "ai-owner-gateway-remote-expectation": "ai-owner-gateway",
      "ai-provider-remote-expectation": "ai-provider-runtime",
      "ai-runtime-base-url": "ai-runtime-base-url",
      "backend-runtime-base-url": "backend-runtime-base-url",
      "docker-daemon": "docker-daemon",
      "frontend-deployed-expectation": "frontend-deployed-expectation",
      "frontend-runtime-base-url": "frontend-base-url",
      "migration-runtime-evidence": "migration-runtime-evidence",
      "release-config-env-file": "release-env-file",
    };
    const byId = {
      "ai-owner-gateway": "ai-owner-gateway",
      "ai-provider-runtime": "ai-provider-runtime",
      "ai-runtime-base-url": "ai-runtime-base-url",
      "backend base url": "backend-runtime-base-url",
      "docker-daemon": "docker-daemon",
      "frontend-base-url": "frontend-base-url",
      "frontend-deployed-expectation": "frontend-deployed-expectation",
      "migration-diagnostic-fresh-database-drill": "migration-fresh-database",
      "migration-diagnostic-upgrade-database-drill": "migration-upgrade-database",
      "migration-fresh-database-drill": "migration-fresh-database",
      "migration-proof-fresh-database": "migration-fresh-database",
      "migration-proof-upgrade-database": "migration-upgrade-database",
      "migration-runtime-ready": "migration-runtime-evidence",
      "migration-upgrade-database-drill": "migration-upgrade-database",
      "release-env-lint-real-env-file": "release-env-file",
      "runtime-readiness-production-equivalence": "runtime-production-equivalence",
    };
    const root = byCheck[normalized] || byId[id] || null;
    if (!root) {
      return null;
    }
    return `${owner || "release-owner"}:${root}`;
  };
  const add = (owner, source, item = {}) => {
    const plan = ensureOwner(owner);
    const envKeys = Array.isArray(item.envKeys) ? item.envKeys.filter(Boolean) : [];
    const id = item.id || item.file || item.context || item.check || item.group || `${source}-${plan.pendingItems + 1}`;
    const itemReason = reasonForItem(item);
    const key = `${source}:${id}:${itemReason || item.action || ""}`;
    if (plan.items.some((existing) => existing.key === key)) {
      return;
    }
    if (collapseReleaseConfigToEnvFile && source === "release-config") {
      plan.collapsedItems += 1;
      plan.collapsedSources[source] = (plan.collapsedSources[source] || 0) + 1;
      plan.collapsed.push({
        source,
        id,
        reason: reasonForItem(item),
        action: item.action || item.command || null,
        envKeys,
        rootCauseKey: "global:release-env-file",
        coveredBy: {
          source: "release-env-lint",
          id: "release-env-lint-real-env-file",
          owner: "release-infra",
        },
      });
      return;
    }
    if (collapseReleaseConfigToEnvFile
      && source === "orchestrator"
      && ["backend-runtime-base-url", "release-config-env-file"].includes(String(item.checkId || id).replace(/^orchestrator-preflight-/, ""))) {
      plan.collapsedItems += 1;
      plan.collapsedSources[source] = (plan.collapsedSources[source] || 0) + 1;
      plan.collapsed.push({
        source,
        id,
        reason: reasonForItem(item),
        action: item.action || item.command || null,
        envKeys,
        rootCauseKey: "global:release-env-file",
        coveredBy: {
          source: "release-env-lint",
          id: "release-env-lint-real-env-file",
          owner: "release-infra",
        },
      });
      return;
    }
    const rootCauseKey = rootCauseKeyFor(plan.owner, source, { ...item, id });
    const ownerRootCauses = rootCauseByOwner.get(plan.owner) || new Map();
    const coveredBy = rootCauseKey ? ownerRootCauses.get(rootCauseKey) : null;
    if (coveredBy && (source === "orchestrator" || source === "migration")) {
      plan.collapsedItems += 1;
      plan.collapsedSources[source] = (plan.collapsedSources[source] || 0) + 1;
      plan.collapsed.push({
        source,
        id,
        reason: reasonForItem(item),
        action: item.action || item.command || null,
        envKeys,
        rootCauseKey,
        coveredBy,
      });
      return;
    }
    plan.pendingItems += 1;
    plan.sources[source] = (plan.sources[source] || 0) + 1;
    for (const envKey of envKeys) {
      if (!plan.envKeys.includes(envKey)) {
        plan.envKeys.push(envKey);
      }
    }
    if (rootCauseKey && !ownerRootCauses.has(rootCauseKey)) {
      ownerRootCauses.set(rootCauseKey, { source, id });
      rootCauseByOwner.set(plan.owner, ownerRootCauses);
    }
    plan.items.push({
      key,
      source,
      id,
      check: item.check || item.checkId || id,
      reason: itemReason,
      detail: item.detail || item.reason || itemReason,
      structured: item.structured === true,
      action: item.action || item.command || null,
      envKeys,
      artifact: item.artifact || null,
      rootCauseKey,
    });
  };
  const addPlan = (source, actionPlan) => {
    if (!actionPlan) {
      return;
    }
    if (actionPlan.items) {
      for (const item of actionPlan.items || []) {
        add(item.owner || actionPlan.owner, source, item);
      }
      return;
    }
    for (const [owner, plan] of Object.entries(actionPlan || {})) {
      for (const item of plan.items || []) {
        add(item.owner || owner, source, item);
      }
    }
  };

  addPlan("manifest", summary.manifest?.actionPlan);
  addPlan("runtime-readiness", summary.diagnostics?.runtimeReadiness?.actionPlan);
  addPlan("release-env-lint", summary.diagnostics?.releaseEnvLint?.actionPlan);
  addPlan("release-config", summary.diagnostics?.releaseConfig?.actionPlan);
  addPlan("authenticated-performance", summary.diagnostics?.authenticatedPerformance?.actionPlan);
  addPlan("business-e2e", summary.diagnostics?.businessE2e?.actionPlan);
  addPlan("docker", summary.diagnostics?.docker?.actionPlan);
  addPlan("frontend-smoke", summary.diagnostics?.frontendSmoke?.actionPlan);
  addPlan("migration", summary.diagnostics?.migration?.actionPlan);
  addPlan("explain", summary.diagnostics?.explain?.actionPlan);
  addPlan("ai-runtime", summary.diagnostics?.aiRuntime?.actionPlan);
  addPlan("rollback", summary.diagnostics?.rollback?.actionPlan);
  addPlan("orchestrator", summary.diagnostics?.orchestrator?.actionPlan);

  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          sources: Object.fromEntries(Object.entries(plan.sources).sort(([left], [right]) => left.localeCompare(right))),
          collapsedSources: Object.fromEntries(
            Object.entries(plan.collapsedSources).sort(([left], [right]) => left.localeCompare(right)),
          ),
          items: plan.items
            .sort((left, right) => `${left.source}.${left.id}`.localeCompare(`${right.source}.${right.id}`))
            .map(({ key, ...item }) => item),
          collapsed: plan.collapsed
            .sort((left, right) => `${left.source}.${left.id}`.localeCompare(`${right.source}.${right.id}`)),
        },
      ]),
  );
}

function ownerActionRollupArtifact(summary) {
  const owners = summary.ownerActionRollup || {};
  const activeOwners = Object.entries(owners)
    .filter(([, plan]) => (plan.pendingItems || 0) > 0)
    .map(([owner]) => owner)
    .sort();
  const collapsedOnlyOwners = Object.entries(owners)
    .filter(([, plan]) => (plan.pendingItems || 0) === 0 && (plan.collapsedItems || 0) > 0)
    .map(([owner]) => owner)
    .sort();
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    ownerCount: Object.keys(owners).length,
    activeOwnerCount: activeOwners.length,
    collapsedOnlyOwnerCount: collapsedOnlyOwners.length,
    activeOwners,
    collapsedOnlyOwners,
    totalPendingItems: Object.values(owners)
      .reduce((sum, plan) => sum + (plan.pendingItems || 0), 0),
    totalCollapsedItems: Object.values(owners)
      .reduce((sum, plan) => sum + (plan.collapsedItems || 0), 0),
    owners: redactReleaseEnvValueForDisplay(owners),
  };
}

function sourceActionRollup(summary) {
  const bySource = new Map();
  const ensureSource = (source) => {
    const key = source || "unknown";
    if (!bySource.has(key)) {
      bySource.set(key, {
        source: key,
        pendingItems: 0,
        owners: {},
        envKeys: [],
        items: [],
      });
    }
    return bySource.get(key);
  };
  for (const [owner, plan] of Object.entries(summary.ownerActionRollup || {})) {
    for (const item of plan.items || []) {
      const sourcePlan = ensureSource(item.source);
      const envKeys = Array.isArray(item.envKeys) ? item.envKeys.filter(Boolean) : [];
      sourcePlan.pendingItems += 1;
      sourcePlan.owners[owner] = (sourcePlan.owners[owner] || 0) + 1;
      for (const envKey of envKeys) {
        if (!sourcePlan.envKeys.includes(envKey)) {
          sourcePlan.envKeys.push(envKey);
        }
      }
      sourcePlan.items.push({
        owner,
        id: item.id || "",
        check: item.check || item.id || "",
        reason: item.reason || null,
        detail: item.detail || item.reason || null,
        structured: item.structured === true,
        action: item.action || null,
        envKeys,
        artifact: item.artifact || null,
      });
    }
  }
  return Object.fromEntries(
    [...bySource.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([source, plan]) => [
        source,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          owners: Object.fromEntries(Object.entries(plan.owners).sort(([left], [right]) => left.localeCompare(right))),
          items: plan.items.sort((left, right) => `${left.owner}.${left.id}`.localeCompare(`${right.owner}.${right.id}`)),
        },
      ]),
  );
}

function sourceActionRollupArtifact(summary) {
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    sourceCount: Object.keys(summary.sourceActionRollup || {}).length,
    totalPendingItems: Object.values(summary.sourceActionRollup || {})
      .reduce((sum, plan) => sum + (plan.pendingItems || 0), 0),
    sources: redactReleaseEnvValueForDisplay(summary.sourceActionRollup || {}),
  };
}

function releaseActionPriority(summary) {
  const sourceIndex = new Map(prioritySourceOrder.map((source, index) => [source, index]));
  const tierRank = { P0: 0, P1: 1, P2: 2, P3: 3 };
  const items = [];
  for (const [owner, plan] of Object.entries(summary.ownerActionRollup || {})) {
    for (const item of plan.items || []) {
      const source = item.source || "unknown";
      const tier = prioritySourceTiers[source] || "P3";
      items.push({
        priority: tier,
        source,
        owner,
        id: item.id || "",
        check: item.check || item.id || "",
        reason: item.reason || null,
        detail: item.detail || item.reason || null,
        structured: item.structured === true,
        envKeys: item.envKeys || [],
        action: item.action || null,
        artifact: item.artifact || null,
      });
    }
  }
  return items.sort((left, right) => {
    const leftTier = tierRank[left.priority] ?? 99;
    const rightTier = tierRank[right.priority] ?? 99;
    if (leftTier !== rightTier) {
      return leftTier - rightTier;
    }
    const leftSource = sourceIndex.has(left.source) ? sourceIndex.get(left.source) : 99;
    const rightSource = sourceIndex.has(right.source) ? sourceIndex.get(right.source) : 99;
    if (leftSource !== rightSource) {
      return leftSource - rightSource;
    }
    return `${left.owner}.${left.id}`.localeCompare(`${right.owner}.${right.id}`);
  });
}

function releaseActionPriorityArtifact(summary) {
  const items = summary.releaseActionPriority || [];
  const byPriority = {};
  const bySource = {};
  const byOwner = {};
  for (const item of items) {
    byPriority[item.priority] = (byPriority[item.priority] || 0) + 1;
    bySource[item.source] = (bySource[item.source] || 0) + 1;
    byOwner[item.owner] = (byOwner[item.owner] || 0) + 1;
  }
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    policy: {
      prioritySourceOrder,
      prioritySourceTiers,
      note: "P0 clears release-evidence prerequisites and production-equivalent baseline gates; P1 clears runtime/business acceptance; P2 clears database plan recertification; P3 reruns final orchestration after prerequisite evidence is ready.",
    },
    totalPendingItems: items.length,
    byPriority,
    bySource: Object.fromEntries(Object.entries(bySource).sort(([left], [right]) => left.localeCompare(right))),
    byOwner: Object.fromEntries(Object.entries(byOwner).sort(([left], [right]) => left.localeCompare(right))),
    items: items.map(releaseDisplayActionItem),
  };
}

function releaseBlockerMapArtifact(summary) {
  const actions = Array.isArray(summary.actions) ? summary.actions : [];
  const batches = Array.isArray(summary.releaseActionBatches) ? summary.releaseActionBatches : [];
  const byCategory = new Map();
  const byOwner = new Map();
  const ensureCategory = (category) => {
    const key = category || "unknown";
    if (!byCategory.has(key)) {
      byCategory.set(key, {
        category: key,
        blockerCount: 0,
        owners: {},
        blockers: [],
      });
    }
    return byCategory.get(key);
  };
  const ensureOwner = (owner) => {
    const key = owner || "unknown";
    if (!byOwner.has(key)) {
      byOwner.set(key, {
        owner: key,
        blockerCount: 0,
        categories: {},
        blockers: [],
      });
    }
    return byOwner.get(key);
  };
  for (const action of actions) {
    const group = ensureCategory(action.category);
    const owner = action.owner || "unknown";
    const ownerGroup = ensureOwner(owner);
    group.blockerCount += 1;
    group.owners[owner] = (group.owners[owner] || 0) + 1;
    group.blockers.push({
      blocker: action.blocker,
      check: action.check || null,
      detail: action.detail || null,
      structured: action.structured === true,
      owner,
        action: redactReleaseEnvCommandForDisplay(action.action || ""),
    });
    ownerGroup.blockerCount += 1;
    ownerGroup.categories[action.category || "unknown"] = (ownerGroup.categories[action.category || "unknown"] || 0) + 1;
    ownerGroup.blockers.push({
      blocker: action.blocker,
      check: action.check || null,
      detail: action.detail || null,
      structured: action.structured === true,
      category: action.category || "unknown",
      action: redactReleaseEnvCommandForDisplay(action.action || ""),
    });
  }
  const enrichBatches = (ownerNames) => {
    const owners = new Set(ownerNames);
    const candidateBatches = batches
      .filter((batch) => owners.has(batch.owner))
      .map((batch) => ({
        id: batch.id,
        priority: batch.priority,
        source: batch.source,
        owner: batch.owner,
        canRunImmediately: batch.canRunImmediately === true,
        commands: redactedDisplayCommands(batch.commands || []),
        expectedArtifacts: batch.expectedArtifacts || [],
        exitCriteria: batch.exitCriteria || [],
      }));
    return {
      readyBatchIds: candidateBatches.filter((batch) => batch.canRunImmediately).map((batch) => batch.id),
      blockedBatchIds: candidateBatches.filter((batch) => !batch.canRunImmediately).map((batch) => batch.id),
      commands: redactedDisplayCommands(candidateBatches.flatMap((batch) => batch.commands || [])),
      expectedArtifacts: [...new Set(candidateBatches.flatMap((batch) => batch.expectedArtifacts || []))],
      candidateBatches,
    };
  };
  const categories = [...byCategory.values()].map((group) => {
    const owners = Object.keys(group.owners).sort();
    const batchSummary = enrichBatches(owners);
    return {
      ...group,
      owners: Object.fromEntries(Object.entries(group.owners).sort(([left], [right]) => left.localeCompare(right))),
      ...batchSummary,
    };
  }).sort((left, right) => right.blockerCount - left.blockerCount || left.category.localeCompare(right.category));
  const owners = [...byOwner.values()].map((group) => ({
    ...group,
    categories: Object.fromEntries(Object.entries(group.categories).sort(([left], [right]) => left.localeCompare(right))),
    ...enrichBatches([group.owner]),
  })).sort((left, right) => right.blockerCount - left.blockerCount || left.owner.localeCompare(right.owner));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    categoryCount: categories.length,
    ownerCount: owners.length,
    totalBlockers: categories.reduce((sum, category) => sum + category.blockerCount, 0),
    owners,
    categories,
  };
}

function releaseSafetySignals(summary) {
  const envLint = summary.diagnostics?.releaseEnvLint || null;
  const envFileSecurity = envLint?.envFileSecurity || null;
  const envReadiness = releaseEnvReadinessRedactedArtifact(summary);
  const releaseEnvFileReady = envLint?.status === "PASS"
    && envLint?.inputKind === "release-env-file"
    && envLint?.generatedMissingTemplate !== true
    && envFileSecurity?.permissionSafe === true;
  const releaseEnvFilePath = envLint?.envFile && path.isAbsolute(envLint.envFile) && envLint.envFile.startsWith(`${repoRoot}${path.sep}`)
    ? path.relative(repoRoot, envLint.envFile)
    : envLint?.envFile || null;
  return {
    releaseEnvFile: {
      ready: releaseEnvFileReady,
      status: envLint?.status || "missing",
      inputKind: envLint?.inputKind || "missing",
      envFile: releaseEnvFilePath ? redactReleaseEnvCommandForDisplay(releaseEnvFilePath) : null,
      envFilePresent: Boolean(envLint?.envFile),
      generatedMissingTemplate: envLint?.generatedMissingTemplate === true,
      securityChecked: envFileSecurity?.checked === true,
      permissionSafe: envFileSecurity?.permissionSafe === true,
      permissionCheckSkipped: envFileSecurity?.permissionCheckSkipped === true,
      modeOctal: envFileSecurity?.modeOctal || "missing",
      reason: envFileSecurity?.reason || "missing",
      requiredMode: envFileSecurity?.requiredMode || "600",
      pendingActionIds: envLint?.actionPlan?.items?.map((item) => item.id).filter(Boolean) || [],
      blockingSafeDefaultAvailable: envReadiness.summary?.blockingSafeDefaultAvailable ?? 0,
      blockingRequiresOwnerInput: envReadiness.summary?.blockingRequiresOwnerInput ?? 0,
      safeDefaultsExhausted: envReadiness.summary?.safeDefaultsExhausted === true,
      ownerInputReasonCounts: envReadiness.summary?.ownerInputReasonCounts || {},
      ownerInputOwners: (envReadiness.byOwner || [])
        .filter((owner) => owner.requiresOwnerInput > 0)
        .map((owner) => ({
          owner: owner.owner,
          requiresOwnerInput: owner.requiresOwnerInput,
          safeDefaultAvailable: owner.safeDefaultAvailable,
        })),
    },
  };
}

function releaseEnvFileIsCutoverSafe(releaseEnvFile) {
  return releaseEnvFile?.ready === true
    && releaseEnvFile?.status === "PASS"
    && releaseEnvFile?.inputKind === "release-env-file"
    && releaseEnvFile?.envFilePresent === true
    && releaseEnvFile?.generatedMissingTemplate !== true
    && releaseEnvFile?.securityChecked === true
    && releaseEnvFile?.permissionSafe === true
    && releaseEnvFile?.permissionCheckSkipped !== true
    && releaseEnvFile?.modeOctal === (releaseEnvFile?.requiredMode || "600")
    && (releaseEnvFile?.requiredMode || "600") === "600";
}

function releaseFastTrackArtifact(summary) {
  const batches = summary.releaseActionBatches || [];
  const priority = summary.releaseActionPriority || [];
  const byLane = new Map();
  const ensureLane = (lane, policy) => {
    if (!byLane.has(lane)) {
      byLane.set(lane, {
        lane,
        safetyClass: policy.safetyClass,
        pendingItems: 0,
        sources: [],
        owners: [],
        batchIds: [],
        readyBatchIds: [],
        blockedBatchIds: [],
        envCheckGroups: [],
        commands: [],
        expectedArtifacts: [],
        acceleration: policy.acceleration,
      });
    }
    return byLane.get(lane);
  };
  for (const batch of batches) {
    const policy = fastTrackSourcePolicies[batch.source] || {
      lane: "other",
      safetyClass: "required-before-cutover",
      acceleration: "Resolve this batch with owner-specific evidence before cutover.",
    };
    const lane = ensureLane(policy.lane, policy);
    lane.pendingItems += batch.pendingItems || 0;
    if (!lane.sources.includes(batch.source)) {
      lane.sources.push(batch.source);
    }
    if (!lane.owners.includes(batch.owner)) {
      lane.owners.push(batch.owner);
    }
    lane.batchIds.push(batch.id);
    if (batch.canRunImmediately === true) {
      lane.readyBatchIds.push(batch.id);
    } else {
      lane.blockedBatchIds.push(batch.id);
    }
    for (const group of batch.envCheckGroups || []) {
      if (group.spec && !lane.envCheckGroups.includes(group.spec)) {
        lane.envCheckGroups.push(group.spec);
      }
    }
    for (const command of redactedDisplayCommands(batch.commands || [])) {
      if (!lane.commands.includes(command)) {
        lane.commands.push(command);
      }
    }
    for (const artifact of batch.expectedArtifacts || []) {
      if (!lane.expectedArtifacts.includes(artifact)) {
        lane.expectedArtifacts.push(artifact);
      }
    }
  }
  const laneOrder = [
    "environment",
    "deployable-image",
    "production-equivalence",
    "data-safety",
    "performance",
    "runtime-acceptance",
    "frontend-acceptance",
    "business-acceptance",
    "rollback-safety",
    "database-performance",
    "evidence-integrity",
    "final-verification",
    "other",
  ];
  const lanes = [...byLane.values()]
    .map((lane) => ({
      ...lane,
      sources: lane.sources.sort(),
      owners: lane.owners.sort(),
      envCheckGroups: lane.envCheckGroups.sort(),
      expectedArtifacts: lane.expectedArtifacts.sort(),
    }))
    .sort((left, right) => laneOrder.indexOf(left.lane) - laneOrder.indexOf(right.lane) || left.lane.localeCompare(right.lane));
  const nonWaivableItems = priority.filter((item) => (fastTrackSourcePolicies[item.source]?.safetyClass || "required-before-cutover") === "non-waivable");
  const readyBatches = batches.filter((batch) => batch.canRunImmediately === true);
  const gateBlockers = summary.gate?.blockers ?? 0;
  const laneByName = new Map(lanes.map((lane) => [lane.lane, lane]));
  const checklistItem = (id, title, laneNames, required) => {
    const relatedLanes = laneNames.map((lane) => laneByName.get(lane)).filter(Boolean);
    const pendingItems = relatedLanes.reduce((sum, lane) => sum + (lane.pendingItems || 0), 0);
    return {
      id,
      title,
      required,
      status: pendingItems === 0 ? "PASS" : "BLOCKED",
      pendingItems,
      lanes: laneNames,
      batchIds: relatedLanes.flatMap((lane) => lane.batchIds || []),
      readyBatchIds: relatedLanes.flatMap((lane) => lane.readyBatchIds || []),
      blockedBatchIds: relatedLanes.flatMap((lane) => lane.blockedBatchIds || []),
    };
  };
  const cutoverChecklist = [
    {
      id: "strict-release-gate",
      title: "Strict release gate has zero blockers and no contract issues.",
      required: true,
      status: gateBlockers === 0 ? "PASS" : "BLOCKED",
      pendingItems: gateBlockers,
      lanes: [],
      batchIds: [],
      readyBatchIds: readyBatches.map((batch) => batch.id),
      blockedBatchIds: batches.filter((batch) => batch.canRunImmediately !== true).map((batch) => batch.id),
    },
    checklistItem("release-environment", "Completed release env file and config matrix are valid.", ["environment"], true),
    checklistItem("deployable-images", "Deployable backend/frontend images are built and inspected.", ["deployable-image"], true),
    checklistItem("production-equivalence", "Runtime and performance evidence use HTTPS non-local production-equivalent endpoints.", ["production-equivalence", "performance"], true),
    checklistItem("data-safety", "Fresh and upgrade migrations are proven with runtime metadata.", ["data-safety"], true),
    checklistItem("runtime-business-acceptance", "AI, frontend, file, job, and payment acceptance evidence is complete.", ["runtime-acceptance", "frontend-acceptance", "business-acceptance"], true),
    checklistItem("rollback-safety", "Every bounded context has rollback PASS or approved unexpired DEFERRED risk acceptance.", ["rollback-safety"], true),
    checklistItem("database-performance", "Fresh production-equivalent EXPLAIN evidence has no scan/index blockers.", ["database-performance"], true),
    checklistItem("evidence-integrity", "Evidence manifest and final orchestrator strict rerun are clean.", ["evidence-integrity", "final-verification"], true),
  ];
  const blockedCutoverItems = cutoverChecklist.filter((item) => item.status !== "PASS").length;
  const recommendation = gateBlockers === 0 && blockedCutoverItems === 0 ? "GO_STRICT" : "NO_GO_STRICT";
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    recommendation,
    noAutoWaivers: true,
    reason: recommendation === "GO_STRICT"
      ? "Strict release gate has no blockers and every cutover checklist item is PASS."
      : "Cutover still has required safety or evidence items blocked; fastest safe path is to parallelize evidence collection without bypassing non-waivable safety gates.",
    gate: {
      strict: summary.gate?.strict === true,
      blockers: gateBlockers,
      warnings: summary.gate?.warnings ?? 0,
    },
    safetySignals: releaseSafetySignals(summary),
    summary: {
      totalPendingItems: priority.length,
      nonWaivableItems: nonWaivableItems.length,
      readyBatches: readyBatches.length,
      blockedBatches: batches.length - readyBatches.length,
      lanes: lanes.length,
      cutoverChecklistItems: cutoverChecklist.length,
      blockedCutoverItems,
    },
    fastestSafePath: [
      "Complete DDD_RELEASE_ENV_FILE and run release-execution-commands.sh with DDD_RELEASE_CHECK_ENV_ONLY=1.",
      "Run all P0 ready batches in parallel where infrastructure allows, then rerun strict release gate and readiness summary.",
      "After P0 is clean, run P1 runtime/business/rollback acceptance batches in parallel against HTTPS production-equivalent endpoints.",
      "Collect P2 EXPLAIN from production-equivalent MySQL after migrations are applied.",
      "Run P3 strict orchestrator and regenerate manifest/readiness summary only after P0/P1/P2 blockers are gone.",
    ],
    cutoverChecklist,
    lanes,
  };
}

function releaseFastTrackMarkdown(summary) {
  const artifact = releaseFastTrackArtifact(summary);
  const lines = [
    "# DDD Fast Track Release Decision",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Release gate blockers: ${artifact.gate.blockers}`,
    "",
    "## Reason",
    "",
    artifact.reason,
    "",
    "## Fastest Safe Path",
    "",
  ];
  for (const [index, step] of artifact.fastestSafePath.entries()) {
    lines.push(`${index + 1}. ${step}`);
  }
  lines.push("", "## Cutover Checklist", "");
  for (const item of artifact.cutoverChecklist || []) {
    lines.push(`- [${item.status}] ${item.id}: ${item.title}`);
    lines.push(`  - Pending items: ${item.pendingItems}`);
    if ((item.readyBatchIds || []).length > 0) {
      lines.push(`  - Ready batches: ${item.readyBatchIds.join(", ")}`);
    }
    if ((item.blockedBatchIds || []).length > 0) {
      lines.push(`  - Blocked batches: ${item.blockedBatchIds.join(", ")}`);
    }
  }
  lines.push("", "## Safety Signals", "");
  const releaseEnvFile = artifact.safetySignals?.releaseEnvFile || {};
  lines.push(`- releaseEnvFile: ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || "missing"} inputKind=${releaseEnvFile.inputKind || "missing"} envFilePresent=${releaseEnvFile.envFilePresent === true}`);
  lines.push(`  - securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || "missing"} requiredMode=${releaseEnvFile.requiredMode || "600"} reason=${releaseEnvFile.reason || "missing"} permissionCheckSkipped=${releaseEnvFile.permissionCheckSkipped === true}`);
  lines.push(`  - pendingActions=${(releaseEnvFile.pendingActionIds || []).join(", ") || "none"}`);
  lines.push("", "## Lanes", "");
  for (const lane of artifact.lanes) {
    lines.push(`### ${lane.lane}`);
    lines.push("");
    lines.push(`- Safety class: ${lane.safetyClass}`);
    lines.push(`- Pending items: ${lane.pendingItems}`);
    lines.push(`- Sources: ${lane.sources.join(", ") || "none"}`);
    lines.push(`- Owners: ${lane.owners.join(", ") || "none"}`);
    lines.push(`- Ready batches: ${lane.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${lane.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Acceleration: ${lane.acceleration}`);
    if (lane.envCheckGroups.length > 0) {
      lines.push("- Env check groups:");
      for (const group of lane.envCheckGroups.slice(0, 12)) {
        lines.push(`  - \`${group}\``);
      }
      if (lane.envCheckGroups.length > 12) {
        lines.push(`  - ... ${lane.envCheckGroups.length - 12} more`);
      }
    }
    if (lane.commands.length > 0) {
      lines.push("- Commands:");
      for (const command of lane.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseCutoverOwnerMatrixArtifact(summary) {
  const fastTrack = releaseFastTrackArtifact(summary);
  const batchesById = new Map((summary.releaseActionBatches || []).map((batch) => [batch.id, batch]));
  const ownerMap = new Map();
  const addOwnerItem = (owner, checklistItem, ownerBatches) => {
    if (!ownerMap.has(owner)) {
      ownerMap.set(owner, {
        owner,
        blockedItems: 0,
        totalItems: 0,
        readyBatchIds: [],
        blockedBatchIds: [],
        batchIds: [],
        items: [],
      });
    }
    const plan = ownerMap.get(owner);
    const batchIds = ownerBatches.map((batch) => batch.id);
    const readyBatchIds = ownerBatches.filter((batch) => batch.canRunImmediately === true).map((batch) => batch.id);
    const blockedBatchIds = ownerBatches.filter((batch) => batch.canRunImmediately !== true).map((batch) => batch.id);
    const envCheckGroups = [...new Set(ownerBatches.flatMap((batch) => (
      (batch.envCheckGroups || []).map((group) => group.spec).filter(Boolean)
    )))].sort();
    const commands = redactedDisplayCommands(ownerBatches.flatMap((batch) => batch.commands || [])).sort();
    const expectedArtifacts = [...new Set(ownerBatches.flatMap((batch) => batch.expectedArtifacts || []))].sort();
    const exitCriteria = [...new Set(ownerBatches.flatMap((batch) => batch.exitCriteria || []))].sort();
    const item = {
      checklistId: checklistItem.id,
      title: checklistItem.title,
      required: checklistItem.required === true,
      status: checklistItem.status,
      pendingItems: checklistItem.pendingItems || 0,
      lanes: checklistItem.lanes || [],
      batchIds,
      readyBatchIds,
      blockedBatchIds,
      commands,
      envCheckGroups,
      expectedArtifacts,
      exitCriteria,
    };
    plan.items.push(item);
    plan.totalItems += 1;
    if (item.status !== "PASS") {
      plan.blockedItems += 1;
    }
    for (const id of batchIds) {
      if (!plan.batchIds.includes(id)) {
        plan.batchIds.push(id);
      }
    }
    for (const id of readyBatchIds) {
      if (!plan.readyBatchIds.includes(id)) {
        plan.readyBatchIds.push(id);
      }
    }
    for (const id of blockedBatchIds) {
      if (!plan.blockedBatchIds.includes(id)) {
        plan.blockedBatchIds.push(id);
      }
    }
  };
  for (const checklistItem of fastTrack.cutoverChecklist || []) {
    const relatedBatches = (checklistItem.batchIds || [])
      .map((id) => batchesById.get(id))
      .filter(Boolean);
    if (relatedBatches.length === 0) {
      addOwnerItem("release-owner", checklistItem, []);
      continue;
    }
    const batchesByOwner = new Map();
    for (const batch of relatedBatches) {
      const owner = batch.owner || "release-owner";
      batchesByOwner.set(owner, [...(batchesByOwner.get(owner) || []), batch]);
    }
    for (const [owner, ownerBatches] of batchesByOwner.entries()) {
      addOwnerItem(owner, checklistItem, ownerBatches);
    }
  }
  const owners = [...ownerMap.values()]
    .map((plan) => ({
      ...plan,
      readyBatchIds: plan.readyBatchIds.sort(),
      blockedBatchIds: plan.blockedBatchIds.sort(),
      batchIds: plan.batchIds.sort(),
      items: plan.items.sort((left, right) => (
        left.status.localeCompare(right.status)
        || left.checklistId.localeCompare(right.checklistId)
      )),
    }))
    .sort((left, right) => (
      right.blockedItems - left.blockedItems
      || left.owner.localeCompare(right.owner)
    ));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: fastTrack.recommendation,
    noAutoWaivers: fastTrack.noAutoWaivers === true,
    releaseEnvFileCutoverSafe: releaseEnvFileIsCutoverSafe(fastTrack.safetySignals?.releaseEnvFile),
    safetySignals: fastTrack.safetySignals,
    gate: fastTrack.gate,
    summary: {
      ownerCount: owners.length,
      blockedOwnerCount: owners.filter((owner) => owner.blockedItems > 0).length,
      cutoverChecklistItems: (fastTrack.cutoverChecklist || []).length,
      blockedCutoverItems: fastTrack.summary?.blockedCutoverItems ?? 0,
      totalOwnerItems: owners.reduce((sum, owner) => sum + owner.totalItems, 0),
    },
    owners,
  };
}

function releaseCutoverOwnerMatrixMarkdown(summary) {
  const artifact = releaseCutoverOwnerMatrixArtifact(summary);
  const lines = [
    "# DDD Cutover Owner Matrix",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`,
    `Owner count: ${artifact.summary.ownerCount}`,
    `Blocked owners: ${artifact.summary.blockedOwnerCount}`,
    "",
  ];
  for (const owner of artifact.owners) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Blocked items: ${owner.blockedItems}`);
    lines.push(`- Total items: ${owner.totalItems}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    for (const item of owner.items) {
      lines.push(`- [${item.status}] ${item.checklistId}: ${item.title}`);
      lines.push(`  - Pending items: ${item.pendingItems}`);
      lines.push(`  - Lanes: ${item.lanes.join(", ") || "none"}`);
      lines.push(`  - Batches: ${item.batchIds.join(", ") || "none"}`);
      if (item.envCheckGroups.length > 0) {
        lines.push(`  - Env check groups: ${item.envCheckGroups.join(", ")}`);
      }
      if (item.expectedArtifacts.length > 0) {
        lines.push(`  - Expected artifacts: ${item.expectedArtifacts.join(", ")}`);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseSprintBoardArtifact(summary) {
  const fastTrack = releaseFastTrackArtifact(summary);
  const executionQueue = releaseExecutionQueueArtifact(summary);
  const ownerMatrix = releaseCutoverOwnerMatrixArtifact(summary);
  const batchById = new Map((summary.releaseActionBatches || []).map((batch) => [batch.id, batch]));
  const queueStatusById = new Map([
    ...(executionQueue.readyBatches || []).map((batch) => [batch.id, "READY"]),
    ...(executionQueue.blockedBatches || []).map((batch) => [batch.id, "BLOCKED"]),
  ]);
  const cutoverByBatchId = new Map();
  for (const owner of ownerMatrix.owners || []) {
    for (const item of owner.items || []) {
      for (const batchId of item.batchIds || []) {
        cutoverByBatchId.set(batchId, [...(cutoverByBatchId.get(batchId) || []), item.checklistId]);
      }
    }
  }
  const laneByBatchId = new Map();
  for (const lane of fastTrack.lanes || []) {
    for (const batchId of lane.batchIds || []) {
      laneByBatchId.set(batchId, [...(laneByBatchId.get(batchId) || []), lane.lane]);
    }
  }
  const batchCards = [...batchById.values()].map((batch) => ({
    id: batch.id,
    priority: batch.priority,
    status: queueStatusById.get(batch.id) || (batch.canRunImmediately === true ? "READY" : "BLOCKED"),
    source: batch.source,
    owner: batch.owner,
    pendingItems: batch.pendingItems || 0,
    dependsOn: batch.dependsOn || [],
    unmetDependencies: (batch.dependsOn || []).filter((dependencyId) => queueStatusById.get(dependencyId) !== "READY"),
    lanes: [...new Set(laneByBatchId.get(batch.id) || [])].sort(),
    cutoverChecklistIds: [...new Set(cutoverByBatchId.get(batch.id) || [])].sort(),
    commands: redactedDisplayCommands(batch.commands || []),
    envCheckGroups: (batch.envCheckGroups || []).map((group) => group.spec).filter(Boolean),
    expectedArtifacts: batch.expectedArtifacts || [],
    exitCriteria: batch.exitCriteria || [],
    itemIds: (batch.items || []).map((item) => item.id),
  })).sort((left, right) => (
    releaseBatchPriorityRank(left.priority) - releaseBatchPriorityRank(right.priority)
    || left.status.localeCompare(right.status)
    || left.owner.localeCompare(right.owner)
    || left.id.localeCompare(right.id)
  ));
  const priorities = ["P0", "P1", "P2", "P3"].map((priority) => {
    const cards = batchCards.filter((card) => card.priority === priority);
    return {
      priority,
      batchCount: cards.length,
      readyBatchCount: cards.filter((card) => card.status === "READY").length,
      blockedBatchCount: cards.filter((card) => card.status !== "READY").length,
      pendingItems: cards.reduce((sum, card) => sum + card.pendingItems, 0),
      owners: [...new Set(cards.map((card) => card.owner))].sort(),
      readyBatchIds: cards.filter((card) => card.status === "READY").map((card) => card.id),
      blockedBatchIds: cards.filter((card) => card.status !== "READY").map((card) => card.id),
    };
  }).filter((priority) => priority.batchCount > 0);
  const owners = [...new Set(batchCards.map((card) => card.owner))].sort().map((owner) => {
    const cards = batchCards.filter((card) => card.owner === owner);
    return {
      owner,
      batchCount: cards.length,
      readyBatchCount: cards.filter((card) => card.status === "READY").length,
      blockedBatchCount: cards.filter((card) => card.status !== "READY").length,
      pendingItems: cards.reduce((sum, card) => sum + card.pendingItems, 0),
      priorities: [...new Set(cards.map((card) => card.priority))].sort((left, right) => releaseBatchPriorityRank(left) - releaseBatchPriorityRank(right)),
      nextReadyBatchIds: cards.filter((card) => card.status === "READY").map((card) => card.id),
      blockedBatchIds: cards.filter((card) => card.status !== "READY").map((card) => card.id),
    };
  }).sort((left, right) => (
    right.readyBatchCount - left.readyBatchCount
    || right.pendingItems - left.pendingItems
    || left.owner.localeCompare(right.owner)
  ));
  const nextWave = batchCards.filter((card) => card.status === "READY");
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: fastTrack.recommendation,
    noAutoWaivers: fastTrack.noAutoWaivers === true,
    gate: fastTrack.gate,
    summary: {
      batchCount: batchCards.length,
      readyBatchCount: nextWave.length,
      blockedBatchCount: batchCards.length - nextWave.length,
      pendingItems: batchCards.reduce((sum, card) => sum + card.pendingItems, 0),
      priorityCount: priorities.length,
      ownerCount: owners.length,
      blockedCutoverItems: fastTrack.summary?.blockedCutoverItems ?? 0,
    },
    nextWave: {
      priority: executionQueue.nextPriority,
      batchIds: nextWave.map((card) => card.id),
      owners: [...new Set(nextWave.map((card) => card.owner))].sort(),
      commandCount: nextWave.reduce((sum, card) => sum + card.commands.length, 0),
      expectedArtifacts: [...new Set(nextWave.flatMap((card) => card.expectedArtifacts))].sort(),
    },
    priorities,
    owners,
    batchCards,
  };
}

function releaseSprintBoardMarkdown(summary) {
  const artifact = releaseSprintBoardArtifact(summary);
  const lines = [
    "# DDD Release Sprint Board",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Ready batches: ${artifact.summary.readyBatchCount}`,
    `Blocked batches: ${artifact.summary.blockedBatchCount}`,
    `Next wave priority: ${artifact.nextWave.priority || "none"}`,
    "",
    "## Next Wave",
    "",
    `- Owners: ${artifact.nextWave.owners.join(", ") || "none"}`,
    `- Batch IDs: ${artifact.nextWave.batchIds.join(", ") || "none"}`,
    `- Expected artifacts: ${artifact.nextWave.expectedArtifacts.join(", ") || "none"}`,
    "",
    "## Priorities",
    "",
  ];
  for (const priority of artifact.priorities) {
    lines.push(`### ${priority.priority}`, "");
    lines.push(`- Pending items: ${priority.pendingItems}`);
    lines.push(`- Ready batches: ${priority.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${priority.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Owners: ${priority.owners.join(", ") || "none"}`);
    lines.push("");
  }
  lines.push("## Owners", "");
  for (const owner of artifact.owners) {
    lines.push(`### ${owner.owner}`, "");
    lines.push(`- Pending items: ${owner.pendingItems}`);
    lines.push(`- Ready batches: ${owner.nextReadyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push("");
  }
  lines.push("## Batch Cards", "");
  for (const card of artifact.batchCards) {
    lines.push(`### ${card.id}`, "");
    lines.push(`- Status: ${card.status}`);
    lines.push(`- Scope: ${card.priority} ${card.source} -> ${card.owner}`);
    lines.push(`- Pending items: ${card.pendingItems}`);
    lines.push(`- Depends on: ${card.dependsOn.join(", ") || "none"}`);
    lines.push(`- Cutover items: ${card.cutoverChecklistIds.join(", ") || "none"}`);
    lines.push(`- Lanes: ${card.lanes.join(", ") || "none"}`);
    lines.push(`- Expected artifacts: ${card.expectedArtifacts.join(", ") || "none"}`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseCutoverDecisionSummary(summary) {
  const fastTrack = releaseFastTrackArtifact(summary);
  const blockedCutoverItems = (fastTrack.cutoverChecklist || []).filter((item) => item.status !== "PASS");
  const safetySignals = releaseSafetySignals(summary);
  const releaseEnvFileCutoverSafe = releaseEnvFileIsCutoverSafe(safetySignals.releaseEnvFile);
  const gateBlockers = summary.gate?.blockers ?? 0;
  const finalRecommendation = fastTrack.recommendation;
  const cutoverAllowed = false;
  const stopReasons = sortedUniqueStrings([
    ...(finalRecommendation !== "GO_STRICT" ? [`strict release gate blockers=${gateBlockers}`] : []),
    ...blockedCutoverItems.map((item) => `cutover checklist blocked: ${item.id}`),
  ]);
  return {
    recommendation: finalRecommendation,
    finalRecommendation,
    cutoverAllowed,
    releaseEnvFileCutoverSafe,
    gateBlockers,
    blockedCutoverItems: blockedCutoverItems.length,
    stopReasonCount: stopReasons.length,
    stopReasonCoverage: "catalog-snapshot",
    stopReasons: stopReasons.slice(0, 12),
    source: "artifacts/ddd/release/release-final-go-no-go.json",
    enforceCommand: finalGoNoGoEnforceCommand,
    cutoverAuthority: "final-go-no-go-gate",
    requiresFinalGate: true,
  };
}

function releaseCommandCatalogArtifact(summary) {
  const board = releaseSprintBoardArtifact(summary);
  const finalDecision = releaseCutoverDecisionSummary(summary);
  const scriptPath = "artifacts/ddd/release/release-execution-commands.sh";
  const commandSet = ({ owner = "", priority = "", batchId = "" } = {}) => {
    const envParts = [];
    if (batchId) {
      envParts.push(`DDD_RELEASE_BATCH=${batchId}`);
    }
    if (owner) {
      envParts.push(`DDD_RELEASE_OWNER=${owner}`);
    }
    if (priority) {
      envParts.push(`DDD_RELEASE_PRIORITY=${priority}`);
    }
    const prefix = envParts.length > 0 ? `${envParts.join(" ")} ` : "";
    return {
      list: `${prefix}DDD_RELEASE_LIST_BATCHES=1 bash ${scriptPath}`,
      envCheck: `${prefix}DDD_RELEASE_CHECK_ENV_ONLY=1 bash ${scriptPath}`,
      dryRun: `${prefix}DDD_RELEASE_DRY_RUN=1 bash ${scriptPath}`,
      execute: `${prefix}bash ${scriptPath}`,
    };
  };
  const nextPriority = board.nextWave.priority || "";
  const ownerCommands = (board.nextWave.owners || []).map((owner) => {
    const cards = (board.batchCards || []).filter((card) => card.status === "READY" && card.owner === owner);
    return {
      owner,
      priority: nextPriority,
      readyBatchIds: cards.map((card) => card.id),
      expectedArtifacts: [...new Set(cards.flatMap((card) => card.expectedArtifacts || []))].sort(),
      commands: commandSet({ owner, priority: nextPriority }),
    };
  });
  const batchCommands = (board.batchCards || [])
    .filter((card) => card.status === "READY")
    .map((card) => ({
      batchId: card.id,
      owner: card.owner,
      priority: card.priority,
      expectedArtifacts: card.expectedArtifacts || [],
      commands: commandSet({ batchId: card.id }),
    }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: board.recommendation,
    noAutoWaivers: board.noAutoWaivers === true,
    finalDecision,
    safetySignals: releaseSafetySignals(summary),
    releaseEnvFileCutoverSafe: releaseEnvFileIsCutoverSafe(releaseSafetySignals(summary).releaseEnvFile),
    scriptPath,
    summary: {
      ownerCommandCount: ownerCommands.length,
      batchCommandCount: batchCommands.length,
      nextPriority,
      readyBatchCount: board.summary.readyBatchCount,
    },
    nextPriorityCommands: nextPriority ? commandSet({ priority: nextPriority }) : null,
    ownerCommands,
    batchCommands,
  };
}

function releaseCommandCatalogMarkdown(summary) {
  const artifact = releaseCommandCatalogArtifact(summary);
  const lines = [
    "# DDD Release Command Catalog",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Cutover allowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `Stop reasons: ${artifact.finalDecision.stopReasonCount}`,
    `Script: ${artifact.scriptPath}`,
    `Next priority: ${artifact.summary.nextPriority || "none"}`,
    "",
    "## Final Cutover Decision",
    "",
    `- finalRecommendation: ${artifact.finalDecision.finalRecommendation}`,
    `- cutoverAllowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `- releaseEnvFileCutoverSafe: ${artifact.finalDecision.releaseEnvFileCutoverSafe === true}`,
    `- gateBlockers: ${artifact.finalDecision.gateBlockers}`,
    `- blockedCutoverItems: ${artifact.finalDecision.blockedCutoverItems}`,
    `- stopReasonCount: ${artifact.finalDecision.stopReasonCount}`,
    `- stopReasonCoverage: ${artifact.finalDecision.stopReasonCoverage}`,
    `- cutoverAuthority: ${artifact.finalDecision.cutoverAuthority}`,
    `- requiresFinalGate: ${artifact.finalDecision.requiresFinalGate === true}`,
    `- source: ${artifact.finalDecision.source}`,
    `- enforceCommand: \`${artifact.finalDecision.enforceCommand}\``,
    "",
    "### Current Stop Reasons",
    "",
    ...(artifact.finalDecision.stopReasons || []).length > 0
      ? artifact.finalDecision.stopReasons.map((reason) => `- ${reason}`)
      : ["- none"],
    "",
    "## Safety Signals",
    "",
  ];
  const releaseEnvFile = artifact.safetySignals?.releaseEnvFile || {};
  lines.push(`- releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`);
  lines.push(`- releaseEnvFile: ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || "missing"} inputKind=${releaseEnvFile.inputKind || "missing"} envFilePresent=${releaseEnvFile.envFilePresent === true}`);
  lines.push(`  - securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || "missing"} requiredMode=${releaseEnvFile.requiredMode || "600"} reason=${releaseEnvFile.reason || "missing"} permissionCheckSkipped=${releaseEnvFile.permissionCheckSkipped === true}`);
  lines.push(`  - safeDefaultsExhausted=${releaseEnvFile.safeDefaultsExhausted === true} blockingSafeDefaultAvailable=${releaseEnvFile.blockingSafeDefaultAvailable ?? 0} blockingRequiresOwnerInput=${releaseEnvFile.blockingRequiresOwnerInput ?? 0}`);
  lines.push(`  - ownerInputReasons=${Object.entries(releaseEnvFile.ownerInputReasonCounts || {}).map(([reason, count]) => `${reason}:${count}`).join(", ") || "none"}`);
  lines.push(`  - ownerInputOwners=${(releaseEnvFile.ownerInputOwners || []).map((owner) => `${owner.owner}:${owner.requiresOwnerInput}`).join(", ") || "none"}`);
  lines.push(`  - pendingActions=${(releaseEnvFile.pendingActionIds || []).join(", ") || "none"}`);
  lines.push(
    "",
    "## Next Priority",
    "",
  );
  if (artifact.nextPriorityCommands) {
    for (const [label, command] of Object.entries(artifact.nextPriorityCommands)) {
      lines.push(`- ${label}: \`${command}\``);
    }
  } else {
    lines.push("- None");
  }
  lines.push("", "## Owners", "");
  for (const owner of artifact.ownerCommands || []) {
    lines.push(`### ${owner.owner}`, "");
    lines.push(`- Priority: ${owner.priority || "none"}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Expected artifacts: ${owner.expectedArtifacts.join(", ") || "none"}`);
    for (const [label, command] of Object.entries(owner.commands || {})) {
      lines.push(`- ${label}: \`${command}\``);
    }
    lines.push("");
  }
  lines.push("## Batches", "");
  for (const batch of artifact.batchCommands || []) {
    lines.push(`### ${batch.batchId}`, "");
    lines.push(`- Owner: ${batch.owner}`);
    lines.push(`- Priority: ${batch.priority}`);
    lines.push(`- Expected artifacts: ${(batch.expectedArtifacts || []).join(", ") || "none"}`);
    for (const [label, command] of Object.entries(batch.commands || {})) {
      lines.push(`- ${label}: \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseOwnerHandoffArtifact(summary) {
  const board = releaseSprintBoardArtifact(summary);
  const catalog = releaseCommandCatalogArtifact(summary);
  const envMatrix = releaseEnvOwnerMatrixArtifact(summary);
  const commandByOwner = new Map((catalog.ownerCommands || []).map((owner) => [owner.owner, owner]));
  const envByOwner = new Map((envMatrix.owners || []).map((owner) => [owner.owner, owner]));
  const owners = [...new Set([
    ...(board.owners || []).map((owner) => owner.owner),
    ...(catalog.ownerCommands || []).map((owner) => owner.owner),
    ...(envMatrix.owners || []).map((owner) => owner.owner),
  ])].sort().map((owner) => {
    const cards = (board.batchCards || []).filter((card) => card.owner === owner);
    const readyCards = cards.filter((card) => card.status === "READY");
    const blockedCards = cards.filter((card) => card.status !== "READY");
    const commandPlan = commandByOwner.get(owner) || null;
    const envPlan = envByOwner.get(owner) || {};
    const expectedArtifacts = sortedUniqueStrings([
      ...cards.flatMap((card) => card.expectedArtifacts || []),
      ...(commandPlan?.expectedArtifacts || []),
      ...(envPlan.expectedArtifacts || []),
    ]);
    const exitCriteria = sortedUniqueStrings([
      ...cards.flatMap((card) => card.exitCriteria || []),
      ...(envPlan.exitCriteria || []),
      "Rerun strict release gate and readiness summary after evidence is refreshed.",
    ]);
    const handoffChecklist = [
      ...(envPlan.templateEnvKeys || []).length > 0
        ? ["Populate the listed canonical env keys or accepted aliases in a secure release env file; do not commit secret values."]
        : [],
      ...(commandPlan?.commands?.envCheck ? ["Run the owner env-check command before collecting evidence."] : []),
      ...(commandPlan?.commands?.dryRun ? ["Run the owner dry-run command to confirm batch scope."] : []),
      ...(commandPlan?.commands?.execute ? ["Run the owner execute command in the production-equivalent release environment."] : []),
      "Archive or refresh every expected artifact listed for this owner.",
      "Confirm every exit criterion before the next dependent batch starts.",
    ];
    return {
      owner,
      status: readyCards.length > 0 ? "READY" : "BLOCKED",
      pendingItems: cards.reduce((sum, card) => sum + (card.pendingItems || 0), 0),
      priorities: [...new Set(cards.map((card) => card.priority))].sort((left, right) => releaseBatchPriorityRank(left) - releaseBatchPriorityRank(right)),
      batchIds: cards.map((card) => card.id),
      readyBatchIds: readyCards.map((card) => card.id),
      blockedBatchIds: blockedCards.map((card) => card.id),
      blockedByBatchIds: sortedUniqueStrings(blockedCards.flatMap((card) => card.unmetDependencies || [])),
      commandSet: commandPlan?.commands || null,
      templateEnvKeys: envPlan.templateEnvKeys || [],
      aliasMappings: envPlan.aliasMappings || [],
      expectedArtifacts,
      exitCriteria,
      handoffChecklist,
    };
  }).sort((left, right) => (
    (left.status === "READY" ? 0 : 1) - (right.status === "READY" ? 0 : 1)
    || right.pendingItems - left.pendingItems
    || left.owner.localeCompare(right.owner)
  ));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: board.recommendation,
    noAutoWaivers: board.noAutoWaivers === true,
    finalDecision: catalog.finalDecision,
    safetySignals: catalog.safetySignals,
    releaseEnvFileCutoverSafe: catalog.releaseEnvFileCutoverSafe,
    gate: board.gate,
    summary: {
      ownerCount: owners.length,
      readyOwnerCount: owners.filter((owner) => owner.status === "READY").length,
      blockedOwnerCount: owners.filter((owner) => owner.status !== "READY").length,
      readyBatchCount: board.summary.readyBatchCount,
      blockedBatchCount: board.summary.blockedBatchCount,
      templateEnvKeyCount: envMatrix.templateEnvKeyCount,
    },
    owners,
  };
}

function releaseOwnerHandoffMarkdown(summary) {
  const artifact = releaseOwnerHandoffArtifact(summary);
  const lines = [
    "# DDD Release Owner Handoff",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Cutover allowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `Stop reasons: ${artifact.finalDecision.stopReasonCount}`,
    `releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`,
    `Ready owners: ${artifact.summary.readyOwnerCount}`,
    `Blocked owners: ${artifact.summary.blockedOwnerCount}`,
    "",
    "## Final Cutover Decision",
    "",
    `- finalRecommendation: ${artifact.finalDecision.finalRecommendation}`,
    `- cutoverAllowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `- releaseEnvFileCutoverSafe: ${artifact.finalDecision.releaseEnvFileCutoverSafe === true}`,
    `- gateBlockers: ${artifact.finalDecision.gateBlockers}`,
    `- blockedCutoverItems: ${artifact.finalDecision.blockedCutoverItems}`,
    `- stopReasonCount: ${artifact.finalDecision.stopReasonCount}`,
    `- stopReasonCoverage: ${artifact.finalDecision.stopReasonCoverage}`,
    `- cutoverAuthority: ${artifact.finalDecision.cutoverAuthority}`,
    `- requiresFinalGate: ${artifact.finalDecision.requiresFinalGate === true}`,
    `- source: ${artifact.finalDecision.source}`,
    `- enforceCommand: \`${artifact.finalDecision.enforceCommand}\``,
    "",
    "### Current Stop Reasons",
    "",
    ...(artifact.finalDecision.stopReasons || []).length > 0
      ? artifact.finalDecision.stopReasons.map((reason) => `- ${reason}`)
      : ["- none"],
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Status: ${owner.status}`);
    lines.push(`- Pending items: ${owner.pendingItems}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked by: ${owner.blockedByBatchIds.join(", ") || "none"}`);
    lines.push(`- Env keys: ${owner.templateEnvKeys.length}`);
    lines.push(`- Expected artifacts: ${owner.expectedArtifacts.join(", ") || "none"}`);
    if (owner.commandSet) {
      lines.push("- Commands:");
      for (const [label, command] of Object.entries(owner.commandSet)) {
        lines.push(`  - ${label}: \`${command}\``);
      }
    }
    if (owner.templateEnvKeys.length > 0) {
      lines.push("- Template env keys:");
      for (const key of owner.templateEnvKeys) {
        lines.push(`  - \`${key}\``);
      }
    }
    lines.push("- Handoff checklist:");
    for (const item of owner.handoffChecklist || []) {
      lines.push(`  - ${item}`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseOwnerReceiptsArtifact(summary) {
  const handoff = releaseOwnerHandoffArtifact(summary);
  const owners = (handoff.owners || []).map((owner) => {
    const actionPlan = summary.ownerActionRollup?.[owner.owner] || {};
    const artifactReceipts = (owner.expectedArtifacts || []).map((relativePath) => {
      const absolutePath = path.isAbsolute(relativePath) ? relativePath : path.join(repoRoot, relativePath);
      const exists = fs.existsSync(absolutePath);
      return {
        relativePath,
        absolutePath,
        exists,
      };
    });
    const missingArtifacts = artifactReceipts.filter((artifact) => !artifact.exists).map((artifact) => artifact.relativePath);
    const presentArtifacts = artifactReceipts.filter((artifact) => artifact.exists).map((artifact) => artifact.relativePath);
    const pendingActions = (actionPlan.items || []).map((item) => ({
      source: item.source || "",
      id: item.id || "",
      reason: item.reason || "",
      action: item.action || "",
      envKeys: item.envKeys || [],
      artifact: item.artifact || null,
    }));
    const receiptStatus = missingArtifacts.length > 0
      ? "ARTIFACT_MISSING"
      : pendingActions.length > 0
        ? "CONTENT_BLOCKED"
        : owner.status === "READY"
          ? "READY_FOR_STRICT_GATE_RERUN"
          : "WAITING_ON_DEPENDENCIES";
    return {
      owner: owner.owner,
      status: owner.status,
      receiptStatus,
      readyBatchIds: owner.readyBatchIds || [],
      blockedBatchIds: owner.blockedBatchIds || [],
      expectedArtifactCount: artifactReceipts.length,
      presentArtifactCount: presentArtifacts.length,
      missingArtifactCount: missingArtifacts.length,
      pendingActionCount: pendingActions.length,
      collapsedActionCount: actionPlan.collapsedItems || 0,
      pendingActions,
      presentArtifacts,
      missingArtifacts,
      exitCriteria: owner.exitCriteria || [],
      nextCheck: "Rerun strict release gate and readiness summary after all missing artifacts are present.",
    };
  });
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: handoff.recommendation,
    noAutoWaivers: handoff.noAutoWaivers === true,
    summary: {
      ownerCount: owners.length,
      readyOwnerCount: owners.filter((owner) => owner.status === "READY").length,
      readyForStrictGateRerunOwnerCount: owners.filter((owner) => owner.receiptStatus === "READY_FOR_STRICT_GATE_RERUN").length,
      contentBlockedOwnerCount: owners.filter((owner) => owner.receiptStatus === "CONTENT_BLOCKED").length,
      artifactMissingOwnerCount: owners.filter((owner) => owner.receiptStatus === "ARTIFACT_MISSING").length,
      waitingOnDependenciesOwnerCount: owners.filter((owner) => owner.receiptStatus === "WAITING_ON_DEPENDENCIES").length,
      expectedArtifactCount: owners.reduce((sum, owner) => sum + owner.expectedArtifactCount, 0),
      presentArtifactCount: owners.reduce((sum, owner) => sum + owner.presentArtifactCount, 0),
      missingArtifactCount: owners.reduce((sum, owner) => sum + owner.missingArtifactCount, 0),
      pendingActionCount: owners.reduce((sum, owner) => sum + owner.pendingActionCount, 0),
    },
    owners,
  };
}

function releaseOwnerReceiptsMarkdown(summary) {
  const artifact = releaseOwnerReceiptsArtifact(summary);
  const lines = [
    "# DDD Release Owner Receipts",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Ready for strict gate rerun owners: ${artifact.summary.readyForStrictGateRerunOwnerCount}`,
    `Content blocked owners: ${artifact.summary.contentBlockedOwnerCount}`,
    `Artifact missing owners: ${artifact.summary.artifactMissingOwnerCount}`,
    `Missing artifacts: ${artifact.summary.missingArtifactCount}`,
    `Pending actions: ${artifact.summary.pendingActionCount}`,
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Receipt status: ${owner.receiptStatus}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Present artifacts: ${owner.presentArtifactCount}`);
    lines.push(`- Missing artifacts: ${owner.missingArtifactCount}`);
    lines.push(`- Pending actions: ${owner.pendingActionCount}`);
    if (owner.missingArtifacts.length > 0) {
      lines.push("- Missing artifact paths:");
      for (const artifactPath of owner.missingArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    if (owner.pendingActions.length > 0) {
      lines.push("- Pending action reasons:");
      for (const action of owner.pendingActions.slice(0, 5)) {
        lines.push(`  - [${action.source}] ${action.id}: ${action.reason}`);
      }
    }
    lines.push(`- Next check: ${owner.nextCheck}`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

const nextActionPrimarySourceRank = {
  "release-env-lint": 0,
  "release-config": 1,
  orchestrator: 2,
  docker: 3,
  "authenticated-performance": 4,
  "business-e2e": 5,
  "frontend-smoke": 6,
  "ai-runtime": 7,
  rollback: 8,
  explain: 9,
  migration: 10,
};

function primaryOwnerPendingAction(actions = []) {
  return actions
    .map((action, index) => ({ action, index }))
    .sort((left, right) => (
      (nextActionPrimarySourceRank[left.action?.source] ?? 99) - (nextActionPrimarySourceRank[right.action?.source] ?? 99)
      || left.index - right.index
    ))[0]?.action || null;
}

function releaseOwnerInputReceiptQueueSummary(summary) {
  const ownerInputReceipt = releaseOwnerInputReceiptArtifact(summary);
  const packet = releaseEnvOwnerInputPacketArtifact(summary);
  const itemChecklistPathByOwner = new Map((packet.owners || []).map((owner) => [
    owner.owner,
    path.posix.join("artifacts", "ddd", "release", "release-owner-input-receipt-items", `${owner.fileName}.md`),
  ]));
  return {
    artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
    csv: "artifacts/ddd/release/release-owner-input-receipt.csv",
    itemsCsv: "artifacts/ddd/release/release-owner-input-receipt-items.csv",
    itemsMarkdown: "artifacts/ddd/release/release-owner-input-receipt-items.md",
    markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
    status: ownerInputReceipt.status,
    cutoverReady: ownerInputReceipt.cutoverReady === true,
    requiredOwnerInputs: Number(ownerInputReceipt.summary?.requiredOwnerInputs || 0),
    ownerCount: Number(ownerInputReceipt.summary?.ownerCount || 0),
    readyOwnerCount: Number(ownerInputReceipt.summary?.readyOwnerCount || 0),
    pendingOwnerCount: Number(ownerInputReceipt.summary?.pendingOwnerCount || 0),
    missingCriteria: sortedUniqueStrings(ownerInputReceipt.missingCriteria || []),
    pendingOwners: (ownerInputReceipt.ownerReceipts || [])
      .filter((owner) => owner.ready !== true)
      .map((owner) => ({
        owner: owner.owner || "unknown",
        requiredOwnerInputs: Number(owner.requiredOwnerInputs || 0),
        remainingPlaceholders: Number(owner.remainingPlaceholders || 0),
        remainingMissing: Number(owner.remainingMissing || 0),
        packetPath: owner.packetPath || "",
        handoffPath: owner.handoffPath || "",
        itemChecklistPath: itemChecklistPathByOwner.get(owner.owner) || "",
      }))
      .sort((left, right) => right.requiredOwnerInputs - left.requiredOwnerInputs
        || left.owner.localeCompare(right.owner)),
  };
}

function releaseNextActionQueueArtifact(summary) {
  const receipts = releaseOwnerReceiptsArtifact(summary);
  const handoff = releaseOwnerHandoffArtifact(summary);
  const ownerInputReceipt = releaseOwnerInputReceiptQueueSummary(summary);
  const handoffByOwner = new Map((handoff.owners || []).map((owner) => [owner.owner, owner]));
  const strictGateBlockersByOwner = new Map();
  const strictGateActionsByOwner = new Map();
  for (const action of summary.actions || []) {
    const owner = action.owner || "release-owner";
    strictGateBlockersByOwner.set(owner, (strictGateBlockersByOwner.get(owner) || 0) + 1);
    if (!strictGateActionsByOwner.has(owner)) {
      strictGateActionsByOwner.set(owner, action);
    }
  }
  const statusRank = {
    ARTIFACT_MISSING: 0,
    CONTENT_BLOCKED: 1,
    READY_FOR_STRICT_GATE_RERUN: 2,
    WAITING_ON_DEPENDENCIES: 3,
  };
  const items = (receipts.owners || []).map((owner) => {
    const firstAction = primaryOwnerPendingAction(owner.pendingActions || []);
    const handoffOwner = handoffByOwner.get(owner.owner) || {};
    const ownerCommandSet = handoffOwner.commandSet || {};
    const strictGateBlockerCount = strictGateBlockersByOwner.get(owner.owner) || 0;
    const firstStrictGateAction = strictGateActionsByOwner.get(owner.owner) || null;
    const strictGateSource = strictGateSourceForAction(firstStrictGateAction || {});
    const strictGateCommandHints = strictGateSource ? (defaultCommandHintsBySource[strictGateSource] || []) : [];
    const strictGateEnvKeys = strictGateSource ? (defaultEnvKeysBySource[strictGateSource] || []) : [];
    const nextAction = firstStrictGateAction?.action || (owner.missingArtifactCount > 0
      ? `Produce missing artifact: ${owner.missingArtifacts[0]}`
      : firstAction?.action || owner.nextCheck);
    const reason = firstStrictGateAction
      ? `strictGate=${firstStrictGateAction.check || firstStrictGateAction.category || "blocker"} ${firstStrictGateAction.detail || firstStrictGateAction.blocker || ""}`.trim()
      : owner.missingArtifactCount > 0
      ? `missingArtifact=${owner.missingArtifacts[0]}`
      : firstAction
        ? `${firstAction.source}:${firstAction.id} ${firstAction.reason}`
        : owner.receiptStatus;
    const executableCommands = orderedUniqueStrings([
      ...strictGateCommandHints,
      ...commandHintsFromAction(nextAction),
      ...commandHintsFromAction(firstStrictGateAction?.action || ""),
      ...commandHintsFromAction(firstAction?.action || ""),
      ...commandHintsFromAction(reason),
      ...(owner.status === "READY" ? Object.values(ownerCommandSet).filter(Boolean) : []),
    ].map(redactReleaseEnvCommandForDisplay));
    return {
      owner: owner.owner,
      queueStatus: owner.status === "READY" ? "RUN_NOW" : "WAIT_FOR_DEPENDENCIES",
      receiptStatus: owner.receiptStatus,
      readyBatchIds: owner.readyBatchIds || [],
      blockedBatchIds: owner.blockedBatchIds || [],
      strictGateBlockerCount,
      strictGateBlocker: firstStrictGateAction ? {
        check: firstStrictGateAction.check || "",
        category: firstStrictGateAction.category || "",
        detail: firstStrictGateAction.detail || "",
      } : null,
      missingArtifacts: owner.missingArtifacts || [],
      pendingActionCount: owner.pendingActionCount || 0,
      collapsedActionCount: owner.collapsedActionCount || 0,
      nextAction: redactReleaseEnvCommandForDisplay(nextAction),
      reason: redactReleaseEnvCommandForDisplay(reason),
      commandHint: redactReleaseEnvCommandForDisplay(firstStrictGateAction?.action || firstAction?.action || ""),
      executableCommands,
      envKeys: sortedUniqueStrings([
        ...strictGateEnvKeys,
        ...(firstStrictGateAction?.envKeys || []),
        ...(firstAction?.envKeys || []),
      ]),
    };
  }).sort((left, right) => (
    (left.queueStatus === "RUN_NOW" ? 0 : 1) - (right.queueStatus === "RUN_NOW" ? 0 : 1)
    || right.strictGateBlockerCount - left.strictGateBlockerCount
    || (statusRank[left.receiptStatus] ?? 9) - (statusRank[right.receiptStatus] ?? 9)
    || right.missingArtifacts.length - left.missingArtifacts.length
    || right.pendingActionCount - left.pendingActionCount
    || left.owner.localeCompare(right.owner)
  )).map((item, index) => ({
    order: index + 1,
    ...item,
  }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: receipts.recommendation,
    noAutoWaivers: receipts.noAutoWaivers === true,
    finalDecision: handoff.finalDecision,
    safetySignals: handoff.safetySignals,
    releaseEnvFileCutoverSafe: handoff.releaseEnvFileCutoverSafe,
    summary: {
      itemCount: items.length,
      runNowCount: items.filter((item) => item.queueStatus === "RUN_NOW").length,
      waitingCount: items.filter((item) => item.queueStatus !== "RUN_NOW").length,
      artifactMissingCount: items.filter((item) => item.receiptStatus === "ARTIFACT_MISSING").length,
      contentBlockedCount: items.filter((item) => item.receiptStatus === "CONTENT_BLOCKED").length,
      readyForStrictGateRerunCount: items.filter((item) => item.receiptStatus === "READY_FOR_STRICT_GATE_RERUN").length,
      strictGateBlockerOwnerCount: items.filter((item) => item.strictGateBlockerCount > 0).length,
      ownerInputReceiptStatus: ownerInputReceipt.status,
      ownerInputReceiptCutoverReady: ownerInputReceipt.cutoverReady,
      ownerInputReceiptRequiredOwnerInputs: ownerInputReceipt.requiredOwnerInputs,
      ownerInputReceiptPendingOwnerCount: ownerInputReceipt.pendingOwnerCount,
      ownerInputReceiptMissingCriteriaCount: ownerInputReceipt.missingCriteria.length,
    },
    ownerInputReceipt,
    items,
  };
}

function releaseNextActionQueueMarkdown(summary) {
  const artifact = releaseNextActionQueueArtifact(summary);
  const lines = [
    "# DDD Release Next Action Queue",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Cutover allowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `Stop reasons: ${artifact.finalDecision.stopReasonCount}`,
    `releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`,
    `Run now: ${artifact.summary.runNowCount}`,
    `Waiting: ${artifact.summary.waitingCount}`,
    `Owner input receipt status: ${artifact.summary.ownerInputReceiptStatus}`,
    `Owner input receipt cutover ready: ${artifact.summary.ownerInputReceiptCutoverReady}`,
    `Owner input receipt required inputs: ${artifact.summary.ownerInputReceiptRequiredOwnerInputs}`,
    `Owner input receipt pending owners: ${artifact.summary.ownerInputReceiptPendingOwnerCount}`,
    "",
    "## Final Cutover Decision",
    "",
    `- finalRecommendation: ${artifact.finalDecision.finalRecommendation}`,
    `- cutoverAllowed: ${artifact.finalDecision.cutoverAllowed === true}`,
    `- releaseEnvFileCutoverSafe: ${artifact.finalDecision.releaseEnvFileCutoverSafe === true}`,
    `- gateBlockers: ${artifact.finalDecision.gateBlockers}`,
    `- blockedCutoverItems: ${artifact.finalDecision.blockedCutoverItems}`,
    `- stopReasonCount: ${artifact.finalDecision.stopReasonCount}`,
    `- stopReasonCoverage: ${artifact.finalDecision.stopReasonCoverage}`,
    `- cutoverAuthority: ${artifact.finalDecision.cutoverAuthority}`,
    `- requiresFinalGate: ${artifact.finalDecision.requiresFinalGate === true}`,
    `- source: ${artifact.finalDecision.source}`,
    `- enforceCommand: \`${artifact.finalDecision.enforceCommand}\``,
    "",
    "### Current Stop Reasons",
    "",
    ...(artifact.finalDecision.stopReasons || []).length > 0
      ? artifact.finalDecision.stopReasons.map((reason) => `- ${reason}`)
      : ["- none"],
    "",
    "## Owner Input Receipt",
    "",
    `- Status: ${artifact.ownerInputReceipt.status}`,
    `- Cutover ready: ${artifact.ownerInputReceipt.cutoverReady}`,
    `- Required owner inputs: ${artifact.ownerInputReceipt.requiredOwnerInputs}`,
    `- Owners: ${artifact.ownerInputReceipt.ownerCount}`,
    `- Pending owners: ${artifact.ownerInputReceipt.pendingOwnerCount}`,
    "- Missing criteria:",
    ...(artifact.ownerInputReceipt.missingCriteria.length > 0
      ? artifact.ownerInputReceipt.missingCriteria.map((criteria) => `  - ${criteria}`)
      : ["  - none"]),
    "- Pending owner inputs:",
    ...(artifact.ownerInputReceipt.pendingOwners.length > 0
      ? artifact.ownerInputReceipt.pendingOwners.map((owner) => `  - ${owner.owner}: required=${owner.requiredOwnerInputs} placeholders=${owner.remainingPlaceholders} missing=${owner.remainingMissing} packet=${owner.packetPath || "n/a"} handoff=${owner.handoffPath || "n/a"} checklist=${owner.itemChecklistPath || "n/a"}`)
      : ["  - none"]),
    "",
  ];
  for (const item of artifact.items || []) {
    lines.push(`## ${item.order}. ${item.owner}`, "");
    lines.push(`- Queue status: ${item.queueStatus}`);
    lines.push(`- Receipt status: ${item.receiptStatus}`);
    lines.push(`- Strict gate blockers: ${item.strictGateBlockerCount}`);
    lines.push(`- Ready batches: ${item.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${item.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Next action: ${item.nextAction}`);
    lines.push(`- Reason: ${item.reason}`);
    if (item.executableCommands.length > 0) {
      lines.push("- Executable commands:");
      for (const command of item.executableCommands) {
        lines.push(`  - \`${command}\``);
      }
    }
    if (item.envKeys.length > 0) {
      lines.push(`- Env keys: ${item.envKeys.join(", ")}`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseBlockerClosureKind(item, batch) {
  if (!batch?.canRunImmediately) {
    return "WAIT_FOR_DEPENDENCIES";
  }
  if ((item.envKeys || []).length > 0) {
    return "RUN_NOW_WITH_REAL_ENV";
  }
  if (/HTTPS|non-local|production-equivalent|Docker daemon|CI runner|real /i.test(`${item.reason || ""} ${item.action || ""}`)) {
    return "RUN_NOW_WITH_REAL_ENV";
  }
  return "RUN_NOW_LOCAL";
}

function releaseBlockerClosurePlanArtifact(summary, { display = true } = {}) {
  const nextActionQueue = releaseNextActionQueueArtifact(summary);
  const ownerInputReceipt = releaseOwnerInputReceiptQueueSummary(summary);
  const batches = summary.releaseActionBatches || [];
  const batchByItemId = new Map();
  for (const batch of batches) {
    for (const item of batch.items || []) {
      if (item.id) {
        batchByItemId.set(item.id, batch);
      }
    }
  }
  const items = (summary.releaseActionPriority || []).map((item, index) => {
    const batch = batchByItemId.get(item.id) || null;
    const rawCommands = orderedUniqueStrings([
      ...commandHintsForItem(item),
      ...(batch?.commands || []),
    ]);
    const commands = display ? redactedDisplayCommands(rawCommands) : rawCommands;
    const expectedArtifacts = sortedUniqueStrings([
      item.artifact ? (item.artifact.startsWith("artifacts/") ? item.artifact : `artifacts/ddd/${item.artifact}`) : null,
      ...(batch?.expectedArtifacts || []),
    ].filter(Boolean));
    const artifactReferences = expectedArtifacts.filter(isReleaseArtifactReference);
    const blockerHints = expectedArtifacts
      .filter((value) => !isReleaseArtifactReference(value))
      .map((value) => releaseBlockerHint(value, item));
    const closureKind = releaseBlockerClosureKind(item, batch);
    return {
      order: index + 1,
      closureKind,
      priority: item.priority || batch?.priority || "P3",
      source: item.source || batch?.source || "unknown",
      owner: item.owner || batch?.owner || "release-owner",
      id: item.id,
      reason: display ? redactReleaseEnvCommandForDisplay(item.reason || "") : (item.reason || ""),
      action: display ? redactReleaseEnvCommandForDisplay(item.action || "") : (item.action || ""),
      batchId: batch?.id || null,
      batchReady: batch?.canRunImmediately === true,
      dependencies: batch?.dependsOn || [],
      envKeys: sortedUniqueStrings(item.envKeys || []),
      commands,
      expectedArtifacts: artifactReferences,
      blockerHints,
      exitCriteria: batch?.exitCriteria || [],
    };
  });
  const byClosureKind = stableCountBy(items.map((item) => item.closureKind));
  const byOwner = stableCountBy(items.map((item) => item.owner));
  const runnableItems = items.filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES");
  const fastTrack = releaseFastTrackArtifact(summary);
  const runnableWaveMap = new Map();
  for (const item of runnableItems) {
    const key = item.batchId || `item-${item.id}`;
    if (!runnableWaveMap.has(key)) {
      runnableWaveMap.set(key, {
        wave: runnableWaveMap.size + 1,
        batchId: item.batchId,
        owner: item.owner,
        priority: item.priority,
        closureKinds: [],
        itemOrders: [],
        itemIds: [],
        envKeys: [],
        commands: [],
        expectedArtifacts: [],
        blockerHints: [],
        exitCriteria: [],
      });
    }
    const wave = runnableWaveMap.get(key);
    wave.closureKinds.push(item.closureKind);
    wave.itemOrders.push(item.order);
    wave.itemIds.push(item.id);
    wave.envKeys.push(...item.envKeys);
    wave.commands.push(...item.commands);
    wave.expectedArtifacts.push(...item.expectedArtifacts);
    wave.blockerHints.push(...item.blockerHints);
    wave.exitCriteria.push(...item.exitCriteria);
  }
  const waves = [...runnableWaveMap.values()].map((wave) => ({
    ...wave,
    closureKinds: sortedUniqueStrings(wave.closureKinds),
    itemIds: sortedUniqueStrings(wave.itemIds),
    envKeys: sortedUniqueStrings(wave.envKeys),
    commands: display ? redactedDisplayCommands(wave.commands) : orderedUniqueStrings(wave.commands),
    expectedArtifacts: sortedUniqueStrings(wave.expectedArtifacts),
    blockerHints: sortedUniqueStrings(wave.blockerHints),
    exitCriteria: sortedUniqueStrings(wave.exitCriteria),
  }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: fastTrack.recommendation,
    noAutoWaivers: true,
    safetySignals: nextActionQueue.safetySignals,
    releaseEnvFileCutoverSafe: nextActionQueue.releaseEnvFileCutoverSafe,
    summary: {
      itemCount: items.length,
      runNowLocalCount: byClosureKind.RUN_NOW_LOCAL || 0,
      runNowWithRealEnvCount: byClosureKind.RUN_NOW_WITH_REAL_ENV || 0,
      waitingForDependenciesCount: byClosureKind.WAIT_FOR_DEPENDENCIES || 0,
      runnableWaveCount: waves.length,
      ownerCount: Object.keys(byOwner).length,
      ownerInputReceiptStatus: ownerInputReceipt.status,
      ownerInputReceiptCutoverReady: ownerInputReceipt.cutoverReady,
      ownerInputReceiptRequiredOwnerInputs: ownerInputReceipt.requiredOwnerInputs,
      ownerInputReceiptPendingOwnerCount: ownerInputReceipt.pendingOwnerCount,
      ownerInputReceiptMissingCriteriaCount: ownerInputReceipt.missingCriteria.length,
    },
    ownerInputReceipt,
    byClosureKind,
    byOwner,
    waves,
    items,
  };
}

function releaseBlockerHint(value, item = {}) {
  const text = String(value || "").trim();
  const manifestMatch = text.match(/^(?:artifacts\/ddd\/)?manifest provenance (sourceEnvironment|releaseCandidate|evidenceOperator) is required$/);
  if (manifestMatch) {
    return [
      `artifacts/ddd/release/evidence-manifest.json requires ${manifestMatch[1]}`,
      "set DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_CANDIDATE, and DDD_EVIDENCE_OPERATOR",
      "run DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs",
    ].join("; ");
  }
  if (item?.source === "manifest" && text) {
    return `artifacts/ddd/release/evidence-manifest.json blocker: ${text}`;
  }
  return text;
}

function releaseBlockerClosurePlanMarkdown(summary) {
  const artifact = releaseBlockerClosurePlanArtifact(summary);
  const lines = [
    "# DDD Release Blocker Closure Plan",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`,
    `RUN_NOW_LOCAL: ${artifact.summary.runNowLocalCount}`,
    `RUN_NOW_WITH_REAL_ENV: ${artifact.summary.runNowWithRealEnvCount}`,
    `WAIT_FOR_DEPENDENCIES: ${artifact.summary.waitingForDependenciesCount}`,
    `Runnable waves: ${artifact.summary.runnableWaveCount}`,
    `Owner input receipt status: ${artifact.summary.ownerInputReceiptStatus}`,
    `Owner input receipt cutover ready: ${artifact.summary.ownerInputReceiptCutoverReady}`,
    `Owner input receipt required inputs: ${artifact.summary.ownerInputReceiptRequiredOwnerInputs}`,
    `Owner input receipt pending owners: ${artifact.summary.ownerInputReceiptPendingOwnerCount}`,
    "",
    "## Owner Input Receipt",
    "",
    `- Status: ${artifact.ownerInputReceipt.status}`,
    `- Cutover ready: ${artifact.ownerInputReceipt.cutoverReady}`,
    `- Required owner inputs: ${artifact.ownerInputReceipt.requiredOwnerInputs}`,
    `- Owners: ${artifact.ownerInputReceipt.ownerCount}`,
    `- Pending owners: ${artifact.ownerInputReceipt.pendingOwnerCount}`,
    "- Missing criteria:",
    ...(artifact.ownerInputReceipt.missingCriteria.length > 0
      ? artifact.ownerInputReceipt.missingCriteria.map((criteria) => `  - ${criteria}`)
      : ["  - none"]),
    "- Pending owner inputs:",
    ...(artifact.ownerInputReceipt.pendingOwners.length > 0
      ? artifact.ownerInputReceipt.pendingOwners.map((owner) => `  - ${owner.owner}: required=${owner.requiredOwnerInputs} placeholders=${owner.remainingPlaceholders} missing=${owner.remainingMissing} packet=${owner.packetPath || "n/a"} handoff=${owner.handoffPath || "n/a"}`)
      : ["  - none"]),
    "",
    "## Runnable Waves",
    "",
  ];
  for (const wave of artifact.waves || []) {
    lines.push(`### Wave ${wave.wave}. ${wave.owner} / ${wave.batchId || "single item"}`, "");
    lines.push(`- Priority: ${wave.priority}`);
    lines.push(`- Closure kinds: ${wave.closureKinds.join(", ") || "none"}`);
    lines.push(`- Item orders: ${wave.itemOrders.join(", ") || "none"}`);
    lines.push(`- Item ids: ${wave.itemIds.join(", ") || "none"}`);
    if (wave.envKeys.length > 0) {
      lines.push(`- Env keys: ${wave.envKeys.join(", ")}`);
    }
    if (wave.commands.length > 0) {
      lines.push("- Commands:");
      for (const command of wave.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    if (wave.expectedArtifacts.length > 0) {
      lines.push("- Expected artifacts:");
      for (const artifactPath of wave.expectedArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    lines.push("");
  }
  lines.push("## Items", "");
  for (const item of artifact.items || []) {
    lines.push(`## ${item.order}. ${item.owner} / ${item.id}`, "");
    lines.push(`- Closure kind: ${item.closureKind}`);
    lines.push(`- Priority: ${item.priority}`);
    lines.push(`- Source: ${item.source}`);
    lines.push(`- Batch: ${item.batchId || "none"} (${item.batchReady ? "ready" : "blocked"})`);
    lines.push(`- Dependencies: ${item.dependencies.join(", ") || "none"}`);
    lines.push(`- Reason: ${item.reason}`);
    lines.push(`- Action: ${item.action}`);
    if (item.envKeys.length > 0) {
      lines.push(`- Env keys: ${item.envKeys.join(", ")}`);
    }
    if (item.commands.length > 0) {
      lines.push("- Commands:");
      for (const command of item.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    if (item.expectedArtifacts.length > 0) {
      lines.push("- Expected artifacts:");
      for (const artifactPath of item.expectedArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseBlockerClosurePlanCsv(summary) {
  const artifact = releaseBlockerClosurePlanArtifact(summary);
  const rows = [[
    "order",
    "closureKind",
    "priority",
    "source",
    "owner",
    "id",
    "batchId",
    "batchReady",
    "dependencies",
    "envKeys",
    "commands",
    "expectedArtifacts",
    "reason",
    "action",
  ]];
  for (const item of artifact.items || []) {
    rows.push([
      item.order,
      item.closureKind,
      item.priority,
      item.source,
      item.owner,
      item.id,
      item.batchId || "",
      item.batchReady,
      item.dependencies,
      item.envKeys,
      item.commands,
      item.expectedArtifacts,
      item.reason,
      item.action,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseBlockerClosureCommands(summary) {
  const artifact = releaseBlockerClosurePlanArtifact(summary, { display: false });
  const runnableItems = (artifact.items || []).filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release blocker closure commands."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine(`Status: ${summary.status}`),
    shellCommentLine(`Release gate blockers: ${summary.gate?.blockers ?? 0}`),
    shellCommentLine("Default mode lists runnable closure items. Set DDD_RELEASE_CLOSURE_EXECUTE=1 to execute commands."),
    shellCommentLine("Use DDD_RELEASE_CLOSURE_ORDER, DDD_RELEASE_CLOSURE_OWNER, DDD_RELEASE_CLOSURE_PRIORITY, or DDD_RELEASE_CLOSURE_KIND to filter."),
    ...releaseRepoRootPreambleLines(),
    "",
    "DDD_RELEASE_CLOSURE_ORDER=\"${DDD_RELEASE_CLOSURE_ORDER:-}\"",
    "DDD_RELEASE_CLOSURE_OWNER=\"${DDD_RELEASE_CLOSURE_OWNER:-}\"",
    "DDD_RELEASE_CLOSURE_PRIORITY=\"${DDD_RELEASE_CLOSURE_PRIORITY:-}\"",
    "DDD_RELEASE_CLOSURE_KIND=\"${DDD_RELEASE_CLOSURE_KIND:-}\"",
    "DDD_RELEASE_CLOSURE_DETAIL=\"${DDD_RELEASE_CLOSURE_DETAIL:-}\"",
    "DDD_RELEASE_CLOSURE_CHECK_ENV=\"${DDD_RELEASE_CLOSURE_CHECK_ENV:-}\"",
    "DDD_RELEASE_CLOSURE_EXECUTE=\"${DDD_RELEASE_CLOSURE_EXECUTE:-}\"",
    "DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=\"${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR:-}\"",
    "DDD_RELEASE_CLOSURE_MATCHED=0",
    "DDD_RELEASE_CLOSURE_COMMAND_FAILURES=0",
    "if [[ \"${DDD_RELEASE_CLOSURE_EXECUTE}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_EXECUTE}\" == \"true\" || \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"true\" ]]; then",
    "  if [[ -z \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE is required when executing or checking closure env.\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" ]]; then",
    "    echo \"Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' \"${DDD_RELEASE_ENV_FILE}\" 2>/dev/null || node -e \"const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));\" \"${DDD_RELEASE_ENV_FILE}\")",
    "  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then",
    "    echo \"Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600.\" >&2",
    "    exit 1",
    "  fi",
    "  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1",
    "fi",
    ...safeReleaseEnvLoaderLines(),
    "if [[ \"${DDD_RELEASE_CLOSURE_EXECUTE}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_EXECUTE}\" == \"true\" || \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"true\" ]]; then",
    "  safe_load_release_env_file",
    "fi",
    "matches_closure_filter() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  local priority=\"$3\"",
    "  local kind=\"$4\"",
    "  if [[ -n \"${DDD_RELEASE_CLOSURE_ORDER}\" && \"${DDD_RELEASE_CLOSURE_ORDER}\" != \"${order}\" ]]; then return 1; fi",
    "  if [[ -n \"${DDD_RELEASE_CLOSURE_OWNER}\" && \"${DDD_RELEASE_CLOSURE_OWNER}\" != \"${owner}\" ]]; then return 1; fi",
    "  if [[ -n \"${DDD_RELEASE_CLOSURE_PRIORITY}\" && \"${DDD_RELEASE_CLOSURE_PRIORITY}\" != \"${priority}\" ]]; then return 1; fi",
    "  if [[ -n \"${DDD_RELEASE_CLOSURE_KIND}\" && \"${DDD_RELEASE_CLOSURE_KIND}\" != \"${kind}\" ]]; then return 1; fi",
    "  return 0",
    "}",
    "check_closure_env() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  shift 2",
    "  local missing=0",
    "  local key",
    "  if [[ \"$#\" -eq 0 ]]; then",
    "    echo \"[ddd-release-closure][env-ok] order=${order} owner=${owner} requiredEnv=none\"",
    "    return 0",
    "  fi",
    "  for key in \"$@\"; do",
    "    if [[ -z \"${!key:-}\" ]]; then",
    "      echo \"[ddd-release-closure][env-missing] order=${order} owner=${owner} key=${key}\" >&2",
    "      missing=1",
    "    fi",
    "  done",
    "  if [[ \"${missing}\" == \"0\" ]]; then",
    "    echo \"[ddd-release-closure][env-ok] order=${order} owner=${owner}\"",
    "  fi",
    "  return \"${missing}\"",
    "}",
    "run_closure_command() {",
    "  local command=\"$1\"",
    "  if [[ \"${DDD_RELEASE_CLOSURE_EXECUTE}\" != \"1\" && \"${DDD_RELEASE_CLOSURE_EXECUTE}\" != \"true\" ]]; then",
    "    echo \"[ddd-release-closure][dry-run] ${command}\"",
    "    return 0",
    "  fi",
    "  set +e",
    "  bash -lc \"${command}\"",
    "  local status=$?",
    "  set -e",
    "  if [[ \"${status}\" != \"0\" ]]; then",
    "    echo \"[ddd-release-closure][command-failed] status=${status} command=${command}\" >&2",
    "    DDD_RELEASE_CLOSURE_COMMAND_FAILURES=$((DDD_RELEASE_CLOSURE_COMMAND_FAILURES + 1))",
    "    if [[ \"${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}\" == \"true\" ]]; then",
    "      echo \"[ddd-release-closure][command-failed] continuing because DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}\" >&2",
    "      return 0",
    "    fi",
    "    return \"${status}\"",
    "  fi",
    "  return 0",
    "}",
    "",
  ];
  if (runnableItems.length === 0) {
    lines.push(shellCommentLine("No runnable closure items."));
    return `${lines.join("\n")}\n`;
  }
  const runnableWaves = artifact.waves || [];
  lines.push("if [[ \"${DDD_RELEASE_CLOSURE_DETAIL}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_DETAIL}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_CLOSURE_DETAIL_MATCHED=0");
  for (const item of runnableItems) {
    lines.push(`  if matches_closure_filter ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.priority)} ${shellSingleQuoted(item.closureKind)}; then`);
    lines.push("    DDD_RELEASE_CLOSURE_DETAIL_MATCHED=1");
    lines.push(`    echo ${shellSingleQuoted(`order=${item.order}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`owner=${item.owner}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`priority=${item.priority}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`closureKind=${item.closureKind}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`id=${item.id}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`batch=${item.batchId || "none"}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`reason=${item.reason}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`envKeys=${(item.envKeys || []).join(";") || "none"}`)}`);
    lines.push("    echo \"commands:\"");
    for (const command of item.commands || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${command}`)}`);
    }
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_CLOSURE_DETAIL_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No runnable closure item matched the requested filters.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"1\" || \"${DDD_RELEASE_CLOSURE_CHECK_ENV}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_CLOSURE_ENV_MATCHED=0");
  lines.push("  DDD_RELEASE_CLOSURE_ENV_FAILED=0");
  for (const item of runnableItems) {
    lines.push(`  if matches_closure_filter ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.priority)} ${shellSingleQuoted(item.closureKind)}; then`);
    lines.push("    DDD_RELEASE_CLOSURE_ENV_MATCHED=1");
    const envKeys = sortedUniqueStrings(item.envKeys || []);
    if (envKeys.length > 0) {
      lines.push(`    check_closure_env ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${envKeys.map(shellSingleQuoted).join(" ")} || DDD_RELEASE_CLOSURE_ENV_FAILED=1`);
    } else {
      lines.push(`    check_closure_env ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} || DDD_RELEASE_CLOSURE_ENV_FAILED=1`);
    }
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_CLOSURE_ENV_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No runnable closure item matched the requested filters.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  if [[ \"${DDD_RELEASE_CLOSURE_ENV_FAILED}\" != \"0\" ]]; then exit 1; fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_RELEASE_CLOSURE_EXECUTE}\" != \"1\" && \"${DDD_RELEASE_CLOSURE_EXECUTE}\" != \"true\" ]]; then");
  lines.push("  echo \"Runnable release blocker closure waves:\"");
  for (const wave of runnableWaves) {
    lines.push(`  if [[ ( -z "\${DDD_RELEASE_CLOSURE_OWNER}" || "\${DDD_RELEASE_CLOSURE_OWNER}" == ${shellSingleQuoted(wave.owner)} ) && ( -z "\${DDD_RELEASE_CLOSURE_PRIORITY}" || "\${DDD_RELEASE_CLOSURE_PRIORITY}" == ${shellSingleQuoted(wave.priority)} ) ]]; then`);
    lines.push(`    echo ${shellSingleQuoted(`${wave.wave} ${wave.priority} owner=${wave.owner} batch=${wave.batchId || "none"} items=${(wave.itemOrders || []).join(";")}`)}`);
    lines.push("  fi");
  }
  lines.push("  echo \"\"");
  lines.push("  echo \"Runnable release blocker closure items:\"");
  for (const item of runnableItems) {
    lines.push(`  if matches_closure_filter ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.priority)} ${shellSingleQuoted(item.closureKind)}; then`);
    lines.push(`    echo ${shellSingleQuoted(`${item.order} ${item.priority} ${item.closureKind} owner=${item.owner} id=${item.id} batch=${item.batchId || "none"}`)}`);
    lines.push("    DDD_RELEASE_CLOSURE_MATCHED=1");
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_CLOSURE_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No runnable closure item matched the requested filters.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  for (const item of runnableItems) {
    lines.push(`if matches_closure_filter ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.priority)} ${shellSingleQuoted(item.closureKind)}; then`);
    lines.push("  DDD_RELEASE_CLOSURE_MATCHED=1");
    lines.push(`  echo ${shellSingleQuoted(`[ddd-release-closure] running order=${item.order} owner=${item.owner} priority=${item.priority} kind=${item.closureKind} id=${item.id}`)}`);
    lines.push(shellCommentLine(`Reason: ${item.reason}`));
    if ((item.expectedArtifacts || []).length > 0) {
      lines.push(shellCommentLine(`Expected artifacts: ${(item.expectedArtifacts || []).join("; ")}`));
    }
    for (const command of item.commands || []) {
      lines.push(`  run_closure_command ${shellSingleQuoted(command)}`);
    }
    lines.push("fi");
    lines.push("");
  }
  lines.push("if [[ \"${DDD_RELEASE_CLOSURE_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No runnable closure item matched the requested filters.\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("");
  lines.push(shellCommentLine("After closure commands refresh artifacts, rerun:"));
  lines.push("run_closure_command 'node scripts/ddd-release-evidence-gate.mjs'");
  lines.push("run_closure_command 'node scripts/ddd-release-readiness-summary.mjs'");
  lines.push("run_closure_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'");
  lines.push("if [[ \"${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}\" != \"0\" ]]; then");
  lines.push("  echo \"[ddd-release-closure][completed-with-failures] commandFailures=${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  return `${lines.join("\n")}\n`;
}

function releaseClosureWaveEnvMatrixArtifact(summary) {
  const closurePlan = releaseBlockerClosurePlanArtifact(summary);
  const waves = (closurePlan.waves || []).map((wave) => ({
    wave: wave.wave,
    owner: wave.owner,
    batchId: wave.batchId,
    priority: wave.priority,
    closureKinds: wave.closureKinds || [],
    itemOrders: wave.itemOrders || [],
    itemIds: wave.itemIds || [],
    envKeyCount: (wave.envKeys || []).length,
    envKeys: wave.envKeys || [],
    commands: wave.commands || [],
    expectedArtifacts: wave.expectedArtifacts || [],
    blockerHints: wave.blockerHints || [],
    exitCriteria: wave.exitCriteria || [],
  }));
  const uniqueEnvKeys = sortedUniqueStrings(waves.flatMap((wave) => wave.envKeys || []));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: closurePlan.recommendation,
    noAutoWaivers: true,
    summary: {
      waveCount: waves.length,
      uniqueEnvKeyCount: uniqueEnvKeys.length,
      ownerCount: new Set(waves.map((wave) => wave.owner)).size,
    },
    uniqueEnvKeys,
    waves,
  };
}

function releaseClosureWaveEnvMatrixMarkdown(summary) {
  const artifact = releaseClosureWaveEnvMatrixArtifact(summary);
  const lines = [
    "# DDD Release Closure Wave Env Matrix",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Waves: ${artifact.summary.waveCount}`,
    `Unique env keys: ${artifact.summary.uniqueEnvKeyCount}`,
    "",
  ];
  for (const wave of artifact.waves || []) {
    lines.push(`## Wave ${wave.wave}. ${wave.owner} / ${wave.batchId || "single item"}`, "");
    lines.push(`- Priority: ${wave.priority}`);
    lines.push(`- Items: ${wave.itemOrders.join(", ") || "none"}`);
    lines.push(`- Item ids: ${wave.itemIds.join(", ") || "none"}`);
    lines.push(`- Env keys: ${wave.envKeyCount}`);
    for (const key of wave.envKeys || []) {
      lines.push(`  - \`${key}\``);
    }
    lines.push("- Commands:");
    for (const command of wave.commands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("- Expected artifacts:");
    for (const artifactPath of wave.expectedArtifacts || []) {
      lines.push(`  - \`${artifactPath}\``);
    }
    if ((wave.blockerHints || []).length > 0) {
      lines.push("- Blocker hints:");
      for (const hint of wave.blockerHints || []) {
        lines.push(`  - ${hint}`);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseClosureWaveEnvMatrixCsv(summary) {
  const artifact = releaseClosureWaveEnvMatrixArtifact(summary);
  const rows = [[
    "wave",
    "owner",
    "batchId",
    "priority",
    "closureKinds",
    "itemOrders",
    "itemIds",
    "envKeyCount",
    "envKeys",
    "commands",
    "expectedArtifacts",
    "blockerHints",
  ]];
  for (const wave of artifact.waves || []) {
    rows.push([
      wave.wave,
      wave.owner,
      wave.batchId || "",
      wave.priority,
      wave.closureKinds,
      wave.itemOrders,
      wave.itemIds,
      wave.envKeyCount,
      wave.envKeys,
      wave.commands,
      wave.expectedArtifacts,
      wave.blockerHints,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseClosureWaveEnvTemplate(summary) {
  const artifact = releaseClosureWaveEnvMatrixArtifact(summary);
  const lines = [
    "# Lumira DDD closure wave release environment template.",
    "# Fill real values in a secure file and point DDD_RELEASE_ENV_FILE at it.",
    "# Do not commit populated secrets.",
    `# Generated at: ${artifact.generatedAt}`,
    `# Status: ${artifact.status}`,
    `# Recommendation: ${artifact.recommendation}`,
    "",
  ];
  const emitted = new Set();
  for (const wave of artifact.waves || []) {
    lines.push(`# Wave ${wave.wave}: ${wave.owner} / ${wave.batchId || "single item"}`);
    lines.push(`# Items: ${wave.itemIds.join(", ") || "none"}`);
    lines.push(`# Commands: ${(wave.commands || []).join("; ") || "none"}`);
    for (const key of wave.envKeys || []) {
      if (emitted.has(key)) {
        lines.push(`# ${key}=__REQUIRED__ # already declared above`);
      } else {
        lines.push(`${key}=__REQUIRED__`);
        emitted.add(key);
      }
    }
    lines.push("");
  }
  lines.push("# Usage:");
  lines.push("# export DDD_RELEASE_ENV_FILE=/secure/path/to/.env.release");
  lines.push("# DDD_RELEASE_CLOSURE_CHECK_ENV=1 bash artifacts/ddd/release/release-blocker-closure-commands.sh");
  lines.push("# DDD_RELEASE_CLOSURE_EXECUTE=1 bash artifacts/ddd/release/release-blocker-closure-commands.sh");
  lines.push("# DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1 DDD_RELEASE_CLOSURE_EXECUTE=1 bash artifacts/ddd/release/release-blocker-closure-commands.sh # diagnostic only; final exit remains non-zero on failures");
  return `${lines.join("\n")}\n`;
}

function releaseClosureWaveReceiptsArtifact(summary) {
  const matrix = releaseClosureWaveEnvMatrixArtifact(summary);
  const waves = (matrix.waves || []).map((wave) => {
    const artifactReceipts = (wave.expectedArtifacts || []).map((relativePath) => {
      const absolutePath = path.isAbsolute(relativePath) ? relativePath : path.join(repoRoot, relativePath);
      return {
        relativePath,
        absolutePath,
        exists: fs.existsSync(absolutePath),
      };
    });
    const missingArtifacts = artifactReceipts.filter((artifact) => !artifact.exists).map((artifact) => artifact.relativePath);
    const presentArtifacts = artifactReceipts.filter((artifact) => artifact.exists).map((artifact) => artifact.relativePath);
    const blockerHints = wave.blockerHints || [];
    const receiptStatus = missingArtifacts.length > 0
      ? "ARTIFACT_MISSING"
      : blockerHints.length > 0
        ? "CONTENT_BLOCKED"
        : "READY_FOR_STRICT_GATE_RERUN";
    return {
      wave: wave.wave,
      owner: wave.owner,
      batchId: wave.batchId,
      priority: wave.priority,
      receiptStatus,
      itemOrders: wave.itemOrders || [],
      itemIds: wave.itemIds || [],
      expectedArtifactCount: artifactReceipts.length,
      presentArtifactCount: presentArtifacts.length,
      missingArtifactCount: missingArtifacts.length,
      presentArtifacts,
      missingArtifacts,
      blockerHints,
      commands: wave.commands || [],
      exitCriteria: wave.exitCriteria || [],
      nextCheck: receiptStatus === "READY_FOR_STRICT_GATE_RERUN"
        ? "Rerun strict release gate and readiness summary; this wave's expected artifacts are present."
        : receiptStatus === "CONTENT_BLOCKED"
          ? "Regenerate this wave's content/provenance evidence, then rerun strict release gate and readiness summary."
          : "Run this wave in a production-equivalent environment, then rerun strict release gate and readiness summary.",
      rerunCommands: [
        "node scripts/ddd-release-evidence-gate.mjs",
        "node scripts/ddd-release-readiness-summary.mjs",
      ],
    };
  });
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: matrix.recommendation,
    noAutoWaivers: true,
    summary: {
      waveCount: waves.length,
      readyForStrictGateRerunCount: waves.filter((wave) => wave.receiptStatus === "READY_FOR_STRICT_GATE_RERUN").length,
      artifactMissingCount: waves.filter((wave) => wave.receiptStatus === "ARTIFACT_MISSING").length,
      contentBlockedCount: waves.filter((wave) => wave.receiptStatus === "CONTENT_BLOCKED").length,
      expectedArtifactCount: waves.reduce((sum, wave) => sum + wave.expectedArtifactCount, 0),
      presentArtifactCount: waves.reduce((sum, wave) => sum + wave.presentArtifactCount, 0),
      missingArtifactCount: waves.reduce((sum, wave) => sum + wave.missingArtifactCount, 0),
    },
    waves,
  };
}

function releaseClosureWaveReceiptsMarkdown(summary) {
  const artifact = releaseClosureWaveReceiptsArtifact(summary);
  const lines = [
    "# DDD Release Closure Wave Receipts",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Ready for strict gate rerun: ${artifact.summary.readyForStrictGateRerunCount}`,
    `Artifact missing: ${artifact.summary.artifactMissingCount}`,
    `Content blocked: ${artifact.summary.contentBlockedCount}`,
    `Missing artifacts: ${artifact.summary.missingArtifactCount}`,
    "",
  ];
  for (const wave of artifact.waves || []) {
    lines.push(`## Wave ${wave.wave}. ${wave.owner} / ${wave.batchId || "single item"}`, "");
    lines.push(`- Receipt status: ${wave.receiptStatus}`);
    lines.push(`- Expected artifacts: ${wave.expectedArtifactCount}`);
    lines.push(`- Present artifacts: ${wave.presentArtifactCount}`);
    lines.push(`- Missing artifacts: ${wave.missingArtifactCount}`);
    if (wave.missingArtifacts.length > 0) {
      lines.push("- Missing artifact paths:");
      for (const artifactPath of wave.missingArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    if ((wave.blockerHints || []).length > 0) {
      lines.push("- Blocker hints:");
      for (const hint of wave.blockerHints || []) {
        lines.push(`  - ${hint}`);
      }
    }
    lines.push(`- Next check: ${wave.nextCheck}`);
    lines.push("- Rerun commands:");
    for (const command of wave.rerunCommands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseClosureWaveReceiptsCsv(summary) {
  const artifact = releaseClosureWaveReceiptsArtifact(summary);
  const rows = [[
    "wave",
    "owner",
    "batchId",
    "priority",
    "receiptStatus",
    "itemOrders",
    "itemIds",
    "expectedArtifactCount",
    "presentArtifactCount",
    "missingArtifactCount",
    "missingArtifacts",
    "blockerHints",
    "rerunCommands",
  ]];
  for (const wave of artifact.waves || []) {
    rows.push([
      wave.wave,
      wave.owner,
      wave.batchId || "",
      wave.priority,
      wave.receiptStatus,
      wave.itemOrders,
      wave.itemIds,
      wave.expectedArtifactCount,
      wave.presentArtifactCount,
      wave.missingArtifactCount,
      wave.missingArtifacts,
      wave.blockerHints || [],
      wave.rerunCommands,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function actionCategoryHint(source) {
  if (source === "release-env-lint") return "release-environment";
  if (source === "runtime-readiness") return "production-equivalent-runtime";
  if (source === "ai-remote-smoke") return "ai-runtime";
  if (source === "frontend-smoke") return "frontend-runtime";
  if (source === "rollback-drill") return "rollback";
  if (source === "performance-baseline") return "performance";
  if (source === "release-docker") return "release-infra";
  if (source === "migration-rollback") return "database-migration";
  return source || "unknown";
}

function releaseClosureWaveBlockerMapArtifact(summary) {
  const closurePlan = releaseBlockerClosurePlanArtifact(summary);
  const actionById = new Map((closurePlan.items || []).map((item) => [item.id, item]));
  const strictActions = Array.isArray(summary.actions) ? summary.actions : [];
  const waves = (closurePlan.waves || []).map((wave) => {
    const actions = (wave.itemIds || []).map((id) => actionById.get(id)).filter(Boolean);
    const sources = sortedUniqueStrings(actions.map((action) => action.source));
    const owners = sortedUniqueStrings(actions.map((action) => action.owner));
    const categoryHints = sortedUniqueStrings(sources.map(actionCategoryHint));
    const candidateBlockers = strictActions.filter((strictAction) => (
      owners.includes(strictAction.owner)
      && categoryHints.includes(strictAction.category)
    )).map((strictAction) => ({
      category: strictAction.category,
      owner: strictAction.owner,
      blocker: strictAction.blocker,
      action: strictAction.action,
    }));
    return {
      wave: wave.wave,
      owner: wave.owner,
      batchId: wave.batchId,
      priority: wave.priority,
      mappingConfidence: "candidate",
      mappingNote: "Mapped by closure action owner and source-derived category; strict gate remains authoritative.",
      actionCount: actions.length,
      sources,
      categoryHints,
      itemOrders: wave.itemOrders || [],
      itemIds: wave.itemIds || [],
      actionReasons: actions.map((action) => ({
        id: action.id,
        source: action.source,
        owner: action.owner,
        priority: action.priority,
        reason: action.reason,
        action: action.action,
      })),
      candidateBlockerCount: candidateBlockers.length,
      candidateBlockers,
      commands: wave.commands || [],
      expectedArtifacts: wave.expectedArtifacts || [],
      blockerHints: wave.blockerHints || [],
      exitCriteria: wave.exitCriteria || [],
      rerunCommands: [
        "node scripts/ddd-release-evidence-gate.mjs",
        "node scripts/ddd-release-readiness-summary.mjs",
      ],
    };
  });
  const mappedItemIds = sortedUniqueStrings(waves.flatMap((wave) => wave.itemIds || []));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: closurePlan.recommendation,
    noAutoWaivers: true,
    summary: {
      waveCount: waves.length,
      mappedActionCount: waves.reduce((sum, wave) => sum + wave.actionCount, 0),
      uniqueOwnerCount: new Set(waves.map((wave) => wave.owner)).size,
      candidateBlockerHintCount: waves.reduce((sum, wave) => sum + wave.candidateBlockerCount, 0),
      nonArtifactBlockerHintCount: waves.reduce((sum, wave) => sum + (wave.blockerHints || []).length, 0),
      mappedItemCount: mappedItemIds.length,
    },
    mappedItemIds,
    waves,
  };
}

function releaseClosureWaveBlockerMapMarkdown(summary) {
  const artifact = releaseClosureWaveBlockerMapArtifact(summary);
  const lines = [
    "# DDD Release Closure Wave Blocker Map",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Waves: ${artifact.summary.waveCount}`,
    `Mapped actions: ${artifact.summary.mappedActionCount}`,
    `Candidate blocker hints: ${artifact.summary.candidateBlockerHintCount}`,
    `Non-artifact blocker hints: ${artifact.summary.nonArtifactBlockerHintCount}`,
    "",
    "Candidate blockers are traceability hints only. The strict release evidence gate remains authoritative.",
    "",
  ];
  for (const wave of artifact.waves || []) {
    lines.push(`## Wave ${wave.wave}. ${wave.owner} / ${wave.batchId || "single item"}`, "");
    lines.push(`- Priority: ${wave.priority}`);
    lines.push(`- Sources: ${wave.sources.join(", ") || "none"}`);
    lines.push(`- Category hints: ${wave.categoryHints.join(", ") || "none"}`);
    lines.push(`- Item ids: ${wave.itemIds.join(", ") || "none"}`);
    lines.push(`- Candidate blocker hints: ${wave.candidateBlockerCount}`);
    if (wave.candidateBlockers.length > 0) {
      lines.push("- Candidate blockers:");
      for (const blocker of wave.candidateBlockers) {
        lines.push(`  - [${blocker.category}] ${blocker.blocker}`);
      }
    }
    lines.push("- Commands:");
    for (const command of wave.commands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("- Expected artifacts:");
    for (const artifactPath of wave.expectedArtifacts || []) {
      lines.push(`  - \`${artifactPath}\``);
    }
    if ((wave.blockerHints || []).length > 0) {
      lines.push("- Non-artifact blocker hints:");
      for (const hint of wave.blockerHints || []) {
        lines.push(`  - ${hint}`);
      }
    }
    lines.push("- Rerun commands:");
    for (const command of wave.rerunCommands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseClosureWaveBlockerMapCsv(summary) {
  const artifact = releaseClosureWaveBlockerMapArtifact(summary);
  const rows = [[
    "wave",
    "owner",
    "batchId",
    "priority",
    "mappingConfidence",
    "itemIds",
    "sources",
    "categoryHints",
    "candidateBlockerCount",
    "commands",
    "expectedArtifacts",
    "blockerHints",
    "rerunCommands",
  ]];
  for (const wave of artifact.waves || []) {
    rows.push([
      wave.wave,
      wave.owner,
      wave.batchId || "",
      wave.priority,
      wave.mappingConfidence,
      wave.itemIds,
      wave.sources,
      wave.categoryHints,
      wave.candidateBlockerCount,
      wave.commands,
      wave.expectedArtifacts,
      wave.blockerHints || [],
      wave.rerunCommands,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releasePerformanceBaselineClosureArtifact(summary) {
  const performance = summary.diagnostics?.authenticatedPerformance || {};
  const fastTrack = releaseFastTrackArtifact(summary);
  const closurePlan = releaseBlockerClosurePlanArtifact(summary);
  const performanceWaves = (closurePlan.waves || []).filter((wave) => wave.owner === "release-performance");
  const baselineFile = performance.baseline?.file
    ? (path.isAbsolute(performance.baseline.file) ? path.relative(repoRoot, performance.baseline.file) : performance.baseline.file)
    : "artifacts/ddd/performance/authenticated-runtime-baseline.json";
  const blockers = sortedUniqueStrings([
    ...(performance.actual?.shapeIssues || []),
    ...(performance.baseline?.shapeIssues || []),
    ...(performance.baseline?.metadataIssues || []),
    ...(performance.regressionIssues || []),
    ...(performance.baselinePromotion?.blockers || []),
    ...(!performance.baseline?.present ? [`missing authenticated performance baseline ${baselineFile}`] : []),
  ]);
  const readyToPromote = performance.actual?.present === true
    && performance.actual.localOnly !== true
    && performance.actual.failed === 0
    && (performance.actual.shapeIssues || []).length === 0
    && (performance.actual.productionEquivalence?.blockers || []).length === 0;
  const commands = [
    "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
    "DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs",
    "node scripts/ddd-promote-performance-baseline.mjs",
    "DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
    "DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs",
    "node scripts/ddd-release-evidence-gate.mjs",
    finalReadinessSummaryCommand,
    finalGoNoGoEnforceCommand,
  ];
  return {
    generatedAt: summary.generatedAt,
    status: blockers.length === 0 ? "READY_FOR_STRICT_GATE_RERUN" : "BLOCKED",
    recommendation: fastTrack.recommendation,
    noAutoWaivers: true,
    readyToPromote,
    productionEquivalenceRequired: {
      https: true,
      nonLocal: true,
      deploymentEvidence: true,
      noProductionEquivalenceIssues: true,
    },
    evidenceChecklist: requiredPerformanceBaselineEvidenceChecklist,
    nextCommand: readyToPromote
      ? "node scripts/ddd-promote-performance-baseline.mjs"
      : "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh",
    fastPath: {
      objective: "Capture authenticated hot-path performance from a production-equivalent HTTPS backend, promote it as baseline, then rerun final release gates.",
      blockedUntil: "authenticated-runtime-actual.json is generated from HTTPS non-local deployment evidence and baseline promotion succeeds.",
      commands,
    },
    actual: performance.actual || null,
    baseline: performance.baseline || null,
    promotion: performance.baselinePromotion || null,
    blockers,
    requiredEnvKeys: sortedUniqueStrings([
      "LUMIRA_BASE_URL",
      "BASE_URL",
      "DEPLOY_CHECK_BASE_URL",
      "DDD_AUTH_USERNAME",
      "DDD_AUTH_PASSWORD",
      "DDD_AUTH_PERF_ENVIRONMENT",
      "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE",
      "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
      "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
      "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
      "DDD_RELEASE_CANDIDATE",
      "DDD_EVIDENCE_OPERATOR",
    ]),
    commands,
    expectedArtifacts: [
      "artifacts/ddd/performance/authenticated-runtime-actual.json",
      "artifacts/ddd/performance/authenticated-runtime-baseline.json",
      "artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json",
      "artifacts/ddd/release/evidence-manifest.json",
    ],
    waves: performanceWaves,
  };
}

function releasePerformanceBaselineClosureMarkdown(summary) {
  const artifact = releasePerformanceBaselineClosureArtifact(summary);
  const lines = [
    "# DDD Release Performance Baseline Closure",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Ready to promote: ${artifact.readyToPromote}`,
    `Next command: ${artifact.nextCommand}`,
    "",
    "## Fast Path",
    "",
    `- Objective: ${artifact.fastPath.objective}`,
    `- Blocked until: ${artifact.fastPath.blockedUntil}`,
    "- Commands:",
  ];
  for (const command of artifact.fastPath.commands) {
    lines.push(`  - \`${command}\``);
  }
  lines.push(
    "",
    "## Production Equivalence Required",
    "",
    `- HTTPS: ${artifact.productionEquivalenceRequired.https}`,
    `- Non-local backend: ${artifact.productionEquivalenceRequired.nonLocal}`,
    `- Deployment evidence: ${artifact.productionEquivalenceRequired.deploymentEvidence}`,
    `- No production-equivalence issues: ${artifact.productionEquivalenceRequired.noProductionEquivalenceIssues}`,
    "",
    "## Evidence Checklist",
    "",
  );
  for (const item of artifact.evidenceChecklist) {
    lines.push(
      `### ${item.id}`,
      "",
      item.description,
      "",
      "- Required artifacts:",
    );
    for (const artifactPath of item.requiredArtifacts) {
      lines.push(`  - \`${artifactPath}\``);
    }
    lines.push("- Required fields:");
    for (const field of item.requiredFields) {
      lines.push(`  - \`${field}\``);
    }
    lines.push("- Required env keys:");
    for (const key of item.requiredEnvKeys) {
      lines.push(`  - \`${key}\``);
    }
    lines.push("- Acceptance criteria:");
    for (const criterion of item.acceptanceCriteria) {
      lines.push(`  - ${criterion}`);
    }
    lines.push("");
  }
  lines.push(
    "## Blockers",
    "",
  );
  if (artifact.blockers.length === 0) {
    lines.push("- None");
  } else {
    for (const blocker of artifact.blockers) {
      lines.push(`- ${blocker}`);
    }
  }
  lines.push("", "## Required Env Keys", "");
  for (const key of artifact.requiredEnvKeys) {
    lines.push(`- \`${key}\``);
  }
  lines.push("", "## Commands", "");
  for (const command of artifact.commands) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "## Expected Artifacts", "");
  for (const artifactPath of artifact.expectedArtifacts) {
    lines.push(`- \`${artifactPath}\``);
  }
  return `${lines.join("\n")}\n`;
}

function releasePerformanceBaselineCommands(summary) {
  const artifact = releasePerformanceBaselineClosureArtifact(summary);
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD authenticated performance baseline closure commands."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine(`Status: ${artifact.status}`),
    shellCommentLine(`Ready to promote: ${artifact.readyToPromote}`),
    ...releaseRepoRootPreambleLines(),
    "",
    "DDD_AUTH_PERF_BASELINE_DETAIL=\"${DDD_AUTH_PERF_BASELINE_DETAIL:-}\"",
    "DDD_AUTH_PERF_BASELINE_CHECK_ENV=\"${DDD_AUTH_PERF_BASELINE_CHECK_ENV:-}\"",
    "DDD_AUTH_PERF_BASELINE_EXECUTE=\"${DDD_AUTH_PERF_BASELINE_EXECUTE:-}\"",
    "if [[ \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" == \"true\" || \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"true\" ]]; then",
    "  if [[ -z \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE is required when executing or checking performance baseline env.\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" ]]; then",
    "    echo \"Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' \"${DDD_RELEASE_ENV_FILE}\" 2>/dev/null || node -e \"const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));\" \"${DDD_RELEASE_ENV_FILE}\")",
    "  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then",
    "    echo \"Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600.\" >&2",
    "    exit 1",
    "  fi",
    "  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1",
    "fi",
    ...safeReleaseEnvLoaderLines(),
    "if [[ \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" == \"true\" || \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"true\" ]]; then",
    "  safe_load_release_env_file",
    "fi",
    "check_required_env() {",
    "  local missing=0",
    "  local key",
    "  local value",
    "  for key in \"$@\"; do",
    "    value=\"${!key:-}\"",
    "    if [[ -z \"${value}\" ]]; then",
    "      echo \"[ddd-auth-perf-baseline][env-missing] key=${key}\" >&2",
    "      missing=1",
    "      continue",
    "    fi",
    "    if [[ \"${value}\" == \"__REQUIRED__\" || \"${value}\" == *\"replace-with\"* || \"${value}\" == *\"example.com\"* || \"${value}\" == *\"example.internal\"* ]]; then",
    "      echo \"[ddd-auth-perf-baseline][env-placeholder] key=${key}\" >&2",
    "      missing=1",
    "      continue",
    "    fi",
    "    if [[ \"${key}\" == \"BASE_URL\" || \"${key}\" == \"DEPLOY_CHECK_BASE_URL\" || \"${key}\" == \"LUMIRA_BASE_URL\" ]]; then",
    "      if [[ \"${value}\" != https://* ]]; then",
    "        echo \"[ddd-auth-perf-baseline][env-not-https] key=${key}\" >&2",
    "        missing=1",
    "        continue",
    "      fi",
    "      if [[ \"${value}\" == *\"localhost\"* || \"${value}\" == *\"127.0.0.1\"* || \"${value}\" == *\"[::1]\"* || \"${value}\" == *\"0.0.0.0\"* ]]; then",
    "        echo \"[ddd-auth-perf-baseline][env-local-url] key=${key}\" >&2",
    "        missing=1",
    "        continue",
    "      fi",
    "    fi",
    "  done",
    "  if [[ \"${missing}\" == \"0\" ]]; then",
    "    echo \"[ddd-auth-perf-baseline][env-ok]\"",
    "  fi",
    "  return \"${missing}\"",
    "}",
    "run_command() {",
    "  local command=\"$1\"",
    "  if [[ \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"1\" && \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"true\" ]]; then",
    "    echo \"[ddd-auth-perf-baseline][dry-run] ${command}\"",
    "    return 0",
    "  fi",
    "  bash -lc \"${command}\"",
    "}",
    "",
    "if [[ \"${DDD_AUTH_PERF_BASELINE_DETAIL}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_DETAIL}\" == \"true\" || ( \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" != \"1\" && \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" != \"true\" && \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"1\" && \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"true\" ) ]]; then",
    `  echo ${shellSingleQuoted(`status=${artifact.status}`)}`,
    `  echo ${shellSingleQuoted(`readyToPromote=${artifact.readyToPromote}`)}`,
    "  echo \"blockers:\"",
  ];
  for (const blocker of artifact.blockers || []) {
    lines.push(`  echo ${shellSingleQuoted(`- ${blocker}`)}`);
  }
  lines.push("  echo \"commands:\"");
  for (const command of artifact.commands || []) {
    lines.push(`  echo ${shellSingleQuoted(`- ${command}`)}`);
  }
  lines.push("  if [[ \"${DDD_AUTH_PERF_BASELINE_DETAIL}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_DETAIL}\" == \"true\" ]]; then exit 0; fi");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"1\" || \"${DDD_AUTH_PERF_BASELINE_CHECK_ENV}\" == \"true\" ]]; then");
  lines.push(`  check_required_env ${(artifact.requiredEnvKeys || []).map(shellSingleQuoted).join(" ")}`);
  lines.push("  exit $?");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"1\" && \"${DDD_AUTH_PERF_BASELINE_EXECUTE}\" != \"true\" ]]; then");
  for (const command of artifact.commands || []) {
    lines.push(`  run_command ${shellSingleQuoted(command)}`);
  }
  lines.push("  exit 0");
  lines.push("fi");
  for (const command of artifact.commands || []) {
    lines.push(`run_command ${shellSingleQuoted(command)}`);
  }
  return `${lines.join("\n")}\n`;
}

function releaseFinalGoNoGoArtifact(summary) {
  const fastTrack = releaseFastTrackArtifact(summary);
  const nextActionQueue = releaseNextActionQueueArtifact(summary);
  const firstOwnerAction = (nextActionQueue.items || [])[0] || null;
  const closurePlan = releaseBlockerClosurePlanArtifact(summary);
  const closureReceipts = releaseClosureWaveReceiptsArtifact(summary);
  const closureBlockerMap = releaseClosureWaveBlockerMapArtifact(summary);
  const performanceBaseline = releasePerformanceBaselineClosureArtifact(summary);
  const ownerInputReceipt = releaseOwnerInputReceiptArtifact(summary);
  const blockedCutoverItems = (fastTrack.cutoverChecklist || []).filter((item) => item.status !== "PASS");
  const waveBlockers = (closureReceipts.waves || []).filter((wave) => wave.receiptStatus !== "READY_FOR_STRICT_GATE_RERUN");
  const safetySignals = releaseSafetySignals(summary);
  const releaseEnvFileCutoverSafe = releaseEnvFileIsCutoverSafe(safetySignals.releaseEnvFile);
  const cutoverAllowed = fastTrack.recommendation === "GO_STRICT"
    && fastTrack.noAutoWaivers === true
    && releaseEnvFileCutoverSafe
    && (summary.gate?.blockers ?? 0) === 0
    && blockedCutoverItems.length === 0
    && waveBlockers.length === 0
    && performanceBaseline.status === "READY"
    && ownerInputReceipt.cutoverReady === true;
  const currentStopReasons = sortedUniqueStrings([
    ...(fastTrack.recommendation !== "GO_STRICT" ? [`strict release gate blockers=${summary.gate?.blockers ?? 0}`] : []),
    ...(ownerInputReceipt.cutoverReady !== true ? [`owner input receipt pending: ${ownerInputReceipt.missingCriteria.join(",") || ownerInputReceipt.status}`] : []),
    ...blockedCutoverItems.map((item) => `cutover checklist blocked: ${item.id}`),
    ...waveBlockers.map((wave) => `closure wave ${wave.receiptStatus}: wave ${wave.wave} ${wave.owner}/${wave.batchId || "single item"}`),
    ...waveBlockers.flatMap((wave) => (wave.blockerHints || []).map((hint) => `closure wave ${wave.wave} blocker hint: ${releaseBlockerHint(hint)}`)),
    ...(performanceBaseline.status !== "READY" ? [`authenticated performance baseline not ready: ${performanceBaseline.status}`] : []),
  ]);
  const releaseEnvFileCommandPath = safetySignals.releaseEnvFile.envFile
    ? (path.isAbsolute(safetySignals.releaseEnvFile.envFile) && safetySignals.releaseEnvFile.envFile.startsWith(`${repoRoot}${path.sep}`)
      ? path.relative(repoRoot, safetySignals.releaseEnvFile.envFile)
      : safetySignals.releaseEnvFile.envFile)
    : null;
  const releaseEnvBootstrapCommands = safetySignals.releaseEnvFile.envFilePresent === true
    ? [
      "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} bash artifacts/ddd/release/release-env-bootstrap.sh`,
      "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${releaseEnvFileCommandPath}`,
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} ${releaseEnvSafeDefaultsCommand}`,
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} ${releaseProvenanceDefaultsCommand}`,
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} node scripts/ddd-release-env-alias-sync.mjs`,
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`,
      `DDD_RELEASE_ENV_FILE=${releaseEnvFileCommandPath} node scripts/ddd-release-env-file-lint.mjs`,
    ]
    : ["bash artifacts/ddd/release/release-final-owner-queue-env-init.sh"];
  const nextActionQueueCommands = (nextActionQueue.items || [])
    .filter((item) => item.queueStatus === "RUN_NOW")
    .flatMap((item) => item.executableCommands || []);
  const nextCommands = orderedUniqueStrings([
    "bash artifacts/ddd/release/release-preflight-gate.sh",
    "bash artifacts/ddd/release/release-artifact-integrity-gate.sh",
    ...releaseEnvBootstrapCommands,
    ...nextActionQueueCommands,
    ...(performanceBaseline.commands || []),
    ...((summary.releaseActionBatches || []).flatMap((batch) => batch.commands || [])),
    ...((closurePlan.waves || []).flatMap((wave) => wave.commands || [])),
    "node scripts/ddd-release-evidence-gate.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
  ].map(redactReleaseEnvCommandForDisplay));
  const stopOwners = sortedUniqueStrings([
    ...blockedCutoverItems.flatMap((item) => [...(item.readyBatchIds || []), ...(item.blockedBatchIds || [])]
      .map((batchId) => (summary.releaseActionBatches || []).find((batch) => batch.id === batchId)?.owner)
      .filter(Boolean)),
    ...waveBlockers.map((wave) => wave.owner),
    ...(performanceBaseline.status !== "READY" ? ["release-performance"] : []),
    ...((summary.gate?.blockers ?? 0) > 0 ? ["release-owner"] : []),
  ]);
  const blockedArtifactPaths = sortedUniqueStrings([
    ...waveBlockers.flatMap((wave) => wave.missingArtifacts || []),
    ...(performanceBaseline.status !== "READY" ? (performanceBaseline.expectedArtifacts || []) : []),
  ]);
  const blockedContentHints = sortedUniqueStrings([
    ...waveBlockers.flatMap((wave) => (wave.blockerHints || []).map((hint) => releaseBlockerHint(hint))),
  ]);
  const releaseEnvReadiness = releaseEnvReadinessRedactedArtifact(summary);
  const releaseEnvOwnerHandoff = releaseEnvOwnerHandoffRedactedArtifact(summary);
  const configOwnerInputReconciliation = releaseConfigOwnerInputReconciliationArtifact(summary);
  const releaseEnvOwnerBlockerSummary = (releaseEnvOwnerHandoff.owners || [])
    .filter((owner) => (
      Number(owner.blockers || 0) > 0
      || Number(owner.placeholders || 0) > 0
      || Number(owner.missing || 0) > 0
    ))
    .map((owner) => ({
      owner: owner.owner,
      blockers: owner.blockers,
      placeholders: owner.placeholders,
      missing: owner.missing,
      secretKeys: owner.secretKeys,
      handoffPath: owner.handoffPath,
    }));
  const firstEnvOwnerAction = releaseEnvOwnerBlockerSummary.length > 0
    ? {
      owner: releaseEnvOwnerBlockerSummary[0].owner,
      blockers: releaseEnvOwnerBlockerSummary[0].blockers,
      placeholders: releaseEnvOwnerBlockerSummary[0].placeholders,
      missing: releaseEnvOwnerBlockerSummary[0].missing,
      secretKeys: releaseEnvOwnerBlockerSummary[0].secretKeys,
      handoffPath: releaseEnvOwnerBlockerSummary[0].handoffPath,
      nextCommand: "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    }
    : null;
  const orchestratorDiagnostics = summary.diagnostics?.orchestrator || null;
  const orchestratorPreflightOwnerSummary = Object.values(orchestratorDiagnostics?.actionPlan || {})
    .map((plan) => ({
      owner: plan.owner,
      pendingItems: plan.pendingItems || 0,
      envKeys: plan.envKeys || [],
      actions: (plan.items || [])
        .filter((item) => String(item.id || "").startsWith("orchestrator-preflight-"))
        .map((item) => ({
          id: item.id,
          checkId: item.checkId || null,
          reason: item.reason || null,
          envKeys: item.envKeys || [],
          action: item.action || null,
        })),
    }))
    .filter((plan) => plan.actions.length > 0)
    .sort((left, right) => right.actions.length - left.actions.length || left.owner.localeCompare(right.owner));
  const firstOrchestratorPreflightAction = orchestratorPreflightOwnerSummary.length > 0
    ? {
      owner: orchestratorPreflightOwnerSummary[0].owner,
      id: orchestratorPreflightOwnerSummary[0].actions[0].id,
      checkId: orchestratorPreflightOwnerSummary[0].actions[0].checkId,
      reason: orchestratorPreflightOwnerSummary[0].actions[0].reason,
      envKeys: orchestratorPreflightOwnerSummary[0].actions[0].envKeys,
      action: orchestratorPreflightOwnerSummary[0].actions[0].action,
      command: "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict",
    }
    : null;
  const firstOwnerActionCommand = firstOwnerAction?.executableCommands?.[0] || null;
  const firstOwnerActionDisplayCommand = redactReleaseEnvCommandForDisplay(firstOwnerActionCommand);
  const firstOwnerActionDisplayNextAction = redactReleaseEnvCommandForDisplay(firstOwnerAction?.nextAction || "");
  const firstOwnerActionDisplayReason = redactReleaseEnvCommandForDisplay(firstOwnerAction?.reason || "");
  const exitCodeMap = {
    finalNoGo: 10,
    finalPacketInvalid: 11,
    releaseEnvUnresolved: 21,
    releaseEnvInvalidPacket: 22,
  };
  const ciSummary = {
    enforceCommand: "DDD_RELEASE_PREFLIGHT_ENFORCE=1 bash artifacts/ddd/release/release-preflight-gate.sh",
    finalGoNoGoEnforceCommand: "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    nonGoExitCode: exitCodeMap.finalNoGo,
    exitCodeMap,
    stopReasonCount: currentStopReasons.length,
    stopOwners,
    blockedArtifactPaths,
    blockedContentHints,
    configOwnerInputReconciliation: {
      artifact: "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
      status: configOwnerInputReconciliation.status,
      configPlaceholderBlockers: configOwnerInputReconciliation.summary.configPlaceholderBlockers,
      uniqueConfigPlaceholderKeys: configOwnerInputReconciliation.summary.uniqueConfigPlaceholderKeys,
      ownerInputKeys: configOwnerInputReconciliation.summary.ownerInputKeys,
      mappedConfigPlaceholderKeys: configOwnerInputReconciliation.summary.mappedConfigPlaceholderKeys,
      unmappedConfigPlaceholderKeys: configOwnerInputReconciliation.summary.unmappedConfigPlaceholderKeys,
      duplicateConfigPlaceholderBlockers: configOwnerInputReconciliation.summary.duplicateConfigPlaceholderBlockers,
      ownerInputsWithoutConfigPlaceholder: configOwnerInputReconciliation.summary.ownerInputsWithoutConfigPlaceholder,
      issueCount: configOwnerInputReconciliation.issueCount,
    },
    ownerInputReceipt: {
      artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
      csv: "artifacts/ddd/release/release-owner-input-receipt.csv",
      itemsCsv: "artifacts/ddd/release/release-owner-input-receipt-items.csv",
      itemsMarkdown: "artifacts/ddd/release/release-owner-input-receipt-items.md",
      markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
      status: ownerInputReceipt.status,
      cutoverReady: ownerInputReceipt.cutoverReady,
      requiredOwnerInputs: ownerInputReceipt.summary.requiredOwnerInputs,
      ownerCount: ownerInputReceipt.summary.ownerCount,
      readyOwnerCount: ownerInputReceipt.summary.readyOwnerCount,
      pendingOwnerCount: ownerInputReceipt.summary.pendingOwnerCount,
      missingCriteria: ownerInputReceipt.missingCriteria,
    },
    releaseEnvReadiness: {
      artifact: "artifacts/ddd/release/release-env-readiness-redacted.json",
      ownerHandoff: "artifacts/ddd/release/release-env-owner-handoff-redacted.json",
      ownerHandoffCsv: "artifacts/ddd/release/release-env-owner-handoff-redacted.csv",
      ownerHandoffDir: "artifacts/ddd/release/release-env-owner-handoff-redacted",
      totalCanonicalKeys: releaseEnvReadiness.summary.totalCanonicalKeys,
      blockers: releaseEnvReadiness.summary.blockers,
      placeholders: releaseEnvReadiness.summary.placeholders,
      missing: releaseEnvReadiness.summary.missing,
      optionalEmpty: releaseEnvReadiness.summary.optionalEmpty,
      filledRedacted: releaseEnvReadiness.summary.filledRedacted,
      secretKeys: releaseEnvReadiness.summary.secretKeys,
      ownerCount: releaseEnvReadiness.summary.ownerCount,
      ownerBlockerSummary: releaseEnvOwnerBlockerSummary,
    },
    firstEnvOwnerAction,
    orchestratorPreflight: {
      artifact: "artifacts/ddd/release/orchestrator-report.json",
      mode: orchestratorDiagnostics?.mode || null,
      strict: orchestratorDiagnostics?.strict === true,
      status: orchestratorDiagnostics?.preflight?.status || null,
      blockers: orchestratorDiagnostics?.preflight?.blockers ?? 0,
      warnings: orchestratorDiagnostics?.preflight?.warnings ?? 0,
      selectedStepCount: orchestratorDiagnostics?.selectedStepCount ?? 0,
      executedResultCount: orchestratorDiagnostics?.executedResultCount ?? 0,
      blockerChecks: orchestratorDiagnostics?.blockerChecks || [],
      ownerActionSummary: orchestratorPreflightOwnerSummary,
    },
    firstOrchestratorPreflightAction,
    firstNextCommand: nextCommands[0] || null,
    firstOwnerAction: firstOwnerAction ? {
      order: firstOwnerAction.order,
      owner: firstOwnerAction.owner,
      strictGateBlockerCount: firstOwnerAction.strictGateBlockerCount,
      nextAction: firstOwnerAction.nextAction,
      reason: firstOwnerAction.reason,
      command: firstOwnerActionCommand,
      displayNextAction: firstOwnerActionDisplayNextAction,
      displayReason: firstOwnerActionDisplayReason,
      displayCommand: firstOwnerActionDisplayCommand || null,
      envKeys: firstOwnerAction.envKeys || [],
    } : null,
    firstOwnerActionCommand,
    firstOwnerActionDisplayCommand: firstOwnerActionDisplayCommand || null,
    rerunCommands: [
      "bash artifacts/ddd/release/release-preflight-gate.sh",
      "bash artifacts/ddd/release/release-artifact-integrity-gate.sh",
      "node scripts/ddd-release-evidence-gate.mjs",
      "node scripts/ddd-release-readiness-summary.mjs",
      "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    ],
  };
  const recommendation = cutoverAllowed ? "GO_STRICT" : "NO_GO_STRICT";
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation,
    finalRecommendation: recommendation,
    cutoverAllowed,
    noAutoWaivers: true,
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    summary: {
      cutoverChecklistItems: fastTrack.summary?.cutoverChecklistItems || 0,
      blockedCutoverItems: blockedCutoverItems.length,
      runnableClosureWaves: closurePlan.summary?.runnableWaveCount || 0,
      receiptReadyWaves: closureReceipts.summary?.readyForStrictGateRerunCount || 0,
      receiptMissingArtifactWaves: closureReceipts.summary?.artifactMissingCount || 0,
      receiptContentBlockedWaves: closureReceipts.summary?.contentBlockedCount || 0,
      mappedClosureActions: closureBlockerMap.summary?.mappedActionCount || 0,
      candidateBlockerHints: closureBlockerMap.summary?.candidateBlockerHintCount || 0,
      performanceBaselineStatus: performanceBaseline.status,
      ownerInputReceiptStatus: ownerInputReceipt.status,
      ownerInputReceiptCutoverReady: ownerInputReceipt.cutoverReady,
      ownerInputReceiptMissingCriteria: ownerInputReceipt.missingCriteria.length,
      stopReasons: currentStopReasons.length,
    },
    ciSummary,
    safetySignals,
    releaseEnvFileCutoverSafe,
    currentStopReasons,
    decisionRules: [
      "GO only when strict release gate has zero blockers.",
      "GO only when all cutover checklist items are PASS.",
      "GO only when closure wave receipts are ready for strict gate rerun.",
      "GO only when owner input receipt is PASS and cutoverReady=true.",
      "GO only when authenticated performance baseline is READY.",
      "GO only when the release env file is a completed release-env-file with checked chmod 600 permissions.",
      "No automatic waivers are allowed for security, migration, rollback, production-equivalence, database, performance, or final orchestrator evidence.",
    ],
    fastestSafePath: fastTrack.fastestSafePath || [],
    blockedCutoverItems: blockedCutoverItems.map((item) => ({
      id: item.id,
      title: item.title,
      pendingItems: item.pendingItems,
      readyBatchIds: item.readyBatchIds || [],
      blockedBatchIds: item.blockedBatchIds || [],
    })),
    closureWaves: (closureReceipts.waves || []).map((wave) => ({
      wave: wave.wave,
      owner: wave.owner,
      batchId: wave.batchId,
      priority: wave.priority,
      receiptStatus: wave.receiptStatus,
      missingArtifactCount: wave.missingArtifactCount,
      missingArtifacts: wave.missingArtifacts || [],
      blockerHints: wave.blockerHints || [],
      commands: (wave.commands || []).map(redactReleaseEnvCommandForDisplay),
      rerunCommands: (wave.rerunCommands || []).map(redactReleaseEnvCommandForDisplay),
    })),
    performanceBaseline: {
      status: performanceBaseline.status,
      readyToPromote: performanceBaseline.readyToPromote,
      blockers: performanceBaseline.blockers || [],
      requiredEnvKeys: performanceBaseline.requiredEnvKeys || [],
      commands: performanceBaseline.commands || [],
    },
    nextCommands,
    evidenceArtifacts: [
      "artifacts/ddd/release/release-evidence-gate.json",
      "artifacts/ddd/release/readiness-summary.json",
      "artifacts/ddd/release/release-fast-track.json",
      "artifacts/ddd/release/release-closure-wave-receipts.json",
      "artifacts/ddd/release/release-closure-wave-blocker-map.json",
      "artifacts/ddd/release/release-performance-baseline-closure.json",
      "artifacts/ddd/release/release-owner-input-receipt.json",
      "artifacts/ddd/release/release-owner-input-receipt.csv",
      "artifacts/ddd/release/release-owner-input-receipt-items.csv",
      "artifacts/ddd/release/release-owner-input-receipt-items.md",
    ],
  };
}

function releaseFinalGoNoGoMarkdown(summary) {
  const artifact = releaseFinalGoNoGoArtifact(summary);
  const lines = [
    "# DDD Final Go/No-Go Packet",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `Final recommendation: ${artifact.finalRecommendation}`,
    `Cutover allowed: ${artifact.cutoverAllowed}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Strict gate blockers: ${artifact.gate.blockers}`,
    `Blocked cutover items: ${artifact.summary.blockedCutoverItems}`,
    `Receipt missing artifact waves: ${artifact.summary.receiptMissingArtifactWaves}`,
    `Receipt content blocked waves: ${artifact.summary.receiptContentBlockedWaves}`,
    `Performance baseline status: ${artifact.summary.performanceBaselineStatus}`,
    `Owner input receipt status: ${artifact.summary.ownerInputReceiptStatus}`,
    `Owner input receipt cutover ready: ${artifact.summary.ownerInputReceiptCutoverReady}`,
    `CI non-GO exit code: ${artifact.ciSummary.nonGoExitCode}`,
    "",
    "## CI Summary",
    "",
    `- Enforce command: \`${artifact.ciSummary.enforceCommand}\``,
    `- Stop owners: ${artifact.ciSummary.stopOwners.join(", ") || "none"}`,
    `- First next command: ${artifact.ciSummary.firstNextCommand ? `\`${artifact.ciSummary.firstNextCommand}\`` : "none"}`,
    `- First owner action: ${artifact.ciSummary.firstOwnerAction ? `${artifact.ciSummary.firstOwnerAction.owner} - ${artifact.ciSummary.firstOwnerAction.displayNextAction || artifact.ciSummary.firstOwnerAction.nextAction}` : "none"}`,
    `- First owner action command: ${artifact.ciSummary.firstOwnerActionDisplayCommand ? `\`${artifact.ciSummary.firstOwnerActionDisplayCommand}\`` : "none"}`,
    `- Exit codes: finalNoGo=${artifact.ciSummary.exitCodeMap?.finalNoGo ?? artifact.ciSummary.nonGoExitCode}, finalPacketInvalid=${artifact.ciSummary.exitCodeMap?.finalPacketInvalid ?? 11}, releaseEnvUnresolved=${artifact.ciSummary.exitCodeMap?.releaseEnvUnresolved ?? 21}, releaseEnvInvalidPacket=${artifact.ciSummary.exitCodeMap?.releaseEnvInvalidPacket ?? 22}`,
    `- Blocked artifacts: ${artifact.ciSummary.blockedArtifactPaths.length}`,
    `- Blocked content hints: ${artifact.ciSummary.blockedContentHints.length}`,
    `- Release env readiness: blockers=${artifact.ciSummary.releaseEnvReadiness?.blockers ?? 0}, placeholders=${artifact.ciSummary.releaseEnvReadiness?.placeholders ?? 0}, owners=${artifact.ciSummary.releaseEnvReadiness?.ownerCount ?? 0}`,
    `- Owner input receipt: status=${artifact.ciSummary.ownerInputReceipt?.status || "missing"}, cutoverReady=${artifact.ciSummary.ownerInputReceipt?.cutoverReady === true}, inputs=${artifact.ciSummary.ownerInputReceipt?.requiredOwnerInputs ?? 0}, pendingOwners=${artifact.ciSummary.ownerInputReceipt?.pendingOwnerCount ?? 0}, missingCriteria=${(artifact.ciSummary.ownerInputReceipt?.missingCriteria || []).join("|") || "none"}`,
    `- Release env owner blockers: ${(artifact.ciSummary.releaseEnvReadiness?.ownerBlockerSummary || []).map((owner) => `${owner.owner}:${owner.blockers}`).join(", ") || "none"}`,
    `- First release env owner action: ${artifact.ciSummary.firstEnvOwnerAction ? `${artifact.ciSummary.firstEnvOwnerAction.owner} blockers=${artifact.ciSummary.firstEnvOwnerAction.blockers} file=${artifact.ciSummary.firstEnvOwnerAction.handoffPath}` : "none"}`,
    `- Orchestrator preflight: mode=${artifact.ciSummary.orchestratorPreflight?.mode || "missing"} status=${artifact.ciSummary.orchestratorPreflight?.status || "missing"} blockers=${artifact.ciSummary.orchestratorPreflight?.blockers ?? 0}`,
    `- Orchestrator preflight owners: ${(artifact.ciSummary.orchestratorPreflight?.ownerActionSummary || []).map((owner) => `${owner.owner}:${owner.actions.length}`).join(", ") || "none"}`,
    `- First orchestrator preflight action: ${artifact.ciSummary.firstOrchestratorPreflightAction ? `${artifact.ciSummary.firstOrchestratorPreflightAction.owner} ${artifact.ciSummary.firstOrchestratorPreflightAction.checkId || artifact.ciSummary.firstOrchestratorPreflightAction.id}` : "none"}`,
    `- Release env redacted handoff: ${artifact.ciSummary.releaseEnvReadiness?.ownerHandoffDir || "missing"}`,
    `- Release env redacted handoff CSV: ${artifact.ciSummary.releaseEnvReadiness?.ownerHandoffCsv || "missing"}`,
    "",
    "## Stop Reasons",
    "",
  ];
  if (artifact.currentStopReasons.length === 0) {
    lines.push("- none");
  } else {
    for (const reason of artifact.currentStopReasons) {
      lines.push(`- ${reason}`);
    }
  }
  lines.push("", "## Safety Signals", "");
  const releaseEnvFile = artifact.safetySignals?.releaseEnvFile || {};
  lines.push(`- releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`);
  lines.push(`- releaseEnvFile: ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || "missing"} inputKind=${releaseEnvFile.inputKind || "missing"} envFilePresent=${releaseEnvFile.envFilePresent === true}`);
  lines.push(`  - securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || "missing"} requiredMode=${releaseEnvFile.requiredMode || "600"} reason=${releaseEnvFile.reason || "missing"} permissionCheckSkipped=${releaseEnvFile.permissionCheckSkipped === true}`);
  lines.push(`  - safeDefaultsExhausted=${releaseEnvFile.safeDefaultsExhausted === true} blockingSafeDefaultAvailable=${releaseEnvFile.blockingSafeDefaultAvailable ?? 0} blockingRequiresOwnerInput=${releaseEnvFile.blockingRequiresOwnerInput ?? 0}`);
  lines.push(`  - ownerInputReasons=${Object.entries(releaseEnvFile.ownerInputReasonCounts || {}).map(([reason, count]) => `${reason}:${count}`).join(", ") || "none"}`);
  lines.push(`  - ownerInputOwners=${(releaseEnvFile.ownerInputOwners || []).map((owner) => `${owner.owner}:${owner.requiresOwnerInput}`).join(", ") || "none"}`);
  lines.push(`  - pendingActions=${(releaseEnvFile.pendingActionIds || []).join(", ") || "none"}`);
  lines.push("", "## Decision Rules", "");
  for (const rule of artifact.decisionRules || []) {
    lines.push(`- ${rule}`);
  }
  lines.push("", "## Fastest Safe Path", "");
  for (const [index, step] of (artifact.fastestSafePath || []).entries()) {
    lines.push(`${index + 1}. ${step}`);
  }
  lines.push("", "## Blocked Cutover Items", "");
  for (const item of artifact.blockedCutoverItems || []) {
    lines.push(`- ${item.id}: ${item.title}`);
    lines.push(`  - Pending items: ${item.pendingItems}`);
    if (item.readyBatchIds.length > 0) {
      lines.push(`  - Ready batches: ${item.readyBatchIds.join(", ")}`);
    }
    if (item.blockedBatchIds.length > 0) {
      lines.push(`  - Blocked batches: ${item.blockedBatchIds.join(", ")}`);
    }
  }
  lines.push("", "## Closure Waves", "");
  for (const wave of artifact.closureWaves || []) {
    lines.push(`- Wave ${wave.wave}: ${wave.owner}/${wave.batchId || "single item"} - ${wave.receiptStatus}`);
    if (wave.missingArtifacts.length > 0) {
      lines.push(`  - Missing artifacts: ${wave.missingArtifacts.join(", ")}`);
    }
    if ((wave.blockerHints || []).length > 0) {
      lines.push(`  - Content blockers: ${(wave.blockerHints || []).join("; ")}`);
    }
  }
  lines.push("", "## Next Commands", "");
  for (const command of artifact.nextCommands || []) {
    lines.push(`- \`${command}\``);
  }
  return `${lines.join("\n")}\n`;
}

function releaseFinalGoNoGoCsv(summary) {
  const artifact = releaseFinalGoNoGoArtifact(summary);
  const rows = [[
    "recommendation",
    "finalRecommendation",
    "cutoverAllowed",
    "releaseEnvFileCutoverSafe",
    "gateBlockers",
    "blockedCutoverItems",
    "runnableClosureWaves",
    "receiptMissingArtifactWaves",
    "receiptContentBlockedWaves",
    "performanceBaselineStatus",
    "stopReasons",
    "nextCommands",
  ], [
    artifact.recommendation,
    artifact.finalRecommendation,
    artifact.cutoverAllowed,
    artifact.releaseEnvFileCutoverSafe,
    artifact.gate.blockers,
    artifact.summary.blockedCutoverItems,
    artifact.summary.runnableClosureWaves,
    artifact.summary.receiptMissingArtifactWaves,
    artifact.summary.receiptContentBlockedWaves,
    artifact.summary.performanceBaselineStatus,
    artifact.currentStopReasons,
    artifact.nextCommands,
  ]];
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseFinalGoNoGoGate(summary) {
  const artifact = releaseFinalGoNoGoArtifact(summary);
  const packetPath = path.relative(repoRoot, releaseFinalGoNoGoOutput).replaceAll("\\", "/");
  const markdownPath = path.relative(repoRoot, releaseFinalGoNoGoMarkdownOutput).replaceAll("\\", "/");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD final go/no-go gate."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine("Default mode prints the decision. Set DDD_FINAL_GO_NO_GO_ENFORCE=1 to fail on NO-GO."),
    ...releaseRepoRootPreambleLines(),
    "",
    `DDD_FINAL_GO_NO_GO_PACKET="\${DDD_FINAL_GO_NO_GO_PACKET:-${packetPath}}"`,
    "DDD_FINAL_GO_NO_GO_ENFORCE=\"${DDD_FINAL_GO_NO_GO_ENFORCE:-}\"",
    "DDD_STAGING_FINAL_REVIEW_ENFORCE=\"${DDD_STAGING_FINAL_REVIEW_ENFORCE:-${DDD_FINAL_GO_NO_GO_ENFORCE}}\"",
    "DDD_NODE_BIN=\"${DDD_NODE_BIN:-node}\"",
    "if [[ ! -f \"${DDD_FINAL_GO_NO_GO_PACKET}\" ]]; then",
    "  echo \"Final go/no-go packet does not exist: ${DDD_FINAL_GO_NO_GO_PACKET}\" >&2",
    "  echo \"Run: node scripts/ddd-release-readiness-summary.mjs\" >&2",
    "  exit 2",
    "fi",
    "set +e",
    "\"${DDD_NODE_BIN}\" --input-type=module - \"${DDD_FINAL_GO_NO_GO_PACKET}\" <<'NODE'",
    "import fs from 'node:fs';",
    "const packetPath = process.argv[2];",
    "const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));",
    "const stopReasons = Array.isArray(packet.currentStopReasons) ? packet.currentStopReasons : [];",
    "const nextCommands = Array.isArray(packet.nextCommands) ? packet.nextCommands : [];",
    "const stopOwners = Array.isArray(packet.ciSummary?.stopOwners) ? packet.ciSummary.stopOwners : [];",
    "const blockedArtifacts = Array.isArray(packet.ciSummary?.blockedArtifactPaths) ? packet.ciSummary.blockedArtifactPaths : [];",
    "const blockedContentHints = Array.isArray(packet.ciSummary?.blockedContentHints) ? packet.ciSummary.blockedContentHints : [];",
    "const exitCodeMap = packet.ciSummary?.exitCodeMap || {};",
    "const finalNoGoExitCode = Number(exitCodeMap.finalNoGo ?? packet.ciSummary?.nonGoExitCode ?? 10);",
    "const finalPacketInvalidExitCode = Number(exitCodeMap.finalPacketInvalid ?? 11);",
    "const releaseEnvUnresolvedExitCode = Number(exitCodeMap.releaseEnvUnresolved ?? 21);",
    "const releaseEnvInvalidPacketExitCode = Number(exitCodeMap.releaseEnvInvalidPacket ?? 22);",
    "const invalidPacketReasons = [];",
    "if (!['GO_STRICT', 'NO_GO_STRICT'].includes(packet.finalRecommendation || packet.recommendation || '')) invalidPacketReasons.push('finalRecommendation');",
    "if (typeof packet.cutoverAllowed !== 'boolean') invalidPacketReasons.push('cutoverAllowed');",
    "if (packet.noAutoWaivers !== true) invalidPacketReasons.push('noAutoWaivers');",
    "if (!packet.gate || typeof packet.gate.blockers !== 'number') invalidPacketReasons.push('gate.blockers');",
    "if (!Array.isArray(packet.currentStopReasons)) invalidPacketReasons.push('currentStopReasons');",
    "if (!Array.isArray(packet.nextCommands)) invalidPacketReasons.push('nextCommands');",
    "if (!packet.ciSummary || typeof packet.ciSummary !== 'object') invalidPacketReasons.push('ciSummary');",
    "if (!packet.ciSummary?.releaseEnvReadiness || typeof packet.ciSummary.releaseEnvReadiness !== 'object') invalidPacketReasons.push('ciSummary.releaseEnvReadiness');",
    "if (!packet.ciSummary?.configOwnerInputReconciliation || typeof packet.ciSummary.configOwnerInputReconciliation !== 'object') invalidPacketReasons.push('ciSummary.configOwnerInputReconciliation');",
    "if (!packet.ciSummary?.ownerInputReceipt || typeof packet.ciSummary.ownerInputReceipt !== 'object') invalidPacketReasons.push('ciSummary.ownerInputReceipt');",
    "if (invalidPacketReasons.length > 0) {",
    "  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);",
    "  process.exit(finalPacketInvalidExitCode);",
    "}",
    "const releaseEnvReadiness = packet.ciSummary?.releaseEnvReadiness || {};",
    "const configOwnerInputReconciliation = packet.ciSummary?.configOwnerInputReconciliation || {};",
    "const ownerInputReceipt = packet.ciSummary?.ownerInputReceipt || {};",
    "const orchestratorPreflight = packet.ciSummary?.orchestratorPreflight || {};",
    "const releaseEnvFile = packet.safetySignals?.releaseEnvFile || {};",
    "const releaseEnvFileCutoverSafe = releaseEnvFile.ready === true",
    "  && releaseEnvFile.status === 'PASS'",
    "  && releaseEnvFile.inputKind === 'release-env-file'",
    "  && releaseEnvFile.envFilePresent === true",
    "  && releaseEnvFile.generatedMissingTemplate !== true",
    "  && releaseEnvFile.securityChecked === true",
    "  && releaseEnvFile.permissionSafe === true",
    "  && releaseEnvFile.permissionCheckSkipped !== true",
    "  && releaseEnvFile.modeOctal === (releaseEnvFile.requiredMode || '600')",
    "  && (releaseEnvFile.requiredMode || '600') === '600';",
    "if (typeof packet.releaseEnvFileCutoverSafe !== 'boolean') invalidPacketReasons.push('releaseEnvFileCutoverSafe');",
    "if (typeof packet.releaseEnvFileCutoverSafe === 'boolean' && packet.releaseEnvFileCutoverSafe !== releaseEnvFileCutoverSafe) invalidPacketReasons.push('releaseEnvFileCutoverSafeMismatch');",
    "if (configOwnerInputReconciliation.status !== 'PASS') invalidPacketReasons.push('configOwnerInputReconciliation.status');",
    "if (Number(configOwnerInputReconciliation.unmappedConfigPlaceholderKeys ?? 0) !== 0) invalidPacketReasons.push('configOwnerInputUnmapped');",
    "if (Number(configOwnerInputReconciliation.mappedConfigPlaceholderKeys ?? -1) !== Number(configOwnerInputReconciliation.uniqueConfigPlaceholderKeys ?? -2)) invalidPacketReasons.push('configOwnerInputMappedCount');",
    "if (!['PASS', 'PENDING_OWNER_INPUT'].includes(ownerInputReceipt.status || '')) invalidPacketReasons.push('ownerInputReceipt.status');",
    "if (ownerInputReceipt.status === 'PASS' && ownerInputReceipt.cutoverReady !== true) invalidPacketReasons.push('ownerInputReceipt.cutoverReady');",
    "if (ownerInputReceipt.status === 'PENDING_OWNER_INPUT' && ownerInputReceipt.cutoverReady !== false) invalidPacketReasons.push('ownerInputReceipt.pendingCutoverReady');",
    "if (invalidPacketReasons.length > 0) {",
    "  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);",
    "  process.exit(finalPacketInvalidExitCode);",
    "}",
    "const finalRecommendation = packet.finalRecommendation || packet.recommendation || 'UNKNOWN';",
    "if (finalRecommendation === 'GO_STRICT' && stopReasons.length > 0) invalidPacketReasons.push('goWithStopReasons');",
    "if (packet.cutoverAllowed === true && stopReasons.length > 0) invalidPacketReasons.push('cutoverAllowedWithStopReasons');",
    "if (packet.cutoverAllowed === true && Number(packet.gate?.blockers ?? 0) > 0) invalidPacketReasons.push('cutoverAllowedWithGateBlockers');",
    "if (invalidPacketReasons.length > 0) {",
    "  console.error(`[ddd-final-go-no-go][invalid-packet] ${invalidPacketReasons.join(',')}`);",
    "  process.exit(finalPacketInvalidExitCode);",
    "}",
    "console.log(`[ddd-final-go-no-go] recommendation=${packet.recommendation} finalRecommendation=${finalRecommendation} cutoverAllowed=${packet.cutoverAllowed} gateBlockers=${packet.gate?.blockers ?? 'unknown'} stopReasons=${stopReasons.length}`);",
    "console.log(`[ddd-final-go-no-go] ci stopOwners=${stopOwners.join(',') || 'none'} blockedArtifacts=${blockedArtifacts.length} blockedContentHints=${blockedContentHints.length} nonGoExitCode=${finalNoGoExitCode}`);",
    "console.log(`[ddd-final-go-no-go] exitCodes finalNoGo=${finalNoGoExitCode} finalPacketInvalid=${finalPacketInvalidExitCode} envUnresolved=${releaseEnvUnresolvedExitCode} envInvalidPacket=${releaseEnvInvalidPacketExitCode}`);",
    "console.log(`[ddd-final-go-no-go] configOwnerInputReconciliation status=${configOwnerInputReconciliation.status || 'missing'} placeholders=${configOwnerInputReconciliation.configPlaceholderBlockers ?? 'unknown'} uniqueKeys=${configOwnerInputReconciliation.uniqueConfigPlaceholderKeys ?? 'unknown'} mapped=${configOwnerInputReconciliation.mappedConfigPlaceholderKeys ?? 'unknown'} unmapped=${configOwnerInputReconciliation.unmappedConfigPlaceholderKeys ?? 'unknown'} ownerInputs=${configOwnerInputReconciliation.ownerInputKeys ?? 'unknown'}`);",
    "console.log(`[ddd-final-go-no-go] ownerInputReceipt status=${ownerInputReceipt.status || 'missing'} cutoverReady=${ownerInputReceipt.cutoverReady === true} inputs=${ownerInputReceipt.requiredOwnerInputs ?? 'unknown'} owners=${ownerInputReceipt.ownerCount ?? 'unknown'} pendingOwners=${ownerInputReceipt.pendingOwnerCount ?? 'unknown'} missingCriteria=${Array.isArray(ownerInputReceipt.missingCriteria) ? ownerInputReceipt.missingCriteria.join(',') : 'unknown'} artifact=${ownerInputReceipt.artifact || 'missing'}`);",
    "console.log(`[ddd-final-go-no-go] releaseEnvReadiness blockers=${releaseEnvReadiness.blockers ?? 'unknown'} placeholders=${releaseEnvReadiness.placeholders ?? 'unknown'} missing=${releaseEnvReadiness.missing ?? 'unknown'} filledRedacted=${releaseEnvReadiness.filledRedacted ?? 'unknown'} owners=${releaseEnvReadiness.ownerCount ?? 'unknown'} handoff=${releaseEnvReadiness.ownerHandoffDir || 'missing'} handoffCsv=${releaseEnvReadiness.ownerHandoffCsv || 'missing'}`);",
    "const releaseEnvOwnerBlockers = Array.isArray(releaseEnvReadiness.ownerBlockerSummary) ? releaseEnvReadiness.ownerBlockerSummary : [];",
    "if (releaseEnvOwnerBlockers.length > 0) console.log(`[ddd-final-go-no-go] releaseEnvOwnerBlockers ${releaseEnvOwnerBlockers.map((owner) => `${owner.owner}:${owner.blockers}`).join(',')}`);",
    "const orchestratorOwnerActions = Array.isArray(orchestratorPreflight.ownerActionSummary) ? orchestratorPreflight.ownerActionSummary : [];",
    "console.log(`[ddd-final-go-no-go] orchestratorPreflight mode=${orchestratorPreflight.mode || 'missing'} status=${orchestratorPreflight.status || 'missing'} blockers=${orchestratorPreflight.blockers ?? 'unknown'} warnings=${orchestratorPreflight.warnings ?? 'unknown'} selectedSteps=${orchestratorPreflight.selectedStepCount ?? 'unknown'} executedResults=${orchestratorPreflight.executedResultCount ?? 'unknown'} artifact=${orchestratorPreflight.artifact || 'missing'}`);",
    "if (orchestratorOwnerActions.length > 0) console.log(`[ddd-final-go-no-go] orchestratorPreflightOwners ${orchestratorOwnerActions.map((owner) => `${owner.owner}:${Array.isArray(owner.actions) ? owner.actions.length : 0}`).join(',')}`);",
    "console.log(`[ddd-final-go-no-go] safety releaseEnvFile ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || 'missing'} inputKind=${releaseEnvFile.inputKind || 'missing'} envFilePresent=${releaseEnvFile.envFilePresent === true} securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || 'missing'} requiredMode=${releaseEnvFile.requiredMode || '600'}`);",
    "if (packet.ciSummary?.firstEnvOwnerAction) console.log(`[ddd-final-go-no-go] first env owner action: owner=${packet.ciSummary.firstEnvOwnerAction.owner} blockers=${packet.ciSummary.firstEnvOwnerAction.blockers ?? 0} placeholders=${packet.ciSummary.firstEnvOwnerAction.placeholders ?? 0} missing=${packet.ciSummary.firstEnvOwnerAction.missing ?? 0} handoff=${packet.ciSummary.firstEnvOwnerAction.handoffPath || 'missing'} next=${packet.ciSummary.firstEnvOwnerAction.nextCommand || 'none'}`);",
    "if (packet.ciSummary?.firstOrchestratorPreflightAction) console.log(`[ddd-final-go-no-go] first orchestrator preflight action: owner=${packet.ciSummary.firstOrchestratorPreflightAction.owner} check=${packet.ciSummary.firstOrchestratorPreflightAction.checkId || packet.ciSummary.firstOrchestratorPreflightAction.id || 'missing'} reason=${packet.ciSummary.firstOrchestratorPreflightAction.reason || 'missing'} envKeys=${(packet.ciSummary.firstOrchestratorPreflightAction.envKeys || []).join(',') || 'none'} command=${packet.ciSummary.firstOrchestratorPreflightAction.command || 'none'}`);",
    "if (packet.ciSummary?.firstNextCommand) console.log(`[ddd-final-go-no-go] first next command: ${packet.ciSummary.firstNextCommand}`);",
    "if (packet.ciSummary?.firstOwnerAction) console.log(`[ddd-final-go-no-go] first owner action: owner=${packet.ciSummary.firstOwnerAction.owner} command=${packet.ciSummary.firstOwnerAction.displayCommand || packet.ciSummary.firstOwnerAction.command || 'none'} reason=${packet.ciSummary.firstOwnerAction.displayReason || packet.ciSummary.firstOwnerAction.reason || 'none'}`);",
    "if (packet.ciSummary?.firstOwnerAction?.nextAction) console.log(`[ddd-final-go-no-go] first owner next action: ${packet.ciSummary.firstOwnerAction.displayNextAction || packet.ciSummary.firstOwnerAction.nextAction}`);",
    "if (stopReasons.length > 0) {",
    "  console.log('[ddd-final-go-no-go] stop reasons:');",
    "  for (const reason of stopReasons) console.log(`- ${reason}`);",
    "}",
    "if (nextCommands.length > 0) {",
    "  console.log('[ddd-final-go-no-go] next commands:');",
    "  for (const command of nextCommands) console.log(`- ${command}`);",
    "}",
    "if (packet.cutoverAllowed === true && releaseEnvFile.ready !== true) {",
    "  console.error('[ddd-final-go-no-go] releaseEnvFile.ready must be true before cutoverAllowed can be true');",
    "  process.exit(4);",
    "}",
    "if (packet.cutoverAllowed === true && releaseEnvFileCutoverSafe !== true) {",
    "  console.error('[ddd-final-go-no-go] releaseEnvFile must be PASS release-env-file with checked chmod 600 permissions before cutoverAllowed can be true');",
    "  process.exit(4);",
    "}",
    "if (packet.cutoverAllowed !== true) {",
    "  process.exitCode = finalNoGoExitCode;",
    "}",
    "NODE",
    "DDD_FINAL_GO_NO_GO_STATUS=$?",
    "set -e",
    "if [[ \"${DDD_FINAL_GO_NO_GO_STATUS}\" == \"0\" ]]; then",
    "  if [[ \"${DDD_STAGING_FINAL_REVIEW_ENFORCE}\" == \"1\" || \"${DDD_STAGING_FINAL_REVIEW_ENFORCE}\" == \"true\" ]]; then",
    "    set +e",
    "    \"${DDD_NODE_BIN}\" scripts/ddd-staging-execution-checklist.mjs --final-review-enforce",
    "    DDD_STAGING_FINAL_REVIEW_STATUS=$?",
    "    set -e",
    "    if [[ \"${DDD_STAGING_FINAL_REVIEW_STATUS}\" != \"0\" ]]; then",
    "      echo \"[ddd-final-go-no-go][staging-final-review-blocked] cutover blocked; run node scripts/ddd-staging-execution-checklist.mjs --final-review\" >&2",
    "      if [[ \"${DDD_FINAL_GO_NO_GO_ENFORCE}\" == \"1\" || \"${DDD_FINAL_GO_NO_GO_ENFORCE}\" == \"true\" ]]; then",
    "        exit 10",
    "      fi",
    "      exit 0",
    "    fi",
    "  fi",
    "  echo \"[ddd-final-go-no-go][go] cutover allowed\"",
    "  exit 0",
    "fi",
    "if [[ \"${DDD_FINAL_GO_NO_GO_STATUS}\" == \"10\" ]]; then",
    "  echo \"[ddd-final-go-no-go][no-go] cutover blocked; see ${DDD_FINAL_GO_NO_GO_PACKET} and " + markdownPath + "\" >&2",
    "  if [[ \"${DDD_FINAL_GO_NO_GO_ENFORCE}\" == \"1\" || \"${DDD_FINAL_GO_NO_GO_ENFORCE}\" == \"true\" ]]; then",
    "    exit 10",
    "  fi",
    "  exit 0",
    "fi",
    "exit \"${DDD_FINAL_GO_NO_GO_STATUS}\"",
  ];
  if (artifact.cutoverAllowed === false) {
    lines.push("");
    lines.push(shellCommentLine(`Generated packet is currently NO-GO with ${artifact.currentStopReasons.length} stop reasons.`));
  }
  return `${lines.join("\n")}\n`;
}

function releasePreflightGate(summary) {
  const finalGoNoGo = releaseFinalGoNoGoArtifact(summary);
  return `${[
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release preflight gate."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine("Default mode reports every gate without failing on NO-GO. Set DDD_RELEASE_PREFLIGHT_ENFORCE=1 for CI blocking behavior."),
    ...releaseRepoRootPreambleLines(),
    "",
    "DDD_RELEASE_PREFLIGHT_ENFORCE=\"${DDD_RELEASE_PREFLIGHT_ENFORCE:-}\"",
    "DDD_RELEASE_DIR=\"${DDD_RELEASE_DIR:-${DDD_RELEASE_EVIDENCE_DIR:-artifacts/ddd}/release}\"",
    "export DDD_RELEASE_DIR",
    "DDD_RELEASE_CONFIG_REPORT=\"${DDD_RELEASE_CONFIG_REPORT:-${DDD_RELEASE_EVIDENCE_DIR:-artifacts/ddd}/config/release-config-evidence.json}\"",
    "export DDD_RELEASE_CONFIG_REPORT",
    "DDD_RELEASE_PREFLIGHT_REPORT=\"${DDD_RELEASE_PREFLIGHT_REPORT:-${DDD_RELEASE_DIR}/release-preflight-report.json}\"",
    ...safeReleaseEnvLoaderLines(),
    "if [[ -n \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then",
    "  safe_load_release_env_file",
    "fi",
    "",
    "artifact_integrity_status=-1",
    "manifest_preflight_status=-1",
    "path_leak_status=-1",
    "unblock_brief_status=-1",
    "env_owner_handoff_status=-1",
    "env_owner_input_packet_status=-1",
    "config_owner_input_reconciliation_status=-1",
    "owner_input_receipt_status=-1",
    "env_readiness_status=-1",
    "final_go_no_go_status=-1",
    "preflight_step_status=0",
    "failed_step=\"\"",
    "",
    "write_preflight_report() {",
    "  local status=\"$1\"",
    "  mkdir -p \"$(dirname \"${DDD_RELEASE_PREFLIGHT_REPORT}\")\"",
    `  node --input-type=module - "\${DDD_RELEASE_PREFLIGHT_REPORT}" "\${DDD_RELEASE_PREFLIGHT_ENFORCE:-}" "\${status}" "\${failed_step}" "\${artifact_integrity_status}" "\${manifest_preflight_status}" "\${path_leak_status}" "\${unblock_brief_status}" "\${env_owner_handoff_status}" "\${env_owner_input_packet_status}" "\${config_owner_input_reconciliation_status}" "\${owner_input_receipt_status}" "\${env_readiness_status}" "\${final_go_no_go_status}" "${finalGoNoGo.finalRecommendation}" "${finalGoNoGo.cutoverAllowed}" "${finalGoNoGo.releaseEnvFileCutoverSafe}" "${finalGoNoGo.gate?.blockers ?? -1}" "${finalGoNoGo.currentStopReasons.length}" <<'NODE'`,
    "import fs from 'node:fs';",
    "const [reportPath, enforceValue, status, failedStep, artifactIntegrityStatus, manifestPreflightStatus, pathLeakStatus, unblockBriefStatus, envOwnerHandoffStatus, envOwnerInputPacketStatus, configOwnerInputReconciliationStatus, ownerInputReceiptStatus, envReadinessStatus, finalGoNoGoStatus, finalRecommendation, cutoverAllowedValue, releaseEnvFileCutoverSafeValue, gateBlockersValue, stopReasonCountValue] = process.argv.slice(2);",
    "const toNumber = (value) => Number.isFinite(Number(value)) ? Number(value) : -1;",
    "const enforce = enforceValue === '1' || enforceValue === 'true';",
    "const cutoverAllowed = cutoverAllowedValue === 'true';",
    "const releaseEnvFileCutoverSafe = releaseEnvFileCutoverSafeValue === 'true';",
    "const steps = [",
    "  { name: 'artifact-integrity', exitCode: toNumber(artifactIntegrityStatus), command: 'bash ${DDD_RELEASE_DIR}/release-artifact-integrity-gate.sh' },",
    "  { name: 'manifest-provenance-preflight', exitCode: toNumber(manifestPreflightStatus), command: 'DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs' },",
    "  { name: 'artifact-path-leak', exitCode: toNumber(pathLeakStatus), command: 'node scripts/ddd-release-artifact-path-leak-contract.mjs' },",
    "  { name: 'unblock-brief', exitCode: toNumber(unblockBriefStatus), command: 'node scripts/ddd-release-unblock-brief.mjs && node scripts/ddd-release-unblock-brief-contract.mjs' },",
    "  { name: 'env-owner-handoff-redacted', exitCode: toNumber(envOwnerHandoffStatus), command: 'node scripts/ddd-release-env-owner-handoff-redacted-contract.mjs' },",
    "  { name: 'env-owner-input-packet', exitCode: toNumber(envOwnerInputPacketStatus), command: 'node scripts/ddd-release-env-owner-input-packet-contract.mjs' },",
    "  { name: 'config-owner-input-reconciliation', exitCode: toNumber(configOwnerInputReconciliationStatus), command: 'DDD_RELEASE_CONFIG_REPORT=${DDD_RELEASE_CONFIG_REPORT} node scripts/ddd-release-config-owner-input-reconciliation.mjs' },",
    "  { name: 'owner-input-receipt', exitCode: toNumber(ownerInputReceiptStatus), command: 'node scripts/ddd-release-owner-input-receipt.mjs && node scripts/ddd-release-owner-input-receipt-contract.mjs' },",
    "  { name: 'env-readiness', exitCode: toNumber(envReadinessStatus), command: enforceValue === '1' || enforceValue === 'true' ? 'DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash ${DDD_RELEASE_DIR}/release-env-readiness-gate.sh' : 'bash ${DDD_RELEASE_DIR}/release-env-readiness-gate.sh' },",
    "  { name: 'final-go-no-go', exitCode: toNumber(finalGoNoGoStatus), command: enforceValue === '1' || enforceValue === 'true' ? 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash ${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh' : 'bash ${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh' },",
    "];",
    "const advisoryFailures = enforce ? [] : steps",
    "  .filter((step) => step.exitCode > 0)",
    "  .map((step) => ({ name: step.name, exitCode: step.exitCode, command: step.command }));",
    "const report = {",
    "  generatedAt: new Date().toISOString(),",
    "  status,",
    "  enforce,",
    "  advisoryOnly: !enforce,",
    "  advisoryFailureCount: advisoryFailures.length,",
    "  advisoryFailures,",
    "  cutoverAllowed,",
    "  releaseEnvFileCutoverSafe,",
    "  finalRecommendation,",
    "  gateBlockers: toNumber(gateBlockersValue),",
    "  stopReasonCount: toNumber(stopReasonCountValue),",
    "  cutoverDecisionSource: 'artifacts/ddd/release/release-final-go-no-go.json',",
    "  advisoryNotice: !enforce && !cutoverAllowed ? `Default preflight PASS means checks completed; it is not cutover approval. advisoryFailureCount=${advisoryFailures.length}. Run DDD_RELEASE_PREFLIGHT_ENFORCE=1 for CI blocking behavior.` : null,",
    "  failedStep: failedStep || null,",
    "  reportPath,",
    "  steps,",
    "};",
    "fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\\n`);",
    "NODE",
    "}",
    "",
    "run_preflight_step() {",
    "  local name=\"$1\"",
    "  shift",
    "  echo \"[ddd-release-preflight] step=${name}\"",
    "  set +e",
    "  \"$@\"",
    "  local status=\"$?\"",
    "  set -e",
    "  preflight_step_status=\"${status}\"",
    "  return 0",
    "}",
    "",
    "run_preflight_step artifact-integrity bash \"${DDD_RELEASE_DIR}/release-artifact-integrity-gate.sh\"",
    "artifact_integrity_status=\"${preflight_step_status}\"",
    "if [[ \"${artifact_integrity_status}\" != \"0\" ]]; then",
    "  failed_step=\"artifact-integrity\"",
    "  write_preflight_report FAIL",
    "  exit \"${artifact_integrity_status}\"",
    "fi",
    "run_preflight_step manifest-provenance-preflight env DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
    "manifest_preflight_status=\"${preflight_step_status}\"",
    "run_preflight_step artifact-path-leak node scripts/ddd-release-artifact-path-leak-contract.mjs",
    "path_leak_status=\"${preflight_step_status}\"",
    "if [[ \"${path_leak_status}\" != \"0\" ]]; then",
    "  failed_step=\"artifact-path-leak\"",
    "  write_preflight_report FAIL",
    "  exit \"${path_leak_status}\"",
    "fi",
    "if [[ \"${manifest_preflight_status}\" != \"0\" && ( \"${DDD_RELEASE_PREFLIGHT_ENFORCE}\" == \"1\" || \"${DDD_RELEASE_PREFLIGHT_ENFORCE}\" == \"true\" ) ]]; then",
    "  failed_step=\"manifest-provenance-preflight\"",
    "  write_preflight_report NO_GO",
    "  exit \"${manifest_preflight_status}\"",
    "fi",
    "run_preflight_step unblock-brief node scripts/ddd-release-unblock-brief.mjs",
    "unblock_brief_status=\"${preflight_step_status}\"",
    "if [[ \"${unblock_brief_status}\" == \"0\" ]]; then",
    "  run_preflight_step unblock-brief-contract node scripts/ddd-release-unblock-brief-contract.mjs",
    "  unblock_brief_status=\"${preflight_step_status}\"",
    "fi",
    "if [[ \"${unblock_brief_status}\" != \"0\" ]]; then",
    "  failed_step=\"unblock-brief\"",
    "  write_preflight_report FAIL",
    "  exit \"${unblock_brief_status}\"",
    "fi",
    "run_preflight_step env-owner-handoff-redacted node scripts/ddd-release-env-owner-handoff-redacted-contract.mjs",
    "env_owner_handoff_status=\"${preflight_step_status}\"",
    "if [[ \"${env_owner_handoff_status}\" != \"0\" ]]; then",
    "  failed_step=\"env-owner-handoff-redacted\"",
    "  write_preflight_report FAIL",
    "  exit \"${env_owner_handoff_status}\"",
    "fi",
    "run_preflight_step env-owner-input-packet node scripts/ddd-release-env-owner-input-packet-contract.mjs",
    "env_owner_input_packet_status=\"${preflight_step_status}\"",
    "if [[ \"${env_owner_input_packet_status}\" != \"0\" ]]; then",
    "  failed_step=\"env-owner-input-packet\"",
    "  write_preflight_report FAIL",
    "  exit \"${env_owner_input_packet_status}\"",
    "fi",
    "run_preflight_step config-owner-input-reconciliation env DDD_RELEASE_CONFIG_REPORT=\"${DDD_RELEASE_CONFIG_REPORT}\" node scripts/ddd-release-config-owner-input-reconciliation.mjs",
    "config_owner_input_reconciliation_status=\"${preflight_step_status}\"",
    "if [[ \"${config_owner_input_reconciliation_status}\" != \"0\" ]]; then",
    "  failed_step=\"config-owner-input-reconciliation\"",
    "  write_preflight_report FAIL",
    "  exit \"${config_owner_input_reconciliation_status}\"",
    "fi",
    "run_preflight_step owner-input-receipt node scripts/ddd-release-owner-input-receipt.mjs",
    "owner_input_receipt_status=\"${preflight_step_status}\"",
    "if [[ \"${owner_input_receipt_status}\" == \"0\" ]]; then",
    "  run_preflight_step owner-input-receipt-contract node scripts/ddd-release-owner-input-receipt-contract.mjs",
    "  owner_input_receipt_status=\"${preflight_step_status}\"",
    "fi",
    "if [[ \"${owner_input_receipt_status}\" != \"0\" ]]; then",
    "  failed_step=\"owner-input-receipt\"",
    "  write_preflight_report FAIL",
    "  exit \"${owner_input_receipt_status}\"",
    "fi",
    "if [[ \"${DDD_RELEASE_PREFLIGHT_ENFORCE}\" == \"1\" || \"${DDD_RELEASE_PREFLIGHT_ENFORCE}\" == \"true\" ]]; then",
    "  run_preflight_step env-readiness env DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash \"${DDD_RELEASE_DIR}/release-env-readiness-gate.sh\"",
    "  env_readiness_status=\"${preflight_step_status}\"",
    "  if [[ \"${env_readiness_status}\" != \"0\" ]]; then",
    "    failed_step=\"env-readiness\"",
    "    write_preflight_report NO_GO",
    "    exit \"${env_readiness_status}\"",
    "  fi",
    "  run_preflight_step final-go-no-go env DDD_FINAL_GO_NO_GO_ENFORCE=1 bash \"${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh\"",
    "  final_go_no_go_status=\"${preflight_step_status}\"",
    "  if [[ \"${final_go_no_go_status}\" != \"0\" ]]; then",
    "    failed_step=\"final-go-no-go\"",
    "    write_preflight_report NO_GO",
    "    exit \"${final_go_no_go_status}\"",
    "  fi",
    "else",
    "  run_preflight_step env-readiness bash \"${DDD_RELEASE_DIR}/release-env-readiness-gate.sh\"",
    "  env_readiness_status=\"${preflight_step_status}\"",
    "  run_preflight_step final-go-no-go bash \"${DDD_RELEASE_DIR}/release-final-go-no-go-gate.sh\"",
    "  final_go_no_go_status=\"${preflight_step_status}\"",
    "fi",
    "write_preflight_report PASS",
    "echo \"[ddd-release-preflight] report=${DDD_RELEASE_PREFLIGHT_REPORT}\"",
    "echo \"[ddd-release-preflight] complete enforce=${DDD_RELEASE_PREFLIGHT_ENFORCE:-false}\"",
  ].join("\n")}\n`;
}

function releaseFinalOwnerQueueArtifact(summary) {
  const finalGoNoGo = releaseFinalGoNoGoArtifact(summary);
  const ownerInputReceiptSummary = releaseOwnerInputReceiptQueueSummary(summary);
  const batchById = new Map((summary.releaseActionBatches || []).map((batch) => [batch.id, batch]));
  const batchOrderById = new Map((summary.releaseActionBatches || []).map((batch, index) => [batch.id, index + 1]));
  const closureWaveByNumber = new Map((finalGoNoGo.closureWaves || []).map((wave) => [wave.wave, wave]));
  const firstOwnerActionOwner = finalGoNoGo.ciSummary?.firstOwnerAction?.owner || null;
  const ownerMap = new Map();
  const pushMissingArtifact = (ownerQueue, artifactPath) => {
    if (isReleaseArtifactReference(artifactPath)) {
      ownerQueue.missingArtifacts.push(artifactPath);
    } else if (artifactPath) {
      ownerQueue.stopReasons.push(String(artifactPath));
    }
  };
  const ensureOwner = (owner) => {
    const ownerKey = owner || "release-owner";
    if (!ownerMap.has(ownerKey)) {
      ownerMap.set(ownerKey, {
        owner: ownerKey,
        cutoverItems: [],
        readyBatchIds: [],
        blockedBatchIds: [],
        closureWaves: [],
        envKeys: [],
        missingArtifacts: [],
        contentBlockers: [],
        commands: [],
        rerunCommands: [],
        stopReasons: [],
      });
    }
    return ownerMap.get(ownerKey);
  };
  for (const item of finalGoNoGo.blockedCutoverItems || []) {
    const batchIds = sortedUniqueStrings([...(item.readyBatchIds || []), ...(item.blockedBatchIds || [])]);
    for (const batchId of batchIds) {
      const batch = batchById.get(batchId);
      const ownerQueue = ensureOwner(batch?.owner || "release-owner");
      ownerQueue.cutoverItems.push(item.id);
      if ((item.readyBatchIds || []).includes(batchId)) {
        ownerQueue.readyBatchIds.push(batchId);
      }
      if ((item.blockedBatchIds || []).includes(batchId)) {
        ownerQueue.blockedBatchIds.push(batchId);
      }
      ownerQueue.commands.push(...(batch?.commands || []));
      ownerQueue.envKeys.push(...(batch?.envKeys || []));
      for (const artifactPath of batch?.expectedArtifacts || []) {
        pushMissingArtifact(ownerQueue, artifactPath);
      }
      ownerQueue.stopReasons.push(`cutover checklist blocked: ${item.id}`);
    }
  }
  for (const wave of finalGoNoGo.closureWaves || []) {
    const ownerQueue = ensureOwner(wave.owner);
    ownerQueue.closureWaves.push(wave.wave);
    for (const artifactPath of wave.missingArtifacts || []) {
      pushMissingArtifact(ownerQueue, artifactPath);
    }
    for (const hint of wave.blockerHints || []) {
      ownerQueue.contentBlockers.push(hint);
      ownerQueue.stopReasons.push(`closure wave ${wave.wave} blocker hint: ${hint}`);
    }
    ownerQueue.commands.push(...(wave.commands || []));
    ownerQueue.rerunCommands.push(...(wave.rerunCommands || []));
    if (wave.receiptStatus !== "READY_FOR_STRICT_GATE_RERUN") {
      ownerQueue.stopReasons.push(`closure wave ${wave.wave} ${wave.receiptStatus}`);
    }
  }
  if (finalGoNoGo.performanceBaseline?.status !== "READY") {
    const ownerQueue = ensureOwner("release-performance");
    for (const blocker of finalGoNoGo.performanceBaseline?.blockers || []) {
      pushMissingArtifact(ownerQueue, blocker);
    }
    ownerQueue.commands.push(...(finalGoNoGo.performanceBaseline?.commands || []));
    ownerQueue.envKeys.push(...(finalGoNoGo.performanceBaseline?.requiredEnvKeys || []));
    ownerQueue.stopReasons.push(`authenticated performance baseline not ready: ${finalGoNoGo.performanceBaseline.status}`);
  }
  if (firstOwnerActionOwner) {
    const ownerQueue = ensureOwner(firstOwnerActionOwner);
    if (finalGoNoGo.ciSummary?.firstOwnerAction?.command) {
      ownerQueue.commands.push(finalGoNoGo.ciSummary.firstOwnerAction.command);
    }
    ownerQueue.envKeys.push(...(finalGoNoGo.ciSummary?.firstOwnerAction?.envKeys || []));
  }
  const ownerQueues = [...ownerMap.values()].map((ownerQueue) => {
    const closureCommands = ownerQueue.closureWaves
      .slice()
      .sort((left, right) => left - right)
      .flatMap((waveNumber) => closureWaveByNumber.get(waveNumber)?.commands || []);
    const ownerFirstActionCommand = firstOwnerActionOwner === ownerQueue.owner
      ? finalGoNoGo.ciSummary?.firstOwnerAction?.command
      : null;
    const rawCommands = orderedUniqueStrings([
      ...closureCommands,
      ...ownerQueue.commands,
    ]);
    const queueStatus = ownerQueue.readyBatchIds.length > 0 || ownerQueue.closureWaves.length > 0 ? "ACTIONABLE" : "WAITING";
    const commands = ownerFirstActionCommand
      ? orderedUniqueStrings([
        ownerFirstActionCommand,
        ...rawCommands,
        ...(queueStatus === "ACTIONABLE" ? [finalReadinessSummaryCommand, finalGoNoGoEnforceCommand] : []),
      ].map(redactReleaseEnvCommandForDisplay))
      : orderedUniqueStrings([
        ...rawCommands,
        ...(queueStatus === "ACTIONABLE" ? [finalReadinessSummaryCommand, finalGoNoGoEnforceCommand] : []),
      ].map(redactReleaseEnvCommandForDisplay));
    const envKeys = sortedUniqueStrings(ownerQueue.envKeys);
    const missingArtifacts = sortedUniqueStrings(ownerQueue.missingArtifacts);
    const contentBlockers = sortedUniqueStrings(ownerQueue.contentBlockers);
    const stopReasons = sortedUniqueStrings(ownerQueue.stopReasons);
    const closureWaveOrder = ownerQueue.closureWaves.length > 0 ? Math.min(...ownerQueue.closureWaves) : Number.POSITIVE_INFINITY;
    const readyBatchOrder = ownerQueue.readyBatchIds.length > 0
      ? Math.min(...ownerQueue.readyBatchIds.map((batchId) => batchOrderById.get(batchId) || Number.POSITIVE_INFINITY))
      : Number.POSITIVE_INFINITY;
    const executionOrderHint = Math.min(closureWaveOrder, readyBatchOrder);
    return {
      owner: ownerQueue.owner,
      queueStatus,
      canExecute: queueStatus === "ACTIONABLE",
      firstOwnerActionPriority: firstOwnerActionOwner === ownerQueue.owner && queueStatus === "ACTIONABLE",
      executionOrderHint: Number.isFinite(executionOrderHint) ? executionOrderHint : null,
      cutoverItems: sortedUniqueStrings(ownerQueue.cutoverItems),
      readyBatchIds: sortedUniqueStrings(ownerQueue.readyBatchIds),
      blockedBatchIds: sortedUniqueStrings(ownerQueue.blockedBatchIds),
      closureWaves: sortedUniqueStrings(ownerQueue.closureWaves.map(String)).map(Number),
      envKeys,
      envKeyCount: envKeys.length,
      missingArtifacts,
      missingArtifactCount: missingArtifacts.length,
      contentBlockers,
      contentBlockerCount: contentBlockers.length,
      commands,
      commandCount: commands.length,
      firstCommand: commands[0] || null,
      rerunCommands: sortedUniqueStrings([
        ...ownerQueue.rerunCommands,
        ...(finalGoNoGo.ciSummary?.rerunCommands || []),
      ].map(redactReleaseEnvCommandForDisplay)),
      stopReasons,
      stopReasonCount: stopReasons.length,
    };
  }).sort((left, right) => {
    if (left.queueStatus !== right.queueStatus) {
      return left.queueStatus === "ACTIONABLE" ? -1 : 1;
    }
    if (left.firstOwnerActionPriority !== right.firstOwnerActionPriority) {
      return left.firstOwnerActionPriority ? -1 : 1;
    }
    const leftOrder = left.executionOrderHint ?? Number.POSITIVE_INFINITY;
    const rightOrder = right.executionOrderHint ?? Number.POSITIVE_INFINITY;
    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }
    return left.owner.localeCompare(right.owner);
  }).map((ownerQueue, index) => ({
    queueOrder: index + 1,
    ...ownerQueue,
  }));
  const nextExecutableOwner = ownerQueues.find((owner) => owner.canExecute === true) || null;
  const fastPathCommands = orderedUniqueStrings([
    ...(nextExecutableOwner?.commands || []),
    finalReadinessSummaryCommand,
    finalGoNoGoEnforceCommand,
  ]);
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    recommendation: finalGoNoGo.recommendation,
    finalRecommendation: finalGoNoGo.finalRecommendation,
    cutoverAllowed: finalGoNoGo.cutoverAllowed,
    releaseEnvFileCutoverSafe: finalGoNoGo.releaseEnvFileCutoverSafe,
    noAutoWaivers: true,
    safetySignals: finalGoNoGo.safetySignals,
    summary: {
      ownerCount: ownerQueues.length,
      actionableOwnerCount: ownerQueues.filter((owner) => owner.queueStatus === "ACTIONABLE").length,
      waitingOwnerCount: ownerQueues.filter((owner) => owner.queueStatus !== "ACTIONABLE").length,
      missingArtifactCount: sortedUniqueStrings(ownerQueues.flatMap((owner) => owner.missingArtifacts)).length,
      contentBlockerCount: sortedUniqueStrings(ownerQueues.flatMap((owner) => owner.contentBlockers)).length,
      ownerInputReceiptStatus: ownerInputReceiptSummary.status,
      ownerInputReceiptCutoverReady: ownerInputReceiptSummary.cutoverReady,
      ownerInputReceiptRequiredOwnerInputs: ownerInputReceiptSummary.requiredOwnerInputs,
      ownerInputReceiptPendingOwnerCount: ownerInputReceiptSummary.pendingOwnerCount,
      ownerInputReceiptMissingCriteriaCount: ownerInputReceiptSummary.missingCriteria.length,
      nextExecutableOwner: nextExecutableOwner?.owner || null,
      nextExecutableQueueOrder: nextExecutableOwner?.queueOrder || null,
      nextExecutableCommand: nextExecutableOwner?.firstCommand || null,
      nextExecutableEnvKeyCount: nextExecutableOwner?.envKeyCount || 0,
      nextExecutableMissingArtifactCount: nextExecutableOwner?.missingArtifactCount || 0,
    },
    ownerInputReceipt: ownerInputReceiptSummary,
    fastPath: {
      objective: "Run the next actionable owner queue through strict evidence refresh and final go/no-go without bypassing safety gates.",
      blockedUntil: "Required owner env keys and expected evidence artifacts are available in a permission-safe release env file.",
      owner: nextExecutableOwner?.owner || null,
      queueOrder: nextExecutableOwner?.queueOrder || null,
      firstCommand: nextExecutableOwner?.firstCommand || null,
      envKeyCount: nextExecutableOwner?.envKeyCount || 0,
      missingArtifactCount: nextExecutableOwner?.missingArtifactCount || 0,
      commands: fastPathCommands,
      releaseEnvFileRequired: nextExecutableOwner?.envKeyCount > 0,
      finalGateCommand: finalGoNoGoEnforceCommand,
    },
    ownerQueues,
  };
}

function isReleaseArtifactReference(value) {
  const text = String(value || "");
  return /^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(text);
}

function releaseFinalOwnerQueueMarkdown(summary) {
  const artifact = releaseFinalOwnerQueueArtifact(summary);
  const lines = [
    "# DDD Final Owner Queue",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Recommendation: ${artifact.recommendation}`,
    `Cutover allowed: ${artifact.cutoverAllowed}`,
    `No auto waivers: ${artifact.noAutoWaivers}`,
    `Owners: ${artifact.summary.ownerCount}`,
    `Actionable owners: ${artifact.summary.actionableOwnerCount}`,
    `Waiting owners: ${artifact.summary.waitingOwnerCount}`,
    `Unique missing artifacts: ${artifact.summary.missingArtifactCount}`,
    `Unique content blockers: ${artifact.summary.contentBlockerCount}`,
    `Owner input receipt status: ${artifact.summary.ownerInputReceiptStatus}`,
    `Owner input receipt cutover ready: ${artifact.summary.ownerInputReceiptCutoverReady}`,
    `Owner input receipt required inputs: ${artifact.summary.ownerInputReceiptRequiredOwnerInputs}`,
    `Owner input receipt pending owners: ${artifact.summary.ownerInputReceiptPendingOwnerCount}`,
    `Owner input receipt missing criteria: ${artifact.summary.ownerInputReceiptMissingCriteriaCount}`,
    `Next executable owner: ${artifact.summary.nextExecutableOwner || "none"}`,
    `Next executable command: ${artifact.summary.nextExecutableCommand || "none"}`,
    "Queue order: ACTIONABLE owners first, then WAITING owners.",
    "",
    "## Fast Path",
    "",
    `- Objective: ${artifact.fastPath.objective}`,
    `- Blocked until: ${artifact.fastPath.blockedUntil}`,
    `- Owner: ${artifact.fastPath.owner || "none"}`,
    `- Queue order: ${artifact.fastPath.queueOrder || "none"}`,
    `- First command: ${artifact.fastPath.firstCommand ? `\`${artifact.fastPath.firstCommand}\`` : "none"}`,
    `- Release env file required: ${artifact.fastPath.releaseEnvFileRequired}`,
    `- Env keys: ${artifact.fastPath.envKeyCount}`,
    `- Missing artifacts: ${artifact.fastPath.missingArtifactCount}`,
    "- Commands:",
  ];
  for (const command of artifact.fastPath.commands || []) {
    lines.push(`  - \`${command}\``);
  }
  lines.push(
    "",
    "## Safety Signals",
    "",
  );
  const releaseEnvFile = artifact.safetySignals?.releaseEnvFile || {};
  lines.push(`- releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`);
  lines.push(`- releaseEnvFile: ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || "missing"} inputKind=${releaseEnvFile.inputKind || "missing"} envFilePresent=${releaseEnvFile.envFilePresent === true}`);
  lines.push(`  - securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || "missing"} requiredMode=${releaseEnvFile.requiredMode || "600"} reason=${releaseEnvFile.reason || "missing"} permissionCheckSkipped=${releaseEnvFile.permissionCheckSkipped === true}`);
  lines.push(`  - pendingActions=${(releaseEnvFile.pendingActionIds || []).join(", ") || "none"}`);
  lines.push("");
  lines.push(
    "## Owner Input Receipt",
    "",
    `- Status: ${artifact.ownerInputReceipt.status}`,
    `- Cutover ready: ${artifact.ownerInputReceipt.cutoverReady}`,
    `- Required owner inputs: ${artifact.ownerInputReceipt.requiredOwnerInputs}`,
    `- Owners: ${artifact.ownerInputReceipt.ownerCount}`,
    `- Ready owners: ${artifact.ownerInputReceipt.readyOwnerCount}`,
    `- Pending owners: ${artifact.ownerInputReceipt.pendingOwnerCount}`,
    `- Artifact: ${artifact.ownerInputReceipt.artifact}`,
    `- Markdown: ${artifact.ownerInputReceipt.markdown}`,
    "- Missing criteria:",
  );
  for (const criteria of artifact.ownerInputReceipt.missingCriteria || []) {
    lines.push(`  - ${criteria}`);
  }
  if ((artifact.ownerInputReceipt.missingCriteria || []).length === 0) {
    lines.push("  - none");
  }
  lines.push("- Pending owner inputs:");
  for (const owner of artifact.ownerInputReceipt.pendingOwners || []) {
    lines.push(`  - ${owner.owner}: required=${owner.requiredOwnerInputs} placeholders=${owner.remainingPlaceholders} missing=${owner.remainingMissing} packet=${owner.packetPath || "n/a"} handoff=${owner.handoffPath || "n/a"} checklist=${owner.itemChecklistPath || "n/a"}`);
  }
  if ((artifact.ownerInputReceipt.pendingOwners || []).length === 0) {
    lines.push("  - none");
  }
  lines.push("");
  for (const owner of artifact.ownerQueues || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Queue order: ${owner.queueOrder}`);
    lines.push(`- Execution order hint: ${owner.executionOrderHint ?? "none"}`);
    lines.push(`- Queue status: ${owner.queueStatus}`);
    lines.push(`- Can execute: ${owner.canExecute}`);
    lines.push(`- Cutover items: ${owner.cutoverItems.join(", ") || "none"}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Closure waves: ${owner.closureWaves.join(", ") || "none"}`);
    lines.push(`- Commands: ${owner.commandCount}`);
    lines.push(`- Env keys: ${owner.envKeyCount}`);
    lines.push(`- Missing artifacts: ${owner.missingArtifactCount}`);
    lines.push(`- Content blockers: ${owner.contentBlockerCount}`);
    lines.push(`- Stop reasons: ${owner.stopReasonCount}`);
    lines.push(`- First command: ${owner.firstCommand ? `\`${owner.firstCommand}\`` : "none"}`);
    if (owner.envKeys.length > 0) {
      lines.push("- Env key names:");
      for (const key of owner.envKeys) {
        lines.push(`  - \`${key}\``);
      }
    }
    if (owner.missingArtifacts.length > 0) {
      lines.push("- Missing artifacts:");
      for (const artifactPath of owner.missingArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    if (owner.contentBlockers.length > 0) {
      lines.push("- Content blockers:");
      for (const blocker of owner.contentBlockers) {
        lines.push(`  - ${blocker}`);
      }
    }
    lines.push("- Rerun commands:");
    for (const command of owner.rerunCommands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseFinalOwnerQueueCsv(summary) {
  const artifact = releaseFinalOwnerQueueArtifact(summary);
  const rows = [[
    "owner",
    "queueOrder",
    "executionOrderHint",
    "queueStatus",
    "canExecute",
    "commandCount",
    "envKeyCount",
    "missingArtifactCount",
    "contentBlockerCount",
    "stopReasonCount",
    "cutoverItems",
    "readyBatchIds",
    "blockedBatchIds",
    "closureWaves",
    "envKeys",
    "missingArtifacts",
    "contentBlockers",
    "firstCommand",
    "rerunCommands",
    "stopReasons",
  ]];
  for (const owner of artifact.ownerQueues || []) {
    rows.push([
      owner.owner,
      owner.queueOrder,
      owner.executionOrderHint ?? "",
      owner.queueStatus,
      owner.canExecute,
      owner.commandCount,
      owner.envKeyCount,
      owner.missingArtifactCount,
      owner.contentBlockerCount,
      owner.stopReasonCount,
      owner.cutoverItems,
      owner.readyBatchIds,
      owner.blockedBatchIds,
      owner.closureWaves,
      owner.envKeys,
      owner.missingArtifacts,
      owner.contentBlockers,
      owner.firstCommand || "",
      owner.rerunCommands,
      owner.stopReasons,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseFinalOwnerQueueCommands(summary) {
  const artifact = releaseFinalOwnerQueueArtifact(summary);
  const ownerQueues = artifact.ownerQueues || [];
  const staticEnvValueCheckJs = [
    "import fs from 'node:fs';",
    "const [file, key] = process.argv.slice(1);",
    "const text = fs.readFileSync(file, 'utf8');",
    "const escaped = key.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');",
    "const pattern = new RegExp(`^\\\\s*(?:export\\\\s+)?${escaped}\\\\s*=\\\\s*(.*)$`, 'gm');",
    "const matches = [...text.matchAll(pattern)];",
    "if (matches.length === 0) process.exit(1);",
    "const raw = matches.at(-1)[1].trim();",
    "const value = raw.replace(/^(['\\\"])(.*)\\1$/, '$2').trim();",
    "if (!value || value === '__REQUIRED__') process.exit(1);",
  ].join(" ");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD final owner queue commands."),
    shellCommentLine(`Generated at: ${artifact.generatedAt}`),
    shellCommentLine(`Recommendation: ${artifact.recommendation}`),
    shellCommentLine("Default mode lists actionable owners. Set DDD_FINAL_OWNER_QUEUE_EXECUTE=1 to run commands."),
    ...releaseRepoRootPreambleLines(),
    "",
    "DDD_FINAL_OWNER_QUEUE_OWNER=\"${DDD_FINAL_OWNER_QUEUE_OWNER:-}\"",
    "DDD_FINAL_OWNER_QUEUE_STATUS=\"${DDD_FINAL_OWNER_QUEUE_STATUS:-ACTIONABLE}\"",
    "DDD_FINAL_OWNER_QUEUE_DETAIL=\"${DDD_FINAL_OWNER_QUEUE_DETAIL:-}\"",
    "DDD_FINAL_OWNER_QUEUE_CHECK_ENV=\"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV:-}\"",
    "DDD_FINAL_OWNER_QUEUE_EXECUTE=\"${DDD_FINAL_OWNER_QUEUE_EXECUTE:-}\"",
    "DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=\"${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR:-}\"",
    "DDD_FINAL_OWNER_QUEUE_REPORT=\"${DDD_FINAL_OWNER_QUEUE_REPORT:-artifacts/ddd/release/release-final-owner-queue-run-report.json}\"",
    "DDD_FINAL_OWNER_QUEUE_REPORT_TMP=\"${DDD_FINAL_OWNER_QUEUE_REPORT}.jsonl.$$\"",
    "DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES=0",
    "if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"true\" || \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" == \"true\" ]]; then",
    "  if [[ -z \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE is required when executing or checking final owner queue env.\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" ]]; then",
    "    echo \"Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' \"${DDD_RELEASE_ENV_FILE}\" 2>/dev/null || node -e \"const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));\" \"${DDD_RELEASE_ENV_FILE}\")",
    "  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then",
    "    echo \"Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600.\" >&2",
    "    exit 1",
    "  fi",
    "  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1",
    "fi",
    "matches_owner_queue_filter() {",
    "  local owner=\"$1\"",
    "  local status=\"$2\"",
    "  if [[ -n \"${DDD_FINAL_OWNER_QUEUE_OWNER}\" && \"${owner}\" != \"${DDD_FINAL_OWNER_QUEUE_OWNER}\" ]]; then return 1; fi",
    "  if [[ -n \"${DDD_FINAL_OWNER_QUEUE_STATUS}\" && \"${status}\" != \"${DDD_FINAL_OWNER_QUEUE_STATUS}\" ]]; then return 1; fi",
    "  return 0",
    "}",
    "env_file_has_owner_queue_key() {",
    "  local key=\"$1\"",
    `  node --input-type=module -e ${shellSingleQuoted(staticEnvValueCheckJs)} "$DDD_RELEASE_ENV_FILE" "$key"`,
    "}",
    ...safeReleaseEnvLoaderLines(),
    "check_owner_queue_env() {",
    "  local missing=0",
    "  local key",
    "  for key in \"$@\"; do",
    "    if ! env_file_has_owner_queue_key \"${key}\"; then",
    "      echo \"[ddd-final-owner-queue][env-missing] key=${key}\" >&2",
    "      missing=1",
    "    fi",
    "  done",
    "  if [[ \"${missing}\" == \"0\" ]]; then",
    "    echo \"[ddd-final-owner-queue][env-ok]\"",
    "  fi",
    "  return \"${missing}\"",
    "}",
    "append_owner_queue_report_entry() {",
    "  local owner=\"$1\"",
    "  local queue_order=\"$2\"",
    "  local queue_status=\"$3\"",
    "  local command_index=\"1\"",
    "  local command_count=\"1\"",
    "  local command=\"\"",
    "  local status=\"\"",
    "  local duration_ms=\"\"",
    "  if [[ \"$#\" -ge 8 ]]; then",
    "    command_index=\"$4\"",
    "    command_count=\"$5\"",
    "    command=\"$6\"",
    "    status=\"$7\"",
    "    duration_ms=\"$8\"",
    "  elif [[ \"$#\" -eq 6 ]]; then",
    "    command=\"$4\"",
    "    status=\"$5\"",
    "    duration_ms=\"$6\"",
    "  else",
    "    echo \"[ddd-final-owner-queue][report-entry-invalid] expected 6 legacy args or 8 indexed args, got $#\" >&2",
    "    return 2",
    "  fi",
    "  if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"true\" ]]; then return 0; fi",
    "  node --input-type=module -e 'import fs from \"node:fs\"; const [file, owner, queueOrder, queueStatus, commandIndex, commandCount, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ owner, queueOrder: Number(queueOrder), queueStatus, commandIndex: Number(commandIndex), commandCount: Number(commandCount), command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\\n`);' \"${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}\" \"${owner}\" \"${queue_order}\" \"${queue_status}\" \"${command_index}\" \"${command_count}\" \"${command}\" \"${status}\" \"${duration_ms}\"",
    "}",
    "finalize_owner_queue_report() {",
    "  local exit_code=\"$1\"",
    "  if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"true\" ]]; then return 0; fi",
    "  mkdir -p \"$(dirname \"${DDD_FINAL_OWNER_QUEUE_REPORT}\")\"",
    "  node --input-type=module -e 'import fs from \"node:fs\"; const [tmp, out, exitCode, ownerFilter, statusFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, \"utf8\").split(\"\\n\").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? \"PASS\" : \"FAIL\", exitCode: exit, ownerFilter: ownerFilter || null, statusFilter: statusFilter || null, summary, entries }, null, 2)}\\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' \"${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}\" \"${DDD_FINAL_OWNER_QUEUE_REPORT}\" \"${exit_code}\" \"${DDD_FINAL_OWNER_QUEUE_OWNER}\" \"${DDD_FINAL_OWNER_QUEUE_STATUS}\"",
    "  if ! DDD_FINAL_OWNER_QUEUE_REPORT=\"${DDD_FINAL_OWNER_QUEUE_REPORT}\" node scripts/ddd-final-owner-queue-run-report-contract.mjs; then",
    "    echo \"[ddd-final-owner-queue][report-contract] failed\" >&2",
    "    return 1",
    "  fi",
    "  echo \"[ddd-final-owner-queue][report] ${DDD_FINAL_OWNER_QUEUE_REPORT}\"",
    "  return \"${exit_code}\"",
    "}",
    "run_owner_queue_command() {",
    "  local owner=\"$1\"",
    "  local queue_order=\"$2\"",
    "  local queue_status=\"$3\"",
    "  local command_index=\"$4\"",
    "  local command_count=\"$5\"",
    "  local command=\"$6\"",
    "  local execution_command=\"${command//DDD_RELEASE_ENV_FILE=<release-env-file>/DDD_RELEASE_ENV_FILE=${DDD_RELEASE_ENV_FILE:-}}\"",
    "  if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"true\" ]]; then",
    "    echo \"[ddd-final-owner-queue][dry-run] ${command}\"",
    "    return 0",
    "  fi",
    "  local started_ms",
    "  local finished_ms",
    "  local status",
    "  started_ms=$(node -e 'console.log(Date.now())')",
    "  set +e",
    "  bash -lc \"${execution_command}\"",
    "  status=$?",
    "  set -e",
    "  finished_ms=$(node -e 'console.log(Date.now())')",
    "  append_owner_queue_report_entry \"${owner}\" \"${queue_order}\" \"${queue_status}\" \"${command_index}\" \"${command_count}\" \"${command}\" \"${status}\" \"$((finished_ms - started_ms))\"",
    "  if [[ \"${status}\" != \"0\" ]]; then",
    "    echo \"[ddd-final-owner-queue][command-failed] owner=${owner} status=${status} command=${command}\" >&2",
    "    DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES=$((DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES + 1))",
    "    if [[ \"${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}\" == \"true\" ]]; then",
    "      echo \"[ddd-final-owner-queue][command-failed] continuing because DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}\" >&2",
    "      return 0",
    "    fi",
    "  fi",
    "  return \"${status}\"",
    "}",
    "if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"true\" ]]; then",
    "  mkdir -p \"$(dirname \"${DDD_FINAL_OWNER_QUEUE_REPORT}\")\"",
    "  : > \"${DDD_FINAL_OWNER_QUEUE_REPORT_TMP}\"",
    "  trap 'finalize_owner_queue_report \"$?\"' EXIT",
    "  safe_load_release_env_file",
    "fi",
    "",
    "DDD_FINAL_OWNER_QUEUE_MATCHED=0",
    "if [[ \"${DDD_FINAL_OWNER_QUEUE_DETAIL}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_DETAIL}\" != \"true\" && \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" != \"true\" && \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" != \"true\" ]]; then",
    "  echo \"Final owner queue:\"",
  ];
  for (const owner of artifact.ownerQueues || []) {
    lines.push(`  if matches_owner_queue_filter ${shellSingleQuoted(owner.owner)} ${shellSingleQuoted(owner.queueStatus)}; then`);
    lines.push("    DDD_FINAL_OWNER_QUEUE_MATCHED=1");
    lines.push(`    echo ${shellSingleQuoted(`[ddd-final-owner-queue] order=${owner.queueOrder} owner=${owner.owner} status=${owner.queueStatus} ready=${owner.readyBatchIds.length} blocked=${owner.blockedBatchIds.length} missingArtifacts=${owner.missingArtifactCount} contentBlockers=${owner.contentBlockerCount} first=${owner.firstCommand || "none"}`)}`);
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_FINAL_OWNER_QUEUE_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No final owner queue item matched the requested filters.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  for (const owner of ownerQueues) {
    lines.push(`if matches_owner_queue_filter ${shellSingleQuoted(owner.owner)} ${shellSingleQuoted(owner.queueStatus)}; then`);
    lines.push("  DDD_FINAL_OWNER_QUEUE_MATCHED=1");
    lines.push(`  echo ${shellSingleQuoted(`[ddd-final-owner-queue] order=${owner.queueOrder} owner=${owner.owner} status=${owner.queueStatus}`)}`);
    lines.push("  echo \"commands:\"");
    for (const command of owner.commands || []) {
      lines.push(`  echo ${shellSingleQuoted(`- ${command}`)}`);
    }
    lines.push("  if [[ \"${DDD_FINAL_OWNER_QUEUE_DETAIL}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_DETAIL}\" == \"true\" ]]; then");
    lines.push("    echo \"envKeys:\"");
    for (const key of owner.envKeys || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${key}`)}`);
    }
    lines.push("    echo \"missingArtifacts:\"");
    for (const artifactPath of owner.missingArtifacts || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${artifactPath}`)}`);
    }
    lines.push("    echo \"contentBlockers:\"");
    for (const blocker of owner.contentBlockers || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${blocker}`)}`);
    }
    lines.push("    echo \"rerunCommands:\"");
    for (const command of owner.rerunCommands || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${command}`)}`);
    }
    lines.push("  fi");
    lines.push("  if [[ \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_CHECK_ENV}\" == \"true\" ]]; then");
    if ((owner.envKeys || []).length > 0) {
      lines.push(`    check_owner_queue_env ${(owner.envKeys || []).map(shellSingleQuoted).join(" ")}`);
    } else {
      lines.push("    echo \"[ddd-final-owner-queue][env-ok] no env keys required\"");
    }
    lines.push("  else");
    if (owner.queueStatus !== "ACTIONABLE") {
      lines.push("    if [[ \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"1\" || \"${DDD_FINAL_OWNER_QUEUE_EXECUTE}\" == \"true\" ]]; then");
      lines.push(`      echo ${shellSingleQuoted(`[ddd-final-owner-queue][blocked] owner=${owner.owner} status=${owner.queueStatus}; resolve dependencies before executing this owner queue.`)} >&2`);
      lines.push("      exit 1");
      lines.push("    fi");
      lines.push(`    echo ${shellSingleQuoted(`[ddd-final-owner-queue][waiting] owner=${owner.owner} status=${owner.queueStatus}; use DETAIL or CHECK_ENV for diagnostics.`)}`);
    } else {
      const ownerCommands = owner.commands || [];
      for (const [commandIndex, command] of ownerCommands.entries()) {
        lines.push(`    run_owner_queue_command ${shellSingleQuoted(owner.owner)} ${shellSingleQuoted(owner.queueOrder)} ${shellSingleQuoted(owner.queueStatus)} ${shellSingleQuoted(commandIndex + 1)} ${shellSingleQuoted(ownerCommands.length)} ${shellSingleQuoted(command)}`);
      }
    }
    lines.push("  fi");
    lines.push("fi");
    lines.push("");
  }
  lines.push("if [[ \"${DDD_FINAL_OWNER_QUEUE_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No actionable final owner queue item matched the requested filters.\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("");
  lines.push(shellCommentLine("After owner commands refresh evidence, rerun:"));
  lines.push("run_owner_queue_command 'post-run' '0' 'POST_RUN' '1' '3' 'node scripts/ddd-release-evidence-gate.mjs'");
  lines.push("run_owner_queue_command 'post-run' '0' 'POST_RUN' '2' '3' 'node scripts/ddd-release-readiness-summary.mjs'");
  lines.push("run_owner_queue_command 'post-run' '0' 'POST_RUN' '3' '3' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'");
  lines.push("if [[ \"${DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES}\" != \"0\" ]]; then");
  lines.push("  echo \"[ddd-final-owner-queue][completed-with-failures] commandFailures=${DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  return `${lines.join("\n")}\n`;
}

function releaseFinalOwnerQueueEnvTemplate(summary) {
  const artifact = releaseFinalOwnerQueueArtifact(summary);
  const nextOwner = (artifact.ownerQueues || []).find((owner) => owner.canExecute === true)?.owner
    || artifact.ownerQueues?.[0]?.owner
    || "release-owner";
  const lines = [
    "# Lumira DDD final owner queue environment template.",
    "# Fill real values in a secure file and point DDD_RELEASE_ENV_FILE at it.",
    "# Do not commit populated secrets.",
    `# Generated at: ${artifact.generatedAt}`,
    `# Status: ${artifact.status}`,
    `# Recommendation: ${artifact.recommendation}`,
    "",
  ];
  const emitted = new Set();
  for (const owner of artifact.ownerQueues || []) {
    lines.push(`# Owner: ${owner.owner}`);
    lines.push(`# Queue order: ${owner.queueOrder}`);
    lines.push(`# Queue status: ${owner.queueStatus}`);
    lines.push(`# Can execute: ${owner.canExecute}`);
    lines.push(`# Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`# Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`# First command: ${owner.firstCommand || "none"}`);
    for (const key of owner.envKeys || []) {
      if (emitted.has(key)) {
        const defaultValue = finalOwnerQueueSafeEnvDefaults.get(key);
        lines.push(`# ${key}=${defaultValue ?? "__REQUIRED__"} # already declared above`);
      } else {
        const defaultValue = finalOwnerQueueSafeEnvDefaults.get(key);
        if (defaultValue === undefined) {
          lines.push(`${key}=__REQUIRED__`);
        } else {
          lines.push("# Safe default: non-secret release automation value; override if your environment differs.");
          lines.push(`${key}=${defaultValue}`);
        }
        emitted.add(key);
      }
    }
    lines.push("");
  }
  lines.push("# Usage:");
  lines.push("# export DDD_RELEASE_ENV_FILE=/secure/path/to/.env.release");
  lines.push(`# DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1 DDD_FINAL_OWNER_QUEUE_OWNER=${nextOwner} bash artifacts/ddd/release/release-final-owner-queue-commands.sh`);
  lines.push(`# DDD_FINAL_OWNER_QUEUE_EXECUTE=1 DDD_FINAL_OWNER_QUEUE_OWNER=${nextOwner} bash artifacts/ddd/release/release-final-owner-queue-commands.sh`);
  lines.push(`# DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1 DDD_FINAL_OWNER_QUEUE_EXECUTE=1 DDD_FINAL_OWNER_QUEUE_OWNER=${nextOwner} bash artifacts/ddd/release/release-final-owner-queue-commands.sh # diagnostic only; final exit remains non-zero on failures`);
  return `${lines.join("\n")}\n`;
}

function releaseFinalOwnerQueueEnvInit(summary) {
  const artifact = releaseFinalOwnerQueueArtifact(summary);
  const templatePath = path.relative(repoRoot, releaseFinalOwnerQueueEnvTemplateOutput);
  const nextOwner = (artifact.ownerQueues || []).find((owner) => owner.canExecute === true)?.owner
    || artifact.ownerQueues?.[0]?.owner
    || "release-owner";
  const receiptCode = [
    "import fs from 'node:fs';",
    `const safeDefaults = new Map(${JSON.stringify([...finalOwnerQueueSafeEnvDefaults])});`,
    "const [templatePath, targetPath, receiptPath, nextOwner] = process.argv.slice(1);",
    "let text = fs.readFileSync(targetPath, 'utf8');",
    "const dynamicDefaultKeys = [];",
    "if (/^DDD_RELEASE_ENV_FILE=__REQUIRED__$/m.test(text)) { text = text.replace(/^DDD_RELEASE_ENV_FILE=__REQUIRED__$/m, `DDD_RELEASE_ENV_FILE=${targetPath}`); dynamicDefaultKeys.push('DDD_RELEASE_ENV_FILE'); fs.writeFileSync(targetPath, text); }",
    "const modeOctal = (fs.statSync(targetPath).mode & 0o777).toString(8).padStart(3, '0');",
    "const unresolvedTemplateKeys = [...text.matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);",
    "const escapeRegExp = (value) => String(value).replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');",
    "const safeDefaultKeys = [...safeDefaults].filter(([key, value]) => new RegExp(`^${escapeRegExp(key)}=${escapeRegExp(value)}$`, 'm').test(text)).map(([key]) => key);",
    "const receipt = {",
    "  generatedAt: new Date().toISOString(),",
    "  templatePath,",
    "  targetPath,",
    "  targetModeOctal: modeOctal,",
    "  permissionSafe: modeOctal === '600',",
    "  safeDefaultKeyCount: safeDefaultKeys.length,",
    "  safeDefaultKeys,",
    "  dynamicDefaultKeyCount: dynamicDefaultKeys.length,",
    "  dynamicDefaultKeys,",
    "  unresolvedTemplateKeyCount: unresolvedTemplateKeys.length,",
    "  unresolvedTemplateKeys,",
    "  artifactIntegrityGateCommand: 'bash artifacts/ddd/release/release-artifact-integrity-gate.sh',",
    "  artifactIntegrityArtifact: 'artifacts/ddd/release/release-artifact-integrity.json',",
    "  artifactIntegrityMarkdown: 'artifacts/ddd/release/release-artifact-integrity.md',",
    "  envSafeDefaultsCommand: 'node scripts/ddd-release-env-safe-defaults.mjs',",
    "  envSafeDefaultsArtifact: 'artifacts/ddd/release/release-env-safe-defaults.json',",
    "  provenanceDefaultsCommand: 'node scripts/ddd-release-provenance-defaults.mjs',",
    "  provenanceDefaultsArtifact: 'artifacts/ddd/release/release-provenance-defaults.json',",
    "  envReadinessGateCommand: 'DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh',",
    "  envReadinessArtifact: 'artifacts/ddd/release/release-env-readiness-redacted.json',",
    "  envReadinessCsv: 'artifacts/ddd/release/release-env-readiness-redacted.csv',",
    "  finalGoNoGoGateCommand: 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh',",
    "  finalGoNoGoPacket: 'artifacts/ddd/release/release-final-go-no-go.json',",
    "  finalGoNoGoMarkdown: 'artifacts/ddd/release/release-final-go-no-go.md',",
    "  ownerHandoffArtifact: 'artifacts/ddd/release/release-env-owner-handoff-redacted.json',",
    "  ownerHandoffCsv: 'artifacts/ddd/release/release-env-owner-handoff-redacted.csv',",
    "  ownerHandoffDir: 'artifacts/ddd/release/release-env-owner-handoff-redacted',",
    "  nextCommands: [",
    "    'bash artifacts/ddd/release/release-artifact-integrity-gate.sh',",
    "    `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${targetPath}`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-env-safe-defaults.mjs`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-provenance-defaults.mjs`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-env-alias-sync.mjs`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`,",
    "    'DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh',",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-env-file-lint.mjs`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1 DDD_FINAL_OWNER_QUEUE_OWNER=${nextOwner} bash artifacts/ddd/release/release-final-owner-queue-commands.sh`,",
    "    `DDD_RELEASE_ENV_FILE=${targetPath} node scripts/ddd-release-readiness-summary.mjs`,",
    "    'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh',",
    "  ],",
    "};",
    "fs.writeFileSync(receiptPath, `${JSON.stringify(receipt, null, 2)}\\n`);",
  ].join(" ");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD final owner queue env initializer."),
    shellCommentLine(`Generated at: ${artifact.generatedAt}`),
    shellCommentLine("Creates a local release env file from the generated template without overwriting existing secrets."),
    ...releaseRepoRootPreambleLines(),
    "",
    `DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE="\${DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE:-${templatePath}}"`,
    "DDD_FINAL_OWNER_QUEUE_ENV_TARGET=\"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET:-${DDD_RELEASE_ENV_FILE:-.env.release.local}}\"",
    "DDD_FINAL_OWNER_QUEUE_ENV_FORCE=\"${DDD_FINAL_OWNER_QUEUE_ENV_FORCE:-}\"",
    "DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT=\"${DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT:-artifacts/ddd/release/release-final-owner-queue-env-init-receipt.json}\"",
    "",
    "if [[ ! -f \"${DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE}\" ]]; then",
    "  echo \"Template does not exist: ${DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE}\" >&2",
    "  exit 1",
    "fi",
    "if [[ \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" == *\"release-final-owner-queue-env.template.env\" || \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" == *\"release-env-missing.template.env\" || \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" == *\"release-env-canonical-fill.template.env\" ]]; then",
    "  echo \"Refusing to use a generated template as the populated release env target: ${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" >&2",
    "  exit 1",
    "fi",
    "if [[ -e \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" && \"${DDD_FINAL_OWNER_QUEUE_ENV_FORCE}\" != \"1\" && \"${DDD_FINAL_OWNER_QUEUE_ENV_FORCE}\" != \"true\" ]]; then",
    "  echo \"Release env target already exists: ${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\" >&2",
    "  echo \"Set DDD_FINAL_OWNER_QUEUE_ENV_FORCE=1 only after backing up the existing file.\" >&2",
    "  exit 1",
    "fi",
    "",
    "mkdir -p \"$(dirname \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\")\"",
    "cp \"${DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE}\" \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\"",
    "chmod 600 \"${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\"",
    "mkdir -p \"$(dirname \"${DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT}\")\"",
    `node --input-type=module -e ${shellSingleQuoted(receiptCode)} "$DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE" "$DDD_FINAL_OWNER_QUEUE_ENV_TARGET" "$DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT" ${shellSingleQuoted(nextOwner)}`,
    "echo \"[ddd-final-owner-queue][env-init] target=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\"",
    "echo \"[ddd-final-owner-queue][env-init] receipt=${DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT}\"",
    "echo \"[ddd-final-owner-queue][env-init] fill __REQUIRED__ values, then run:\"",
    "echo \"bash artifacts/ddd/release/release-artifact-integrity-gate.sh\"",
    "echo \"node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${DDD_FINAL_OWNER_QUEUE_ENV_TARGET}\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-env-safe-defaults.mjs\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-provenance-defaults.mjs\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-env-alias-sync.mjs\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env\"",
    "echo \"DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh\"",
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-env-file-lint.mjs\"",
    `echo "DDD_RELEASE_ENV_FILE=\${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1 DDD_FINAL_OWNER_QUEUE_OWNER=${nextOwner} bash artifacts/ddd/release/release-final-owner-queue-commands.sh"`,
    "echo \"DDD_RELEASE_ENV_FILE=${DDD_FINAL_OWNER_QUEUE_ENV_TARGET} node scripts/ddd-release-readiness-summary.mjs\"",
    "echo \"DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh\"",
  ];
  return `${lines.join("\n")}\n`;
}

function releaseEnvBootstrapScript(summary) {
  const safetySignals = releaseSafetySignals(summary);
  const defaultEnvFile = ".env.release.local";
  const receiptCode = [
    "import fs from 'node:fs';",
    "import path from 'node:path';",
    "const [receiptPath, status, exitCode, step, envFile, canonicalFile, ownerDir, repoRoot] = process.argv.slice(1);",
    "function portablePath(value) {",
    "  if (!value) return value;",
    "  const absolute = path.resolve(value);",
    "  const root = repoRoot ? path.resolve(repoRoot) : process.cwd();",
    "  if (absolute === root) return '.';",
    "  if (absolute.startsWith(`${root}${path.sep}`)) return path.relative(root, absolute) || '.';",
    "  const homeDir = process.env.HOME ? path.resolve(process.env.HOME) : '';",
    "  if (homeDir && absolute === homeDir) return '~';",
    "  if (homeDir && absolute.startsWith(`${homeDir}${path.sep}`)) return `~/${path.relative(homeDir, absolute)}`;",
    "  return value;",
    "}",
    "const portableEnvFile = portablePath(envFile);",
    "const receipt = {",
    "  generatedAt: new Date().toISOString(),",
    "  status,",
    "  exitCode: Number(exitCode),",
    "  step,",
    "  failedStep: status === 'FAIL' ? step : null,",
    "  completedStep: status === 'PASS' ? step : null,",
    "  envFile: portableEnvFile,",
    "  canonicalEnvFile: portablePath(canonicalFile),",
    "  ownerTemplateDir: portablePath(ownerDir),",
    "  repoRoot: portablePath(repoRoot),",
    "  receiptPath: portablePath(receiptPath),",
    "  artifactIntegrityGateCommand: 'bash artifacts/ddd/release/release-artifact-integrity-gate.sh',",
    "  artifactIntegrityArtifact: 'artifacts/ddd/release/release-artifact-integrity.json',",
    "  artifactIntegrityMarkdown: 'artifacts/ddd/release/release-artifact-integrity.md',",
    "  envSafeDefaultsCommand: 'node scripts/ddd-release-env-safe-defaults.mjs',",
    "  envSafeDefaultsArtifact: 'artifacts/ddd/release/release-env-safe-defaults.json',",
    "  provenanceDefaultsCommand: 'node scripts/ddd-release-provenance-defaults.mjs',",
    "  provenanceDefaultsArtifact: 'artifacts/ddd/release/release-provenance-defaults.json',",
    "  envReadinessGateCommand: 'DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh',",
    "  envReadinessArtifact: 'artifacts/ddd/release/release-env-readiness-redacted.json',",
    "  envReadinessCsv: 'artifacts/ddd/release/release-env-readiness-redacted.csv',",
    "  ownerHandoffArtifact: 'artifacts/ddd/release/release-env-owner-handoff-redacted.json',",
    "  ownerHandoffCsv: 'artifacts/ddd/release/release-env-owner-handoff-redacted.csv',",
    "  ownerHandoffDir: 'artifacts/ddd/release/release-env-owner-handoff-redacted',",
    "  finalGoNoGoGateCommand: 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh',",
    "  finalGoNoGoPacket: 'artifacts/ddd/release/release-final-go-no-go.json',",
    "  finalGoNoGoMarkdown: 'artifacts/ddd/release/release-final-go-no-go.md',",
    "  nextCommand: `DDD_RELEASE_ENV_FILE=${portableEnvFile} bash artifacts/ddd/release/release-env-bootstrap.sh`,",
    "};",
    "fs.mkdirSync(path.dirname(receiptPath), { recursive: true });",
    "fs.writeFileSync(receiptPath, `${JSON.stringify(receipt, null, 2)}\\n`);",
  ].join(" ");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release env bootstrap."),
    shellCommentLine("Merges owner-scoped env templates, canonical values, aliases, and strict env/config evidence."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    ...releaseRepoRootPreambleLines(),
    "",
    `DDD_RELEASE_ENV_FILE="\${DDD_RELEASE_ENV_FILE:-${defaultEnvFile}}"`,
    "DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR=\"${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR:-artifacts/ddd/release/release-env-owner-templates}\"",
    "DDD_RELEASE_CANONICAL_ENV_FILE=\"${DDD_RELEASE_CANONICAL_ENV_FILE:-artifacts/ddd/release/release-env-canonical-fill.template.env}\"",
    "DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT=\"${DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT:-artifacts/ddd/release/release-env-bootstrap-receipt.json}\"",
    "DDD_NODE_BIN=\"${DDD_NODE_BIN:-node}\"",
    "export DDD_NODE_BIN",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"init\"",
    "write_bootstrap_receipt() {",
    "  local status=\"$1\"",
    "  local exit_code=\"$2\"",
    "  local step=\"$3\"",
    `  "\${DDD_NODE_BIN}" --input-type=module -e ${shellSingleQuoted(receiptCode)} "$DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT" "$status" "$exit_code" "$step" "$DDD_RELEASE_ENV_FILE" "$DDD_RELEASE_CANONICAL_ENV_FILE" "$DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR" "$LUMIRA_REPO_ROOT"`,
    "}",
    "on_bootstrap_exit() {",
    "  local exit_code=\"$?\"",
    "  if [[ \"${exit_code}\" -ne 0 ]]; then",
    "    write_bootstrap_receipt FAIL \"${exit_code}\" \"${DDD_RELEASE_ENV_BOOTSTRAP_STEP}\"",
    "  fi",
    "}",
    "trap on_bootstrap_exit EXIT",
    "",
    "if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-canonical-fill.template.env\" ]]; then",
    "  echo \"Refusing to use a generated template as DDD_RELEASE_ENV_FILE: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "  exit 1",
    "fi",
    "if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then",
    "  echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "  echo \"Run: bash artifacts/ddd/release/release-final-owner-queue-env-init.sh\" >&2",
    "  exit 1",
    "fi",
    "if [[ ! -d \"${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}\" ]]; then",
    "  echo \"Owner template dir does not exist: ${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}\" >&2",
    "  exit 1",
    "fi",
    "if [[ ! -f \"${DDD_RELEASE_CANONICAL_ENV_FILE}\" ]]; then",
    "  echo \"Canonical env file does not exist: ${DDD_RELEASE_CANONICAL_ENV_FILE}\" >&2",
    "  exit 1",
    "fi",
    "if [[ \"${DDD_RELEASE_CANONICAL_ENV_FILE}\" != *\"release-env-canonical-fill.template.env\" ]]; then",
    "  echo \"Refusing to use a non-canonical generated env file as DDD_RELEASE_CANONICAL_ENV_FILE: ${DDD_RELEASE_CANONICAL_ENV_FILE}\" >&2",
    "  exit 1",
    "fi",
    "",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"owner-templates-merge\"",
    "echo \"[ddd-release-env-bootstrap] owner templates -> canonical\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-owner-templates-merge.mjs \"${DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR}\" \"${DDD_RELEASE_CANONICAL_ENV_FILE}\"",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"canonical-merge\"",
    "echo \"[ddd-release-env-bootstrap] canonical -> release env\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-canonical-merge.mjs \"${DDD_RELEASE_CANONICAL_ENV_FILE}\" \"${DDD_RELEASE_ENV_FILE}\"",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"safe-defaults\"",
    "echo \"[ddd-release-env-bootstrap] safe defaults\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-safe-defaults.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"provenance-defaults\"",
    "echo \"[ddd-release-env-bootstrap] provenance defaults\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-provenance-defaults.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"alias-sync\"",
    "echo \"[ddd-release-env-bootstrap] alias sync\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-alias-sync.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"canonical-lint\"",
    "echo \"[ddd-release-env-bootstrap] canonical lint\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-canonical-lint.mjs \"${DDD_RELEASE_CANONICAL_ENV_FILE}\"",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"env-readiness-gate\"",
    "echo \"[ddd-release-env-bootstrap] env readiness gate\"",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"release-env-lint\"",
    "echo \"[ddd-release-env-bootstrap] release env lint\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"release-config-evidence\"",
    "echo \"[ddd-release-env-bootstrap] release config evidence\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-config-evidence.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"manifest-provenance-env\"",
    "echo \"[ddd-release-env-bootstrap] manifest provenance env\"",
    "DDD_RELEASE_MANIFEST_CHECK_ENV=true \"${DDD_NODE_BIN}\" scripts/ddd-release-evidence-manifest.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"readiness-summary\"",
    "echo \"[ddd-release-env-bootstrap] readiness summary\"",
    "\"${DDD_NODE_BIN}\" scripts/ddd-release-readiness-summary.mjs",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"final-go-no-go\"",
    "echo \"[ddd-release-env-bootstrap] final go/no-go\"",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
    "DDD_RELEASE_ENV_BOOTSTRAP_STEP=\"complete\"",
    "write_bootstrap_receipt PASS 0 \"${DDD_RELEASE_ENV_BOOTSTRAP_STEP}\"",
    "trap - EXIT",
    "echo \"[ddd-release-env-bootstrap] receipt=${DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT}\"",
  ];
  return `${lines.join("\n")}\n`;
}

function commandHintsFromAction(action) {
  const text = String(action || "");
  const commands = [...text.matchAll(/`([^`]+)`/g)]
    .map((match) => match[1].trim())
    .map(normalizeCommandHint)
    .filter((command) => /^(?:(?:[A-Z0-9_]+=\S+|export\s+[A-Z0-9_]+=.+)\s+)*(?:DDD_[A-Z0-9_]+=\S+\s+)*node\s+scripts\//.test(command));
  return [...new Set(commands)];
}

function normalizeCommandHint(command) {
  return command === "node scripts/ddd-release-evidence-manifest.mjs"
    ? "DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs"
    : command;
}

const defaultCommandHintsBySource = {
  "ai-runtime": ["node scripts/ddd-ai-runtime-drill.mjs"],
  "authenticated-performance": [
    "node scripts/ddd-authenticated-performance-smoke.mjs",
    "node scripts/ddd-promote-performance-baseline.mjs",
  ],
  docker: ["DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs"],
  explain: [
    "node scripts/ddd-collect-explain.mjs",
    "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
  ],
  "frontend-smoke": [
    "node scripts/ddd-frontend-playwright-smoke.mjs",
    "node scripts/ddd-frontend-smoke-evidence.mjs",
  ],
  manifest: [
    "DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs",
    "node scripts/ddd-promote-performance-baseline.mjs",
    "DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs",
  ],
  migration: [
    "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    "node scripts/ddd-migration-evidence.mjs",
  ],
  orchestrator: [
    "node scripts/ddd-release-evidence-orchestrator.mjs",
    "DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict",
  ],
  "release-config": ["node scripts/ddd-release-config-evidence.mjs"],
  "release-env-lint": [
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    "node scripts/ddd-release-config-evidence.mjs",
  ],
  rollback: [
    "node scripts/ddd-rollback-deferral-template.mjs",
    "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
    "node scripts/ddd-rollback-drill-evidence.mjs",
  ],
  "runtime-readiness": ["node scripts/ddd-runtime-readiness-smoke.mjs"],
};

const defaultEnvKeysBySource = {
  explain: [
    "DDD_EXPLAIN_DIR",
    "DDD_EXPLAIN_STRICT",
    "DDD_EXPLAIN_ENVIRONMENT",
    "DDD_RELEASE_CANDIDATE",
    "DDD_EVIDENCE_OPERATOR",
    "MYSQL_CLI",
    "MYSQL_HOST",
    "MYSQL_PORT",
    "MYSQL_USER",
    "MYSQL_PASSWORD",
    "MYSQL_DATABASE",
    "DDD_EXPLAIN_DATABASE",
  ],
  migration: [
    "DDD_MIGRATION_ENVIRONMENT",
    "DDD_MIGRATION_OPERATOR",
    "DDD_MIGRATION_COMPLETED_AT",
    "DDD_MIGRATION_FRESH_DB_VALIDATED",
    "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
    "DDD_MIGRATION_FRESH_DB_EVIDENCE",
    "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
    "DDD_MIGRATION_HANDOFF_FILE",
    "DDD_RELEASE_CANDIDATE",
    "DDD_EVIDENCE_OPERATOR",
  ],
  rollback: [
    "DDD_ROLLBACK_DRILL_FILE",
    "DDD_ROLLBACK_DRILL_CHECK_ENV",
    "DDD_ROLLBACK_DRILL_HANDOFF_FILE",
    "DDD_ROLLBACK_DRILL_STRICT",
    "DDD_ROLLBACK_DRILL_DEFERRAL_FILE",
    "DDD_EVIDENCE_ENVIRONMENT",
    "DDD_RELEASE_CANDIDATE",
    "DDD_EVIDENCE_OPERATOR",
  ],
};

const businessCommandHintsByOwner = {
  "file-owner": ["node scripts/ddd-file-processing-e2e-smoke.mjs"],
  "job-owner": ["node scripts/ddd-job-e2e-smoke.mjs"],
  "payment-owner": ["node scripts/ddd-payment-webhook-e2e-smoke.mjs"],
};

function requiredCommandHintsForBatch(batch) {
  return batch.source === "business-e2e"
    ? (businessCommandHintsByOwner[batch.owner] || [])
    : (defaultCommandHintsBySource[batch.source] || []);
}

function commandHintsForItem(item) {
  const sourceDefaults = item.source === "business-e2e"
    ? (businessCommandHintsByOwner[item.owner] || [])
    : (defaultCommandHintsBySource[item.source] || []);
  return [...new Set([...sourceDefaults, ...commandHintsFromAction(item.action)])];
}

function strictGateSourceForAction(action = {}) {
  const category = String(action.category || "");
  const check = String(action.check || "");
  if (category.startsWith("migration") || check.startsWith("migration-evidence")) {
    return "migration";
  }
  if (category.startsWith("rollback") || check.startsWith("rollback-drill")) {
    return "rollback";
  }
  if (category.startsWith("explain") || check.startsWith("explain-evidence")) {
    return "explain";
  }
  if (category.startsWith("manifest") || check.startsWith("release-evidence-manifest")) {
    return "manifest";
  }
  if (category.startsWith("performance") || check.startsWith("authenticated-performance")) {
    return "authenticated-performance";
  }
  if (category.startsWith("docker") || check.startsWith("docker-build-evidence")) {
    return "docker";
  }
  return null;
}

const expectedArtifactsBySource = {
  "ai-runtime": ["artifacts/ddd/ai/ai-runtime-drill.json"],
  "authenticated-performance": [
    "artifacts/ddd/performance/authenticated-runtime-actual.json",
    "artifacts/ddd/performance/authenticated-runtime-baseline.json",
    "artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json",
  ],
  docker: ["artifacts/ddd/build/docker-image-evidence.json"],
  explain: ["tmp/ddd-explain/*.json", "artifacts/ddd/release/explain-gate-report.json"],
  "frontend-smoke": ["artifacts/ddd/frontend/frontend-smoke.json", "artifacts/ddd/frontend/playwright-smoke-results.json"],
  manifest: ["artifacts/ddd/release/evidence-manifest.json"],
  migration: ["artifacts/ddd/migration/migration-evidence.json"],
  orchestrator: ["artifacts/ddd/release/orchestrator-report.json", "artifacts/ddd/release/release-evidence-gate.json", "artifacts/ddd/release/readiness-summary.json"],
  "release-config": ["artifacts/ddd/config/release-config-evidence.json"],
  "release-env-lint": ["artifacts/ddd/release/release-env-lint.json", "artifacts/ddd/config/release-config-evidence.json"],
  rollback: ["artifacts/ddd/rollback/rollback-drill.json"],
  "runtime-readiness": ["artifacts/ddd/readiness/summary.json"],
};

function manifestExpectedArtifactsForBatch(batch) {
  const artifacts = ["artifacts/ddd/release/evidence-manifest.json"];
  const add = (artifact) => {
    if (artifact && !artifacts.includes(artifact)) {
      artifacts.push(artifact);
    }
  };
  for (const item of batch.items || []) {
    const artifact = item.artifact || String(item.reason || "").replace(/^missing artifact\s+/i, "");
    if (!artifact || artifact === item.reason) {
      continue;
    }
    add(artifact.startsWith("artifacts/") ? artifact : `artifacts/ddd/${artifact}`);
    if (artifact.includes("performance/authenticated-runtime-baseline.json")) {
      add("artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json");
    }
  }
  return artifacts;
}

const exitCriteriaBySource = {
  "release-env-lint": [
    "Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing.template.env.",
    "release-env-lint summary primaryBlockers is 0 before expensive runtime evidence is rerun.",
  ],
  "release-config": [
    "release-config-evidence status is PASS with no contract issues.",
    "releaseConfigBlockersAfterPlaceholders is 0 after placeholders are replaced.",
  ],
  docker: [
    "Docker CLI and daemon are available in the evidence runner.",
    "Required lumira-server and frontend images are built, inspected, and not skipped.",
  ],
  "runtime-readiness": [
    "Runtime readiness is generated from an HTTPS non-local backend base URL.",
    "All 30 owner readiness/health/metrics checks pass.",
  ],
  migration: [
    "Fresh database and previous-schema upgrade Flyway drills both have concrete evidence.",
    "migration-evidence runtimeReady is true with non-placeholder provenance.",
  ],
  manifest: [
    "All required release evidence artifacts are present and checksummed.",
  ],
  "authenticated-performance": [
    "Authenticated performance actual is generated from a production-equivalent HTTPS backend.",
    "Accepted baseline exists and current p95/upload metrics do not regress beyond the configured threshold.",
  ],
  "ai-runtime": [
    "AI runtime drill uses HTTPS non-local base URL with remote provider and owner gateway expectations enabled.",
    "Provider is not local fallback and owner gateway has configured owner integrations.",
  ],
  "frontend-smoke": [
    "Frontend smoke runs against a deployed HTTPS frontend with DDD_FRONTEND_EXPECT_DEPLOYED=true.",
    "Required Playwright smoke flows all pass and produce a JSON report.",
  ],
  "business-e2e": [
    "File, Job, and Payment business E2E artifacts are generated from HTTPS non-local runtime endpoints.",
    "Owner-specific task/webhook/job contract checks pass with production-equivalence metadata.",
  ],
  rollback: [
    "Each bounded context has PASS rollback drill evidence or approved unexpired DEFERRED risk acceptance.",
  ],
  explain: [
    "Production-equivalent MySQL EXPLAIN artifacts are freshly collected for every required hot path.",
    "Strict explain gate has no full scans, legacy imports, missing indexes, or contract issues.",
  ],
  orchestrator: [
    "Rerun release evidence orchestrator in strict run mode after P0/P1/P2 batches are clean.",
    "Final strict release gate and readiness summary report zero blockers.",
  ],
};

function expectedArtifactsForBatch(batch) {
  if (batch.source === "business-e2e") {
    const ownerArtifacts = {
      "file-owner": ["artifacts/ddd/file/file-processing-e2e.json"],
      "job-owner": ["artifacts/ddd/jobs/job-e2e-smoke.json"],
      "payment-owner": ["artifacts/ddd/payment/payment-webhook-e2e.json"],
    };
    return ownerArtifacts[batch.owner] || [
      "artifacts/ddd/file/file-processing-e2e.json",
      "artifacts/ddd/jobs/job-e2e-smoke.json",
      "artifacts/ddd/payment/payment-webhook-e2e.json",
    ];
  }
  if (batch.source === "manifest") {
    return manifestExpectedArtifactsForBatch(batch);
  }
  return expectedArtifactsBySource[batch.source] || [];
}

function exitCriteriaForBatch(batch) {
  const criteria = [...(exitCriteriaBySource[batch.source] || [])];
  if (batch.priority === "P0") {
    criteria.push("Clear this batch before running downstream runtime-heavy evidence.");
  } else if (batch.priority === "P3") {
    criteria.push("Run only after all prerequisite evidence batches are clean.");
  }
  return [...new Set(criteria)];
}

function releaseBatchId(batch) {
  return [batch.priority, batch.source, batch.owner]
    .map((part) => String(part || "unknown").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""))
    .join("-");
}

function releaseBatchPriorityRank(priority) {
  return { P0: 0, P1: 1, P2: 2, P3: 3 }[priority] ?? 99;
}

function releaseActionBatches(summary) {
  const byBatch = new Map();
  const ensureBatch = (item) => {
    const key = `${item.priority}:${item.source}:${item.owner}`;
    if (!byBatch.has(key)) {
      byBatch.set(key, {
        key,
        priority: item.priority,
        source: item.source,
        owner: item.owner,
        pendingItems: 0,
        envKeys: [],
        commands: [],
        items: [],
      });
    }
    return byBatch.get(key);
  };
  for (const item of summary.releaseActionPriority || []) {
    const batch = ensureBatch(item);
    batch.pendingItems += 1;
    for (const envKey of item.envKeys || []) {
      if (!batch.envKeys.includes(envKey)) {
        batch.envKeys.push(envKey);
      }
    }
    for (const command of commandHintsForItem(item)) {
      if (!batch.commands.includes(command)) {
        batch.commands.push(command);
      }
    }
    batch.items.push({
      id: item.id,
      check: item.check || item.id || "",
      reason: item.reason,
      detail: item.detail || item.reason || null,
      structured: item.structured === true,
      action: item.action,
      envKeys: item.envKeys || [],
      artifact: item.artifact || null,
    });
  }
  const batches = [...byBatch.values()].map((batch, index) => ({
    ...batch,
    id: releaseBatchId(batch),
    order: index + 1,
    envKeys: batch.envKeys.sort(),
    envCheckGroups: envCheckGroups(batch.envKeys),
    commands: batch.commands,
    expectedArtifacts: expectedArtifactsForBatch(batch),
    exitCriteria: exitCriteriaForBatch(batch),
  }));
  return batches.map((batch) => {
    const batchRank = releaseBatchPriorityRank(batch.priority);
    const dependsOn = batches
      .filter((candidate) => releaseBatchPriorityRank(candidate.priority) < batchRank)
      .map((candidate) => candidate.id);
    return {
      ...batch,
      dependsOn,
      canRunImmediately: dependsOn.length === 0,
    };
  });
}

function releaseActionBatchesArtifact(summary) {
  const batches = summary.releaseActionBatches || [];
  const byPriority = {};
  const bySource = {};
  const byOwner = {};
  for (const batch of batches) {
    byPriority[batch.priority] = (byPriority[batch.priority] || 0) + batch.pendingItems;
    bySource[batch.source] = (bySource[batch.source] || 0) + batch.pendingItems;
    byOwner[batch.owner] = (byOwner[batch.owner] || 0) + batch.pendingItems;
  }
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    batchCount: batches.length,
    totalPendingItems: batches.reduce((sum, batch) => sum + (batch.pendingItems || 0), 0),
    byPriority,
    bySource: Object.fromEntries(Object.entries(bySource).sort(([left], [right]) => left.localeCompare(right))),
    byOwner: Object.fromEntries(Object.entries(byOwner).sort(([left], [right]) => left.localeCompare(right))),
    batches: batches.map(releaseDisplayBatch),
  };
}

function releaseActionDependencyGraphArtifact(summary) {
  const batches = summary.releaseActionBatches || [];
  const nodes = batches.map((batch) => ({
    id: batch.id,
    order: batch.order,
    priority: batch.priority,
    source: batch.source,
    owner: batch.owner,
    pendingItems: batch.pendingItems || 0,
    canRunImmediately: batch.canRunImmediately === true,
    dependsOn: batch.dependsOn || [],
    envKeys: batch.envKeys || [],
    envCheckGroups: batch.envCheckGroups || envCheckGroups(batch.envKeys || []),
    commands: redactedDisplayCommands(batch.commands || []),
    expectedArtifacts: batch.expectedArtifacts || [],
    exitCriteria: batch.exitCriteria || [],
  }));
  const edges = nodes.flatMap((node) => (
    (node.dependsOn || []).map((dependency) => ({
      from: dependency,
      to: node.id,
    }))
  ));
  const byPriority = {};
  for (const node of nodes) {
    byPriority[node.priority] = byPriority[node.priority] || [];
    byPriority[node.priority].push(node.id);
  }
  const priorities = Object.keys(byPriority)
    .sort((left, right) => releaseBatchPriorityRank(left) - releaseBatchPriorityRank(right) || left.localeCompare(right));
  const compressedEdgeKeys = new Set();
  for (const edge of edges) {
    const fromNode = nodes.find((node) => node.id === edge.from);
    const toNode = nodes.find((node) => node.id === edge.to);
    if (!fromNode || !toNode || fromNode.priority === toNode.priority) {
      continue;
    }
    compressedEdgeKeys.add(`${fromNode.priority}->${toNode.priority}`);
  }
  const compressedEdges = [...compressedEdgeKeys]
    .map((key) => {
      const [fromPriority, toPriority] = key.split("->");
      return { fromPriority, toPriority };
    })
    .sort((left, right) => (
      releaseBatchPriorityRank(left.fromPriority) - releaseBatchPriorityRank(right.fromPriority)
        || releaseBatchPriorityRank(left.toPriority) - releaseBatchPriorityRank(right.toPriority)
        || left.fromPriority.localeCompare(right.fromPriority)
        || left.toPriority.localeCompare(right.toPriority)
    ));
  const maxDirectedEdges = nodes.length > 1 ? nodes.length * (nodes.length - 1) : 0;
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    batchCount: nodes.length,
    edgeCount: edges.length,
    graphDensity: maxDirectedEdges > 0 ? Number((edges.length / maxDirectedEdges).toFixed(4)) : 0,
    compressedEdgeCount: compressedEdges.length,
    readyBatchIds: nodes.filter((node) => node.canRunImmediately).map((node) => node.id),
    blockedBatchIds: nodes.filter((node) => !node.canRunImmediately).map((node) => node.id),
    byPriority,
    executionLevels: priorities.map((priority) => {
      const levelNodes = nodes.filter((node) => node.priority === priority);
      return {
        priority,
        batchIds: levelNodes.map((node) => node.id),
        ready: levelNodes.filter((node) => node.canRunImmediately).length,
        blocked: levelNodes.filter((node) => !node.canRunImmediately).length,
      };
    }),
    nodes,
    edges,
    compressedEdges,
  };
}

function mermaidNodeId(id) {
  return `b_${String(id || "unknown").replace(/[^A-Za-z0-9_]/g, "_")}`;
}

function mermaidPriorityNodeId(priority) {
  return `p_${String(priority || "unknown").replace(/[^A-Za-z0-9_]/g, "_")}`;
}

function mermaidNodeLabel(node) {
  return `${node.priority} ${node.source} / ${node.owner}`;
}

function releaseActionDependencyGraphMarkdown(summary) {
  const artifact = releaseActionDependencyGraphArtifact(summary);
  const lines = [
    "# DDD Release Action Dependency Graph",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    `Batch count: ${artifact.batchCount}`,
    `Edge count: ${artifact.edgeCount}`,
    `Graph density: ${artifact.graphDensity}`,
    `Compressed edge count: ${artifact.compressedEdgeCount}`,
    "",
    "## Execution Levels",
    "",
  ];
  if (artifact.executionLevels.length === 0) {
    lines.push("- None");
  } else {
    for (const level of artifact.executionLevels) {
      lines.push(`- ${level.priority}: ${level.batchIds.length} batches, ${level.ready} ready, ${level.blocked} blocked`);
    }
  }
  lines.push("", "## Compressed Graph", "", "```mermaid", "flowchart TD");
  if (artifact.executionLevels.length === 0) {
    lines.push("  empty[\"No release action batches\"]");
  } else {
    for (const level of artifact.executionLevels) {
      lines.push(`  ${mermaidPriorityNodeId(level.priority)}["${level.priority}: ${level.batchIds.length} batches / ${level.ready} ready / ${level.blocked} blocked"]`);
    }
    for (const edge of artifact.compressedEdges) {
      lines.push(`  ${mermaidPriorityNodeId(edge.fromPriority)} --> ${mermaidPriorityNodeId(edge.toPriority)}`);
    }
  }
  lines.push("```", "", "## Full Graph", "", "```mermaid", "flowchart TD");
  if (artifact.nodes.length === 0) {
    lines.push("  empty[\"No release action batches\"]");
  } else {
    for (const node of artifact.nodes) {
      lines.push(`  ${mermaidNodeId(node.id)}["${mermaidNodeLabel(node)}"]`);
    }
    for (const edge of artifact.edges) {
      lines.push(`  ${mermaidNodeId(edge.from)} --> ${mermaidNodeId(edge.to)}`);
    }
  }
  lines.push("```", "", "## Ready Batches", "");
  if (artifact.readyBatchIds.length === 0) {
    lines.push("- None");
  } else {
    for (const id of artifact.readyBatchIds) {
      const node = artifact.nodes.find((candidate) => candidate.id === id);
      lines.push(`- ${id}: ${mermaidNodeLabel(node)}`);
    }
  }
  lines.push("", "## Blocked Batches", "");
  if (artifact.blockedBatchIds.length === 0) {
    lines.push("- None");
  } else {
    for (const id of artifact.blockedBatchIds) {
      const node = artifact.nodes.find((candidate) => candidate.id === id);
      lines.push(`- ${id}: waits for ${(node?.dependsOn || []).join(", ") || "none"}`);
    }
  }
  return `${lines.join("\n")}\n`;
}

function releaseExecutionQueueArtifact(summary) {
  const batches = summary.releaseActionBatches || [];
  const graph = releaseActionDependencyGraphArtifact(summary);
  const batchById = new Map(batches.map((batch) => [batch.id, batch]));
  const safetySignals = releaseSafetySignals(summary);
  const readyBatches = graph.readyBatchIds
    .map((id) => batchById.get(id))
    .filter(Boolean)
    .map((batch) => ({
      id: batch.id,
      priority: batch.priority,
      source: batch.source,
      owner: batch.owner,
      pendingItems: batch.pendingItems || 0,
      commands: batch.commands || [],
      envKeys: batch.envKeys || [],
      envCheckGroups: envCheckGroups(batch.envKeys || []),
      expectedArtifacts: batch.expectedArtifacts || [],
      exitCriteria: batch.exitCriteria || [],
      itemIds: (batch.items || []).map((item) => item.id),
    }));
  const blockedBatches = graph.blockedBatchIds
    .map((id) => batchById.get(id))
    .filter(Boolean)
    .map((batch) => {
      const unmetDependencies = (batch.dependsOn || [])
        .map((dependencyId) => batchById.get(dependencyId))
        .filter(Boolean)
        .map((dependency) => ({
          id: dependency.id,
          priority: dependency.priority,
          source: dependency.source,
          owner: dependency.owner,
          expectedArtifacts: dependency.expectedArtifacts || [],
          exitCriteria: dependency.exitCriteria || [],
        }));
      return {
        id: batch.id,
        priority: batch.priority,
        source: batch.source,
        owner: batch.owner,
        pendingItems: batch.pendingItems || 0,
        commands: batch.commands || [],
        envKeys: batch.envKeys || [],
        envCheckGroups: envCheckGroups(batch.envKeys || []),
        expectedArtifacts: batch.expectedArtifacts || [],
        exitCriteria: batch.exitCriteria || [],
        itemIds: (batch.items || []).map((item) => item.id),
        unmetDependencyCount: unmetDependencies.length,
        unmetDependencies,
      };
    });
  const nextPriority = readyBatches[0]?.priority || null;
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    safetySignals,
    releaseEnvFileCutoverSafe: releaseEnvFileIsCutoverSafe(safetySignals.releaseEnvFile),
    batchCount: graph.batchCount,
    readyBatchCount: readyBatches.length,
    blockedBatchCount: blockedBatches.length,
    nextPriority,
    nextBatchIds: readyBatches.map((batch) => batch.id),
    readyBatches,
    blockedBatches,
  };
}

function releaseExecutionQueueMarkdown(summary) {
  const artifact = releaseExecutionQueueArtifact(summary);
  const lines = [
    "# DDD Release Execution Queue",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    `Ready batches: ${artifact.readyBatchCount}`,
    `Blocked batches: ${artifact.blockedBatchCount}`,
    `Next priority: ${artifact.nextPriority || "none"}`,
    "",
    "## Safety Signals",
    "",
  ];
  const releaseEnvFile = artifact.safetySignals?.releaseEnvFile || {};
  lines.push(`- releaseEnvFileCutoverSafe: ${artifact.releaseEnvFileCutoverSafe === true}`);
  lines.push(`- releaseEnvFile: ready=${releaseEnvFile.ready === true} status=${releaseEnvFile.status || "missing"} inputKind=${releaseEnvFile.inputKind || "missing"} envFilePresent=${releaseEnvFile.envFilePresent === true}`);
  lines.push(`  - securityChecked=${releaseEnvFile.securityChecked === true} permissionSafe=${releaseEnvFile.permissionSafe === true} mode=${releaseEnvFile.modeOctal || "missing"} requiredMode=${releaseEnvFile.requiredMode || "600"} reason=${releaseEnvFile.reason || "missing"} permissionCheckSkipped=${releaseEnvFile.permissionCheckSkipped === true}`);
  lines.push(`  - pendingActions=${(releaseEnvFile.pendingActionIds || []).join(", ") || "none"}`);
  lines.push(
    "",
    "## Ready Now",
    "",
  );
  if (artifact.readyBatches.length === 0) {
    lines.push("- None");
  } else {
    for (const batch of artifact.readyBatches) {
      lines.push(`### ${batch.id}`);
      lines.push("");
      lines.push(`- Scope: ${batch.priority} ${batch.source} -> ${batch.owner}`);
      lines.push(`- Pending items: ${batch.pendingItems}`);
      appendMarkdownEnvKeys(lines, batch.envKeys);
      appendMarkdownEnvCheckGroups(lines, batch.envCheckGroups);
      lines.push("- Commands:");
      for (const command of batch.commands) {
        lines.push(`  - \`${command}\``);
      }
      lines.push("- Expected artifacts:");
      for (const artifactPath of batch.expectedArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
      lines.push("- Exit criteria:");
      for (const criterion of batch.exitCriteria) {
        lines.push(`  - ${criterion}`);
      }
      lines.push("");
    }
  }
  lines.push("## Blocked Later", "");
  if (artifact.blockedBatches.length === 0) {
    lines.push("- None");
  } else {
    for (const batch of artifact.blockedBatches) {
      lines.push(`- ${batch.id}: waits for ${batch.unmetDependencies.map((dependency) => dependency.id).join(", ") || "none"}`);
      if ((batch.expectedArtifacts || []).length > 0) {
        lines.push("  - Expected artifacts:");
        for (const artifactPath of batch.expectedArtifacts) {
          lines.push(`    - \`${artifactPath}\``);
        }
      }
    }
  }
  return `${lines.join("\n")}\n`;
}

function releaseMissingEnv(summary) {
  const groups = [];
  const uniqueEnvKeys = new Set();
  const uniqueTemplateEnvKeys = new Set();
  const templateAliasMappings = new Map();
  for (const batch of summary.releaseActionBatches || []) {
    const envKeys = (batch.envKeys || []).filter(Boolean).sort();
    if (envKeys.length === 0) {
      continue;
    }
    const groupTemplateEnvKeys = new Set();
    const groupAliasMappings = new Map();
    for (const envKey of envKeys) {
      uniqueEnvKeys.add(envKey);
      const templateEnvKey = canonicalReleaseEnvTemplateKey(envKey);
      if (!releaseEnvTemplateControlKeys.has(templateEnvKey)) {
        uniqueTemplateEnvKeys.add(templateEnvKey);
        groupTemplateEnvKeys.add(templateEnvKey);
      }
      if (templateEnvKey !== envKey) {
        const mappingKey = `${envKey}->${templateEnvKey}`;
        templateAliasMappings.set(mappingKey, { alias: envKey, canonical: templateEnvKey });
        groupAliasMappings.set(mappingKey, { alias: envKey, canonical: templateEnvKey });
      }
    }
    groups.push({
      batchId: batch.id || null,
      priority: batch.priority,
      source: batch.source,
      owner: batch.owner,
      pendingItems: batch.pendingItems,
      dependsOn: batch.dependsOn || [],
      canRunImmediately: batch.canRunImmediately === true,
      envKeys,
      envCheckGroups: envCheckGroups(envKeys),
      templateEnvKeys: [...groupTemplateEnvKeys].sort(),
      aliasMappings: [...groupAliasMappings.values()].sort((left, right) => `${left.canonical}.${left.alias}`.localeCompare(`${right.canonical}.${right.alias}`)),
      commands: batch.commands || [],
      expectedArtifacts: batch.expectedArtifacts || [],
      exitCriteria: batch.exitCriteria || [],
      itemIds: (batch.items || []).map((item) => item.id),
    });
  }
  const sortedUniqueEnvKeys = [...uniqueEnvKeys].sort();
  const templateEnvKeys = [...uniqueTemplateEnvKeys].sort();
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: {
      strict: summary.gate?.strict === true,
      blockers: summary.gate?.blockers ?? 0,
      warnings: summary.gate?.warnings ?? 0,
    },
    uniqueEnvKeyCount: sortedUniqueEnvKeys.length,
    templateEnvKeyCount: templateEnvKeys.length,
    templateControlKeys: [...releaseEnvTemplateControlKeys].sort(),
    groupCount: groups.length,
    uniqueEnvKeys: sortedUniqueEnvKeys,
    templateEnvKeys,
    templateAliasMappings: [...templateAliasMappings.values()].sort((left, right) => `${left.canonical}.${left.alias}`.localeCompare(`${right.canonical}.${right.alias}`)),
    groups,
  };
}

function releaseEnvOwnerMatrixArtifact(summary) {
  const missingEnv = releaseMissingEnv(summary);
  const unresolvedTemplateKeys = new Set(summary.diagnostics?.ownerQueueEnvInitReceipt?.unresolvedTemplateKeys || []);
  const byOwner = new Map();
  for (const group of missingEnv.groups || []) {
    const owner = group.owner || "release-owner";
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        groupCount: 0,
        readyGroupCount: 0,
        blockedGroupCount: 0,
        templateEnvKeys: [],
        aliasMappings: [],
        batchIds: [],
        readyBatchIds: [],
        blockedBatchIds: [],
        commands: [],
        expectedArtifacts: [],
        exitCriteria: [],
        groups: [],
      });
    }
    const plan = byOwner.get(owner);
    plan.groupCount += 1;
    if (group.canRunImmediately === true) {
      plan.readyGroupCount += 1;
      if (group.batchId) {
        plan.readyBatchIds.push(group.batchId);
      }
    } else {
      plan.blockedGroupCount += 1;
      if (group.batchId) {
        plan.blockedBatchIds.push(group.batchId);
      }
    }
    if (group.batchId) {
      plan.batchIds.push(group.batchId);
    }
    plan.templateEnvKeys.push(...(group.templateEnvKeys || []));
    plan.aliasMappings.push(...(group.aliasMappings || []));
    plan.commands.push(...(group.commands || []));
    plan.expectedArtifacts.push(...(group.expectedArtifacts || []));
    plan.exitCriteria.push(...(group.exitCriteria || []));
    plan.groups.push(group);
  }
  const owners = [...byOwner.values()]
    .map((owner) => {
      const templateEnvKeys = sortedUniqueStrings(owner.templateEnvKeys);
      const unresolvedOwnerTemplateKeys = templateEnvKeys.filter((envKey) => unresolvedTemplateKeys.has(envKey));
      return {
        ...owner,
        templateEnvKeys,
        unresolvedTemplateKeyCount: unresolvedOwnerTemplateKeys.length,
        unresolvedTemplateKeys: unresolvedOwnerTemplateKeys,
        aliasMappings: [...new Map(owner.aliasMappings.map((mapping) => [`${mapping.alias}->${mapping.canonical}`, mapping])).values()]
          .sort((left, right) => `${left.canonical}.${left.alias}`.localeCompare(`${right.canonical}.${right.alias}`)),
        batchIds: sortedUniqueStrings(owner.batchIds),
        readyBatchIds: sortedUniqueStrings(owner.readyBatchIds),
        blockedBatchIds: sortedUniqueStrings(owner.blockedBatchIds),
        commands: redactedDisplayCommands(owner.commands),
        expectedArtifacts: sortedUniqueStrings(owner.expectedArtifacts),
        exitCriteria: sortedUniqueStrings(owner.exitCriteria),
      };
    })
    .sort((left, right) => (
      right.readyGroupCount - left.readyGroupCount
      || right.templateEnvKeys.length - left.templateEnvKeys.length
      || left.owner.localeCompare(right.owner)
    ));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: missingEnv.gate,
    ownerCount: owners.length,
    readyOwnerCount: owners.filter((owner) => owner.readyGroupCount > 0).length,
    templateEnvKeyCount: missingEnv.templateEnvKeyCount,
    uniqueUnresolvedTemplateKeyCount: [...unresolvedTemplateKeys]
      .filter((envKey) => missingEnv.templateEnvKeys.includes(envKey))
      .length,
    unresolvedOwnerAssignmentCount: owners.reduce((sum, owner) => sum + owner.unresolvedTemplateKeyCount, 0),
    groupCount: missingEnv.groupCount,
    owners,
  };
}

function releaseEnvOwnerMatrixMarkdown(summary) {
  const artifact = releaseEnvOwnerMatrixArtifact(summary);
  const lines = [
    "# DDD Release Env Owner Matrix",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Release gate blockers: ${artifact.gate?.blockers ?? 0}`,
    `Owners: ${artifact.ownerCount}`,
    `Template env keys: ${artifact.templateEnvKeyCount}`,
    `Unique unresolved template env keys: ${artifact.uniqueUnresolvedTemplateKeyCount}`,
    `Unresolved owner assignments: ${artifact.unresolvedOwnerAssignmentCount}`,
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Env keys: ${owner.templateEnvKeys.length}`);
    lines.push(`- Unresolved env keys: ${owner.unresolvedTemplateKeyCount}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push(`- Expected artifacts: ${owner.expectedArtifacts.join(", ") || "none"}`);
    if ((owner.unresolvedTemplateKeys || []).length > 0) {
      lines.push("- Unresolved template env keys:");
      for (const envKey of owner.unresolvedTemplateKeys) {
        lines.push(`  - \`${envKey}\``);
      }
    }
    lines.push("- Template env keys:");
    for (const envKey of owner.templateEnvKeys) {
      lines.push(`  - \`${envKey}\``);
    }
    if ((owner.aliasMappings || []).length > 0) {
      lines.push("- Alias mappings:");
      for (const mapping of owner.aliasMappings) {
        lines.push(`  - \`${mapping.alias}\` -> \`${mapping.canonical}\``);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvFillPriorityArtifact(summary) {
  const matrix = releaseEnvOwnerMatrixArtifact(summary);
  const envLint = summary.diagnostics?.releaseEnvLint || {};
  const presentKeys = new Set(envLint.keys || []);
  const unresolvedKeys = new Set(envLint.unresolvedTemplateKeys || []);
  const keyStatus = (envKey) => {
    if (unresolvedKeys.has(envKey)) {
      return "placeholder";
    }
    if (presentKeys.has(envKey)) {
      return "filled";
    }
    return "missing";
  };
  const owners = (matrix.owners || [])
    .filter((owner) => (owner.unresolvedTemplateKeyCount || 0) > 0)
    .map((owner) => {
      const unresolvedTemplateKeys = owner.unresolvedTemplateKeys || [];
      const fillStatusByKey = unresolvedTemplateKeys.map((envKey) => ({
        envKey,
        status: keyStatus(envKey),
      }));
      const filledTemplateKeys = fillStatusByKey
        .filter((item) => item.status === "filled")
        .map((item) => item.envKey);
      const placeholderTemplateKeys = fillStatusByKey
        .filter((item) => item.status === "placeholder")
        .map((item) => item.envKey);
      const missingTemplateKeys = fillStatusByKey
        .filter((item) => item.status === "missing")
        .map((item) => item.envKey);
      return {
        owner: owner.owner,
        fillOrder: 0,
        priority: owner.readyGroupCount > 0 ? "RUN_NOW" : "WAITING",
        readyGroupCount: owner.readyGroupCount,
        blockedGroupCount: owner.blockedGroupCount,
        unresolvedTemplateKeyCount: owner.unresolvedTemplateKeyCount,
        unresolvedTemplateKeys,
        filledTemplateKeyCount: filledTemplateKeys.length,
        placeholderTemplateKeyCount: placeholderTemplateKeys.length,
        missingTemplateKeyCount: missingTemplateKeys.length,
        filledTemplateKeys,
        placeholderTemplateKeys,
        missingTemplateKeys,
        fillStatusByKey,
        readyBatchIds: owner.readyBatchIds || [],
        blockedBatchIds: owner.blockedBatchIds || [],
        commands: owner.commands || [],
        exitCriteria: owner.exitCriteria || [],
      };
    })
    .sort((left, right) => (
      (left.priority === "RUN_NOW" ? 0 : 1) - (right.priority === "RUN_NOW" ? 0 : 1)
      || right.readyGroupCount - left.readyGroupCount
      || right.unresolvedTemplateKeyCount - left.unresolvedTemplateKeyCount
      || left.owner.localeCompare(right.owner)
    ))
    .map((owner, index) => ({
      ...owner,
      fillOrder: index + 1,
    }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    gate: matrix.gate,
    ownerCount: owners.length,
    runNowOwnerCount: owners.filter((owner) => owner.priority === "RUN_NOW").length,
    waitingOwnerCount: owners.filter((owner) => owner.priority !== "RUN_NOW").length,
    uniqueUnresolvedTemplateKeyCount: matrix.uniqueUnresolvedTemplateKeyCount || 0,
    unresolvedOwnerAssignmentCount: matrix.unresolvedOwnerAssignmentCount || 0,
    filledOwnerAssignmentCount: owners.reduce((total, owner) => total + owner.filledTemplateKeyCount, 0),
    placeholderOwnerAssignmentCount: owners.reduce((total, owner) => total + owner.placeholderTemplateKeyCount, 0),
    missingOwnerAssignmentCount: owners.reduce((total, owner) => total + owner.missingTemplateKeyCount, 0),
    owners,
  };
}

function releaseEnvFillPriorityMarkdown(summary) {
  const artifact = releaseEnvFillPriorityArtifact(summary);
  const lines = [
    "# DDD Release Env Fill Priority",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Release gate blockers: ${artifact.gate?.blockers ?? 0}`,
    `Owners with unresolved keys: ${artifact.ownerCount}`,
    `Run now owners: ${artifact.runNowOwnerCount}`,
    `Waiting owners: ${artifact.waitingOwnerCount}`,
    `Unique unresolved template env keys: ${artifact.uniqueUnresolvedTemplateKeyCount}`,
    `Unresolved owner assignments: ${artifact.unresolvedOwnerAssignmentCount}`,
    `Filled owner assignments: ${artifact.filledOwnerAssignmentCount}`,
    `Placeholder owner assignments: ${artifact.placeholderOwnerAssignmentCount}`,
    `Missing owner assignments: ${artifact.missingOwnerAssignmentCount}`,
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.fillOrder}. ${owner.owner}`, "");
    lines.push(`- Priority: ${owner.priority}`);
    lines.push(`- Unresolved env keys: ${owner.unresolvedTemplateKeyCount}`);
    lines.push(`- Fill status: filled=${owner.filledTemplateKeyCount}, placeholder=${owner.placeholderTemplateKeyCount}, missing=${owner.missingTemplateKeyCount}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push("- Fill keys:");
    for (const item of owner.fillStatusByKey || []) {
      lines.push(`  - \`${item.envKey}\` (${item.status})`);
    }
    if ((owner.commands || []).length > 0) {
      lines.push("- Rerun after fill:");
      for (const command of owner.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvCanonicalFillArtifact(summary) {
  const envLint = summary.diagnostics?.releaseEnvLint || {};
  const envFilePath = portablePath(envLint.envFile) || ".env.release.local";
  const unresolvedKeys = new Set(envLint.unresolvedTemplateKeys || []);
  const canonicalUnresolvedKeys = new Set(envLint.canonicalUnresolvedTemplateKeys || []);
  const matrix = releaseEnvOwnerMatrixArtifact(summary);
  const ownerByKey = new Map();
  for (const owner of matrix.owners || []) {
    for (const key of owner.templateEnvKeys || []) {
      if (!ownerByKey.has(key)) {
        ownerByKey.set(key, []);
      }
      ownerByKey.get(key).push(owner.owner);
    }
  }
  const items = [];
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements || []) {
      const aliases = [...new Set((Array.isArray(requirement.keys) ? requirement.keys : [requirement.key]).filter(Boolean))];
      if (aliases.length === 0) {
        continue;
      }
      const canonicalKey = aliases[0];
      const unresolvedAliases = aliases.filter((key) => unresolvedKeys.has(key));
      const owners = sortedUniqueStrings([
        group.owner,
        ...aliases.flatMap((key) => ownerByKey.get(key) || []),
      ].filter(Boolean));
      const valueClassification = canonicalEnvValueClassification(canonicalKey, requirement);
      items.push({
        fillOrder: 0,
        owner: group.owner,
        owners,
        group: group.name,
        requirement: requirement.name,
        canonicalKey,
        valueClass: valueClassification.valueClass,
        fillGuidance: valueClassification.fillGuidance,
        secret: valueClassification.secret,
        safeToPreFill: valueClassification.safeToPreFill,
        aliasCount: aliases.length,
        aliases,
        unresolvedAliasCount: unresolvedAliases.length,
        unresolvedAliases,
        required: requirement.required !== false,
        validation: {
          https: requirement.https === true,
          nonLocal: requirement.nonLocal === true,
          minLength: requirement.minLength || null,
          expectedValues: requirement.expectedValues || [],
          pattern: requirement.pattern || null,
          disallowValues: requirement.disallowValues || [],
        },
        aliasSyncCommand: `DDD_RELEASE_ENV_FILE=${envFilePath} node scripts/ddd-release-env-alias-sync.mjs`,
      });
    }
  }
  const sortedItems = items
    .sort((left, right) => (
      left.owner.localeCompare(right.owner)
      || left.group.localeCompare(right.group)
      || left.requirement.localeCompare(right.requirement)
      || left.canonicalKey.localeCompare(right.canonicalKey)
    ))
    .map((item, index) => ({ ...item, fillOrder: index + 1 }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    inputArtifacts: summary.inputArtifacts || {},
    envFile: envFilePath,
    canonicalFillItemCount: sortedItems.length,
    unresolvedAliasCount: sortedItems.reduce((sum, item) => sum + item.unresolvedAliasCount, 0),
    ownerCount: new Set(sortedItems.flatMap((item) => item.owners || [])).size,
    items: sortedItems,
  };
}

function canonicalEnvValueClassification(canonicalKey, requirement) {
  const key = String(canonicalKey || "");
  const name = String(requirement?.name || "");
  const expectedValues = Array.isArray(requirement?.expectedValues) ? requirement.expectedValues : [];
  const secret = /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i.test(key);
  if (secret) {
    return {
      valueClass: "secret",
      secret: true,
      safeToPreFill: false,
      fillGuidance: "Provide via approved secret manager or secure release channel; never commit.",
    };
  }
  if (requirement?.https === true || /(URL|URI|BASE_URL|ADDRESSES)$/i.test(key)) {
    return {
      valueClass: "url",
      secret: false,
      safeToPreFill: false,
      fillGuidance: requirement?.https === true
        ? "Use the production-equivalent HTTPS endpoint; localhost, example, and test domains are rejected."
        : "Use the production-equivalent endpoint or DSN; localhost and placeholders are rejected when nonLocal=true.",
    };
  }
  if (expectedValues.length > 0) {
    return {
      valueClass: expectedValues.every((value) => ["true", "false"].includes(String(value))) ? "toggle" : "enum",
      secret: false,
      safeToPreFill: true,
      fillGuidance: `Use one of: ${expectedValues.join(", ")}.`,
    };
  }
  if (/PORT$/i.test(key)) {
    return {
      valueClass: "port",
      secret: false,
      safeToPreFill: false,
      fillGuidance: "Use the production service TCP port.",
    };
  }
  if (/MODE|DISPATCHER|STREAM|ROOT/i.test(key) || /mode|dispatcher|stream|root/i.test(name)) {
    return {
      valueClass: "runtime-setting",
      secret: false,
      safeToPreFill: true,
      fillGuidance: "Use the production runtime setting agreed by the owning context.",
    };
  }
  return {
    valueClass: "identifier",
    secret: false,
    safeToPreFill: false,
    fillGuidance: "Use the production value from the owning context.",
  };
}

function canonicalEnvTemplateValue(item) {
  const expectedValues = Array.isArray(item?.validation?.expectedValues) ? item.validation.expectedValues : [];
  if (item?.required === false) {
    return "";
  }
  if (item?.safeToPreFill === true && item?.secret !== true && expectedValues.length === 1) {
    return String(expectedValues[0]);
  }
  return "__REQUIRED__";
}

function releaseEnvOwnerInputReason(item) {
  const validation = item?.validation || {};
  if (item?.blocker !== true) {
    return "not-blocking";
  }
  if (item?.secret === true) {
    return "secret-manager";
  }
  if (validation.https === true || validation.nonLocal === true || item?.valueClass === "url") {
    return "production-endpoint";
  }
  if (item?.valueClass === "port") {
    return "production-port";
  }
  if (item?.safeToPreFill === true) {
    return "safe-default-or-owner-choice";
  }
  if (validation.minLength) {
    return "owner-secure-value";
  }
  return "owner-production-value";
}

function releaseEnvBlockingSafeDefaultAvailable(item) {
  return item?.blocker === true
    && item?.secret !== true
    && item?.safeToPreFill === true
    && ["PLACEHOLDER", "MISSING"].includes(item?.status);
}

function releaseEnvCanonicalFillMarkdown(summary) {
  const artifact = releaseEnvCanonicalFillArtifact(summary);
  const lines = [
    "# DDD Release Env Canonical Fill",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Env file: ${artifact.envFile || "missing"}`,
    `Canonical fill items: ${artifact.canonicalFillItemCount}`,
    `Unresolved aliases covered: ${artifact.unresolvedAliasCount}`,
    `Owners: ${artifact.ownerCount}`,
    "",
    "Fill the canonical key once, then run alias sync to propagate equivalent keys.",
    "",
  ];
  for (const item of artifact.items || []) {
    lines.push(`## ${item.fillOrder}. ${item.canonicalKey}`, "");
    lines.push(`- Owner: ${item.owner}`);
    lines.push(`- Group: ${item.group}`);
    lines.push(`- Requirement: ${item.requirement}`);
    lines.push(`- Required: ${item.required}`);
    lines.push(`- Value class: ${item.valueClass}; secret=${item.secret}; safeToPreFill=${item.safeToPreFill}`);
    lines.push(`- Fill guidance: ${item.fillGuidance}`);
    lines.push(`- Validation: https=${item.validation.https}, nonLocal=${item.validation.nonLocal}, minLength=${item.validation.minLength || "none"}, expectedValues=${item.validation.expectedValues.join("|") || "none"}`);
    lines.push(`- Alias sync: \`${item.aliasSyncCommand}\``);
    lines.push("- Aliases:");
    for (const alias of item.aliases || []) {
      const marker = (item.unresolvedAliases || []).includes(alias) ? "placeholder" : "present";
      lines.push(`  - \`${alias}\` (${marker})`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerHandoffArtifact(summary) {
  const canonicalFill = releaseEnvCanonicalFillArtifact(summary);
  const finalQueue = releaseFinalOwnerQueueArtifact(summary);
  const queueByOwner = new Map((finalQueue.ownerQueues || []).map((owner) => [owner.owner, owner]));
  const explicitEnvFileLintCommand = `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-file-lint.mjs`;
  const explicitEnvSafeDefaultsCommand = `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} ${releaseEnvSafeDefaultsCommand}`;
  const explicitProvenanceDefaultsCommand = `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} ${releaseProvenanceDefaultsCommand}`;
  const ownerPostFillCommand = (command) => (
    command === "node scripts/ddd-release-env-file-lint.mjs" ? explicitEnvFileLintCommand : command
  );
  const fastPathCommands = [
    "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
    `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${canonicalFill.envFile || ".env.release.local"}`,
    explicitEnvSafeDefaultsCommand,
    explicitProvenanceDefaultsCommand,
    `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-alias-sync.mjs`,
    `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`,
    explicitEnvFileLintCommand,
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    finalReadinessSummaryCommand,
    finalGoNoGoEnforceCommand,
  ];
  const byOwner = new Map();
  for (const item of canonicalFill.items || []) {
    const owner = item.owner || "release-owner";
    if (!byOwner.has(owner)) {
      const queue = queueByOwner.get(owner) || {};
      byOwner.set(owner, {
        owner,
        queueOrder: queue.queueOrder || 0,
        queueStatus: queue.queueStatus || "WAITING",
        canExecute: queue.canExecute === true,
        readyBatchIds: queue.readyBatchIds || [],
        blockedBatchIds: queue.blockedBatchIds || [],
        firstCommand: queue.firstCommand || null,
        canonicalFillItems: [],
        aliasSyncCommand: item.aliasSyncCommand,
      });
    }
    byOwner.get(owner).canonicalFillItems.push(item);
  }
  const owners = [...byOwner.values()]
    .map((owner) => ({
      ...owner,
      canonicalFillItemCount: owner.canonicalFillItems.length,
      unresolvedAliasCount: owner.canonicalFillItems.reduce((sum, item) => sum + item.unresolvedAliasCount, 0),
      secretCanonicalKeyCount: owner.canonicalFillItems.filter((item) => item.secret === true).length,
      safeToPreFillCanonicalKeyCount: owner.canonicalFillItems.filter((item) => item.safeToPreFill === true).length,
      canonicalKeys: owner.canonicalFillItems.map((item) => item.canonicalKey),
      secretCanonicalKeys: owner.canonicalFillItems.filter((item) => item.secret === true).map((item) => item.canonicalKey),
      safeToPreFillCanonicalKeys: owner.canonicalFillItems.filter((item) => item.safeToPreFill === true).map((item) => item.canonicalKey),
      requiredCanonicalKeys: owner.canonicalFillItems.filter((item) => item.required).map((item) => item.canonicalKey),
      nextCommand: "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      postFillCommands: orderedUniqueStrings([
        "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
        `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env ${canonicalFill.envFile || ".env.release.local"}`,
        explicitEnvSafeDefaultsCommand,
        explicitProvenanceDefaultsCommand,
        owner.aliasSyncCommand,
        `DDD_RELEASE_ENV_FILE=${canonicalFill.envFile || ".env.release.local"} node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env`,
        explicitEnvFileLintCommand,
        "node scripts/ddd-release-config-evidence.mjs",
        "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
        ...(owner.firstCommand ? [ownerPostFillCommand(owner.firstCommand)] : []),
        finalReadinessSummaryCommand,
        finalGoNoGoEnforceCommand,
      ]),
    }))
    .sort((left, right) => (
      (left.canExecute === true ? 0 : 1) - (right.canExecute === true ? 0 : 1)
      || (left.queueOrder || 9999) - (right.queueOrder || 9999)
      || right.canonicalFillItemCount - left.canonicalFillItemCount
      || left.owner.localeCompare(right.owner)
    ));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    envFile: canonicalFill.envFile,
    ownerCount: owners.length,
    canonicalFillItemCount: canonicalFill.canonicalFillItemCount,
    unresolvedAliasCount: canonicalFill.unresolvedAliasCount,
    fastPath: {
      objective: "Fill canonical release env keys once, sync aliases, then rerun strict env and final go/no-go gates.",
      blockedUntil: "All blocking release env placeholders are replaced in a permission-safe release env file.",
      commands: fastPathCommands,
    },
    owners,
  };
}

function releaseEnvOwnerHandoffMarkdown(summary) {
  const artifact = releaseEnvOwnerHandoffArtifact(summary);
  const lines = [
    "# DDD Release Env Owner Handoff",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Env file: ${artifact.envFile || "missing"}`,
    `Owners: ${artifact.ownerCount}`,
    `Canonical fill items: ${artifact.canonicalFillItemCount}`,
    `Unresolved aliases covered: ${artifact.unresolvedAliasCount}`,
    "",
    "## Fast Path",
    "",
    `- Objective: ${artifact.fastPath.objective}`,
    `- Blocked until: ${artifact.fastPath.blockedUntil}`,
    "- Commands:",
  ];
  for (const command of artifact.fastPath.commands || []) {
    lines.push(`  - \`${command}\``);
  }
  lines.push(
    "",
    "## Owners",
    "",
  );
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Queue: ${owner.queueStatus}; canExecute=${owner.canExecute}`);
    lines.push(`- Next command: \`${owner.nextCommand}\``);
    lines.push(`- Canonical fill items: ${owner.canonicalFillItemCount}`);
    lines.push(`- Secret canonical keys: ${owner.secretCanonicalKeyCount}`);
    lines.push(`- Safe-to-prefill canonical keys: ${owner.safeToPreFillCanonicalKeyCount}`);
    lines.push(`- Unresolved aliases covered: ${owner.unresolvedAliasCount}`);
    lines.push(`- Ready batches: ${owner.readyBatchIds.join(", ") || "none"}`);
    lines.push(`- Blocked batches: ${owner.blockedBatchIds.join(", ") || "none"}`);
    lines.push("- Fill canonical keys:");
    for (const item of owner.canonicalFillItems || []) {
      lines.push(`  - \`${item.canonicalKey}\` (${item.group}.${item.requirement}; class=${item.valueClass}; secret=${item.secret}; aliases=${item.aliases.join("|")})`);
    }
    lines.push("- Run after fill:");
    for (const command of owner.postFillCommands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvCanonicalFillTemplate(summary) {
  const handoff = releaseEnvOwnerHandoffArtifact(summary);
  const lines = [
    "# Lumira DDD canonical release environment fill template.",
    "# Fill each canonical key once, then merge into DDD_RELEASE_ENV_FILE and run alias sync.",
    "# Do not commit populated secrets.",
    `# Generated at: ${handoff.generatedAt}`,
    `# Status: ${handoff.status}`,
    `# Env file: ${handoff.envFile || "missing"}`,
    "",
  ];
  const emitted = new Set();
  for (const owner of handoff.owners || []) {
    lines.push(`# Owner: ${owner.owner}`);
    lines.push(`# Queue status: ${owner.queueStatus}; canExecute=${owner.canExecute}`);
    lines.push(`# Canonical fill items: ${owner.canonicalFillItemCount}`);
    for (const item of owner.canonicalFillItems || []) {
      if (emitted.has(item.canonicalKey)) {
        continue;
      }
      emitted.add(item.canonicalKey);
      lines.push(`# ${item.group}.${item.requirement}`);
      lines.push(`# Aliases: ${item.aliases.join(", ")}`);
      lines.push(`# Value class: ${item.valueClass}; secret=${item.secret}; safeToPreFill=${item.safeToPreFill}`);
      lines.push(`# Fill guidance: ${item.fillGuidance}`);
      lines.push(`# Validation: https=${item.validation.https}, nonLocal=${item.validation.nonLocal}, minLength=${item.validation.minLength || "none"}, expectedValues=${item.validation.expectedValues.join("|") || "none"}`);
      lines.push(`${item.canonicalKey}=${canonicalEnvTemplateValue(item)}`);
    }
    lines.push("");
  }
  lines.push("# Usage after filling real values:");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env .env.release.local");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-safe-defaults.mjs");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-provenance-defaults.mjs");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-alias-sync.mjs");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs");
  lines.push("# DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-readiness-summary.mjs");
  return `${lines.join("\n")}\n`;
}

function safeFileName(value) {
  return String(value || "unknown")
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "") || "unknown";
}

function releaseEnvOwnerTemplateContent(owner, generatedAt) {
  const lines = [
    "# Lumira DDD owner-scoped canonical release environment template.",
    "# Fill only the keys owned by this section, then merge the combined canonical template into DDD_RELEASE_ENV_FILE.",
    "# Do not commit populated secrets.",
    `# Generated at: ${generatedAt}`,
    `# Owner: ${owner.owner}`,
    `# Queue status: ${owner.queueStatus}; canExecute=${owner.canExecute}`,
    `# Canonical fill items: ${owner.canonicalFillItemCount}`,
    `# Secret canonical keys: ${owner.secretCanonicalKeyCount}`,
    `# Safe-to-prefill canonical keys: ${owner.safeToPreFillCanonicalKeyCount}`,
    "",
  ];
  for (const item of owner.canonicalFillItems || []) {
    lines.push(`# ${item.group}.${item.requirement}`);
    lines.push(`# Aliases: ${item.aliases.join(", ")}`);
    lines.push(`# Value class: ${item.valueClass}; secret=${item.secret}; safeToPreFill=${item.safeToPreFill}`);
    lines.push(`# Fill guidance: ${item.fillGuidance}`);
    lines.push(`# Validation: https=${item.validation.https}, nonLocal=${item.validation.nonLocal}, minLength=${item.validation.minLength || "none"}, expectedValues=${item.validation.expectedValues.join("|") || "none"}`);
    lines.push(`${item.canonicalKey}=${canonicalEnvTemplateValue(item)}`);
  }
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerTemplatesArtifact(summary) {
  const handoff = releaseEnvOwnerHandoffArtifact(summary);
  const owners = (handoff.owners || []).map((owner) => {
    const fileName = `${String(owner.queueOrder || 0).padStart(2, "0")}-${safeFileName(owner.owner)}.env`;
    return {
      owner: owner.owner,
      queueOrder: owner.queueOrder,
      queueStatus: owner.queueStatus,
      canExecute: owner.canExecute,
      templatePath: path.posix.join("artifacts", "ddd", "release", "release-env-owner-templates", fileName),
      fileName,
      canonicalFillItemCount: owner.canonicalFillItemCount,
      secretCanonicalKeyCount: owner.secretCanonicalKeyCount,
      safeToPreFillCanonicalKeyCount: owner.safeToPreFillCanonicalKeyCount,
      canonicalKeys: owner.canonicalKeys || [],
      secretCanonicalKeys: owner.secretCanonicalKeys || [],
      safeToPreFillCanonicalKeys: owner.safeToPreFillCanonicalKeys || [],
      postFillCommands: owner.postFillCommands || [],
      content: releaseEnvOwnerTemplateContent(owner, handoff.generatedAt),
    };
  });
  return {
    generatedAt: handoff.generatedAt,
    status: handoff.status,
    envFile: handoff.envFile,
    ownerCount: owners.length,
    canonicalFillItemCount: handoff.canonicalFillItemCount,
    secretCanonicalKeyCount: owners.reduce((sum, owner) => sum + owner.secretCanonicalKeyCount, 0),
    safeToPreFillCanonicalKeyCount: owners.reduce((sum, owner) => sum + owner.safeToPreFillCanonicalKeyCount, 0),
    templateDir: path.posix.join("artifacts", "ddd", "release", "release-env-owner-templates"),
    owners,
  };
}

function releaseEnvOwnerTemplatesMarkdown(summary) {
  const artifact = releaseEnvOwnerTemplatesArtifact(summary);
  const lines = [
    "# DDD Release Env Owner Templates",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Env file: ${artifact.envFile || "missing"}`,
    `Template dir: ${artifact.templateDir}`,
    `Owners: ${artifact.ownerCount}`,
    `Canonical fill items: ${artifact.canonicalFillItemCount}`,
    `Secret canonical keys: ${artifact.secretCanonicalKeyCount}`,
    `Safe-to-prefill canonical keys: ${artifact.safeToPreFillCanonicalKeyCount}`,
    "",
    "Each owner template is intentionally scoped to one owner so release values can be collected in parallel without sharing unrelated secrets.",
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`## ${owner.owner}`, "");
    lines.push(`- Template: \`${owner.templatePath}\``);
    lines.push(`- Queue: ${owner.queueStatus}; canExecute=${owner.canExecute}`);
    lines.push(`- Canonical fill items: ${owner.canonicalFillItemCount}`);
    lines.push(`- Secret canonical keys: ${owner.secretCanonicalKeyCount}`);
    lines.push(`- Safe-to-prefill canonical keys: ${owner.safeToPreFillCanonicalKeyCount}`);
    lines.push(`- Keys: ${owner.canonicalKeys.join(", ") || "none"}`);
    lines.push("- Run after merging owner values into the canonical fill template:");
    for (const command of owner.postFillCommands || []) {
      lines.push(`  - \`${command}\``);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseMissingEnvTemplate(summary) {
  const artifact = releaseMissingEnv(summary);
  const lines = [
    "# Lumira DDD missing release evidence environment template.",
    "# Generated from current readiness blockers; replace every __REQUIRED__ placeholder that you keep in a secure env file.",
    "# Some keys are accepted aliases for the same check; set the canonical key(s) you use and delete unused alias placeholder lines.",
    "# Do not commit populated secrets. Point DDD_RELEASE_ENV_FILE at the completed file.",
    `# Generated at: ${summary.generatedAt}`,
    `# Status: ${summary.status}`,
    `# Release gate mode: ${releaseGateMode(summary)}`,
    `# Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    "",
  ];
  const emitted = new Set();
  for (const group of artifact.groups) {
    const freshKeys = (group.templateEnvKeys || []).filter((envKey) => !releaseEnvTemplateControlKeys.has(envKey) && !emitted.has(envKey));
    if (freshKeys.length === 0) {
      continue;
    }
    lines.push(`# ${group.priority} ${group.source} -> ${group.owner} (${group.pendingItems} pending items)`);
    if (group.batchId) {
      lines.push(`# Batch id: ${group.batchId}`);
    }
    if ((group.dependsOn || []).length > 0) {
      lines.push(`# Depends on: ${group.dependsOn.join(", ")}`);
    } else {
      lines.push("# Depends on: none");
    }
    lines.push(`# Can run immediately: ${group.canRunImmediately === true}`);
    if ((group.itemIds || []).length > 0) {
      lines.push(`# Covers: ${group.itemIds.join(", ")}`);
    }
    if ((group.commands || []).length > 0) {
      lines.push(`# Rerun: ${group.commands.join("; ")}`);
    }
    if ((group.expectedArtifacts || []).length > 0) {
      lines.push(`# Expected artifacts: ${group.expectedArtifacts.join("; ")}`);
    }
    if ((group.exitCriteria || []).length > 0) {
      lines.push("# Exit criteria:");
      for (const criterion of group.exitCriteria) {
        lines.push(`# - ${criterion}`);
      }
    }
    const aliasMappings = (group.aliasMappings || []).filter((mapping) => freshKeys.includes(mapping.canonical));
    if (aliasMappings.length > 0) {
      lines.push("# Accepted aliases omitted from template:");
      for (const mapping of aliasMappings) {
        lines.push(`# - ${mapping.alias}->${mapping.canonical}`);
      }
    }
    for (const envKey of freshKeys) {
      lines.push(`${envKey}=__REQUIRED__`);
      emitted.add(envKey);
    }
    lines.push("");
  }
  lines.push("# After completing this file:");
  lines.push("# export DDD_RELEASE_ENV_FILE=/secure/path/to/.env.release");
  lines.push("# node scripts/ddd-release-env-file-lint.mjs");
  lines.push("# node scripts/ddd-release-config-evidence.mjs");
  lines.push("# DDD_RELEASE_CONTINUE_ON_ERROR=1 bash artifacts/ddd/release/release-execution-commands.sh # diagnostic only; final exit remains non-zero on failures");
  lines.push("# DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=1 DDD_RELEASE_NEXT_ACTION_EXECUTE=1 bash artifacts/ddd/release/release-next-action-commands.sh # diagnostic only; final exit remains non-zero on failures");
  lines.push("# DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1 DDD_RELEASE_CLOSURE_EXECUTE=1 bash artifacts/ddd/release/release-blocker-closure-commands.sh # diagnostic only; final exit remains non-zero on failures");
  lines.push("# DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1 DDD_FINAL_OWNER_QUEUE_EXECUTE=1 bash artifacts/ddd/release/release-final-owner-queue-commands.sh # diagnostic only; final exit remains non-zero on failures");
  return `${lines.join("\n")}\n`;
}

function releaseGateMode(summary) {
  return summary.gate?.strict === true ? "strict" : "advisory";
}

function releaseMissingEnvTemplateKeys(template) {
  return [...String(template).matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);
}

function csvCell(value) {
  const text = Array.isArray(value) ? value.join(";") : String(value ?? "");
  if (!/[",\n\r;]/.test(text)) {
    return text;
  }
  return `"${text.replace(/"/g, '""')}"`;
}

function fileSha256(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function releaseArtifactIntegrityArtifact(summary, outputs) {
  const entries = Object.entries(outputs)
    .map(([name, filePath]) => {
      const stat = fs.statSync(filePath);
      const relativePath = path.relative(repoRoot, filePath).replaceAll("\\", "/");
      const executable = process.platform === "win32" && relativePath.endsWith(".sh")
        ? true
        : (stat.mode & 0o111) !== 0;
      return {
        name,
        path: relativePath,
        bytes: stat.size,
        executable,
        sha256: fileSha256(filePath),
      };
    })
    .sort((a, b) => a.path.localeCompare(b.path));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    redacted: true,
    algorithm: "sha256",
    selfExcluded: true,
    artifactCount: entries.length,
    totalBytes: entries.reduce((sum, entry) => sum + entry.bytes, 0),
    entries,
  };
}

function releaseArtifactIntegrityMarkdown(summary, outputs) {
  const artifact = releaseArtifactIntegrityArtifact(summary, outputs);
  const lines = [
    "# DDD Release Artifact Integrity",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Algorithm: ${artifact.algorithm}`,
    `Self excluded: ${artifact.selfExcluded}`,
    `Artifact count: ${artifact.artifactCount}`,
    `Total bytes: ${artifact.totalBytes}`,
    "",
    "| Artifact | Bytes | Executable | SHA-256 |",
    "|---|---:|---:|---|",
  ];
  for (const entry of artifact.entries) {
    lines.push(`| ${entry.path} | ${entry.bytes} | ${entry.executable} | \`${entry.sha256}\` |`);
  }
  return `${lines.join("\n")}\n`;
}

function releaseArtifactIntegrityGate(summary) {
  const packetPath = path.relative(repoRoot, releaseArtifactIntegrityOutput).replaceAll("\\", "/");
  return `${[
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release artifact integrity gate."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine("Exit code 12 means the integrity packet is invalid or at least one artifact hash no longer matches."),
    ...releaseRepoRootPreambleLines(),
    "",
    `DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET="\${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET:-${packetPath}}"`,
    "DDD_NODE_BIN=\"${DDD_NODE_BIN:-node}\"",
    "if [[ ! -f \"${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}\" ]]; then",
    "  echo \"Release artifact integrity packet does not exist: ${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}\" >&2",
    "  exit 12",
    "fi",
    "\"${DDD_NODE_BIN}\" --input-type=module - \"${DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET}\" <<'NODE'",
    "import crypto from 'node:crypto';",
    "import fs from 'node:fs';",
    "const packetPath = process.argv[2];",
    "const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));",
    "const failures = [];",
    "const allowedStatuses = new Set(['ADVISORY', 'PASS', 'FAIL', 'NOT_READY']);",
    "if (packet.algorithm !== 'sha256') failures.push('algorithm');",
    "if (!packet.generatedAt || Number.isNaN(Date.parse(packet.generatedAt))) failures.push('generatedAt');",
    "if (!allowedStatuses.has(packet.status)) failures.push('status');",
    "if (packet.redacted !== true) failures.push('redacted');",
    "if (packet.selfExcluded !== true) failures.push('selfExcluded');",
    "if (!Array.isArray(packet.entries)) failures.push('entries');",
    "const entries = Array.isArray(packet.entries) ? packet.entries : [];",
    "if (entries.length === 0) failures.push('entries-empty');",
    "const seenNames = new Set();",
    "const seenPaths = new Set();",
    "for (const entry of entries) {",
    "  if (!entry || typeof entry.path !== 'string' || typeof entry.sha256 !== 'string') {",
    "    failures.push(`invalid-entry:${entry?.name || 'unknown'}`);",
    "    continue;",
    "  }",
    "  if (!entry.name) failures.push(`name:${entry.path}`);",
    "  if (seenNames.has(entry.name)) failures.push(`duplicate-name:${entry.name}`);",
    "  if (seenPaths.has(entry.path)) failures.push(`duplicate-path:${entry.path}`);",
    "  seenNames.add(entry.name);",
    "  seenPaths.add(entry.path);",
    "  if (!/^[a-f0-9]{64}$/.test(entry.sha256)) failures.push(`sha256-format:${entry.path}`);",
    "  if (!Number.isInteger(entry.bytes) || entry.bytes < 0) failures.push(`bytes:${entry.path}`);",
    "  if (typeof entry.executable !== 'boolean') failures.push(`executable:${entry.path}`);",
    "  if (!fs.existsSync(entry.path)) {",
    "    failures.push(`missing:${entry.path}`);",
    "    continue;",
    "  }",
    "  const stat = fs.statSync(entry.path);",
    "  const executable = process.platform === 'win32' && entry.path.endsWith('.sh') ? true : (stat.mode & 0o111) !== 0;",
    "  const sha256 = crypto.createHash('sha256').update(fs.readFileSync(entry.path)).digest('hex');",
    "  if (stat.size !== entry.bytes) failures.push(`size:${entry.path}`);",
    "  if (executable !== entry.executable) failures.push(`mode:${entry.path}`);",
    "  if (sha256 !== entry.sha256) failures.push(`sha256:${entry.path}`);",
    "}",
    "if (packet.artifactCount !== entries.length) failures.push('artifactCount');",
    "const totalBytes = entries.reduce((sum, entry) => sum + Number(entry.bytes || 0), 0);",
    "if (packet.totalBytes !== totalBytes) failures.push('totalBytes');",
    "if (failures.length > 0) {",
    "  console.error(`[ddd-release-artifact-integrity][invalid] ${failures.join(',')}`);",
    "  process.exit(12);",
    "}",
    "console.log(`[ddd-release-artifact-integrity] ok artifacts=${entries.length} totalBytes=${packet.totalBytes}`);",
    "NODE",
  ].join("\n")}\n`;
}

function ownerActionRollupCsv(summary) {
  const artifact = ownerActionRollupArtifact(summary);
  const rows = [["owner", "pendingItems", "source", "id", "reason", "envKeys", "action"]];
  for (const [owner, plan] of Object.entries(artifact.owners || {})) {
    for (const item of plan.items || []) {
      rows.push([
        owner,
        plan.pendingItems ?? 0,
        item.source || "",
        item.id || "",
        item.reason || "",
        item.envKeys || [],
        item.action || "",
      ]);
    }
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function sourceActionRollupCsv(summary) {
  const artifact = sourceActionRollupArtifact(summary);
  const rows = [["source", "pendingItems", "owner", "id", "reason", "envKeys", "action"]];
  for (const [source, plan] of Object.entries(artifact.sources || {})) {
    for (const item of plan.items || []) {
      rows.push([
        source,
        plan.pendingItems ?? 0,
        item.owner || "",
        item.id || "",
        item.reason || "",
        item.envKeys || [],
        item.action || "",
      ]);
    }
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseActionPriorityCsv(summary) {
  const artifact = releaseActionPriorityArtifact(summary);
  const rows = [["priority", "source", "owner", "id", "check", "reason", "detail", "structured", "envKeys", "action"]];
  for (const item of artifact.items || []) {
    rows.push([
      item.priority,
      item.source,
      item.owner,
      item.id,
      item.check || "",
      item.reason || "",
      item.detail || "",
      item.structured === true,
      item.envKeys || [],
      item.action || "",
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseBlockerMapCsv(summary) {
  const artifact = releaseBlockerMapArtifact(summary);
  const rows = [[
    "owner",
    "blockerCount",
    "categories",
    "readyBatchIds",
    "blockedBatchIds",
    "commands",
    "expectedArtifacts",
    "sampleBlockers",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      owner.owner,
      owner.blockerCount,
      Object.entries(owner.categories || {}).map(([category, count]) => `${category}=${count}`).join(";"),
      owner.readyBatchIds,
      owner.blockedBatchIds,
      owner.commands,
      owner.expectedArtifacts,
      (owner.blockers || []).slice(0, 5).map((blocker) => `[${blocker.category}] ${blocker.check || "unknown"}: ${blocker.detail || blocker.blocker}`),
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseActionBatchesCsv(summary) {
  const artifact = releaseActionBatchesArtifact(summary);
  const rows = [[
    "order",
    "priority",
    "source",
    "owner",
    "id",
    "pendingItems",
    "canRunImmediately",
    "dependsOn",
    "commands",
    "envKeys",
    "envCheckGroups",
    "expectedArtifacts",
    "exitCriteria",
    "itemIds",
  ]];
  for (const batch of artifact.batches || []) {
    rows.push([
      batch.order,
      batch.priority,
      batch.source,
      batch.owner,
      batch.id,
      batch.pendingItems,
      batch.canRunImmediately === true,
      batch.dependsOn || [],
      batch.commands || [],
      batch.envKeys || [],
      (batch.envCheckGroups || []).map((group) => group.spec),
      batch.expectedArtifacts || [],
      batch.exitCriteria || [],
      (batch.items || []).map((item) => item.id),
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseCutoverChecklistCsv(summary) {
  const artifact = releaseFastTrackArtifact(summary);
  const rows = [[
    "recommendation",
    "noAutoWaivers",
    "id",
    "title",
    "required",
    "status",
    "pendingItems",
    "lanes",
    "readyBatchIds",
    "blockedBatchIds",
    "batchIds",
  ]];
  for (const item of artifact.cutoverChecklist || []) {
    rows.push([
      artifact.recommendation,
      artifact.noAutoWaivers === true,
      item.id,
      item.title,
      item.required === true,
      item.status,
      item.pendingItems,
      item.lanes || [],
      item.readyBatchIds || [],
      item.blockedBatchIds || [],
      item.batchIds || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseCutoverOwnerMatrixCsv(summary) {
  const artifact = releaseCutoverOwnerMatrixArtifact(summary);
  const rows = [[
    "recommendation",
    "noAutoWaivers",
    "owner",
    "blockedItems",
    "totalItems",
    "checklistId",
    "status",
    "required",
    "pendingItems",
    "lanes",
    "batchIds",
    "readyBatchIds",
    "blockedBatchIds",
    "commands",
    "expectedArtifacts",
    "envCheckGroups",
    "exitCriteria",
  ]];
  for (const owner of artifact.owners || []) {
    for (const item of owner.items || []) {
      rows.push([
        artifact.recommendation,
        artifact.noAutoWaivers === true,
        owner.owner,
        owner.blockedItems,
        owner.totalItems,
        item.checklistId,
        item.status,
        item.required === true,
        item.pendingItems,
        item.lanes || [],
        item.batchIds || [],
        item.readyBatchIds || [],
        item.blockedBatchIds || [],
        item.commands || [],
        item.expectedArtifacts || [],
        item.envCheckGroups || [],
        item.exitCriteria || [],
      ]);
    }
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseSprintBoardCsv(summary) {
  const artifact = releaseSprintBoardArtifact(summary);
  const rows = [[
    "recommendation",
    "noAutoWaivers",
    "priority",
    "status",
    "owner",
    "source",
    "batchId",
    "pendingItems",
    "dependsOn",
    "unmetDependencies",
    "lanes",
    "cutoverChecklistIds",
    "commands",
    "expectedArtifacts",
    "envCheckGroups",
    "exitCriteria",
    "itemIds",
  ]];
  for (const card of artifact.batchCards || []) {
    rows.push([
      artifact.recommendation,
      artifact.noAutoWaivers === true,
      card.priority,
      card.status,
      card.owner,
      card.source,
      card.id,
      card.pendingItems,
      card.dependsOn || [],
      card.unmetDependencies || [],
      card.lanes || [],
      card.cutoverChecklistIds || [],
      card.commands || [],
      card.expectedArtifacts || [],
      card.envCheckGroups || [],
      card.exitCriteria || [],
      card.itemIds || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseCommandCatalogCsv(summary) {
  const artifact = releaseCommandCatalogArtifact(summary);
  const rows = [[
    "scope",
    "finalRecommendation",
    "cutoverAllowed",
    "stopReasonCount",
    "owner",
    "priority",
    "batchId",
    "readyBatchIds",
    "expectedArtifacts",
    "listCommand",
    "envCheckCommand",
    "dryRunCommand",
    "executeCommand",
  ]];
  if (artifact.nextPriorityCommands) {
    rows.push([
      "priority",
      artifact.finalDecision.finalRecommendation,
      artifact.finalDecision.cutoverAllowed,
      artifact.finalDecision.stopReasonCount,
      "",
      artifact.summary.nextPriority || "",
      "",
      "",
      "",
      artifact.nextPriorityCommands.list,
      artifact.nextPriorityCommands.envCheck,
      artifact.nextPriorityCommands.dryRun,
      artifact.nextPriorityCommands.execute,
    ]);
  }
  for (const owner of artifact.ownerCommands || []) {
    rows.push([
      "owner",
      artifact.finalDecision.finalRecommendation,
      artifact.finalDecision.cutoverAllowed,
      artifact.finalDecision.stopReasonCount,
      owner.owner,
      owner.priority || "",
      "",
      owner.readyBatchIds || [],
      owner.expectedArtifacts || [],
      owner.commands?.list || "",
      owner.commands?.envCheck || "",
      owner.commands?.dryRun || "",
      owner.commands?.execute || "",
    ]);
  }
  for (const batch of artifact.batchCommands || []) {
    rows.push([
      "batch",
      artifact.finalDecision.finalRecommendation,
      artifact.finalDecision.cutoverAllowed,
      artifact.finalDecision.stopReasonCount,
      batch.owner,
      batch.priority,
      batch.batchId,
      [batch.batchId],
      batch.expectedArtifacts || [],
      batch.commands?.list || "",
      batch.commands?.envCheck || "",
      batch.commands?.dryRun || "",
      batch.commands?.execute || "",
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseOwnerHandoffCsv(summary) {
  const artifact = releaseOwnerHandoffArtifact(summary);
  const rows = [[
    "finalRecommendation",
    "cutoverAllowed",
    "stopReasonCount",
    "owner",
    "status",
    "pendingItems",
    "priorities",
    "readyBatchIds",
    "blockedBatchIds",
    "blockedByBatchIds",
    "templateEnvKeys",
    "aliasMappings",
    "listCommand",
    "envCheckCommand",
    "dryRunCommand",
    "executeCommand",
    "expectedArtifacts",
    "exitCriteria",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      artifact.finalDecision.finalRecommendation,
      artifact.finalDecision.cutoverAllowed,
      artifact.finalDecision.stopReasonCount,
      owner.owner,
      owner.status,
      owner.pendingItems,
      owner.priorities || [],
      owner.readyBatchIds || [],
      owner.blockedBatchIds || [],
      owner.blockedByBatchIds || [],
      owner.templateEnvKeys || [],
      (owner.aliasMappings || []).map((mapping) => `${mapping.alias}->${mapping.canonical}`),
      owner.commandSet?.list || "",
      owner.commandSet?.envCheck || "",
      owner.commandSet?.dryRun || "",
      owner.commandSet?.execute || "",
      owner.expectedArtifacts || [],
      owner.exitCriteria || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseOwnerReceiptsCsv(summary) {
  const artifact = releaseOwnerReceiptsArtifact(summary);
  const rows = [[
    "owner",
    "status",
    "receiptStatus",
    "readyBatchIds",
    "blockedBatchIds",
    "expectedArtifactCount",
    "presentArtifactCount",
    "missingArtifactCount",
    "pendingActionCount",
    "collapsedActionCount",
    "presentArtifacts",
    "missingArtifacts",
    "pendingActionIds",
    "nextCheck",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      owner.owner,
      owner.status,
      owner.receiptStatus,
      owner.readyBatchIds || [],
      owner.blockedBatchIds || [],
      owner.expectedArtifactCount,
      owner.presentArtifactCount,
      owner.missingArtifactCount,
      owner.pendingActionCount,
      owner.collapsedActionCount,
      owner.presentArtifacts || [],
      owner.missingArtifacts || [],
      (owner.pendingActions || []).map((action) => `${action.source}:${action.id}`),
      owner.nextCheck,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseNextActionQueueCsv(summary) {
  const artifact = releaseNextActionQueueArtifact(summary);
  const rows = [[
    "order",
    "finalRecommendation",
    "cutoverAllowed",
    "stopReasonCount",
    "owner",
    "queueStatus",
    "receiptStatus",
    "strictGateBlockerCount",
    "readyBatchIds",
    "blockedBatchIds",
    "missingArtifacts",
    "pendingActionCount",
    "collapsedActionCount",
    "nextAction",
    "reason",
    "commandHint",
    "executableCommands",
    "envKeys",
  ]];
  for (const item of artifact.items || []) {
    rows.push([
      item.order,
      artifact.finalDecision.finalRecommendation,
      artifact.finalDecision.cutoverAllowed,
      artifact.finalDecision.stopReasonCount,
      item.owner,
      item.queueStatus,
      item.receiptStatus,
      item.strictGateBlockerCount,
      item.readyBatchIds || [],
      item.blockedBatchIds || [],
      item.missingArtifacts || [],
      item.pendingActionCount,
      item.collapsedActionCount,
      item.nextAction,
      item.reason,
      item.commandHint,
      item.executableCommands || [],
      item.envKeys || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvOwnerMatrixCsv(summary) {
  const artifact = releaseEnvOwnerMatrixArtifact(summary);
  const rows = [[
    "owner",
    "groupCount",
    "readyGroupCount",
    "blockedGroupCount",
    "templateEnvKeyCount",
    "unresolvedTemplateKeyCount",
    "unresolvedTemplateKeys",
    "templateEnvKeys",
    "aliasMappings",
    "batchIds",
    "readyBatchIds",
    "blockedBatchIds",
    "commands",
    "expectedArtifacts",
    "exitCriteria",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      owner.owner,
      owner.groupCount,
      owner.readyGroupCount,
      owner.blockedGroupCount,
      owner.templateEnvKeys.length,
      owner.unresolvedTemplateKeyCount,
      owner.unresolvedTemplateKeys || [],
      owner.templateEnvKeys || [],
      (owner.aliasMappings || []).map((mapping) => `${mapping.alias}->${mapping.canonical}`),
      owner.batchIds || [],
      owner.readyBatchIds || [],
      owner.blockedBatchIds || [],
      owner.commands || [],
      owner.expectedArtifacts || [],
      owner.exitCriteria || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvFillPriorityCsv(summary) {
  const artifact = releaseEnvFillPriorityArtifact(summary);
  const rows = [[
    "fillOrder",
    "owner",
    "priority",
    "readyGroupCount",
    "blockedGroupCount",
    "unresolvedTemplateKeyCount",
    "filledTemplateKeyCount",
    "placeholderTemplateKeyCount",
    "missingTemplateKeyCount",
    "unresolvedTemplateKeys",
    "filledTemplateKeys",
    "placeholderTemplateKeys",
    "missingTemplateKeys",
    "fillStatusByKey",
    "readyBatchIds",
    "blockedBatchIds",
    "commands",
    "exitCriteria",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      owner.fillOrder,
      owner.owner,
      owner.priority,
      owner.readyGroupCount,
      owner.blockedGroupCount,
      owner.unresolvedTemplateKeyCount,
      owner.filledTemplateKeyCount,
      owner.placeholderTemplateKeyCount,
      owner.missingTemplateKeyCount,
      owner.unresolvedTemplateKeys || [],
      owner.filledTemplateKeys || [],
      owner.placeholderTemplateKeys || [],
      owner.missingTemplateKeys || [],
      (owner.fillStatusByKey || []).map((item) => `${item.envKey}:${item.status}`),
      owner.readyBatchIds || [],
      owner.blockedBatchIds || [],
      owner.commands || [],
      owner.exitCriteria || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvCanonicalFillCsv(summary) {
  const artifact = releaseEnvCanonicalFillArtifact(summary);
  const rows = [[
    "fillOrder",
    "owner",
    "owners",
    "group",
    "requirement",
    "canonicalKey",
    "valueClass",
    "secret",
    "safeToPreFill",
    "fillGuidance",
    "aliasCount",
    "unresolvedAliasCount",
    "aliases",
    "unresolvedAliases",
    "required",
    "https",
    "nonLocal",
    "minLength",
    "expectedValues",
    "pattern",
    "disallowValues",
    "aliasSyncCommand",
  ]];
  for (const item of artifact.items || []) {
    rows.push([
      item.fillOrder,
      item.owner,
      item.owners || [],
      item.group,
      item.requirement,
      item.canonicalKey,
      item.valueClass,
      item.secret,
      item.safeToPreFill,
      item.fillGuidance,
      item.aliasCount,
      item.unresolvedAliasCount,
      item.aliases || [],
      item.unresolvedAliases || [],
      item.required,
      item.validation?.https === true,
      item.validation?.nonLocal === true,
      item.validation?.minLength || "",
      item.validation?.expectedValues || [],
      item.validation?.pattern || "",
      item.validation?.disallowValues || [],
      item.aliasSyncCommand,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvReadinessRedactedArtifact(summary) {
  const canonicalFill = releaseEnvCanonicalFillArtifact(summary);
  const envLint = summary.diagnostics?.releaseEnvLint || {};
  const presentKeys = new Set(envLint.keys || []);
  const unresolvedKeys = new Set(envLint.unresolvedTemplateKeys || []);
  const blockerKeys = new Set((envLint.canonicalReleaseConfigBlockerKeys || []).filter(Boolean));
  const items = (canonicalFill.items || []).map((item) => {
    const status = unresolvedKeys.has(item.canonicalKey)
      ? "PLACEHOLDER"
      : presentKeys.has(item.canonicalKey)
        ? "FILLED_REDACTED"
        : item.required === false
          ? "OPTIONAL_EMPTY"
          : "MISSING";
    const unresolvedRequired = item.required !== false && (status === "PLACEHOLDER" || status === "MISSING");
    const classified = {
      fillOrder: item.fillOrder,
      owner: item.owner,
      owners: item.owners || [],
      group: item.group,
      requirement: item.requirement,
      canonicalKey: item.canonicalKey,
      status,
      required: item.required,
      secret: item.secret,
      valueClass: item.valueClass,
      safeToPreFill: item.safeToPreFill,
      blocker: blockerKeys.has(item.canonicalKey) || unresolvedRequired,
      validation: item.validation,
      aliases: item.aliases || [],
      fillGuidance: item.fillGuidance,
    };
    const safeDefaultAvailable = releaseEnvBlockingSafeDefaultAvailable(classified);
    const requiresOwnerInput = classified.blocker === true && safeDefaultAvailable !== true;
    return {
      ...classified,
      safeDefaultAvailable,
      requiresOwnerInput,
      ownerInputReason: releaseEnvOwnerInputReason(classified),
    };
  });
  const ownerInputReasonCounts = items.reduce((counts, item) => {
    if (item.blocker !== true) {
      return counts;
    }
    counts[item.ownerInputReason] = (counts[item.ownerInputReason] || 0) + 1;
    return counts;
  }, {});
  const byOwner = [...items.reduce((map, item) => {
    const owner = item.owner || "release-owner";
    if (!map.has(owner)) {
      map.set(owner, {
        owner,
        total: 0,
        filled: 0,
        placeholder: 0,
        missing: 0,
        optionalEmpty: 0,
        blockers: 0,
        secretKeys: 0,
        safeDefaultAvailable: 0,
        requiresOwnerInput: 0,
      });
    }
    const entry = map.get(owner);
    entry.total += 1;
    if (item.status === "FILLED_REDACTED") entry.filled += 1;
    if (item.status === "PLACEHOLDER") entry.placeholder += 1;
    if (item.status === "MISSING") entry.missing += 1;
    if (item.status === "OPTIONAL_EMPTY") entry.optionalEmpty += 1;
    if (item.blocker) entry.blockers += 1;
    if (item.secret) entry.secretKeys += 1;
    if (item.safeDefaultAvailable) entry.safeDefaultAvailable += 1;
    if (item.requiresOwnerInput) entry.requiresOwnerInput += 1;
    return map;
  }, new Map()).values()].sort((left, right) => (
    right.blockers - left.blockers
    || right.placeholder - left.placeholder
    || right.missing - left.missing
    || left.owner.localeCompare(right.owner)
  ));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    redacted: true,
    valuePolicy: "No concrete environment values are emitted; only key names, ownership, validation metadata, and redacted fill status.",
    envFile: canonicalFill.envFile,
    summary: {
      totalCanonicalKeys: items.length,
      filledRedacted: items.filter((item) => item.status === "FILLED_REDACTED").length,
      placeholders: items.filter((item) => item.status === "PLACEHOLDER").length,
      missing: items.filter((item) => item.status === "MISSING").length,
      optionalEmpty: items.filter((item) => item.status === "OPTIONAL_EMPTY").length,
      blockers: items.filter((item) => item.blocker).length,
      secretKeys: items.filter((item) => item.secret).length,
      ownerCount: byOwner.length,
      blockingSafeDefaultAvailable: items.filter((item) => item.safeDefaultAvailable).length,
      blockingRequiresOwnerInput: items.filter((item) => item.requiresOwnerInput).length,
      safeDefaultsExhausted: items.filter((item) => item.safeDefaultAvailable).length === 0,
      ownerInputReasonCounts,
    },
    byOwner,
    items,
  };
}

function releaseEnvReadinessRedactedMarkdown(summary) {
  const artifact = releaseEnvReadinessRedactedArtifact(summary);
  const lines = [
    "# DDD Release Env Readiness Redacted",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Env file: ${artifact.envFile || "missing"}`,
    `Value policy: ${artifact.valuePolicy}`,
    `Canonical keys: ${artifact.summary.totalCanonicalKeys}`,
    `Filled redacted: ${artifact.summary.filledRedacted}`,
    `Placeholders: ${artifact.summary.placeholders}`,
    `Missing: ${artifact.summary.missing}`,
    `Optional empty: ${artifact.summary.optionalEmpty}`,
    `Blockers: ${artifact.summary.blockers}`,
    `Secret keys: ${artifact.summary.secretKeys}`,
    `Blocking safe defaults available: ${artifact.summary.blockingSafeDefaultAvailable}`,
    `Blocking values requiring owner input: ${artifact.summary.blockingRequiresOwnerInput}`,
    `Safe defaults exhausted: ${artifact.summary.safeDefaultsExhausted}`,
    "",
    "## Owners",
    "",
  ];
  for (const owner of artifact.byOwner || []) {
    lines.push(`- ${owner.owner}: total=${owner.total}, filled=${owner.filled}, placeholder=${owner.placeholder}, missing=${owner.missing}, optionalEmpty=${owner.optionalEmpty}, blockers=${owner.blockers}, secretKeys=${owner.secretKeys}, safeDefaultAvailable=${owner.safeDefaultAvailable}, requiresOwnerInput=${owner.requiresOwnerInput}`);
  }
  lines.push("", "## Blocking Keys", "");
  for (const item of (artifact.items || []).filter((entry) => entry.blocker)) {
    lines.push(`- \`${item.canonicalKey}\` owner=${item.owner} class=${item.valueClass} secret=${item.secret} status=${item.status} safeDefaultAvailable=${item.safeDefaultAvailable} requiresOwnerInput=${item.requiresOwnerInput} reason=${item.ownerInputReason}`);
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function releaseEnvReadinessRedactedCsv(summary) {
  const artifact = releaseEnvReadinessRedactedArtifact(summary);
  const rows = [[
    "fillOrder",
    "owner",
    "owners",
    "group",
    "requirement",
    "canonicalKey",
    "status",
    "required",
    "secret",
    "valueClass",
    "safeToPreFill",
    "safeDefaultAvailable",
    "requiresOwnerInput",
    "ownerInputReason",
    "blocker",
    "aliases",
    "https",
    "nonLocal",
    "minLength",
    "expectedValues",
    "pattern",
    "disallowValues",
    "fillGuidance",
  ]];
  for (const item of artifact.items || []) {
    rows.push([
      item.fillOrder,
      item.owner,
      item.owners || [],
      item.group,
      item.requirement,
      item.canonicalKey,
      item.status,
      item.required,
      item.secret,
      item.valueClass,
      item.safeToPreFill,
      item.safeDefaultAvailable,
      item.requiresOwnerInput,
      item.ownerInputReason,
      item.blocker,
      item.aliases || [],
      item.validation?.https,
      item.validation?.nonLocal,
      item.validation?.minLength,
      item.validation?.expectedValues || [],
      item.validation?.pattern,
      item.validation?.disallowValues || [],
      item.fillGuidance,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvReadinessGate(summary) {
  const packetPath = path.relative(repoRoot, releaseEnvReadinessRedactedOutput).replaceAll("\\", "/");
  const handoffPath = path.relative(repoRoot, releaseEnvOwnerHandoffRedactedMarkdownOutput).replaceAll("\\", "/");
  const handoffCsvPath = path.relative(repoRoot, releaseEnvOwnerHandoffRedactedCsvOutput).replaceAll("\\", "/");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release env readiness gate."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine("Default mode prints redacted env readiness. Set DDD_RELEASE_ENV_READINESS_ENFORCE=1 to fail while env blockers remain."),
    shellCommentLine("Exit codes: 21 means release env values are unresolved; 22 means the redacted readiness packet is invalid."),
    ...releaseRepoRootPreambleLines(),
    "",
    `DDD_RELEASE_ENV_READINESS_PACKET="\${DDD_RELEASE_ENV_READINESS_PACKET:-${packetPath}}"`,
    "DDD_RELEASE_ENV_READINESS_ENFORCE=\"${DDD_RELEASE_ENV_READINESS_ENFORCE:-}\"",
    "DDD_NODE_BIN=\"${DDD_NODE_BIN:-node}\"",
    "if [[ ! -f \"${DDD_RELEASE_ENV_READINESS_PACKET}\" ]]; then",
    "  echo \"Release env readiness packet does not exist: ${DDD_RELEASE_ENV_READINESS_PACKET}\" >&2",
    "  echo \"Run: node scripts/ddd-release-readiness-summary.mjs\" >&2",
    "  exit 2",
    "fi",
    "set +e",
    "\"${DDD_NODE_BIN}\" --input-type=module - \"${DDD_RELEASE_ENV_READINESS_PACKET}\" \"${DDD_RELEASE_ENV_READINESS_ENFORCE}\" <<'NODE'",
    "import fs from 'node:fs';",
    "const packetPath = process.argv[2];",
    "const enforceMode = process.argv[3];",
    "const packet = JSON.parse(fs.readFileSync(packetPath, 'utf8'));",
    "const schemaIssues = [];",
    "if (packet.redacted !== true) schemaIssues.push('redacted must be true');",
    "if (!packet.summary || typeof packet.summary !== 'object') schemaIssues.push('summary is missing');",
    "if (!Array.isArray(packet.items)) schemaIssues.push('items must be an array');",
    "if (!Array.isArray(packet.byOwner)) schemaIssues.push('byOwner must be an array');",
    "if (schemaIssues.length > 0) {",
    "  console.error(`[ddd-release-env-readiness][invalid-packet] ${schemaIssues.join('; ')}`);",
    "  process.exit(22);",
    "}",
    "const summary = packet.summary || {};",
    "const byOwner = Array.isArray(packet.byOwner) ? packet.byOwner : [];",
    "const items = Array.isArray(packet.items) ? packet.items : [];",
    "const counted = {",
    "  totalCanonicalKeys: items.length,",
    "  filledRedacted: items.filter((item) => item.status === 'FILLED_REDACTED').length,",
    "  placeholders: items.filter((item) => item.status === 'PLACEHOLDER').length,",
    "  missing: items.filter((item) => item.status === 'MISSING').length,",
    "  optionalEmpty: items.filter((item) => item.status === 'OPTIONAL_EMPTY').length,",
    "  blockers: items.filter((item) => item.blocker === true).length,",
    "  secretKeys: items.filter((item) => item.secret === true).length,",
    "  blockingSafeDefaultAvailable: items.filter((item) => item.safeDefaultAvailable === true).length,",
    "  blockingRequiresOwnerInput: items.filter((item) => item.requiresOwnerInput === true).length,",
    "  ownerCount: byOwner.length,",
    "};",
    "const countIssues = Object.entries(counted)",
    "  .filter(([key, value]) => Number(summary[key] ?? -1) !== value)",
    "  .map(([key, value]) => `${key} summary=${summary[key] ?? 'missing'} counted=${value}`);",
    "if (countIssues.length > 0) {",
    "  console.error(`[ddd-release-env-readiness][invalid-counts] ${countIssues.join('; ')}`);",
    "  process.exit(22);",
    "}",
    "const ownerCounts = new Map();",
    "for (const item of items) {",
    "  const ownerName = item.owner || 'release-owner';",
    "  if (!ownerCounts.has(ownerName)) ownerCounts.set(ownerName, { total: 0, filled: 0, placeholder: 0, missing: 0, optionalEmpty: 0, blockers: 0, secretKeys: 0, safeDefaultAvailable: 0, requiresOwnerInput: 0 });",
    "  const entry = ownerCounts.get(ownerName);",
    "  entry.total += 1;",
    "  if (item.status === 'FILLED_REDACTED') entry.filled += 1;",
    "  if (item.status === 'PLACEHOLDER') entry.placeholder += 1;",
    "  if (item.status === 'MISSING') entry.missing += 1;",
    "  if (item.status === 'OPTIONAL_EMPTY') entry.optionalEmpty += 1;",
    "  if (item.blocker === true) entry.blockers += 1;",
    "  if (item.secret === true) entry.secretKeys += 1;",
    "  if (item.safeDefaultAvailable === true) entry.safeDefaultAvailable += 1;",
    "  if (item.requiresOwnerInput === true) entry.requiresOwnerInput += 1;",
    "}",
    "const ownerIssues = [];",
    "for (const owner of byOwner) {",
    "  const countedOwner = ownerCounts.get(owner.owner);",
    "  if (!countedOwner) { ownerIssues.push(`${owner.owner}: no matching items`); continue; }",
    "  for (const key of ['total', 'filled', 'placeholder', 'missing', 'optionalEmpty', 'blockers', 'secretKeys', 'safeDefaultAvailable', 'requiresOwnerInput']) {",
    "    if (Number(owner[key] ?? -1) !== countedOwner[key]) ownerIssues.push(`${owner.owner}.${key} summary=${owner[key] ?? 'missing'} counted=${countedOwner[key]}`);",
    "  }",
    "}",
    "for (const ownerName of ownerCounts.keys()) if (!byOwner.some((owner) => owner.owner === ownerName)) ownerIssues.push(`${ownerName}: missing owner summary`);",
    "if (ownerIssues.length > 0) {",
    "  console.error(`[ddd-release-env-readiness][invalid-owner-counts] ${ownerIssues.join('; ')}`);",
    "  process.exit(22);",
    "}",
    "console.log(`[ddd-release-env-readiness] status=${packet.status || 'missing'} blockers=${summary.blockers ?? 'unknown'} placeholders=${summary.placeholders ?? 'unknown'} missing=${summary.missing ?? 'unknown'} optionalEmpty=${summary.optionalEmpty ?? 'unknown'} filledRedacted=${summary.filledRedacted ?? 'unknown'} secretKeys=${summary.secretKeys ?? 'unknown'} safeDefaultAvailable=${summary.blockingSafeDefaultAvailable ?? 'unknown'} requiresOwnerInput=${summary.blockingRequiresOwnerInput ?? 'unknown'} owners=${summary.ownerCount ?? byOwner.length}`);",
    "console.log('[ddd-release-env-readiness] exitCodes unresolved=21 invalidPacket=22');",
    `console.log('[ddd-release-env-readiness] handoff=${handoffPath} handoffCsv=${handoffCsvPath} dir=artifacts/ddd/release/release-env-owner-handoff-redacted');`,
    "if (byOwner.length > 0) {",
    "  console.log('[ddd-release-env-readiness] owners:');",
    "  for (const owner of byOwner) console.log(`- ${owner.owner}: blockers=${owner.blockers} placeholder=${owner.placeholder} missing=${owner.missing} optionalEmpty=${owner.optionalEmpty} secretKeys=${owner.secretKeys} safeDefaultAvailable=${owner.safeDefaultAvailable} requiresOwnerInput=${owner.requiresOwnerInput}`);",
    "}",
    "const unresolved = Number(summary.blockers || 0) + Number(summary.missing || 0) + Number(summary.placeholders || 0);",
    "if (enforceMode === '1' || enforceMode === 'true' || process.env.DDD_RELEASE_ENV_READINESS_ENFORCE === '1' || process.env.DDD_RELEASE_ENV_READINESS_ENFORCE === 'true') {",
    "  if (unresolved > 0) {",
    "    console.error(`[ddd-release-env-readiness][no-go] unresolved release env values remain: blockers=${summary.blockers ?? 0} placeholders=${summary.placeholders ?? 0} missing=${summary.missing ?? 0}`);",
    "    process.exit(21);",
    "  }",
    "}",
    "process.exit(0);",
    "NODE",
    "node_status=$?",
    "set -e",
    "exit \"${node_status}\"",
  ];
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerHandoffRedactedArtifact(summary) {
  const redacted = releaseEnvReadinessRedactedArtifact(summary);
  const validationCommands = [
    "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    finalReadinessSummaryCommand,
    finalGoNoGoEnforceCommand,
  ];
  const ownerMap = new Map();
  for (const item of redacted.items || []) {
    const owner = item.owner || "release-owner";
    if (!ownerMap.has(owner)) {
      ownerMap.set(owner, []);
    }
    ownerMap.get(owner).push(item);
  }
  const owners = [...ownerMap.entries()]
    .map(([owner, items], index) => ({
      owner,
      fileName: `${String(index + 1).padStart(2, "0")}-${owner}.md`,
      total: items.length,
      blockers: items.filter((item) => item.blocker).length,
      placeholders: items.filter((item) => item.status === "PLACEHOLDER").length,
      missing: items.filter((item) => item.status === "MISSING").length,
      optionalEmpty: items.filter((item) => item.status === "OPTIONAL_EMPTY").length,
      secretKeys: items.filter((item) => item.secret).length,
      safeDefaultAvailable: items.filter((item) => item.safeDefaultAvailable).length,
      requiresOwnerInput: items.filter((item) => item.requiresOwnerInput).length,
      ownerInputReasons: [...new Set(items.filter((item) => item.blocker).map((item) => item.ownerInputReason))].sort(),
      keys: items.map((item) => item.canonicalKey),
      items,
    }))
    .sort((left, right) => (
      right.blockers - left.blockers
      || right.placeholders - left.placeholders
      || left.owner.localeCompare(right.owner)
    ))
    .map((owner, index) => ({
      ...owner,
      fileName: `${String(index + 1).padStart(2, "0")}-${owner.owner}.md`,
      handoffPath: path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted", `${String(index + 1).padStart(2, "0")}-${owner.owner}.md`),
      nextCommand: "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
      postFillCommands: validationCommands,
    }));
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    redacted: true,
    valuePolicy: redacted.valuePolicy,
    ownerCount: owners.length,
    blockerOwnerCount: owners.filter((owner) => owner.blockers > 0).length,
    templateDir: path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted"),
    fastPath: {
      objective: "Fill owner-owned release env keys without exposing values, then rerun strict env and final go/no-go gates.",
      blockedUntil: "All blocking placeholder release env values are replaced in the permission-safe release env file.",
      commands: validationCommands,
    },
    validationCommands,
    owners,
  };
}

function releaseEnvOwnerHandoffRedactedOwnerMarkdown(owner) {
  const lines = [
    `# DDD Release Env Owner Handoff: ${owner.owner}`,
    "",
    "Concrete values are intentionally omitted from this artifact.",
    `Total keys: ${owner.total}`,
    `Blocking keys: ${owner.blockers}`,
    `Placeholders: ${owner.placeholders}`,
    `Missing: ${owner.missing}`,
    `Optional empty: ${owner.optionalEmpty}`,
    `Secret keys: ${owner.secretKeys}`,
    `Safe defaults available: ${owner.safeDefaultAvailable}`,
    `Requires owner input: ${owner.requiresOwnerInput}`,
    `Owner input reasons: ${(owner.ownerInputReasons || []).join(", ") || "none"}`,
    `Next command: \`${owner.nextCommand}\``,
    "",
    "## Keys",
    "",
  ];
  for (const item of owner.items || []) {
    const validation = item.validation || {};
    const expectedValues = (validation.expectedValues || []).join("|") || "none";
    const minLength = validation.minLength || "none";
    lines.push(`- \`${item.canonicalKey}\`: status=${item.status}; class=${item.valueClass}; secret=${item.secret}; required=${item.required}; blocker=${item.blocker}; safeDefaultAvailable=${item.safeDefaultAvailable}; requiresOwnerInput=${item.requiresOwnerInput}; reason=${item.ownerInputReason}; https=${validation.https === true}; nonLocal=${validation.nonLocal === true}; minLength=${minLength}; expectedValues=${expectedValues}`);
    lines.push(`  guidance: ${item.fillGuidance}`);
  }
  lines.push(
    "",
    "## After Filling",
    "",
  );
  for (const command of owner.postFillCommands || []) {
    lines.push(`- \`${command}\``);
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerHandoffRedactedMarkdown(summary) {
  const artifact = releaseEnvOwnerHandoffRedactedArtifact(summary);
  const lines = [
    "# DDD Release Env Owner Handoff Redacted",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Value policy: ${artifact.valuePolicy}`,
    `Owner count: ${artifact.ownerCount}`,
    `Owners with blockers: ${artifact.blockerOwnerCount}`,
    `Directory: ${artifact.templateDir}`,
    "",
    "## Fast Path",
    "",
    `- Objective: ${artifact.fastPath.objective}`,
    `- Blocked until: ${artifact.fastPath.blockedUntil}`,
    "- Commands:",
  ];
  for (const command of artifact.fastPath.commands || []) {
    lines.push(`  - \`${command}\``);
  }
  lines.push(
    "",
    "## Validation Commands",
    "",
  );
  for (const command of artifact.validationCommands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "## Owners", "");
  for (const owner of artifact.owners || []) {
    lines.push(`- ${owner.owner}: blockers=${owner.blockers}, placeholders=${owner.placeholders}, secretKeys=${owner.secretKeys}, file=${owner.handoffPath}`);
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerHandoffRedactedCsv(summary) {
  const artifact = releaseEnvOwnerHandoffRedactedArtifact(summary);
  const rows = [[
    "owner",
    "handoffPath",
    "ownerTotalKeys",
    "ownerBlockers",
    "ownerPlaceholders",
    "ownerMissing",
    "ownerOptionalEmpty",
    "ownerSecretKeys",
    "ownerSafeDefaultAvailable",
    "ownerRequiresOwnerInput",
    "ownerInputReasons",
    "nextCommand",
    "canonicalKey",
    "status",
    "required",
    "secret",
    "blocker",
    "valueClass",
    "safeToPreFill",
    "safeDefaultAvailable",
    "requiresOwnerInput",
    "ownerInputReason",
    "fillGuidance",
    "validationHttps",
    "validationNonLocal",
    "validationMinLength",
    "validationExpectedValues",
  ]];
  for (const owner of artifact.owners || []) {
    for (const item of owner.items || []) {
      const validation = item.validation || {};
      rows.push([
        owner.owner,
        owner.handoffPath,
        owner.total,
        owner.blockers,
        owner.placeholders,
        owner.missing,
        owner.optionalEmpty,
        owner.secretKeys,
        owner.safeDefaultAvailable,
        owner.requiresOwnerInput,
        owner.ownerInputReasons || [],
        owner.nextCommand,
        item.canonicalKey,
        item.status,
        item.required,
        item.secret,
        item.blocker,
        item.valueClass,
        item.safeToPreFill,
        item.safeDefaultAvailable,
        item.requiresOwnerInput,
        item.ownerInputReason,
        item.fillGuidance,
        validation.https === true,
        validation.nonLocal === true,
        validation.minLength || "",
        validation.expectedValues || [],
      ]);
    }
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseEnvOwnerInputPacketArtifact(summary) {
  const readiness = releaseEnvReadinessRedactedArtifact(summary);
  const items = (readiness.items || [])
    .filter((item) => item.requiresOwnerInput === true)
    .map((item, index) => ({
      inputOrder: index + 1,
      fillOrder: item.fillOrder,
      owner: item.owner,
      owners: item.owners || [],
      group: item.group,
      requirement: item.requirement,
      canonicalKey: item.canonicalKey,
      aliases: item.aliases || [],
      status: item.status,
      valueClass: item.valueClass,
      ownerInputReason: item.ownerInputReason,
      secret: item.secret,
      safeToPreFill: item.safeToPreFill,
      safeDefaultAvailable: item.safeDefaultAvailable,
      requiresOwnerInput: item.requiresOwnerInput,
      required: item.required,
      validation: item.validation,
      fillGuidance: item.fillGuidance,
      collectionGuidance: item.secret
        ? "Collect through the approved secret manager or secure release channel; do not paste values into chat, commits, or artifacts."
        : item.ownerInputReason === "production-endpoint"
          ? "Collect the production-equivalent HTTPS endpoint from the owning runtime or deployment platform."
          : "Collect the production value from the owning release context.",
    }));
  const owners = [...items.reduce((map, item) => {
    if (!map.has(item.owner)) {
      map.set(item.owner, {
        owner: item.owner,
        totalInputs: 0,
        secretInputs: 0,
        productionEndpointInputs: 0,
        ownerProductionValueInputs: 0,
        keys: [],
        reasons: new Set(),
      });
    }
    const owner = map.get(item.owner);
    owner.totalInputs += 1;
    if (item.secret) owner.secretInputs += 1;
    if (item.ownerInputReason === "production-endpoint") owner.productionEndpointInputs += 1;
    if (item.ownerInputReason === "owner-production-value") owner.ownerProductionValueInputs += 1;
    owner.keys.push(item.canonicalKey);
    owner.reasons.add(item.ownerInputReason);
    return map;
  }, new Map()).values()]
    .map((owner) => ({
      ...owner,
      reasons: [...owner.reasons].sort(),
      keys: owner.keys.sort(),
    }))
    .sort((left, right) => right.totalInputs - left.totalInputs || left.owner.localeCompare(right.owner))
    .map((owner, index) => ({
      ...owner,
      fileName: `${String(index + 1).padStart(2, "0")}-${owner.owner}`,
      packetPath: path.posix.join("artifacts", "ddd", "release", "release-env-owner-input-packet", `${String(index + 1).padStart(2, "0")}-${owner.owner}.json`),
      packetMarkdownPath: path.posix.join("artifacts", "ddd", "release", "release-env-owner-input-packet", `${String(index + 1).padStart(2, "0")}-${owner.owner}.md`),
      handoffPath: path.posix.join("artifacts", "ddd", "release", "release-env-owner-handoff-redacted", `${String((readiness.byOwner || []).findIndex((entry) => entry.owner === owner.owner) + 1).padStart(2, "0")}-${owner.owner}.md`),
    }));
  const ownerInputReasonCounts = items.reduce((counts, item) => {
    counts[item.ownerInputReason] = (counts[item.ownerInputReason] || 0) + 1;
    return counts;
  }, {});
  const validationCommands = [
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    finalReadinessSummaryCommand,
    finalGoNoGoEnforceCommand,
  ];
  const receiptCommands = [
    ...validationCommands.slice(0, 6),
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-readiness-summary.mjs",
    "node scripts/ddd-release-config-owner-input-reconciliation.mjs",
    "DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh",
    finalGoNoGoEnforceCommand,
  ];
  return {
    generatedAt: summary.generatedAt,
    status: summary.status,
    redacted: true,
    valuePolicy: "No concrete environment values are emitted; this packet lists only owner, key, validation, reason, and redacted collection guidance.",
    sourceArtifact: path.posix.join("artifacts", "ddd", "release", "release-env-readiness-redacted.json"),
    envFile: readiness.envFile ? "<release-env-file>" : null,
    postCollectionReceipt: {
      redacted: true,
      purpose: "Verify that collected owner values removed release env placeholders without exposing concrete values.",
      commands: receiptCommands,
      passCriteria: {
        releaseEnvReadinessStatus: "PASS",
        releaseEnvReadinessBlockers: 0,
        releaseEnvReadinessPlaceholders: 0,
        releaseEnvReadinessMissing: 0,
        configOwnerInputReconciliationStatus: "PASS",
        configOwnerInputReconciliationUnmappedKeys: 0,
      },
    },
    summary: {
      requiredOwnerInputs: items.length,
      ownerCount: owners.length,
      secretInputs: items.filter((item) => item.secret).length,
      productionEndpointInputs: items.filter((item) => item.ownerInputReason === "production-endpoint").length,
      ownerProductionValueInputs: items.filter((item) => item.ownerInputReason === "owner-production-value").length,
      blockingSafeDefaultAvailable: readiness.summary.blockingSafeDefaultAvailable,
      safeDefaultsExhausted: readiness.summary.safeDefaultsExhausted,
      ownerInputReasonCounts,
    },
    validationCommands,
    owners,
    items,
  };
}

function releaseEnvOwnerInputPacketMarkdown(summary) {
  const artifact = releaseEnvOwnerInputPacketArtifact(summary);
  const lines = [
    "# DDD Release Env Owner Input Packet",
    "",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Env file: ${artifact.envFile || "missing"}`,
    `Value policy: ${artifact.valuePolicy}`,
    `Required owner inputs: ${artifact.summary.requiredOwnerInputs}`,
    `Owners: ${artifact.summary.ownerCount}`,
    `Secret inputs: ${artifact.summary.secretInputs}`,
    `Production endpoint inputs: ${artifact.summary.productionEndpointInputs}`,
    `Owner production value inputs: ${artifact.summary.ownerProductionValueInputs}`,
    `Blocking safe defaults available: ${artifact.summary.blockingSafeDefaultAvailable}`,
    `Safe defaults exhausted: ${artifact.summary.safeDefaultsExhausted}`,
    "",
    "## Owners",
    "",
  ];
  for (const owner of artifact.owners || []) {
    lines.push(`- ${owner.owner}: inputs=${owner.totalInputs}, secrets=${owner.secretInputs}, endpoints=${owner.productionEndpointInputs}, ownerValues=${owner.ownerProductionValueInputs}, reasons=${owner.reasons.join("|") || "none"}, handoff=${owner.handoffPath}`);
  }
  lines.push("", "## Inputs", "");
  for (const item of artifact.items || []) {
    const validation = item.validation || {};
    lines.push(`- ${item.inputOrder}. \`${item.canonicalKey}\` owner=${item.owner} class=${item.valueClass} reason=${item.ownerInputReason} secret=${item.secret} status=${item.status}`);
    lines.push(`  - validation: https=${validation.https === true}; nonLocal=${validation.nonLocal === true}; minLength=${validation.minLength || "none"}; expectedValues=${(validation.expectedValues || []).join("|") || "none"}`);
    lines.push(`  - aliases: ${(item.aliases || []).join(", ") || "none"}`);
    lines.push(`  - guidance: ${item.collectionGuidance}`);
  }
  lines.push("", "## After Collection", "");
  for (const command of artifact.validationCommands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "## Receipt Gate", "");
  lines.push(`Purpose: ${artifact.postCollectionReceipt.purpose}`);
  lines.push("Pass criteria:");
  for (const [key, value] of Object.entries(artifact.postCollectionReceipt.passCriteria || {})) {
    lines.push(`- ${key}: ${value}`);
  }
  lines.push("Commands:");
  for (const command of artifact.postCollectionReceipt.commands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerInputPacketCsv(summary) {
  const artifact = releaseEnvOwnerInputPacketArtifact(summary);
  const rows = [[
    "inputOrder",
    "owner",
    "canonicalKey",
    "aliases",
    "group",
    "requirement",
    "status",
    "valueClass",
    "ownerInputReason",
    "secret",
    "safeDefaultAvailable",
    "required",
    "validationHttps",
    "validationNonLocal",
    "validationMinLength",
    "validationExpectedValues",
    "collectionGuidance",
  ]];
  for (const item of artifact.items || []) {
    const validation = item.validation || {};
    rows.push([
      item.inputOrder,
      item.owner,
      item.canonicalKey,
      item.aliases || [],
      item.group,
      item.requirement,
      item.status,
      item.valueClass,
      item.ownerInputReason,
      item.secret,
      item.safeDefaultAvailable,
      item.required,
      validation.https === true,
      validation.nonLocal === true,
      validation.minLength || "",
      validation.expectedValues || [],
      item.collectionGuidance,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseConfigOwnerInputReconciliationArtifact(summary) {
  const configBlockerDetails = Array.isArray(summary.diagnostics?.releaseConfig?.blockerDetails)
    ? summary.diagnostics.releaseConfig.blockerDetails
    : [];
  const configPlaceholderDetails = configBlockerDetails.filter((detail) => detail?.blockedByPlaceholderKey === true);
  const configPlaceholderKeys = sortedUniqueStrings(configPlaceholderDetails.map((detail) => detail.matchedKey));
  const ownerInputPacket = releaseEnvOwnerInputPacketArtifact(summary);
  const ownerInputItems = Array.isArray(ownerInputPacket.items) ? ownerInputPacket.items : [];
  const ownerInputKeyIndex = new Map();

  for (const item of ownerInputItems) {
    for (const key of sortedUniqueStrings([item.canonicalKey, ...(item.aliases || [])])) {
      ownerInputKeyIndex.set(key, {
        canonicalKey: item.canonicalKey,
        owner: item.owner,
        ownerInputReason: item.ownerInputReason,
        valueClass: item.valueClass,
      });
    }
  }

  const mappedConfigPlaceholderKeys = [];
  const unmappedConfigPlaceholderKeys = [];
  for (const key of configPlaceholderKeys) {
    const ownerInput = ownerInputKeyIndex.get(key);
    if (ownerInput) {
      mappedConfigPlaceholderKeys.push({
        key,
        owner: ownerInput.owner,
        canonicalKey: ownerInput.canonicalKey,
        ownerInputReason: ownerInput.ownerInputReason,
        valueClass: ownerInput.valueClass,
        duplicateConfigBlockers: configPlaceholderDetails.filter((detail) => detail.matchedKey === key).length,
      });
    } else {
      unmappedConfigPlaceholderKeys.push(key);
    }
  }

  const configPlaceholderKeySet = new Set(configPlaceholderKeys);
  const ownerInputsWithoutConfigPlaceholder = ownerInputItems
    .filter((item) => !sortedUniqueStrings([item.canonicalKey, ...(item.aliases || [])]).some((key) => configPlaceholderKeySet.has(key)))
    .map((item) => ({
      canonicalKey: item.canonicalKey,
      owner: item.owner,
      ownerInputReason: item.ownerInputReason,
      valueClass: item.valueClass,
    }))
    .sort((left, right) => left.canonicalKey.localeCompare(right.canonicalKey));

  const issues = [];
  if (Number(summary.diagnostics?.releaseConfig?.summary?.releaseConfigBlockersFromPlaceholders || 0) !== configPlaceholderDetails.length) {
    issues.push("release config placeholder blocker summary must match blockerDetails");
  }
  if (unmappedConfigPlaceholderKeys.length > 0) {
    issues.push(`config placeholder keys must be covered by owner input packet: ${unmappedConfigPlaceholderKeys.join(",")}`);
  }

  const body = {
    status: issues.length === 0 ? "PASS" : "FAIL",
    redacted: true,
    contract: "ddd-release-config-owner-input-reconciliation",
    configArtifact: "artifacts/ddd/config/release-config-evidence.json",
    ownerInputPacket: "artifacts/ddd/release/release-env-owner-input-packet.json",
    summary: {
      configPlaceholderBlockers: configPlaceholderDetails.length,
      uniqueConfigPlaceholderKeys: configPlaceholderKeys.length,
      ownerInputKeys: ownerInputItems.length,
      mappedConfigPlaceholderKeys: mappedConfigPlaceholderKeys.length,
      unmappedConfigPlaceholderKeys: unmappedConfigPlaceholderKeys.length,
      duplicateConfigPlaceholderBlockers: configPlaceholderDetails.length - configPlaceholderKeys.length,
      ownerInputsWithoutConfigPlaceholder: ownerInputsWithoutConfigPlaceholder.length,
    },
    mappedConfigPlaceholderKeys,
    unmappedConfigPlaceholderKeys,
    ownerInputsWithoutConfigPlaceholder,
    issueCount: issues.length,
    issues,
  };
  return {
    generatedAt: stableGeneratedAtForFile(releaseConfigOwnerInputReconciliationOutput, body),
    ...body,
  };
}

function releaseOwnerInputReceiptArtifact(summary) {
  const readiness = releaseEnvReadinessRedactedArtifact(summary);
  const packet = releaseEnvOwnerInputPacketArtifact(summary);
  const reconciliation = releaseConfigOwnerInputReconciliationArtifact(summary);
  const observed = {
    releaseEnvReadinessStatus: readiness.status,
    releaseEnvReadinessBlockers: Number(readiness.summary?.blockers || 0),
    releaseEnvReadinessPlaceholders: Number(readiness.summary?.placeholders || 0),
    releaseEnvReadinessMissing: Number(readiness.summary?.missing || 0),
    configOwnerInputReconciliationStatus: reconciliation.status,
    configOwnerInputReconciliationUnmappedKeys: Number(reconciliation.summary?.unmappedConfigPlaceholderKeys || 0),
  };
  const expected = packet.postCollectionReceipt?.passCriteria || {
    releaseEnvReadinessStatus: "PASS",
    releaseEnvReadinessBlockers: 0,
    releaseEnvReadinessPlaceholders: 0,
    releaseEnvReadinessMissing: 0,
    configOwnerInputReconciliationStatus: "PASS",
    configOwnerInputReconciliationUnmappedKeys: 0,
  };
  const criteria = Object.fromEntries(Object.entries(expected).map(([field, expectedValue]) => {
    const actual = observed[field];
    return [field, {
      expected: expectedValue,
      actual,
      met: JSON.stringify(actual) === JSON.stringify(expectedValue),
    }];
  }));
  const missingCriteria = Object.entries(criteria)
    .filter(([, criterion]) => criterion.met !== true)
    .map(([field]) => field);
  const readinessByOwner = new Map((readiness.byOwner || readiness.owners || []).map((owner) => [owner.owner, owner]));
  const ownerReceipts = (packet.owners || []).map((owner) => {
    const observedOwner = readinessByOwner.get(owner.owner) || {};
    const remainingPlaceholders = Number(observedOwner.placeholders || observedOwner.placeholder || 0);
    const remainingMissing = Number(observedOwner.missing || 0);
    return {
      owner: owner.owner,
      requiredOwnerInputs: Number(owner.totalInputs || 0),
      secretInputs: Number(owner.secretInputs || 0),
      productionEndpointInputs: Number(owner.productionEndpointInputs || 0),
      ownerProductionValueInputs: Number(owner.ownerProductionValueInputs || 0),
      remainingPlaceholders,
      remainingMissing,
      ready: remainingPlaceholders === 0 && remainingMissing === 0,
      packetPath: owner.packetPath,
      handoffPath: owner.handoffPath,
    };
  });
  const ownerReceiptByName = new Map(ownerReceipts.map((owner) => [owner.owner, owner]));
  const itemReceipts = (packet.items || []).map((item) => {
    const owner = ownerReceiptByName.get(item.owner) || {};
    return {
      inputOrder: Number(item.inputOrder || 0),
      fillOrder: Number(item.fillOrder || 0),
      owner: item.owner || "unknown",
      ownerReady: owner.ready === true,
      canonicalKey: item.canonicalKey || "",
      aliases: item.aliases || [],
      group: item.group || "",
      requirement: item.requirement || "",
      status: item.status || "UNKNOWN",
      valueClass: item.valueClass || "",
      ownerInputReason: item.ownerInputReason || "",
      secret: item.secret === true,
      requiresOwnerInput: item.requiresOwnerInput === true,
      required: item.required === true,
      httpsRequired: item.validation?.https === true,
      nonLocalRequired: item.validation?.nonLocal === true,
      minLength: item.validation?.minLength ?? "",
      safeDefaultAvailable: item.safeDefaultAvailable === true,
      packetPath: owner.packetPath || "",
      handoffPath: owner.handoffPath || "",
      collectionGuidance: item.collectionGuidance || "",
    };
  }).sort((left, right) => left.inputOrder - right.inputOrder || left.owner.localeCompare(right.owner));
  const body = {
    status: missingCriteria.length === 0 ? "PASS" : "PENDING_OWNER_INPUT",
    redacted: true,
    contract: "ddd-release-owner-input-receipt",
    envFile: "<release-env-file>",
    sourceArtifacts: {
      ownerInputPacket: "artifacts/ddd/release/release-env-owner-input-packet.json",
      releaseEnvReadiness: "artifacts/ddd/release/release-env-readiness-redacted.json",
      configOwnerInputReconciliation: "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
    },
    cutoverReady: missingCriteria.length === 0,
    summary: {
      requiredOwnerInputs: ownerReceipts.reduce((sum, owner) => sum + owner.requiredOwnerInputs, 0),
      itemReceiptCount: itemReceipts.length,
      ownerCount: ownerReceipts.length,
      readyOwnerCount: ownerReceipts.filter((owner) => owner.ready).length,
      pendingOwnerCount: ownerReceipts.filter((owner) => !owner.ready).length,
      missingCriteria: missingCriteria.length,
      cutoverReady: missingCriteria.length === 0,
    },
    observed,
    criteria,
    missingCriteria,
    ownerReceipts,
    itemReceipts,
    validationCommands: packet.postCollectionReceipt?.commands || packet.validationCommands || [],
  };
  return {
    generatedAt: stableGeneratedAtForFile(releaseOwnerInputReceiptOutput, body),
    ...body,
  };
}

function releaseOwnerInputReceiptMarkdown(summary) {
  const receipt = releaseOwnerInputReceiptArtifact(summary);
  const lines = [
    "# DDD Release Owner Input Receipt",
    "",
    `Generated at: ${receipt.generatedAt}`,
    `Status: ${receipt.status}`,
    `Cutover ready: ${receipt.cutoverReady}`,
    `Required owner inputs: ${receipt.summary.requiredOwnerInputs}`,
    `Owners: ${receipt.summary.ownerCount}`,
    `Missing criteria: ${receipt.summary.missingCriteria}`,
    "",
    "## Criteria",
    "",
  ];
  for (const [field, criterion] of Object.entries(receipt.criteria || {})) {
    lines.push(`- ${field}: expected=${criterion.expected}; actual=${criterion.actual}; met=${criterion.met}`);
  }
  lines.push("", "## Owners", "");
  for (const owner of receipt.ownerReceipts || []) {
    lines.push(`- ${owner.owner}: ready=${owner.ready}; inputs=${owner.requiredOwnerInputs}; placeholders=${owner.remainingPlaceholders}; missing=${owner.remainingMissing}; packet=${owner.packetPath}`);
  }
  lines.push("", "## Validation Commands", "");
  for (const command of receipt.validationCommands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function releaseOwnerInputReceiptCsv(summary) {
  const receipt = releaseOwnerInputReceiptArtifact(summary);
  const rows = [[
    "owner",
    "ready",
    "requiredOwnerInputs",
    "secretInputs",
    "productionEndpointInputs",
    "ownerProductionValueInputs",
    "remainingPlaceholders",
    "remainingMissing",
    "packetPath",
    "handoffPath",
    "receiptStatus",
    "cutoverReady",
    "missingCriteria",
  ]];
  for (const owner of receipt.ownerReceipts || []) {
    rows.push([
      owner.owner,
      owner.ready,
      owner.requiredOwnerInputs,
      owner.secretInputs,
      owner.productionEndpointInputs,
      owner.ownerProductionValueInputs,
      owner.remainingPlaceholders,
      owner.remainingMissing,
      owner.packetPath,
      owner.handoffPath,
      receipt.status,
      receipt.cutoverReady,
      receipt.missingCriteria || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseOwnerInputReceiptItemsCsv(summary) {
  const receipt = releaseOwnerInputReceiptArtifact(summary);
  const rows = [[
    "inputOrder",
    "fillOrder",
    "owner",
    "ownerReady",
    "canonicalKey",
    "aliases",
    "group",
    "requirement",
    "status",
    "valueClass",
    "ownerInputReason",
    "secret",
    "requiresOwnerInput",
    "required",
    "httpsRequired",
    "nonLocalRequired",
    "minLength",
    "safeDefaultAvailable",
    "packetPath",
    "handoffPath",
    "collectionGuidance",
  ]];
  for (const item of receipt.itemReceipts || []) {
    rows.push([
      item.inputOrder,
      item.fillOrder,
      item.owner,
      item.ownerReady,
      item.canonicalKey,
      item.aliases || [],
      item.group,
      item.requirement,
      item.status,
      item.valueClass,
      item.ownerInputReason,
      item.secret,
      item.requiresOwnerInput,
      item.required,
      item.httpsRequired,
      item.nonLocalRequired,
      item.minLength,
      item.safeDefaultAvailable,
      item.packetPath,
      item.handoffPath,
      item.collectionGuidance,
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseOwnerInputReceiptItemsMarkdown(summary) {
  const receipt = releaseOwnerInputReceiptArtifact(summary);
  const lines = [
    "# DDD Release Owner Input Receipt Items",
    "",
    `Generated at: ${receipt.generatedAt}`,
    `Status: ${receipt.status}`,
    `Cutover ready: ${receipt.cutoverReady}`,
    `Required owner inputs: ${receipt.summary.requiredOwnerInputs}`,
    `Item receipts: ${receipt.summary.itemReceiptCount || 0}`,
    "",
    "## Items",
    "",
  ];
  for (const item of receipt.itemReceipts || []) {
    lines.push(
      `- [ ] ${item.inputOrder}. \`${item.canonicalKey}\` owner=${item.owner}; status=${item.status}; class=${item.valueClass}; reason=${item.ownerInputReason}; secret=${item.secret}; https=${item.httpsRequired}; nonLocal=${item.nonLocalRequired}; aliases=${(item.aliases || []).join("|") || "none"}; packet=${item.packetPath || "n/a"}; handoff=${item.handoffPath || "n/a"}`,
    );
    if (item.collectionGuidance) {
      lines.push(`  - Collection: ${item.collectionGuidance}`);
    }
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function releaseOwnerInputReceiptOwnerItems(summary) {
  const receipt = releaseOwnerInputReceiptArtifact(summary);
  const packet = releaseEnvOwnerInputPacketArtifact(summary);
  const fileNameByOwner = new Map((packet.owners || []).map((owner) => [owner.owner, owner.fileName]));
  return [...new Set((receipt.itemReceipts || []).map((item) => item.owner))]
    .sort((left, right) => {
      const leftCount = (receipt.itemReceipts || []).filter((item) => item.owner === left).length;
      const rightCount = (receipt.itemReceipts || []).filter((item) => item.owner === right).length;
      return rightCount - leftCount || left.localeCompare(right);
    })
    .map((owner, index) => ({
      owner,
      fileName: `${fileNameByOwner.get(owner) || `${String(index + 1).padStart(2, "0")}-${safeFileName(owner)}`}.md`,
      items: (receipt.itemReceipts || []).filter((item) => item.owner === owner),
      receipt,
    }));
}

function releaseOwnerInputReceiptOwnerItemsMarkdown(ownerChecklist) {
  const { owner, items, receipt } = ownerChecklist;
  const lines = [
    `# DDD Release Owner Input Receipt Items: ${owner}`,
    "",
    `Generated at: ${receipt.generatedAt}`,
    `Status: ${receipt.status}`,
    `Cutover ready: ${receipt.cutoverReady}`,
    `Owner input items: ${items.length}`,
    "",
    "## Items",
    "",
  ];
  for (const item of items) {
    lines.push(
      `- [ ] ${item.inputOrder}. \`${item.canonicalKey}\` status=${item.status}; class=${item.valueClass}; reason=${item.ownerInputReason}; secret=${item.secret}; https=${item.httpsRequired}; nonLocal=${item.nonLocalRequired}; aliases=${(item.aliases || []).join("|") || "none"}; packet=${item.packetPath || "n/a"}; handoff=${item.handoffPath || "n/a"}`,
    );
    if (item.collectionGuidance) {
      lines.push(`  - Collection: ${item.collectionGuidance}`);
    }
  }
  lines.push("", "Concrete values are intentionally omitted from this artifact.");
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerInputPacketOwnerArtifact(packet, owner) {
  const items = (packet.items || []).filter((item) => item.owner === owner.owner);
  return {
    generatedAt: packet.generatedAt,
    status: packet.status,
    redacted: true,
    valuePolicy: packet.valuePolicy,
    owner: owner.owner,
    summary: {
      totalInputs: owner.totalInputs,
      secretInputs: owner.secretInputs,
      productionEndpointInputs: owner.productionEndpointInputs,
      ownerProductionValueInputs: owner.ownerProductionValueInputs,
      reasons: owner.reasons || [],
      keys: owner.keys || [],
    },
    packetPath: owner.packetPath,
    packetMarkdownPath: owner.packetMarkdownPath,
    handoffPath: owner.handoffPath,
    validationCommands: packet.validationCommands || [],
    postCollectionReceipt: packet.postCollectionReceipt,
    items,
  };
}

function releaseEnvOwnerInputPacketOwnerMarkdown(packet, owner) {
  const artifact = releaseEnvOwnerInputPacketOwnerArtifact(packet, owner);
  const lines = [
    `# DDD Release Env Owner Input Packet: ${artifact.owner}`,
    "",
    "Concrete values are intentionally omitted from this artifact.",
    `Generated at: ${artifact.generatedAt}`,
    `Status: ${artifact.status}`,
    `Inputs: ${artifact.summary.totalInputs}`,
    `Secret inputs: ${artifact.summary.secretInputs}`,
    `Production endpoint inputs: ${artifact.summary.productionEndpointInputs}`,
    `Owner production value inputs: ${artifact.summary.ownerProductionValueInputs}`,
    `Reasons: ${artifact.summary.reasons.join(", ") || "none"}`,
    `Redacted handoff: ${artifact.handoffPath}`,
    "",
    "## Inputs",
    "",
  ];
  for (const item of artifact.items || []) {
    const validation = item.validation || {};
    lines.push(`- \`${item.canonicalKey}\`: class=${item.valueClass}; reason=${item.ownerInputReason}; secret=${item.secret}; status=${item.status}; group=${item.group}; requirement=${item.requirement}`);
    lines.push(`  - validation: https=${validation.https === true}; nonLocal=${validation.nonLocal === true}; minLength=${validation.minLength || "none"}; expectedValues=${(validation.expectedValues || []).join("|") || "none"}`);
    lines.push(`  - aliases: ${(item.aliases || []).join(", ") || "none"}`);
    lines.push(`  - guidance: ${item.collectionGuidance}`);
  }
  lines.push("", "## After Collection", "");
  for (const command of artifact.validationCommands || []) {
    lines.push(`- \`${command}\``);
  }
  lines.push("", "## Receipt Gate", "");
  lines.push(`Purpose: ${artifact.postCollectionReceipt.purpose}`);
  for (const command of artifact.postCollectionReceipt.commands || []) {
    lines.push(`- \`${command}\``);
  }
  return `${lines.join("\n")}\n`;
}

function releaseEnvOwnerHandoffCsv(summary) {
  const artifact = releaseEnvOwnerHandoffArtifact(summary);
  const rows = [[
    "owner",
    "queueOrder",
    "queueStatus",
    "canExecute",
    "canonicalFillItemCount",
    "unresolvedAliasCount",
    "secretCanonicalKeyCount",
    "safeToPreFillCanonicalKeyCount",
    "canonicalKeys",
    "secretCanonicalKeys",
    "safeToPreFillCanonicalKeys",
    "requiredCanonicalKeys",
    "readyBatchIds",
    "blockedBatchIds",
    "nextCommand",
    "postFillCommands",
  ]];
  for (const owner of artifact.owners || []) {
    rows.push([
      owner.owner,
      owner.queueOrder,
      owner.queueStatus,
      owner.canExecute,
      owner.canonicalFillItemCount,
      owner.unresolvedAliasCount,
      owner.secretCanonicalKeyCount,
      owner.safeToPreFillCanonicalKeyCount,
      owner.canonicalKeys || [],
      owner.secretCanonicalKeys || [],
      owner.safeToPreFillCanonicalKeys || [],
      owner.requiredCanonicalKeys || [],
      owner.readyBatchIds || [],
      owner.blockedBatchIds || [],
      owner.nextCommand,
      owner.postFillCommands || [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function releaseExecutionQueueCsv(summary) {
  const artifact = releaseExecutionQueueArtifact(summary);
  const rows = [[
    "queueStatus",
    "priority",
    "source",
    "owner",
    "id",
    "pendingItems",
    "dependsOn",
    "commands",
    "envKeys",
    "envCheckGroups",
    "expectedArtifacts",
    "exitCriteria",
  ]];
  for (const batch of artifact.readyBatches || []) {
    rows.push([
      "ready",
      batch.priority,
      batch.source,
      batch.owner,
      batch.id,
      batch.pendingItems,
      [],
      batch.commands || [],
      batch.envKeys || [],
      (batch.envCheckGroups || []).map((group) => group.spec),
      batch.expectedArtifacts || [],
      batch.exitCriteria || [],
    ]);
  }
  for (const batch of artifact.blockedBatches || []) {
    rows.push([
      "blocked",
      batch.priority,
      batch.source,
      batch.owner,
      batch.id,
      batch.pendingItems,
      (batch.unmetDependencies || []).map((dependency) => dependency.id),
      [],
      [],
      [],
      [],
      [],
    ]);
  }
  return `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`;
}

function shellCommentLine(text) {
  return `# ${String(text || "").replace(/\r?\n/g, " ")}`;
}

function shellSingleQuoted(value) {
  return `'${String(value ?? "").replace(/'/g, "'\\''")}'`;
}

function releaseRepoRootPreambleLines() {
  return [
    "SCRIPT_DIR=$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)",
    "if [[ -z \"${LUMIRA_REPO_ROOT:-}\" ]]; then",
    "  if [[ -f \"scripts/ddd-release-readiness-summary.mjs\" ]]; then",
    "    LUMIRA_REPO_ROOT=$(pwd)",
    "  else",
    "    LUMIRA_REPO_ROOT=$(cd \"${SCRIPT_DIR}/../../..\" && pwd)",
    "  fi",
    "fi",
    "export LUMIRA_REPO_ROOT",
    "cd \"${LUMIRA_REPO_ROOT}\"",
  ];
}

function safeReleaseEnvExportJs() {
  return [
    "import fs from 'node:fs';",
    "import path from 'node:path';",
    "const [file, permissionCheckedArg] = process.argv.slice(1);",
    "const templateNames = new Set(['release-env-missing.template.env', 'release-closure-wave-env.template.env', 'release-final-owner-queue-env.template.env', 'release-env-canonical-fill.template.env']);",
    "if (templateNames.has(path.basename(file))) {",
    "  console.error(`[ddd-release-env][template-refused] file=${file}`);",
    "  process.exit(1);",
    "}",
    "const permissionAlreadyChecked = permissionCheckedArg === '1' || permissionCheckedArg === 'true' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === '1' || process.env.DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED === 'true';",
    "const mode = permissionAlreadyChecked ? 0o600 : fs.statSync(file).mode & 0o777;",
    "if (!permissionAlreadyChecked && (mode & 0o077) !== 0) {",
    "  console.error(`[ddd-release-env][permission-refused] file=${file} mode=${mode.toString(8).padStart(3, '0')} required=600`);",
    "  process.exit(1);",
    "}",
    "const text = fs.readFileSync(file, 'utf8');",
    "const quote = (value) => `'${String(value).replace(/'/g, `'\\\\''`)}'`;",
    "let lineNumber = 0;",
    "for (const line of text.split(/\\r?\\n/)) {",
    "  lineNumber += 1;",
    "  const trimmed = line.trim();",
    "  if (!trimmed || trimmed.startsWith('#')) continue;",
    "  const match = trimmed.match(/^(?:export\\s+)?([A-Z_][A-Z0-9_]*)\\s*=\\s*(.*)$/);",
    "  if (!match) {",
    "    console.error(`[ddd-release-env][env-invalid] line=${lineNumber}`);",
    "    process.exit(1);",
    "  }",
    "  let value = match[2].trim();",
    "  const quoted = value.match(/^(['\\\"])(.*)\\1$/s);",
    "  if (quoted) value = quoted[2];",
    "  console.log(`export ${match[1]}=${quote(value)}`);",
    "}",
  ].join(" ");
}

function safeReleaseEnvLoaderLines(functionName = "safe_load_release_env_file") {
  return [
    `${functionName}() {`,
    "  local exports",
    `  if ! exports=$(node --input-type=module -e ${shellSingleQuoted(safeReleaseEnvExportJs())} "$DDD_RELEASE_ENV_FILE" "\${DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED:-}"); then`,
    "    return 1",
    "  fi",
    "  eval \"${exports}\"",
    "}",
  ];
}

function envCheckGroups(envKeys) {
  const groups = [];
  const seenLabels = new Set();
  for (const envKey of [...new Set((envKeys || []).filter(Boolean))].sort()) {
    const canonicalKey = canonicalReleaseEnvTemplateKey(envKey);
    if (seenLabels.has(canonicalKey)) {
      continue;
    }
    seenLabels.add(canonicalKey);
    const aliases = releaseConfigEnvAliasesByCanonical.get(canonicalKey) || [envKey];
    const keys = [...new Set([...aliases, envKey].filter(Boolean))].sort();
    groups.push({
      label: canonicalKey,
      keys,
      spec: `${canonicalKey}=${keys.join("|")}`,
    });
  }
  return groups;
}

function envCheckSpecs(envKeys) {
  return envCheckGroups(envKeys).map((group) => group.spec);
}

function validateEnvCheckGroupsForEnvKeys(scope, envKeys, groups, issues) {
  const cleanEnvKeys = (envKeys || []).filter(Boolean);
  const cleanGroups = groups || [];
  const expectedSpecs = envCheckSpecs(cleanEnvKeys);
  const actualSpecs = cleanGroups.map((group) => group.spec);
  if (cleanEnvKeys.length > 0 && cleanGroups.length === 0) {
    issues.push(`${scope} envCheckGroups must be present when envKeys are present`);
  }
  if (!sameStringSet(actualSpecs, expectedSpecs)) {
    issues.push(`${scope} envCheckGroups must match envKeys alias groups`);
  }
  for (const group of cleanGroups) {
    if (!group.label || !Array.isArray(group.keys) || group.keys.length === 0 || !group.spec) {
      issues.push(`${scope} envCheckGroups entries must include label, keys, and spec`);
      continue;
    }
    if (group.spec !== `${group.label}=${group.keys.join("|")}`) {
      issues.push(`${scope} envCheckGroups spec must equal label=keys`);
    }
  }
}

function releaseExecutionCommands(summary) {
  const artifact = releaseExecutionQueueArtifact(summary);
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release execution commands."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine(`Status: ${summary.status}`),
    shellCommentLine(`Release gate blockers: ${summary.gate?.blockers ?? 0}`),
    shellCommentLine("This file contains command hints only. Provide a real DDD_RELEASE_ENV_FILE before running evidence commands."),
    shellCommentLine("Do not use release-env-missing.template.env as release evidence."),
    ...releaseRepoRootPreambleLines(),
    "",
  ];
  if (artifact.readyBatches.length === 0) {
    lines.push(shellCommentLine("No ready batches."));
    return `${lines.join("\n")}\n`;
  }
  lines.push("if [[ \"${DDD_RELEASE_LIST_BATCHES:-}\" == \"1\" || \"${DDD_RELEASE_LIST_BATCHES:-}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_LIST_MATCHED=0");
  lines.push("  echo \"Ready release batches:\"");
  for (const batch of artifact.readyBatches) {
    lines.push(`  if [[ ( -z "\${DDD_RELEASE_BATCH:-}" || "\${DDD_RELEASE_BATCH:-}" == ${shellSingleQuoted(batch.id)} ) && ( -z "\${DDD_RELEASE_OWNER:-}" || "\${DDD_RELEASE_OWNER:-}" == ${shellSingleQuoted(batch.owner)} ) && ( -z "\${DDD_RELEASE_PRIORITY:-}" || "\${DDD_RELEASE_PRIORITY:-}" == ${shellSingleQuoted(batch.priority)} ) ]]; then`);
    lines.push(`    echo ${shellSingleQuoted(`${batch.id} ${batch.priority} ${batch.source}->${batch.owner} owner=${batch.owner} priority=${batch.priority}`)}`);
    lines.push("    DDD_RELEASE_LIST_MATCHED=1");
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_LIST_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No ready release batches matched DDD_RELEASE_BATCH=${DDD_RELEASE_BATCH:-} DDD_RELEASE_OWNER=${DDD_RELEASE_OWNER:-} DDD_RELEASE_PRIORITY=${DDD_RELEASE_PRIORITY:-}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  lines.push("DDD_RELEASE_BATCH=\"${DDD_RELEASE_BATCH:-}\"");
  lines.push("DDD_RELEASE_OWNER=\"${DDD_RELEASE_OWNER:-}\"");
  lines.push("DDD_RELEASE_PRIORITY=\"${DDD_RELEASE_PRIORITY:-}\"");
  lines.push("DDD_RELEASE_DRY_RUN=\"${DDD_RELEASE_DRY_RUN:-}\"");
  lines.push("DDD_RELEASE_CHECK_ENV_ONLY=\"${DDD_RELEASE_CHECK_ENV_ONLY:-}\"");
  lines.push("DDD_RELEASE_ALLOW_MISSING_ENV=\"${DDD_RELEASE_ALLOW_MISSING_ENV:-}\"");
  lines.push("DDD_RELEASE_CONTINUE_ON_ERROR=\"${DDD_RELEASE_CONTINUE_ON_ERROR:-}\"");
  lines.push("DDD_RELEASE_EXECUTION_REPORT=\"${DDD_RELEASE_EXECUTION_REPORT:-artifacts/ddd/release/release-execution-run-report.json}\"");
  lines.push("DDD_RELEASE_EXECUTION_REPORT_TMP=\"${DDD_RELEASE_EXECUTION_REPORT}.jsonl.$$\"");
  lines.push("DDD_RELEASE_EXECUTION_REPORT_FINALIZED=0");
  lines.push("DDD_RELEASE_NEEDS_ENV=1");
  lines.push("if [[ \"${DDD_RELEASE_DRY_RUN}\" == \"1\" || \"${DDD_RELEASE_DRY_RUN}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_NEEDS_ENV=0");
  lines.push("fi");
  lines.push("if [[ \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"1\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_NEEDS_ENV=1");
  lines.push("fi");
  lines.push("if [[ \"${DDD_RELEASE_NEEDS_ENV}\" == \"1\" ]]; then");
  lines.push("  if [[ -z \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then");
  lines.push("    echo \"DDD_RELEASE_ENV_FILE is required and must point to a completed release env file.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then");
  lines.push("    echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" ]]; then");
  lines.push("    echo \"Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' \"${DDD_RELEASE_ENV_FILE}\" 2>/dev/null || node -e \"const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));\" \"${DDD_RELEASE_ENV_FILE}\")");
  lines.push("  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then");
  lines.push("    echo \"Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600.\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  export DDD_RELEASE_ENV_FILE_PERMISSION_CHECKED=1");
  lines.push("fi");
  lines.push(...safeReleaseEnvLoaderLines());
  lines.push("if [[ \"${DDD_RELEASE_NEEDS_ENV}\" == \"1\" ]]; then");
  lines.push("  safe_load_release_env_file");
  lines.push("fi");
  lines.push("DDD_RELEASE_BATCH_MATCHED=0");
  lines.push("DDD_RELEASE_COMMAND_FAILURES=0");
  lines.push("append_release_execution_report_entry() {");
  lines.push("  local batch_id=\"$1\"");
  lines.push("  local batch_owner=\"$2\"");
  lines.push("  local batch_priority=\"$3\"");
  lines.push("  local command=\"$4\"");
  lines.push("  local status=\"$5\"");
  lines.push("  local duration_ms=\"$6\"");
  lines.push("  if [[ \"${DDD_RELEASE_DRY_RUN}\" == \"1\" || \"${DDD_RELEASE_DRY_RUN}\" == \"true\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"1\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"true\" ]]; then return 0; fi");
  lines.push("  node --input-type=module -e 'import fs from \"node:fs\"; const [file, batchId, owner, priority, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ batchId, owner, priority, command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\\n`);' \"${DDD_RELEASE_EXECUTION_REPORT_TMP}\" \"${batch_id}\" \"${batch_owner}\" \"${batch_priority}\" \"${command}\" \"${status}\" \"${duration_ms}\"");
  lines.push("}");
  lines.push("finalize_release_execution_report() {");
  lines.push("  local exit_code=\"$1\"");
  lines.push("  if [[ \"${DDD_RELEASE_DRY_RUN}\" == \"1\" || \"${DDD_RELEASE_DRY_RUN}\" == \"true\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"1\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"true\" ]]; then return 0; fi");
  lines.push("  if [[ \"${DDD_RELEASE_EXECUTION_REPORT_FINALIZED}\" == \"1\" ]]; then return \"${exit_code}\"; fi");
  lines.push("  DDD_RELEASE_EXECUTION_REPORT_FINALIZED=1");
  lines.push("  mkdir -p \"$(dirname \"${DDD_RELEASE_EXECUTION_REPORT}\")\"");
  lines.push("  node --input-type=module -e 'import fs from \"node:fs\"; const [tmp, out, exitCode, batchFilter, ownerFilter, priorityFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, \"utf8\").split(\"\\n\").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? \"PASS\" : \"FAIL\", exitCode: exit, batchFilter: batchFilter || null, ownerFilter: ownerFilter || null, priorityFilter: priorityFilter || null, summary, entries }, null, 2)}\\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' \"${DDD_RELEASE_EXECUTION_REPORT_TMP}\" \"${DDD_RELEASE_EXECUTION_REPORT}\" \"${exit_code}\" \"${DDD_RELEASE_BATCH}\" \"${DDD_RELEASE_OWNER}\" \"${DDD_RELEASE_PRIORITY}\"");
  lines.push("  if ! DDD_RELEASE_EXECUTION_REPORT=\"${DDD_RELEASE_EXECUTION_REPORT}\" node scripts/ddd-release-execution-run-report-contract.mjs; then");
  lines.push("    echo \"[ddd-release-execution][report-contract] failed\" >&2");
  lines.push("    return 1");
  lines.push("  fi");
  lines.push("  echo \"[ddd-release-execution][report] ${DDD_RELEASE_EXECUTION_REPORT}\"");
  lines.push("  return \"${exit_code}\"");
  lines.push("}");
  lines.push("trap 'status=$?; finalize_release_execution_report \"${status}\"; exit \"${status}\"' EXIT");
  lines.push("print_missing_env_groups() {");
  lines.push("  local batch_id=\"$1\"");
  lines.push("  shift");
  lines.push("  local missing=()");
  lines.push("  local spec label keys key found");
  lines.push("  for spec in \"$@\"; do");
  lines.push("    label=\"${spec%%=*}\"");
  lines.push("    keys=\"${spec#*=}\"");
  lines.push("    found=0");
  lines.push("    IFS='|' read -r -a key_group <<< \"${keys}\"");
  lines.push("    for key in \"${key_group[@]}\"; do");
  lines.push("      if [[ -n \"${!key:-}\" ]]; then");
  lines.push("        found=1");
  lines.push("        break");
  lines.push("      fi");
  lines.push("    done");
  lines.push("    if [[ \"${found}\" != \"1\" ]]; then");
  lines.push("      missing+=(\"${label}(${keys//|/ or })\")");
  lines.push("    fi");
  lines.push("  done");
  lines.push("  if [[ ${#missing[@]} -gt 0 ]]; then");
  lines.push("    echo \"[ddd-release-execution][env-check] ${batch_id} missing env groups: ${missing[*]}\" >&2");
  lines.push("    echo \"[ddd-release-execution][env-check] ${batch_id} at least one key in each group must be present.\" >&2");
  lines.push("    if [[ \"${DDD_RELEASE_ALLOW_MISSING_ENV}\" == \"1\" || \"${DDD_RELEASE_ALLOW_MISSING_ENV}\" == \"true\" ]]; then");
  lines.push("      echo \"[ddd-release-execution][env-check] ${batch_id} continuing because DDD_RELEASE_ALLOW_MISSING_ENV=${DDD_RELEASE_ALLOW_MISSING_ENV}\" >&2");
  lines.push("      return 0");
  lines.push("    fi");
  lines.push("    return 1");
  lines.push("  fi");
  lines.push("  return 0");
  lines.push("}");
  lines.push("run_command() {");
  lines.push("  local batch_id=\"$1\"");
  lines.push("  local batch_owner=\"$2\"");
  lines.push("  local batch_priority=\"$3\"");
  lines.push("  local command=\"$4\"");
  lines.push("  if [[ \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"1\" || \"${DDD_RELEASE_CHECK_ENV_ONLY}\" == \"true\" ]]; then");
  lines.push("    echo \"[ddd-release-execution][env-check-only] skip ${command}\"");
  lines.push("    return 0");
  lines.push("  fi");
  lines.push("  if [[ \"${DDD_RELEASE_DRY_RUN}\" == \"1\" || \"${DDD_RELEASE_DRY_RUN}\" == \"true\" ]]; then");
  lines.push("    echo \"[ddd-release-execution][dry-run] ${command}\"");
  lines.push("    return 0");
  lines.push("  fi");
  lines.push("  local started_ms");
  lines.push("  started_ms=$(node -e 'console.log(Date.now())')");
  lines.push("  set +e");
  lines.push("  bash -lc \"${command}\"");
  lines.push("  local status=$?");
  lines.push("  set -e");
  lines.push("  local finished_ms");
  lines.push("  finished_ms=$(node -e 'console.log(Date.now())')");
  lines.push("  append_release_execution_report_entry \"${batch_id}\" \"${batch_owner}\" \"${batch_priority}\" \"${command}\" \"${status}\" \"$((finished_ms - started_ms))\"");
  lines.push("  if [[ \"${status}\" != \"0\" ]]; then");
  lines.push("    echo \"[ddd-release-execution][command-failed] status=${status} command=${command}\" >&2");
  lines.push("    DDD_RELEASE_COMMAND_FAILURES=$((DDD_RELEASE_COMMAND_FAILURES + 1))");
  lines.push("    if [[ \"${DDD_RELEASE_CONTINUE_ON_ERROR}\" == \"1\" || \"${DDD_RELEASE_CONTINUE_ON_ERROR}\" == \"true\" ]]; then");
  lines.push("      echo \"[ddd-release-execution][command-failed] continuing because DDD_RELEASE_CONTINUE_ON_ERROR=${DDD_RELEASE_CONTINUE_ON_ERROR}\" >&2");
  lines.push("      return 0");
  lines.push("    fi");
  lines.push("    return \"${status}\"");
  lines.push("  fi");
  lines.push("  return 0");
  lines.push("}");
  lines.push("run_batch() {");
  lines.push("  local batch_id=\"$1\"");
  lines.push("  local batch_owner=\"$2\"");
  lines.push("  local batch_priority=\"$3\"");
  lines.push("  if [[ -n \"${DDD_RELEASE_BATCH}\" && \"${DDD_RELEASE_BATCH}\" != \"${batch_id}\" ]]; then");
  lines.push("    return 0");
  lines.push("  fi");
  lines.push("  if [[ -n \"${DDD_RELEASE_OWNER}\" && \"${DDD_RELEASE_OWNER}\" != \"${batch_owner}\" ]]; then");
  lines.push("    return 0");
  lines.push("  fi");
  lines.push("  if [[ -n \"${DDD_RELEASE_PRIORITY}\" && \"${DDD_RELEASE_PRIORITY}\" != \"${batch_priority}\" ]]; then");
  lines.push("    return 0");
  lines.push("  fi");
  lines.push("  DDD_RELEASE_BATCH_MATCHED=1");
  lines.push("  echo \"[ddd-release-execution] running ${batch_id} owner=${batch_owner} priority=${batch_priority}\"");
  lines.push("}");
  lines.push("");
  for (const batch of artifact.readyBatches) {
    lines.push(`run_batch ${shellSingleQuoted(batch.id)} ${shellSingleQuoted(batch.owner)} ${shellSingleQuoted(batch.priority)}`);
    lines.push(`if [[ "\${DDD_RELEASE_BATCH_MATCHED}" == "1" && ( -z "\${DDD_RELEASE_BATCH}" || "\${DDD_RELEASE_BATCH}" == ${shellSingleQuoted(batch.id)} ) && ( -z "\${DDD_RELEASE_OWNER}" || "\${DDD_RELEASE_OWNER}" == ${shellSingleQuoted(batch.owner)} ) && ( -z "\${DDD_RELEASE_PRIORITY}" || "\${DDD_RELEASE_PRIORITY}" == ${shellSingleQuoted(batch.priority)} ) ]]; then`);
    lines.push(shellCommentLine("-----"));
    lines.push(shellCommentLine(`${batch.id}: ${batch.priority} ${batch.source} -> ${batch.owner}`));
    lines.push(shellCommentLine(`Pending items: ${batch.pendingItems}`));
    if ((batch.expectedArtifacts || []).length > 0) {
      lines.push(shellCommentLine(`Expected artifacts: ${(batch.expectedArtifacts || []).join("; ")}`));
    }
    if ((batch.envKeys || []).length > 0) {
      lines.push(shellCommentLine(`Env keys: ${(batch.envKeys || []).join("; ")}`));
      const envGroupSpecs = envCheckSpecs(batch.envKeys || []);
      lines.push(`  print_missing_env_groups ${shellSingleQuoted(batch.id)} ${envGroupSpecs.map(shellSingleQuoted).join(" ")}`);
    }
    if ((batch.exitCriteria || []).length > 0) {
      lines.push(shellCommentLine("Exit criteria:"));
      for (const criterion of batch.exitCriteria || []) {
        lines.push(shellCommentLine(`- ${criterion}`));
      }
    }
    for (const command of batch.commands || []) {
      lines.push(`run_command ${shellSingleQuoted(batch.id)} ${shellSingleQuoted(batch.owner)} ${shellSingleQuoted(batch.priority)} ${shellSingleQuoted(command)}`);
    }
    lines.push("fi");
    lines.push("");
  }
  lines.push("if [[ -n \"${DDD_RELEASE_BATCH}\" && \"${DDD_RELEASE_BATCH_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No ready release batch matched DDD_RELEASE_BATCH=${DDD_RELEASE_BATCH}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("if [[ -n \"${DDD_RELEASE_OWNER}\" && \"${DDD_RELEASE_BATCH_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No ready release batch matched DDD_RELEASE_OWNER=${DDD_RELEASE_OWNER}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("if [[ -n \"${DDD_RELEASE_PRIORITY}\" && \"${DDD_RELEASE_BATCH_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No ready release batch matched DDD_RELEASE_PRIORITY=${DDD_RELEASE_PRIORITY}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("");
  lines.push(shellCommentLine("After these commands refresh artifacts, rerun:"));
  lines.push("run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts/ddd-release-evidence-gate.mjs'");
  lines.push("run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts/ddd-release-readiness-summary.mjs'");
  lines.push("run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'");
  lines.push("if [[ \"${DDD_RELEASE_COMMAND_FAILURES}\" != \"0\" ]]; then");
  lines.push("  echo \"[ddd-release-execution][completed-with-failures] commandFailures=${DDD_RELEASE_COMMAND_FAILURES}\" >&2");
  lines.push("  finalize_release_execution_report 1");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("finalize_release_execution_report 0");
  return `${lines.join("\n")}\n`;
}

function releaseNextActionCommands(summary) {
  const artifact = releaseNextActionQueueArtifact(summary);
  const runNowItems = (artifact.items || []).filter((item) => item.queueStatus === "RUN_NOW");
  const lines = [
    "#!/usr/bin/env bash",
    "set -euo pipefail",
    "",
    shellCommentLine("Lumira DDD release next-action commands."),
    shellCommentLine(`Generated at: ${summary.generatedAt}`),
    shellCommentLine(`Status: ${summary.status}`),
    shellCommentLine(`Release gate blockers: ${summary.gate?.blockers ?? 0}`),
    shellCommentLine("Default mode lists RUN_NOW items. Set DDD_RELEASE_NEXT_ACTION_EXECUTE=1 to execute commands."),
    shellCommentLine("Use DDD_RELEASE_NEXT_ACTION_ORDER or DDD_RELEASE_NEXT_ACTION_OWNER to narrow execution."),
    ...releaseRepoRootPreambleLines(),
    "",
    "DDD_RELEASE_NEXT_ACTION_ORDER=\"${DDD_RELEASE_NEXT_ACTION_ORDER:-}\"",
    "DDD_RELEASE_NEXT_ACTION_OWNER=\"${DDD_RELEASE_NEXT_ACTION_OWNER:-}\"",
    "DDD_RELEASE_NEXT_ACTION_LIST=\"${DDD_RELEASE_NEXT_ACTION_LIST:-}\"",
    "DDD_RELEASE_NEXT_ACTION_DETAIL=\"${DDD_RELEASE_NEXT_ACTION_DETAIL:-}\"",
    "DDD_RELEASE_NEXT_ACTION_CHECK_ENV=\"${DDD_RELEASE_NEXT_ACTION_CHECK_ENV:-}\"",
    "DDD_RELEASE_NEXT_ACTION_EXECUTE=\"${DDD_RELEASE_NEXT_ACTION_EXECUTE:-}\"",
    "DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=\"${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR:-}\"",
    "DDD_RELEASE_NEXT_ACTION_REPORT=\"${DDD_RELEASE_NEXT_ACTION_REPORT:-artifacts/ddd/release/release-next-action-run-report.json}\"",
    "DDD_RELEASE_NEXT_ACTION_REPORT_TMP=\"${DDD_RELEASE_NEXT_ACTION_REPORT}.jsonl.$$\"",
    "DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED=0",
    "DDD_RELEASE_NEXT_ACTION_MATCHED=0",
    "DDD_RELEASE_NEXT_ACTION_ENV_LOADED=0",
    "DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES=0",
    ...safeReleaseEnvLoaderLines(),
    "if [[ \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" == \"true\" || \"${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}\" == \"true\" ]]; then",
    "  if [[ -z \"${DDD_RELEASE_ENV_FILE:-}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE is required when executing or checking release next-action env.\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ ! -f \"${DDD_RELEASE_ENV_FILE}\" ]]; then",
    "    echo \"DDD_RELEASE_ENV_FILE does not exist: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  if [[ \"${DDD_RELEASE_ENV_FILE}\" == *\"release-env-missing.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-closure-wave-env.template.env\" || \"${DDD_RELEASE_ENV_FILE}\" == *\"release-final-owner-queue-env.template.env\" ]]; then",
    "    echo \"Template env files are worksheets, not release evidence: ${DDD_RELEASE_ENV_FILE}\" >&2",
    "    exit 1",
    "  fi",
    "  DDD_RELEASE_ENV_FILE_MODE=$(stat -c '%a' \"${DDD_RELEASE_ENV_FILE}\" 2>/dev/null || node -e \"const fs=require('node:fs'); const mode=fs.statSync(process.argv[1]).mode & 0o777; console.log(mode.toString(8).padStart(3, '0'));\" \"${DDD_RELEASE_ENV_FILE}\")",
    "  if (( 8#${DDD_RELEASE_ENV_FILE_MODE} & 077 )); then",
    "    echo \"Release env file permissions are too broad: ${DDD_RELEASE_ENV_FILE} mode=${DDD_RELEASE_ENV_FILE_MODE}; use chmod 600.\" >&2",
    "    exit 1",
    "  fi",
    "  safe_load_release_env_file",
    "  DDD_RELEASE_NEXT_ACTION_ENV_LOADED=1",
    "fi",
    "check_next_action_env() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  shift 2",
    "  local missing=0",
    "  local key",
    "  if [[ \"$#\" -eq 0 ]]; then",
    "    echo \"[ddd-release-next-action][env-ok] order=${order} owner=${owner} requiredEnv=none\"",
    "    return 0",
    "  fi",
    "  for key in \"$@\"; do",
    "    if [[ -z \"${!key:-}\" ]]; then",
    "      echo \"[ddd-release-next-action][env-missing] order=${order} owner=${owner} key=${key}\" >&2",
    "      missing=1",
    "    fi",
    "  done",
    "  if [[ \"${missing}\" == \"0\" ]]; then",
    "    echo \"[ddd-release-next-action][env-ok] order=${order} owner=${owner}\"",
    "  fi",
    "  return \"${missing}\"",
    "}",
    "append_next_action_report_entry() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  local receipt_status=\"$3\"",
    "  local command=\"$4\"",
    "  local status=\"$5\"",
    "  local duration_ms=\"$6\"",
    "  if [[ \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"1\" && \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"true\" ]]; then return 0; fi",
    "  node --input-type=module -e 'import fs from \"node:fs\"; const [file, order, owner, receiptStatus, command, status, durationMs] = process.argv.slice(1); fs.appendFileSync(file, `${JSON.stringify({ order: Number(order), owner, receiptStatus, command, status: Number(status), durationMs: Number(durationMs), finishedAt: new Date().toISOString() })}\\n`);' \"${DDD_RELEASE_NEXT_ACTION_REPORT_TMP}\" \"${order}\" \"${owner}\" \"${receipt_status}\" \"${command}\" \"${status}\" \"${duration_ms}\"",
    "}",
    "finalize_next_action_report() {",
    "  local exit_code=\"$1\"",
    "  if [[ \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"1\" && \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"true\" ]]; then return 0; fi",
    "  if [[ \"${DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED}\" == \"1\" ]]; then return \"${exit_code}\"; fi",
    "  DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED=1",
    "  mkdir -p \"$(dirname \"${DDD_RELEASE_NEXT_ACTION_REPORT}\")\"",
    "  node --input-type=module -e 'import fs from \"node:fs\"; const [tmp, out, exitCode, ownerFilter, orderFilter] = process.argv.slice(1); const entries = fs.existsSync(tmp) ? fs.readFileSync(tmp, \"utf8\").split(\"\\n\").filter(Boolean).map((line) => JSON.parse(line)) : []; const exit = Number(exitCode); const failedEntries = entries.filter((entry) => Number(entry.status) !== 0).length; const summary = { totalEntries: entries.length, succeededEntries: entries.length - failedEntries, failedEntries }; fs.writeFileSync(out, `${JSON.stringify({ generatedAt: new Date().toISOString(), reportStatus: exit === 0 ? \"PASS\" : \"FAIL\", exitCode: exit, ownerFilter: ownerFilter || null, orderFilter: orderFilter || null, summary, entries }, null, 2)}\\n`); if (fs.existsSync(tmp)) fs.rmSync(tmp);' \"${DDD_RELEASE_NEXT_ACTION_REPORT_TMP}\" \"${DDD_RELEASE_NEXT_ACTION_REPORT}\" \"${exit_code}\" \"${DDD_RELEASE_NEXT_ACTION_OWNER}\" \"${DDD_RELEASE_NEXT_ACTION_ORDER}\"",
    "  if ! DDD_RELEASE_NEXT_ACTION_REPORT=\"${DDD_RELEASE_NEXT_ACTION_REPORT}\" node scripts/ddd-release-next-action-run-report-contract.mjs; then",
    "    echo \"[ddd-release-next-action][report-contract] failed\" >&2",
    "    return 1",
    "  fi",
    "  echo \"[ddd-release-next-action][report] ${DDD_RELEASE_NEXT_ACTION_REPORT}\"",
    "  return \"${exit_code}\"",
    "}",
    "trap 'status=$?; finalize_next_action_report \"${status}\"; exit \"${status}\"' EXIT",
    "run_next_action_command() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  local receipt_status=\"$3\"",
    "  local command=\"$4\"",
    "  if [[ \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"1\" && \"${DDD_RELEASE_NEXT_ACTION_EXECUTE}\" != \"true\" ]]; then",
    "    echo \"[ddd-release-next-action][dry-run] ${command}\"",
    "    return 0",
    "  fi",
    "  local started_ms",
    "  started_ms=$(node -e 'console.log(Date.now())')",
    "  set +e",
    "  bash -lc \"${command}\"",
    "  local status=$?",
    "  set -e",
    "  local finished_ms",
    "  finished_ms=$(node -e 'console.log(Date.now())')",
    "  append_next_action_report_entry \"${order}\" \"${owner}\" \"${receipt_status}\" \"${command}\" \"${status}\" \"$((finished_ms - started_ms))\"",
    "  if [[ \"${status}\" != \"0\" ]]; then",
    "    echo \"[ddd-release-next-action][command-failed] status=${status} command=${command}\" >&2",
    "    DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES=$((DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES + 1))",
    "    if [[ \"${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}\" == \"true\" ]]; then",
    "      echo \"[ddd-release-next-action][command-failed] continuing because DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}\" >&2",
    "      return 0",
    "    fi",
    "    return \"${status}\"",
    "  fi",
    "  return 0",
    "}",
    "maybe_run_next_action() {",
    "  local order=\"$1\"",
    "  local owner=\"$2\"",
    "  local receipt_status=\"$3\"",
    "  local next_action=\"$4\"",
    "  if [[ -n \"${DDD_RELEASE_NEXT_ACTION_ORDER}\" && \"${DDD_RELEASE_NEXT_ACTION_ORDER}\" != \"${order}\" ]]; then",
    "    return 0",
    "  fi",
    "  if [[ -n \"${DDD_RELEASE_NEXT_ACTION_OWNER}\" && \"${DDD_RELEASE_NEXT_ACTION_OWNER}\" != \"${owner}\" ]]; then",
    "    return 0",
    "  fi",
    "  DDD_RELEASE_NEXT_ACTION_MATCHED=1",
    "  echo \"[ddd-release-next-action] order=${order} owner=${owner} receiptStatus=${receipt_status}\"",
    "  echo \"[ddd-release-next-action] next=${next_action}\"",
    "}",
    "",
  ];
  if (runNowItems.length === 0) {
    lines.push(shellCommentLine("No RUN_NOW next actions."));
    return `${lines.join("\n")}\n`;
  }
  lines.push("if [[ \"${DDD_RELEASE_NEXT_ACTION_LIST}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_LIST}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=0");
  lines.push("  echo \"RUN_NOW release next actions:\"");
  for (const item of runNowItems) {
    lines.push(`  if [[ ( -z "\${DDD_RELEASE_NEXT_ACTION_ORDER}" || "\${DDD_RELEASE_NEXT_ACTION_ORDER}" == ${shellSingleQuoted(item.order)} ) && ( -z "\${DDD_RELEASE_NEXT_ACTION_OWNER}" || "\${DDD_RELEASE_NEXT_ACTION_OWNER}" == ${shellSingleQuoted(item.owner)} ) ]]; then`);
    lines.push(`    echo ${shellSingleQuoted(`${item.order} owner=${item.owner} receiptStatus=${item.receiptStatus} next=${item.nextAction}`)}`);
    lines.push("    DDD_RELEASE_NEXT_ACTION_LIST_MATCHED=1");
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_NEXT_ACTION_LIST_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_RELEASE_NEXT_ACTION_DETAIL}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_DETAIL}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=0");
  for (const item of runNowItems) {
    lines.push(`  if [[ ( -z "\${DDD_RELEASE_NEXT_ACTION_ORDER}" || "\${DDD_RELEASE_NEXT_ACTION_ORDER}" == ${shellSingleQuoted(item.order)} ) && ( -z "\${DDD_RELEASE_NEXT_ACTION_OWNER}" || "\${DDD_RELEASE_NEXT_ACTION_OWNER}" == ${shellSingleQuoted(item.owner)} ) ]]; then`);
    lines.push("    DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED=1");
    lines.push(`    echo ${shellSingleQuoted(`order=${item.order}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`owner=${item.owner}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`receiptStatus=${item.receiptStatus}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`next=${item.nextAction}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`reason=${item.reason}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`readyBatches=${(item.readyBatchIds || []).join(";") || "none"}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`blockedBatches=${(item.blockedBatchIds || []).join(";") || "none"}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`missingArtifacts=${(item.missingArtifacts || []).join(";") || "none"}`)}`);
    lines.push(`    echo ${shellSingleQuoted(`envKeys=${(item.envKeys || []).join(";") || "none"}`)}`);
    lines.push("    echo \"commands:\"");
    for (const command of item.executableCommands || []) {
      lines.push(`    echo ${shellSingleQuoted(`- ${command}`)}`);
    }
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_NEXT_ACTION_DETAIL_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  lines.push("if [[ \"${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}\" == \"1\" || \"${DDD_RELEASE_NEXT_ACTION_CHECK_ENV}\" == \"true\" ]]; then");
  lines.push("  DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=0");
  lines.push("  DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=0");
  for (const item of runNowItems) {
    lines.push(`  if [[ ( -z "\${DDD_RELEASE_NEXT_ACTION_ORDER}" || "\${DDD_RELEASE_NEXT_ACTION_ORDER}" == ${shellSingleQuoted(item.order)} ) && ( -z "\${DDD_RELEASE_NEXT_ACTION_OWNER}" || "\${DDD_RELEASE_NEXT_ACTION_OWNER}" == ${shellSingleQuoted(item.owner)} ) ]]; then`);
    lines.push("    DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED=1");
    const envKeys = sortedUniqueStrings(item.envKeys || []);
    if (envKeys.length > 0) {
      lines.push(`    check_next_action_env ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${envKeys.map(shellSingleQuoted).join(" ")} || DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=1`);
    } else {
      lines.push(`    check_next_action_env ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} || DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED=1`);
    }
    lines.push("  fi");
  }
  lines.push("  if [[ \"${DDD_RELEASE_NEXT_ACTION_ENV_CHECK_MATCHED}\" != \"1\" ]]; then");
  lines.push("    echo \"No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}\" >&2");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  if [[ \"${DDD_RELEASE_NEXT_ACTION_ENV_CHECK_FAILED}\" != \"0\" ]]; then");
  lines.push("    exit 1");
  lines.push("  fi");
  lines.push("  exit 0");
  lines.push("fi");
  lines.push("");
  for (const item of runNowItems) {
    lines.push(`maybe_run_next_action ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.receiptStatus)} ${shellSingleQuoted(item.nextAction)}`);
    lines.push(`if [[ "\${DDD_RELEASE_NEXT_ACTION_MATCHED}" == "1" && ( -z "\${DDD_RELEASE_NEXT_ACTION_ORDER}" || "\${DDD_RELEASE_NEXT_ACTION_ORDER}" == ${shellSingleQuoted(item.order)} ) && ( -z "\${DDD_RELEASE_NEXT_ACTION_OWNER}" || "\${DDD_RELEASE_NEXT_ACTION_OWNER}" == ${shellSingleQuoted(item.owner)} ) ]]; then`);
    lines.push(shellCommentLine("-----"));
    lines.push(shellCommentLine(`Reason: ${item.reason}`));
    if ((item.missingArtifacts || []).length > 0) {
      lines.push(shellCommentLine(`Missing artifacts: ${(item.missingArtifacts || []).join("; ")}`));
    }
    for (const command of item.executableCommands || []) {
      lines.push(`  run_next_action_command ${shellSingleQuoted(item.order)} ${shellSingleQuoted(item.owner)} ${shellSingleQuoted(item.receiptStatus)} ${shellSingleQuoted(command)}`);
    }
    lines.push("fi");
    lines.push("");
  }
  lines.push("if [[ \"${DDD_RELEASE_NEXT_ACTION_MATCHED}\" != \"1\" ]]; then");
  lines.push("  echo \"No RUN_NOW next action matched DDD_RELEASE_NEXT_ACTION_ORDER=${DDD_RELEASE_NEXT_ACTION_ORDER} DDD_RELEASE_NEXT_ACTION_OWNER=${DDD_RELEASE_NEXT_ACTION_OWNER}\" >&2");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("");
  lines.push(shellCommentLine("After next-action commands refresh artifacts, rerun:"));
  lines.push("run_next_action_command '0' 'release-next-action' 'RERUN' 'node scripts/ddd-release-evidence-gate.mjs'");
  lines.push("run_next_action_command '0' 'release-next-action' 'RERUN' 'node scripts/ddd-release-readiness-summary.mjs'");
  lines.push("run_next_action_command '0' 'release-next-action' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'");
  lines.push("if [[ \"${DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES}\" != \"0\" ]]; then");
  lines.push("  echo \"[ddd-release-next-action][completed-with-failures] commandFailures=${DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES}\" >&2");
  lines.push("  finalize_next_action_report 1");
  lines.push("  exit 1");
  lines.push("fi");
  lines.push("finalize_next_action_report 0");
  return `${lines.join("\n")}\n`;
}

function ownerActionRollupMarkdown(summary) {
  const artifact = ownerActionRollupArtifact(summary);
  const lines = [
    "# DDD Owner Action Rollup",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    "",
  ];
  const ownerRollup = artifact.owners || {};
  if (Object.keys(ownerRollup).length === 0) {
    lines.push("- None");
    return `${lines.join("\n")}\n`;
  }
  for (const [owner, plan] of Object.entries(ownerRollup)) {
    const sources = Object.entries(plan.sources || {}).map(([source, count]) => `${source}=${count}`).join(", ");
    lines.push(`## ${owner}`);
    lines.push("");
    lines.push(`- Pending items: ${plan.pendingItems ?? 0}`);
    lines.push(`- Collapsed duplicates: ${plan.collapsedItems ?? 0}`);
    lines.push(`- Sources: ${sources || "none"}`);
    appendMarkdownEnvKeys(lines, plan.envKeys);
    lines.push("");
    for (const item of plan.items || []) {
      lines.push(`- [${item.source}] ${item.id}`);
      lines.push(`  - Reason: ${item.reason || "missing"}`);
      appendMarkdownEnvKeys(lines, item.envKeys, { indent: "  " });
      lines.push(`  - Action: ${item.action || "missing"}`);
    }
    for (const item of plan.collapsed || []) {
      lines.push(`- [collapsed:${item.source}] ${item.id}`);
      lines.push(`  - Covered by: ${item.coveredBy?.source || "unknown"}:${item.coveredBy?.id || "unknown"}`);
      lines.push(`  - Reason: ${item.reason || "missing"}`);
      appendMarkdownEnvKeys(lines, item.envKeys, { indent: "  " });
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function appendMarkdownEnvKeys(lines, envKeys, { indent = "", label = "Env keys", inlineLimit = 8, chunkSize = 4 } = {}) {
  const keys = [...new Set((envKeys || []).filter(Boolean))].sort();
  if (keys.length === 0) {
    lines.push(`${indent}- ${label}: none`);
    return;
  }
  if (keys.length <= inlineLimit) {
    lines.push(`${indent}- ${label}: ${keys.join(", ")}`);
    return;
  }
  lines.push(`${indent}- ${label}: ${keys.length} keys`);
  for (let index = 0; index < keys.length; index += chunkSize) {
    lines.push(`${indent}  - ${keys.slice(index, index + chunkSize).join(", ")}`);
  }
}

function appendMarkdownEnvCheckGroups(lines, groups, { indent = "", label = "Env check groups", inlineLimit = 5 } = {}) {
  const specs = (groups || []).map((group) => group.spec || `${group.label}=${(group.keys || []).join("|")}`).filter(Boolean);
  if (specs.length === 0) {
    lines.push(`${indent}- ${label}: none`);
    return;
  }
  if (specs.length <= inlineLimit) {
    lines.push(`${indent}- ${label}:`);
    for (const spec of specs) {
      lines.push(`${indent}  - \`${spec}\``);
    }
    return;
  }
  lines.push(`${indent}- ${label}: ${specs.length} groups`);
  for (const spec of specs) {
    lines.push(`${indent}  - \`${spec}\``);
  }
}

function markdownEnvKeysSuffix(envKeys, { label = "envKeys", inlineLimit = 8 } = {}) {
  const keys = [...new Set((envKeys || []).filter(Boolean))].sort();
  if (keys.length === 0) {
    return `; ${label}=none`;
  }
  if (keys.length <= inlineLimit) {
    return `; ${label}=${keys.join(",")}`;
  }
  return `; ${label}=${keys.length} keys`;
}

function appendMarkdownEnvKeyDetails(lines, envKeys, { indent = "  ", label = "envKeys", inlineLimit = 8, chunkSize = 4 } = {}) {
  const keys = [...new Set((envKeys || []).filter(Boolean))].sort();
  if (keys.length <= inlineLimit) {
    return;
  }
  lines.push(`${indent}- ${label}: ${keys.length} keys`);
  for (let index = 0; index < keys.length; index += chunkSize) {
    lines.push(`${indent}  - ${keys.slice(index, index + chunkSize).join(",")}`);
  }
}

function releaseActionPriorityMarkdown(summary) {
  const artifact = releaseActionPriorityArtifact(summary);
  const lines = [
    "# DDD Release Action Priority",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    `Total pending items: ${artifact.totalPendingItems}`,
    "",
    "## Policy",
    "",
    `- Source order: ${prioritySourceOrder.join(", ")}`,
    "- P0: release-evidence prerequisites and production-equivalent baseline gates",
    "- P1: runtime/business acceptance gates",
    "- P2: database plan recertification",
    "- P3: final strict orchestrator rerun after prerequisite evidence is ready",
    "",
    "## Counts",
    "",
  ];
  for (const [priority, count] of Object.entries(artifact.byPriority)) {
    lines.push(`- ${priority}: ${count}`);
  }
  lines.push("", "## Actions", "");
  if ((artifact.items || []).length === 0) {
    lines.push("- None");
    return `${lines.join("\n")}\n`;
  }
  for (const item of artifact.items || []) {
    lines.push(`- [${item.priority}] [${item.source}] ${item.owner}: ${item.id}`);
    lines.push(`  - Check: ${item.check || "missing"}`);
    lines.push(`  - Reason: ${item.reason || "missing"}`);
    lines.push(`  - Detail: ${item.detail || "missing"}`);
    lines.push(`  - Structured: ${item.structured === true}`);
    appendMarkdownEnvKeys(lines, item.envKeys, { indent: "  " });
    lines.push(`  - Action: ${item.action || "missing"}`);
  }
  return `${lines.join("\n")}\n`;
}

function releaseBlockerMapMarkdown(summary) {
  const artifact = releaseBlockerMapArtifact(summary);
  const lines = [
    "# DDD Release Blocker Map",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    `Category count: ${artifact.categoryCount}`,
    `Owner count: ${artifact.ownerCount}`,
    `Total blockers: ${artifact.totalBlockers}`,
    "",
    "## Owners",
    "",
  ];
  if (artifact.owners.length === 0) {
    lines.push("- None");
  } else {
    for (const owner of artifact.owners) {
      lines.push(`### ${owner.owner}`);
      lines.push("");
      lines.push(`- Blockers: ${owner.blockerCount}`);
      lines.push(`- Categories: ${Object.entries(owner.categories).map(([category, count]) => `${category}=${count}`).join(", ") || "none"}`);
      lines.push(`- Ready batches: ${owner.readyBatchIds.length > 0 ? owner.readyBatchIds.join(", ") : "none"}`);
      lines.push(`- Blocked batches: ${owner.blockedBatchIds.length > 0 ? owner.blockedBatchIds.join(", ") : "none"}`);
      if (owner.commands.length > 0) {
        lines.push("- Commands:");
        for (const command of owner.commands) {
          lines.push(`  - \`${command}\``);
        }
      }
      if (owner.expectedArtifacts.length > 0) {
        lines.push("- Expected artifacts:");
        for (const artifactPath of owner.expectedArtifacts) {
          lines.push(`  - \`${artifactPath}\``);
        }
      }
    lines.push("- Sample blockers:");
    for (const blocker of owner.blockers.slice(0, 5)) {
      lines.push(`  - [${blocker.category}] ${blocker.check || "unknown"}: ${blocker.detail || blocker.blocker}`);
    }
      lines.push("");
    }
  }
  lines.push(
    "## Categories",
    "",
  );
  if (artifact.categories.length === 0) {
    lines.push("- None");
    return `${lines.join("\n")}\n`;
  }
  for (const category of artifact.categories) {
    lines.push(`### ${category.category}`);
    lines.push("");
    lines.push(`- Blockers: ${category.blockerCount}`);
    lines.push(`- Owners: ${Object.entries(category.owners).map(([owner, count]) => `${owner}=${count}`).join(", ") || "none"}`);
    lines.push(`- Ready batches: ${category.readyBatchIds.length > 0 ? category.readyBatchIds.join(", ") : "none"}`);
    lines.push(`- Blocked batches: ${category.blockedBatchIds.length > 0 ? category.blockedBatchIds.join(", ") : "none"}`);
    if (category.commands.length > 0) {
      lines.push("- Commands:");
      for (const command of category.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    if (category.expectedArtifacts.length > 0) {
      lines.push("- Expected artifacts:");
      for (const artifactPath of category.expectedArtifacts) {
        lines.push(`  - \`${artifactPath}\``);
      }
    }
    lines.push("- Sample blockers:");
    for (const blocker of category.blockers.slice(0, 5)) {
      lines.push(`  - [${blocker.owner}] ${blocker.check || "unknown"}: ${blocker.detail || blocker.blocker}`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseActionBatchesMarkdown(summary) {
  const artifact = releaseActionBatchesArtifact(summary);
  const lines = [
    "# DDD Release Action Batches",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    `Batch count: ${artifact.batchCount}`,
    `Total pending items: ${artifact.totalPendingItems}`,
    "",
    "## Execution Notes",
    "",
    "- Batches are ordered by release priority, source order, and owner.",
    "- Batch `id`, `dependsOn`, and `canRunImmediately` define the machine-readable execution graph.",
    "- P0 batches can run immediately; P1/P2/P3 batches should wait until their dependencies meet exit criteria and the release gate is rerun.",
    "- Commands are hints extracted from action text; environment evidence still has to be real and production-equivalent.",
    "- The current release gate remains authoritative after every batch; strict mode is required for final release approval.",
    "",
    "## Batches",
    "",
  ];
  if ((artifact.batches || []).length === 0) {
    lines.push("- None");
    return `${lines.join("\n")}\n`;
  }
  for (const batch of artifact.batches || []) {
    lines.push(`### ${batch.order}. ${batch.priority} ${batch.source} -> ${batch.owner}`);
    lines.push("");
    lines.push(`- Batch id: ${batch.id || "missing"}`);
    lines.push(`- Depends on: ${(batch.dependsOn || []).length > 0 ? batch.dependsOn.join(", ") : "none"}`);
    lines.push(`- Can run immediately: ${batch.canRunImmediately === true}`);
    lines.push(`- Pending items: ${batch.pendingItems}`);
    appendMarkdownEnvKeys(lines, batch.envKeys);
    appendMarkdownEnvCheckGroups(lines, batch.envCheckGroups);
    if ((batch.commands || []).length === 0) {
      lines.push("- Commands: none");
    } else {
      lines.push("- Commands:");
      for (const command of batch.commands) {
        lines.push(`  - \`${command}\``);
      }
    }
    if ((batch.expectedArtifacts || []).length > 0) {
      lines.push("- Expected artifacts:");
      for (const artifact of batch.expectedArtifacts) {
        lines.push(`  - \`${artifact}\``);
      }
    }
    if ((batch.exitCriteria || []).length > 0) {
      lines.push("- Exit criteria:");
      for (const criterion of batch.exitCriteria) {
        lines.push(`  - ${criterion}`);
      }
    }
    lines.push("");
    for (const item of batch.items || []) {
      lines.push(`- ${item.id}: ${item.reason || "missing reason"}`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function sourceActionRollupMarkdown(summary) {
  const artifact = sourceActionRollupArtifact(summary);
  const lines = [
    "# DDD Source Action Rollup",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate?.blockers ?? 0}`,
    "",
  ];
  const sourceRollup = artifact.sources || {};
  if (Object.keys(sourceRollup).length === 0) {
    lines.push("- None");
    return `${lines.join("\n")}\n`;
  }
  for (const [source, plan] of Object.entries(sourceRollup)) {
    const owners = Object.entries(plan.owners || {}).map(([owner, count]) => `${owner}=${count}`).join(", ");
    lines.push(`## ${source}`);
    lines.push("");
    lines.push(`- Pending items: ${plan.pendingItems ?? 0}`);
    lines.push(`- Owners: ${owners || "none"}`);
    appendMarkdownEnvKeys(lines, plan.envKeys);
    lines.push("");
    for (const item of plan.items || []) {
      lines.push(`- [${item.owner}] ${item.id}`);
      lines.push(`  - Reason: ${item.reason || "missing"}`);
      appendMarkdownEnvKeys(lines, item.envKeys, { indent: "  " });
      lines.push(`  - Action: ${item.action || "missing"}`);
    }
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

function releaseConfigActionPlan(details = []) {
  const byOwner = new Map();
  const actionFor = (detail, envKeys) => {
    const keys = envKeys.length > 0 ? envKeys.join(" or ") : "the required runtime key";
    const check = detail?.check || "release configuration check";
    return `Set ${keys} for ${check} in DDD_RELEASE_ENV_FILE or the production-equivalent runtime environment, then rerun \`node scripts/ddd-release-config-evidence.mjs\`.`;
  };
  for (const detail of details || []) {
    const owner = detail?.owner || "release-infra";
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        missingChecks: 0,
        envKeys: [],
        items: [],
      });
    }
    const ownerPlan = byOwner.get(owner);
    ownerPlan.missingChecks += 1;
    const envKeys = Array.isArray(detail?.envKeys) ? detail.envKeys.filter(Boolean) : [];
    ownerPlan.items.push({
      group: detail?.group || "unknown",
      check: detail?.check || "unknown",
      reason: detail?.reason || detail?.blocker || "missing reason",
      envKeys,
      action: actionFor(detail, envKeys),
    });
    for (const envKey of envKeys) {
      if (!ownerPlan.envKeys.includes(envKey)) {
        ownerPlan.envKeys.push(envKey);
      }
    }
  }
  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          items: plan.items.sort((left, right) => `${left.group}.${left.check}`.localeCompare(`${right.group}.${right.check}`)),
        },
      ]),
  );
}

function releaseEnvLintActionPlan(diagnostics) {
  if (!diagnostics) {
    return null;
  }
  const generatedMissingTemplate = diagnostics.generatedMissingTemplate === true || diagnostics.inputKind === "generated-missing-template";
  const envKeys = generatedMissingTemplate && Array.isArray(diagnostics.missingEnv?.uniqueEnvKeys)
    ? (Array.isArray(diagnostics.canonicalMissingEnvKeys) && diagnostics.canonicalMissingEnvKeys.length > 0
      ? diagnostics.canonicalMissingEnvKeys.filter(Boolean)
      : diagnostics.missingEnv.uniqueEnvKeys.filter(Boolean).map(canonicalReleaseEnvTemplateKey))
    : Array.isArray(diagnostics.unresolvedTemplateKeys)
    ? (Array.isArray(diagnostics.canonicalUnresolvedTemplateKeys) && diagnostics.canonicalUnresolvedTemplateKeys.length > 0
      ? diagnostics.canonicalUnresolvedTemplateKeys.filter(Boolean)
      : diagnostics.unresolvedTemplateKeys.filter(Boolean).map(canonicalReleaseEnvTemplateKey))
    : [];
  const releaseConfigEnvKeys = Array.isArray(diagnostics.canonicalReleaseConfigBlockerKeys)
    ? diagnostics.canonicalReleaseConfigBlockerKeys.filter(Boolean)
    : [];
  const combinedEnvKeys = [...new Set([...envKeys, ...releaseConfigEnvKeys])].sort();
  const items = [];
  const envFileSecurity = diagnostics.envFileSecurity || {};
  if (generatedMissingTemplate) {
    return {
      owner: "release-infra",
      pendingItems: 1,
      envKeys: [...new Set(envKeys)].sort(),
      items: [{
        id: "release-env-lint-real-env-file",
        owner: "release-infra",
        reason: "release env lint used generated missing-env template instead of a completed release env file",
        envKeys: [...new Set(envKeys)].sort(),
        action: "Create a secure completed env file from `artifacts/ddd/release/release-env-missing.template.env`, set `DDD_RELEASE_ENV_FILE` to that path, then rerun `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs` and `node scripts/ddd-release-config-evidence.mjs`.",
      }],
    };
  }
  if (envFileSecurity.permissionSafe === false) {
    items.push({
      id: "release-env-lint-permissions",
      owner: "release-infra",
      reason: `envFileMode=${envFileSecurity.modeOctal || "unknown"} requiredMode=${envFileSecurity.requiredMode || "600"}`,
      envKeys: [],
      action: "Restrict `DDD_RELEASE_ENV_FILE` permissions with `chmod 600`, then rerun `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`.",
    });
  }
  if (diagnostics.status !== "PASS") {
    items.push({
      id: "release-env-lint-status",
      owner: "release-infra",
      reason: `status=${diagnostics.status || "missing"} primaryBlockers=${diagnostics.summary?.primaryBlockers ?? diagnostics.summary?.blockers ?? 0}`,
      envKeys: combinedEnvKeys,
      action: "Replace placeholders and invalid values in `DDD_RELEASE_ENV_FILE`, then rerun `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`.",
    });
  }
  if ((diagnostics.summary?.unresolvedTemplateKeys || 0) > 0) {
    items.push({
      id: "release-env-lint-placeholders",
      owner: "release-infra",
      reason: `unresolvedTemplateKeys=${diagnostics.summary.unresolvedTemplateKeys}`,
      envKeys,
      action: "Replace every placeholder-like value (`<placeholder>`, `replace-with-*`, TODO/TBD, example domains) in `DDD_RELEASE_ENV_FILE` before running release evidence.",
    });
  }
  if ((diagnostics.summary?.releaseConfigBlockersAfterPlaceholders || 0) > 0) {
    items.push({
      id: "release-env-lint-config",
      owner: "release-infra",
      reason: `releaseConfigBlockersAfterPlaceholders=${diagnostics.summary.releaseConfigBlockersAfterPlaceholders}`,
      envKeys: releaseConfigEnvKeys.length > 0 ? releaseConfigEnvKeys : combinedEnvKeys,
      action: "Fix values in `DDD_RELEASE_ENV_FILE` so `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs` and `node scripts/ddd-release-config-evidence.mjs` both pass.",
    });
  }
  if (items.length === 0) {
    return {
      owner: "release-infra",
      pendingItems: 0,
      envKeys: [],
      items: [],
    };
  }
  return {
    owner: "release-infra",
    pendingItems: items.length,
    envKeys: [...new Set(items.flatMap((item) => item.envKeys || []))].sort(),
    items,
  };
}

function rollbackActionPlan(contextDiagnostics = []) {
  const byOwner = new Map();
  const rollbackEnvKeys = [
    "DDD_ROLLBACK_DRILL_FILE",
    "DDD_ROLLBACK_DRILL_CHECK_ENV",
    "DDD_ROLLBACK_DRILL_HANDOFF_FILE",
    "DDD_ROLLBACK_DRILL_DEFERRAL_FILE",
    "DDD_ROLLBACK_DRILL_STRICT",
    "DDD_EVIDENCE_ENVIRONMENT",
    "DDD_RELEASE_CANDIDATE",
    "DDD_EVIDENCE_OPERATOR",
  ];
  const reasonFor = (diagnostic = {}) => {
    const context = diagnostic.context || "unknown";
    const status = diagnostic.status || "missing";
    if (diagnostic.ready === true && diagnostic.deferralApplied === true) {
      return `${context} rollback drill is DEFERRED with approved deferral evidence`;
    }
    if (diagnostic.ready === true) {
      return `${context} rollback drill is ready with PASS evidence`;
    }
    if (diagnostic.missingEvidence === true) {
      return `${context} rollback drill requires PASS evidence or approved DEFERRED risk acceptance; status=${status}`;
    }
    if (!diagnostic.evidence) {
      return `${context} rollback drill evidence is missing; status=${status}`;
    }
    return `${context} rollback drill is not ready; status=${status}`;
  };
  const actionFor = (diagnostic = {}) => {
    const baseAction = diagnostic?.action || "Attach context-specific rollback evidence or deferral.";
    const requirements = Array.isArray(diagnostic?.evidenceRequirements)
      ? diagnostic.evidenceRequirements.filter(Boolean)
      : [];
    const requirementText = requirements.length > 0
      ? ` Required evidence: ${requirements.join("; ")}.`
      : "";
    return `${baseAction}${requirementText} If the drill is not safely exercisable, generate a reviewed deferral input with \`node scripts/ddd-rollback-deferral-template.mjs\`, fill real approval evidence, then run \`node scripts/ddd-rollback-drill-evidence.mjs\`.`;
  };
  for (const diagnostic of contextDiagnostics || []) {
    const owner = diagnostic?.owner || "release-owner";
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        pendingContexts: 0,
        readyContexts: 0,
        missingEvidence: 0,
        items: [],
      });
    }
    const ownerPlan = byOwner.get(owner);
    if (diagnostic?.ready === true) {
      ownerPlan.readyContexts += 1;
    } else {
      ownerPlan.pendingContexts += 1;
    }
    if (diagnostic?.missingEvidence === true) {
      ownerPlan.missingEvidence += 1;
    }
    ownerPlan.items.push({
      context: diagnostic?.context || "unknown",
      status: diagnostic?.status || "missing",
      reason: reasonFor(diagnostic),
      action: actionFor(diagnostic),
      envKeys: rollbackEnvKeys,
      evidence: diagnostic?.evidence || null,
      ready: diagnostic?.ready === true,
      missingEvidence: diagnostic?.missingEvidence === true,
      deferralApplied: diagnostic?.deferralApplied === true,
    });
  }
  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          items: plan.items.sort((left, right) => left.context.localeCompare(right.context)),
        },
      ]),
  );
}

function explainActionPlan({ missingRequiredFiles = [], legacyPlanImports = [], issues = [] } = {}) {
  const itemByFile = new Map();
  const explainEnvKeys = [
    "DDD_EXPLAIN_DIR",
    "DDD_EXPLAIN_STRICT",
    "DDD_EXPLAIN_ENVIRONMENT",
    "DDD_RELEASE_CANDIDATE",
    "DDD_EVIDENCE_OPERATOR",
    "MYSQL_CLI",
    "MYSQL_HOST",
    "MYSQL_PORT",
    "MYSQL_USER",
    "MYSQL_PASSWORD",
    "MYSQL_DATABASE",
    "DDD_EXPLAIN_DATABASE",
  ];
  const ensureItem = (file) => {
    const key = file || "unknown";
    if (!itemByFile.has(key)) {
      itemByFile.set(key, {
        file: key,
        reasons: [],
        envKeys: explainEnvKeys,
        command: "Run production-equivalent MySQL EXPLAIN collection with `node scripts/ddd-collect-explain.mjs`, then `DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs`.",
      });
    }
    return itemByFile.get(key);
  };
  for (const file of missingRequiredFiles) {
    ensureItem(file).reasons.push("missing required EXPLAIN artifact");
  }
  for (const file of legacyPlanImports) {
    ensureItem(file).reasons.push("legacyPlanImport=true; strict release requires fresh production-equivalent EXPLAIN");
  }
  for (const issue of issues || []) {
    ensureItem(issue?.file).reasons.push(`[${issue?.scope || "unknown"}] ${issue?.detail || "unknown issue"}`);
  }
  const items = [...itemByFile.values()]
    .map((item) => ({
      ...item,
      reasons: [...new Set(item.reasons)].sort(),
    }))
    .sort((left, right) => left.file.localeCompare(right.file));
  return {
    owner: "database",
    pendingFiles: items.length,
    items,
  };
}

function frontendSmokeActionPlan(artifact, strictGate) {
  if (!artifact) {
    return null;
  }
  const items = [];
  const add = (id, owner, action, envKeys = [], reason = null, extra = {}) => {
    if (items.some((item) => item.id === id)) {
      return;
    }
    items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
  };
  const productionEquivalence = productionEquivalenceDiagnostics(
    artifact,
    strictGate,
    "frontend smoke",
    process.env.DDD_FRONTEND_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
  );
  const baseUrl = artifact.baseUrl || "";
  if (!baseUrl || productionEquivalence?.issues?.length > 0) {
    add(
      "frontend-base-url",
      "frontend",
      "Run frontend smoke against a deployed HTTPS non-local frontend URL.",
      ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL", "DDD_FRONTEND_DEPLOYMENT_EVIDENCE"],
      productionEquivalence?.issues?.join("; ") || "missing frontend baseUrl",
    );
  }
  if (artifact.expectDeployed !== true) {
    add(
      "frontend-deployed-expectation",
      "frontend",
      "Set DDD_FRONTEND_EXPECT_DEPLOYED=true for strict deployed frontend smoke evidence.",
      ["DDD_FRONTEND_EXPECT_DEPLOYED"],
      "strict release requires deployed frontend smoke expectation",
    );
  }
  const playwrightReport = artifact.diagnostics?.playwrightReport || null;
  const missingPlaywrightReport = playwrightReport?.present === false || !playwrightReport;
  if (missingPlaywrightReport) {
    add(
      "frontend-playwright-report",
      "frontend",
      "Run deployed Playwright smoke and convert the JSON report with `node scripts/ddd-frontend-smoke-evidence.mjs`.",
      ["PLAYWRIGHT_BASE_URL", "DDD_FRONTEND_EXPECT_DEPLOYED", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
      playwrightReport?.file ? `missing Playwright JSON report ${portablePath(playwrightReport.file)}` : "missing Playwright JSON report",
    );
  }
  const missingFlows = (artifact.flowCoverage || []).filter((flow) => flow.status === "missing");
  if (missingFlows.length > 0 && !missingPlaywrightReport) {
    add(
      "frontend-flow-coverage",
      "frontend",
      "Cover every required frontend smoke flow in deployed Playwright results, then regenerate frontend-smoke.json.",
      ["PLAYWRIGHT_BASE_URL", "DDD_FRONTEND_EXPECT_DEPLOYED"],
      `missing required flows=${missingFlows.length}`,
      { flows: missingFlows.map((flow) => flow.flow).filter(Boolean).sort() },
    );
  }
  const staticCoverage = artifact.diagnostics?.staticSpecCoverage || null;
  if (staticCoverage?.present === false || (staticCoverage?.missing || 0) > 0) {
    add(
      "frontend-static-spec-coverage",
      "frontend",
      "Update frontend/tests/e2e/app.spec.ts so every required smoke flow has static coverage.",
      [],
      `static smoke spec missing flows=${staticCoverage?.missing ?? "unknown"}`,
    );
  }
  const envKeys = [...new Set(items.flatMap((item) => item.envKeys || []))].sort();
  return {
    owner: "frontend",
    pendingItems: items.length,
    envKeys,
    items: items.sort((left, right) => left.id.localeCompare(right.id)),
  };
}

function aiRuntimeActionPlan(aiRuntime) {
  if (!aiRuntime) {
    return null;
  }
  const items = [];
  const add = (id, owner, action, envKeys = [], reason = null) => {
    if (items.some((item) => item.id === id)) {
      return;
    }
    items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
    });
  };
  const productionIssues = aiRuntime.productionEquivalence?.issues || [];
  if (productionIssues.length > 0 || aiRuntime.localOnly === true || !aiRuntime.baseUrl) {
    add(
      "ai-runtime-base-url",
      "ai",
      "Run AI runtime drill against an HTTPS non-local AI runtime base URL.",
      ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
      productionIssues.join("; ") || "missing production-equivalent AI base URL",
    );
  }
  if (aiRuntime.remoteEvidence?.provider?.remoteConfigured !== true
    || aiRuntime.remoteEvidence?.provider?.status !== "UP") {
    add(
      "ai-provider-runtime",
      "ai",
      "Configure and verify a remote AI provider runtime; strict release must not rely on local fallback.",
      [
        "DDD_AI_EXPECT_PROVIDER_REMOTE",
        "LUMIRA_AI_PROVIDER",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
      ],
      `provider status=${aiRuntime.remoteEvidence?.provider?.status ?? "missing"} remoteConfigured=${aiRuntime.remoteEvidence?.provider?.remoteConfigured === true}`,
    );
  }
  if ((aiRuntime.remoteEvidence?.ownerGateway?.configuredOwnerCount || 0) <= 0
    || aiRuntime.remoteEvidence?.ownerGateway?.status !== "UP") {
    add(
      "ai-owner-gateway",
      "ai",
      "Configure and verify remote AI owner gateways for IAM/File/Platform integrations.",
      [
        "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE",
        "LUMIRA_AI_OWNER_IAM_BASE_URL",
        "LUMIRA_AI_OWNER_FILE_BASE_URL",
        "LUMIRA_AI_OWNER_PLATFORM_BASE_URL",
      ],
      `ownerGateway status=${aiRuntime.remoteEvidence?.ownerGateway?.status ?? "missing"} configuredOwners=${aiRuntime.remoteEvidence?.ownerGateway?.configuredOwnerCount || 0}`,
    );
  }
  for (const detail of aiRuntime.failureDetails || []) {
    add(
      `ai-failure-${detail.category || "unknown"}-${detail.owner || "unknown"}`,
      detail.owner || "ai",
      "Resolve AI runtime drill failure and rerun `node scripts/ddd-ai-runtime-drill.mjs` with strict remote expectations.",
      ["DDD_AI_EXPECT_PROVIDER_REMOTE", "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"],
      detail.message || "AI runtime drill failure",
    );
  }
  return {
    owner: "ai",
    pendingItems: items.length,
    items: items.sort((left, right) => left.id.localeCompare(right.id)),
  };
}

function orchestratorMissingResults(report) {
  if (report?.mode !== "run") {
    return [];
  }
  const results = Array.isArray(report?.results) ? report.results : [];
  return (Array.isArray(report?.selectedSteps) ? report.selectedSteps : [])
    .filter((step) => step?.enabled !== false)
    .filter((step) => !results.some((result) => result.id === step.id))
    .map((step) => step.id);
}

function orchestratorOwnerFor(id = "") {
  if (id.includes("frontend")) {
    return "frontend";
  }
  if (id.includes("ai-")) {
    return "ai";
  }
  if (id.includes("migration")) {
    return "database";
  }
  if (id.includes("docker")) {
    return "release-infra";
  }
  if (id.includes("backend-runtime") || id.includes("release-config") || id.includes("runtime-base-url")) {
    return "release-infra";
  }
  return "release-owner";
}

function orchestratorActionPlan(orchestratorDiagnostics) {
  if (!orchestratorDiagnostics) {
    return {};
  }
  const byOwner = new Map();
  const add = (owner, id, action, envKeys = [], reason = null, extra = {}) => {
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        pendingItems: 0,
        envKeys: [],
        items: [],
      });
    }
    const plan = byOwner.get(owner);
    if (plan.items.some((item) => item.id === id)) {
      return;
    }
    plan.pendingItems += 1;
    plan.items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
    for (const envKey of envKeys || []) {
      if (!plan.envKeys.includes(envKey)) {
        plan.envKeys.push(envKey);
      }
    }
  };

  if (orchestratorDiagnostics.mode !== "run") {
    add(
      "release-owner",
      "orchestrator-run-mode",
      "Run the release evidence orchestrator in strict run mode after preflight blockers are resolved with `node scripts/ddd-release-evidence-orchestrator.mjs`.",
      ["DDD_RELEASE_EVIDENCE_STRICT"],
      `strict release requires run mode report, got ${orchestratorDiagnostics.mode || "missing"}`,
    );
  }
  for (const check of orchestratorDiagnostics.blockerChecks || []) {
    const owner = orchestratorOwnerFor(check.id || "");
    add(
      owner,
      `orchestrator-preflight-${check.id || "unknown"}`,
      "Resolve the orchestrator preflight blocker, then rerun strict release evidence with `node scripts/ddd-release-evidence-orchestrator.mjs`.",
      check.envKeys || [],
      check.detail || "orchestrator preflight blocker",
      { checkId: check.id || null },
    );
  }
  for (const stepId of orchestratorDiagnostics.missingResults || []) {
    add(
      "release-owner",
      `orchestrator-missing-result-${stepId}`,
      "Rerun the strict orchestrator with `node scripts/ddd-release-evidence-orchestrator.mjs` and keep the generated result for every enabled selected step.",
      ["DDD_RELEASE_EVIDENCE_STRICT"],
      `missing orchestrator result for enabled step ${stepId}`,
      { stepId },
    );
  }
  const resultById = new Map((orchestratorDiagnostics.results || []).map((result) => [result.id, result]));
  for (const step of orchestratorDiagnostics.selectedSteps || []) {
    const result = resultById.get(step.id);
    if (orchestratorDiagnostics.mode === "run" && step.enabled !== false && !result) {
      continue;
    }
    if (result?.status === "FAIL" || result?.exitCode > 0) {
      add(
        orchestratorOwnerFor(step.id || ""),
        `orchestrator-step-${step.id}`,
        "Fix the failed orchestrator step and rerun strict release evidence with `node scripts/ddd-release-evidence-orchestrator.mjs`.",
        step.envKeys || [],
        `step ${step.id} failed with status=${result.status || "missing"} exitCode=${result.exitCode ?? "missing"}`,
        { stepId: step.id },
      );
    }
  }

  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          items: plan.items.sort((left, right) => left.id.localeCompare(right.id)),
        },
      ]),
  );
}

function dockerActionPlan(dockerDiagnostics) {
  if (!dockerDiagnostics) {
    return null;
  }
  const items = [];
  const mirrorRetryCommand = dockerDiagnostics.remediation?.nextActions
    ?.find((item) => item.id === "docker-registry-mirror-retry")
    ?.exampleCommand;
  const mirrorRetrySuffix = mirrorRetryCommand
    ? ` Mirror retry example: ${mirrorRetryCommand}.`
    : "";
  const existingImageInspectCommand = dockerDiagnostics.remediation?.nextActions
    ?.find((item) => item.id === "docker-existing-image-inspect")
    ?.exampleCommand || dockerExistingImageInspectExampleCommand;
  const existingImageInspectSuffix = dockerDiagnostics.remediation?.transientRegistryFailure === true
    ? ` If CI already built the release candidate images, use explicit inspect-only evidence instead: ${existingImageInspectCommand}.`
    : "";
  const hasDockerPreflightBlocker = dockerDiagnostics.cliStatus !== 0
    || dockerDiagnostics.daemonStatus !== 0
    || Boolean(dockerDiagnostics.daemonError)
    || (dockerDiagnostics.blockers || []).some((blocker) => /docker (CLI|daemon) is not available/i.test(String(blocker)));
  const add = (id, action, envKeys = [], reason = null, extra = {}) => {
    if (items.some((item) => item.id === id)) {
      return;
    }
    items.push({
      id,
      owner: "release-infra",
      action,
      envKeys,
      reason,
      ...extra,
    });
  };
  if (dockerDiagnostics.daemonStatus !== 0 || dockerDiagnostics.daemonError) {
    add(
      "docker-daemon",
      `Start Docker daemon locally or run \`DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs\` in a CI runner with Docker Buildx available; set \`DDD_DOCKER_*_IMAGE\` values when using a registry mirror.${existingImageInspectSuffix}`,
      dockerBuildEvidenceEnvKeys,
      dockerDiagnostics.daemonError || `docker daemon status=${dockerDiagnostics.daemonStatus ?? "missing"}`,
    );
  }
  for (const blocker of dockerDiagnostics.blockers || []) {
    if (hasDockerPreflightBlocker && /docker (CLI|daemon) is not available/i.test(String(blocker))) {
      continue;
    }
    add(
      `docker-blocker-${items.length + 1}`,
      `Resolve Docker image evidence blocker and rerun \`node scripts/ddd-docker-build-evidence.mjs\`; for Docker Hub/network failures set \`DDD_DOCKER_MAVEN_IMAGE\`, \`DDD_DOCKER_JRE_IMAGE\`, \`DDD_DOCKER_NODE_IMAGE\`, and \`DDD_DOCKER_NGINX_IMAGE\` to trusted registry mirror images.${mirrorRetrySuffix}${existingImageInspectSuffix}`,
      dockerBuildEvidenceEnvKeys,
      blocker,
    );
  }
  for (const image of dockerDiagnostics.images || []) {
    if (image.status === "SKIPPED" && !hasDockerPreflightBlocker) {
      add(
        `docker-image-${image.name || "unknown"}-skipped`,
        "Build and inspect the skipped Docker image in an environment with Docker daemon access by running `DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs`; use `DDD_DOCKER_*_IMAGE` mirror overrides if Docker Hub is unreliable.",
        dockerBuildEvidenceEnvKeys,
        image.skipReason || "image build skipped",
        { image: image.name || null, dockerfile: image.dockerfile || null },
      );
    }
    if (image.status === "FAIL" || image.buildStatus > 0 || image.inspectStatus > 0) {
      add(
        `docker-image-${image.name || "unknown"}-failed`,
        `Fix Docker image build/inspect failure and regenerate image evidence with \`DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs\`; for transient registry failures configure \`DDD_DOCKER_*_IMAGE\` mirror overrides and rerun.${mirrorRetrySuffix}${existingImageInspectSuffix}`,
        dockerBuildEvidenceEnvKeys,
        (image.blockers || []).join("; ") || `image status=${image.status || "missing"}`,
        { image: image.name || null, dockerfile: image.dockerfile || null },
      );
    }
    const staticIssues = image.staticDockerfile?.issues || [];
    if (staticIssues.length > 0 || image.staticDockerfile?.status === "FAIL") {
      add(
        `docker-image-${image.name || "unknown"}-static-dockerfile`,
        "Fix static Dockerfile contract issues before regenerating Docker evidence.",
        [],
        staticIssues.join("; ") || "static Dockerfile contract failed",
        { image: image.name || null, dockerfile: image.dockerfile || null },
      );
    }
    if (image.requireNonRootUser === true && image.inspectStatus === 0 && !image.imageUser) {
      add(
        `docker-image-${image.name || "unknown"}-non-root-user`,
        "Configure the Docker image to run as a non-root user and regenerate inspect evidence.",
        [],
        "required non-root image user is missing",
        { image: image.name || null, dockerfile: image.dockerfile || null },
      );
    }
  }
  return {
    owner: "release-infra",
    pendingItems: items.length,
    envKeys: [...new Set(items.flatMap((item) => item.envKeys || []))].sort(),
    items: items.sort((left, right) => left.id.localeCompare(right.id)),
  };
}

function migrationActionPlan(migrationDiagnostics) {
  if (!migrationDiagnostics) {
    return {};
  }
  const byOwner = new Map();
  const add = (owner, id, action, envKeys = [], reason = null, extra = {}) => {
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        pendingItems: 0,
        envKeys: [],
        items: [],
      });
    }
    const plan = byOwner.get(owner);
    if (plan.items.some((item) => item.id === id)) {
      return;
    }
    plan.pendingItems += 1;
    plan.items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
    for (const envKey of envKeys || []) {
      if (!plan.envKeys.includes(envKey)) {
        plan.envKeys.push(envKey);
      }
    }
  };

  const runtime = migrationDiagnostics.runtime || {};
  if (runtime.freshDatabaseValidated !== true) {
    add(
      "database",
      "migration-fresh-database-drill",
      "Run Flyway against an empty production-equivalent database, archive schema history plus Flyway logs, then regenerate migration evidence with `node scripts/ddd-migration-evidence.mjs`.",
      ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      `freshDatabaseValidated=${runtime.freshDatabaseValidated === true} evidence=${runtime.freshDatabaseEvidence || "missing"}`,
    );
  }
  if (runtime.upgradeDatabaseValidated !== true) {
    add(
      "database",
      "migration-upgrade-database-drill",
      "Run Flyway upgrade against a copy of the previous production schema, archive before/after schema history plus Flyway logs, then regenerate migration evidence with `node scripts/ddd-migration-evidence.mjs`.",
      ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      `upgradeDatabaseValidated=${runtime.upgradeDatabaseValidated === true} evidence=${runtime.upgradeDatabaseEvidence || "missing"}`,
    );
  }
  for (const proof of migrationDiagnostics.runtimeProofs || []) {
    if (proof.validated !== true || !proof.evidence) {
      add(
        proof.owner || "database",
        `migration-proof-${proof.id || "unknown"}`,
        `${proof.action || "Attach production-equivalent migration drill proof and regenerate migration evidence."} Rerun \`node scripts/ddd-migration-evidence.mjs\`.`,
        proof.requiredEnvKeys || [],
        `validated=${proof.validated === true} evidence=${proof.evidence || "missing"}`,
        { proofId: proof.id || null },
      );
    }
  }
  for (const diagnostic of migrationDiagnostics.runtimeDiagnostics || []) {
    if (diagnostic.status !== "PASS") {
      add(
        diagnostic.owner || "database",
        `migration-diagnostic-${diagnostic.id || "unknown"}`,
        `${diagnostic.action || "Resolve migration runtime diagnostic and regenerate migration evidence."} Rerun \`node scripts/ddd-migration-evidence.mjs\`.`,
        diagnostic.envKeys || [],
        `status=${diagnostic.status || "missing"} evidence=${diagnostic.evidence || "missing"}`,
        { diagnosticId: diagnostic.id || null },
      );
    }
  }
  if (migrationDiagnostics.runtimeReady !== true) {
    add(
      "release-owner",
      "migration-runtime-ready",
      "Regenerate migration evidence with `node scripts/ddd-migration-evidence.mjs` after both fresh and upgrade drills pass with concrete evidence links.",
      ["DDD_MIGRATION_ENVIRONMENT", "DDD_MIGRATION_OPERATOR", "DDD_MIGRATION_COMPLETED_AT"],
      "migration runtime evidence is not ready",
    );
  }

  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          items: plan.items.sort((left, right) => left.id.localeCompare(right.id)),
        },
      ]),
  );
}

function countBy(items, key) {
  const counts = {};
  for (const item of items || []) {
    const value = item?.[key] || "unknown";
    counts[value] = (counts[value] || 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort(([left], [right]) => left.localeCompare(right)));
}

function stableCountBy(values) {
  const counts = {};
  for (const value of values) {
    counts[value] = (counts[value] || 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort(([left], [right]) => left.localeCompare(right)));
}

function sortedUniqueStrings(values) {
  return [...new Set((values || []).filter(Boolean).map(String))].sort();
}

function orderedUniqueStrings(values) {
  return [...new Set((values || []).filter(Boolean).map(String))];
}

function sameStringSet(left, right) {
  return JSON.stringify(sortedUniqueStrings(left)) === JSON.stringify(sortedUniqueStrings(right));
}

function flattenGroupedActions(groups) {
  return Object.values(groups || {}).flat();
}

function isIsoDateTime(value) {
  return typeof value === "string" && !Number.isNaN(Date.parse(value));
}

function validateReadinessSummary(summary) {
  const issues = [];
  const assertReleaseEnvFileSafety = (scriptName, scriptText) => {
    if (!scriptText.includes("safe_load_release_env_file")) {
      issues.push(`${scriptName} must safely parse DDD_RELEASE_ENV_FILE instead of sourcing it`);
    }
    if (/^\s*source "\$\{DDD_RELEASE_ENV_FILE\}"/m.test(scriptText)) {
      issues.push(`${scriptName} must not source DDD_RELEASE_ENV_FILE during execution`);
    }
    for (const templateName of [
      "release-env-missing.template.env",
      "release-closure-wave-env.template.env",
      "release-final-owner-queue-env.template.env",
    ]) {
      if (!scriptText.includes(templateName)) {
        issues.push(`${scriptName} must reject ${templateName} as release evidence`);
      }
    }
    if (!scriptText.includes("Template env files are worksheets, not release evidence")) {
      issues.push(`${scriptName} must explain template env rejection`);
    }
    if (!scriptText.includes("DDD_RELEASE_ENV_FILE_MODE=")) {
      issues.push(`${scriptName} must inspect release env file permissions before sourcing`);
    }
    if (!scriptText.includes("Release env file permissions are too broad")) {
      issues.push(`${scriptName} must reject group/other-readable release env files`);
    }
    if (!scriptText.includes("use chmod 600")) {
      issues.push(`${scriptName} must tell operators how to fix release env file permissions`);
    }
  };
  const finalGoNoGoEnforceCommand = "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh";
  const assertFinalGoNoGoClosure = (scriptName, scriptText) => {
    if (!scriptText.includes(finalGoNoGoEnforceCommand)) {
      issues.push(`${scriptName} must force final go/no-go before it can complete a release execution path`);
    }
  };
  const assertReleaseEnvFileSignal = (artifactName, signal) => {
    if (!signal || typeof signal !== "object") {
      issues.push(`${artifactName} must include safetySignals.releaseEnvFile`);
      return;
    }
    for (const field of [
      "ready",
      "envFilePresent",
      "generatedMissingTemplate",
      "securityChecked",
      "permissionSafe",
      "permissionCheckSkipped",
    ]) {
      if (typeof signal[field] !== "boolean") {
        issues.push(`${artifactName}.safetySignals.releaseEnvFile.${field} must be boolean`);
      }
    }
    for (const field of ["status", "inputKind", "requiredMode"]) {
      if (typeof signal[field] !== "string" || signal[field].length === 0) {
        issues.push(`${artifactName}.safetySignals.releaseEnvFile.${field} must be non-empty string`);
      }
    }
    if (signal.requiredMode !== "600") {
      issues.push(`${artifactName}.safetySignals.releaseEnvFile.requiredMode must be 600`);
    }
    if (!Array.isArray(signal.pendingActionIds)) {
      issues.push(`${artifactName}.safetySignals.releaseEnvFile.pendingActionIds must be an array`);
    }
    if (signal.ready === true && (signal.status !== "PASS" || signal.inputKind !== "release-env-file" || signal.envFilePresent !== true || signal.securityChecked !== true || signal.permissionSafe !== true || signal.generatedMissingTemplate === true)) {
      issues.push(`${artifactName}.safetySignals.releaseEnvFile.ready=true must imply PASS release-env-file with checked safe permissions`);
    }
  };
  if (!isIsoDateTime(summary.generatedAt)) {
    issues.push("generatedAt must be an ISO datetime");
  }
  if (!["READY", "ADVISORY", "NOT_READY"].includes(summary.status)) {
    issues.push(`status must be READY, ADVISORY, or NOT_READY, got ${summary.status}`);
  }
  const releaseGateContractIssues = summary.diagnostics?.releaseGate?.contractIssues || [];
  const releaseConfigContractIssues = summary.diagnostics?.releaseConfig?.contractIssues || [];
  const expectedStatus = summary.gate?.present !== true || releaseGateContractIssues.length > 0 || releaseConfigContractIssues.length > 0 || summary.gate?.blockers > 0
    ? "NOT_READY"
    : (summary.gate?.strict === true ? "READY" : "ADVISORY");
  if (summary.status !== expectedStatus) {
    issues.push(`status must be ${expectedStatus} for gate present=${summary.gate?.present === true}, strict=${summary.gate?.strict === true}, blockers=${summary.gate?.blockers ?? "missing"}, releaseGateContractIssues=${releaseGateContractIssues.length}`);
  }
  if (!Number.isInteger(summary.gate?.blockers) || summary.gate.blockers < 0) {
    issues.push("gate.blockers must be a non-negative integer");
  }
  if (!Number.isInteger(summary.gate?.warnings) || summary.gate.warnings < 0) {
    issues.push("gate.warnings must be a non-negative integer");
  }
	  const releaseGateInput = summary.inputArtifacts?.releaseGate;
	  if (!releaseGateInput || releaseGateInput.present !== summary.gate?.present) {
	    issues.push("inputArtifacts.releaseGate.present must match gate.present");
	  }
  if (releaseGateInput?.present === true) {
    if (!isIsoDateTime(releaseGateInput.modifiedAt)) {
      issues.push("inputArtifacts.releaseGate.modifiedAt must be an ISO datetime");
    }
    const actionLength = Array.isArray(summary.actions) ? summary.actions.length : null;
    if (actionLength !== null && releaseGateInput.blockers !== actionLength) {
      issues.push(`inputArtifacts.releaseGate.blockers must match actions.length, got ${releaseGateInput.blockers ?? "missing"} and ${actionLength}`);
    }
    if (releaseGateInput.warnings !== null && releaseGateInput.warnings !== summary.gate?.warnings) {
	      issues.push(`inputArtifacts.releaseGate.warnings must match gate.warnings, got ${releaseGateInput.warnings} and ${summary.gate?.warnings ?? "missing"}`);
	    }
	  }
	  const ownerQueueRunReportInput = summary.inputArtifacts?.ownerQueueRunReport;
	  if (!ownerQueueRunReportInput) {
	    issues.push("inputArtifacts.ownerQueueRunReport is required");
	  } else {
	    if (ownerQueueRunReportInput.relativePath !== "release/release-final-owner-queue-run-report.json") {
	      issues.push(`inputArtifacts.ownerQueueRunReport.relativePath must be release/release-final-owner-queue-run-report.json, got ${ownerQueueRunReportInput.relativePath || "missing"}`);
	    }
	    if (ownerQueueRunReportInput.present === true) {
	      if (!isIsoDateTime(ownerQueueRunReportInput.modifiedAt)) {
	        issues.push("inputArtifacts.ownerQueueRunReport.modifiedAt must be an ISO datetime when present");
	      }
	      if (!["PASS", "FAIL"].includes(ownerQueueRunReportInput.status)) {
	        issues.push(`inputArtifacts.ownerQueueRunReport.status must be PASS or FAIL when present, got ${ownerQueueRunReportInput.status || "missing"}`);
	      }
	    }
	  }
  const ownerQueueEnvInitReceiptInput = summary.inputArtifacts?.ownerQueueEnvInitReceipt;
  if (!ownerQueueEnvInitReceiptInput) {
    issues.push("inputArtifacts.ownerQueueEnvInitReceipt is required");
  } else {
    if (ownerQueueEnvInitReceiptInput.relativePath !== "release/release-final-owner-queue-env-init-receipt.json") {
      issues.push(`inputArtifacts.ownerQueueEnvInitReceipt.relativePath must be release/release-final-owner-queue-env-init-receipt.json, got ${ownerQueueEnvInitReceiptInput.relativePath || "missing"}`);
    }
    if (ownerQueueEnvInitReceiptInput.present === true && !isIsoDateTime(ownerQueueEnvInitReceiptInput.modifiedAt)) {
      issues.push("inputArtifacts.ownerQueueEnvInitReceipt.modifiedAt must be an ISO datetime when present");
    }
  }
  const explainGateReportInput = summary.inputArtifacts?.explainGateReport;
  if (!explainGateReportInput) {
    issues.push("inputArtifacts.explainGateReport is required");
  } else {
    if (explainGateReportInput.relativePath !== "release/explain-gate-report.json") {
      issues.push(`inputArtifacts.explainGateReport.relativePath must be release/explain-gate-report.json, got ${explainGateReportInput.relativePath || "missing"}`);
    }
    if (explainGateReportInput.present === true) {
      if (!isIsoDateTime(explainGateReportInput.modifiedAt)) {
        issues.push("inputArtifacts.explainGateReport.modifiedAt must be an ISO datetime when present");
      }
      if (!["PASS", "FAIL"].includes(explainGateReportInput.status)) {
        issues.push(`inputArtifacts.explainGateReport.status must be PASS or FAIL when present, got ${explainGateReportInput.status || "missing"}`);
      }
      if (!Number.isInteger(explainGateReportInput.blockers) || explainGateReportInput.blockers < 0) {
        issues.push("inputArtifacts.explainGateReport.blockers must be a non-negative integer when present");
      }
    }
  }
	  if (releaseConfigContractIssues.length > 0 && summary.status !== "NOT_READY") {
    issues.push(`status must be NOT_READY when releaseConfig contractIssues=${releaseConfigContractIssues.length}`);
  }
  if (!Array.isArray(summary.actions)) {
    issues.push("actions must be an array");
    return issues;
  }
  if (summary.actions.length !== summary.gate?.blockers) {
    issues.push(`actions.length must match gate.blockers, got ${summary.actions.length} actions and ${summary.gate?.blockers ?? "missing"} blockers`);
  }
  const duplicateBlockers = Object.entries(stableCountBy(summary.actions.map((action) => action.blocker)))
    .filter(([, count]) => count > 1)
    .map(([blocker]) => blocker);
  if (duplicateBlockers.length > 0) {
    issues.push(`actions must not contain duplicate blockers: ${duplicateBlockers.join(", ")}`);
  }
  for (const [index, action] of summary.actions.entries()) {
    for (const field of ["blocker", "check", "detail", "category", "owner", "action"]) {
      if (typeof action[field] !== "string" || action[field].trim() === "") {
        issues.push(`actions[${index}].${field} must be a non-empty string`);
      }
    }
    if (typeof action.structured !== "boolean") {
      issues.push(`actions[${index}].structured must be a boolean`);
    }
  }
  const actionBlockerCounts = stableCountBy(summary.actions.map((action) => action.blocker));
  const categoryBlockerCounts = stableCountBy(flattenGroupedActions(summary.actionsByCategory).map((action) => action.blocker));
  const ownerBlockerCounts = stableCountBy(flattenGroupedActions(summary.actionsByOwner).map((action) => action.blocker));
  if (JSON.stringify(categoryBlockerCounts) !== JSON.stringify(actionBlockerCounts)) {
    issues.push("actionsByCategory must cover the same blockers as actions exactly once");
  }
  if (JSON.stringify(ownerBlockerCounts) !== JSON.stringify(actionBlockerCounts)) {
    issues.push("actionsByOwner must cover the same blockers as actions exactly once");
  }
  const blockerMap = releaseBlockerMapArtifact(summary);
  if (blockerMap.totalBlockers !== summary.actions.length) {
    issues.push(`releaseBlockerMap totalBlockers must match actions.length, got ${blockerMap.totalBlockers} and ${summary.actions.length}`);
  }
  if (blockerMap.ownerCount !== blockerMap.owners.length) {
    issues.push("releaseBlockerMap ownerCount must match owners length");
  }
  const blockerMapCategoryCounts = stableCountBy(blockerMap.categories.flatMap((category) => (
    (category.blockers || []).map((blocker) => blocker.blocker)
  )));
  if (JSON.stringify(blockerMapCategoryCounts) !== JSON.stringify(actionBlockerCounts)) {
    issues.push("releaseBlockerMap categories must cover the same blockers as actions exactly once");
  }
  const blockerMapOwnerCounts = stableCountBy(blockerMap.owners.flatMap((owner) => (
    (owner.blockers || []).map((blocker) => blocker.blocker)
  )));
  if (JSON.stringify(blockerMapOwnerCounts) !== JSON.stringify(actionBlockerCounts)) {
    issues.push("releaseBlockerMap owners must cover the same blockers as actions exactly once");
  }
  const blockerMapStructuredCount = blockerMap.owners.flatMap((owner) => owner.blockers || [])
    .filter((blocker) => blocker.structured === true).length;
  const expectedStructuredCount = summary.diagnostics?.releaseGate?.structuredBlockers ?? summary.actions.filter((action) => action.structured === true).length;
  if (blockerMapStructuredCount !== expectedStructuredCount) {
    issues.push(`releaseBlockerMap structured blocker count must match diagnostics.releaseGate.structuredBlockers, got ${blockerMapStructuredCount} and ${expectedStructuredCount}`);
  }
  for (const [ownerIndex, owner] of (blockerMap.owners || []).entries()) {
    for (const [blockerIndex, blocker] of (owner.blockers || []).entries()) {
      for (const field of ["blocker", "check", "detail", "category", "action"]) {
        if (typeof blocker[field] !== "string" || blocker[field].trim() === "") {
          issues.push(`releaseBlockerMap.owners[${ownerIndex}].blockers[${blockerIndex}].${field} must be a non-empty string`);
        }
      }
      if (typeof blocker.structured !== "boolean") {
        issues.push(`releaseBlockerMap.owners[${ownerIndex}].blockers[${blockerIndex}].structured must be a boolean`);
      }
    }
  }
  for (const [categoryIndex, category] of (blockerMap.categories || []).entries()) {
    for (const [blockerIndex, blocker] of (category.blockers || []).entries()) {
      for (const field of ["blocker", "check", "detail", "owner", "action"]) {
        if (typeof blocker[field] !== "string" || blocker[field].trim() === "") {
          issues.push(`releaseBlockerMap.categories[${categoryIndex}].blockers[${blockerIndex}].${field} must be a non-empty string`);
        }
      }
      if (typeof blocker.structured !== "boolean") {
        issues.push(`releaseBlockerMap.categories[${categoryIndex}].blockers[${blockerIndex}].structured must be a boolean`);
      }
    }
  }
  const fastTrack = releaseFastTrackArtifact(summary);
  const expectedFastTrackRecommendation = (summary.gate?.blockers ?? 0) === 0
    && (fastTrack.cutoverChecklist || []).every((item) => item.status === "PASS")
    ? "GO_STRICT"
    : "NO_GO_STRICT";
  if (fastTrack.recommendation !== expectedFastTrackRecommendation) {
    issues.push(`releaseFastTrack recommendation must be ${expectedFastTrackRecommendation}, got ${fastTrack.recommendation || "missing"}`);
  }
  if (fastTrack.noAutoWaivers !== true) {
    issues.push("releaseFastTrack must keep noAutoWaivers=true");
  }
  assertReleaseEnvFileSignal("releaseFastTrack", fastTrack.safetySignals?.releaseEnvFile);
  if ((fastTrack.summary?.totalPendingItems ?? -1) !== (summary.releaseActionPriority || []).length) {
    issues.push("releaseFastTrack totalPendingItems must match releaseActionPriority length");
  }
  if ((fastTrack.summary?.blockedCutoverItems ?? -1) !== (fastTrack.cutoverChecklist || []).filter((item) => item.status !== "PASS").length) {
    issues.push("releaseFastTrack blockedCutoverItems must match cutoverChecklist");
  }
  if (summary.gate?.blockers > 0 && !(fastTrack.cutoverChecklist || []).some((item) => item.id === "strict-release-gate" && item.status === "BLOCKED")) {
    issues.push("releaseFastTrack strict-release-gate checklist item must be BLOCKED while gate has blockers");
  }
  if ((fastTrack.lanes || []).some((lane) => lane.safetyClass === "non-waivable" && lane.pendingItems > 0 && !lane.acceleration)) {
    issues.push("releaseFastTrack non-waivable lanes must include acceleration guidance");
  }
  const ownerMatrix = releaseCutoverOwnerMatrixArtifact(summary);
  if (ownerMatrix.recommendation !== fastTrack.recommendation) {
    issues.push("releaseCutoverOwnerMatrix recommendation must match releaseFastTrack");
  }
  if (ownerMatrix.noAutoWaivers !== true) {
    issues.push("releaseCutoverOwnerMatrix must keep noAutoWaivers=true");
  }
  if (JSON.stringify(ownerMatrix.safetySignals?.releaseEnvFile || null) !== JSON.stringify(fastTrack.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseCutoverOwnerMatrix safetySignals.releaseEnvFile must match releaseFastTrack");
  }
  if (ownerMatrix.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(ownerMatrix.safetySignals?.releaseEnvFile)) {
    issues.push("releaseCutoverOwnerMatrix releaseEnvFileCutoverSafe must match release env safety predicate");
  }
  if (!releaseCutoverOwnerMatrixMarkdown(summary).includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseCutoverOwnerMatrixMarkdown must include releaseEnvFileCutoverSafe");
  }
  const matrixChecklistIds = new Set((ownerMatrix.owners || []).flatMap((owner) => (
    (owner.items || []).map((item) => item.checklistId)
  )));
  const missingMatrixChecklistIds = (fastTrack.cutoverChecklist || [])
    .map((item) => item.id)
    .filter((id) => !matrixChecklistIds.has(id));
  if (missingMatrixChecklistIds.length > 0) {
    issues.push(`releaseCutoverOwnerMatrix must cover every cutover checklist item, missing: ${missingMatrixChecklistIds.join(", ")}`);
  }
  const matrixBlockedOwnerCount = (ownerMatrix.owners || []).filter((owner) => owner.blockedItems > 0).length;
  if ((ownerMatrix.summary?.blockedOwnerCount ?? -1) !== matrixBlockedOwnerCount) {
    issues.push("releaseCutoverOwnerMatrix blockedOwnerCount must match owners with blocked items");
  }
  for (const owner of ownerMatrix.owners || []) {
    if (owner.totalItems !== (owner.items || []).length) {
      issues.push(`releaseCutoverOwnerMatrix.${owner.owner}.totalItems must match items.length`);
    }
    const ownerBlockedItems = (owner.items || []).filter((item) => item.status !== "PASS").length;
    if (owner.blockedItems !== ownerBlockedItems) {
      issues.push(`releaseCutoverOwnerMatrix.${owner.owner}.blockedItems must match blocked items`);
    }
  }
  const sprintBoard = releaseSprintBoardArtifact(summary);
  if (sprintBoard.recommendation !== fastTrack.recommendation) {
    issues.push("releaseSprintBoard recommendation must match releaseFastTrack");
  }
  if (sprintBoard.noAutoWaivers !== true) {
    issues.push("releaseSprintBoard must keep noAutoWaivers=true");
  }
  if ((sprintBoard.summary?.batchCount ?? -1) !== (summary.releaseActionBatches || []).length) {
    issues.push("releaseSprintBoard batchCount must match releaseActionBatches length");
  }
  const sprintReadyIds = (sprintBoard.batchCards || []).filter((card) => card.status === "READY").map((card) => card.id).sort();
  const sprintBlockedIds = (sprintBoard.batchCards || []).filter((card) => card.status !== "READY").map((card) => card.id).sort();
  const expectedReadyIds = (releaseExecutionQueueArtifact(summary).readyBatches || []).map((batch) => batch.id).sort();
  const expectedBlockedIds = (releaseExecutionQueueArtifact(summary).blockedBatches || []).map((batch) => batch.id).sort();
  if (JSON.stringify(sprintReadyIds) !== JSON.stringify(expectedReadyIds)) {
    issues.push("releaseSprintBoard READY batch ids must match releaseExecutionQueue ready batches");
  }
  if (JSON.stringify(sprintBlockedIds) !== JSON.stringify(expectedBlockedIds)) {
    issues.push("releaseSprintBoard BLOCKED batch ids must match releaseExecutionQueue blocked batches");
  }
  if (JSON.stringify((sprintBoard.nextWave?.batchIds || []).sort()) !== JSON.stringify(expectedReadyIds)) {
    issues.push("releaseSprintBoard nextWave batch ids must match ready batches");
  }
  const expectedFinalDecision = releaseCutoverDecisionSummary(summary);
  const commandCatalog = releaseCommandCatalogArtifact(summary);
  assertReleaseEnvFileSignal("releaseCommandCatalog", commandCatalog.safetySignals?.releaseEnvFile);
  if (JSON.stringify(commandCatalog.finalDecision || null) !== JSON.stringify(expectedFinalDecision)) {
    issues.push("releaseCommandCatalog finalDecision must match release cutover decision summary");
  }
  if (JSON.stringify(commandCatalog.safetySignals?.releaseEnvFile || null) !== JSON.stringify(fastTrack.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseCommandCatalog safetySignals.releaseEnvFile must match releaseFastTrack");
  }
  if (commandCatalog.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(commandCatalog.safetySignals?.releaseEnvFile)) {
    issues.push("releaseCommandCatalog releaseEnvFileCutoverSafe must match release env safety predicate");
  }
  if (!releaseCommandCatalogMarkdown(summary).includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseCommandCatalogMarkdown must include releaseEnvFileCutoverSafe");
  }
  if (!releaseCommandCatalogMarkdown(summary).includes("cutoverAllowed")) {
    issues.push("releaseCommandCatalogMarkdown must include cutoverAllowed");
  }
  if (commandCatalog.recommendation !== fastTrack.recommendation) {
    issues.push("releaseCommandCatalog recommendation must match releaseFastTrack");
  }
  if (commandCatalog.noAutoWaivers !== true) {
    issues.push("releaseCommandCatalog must keep noAutoWaivers=true");
  }
  if ((commandCatalog.summary?.batchCommandCount ?? -1) !== expectedReadyIds.length) {
    issues.push("releaseCommandCatalog batchCommandCount must match ready batches");
  }
  if ((commandCatalog.summary?.ownerCommandCount ?? -1) !== (sprintBoard.nextWave?.owners || []).length) {
    issues.push("releaseCommandCatalog ownerCommandCount must match nextWave owners");
  }
  const catalogBatchIds = (commandCatalog.batchCommands || []).map((batch) => batch.batchId).sort();
  if (JSON.stringify(catalogBatchIds) !== JSON.stringify(expectedReadyIds)) {
    issues.push("releaseCommandCatalog batch commands must cover ready batches exactly once");
  }
  if ((commandCatalog.ownerCommands || []).some((owner) => !owner.commands?.list || !owner.commands?.envCheck || !owner.commands?.dryRun || !owner.commands?.execute)) {
    issues.push("releaseCommandCatalog owner commands must include list/envCheck/dryRun/execute");
  }
  const ownerHandoff = releaseOwnerHandoffArtifact(summary);
  if (ownerHandoff.recommendation !== fastTrack.recommendation) {
    issues.push("releaseOwnerHandoff recommendation must match releaseFastTrack");
  }
  if (JSON.stringify(ownerHandoff.finalDecision || null) !== JSON.stringify(commandCatalog.finalDecision || null)) {
    issues.push("releaseOwnerHandoff finalDecision must match releaseCommandCatalog");
  }
  if (ownerHandoff.noAutoWaivers !== true) {
    issues.push("releaseOwnerHandoff must keep noAutoWaivers=true");
  }
  if (JSON.stringify(ownerHandoff.safetySignals?.releaseEnvFile || null) !== JSON.stringify(commandCatalog.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseOwnerHandoff safetySignals.releaseEnvFile must match releaseCommandCatalog");
  }
  if (ownerHandoff.releaseEnvFileCutoverSafe !== commandCatalog.releaseEnvFileCutoverSafe) {
    issues.push("releaseOwnerHandoff releaseEnvFileCutoverSafe must match releaseCommandCatalog");
  }
  if (!releaseOwnerHandoffMarkdown(summary).includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseOwnerHandoffMarkdown must include releaseEnvFileCutoverSafe");
  }
  if (!releaseOwnerHandoffMarkdown(summary).includes("cutoverAllowed")) {
    issues.push("releaseOwnerHandoffMarkdown must include cutoverAllowed");
  }
  const handoffReadyIds = sortedUniqueStrings((ownerHandoff.owners || []).flatMap((owner) => owner.readyBatchIds || []));
  if (JSON.stringify(handoffReadyIds) !== JSON.stringify(expectedReadyIds)) {
    issues.push("releaseOwnerHandoff ready batch ids must match releaseExecutionQueue ready batches");
  }
  const handoffOwners = sortedUniqueStrings((ownerHandoff.owners || []).map((owner) => owner.owner));
  const requiredHandoffOwners = sortedUniqueStrings([
    ...(commandCatalog.ownerCommands || []).map((owner) => owner.owner),
    ...(releaseEnvOwnerMatrixArtifact(summary).owners || []).map((owner) => owner.owner),
  ]);
  for (const owner of requiredHandoffOwners) {
    if (!handoffOwners.includes(owner)) {
      issues.push(`releaseOwnerHandoff must include owner ${owner}`);
    }
  }
  const ownerReceipts = releaseOwnerReceiptsArtifact(summary);
  if (ownerReceipts.recommendation !== fastTrack.recommendation) {
    issues.push("releaseOwnerReceipts recommendation must match releaseFastTrack");
  }
  if (ownerReceipts.noAutoWaivers !== true) {
    issues.push("releaseOwnerReceipts must keep noAutoWaivers=true");
  }
  const receiptOwners = sortedUniqueStrings((ownerReceipts.owners || []).map((owner) => owner.owner));
  if (JSON.stringify(receiptOwners) !== JSON.stringify(handoffOwners)) {
    issues.push("releaseOwnerReceipts owners must match releaseOwnerHandoff owners");
  }
  const receiptExpectedArtifacts = (ownerReceipts.owners || []).reduce((sum, owner) => sum + (owner.expectedArtifactCount || 0), 0);
  if (ownerReceipts.summary?.expectedArtifactCount !== receiptExpectedArtifacts) {
    issues.push("releaseOwnerReceipts expectedArtifactCount must match owner rows");
  }
  const receiptPendingActions = (ownerReceipts.owners || []).reduce((sum, owner) => sum + (owner.pendingActionCount || 0), 0);
  if (ownerReceipts.summary?.pendingActionCount !== receiptPendingActions) {
    issues.push("releaseOwnerReceipts pendingActionCount must match owner rows");
  }
  const nextActionQueue = releaseNextActionQueueArtifact(summary);
  if (nextActionQueue.recommendation !== fastTrack.recommendation) {
    issues.push("releaseNextActionQueue recommendation must match releaseFastTrack");
  }
  if (JSON.stringify(nextActionQueue.finalDecision || null) !== JSON.stringify(ownerHandoff.finalDecision || null)) {
    issues.push("releaseNextActionQueue finalDecision must match releaseOwnerHandoff");
  }
  if (nextActionQueue.noAutoWaivers !== true) {
    issues.push("releaseNextActionQueue must keep noAutoWaivers=true");
  }
  if (JSON.stringify(nextActionQueue.safetySignals?.releaseEnvFile || null) !== JSON.stringify(ownerHandoff.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseNextActionQueue safetySignals.releaseEnvFile must match releaseOwnerHandoff");
  }
  if (nextActionQueue.releaseEnvFileCutoverSafe !== ownerHandoff.releaseEnvFileCutoverSafe) {
    issues.push("releaseNextActionQueue releaseEnvFileCutoverSafe must match releaseOwnerHandoff");
  }
  const nextActionOwnerInputReceipt = nextActionQueue.ownerInputReceipt || {};
  if (nextActionOwnerInputReceipt.status !== releaseOwnerInputReceiptArtifact(summary).status) {
    issues.push("releaseNextActionQueue ownerInputReceipt.status must match releaseOwnerInputReceipt");
  }
  if (nextActionOwnerInputReceipt.cutoverReady !== releaseOwnerInputReceiptArtifact(summary).cutoverReady) {
    issues.push("releaseNextActionQueue ownerInputReceipt.cutoverReady must match releaseOwnerInputReceipt");
  }
  if (nextActionQueue.summary?.ownerInputReceiptStatus !== nextActionOwnerInputReceipt.status) {
    issues.push("releaseNextActionQueue summary.ownerInputReceiptStatus must match ownerInputReceipt");
  }
  if (nextActionQueue.finalDecision?.cutoverAllowed === true && (nextActionOwnerInputReceipt.status !== "PASS" || nextActionOwnerInputReceipt.cutoverReady !== true)) {
    issues.push("releaseNextActionQueue cutoverAllowed=true requires ownerInputReceipt PASS and cutoverReady=true");
  }
  if (!releaseNextActionQueueMarkdown(summary).includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseNextActionQueueMarkdown must include releaseEnvFileCutoverSafe");
  }
  if (!releaseNextActionQueueMarkdown(summary).includes("cutoverAllowed")) {
    issues.push("releaseNextActionQueueMarkdown must include cutoverAllowed");
  }
  if (!releaseNextActionQueueMarkdown(summary).includes("## Owner Input Receipt")) {
    issues.push("releaseNextActionQueueMarkdown must include owner input receipt section");
  }
  const queueOwners = sortedUniqueStrings((nextActionQueue.items || []).map((item) => item.owner));
  if (JSON.stringify(queueOwners) !== JSON.stringify(receiptOwners)) {
    issues.push("releaseNextActionQueue owners must match releaseOwnerReceipts owners");
  }
  const queueRunNowCount = (nextActionQueue.items || []).filter((item) => item.queueStatus === "RUN_NOW").length;
  if (nextActionQueue.summary?.runNowCount !== queueRunNowCount) {
    issues.push("releaseNextActionQueue runNowCount must match queue rows");
  }
  if ((nextActionQueue.items || []).some((item) => item.queueStatus === "RUN_NOW" && (!Array.isArray(item.executableCommands) || item.executableCommands.length === 0))) {
    issues.push("releaseNextActionQueue RUN_NOW items must include executableCommands");
  }
  const blockerClosurePlan = releaseBlockerClosurePlanArtifact(summary);
  if (blockerClosurePlan.recommendation !== fastTrack.recommendation) {
    issues.push("releaseBlockerClosurePlan recommendation must match releaseFastTrack");
  }
  if (blockerClosurePlan.noAutoWaivers !== true) {
    issues.push("releaseBlockerClosurePlan must keep noAutoWaivers=true");
  }
  if (JSON.stringify(blockerClosurePlan.safetySignals?.releaseEnvFile || null) !== JSON.stringify(nextActionQueue.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseBlockerClosurePlan safetySignals.releaseEnvFile must match releaseNextActionQueue");
  }
  if (blockerClosurePlan.releaseEnvFileCutoverSafe !== nextActionQueue.releaseEnvFileCutoverSafe) {
    issues.push("releaseBlockerClosurePlan releaseEnvFileCutoverSafe must match releaseNextActionQueue");
  }
  if (JSON.stringify(blockerClosurePlan.ownerInputReceipt || null) !== JSON.stringify(nextActionQueue.ownerInputReceipt || null)) {
    issues.push("releaseBlockerClosurePlan ownerInputReceipt must match releaseNextActionQueue");
  }
  if (blockerClosurePlan.summary?.ownerInputReceiptStatus !== blockerClosurePlan.ownerInputReceipt?.status) {
    issues.push("releaseBlockerClosurePlan summary.ownerInputReceiptStatus must match ownerInputReceipt");
  }
  if (blockerClosurePlan.recommendation === "GO_STRICT" && (blockerClosurePlan.ownerInputReceipt?.status !== "PASS" || blockerClosurePlan.ownerInputReceipt?.cutoverReady !== true)) {
    issues.push("releaseBlockerClosurePlan GO_STRICT requires ownerInputReceipt PASS and cutoverReady=true");
  }
  if (!releaseBlockerClosurePlanMarkdown(summary).includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseBlockerClosurePlanMarkdown must include releaseEnvFileCutoverSafe");
  }
  if (!releaseBlockerClosurePlanMarkdown(summary).includes("## Owner Input Receipt")) {
    issues.push("releaseBlockerClosurePlanMarkdown must include owner input receipt section");
  }
  if ((blockerClosurePlan.items || []).length !== (summary.releaseActionPriority || []).length) {
    issues.push("releaseBlockerClosurePlan items must match releaseActionPriority length");
  }
  const closurePlanIds = sortedUniqueStrings((blockerClosurePlan.items || []).map((item) => item.id));
  const priorityIds = sortedUniqueStrings((summary.releaseActionPriority || []).map((item) => item.id));
  if (JSON.stringify(closurePlanIds) !== JSON.stringify(priorityIds)) {
    issues.push("releaseBlockerClosurePlan ids must match releaseActionPriority ids");
  }
  const closurePlanRunNow = (blockerClosurePlan.items || []).filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES");
  if (closurePlanRunNow.some((item) => !item.batchReady)) {
    issues.push("releaseBlockerClosurePlan non-waiting items must be batchReady");
  }
  if (closurePlanRunNow.some((item) => !Array.isArray(item.commands) || item.commands.length === 0)) {
    issues.push("releaseBlockerClosurePlan non-waiting items must include commands");
  }
  if (blockerClosurePlan.summary?.runnableWaveCount !== (blockerClosurePlan.waves || []).length) {
    issues.push("releaseBlockerClosurePlan runnableWaveCount must match waves length");
  }
  const waveItemIds = sortedUniqueStrings((blockerClosurePlan.waves || []).flatMap((wave) => wave.itemIds || []));
  const runNowItemIds = sortedUniqueStrings(closurePlanRunNow.map((item) => item.id));
  if (JSON.stringify(waveItemIds) !== JSON.stringify(runNowItemIds)) {
    issues.push("releaseBlockerClosurePlan waves must cover every runnable item exactly once");
  }
  if ((blockerClosurePlan.waves || []).some((wave) => !Array.isArray(wave.commands) || wave.commands.length === 0)) {
    issues.push("releaseBlockerClosurePlan waves must include commands");
  }
  const closureWaveEnvMatrix = releaseClosureWaveEnvMatrixArtifact(summary);
  if (closureWaveEnvMatrix.recommendation !== fastTrack.recommendation) {
    issues.push("releaseClosureWaveEnvMatrix recommendation must match releaseFastTrack");
  }
  if (closureWaveEnvMatrix.noAutoWaivers !== true) {
    issues.push("releaseClosureWaveEnvMatrix must keep noAutoWaivers=true");
  }
  if (closureWaveEnvMatrix.summary?.waveCount !== (blockerClosurePlan.waves || []).length) {
    issues.push("releaseClosureWaveEnvMatrix waveCount must match releaseBlockerClosurePlan waves length");
  }
  if (!sameStringSet(closureWaveEnvMatrix.uniqueEnvKeys, (closureWaveEnvMatrix.waves || []).flatMap((wave) => wave.envKeys || []))) {
    issues.push("releaseClosureWaveEnvMatrix uniqueEnvKeys must match wave env keys");
  }
  const closureWaveReceipts = releaseClosureWaveReceiptsArtifact(summary);
  if (closureWaveReceipts.recommendation !== fastTrack.recommendation) {
    issues.push("releaseClosureWaveReceipts recommendation must match releaseFastTrack");
  }
  if (closureWaveReceipts.noAutoWaivers !== true) {
    issues.push("releaseClosureWaveReceipts must keep noAutoWaivers=true");
  }
  if (closureWaveReceipts.summary?.waveCount !== (closureWaveEnvMatrix.waves || []).length) {
    issues.push("releaseClosureWaveReceipts waveCount must match closure wave env matrix waves");
  }
  const receiptMissingArtifacts = (closureWaveReceipts.waves || []).reduce((sum, wave) => sum + (wave.missingArtifactCount || 0), 0);
  if (closureWaveReceipts.summary?.missingArtifactCount !== receiptMissingArtifacts) {
    issues.push("releaseClosureWaveReceipts missingArtifactCount must match wave rows");
  }
  const closureWaveBlockerMap = releaseClosureWaveBlockerMapArtifact(summary);
  if (closureWaveBlockerMap.recommendation !== fastTrack.recommendation) {
    issues.push("releaseClosureWaveBlockerMap recommendation must match releaseFastTrack");
  }
  if (closureWaveBlockerMap.noAutoWaivers !== true) {
    issues.push("releaseClosureWaveBlockerMap must keep noAutoWaivers=true");
  }
  if (closureWaveBlockerMap.summary?.waveCount !== (blockerClosurePlan.waves || []).length) {
    issues.push("releaseClosureWaveBlockerMap waveCount must match releaseBlockerClosurePlan waves length");
  }
  if (closureWaveBlockerMap.summary?.mappedActionCount !== closurePlanRunNow.length) {
    issues.push("releaseClosureWaveBlockerMap mappedActionCount must match runnable closure items");
  }
  if (JSON.stringify(sortedUniqueStrings(closureWaveBlockerMap.mappedItemIds || [])) !== JSON.stringify(runNowItemIds)) {
    issues.push("releaseClosureWaveBlockerMap item ids must cover runnable closure items");
  }
  if ((closureWaveBlockerMap.waves || []).some((wave) => !Array.isArray(wave.commands) || wave.commands.length === 0)) {
    issues.push("releaseClosureWaveBlockerMap waves must include commands");
  }
  if ((closureWaveBlockerMap.waves || []).some((wave) => !Array.isArray(wave.expectedArtifacts) || wave.expectedArtifacts.length === 0)) {
    issues.push("releaseClosureWaveBlockerMap waves must include expectedArtifacts");
  }
  const performanceBaselineClosure = releasePerformanceBaselineClosureArtifact(summary);
  if (performanceBaselineClosure.noAutoWaivers !== true) {
    issues.push("releasePerformanceBaselineClosure must keep noAutoWaivers=true");
  }
  if (!Array.isArray(performanceBaselineClosure.commands) || performanceBaselineClosure.commands.length === 0) {
    issues.push("releasePerformanceBaselineClosure must include commands");
  }
  if (!Array.isArray(performanceBaselineClosure.requiredEnvKeys) || performanceBaselineClosure.requiredEnvKeys.length === 0) {
    issues.push("releasePerformanceBaselineClosure must include requiredEnvKeys");
  }
  const releaseEnvBootstrap = releaseEnvBootstrapScript(summary);
  assertFinalGoNoGoClosure("releaseEnvBootstrap", releaseEnvBootstrap);
  const releaseExecutionCommandsText = releaseExecutionCommands(summary);
  assertReleaseEnvFileSafety("releaseExecutionCommands", releaseExecutionCommandsText);
  assertFinalGoNoGoClosure("releaseExecutionCommands", releaseExecutionCommandsText);
  const releaseNextActionCommandsText = releaseNextActionCommands(summary);
  assertReleaseEnvFileSafety("releaseNextActionCommands", releaseNextActionCommandsText);
  assertFinalGoNoGoClosure("releaseNextActionCommands", releaseNextActionCommandsText);
  const releaseBlockerClosureCommandsText = releaseBlockerClosureCommands(summary);
  assertReleaseEnvFileSafety("releaseBlockerClosureCommands", releaseBlockerClosureCommandsText);
  assertFinalGoNoGoClosure("releaseBlockerClosureCommands", releaseBlockerClosureCommandsText);
  const performanceBaselineCommands = releasePerformanceBaselineCommands(summary);
  assertReleaseEnvFileSafety("releasePerformanceBaselineCommands", performanceBaselineCommands);
  assertFinalGoNoGoClosure("releasePerformanceBaselineCommands", performanceBaselineCommands);
  if (!performanceBaselineCommands.includes("DDD_AUTH_PERF_BASELINE_EXECUTE")) {
    issues.push("releasePerformanceBaselineCommands must include execute toggle");
  }
  if (!performanceBaselineCommands.includes("DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs")) {
    issues.push("releasePerformanceBaselineCommands must run authenticated performance smoke");
  }
  const finalGoNoGo = releaseFinalGoNoGoArtifact(summary);
  assertReleaseEnvFileSignal("releaseFinalGoNoGo", finalGoNoGo.safetySignals?.releaseEnvFile);
  if (JSON.stringify(finalGoNoGo.safetySignals?.releaseEnvFile || null) !== JSON.stringify(fastTrack.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseFinalGoNoGo safetySignals.releaseEnvFile must match releaseFastTrack");
  }
  if (finalGoNoGo.noAutoWaivers !== true) {
    issues.push("releaseFinalGoNoGo must keep noAutoWaivers=true");
  }
  if (finalGoNoGo.recommendation !== fastTrack.recommendation) {
    issues.push("releaseFinalGoNoGo recommendation must match releaseFastTrack");
  }
  if (finalGoNoGo.finalRecommendation !== finalGoNoGo.recommendation) {
    issues.push("releaseFinalGoNoGo finalRecommendation must match recommendation");
  }
  const finalGoNoGoMarkdown = releaseFinalGoNoGoMarkdown(summary);
  if (!finalGoNoGoMarkdown.includes("Final recommendation:")) {
    issues.push("releaseFinalGoNoGoMarkdown must include finalRecommendation");
  }
  const expectedCutoverAllowed = fastTrack.recommendation === "GO_STRICT"
    && releaseEnvFileIsCutoverSafe(finalGoNoGo.safetySignals?.releaseEnvFile);
  if (finalGoNoGo.cutoverAllowed !== expectedCutoverAllowed) {
    issues.push("releaseFinalGoNoGo cutoverAllowed must match strict GO decision and release env safety");
  }
  if (finalGoNoGo.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(finalGoNoGo.safetySignals?.releaseEnvFile)) {
    issues.push("releaseFinalGoNoGo releaseEnvFileCutoverSafe must match release env safety predicate");
  }
  if (finalGoNoGo.summary?.blockedCutoverItems !== (fastTrack.cutoverChecklist || []).filter((item) => item.status !== "PASS").length) {
    issues.push("releaseFinalGoNoGo blockedCutoverItems must match fastTrack checklist");
  }
  if (finalGoNoGo.summary?.runnableClosureWaves !== (blockerClosurePlan.waves || []).length) {
    issues.push("releaseFinalGoNoGo runnableClosureWaves must match closure plan waves");
  }
  if (finalGoNoGo.summary?.receiptMissingArtifactWaves !== closureWaveReceipts.summary?.artifactMissingCount) {
    issues.push("releaseFinalGoNoGo receiptMissingArtifactWaves must match closure receipts");
  }
  const finalGoNoGoNextActionQueue = releaseNextActionQueueArtifact(summary);
  const finalGoNoGoActionCommands = (finalGoNoGoNextActionQueue.items || []).flatMap((item) => item.executableCommands || []);
  if (!Array.isArray(finalGoNoGo.nextCommands) || finalGoNoGo.nextCommands.length === 0) {
    issues.push("releaseFinalGoNoGo must include nextCommands");
  }
  if (finalGoNoGoActionCommands.includes("DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs")
    && !finalGoNoGo.nextCommands.includes("DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs")) {
    issues.push("releaseFinalGoNoGo nextCommands must include migration check-env handoff command");
  }
  if (finalGoNoGoActionCommands.includes("DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs")
    && !finalGoNoGo.nextCommands.includes("DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs")) {
    issues.push("releaseFinalGoNoGo nextCommands must include rollback check-env handoff command");
  }
  if (performanceBaselineClosure.status !== "READY"
    && !finalGoNoGo.nextCommands.includes("DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh")) {
    issues.push("releaseFinalGoNoGo nextCommands must include authenticated performance baseline check-env command");
  }
  for (const command of finalGoNoGo.nextCommands || []) {
    if (!finalGoNoGoMarkdown.includes(command)) {
      issues.push(`releaseFinalGoNoGoMarkdown must include next command: ${command}`);
    }
  }
  if (finalGoNoGo.ciSummary?.nonGoExitCode !== 10) {
    issues.push("releaseFinalGoNoGo ciSummary must use nonGoExitCode=10");
  }
  if (!String(finalGoNoGo.ciSummary?.enforceCommand || "").includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1")) {
    issues.push("releaseFinalGoNoGo ciSummary must include preflight enforce command");
  }
  if (!String(finalGoNoGo.ciSummary?.finalGoNoGoEnforceCommand || "").includes("DDD_FINAL_GO_NO_GO_ENFORCE=1")) {
    issues.push("releaseFinalGoNoGo ciSummary must keep final go/no-go enforce command");
  }
  const finalGoNoGoFirstOwnerAction = (finalGoNoGoNextActionQueue.items || [])[0] || null;
  if (finalGoNoGoFirstOwnerAction) {
    if (finalGoNoGo.ciSummary?.firstOwnerAction?.owner !== finalGoNoGoFirstOwnerAction.owner) {
      issues.push("releaseFinalGoNoGo ciSummary.firstOwnerAction owner must match releaseNextActionQueue first item");
    }
    if (finalGoNoGo.ciSummary?.firstOwnerActionCommand !== (finalGoNoGoFirstOwnerAction.executableCommands?.[0] || null)) {
      issues.push("releaseFinalGoNoGo ciSummary.firstOwnerActionCommand must match releaseNextActionQueue first command");
    }
    if (String(finalGoNoGo.ciSummary?.firstOwnerActionCommand || "").includes("DDD_RELEASE_ENV_FILE=")
      && !String(finalGoNoGo.ciSummary?.firstOwnerActionDisplayCommand || "").includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
      issues.push("releaseFinalGoNoGo ciSummary.firstOwnerActionDisplayCommand must redact DDD_RELEASE_ENV_FILE");
    }
    if (String(finalGoNoGo.ciSummary?.firstOwnerAction?.nextAction || "").includes("DDD_RELEASE_ENV_FILE=")
      && !String(finalGoNoGo.ciSummary?.firstOwnerAction?.displayNextAction || "").includes("DDD_RELEASE_ENV_FILE=<release-env-file>")) {
      issues.push("releaseFinalGoNoGo ciSummary.firstOwnerAction.displayNextAction must redact DDD_RELEASE_ENV_FILE");
    }
  }
  if (finalGoNoGo.cutoverAllowed === false && (!Array.isArray(finalGoNoGo.ciSummary?.stopOwners) || finalGoNoGo.ciSummary.stopOwners.length === 0)) {
    issues.push("releaseFinalGoNoGo ciSummary must include stopOwners for NO-GO");
  }
  const finalOwnerQueue = releaseFinalOwnerQueueArtifact(summary);
  assertReleaseEnvFileSignal("releaseFinalOwnerQueue", finalOwnerQueue.safetySignals?.releaseEnvFile);
  if (finalOwnerQueue.noAutoWaivers !== true) {
    issues.push("releaseFinalOwnerQueue must keep noAutoWaivers=true");
  }
  if (finalOwnerQueue.recommendation !== finalGoNoGo.recommendation) {
    issues.push("releaseFinalOwnerQueue recommendation must match finalGoNoGo");
  }
  if (finalOwnerQueue.finalRecommendation !== finalGoNoGo.finalRecommendation) {
    issues.push("releaseFinalOwnerQueue finalRecommendation must match finalGoNoGo");
  }
  if (finalOwnerQueue.cutoverAllowed !== finalGoNoGo.cutoverAllowed) {
    issues.push("releaseFinalOwnerQueue cutoverAllowed must match finalGoNoGo");
  }
  if (finalOwnerQueue.releaseEnvFileCutoverSafe !== finalGoNoGo.releaseEnvFileCutoverSafe) {
    issues.push("releaseFinalOwnerQueue releaseEnvFileCutoverSafe must match finalGoNoGo");
  }
  if (JSON.stringify(finalOwnerQueue.safetySignals?.releaseEnvFile || null) !== JSON.stringify(finalGoNoGo.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseFinalOwnerQueue safetySignals.releaseEnvFile must match finalGoNoGo");
  }
  if (!finalOwnerQueue.ownerInputReceipt || typeof finalOwnerQueue.ownerInputReceipt !== "object") {
    issues.push("releaseFinalOwnerQueue ownerInputReceipt must be present");
  } else {
    const finalReceipt = finalGoNoGo.ciSummary?.ownerInputReceipt || {};
    if (finalOwnerQueue.ownerInputReceipt.status !== finalReceipt.status) {
      issues.push("releaseFinalOwnerQueue ownerInputReceipt.status must match finalGoNoGo ownerInputReceipt");
    }
    if (finalOwnerQueue.ownerInputReceipt.cutoverReady !== finalReceipt.cutoverReady) {
      issues.push("releaseFinalOwnerQueue ownerInputReceipt.cutoverReady must match finalGoNoGo ownerInputReceipt");
    }
    if (finalOwnerQueue.ownerInputReceipt.requiredOwnerInputs !== finalReceipt.requiredOwnerInputs) {
      issues.push("releaseFinalOwnerQueue ownerInputReceipt.requiredOwnerInputs must match finalGoNoGo ownerInputReceipt");
    }
    if (finalOwnerQueue.ownerInputReceipt.pendingOwnerCount !== finalReceipt.pendingOwnerCount) {
      issues.push("releaseFinalOwnerQueue ownerInputReceipt.pendingOwnerCount must match finalGoNoGo ownerInputReceipt");
    }
    if (!sameStringSet(finalOwnerQueue.ownerInputReceipt.missingCriteria || [], finalReceipt.missingCriteria || [])) {
      issues.push("releaseFinalOwnerQueue ownerInputReceipt.missingCriteria must match finalGoNoGo ownerInputReceipt");
    }
    if (finalOwnerQueue.cutoverAllowed === true && (finalOwnerQueue.ownerInputReceipt.status !== "PASS" || finalOwnerQueue.ownerInputReceipt.cutoverReady !== true)) {
      issues.push("releaseFinalOwnerQueue cutoverAllowed=true requires ownerInputReceipt PASS and cutoverReady=true");
    }
  }
  const finalOwnerQueueMarkdown = releaseFinalOwnerQueueMarkdown(summary);
  if (!finalOwnerQueueMarkdown.includes("releaseEnvFileCutoverSafe")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include releaseEnvFileCutoverSafe");
  }
  if (!finalOwnerQueueMarkdown.includes("Waiting owners:")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include waiting owner count");
  }
  if (!finalOwnerQueueMarkdown.includes("Unique missing artifacts:")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include missing artifact count");
  }
  if (!finalOwnerQueueMarkdown.includes("Owner input receipt status:")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include owner input receipt status");
  }
  if (!finalOwnerQueueMarkdown.includes("## Owner Input Receipt")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include owner input receipt section");
  }
  if (!finalOwnerQueueMarkdown.includes("Next executable owner:")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include next executable owner");
  }
  if (!finalOwnerQueueMarkdown.includes("Next executable command:")) {
    issues.push("releaseFinalOwnerQueueMarkdown must include next executable command");
  }
  if (!finalOwnerQueueMarkdown.includes("ACTIONABLE owners first")) {
    issues.push("releaseFinalOwnerQueueMarkdown must explain queue ordering");
  }
  const finalOwnerQueueFirstExecutable = (finalOwnerQueue.ownerQueues || []).find((owner) => owner.canExecute === true) || null;
  if (finalOwnerQueue.summary?.nextExecutableOwner !== (finalOwnerQueueFirstExecutable?.owner || null)) {
    issues.push("releaseFinalOwnerQueue summary.nextExecutableOwner must match first executable owner");
  }
  if (finalOwnerQueue.summary?.nextExecutableQueueOrder !== (finalOwnerQueueFirstExecutable?.queueOrder || null)) {
    issues.push("releaseFinalOwnerQueue summary.nextExecutableQueueOrder must match first executable owner queueOrder");
  }
  if (finalOwnerQueue.summary?.nextExecutableCommand !== (finalOwnerQueueFirstExecutable?.firstCommand || null)) {
    issues.push("releaseFinalOwnerQueue summary.nextExecutableCommand must match first executable owner firstCommand");
  }
  if (finalOwnerQueue.summary?.nextExecutableEnvKeyCount !== (finalOwnerQueueFirstExecutable?.envKeyCount || 0)) {
    issues.push("releaseFinalOwnerQueue summary.nextExecutableEnvKeyCount must match first executable owner envKeyCount");
  }
  if (finalOwnerQueue.summary?.nextExecutableMissingArtifactCount !== (finalOwnerQueueFirstExecutable?.missingArtifactCount || 0)) {
    issues.push("releaseFinalOwnerQueue summary.nextExecutableMissingArtifactCount must match first executable owner missingArtifactCount");
  }
  if (finalGoNoGo.ciSummary?.firstOwnerAction?.owner && finalOwnerQueueFirstExecutable?.owner !== finalGoNoGo.ciSummary.firstOwnerAction.owner) {
    issues.push("releaseFinalOwnerQueue first executable owner must match finalGoNoGo firstOwnerAction owner");
  }
  if (finalGoNoGo.ciSummary?.firstOwnerAction?.command && finalOwnerQueueFirstExecutable?.firstCommand !== finalGoNoGo.ciSummary.firstOwnerAction.command) {
    issues.push("releaseFinalOwnerQueue first executable command must match finalGoNoGo firstOwnerAction command");
  }
  if (finalOwnerQueueFirstExecutable && finalOwnerQueueFirstExecutable.firstOwnerActionPriority !== true) {
    issues.push("releaseFinalOwnerQueue first executable owner must carry firstOwnerActionPriority=true");
  }
  for (const envKey of finalGoNoGo.ciSummary?.firstOwnerAction?.envKeys || []) {
    if (!finalOwnerQueueFirstExecutable?.envKeys?.includes(envKey)) {
      issues.push(`releaseFinalOwnerQueue first executable owner must include firstOwnerAction env key ${envKey}`);
    }
  }
  if (!sameStringSet((finalOwnerQueue.ownerQueues || []).map((owner) => owner.owner), finalGoNoGo.ciSummary?.stopOwners || [])) {
    issues.push("releaseFinalOwnerQueue owners must match finalGoNoGo stopOwners");
  }
  let seenWaitingOwnerQueue = false;
  for (const [index, ownerQueue] of (finalOwnerQueue.ownerQueues || []).entries()) {
    if (ownerQueue.queueOrder !== index + 1) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} queueOrder must be ${index + 1}`);
    }
    if (ownerQueue.queueStatus === "WAITING") {
      seenWaitingOwnerQueue = true;
    }
    if (seenWaitingOwnerQueue && ownerQueue.queueStatus === "ACTIONABLE") {
      issues.push("releaseFinalOwnerQueue ACTIONABLE owners must be ordered before WAITING owners");
    }
    if (ownerQueue.canExecute !== (ownerQueue.queueStatus === "ACTIONABLE")) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} canExecute must match queueStatus`);
    }
    if (ownerQueue.commandCount !== (ownerQueue.commands || []).length) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} commandCount must match commands`);
    }
    if (ownerQueue.envKeyCount !== (ownerQueue.envKeys || []).length) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} envKeyCount must match envKeys`);
    }
    if (ownerQueue.missingArtifactCount !== (ownerQueue.missingArtifacts || []).length) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} missingArtifactCount must match missingArtifacts`);
    }
    if (ownerQueue.stopReasonCount !== (ownerQueue.stopReasons || []).length) {
      issues.push(`releaseFinalOwnerQueue owner ${ownerQueue.owner} stopReasonCount must match stopReasons`);
    }
  }
  if (finalGoNoGo.cutoverAllowed === false && (finalOwnerQueue.summary?.actionableOwnerCount || 0) === 0) {
    issues.push("releaseFinalOwnerQueue must include actionable owners for NO-GO");
  }
  const finalOwnerQueueCommands = releaseFinalOwnerQueueCommands(summary);
  assertFinalGoNoGoClosure("releaseFinalOwnerQueueCommands", finalOwnerQueueCommands);
  if (!finalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_EXECUTE")) {
    issues.push("releaseFinalOwnerQueueCommands must include execute toggle");
  }
  if (!finalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_OWNER")) {
    issues.push("releaseFinalOwnerQueueCommands must include owner filter");
  }
  if (!finalOwnerQueueCommands.includes("order=")) {
    issues.push("releaseFinalOwnerQueueCommands must print queueOrder in owner output");
  }
  if (!finalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_CHECK_ENV")) {
    issues.push("releaseFinalOwnerQueueCommands must include env check toggle");
  }
  if (!finalOwnerQueueCommands.includes("[ddd-final-owner-queue][env-missing]")) {
    issues.push("releaseFinalOwnerQueueCommands must report missing env keys");
  }
  if (!finalOwnerQueueCommands.includes("[ddd-final-owner-queue][dry-run]")) {
    issues.push("releaseFinalOwnerQueueCommands must default to dry-run");
  }
  if (!finalOwnerQueueCommands.includes("[ddd-final-owner-queue][waiting]")) {
    issues.push("releaseFinalOwnerQueueCommands must expose waiting owners for diagnostics");
  }
  if (!finalOwnerQueueCommands.includes("[ddd-final-owner-queue][blocked]")) {
    issues.push("releaseFinalOwnerQueueCommands must refuse execution for non-actionable owners");
  }
  if (!finalOwnerQueueCommands.includes("env_file_has_owner_queue_key")) {
    issues.push("releaseFinalOwnerQueueCommands must statically inspect env files for CHECK_ENV");
  }
  if (!finalOwnerQueueCommands.includes("node --input-type=module -e")) {
    issues.push("releaseFinalOwnerQueueCommands must avoid sourcing env files during CHECK_ENV");
  }
  if (!finalOwnerQueueCommands.includes("release-final-owner-queue-env.template.env")) {
    issues.push("releaseFinalOwnerQueueCommands must reject final owner queue env template as a release env file");
  }
  if (!finalOwnerQueueCommands.includes("Release env file permissions are too broad")) {
    issues.push("releaseFinalOwnerQueueCommands must reject broadly readable release env files");
  }
  if (!finalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_REPORT")) {
    issues.push("releaseFinalOwnerQueueCommands must support execution report output");
  }
  if (!finalOwnerQueueCommands.includes("failedEntries") || !finalOwnerQueueCommands.includes("succeededEntries")) {
    issues.push("releaseFinalOwnerQueueCommands must write run report summary counts");
  }
  if (!finalOwnerQueueCommands.includes("finalize_owner_queue_report")) {
    issues.push("releaseFinalOwnerQueueCommands must finalize execution report on exit");
  }
  if (!finalOwnerQueueCommands.includes("append_owner_queue_report_entry")) {
    issues.push("releaseFinalOwnerQueueCommands must append per-command execution report entries");
  }
  if (!finalOwnerQueueCommands.includes("queueOrder: Number(queueOrder)")) {
    issues.push("releaseFinalOwnerQueueCommands must include queueOrder in execution report entries");
  }
  if (!finalOwnerQueueCommands.includes("queueStatus, commandIndex, commandCount, command")) {
    issues.push("releaseFinalOwnerQueueCommands must include queueStatus and command index metadata in execution report entries");
  }
  if (!finalOwnerQueueCommands.includes("commandIndex: Number(commandIndex)") || !finalOwnerQueueCommands.includes("commandCount: Number(commandCount)")) {
    issues.push("releaseFinalOwnerQueueCommands must include commandIndex and commandCount in execution report entries");
  }
  if (!finalOwnerQueueCommands.includes("ddd-final-owner-queue-run-report-contract.mjs")) {
    issues.push("releaseFinalOwnerQueueCommands must validate execution report with the run report contract");
  }
  assertReleaseEnvFileSafety("releaseFinalOwnerQueueCommands", finalOwnerQueueCommands);
  const finalOwnerQueueEnvTemplate = releaseFinalOwnerQueueEnvTemplate(summary);
  const finalOwnerQueueNextOwner = (finalOwnerQueue.ownerQueues || []).find((owner) => owner.canExecute === true)?.owner
    || finalOwnerQueue.ownerQueues?.[0]?.owner
    || "release-owner";
  if (!finalOwnerQueueEnvTemplate.includes("DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1")) {
    issues.push("releaseFinalOwnerQueueEnvTemplate must document env check usage");
  }
  if (!finalOwnerQueueEnvTemplate.includes(`DDD_FINAL_OWNER_QUEUE_OWNER=${finalOwnerQueueNextOwner}`)) {
    issues.push("releaseFinalOwnerQueueEnvTemplate usage must point to the first executable owner");
  }
  if (!finalOwnerQueueEnvTemplate.includes("# Queue order:")) {
    issues.push("releaseFinalOwnerQueueEnvTemplate must include queue order comments");
  }
  if (!finalOwnerQueueEnvTemplate.includes("# Can execute:")) {
    issues.push("releaseFinalOwnerQueueEnvTemplate must include canExecute comments");
  }
  const finalOwnerQueueEnvTemplateKeys = [...finalOwnerQueueEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);
  const finalOwnerQueueEnvTemplateSafeDefaultKeys = [...finalOwnerQueueEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=(?!__REQUIRED__)([^#\n]+)$/gm)]
    .filter((match) => finalOwnerQueueSafeEnvDefaults.has(match[1]))
    .map((match) => match[1]);
  if (new Set(finalOwnerQueueEnvTemplateKeys).size !== finalOwnerQueueEnvTemplateKeys.length) {
    issues.push("releaseFinalOwnerQueueEnvTemplate must not duplicate active env key declarations");
  }
  if (new Set(finalOwnerQueueEnvTemplateSafeDefaultKeys).size !== finalOwnerQueueEnvTemplateSafeDefaultKeys.length) {
    issues.push("releaseFinalOwnerQueueEnvTemplate must not duplicate safe default env key declarations");
  }
  if (!sameStringSet([...finalOwnerQueueEnvTemplateKeys, ...finalOwnerQueueEnvTemplateSafeDefaultKeys], (finalOwnerQueue.ownerQueues || []).flatMap((owner) => owner.envKeys || []))) {
    issues.push("releaseFinalOwnerQueueEnvTemplate keys must match owner queue env keys");
  }
  const finalOwnerQueueEnvInit = releaseFinalOwnerQueueEnvInit(summary);
  if (!finalOwnerQueueEnvInit.includes("DDD_FINAL_OWNER_QUEUE_ENV_TARGET")) {
    issues.push("releaseFinalOwnerQueueEnvInit must include target override");
  }
  if (!finalOwnerQueueEnvInit.includes("DDD_FINAL_OWNER_QUEUE_ENV_FORCE")) {
    issues.push("releaseFinalOwnerQueueEnvInit must include explicit overwrite toggle");
  }
  if (!finalOwnerQueueEnvInit.includes(`DDD_FINAL_OWNER_QUEUE_OWNER=${finalOwnerQueueNextOwner}`)) {
    issues.push("releaseFinalOwnerQueueEnvInit must point follow-up check to the first executable owner");
  }
  if (!finalOwnerQueueEnvInit.includes("chmod 600")) {
    issues.push("releaseFinalOwnerQueueEnvInit must restrict initialized env file permissions");
  }
  if (!finalOwnerQueueEnvInit.includes("Refusing to use a generated template as the populated release env target")) {
    issues.push("releaseFinalOwnerQueueEnvInit must reject generated templates as target files");
  }
  assertFinalGoNoGoClosure("releaseFinalOwnerQueueEnvInit", finalOwnerQueueEnvInit);
  const finalGoNoGoGate = releaseFinalGoNoGoGate(summary);
  if (!finalGoNoGoGate.includes("DDD_FINAL_GO_NO_GO_ENFORCE")) {
    issues.push("releaseFinalGoNoGoGate must include enforce toggle");
  }
  if (!finalGoNoGoGate.includes("finalRecommendation=")) {
    issues.push("releaseFinalGoNoGoGate must print finalRecommendation");
  }
  if (!finalGoNoGoGate.includes("\"${DDD_NODE_BIN}\" --input-type=module")) {
    issues.push("releaseFinalGoNoGoGate must parse packet with DDD_NODE_BIN");
  }
  if (!finalGoNoGoGate.includes("exit 10")) {
    issues.push("releaseFinalGoNoGoGate must return non-zero in enforce mode");
  }
  const ownerRollupArtifact = ownerActionRollupArtifact(summary);
  const activeOwners = Object.entries(summary.ownerActionRollup || {})
    .filter(([, plan]) => (plan.pendingItems || 0) > 0)
    .map(([owner]) => owner)
    .sort();
  const collapsedOnlyOwners = Object.entries(summary.ownerActionRollup || {})
    .filter(([, plan]) => (plan.pendingItems || 0) === 0 && (plan.collapsedItems || 0) > 0)
    .map(([owner]) => owner)
    .sort();
  if (JSON.stringify(ownerRollupArtifact.activeOwners || []) !== JSON.stringify(activeOwners)) {
    issues.push("ownerActionRollup activeOwners must match owners with pendingItems > 0");
  }
  if (JSON.stringify(ownerRollupArtifact.collapsedOnlyOwners || []) !== JSON.stringify(collapsedOnlyOwners)) {
    issues.push("ownerActionRollup collapsedOnlyOwners must match owners with only collapsed items");
  }
  for (const [owner, plan] of Object.entries(summary.ownerActionRollup || {})) {
    const pendingItems = plan.pendingItems || 0;
    const collapsedItems = plan.collapsedItems || 0;
    const items = Array.isArray(plan.items) ? plan.items : [];
    const collapsed = Array.isArray(plan.collapsed) ? plan.collapsed : [];
    if (pendingItems !== items.length) {
      issues.push(`ownerActionRollup.${owner}.pendingItems must match items.length, got ${pendingItems} and ${items.length}`);
    }
    if (collapsedItems !== collapsed.length) {
      issues.push(`ownerActionRollup.${owner}.collapsedItems must match collapsed.length, got ${collapsedItems} and ${collapsed.length}`);
    }
    if (pendingItems === 0 && collapsedItems > 0) {
      if (items.length > 0) {
        issues.push(`ownerActionRollup.${owner} collapsed-only owner must not include active items`);
      }
      if (Object.keys(plan.sources || {}).length > 0) {
        issues.push(`ownerActionRollup.${owner} collapsed-only owner must not include active sources`);
      }
      if ((plan.envKeys || []).length > 0) {
        issues.push(`ownerActionRollup.${owner} collapsed-only owner must not include active envKeys`);
      }
    }
  }
  const priorityItems = Array.isArray(summary.releaseActionPriority) ? summary.releaseActionPriority : [];
  const priorityIdentity = (item = {}) => `${item.priority || "unknown"}:${item.source || "unknown"}:${item.owner || "unknown"}:${item.id || "unknown"}`;
  for (const [index, item] of priorityItems.entries()) {
    for (const field of ["priority", "source", "owner", "id", "check", "reason", "detail", "action"]) {
      if (typeof item[field] !== "string" || item[field].trim() === "") {
        issues.push(`releaseActionPriority[${index}].${field} must be a non-empty string`);
      }
    }
    if (typeof item.structured !== "boolean") {
      issues.push(`releaseActionPriority[${index}].structured must be a boolean`);
    }
  }
  const duplicatePriorityItems = Object.entries(stableCountBy(priorityItems.map(priorityIdentity)))
    .filter(([, count]) => count > 1)
    .map(([identity]) => identity);
  if (duplicatePriorityItems.length > 0) {
    issues.push(`releaseActionPriority must not contain duplicate items: ${duplicatePriorityItems.join(", ")}`);
  }
  const batches = Array.isArray(summary.releaseActionBatches) ? summary.releaseActionBatches : [];
  const priorityItemCount = priorityItems.length;
  const batchItemCount = batches.reduce((sum, batch) => sum + (batch.pendingItems || 0), 0);
  if (priorityItemCount !== batchItemCount) {
    issues.push(`releaseActionBatches pendingItems must match releaseActionPriority length, got ${batchItemCount} batch items and ${priorityItemCount} priority items`);
  }
  const batchIds = batches.map((batch) => batch.id).filter(Boolean);
  const batchIdSet = new Set(batchIds);
  const duplicateBatchIds = Object.entries(stableCountBy(batchIds))
    .filter(([, count]) => count > 1)
    .map(([id]) => id);
  if (duplicateBatchIds.length > 0) {
    issues.push(`releaseActionBatches ids must be unique: ${duplicateBatchIds.join(", ")}`);
  }
  const priorityByIdentity = new Map(priorityItems.map((item) => [priorityIdentity(item), item]));
  for (const [index, batch] of batches.entries()) {
    const batchItems = Array.isArray(batch.items) ? batch.items : [];
    if (typeof batch.id !== "string" || batch.id.trim() === "") {
      issues.push(`releaseActionBatches[${index}] must include a stable id`);
    }
    if ((batch.pendingItems || 0) !== batchItems.length) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} pendingItems must match items.length, got ${batch.pendingItems || 0} and ${batchItems.length}`);
    }
    if (!Array.isArray(batch.commands) || batch.commands.length === 0) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} must include at least one command hint`);
    }
    if (Array.isArray(batch.commands) && batch.commands.length > 0 && (!Array.isArray(batch.envKeys) || batch.envKeys.length === 0)) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} must include env key hints for its commands`);
    }
    validateEnvCheckGroupsForEnvKeys(`releaseActionBatches[${index}] ${batch.id || "unknown"}`, batch.envKeys || [], batch.envCheckGroups || [], issues);
    for (const requiredCommand of requiredCommandHintsForBatch(batch)) {
      if (!(batch.commands || []).includes(requiredCommand)) {
        issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} missing required command hint: ${requiredCommand}`);
      }
    }
    if (!Array.isArray(batch.expectedArtifacts) || batch.expectedArtifacts.length === 0) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} must include expected artifacts`);
    } else if (!sameStringSet(batch.expectedArtifacts, expectedArtifactsForBatch(batch))) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} expected artifacts must match source/owner contract`);
    }
    if (!Array.isArray(batch.exitCriteria) || batch.exitCriteria.length === 0) {
      issues.push(`releaseActionBatches[${index}] ${batch.source || "unknown"}/${batch.owner || "unknown"} must include exit criteria`);
    }
    if (!Array.isArray(batch.dependsOn)) {
      issues.push(`releaseActionBatches[${index}] ${batch.id || "unknown"} dependsOn must be an array`);
    } else {
      for (const dependency of batch.dependsOn) {
        if (!batchIdSet.has(dependency)) {
          issues.push(`releaseActionBatches[${index}] ${batch.id || "unknown"} depends on unknown batch ${dependency}`);
        }
        if (dependency === batch.id) {
          issues.push(`releaseActionBatches[${index}] ${batch.id || "unknown"} must not depend on itself`);
        }
      }
      if ((batch.canRunImmediately === true) !== (batch.dependsOn.length === 0)) {
        issues.push(`releaseActionBatches[${index}] ${batch.id || "unknown"} canRunImmediately must match dependsOn emptiness`);
      }
    }
    for (const [itemIndex, item] of batchItems.entries()) {
      for (const field of ["id", "check", "reason", "detail", "action"]) {
        if (typeof item[field] !== "string" || item[field].trim() === "") {
          issues.push(`releaseActionBatches[${index}].items[${itemIndex}].${field} must be a non-empty string`);
        }
      }
      if (typeof item.structured !== "boolean") {
        issues.push(`releaseActionBatches[${index}].items[${itemIndex}].structured must be a boolean`);
      }
      const matchingPriority = priorityByIdentity.get(`${batch.priority || "unknown"}:${batch.source || "unknown"}:${batch.owner || "unknown"}:${item.id || "unknown"}`);
      if (matchingPriority) {
        for (const field of ["check", "detail", "structured"]) {
          if (item[field] !== matchingPriority[field]) {
            issues.push(`releaseActionBatches[${index}].items[${itemIndex}].${field} must match releaseActionPriority ${field}`);
          }
        }
      }
    }
  }
  const batchIdentities = batches.flatMap((batch) => (batch.items || []).map((item) => (
    `${batch.priority || "unknown"}:${batch.source || "unknown"}:${batch.owner || "unknown"}:${item.id || "unknown"}`
  )));
  const priorityIdentityCounts = stableCountBy(priorityItems.map(priorityIdentity));
  const batchIdentityCounts = stableCountBy(batchIdentities);
  if (JSON.stringify(batchIdentityCounts) !== JSON.stringify(priorityIdentityCounts)) {
    issues.push("releaseActionBatches items must cover the same priority items exactly once");
  }
  const dependencyGraph = releaseActionDependencyGraphArtifact(summary);
  const expectedEdgeCount = batches.reduce((sum, batch) => sum + ((batch.dependsOn || []).length), 0);
  const expectedReadyBatchIds = batches
    .filter((batch) => batch.canRunImmediately === true)
    .map((batch) => batch.id)
    .filter(Boolean);
  const expectedBlockedBatchIds = batches
    .filter((batch) => batch.canRunImmediately !== true)
    .map((batch) => batch.id)
    .filter(Boolean);
  if (dependencyGraph.batchCount !== batches.length || dependencyGraph.nodes.length !== batches.length) {
    issues.push(`releaseActionDependencyGraph nodes must match releaseActionBatches length, got ${dependencyGraph.nodes.length} nodes and ${batches.length} batches`);
  }
  for (const node of dependencyGraph.nodes || []) {
    const originalBatch = batches.find((batch) => batch.id === node.id);
    validateEnvCheckGroupsForEnvKeys(`releaseActionDependencyGraph ${node.id || "unknown"}`, node.envKeys || [], node.envCheckGroups || [], issues);
    if (originalBatch && !sameStringSet(node.envKeys || [], originalBatch.envKeys || [])) {
      issues.push(`releaseActionDependencyGraph ${node.id || "unknown"} envKeys must match releaseActionBatches`);
    }
  }
  if (dependencyGraph.edgeCount !== expectedEdgeCount || dependencyGraph.edges.length !== expectedEdgeCount) {
    issues.push(`releaseActionDependencyGraph edges must match dependsOn count, got ${dependencyGraph.edges.length} edges and ${expectedEdgeCount} dependencies`);
  }
  const maxDependencyGraphEdges = batches.length > 1 ? batches.length * (batches.length - 1) : 0;
  const expectedGraphDensity = maxDependencyGraphEdges > 0 ? Number((expectedEdgeCount / maxDependencyGraphEdges).toFixed(4)) : 0;
  if (dependencyGraph.graphDensity !== expectedGraphDensity) {
    issues.push(`releaseActionDependencyGraph graphDensity must match edgeCount/maxDirectedEdges, got ${dependencyGraph.graphDensity} and ${expectedGraphDensity}`);
  }
  if (!sameStringSet(dependencyGraph.readyBatchIds, expectedReadyBatchIds)) {
    issues.push("releaseActionDependencyGraph readyBatchIds must match canRunImmediately batches");
  }
  if (!sameStringSet(dependencyGraph.blockedBatchIds, expectedBlockedBatchIds)) {
    issues.push("releaseActionDependencyGraph blockedBatchIds must match dependent batches");
  }
  for (const edge of dependencyGraph.edges) {
    if (!batchIdSet.has(edge.from) || !batchIdSet.has(edge.to)) {
      issues.push(`releaseActionDependencyGraph edge references unknown batch: ${edge.from || "missing"} -> ${edge.to || "missing"}`);
    }
  }
  const batchById = new Map(batches.map((batch) => [batch.id, batch]));
  const expectedCompressedEdgeKeys = new Set();
  for (const edge of dependencyGraph.edges) {
    const fromBatch = batchById.get(edge.from);
    const toBatch = batchById.get(edge.to);
    if (fromBatch && toBatch && fromBatch.priority !== toBatch.priority) {
      expectedCompressedEdgeKeys.add(`${fromBatch.priority}->${toBatch.priority}`);
    }
  }
  const compressedEdgeKeys = (dependencyGraph.compressedEdges || [])
    .map((edge) => `${edge.fromPriority}->${edge.toPriority}`);
  if (!sameStringSet(compressedEdgeKeys, [...expectedCompressedEdgeKeys])) {
    issues.push("releaseActionDependencyGraph compressedEdges must match full graph edges grouped by priority");
  }
  if (dependencyGraph.compressedEdgeCount !== compressedEdgeKeys.length) {
    issues.push("releaseActionDependencyGraph compressedEdgeCount must match compressedEdges length");
  }
  const executionQueue = releaseExecutionQueueArtifact(summary);
  assertReleaseEnvFileSignal("releaseExecutionQueue", executionQueue.safetySignals?.releaseEnvFile);
  if (JSON.stringify(executionQueue.safetySignals?.releaseEnvFile || null) !== JSON.stringify(fastTrack.safetySignals?.releaseEnvFile || null)) {
    issues.push("releaseExecutionQueue safetySignals.releaseEnvFile must match releaseFastTrack");
  }
  if (executionQueue.releaseEnvFileCutoverSafe !== releaseEnvFileIsCutoverSafe(executionQueue.safetySignals?.releaseEnvFile)) {
    issues.push("releaseExecutionQueue releaseEnvFileCutoverSafe must match release env safety predicate");
  }
  if (executionQueue.readyBatchCount !== expectedReadyBatchIds.length) {
    issues.push(`releaseExecutionQueue readyBatchCount must match ready batches, got ${executionQueue.readyBatchCount} and ${expectedReadyBatchIds.length}`);
  }
  if (executionQueue.blockedBatchCount !== expectedBlockedBatchIds.length) {
    issues.push(`releaseExecutionQueue blockedBatchCount must match blocked batches, got ${executionQueue.blockedBatchCount} and ${expectedBlockedBatchIds.length}`);
  }
  if (!sameStringSet(executionQueue.nextBatchIds, expectedReadyBatchIds)) {
    issues.push("releaseExecutionQueue nextBatchIds must match ready batch ids");
  }
  for (const readyBatch of executionQueue.readyBatches || []) {
    validateEnvCheckGroupsForEnvKeys(`releaseExecutionQueue ${readyBatch.id || "unknown"}`, readyBatch.envKeys || [], readyBatch.envCheckGroups || [], issues);
  }
  for (const blockedBatch of executionQueue.blockedBatches || []) {
    const originalBatch = batchById.get(blockedBatch.id);
    const expectedUnmetCount = (originalBatch?.dependsOn || []).length;
    if (blockedBatch.unmetDependencyCount !== expectedUnmetCount) {
      issues.push(`releaseExecutionQueue ${blockedBatch.id || "unknown"} unmetDependencyCount must match dependsOn length`);
    }
  }
  const orchestratorItems = priorityItems.filter((item) => item.source === "orchestrator");
  if (orchestratorItems.some((item) => item.priority !== "P3")) {
    issues.push("orchestrator release actions must be P3 final verification");
  }
  if (orchestratorItems.length > 0) {
    const lastBatch = batches.at(-1);
    if (lastBatch?.source !== "orchestrator" || lastBatch?.priority !== "P3") {
      issues.push("orchestrator release batch must be the final P3 batch");
    }
  }
  const missingEnv = releaseMissingEnv(summary);
  const templateKeys = releaseMissingEnvTemplateKeys(releaseMissingEnvTemplate(summary));
  const templateKeyCounts = stableCountBy(templateKeys);
  const duplicateTemplateKeys = Object.entries(templateKeyCounts)
    .filter(([, count]) => count > 1)
    .map(([envKey]) => envKey);
  const expectedTemplateKeys = missingEnv.templateEnvKeys || [];
  const emittedTemplateKeySet = new Set(templateKeys);
  const expectedTemplateKeySet = new Set(expectedTemplateKeys);
  const missingTemplateKeys = expectedTemplateKeys.filter((envKey) => !emittedTemplateKeySet.has(envKey));
  const extraTemplateKeys = templateKeys.filter((envKey) => !expectedTemplateKeySet.has(envKey));
  for (const group of missingEnv.groups || []) {
    validateEnvCheckGroupsForEnvKeys(`releaseMissingEnv ${group.batchId || `${group.source || "unknown"}/${group.owner || "unknown"}`}`, group.envKeys || [], group.envCheckGroups || [], issues);
  }
  const envOwnerMatrix = releaseEnvOwnerMatrixArtifact(summary);
  if ((envOwnerMatrix.groupCount ?? -1) !== (missingEnv.groupCount ?? 0)) {
    issues.push("releaseEnvOwnerMatrix groupCount must match releaseMissingEnv groupCount");
  }
  const matrixTemplateKeys = sortedUniqueStrings((envOwnerMatrix.owners || []).flatMap((owner) => owner.templateEnvKeys || []));
  if (JSON.stringify(matrixTemplateKeys) !== JSON.stringify(expectedTemplateKeys)) {
    issues.push("releaseEnvOwnerMatrix template keys must match releaseMissingEnv templateEnvKeys");
  }
  for (const owner of envOwnerMatrix.owners || []) {
    if (owner.groupCount !== (owner.groups || []).length) {
      issues.push(`releaseEnvOwnerMatrix.${owner.owner}.groupCount must match groups.length`);
    }
    if (owner.readyGroupCount + owner.blockedGroupCount !== owner.groupCount) {
      issues.push(`releaseEnvOwnerMatrix.${owner.owner}.readyGroupCount + blockedGroupCount must match groupCount`);
    }
  }
  if (duplicateTemplateKeys.length > 0) {
    issues.push(`release-env-missing.template.env must not contain duplicate env keys: ${duplicateTemplateKeys.join(", ")}`);
  }
  if (missingTemplateKeys.length > 0) {
    issues.push(`release-env-missing.template.env missing env keys: ${missingTemplateKeys.join(", ")}`);
  }
  if (extraTemplateKeys.length > 0) {
    issues.push(`release-env-missing.template.env contains unexpected env keys: ${extraTemplateKeys.join(", ")}`);
  }
  return issues;
}

function isLocalBaseUrl(value) {
  return typeof value === "string"
    && /^(http:\/\/)?(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?/i.test(value.replace(/^https?:\/\//i, ""));
}

function productionEquivalenceDiagnostics(artifact, strictGate, evidenceName, deploymentEvidence = null) {
  if (artifact?.productionEquivalence) {
    return artifact.productionEquivalence;
  }
  if (!artifact?.baseUrl) {
    return null;
  }
  const evidence = deploymentEvidence ?? (process.env.DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "");
  return buildProductionEquivalenceEvidence({
    strict: strictGate,
    baseUrl: artifact.baseUrl,
    deploymentEvidence: evidence,
    evidenceName,
  });
}

function performanceActionPlan(performance) {
  if (!performance) {
    return null;
  }
  const items = [];
  const add = (id, owner, action, envKeys = [], reason = null, extra = {}) => {
    if (items.some((item) => item.id === id)) {
      return;
    }
    items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
  };
  const actual = performance.actual || {};
  if (actual.present !== true) {
    add(
      "performance-actual",
      "release-performance",
      "Run authenticated performance smoke against a production-equivalent backend URL.",
      ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "LUMIRA_AUTH_TOKEN", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
      actual.file ? `missing authenticated performance actual ${actual.file}` : "missing authenticated performance actual",
    );
  } else if (actual.productionEquivalence?.issues?.length > 0) {
    add(
      "performance-actual-production-equivalence",
      "release-performance",
      "Regenerate authenticated performance actual against an HTTPS non-local production-equivalent backend URL.",
      ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
      actual.productionEquivalence.issues.join("; "),
    );
  }
  for (const issue of actual.shapeIssues || []) {
    add(
      `performance-actual-shape-${items.length + 1}`,
      "release-performance",
      "Fix authenticated performance actual artifact shape and rerun the smoke.",
      [],
      issue,
    );
  }
  const baseline = performance.baseline || {};
  const baselineFile = baseline.file
    ? (path.isAbsolute(baseline.file) ? path.relative(repoRoot, baseline.file) : baseline.file)
    : null;
  if (baseline.present !== true) {
    add(
      "performance-baseline",
      "release-performance",
      baseline.action || "Promote an accepted production-equivalent authenticated performance actual as the strict baseline.",
      baseline.requiredEnvKeys || [
        "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
        "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
        "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
        "DDD_RELEASE_CANDIDATE",
      ],
      baselineFile ? `missing authenticated performance baseline ${baselineFile}` : "missing authenticated performance baseline",
    );
  }
  for (const issue of baseline.shapeIssues || []) {
    add(
      `performance-baseline-shape-${items.length + 1}`,
      "release-performance",
      "Fix authenticated performance baseline artifact shape before strict release.",
      [],
      issue,
    );
  }
  for (const issue of baseline.metadataIssues || []) {
    add(
      `performance-baseline-metadata-${items.length + 1}`,
      "release-performance",
      "Regenerate authenticated performance baseline with accepted-by, source environment, source artifact, and release candidate metadata.",
      ["DDD_AUTH_PERF_BASELINE_ACCEPTED_BY", "DDD_AUTH_PERF_BASELINE_ENVIRONMENT", "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT", "DDD_RELEASE_CANDIDATE"],
      issue,
    );
  }
  const promotion = performance.baselinePromotion || {};
  for (const blocker of promotion.blockers || []) {
    add(
      `performance-baseline-promotion-${items.length + 1}`,
      "release-performance",
      "Resolve baseline promotion blocker and rerun `node scripts/ddd-promote-performance-baseline.mjs`.",
      promotion.requiredEnvKeys || ["DDD_AUTH_PERF_BASELINE_ACCEPTED_BY", "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT", "DDD_RELEASE_CANDIDATE"],
      blocker,
    );
  }
  for (const endpoint of performance.missingBaselineEndpoints || []) {
    add(
      `performance-endpoint-${endpoint}`,
      "release-performance",
      "Rerun authenticated performance smoke so actual and baseline contain the same endpoint set.",
      ["LUMIRA_BASE_URL", "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT"],
      `baseline endpoint missing from actual: ${endpoint}`,
      { endpoint },
    );
  }
  for (const issue of performance.regressionIssues || []) {
    add(
      `performance-regression-${issue.name || items.length + 1}`,
      "release-performance",
      "Investigate authenticated performance regression or intentionally promote a new accepted baseline after review.",
      ["DDD_AUTH_PERF_BASELINE_ACCEPTED_BY", "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT"],
      issue.detail || "authenticated performance regression",
      { metric: issue.name || null },
    );
  }
  return {
    owner: "release-performance",
    pendingItems: items.length,
    envKeys: [...new Set(items.flatMap((item) => item.envKeys || []))].sort(),
    items: items.sort((left, right) => left.id.localeCompare(right.id)),
  };
}

function businessRuntimeActionPlan(business) {
  if (!business) {
    return {};
  }
  const byOwner = new Map();
  const add = (owner, id, action, envKeys = [], reason = null, extra = {}) => {
    if (!byOwner.has(owner)) {
      byOwner.set(owner, {
        owner,
        pendingItems: 0,
        envKeys: [],
        items: [],
      });
    }
    const plan = byOwner.get(owner);
    if (plan.items.some((item) => item.id === id)) {
      return;
    }
    plan.pendingItems += 1;
    plan.items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
    for (const envKey of envKeys || []) {
      if (!plan.envKeys.includes(envKey)) {
        plan.envKeys.push(envKey);
      }
    }
  };

  const file = business.fileProcessing || {};
  if (file.present !== true) {
    add(
      "file-owner",
      "file-processing-artifact",
      "Run file processing E2E smoke with `node scripts/ddd-file-processing-e2e-smoke.mjs` and attach the generated file-processing-e2e.json evidence.",
      ["LUMIRA_BASE_URL", "LUMIRA_JOB_INTERNAL_TOKEN", "LUMIRA_UPLOAD_STORAGE_ROOT"],
      file.file ? `missing file processing artifact ${file.file}` : "missing file processing artifact",
    );
  } else {
    if (file.productionEquivalence?.issues?.length > 0) {
      add(
        "file-owner",
        "file-processing-production-equivalence",
        "Regenerate File processing E2E smoke against an HTTPS non-local production-equivalent backend with real storage and job token evidence using `node scripts/ddd-file-processing-e2e-smoke.mjs`.",
        ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE", "LUMIRA_UPLOAD_STORAGE_ROOT", "LUMIRA_JOB_INTERNAL_TOKEN"],
        file.productionEquivalence.issues.join("; "),
      );
    }
    const incompleteTasks = (file.requiredTasks || []).filter((task) => task.status !== "SUCCEEDED");
    if (incompleteTasks.length > 0) {
      add(
        "file-owner",
        "file-processing-required-tasks",
        "Fix asynchronous file processing so security scan, text extraction, and AI parse tasks complete before release evidence is accepted.",
        ["LUMIRA_JOB_INTERNAL_TOKEN", "LUMIRA_FILE_SECURITY_SCAN_MODE", "LUMIRA_FILE_OCR_MODE"],
        `incomplete file tasks=${incompleteTasks.map((task) => `${task.taskType}:${task.status}`).join(",")}`,
        { tasks: incompleteTasks.map((task) => task.taskType).sort() },
      );
    }
    const missingArtifacts = (file.requiredArtifacts || []).filter((artifact) => artifact.present !== true);
    if (missingArtifacts.length > 0) {
      add(
        "file-owner",
        "file-processing-required-artifacts",
        "Fix file processing projections so required security, text, and AI parse artifacts are persisted.",
        ["LUMIRA_UPLOAD_STORAGE_ROOT"],
        `missing file artifacts=${missingArtifacts.map((artifact) => artifact.artifactType).join(",")}`,
        { artifacts: missingArtifacts.map((artifact) => artifact.artifactType).sort() },
      );
    }
  }

  const payment = business.paymentWebhook || {};
  if (payment.present !== true) {
    add(
      "payment-owner",
      "payment-webhook-artifact",
      "Run payment webhook E2E smoke with `node scripts/ddd-payment-webhook-e2e-smoke.mjs` and attach payment-webhook-e2e.json evidence.",
      ["LUMIRA_BASE_URL", "PAYMENT_PUBLIC_BASE_URL"],
      payment.file ? `missing payment webhook artifact ${payment.file}` : "missing payment webhook artifact",
    );
  } else {
    if (payment.productionEquivalence?.issues?.length > 0) {
      add(
        "payment-owner",
        "payment-webhook-production-equivalence",
        "Regenerate Payment webhook E2E smoke against an HTTPS non-local webhook URL with provider sandbox or deployment evidence using `node scripts/ddd-payment-webhook-e2e-smoke.mjs`.",
        ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "PAYMENT_PUBLIC_BASE_URL", "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE"],
        payment.productionEquivalence.issues.join("; "),
      );
    }
    if (payment.providerConfigured !== true || payment.orderStatus !== "PAID") {
      add(
        "payment-owner",
        "payment-webhook-state",
        "Verify payment provider configuration and webhook state transition to PAID with idempotent duplicate handling.",
        ["PAYMENT_PUBLIC_BASE_URL"],
        `providerConfigured=${payment.providerConfigured === true} orderStatus=${payment.orderStatus || "missing"}`,
      );
    }
    const webhookFailures = Object.entries(payment.webhooks || {})
      .filter(([, webhook]) => webhook.processed === null || webhook.signatureValid === null);
    if (webhookFailures.length > 0) {
      add(
        "payment-owner",
        "payment-webhook-cases",
        "Regenerate webhook smoke evidence with first, duplicate, nonce replay, and bad signature cases.",
        ["PAYMENT_PUBLIC_BASE_URL"],
        `missing webhook case outcomes=${webhookFailures.map(([name]) => name).join(",")}`,
        { cases: webhookFailures.map(([name]) => name).sort() },
      );
    }
  }

  const job = business.jobE2e || {};
  if (job.present !== true) {
    add(
      "job-owner",
      "job-e2e-artifact",
      "Run Job/internal E2E smoke with `node scripts/ddd-job-e2e-smoke.mjs` and attach job-e2e-smoke.json evidence.",
      ["LUMIRA_BASE_URL", "LUMIRA_JOB_INTERNAL_TOKEN"],
      job.file ? `missing job E2E artifact ${job.file}` : "missing job E2E artifact",
    );
  } else {
    if (job.productionEquivalence?.issues?.length > 0) {
      add(
        "job-owner",
        "job-e2e-production-equivalence",
        "Regenerate Job/internal E2E smoke against HTTPS non-local owner endpoints with the release job token using `node scripts/ddd-job-e2e-smoke.mjs`.",
        ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "LUMIRA_JOB_INTERNAL_TOKEN", "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE"],
        job.productionEquivalence.issues.join("; "),
      );
    }
    const badEndpoints = (job.endpoints || []).filter((endpoint) => (
      endpoint.present !== true
        || endpoint.status !== 200
        || endpoint.dataType !== endpoint.expectedDataType
    ));
    if (badEndpoints.length > 0) {
      add(
        "job-owner",
        "job-e2e-endpoints",
        "Fix owner internal job endpoints so every required relay/processing endpoint returns 200 with the expected response type.",
        ["LUMIRA_JOB_INTERNAL_TOKEN"],
        `job endpoint failures=${badEndpoints.map((endpoint) => endpoint.name).join(",")}`,
        { endpoints: badEndpoints.map((endpoint) => endpoint.name).sort() },
      );
    }
    if (job.unauthorizedStatus !== 401) {
      add(
        "job-owner",
        "job-e2e-unauthorized",
        "Verify internal job endpoints reject unauthenticated calls with HTTP 401.",
        ["LUMIRA_JOB_INTERNAL_TOKEN"],
        `unauthorizedStatus=${job.unauthorizedStatus ?? "missing"}`,
      );
    }
    if (job.outboxOwnership && job.outboxOwnership.crossOwnerPayloadFailuresDelta !== 0) {
      add(
        "job-owner",
        "job-outbox-ownership",
        "Fix outbox relay ownership routing so cross-owner payload failures do not increase during job smoke.",
        ["LUMIRA_EVENT_OUTBOX_DISPATCHER", "LUMIRA_EVENT_REDIS_STREAM_KEY"],
        `crossOwnerPayloadFailuresDelta=${job.outboxOwnership.crossOwnerPayloadFailuresDelta}`,
      );
    }
  }

  return Object.fromEntries(
    [...byOwner.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([owner, plan]) => [
        owner,
        {
          ...plan,
          envKeys: plan.envKeys.sort(),
          items: plan.items.sort((left, right) => left.id.localeCompare(right.id)),
        },
      ]),
  );
}

function runtimeReadinessActionPlan(readiness) {
  if (!readiness) {
    return null;
  }
  const items = [];
  const add = (id, owner, action, envKeys = [], reason = null, extra = {}) => {
    if (items.some((item) => item.id === id)) {
      return;
    }
    items.push({
      id,
      owner,
      action,
      envKeys,
      reason,
      ...extra,
    });
  };
  if (readiness.present !== true) {
    add(
      "runtime-readiness-artifact",
      "release-infra",
      "Run runtime readiness smoke with `node scripts/ddd-runtime-readiness-smoke.mjs` and attach artifacts/ddd/readiness/summary.json.",
      ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
      readiness.file ? `missing runtime readiness artifact ${readiness.file}` : "missing runtime readiness artifact",
    );
  } else {
    if (readiness.productionEquivalence?.issues?.length > 0) {
      add(
        "runtime-readiness-production-equivalence",
        "release-infra",
        "Regenerate runtime readiness against an HTTPS non-local production-equivalent backend URL with `node scripts/ddd-runtime-readiness-smoke.mjs`.",
        ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL", "DDD_DEPLOYMENT_EVIDENCE", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
        readiness.productionEquivalence.issues.join("; "),
      );
    }
    for (const issue of readiness.contractIssues || []) {
      add(
        `runtime-readiness-contract-${items.length + 1}`,
        "release-infra",
        "Fix runtime readiness artifact contract issues and regenerate summary.json with `node scripts/ddd-runtime-readiness-smoke.mjs`.",
        ["LUMIRA_BASE_URL", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
        issue,
      );
    }
    const notReadyContexts = (readiness.contexts || []).filter((context) => context.ready !== true);
    for (const context of notReadyContexts) {
      const owner = `${context.context}-owner`;
      const missing = context.missing || [];
      const non200 = context.non200 || [];
      add(
        `runtime-readiness-context-${context.context}`,
        owner,
        "Fix owner readiness endpoints so ready/health/metrics all return HTTP 200 with artifact links.",
        ["LUMIRA_BASE_URL"],
        [
          missing.length > 0 ? `missing=${missing.join(",")}` : null,
          non200.length > 0 ? `non200=${non200.map((item) => `${item.suffix}:${item.status}`).join(",")}` : null,
        ].filter(Boolean).join("; ") || "context readiness is not complete",
        {
          context: context.context,
          missing,
          non200,
        },
      );
    }
  }
  return {
    owner: "release-infra",
    pendingItems: items.length,
    envKeys: [...new Set(items.flatMap((item) => item.envKeys || []))].sort(),
    items: items.sort((left, right) => left.id.localeCompare(right.id)),
  };
}

function performanceDiagnostics(actualRead, baselineRead, promotionRead, strictGate) {
  const actual = actualRead.data || null;
  const baseline = baselineRead.data || null;
  const promotion = promotionRead.data || null;
  const actualShapeIssues = actual ? validateAuthenticatedPerformanceShape("authenticated performance actual", actual, { strict: strictGate }) : [];
  const baselineShapeIssues = baseline ? validateAuthenticatedPerformanceShape("authenticated performance baseline", baseline) : [];
  const baselineMetadataIssues = baseline ? validateAuthenticatedPerformanceBaselineMetadata(baseline, { strict: strictGate }) : [];
  const regressionIssues = actual && baseline
    ? compareAuthenticatedPerformance(actual, baseline, { maxRegressionRatio: 0.10 })
    : [];
  const actualEndpoints = actual?.perEndpoint || {};
  const baselineEndpoints = baseline?.perEndpoint || {};
  const missingBaselineEndpoints = Object.keys(baselineEndpoints)
    .filter((endpoint) => !actualEndpoints[endpoint])
    .sort();
  const diagnostics = {
    actual: actual ? {
      present: true,
      baseUrl: actual.baseUrl || null,
      localOnly: isLocalBaseUrl(actual.baseUrl),
      sourceEnvironment: actual.sourceEnvironment || null,
      releaseCandidate: actual.releaseCandidate || null,
      evidenceOperator: actual.evidenceOperator || null,
      productionEquivalence: productionEquivalenceDiagnostics(
        actual,
        strictGate,
        "authenticated performance actual",
        process.env.DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
      ),
      failed: actual.failed ?? null,
      samples: actual.samples ?? null,
      p95: actual.p95 ?? null,
      uploadStatus: actual.upload?.status ?? null,
      uploadElapsedMs: actual.upload?.elapsedMs ?? null,
      endpointCount: Object.keys(actualEndpoints).length,
      shapeIssues: actualShapeIssues,
    } : {
      present: false,
      missing: actualRead.missing === true,
      invalid: actualRead.invalid || null,
      file: portablePath(actualRead.file),
    },
    baseline: baseline ? {
      present: true,
      baseUrl: baseline.baseUrl || null,
      localOnly: isLocalBaseUrl(baseline.baseUrl),
      baselineType: baseline.baselineType || null,
      acceptedAt: baseline.acceptedAt || null,
      acceptedBy: baseline.acceptedBy || null,
      sourceEnvironment: baseline.sourceEnvironment || null,
      sourceArtifact: baseline.sourceArtifact || null,
      releaseCandidate: baseline.releaseCandidate || null,
      evidenceOperator: baseline.evidenceOperator || null,
      p95: baseline.p95 ?? null,
      uploadElapsedMs: baseline.upload?.elapsedMs ?? null,
      endpointCount: Object.keys(baselineEndpoints).length,
      shapeIssues: baselineShapeIssues,
      metadataIssues: baselineMetadataIssues,
    } : {
      present: false,
      missing: baselineRead.missing === true,
      invalid: baselineRead.invalid || null,
      file: portablePath(baselineRead.file),
      requiredEnvKeys: [
        "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
        "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
        "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
        "DDD_RELEASE_CANDIDATE",
      ],
      action: "Run authenticated performance smoke against a production-equivalent URL, then promote the accepted actual with scripts/ddd-promote-performance-baseline.mjs.",
    },
    missingBaselineEndpoints,
    regressionIssues,
    baselinePromotion: promotion ? {
      present: true,
      status: promotion.status || null,
      sourceFile: portablePath(promotion.sourceFile),
      sourceSha256: promotion.sourceSha256 || null,
      outputFile: portablePath(promotion.outputFile),
      sourceArtifact: promotion.sourceArtifact || null,
      sourceEnvironment: promotion.sourceEnvironment || null,
      releaseCandidate: promotion.releaseCandidate || null,
      acceptedBy: promotion.acceptedBy || null,
      requiredEnvKeys: promotion.requiredEnvKeys || [],
      sourceActual: promotion.sourceActual || null,
      baseline: promotion.baseline || null,
      blockers: promotion.blockers || [],
    } : {
      present: false,
      missing: promotionRead.missing === true,
      invalid: promotionRead.invalid || null,
      file: portablePath(promotionRead.file),
    },
  };
  diagnostics.actionPlan = performanceActionPlan(diagnostics);
  return diagnostics;
}

function runtimeReadinessDiagnostics(readinessRead, strictGate) {
  const artifact = readinessRead.data || null;
  if (!artifact) {
    const diagnostics = {
      present: false,
      missing: readinessRead.missing === true,
      invalid: readinessRead.invalid || null,
      file: portablePath(readinessRead.file),
    };
    diagnostics.actionPlan = runtimeReadinessActionPlan(diagnostics);
    return diagnostics;
  }
  const summaryRows = Array.isArray(artifact.summary) ? artifact.summary : [];
  const byKey = new Map(summaryRows.map((item) => [`${item.context}:${item.suffix}`, item]));
  const contexts = runtimeReadinessContexts.map(([context]) => {
    const checks = runtimeReadinessSuffixes.map((suffix) => {
      const row = byKey.get(`${context}:${suffix}`);
      return {
        suffix,
        status: row?.status ?? null,
        artifact: row?.artifact || null,
        present: Boolean(row),
        ok: row?.status === 200 && Boolean(row?.artifact),
      };
    });
    return {
      context,
      checks,
      ready: checks.every((check) => check.ok),
      missing: checks.filter((check) => !check.present).map((check) => check.suffix),
      non200: checks.filter((check) => check.present && check.status !== 200).map((check) => ({
        suffix: check.suffix,
        status: check.status,
      })),
    };
  });
  const diagnostics = {
    present: true,
    baseUrl: artifact.baseUrl || null,
    localOnly: isLocalBaseUrl(artifact.baseUrl),
    sourceEnvironment: artifact.sourceEnvironment || null,
    releaseCandidate: artifact.releaseCandidate || null,
    evidenceOperator: artifact.evidenceOperator || null,
    checkedAt: artifact.checkedAt || null,
    productionEquivalence: artifact.productionEquivalence || null,
    expectedChecks: expectedRuntimeReadinessChecks().length,
    actualChecks: summaryRows.length,
    failures: artifact.failures || [],
    contractIssues: validateRuntimeReadinessArtifact(artifact, { strict: strictGate }),
    contexts,
  };
  diagnostics.actionPlan = runtimeReadinessActionPlan(diagnostics);
  return diagnostics;
}

function fileProcessingDiagnostics(fileRead, strictGate) {
  const artifact = fileRead.data || null;
  if (!artifact) {
    return {
      present: false,
      missing: fileRead.missing === true,
      invalid: fileRead.invalid || null,
      file: portablePath(fileRead.file),
    };
  }
  const tasks = Array.isArray(artifact.finalState?.tasks) ? artifact.finalState.tasks : [];
  const artifacts = Array.isArray(artifact.finalState?.artifacts) ? artifact.finalState.artifacts : [];
  const taskByType = new Map(tasks.map((task) => [task.taskType, task]));
  const artifactTypes = new Set(artifacts.map((item) => item.artifactType));
  return {
    present: true,
    status: artifact.status || null,
    baseUrl: artifact.baseUrl || null,
    localOnly: isLocalBaseUrl(artifact.baseUrl),
    sourceEnvironment: artifact.sourceEnvironment || null,
    releaseCandidate: artifact.releaseCandidate || null,
    evidenceOperator: artifact.evidenceOperator || null,
    productionEquivalence: productionEquivalenceDiagnostics(artifact, strictGate, "file processing E2E"),
    elapsedMs: artifact.elapsedMs ?? null,
    uploadElapsedMs: artifact.upload?.elapsedMs ?? null,
    fileId: artifact.upload?.fileId ?? null,
    jobRuns: artifact.jobRuns || [],
    requiredTasks: requiredFileProcessingTasks.map((taskType) => ({
      taskType,
      status: taskByType.get(taskType)?.status || "MISSING",
    })),
    requiredArtifacts: requiredFileProcessingArtifacts.map((artifactType) => ({
      artifactType,
      present: artifactTypes.has(artifactType),
    })),
    contractIssues: validateFileProcessingArtifact(artifact, { strict: strictGate }),
  };
}

function paymentWebhookDiagnostics(paymentRead, strictGate) {
  const artifact = paymentRead.data || null;
  if (!artifact) {
    return {
      present: false,
      missing: paymentRead.missing === true,
      invalid: paymentRead.invalid || null,
      file: portablePath(paymentRead.file),
    };
  }
  return {
    present: true,
    status: artifact.status || null,
    baseUrl: artifact.baseUrl || null,
    localOnly: isLocalBaseUrl(artifact.baseUrl),
    sourceEnvironment: artifact.sourceEnvironment || null,
    releaseCandidate: artifact.releaseCandidate || null,
    evidenceOperator: artifact.evidenceOperator || null,
    productionEquivalence: productionEquivalenceDiagnostics(artifact, strictGate, "payment webhook E2E"),
    elapsedMs: artifact.elapsedMs ?? null,
    providerConfigured: artifact.provider?.configured === true,
    orderStatus: artifact.finalState?.order?.status || null,
    webhooks: {
      first: summarizeWebhook(artifact.webhooks?.first),
      duplicate: summarizeWebhook(artifact.webhooks?.duplicate),
      nonceReplay: summarizeWebhook(artifact.webhooks?.nonceReplay),
      badSignature: summarizeWebhook(artifact.webhooks?.badSignature),
    },
    contractIssues: validatePaymentWebhookArtifact(artifact, { strict: strictGate }),
  };
}

function summarizeWebhook(webhook) {
  return {
    eventId: webhook?.eventId || null,
    processed: webhook?.processed ?? null,
    signatureValid: webhook?.signatureValid ?? null,
    elapsedMs: webhook?.elapsedMs ?? null,
    processMessage: webhook?.processMessage || null,
  };
}

function jobE2eDiagnostics(jobRead, strictGate) {
  const artifact = jobRead.data || null;
  if (!artifact) {
    return {
      present: false,
      missing: jobRead.missing === true,
      invalid: jobRead.invalid || null,
      file: portablePath(jobRead.file),
    };
  }
  const byName = new Map((artifact.endpoints || []).map((endpoint) => [endpoint.name, endpoint]));
  return {
    present: true,
    baseUrl: artifact.baseUrl || null,
    localOnly: isLocalBaseUrl(artifact.baseUrl),
    sourceEnvironment: artifact.sourceEnvironment || null,
    releaseCandidate: artifact.releaseCandidate || null,
    evidenceOperator: artifact.evidenceOperator || null,
    productionEquivalence: productionEquivalenceDiagnostics(artifact, strictGate, "job E2E"),
    unauthorizedStatus: artifact.unauthorized?.status ?? null,
    summary: artifact.summary || {},
    endpoints: requiredJobSmokeEndpoints.map((required) => {
      const endpoint = byName.get(required.name);
      return {
        name: required.name,
        status: endpoint?.status ?? null,
        dataType: endpoint ? typeof endpoint.data : null,
        expectedDataType: required.dataType,
        elapsedMs: endpoint?.elapsedMs ?? null,
        present: Boolean(endpoint),
      };
    }),
    outboxOwnership: artifact.diagnostics?.outboxOwnership || null,
    contractIssues: validateJobE2eArtifact(artifact, { strict: strictGate }),
  };
}

function explainDiagnostics(strictGate) {
  const explainDir = process.env.DDD_EXPLAIN_DIR
    ? path.resolve(process.env.DDD_EXPLAIN_DIR)
    : path.join(repoRoot, "tmp", "ddd-explain");
  if (!fs.existsSync(explainDir)) {
    return {
      present: false,
      dir: portablePath(explainDir),
      files: [],
      missingRequiredFiles: [],
      legacyPlanImports: [],
      issues: [`missing explain directory ${explainDir}`],
      actionPlan: explainActionPlan({
        issues: [{
          file: "explain-directory",
          scope: "directory",
          detail: `missing explain directory ${explainDir}`,
        }],
      }),
    };
  }
  const files = fs.readdirSync(explainDir)
    .filter((file) => file.endsWith(".json"))
    .sort();
  const issueRows = [];
  const fileRows = [];
  for (const file of files) {
    const fullPath = path.join(explainDir, file);
    try {
      const parsed = JSON.parse(fs.readFileSync(fullPath, "utf8"));
      const issues = validateExplainArtifact(file, parsed, { strict: strictGate });
      issueRows.push(...issues.map((issue) => ({
        file,
        scope: issue.scope,
        detail: issue.detail,
      })));
      fileRows.push({
        file,
        queryName: parsed.queryName || null,
        sourceEnvironment: parsed.sourceEnvironment || null,
        releaseCandidate: parsed.releaseCandidate || null,
        evidenceOperator: parsed.evidenceOperator || null,
        generatedAt: parsed.generatedAt || null,
        legacyPlanImport: parsed.legacyPlanImport === true,
        issueCount: issues.length,
      });
    } catch (error) {
      issueRows.push({
        file,
        scope: "json",
        detail: `${file} invalid JSON: ${error.message}`,
      });
      fileRows.push({
        file,
        queryName: null,
        sourceEnvironment: null,
        releaseCandidate: null,
        evidenceOperator: null,
        generatedAt: null,
        legacyPlanImport: false,
        issueCount: 1,
      });
    }
  }
  const missingRequiredFiles = missingRequiredExplainFiles(files);
  return {
    present: true,
    dir: portablePath(explainDir),
    files: fileRows,
    fileCount: files.length,
    missingRequiredFiles,
    legacyPlanImports: fileRows.filter((file) => file.legacyPlanImport).map((file) => file.file),
    issues: issueRows,
    actionPlan: explainActionPlan({
      missingRequiredFiles,
      legacyPlanImports: fileRows.filter((file) => file.legacyPlanImport).map((file) => file.file),
      issues: issueRows,
    }),
  };
}

const gate = readJson("release/release-evidence-gate.json");
const manifest = readJson("release/evidence-manifest.json");
const docker = readJson("build/docker-image-evidence.json");
const releaseEnvLint = readJson("release/release-env-lint.json");
const releaseConfig = readJson("config/release-config-evidence.json");
const authenticatedPerformanceActual = readJson("performance/authenticated-runtime-actual.json");
const authenticatedPerformanceBaseline = readJson("performance/authenticated-runtime-baseline.json");
const authenticatedPerformanceBaselinePromotion = readJson("performance/authenticated-runtime-baseline-promotion.json");
const runtimeReadiness = readJson("readiness/summary.json");
const fileProcessing = readJson("file/file-processing-e2e.json");
const paymentWebhook = readJson("payment/payment-webhook-e2e.json");
const jobE2e = readJson("jobs/job-e2e-smoke.json");
const frontendSmoke = readJson("frontend/frontend-smoke.json");
const aiRuntime = readJson("ai/ai-runtime-drill.json");
const migration = readJson("migration/migration-evidence.json");
const rollback = readJson("rollback/rollback-drill.json");
const orchestrator = readJson("release/orchestrator-report.json");
const ownerQueueRunReport = readJson("release/release-final-owner-queue-run-report.json");
const ownerQueueEnvInitReceipt = readJson("release/release-final-owner-queue-env-init-receipt.json");
const explainGateReport = readJson("release/explain-gate-report.json");

const inputArtifacts = {
  releaseGate: artifactInputSummary(gate),
  manifest: artifactInputSummary(manifest),
  releaseEnvLint: artifactInputSummary(releaseEnvLint),
  releaseConfig: artifactInputSummary(releaseConfig),
  docker: artifactInputSummary(docker),
  migration: artifactInputSummary(migration),
  runtimeReadiness: artifactInputSummary(runtimeReadiness),
  authenticatedPerformanceActual: artifactInputSummary(authenticatedPerformanceActual),
  authenticatedPerformanceBaseline: artifactInputSummary(authenticatedPerformanceBaseline),
  authenticatedPerformanceBaselinePromotion: artifactInputSummary(authenticatedPerformanceBaselinePromotion),
  fileProcessing: artifactInputSummary(fileProcessing),
  paymentWebhook: artifactInputSummary(paymentWebhook),
  jobE2e: artifactInputSummary(jobE2e),
  frontendSmoke: artifactInputSummary(frontendSmoke),
  aiRuntime: artifactInputSummary(aiRuntime),
  rollback: artifactInputSummary(rollback),
  orchestrator: artifactInputSummary(orchestrator),
  ownerQueueRunReport: artifactInputSummary(ownerQueueRunReport, { statusPath: "reportStatus" }),
  ownerQueueEnvInitReceipt: artifactInputSummary(ownerQueueEnvInitReceipt),
  explainGateReport: artifactInputSummary(explainGateReport),
};

const blockers = gate.data?.blockers || [];
const gateBlockerEntries = releaseGateBlockerEntries(gate.data);
const releaseGateContractIssues = gate.data ? validateReleaseGateArtifact(gate.data) : [];
const releaseConfigContractIssues = releaseConfig.data ? validateReleaseConfigArtifact(releaseConfig.data) : [];
  const releaseEnvLintDiagnostics = releaseEnvLint.data ? {
  status: releaseEnvLint.data.status,
  envFile: releaseEnvLint.data.envFile || null,
  inputKind: releaseEnvLint.data.inputKind || null,
  generatedMissingTemplate: releaseEnvLint.data.generatedMissingTemplate === true,
  envFileSecurity: releaseEnvLint.data.envFileSecurity || null,
  missingEnv: releaseEnvLint.data.missingEnv || null,
  summary: releaseEnvLint.data.summary || {},
  keys: releaseEnvLint.data.keys || [],
  canonicalKeys: releaseEnvLint.data.canonicalKeys || [],
  duplicateKeys: releaseEnvLint.data.duplicateKeys || [],
  unresolvedTemplateKeys: releaseEnvLint.data.unresolvedTemplateKeys || [],
  canonicalUnresolvedTemplateKeys: releaseEnvLint.data.canonicalUnresolvedTemplateKeys || [],
  canonicalMissingEnvKeys: releaseEnvLint.data.canonicalMissingEnvKeys || [],
  canonicalReleaseConfigBlockerKeys: releaseEnvLint.data.canonicalReleaseConfigBlockerKeys || [],
  releaseConfigBlockerDetails: releaseEnvLint.data.releaseConfigBlockerDetails || [],
  blockers: releaseEnvLint.data.blockers || [],
  warnings: releaseEnvLint.data.warnings || [],
} : null;
if (releaseEnvLintDiagnostics) {
  releaseEnvLintDiagnostics.actionPlan = releaseEnvLintActionPlan(releaseEnvLintDiagnostics);
}
const strictGate = gate.data?.strict === true;
const gatePresent = !gate.missing && !gate.invalid;
const gateBlockerCount = gate.data?.summary?.blockers ?? blockers.length;
const readinessStatus = !gatePresent || releaseGateContractIssues.length > 0 || releaseConfigContractIssues.length > 0 || gateBlockerCount > 0
  ? "NOT_READY"
  : (strictGate ? "READY" : "ADVISORY");
const actions = gateBlockerEntries.map((entry) => ({
  blocker: entry.blocker,
  check: entry.check,
  detail: entry.detail,
  file: entry.file,
  structured: entry.structured,
  ...classify(entry),
}));
const missingManifestArtifacts = manifest.data?.blockers || [];
const businessE2eDiagnostics = {
  fileProcessing: fileProcessingDiagnostics(fileProcessing, strictGate),
  paymentWebhook: paymentWebhookDiagnostics(paymentWebhook, strictGate),
  jobE2e: jobE2eDiagnostics(jobE2e, strictGate),
};
businessE2eDiagnostics.actionPlan = businessRuntimeActionPlan(businessE2eDiagnostics);
const dockerDiagnostics = docker.data ? {
  status: docker.data.status,
  summary: docker.data.summary || {},
  blockers: docker.data.blockers || [],
  remediation: docker.data.remediation || null,
  dockerCommand: docker.data.dockerCommand || "docker",
  cliStatus: docker.data.preflight?.version?.status ?? null,
  cliVersion: docker.data.preflight?.version?.stdoutTail || null,
  daemonStatus: docker.data.preflight?.info?.status ?? null,
  daemonError: docker.data.preflight?.info?.stderrTail || docker.data.preflight?.info?.error || null,
  images: (docker.data.images || [])
    .map((image) => ({
      name: image.name,
      status: image.status || null,
      dockerfile: image.dockerfile || null,
      dockerfileSha256: image.dockerfileSha256 || null,
      tag: image.tag || null,
      expectedExposedPort: image.expectedExposedPort || null,
      requireNonRootUser: image.requireNonRootUser === true,
      staticDockerfile: image.staticDockerfile || null,
      skipReason: image.skipReason || null,
      buildStatus: image.build?.status ?? null,
      inspectStatus: image.inspect?.command?.status ?? null,
      imageUser: image.inspect?.image?.user ?? null,
      exposedPorts: image.inspect?.image?.exposedPorts || [],
      blockers: image.blockers || [],
      action: image.status === "SKIPPED"
        ? `Start Docker daemon or run DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs in a CI runner with ${docker.data.dockerCommand || "docker"} available.`
        : "Inspect Docker build blockers and rebuild the image evidence artifact.",
    })),
  skippedImages: (docker.data.images || [])
    .filter((image) => image.status === "SKIPPED")
    .map((image) => ({
      name: image.name,
      skipReason: image.skipReason || null,
      blockers: image.blockers || [],
    })),
} : null;
if (dockerDiagnostics) {
  dockerDiagnostics.actionPlan = dockerActionPlan(dockerDiagnostics);
}
const migrationDiagnostics = migration.data ? {
  status: migration.data.status,
  runtime: migration.data.runtime || {},
  runtimeReady: migration.data.runtimeReady === true || migration.data.summary?.runtimeReady === true,
  runtimeProofs: migration.data.runtimeProofs || [],
  runtimeDiagnostics: migration.data.runtimeDiagnostics || [],
} : null;
if (migrationDiagnostics) {
  migrationDiagnostics.actionPlan = migrationActionPlan(migrationDiagnostics);
}

const summary = {
  generatedAt: new Date().toISOString(),
  status: readinessStatus,
  inputArtifacts,
  gate: {
    present: gatePresent,
    strict: strictGate,
    blockers: gateBlockerCount,
    warnings: gate.data?.summary?.warnings || 0,
  },
	  manifest: {
	    present: !manifest.missing && !manifest.invalid,
	    status: manifest.data?.status || null,
	    summary: manifest.data?.summary || {},
	    optionalOwnerQueueRunReport: manifest.data?.artifacts?.find?.((artifact) => artifact.relativePath === "release/release-final-owner-queue-run-report.json") || null,
	    missingArtifacts: missingManifestArtifacts,
	    actionPlan: manifestActionPlan(missingManifestArtifacts),
	  },
  diagnostics: {
    runtimeReadiness: runtimeReadinessDiagnostics(runtimeReadiness, strictGate),
    businessE2e: businessE2eDiagnostics,
    authenticatedPerformance: performanceDiagnostics(authenticatedPerformanceActual, authenticatedPerformanceBaseline, authenticatedPerformanceBaselinePromotion, strictGate),
    docker: dockerDiagnostics,
    releaseEnvLint: releaseEnvLintDiagnostics,
    ownerQueueEnvInitReceipt: ownerQueueEnvInitReceipt.data ? {
      generatedAt: ownerQueueEnvInitReceipt.data.generatedAt || null,
      templatePath: ownerQueueEnvInitReceipt.data.templatePath || null,
      targetPath: ownerQueueEnvInitReceipt.data.targetPath || null,
      targetModeOctal: ownerQueueEnvInitReceipt.data.targetModeOctal || null,
      permissionSafe: ownerQueueEnvInitReceipt.data.permissionSafe === true,
      unresolvedTemplateKeyCount: ownerQueueEnvInitReceipt.data.unresolvedTemplateKeyCount ?? null,
      unresolvedTemplateKeys: ownerQueueEnvInitReceipt.data.unresolvedTemplateKeys || [],
      nextCommands: ownerQueueEnvInitReceipt.data.nextCommands || [],
    } : null,
    releaseConfig: releaseConfig.data ? {
      status: releaseConfig.data.status,
      envFile: releaseConfig.data.envFile || null,
      inputKind: releaseConfig.data.inputKind || null,
      generatedMissingTemplate: releaseConfig.data.generatedMissingTemplate === true,
      envFileExists: releaseConfig.data.envFileExists === true,
      summary: releaseConfig.data.summary || {},
      coverageMatrix: releaseConfig.data.coverageMatrix || [],
      blockers: releaseConfig.data.blockers || [],
      blockerDetails: releaseConfig.data.blockerDetails || [],
      blockersByGroup: releaseConfig.data.blockersByGroup || {},
      blockersByOwner: releaseConfig.data.blockersByOwner || {},
      actionPlan: releaseConfigActionPlan(releaseConfig.data.blockerDetails || []),
      contractIssues: releaseConfigContractIssues,
      warnings: releaseConfig.data.warnings || [],
    } : null,
    releaseGate: {
      contractIssues: releaseGateContractIssues,
      blockerDetails: gateBlockerEntries,
      structuredBlockers: gateBlockerEntries.filter((entry) => entry.structured).length,
    },
    frontendSmoke: frontendSmoke.data ? {
      status: frontendSmoke.data.status,
      baseUrl: frontendSmoke.data.baseUrl || null,
      https: typeof frontendSmoke.data.baseUrl === "string" && frontendSmoke.data.baseUrl.startsWith("https://"),
      localOnly: isLocalBaseUrl(frontendSmoke.data.baseUrl),
      expectDeployed: frontendSmoke.data.expectDeployed === true,
      sourceEnvironment: frontendSmoke.data.sourceEnvironment || null,
      releaseCandidate: frontendSmoke.data.releaseCandidate || null,
      evidenceOperator: frontendSmoke.data.evidenceOperator || null,
      productionEquivalence: productionEquivalenceDiagnostics(
        frontendSmoke.data,
        strictGate,
        "frontend smoke",
        process.env.DDD_FRONTEND_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
      ),
      inputFile: frontendSmoke.data.inputFile || null,
      summary: frontendSmoke.data.summary || {},
      blockers: frontendSmoke.data.blockers || [],
      staticSpecCoverage: frontendSmoke.data.diagnostics?.staticSpecCoverage || null,
      playwrightReport: frontendSmoke.data.diagnostics?.playwrightReport || null,
      actionPlan: frontendSmokeActionPlan(frontendSmoke.data, strictGate),
      missingFlows: (frontendSmoke.data.flowCoverage || [])
        .filter((flow) => flow.status === "missing")
        .map((flow) => ({
          flow: flow.flow,
          reason: flow.reason || null,
          action: "Run deployed Playwright @smoke coverage for this flow and regenerate frontend-smoke.json.",
        })),
      passedFlows: (frontendSmoke.data.flowCoverage || [])
        .filter((flow) => flow.status === "passed")
        .map((flow) => ({
          flow: flow.flow,
          matchedTitle: flow.matchedTitle || null,
        })),
    } : null,
    aiRuntime: aiRuntime.data ? {
      status: aiRuntime.data.status,
      baseUrl: aiRuntime.data.baseUrl || null,
      localOnly: isLocalBaseUrl(aiRuntime.data.baseUrl),
      sourceEnvironment: aiRuntime.data.sourceEnvironment || null,
      releaseCandidate: aiRuntime.data.releaseCandidate || null,
      evidenceOperator: aiRuntime.data.evidenceOperator || null,
      productionEquivalence: productionEquivalenceDiagnostics(
        aiRuntime.data,
        strictGate,
        "AI runtime drill",
        process.env.DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
      ),
      summary: aiRuntime.data.summary || {},
      remoteEvidence: aiRuntime.data.remoteEvidence || {},
      failures: aiRuntime.data.failures || [],
      failureDetails: aiRuntime.data.failureDetails || [],
      failureCategories: aiRuntime.data.summary?.failureCategories || countBy(aiRuntime.data.failureDetails || [], "category"),
      failureOwners: countBy(aiRuntime.data.failureDetails || [], "owner"),
      actionPlan: aiRuntimeActionPlan({
        status: aiRuntime.data.status,
        baseUrl: aiRuntime.data.baseUrl || null,
        localOnly: isLocalBaseUrl(aiRuntime.data.baseUrl),
        productionEquivalence: productionEquivalenceDiagnostics(
          aiRuntime.data,
          strictGate,
          "AI runtime drill",
          process.env.DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE || process.env.DDD_DEPLOYMENT_EVIDENCE || "",
        ),
        remoteEvidence: aiRuntime.data.remoteEvidence || {},
        failureDetails: aiRuntime.data.failureDetails || [],
      }),
    } : null,
    migration: migrationDiagnostics,
    explain: explainDiagnostics(strictGate),
    rollback: rollback.data ? {
      status: rollback.data.status,
      environment: rollback.data.environment || null,
      releaseVersion: rollback.data.releaseVersion || null,
      summary: rollback.data.summary || buildRollbackDrillSummary(rollback.data),
      contextDiagnostics: rollback.data.contextDiagnostics || [],
      actionPlan: rollbackActionPlan(rollback.data.contextDiagnostics || []),
      contexts: (rollback.data.contexts || []).map((context) => ({
        context: context.context,
        status: context.status,
        rollbackAction: context.rollbackAction || null,
        drillEvidence: context.drillEvidence || context.deferralEvidence || null,
      })),
    } : null,
    orchestrator: orchestrator.data ? {
      mode: orchestrator.data.mode || null,
      strict: orchestrator.data.strict === true,
      summary: orchestrator.data.summary || {},
      preflight: orchestrator.data.preflight || null,
      selectedStepCount: (orchestrator.data.selectedSteps || []).length,
      executedResultCount: (orchestrator.data.results || []).length,
      selectedSteps: (orchestrator.data.selectedSteps || []).map((step) => ({
        id: step.id,
        label: step.label || null,
        optional: step.optional === true,
        enabled: step.enabled !== false,
        runtime: step.runtime === true,
        heavy: step.heavy === true,
        envKeys: step.envKeys || [],
      })),
      results: (orchestrator.data.results || []).map((result) => ({
        id: result.id,
        status: result.status || null,
        skipped: result.skipped === true,
        elapsedMs: result.elapsedMs ?? null,
        exitCode: result.exitCode ?? null,
      })),
      missingResults: orchestratorMissingResults(orchestrator.data),
      action: orchestrator.data.mode !== "run"
        ? "Run `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict` after resolving preflight blockers."
        : null,
      blockerChecks: (orchestrator.data.preflight?.checks || [])
        .filter((check) => check.status === "BLOCKER")
        .map((check) => ({
          id: check.id,
          detail: check.detail || null,
          envKeys: check.envKeys || [],
        })),
    } : null,
  },
  actions,
  actionsByCategory: groupBy(actions, "category"),
  actionsByOwner: groupBy(actions, "owner"),
};

if (summary.diagnostics.orchestrator) {
  summary.diagnostics.orchestrator.actionPlan = orchestratorActionPlan(summary.diagnostics.orchestrator);
}
summary.ownerActionRollup = ownerActionRollup(summary);
summary.sourceActionRollup = sourceActionRollup(summary);
summary.releaseActionPriority = releaseActionPriority(summary);
summary.releaseActionBatches = releaseActionBatches(summary);

summary.diagnostics.readinessSummary = {
  contractIssues: validateReadinessSummary(summary),
};

function appendProductionEquivalence(lines, prefix, productionEquivalence) {
  if (!productionEquivalence) {
    return;
  }
  lines.push(`- ${prefix}ProductionEquivalence: strict=${productionEquivalence.strict === true} https=${productionEquivalence.https === true} localOnly=${productionEquivalence.localOnly === true} deploymentEvidence=${productionEquivalence.deploymentEvidence || "missing"}`);
  for (const issue of productionEquivalence.issues || []) {
    lines.push(`- ${prefix}ProductionEquivalenceIssue: ${issue}`);
  }
}

function markdown(summary) {
  const lines = [
    "# DDD Release Readiness Summary",
    "",
    `Generated at: ${summary.generatedAt}`,
    `Status: ${summary.status}`,
    `Release gate mode: ${releaseGateMode(summary)}`,
    `Release gate blockers: ${summary.gate.blockers}`,
    `Release gate warnings: ${summary.gate.warnings}`,
    "",
	    "## Missing Manifest Artifacts",
	    "",
	  ];
	  lines.push(`- Manifest status: ${summary.manifest.status || "missing"}`);
	  lines.push(`- Manifest optional artifacts: ${summary.manifest.summary?.optionalArtifacts ?? 0}`);
	  if (summary.manifest.optionalOwnerQueueRunReport) {
	    const report = summary.manifest.optionalOwnerQueueRunReport;
	    const contractIssues = (report.contractIssues || []).length;
	    lines.push(`- Optional owner queue run report: ${report.status || "unknown"}; bytes=${report.bytes || 0}; contractIssues=${contractIssues}`);
	  } else {
	    lines.push("- Optional owner queue run report: not present");
	  }
  const envInitReceiptInput = summary.inputArtifacts?.ownerQueueEnvInitReceipt;
  const envInitReceipt = summary.diagnostics?.ownerQueueEnvInitReceipt;
  if (envInitReceiptInput?.present === true && envInitReceipt) {
    lines.push(`- Owner queue env init receipt: PRESENT; permissionSafe=${envInitReceipt.permissionSafe}; mode=${envInitReceipt.targetModeOctal || "missing"}; unresolvedTemplateKeys=${envInitReceipt.unresolvedTemplateKeyCount ?? "missing"}`);
  } else {
    lines.push("- Owner queue env init receipt: not present");
  }
	  if (summary.manifest.missingArtifacts.length === 0) {
	    lines.push("- None");
  } else {
    for (const blocker of summary.manifest.missingArtifacts) {
      lines.push(`- ${blocker}`);
    }
    for (const [owner, plan] of Object.entries(summary.manifest.actionPlan || {})) {
      lines.push(`- actionPlan: owner=${owner} pendingItems=${plan.pendingItems ?? 0} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
      for (const item of plan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const artifact = item.artifact ? `; artifact=${item.artifact}` : "";
        lines.push(`- manifestAction: ${item.id}; owner=${item.owner || owner}; reason=${item.reason || "missing"}${envKeys}${artifact}; action=${item.action || "missing"}`);
      }
    }
  }
  lines.push("", "## Owner Action Rollup", "");
  const ownerRollup = summary.ownerActionRollup || {};
  if (Object.keys(ownerRollup).length === 0) {
    lines.push("- None");
  } else {
    for (const [owner, plan] of Object.entries(ownerRollup)) {
      const sources = Object.entries(plan.sources || {}).map(([source, count]) => `${source}=${count}`).join(",");
      const collapsedSources = Object.entries(plan.collapsedSources || {})
        .map(([source, count]) => `${source}=${count}`)
        .join(",");
      lines.push(`- owner=${owner} pendingItems=${plan.pendingItems ?? 0} collapsedItems=${plan.collapsedItems ?? 0} sources=${sources || "none"} collapsedSources=${collapsedSources || "none"} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
      for (const item of plan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        lines.push(`  - ownerAction: source=${item.source}; id=${item.id}; reason=${item.reason || "missing"}${envKeys}; action=${item.action || "missing"}`);
      }
      for (const item of plan.collapsed || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const coveredBy = item.coveredBy ? `${item.coveredBy.source}:${item.coveredBy.id}` : "unknown";
        lines.push(`  - ownerActionCollapsed: source=${item.source}; id=${item.id}; coveredBy=${coveredBy}; reason=${item.reason || "missing"}${envKeys}`);
      }
    }
  }
  lines.push("", "## Actions By Category", "");
  for (const [category, items] of Object.entries(summary.actionsByCategory)) {
    lines.push(`### ${category}`);
    const seen = new Set();
    for (const item of items) {
      const key = `${item.owner}|${item.action}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      lines.push(`- Owner: ${item.owner}`);
      lines.push(`  Action: ${item.action}`);
    }
    lines.push("");
  }
  lines.push("## Runtime Readiness Diagnostics", "");
  const readiness = summary.diagnostics.runtimeReadiness;
  if (!readiness?.present) {
    lines.push(`- missing readiness summary; file=${readiness?.file || "missing"}`);
  } else {
    lines.push(`- baseUrl: ${readiness.baseUrl || "missing"}`);
    lines.push(`- localOnly: ${readiness.localOnly === true}`);
    if (readiness.productionEquivalence) {
      lines.push(`- productionEquivalence: strict=${readiness.productionEquivalence.strict === true} https=${readiness.productionEquivalence.https === true} localOnly=${readiness.productionEquivalence.localOnly === true} deploymentEvidence=${readiness.productionEquivalence.deploymentEvidence || "missing"}`);
      for (const issue of readiness.productionEquivalence.issues || []) {
        lines.push(`- productionEquivalenceIssue: ${issue}`);
      }
    }
    lines.push(`- checks: ${readiness.actualChecks}/${readiness.expectedChecks}`);
    lines.push(`- failures: ${(readiness.failures || []).length}`);
    for (const issue of readiness.contractIssues || []) {
      lines.push(`- contractIssue: ${issue}`);
    }
    for (const context of readiness.contexts || []) {
      const missing = context.missing.length > 0 ? `; missing=${context.missing.join(",")}` : "";
      const non200 = context.non200.length > 0
        ? `; non200=${context.non200.map((item) => `${item.suffix}:${item.status}`).join(",")}`
        : "";
      lines.push(`- ${context.context}: ready=${context.ready === true}${missing}${non200}`);
    }
    if (readiness.actionPlan) {
      lines.push(`- actionPlan: owner=${readiness.actionPlan.owner || "release-infra"} pendingItems=${readiness.actionPlan.pendingItems ?? 0}`);
      for (const item of readiness.actionPlan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const missing = (item.missing || []).length > 0 ? `; missing=${item.missing.join(",")}` : "";
        const non200 = (item.non200 || []).length > 0 ? `; non200=${item.non200.map((entry) => `${entry.suffix}:${entry.status}`).join(",")}` : "";
        lines.push(`- runtimeAction: ${item.id}; owner=${item.owner || "release-infra"}; reason=${item.reason || "missing"}${envKeys}${missing}${non200}; action=${item.action || "missing"}`);
      }
    }
  }
  lines.push("", "## Release Env Lint", "");
  const envLint = summary.diagnostics.releaseEnvLint;
  if (!envLint) {
    lines.push("- missing release env lint artifact");
  } else {
    lines.push(`- status: ${envLint.status || "missing"} inputKind=${envLint.inputKind || "unknown"} envFile=${portableDisplayPath(envLint.envFile) || "missing"} keys=${envLint.summary?.keys ?? "missing"} blockers=${envLint.summary?.blockers ?? 0} primaryBlockers=${envLint.summary?.primaryBlockers ?? envLint.summary?.blockers ?? 0}`);
    if (envLint.generatedMissingTemplate) {
      lines.push("- generatedMissingTemplate: true; provide a completed DDD_RELEASE_ENV_FILE before strict release evidence can pass");
    }
    const envFileSecurity = envLint.envFileSecurity;
    if (envFileSecurity) {
      lines.push(`- envFileSecurity: checked=${envFileSecurity.checked === true} mode=${envFileSecurity.modeOctal || "missing"} permissionSafe=${envFileSecurity.permissionSafe === true} permissionCheckSkipped=${envFileSecurity.permissionCheckSkipped === true} reason=${envFileSecurity.reason || "missing"} requiredMode=${envFileSecurity.requiredMode || "600"}`);
    }
    lines.push(`- unresolvedTemplateKeys: ${envLint.summary?.unresolvedTemplateKeys ?? 0}`);
    lines.push(`- releaseConfigBlockers: ${envLint.summary?.releaseConfigBlockers ?? 0}`);
    lines.push(`- releaseConfigBlockersFromPlaceholders: ${envLint.summary?.releaseConfigBlockersFromPlaceholders ?? 0}`);
    lines.push(`- releaseConfigBlockersAfterPlaceholders: ${envLint.summary?.releaseConfigBlockersAfterPlaceholders ?? envLint.summary?.releaseConfigBlockers ?? 0}`);
    const plan = envLint.actionPlan;
    if (plan) {
      lines.push(`- actionPlan: owner=${plan.owner || "release-infra"} pendingItems=${plan.pendingItems ?? 0} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
      for (const item of plan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        lines.push(`- envLintAction: ${item.id}; owner=${item.owner || plan.owner}; reason=${item.reason || "missing"}${envKeys}; action=${item.action || "missing"}`);
      }
    }
  }
  lines.push("", "## Release Config Blockers", "");
  const configBlockers = summary.diagnostics.releaseConfig?.blockers || [];
  const configSummary = summary.diagnostics.releaseConfig?.summary || {};
  if (summary.diagnostics.releaseConfig) {
    const config = summary.diagnostics.releaseConfig;
    lines.push(`- status: ${config.status || "missing"} inputKind=${config.inputKind || "unknown"} envFile=${portableDisplayPath(config.envFile) || "missing"} envFileExists=${config.envFileExists === true}`);
    if (config.generatedMissingTemplate) {
      lines.push("- generatedMissingTemplate: true; provide a completed DDD_RELEASE_ENV_FILE before release config evidence can pass");
    }
    lines.push(`- blockerSummary: blockers=${configSummary.blockers ?? configBlockers.length} primaryBlockers=${configSummary.primaryBlockers ?? configSummary.blockers ?? configBlockers.length} fromPlaceholders=${configSummary.releaseConfigBlockersFromPlaceholders ?? 0} afterPlaceholders=${configSummary.releaseConfigBlockersAfterPlaceholders ?? configSummary.blockers ?? configBlockers.length}`);
    lines.push(`- coverage: required=${configSummary.requiredChecks ?? "missing"} runtimePresent=${configSummary.runtimePresentRequiredChecks ?? "missing"} envFile=${configSummary.envFileCoveredRequiredChecks ?? "missing"} template=${configSummary.templateCoveredRequiredChecks ?? "missing"} workflow=${configSummary.workflowCoveredRequiredChecks ?? "missing"}`);
    const missingRuntime = (summary.diagnostics.releaseConfig.coverageMatrix || [])
      .filter((entry) => entry.required && entry.runtimePresent !== true);
    lines.push(`- missingRuntimeRequiredChecks: ${missingRuntime.length}`);
  }
  if (configBlockers.length === 0) {
    lines.push("- None");
  } else {
    const byOwner = Object.entries(summary.diagnostics.releaseConfig?.blockersByOwner || {});
    const byGroup = Object.entries(summary.diagnostics.releaseConfig?.blockersByGroup || {});
    if (byOwner.length > 0) {
      lines.push(`- owners: ${byOwner.map(([owner, count]) => `${owner}=${count}`).join(", ")}`);
    }
    if (byGroup.length > 0) {
      lines.push(`- groups: ${byGroup.map(([group, count]) => `${group}=${count}`).join(", ")}`);
    }
    const actionPlan = summary.diagnostics.releaseConfig?.actionPlan || {};
    for (const [owner, plan] of Object.entries(actionPlan)) {
      const envKeys = markdownEnvKeysSuffix(plan.envKeys);
      lines.push(`- ownerPlan: ${owner} missingChecks=${plan.missingChecks}${envKeys}`);
      appendMarkdownEnvKeyDetails(lines, plan.envKeys);
    }
    const details = summary.diagnostics.releaseConfig?.blockerDetails || [];
    if (details.length > 0) {
      for (const detail of details) {
        const envKeys = markdownEnvKeysSuffix(detail.envKeys);
        lines.push(`- [${detail.owner || "unknown"}][${detail.group || "unknown"}] ${detail.check || "unknown"}: ${detail.reason || detail.blocker || "missing reason"}${envKeys}`);
        appendMarkdownEnvKeyDetails(lines, detail.envKeys);
      }
    } else {
      for (const blocker of configBlockers) {
        lines.push(`- ${blocker}`);
      }
    }
  }
  for (const issue of summary.diagnostics.releaseConfig?.contractIssues || []) {
    lines.push(`- configContractIssue: ${issue}`);
  }
  lines.push("", "## Authenticated Performance Diagnostics", "");
  const performance = summary.diagnostics.authenticatedPerformance;
  if (!performance?.actual?.present) {
    lines.push(`- actual: missing; file=${performance?.actual?.file || "missing"}`);
  } else {
    lines.push(`- actualBaseUrl: ${performance.actual.baseUrl || "missing"}`);
    lines.push(`- actualLocalOnly: ${performance.actual.localOnly === true}`);
    appendProductionEquivalence(lines, "authenticatedPerformanceActual", performance.actual.productionEquivalence);
    lines.push(`- actualFailed: ${performance.actual.failed ?? "missing"}`);
    lines.push(`- actualP95: ${performance.actual.p95 ?? "missing"}`);
    lines.push(`- actualUpload: status=${performance.actual.uploadStatus ?? "missing"} elapsedMs=${performance.actual.uploadElapsedMs ?? "missing"}`);
    lines.push(`- actualEndpointCount: ${performance.actual.endpointCount}`);
    for (const issue of performance.actual.shapeIssues || []) {
      lines.push(`- actualShapeIssue: ${issue}`);
    }
  }
  if (!performance?.baseline?.present) {
    const envKeys = (performance?.baseline?.requiredEnvKeys || []).join(",");
    lines.push(`- baseline: missing; file=${performance?.baseline?.file || "missing"}; envKeys=${envKeys}`);
    lines.push(`- baselineAction: ${performance?.baseline?.action || "missing"}`);
  } else {
    lines.push(`- baselineBaseUrl: ${performance.baseline.baseUrl || "missing"}`);
    lines.push(`- baselineLocalOnly: ${performance.baseline.localOnly === true}`);
    lines.push(`- baselineP95: ${performance.baseline.p95 ?? "missing"}`);
    lines.push(`- baselineUploadElapsedMs: ${performance.baseline.uploadElapsedMs ?? "missing"}`);
    lines.push(`- baselineEndpointCount: ${performance.baseline.endpointCount}`);
    for (const issue of performance.baseline.shapeIssues || []) {
      lines.push(`- baselineShapeIssue: ${issue}`);
    }
    for (const issue of performance.baseline.metadataIssues || []) {
      lines.push(`- baselineMetadataIssue: ${issue}`);
    }
  }
  if (performance?.baselinePromotion?.present) {
    const promotion = performance.baselinePromotion;
    lines.push(`- baselinePromotion: status=${promotion.status || "missing"} sourceFile=${promotion.sourceFile || "missing"} outputFile=${promotion.outputFile || "missing"}`);
    lines.push(`- baselinePromotionSource: sourceArtifact=${promotion.sourceArtifact || "missing"} sourceSha256=${promotion.sourceSha256 || "missing"}`);
    lines.push(`- baselinePromotionEnv: acceptedBy=${promotion.acceptedBy || "missing"} sourceEnvironment=${promotion.sourceEnvironment || "missing"} releaseCandidate=${promotion.releaseCandidate || "missing"}`);
    if (promotion.sourceActual) {
      lines.push(`- baselinePromotionActual: localOnly=${promotion.sourceActual.localOnly === true} failed=${promotion.sourceActual.failed ?? "missing"} p95=${promotion.sourceActual.p95 ?? "missing"} endpointCount=${promotion.sourceActual.endpointCount ?? "missing"}`);
    }
    for (const blocker of promotion.blockers || []) {
      lines.push(`- baselinePromotionBlocker: ${blocker}`);
    }
  } else if (performance?.baselinePromotion?.file) {
    lines.push(`- baselinePromotion: missing; file=${performance.baselinePromotion.file}`);
  }
  for (const endpoint of performance?.missingBaselineEndpoints || []) {
    lines.push(`- missingBaselineEndpoint: ${endpoint}`);
  }
  for (const issue of performance?.regressionIssues || []) {
    lines.push(`- regression: [${issue.name}] ${issue.detail}`);
  }
  if (performance?.actionPlan) {
    lines.push(`- actionPlan: owner=${performance.actionPlan.owner || "release-performance"} pendingItems=${performance.actionPlan.pendingItems ?? 0}`);
    for (const item of performance.actionPlan.items || []) {
      const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
      const endpoint = item.endpoint ? `; endpoint=${item.endpoint}` : "";
      const metric = item.metric ? `; metric=${item.metric}` : "";
      lines.push(`- performanceAction: ${item.id}; owner=${item.owner || "release-performance"}; reason=${item.reason || "missing"}${envKeys}${endpoint}${metric}; action=${item.action || "missing"}`);
    }
  }
  lines.push("", "## Business Runtime E2E Diagnostics", "");
  const business = summary.diagnostics.businessE2e || {};
  const fileProcessing = business.fileProcessing;
  if (!fileProcessing?.present) {
    lines.push(`- fileProcessing: missing; file=${fileProcessing?.file || "missing"}`);
  } else {
    lines.push(`- fileProcessing: status=${fileProcessing.status || "missing"} localOnly=${fileProcessing.localOnly === true} uploadMs=${fileProcessing.uploadElapsedMs ?? "missing"} fileId=${fileProcessing.fileId ?? "missing"}`);
    appendProductionEquivalence(lines, "fileProcessing", fileProcessing.productionEquivalence);
    for (const task of fileProcessing.requiredTasks || []) {
      lines.push(`- fileTask: ${task.taskType}=${task.status}`);
    }
    for (const artifact of fileProcessing.requiredArtifacts || []) {
      lines.push(`- fileArtifact: ${artifact.artifactType}=${artifact.present === true}`);
    }
    for (const issue of fileProcessing.contractIssues || []) {
      lines.push(`- fileIssue: ${issue}`);
    }
  }
  const paymentWebhook = business.paymentWebhook;
  if (!paymentWebhook?.present) {
    lines.push(`- paymentWebhook: missing; file=${paymentWebhook?.file || "missing"}`);
  } else {
    lines.push(`- paymentWebhook: status=${paymentWebhook.status || "missing"} localOnly=${paymentWebhook.localOnly === true} orderStatus=${paymentWebhook.orderStatus || "missing"} providerConfigured=${paymentWebhook.providerConfigured === true}`);
    appendProductionEquivalence(lines, "paymentWebhook", paymentWebhook.productionEquivalence);
    for (const [name, webhook] of Object.entries(paymentWebhook.webhooks || {})) {
      lines.push(`- paymentWebhook.${name}: processed=${webhook.processed} signatureValid=${webhook.signatureValid} elapsedMs=${webhook.elapsedMs ?? "missing"}`);
    }
    for (const issue of paymentWebhook.contractIssues || []) {
      lines.push(`- paymentIssue: ${issue}`);
    }
  }
  const jobE2e = business.jobE2e;
  if (!jobE2e?.present) {
    lines.push(`- jobE2e: missing; file=${jobE2e?.file || "missing"}`);
  } else {
    lines.push(`- jobE2e: localOnly=${jobE2e.localOnly === true} unauthorizedStatus=${jobE2e.unauthorizedStatus ?? "missing"} failed=${jobE2e.summary?.failed ?? "missing"} endpointCount=${jobE2e.endpoints?.length ?? 0}`);
    appendProductionEquivalence(lines, "jobE2e", jobE2e.productionEquivalence);
    for (const endpoint of jobE2e.endpoints || []) {
      lines.push(`- jobEndpoint: ${endpoint.name} present=${endpoint.present === true} status=${endpoint.status ?? "missing"} dataType=${endpoint.dataType || "missing"} expected=${endpoint.expectedDataType}`);
    }
    if (jobE2e.outboxOwnership) {
      lines.push(`- jobOutboxOwnershipDelta: ${jobE2e.outboxOwnership.crossOwnerPayloadFailuresDelta ?? "missing"}`);
    }
    for (const issue of jobE2e.contractIssues || []) {
      lines.push(`- jobIssue: ${issue}`);
    }
  }
  for (const [owner, plan] of Object.entries(business.actionPlan || {})) {
    lines.push(`- actionPlan: owner=${owner} pendingItems=${plan.pendingItems ?? 0} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
    for (const item of plan.items || []) {
      const tasks = (item.tasks || []).length > 0 ? `; tasks=${item.tasks.join("|")}` : "";
      const artifacts = (item.artifacts || []).length > 0 ? `; artifacts=${item.artifacts.join("|")}` : "";
      const cases = (item.cases || []).length > 0 ? `; cases=${item.cases.join("|")}` : "";
      const endpoints = (item.endpoints || []).length > 0 ? `; endpoints=${item.endpoints.join("|")}` : "";
      const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
      lines.push(`- businessAction: ${item.id}; owner=${item.owner || owner}; reason=${item.reason || "missing"}${envKeys}${tasks}${artifacts}${cases}${endpoints}; action=${item.action || "missing"}`);
    }
  }
  lines.push("", "## Docker Diagnostics", "");
  const dockerDiagnostics = summary.diagnostics.docker;
  if (!dockerDiagnostics) {
    lines.push("- Missing Docker image evidence artifact");
  } else {
    lines.push(`- status: ${dockerDiagnostics.status || "missing"}`);
    lines.push(`- command: ${dockerDiagnostics.dockerCommand || "docker"}`);
    lines.push(`- cliStatus: ${dockerDiagnostics.cliStatus ?? "missing"}`);
    if (dockerDiagnostics.cliVersion) {
      lines.push(`- cliVersion: ${dockerDiagnostics.cliVersion}`);
    }
    lines.push(`- daemonStatus: ${dockerDiagnostics.daemonStatus ?? "missing"}`);
    if (dockerDiagnostics.daemonError) {
      lines.push(`- daemonError: ${dockerDiagnostics.daemonError}`);
    }
    lines.push(`- images: passed=${dockerDiagnostics.summary?.passed ?? 0} failed=${dockerDiagnostics.summary?.failed ?? 0} skipped=${dockerDiagnostics.summary?.skipped ?? 0}`);
    for (const image of dockerDiagnostics.images || []) {
      lines.push(`- image ${image.name}: status=${image.status || "missing"} dockerfile=${image.dockerfile || "missing"} tag=${image.tag || "missing"} expectedPort=${image.expectedExposedPort || "missing"} nonRoot=${image.requireNonRootUser === true}`);
      if (image.staticDockerfile) {
        lines.push(`  staticDockerfile: status=${image.staticDockerfile.status || "missing"} exists=${image.staticDockerfile.exists === true} sha256=${image.staticDockerfile.dockerfileSha256 || "missing"}`);
        for (const issue of image.staticDockerfile.issues || []) {
          lines.push(`  staticDockerfileIssue: ${issue}`);
        }
      }
      if (image.skipReason) {
        lines.push(`  skipReason: ${image.skipReason}`);
      }
      if (image.blockers.length > 0) {
        lines.push(`  blockers: ${image.blockers.join(" | ")}`);
      }
      lines.push(`  action: ${image.action}`);
    }
    if (dockerDiagnostics.actionPlan) {
      lines.push(`- actionPlan: owner=${dockerDiagnostics.actionPlan.owner || "release-infra"} pendingItems=${dockerDiagnostics.actionPlan.pendingItems ?? 0} envKeys=${(dockerDiagnostics.actionPlan.envKeys || []).join(",") || "none"}`);
      for (const item of dockerDiagnostics.actionPlan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const image = item.image ? `; image=${item.image}` : "";
        const dockerfile = item.dockerfile ? `; dockerfile=${item.dockerfile}` : "";
        lines.push(`- dockerAction: ${item.id}; owner=${item.owner || "release-infra"}; reason=${item.reason || "missing"}${envKeys}${image}${dockerfile}; action=${item.action || "missing"}`);
      }
    }
    if (dockerDiagnostics.remediation) {
      lines.push(`- remediation: transientRegistryFailure=${dockerDiagnostics.remediation.transientRegistryFailure === true} dockerUnavailable=${dockerDiagnostics.remediation.dockerUnavailable === true}`);
      for (const image of dockerDiagnostics.remediation.transientImages || []) {
        lines.push(`- dockerTransientImage: ${image.name || "unknown"}; attempts=${image.attempts ?? "missing"}; retries=${image.retries ?? "missing"}; dockerfile=${image.dockerfile || "missing"}`);
      }
      for (const item of dockerDiagnostics.remediation.nextActions || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const exampleCommand = item.exampleCommand ? `; exampleCommand=${item.exampleCommand}` : "";
        lines.push(`- dockerRemediationAction: ${item.id || "missing"}; owner=${item.owner || "release-infra"}${envKeys}; action=${item.action || "missing"}${exampleCommand}`);
      }
    }
  }
  const skippedImages = dockerDiagnostics?.skippedImages || [];
  if (dockerDiagnostics && skippedImages.length === 0) {
    lines.push("- None");
  } else if (dockerDiagnostics) {
    for (const image of skippedImages) {
      lines.push(`- ${image.name}: ${image.skipReason || "skipped without reason"}`);
    }
  }
  lines.push("", "## Frontend Smoke Missing Flows", "");
  const frontendDiagnostics = summary.diagnostics.frontendSmoke;
  if (!frontendDiagnostics) {
    lines.push("- Missing frontend smoke artifact");
  } else {
    lines.push(`- status: ${frontendDiagnostics.status || "missing"}`);
    lines.push(`- baseUrl: ${frontendDiagnostics.baseUrl || "missing"}`);
    lines.push(`- https: ${frontendDiagnostics.https === true}`);
    lines.push(`- localOnly: ${frontendDiagnostics.localOnly === true}`);
    lines.push(`- expectDeployed: ${frontendDiagnostics.expectDeployed === true}`);
    appendProductionEquivalence(lines, "frontendSmoke", frontendDiagnostics.productionEquivalence);
    lines.push(`- sourceEnvironment: ${frontendDiagnostics.sourceEnvironment || "missing"}`);
    lines.push(`- releaseCandidate: ${frontendDiagnostics.releaseCandidate || "missing"}`);
    lines.push(`- evidenceOperator: ${frontendDiagnostics.evidenceOperator || "missing"}`);
    lines.push(`- tests: total=${frontendDiagnostics.summary?.total ?? 0} passed=${frontendDiagnostics.summary?.passed ?? 0} failed=${frontendDiagnostics.summary?.failed ?? 0} skipped=${frontendDiagnostics.summary?.skipped ?? 0}`);
    lines.push(`- requiredFlows: ${frontendDiagnostics.summary?.requiredFlows ?? 0}; missing=${frontendDiagnostics.summary?.missingRequiredFlows ?? 0}`);
    for (const blocker of frontendDiagnostics.blockers || []) {
      lines.push(`- blocker: ${blocker}`);
    }
    if (frontendDiagnostics.playwrightReport) {
      lines.push(`- playwrightReport: present=${frontendDiagnostics.playwrightReport.present === true} file=${frontendDiagnostics.playwrightReport.file || "missing"}`);
    }
    if (frontendDiagnostics.staticSpecCoverage) {
      const staticCoverage = frontendDiagnostics.staticSpecCoverage;
      lines.push(`- staticSpecCoverage: present=${staticCoverage.present === true} covered=${staticCoverage.covered ?? 0} missing=${staticCoverage.missing ?? 0} file=${staticCoverage.file || "missing"}`);
      for (const flow of staticCoverage.coverage || []) {
        if (flow.status === "missing") {
          lines.push(`- staticSpecMissing: ${flow.flow}; reason=${flow.reason || "missing"}`);
        }
      }
    }
    if (frontendDiagnostics.actionPlan) {
      lines.push(`- actionPlan: owner=${frontendDiagnostics.actionPlan.owner || "frontend"} pendingItems=${frontendDiagnostics.actionPlan.pendingItems ?? 0}`);
      for (const item of frontendDiagnostics.actionPlan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const flows = (item.flows || []).length > 0 ? `; flows=${item.flows.join("|")}` : "";
        lines.push(`- frontendAction: ${item.id}; owner=${item.owner || "frontend"}; reason=${item.reason || "missing"}${envKeys}${flows}; action=${item.action || "missing"}`);
      }
    }
  }
  const missingFlows = frontendDiagnostics?.missingFlows || [];
  if (frontendDiagnostics && missingFlows.length === 0) {
    lines.push("- None");
  } else if (frontendDiagnostics) {
    for (const flow of missingFlows) {
      lines.push(`- ${flow.flow}: ${flow.reason || "missing without reason"}; action=${flow.action}`);
    }
  }
  lines.push("", "## Migration Runtime Evidence", "");
  const migrationRuntime = summary.diagnostics.migration?.runtime || null;
  if (!migrationRuntime) {
    lines.push("- Missing migration evidence artifact");
  } else {
    lines.push(`- freshDatabaseValidated: ${migrationRuntime.freshDatabaseValidated === true}`);
    lines.push(`- upgradeDatabaseValidated: ${migrationRuntime.upgradeDatabaseValidated === true}`);
    lines.push(`- runtimeReady: ${summary.diagnostics.migration?.runtimeReady === true}`);
    lines.push(`- environment: ${migrationRuntime.environment || "missing"}`);
    lines.push(`- releaseCandidate: ${migrationRuntime.releaseCandidate || "missing"}`);
    lines.push(`- freshDatabaseEvidence: ${migrationRuntime.freshDatabaseEvidence || "missing"}`);
    lines.push(`- upgradeDatabaseEvidence: ${migrationRuntime.upgradeDatabaseEvidence || "missing"}`);
    for (const proof of summary.diagnostics.migration?.runtimeProofs || []) {
      const envKeys = (proof.requiredEnvKeys || []).length > 0 ? `; envKeys=${proof.requiredEnvKeys.join(",")}` : "";
      lines.push(`- proof ${proof.id}: validated=${proof.validated === true}; evidence=${proof.evidence || "missing"}; required=${proof.requiredEvidence || "missing"}${envKeys}`);
    }
    const runtimeDiagnostics = summary.diagnostics.migration?.runtimeDiagnostics || [];
    for (const diagnostic of runtimeDiagnostics) {
      const envKeys = (diagnostic.envKeys || []).length > 0 ? `; envKeys=${diagnostic.envKeys.join(",")}` : "";
      lines.push(`- ${diagnostic.id}: ${diagnostic.status || "missing"}; owner=${diagnostic.owner || "missing"}; evidence=${diagnostic.evidence || "missing"}; action=${diagnostic.action || "missing"}${envKeys}`);
    }
    for (const [owner, plan] of Object.entries(summary.diagnostics.migration?.actionPlan || {})) {
      lines.push(`- actionPlan: owner=${owner} pendingItems=${plan.pendingItems ?? 0} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
      for (const item of plan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const proofId = item.proofId ? `; proof=${item.proofId}` : "";
        const diagnosticId = item.diagnosticId ? `; diagnostic=${item.diagnosticId}` : "";
        lines.push(`- migrationAction: ${item.id}; owner=${item.owner || owner}; reason=${item.reason || "missing"}${envKeys}${proofId}${diagnosticId}; action=${item.action || "missing"}`);
      }
    }
  }
  lines.push("", "## EXPLAIN Evidence Diagnostics", "");
  const explainGateReportInput = summary.inputArtifacts?.explainGateReport;
  if (explainGateReportInput) {
    lines.push(`- gateReport: present=${explainGateReportInput.present === true} status=${explainGateReportInput.status || "missing"} blockers=${explainGateReportInput.blockers ?? "missing"} generatedAt=${explainGateReportInput.generatedAt || "missing"}`);
  }
  const explain = summary.diagnostics.explain;
  if (!explain?.present) {
    lines.push(`- missing explain directory: ${explain?.dir || "missing"}`);
  } else {
    lines.push(`- dir: ${explain.dir}`);
    lines.push(`- files: ${explain.fileCount ?? 0}`);
    lines.push(`- missingRequiredFiles: ${(explain.missingRequiredFiles || []).length}`);
    for (const file of explain.missingRequiredFiles || []) {
      lines.push(`- missingExplainFile: ${file}`);
    }
    lines.push(`- legacyPlanImports: ${(explain.legacyPlanImports || []).length}`);
    for (const file of explain.legacyPlanImports || []) {
      lines.push(`- legacyExplainFile: ${file}`);
    }
    if (explain.actionPlan) {
      lines.push(`- actionPlan: owner=${explain.actionPlan.owner || "database"} pendingFiles=${explain.actionPlan.pendingFiles ?? 0}`);
      for (const item of explain.actionPlan.items || []) {
        lines.push(`- explainAction: ${item.file}; reasons=${(item.reasons || []).join(" | ")}; command=${item.command}`);
      }
    }
    for (const file of explain.files || []) {
      lines.push(`- explainFile: ${file.file} queryName=${file.queryName || "missing"} sourceEnvironment=${file.sourceEnvironment || "missing"} releaseCandidate=${file.releaseCandidate || "missing"} legacy=${file.legacyPlanImport === true} issues=${file.issueCount ?? 0}`);
    }
    for (const issue of explain.issues || []) {
      lines.push(`- explainIssue: [${issue.scope || "unknown"}] ${issue.detail}`);
    }
  }
  lines.push("", "## AI Runtime Diagnostics", "");
  const aiDiagnostics = summary.diagnostics.aiRuntime;
  if (!aiDiagnostics) {
    lines.push("- Missing AI runtime artifact");
  } else {
    lines.push(`- status: ${aiDiagnostics.status || "missing"}`);
    lines.push(`- baseUrl: ${aiDiagnostics.baseUrl || "missing"}`);
    lines.push(`- localOnly: ${aiDiagnostics.localOnly === true}`);
    appendProductionEquivalence(lines, "aiRuntime", aiDiagnostics.productionEquivalence);
    lines.push(`- providerRemoteConfigured: ${aiDiagnostics.remoteEvidence?.provider?.remoteConfigured === true}`);
    lines.push(`- ownerGatewayConfiguredOwners: ${aiDiagnostics.remoteEvidence?.ownerGateway?.configuredOwnerCount ?? 0}`);
    if (aiDiagnostics.actionPlan) {
      lines.push(`- actionPlan: owner=${aiDiagnostics.actionPlan.owner || "ai"} pendingItems=${aiDiagnostics.actionPlan.pendingItems ?? 0}`);
      for (const item of aiDiagnostics.actionPlan.items || []) {
        lines.push(`- aiAction: ${item.id}; owner=${item.owner}; reason=${item.reason || "missing"}; envKeys=${(item.envKeys || []).join(",")}; action=${item.action}`);
      }
    }
    const categories = Object.entries(aiDiagnostics.failureCategories || {});
    if (categories.length === 0) {
      lines.push("- failureCategories: none");
    } else {
      lines.push(`- failureCategories: ${categories.map(([category, count]) => `${category}=${count}`).join(", ")}`);
    }
    const owners = Object.entries(aiDiagnostics.failureOwners || {});
    if (owners.length === 0) {
      lines.push("- failureOwners: none");
    } else {
      lines.push(`- failureOwners: ${owners.map(([owner, count]) => `${owner}=${count}`).join(", ")}`);
    }
    const failureDetails = aiDiagnostics.failureDetails || [];
    if (failureDetails.length > 0) {
      for (const detail of failureDetails) {
        lines.push(`- failure: [${detail.category || "unknown"}][${detail.owner || "unknown"}] ${detail.message || "missing message"}`);
      }
    } else {
      const failures = aiDiagnostics.failures || [];
      if (failures.length === 0) {
        lines.push("- failures: none");
      }
      for (const failure of failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
  }
  lines.push("", "## Rollback Drill Contexts", "");
  const rollbackDiagnostics = summary.diagnostics.rollback;
  if (!rollbackDiagnostics) {
    lines.push("- Missing rollback drill artifact");
  } else {
    lines.push(`- status: ${rollbackDiagnostics.status || "missing"}`);
    lines.push(`- environment: ${rollbackDiagnostics.environment || "missing"}`);
    lines.push(`- releaseVersion: ${rollbackDiagnostics.releaseVersion || "missing"}`);
    const rollbackSummary = rollbackDiagnostics.summary || {};
    lines.push(`- summary: ready=${rollbackSummary.readyContexts ?? "missing"}/${rollbackSummary.requiredContexts ?? "missing"} pass=${rollbackSummary.passContexts ?? "missing"} deferred=${rollbackSummary.deferredContexts ?? "missing"} missing=${rollbackSummary.missingContexts ?? "missing"} blockers=${rollbackSummary.blockers ?? "missing"}`);
    for (const [owner, plan] of Object.entries(rollbackDiagnostics.actionPlan || {})) {
      lines.push(`- ownerPlan: ${owner} pending=${plan.pendingContexts} ready=${plan.readyContexts} missingEvidence=${plan.missingEvidence}`);
    }
    const rollbackReasonByContext = new Map();
    for (const plan of Object.values(rollbackDiagnostics.actionPlan || {})) {
      for (const item of plan.items || []) {
        rollbackReasonByContext.set(item.context, item.reason || "missing");
      }
    }
    const contextDiagnostics = rollbackDiagnostics.contextDiagnostics || [];
    if (contextDiagnostics.length > 0) {
      for (const context of contextDiagnostics) {
        lines.push(`- ${context.context}: ${context.status || "missing"}; owner=${context.owner || "missing"}; reason=${rollbackReasonByContext.get(context.context) || "missing"}; evidence=${context.evidence || "missing"}; action=${context.action || "missing"}`);
      }
    } else {
      for (const context of rollbackDiagnostics.contexts || []) {
        lines.push(`- ${context.context}: ${context.status || "missing"}; evidence=${context.drillEvidence || "missing"}`);
      }
    }
  }
  lines.push("", "## Orchestrator Preflight", "");
  const orchestratorDiagnostics = summary.diagnostics.orchestrator;
  const preflight = orchestratorDiagnostics?.preflight || null;
  if (!orchestratorDiagnostics) {
    lines.push("- Missing orchestrator report artifact");
  } else if (!preflight) {
    lines.push("- Missing preflight checks");
  } else {
    lines.push(`- mode: ${orchestratorDiagnostics.mode || "missing"}`);
    lines.push(`- status: ${preflight.status || "missing"}`);
    lines.push(`- blockers: ${preflight.blockers ?? 0}`);
    lines.push(`- warnings: ${preflight.warnings ?? 0}`);
    lines.push(`- selectedSteps: ${orchestratorDiagnostics.selectedStepCount ?? 0}; executedResults: ${orchestratorDiagnostics.executedResultCount ?? 0}`);
    if (orchestratorDiagnostics.action) {
      lines.push(`- action: ${orchestratorDiagnostics.action}`);
    }
    for (const [owner, plan] of Object.entries(orchestratorDiagnostics.actionPlan || {})) {
      lines.push(`- actionPlan: owner=${owner} pendingItems=${plan.pendingItems ?? 0} envKeys=${(plan.envKeys || []).join(",") || "none"}`);
      for (const item of plan.items || []) {
        const envKeys = (item.envKeys || []).length > 0 ? `; envKeys=${item.envKeys.join(",")}` : "";
        const checkId = item.checkId ? `; checkId=${item.checkId}` : "";
        const stepId = item.stepId ? `; stepId=${item.stepId}` : "";
        lines.push(`- orchestratorAction: ${item.id}; owner=${item.owner || owner}; reason=${item.reason || "missing"}${envKeys}${checkId}${stepId}; action=${item.action || "missing"}`);
      }
    }
    const blockingChecks = orchestratorDiagnostics.blockerChecks || (preflight.checks || []).filter((check) => check.status === "BLOCKER");
    if (blockingChecks.length === 0) {
      lines.push("- blockerChecks: none");
    } else {
      for (const check of blockingChecks) {
        const envKeys = (check.envKeys || []).length > 0 ? `; envKeys=${check.envKeys.join(",")}` : "";
        lines.push(`- ${check.id}: ${check.detail || "missing detail"}${envKeys}`);
      }
    }
    const resultById = new Map((orchestratorDiagnostics.results || []).map((result) => [result.id, result]));
    for (const step of orchestratorDiagnostics.selectedSteps || []) {
      const result = resultById.get(step.id);
      const status = result?.status || (result?.skipped ? "SKIPPED" : "not-run");
      const flags = [
        step.enabled === false ? "disabled" : null,
        step.optional ? "optional" : null,
        step.runtime ? "runtime" : null,
        step.heavy ? "heavy" : null,
      ].filter(Boolean).join(",");
      const flagText = flags ? `; flags=${flags}` : "";
      const envText = (step.envKeys || []).length > 0 ? `; envKeys=${step.envKeys.join(",")}` : "";
      lines.push(`- step ${step.id}: ${status}${flagText}${envText}`);
    }
  }
  lines.push("");
  lines.push("## Raw Blockers", "");
  if (summary.actions.length === 0) {
    lines.push("- None");
  } else {
    for (const item of summary.actions) {
      lines.push(`- [${item.category}] ${item.blocker}`);
    }
  }
  return `${lines.join("\n")}\n`;
}

fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(jsonOutput, jsonPortable(summary));
fs.writeFileSync(markdownOutput, markdown(summary));
fs.writeFileSync(ownerActionRollupOutput, jsonPortable(ownerActionRollupArtifact(summary)));
fs.writeFileSync(ownerActionRollupCsvOutput, ownerActionRollupCsv(summary));
fs.writeFileSync(ownerActionRollupMarkdownOutput, ownerActionRollupMarkdown(summary));
fs.writeFileSync(sourceActionRollupOutput, jsonPortable(sourceActionRollupArtifact(summary)));
fs.writeFileSync(sourceActionRollupCsvOutput, sourceActionRollupCsv(summary));
fs.writeFileSync(sourceActionRollupMarkdownOutput, sourceActionRollupMarkdown(summary));
fs.writeFileSync(releaseBlockerMapOutput, jsonPortable(releaseBlockerMapArtifact(summary)));
fs.writeFileSync(releaseBlockerMapCsvOutput, releaseBlockerMapCsv(summary));
fs.writeFileSync(releaseBlockerMapMarkdownOutput, releaseBlockerMapMarkdown(summary));
fs.writeFileSync(releaseFastTrackOutput, jsonPortable(releaseFastTrackArtifact(summary)));
fs.writeFileSync(releaseCutoverChecklistCsvOutput, releaseCutoverChecklistCsv(summary));
fs.writeFileSync(releaseCutoverOwnerMatrixOutput, jsonPortable(releaseCutoverOwnerMatrixArtifact(summary)));
fs.writeFileSync(releaseCutoverOwnerMatrixCsvOutput, releaseCutoverOwnerMatrixCsv(summary));
fs.writeFileSync(releaseCutoverOwnerMatrixMarkdownOutput, releaseCutoverOwnerMatrixMarkdown(summary));
fs.writeFileSync(releaseSprintBoardOutput, jsonPortable(releaseSprintBoardArtifact(summary)));
fs.writeFileSync(releaseSprintBoardCsvOutput, releaseSprintBoardCsv(summary));
fs.writeFileSync(releaseSprintBoardMarkdownOutput, releaseSprintBoardMarkdown(summary));
fs.writeFileSync(releaseCommandCatalogOutput, jsonPortable(releaseCommandCatalogArtifact(summary)));
fs.writeFileSync(releaseCommandCatalogCsvOutput, releaseCommandCatalogCsv(summary));
fs.writeFileSync(releaseCommandCatalogMarkdownOutput, releaseCommandCatalogMarkdown(summary));
fs.writeFileSync(releaseOwnerHandoffOutput, jsonPortable(releaseOwnerHandoffArtifact(summary)));
fs.writeFileSync(releaseOwnerHandoffCsvOutput, releaseOwnerHandoffCsv(summary));
fs.writeFileSync(releaseOwnerHandoffMarkdownOutput, releaseOwnerHandoffMarkdown(summary));
fs.writeFileSync(releaseOwnerReceiptsOutput, jsonPortable(releaseOwnerReceiptsArtifact(summary)));
fs.writeFileSync(releaseOwnerReceiptsCsvOutput, releaseOwnerReceiptsCsv(summary));
fs.writeFileSync(releaseOwnerReceiptsMarkdownOutput, releaseOwnerReceiptsMarkdown(summary));
fs.writeFileSync(releaseNextActionQueueOutput, jsonPortable(releaseNextActionQueueArtifact(summary)));
fs.writeFileSync(releaseNextActionQueueCsvOutput, releaseNextActionQueueCsv(summary));
fs.writeFileSync(releaseNextActionQueueMarkdownOutput, releaseNextActionQueueMarkdown(summary));
fs.writeFileSync(releaseNextActionCommandsOutput, releaseNextActionCommands(summary), { mode: 0o755 });
fs.writeFileSync(releaseBlockerClosurePlanOutput, jsonPortable(releaseBlockerClosurePlanArtifact(summary)));
fs.writeFileSync(releaseBlockerClosurePlanCsvOutput, releaseBlockerClosurePlanCsv(summary));
fs.writeFileSync(releaseBlockerClosurePlanMarkdownOutput, releaseBlockerClosurePlanMarkdown(summary));
fs.writeFileSync(releaseBlockerClosureCommandsOutput, releaseBlockerClosureCommands(summary), { mode: 0o755 });
fs.writeFileSync(releaseClosureWaveEnvMatrixOutput, jsonPortable(releaseClosureWaveEnvMatrixArtifact(summary)));
fs.writeFileSync(releaseClosureWaveEnvMatrixCsvOutput, releaseClosureWaveEnvMatrixCsv(summary));
fs.writeFileSync(releaseClosureWaveEnvMatrixMarkdownOutput, releaseClosureWaveEnvMatrixMarkdown(summary));
fs.writeFileSync(releaseClosureWaveEnvTemplateOutput, releaseClosureWaveEnvTemplate(summary));
fs.writeFileSync(releaseClosureWaveReceiptsOutput, jsonPortable(releaseClosureWaveReceiptsArtifact(summary)));
fs.writeFileSync(releaseClosureWaveReceiptsCsvOutput, releaseClosureWaveReceiptsCsv(summary));
fs.writeFileSync(releaseClosureWaveReceiptsMarkdownOutput, releaseClosureWaveReceiptsMarkdown(summary));
fs.writeFileSync(releaseClosureWaveBlockerMapOutput, jsonPortable(releaseClosureWaveBlockerMapArtifact(summary)));
fs.writeFileSync(releaseClosureWaveBlockerMapCsvOutput, releaseClosureWaveBlockerMapCsv(summary));
fs.writeFileSync(releaseClosureWaveBlockerMapMarkdownOutput, releaseClosureWaveBlockerMapMarkdown(summary));
fs.writeFileSync(releasePerformanceBaselineClosureOutput, jsonPortable(releasePerformanceBaselineClosureArtifact(summary)));
fs.writeFileSync(releasePerformanceBaselineClosureMarkdownOutput, releasePerformanceBaselineClosureMarkdown(summary));
fs.writeFileSync(releasePerformanceBaselineCommandsOutput, releasePerformanceBaselineCommands(summary), { mode: 0o755 });
fs.writeFileSync(releaseFinalGoNoGoOutput, jsonPortable(releaseFinalGoNoGoArtifact(summary)));
fs.writeFileSync(releaseFinalGoNoGoCsvOutput, releaseFinalGoNoGoCsv(summary));
fs.writeFileSync(releaseFinalGoNoGoMarkdownOutput, releaseFinalGoNoGoMarkdown(summary));
fs.writeFileSync(releaseFinalGoNoGoGateOutput, releaseFinalGoNoGoGate(summary), { mode: 0o755 });
fs.writeFileSync(releasePreflightGateOutput, releasePreflightGate(summary), { mode: 0o755 });
fs.writeFileSync(releaseFinalOwnerQueueOutput, jsonPortable(releaseFinalOwnerQueueArtifact(summary)));
fs.writeFileSync(releaseFinalOwnerQueueCsvOutput, releaseFinalOwnerQueueCsv(summary));
fs.writeFileSync(releaseFinalOwnerQueueMarkdownOutput, releaseFinalOwnerQueueMarkdown(summary));
fs.writeFileSync(releaseFinalOwnerQueueCommandsOutput, releaseFinalOwnerQueueCommands(summary), { mode: 0o755 });
fs.writeFileSync(releaseFinalOwnerQueueEnvTemplateOutput, releaseFinalOwnerQueueEnvTemplate(summary));
fs.writeFileSync(releaseFinalOwnerQueueEnvInitOutput, releaseFinalOwnerQueueEnvInit(summary), { mode: 0o755 });
fs.writeFileSync(releaseEnvBootstrapOutput, releaseEnvBootstrapScript(summary), { mode: 0o755 });
fs.writeFileSync(releaseFastTrackMarkdownOutput, releaseFastTrackMarkdown(summary));
fs.writeFileSync(releaseActionPriorityOutput, jsonPortable(releaseActionPriorityArtifact(summary)));
fs.writeFileSync(releaseActionPriorityCsvOutput, releaseActionPriorityCsv(summary));
fs.writeFileSync(releaseActionPriorityMarkdownOutput, releaseActionPriorityMarkdown(summary));
fs.writeFileSync(releaseActionBatchesOutput, jsonPortable(releaseActionBatchesArtifact(summary)));
fs.writeFileSync(releaseActionBatchesCsvOutput, releaseActionBatchesCsv(summary));
fs.writeFileSync(releaseActionBatchesMarkdownOutput, releaseActionBatchesMarkdown(summary));
fs.writeFileSync(releaseActionDependencyGraphOutput, jsonPortable(releaseActionDependencyGraphArtifact(summary)));
fs.writeFileSync(releaseActionDependencyGraphMarkdownOutput, releaseActionDependencyGraphMarkdown(summary));
fs.writeFileSync(releaseExecutionQueueOutput, jsonPortable(releaseExecutionQueueArtifact(summary)));
fs.writeFileSync(releaseExecutionQueueCsvOutput, releaseExecutionQueueCsv(summary));
fs.writeFileSync(releaseExecutionQueueMarkdownOutput, releaseExecutionQueueMarkdown(summary));
fs.writeFileSync(releaseExecutionCommandsOutput, releaseExecutionCommands(summary), { mode: 0o755 });
fs.writeFileSync(releaseMissingEnvOutput, jsonPortable(releaseMissingEnv(summary)));
fs.writeFileSync(releaseEnvOwnerMatrixOutput, jsonPortable(releaseEnvOwnerMatrixArtifact(summary)));
fs.writeFileSync(releaseEnvOwnerMatrixCsvOutput, releaseEnvOwnerMatrixCsv(summary));
fs.writeFileSync(releaseEnvOwnerMatrixMarkdownOutput, releaseEnvOwnerMatrixMarkdown(summary));
fs.writeFileSync(releaseEnvFillPriorityOutput, jsonPortable(releaseEnvFillPriorityArtifact(summary)));
fs.writeFileSync(releaseEnvFillPriorityCsvOutput, releaseEnvFillPriorityCsv(summary));
fs.writeFileSync(releaseEnvFillPriorityMarkdownOutput, releaseEnvFillPriorityMarkdown(summary));
fs.writeFileSync(releaseEnvCanonicalFillOutput, jsonPortable(releaseEnvCanonicalFillArtifact(summary)));
fs.writeFileSync(releaseEnvCanonicalFillCsvOutput, releaseEnvCanonicalFillCsv(summary));
fs.writeFileSync(releaseEnvCanonicalFillMarkdownOutput, releaseEnvCanonicalFillMarkdown(summary));
fs.writeFileSync(releaseEnvCanonicalFillTemplateOutput, releaseEnvCanonicalFillTemplate(summary));
fs.writeFileSync(releaseEnvReadinessRedactedOutput, jsonPortable(releaseEnvReadinessRedactedArtifact(summary)));
fs.writeFileSync(releaseEnvReadinessRedactedCsvOutput, releaseEnvReadinessRedactedCsv(summary));
fs.writeFileSync(releaseEnvReadinessRedactedMarkdownOutput, releaseEnvReadinessRedactedMarkdown(summary));
fs.writeFileSync(releaseEnvReadinessGateOutput, releaseEnvReadinessGate(summary), { mode: 0o755 });
const releaseEnvOwnerHandoffRedacted = releaseEnvOwnerHandoffRedactedArtifact(summary);
fs.writeFileSync(releaseEnvOwnerHandoffRedactedOutput, jsonPortable({
  ...releaseEnvOwnerHandoffRedacted,
  owners: releaseEnvOwnerHandoffRedacted.owners.map(({ items, ...owner }) => owner),
}));
fs.writeFileSync(releaseEnvOwnerHandoffRedactedCsvOutput, releaseEnvOwnerHandoffRedactedCsv(summary));
fs.writeFileSync(releaseEnvOwnerHandoffRedactedMarkdownOutput, releaseEnvOwnerHandoffRedactedMarkdown(summary));
fs.mkdirSync(releaseEnvOwnerHandoffRedactedDir, { recursive: true });
const expectedRedactedOwnerHandoffFiles = new Set((releaseEnvOwnerHandoffRedacted.owners || []).map((owner) => owner.fileName));
for (const fileName of fs.readdirSync(releaseEnvOwnerHandoffRedactedDir).filter((file) => file.endsWith(".md"))) {
  if (!expectedRedactedOwnerHandoffFiles.has(fileName)) {
    fs.unlinkSync(path.join(releaseEnvOwnerHandoffRedactedDir, fileName));
  }
}
for (const owner of releaseEnvOwnerHandoffRedacted.owners || []) {
  fs.writeFileSync(path.join(releaseEnvOwnerHandoffRedactedDir, owner.fileName), releaseEnvOwnerHandoffRedactedOwnerMarkdown(owner));
}
const releaseEnvOwnerInputPacket = releaseEnvOwnerInputPacketArtifact(summary);
fs.writeFileSync(releaseEnvOwnerInputPacketOutput, jsonPortable(releaseEnvOwnerInputPacket));
fs.writeFileSync(releaseEnvOwnerInputPacketCsvOutput, releaseEnvOwnerInputPacketCsv(summary));
fs.writeFileSync(releaseEnvOwnerInputPacketMarkdownOutput, releaseEnvOwnerInputPacketMarkdown(summary));
fs.mkdirSync(releaseEnvOwnerInputPacketDir, { recursive: true });
const expectedOwnerInputPacketFiles = new Set((releaseEnvOwnerInputPacket.owners || [])
  .flatMap((owner) => [`${owner.fileName}.json`, `${owner.fileName}.md`]));
for (const fileName of fs.readdirSync(releaseEnvOwnerInputPacketDir).filter((file) => file.endsWith(".json") || file.endsWith(".md"))) {
  if (!expectedOwnerInputPacketFiles.has(fileName)) {
    fs.unlinkSync(path.join(releaseEnvOwnerInputPacketDir, fileName));
  }
}
for (const owner of releaseEnvOwnerInputPacket.owners || []) {
  fs.writeFileSync(path.join(releaseEnvOwnerInputPacketDir, `${owner.fileName}.json`), jsonPortable(releaseEnvOwnerInputPacketOwnerArtifact(releaseEnvOwnerInputPacket, owner)));
  fs.writeFileSync(path.join(releaseEnvOwnerInputPacketDir, `${owner.fileName}.md`), releaseEnvOwnerInputPacketOwnerMarkdown(releaseEnvOwnerInputPacket, owner));
}
fs.writeFileSync(releaseConfigOwnerInputReconciliationOutput, jsonPortable(releaseConfigOwnerInputReconciliationArtifact(summary)));
fs.writeFileSync(releaseOwnerInputReceiptOutput, jsonPortable(releaseOwnerInputReceiptArtifact(summary)));
fs.writeFileSync(releaseOwnerInputReceiptCsvOutput, releaseOwnerInputReceiptCsv(summary));
fs.writeFileSync(releaseOwnerInputReceiptItemsCsvOutput, releaseOwnerInputReceiptItemsCsv(summary));
fs.writeFileSync(releaseOwnerInputReceiptItemsMarkdownOutput, releaseOwnerInputReceiptItemsMarkdown(summary));
fs.mkdirSync(releaseOwnerInputReceiptItemsDir, { recursive: true });
const releaseOwnerInputReceiptOwnerItemChecklists = releaseOwnerInputReceiptOwnerItems(summary);
const expectedOwnerInputReceiptItemFiles = new Set((releaseOwnerInputReceiptOwnerItemChecklists || []).map((owner) => owner.fileName));
for (const fileName of fs.readdirSync(releaseOwnerInputReceiptItemsDir).filter((file) => file.endsWith(".md"))) {
  if (!expectedOwnerInputReceiptItemFiles.has(fileName)) {
    fs.unlinkSync(path.join(releaseOwnerInputReceiptItemsDir, fileName));
  }
}
for (const ownerChecklist of releaseOwnerInputReceiptOwnerItemChecklists || []) {
  fs.writeFileSync(path.join(releaseOwnerInputReceiptItemsDir, ownerChecklist.fileName), releaseOwnerInputReceiptOwnerItemsMarkdown(ownerChecklist));
}
fs.writeFileSync(releaseOwnerInputReceiptMarkdownOutput, releaseOwnerInputReceiptMarkdown(summary));
fs.writeFileSync(releaseEnvOwnerHandoffOutput, jsonPortable(releaseEnvOwnerHandoffArtifact(summary)));
fs.writeFileSync(releaseEnvOwnerHandoffCsvOutput, releaseEnvOwnerHandoffCsv(summary));
fs.writeFileSync(releaseEnvOwnerHandoffMarkdownOutput, releaseEnvOwnerHandoffMarkdown(summary));
const releaseEnvOwnerTemplates = releaseEnvOwnerTemplatesArtifact(summary);
fs.writeFileSync(releaseEnvOwnerTemplatesOutput, `${JSON.stringify({
  ...releaseEnvOwnerTemplates,
  owners: releaseEnvOwnerTemplates.owners.map(({ content, ...owner }) => owner),
}, null, 2)}\n`);
fs.writeFileSync(releaseEnvOwnerTemplatesMarkdownOutput, releaseEnvOwnerTemplatesMarkdown(summary));
fs.mkdirSync(releaseEnvOwnerTemplatesDir, { recursive: true });
const expectedOwnerTemplateFiles = new Set((releaseEnvOwnerTemplates.owners || []).map((owner) => owner.fileName));
for (const fileName of fs.readdirSync(releaseEnvOwnerTemplatesDir).filter((file) => file.endsWith(".env"))) {
  if (!expectedOwnerTemplateFiles.has(fileName)) {
    fs.unlinkSync(path.join(releaseEnvOwnerTemplatesDir, fileName));
  }
}
for (const owner of releaseEnvOwnerTemplates.owners || []) {
  fs.writeFileSync(path.join(releaseEnvOwnerTemplatesDir, owner.fileName), owner.content);
}
fs.writeFileSync(releaseMissingEnvTemplateOutput, releaseMissingEnvTemplate(summary));
fs.writeFileSync(releaseArtifactIntegrityGateOutput, releaseArtifactIntegrityGate(summary), { mode: 0o755 });

const generatedOutputs = {
  json: jsonOutput,
  markdown: markdownOutput,
  ownerRollup: ownerActionRollupOutput,
  ownerRollupCsv: ownerActionRollupCsvOutput,
  ownerRollupMarkdown: ownerActionRollupMarkdownOutput,
  sourceRollup: sourceActionRollupOutput,
  sourceRollupCsv: sourceActionRollupCsvOutput,
  sourceRollupMarkdown: sourceActionRollupMarkdownOutput,
  releaseBlockerMap: releaseBlockerMapOutput,
  releaseBlockerMapCsv: releaseBlockerMapCsvOutput,
  releaseBlockerMapMarkdown: releaseBlockerMapMarkdownOutput,
  releaseFastTrack: releaseFastTrackOutput,
  releaseFastTrackMarkdown: releaseFastTrackMarkdownOutput,
  releaseCutoverChecklistCsv: releaseCutoverChecklistCsvOutput,
  releaseCutoverOwnerMatrix: releaseCutoverOwnerMatrixOutput,
  releaseCutoverOwnerMatrixCsv: releaseCutoverOwnerMatrixCsvOutput,
  releaseCutoverOwnerMatrixMarkdown: releaseCutoverOwnerMatrixMarkdownOutput,
  releaseSprintBoard: releaseSprintBoardOutput,
  releaseSprintBoardCsv: releaseSprintBoardCsvOutput,
  releaseSprintBoardMarkdown: releaseSprintBoardMarkdownOutput,
  releaseCommandCatalog: releaseCommandCatalogOutput,
  releaseCommandCatalogCsv: releaseCommandCatalogCsvOutput,
  releaseCommandCatalogMarkdown: releaseCommandCatalogMarkdownOutput,
  releaseOwnerHandoff: releaseOwnerHandoffOutput,
  releaseOwnerHandoffCsv: releaseOwnerHandoffCsvOutput,
  releaseOwnerHandoffMarkdown: releaseOwnerHandoffMarkdownOutput,
  releaseOwnerReceipts: releaseOwnerReceiptsOutput,
  releaseOwnerReceiptsCsv: releaseOwnerReceiptsCsvOutput,
  releaseOwnerReceiptsMarkdown: releaseOwnerReceiptsMarkdownOutput,
  releaseNextActionQueue: releaseNextActionQueueOutput,
  releaseNextActionQueueCsv: releaseNextActionQueueCsvOutput,
  releaseNextActionQueueMarkdown: releaseNextActionQueueMarkdownOutput,
  releaseNextActionCommands: releaseNextActionCommandsOutput,
  releaseBlockerClosurePlan: releaseBlockerClosurePlanOutput,
  releaseBlockerClosurePlanCsv: releaseBlockerClosurePlanCsvOutput,
  releaseBlockerClosurePlanMarkdown: releaseBlockerClosurePlanMarkdownOutput,
  releaseBlockerClosureCommands: releaseBlockerClosureCommandsOutput,
  releaseClosureWaveEnvMatrix: releaseClosureWaveEnvMatrixOutput,
  releaseClosureWaveEnvMatrixCsv: releaseClosureWaveEnvMatrixCsvOutput,
  releaseClosureWaveEnvMatrixMarkdown: releaseClosureWaveEnvMatrixMarkdownOutput,
  releaseClosureWaveEnvTemplate: releaseClosureWaveEnvTemplateOutput,
  releaseClosureWaveReceipts: releaseClosureWaveReceiptsOutput,
  releaseClosureWaveReceiptsCsv: releaseClosureWaveReceiptsCsvOutput,
  releaseClosureWaveReceiptsMarkdown: releaseClosureWaveReceiptsMarkdownOutput,
  releaseClosureWaveBlockerMap: releaseClosureWaveBlockerMapOutput,
  releaseClosureWaveBlockerMapCsv: releaseClosureWaveBlockerMapCsvOutput,
  releaseClosureWaveBlockerMapMarkdown: releaseClosureWaveBlockerMapMarkdownOutput,
  releasePerformanceBaselineClosure: releasePerformanceBaselineClosureOutput,
  releasePerformanceBaselineClosureMarkdown: releasePerformanceBaselineClosureMarkdownOutput,
  releasePerformanceBaselineCommands: releasePerformanceBaselineCommandsOutput,
  releaseFinalGoNoGo: releaseFinalGoNoGoOutput,
  releaseFinalGoNoGoCsv: releaseFinalGoNoGoCsvOutput,
  releaseFinalGoNoGoMarkdown: releaseFinalGoNoGoMarkdownOutput,
  releaseFinalGoNoGoGate: releaseFinalGoNoGoGateOutput,
  releasePreflightGate: releasePreflightGateOutput,
  releaseFinalOwnerQueue: releaseFinalOwnerQueueOutput,
  releaseFinalOwnerQueueCsv: releaseFinalOwnerQueueCsvOutput,
  releaseFinalOwnerQueueMarkdown: releaseFinalOwnerQueueMarkdownOutput,
  releaseFinalOwnerQueueCommands: releaseFinalOwnerQueueCommandsOutput,
  releaseFinalOwnerQueueEnvTemplate: releaseFinalOwnerQueueEnvTemplateOutput,
  releaseFinalOwnerQueueEnvInit: releaseFinalOwnerQueueEnvInitOutput,
  releaseEnvBootstrap: releaseEnvBootstrapOutput,
  releaseActionPriority: releaseActionPriorityOutput,
  releaseActionPriorityCsv: releaseActionPriorityCsvOutput,
  releaseActionPriorityMarkdown: releaseActionPriorityMarkdownOutput,
  releaseActionBatches: releaseActionBatchesOutput,
  releaseActionBatchesCsv: releaseActionBatchesCsvOutput,
  releaseActionBatchesMarkdown: releaseActionBatchesMarkdownOutput,
  releaseActionDependencyGraph: releaseActionDependencyGraphOutput,
  releaseActionDependencyGraphMarkdown: releaseActionDependencyGraphMarkdownOutput,
  releaseExecutionQueue: releaseExecutionQueueOutput,
  releaseExecutionQueueCsv: releaseExecutionQueueCsvOutput,
  releaseExecutionQueueMarkdown: releaseExecutionQueueMarkdownOutput,
  releaseExecutionCommands: releaseExecutionCommandsOutput,
  releaseMissingEnv: releaseMissingEnvOutput,
  releaseEnvOwnerMatrix: releaseEnvOwnerMatrixOutput,
  releaseEnvOwnerMatrixCsv: releaseEnvOwnerMatrixCsvOutput,
  releaseEnvOwnerMatrixMarkdown: releaseEnvOwnerMatrixMarkdownOutput,
  releaseEnvFillPriority: releaseEnvFillPriorityOutput,
  releaseEnvFillPriorityCsv: releaseEnvFillPriorityCsvOutput,
  releaseEnvFillPriorityMarkdown: releaseEnvFillPriorityMarkdownOutput,
  releaseEnvCanonicalFill: releaseEnvCanonicalFillOutput,
  releaseEnvCanonicalFillCsv: releaseEnvCanonicalFillCsvOutput,
  releaseEnvCanonicalFillMarkdown: releaseEnvCanonicalFillMarkdownOutput,
  releaseEnvCanonicalFillTemplate: releaseEnvCanonicalFillTemplateOutput,
  releaseEnvReadinessRedacted: releaseEnvReadinessRedactedOutput,
  releaseEnvReadinessRedactedCsv: releaseEnvReadinessRedactedCsvOutput,
  releaseEnvReadinessRedactedMarkdown: releaseEnvReadinessRedactedMarkdownOutput,
  releaseEnvReadinessGate: releaseEnvReadinessGateOutput,
  releaseEnvOwnerHandoffRedacted: releaseEnvOwnerHandoffRedactedOutput,
  releaseEnvOwnerHandoffRedactedCsv: releaseEnvOwnerHandoffRedactedCsvOutput,
  releaseEnvOwnerHandoffRedactedMarkdown: releaseEnvOwnerHandoffRedactedMarkdownOutput,
  releaseEnvOwnerInputPacket: releaseEnvOwnerInputPacketOutput,
  releaseEnvOwnerInputPacketCsv: releaseEnvOwnerInputPacketCsvOutput,
  releaseEnvOwnerInputPacketMarkdown: releaseEnvOwnerInputPacketMarkdownOutput,
  releaseConfigOwnerInputReconciliation: releaseConfigOwnerInputReconciliationOutput,
  releaseOwnerInputReceipt: releaseOwnerInputReceiptOutput,
  releaseOwnerInputReceiptCsv: releaseOwnerInputReceiptCsvOutput,
  releaseOwnerInputReceiptItemsCsv: releaseOwnerInputReceiptItemsCsvOutput,
  releaseOwnerInputReceiptItemsMarkdown: releaseOwnerInputReceiptItemsMarkdownOutput,
  releaseOwnerInputReceiptMarkdown: releaseOwnerInputReceiptMarkdownOutput,
  releaseEnvOwnerHandoff: releaseEnvOwnerHandoffOutput,
  releaseEnvOwnerHandoffCsv: releaseEnvOwnerHandoffCsvOutput,
  releaseEnvOwnerHandoffMarkdown: releaseEnvOwnerHandoffMarkdownOutput,
  releaseEnvOwnerTemplates: releaseEnvOwnerTemplatesOutput,
  releaseEnvOwnerTemplatesMarkdown: releaseEnvOwnerTemplatesMarkdownOutput,
  releaseMissingEnvTemplate: releaseMissingEnvTemplateOutput,
  releaseArtifactIntegrityGate: releaseArtifactIntegrityGateOutput,
};
for (const [index, owner] of (releaseEnvOwnerInputPacket.owners || []).entries()) {
  const ownerKey = String(index + 1).padStart(2, "0");
  generatedOutputs[`releaseEnvOwnerInputPacketOwner${ownerKey}Json`] = path.join(releaseEnvOwnerInputPacketDir, `${owner.fileName}.json`);
  generatedOutputs[`releaseEnvOwnerInputPacketOwner${ownerKey}Markdown`] = path.join(releaseEnvOwnerInputPacketDir, `${owner.fileName}.md`);
}
for (const [index, ownerChecklist] of (releaseOwnerInputReceiptOwnerItemChecklists || []).entries()) {
  const ownerKey = String(index + 1).padStart(2, "0");
  generatedOutputs[`releaseOwnerInputReceiptItemOwner${ownerKey}Markdown`] = path.join(releaseOwnerInputReceiptItemsDir, ownerChecklist.fileName);
}

const releaseArtifactIntegrityInputs = { ...generatedOutputs };
fs.writeFileSync(releaseArtifactIntegrityOutput, jsonPortable(releaseArtifactIntegrityArtifact(summary, releaseArtifactIntegrityInputs)));
fs.writeFileSync(releaseArtifactIntegrityMarkdownOutput, releaseArtifactIntegrityMarkdown(summary, releaseArtifactIntegrityInputs));
generatedOutputs.releaseArtifactIntegrity = releaseArtifactIntegrityOutput;
generatedOutputs.releaseArtifactIntegrityMarkdown = releaseArtifactIntegrityMarkdownOutput;

const generatedOutputText = Object.entries(generatedOutputs)
  .map(([name, filePath]) => `${name}=${filePath}`)
  .join("; ");

console.log(`[ddd-release-readiness-summary] status=${summary.status}; blockers=${summary.gate.blockers}; ${generatedOutputText}`);
