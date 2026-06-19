#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const rawArgs = process.argv.slice(2);

function readArg(name, fallback) {
  const prefix = `--${name}=`;
  const found = rawArgs.find((arg) => arg.startsWith(prefix));
  return found ? found.slice(prefix.length) : fallback;
}

const lintFile = readArg("lint-file", "artifacts/ddd/release/release-env-lint.json");
const configEvidenceFile = readArg("config-evidence-file", "artifacts/ddd/config/release-config-evidence.json");
const markdownOutput = readArg("markdown-output", "tmp/p0-release-env-fill-checklist.md");
const jsonOutput = readArg("json-output", "tmp/p0-release-env-fill-keys.json");
const envTemplateOutput = readArg("env-template-output", "tmp/p0-release-env-fill.template.env");
const markdownOnly = rawArgs.includes("--markdown");
const jsonOnly = rawArgs.includes("--json");
const envTemplateOnly = rawArgs.includes("--env-template");

const groupDefinitions = {
  runtime: [
    "LUMIRA_BASE_URL",
    "BASE_URL",
    "PLAYWRIGHT_BASE_URL",
    "FRONTEND_BASE_URL",
    "AI_SERVICE_BASE_URL",
    "AUTH_SERVICE_BASE_URL",
    "PAYMENT_SERVICE_BASE_URL",
    "FILE_SERVICE_BASE_URL",
    "JOB_EXECUTOR_BASE_URL",
    "MESSAGE_SERVICE_BASE_URL",
    "SYSTEM_SERVICE_BASE_URL",
  ],
  database: ["DB_URL", "DB_USERNAME", "DB_PASSWORD", "MYSQL_HOST", "MYSQL_PORT", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD"],
  security: ["JWT_SECRET", "FIELD_SECRET", "DDD_AUTH_PASSWORD", "DDD_AUTH_USERNAME"],
  evidence: [
    "DDD_DEPLOYMENT_EVIDENCE",
    "DDD_FRONTEND_DEPLOYMENT_EVIDENCE",
    "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE",
    "DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE",
    "DDD_MIGRATION_COMPLETED_AT",
    "DDD_MIGRATION_FRESH_DB_EVIDENCE",
    "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
    "DDD_EXPLAIN_DATABASE",
  ],
  ai: [
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_INTERNAL_TOKEN",
    "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_BASE_URL",
    "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_BASE_URL",
  ],
  jobs: ["SAAS_JOB_INTERNAL_TOKEN", "XXL_JOB_ACCESS_TOKEN", "XXL_JOB_ADMIN_ADDRESSES"],
};

function readJson(file) {
  return JSON.parse(fs.readFileSync(path.resolve(repoRoot, file), "utf8"));
}

function blockerKey(blocker) {
  return String(blocker).split(":")[0]?.trim();
}

function groupKeys(keys) {
  const groupedKeys = new Set(Object.values(groupDefinitions).flat());
  const groups = {};
  for (const [group, groupKeys] of Object.entries(groupDefinitions)) {
    const present = groupKeys.filter((key) => keys.includes(key));
    if (present.length > 0) {
      groups[group] = present;
    }
  }
  groups.other = keys.filter((key) => !groupedKeys.has(key));
  return groups;
}

function renderMarkdown(checklist) {
  const lines = [
    "# P0 Release Env Fill Checklist",
    "",
    `Generated at: ${checklist.generatedAt}`,
    "",
    `Lint status: ${checklist.status}`,
    `Env file: ${checklist.envFile}`,
    `Primary blockers: ${checklist.primaryBlockerCount}`,
    `Config blocker count: ${checklist.configBlockerCount}`,
    "",
    "## Required Keys By Group",
    "",
  ];

  for (const [group, keys] of Object.entries(checklist.groups)) {
    if (keys.length === 0) continue;
    lines.push(`### ${group}`, "", ...keys.map((key) => `- ${key}`), "");
  }

  lines.push(
    "## Validation Commands",
    "",
    "```bash",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-config-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan",
    "```",
    "",
    "## Acceptance Rule",
    "",
    "Do not mark `release-infra:p0-release-env` PASS until release env lint and release config evidence are PASS, and the resulting artifacts are attached to the lane receipt.",
    "",
  );
  return lines.join("\n");
}

function placeholderForKey(key) {
  if (key.endsWith("_BASE_URL") || key === "PLAYWRIGHT_BASE_URL" || key === "LUMIRA_BASE_URL" || key.includes("ORIGIN")) {
    return "__REQUIRED_HTTPS__";
  }
  if (key.includes("PASSWORD") || key.includes("SECRET") || key.includes("TOKEN") || key.endsWith("_KEY")) {
    return "__REQUIRED_SECRET_REF__";
  }
  if (key.includes("COMPLETED_AT")) return "__REQUIRED_ISO_TIMESTAMP__";
  if (key.includes("VALIDATED")) return "__REQUIRED_TRUE__";
  if (key.includes("PORT")) return "__REQUIRED_PORT__";
  if (key.includes("EVIDENCE") || key.includes("ARTIFACT")) return "__REQUIRED_ARTIFACT_PATH_OR_URL__";
  return "__REQUIRED__";
}

function renderEnvTemplate(checklist) {
  const lines = [
    "# P0 release env fill template.",
    "# Fill this into .env.release.local, then run the validation commands at the bottom.",
    "# Do not commit real values. Secret-like values should be secret references, not plaintext.",
    "",
    `# Source lint file: ${checklist.lintFile}`,
    `# Source config evidence file: ${checklist.configEvidenceFile}`,
    `# Primary blockers: ${checklist.primaryBlockerCount}`,
    `# Config blockers: ${checklist.configBlockerCount}`,
    "",
  ];

  for (const [group, keys] of Object.entries(checklist.groups)) {
    if (keys.length === 0) continue;
    lines.push(`# ${group}`, ...keys.map((key) => `${key}=${placeholderForKey(key)}`), "");
  }

  lines.push(
    "# Validation commands",
    ...checklist.validationCommands.map((command) => `# ${command}`),
    "",
  );
  return lines.join("\n");
}

function writeFile(file, value) {
  const absoluteFile = path.resolve(repoRoot, file);
  fs.mkdirSync(path.dirname(absoluteFile), { recursive: true });
  fs.writeFileSync(absoluteFile, value);
}

const lint = readJson(lintFile);
const configEvidence = readJson(configEvidenceFile);
const blockers = lint.primaryBlockers || lint.blockers || [];
const keys = [...new Set(blockers.map(blockerKey).filter(Boolean))];
const generatedAt = new Date().toISOString();
const checklist = {
  generatedAt,
  status: lint.status,
  envFile: lint.envFile,
  lintFile,
  configEvidenceFile,
  primaryBlockerCount: lint.summary?.primaryBlockers ?? blockers.length,
  configBlockerCount: configEvidence.summary?.blockers ?? (configEvidence.blockers || []).length,
  keyCount: keys.length,
  keys,
  groups: groupKeys(keys),
  validationCommands: [
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-config-evidence.mjs",
    "node scripts/ddd-release-readiness-summary.mjs",
    "node scripts/ddd-staging-execution-checklist.mjs --release-env-submission-plan",
  ],
};

const markdown = renderMarkdown(checklist);
const envTemplate = renderEnvTemplate(checklist);

if (markdownOnly) {
  process.stdout.write(markdown);
  process.exit(0);
}

if (jsonOnly) {
  process.stdout.write(`${JSON.stringify(checklist, null, 2)}\n`);
  process.exit(0);
}

if (envTemplateOnly) {
  process.stdout.write(envTemplate);
  process.exit(0);
}

writeFile(markdownOutput, markdown);
writeFile(jsonOutput, `${JSON.stringify(checklist, null, 2)}\n`);
writeFile(envTemplateOutput, envTemplate);
console.log(JSON.stringify({
  status: checklist.status,
  generatedAt,
  willWriteFiles: true,
  markdownOutput,
  jsonOutput,
  envTemplateOutput,
  keyCount: checklist.keyCount,
  primaryBlockerCount: checklist.primaryBlockerCount,
  configBlockerCount: checklist.configBlockerCount,
  nextCommand: "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
}, null, 2));
