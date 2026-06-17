import path from "node:path";

export const requiredFrontendStaticCommands = ["lint", "typecheck", "unit"];
const repoRoot = path.resolve(import.meta.dirname, "..");
const homeDir = process.env.HOME || "";

export function validateFrontendBuildArtifact(artifact) {
  const issues = [];
  const blockers = Array.isArray(artifact?.blockers) ? artifact.blockers : [];
  const files = Number(artifact?.summary?.files || 0);
  const assets = Number(artifact?.summary?.assets || 0);
  const totalBytes = Number(artifact?.summary?.totalBytes || 0);
  const expectedStatus = blockers.length === 0 ? "PASS" : "FAIL";

  if (artifact?.status !== expectedStatus) {
    issues.push(`frontend build status must be ${expectedStatus}, got ${artifact?.status ?? "missing"}`);
  }
  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (artifact?.build?.skipped) {
    issues.push("frontend production build was skipped");
  }
  if (artifact?.build && artifact.build.status !== "PASS") {
    issues.push(`frontend production build status=${artifact.build.status}`);
  }
  if (artifact?.build) {
    if (!artifact.build.command) {
      issues.push("frontend production build command is required");
    }
    if (artifact.build.skipped !== true && artifact.build.exitCode !== 0) {
      issues.push(`frontend production build exitCode=${artifact.build.exitCode ?? "missing"}`);
    }
    if (!Number.isFinite(artifact.build.durationMs) || artifact.build.durationMs < 0) {
      issues.push("frontend production build durationMs must be non-negative");
    }
    validateNoLocalPath("frontend production build stdoutTail", artifact.build.stdoutTail, issues);
    validateNoLocalPath("frontend production build stderrTail", artifact.build.stderrTail, issues);
  }
  if (files === 0 || assets === 0) {
    issues.push(`invalid dist output: files=${files}, assets=${assets}`);
  }
  if (totalBytes <= 0) {
    issues.push(`invalid dist totalBytes=${totalBytes}`);
  }
  if (artifact?.summary?.indexHtmlPresent !== true || !artifact?.entrypoint) {
    issues.push("missing dist index.html");
  } else {
    validateFileMetadata("entrypoint", artifact.entrypoint, issues);
    if (!String(artifact.entrypoint.file || "").endsWith("frontend/dist/index.html")) {
      issues.push(`entrypoint file must be frontend/dist/index.html, got ${artifact.entrypoint.file ?? "missing"}`);
    }
  }
  if (!Array.isArray(artifact?.largestFiles)) {
    issues.push("largestFiles must be an array");
  } else {
    if (artifact.largestFiles.length === 0 && files > 0) {
      issues.push("largestFiles must not be empty when dist files exist");
    }
    let previousBytes = Infinity;
    const seenFiles = new Set();
    for (const [index, file] of artifact.largestFiles.entries()) {
      validateFileMetadata(`largestFiles[${index}]`, file, issues);
      if (seenFiles.has(file?.file)) {
        issues.push(`duplicate frontend build file metadata ${file.file}`);
      }
      seenFiles.add(file?.file);
      if (Number.isFinite(file?.bytes) && file.bytes > previousBytes) {
        issues.push(`largestFiles must be sorted by bytes descending at index ${index}`);
      }
      if (Number.isFinite(file?.bytes)) {
        previousBytes = file.bytes;
      }
    }
    if (artifact.largestFiles.length > Math.min(files, 20)) {
      issues.push(`largestFiles has too many entries: ${artifact.largestFiles.length}`);
    }
  }
  return issues;
}

export function validateFrontendStaticArtifact(artifact) {
  const issues = [];
  const results = Array.isArray(artifact?.results) ? artifact.results : [];
  const actualPassed = results.filter((result) => result.status === "PASS" && result.skipped !== true).length;
  const actualFailed = results.filter((result) => result.status !== "PASS" && result.skipped !== true).length;
  const actualSkipped = results.filter((result) => result.skipped === true).length;
  const actualDurationMs = Math.round(results.reduce((sum, result) => sum + (Number(result.durationMs) || 0), 0) * 100) / 100;
  const missingCommands = requiredFrontendStaticCommands
    .filter((commandName) => !results.some((result) => result.name === commandName));
  const expectedStatus = actualFailed === 0 && actualSkipped === 0 && missingCommands.length === 0 ? "PASS" : "FAIL";

  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (artifact?.status !== expectedStatus) {
    issues.push(`frontend static status must be ${expectedStatus}, got ${artifact?.status ?? "missing"}`);
  }
  if ((artifact?.summary?.commands || 0) !== results.length) {
    issues.push(`frontend static summary commands mismatch: declared=${artifact?.summary?.commands || 0}, actual=${results.length}`);
  }
  if ((artifact?.summary?.passed || 0) !== actualPassed) {
    issues.push(`frontend static summary passed mismatch: declared=${artifact?.summary?.passed || 0}, actual=${actualPassed}`);
  }
  if ((artifact?.summary?.failed || 0) !== actualFailed) {
    issues.push(`frontend static summary failed mismatch: declared=${artifact?.summary?.failed || 0}, actual=${actualFailed}`);
  }
  if ((artifact?.summary?.skipped || 0) !== actualSkipped) {
    issues.push(`frontend static summary skipped mismatch: declared=${artifact?.summary?.skipped || 0}, actual=${actualSkipped}`);
  }
  if (artifact?.summary?.durationMs !== undefined && artifact.summary.durationMs !== actualDurationMs) {
    issues.push(`frontend static summary durationMs mismatch: declared=${artifact.summary.durationMs}, actual=${actualDurationMs}`);
  }
  const resultByName = new Map();
  for (const result of results) {
    if (!requiredFrontendStaticCommands.includes(result.name)) {
      issues.push(`unknown frontend static command ${result.name ?? "missing"}`);
    }
    if (resultByName.has(result.name)) {
      issues.push(`duplicate frontend static command ${result.name}`);
    }
    resultByName.set(result.name, result);
    if (!result.command) {
      issues.push(`${result.name ?? "unknown"} command is required`);
    }
    if (!["PASS", "FAIL", "SKIPPED"].includes(result.status)) {
      issues.push(`${result.name ?? "unknown"} status=${result.status ?? "missing"}`);
    }
    if (result.skipped !== true && typeof result.exitCode !== "number") {
      issues.push(`${result.name ?? "unknown"} exitCode must be a number when not skipped`);
    }
    if (!Number.isFinite(result.durationMs) || result.durationMs < 0) {
      issues.push(`${result.name ?? "unknown"} durationMs must be non-negative`);
    }
    validateNoLocalPath(`${result.name ?? "unknown"} stdoutTail`, result.stdoutTail, issues);
    validateNoLocalPath(`${result.name ?? "unknown"} stderrTail`, result.stderrTail, issues);
  }
  for (const commandName of requiredFrontendStaticCommands) {
    const result = resultByName.get(commandName);
    if (!result) {
      issues.push(`missing frontend static command ${commandName}`);
      continue;
    }
    if (result.skipped) {
      issues.push(`${commandName} was skipped`);
    }
    if (result.status !== "PASS") {
      issues.push(`${commandName} status=${result.status}`);
    }
  }
  if ((artifact?.summary?.failed || 0) > 0) {
    issues.push(`failed=${artifact.summary.failed}`);
  }
  if ((artifact?.summary?.skipped || 0) > 0) {
    issues.push(`skipped=${artifact.summary.skipped}`);
  }
  if ((artifact?.summary?.passed || 0) < requiredFrontendStaticCommands.length) {
    issues.push(`expected lint/typecheck/unit to pass, passed=${artifact?.summary?.passed || 0}`);
  }
  return issues;
}

function validateFileMetadata(label, file, issues) {
  if (!file?.file) {
    issues.push(`${label} file is required`);
  }
  if (!Number.isFinite(file?.bytes) || file.bytes <= 0) {
    issues.push(`${label} bytes must be positive`);
  }
  if (!/^[a-f0-9]{64}$/i.test(file?.sha256 || "")) {
    issues.push(`${label} sha256 must be 64 hex characters`);
  }
  validateNoLocalPath(`${label} file`, file?.file, issues);
}

function validateNoLocalPath(label, value, issues) {
  if (typeof value !== "string" || value.length === 0) return;
  if (repoRoot && value.includes(repoRoot)) {
    issues.push(`${label} must not include local repo path`);
  }
  if (homeDir && homeDir !== "/" && value.includes(homeDir)) {
    issues.push(`${label} must not include local home path`);
  }
}
