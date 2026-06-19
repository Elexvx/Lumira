#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { evaluateReleaseConfig, releaseConfigGroups } from "./ddd-release-config-contract.mjs";
import { evidenceValueIssue } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const envFile = process.argv[2] || process.env.DDD_RELEASE_ENV_FILE || "";
const missingEnvFile = process.env.DDD_RELEASE_MISSING_ENV_REPORT
  ? path.resolve(process.env.DDD_RELEASE_MISSING_ENV_REPORT)
  : path.join(artifactRoot, "release", "release-env-missing.json");
const outputFile = process.env.DDD_RELEASE_ENV_LINT_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_LINT_REPORT)
  : path.join(artifactRoot, "release", "release-env-lint.json");
const generatedMissingTemplateFile = path.join(path.dirname(missingEnvFile), "release-env-missing.template.env");
const sourceEnvironment = process.env.DDD_EVIDENCE_ENVIRONMENT
  || process.env.DDD_RELEASE_ENVIRONMENT
  || "local-dev";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE
  || process.env.GITHUB_SHA
  || inferGitReleaseCandidate()
  || "local-worktree";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR
  || process.env.GITHUB_ACTOR
  || inferEvidenceOperator()
  || "local-operator";
const canonicalEnvKeyByAlias = buildCanonicalEnvKeyByAlias();

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

function inferGitReleaseCandidate() {
  try {
    return execFileSync("git", ["rev-parse", "--short=12", "HEAD"], {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return "";
  }
}

function inferEvidenceOperator() {
  try {
    return os.userInfo().username || "";
  } catch {
    return "";
  }
}

function buildCanonicalEnvKeyByAlias() {
  const canonicalEnvKeyByAlias = new Map();
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements) {
      const keys = Array.isArray(requirement.keys) ? requirement.keys : [requirement.key];
      const canonicalKey = keys.find(Boolean);
      for (const key of keys.filter(Boolean)) {
        if (!canonicalEnvKeyByAlias.has(key)) {
          canonicalEnvKeyByAlias.set(key, canonicalKey || key);
        }
      }
    }
  }
  return canonicalEnvKeyByAlias;
}

function canonicalEnvKey(key) {
  return canonicalEnvKeyByAlias.get(key) || key;
}

function canonicalEnvKeys(keys) {
  return [...new Set((keys || []).filter(Boolean).map(canonicalEnvKey))].sort();
}

function parseEnvFile(file) {
  const env = {};
  const duplicateKeys = [];
  const seen = new Set();
  const issues = [];
  const fileSecurity = {
    checked: false,
    reason: null,
    mode: null,
    modeOctal: null,
    permissionSafe: null,
    permissionCheckSkipped: false,
    generatedMissingTemplate: false,
    requiredMode: "600",
  };
  if (!file) {
    fileSecurity.reason = "process-environment-only";
    const processKeys = Object.keys(process.env)
      .filter((key) => /^[A-Z][A-Z0-9_]*$/.test(key))
      .sort();
    for (const key of processKeys) {
      env[key] = process.env[key] ?? "";
    }
    return { env, keys: processKeys, duplicateKeys, issues, fileSecurity };
  }
  if (!fs.existsSync(file)) {
    fileSecurity.reason = "missing-env-file";
    issues.push(`env file does not exist: ${portablePath(file)}`);
    return { env, keys: [], duplicateKeys, issues, fileSecurity };
  }
  const isGeneratedMissingTemplate = path.resolve(file) === path.resolve(generatedMissingTemplateFile);
  const mode = fs.statSync(file).mode & 0o777;
  fileSecurity.checked = true;
  fileSecurity.reason = "env-file";
  fileSecurity.mode = mode;
  fileSecurity.modeOctal = mode.toString(8).padStart(3, "0");
  fileSecurity.generatedMissingTemplate = isGeneratedMissingTemplate;
  fileSecurity.permissionCheckSkipped = isGeneratedMissingTemplate || process.platform === "win32";
  fileSecurity.permissionSafe = isGeneratedMissingTemplate
    ? null
    : (fileSecurity.permissionCheckSkipped || (mode & 0o077) === 0);
  if (!fileSecurity.permissionCheckSkipped && (mode & 0o077) !== 0) {
    issues.push(`env file permissions are too broad: ${portablePath(file)} mode=${mode.toString(8).padStart(3, "0")}; use chmod 600`);
  }
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const [index, rawLine] of lines.entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const match = line.match(/^(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$/);
    if (!match) {
      issues.push(`line ${index + 1} must be KEY=value, export KEY=value, or a comment`);
      continue;
    }
    const key = match[1];
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (seen.has(key)) {
      duplicateKeys.push(key);
    }
    seen.add(key);
    env[key] = value;
  }
  return { env, keys: [...seen].sort(), duplicateKeys: [...new Set(duplicateKeys)].sort(), issues, fileSecurity };
}

function readMissingEnvContext(file) {
  if (!fs.existsSync(file)) {
    return {
      file: portablePath(file),
      missing: true,
      uniqueEnvKeyCount: 0,
      templateEnvKeyCount: 0,
      groupCount: 0,
      uniqueEnvKeys: [],
      templateEnvKeys: [],
      templateAliasMappings: [],
    };
  }
  try {
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    return {
      file: portablePath(file),
      missing: false,
      status: data.status || null,
      uniqueEnvKeyCount: data.uniqueEnvKeyCount || 0,
      templateEnvKeyCount: data.templateEnvKeyCount || 0,
      groupCount: data.groupCount || 0,
      uniqueEnvKeys: Array.isArray(data.uniqueEnvKeys) ? data.uniqueEnvKeys : [],
      templateEnvKeys: Array.isArray(data.templateEnvKeys) ? data.templateEnvKeys : canonicalEnvKeys(data.uniqueEnvKeys),
      templateAliasMappings: Array.isArray(data.templateAliasMappings) ? data.templateAliasMappings : [],
    };
  } catch (error) {
    return {
      file: portablePath(file),
      missing: false,
      invalid: error.message,
      uniqueEnvKeyCount: 0,
      templateEnvKeyCount: 0,
      groupCount: 0,
      uniqueEnvKeys: [],
      templateEnvKeys: [],
      templateAliasMappings: [],
    };
  }
}

function unresolvedTemplateKeys(env) {
  return Object.entries(env)
    .filter(([, value]) => {
      const text = String(value).trim();
      return text === "__REQUIRED__" || evidenceValueIssue(text) === "must not contain placeholder text";
    })
    .map(([key]) => key)
    .sort();
}

function placeholderBlocker(key, value) {
  if (String(value).trim() === "__REQUIRED__") {
    return `${key}: __REQUIRED__ placeholder must be replaced`;
  }
  return `${key}: placeholder value must be replaced`;
}

function canonicalPlaceholderBlockers(env, unresolvedKeys) {
  const keysByCanonical = new Map();
  for (const key of unresolvedKeys) {
    const canonical = canonicalEnvKey(key);
    if (!keysByCanonical.has(canonical)) {
      keysByCanonical.set(canonical, []);
    }
    keysByCanonical.get(canonical).push(key);
  }
  return [...keysByCanonical.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([canonical, keys]) => {
      const canonicalValue = env[canonical];
      if (canonicalValue !== undefined && String(canonicalValue).trim() === "__REQUIRED__") {
        return `${canonical}: __REQUIRED__ placeholder must be replaced`;
      }
      if (keys.some((key) => String(env[key]).trim() === "__REQUIRED__")) {
        return `${canonical}: __REQUIRED__ placeholder must be replaced in ${keys.length} alias value(s)`;
      }
      return `${canonical}: placeholder value must be replaced in ${keys.length} alias value(s)`;
    });
}

function releaseConfigBlockerDetails(config, unresolvedKeySet = new Set()) {
  const details = [];
  for (const blocker of config.blockers || []) {
    const text = String(blocker);
    const match = text.match(/^([^.]+)\.([^:]+):\s*(.*)$/);
    if (!match) {
      continue;
    }
    const [, groupName, checkName, reason] = match;
    const group = (config.groups || []).find((candidate) => candidate.name === groupName);
    const check = group?.checks?.find((candidate) => candidate.name === checkName);
    if (!check) {
      continue;
    }
    details.push({
      blocker: text,
      group: groupName,
      owner: group.owner || "release-infra",
      check: checkName,
      reason,
      envKeys: check.keys || [],
      canonicalEnvKeys: canonicalEnvKeys(check.keys || []),
      matchedKey: check.matchedKey || null,
      canonicalMatchedKey: check.matchedKey ? canonicalEnvKey(check.matchedKey) : null,
      blockedByPlaceholderKey: Boolean(check.matchedKey && unresolvedKeySet.has(check.matchedKey)),
      required: check.required ?? null,
    });
  }
  return details;
}

const parsed = parseEnvFile(envFile);
const missingEnv = readMissingEnvContext(missingEnvFile);
const config = evaluateReleaseConfig(parsed.env, { envFile: envFile || null });
const unresolvedKeys = unresolvedTemplateKeys(parsed.env);
const unresolvedKeySet = new Set(unresolvedKeys);
const configBlockerDetails = releaseConfigBlockerDetails(config, unresolvedKeySet);
const canonicalKeys = canonicalEnvKeys(parsed.keys);
const canonicalUnresolvedTemplateKeys = canonicalEnvKeys(unresolvedKeys);
const unresolvedTemplateBlockers = unresolvedKeys.map((key) => placeholderBlocker(key, parsed.env[key]));
const canonicalUnresolvedTemplateBlockers = canonicalPlaceholderBlockers(parsed.env, unresolvedKeys);
const canonicalMissingEnvKeys = canonicalEnvKeys(
  missingEnv.templateEnvKeys?.length > 0 ? missingEnv.templateEnvKeys : missingEnv.uniqueEnvKeys,
);
const canonicalReleaseConfigBlockerKeys = canonicalEnvKeys(configBlockerDetails.flatMap((detail) => detail.envKeys));
const releaseConfigBlockersFromPlaceholders = configBlockerDetails
  .filter((detail) => detail.blockedByPlaceholderKey === true)
  .length;
const releaseConfigBlockersAfterPlaceholders = Math.max(0, config.blockers.length - releaseConfigBlockersFromPlaceholders);
const resolvedEnvFile = envFile ? path.resolve(envFile) : null;
const generatedMissingTemplate = Boolean(resolvedEnvFile && resolvedEnvFile === path.resolve(generatedMissingTemplateFile));
const inputKind = generatedMissingTemplate
  ? "generated-missing-template"
  : (resolvedEnvFile ? "release-env-file" : "process-environment-only");
const blockers = [
  ...parsed.issues,
  ...parsed.duplicateKeys.map((key) => `duplicate env key: ${key}`),
  ...unresolvedTemplateBlockers,
  ...config.blockers,
];
const placeholderDerivedConfigBlockers = configBlockerDetails
  .filter((detail) => detail.blockedByPlaceholderKey === true)
  .map((detail) => detail.blocker);
const placeholderDerivedConfigBlockerSet = new Set(placeholderDerivedConfigBlockers);
const primaryBlockers = [
  ...parsed.issues,
  ...parsed.duplicateKeys.map((key) => `duplicate env key: ${key}`),
  ...canonicalUnresolvedTemplateBlockers,
  ...config.blockers.filter((blocker) => !placeholderDerivedConfigBlockerSet.has(blocker)),
];
const report = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  envFile: resolvedEnvFile ? portablePath(resolvedEnvFile) : null,
  inputKind,
  generatedMissingTemplate,
  envFileSecurity: parsed.fileSecurity,
  missingEnv,
  summary: {
    keys: parsed.keys.length,
    canonicalKeys: canonicalKeys.length,
    duplicateKeys: parsed.duplicateKeys.length,
    envFileSecurityChecked: parsed.fileSecurity.checked,
    envFilePermissionSafe: parsed.fileSecurity.permissionSafe,
    envFilePermissionCheckSkipped: parsed.fileSecurity.permissionCheckSkipped,
    envFileModeOctal: parsed.fileSecurity.modeOctal,
    unresolvedTemplateKeys: unresolvedKeys.length,
    canonicalUnresolvedTemplateKeys: canonicalUnresolvedTemplateKeys.length,
    canonicalUnresolvedTemplateBlockers: canonicalUnresolvedTemplateBlockers.length,
    releaseConfigBlockers: config.blockers.length,
    releaseConfigBlockersFromPlaceholders,
    releaseConfigBlockersAfterPlaceholders,
    canonicalReleaseConfigBlockerKeys: canonicalReleaseConfigBlockerKeys.length,
    warnings: config.warnings.length,
    blockers: blockers.length,
    primaryBlockers: primaryBlockers.length,
  },
  keys: parsed.keys,
  canonicalKeys,
  duplicateKeys: parsed.duplicateKeys,
  unresolvedTemplateKeys: unresolvedKeys,
  canonicalUnresolvedTemplateKeys,
  unresolvedTemplateBlockers,
  canonicalUnresolvedTemplateBlockers,
  canonicalMissingEnvKeys,
  canonicalReleaseConfigBlockerKeys,
  releaseConfigBlockerDetails: configBlockerDetails,
  blockers,
  primaryBlockers,
  placeholderDerivedConfigBlockers,
  warnings: config.warnings,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);

if (report.status === "PASS") {
  console.log(`[ddd-release-env-file-lint] PASS envFile=${report.envFile}; report=${outputFile}`);
} else {
  for (const blocker of primaryBlockers) {
    console.error(`[ddd-release-env-file-lint] ${blocker}`);
  }
  if (placeholderDerivedConfigBlockers.length > 0) {
    console.error(`[ddd-release-env-file-lint] ${placeholderDerivedConfigBlockers.length} release config blocker(s) are derived from unresolved placeholder values; replace placeholders first. Full derived list is in ${outputFile}`);
    for (const blocker of placeholderDerivedConfigBlockers.slice(0, 5)) {
      console.error(`[ddd-release-env-file-lint] derived: ${blocker}`);
    }
    if (placeholderDerivedConfigBlockers.length > 5) {
      console.error(`[ddd-release-env-file-lint] derived: ... ${placeholderDerivedConfigBlockers.length - 5} more`);
    }
  }
  console.error(`[ddd-release-env-file-lint] FAIL envFile=${report.envFile || "missing"}; report=${outputFile}`);
  process.exit(1);
}
