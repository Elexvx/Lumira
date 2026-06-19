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
const outputFile = process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_REPORT
  ? path.resolve(process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_REPORT)
  : path.join(artifactRoot, "release", "release-env-safe-defaults.json");
const dryRun = process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_DRY_RUN === "1"
  || process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_DRY_RUN === "true";
const force = process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_FORCE === "1"
  || process.env.DDD_RELEASE_ENV_SAFE_DEFAULTS_FORCE === "true";

const secretNamePattern = /(PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL|PRIVATE|ACCESS_TOKEN)/i;

const safeDefaults = [
  {
    key: "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL",
    value: "gpt-4o-mini",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL",
    value: "text-embedding-3-small",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "LUMIRA_FILE_SECURITY_SCAN_MODE",
    value: "CLAMAV",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "LUMIRA_FILE_OCR_MODE",
    value: "TESSERACT",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "UPLOAD_STORAGE_ROOT",
    value: "/opt/lumira/data/uploads",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "SAAS_EVENT_OUTBOX_DISPATCHER",
    value: "redis-stream",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "REDIS_PORT",
    value: "6379",
    source: "docs/36-ddd-release-env-template.env",
  },
  {
    key: "LUMIRA_AI_PROVIDER",
    value: "openai-compatible",
    source: "frontend/src/pages/settings/ai-employees/AiEmployeesPage.tsx",
  },
];

const auxiliarySafeDefaults = new Map([
  ["LUMIRA_AI_PROVIDER", { expectedValues: ["openai-compatible"] }],
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

function validateSafeDefault(defaultValue, requirement) {
  const issues = [];
  if (!requirement) {
    const auxiliary = auxiliarySafeDefaults.get(defaultValue.key);
    if (!auxiliary) {
      issues.push("is not a canonical release config key");
      return issues;
    }
    const valueIssue = evidenceValueIssue(defaultValue.value);
    if (valueIssue) {
      issues.push(valueIssue);
    }
    if (Array.isArray(auxiliary.expectedValues) && !auxiliary.expectedValues.includes(defaultValue.value)) {
      issues.push(`must be one of: ${auxiliary.expectedValues.join(", ")}`);
    }
    return issues;
  }
  if (secretNamePattern.test(defaultValue.key)) {
    issues.push("looks like a secret and must not be auto-filled");
  }
  if (requirement.https === true || requirement.nonLocal === true) {
    issues.push("requires a production-equivalent endpoint and must not be auto-filled");
  }
  const valueIssue = evidenceValueIssue(defaultValue.value);
  if (valueIssue) {
    issues.push(valueIssue);
  }
  if (Array.isArray(requirement.expectedValues) && requirement.expectedValues.length > 0 && !requirement.expectedValues.includes(defaultValue.value)) {
    issues.push(`must be one of: ${requirement.expectedValues.join(", ")}`);
  }
  if (Array.isArray(requirement.disallowValues) && requirement.disallowValues.includes(defaultValue.value)) {
    issues.push(`must not be one of: ${requirement.disallowValues.join(", ")}`);
  }
  if (requirement.pattern && !(new RegExp(requirement.pattern)).test(defaultValue.value)) {
    issues.push(`must match ${requirement.patternDescription || requirement.pattern}`);
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
    blockers,
    ...extra,
  };
  writeReport(report);
  for (const blocker of blockers) {
    console.error(`[ddd-release-env-safe-defaults] ${blocker}`);
  }
  console.error(`[ddd-release-env-safe-defaults] FAIL report=${portablePath(outputFile)}`);
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
const requirements = canonicalRequirements();
const validationIssues = safeDefaults.flatMap((defaultValue) => (
  validateSafeDefault(defaultValue, requirements.get(defaultValue.key))
    .map((issue) => `${defaultValue.key}: ${issue}`)
));
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

for (const defaultValue of safeDefaults) {
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
  fail(conflicts.map((conflict) => `${conflict.key}: target already has a different concrete value; set DDD_RELEASE_ENV_SAFE_DEFAULTS_FORCE=1 to overwrite`), {
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
  envFileSecurity: {
    checked: true,
    modeOctal,
    permissionSafe,
    requiredMode: "600",
  },
  summary: {
    safeDefaults: safeDefaults.length,
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

console.log(`[ddd-release-env-safe-defaults] PASS updates=${updates.length} additions=${additions.length} skipped=${skipped.length} dryRun=${dryRun} report=${portablePath(outputFile)}`);
