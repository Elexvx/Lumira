#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { evaluateReleaseConfig } from "./ddd-release-config-contract.mjs";
import {
  evidenceValueIssue,
  requireRuntimeProvenanceWhenStrict,
} from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_RELEASE_CONFIG_DIR
  ? path.resolve(process.env.DDD_RELEASE_CONFIG_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "config");
const outputFile = process.env.DDD_RELEASE_CONFIG_REPORT
  ? path.resolve(process.env.DDD_RELEASE_CONFIG_REPORT)
  : path.join(outputDir, "release-config-evidence.json");
const envFile = process.env.DDD_RELEASE_ENV_FILE ? path.resolve(process.env.DDD_RELEASE_ENV_FILE) : null;
const envFileExists = Boolean(envFile && fs.existsSync(envFile));
const missingEnvFile = process.env.DDD_RELEASE_MISSING_ENV_REPORT
  ? path.resolve(process.env.DDD_RELEASE_MISSING_ENV_REPORT)
  : path.join(repoRoot, "artifacts", "ddd", "release", "release-env-missing.json");
const generatedMissingTemplateFile = path.join(path.dirname(missingEnvFile), "release-env-missing.template.env");
const generatedMissingTemplate = Boolean(envFile && envFile === path.resolve(generatedMissingTemplateFile));
const inputKind = generatedMissingTemplate
  ? "generated-missing-template"
  : envFile
  ? (envFileExists ? "release-env-file" : "missing-release-env-file")
  : "process-environment-only";
const templateFile = path.join(repoRoot, "docs", "36-ddd-release-env-template.env");
const workflowFile = path.join(repoRoot, ".github", "workflows", "ddd-release-evidence.yml");
const strict = process.env.DDD_RELEASE_CONFIG_STRICT === "true" || process.env.DDD_RELEASE_EVIDENCE_STRICT === "true";

const env = { ...process.env };
const blockers = [];
const warnings = [];
const envFileKeys = new Set();

function parseEnvFile(file) {
  if (!file) {
    return;
  }
  if (!fs.existsSync(file)) {
    blockers.push(`env file does not exist: ${file}`);
    return;
  }
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const index = line.indexOf("=");
    const key = line.slice(0, index).trim().replace(/^export\s+/, "");
    let value = line.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    env[key] = value;
    envFileKeys.add(key);
  }
}

function envKeysFromTemplate(file) {
  if (!fs.existsSync(file)) {
    return new Set();
  }
  return new Set(fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => line.slice(0, line.indexOf("=")).trim().replace(/^export\s+/, "")));
}

function envKeysFromWorkflow(file) {
  if (!fs.existsSync(file)) {
    return new Set();
  }
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

parseEnvFile(envFile);

const sourceEnvironment = env.DDD_EVIDENCE_ENVIRONMENT || env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = env.DDD_RELEASE_CANDIDATE || env.GITHUB_SHA || "";
const evidenceOperator = env.DDD_EVIDENCE_OPERATOR || env.GITHUB_ACTOR || "";

const result = evaluateReleaseConfig(env, { envFile });
const groups = result.groups;
const templateKeys = envKeysFromTemplate(templateFile);
const workflowKeys = envKeysFromWorkflow(workflowFile);
blockers.push(...result.blockers);
warnings.push(...result.warnings);
for (const issue of requireRuntimeProvenanceWhenStrict({
  strict,
  sourceEnvironment,
  releaseCandidate,
  evidenceOperator,
})) {
  blockers.push(`provenance.${issue}`);
}

function buildBlockerDetails(allBlockers, checkedGroups) {
  return allBlockers.map((blocker) => detailForBlocker(blocker, checkedGroups));
}

function detailForBlocker(blocker, checkedGroups) {
  const text = String(blocker);
  const match = text.match(/^([^.]+)\.([^:]+):\s*(.*)$/);
  if (match) {
    const [, groupName, checkName, reason] = match;
    const group = checkedGroups.find((candidate) => candidate.name === groupName);
    const check = group?.checks?.find((candidate) => candidate.name === checkName);
    return {
      blocker: text,
      group: groupName,
      owner: group?.owner || "release-infra",
      check: checkName,
      reason,
      envKeys: check?.keys || [],
      matchedKey: check?.matchedKey || null,
      blockedByPlaceholderKey: Boolean(check?.matchedKey && evidenceValueIssue(env[check.matchedKey]) === "must not contain placeholder text"),
      required: check?.required ?? null,
    };
  }
  if (text.startsWith("provenance.")) {
    return {
      blocker: text,
      group: "provenance",
      owner: "release-infra",
      check: "runtime provenance",
      reason: text.slice("provenance.".length),
      envKeys: ["DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_CANDIDATE", "DDD_EVIDENCE_OPERATOR"],
      matchedKey: null,
      blockedByPlaceholderKey: false,
      required: true,
    };
  }
  if (text.includes("unsafe default admin login")) {
    return {
      blocker: text,
      group: "runtime",
      owner: "release-infra",
      check: "unsafe default admin login",
      reason: "must be disabled for release evidence",
      envKeys: ["ALLOW_UNSAFE_DEFAULT_ADMIN_LOGIN"],
      matchedKey: "ALLOW_UNSAFE_DEFAULT_ADMIN_LOGIN",
      blockedByPlaceholderKey: false,
      required: true,
    };
  }
  if (text.includes("redis clear-on-startup")) {
    return {
      blocker: text,
      group: "runtime",
      owner: "release-infra",
      check: "redis clear-on-startup",
      reason: "must be false for production-equivalent release evidence",
      envKeys: ["ELEXVX_REDIS_CLEAR_ON_STARTUP"],
      matchedKey: "ELEXVX_REDIS_CLEAR_ON_STARTUP",
      blockedByPlaceholderKey: false,
      required: true,
    };
  }
  if (text.includes("env file")) {
    return {
      blocker: text,
      group: "release-env-file",
      owner: "release-infra",
      check: "release env file",
      reason: text,
      envKeys: ["DDD_RELEASE_ENV_FILE"],
      matchedKey: null,
      blockedByPlaceholderKey: false,
      required: true,
    };
  }
  return {
    blocker: text,
    group: "other",
    owner: "release-infra",
    check: "unknown",
    reason: text,
    envKeys: [],
    matchedKey: null,
    blockedByPlaceholderKey: false,
    required: true,
  };
}

function countBy(items, key) {
  const counts = {};
  for (const item of items) {
    const value = item[key] || "unknown";
    counts[value] = (counts[value] || 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort(([left], [right]) => left.localeCompare(right)));
}

const blockerDetails = buildBlockerDetails(blockers, groups);
const placeholderDerivedConfigBlockers = blockerDetails
  .filter((detail) => detail.blockedByPlaceholderKey === true)
  .map((detail) => detail.blocker);
const placeholderDerivedConfigBlockerSet = new Set(placeholderDerivedConfigBlockers);
const primaryBlockers = blockers.filter((blocker) => !placeholderDerivedConfigBlockerSet.has(blocker));
const coverageMatrix = groups.flatMap((group) => group.checks.map((check) => {
  const keys = check.keys || [];
  return {
    group: group.name,
    owner: group.owner,
    check: check.name,
    required: check.required !== false,
    keys,
    runtimePresent: check.present === true,
    matchedKey: check.matchedKey || null,
    envFileCovered: keys.some((key) => envFileKeys.has(key)),
    templateCovered: keys.some((key) => templateKeys.has(key)),
    workflowCovered: keys.some((key) => workflowKeys.has(key)),
  };
}));
const requiredCoverage = coverageMatrix.filter((entry) => entry.required);

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  strict,
  envFile,
  envFileExists,
  inputKind,
  generatedMissingTemplate,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  summary: {
    groups: groups.length,
    checks: groups.reduce((sum, group) => sum + group.checks.length, 0),
    requiredChecks: requiredCoverage.length,
    runtimePresentRequiredChecks: requiredCoverage.filter((entry) => entry.runtimePresent).length,
    envFileCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.envFileCovered).length,
    templateCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.templateCovered).length,
    workflowCoveredRequiredChecks: requiredCoverage.filter((entry) => entry.workflowCovered).length,
    blockers: blockers.length,
    primaryBlockers: primaryBlockers.length,
    releaseConfigBlockersFromPlaceholders: placeholderDerivedConfigBlockers.length,
    releaseConfigBlockersAfterPlaceholders: Math.max(0, blockers.length - placeholderDerivedConfigBlockers.length),
    warnings: warnings.length,
  },
  groups,
  coverageMatrix,
  blockers,
  primaryBlockers,
  placeholderDerivedConfigBlockers,
  blockerDetails,
  blockersByGroup: countBy(blockerDetails, "group"),
  blockersByOwner: countBy(blockerDetails, "owner"),
  warnings,
};

fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0 && strict) {
  for (const blocker of primaryBlockers) {
    console.error(`[ddd-release-config-evidence] ${blocker}`);
  }
  if (placeholderDerivedConfigBlockers.length > 0) {
    console.error(`[ddd-release-config-evidence] ${placeholderDerivedConfigBlockers.length} release config blocker(s) are derived from unresolved placeholder values; replace placeholders first. Full derived list is in ${outputFile}`);
    for (const blocker of placeholderDerivedConfigBlockers.slice(0, 5)) {
      console.error(`[ddd-release-config-evidence] derived: ${blocker}`);
    }
    if (placeholderDerivedConfigBlockers.length > 5) {
      console.error(`[ddd-release-config-evidence] derived: ... ${placeholderDerivedConfigBlockers.length - 5} more`);
    }
  }
  console.error(`[ddd-release-config-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-config-evidence] config evidence ${artifact.status}; blockers=${blockers.length}; warnings=${warnings.length}; artifact=${outputFile}`);
