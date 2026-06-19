#!/usr/bin/env node

import { evidenceValueIssue } from "./ddd-release-evidence-utils.mjs";

const args = new Set(process.argv.slice(2));
const help = args.has("--help") || args.has("-h");

function printHelp() {
  console.log(`DDD staging data safety check

Usage:
  node scripts/ddd-staging-data-safety-check.mjs [options]

Options:
  --help, -h    Show this help.

Environment:
  DDD_ROLLBACK_DRILL_FILE or DDD_ROLLBACK_DRILL_DEFERRAL_FILE
  DDD_MIGRATION_FRESH_DB_VALIDATED=true
  DDD_MIGRATION_FRESH_DB_EVIDENCE
  DDD_MIGRATION_UPGRADE_DB_VALIDATED=true
  DDD_MIGRATION_UPGRADE_DB_EVIDENCE
  DDD_MIGRATION_COMPLETED_AT
  DDD_EXPLAIN_DATABASE
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD
  DDD_EXPLAIN_ENVIRONMENT or DDD_EVIDENCE_ENVIRONMENT or DDD_RELEASE_ENVIRONMENT

Examples:
  node scripts/ddd-staging-data-safety-check.mjs
`);
}

if (help) {
  printHelp();
  process.exit(0);
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function envAny(keys) {
  return keys.find((key) => hasText(process.env[key])) || null;
}

function requiredValue(name, value) {
  const issue = evidenceValueIssue(value);
  return {
    name,
    valuePresent: hasText(value),
    issue: issue ? `${name} ${issue}` : null,
  };
}

function requiredFlag(name, expected = "true") {
  const value = process.env[name] || "";
  return {
    name,
    expected,
    valuePresent: hasText(value),
    pass: value === expected,
    issue: value === expected ? null : `${name} must be ${expected}`,
  };
}

function requiredGroup(name, keys) {
  const selected = envAny(keys);
  return {
    name,
    keys,
    selected,
    pass: selected !== null,
    issue: selected ? null : `${name} requires one of ${keys.join(", ")}`,
  };
}

const rollbackChecks = [
  requiredGroup("rollback-evidence-source", ["DDD_ROLLBACK_DRILL_FILE", "DDD_ROLLBACK_DRILL_DEFERRAL_FILE"]),
  requiredGroup("rollback-environment", ["DDD_ROLLBACK_DRILL_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"]),
  requiredGroup("rollback-release-candidate", ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"]),
  requiredGroup("rollback-operator", ["DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"]),
];

const migrationChecks = [
  requiredFlag("DDD_MIGRATION_FRESH_DB_VALIDATED"),
  requiredValue("DDD_MIGRATION_FRESH_DB_EVIDENCE", process.env.DDD_MIGRATION_FRESH_DB_EVIDENCE || process.env.DDD_MIGRATION_RUNTIME_EVIDENCE || ""),
  requiredFlag("DDD_MIGRATION_UPGRADE_DB_VALIDATED"),
  requiredValue("DDD_MIGRATION_UPGRADE_DB_EVIDENCE", process.env.DDD_MIGRATION_UPGRADE_DB_EVIDENCE || process.env.DDD_MIGRATION_RUNTIME_EVIDENCE || ""),
  requiredGroup("migration-environment", ["DDD_MIGRATION_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"]),
  requiredGroup("migration-operator", ["DDD_MIGRATION_OPERATOR", "DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"]),
  requiredValue("DDD_MIGRATION_COMPLETED_AT", process.env.DDD_MIGRATION_COMPLETED_AT || ""),
];

const explainChecks = [
  requiredValue("DDD_EXPLAIN_DATABASE", process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || ""),
  requiredValue("MYSQL_HOST", process.env.MYSQL_HOST || ""),
  requiredValue("MYSQL_PORT", process.env.MYSQL_PORT || ""),
  requiredValue("MYSQL_USER", process.env.MYSQL_USER || ""),
  requiredValue("MYSQL_PASSWORD", process.env.MYSQL_PASSWORD || ""),
  requiredGroup("explain-environment", ["DDD_EXPLAIN_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"]),
  requiredGroup("explain-release-candidate", ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"]),
  requiredGroup("explain-operator", ["DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"]),
];

function issuesFor(checks) {
  return checks.map((check) => check.issue).filter(Boolean);
}

const rollbackIssues = issuesFor(rollbackChecks);
const migrationIssues = issuesFor(migrationChecks);
const explainIssues = issuesFor(explainChecks);
const issues = [
  ...rollbackIssues.map((issue) => `rollback: ${issue}`),
  ...migrationIssues.map((issue) => `migration: ${issue}`),
  ...explainIssues.map((issue) => `explain: ${issue}`),
];

const result = {
  status: issues.length === 0 ? "PASS" : "BLOCKED",
  generatedAt: new Date().toISOString(),
  willWriteFiles: false,
  tracks: {
    rollback: {
      status: rollbackIssues.length === 0 ? "PASS" : "BLOCKED",
      checks: rollbackChecks,
      issues: rollbackIssues,
      nextCommands: [
        "DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs",
        "DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs",
      ],
    },
    migration: {
      status: migrationIssues.length === 0 ? "PASS" : "BLOCKED",
      checks: migrationChecks,
      issues: migrationIssues,
      nextCommands: [
        "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs",
        "DDD_MIGRATION_STRICT=true node scripts/ddd-migration-evidence.mjs",
      ],
    },
    explain: {
      status: explainIssues.length === 0 ? "PASS" : "BLOCKED",
      checks: explainChecks,
      issues: explainIssues,
      nextCommands: [
        "node scripts/ddd-collect-explain.mjs",
        "DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs",
      ],
    },
  },
  issues,
};

console.log(JSON.stringify(result, null, 2));
process.exit(issues.length === 0 ? 0 : 1);
