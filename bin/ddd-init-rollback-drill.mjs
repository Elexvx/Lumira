#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  buildRollbackDrillSummary,
  requiredRollbackContexts,
  rollbackContextRemediation,
} from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputFile = process.env.DDD_ROLLBACK_DRILL_FILE
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_FILE)
  : path.join(repoRoot, "artifacts", "ddd", "rollback", "rollback-drill.json");
const environment = process.env.DDD_ROLLBACK_DRILL_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseVersion = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const operator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const overwrite = process.env.DDD_ROLLBACK_DRILL_OVERWRITE === "true";

if (fs.existsSync(outputFile) && !overwrite) {
  console.error(`[ddd-init-rollback-drill] rollback drill artifact already exists: ${outputFile}`);
  console.error("[ddd-init-rollback-drill] set DDD_ROLLBACK_DRILL_OVERWRITE=true to replace it");
  process.exit(1);
}

const artifact = {
  generatedAt: new Date().toISOString(),
  status: "DRAFT",
  environment: environment || null,
  releaseVersion: releaseVersion || null,
  operator: operator || null,
  instructions: {
    pass: "Set status=PASS only after the rollback action was exercised and drillEvidence links to real logs, screenshots, commands or artifacts.",
    deferred: "Use status=DEFERRED only when the drill cannot be safely exercised; provide notExercisableReason, riskAcceptedBy, deferralEvidence and expiresAt.",
    blocked: "TODO entries are intentionally rejected by the release gate.",
  },
  contexts: requiredRollbackContexts.map((context) => {
    const remediation = rollbackContextRemediation(context);
    return {
      context,
      status: "TODO",
      expectedOwner: remediation.owner,
      expectedRollbackAction: remediation.action,
      evidenceRequirements: remediation.evidenceRequirements,
      rollbackAction: null,
      drillEvidence: null,
      validatedAt: null,
      notExercisableReason: null,
      riskAcceptedBy: null,
      deferralEvidence: null,
      expiresAt: null,
    };
  }),
};
artifact.summary = buildRollbackDrillSummary(artifact);

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

console.log(`[ddd-init-rollback-drill] initialized rollback drill draft; artifact=${outputFile}`);
