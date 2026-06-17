#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  buildRollbackDrillBlockers,
  buildRollbackDrillSummary,
  requiredRollbackEvidenceChecklist,
  requiredRollbackContexts,
  rollbackContextRemediation,
} from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const inputFile = process.env.DDD_ROLLBACK_DRILL_FILE
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_FILE)
  : path.join(repoRoot, "artifacts", "ddd", "rollback", "rollback-drill.json");
const sourceEnvironment = process.env.DDD_ROLLBACK_DRILL_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const strict = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_ROLLBACK_DRILL_STRICT === "true";
const deferralFile = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_FILE
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_DEFERRAL_FILE)
  : null;
const overwritePassWithDeferral = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_OVERWRITE_PASS === "true";
const checkEnvOnly = process.env.DDD_ROLLBACK_DRILL_CHECK_ENV === "true";
const handoffFile = process.env.DDD_ROLLBACK_DRILL_HANDOFF_FILE
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_HANDOFF_FILE)
  : path.join(repoRoot, "artifacts", "ddd", "rollback", "rollback-drill-handoff.md");

const blockers = [];
const warnings = [];
const appliedDeferrals = [];

function buildContextDiagnostics(contexts = []) {
  const byContext = new Map(contexts.map((entry) => [entry.context, entry]));
  return requiredRollbackContexts.map((context) => {
    const entry = byContext.get(context) || { context, status: "MISSING" };
    const remediation = rollbackContextRemediation(context);
    const evidence = entry.drillEvidence || entry.deferralEvidence || null;
    return {
      context,
      status: entry.status || "MISSING",
      owner: remediation.owner,
      action: remediation.action,
      evidenceRequirements: remediation.evidenceRequirements,
      evidence,
      ready: entry.status === "PASS" || entry.status === "DEFERRED",
      missingEvidence: !evidence,
      deferralApplied: appliedDeferrals.includes(context),
    };
  });
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function envSatisfied(keys) {
  return keys.some((key) => hasText(process.env[key]));
}

function markdownEscape(value) {
  return String(value ?? "").replaceAll("|", "\\|");
}

function csvCell(value) {
  if (Array.isArray(value)) {
    return csvCell(value.join("; "));
  }
  const text = String(value ?? "");
  return /[",\n]/.test(text) ? `"${text.replaceAll("\"", "\"\"")}"` : text;
}

function sortedUniqueStrings(values = []) {
  return [...new Set(values.filter((value) => value !== undefined && value !== null).map(String).filter(Boolean))].sort();
}

function siblingFile(file, suffix) {
  return path.join(path.dirname(file), `${path.basename(file, path.extname(file))}${suffix}`);
}

function buildRollbackEnvReadiness(artifact = {}) {
  const byContext = new Map((artifact.contexts || []).map((entry) => [entry.context, entry]));
  return requiredRollbackContexts.map((context) => {
    const entry = byContext.get(context) || { context, status: "MISSING" };
    const remediation = rollbackContextRemediation(context);
    const ownerKey = `${context.toUpperCase()}_ROLLBACK_OWNER`;
    const evidenceKey = `${context.toUpperCase()}_ROLLBACK_EVIDENCE`;
    const deferralKey = `${context.toUpperCase()}_ROLLBACK_DEFERRAL_EVIDENCE`;
    const ready = entry.status === "PASS" || entry.status === "DEFERRED";
    const evidenceReady = hasText(entry.drillEvidence) || hasText(entry.deferralEvidence)
      || envSatisfied([evidenceKey, deferralKey, "DDD_ROLLBACK_DRILL_DEFERRAL_FILE"]);
    return {
      context,
      owner: remediation.owner,
      status: ready && evidenceReady ? "READY" : "MISSING",
      currentStatus: entry.status || "MISSING",
      requiredGroups: [
        ["DDD_ROLLBACK_DRILL_FILE"],
        ["DDD_ROLLBACK_DRILL_DEFERRAL_FILE", evidenceKey, deferralKey],
        ["DDD_EVIDENCE_ENVIRONMENT", "DDD_ROLLBACK_DRILL_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"],
        ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"],
        ["DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR", ownerKey],
      ],
      action: remediation.action,
      evidenceRequirements: remediation.evidenceRequirements,
    };
  }).map((item) => ({
    ...item,
    missingGroups: item.requiredGroups.filter((group) => !envSatisfied(group)),
  }));
}

function writeRollbackHandoff(readiness, file) {
  const validationCommands = [
    "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
    "node scripts/ddd-rollback-deferral-template.mjs",
    "DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  ];
  const fastPathCommands = [
    "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
    "node scripts/ddd-rollback-deferral-template.mjs",
    "DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  ];
  const ownerSummary = [...readiness.reduce((map, item) => {
    const owner = item.owner || "release-owner";
    if (!map.has(owner)) {
      map.set(owner, { owner, contexts: 0, ready: 0, missing: 0 });
    }
    const entry = map.get(owner);
    entry.contexts += 1;
    if (item.status === "READY") {
      entry.ready += 1;
    } else {
      entry.missing += 1;
    }
    return map;
  }, new Map()).values()].sort((left, right) => (
    right.missing - left.missing
    || left.owner.localeCompare(right.owner)
  ));
  const ownerRunbook = ownerSummary.map((owner) => ({
    owner: owner.owner,
    status: owner.missing === 0 ? "READY" : "MISSING",
    contexts: readiness
      .filter((item) => item.owner === owner.owner)
      .map((item) => item.context),
    missingContexts: readiness
      .filter((item) => item.owner === owner.owner && item.status !== "READY")
      .map((item) => item.context),
    requiredEnvKeys: sortedUniqueStrings(readiness
      .filter((item) => item.owner === owner.owner)
      .flatMap((item) => item.requiredGroups.flatMap((group) => group))),
    nextCommand: owner.missing === 0
      ? "DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs"
      : "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
  }));
  const artifact = {
    generatedAt: new Date().toISOString(),
    status: readiness.every((item) => item.status === "READY") ? "READY" : "MISSING",
    redacted: true,
    valuePolicy: "No concrete rollback artifact contents, credentials, request payloads, or provider tokens are emitted; only context names, owner names, env key names, status, and commands are included.",
    fastPath: {
      objective: "Close rollback-safety blockers without replacing real PASS drills or approved DEFERRED records.",
      blockedUntil: "Every bounded context has PASS rollback drill evidence or an approved unexpired DEFERRED risk acceptance.",
      commands: fastPathCommands,
    },
    summary: {
      contexts: readiness.length,
      ready: readiness.filter((item) => item.status === "READY").length,
      missing: readiness.filter((item) => item.status !== "READY").length,
      owners: ownerSummary.length,
    },
    ownerSummary,
    ownerRunbook,
    evidenceChecklist: requiredRollbackEvidenceChecklist,
    validationCommands,
    contexts: readiness,
  };
  const lines = [
    "# Rollback Drill Evidence Handoff",
    "",
    "This handoff does not satisfy the release gate by itself. Each bounded context must provide real PASS evidence or an approved DEFERRED record before `rollback-drill.json` can become PASS.",
    "",
    `Status: ${artifact.status}`,
    `Value policy: ${artifact.valuePolicy}`,
    `Contexts: ${artifact.summary.contexts}`,
    `Ready: ${artifact.summary.ready}`,
    `Missing: ${artifact.summary.missing}`,
    "",
    "Fast path:",
    "",
    `- Objective: ${artifact.fastPath.objective}`,
    `- Blocked until: ${artifact.fastPath.blockedUntil}`,
    "- Commands:",
    "",
    "```sh",
  ];
  for (const command of fastPathCommands) {
    lines.push(command);
  }
  lines.push("```");
  lines.push("");
  lines.push("Owner runbook:");
  lines.push("");
  lines.push("| Owner | Status | Contexts | Missing contexts | Required env keys | Next command |");
  lines.push("|---|---|---|---|---|---|");
  for (const owner of ownerRunbook) {
    lines.push(`| ${markdownEscape(owner.owner)} | ${markdownEscape(owner.status)} | ${markdownEscape(owner.contexts.join("; "))} | ${markdownEscape(owner.missingContexts.join("; ") || "none")} | ${markdownEscape(owner.requiredEnvKeys.join("; "))} | ${markdownEscape(owner.nextCommand)} |`);
  }
  lines.push("");
  lines.push("Evidence checklist:");
  lines.push("");
  lines.push("| Evidence path | Status | Required fields | Required artifacts | Acceptance criteria |");
  lines.push("|---|---|---|---|---|");
  for (const item of artifact.evidenceChecklist) {
    lines.push(`| ${markdownEscape(item.id)} | ${markdownEscape(item.status)} | ${markdownEscape(item.requiredFields.join("; "))} | ${markdownEscape(item.requiredArtifacts.join("; "))} | ${markdownEscape(item.acceptanceCriteria.join("; "))} |`);
  }
  lines.push("");
  lines.push(
    "| Owner | Context | Current status | Env status | Required env keys | Action |",
  );
  lines.push("|---|---|---|---|---|---|");
  for (const item of readiness) {
    lines.push(`| ${markdownEscape(item.owner)} | ${markdownEscape(item.context)} | ${markdownEscape(item.currentStatus)} | ${markdownEscape(item.status)} | ${markdownEscape(item.requiredGroups.map((group) => group.join(" or ")).join("; "))} | ${markdownEscape(item.action)} |`);
  }
  lines.push("");
  lines.push("Validation commands:");
  lines.push("");
  lines.push("```sh");
  for (const command of validationCommands) {
    lines.push(command);
  }
  lines.push("```");
  lines.push("");
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${lines.join("\n")}\n`);
  fs.writeFileSync(siblingFile(file, ".json"), `${JSON.stringify(artifact, null, 2)}\n`);
  const rows = [[
    "owner",
    "context",
    "currentStatus",
    "envStatus",
    "requiredEnvKeys",
    "missingEnvKeys",
    "evidenceRequirements",
    "nextCommand",
    "action",
  ]];
  for (const item of readiness) {
    const owner = ownerRunbook.find((entry) => entry.owner === item.owner);
    rows.push([
      item.owner,
      item.context,
      item.currentStatus,
      item.status,
      item.requiredGroups.map((group) => group.join(" or ")).join("; "),
      item.missingGroups.map((group) => group.join(" or ")).join("; "),
      item.evidenceRequirements.join("; "),
      owner?.nextCommand || "",
      item.action,
    ]);
  }
  fs.writeFileSync(siblingFile(file, ".csv"), `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`);
}

function loadDeferrals(file) {
  if (!file) {
    return [];
  }
  if (!fs.existsSync(file)) {
    blockers.push(`missing rollback drill deferral file ${file}`);
    return [];
  }
  try {
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    const entries = Array.isArray(data?.contexts) ? data.contexts : Array.isArray(data) ? data : [];
    if (entries.length === 0) {
      blockers.push("rollback drill deferral file must contain a non-empty contexts array");
    }
    return entries;
  } catch (error) {
    blockers.push(`invalid rollback drill deferral JSON: ${error.message}`);
    return [];
  }
}

function applyDeferrals(artifact, deferrals) {
  if (deferrals.length === 0) {
    return;
  }
  const byContext = new Map((artifact.contexts || []).map((entry) => [entry.context, entry]));
  for (const deferral of deferrals) {
    const context = String(deferral?.context || "");
    if (!requiredRollbackContexts.includes(context)) {
      blockers.push(`rollback drill deferral file contains unknown context ${context || "missing"}`);
      continue;
    }
    const current = byContext.get(context) || { context };
    if (current.status === "PASS" && !overwritePassWithDeferral) {
      warnings.push(`${context} PASS rollback drill was not overwritten by deferral file`);
      continue;
    }
    const merged = {
      context,
      status: "DEFERRED",
      rollbackAction: null,
      drillEvidence: null,
      validatedAt: null,
      notExercisableReason: deferral.notExercisableReason ?? current.notExercisableReason ?? null,
      riskAcceptedBy: deferral.riskAcceptedBy ?? current.riskAcceptedBy ?? null,
      deferralEvidence: deferral.deferralEvidence ?? current.deferralEvidence ?? null,
      expiresAt: deferral.expiresAt ?? current.expiresAt ?? null,
    };
    byContext.set(context, merged);
    appliedDeferrals.push(context);
  }
  artifact.contexts = requiredRollbackContexts.map((context) => byContext.get(context) || { context, status: "MISSING" });
}

if (checkEnvOnly) {
  let artifact = { contexts: [] };
  if (fs.existsSync(inputFile)) {
    try {
      artifact = JSON.parse(fs.readFileSync(inputFile, "utf8"));
    } catch (error) {
      blockers.push(`invalid rollback drill JSON: ${error.message}`);
    }
  }
  const readiness = buildRollbackEnvReadiness(artifact);
  writeRollbackHandoff(readiness, handoffFile);
  const missing = readiness.filter((item) => item.status !== "READY");
  if (blockers.length > 0 || missing.length > 0) {
    for (const blocker of blockers) {
      console.error(`[ddd-rollback-drill-evidence] ${blocker}`);
    }
    for (const item of missing) {
      const missingGroups = item.missingGroups.map((group) => group.join(" or ")).join("; ");
      console.error(`[ddd-rollback-drill-evidence] ${item.context} missing rollback evidence/env: ${missingGroups}`);
    }
    console.error(`[ddd-rollback-drill-evidence] wrote rollback drill handoff to ${handoffFile}`);
    process.exit(1);
  }
  console.log(`[ddd-rollback-drill-evidence] rollback drill env ready; handoff=${handoffFile}`);
  process.exit(0);
}

function writeMissingFailureArtifact() {
  const artifact = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    environment: sourceEnvironment || null,
    releaseVersion: releaseCandidate || null,
    operator: evidenceOperator || null,
    source: "ddd-rollback-drill-evidence",
    contexts: requiredRollbackContexts.map((context) => ({
      context,
      status: "MISSING",
      rollbackAction: null,
      drillEvidence: null,
      validatedAt: null,
    })),
    contextDiagnostics: buildContextDiagnostics(),
    blockers,
  };
  artifact.summary = buildRollbackDrillSummary(artifact);
  fs.mkdirSync(path.dirname(inputFile), { recursive: true });
  fs.writeFileSync(inputFile, `${JSON.stringify(artifact, null, 2)}\n`);
}

if (!fs.existsSync(inputFile)) {
  blockers.push(`missing rollback drill artifact ${inputFile}`);
  writeMissingFailureArtifact();
} else {
  let artifact = null;
  try {
    artifact = JSON.parse(fs.readFileSync(inputFile, "utf8"));
  } catch (error) {
    blockers.push(`invalid rollback drill JSON: ${error.message}`);
  }

  if (artifact) {
    const checkedAt = new Date().toISOString();
    artifact.generatedAt = checkedAt;
    if (!artifact.environment && sourceEnvironment) {
      artifact.environment = sourceEnvironment;
    }
    if (!artifact.releaseVersion && releaseCandidate) {
      artifact.releaseVersion = releaseCandidate;
    }
    if (!artifact.operator && evidenceOperator) {
      artifact.operator = evidenceOperator;
    }
    applyDeferrals(artifact, loadDeferrals(deferralFile));
    artifact.blockers = blockers;
    artifact.warnings = warnings;
    artifact.appliedDeferrals = appliedDeferrals;
    artifact.contextDiagnostics = buildContextDiagnostics(artifact.contexts);
    artifact.summary = buildRollbackDrillSummary(artifact);
    blockers.push(...buildRollbackDrillBlockers(artifact, { strict }));
    artifact.status = blockers.length > 0 ? "FAIL" : "PASS";
    artifact.checkedAt = checkedAt;
    artifact.blockers = blockers;
    artifact.warnings = warnings;
    artifact.appliedDeferrals = appliedDeferrals;
    artifact.contextDiagnostics = buildContextDiagnostics(artifact.contexts);
    artifact.summary = buildRollbackDrillSummary(artifact);
    fs.writeFileSync(inputFile, `${JSON.stringify(artifact, null, 2)}\n`);
    if (blockers.length === 0) {
      console.log(`[ddd-rollback-drill-evidence] rollback drill evidence passed; artifact=${inputFile}`);
      process.exit(0);
    }
  }
}

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-rollback-drill-evidence] ${blocker}`);
  }
  process.exit(1);
}
