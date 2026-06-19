#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { releaseConfigGroups } from "./ddd-release-config-contract.mjs";
import { evidenceValueIssue, isHttpsUrl, isLocalUrlLike } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const sourceDir = process.argv[2]
  || process.env.DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR
  || path.join(artifactRoot, "release", "release-env-owner-templates");
const targetFile = process.argv[3]
  || process.env.DDD_RELEASE_CANONICAL_ENV_FILE
  || path.join(artifactRoot, "release", "release-env-canonical-fill.template.env");
const outputFile = process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_REPORT)
  : path.join(artifactRoot, "release", "release-env-owner-templates-merge.json");
const dryRun = process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_DRY_RUN === "1"
  || process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_DRY_RUN === "true";
const force = process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_FORCE === "1"
  || process.env.DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_FORCE === "true";

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

function isSensitiveKey(key) {
  return /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i.test(key);
}

function writeReport(report) {
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);
}

function fail(blockers, extra = {}) {
  const report = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    sourceDir: portablePath(sourceDir),
    targetFile: portablePath(targetFile || ""),
    dryRun,
    force,
    blockers,
    ...extra,
  };
  writeReport(report);
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-owner-templates-merge] ${blocker}`);
  }
  console.error(`[ddd-release-env-owner-templates-merge] FAIL report=${outputFile}`);
  process.exit(1);
}

if (!fs.existsSync(sourceDir) || !fs.statSync(sourceDir).isDirectory()) {
  fail([`owner template dir does not exist: ${portablePath(sourceDir)}`]);
}
if (!targetFile || !fs.existsSync(targetFile)) {
  fail([`canonical target file does not exist: ${portablePath(targetFile)}`]);
}

const requirements = canonicalRequirements();
const target = parseEnvFile(targetFile);
const targetSecurity = fileSecurity(targetFile);
const sourceFiles = fs.readdirSync(sourceDir)
  .filter((name) => name.endsWith(".env"))
  .sort()
  .map((name) => path.join(sourceDir, name));
const blockers = [
  ...target.duplicateKeys.map((key) => `duplicate canonical target key: ${key}`),
  ...target.invalidLines.map((line) => `invalid canonical target line ${line.line}`),
];
const ownerFiles = [];
const sourceValues = new Map();

for (const file of sourceFiles) {
  const parsed = parseEnvFile(file);
  const security = fileSecurity(file);
  const concreteEntries = [...parsed.entries.values()]
    .filter((entry) => !isPlaceholder(entry.value) && String(entry.value).trim() !== "");
  const sensitiveConcreteKeys = concreteEntries.map((entry) => entry.key).filter(isSensitiveKey).sort();
  ownerFiles.push({
    file: portablePath(file),
    modeOctal: security.modeOctal,
    permissionSafe: security.permissionSafe,
    permissionCheckSkipped: security.permissionCheckSkipped,
    sourceKeys: parsed.entries.size,
    concreteSourceKeys: concreteEntries.length,
    sensitiveConcreteKeys,
    duplicateKeys: parsed.duplicateKeys,
    invalidLines: parsed.invalidLines.map((line) => line.line),
  });
  for (const key of parsed.duplicateKeys) {
    blockers.push(`${path.basename(file)}: duplicate owner template key: ${key}`);
  }
  for (const line of parsed.invalidLines) {
    blockers.push(`${path.basename(file)}: invalid owner template line ${line.line}`);
  }
  if (sensitiveConcreteKeys.length > 0 && !security.permissionSafe) {
    blockers.push(`${path.basename(file)}: concrete secret values require chmod 600; keys=${sensitiveConcreteKeys.join(", ")} mode=${security.modeOctal}`);
  }
  for (const entry of concreteEntries) {
    const requirement = requirements.get(entry.key);
    if (!requirement) {
      blockers.push(`${path.basename(file)}:${entry.key}: is not a canonical release config key`);
      continue;
    }
    for (const issue of validateValue(requirement, entry.value)) {
      blockers.push(`${path.basename(file)}:${entry.key}: ${issue}`);
    }
    if (!sourceValues.has(entry.key)) {
      sourceValues.set(entry.key, []);
    }
    sourceValues.get(entry.key).push({ file, key: entry.key, value: entry.value });
  }
}

const conflicts = [];
for (const [key, values] of sourceValues.entries()) {
  const distinctValues = [...new Set(values.map((entry) => entry.value))];
  if (distinctValues.length > 1) {
    conflicts.push({ key, files: values.map((entry) => path.basename(entry.file)).sort() });
  }
}
for (const conflict of conflicts) {
  blockers.push(`${conflict.key}: conflicting owner template values in ${conflict.files.join(", ")}`);
}

const updates = [];
const additions = [];
if (blockers.length === 0) {
  for (const [key, values] of sourceValues.entries()) {
    const value = values[0].value;
    const targetEntry = target.entries.get(key);
    if (!targetEntry) {
      target.lines.push(`${key}=${quoteEnvValue(value)}`);
      additions.push({ key, sourceFile: path.basename(values[0].file) });
      continue;
    }
    const targetConcrete = !isPlaceholder(targetEntry.value) && String(targetEntry.value).trim() !== "";
    if (targetConcrete && targetEntry.value !== value && !force) {
      blockers.push(`${key}: canonical target already has a different concrete value; set DDD_RELEASE_ENV_OWNER_TEMPLATES_MERGE_FORCE=1 to overwrite`);
      continue;
    }
    const replacement = `${targetEntry.exportPrefix ? "export " : ""}${key}=${quoteEnvValue(value)}`;
    if (target.lines[targetEntry.index] !== replacement) {
      target.lines[targetEntry.index] = replacement;
      updates.push({ key, sourceFile: path.basename(values[0].file) });
    }
  }
}

const status = blockers.length > 0 ? "FAIL" : "PASS";
if (status === "PASS" && (updates.length + additions.length) > 0 && !dryRun) {
  fs.writeFileSync(targetFile, `${target.lines.join("\n").replace(/\n+$/u, "")}\n`);
}

const report = {
  generatedAt: new Date().toISOString(),
  status,
  sourceDir: portablePath(sourceDir),
  targetFile: portablePath(targetFile),
  dryRun,
  force,
  targetSecurity,
  summary: {
    ownerTemplateFiles: sourceFiles.length,
    concreteSourceKeys: sourceValues.size,
    updates: updates.length,
    additions: additions.length,
    conflicts: conflicts.length,
    blockers: blockers.length,
  },
  ownerFiles,
  updates,
  additions,
  conflicts,
  blockers,
};
writeReport(report);

if (status !== "PASS") {
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-owner-templates-merge] ${blocker}`);
  }
  console.error(`[ddd-release-env-owner-templates-merge] FAIL report=${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-env-owner-templates-merge] PASS updates=${updates.length} additions=${additions.length} concreteSourceKeys=${sourceValues.size} dryRun=${dryRun} report=${outputFile}`);
