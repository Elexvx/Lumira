#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { evidenceValueIssue } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = process.env.DDD_RELEASE_EVIDENCE_DIR
  ? path.resolve(process.env.DDD_RELEASE_EVIDENCE_DIR)
  : path.join(repoRoot, "artifacts", "ddd");
const envFile = process.argv[2] || process.env.DDD_RELEASE_ENV_FILE || "";
const outputFile = process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_REPORT
  ? path.resolve(process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_REPORT)
  : path.join(artifactRoot, "release", "release-provenance-defaults.json");
const dryRun = process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_DRY_RUN === "1"
  || process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_DRY_RUN === "true";
const force = process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_FORCE === "1"
  || process.env.DDD_RELEASE_PROVENANCE_DEFAULTS_FORCE === "true";

const localDiagnosticValues = new Set([
  "dev",
  "development",
  "local",
  "local-dev",
  "local-worktree",
  "local-operator",
  "local-test",
  "test",
  "testing",
  "tester",
  "unknown",
]);

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

function inferReleaseCandidate() {
  if (process.env.DDD_RELEASE_CANDIDATE_DEFAULT) {
    return process.env.DDD_RELEASE_CANDIDATE_DEFAULT;
  }
  if (process.env.GITHUB_SHA) {
    return process.env.GITHUB_SHA;
  }
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return "";
  }
}

function inferEvidenceOperator() {
  if (process.env.DDD_EVIDENCE_OPERATOR_DEFAULT) {
    return process.env.DDD_EVIDENCE_OPERATOR_DEFAULT;
  }
  if (process.env.GITHUB_ACTOR) {
    return process.env.GITHUB_ACTOR;
  }
  try {
    return os.userInfo().username || process.env.USER || "";
  } catch {
    return process.env.USER || "";
  }
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
  return text === "" || text === "__REQUIRED__" || evidenceValueIssue(text) === "must not contain placeholder text";
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
      invalidLines.push({ line: index + 1 });
      continue;
    }
    const key = match[2];
    if (entries.has(key)) {
      duplicateKeys.push(key);
    }
    entries.set(key, {
      index,
      key,
      value: unquote(match[3].trim()),
      exportPrefix: rawLine.trimStart().startsWith("export "),
    });
  }
  return { lines, entries, duplicateKeys: [...new Set(duplicateKeys)].sort(), invalidLines };
}

function validateDefault(key, value) {
  const issues = [];
  const text = String(value || "").trim();
  const valueIssue = evidenceValueIssue(text);
  if (valueIssue) {
    issues.push(valueIssue);
  }
  if (localDiagnosticValues.has(text.toLowerCase())) {
    issues.push("must not be a local diagnostic placeholder");
  }
  if (key === "DDD_RELEASE_CANDIDATE" && !/^(?:[a-f0-9]{7,64}|v?\d+\.\d+|[A-Za-z0-9._/-]{8,})$/i.test(text)) {
    issues.push("must look like a commit SHA, version, or build candidate");
  }
  if (key === "DDD_EVIDENCE_OPERATOR" && text.length < 2) {
    issues.push("must identify a real release operator");
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
    envFile: envFile ? portablePath(envFile) : null,
    dryRun,
    force,
    redacted: true,
    blockers,
    ...extra,
  };
  writeReport(report);
  for (const blocker of blockers) {
    console.error(`[ddd-release-provenance-defaults] ${blocker}`);
  }
  console.error(`[ddd-release-provenance-defaults] FAIL report=${portablePath(outputFile)}`);
  process.exit(1);
}

if (!envFile) {
  fail(["DDD_RELEASE_ENV_FILE or first CLI argument is required"]);
}
if (!fs.existsSync(envFile)) {
  fail([`env file does not exist: ${portablePath(envFile)}`]);
}

const mode = fs.statSync(envFile).mode & 0o777;
const modeOctal = mode.toString(8).padStart(3, "0");
const permissionCheckSkipped = process.platform === "win32";
const permissionSafe = permissionCheckSkipped || (mode & 0o077) === 0;
const parsed = parseEnvFile(envFile);
const defaults = [
  { key: "DDD_RELEASE_CANDIDATE", value: inferReleaseCandidate(), source: process.env.DDD_RELEASE_CANDIDATE_DEFAULT ? "DDD_RELEASE_CANDIDATE_DEFAULT" : process.env.GITHUB_SHA ? "GITHUB_SHA" : "git rev-parse HEAD" },
  { key: "DDD_EVIDENCE_OPERATOR", value: inferEvidenceOperator(), source: process.env.DDD_EVIDENCE_OPERATOR_DEFAULT ? "DDD_EVIDENCE_OPERATOR_DEFAULT" : process.env.GITHUB_ACTOR ? "GITHUB_ACTOR" : "os.userInfo" },
];
const validationIssues = defaults.flatMap((entry) => validateDefault(entry.key, entry.value)
  .map((issue) => `${entry.key}: ${issue}`));
const blockers = [
  ...(!permissionSafe ? [`env file permissions are too broad: ${portablePath(envFile)} mode=${modeOctal}; use chmod 600`] : []),
  ...parsed.duplicateKeys.map((key) => `duplicate env key: ${key}`),
  ...parsed.invalidLines.map((line) => `invalid env line ${line.line}`),
  ...validationIssues,
];

if (blockers.length > 0) {
  fail(blockers, {
    envFileSecurity: {
      checked: true,
      modeOctal,
      permissionSafe,
      permissionCheckSkipped,
      requiredMode: "600",
    },
  });
}

const updates = [];
const additions = [];
const skipped = [];
const conflicts = [];

for (const defaultValue of defaults) {
  const entry = parsed.entries.get(defaultValue.key);
  if (!entry) {
    parsed.lines.push(`${defaultValue.key}=${quoteEnvValue(defaultValue.value)}`);
    additions.push({ key: defaultValue.key, source: defaultValue.source });
    continue;
  }
  if (!isPlaceholder(entry.value) && entry.value !== defaultValue.value && !force) {
    conflicts.push({ key: defaultValue.key, source: defaultValue.source });
    continue;
  }
  if (entry.value === defaultValue.value) {
    skipped.push({ key: defaultValue.key, reason: "already-default", source: defaultValue.source });
    continue;
  }
  const replacement = `${entry.exportPrefix ? "export " : ""}${defaultValue.key}=${quoteEnvValue(defaultValue.value)}`;
  parsed.lines[entry.index] = replacement;
  updates.push({ key: defaultValue.key, source: defaultValue.source });
}

if (conflicts.length > 0 && !force) {
  fail(conflicts.map((conflict) => `${conflict.key}: target already has a different concrete value; set DDD_RELEASE_PROVENANCE_DEFAULTS_FORCE=1 to overwrite`), {
    conflicts,
  });
}

if ((updates.length + additions.length) > 0 && !dryRun) {
  fs.writeFileSync(envFile, `${parsed.lines.join("\n").replace(/\n+$/u, "")}\n`);
  fs.chmodSync(envFile, 0o600);
}

const report = {
  generatedAt: new Date().toISOString(),
  status: "PASS",
  envFile: portablePath(envFile),
  dryRun,
  force,
  redacted: true,
  envFileSecurity: {
    checked: true,
    modeOctal,
    permissionSafe,
    requiredMode: "600",
  },
  sourceEnvironmentDefaulted: false,
  sourceEnvironmentPolicy: "DDD_EVIDENCE_ENVIRONMENT is never inferred; set a real production-equivalent environment explicitly.",
  summary: {
    defaults: defaults.length,
    updates: updates.length,
    additions: additions.length,
    skipped: skipped.length,
    conflicts: conflicts.length,
  },
  updates,
  additions,
  skipped,
  conflicts,
};
writeReport(report);

console.log(`[ddd-release-provenance-defaults] PASS updates=${updates.length} additions=${additions.length} skipped=${skipped.length} dryRun=${dryRun} report=${portablePath(outputFile)}`);
