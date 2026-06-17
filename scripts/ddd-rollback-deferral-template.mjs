#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { requiredRollbackContexts, rollbackContextRemediation } from "./ddd-rollback-drill-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputFile = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_FILE
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_DEFERRAL_FILE)
  : path.join(repoRoot, "artifacts", "ddd", "rollback", "rollback-deferrals.template.json");
const handoffDir = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_HANDOFF_DIR
  ? path.resolve(process.env.DDD_ROLLBACK_DRILL_DEFERRAL_HANDOFF_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "rollback", "rollback-deferrals-owner-handoff");
const expiresAt = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_EXPIRES_AT || "replace-with-future-iso-timestamp";
const riskAcceptedBy = process.env.DDD_ROLLBACK_DRILL_RISK_ACCEPTED_BY || "replace-with-risk-acceptor";
const deferralEvidence = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_EVIDENCE || "CHANGE-12345";
const overwrite = process.env.DDD_ROLLBACK_DRILL_DEFERRAL_TEMPLATE_OVERWRITE === "true";

if (fs.existsSync(outputFile) && !overwrite) {
  console.error(`[ddd-rollback-deferral-template] deferral template already exists: ${outputFile}`);
  console.error("[ddd-rollback-deferral-template] set DDD_ROLLBACK_DRILL_DEFERRAL_TEMPLATE_OVERWRITE=true to replace it");
  process.exit(1);
}

const artifact = {
  generatedAt: new Date().toISOString(),
  instructions: {
    purpose: "Fill this file with real risk acceptance evidence, then pass it as DDD_ROLLBACK_DRILL_DEFERRAL_FILE to scripts/ddd-rollback-drill-evidence.mjs.",
    safety: "Do not use this template to bypass rollback drills. DEFERRED is accepted only with a real reason, named risk acceptor, concrete evidence reference, and future expiresAt.",
    evidence: "deferralEvidence must reference an approval ticket, change record, meeting note, artifact/log path, HTTPS link, or object URI.",
  },
  contexts: requiredRollbackContexts.map((context) => {
    const remediation = rollbackContextRemediation(context);
    return {
      context,
      owner: remediation.owner,
      intendedRollbackAction: remediation.action,
      notExercisableReason: `${context} rollback drill cannot be safely exercised before this release window because replace-with-specific-risk-context.`,
      riskAcceptedBy,
      deferralEvidence,
      expiresAt,
    };
  }),
};

function slug(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "owner";
}

function markdownEscape(value) {
  return String(value ?? "").replaceAll("|", "\\|");
}

function ownerHandoffRows(contexts) {
  const byOwner = new Map();
  for (const entry of contexts) {
    const rows = byOwner.get(entry.owner) || [];
    rows.push(entry);
    byOwner.set(entry.owner, rows);
  }
  return [...byOwner.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([owner, contexts]) => ({ owner, contexts }));
}

function ownerMarkdown(owner, contexts) {
  const lines = [
    `# Rollback Deferral Handoff: ${owner}`,
    "",
    "Fill the JSON deferral template with real approval evidence before running the strict rollback drill validator.",
    "",
    "Required fields for every deferred context:",
    "- `notExercisableReason`: concrete release-window reason, no placeholders.",
    "- `riskAcceptedBy`: named approver or approval group.",
    "- `deferralEvidence`: approval ticket, change record, meeting note, artifact/log path, HTTPS link, or object URI.",
    "- `expiresAt`: future ISO timestamp.",
    "",
    "| Context | Intended rollback action | Deferral evidence placeholder | Expires at |",
    "|---|---|---|---|",
    ...contexts.map((entry) => [
      markdownEscape(entry.context),
      markdownEscape(entry.intendedRollbackAction),
      markdownEscape(entry.deferralEvidence),
      markdownEscape(entry.expiresAt),
    ].join(" | ")).map((row) => `| ${row} |`),
    "",
    "After filling the approved deferral file, run:",
    "",
    "```sh",
    "DDD_ROLLBACK_DRILL_STRICT=true DDD_ROLLBACK_DRILL_DEFERRAL_FILE=<approved-deferrals.json> node scripts/ddd-rollback-drill-evidence.mjs",
    "```",
    "",
  ];
  return `${lines.join("\n")}\n`;
}

function summaryMarkdown(ownerRows) {
  const lines = [
    "# Rollback Deferral Owner Handoff",
    "",
    "This handoff is a coordination aid only. It does not make rollback drills pass. The release gate accepts `DEFERRED` only after the deferral JSON contains real reason, approver, evidence reference, and a future expiration for each context.",
    "",
    "| Owner | Contexts | Handoff file |",
    "|---|---|---|",
  ];
  for (const ownerRow of ownerRows) {
    const fileName = `${slug(ownerRow.owner)}.md`;
    lines.push(`| ${markdownEscape(ownerRow.owner)} | ${markdownEscape(ownerRow.contexts.map((entry) => entry.context).join(", "))} | ${fileName} |`);
  }
  lines.push("");
  lines.push("Strict validation command:");
  lines.push("");
  lines.push("```sh");
  lines.push("DDD_ROLLBACK_DRILL_STRICT=true DDD_ROLLBACK_DRILL_DEFERRAL_FILE=<approved-deferrals.json> node scripts/ddd-rollback-drill-evidence.mjs");
  lines.push("```");
  lines.push("");
  return `${lines.join("\n")}\n`;
}

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

const ownerRows = ownerHandoffRows(artifact.contexts);
fs.mkdirSync(handoffDir, { recursive: true });
for (const ownerRow of ownerRows) {
  fs.writeFileSync(path.join(handoffDir, `${slug(ownerRow.owner)}.md`), ownerMarkdown(ownerRow.owner, ownerRow.contexts));
}
fs.writeFileSync(path.join(handoffDir, "README.md"), summaryMarkdown(ownerRows));

console.log(`[ddd-rollback-deferral-template] wrote rollback deferral template; artifact=${outputFile}; handoffDir=${handoffDir}`);
