#!/usr/bin/env node

import assert from "node:assert/strict";
import path from "node:path";
import {
  requiredFrontendStaticCommands,
  validateFrontendBuildArtifact,
  validateFrontendStaticArtifact,
} from "./ddd-frontend-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function buildArtifact(overrides = {}) {
  return {
    status: "PASS",
    build: {
      skipped: false,
      status: "PASS",
      command: "corepack pnpm --dir frontend build",
      exitCode: 0,
      durationMs: 1,
    },
    summary: {
      files: 10,
      assets: 5,
      totalBytes: 1000,
      indexHtmlPresent: true,
    },
    entrypoint: {
      file: "frontend/dist/index.html",
      bytes: 100,
      sha256: "a".repeat(64),
    },
    largestFiles: [
      { file: "frontend/dist/umi.js", bytes: 600, sha256: "b".repeat(64) },
      { file: "frontend/dist/umi.css", bytes: 400, sha256: "c".repeat(64) },
    ],
    blockers: [],
    ...overrides,
  };
}

function staticArtifact(overrides = {}) {
  return {
    status: "PASS",
    summary: {
      commands: requiredFrontendStaticCommands.length,
      passed: requiredFrontendStaticCommands.length,
      failed: 0,
      skipped: 0,
    },
    results: requiredFrontendStaticCommands.map((name) => ({
      name,
      command: `corepack pnpm --dir frontend ${name === "unit" ? "test" : name}`,
      skipped: false,
      status: "PASS",
      exitCode: 0,
      durationMs: 1,
    })),
    blockers: [],
    ...overrides,
  };
}

assert.deepEqual(validateFrontendBuildArtifact(buildArtifact()), []);
assert.deepEqual(validateFrontendStaticArtifact(staticArtifact()), []);

assert.deepEqual(validateFrontendBuildArtifact(buildArtifact({
  status: "FAIL",
  build: { skipped: true, status: "SKIPPED" },
  summary: { files: 0, assets: 0, totalBytes: 0, indexHtmlPresent: false },
  entrypoint: null,
  largestFiles: [],
  blockers: ["frontend build failed"],
})), [
  "status=FAIL",
  "frontend production build was skipped",
  "frontend production build status=SKIPPED",
  "frontend production build command is required",
  "frontend production build durationMs must be non-negative",
  "invalid dist output: files=0, assets=0",
  "invalid dist totalBytes=0",
  "missing dist index.html",
]);

assert(
  validateFrontendStaticArtifact(staticArtifact({
    results: requiredFrontendStaticCommands
      .filter((name) => name !== "unit")
      .map((name) => ({
        name,
        command: `corepack pnpm --dir frontend ${name}`,
        skipped: false,
        status: "PASS",
        exitCode: 0,
        durationMs: 1,
      })),
    summary: { passed: 2, failed: 0, skipped: 0 },
  })).includes("missing frontend static command unit"),
);

assert.deepEqual(validateFrontendStaticArtifact(staticArtifact({
  status: "FAIL",
  summary: { commands: 3, passed: 1, failed: 1, skipped: 1 },
  results: [
    { name: "lint", command: "corepack pnpm --dir frontend lint", skipped: false, status: "PASS", exitCode: 0, durationMs: 1 },
    { name: "typecheck", command: "corepack pnpm --dir frontend typecheck", skipped: false, status: "FAIL", exitCode: 1, durationMs: 1 },
    { name: "unit", command: "corepack pnpm --dir frontend test", skipped: true, status: "SKIPPED", exitCode: null, durationMs: 0 },
  ],
})), [
  "status=FAIL",
  "typecheck status=FAIL",
  "unit was skipped",
  "unit status=SKIPPED",
  "failed=1",
  "skipped=1",
  "expected lint/typecheck/unit to pass, passed=1",
]);

{
  const issues = validateFrontendStaticArtifact(staticArtifact({
    status: "PASS",
    summary: {
      commands: 0,
      passed: 3,
      failed: 0,
      skipped: 0,
    },
    results: [
      { name: "lint", command: "corepack pnpm --dir frontend lint", skipped: false, status: "PASS", exitCode: 0, durationMs: 1 },
      { name: "typecheck", command: "corepack pnpm --dir frontend typecheck", skipped: false, status: "FAIL", exitCode: 1, durationMs: 1 },
      { name: "unit", command: "corepack pnpm --dir frontend test", skipped: true, status: "SKIPPED", exitCode: null, durationMs: 0 },
    ],
  }));
  assert(issues.includes("frontend static status must be FAIL, got PASS"));
  assert(issues.includes("frontend static summary commands mismatch: declared=0, actual=3"));
  assert(issues.includes("frontend static summary passed mismatch: declared=3, actual=1"));
  assert(issues.includes("frontend static summary failed mismatch: declared=0, actual=1"));
  assert(issues.includes("frontend static summary skipped mismatch: declared=0, actual=1"));
}

{
  const issues = validateFrontendBuildArtifact(buildArtifact({
    entrypoint: { file: "frontend/dist/app.html", bytes: 0, sha256: "bad" },
    largestFiles: [
      { file: "frontend/dist/a.js", bytes: 1, sha256: "d".repeat(64) },
      { file: "frontend/dist/b.js", bytes: 2, sha256: "e".repeat(64) },
      { file: "frontend/dist/b.js", bytes: 1, sha256: "bad" },
    ],
  }));
  assert(issues.includes("entrypoint bytes must be positive"));
  assert(issues.includes("entrypoint sha256 must be 64 hex characters"));
  assert(issues.includes("entrypoint file must be frontend/dist/index.html, got frontend/dist/app.html"));
  assert(issues.includes("largestFiles must be sorted by bytes descending at index 1"));
  assert(issues.includes("duplicate frontend build file metadata frontend/dist/b.js"));
  assert(issues.includes("largestFiles[2] sha256 must be 64 hex characters"));
}

{
  const issues = validateFrontendBuildArtifact(buildArtifact({
    build: {
      skipped: false,
      status: "PASS",
      command: "corepack pnpm --dir frontend build",
      exitCode: 0,
      durationMs: 1,
      stdoutTail: `built in ${repoRoot}/frontend`,
      stderrTail: "",
    },
  }));
  assert(issues.includes("frontend production build stdoutTail must not include local repo path"));
}

{
  const issues = validateFrontendStaticArtifact(staticArtifact({
    summary: { commands: 4, passed: 3, failed: 1, skipped: 0, durationMs: 4 },
    results: [
      { name: "lint", command: "", skipped: false, status: "PASS", exitCode: 0, durationMs: 1 },
      { name: "lint", command: "corepack pnpm --dir frontend lint", skipped: false, status: "PASS", exitCode: 0, durationMs: 1 },
      { name: "typecheck", command: "corepack pnpm --dir frontend typecheck", skipped: false, status: "BROKEN", durationMs: -1 },
      { name: "preview", command: "corepack pnpm --dir frontend preview", skipped: false, status: "FAIL", exitCode: 1, durationMs: 1 },
    ],
  }));
  assert(issues.includes("duplicate frontend static command lint"));
  assert(issues.includes("unknown frontend static command preview"));
  assert(issues.includes("lint command is required"));
  assert(issues.includes("typecheck status=BROKEN"));
  assert(issues.includes("typecheck exitCode must be a number when not skipped"));
  assert(issues.includes("typecheck durationMs must be non-negative"));
  assert(issues.includes("missing frontend static command unit"));
}

{
  const artifact = staticArtifact();
  artifact.results[0].stdoutTail = `linted ${repoRoot}/frontend/src/app.ts`;
  const issues = validateFrontendStaticArtifact(artifact);
  assert(issues.includes("lint stdoutTail must not include local repo path"));
}

console.log("[ddd-frontend-evidence-contract.test] ok");
