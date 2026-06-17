#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-physical-split-"));
const artifactFile = path.join(directory, "physical-split-readiness.json");

fs.writeFileSync(artifactFile, `${JSON.stringify({
  generatedAt: "2026-06-14T00:00:00.000Z",
  sourceEnvironment: "staging-prod-equivalent",
  releaseCandidate: "rc-20260614",
  evidenceOperator: "release-owner",
  strict: false,
  summary: {
    contexts: 0,
    failures: 0,
    blockers: 0,
    warnings: 0,
  },
  contexts: [],
  globalChecks: [],
}, null, 2)}\n`);

const result = spawnSync("node", ["scripts/ddd-physical-split-gate.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_SPLIT_READINESS_FILE: artifactFile,
    DDD_SPLIT_ENVIRONMENT: "",
    DDD_EVIDENCE_ENVIRONMENT: "",
    DDD_RELEASE_ENVIRONMENT: "",
    DDD_RELEASE_CANDIDATE: "",
    GITHUB_SHA: "",
    DDD_EVIDENCE_OPERATOR: "",
    GITHUB_ACTOR: "",
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);
const artifact = JSON.parse(fs.readFileSync(artifactFile, "utf8"));
assert.equal(artifact.sourceEnvironment, "staging-prod-equivalent");
assert.equal(artifact.releaseCandidate, "rc-20260614");
assert.equal(artifact.evidenceOperator, "release-owner");
assert.equal(artifact.summary.contexts, 10);
assert.equal(artifact.summary.blockers, 0);

{
  const strictArtifactFile = path.join(directory, "physical-split-readiness-strict.json");
  const strictGeneratedAt = "2026-06-14T01:00:00.000Z";
  fs.writeFileSync(strictArtifactFile, `${JSON.stringify({
    generatedAt: strictGeneratedAt,
    sourceEnvironment: "staging-prod-equivalent",
    releaseCandidate: "rc-20260614",
    evidenceOperator: "release-owner",
    strict: true,
    summary: {
      contexts: 10,
      failures: 0,
      blockers: 0,
      warnings: 0,
    },
    contexts: [],
    globalChecks: [],
  }, null, 2)}\n`);

  const advisoryResult = spawnSync("node", ["scripts/ddd-physical-split-gate.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_SPLIT_READINESS_FILE: strictArtifactFile,
      DDD_SPLIT_STRICT: "",
      DDD_SPLIT_ENVIRONMENT: "",
      DDD_EVIDENCE_ENVIRONMENT: "",
      DDD_RELEASE_ENVIRONMENT: "",
      DDD_RELEASE_CANDIDATE: "",
      GITHUB_SHA: "",
      DDD_EVIDENCE_OPERATOR: "",
      GITHUB_ACTOR: "",
    },
  });

  assert.equal(advisoryResult.status, 0, advisoryResult.stderr || advisoryResult.stdout);
  const preserved = JSON.parse(fs.readFileSync(strictArtifactFile, "utf8"));
  const advisory = JSON.parse(fs.readFileSync(path.join(directory, "physical-split-readiness-strict.advisory.json"), "utf8"));
  assert.equal(preserved.strict, true);
  assert.equal(preserved.generatedAt, strictGeneratedAt);
  assert.equal(advisory.strict, false);
  assert.match(advisoryResult.stdout, /preserved existing strict release artifact/);
}

console.log("[ddd-physical-split-gate.test] ok");
