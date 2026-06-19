#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { releaseConfigGroups } from "./ddd-release-config-contract.mjs";
import { evidenceValueIssue, isHttpsUrl, isLocalUrlLike } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const sourceFile = process.argv[2]
  || process.env.DDD_RELEASE_CANONICAL_ENV_FILE
  || path.join(artifactRoot, "release", "release-env-canonical-fill.template.env");
const targetFile = process.argv[3] || process.env.DDD_RELEASE_ENV_FILE || "";
const outputFile = process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_REPORT)
  : path.join(artifactRoot, "release", "release-env-canonical-merge.json");
const dryRun = process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_DRY_RUN === "1"
  || process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_DRY_RUN === "true";
const force = process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_FORCE === "1"
  || process.env.DDD_RELEASE_ENV_CANONICAL_MERGE_FORCE === "true";

function portablePath(value) {
  if (!value) {
    return value;
  }
  const absolute = path.resolve(value);
  if (absolute === repoRoot) {
    return ".";
  }
  if (absolute.startsWith(`${repoRoot}${path.sep}`)) {
    return path.relative(repoRoot, absolute) || ".";
  }
  const homeDir = process.env.HOME ? path.resolve(process.env.HOME) : "";
  if (homeDir && absolute === homeDir) {
    return "~";
  }
  if (homeDir && absolute.startsWith(`${homeDir}${path.sep}`)) {
    return `~/${path.relative(homeDir, absolute)}`;
  }
  return value;
}

function unquote(value) {
  if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  return value;
}

function quoteEnvValue(value) {
  const text = String(value);
  if (/^[A-Za-z0-9_./:@,+%=-]+$/.test(text)) {
    return text;
  }
  return JSON.stringify(text);
}

function isPlaceholder(value) {
  const text = String(value || "").trim();
  return text === "__REQUIRED__" || evidenceValueIssue(text) === "must not contain placeholder text";
}

function parseEnvFile(file) {
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  const entries = new Map();
  const duplicateKeys = [];
  const invalidLines = [];
  for (const [index, rawLine] of lines.entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const match = rawLine.match(/^(\s*)(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)(\s*)$/);
    if (!match) {
      invalidLines.push({ line: index + 1, text: rawLine });
      continue;
    }
    const key = match[2];
    const value = unquote(match[3].trim());
    if (entries.has(key)) {
      duplicateKeys.push(key);
    }
    entries.set(key, {
      index,
      key,
      value,
      exportPrefix: rawLine.trimStart().startsWith("export "),
    });
  }
  return { lines, entries, duplicateKeys: [...new Set(duplicateKeys)].sort(), invalidLines };
}

function fileSecurity(file) {
  const mode = fs.statSync(file).mode & 0o777;
  const permissionCheckSkipped = process.platform === "win32";
  return {
    checked: true,
    mode,
    modeOctal: mode.toString(8).padStart(3, "0"),
    permissionSafe: permissionCheckSkipped || (mode & 0o077) === 0,
    permissionCheckSkipped,
    requiredMode: "600",
  };
}

function canonicalRequirements() {
  const byKey = new Map();
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements || []) {
      const canonicalKey = requirement.keys?.[0];
      if (!canonicalKey || byKey.has(canonicalKey)) {
        continue;
      }
      byKey.set(canonicalKey, {
        ...requirement,
        canonicalKey,
        group: group.name,
        owner: group.owner,
        required: requirement.required !== false,
      });
    }
  }
  return byKey;
}

function validateValue(requirement, value) {
  const issues = [];
  const valueIssue = evidenceValueIssue(value);
  if (valueIssue) {
    issues.push(valueIssue);
    return issues;
  }
  if (requirement.https === true && !isHttpsUrl(value)) {
    issues.push("must be an HTTPS URL");
  }
  if (requirement.nonLocal === true && isLocalUrlLike(value)) {
    issues.push("must not point to localhost or loopback");
  }
  if (Number.isFinite(requirement.minLength) && String(value).length < requirement.minLength) {
    issues.push(`must be at least ${requirement.minLength} characters`);
  }
  if (Array.isArray(requirement.expectedValues) && requirement.expectedValues.length > 0 && !requirement.expectedValues.includes(value)) {
    issues.push(`must be one of: ${requirement.expectedValues.join(", ")}`);
  }
  if (Array.isArray(requirement.disallowValues) && requirement.disallowValues.includes(value)) {
    issues.push(`must not be one of: ${requirement.disallowValues.join(", ")}`);
  }
  if (requirement.pattern) {
    const regexp = new RegExp(requirement.pattern);
    if (!regexp.test(value)) {
      issues.push(`must match ${requirement.patternDescription || requirement.pattern}`);
    }
  }
  return issues;
}

function writeReport(report) {
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);
}

function fail(blockers, extra = {}) {
  const report = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    sourceFile: portablePath(sourceFile),
    targetFile: portablePath(targetFile),
    dryRun,
    force,
    blockers,
    ...extra,
  };
  writeReport(report);
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-canonical-merge] ${blocker}`);
  }
  console.error(`[ddd-release-env-canonical-merge] FAIL report=${outputFile}`);
  process.exit(1);
}

if (!targetFile) {
  fail(["DDD_RELEASE_ENV_FILE or second CLI argument is required"]);
}
if (!fs.existsSync(sourceFile)) {
  fail([`canonical source file does not exist: ${portablePath(sourceFile)}`]);
}
if (!fs.existsSync(targetFile)) {
  fail([`target release env file does not exist: ${portablePath(targetFile)}`]);
}

const sourceSecurity = fileSecurity(sourceFile);
const targetSecurity = fileSecurity(targetFile);
if (!targetSecurity.permissionSafe) {
  fail([`target env file permissions are too broad: ${portablePath(targetFile)} mode=${targetSecurity.modeOctal}; use chmod 600`], {
    sourceSecurity,
    targetSecurity,
  });
}

const source = parseEnvFile(sourceFile);
const target = parseEnvFile(targetFile);
const requirements = canonicalRequirements();
const sourceEntries = [...source.entries.values()];
const concreteSourceEntries = sourceEntries.filter((entry) => !isPlaceholder(entry.value) && String(entry.value).trim() !== "");
const unresolvedSourceKeys = sourceEntries
  .filter((entry) => isPlaceholder(entry.value) || String(entry.value).trim() === "")
  .map((entry) => entry.key)
  .sort();
const conflicts = [];
const validationIssues = [];
const updates = [];
const additions = [];

for (const sourceEntry of concreteSourceEntries) {
  const requirement = requirements.get(sourceEntry.key);
  if (!requirement) {
    validationIssues.push({ key: sourceEntry.key, issue: "is not a canonical release config key" });
    continue;
  }
  for (const issue of validateValue(requirement, sourceEntry.value)) {
    validationIssues.push({ key: sourceEntry.key, issue });
  }
}
const sensitiveConcreteSourceKeys = concreteSourceEntries
  .map((entry) => entry.key)
  .filter((key) => /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i.test(key))
  .sort();

for (const sourceEntry of concreteSourceEntries) {
  const targetEntry = target.entries.get(sourceEntry.key);
  if (!targetEntry) {
    target.lines.push(`${sourceEntry.key}=${quoteEnvValue(sourceEntry.value)}`);
    additions.push({ key: sourceEntry.key });
    continue;
  }
  const targetConcrete = !isPlaceholder(targetEntry.value) && String(targetEntry.value).trim() !== "";
  if (targetConcrete && targetEntry.value !== sourceEntry.value && !force) {
    conflicts.push({ key: sourceEntry.key });
    continue;
  }
  const replacement = `${targetEntry.exportPrefix ? "export " : ""}${sourceEntry.key}=${quoteEnvValue(sourceEntry.value)}`;
  if (target.lines[targetEntry.index] !== replacement) {
    target.lines[targetEntry.index] = replacement;
    updates.push({ key: sourceEntry.key });
  }
}

const blockers = [
  ...source.duplicateKeys.map((key) => `duplicate canonical source key: ${key}`),
  ...target.duplicateKeys.map((key) => `duplicate target env key: ${key}`),
  ...source.invalidLines.map((line) => `invalid canonical source line ${line.line}`),
  ...target.invalidLines.map((line) => `invalid target env line ${line.line}`),
  ...validationIssues.map((entry) => `${entry.key}: ${entry.issue}`),
  ...(sensitiveConcreteSourceKeys.length > 0 && !sourceSecurity.permissionSafe
    ? [`canonical source file has concrete secret values and permissions are too broad: ${portablePath(sourceFile)} mode=${sourceSecurity.modeOctal}; keys=${sensitiveConcreteSourceKeys.join(", ")}; use chmod 600`]
    : []),
  ...conflicts.map((conflict) => `${conflict.key}: target already has a different concrete value; set DDD_RELEASE_ENV_CANONICAL_MERGE_FORCE=1 to overwrite`),
];
const status = blockers.length > 0 ? "FAIL" : "PASS";
if (status === "PASS" && (updates.length + additions.length) > 0 && !dryRun) {
  fs.writeFileSync(targetFile, `${target.lines.join("\n").replace(/\n+$/u, "")}\n`);
  fs.chmodSync(targetFile, 0o600);
}

const report = {
  generatedAt: new Date().toISOString(),
  status,
  sourceFile: portablePath(sourceFile),
  targetFile: portablePath(targetFile),
  dryRun,
  force,
  sourceSecurity,
  targetSecurity,
  summary: {
    sourceKeys: sourceEntries.length,
    concreteSourceKeys: concreteSourceEntries.length,
    sensitiveConcreteSourceKeys: sensitiveConcreteSourceKeys.length,
    unresolvedSourceKeys: unresolvedSourceKeys.length,
    updates: updates.length,
    additions: additions.length,
    conflicts: conflicts.length,
    validationIssues: validationIssues.length,
    duplicateSourceKeys: source.duplicateKeys.length,
    duplicateTargetKeys: target.duplicateKeys.length,
  },
  updates,
  additions,
  conflicts,
  validationIssues,
  sensitiveConcreteSourceKeys,
  unresolvedSourceKeys,
  blockers,
};
writeReport(report);

if (status !== "PASS") {
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-canonical-merge] ${blocker}`);
  }
  console.error(`[ddd-release-env-canonical-merge] FAIL report=${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-env-canonical-merge] PASS updates=${updates.length} additions=${additions.length} unresolvedSourceKeys=${unresolvedSourceKeys.length} dryRun=${dryRun} report=${outputFile}`);
