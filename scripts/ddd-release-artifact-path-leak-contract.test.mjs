#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");

function write(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
}

function runContract(root, files, homeDir = "/Users/example", releaseEnvDisplayFiles = []) {
  return spawnSync("node", ["scripts/ddd-release-artifact-path-leak-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_RELEASE_EVIDENCE_DIR: root,
      DDD_RELEASE_PATH_LEAK_HOME: homeDir,
      DDD_RELEASE_PATH_LEAK_FILES: files.join(","),
      DDD_RELEASE_ENV_DISPLAY_FILES: releaseEnvDisplayFiles.join(","),
    },
  });
}

const passRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-path-leak-pass-"));
write(path.join(passRoot, "release", "release-evidence-gate.json"), JSON.stringify({
  detail: "Cannot connect to unix://~/.docker/run/docker.sock",
  report: "artifacts/ddd/release/release-evidence-gate.json",
}, null, 2));

const passResult = runContract(passRoot, ["release/release-evidence-gate.json"]);
assert.equal(passResult.status, 0, passResult.stderr || passResult.stdout);
assert.match(passResult.stdout, /\[ddd-release-artifact-path-leak-contract\] ok/);
const passReport = JSON.parse(fs.readFileSync(path.join(passRoot, "release", "release-artifact-path-leak-contract.json"), "utf8"));
assert.equal(passReport.status, "PASS");
assert.equal(passReport.leakCount, 0);

const releaseEnvPlaceholderRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-env-display-pass-"));
write(path.join(releaseEnvPlaceholderRoot, "release", "owner-action-rollup.md"), [
  "# Owner Rollup",
  "`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-evidence-gate.mjs --env-file <release-env-file>`",
  "",
].join("\n"));
const releaseEnvPlaceholderResult = runContract(
  releaseEnvPlaceholderRoot,
  [],
  "/Users/example",
  ["release/owner-action-rollup.md"],
);
assert.equal(releaseEnvPlaceholderResult.status, 0, releaseEnvPlaceholderResult.stderr || releaseEnvPlaceholderResult.stdout);
const releaseEnvPlaceholderReport = JSON.parse(fs.readFileSync(
  path.join(releaseEnvPlaceholderRoot, "release", "release-artifact-path-leak-contract.json"),
  "utf8",
));
assert.equal(releaseEnvPlaceholderReport.status, "PASS");
assert.equal(releaseEnvPlaceholderReport.releaseEnvDisplayScannedFiles, 1);

const releaseEnvFailRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-release-env-display-fail-"));
write(path.join(releaseEnvFailRoot, "release", "owner-action-rollup.md"), [
  "# Owner Rollup",
  "`DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-evidence-gate.mjs --env-file .env.release.local`",
  "",
].join("\n"));
const releaseEnvFailResult = runContract(
  releaseEnvFailRoot,
  [],
  "/Users/example",
  ["release/owner-action-rollup.md"],
);
assert.notEqual(releaseEnvFailResult.status, 0);
assert.match(releaseEnvFailResult.stderr, /release\/owner-action-rollup\.md:2:\d+ contains releaseEnvFile/);
const releaseEnvFailReportText = fs.readFileSync(
  path.join(releaseEnvFailRoot, "release", "release-artifact-path-leak-contract.json"),
  "utf8",
);
assert(!releaseEnvFailReportText.includes(".env.release.local"), "path leak contract report must not echo release env file names");
const releaseEnvFailReport = JSON.parse(releaseEnvFailReportText);
assert.equal(releaseEnvFailReport.status, "FAIL");
assert(releaseEnvFailReport.leakCount >= 1);
assert(releaseEnvFailReport.leaks.every((leak) => leak.type === "releaseEnvFile"));

const failRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-path-leak-fail-"));
write(path.join(failRoot, "release", "release-evidence-gate.json"), JSON.stringify({
  detail: `repo=${repoRoot}`,
  docker: "/Users/example/.docker/run/docker.sock",
}, null, 2));

const failResult = runContract(failRoot, ["release/release-evidence-gate.json"]);
assert.notEqual(failResult.status, 0);
assert.match(failResult.stderr, /contains repoRoot/);
assert.match(failResult.stderr, /contains homeDir/);
const failReportPath = path.join(failRoot, "release", "release-artifact-path-leak-contract.json");
const failReportText = fs.readFileSync(failReportPath, "utf8");
assert(!failReportText.includes(repoRoot), "path leak contract report must not echo the leaked repo path");
assert(!failReportText.includes("/Users/example"), "path leak contract report must not echo the leaked home path");
const failReport = JSON.parse(failReportText);
assert.equal(failReport.status, "FAIL");
assert(failReport.leakCount >= 2);
assert(failReport.leaks.some((leak) => leak.type === "homeDir"));
assert(failReport.leaks.some((leak) => leak.type === "repoRoot"));

const defaultRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-path-leak-default-"));
for (const file of [
  "release/release-evidence-gate.json",
  "release/readiness-summary.json",
  "release/readiness-summary.md",
  "release/release-final-go-no-go.json",
  "release/release-final-go-no-go.md",
  "release/release-config-owner-input-reconciliation.json",
  "release/release-owner-input-receipt.json",
  "release/release-owner-input-receipt.csv",
  "release/release-owner-input-receipt-items.csv",
  "release/release-owner-input-receipt-items.md",
  "release/release-owner-input-receipt.md",
  "release/release-unblock-brief.json",
  "release/release-unblock-brief.md",
  "release/evidence-manifest.json",
  "release/evidence-manifest-preflight.json",
  "release/orchestrator-report.json",
  "release/release-performance-baseline-closure.json",
  "release/release-performance-baseline-closure.md",
  "frontend/frontend-smoke.json",
  "performance/authenticated-runtime-baseline-promotion.json",
  "build/docker-image-evidence.json",
]) {
  write(path.join(defaultRoot, file), `{"file":"${file}","path":"artifacts/ddd/${file}"}\n`);
}
write(path.join(defaultRoot, "release", "release-env-owner-input-packet", "01-release-infra.json"), JSON.stringify({
  owner: "release-infra",
  validationCommand: "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
}, null, 2));
write(path.join(defaultRoot, "release", "release-env-owner-input-packet", "01-release-infra.md"), [
  "# DDD Release Env Owner Input Packet: release-infra",
  "",
  "`DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs`",
  "",
].join("\n"));
write(path.join(defaultRoot, "release", "release-owner-input-receipt-items", "01-release-infra.md"), [
  "# DDD Release Owner Input Receipt Items: release-infra",
  "",
  "- [ ] 1. `DB_PASSWORD` status=PLACEHOLDER; class=secret",
  "",
].join("\n"));
const defaultResult = spawnSync("node", ["scripts/ddd-release-artifact-path-leak-contract.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: defaultRoot,
    DDD_RELEASE_PATH_LEAK_HOME: "/Users/example",
  },
});
assert.equal(defaultResult.status, 0, defaultResult.stderr || defaultResult.stdout);
assert.match(defaultResult.stdout, /ok files=21/);
assert.match(defaultResult.stdout, /releaseEnvDisplayFiles=34/);
const defaultReport = JSON.parse(fs.readFileSync(path.join(defaultRoot, "release", "release-artifact-path-leak-contract.json"), "utf8"));
assert.equal(defaultReport.scannedFiles, 21);
assert.equal(defaultReport.releaseEnvDisplayScannedFiles, 34);
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-final-go-no-go.md")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-config-owner-input-reconciliation.json")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.json")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.csv")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt-items.csv")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt-items.md")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.md")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/release-unblock-brief.md")));
assert(defaultReport.scanned.some((entry) => entry.file.endsWith("release/evidence-manifest-preflight.json")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-env-owner-input-packet/01-release-infra.json")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-env-owner-input-packet/01-release-infra.md")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt-items/01-release-infra.md")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.json")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.csv")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt-items.csv")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt-items.md")));
assert(defaultReport.releaseEnvDisplayScanned.some((entry) => entry.file.endsWith("release/release-owner-input-receipt.md")));

const ownerPacketLeakRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-path-leak-owner-packet-fail-"));
write(path.join(ownerPacketLeakRoot, "release", "release-env-owner-input-packet", "01-release-infra.md"), [
  "# DDD Release Env Owner Input Packet: release-infra",
  "",
  "`DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`",
  "",
].join("\n"));
const ownerPacketLeakResult = runContract(ownerPacketLeakRoot, []);
assert.notEqual(ownerPacketLeakResult.status, 0);
assert.match(ownerPacketLeakResult.stderr, /release\/release-env-owner-input-packet\/01-release-infra\.md:3:\d+ contains releaseEnvFile/);
const ownerPacketLeakReportText = fs.readFileSync(
  path.join(ownerPacketLeakRoot, "release", "release-artifact-path-leak-contract.json"),
  "utf8",
);
assert(!ownerPacketLeakReportText.includes(".env.release.local"), "owner packet path leak report must not echo release env file names");

const markdownFailRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-path-leak-markdown-fail-"));
write(path.join(markdownFailRoot, "release", "release-final-go-no-go.md"), `# Final\nrepo=${repoRoot}\n`);
const markdownFail = runContract(markdownFailRoot, ["release/release-final-go-no-go.md"]);
assert.notEqual(markdownFail.status, 0);
assert.match(markdownFail.stderr, /release\/release-final-go-no-go\.md:2:\d+ contains repoRoot/);

console.log("[ddd-release-artifact-path-leak-contract.test] ok");
