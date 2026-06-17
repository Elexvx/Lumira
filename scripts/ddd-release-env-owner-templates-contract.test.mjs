#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");

function baseArtifacts() {
  const owner = {
    owner: "release-infra",
    queueOrder: 1,
    queueStatus: "ACTIONABLE",
    canExecute: true,
    canonicalFillItemCount: 2,
    secretCanonicalKeyCount: 1,
    safeToPreFillCanonicalKeyCount: 1,
    canonicalKeys: ["DB_PASSWORD", "TRUST_FORWARDED_HEADERS"],
    secretCanonicalKeys: ["DB_PASSWORD"],
    safeToPreFillCanonicalKeys: ["TRUST_FORWARDED_HEADERS"],
    postFillCommands: [
      "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
      "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
      "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
    ],
  };
  return {
    canonicalFill: {
      envFile: ".env.release.local",
      canonicalFillItemCount: 2,
      items: [
        {
          owner: "release-infra",
          canonicalKey: "DB_PASSWORD",
          required: true,
          secret: true,
          safeToPreFill: false,
          validation: { expectedValues: [] },
        },
        {
          owner: "release-infra",
          canonicalKey: "TRUST_FORWARDED_HEADERS",
          required: true,
          secret: false,
          safeToPreFill: true,
          validation: { expectedValues: ["true"] },
        },
      ],
    },
    handoff: {
      envFile: ".env.release.local",
      ownerCount: 1,
      canonicalFillItemCount: 2,
      owners: [owner],
    },
    templates: {
      envFile: ".env.release.local",
      ownerCount: 1,
      canonicalFillItemCount: 2,
      secretCanonicalKeyCount: 1,
      safeToPreFillCanonicalKeyCount: 1,
      templateDir: "artifacts/ddd/release/release-env-owner-templates",
      owners: [{
        ...owner,
        templatePath: "artifacts/ddd/release/release-env-owner-templates/01-release-infra.env",
        fileName: "01-release-infra.env",
      }],
    },
    markdown: [
      "# DDD Release Env Owner Templates",
      "",
      "Each owner template is intentionally scoped to one owner so release values can be collected in parallel without sharing unrelated secrets.",
      "- Template: `artifacts/ddd/release/release-env-owner-templates/01-release-infra.env`",
      "- `node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env`",
      "- `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`",
      "- `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh`",
      "",
    ].join("\n"),
    templateText: [
      "# Lumira DDD owner-scoped canonical release environment template.",
      "# Do not commit populated secrets.",
      "# Owner: release-infra",
      "DB_PASSWORD=__REQUIRED__",
      "TRUST_FORWARDED_HEADERS=true",
      "",
    ].join("\n"),
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-canonical-fill.json"), `${JSON.stringify(artifacts.canonicalFill, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-handoff.json"), `${JSON.stringify(artifacts.handoff, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-templates.json"), `${JSON.stringify(artifacts.templates, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-templates.md"), artifacts.markdown);
  const templatesDir = path.join(directory, "release-env-owner-templates");
  fs.mkdirSync(templatesDir, { recursive: true });
  fs.writeFileSync(path.join(templatesDir, "01-release-infra.env"), artifacts.templateText);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-owner-templates-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  return spawnSync("node", ["scripts/ddd-release-env-owner-templates-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=1/);

const secretPrefillResult = runContract((artifacts) => {
  artifacts.templateText = artifacts.templateText.replace("DB_PASSWORD=__REQUIRED__", "DB_PASSWORD=real-secret");
});
assert.notEqual(secretPrefillResult.status, 0);
assert.match(secretPrefillResult.stderr, /DB_PASSWORD/);

const missingKeyResult = runContract((artifacts) => {
  artifacts.templateText = artifacts.templateText.replace("TRUST_FORWARDED_HEADERS=true\n", "");
});
assert.notEqual(missingKeyResult.status, 0);
assert.match(missingKeyResult.stderr, /template env keys/);

const badPathResult = runContract((artifacts) => {
  artifacts.templates.owners[0].templatePath = "../release.env";
});
assert.notEqual(badPathResult.status, 0);
assert.match(badPathResult.stderr, /templatePath/);

const commandMismatchResult = runContract((artifacts) => {
  artifacts.templates.owners[0].postFillCommands = [];
});
assert.notEqual(commandMismatchResult.status, 0);
assert.match(commandMismatchResult.stderr, /postFillCommands/);

const implicitLintResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].postFillCommands = [
    "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
    "node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  ];
  artifacts.templates.owners[0].postFillCommands = artifacts.handoff.owners[0].postFillCommands;
  artifacts.markdown = artifacts.markdown.replace(
    "- `DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs`",
    "- `node scripts/ddd-release-env-file-lint.mjs`",
  );
});
assert.notEqual(implicitLintResult.status, 0);
assert.match(implicitLintResult.stderr, /explicit release env file/);

const ownerMismatchResult = runContract((artifacts) => {
  artifacts.canonicalFill.items[0].owner = "ai-owner";
});
assert.notEqual(ownerMismatchResult.status, 0);
assert.match(ownerMismatchResult.stderr, /canonical owner/);

console.log("[ddd-release-env-owner-templates-contract.test] ok");
