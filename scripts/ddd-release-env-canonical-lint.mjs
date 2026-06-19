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
const outputFile = process.env.DDD_RELEASE_ENV_CANONICAL_LINT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_CANONICAL_LINT_REPORT)
  : path.join(artifactRoot, "release", "release-env-canonical-lint.json");

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
    entries.set(key, { index, key, value });
  }
  return { entries, duplicateKeys: [...new Set(duplicateKeys)].sort(), invalidLines };
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
        aliases: requirement.keys || [canonicalKey],
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
    blockers,
    warnings: [],
    ...extra,
  };
  writeReport(report);
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-canonical-lint] ${blocker}`);
  }
  console.error(`[ddd-release-env-canonical-lint] FAIL report=${outputFile}`);
  process.exit(1);
}

if (!fs.existsSync(sourceFile)) {
  fail([`canonical source file does not exist: ${portablePath(sourceFile)}`]);
}

const sourceSecurity = fileSecurity(sourceFile);
const source = parseEnvFile(sourceFile);
const requirements = canonicalRequirements();
const blockers = [
  ...source.duplicateKeys.map((key) => `duplicate canonical source key: ${key}`),
  ...source.invalidLines.map((line) => `invalid canonical source line ${line.line}`),
];
const warnings = [];
const unresolvedSourceKeys = [];
const missingCanonicalKeys = [];
const unknownSourceKeys = [];
const validated = [];

for (const key of source.entries.keys()) {
  if (!requirements.has(key)) {
    unknownSourceKeys.push(key);
  }
}

for (const [canonicalKey, requirement] of requirements.entries()) {
  const entry = source.entries.get(canonicalKey);
  if (!entry) {
    missingCanonicalKeys.push(canonicalKey);
    continue;
  }
  const value = String(entry.value || "").trim();
  const concrete = value !== "" && !isPlaceholder(value);
  if (!concrete) {
    if (requirement.required) {
      unresolvedSourceKeys.push(canonicalKey);
    }
    continue;
  }
  const issues = validateValue(requirement, value);
  if (issues.length > 0) {
    for (const issue of issues) {
      blockers.push(`${canonicalKey}: ${issue}`);
    }
  } else {
    validated.push({
      key: canonicalKey,
      group: requirement.group,
      owner: requirement.owner,
      requirement: requirement.name,
    });
  }
}

if (missingCanonicalKeys.length > 0) {
  warnings.push(`canonical source omits ${missingCanonicalKeys.length} contract keys that may be covered by existing env/defaults: ${missingCanonicalKeys.join(", ")}`);
}
for (const key of unresolvedSourceKeys) {
  blockers.push(`${key}: required canonical key is unresolved`);
}
if (unknownSourceKeys.length > 0) {
  warnings.push(`canonical source contains unknown keys: ${unknownSourceKeys.join(", ")}`);
}

const concreteSourceKeys = [...source.entries.values()]
  .filter((entry) => {
    const value = String(entry.value || "").trim();
    return value !== "" && !isPlaceholder(value);
  })
  .map((entry) => entry.key)
  .sort();
const sensitiveConcreteSourceKeys = concreteSourceKeys.filter((key) => /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i.test(key));
if (sensitiveConcreteSourceKeys.length > 0 && !sourceSecurity.permissionSafe) {
  blockers.push(`canonical source file has concrete secret values and permissions are too broad: ${portablePath(sourceFile)} mode=${sourceSecurity.modeOctal}; keys=${sensitiveConcreteSourceKeys.join(", ")}; use chmod 600`);
} else if (!sourceSecurity.permissionSafe) {
  warnings.push(`canonical source file permissions are broad for a template: mode=${sourceSecurity.modeOctal}; use chmod 600 before adding secret values`);
}

const status = blockers.length > 0 ? "FAIL" : "PASS";
const report = {
  generatedAt: new Date().toISOString(),
  status,
  sourceFile: portablePath(sourceFile),
  sourceSecurity,
  summary: {
    canonicalRequirements: requirements.size,
    sourceKeys: source.entries.size,
    concreteSourceKeys: concreteSourceKeys.length,
    sensitiveConcreteSourceKeys: sensitiveConcreteSourceKeys.length,
    unresolvedSourceKeys: unresolvedSourceKeys.length,
    missingCanonicalKeys: missingCanonicalKeys.length,
    unknownSourceKeys: unknownSourceKeys.length,
    validatedKeys: validated.length,
    duplicateSourceKeys: source.duplicateKeys.length,
    invalidLines: source.invalidLines.length,
    blockers: blockers.length,
    warnings: warnings.length,
  },
  validated,
  unresolvedSourceKeys: unresolvedSourceKeys.sort(),
  missingCanonicalKeys: missingCanonicalKeys.sort(),
  unknownSourceKeys: unknownSourceKeys.sort(),
  concreteSourceKeys,
  sensitiveConcreteSourceKeys,
  blockers,
  warnings,
};
writeReport(report);

if (status !== "PASS") {
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-canonical-lint] ${blocker}`);
  }
  console.error(`[ddd-release-env-canonical-lint] FAIL report=${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-env-canonical-lint] PASS concreteSourceKeys=${concreteSourceKeys.length} validatedKeys=${validated.length} report=${outputFile}`);
