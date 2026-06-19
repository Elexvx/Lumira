#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const scriptPath = path.join(repoRoot, "scripts", "ddd-release-env-file-lint.mjs");
const releaseLintArtifact = path.join(repoRoot, "artifacts", "ddd", "release", "release-env-lint.json");
const failures = [];

function addFailure(message) {
  failures.push(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function runLint(envFile, missingReport, lintReport, env = {}) {
  return spawnSync("node", ["scripts/ddd-release-env-file-lint.mjs", envFile].filter(Boolean), {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_MISSING_ENV_REPORT: missingReport,
      DDD_RELEASE_ENV_LINT_REPORT: lintReport,
      DDD_EVIDENCE_ENVIRONMENT: "lint-contract",
      DDD_RELEASE_CANDIDATE: "lint-contract-sha",
      DDD_EVIDENCE_OPERATOR: "lint-contract-runner",
      ...env,
    },
  });
}

const source = fs.readFileSync(scriptPath, "utf8");
for (const snippet of [
  "process-environment-only",
  "missing-env-file",
  "generated-missing-template",
  "env file permissions are too broad",
  "__REQUIRED__ placeholder must be replaced",
  "placeholder value must be replaced",
  "line ${index + 1} must be KEY=value, export KEY=value, or a comment",
  "release config blocker(s) are derived from unresolved placeholder values",
  "process.exit(1)",
  "permissionCheckSkipped",
  "canonicalUnresolvedTemplateKeys",
  "placeholderDerivedConfigBlockers",
]) {
  if (!source.includes(snippet)) addFailure(`env file lint script must include ${snippet}`);
}
if (/^\s*source\s+/m.test(source)) addFailure("env file lint script must parse env files and must not source them");

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-file-lint-contract-"));
try {
  const missingReport = path.join(tmpDir, "release-env-missing.json");
  const lintReport = path.join(tmpDir, "release-env-lint.json");
  fs.writeFileSync(missingReport, `${JSON.stringify({
    status: "NOT_READY",
    uniqueEnvKeyCount: 2,
    templateEnvKeyCount: 2,
    groupCount: 1,
    uniqueEnvKeys: ["LUMIRA_BASE_URL", "DB_URL"],
    templateEnvKeys: ["LUMIRA_BASE_URL", "DB_URL"],
  }, null, 2)}\n`);

  const missing = runLint(path.join(tmpDir, "missing.env"), missingReport, lintReport);
  if (missing.status === 0) addFailure("env file lint must fail when the env file is missing");
  let report = readJson(lintReport);
  if (report.status !== "FAIL") addFailure("missing env file report must be FAIL");
  if (report.inputKind !== "release-env-file") addFailure("missing env file report must use release-env-file inputKind");
  if (report.envFileSecurity?.reason !== "missing-env-file") addFailure("missing env file report must include missing-env-file security reason");
  if (!report.blockers.some((blocker) => blocker.includes("env file does not exist"))) addFailure("missing env file report must include missing file blocker");

  const broadEnv = path.join(tmpDir, "broad.env");
  fs.writeFileSync(broadEnv, [
    "LUMIRA_BASE_URL=https://api.contract.lumira.app",
    "DB_URL=jdbc:mysql://mysql.contract.lumira.app:3306/lumira",
    "",
  ].join("\n"));
  fs.chmodSync(broadEnv, 0o644);
  const broad = runLint(broadEnv, missingReport, lintReport);
  report = readJson(lintReport);
  if (process.platform === "win32") {
    if (report.envFileSecurity?.permissionCheckSkipped !== true) addFailure("broad permission report must mark permissionCheckSkipped=true on Windows");
    if (report.summary?.envFilePermissionCheckSkipped !== true) addFailure("broad permission summary must mark envFilePermissionCheckSkipped=true on Windows");
  } else {
    if (broad.status === 0) addFailure("env file lint must fail broad env file permissions");
    if (report.envFileSecurity?.permissionSafe !== false) addFailure("broad permission report must mark permissionSafe=false");
    if (report.summary?.envFilePermissionSafe !== false) addFailure("broad permission summary must mark envFilePermissionSafe=false");
    if (!report.blockers.some((blocker) => blocker.includes("use chmod 600"))) addFailure("broad permission report must tell operator to use chmod 600");
  }

  const placeholderEnv = path.join(tmpDir, "placeholder.env");
  fs.writeFileSync(placeholderEnv, [
    "LUMIRA_BASE_URL=__REQUIRED__",
    "DB_URL=jdbc:mysql://mysql.contract.lumira.app:3306/lumira",
    "",
  ].join("\n"));
  fs.chmodSync(placeholderEnv, 0o600);
  const placeholder = runLint(placeholderEnv, missingReport, lintReport);
  if (placeholder.status === 0) addFailure("env file lint must fail unresolved placeholders");
  report = readJson(lintReport);
  if (!report.unresolvedTemplateKeys.includes("LUMIRA_BASE_URL")) addFailure("placeholder report must include unresolved LUMIRA_BASE_URL");
  if (!report.canonicalUnresolvedTemplateKeys.includes("LUMIRA_BASE_URL")) addFailure("placeholder report must include canonical unresolved LUMIRA_BASE_URL");
  if (!report.blockers.some((blocker) => blocker.includes("__REQUIRED__ placeholder must be replaced"))) addFailure("placeholder report must include replacement blocker");
  if (report.summary?.primaryBlockers >= report.summary?.blockers) addFailure("placeholder report must separate primary blockers from derived config blockers");

  const unsafeSyntaxEnv = path.join(tmpDir, "unsafe.env");
  fs.writeFileSync(unsafeSyntaxEnv, [
    "LUMIRA_BASE_URL=https://api.contract.lumira.app",
    "echo SHOULD_NOT_RUN >&2",
    "",
  ].join("\n"));
  fs.chmodSync(unsafeSyntaxEnv, 0o600);
  const unsafe = runLint(unsafeSyntaxEnv, missingReport, lintReport);
  if (unsafe.status === 0) addFailure("env file lint must fail unsafe shell-like syntax");
  report = readJson(lintReport);
  if (!report.blockers.some((blocker) => blocker.includes("must be KEY=value"))) addFailure("unsafe syntax report must reject non-env lines");

  const generatedTemplate = path.join(path.dirname(missingReport), "release-env-missing.template.env");
  fs.writeFileSync(generatedTemplate, "LUMIRA_BASE_URL=__REQUIRED__\nDB_URL=__REQUIRED__\n");
  fs.chmodSync(generatedTemplate, 0o644);
  const generated = runLint(generatedTemplate, missingReport, lintReport);
  if (generated.status === 0) addFailure("generated missing template must fail because placeholders remain unresolved");
  report = readJson(lintReport);
  if (report.inputKind !== "generated-missing-template") addFailure("generated template report must use generated-missing-template inputKind");
  if (report.envFileSecurity?.permissionCheckSkipped !== true) addFailure("generated template report must skip permission enforcement");
  if (report.summary?.envFilePermissionCheckSkipped !== true) addFailure("generated template summary must record skipped permission check");
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (!fs.existsSync(releaseLintArtifact)) {
  addFailure(`release env lint artifact must exist: ${releaseLintArtifact}`);
} else {
  const releaseLint = readJson(releaseLintArtifact);
  if (releaseLint.inputKind !== "release-env-file") addFailure("current release env lint artifact must be based on the release env file");
  if (releaseLint.envFileSecurity?.permissionSafe !== true) addFailure("current release env file permissions must be safe");
  if (releaseLint.envFileSecurity?.modeOctal !== "600") addFailure("current release env file must be chmod 600");
  if ((releaseLint.summary?.unresolvedTemplateKeys || 0) > 0) {
    if (releaseLint.status !== "FAIL") {
      addFailure("current release env lint artifact must fail when unresolved placeholders exist");
    }
    if ((releaseLint.summary?.primaryBlockers || 0) <= 0) {
      addFailure("current release env lint artifact must expose primary blockers when unresolved placeholders exist");
    }
  } else if (releaseLint.status !== "PASS") {
    addFailure("current release env lint artifact must be PASS when unresolved placeholders are fully resolved");
  }
  if (!Array.isArray(releaseLint.primaryBlockers) || releaseLint.primaryBlockers.length !== releaseLint.summary?.primaryBlockers) {
    addFailure("current release env lint primaryBlockers length must match summary");
  }
  if (!Array.isArray(releaseLint.placeholderDerivedConfigBlockers)) addFailure("current release env lint must include derived placeholder blockers");
}

if (failures.length > 0) {
  throw new Error(`release env file lint contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-env-file-lint-contract] ok");
