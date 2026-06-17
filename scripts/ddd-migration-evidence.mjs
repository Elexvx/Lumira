#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import {
  requiredMigrationEvidenceChecklist,
  requiredMigrationLocations,
  validateMigrationEvidenceContract,
} from "./ddd-migration-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_MIGRATION_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_MIGRATION_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "migration");
const outputFile = process.env.DDD_MIGRATION_EVIDENCE_REPORT
  ? path.resolve(process.env.DDD_MIGRATION_EVIDENCE_REPORT)
  : path.join(outputDir, "migration-evidence.json");
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_MIGRATION_STRICT === "true";
const allowEvidenceDowngrade = process.env.DDD_MIGRATION_ALLOW_EVIDENCE_DOWNGRADE === "true";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const checkEnvOnly = process.env.DDD_MIGRATION_CHECK_ENV === "true";
const handoffFile = process.env.DDD_MIGRATION_HANDOFF_FILE
  ? path.resolve(process.env.DDD_MIGRATION_HANDOFF_FILE)
  : path.join(outputDir, "migration-evidence-handoff.md");

function siblingFile(file, suffix) {
  return path.join(path.dirname(file), `${path.basename(file, path.extname(file))}${suffix}`);
}

function sha256(content) {
  return crypto.createHash("sha256").update(content).digest("hex");
}

const onlineDdlRequiredIndexes = Object.freeze([
  "idx_msg_notice_visible_recent",
  "idx_msg_notice_visible_target_user_recent",
  "idx_msg_notice_visible_target_role_recent",
]);

function migrationFiles(location) {
  const absolute = path.join(repoRoot, location);
  if (!fs.existsSync(absolute)) {
    return [];
  }
  return fs.readdirSync(absolute)
    .filter((file) => /^V[^/]+__.+\.sql$/.test(file))
    .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
    .map((file) => path.join(absolute, file));
}

function parseMigration(file) {
  const basename = path.basename(file);
  const match = basename.match(/^V([^_]+)__(.+)\.sql$/);
  const content = fs.readFileSync(file, "utf8");
  const onlineDdlCoverage = detectOnlineDdlCoverage(content);
  return {
    version: match?.[1] || "",
    description: (match?.[2] || "").replaceAll("_", " "),
    file: path.relative(repoRoot, file),
    bytes: Buffer.byteLength(content),
    sha256: sha256(content),
    empty: content.trim().length === 0,
    onlineDdlCoverage,
  };
}

function detectOnlineDdlCoverage(content) {
  const statements = content
    .split(";")
    .map((statement) => statement.trim())
    .filter(Boolean);
  const checkedStatements = [];
  const onlineStatements = [];
  const blockingStatements = [];
  const onlineIndexes = new Set();
  const blockingIndexes = new Set();

  for (const statement of statements) {
    if (!/\bALTER\s+TABLE\s+`?msg_notice`?/i.test(statement)) {
      continue;
    }
    const affectedIndexes = onlineDdlRequiredIndexes.filter((indexName) => {
      const escaped = indexName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return new RegExp(`\\b(?:DROP\\s+INDEX|ADD\\s+INDEX)\\s+\`?${escaped}\`?\\b`, "i").test(statement);
    });
    if (affectedIndexes.length === 0) {
      continue;
    }
    const statementDigest = sha256(`${statement};`);
    const hasOnlineDdl = /\bALGORITHM\s*=\s*INPLACE\b/i.test(statement)
      && /\bLOCK\s*=\s*NONE\b/i.test(statement);
    checkedStatements.push(statementDigest);
    const target = hasOnlineDdl ? onlineStatements : blockingStatements;
    target.push(statementDigest);
    for (const indexName of affectedIndexes) {
      if (hasOnlineDdl) {
        onlineIndexes.add(indexName);
      } else {
        blockingIndexes.add(indexName);
      }
    }
  }

  return {
    required: checkedStatements.length > 0,
    checkedStatements: checkedStatements.length,
    onlineStatements: onlineStatements.length,
    blockingStatements: blockingStatements.length,
    onlineIndexes: [...onlineIndexes].sort(),
    blockingIndexes: [...blockingIndexes].sort(),
    statementDigests: checkedStatements,
  };
}

function duplicateVersions(migrations) {
  const seen = new Map();
  const duplicates = [];
  for (const migration of migrations) {
    if (seen.has(migration.version)) {
      duplicates.push({
        version: migration.version,
        files: [seen.get(migration.version), migration.file],
      });
    } else {
      seen.set(migration.version, migration.file);
    }
  }
  return duplicates;
}

function readExistingArtifact(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function migrationRuntimeDowngradeIssues(existingRuntime, nextRuntime) {
  if (!existingRuntime || allowEvidenceDowngrade) {
    return [];
  }
  const issues = [];
  if (existingRuntime.freshDatabaseValidated === true && nextRuntime.freshDatabaseValidated !== true) {
    issues.push("refusing to overwrite existing fresh database migration validation with an unvalidated run");
  }
  if (existingRuntime.upgradeDatabaseValidated === true && nextRuntime.upgradeDatabaseValidated !== true) {
    issues.push("refusing to overwrite existing upgrade database migration validation with an unvalidated run");
  }
  if (hasText(existingRuntime.freshDatabaseEvidence) && !hasText(nextRuntime.freshDatabaseEvidence)) {
    issues.push("refusing to overwrite existing fresh database migration evidence with an empty reference");
  }
  if (hasText(existingRuntime.upgradeDatabaseEvidence) && !hasText(nextRuntime.upgradeDatabaseEvidence)) {
    issues.push("refusing to overwrite existing upgrade database migration evidence with an empty reference");
  }
  if (hasText(existingRuntime.environment) && !hasText(nextRuntime.environment)) {
    issues.push("refusing to overwrite existing migration environment with an empty value");
  }
  if (hasText(existingRuntime.releaseCandidate) && !hasText(nextRuntime.releaseCandidate)) {
    issues.push("refusing to overwrite existing migration release candidate with an empty value");
  }
  if (hasText(existingRuntime.operator) && !hasText(nextRuntime.operator)) {
    issues.push("refusing to overwrite existing migration operator with an empty value");
  }
  if (hasText(existingRuntime.completedAt) && !hasText(nextRuntime.completedAt)) {
    issues.push("refusing to overwrite existing migration completedAt with an empty value");
  }
  return issues;
}

function buildRuntimeDiagnostics(runtimeEvidence) {
  return [
    {
      id: "fresh-database-drill",
      owner: "database",
      status: runtimeEvidence.freshDatabaseValidated === true ? "PASS" : "MISSING",
      action: "Run Flyway against an empty production-equivalent database and archive schema history plus Flyway logs.",
      envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      evidence: runtimeEvidence.freshDatabaseEvidence || null,
    },
    {
      id: "upgrade-database-drill",
      owner: "database",
      status: runtimeEvidence.upgradeDatabaseValidated === true ? "PASS" : "MISSING",
      action: "Run Flyway against a copy of the previous production schema and archive before/after schema history plus Flyway logs.",
      envKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      evidence: runtimeEvidence.upgradeDatabaseEvidence || null,
    },
    {
      id: "migration-environment",
      owner: "release-infra",
      status: hasText(runtimeEvidence.environment) ? "PASS" : "MISSING",
      action: "Set the production-equivalent migration environment name before generating evidence.",
      envKeys: ["DDD_MIGRATION_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"],
      evidence: runtimeEvidence.environment || null,
    },
    {
      id: "migration-release-candidate",
      owner: "release-infra",
      status: hasText(runtimeEvidence.releaseCandidate) ? "PASS" : "MISSING",
      action: "Set the immutable release candidate or commit SHA for the migration drill.",
      envKeys: ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"],
      evidence: runtimeEvidence.releaseCandidate || null,
    },
    {
      id: "migration-operator",
      owner: "release-owner",
      status: hasText(runtimeEvidence.operator) ? "PASS" : "MISSING",
      action: "Record the operator or CI actor who executed the migration drill.",
      envKeys: ["DDD_MIGRATION_OPERATOR", "DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"],
      evidence: runtimeEvidence.operator || null,
    },
    {
      id: "migration-completed-at",
      owner: "release-owner",
      status: hasText(runtimeEvidence.completedAt) ? "PASS" : "MISSING",
      action: "Record the ISO timestamp when both migration drills completed.",
      envKeys: ["DDD_MIGRATION_COMPLETED_AT"],
      evidence: runtimeEvidence.completedAt || null,
    },
  ];
}

function buildRuntimeProofs(runtimeEvidence) {
  return [
    {
      id: "fresh-database",
      label: "Fresh database Flyway drill",
      validated: runtimeEvidence.freshDatabaseValidated === true,
      evidence: runtimeEvidence.freshDatabaseEvidence || null,
      requiredEnvKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      requiredEvidence: "Flyway log plus schema-history artifact from an empty production-equivalent database.",
    },
    {
      id: "upgrade-database",
      label: "Previous schema upgrade Flyway drill",
      validated: runtimeEvidence.upgradeDatabaseValidated === true,
      evidence: runtimeEvidence.upgradeDatabaseEvidence || null,
      requiredEnvKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      requiredEvidence: "Before/after schema-history artifact plus Flyway log from a copy of the previous production schema.",
    },
  ];
}

function envSatisfied(keys) {
  return keys.some((key) => hasText(process.env[key]));
}

function migrationEnvReadiness(runtimeEvidence) {
  return [
    {
      id: "fresh-database-drill",
      owner: "database",
      status: runtimeEvidence.freshDatabaseValidated === true && hasText(runtimeEvidence.freshDatabaseEvidence) ? "READY" : "MISSING",
      requiredGroups: [
        ["DDD_MIGRATION_FRESH_DB_VALIDATED"],
        ["DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      ],
      action: "Set DDD_MIGRATION_FRESH_DB_VALIDATED=true and point DDD_MIGRATION_FRESH_DB_EVIDENCE at the fresh-database Flyway log/schema-history artifact.",
    },
    {
      id: "upgrade-database-drill",
      owner: "database",
      status: runtimeEvidence.upgradeDatabaseValidated === true && hasText(runtimeEvidence.upgradeDatabaseEvidence) ? "READY" : "MISSING",
      requiredGroups: [
        ["DDD_MIGRATION_UPGRADE_DB_VALIDATED"],
        ["DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      ],
      action: "Set DDD_MIGRATION_UPGRADE_DB_VALIDATED=true and point DDD_MIGRATION_UPGRADE_DB_EVIDENCE at the previous-schema upgrade Flyway log/schema-history artifact.",
    },
    {
      id: "migration-environment",
      owner: "release-infra",
      status: hasText(runtimeEvidence.environment) ? "READY" : "MISSING",
      requiredGroups: [["DDD_MIGRATION_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"]],
      action: "Set the production-equivalent migration environment name.",
    },
    {
      id: "migration-release-candidate",
      owner: "release-infra",
      status: hasText(runtimeEvidence.releaseCandidate) ? "READY" : "MISSING",
      requiredGroups: [["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"]],
      action: "Set the immutable release candidate or commit SHA.",
    },
    {
      id: "migration-operator",
      owner: "release-owner",
      status: hasText(runtimeEvidence.operator) ? "READY" : "MISSING",
      requiredGroups: [["DDD_MIGRATION_OPERATOR", "DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"]],
      action: "Set the operator or CI actor who executed the migration drill.",
    },
    {
      id: "migration-completed-at",
      owner: "release-owner",
      status: hasText(runtimeEvidence.completedAt) ? "READY" : "MISSING",
      requiredGroups: [["DDD_MIGRATION_COMPLETED_AT"]],
      action: "Set the ISO timestamp when both migration drills completed.",
    },
  ].map((item) => ({
    ...item,
    missingGroups: item.requiredGroups.filter((group) => !envSatisfied(group)),
  }));
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

function writeMigrationHandoff(readiness, file) {
  const validationCommands = [
    "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    "DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs",
    "node scripts/ddd-collect-explain.mjs",
    "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  ];
  const fastPathCommands = [
    "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
    "DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
  ];
  const ownerSummary = [...readiness.reduce((map, item) => {
    const owner = item.owner || "release-owner";
    if (!map.has(owner)) {
      map.set(owner, { owner, total: 0, ready: 0, missing: 0 });
    }
    const entry = map.get(owner);
    entry.total += 1;
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
    ready: owner.ready,
    missing: owner.missing,
    missingChecks: readiness
      .filter((item) => item.owner === owner.owner && item.status !== "READY")
      .map((item) => item.id),
    requiredEnvKeys: sortedUniqueStrings(readiness
      .filter((item) => item.owner === owner.owner)
      .flatMap((item) => item.requiredGroups.flatMap((group) => group))),
    nextCommand: owner.missing === 0
      ? "DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs"
      : "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
  }));
  const artifact = {
    generatedAt: new Date().toISOString(),
    status: readiness.every((item) => item.status === "READY") ? "READY" : "MISSING",
    redacted: true,
    valuePolicy: "No concrete database credentials, DSNs, or migration artifact contents are emitted; only env key names, owners, status, and commands are included.",
    fastPath: {
      objective: "Close the migration release blocker without bypassing fresh-database and previous-schema upgrade evidence.",
      blockedUntil: "Both fresh DB and previous-schema upgrade Flyway drills have concrete evidence, operator, environment, release candidate, and completion timestamp.",
      commands: fastPathCommands,
    },
    summary: {
      checks: readiness.length,
      ready: readiness.filter((item) => item.status === "READY").length,
      missing: readiness.filter((item) => item.status !== "READY").length,
      owners: ownerSummary.length,
    },
    ownerSummary,
    ownerRunbook,
    evidenceChecklist: requiredMigrationEvidenceChecklist,
    validationCommands,
    checks: readiness,
  };
  const lines = [
    "# Migration Evidence Handoff",
    "",
    "This handoff does not satisfy the release gate by itself. It lists the runtime evidence required before `migration-evidence.json` can become PASS.",
    "",
    `Status: ${artifact.status}`,
    `Value policy: ${artifact.valuePolicy}`,
    `Checks: ${artifact.summary.checks}`,
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
  lines.push("| Owner | Status | Missing checks | Required env keys | Next command |");
  lines.push("|---|---|---|---|---|");
  for (const owner of ownerRunbook) {
    lines.push(`| ${markdownEscape(owner.owner)} | ${markdownEscape(owner.status)} | ${markdownEscape(owner.missingChecks.join("; ") || "none")} | ${markdownEscape(owner.requiredEnvKeys.join("; "))} | ${markdownEscape(owner.nextCommand)} |`);
  }
  lines.push("");
  lines.push("Evidence checklist:");
  lines.push("");
  lines.push("| Evidence package | Owner | Required env keys | Required artifacts | Acceptance criteria |");
  lines.push("|---|---|---|---|---|");
  for (const item of artifact.evidenceChecklist) {
    lines.push(`| ${markdownEscape(item.id)} | ${markdownEscape(item.owner)} | ${markdownEscape(item.requiredEnvKeys.join("; "))} | ${markdownEscape(item.requiredArtifacts.join("; "))} | ${markdownEscape(item.acceptanceCriteria.join("; "))} |`);
  }
  lines.push("");
  lines.push("| Owner | Check | Status | Required env keys | Action |");
  lines.push("|---|---|---|---|---|");
  for (const item of readiness) {
    lines.push(`| ${markdownEscape(item.owner)} | ${markdownEscape(item.id)} | ${markdownEscape(item.status)} | ${markdownEscape(item.requiredGroups.map((group) => group.join(" or ")).join("; "))} | ${markdownEscape(item.action)} |`);
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
    "check",
    "status",
    "requiredEnvKeys",
    "missingEnvKeys",
    "nextCommand",
    "action",
  ]];
  for (const item of readiness) {
    const owner = ownerRunbook.find((entry) => entry.owner === item.owner);
    rows.push([
      item.owner,
      item.id,
      item.status,
      item.requiredGroups.map((group) => group.join(" or ")).join("; "),
      item.missingGroups.map((group) => group.join(" or ")).join("; "),
      owner?.nextCommand || "",
      item.action,
    ]);
  }
  fs.writeFileSync(siblingFile(file, ".csv"), `${rows.map((row) => row.map(csvCell).join(",")).join("\n")}\n`);
}

const locations = requiredMigrationLocations.map((location) => {
  const absolute = path.join(repoRoot, location);
  const exists = fs.existsSync(absolute);
  const migrations = migrationFiles(location).map(parseMigration);
  const onlineDdlCoverage = summarizeOnlineDdlCoverage(migrations);
  return {
    location,
    exists,
    migrationCount: migrations.length,
    duplicateVersions: duplicateVersions(migrations),
    emptyFiles: migrations.filter((migration) => migration.empty).map((migration) => migration.file),
    onlineDdlCoverage,
    migrations,
  };
});

function summarizeOnlineDdlCoverage(migrations) {
  const coverage = {
    requiredMigrations: 0,
    checkedStatements: 0,
    onlineStatements: 0,
    blockingStatements: 0,
    onlineIndexes: [],
    blockingIndexes: [],
  };
  const onlineIndexes = new Set();
  const blockingIndexes = new Set();
  for (const migration of migrations) {
    const migrationCoverage = migration.onlineDdlCoverage || {};
    if (migrationCoverage.required) {
      coverage.requiredMigrations += 1;
    }
    coverage.checkedStatements += Number(migrationCoverage.checkedStatements) || 0;
    coverage.onlineStatements += Number(migrationCoverage.onlineStatements) || 0;
    coverage.blockingStatements += Number(migrationCoverage.blockingStatements) || 0;
    for (const indexName of migrationCoverage.onlineIndexes || []) {
      onlineIndexes.add(indexName);
    }
    for (const indexName of migrationCoverage.blockingIndexes || []) {
      blockingIndexes.add(indexName);
    }
  }
  coverage.onlineIndexes = [...onlineIndexes].sort();
  coverage.blockingIndexes = [...blockingIndexes].sort();
  return coverage;
}

function summarizeLocationsOnlineDdlCoverage(locationReports) {
  return summarizeOnlineDdlCoverage(locationReports.flatMap((location) => location.migrations || []));
}

const blockers = [];
for (const location of locations) {
  if (!location.exists) {
    blockers.push(`missing migration location ${location.location}`);
  }
  if (location.migrationCount === 0) {
    blockers.push(`no migration files in ${location.location}`);
  }
  for (const duplicate of location.duplicateVersions) {
    blockers.push(`duplicate Flyway version ${duplicate.version} in ${location.location}: ${duplicate.files.join(", ")}`);
  }
  for (const file of location.emptyFiles) {
    blockers.push(`empty migration file ${file}`);
  }
}

const runtime = {
  freshDatabaseValidated: process.env.DDD_MIGRATION_FRESH_DB_VALIDATED === "true",
  upgradeDatabaseValidated: process.env.DDD_MIGRATION_UPGRADE_DB_VALIDATED === "true",
  evidence: process.env.DDD_MIGRATION_RUNTIME_EVIDENCE || "",
  environment: process.env.DDD_MIGRATION_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "",
  releaseCandidate,
  operator: process.env.DDD_MIGRATION_OPERATOR || process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "",
  completedAt: process.env.DDD_MIGRATION_COMPLETED_AT || "",
  freshDatabaseEvidence: process.env.DDD_MIGRATION_FRESH_DB_EVIDENCE || process.env.DDD_MIGRATION_RUNTIME_EVIDENCE || "",
  upgradeDatabaseEvidence: process.env.DDD_MIGRATION_UPGRADE_DB_EVIDENCE || process.env.DDD_MIGRATION_RUNTIME_EVIDENCE || "",
};
const runtimeProofs = buildRuntimeProofs(runtime);
const runtimeReady = runtimeProofs.every((proof) => proof.validated && hasText(proof.evidence));
const existingArtifact = readExistingArtifact(outputFile);
const downgradeIssues = migrationRuntimeDowngradeIssues(existingArtifact?.runtime, runtime);
const onlineDdlCoverage = summarizeLocationsOnlineDdlCoverage(locations);
const envReadiness = migrationEnvReadiness(runtime);

if (checkEnvOnly) {
  writeMigrationHandoff(envReadiness, handoffFile);
  const missing = envReadiness.filter((item) => item.status !== "READY");
  if (missing.length > 0) {
    for (const item of missing) {
      const missingGroups = item.missingGroups.map((group) => group.join(" or ")).join("; ");
      console.error(`[ddd-migration-evidence] ${item.id} missing env/evidence: ${missingGroups}`);
    }
    console.error(`[ddd-migration-evidence] wrote migration evidence handoff to ${handoffFile}`);
    process.exit(1);
  }
  console.log(`[ddd-migration-evidence] migration evidence env ready; handoff=${handoffFile}`);
  process.exit(0);
}

for (const proof of runtimeProofs) {
  if (!proof.validated || !hasText(proof.evidence)) {
    blockers.push(`${proof.id} migration drill is not validated with concrete evidence`);
  }
}

const artifact = {
  generatedAt: new Date().toISOString(),
  status: blockers.length === 0 ? "PASS" : "FAIL",
  runtime,
  summary: {
    locations: locations.length,
    migrationFiles: locations.reduce((sum, location) => sum + location.migrationCount, 0),
    duplicateVersionLocations: locations.filter((location) => location.duplicateVersions.length > 0).length,
    emptyFiles: locations.reduce((sum, location) => sum + location.emptyFiles.length, 0),
    runtimeReady,
    onlineDdlCoverage,
  },
  runtimeReady,
  runtimeProofs,
  runtimeDiagnostics: buildRuntimeDiagnostics(runtime),
  requiredLocations: requiredMigrationLocations,
  locations,
  blockers,
};

if (downgradeIssues.length > 0) {
  for (const issue of downgradeIssues) {
    console.error(`[ddd-migration-evidence] ${issue}; set DDD_MIGRATION_ALLOW_EVIDENCE_DOWNGRADE=true to replace it intentionally`);
  }
  console.error(`[ddd-migration-evidence] kept existing artifact unchanged at ${outputFile}`);
  process.exit(1);
}

blockers.push(...validateMigrationEvidenceContract(artifact, { strict: strictEvidence, checkStatus: false }));
artifact.status = blockers.length === 0 ? "PASS" : "FAIL";

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-migration-evidence] ${blocker}`);
  }
  console.error(`[ddd-migration-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-migration-evidence] migration evidence passed; files=${artifact.summary.migrationFiles}; artifact=${outputFile}`);
