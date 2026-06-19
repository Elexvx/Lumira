#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { releaseConfigGroups } from "./ddd-release-config-contract.mjs";
import { evidenceValueIssue } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const envFile = process.argv[2] || process.env.DDD_RELEASE_ENV_FILE || "";
const outputFile = process.env.DDD_RELEASE_ENV_ALIAS_SYNC_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_ALIAS_SYNC_REPORT)
  : path.join(artifactRoot, "release", "release-env-alias-sync.json");
const dryRun = process.env.DDD_RELEASE_ENV_ALIAS_SYNC_DRY_RUN === "1"
  || process.env.DDD_RELEASE_ENV_ALIAS_SYNC_DRY_RUN === "true";
const force = process.env.DDD_RELEASE_ENV_ALIAS_SYNC_FORCE === "1"
  || process.env.DDD_RELEASE_ENV_ALIAS_SYNC_FORCE === "true";

function parseEnvFile(file) {
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  const entries = new Map();
  const duplicateKeys = [];
  for (const [index, rawLine] of lines.entries()) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const match = rawLine.match(/^(\s*)(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)(\s*)$/);
    if (!match) {
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
      rawLine,
      value,
      exportPrefix: rawLine.trimStart().startsWith("export "),
    });
  }
  return { lines, entries, duplicateKeys: [...new Set(duplicateKeys)].sort() };
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

function aliasGroups() {
  const groups = [];
  for (const group of releaseConfigGroups) {
    for (const requirement of group.requirements || []) {
      const keys = [...new Set((Array.isArray(requirement.keys) ? requirement.keys : [requirement.key]).filter(Boolean))];
      if (keys.length > 1) {
        groups.push({
          owner: group.owner,
          group: group.name,
          requirement: requirement.name,
          keys,
        });
      }
    }
  }
  return groups;
}

function syncAliases({ lines, entries }) {
  const updates = [];
  const additions = [];
  const conflicts = [];
  const skipped = [];

  for (const group of aliasGroups()) {
    const present = group.keys
      .map((key) => entries.get(key))
      .filter(Boolean);
    const concrete = present.filter((entry) => !isPlaceholder(entry.value) && String(entry.value).trim() !== "");
    const uniqueValues = [...new Set(concrete.map((entry) => entry.value))];

    if (uniqueValues.length === 0) {
      skipped.push({
        ...group,
        reason: "no concrete source value",
      });
      continue;
    }
    if (uniqueValues.length > 1 && !force) {
      conflicts.push({
        ...group,
        reason: "multiple concrete values; set DDD_RELEASE_ENV_ALIAS_SYNC_FORCE=1 to use the first key order",
        concreteKeys: concrete.map((entry) => entry.key),
      });
      continue;
    }

    const sourceKey = group.keys.find((key) => {
      const entry = entries.get(key);
      return entry && !isPlaceholder(entry.value) && String(entry.value).trim() !== "";
    });
    const sourceValue = entries.get(sourceKey).value;

    for (const key of group.keys) {
      const entry = entries.get(key);
      if (!entry) {
        additions.push({
          ...group,
          key,
          sourceKey,
        });
        lines.push(`${key}=${quoteEnvValue(sourceValue)}`);
        entries.set(key, {
          index: lines.length - 1,
          key,
          rawLine: lines[lines.length - 1],
          value: sourceValue,
          exportPrefix: false,
        });
        continue;
      }
      if (entry.key === sourceKey) {
        continue;
      }
      if (!isPlaceholder(entry.value) && String(entry.value).trim() !== "" && entry.value !== sourceValue) {
        continue;
      }
      const replacement = `${entry.exportPrefix ? "export " : ""}${key}=${quoteEnvValue(sourceValue)}`;
      if (lines[entry.index] !== replacement) {
        updates.push({
          ...group,
          key,
          sourceKey,
        });
        lines[entry.index] = replacement;
        entry.value = sourceValue;
        entry.rawLine = replacement;
      }
    }
  }

  return { lines, updates, additions, conflicts, skipped };
}

function writeReport(report) {
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);
}

if (!envFile) {
  const report = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    envFile: null,
    dryRun,
    blockers: ["DDD_RELEASE_ENV_FILE or first CLI argument is required"],
  };
  writeReport(report);
  console.error("[ddd-release-env-alias-sync] DDD_RELEASE_ENV_FILE or first CLI argument is required");
  process.exit(1);
}

if (!fs.existsSync(envFile)) {
  const report = {
    generatedAt: new Date().toISOString(),
    status: "FAIL",
    envFile,
    dryRun,
    blockers: [`env file does not exist: ${envFile}`],
  };
  writeReport(report);
  console.error(`[ddd-release-env-alias-sync] env file does not exist: ${envFile}`);
  process.exit(1);
}

const mode = fs.statSync(envFile).mode & 0o777;
const modeOctal = mode.toString(8).padStart(3, "0");
const permissionCheckSkipped = process.platform === "win32";
const permissionSafe = permissionCheckSkipped || (mode & 0o077) === 0;
const parsed = parseEnvFile(envFile);
const result = syncAliases(parsed);
const blockers = [
  ...(!permissionSafe ? [`env file permissions are too broad: ${envFile} mode=${modeOctal}; use chmod 600`] : []),
  ...parsed.duplicateKeys.map((key) => `duplicate env key: ${key}`),
  ...result.conflicts.map((conflict) => `${conflict.group}.${conflict.requirement}: ${conflict.reason}`),
];
const status = blockers.length > 0 ? "FAIL" : "PASS";
const changed = result.updates.length + result.additions.length;

if (status === "PASS" && changed > 0 && !dryRun) {
  fs.writeFileSync(envFile, `${result.lines.join("\n").replace(/\n+$/u, "")}\n`);
  fs.chmodSync(envFile, 0o600);
}

const report = {
  generatedAt: new Date().toISOString(),
  status,
  envFile: path.resolve(envFile),
  dryRun,
  force,
  envFileSecurity: {
    checked: true,
    mode: mode,
    modeOctal,
    permissionSafe,
    permissionCheckSkipped,
    requiredMode: "600",
  },
  summary: {
    aliasGroups: aliasGroups().length,
    updates: result.updates.length,
    additions: result.additions.length,
    conflicts: result.conflicts.length,
    skipped: result.skipped.length,
    duplicateKeys: parsed.duplicateKeys.length,
    changed,
  },
  updates: result.updates,
  additions: result.additions,
  conflicts: result.conflicts,
  skipped: result.skipped,
  duplicateKeys: parsed.duplicateKeys,
  blockers,
};
writeReport(report);

if (status !== "PASS") {
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-alias-sync] ${blocker}`);
  }
  console.error(`[ddd-release-env-alias-sync] FAIL report=${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-release-env-alias-sync] PASS updates=${result.updates.length} additions=${result.additions.length} dryRun=${dryRun} report=${outputFile}`);
