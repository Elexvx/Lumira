#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const validationCommands = [
  "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env",
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh",
  "node scripts/ddd-release-readiness-summary.mjs",
  "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh",
];

function baseArtifacts() {
  const item = {
    fillOrder: 1,
    owner: "release-infra",
    owners: ["release-infra"],
    group: "runtime",
    requirement: "database password",
    canonicalKey: "DB_PASSWORD",
    status: "PLACEHOLDER",
    required: true,
    secret: true,
    valueClass: "secret",
    safeToPreFill: false,
    safeDefaultAvailable: false,
    requiresOwnerInput: true,
    ownerInputReason: "secret-manager",
    blocker: true,
    validation: {
      https: false,
      nonLocal: false,
      minLength: 16,
      expectedValues: [],
      pattern: null,
      disallowValues: [],
    },
    aliases: ["DB_PASSWORD", "MYSQL_PASSWORD"],
    fillGuidance: "Provide via approved secret manager or secure release channel; never commit.",
  };
  return {
    readiness: {
      redacted: true,
      valuePolicy: "No concrete environment values are emitted; only key names, ownership, validation metadata, and redacted fill status.",
      summary: { ownerCount: 1 },
      items: [item],
    },
    handoff: {
      redacted: true,
      valuePolicy: "No concrete environment values are emitted; only key names, ownership, validation metadata, and redacted fill status.",
      ownerCount: 1,
      blockerOwnerCount: 1,
      templateDir: "artifacts/ddd/release/release-env-owner-handoff-redacted",
      validationCommands,
      owners: [{
        owner: "release-infra",
        fileName: "01-release-infra.md",
        total: 1,
        blockers: 1,
        placeholders: 1,
        missing: 0,
        optionalEmpty: 0,
        secretKeys: 1,
        safeDefaultAvailable: 0,
        requiresOwnerInput: 1,
        ownerInputReasons: ["secret-manager"],
        keys: ["DB_PASSWORD"],
        handoffPath: "artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
        postFillCommands: validationCommands,
      }],
    },
    csv: [
      "owner,handoffPath,ownerTotalKeys,ownerBlockers,ownerPlaceholders,ownerMissing,ownerOptionalEmpty,ownerSecretKeys,ownerSafeDefaultAvailable,ownerRequiresOwnerInput,ownerInputReasons,nextCommand,canonicalKey,status,required,secret,blocker,valueClass,safeToPreFill,safeDefaultAvailable,requiresOwnerInput,ownerInputReason,fillGuidance,validationHttps,validationNonLocal,validationMinLength,validationExpectedValues",
      "release-infra,artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md,1,1,1,0,0,1,0,1,secret-manager,,DB_PASSWORD,PLACEHOLDER,true,true,true,secret,false,false,true,secret-manager,Provide via approved secret manager or secure release channel; never commit.,false,false,16,",
      "",
    ].join("\n"),
    markdown: [
      "# DDD Release Env Owner Handoff Redacted",
      "",
      "## Validation Commands",
      "",
      ...validationCommands.map((command) => `- \`${command}\``),
      "",
      "- release-infra: blockers=1, placeholders=1, secretKeys=1, file=artifacts/ddd/release/release-env-owner-handoff-redacted/01-release-infra.md",
      "",
    ].join("\n"),
    ownerMarkdown: [
      "# DDD Release Env Owner Handoff: release-infra",
      "",
      "Concrete values are intentionally omitted from this artifact.",
      "- `DB_PASSWORD`: status=PLACEHOLDER; class=secret; secret=true; required=true; blocker=true; safeDefaultAvailable=false; requiresOwnerInput=true; reason=secret-manager; https=false; nonLocal=false; minLength=16; expectedValues=none",
      "  guidance: Provide via approved secret manager or secure release channel; never commit.",
      "",
      "## After Filling",
      "",
      ...validationCommands.map((command) => `- \`${command}\``),
      "",
    ].join("\n"),
  };
}

function writeArtifacts(directory, artifacts) {
  fs.writeFileSync(path.join(directory, "release-env-readiness-redacted.json"), `${JSON.stringify(artifacts.readiness, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-handoff-redacted.json"), `${JSON.stringify(artifacts.handoff, null, 2)}\n`);
  fs.writeFileSync(path.join(directory, "release-env-owner-handoff-redacted.csv"), artifacts.csv);
  fs.writeFileSync(path.join(directory, "release-env-owner-handoff-redacted.md"), artifacts.markdown);
  const handoffDir = path.join(directory, "release-env-owner-handoff-redacted");
  fs.mkdirSync(handoffDir, { recursive: true });
  fs.writeFileSync(path.join(handoffDir, "01-release-infra.md"), artifacts.ownerMarkdown);
}

function runContract(mutator = () => {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-env-owner-handoff-redacted-contract-"));
  const artifacts = baseArtifacts();
  mutator(artifacts);
  writeArtifacts(directory, artifacts);
  const result = spawnSync("node", ["scripts/ddd-release-env-owner-handoff-redacted-contract.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_DIR: directory },
  });
  const reportPath = path.join(directory, "release-env-owner-handoff-redacted-contract.json");
  return {
    ...result,
    directory,
    reportPath,
    report: fs.existsSync(reportPath) ? JSON.parse(fs.readFileSync(reportPath, "utf8")) : null,
  };
}

const passResult = runContract();
assert.equal(passResult.status, 0, passResult.stderr);
assert.match(passResult.stdout, /owners=1/);
assert.equal(passResult.report.status, "PASS");
assert.equal(passResult.report.issueCount, 0);
assert.deepEqual(passResult.report.expectedMarkdownFiles, ["01-release-infra.md"]);

const leakResult = runContract((artifacts) => {
  artifacts.ownerMarkdown += "DB_PASSWORD=super-secret\n";
});
assert.notEqual(leakResult.status, 0);
assert.match(leakResult.stderr, /must not expose/);
assert.equal(leakResult.report.status, "FAIL");
assert(leakResult.report.issueCount > 0);

const badPathResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].handoffPath = "../secret.md";
});
assert.notEqual(badPathResult.status, 0);
assert.match(badPathResult.stderr, /handoffPath/);

const missingFileResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].fileName = "01-missing.md";
  artifacts.handoff.owners[0].handoffPath = "artifacts/ddd/release/release-env-owner-handoff-redacted/01-missing.md";
});
assert.notEqual(missingFileResult.status, 0);
assert.match(missingFileResult.stderr, /markdown file|directory markdown/);

const statsMismatchResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].blockers = 0;
});
assert.notEqual(statsMismatchResult.status, 0);
assert.match(statsMismatchResult.stderr, /blockers/);

const missingKeyResult = runContract((artifacts) => {
  artifacts.handoff.owners[0].keys = [];
});
assert.notEqual(missingKeyResult.status, 0);
assert.match(missingKeyResult.stderr, /keys/);

const unsafeConcreteCommandResult = runContract((artifacts) => {
  const unsafeCommand = "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs";
  artifacts.handoff.validationCommands = [unsafeCommand];
  artifacts.handoff.owners[0].postFillCommands = [unsafeCommand];
  artifacts.markdown += `- \`${unsafeCommand}\`\n`;
  artifacts.ownerMarkdown += `- \`${unsafeCommand}\`\n`;
});
assert.notEqual(unsafeConcreteCommandResult.status, 0);
assert.match(unsafeConcreteCommandResult.stderr, /validationCommands|postFillCommands|validation command/);

console.log("[ddd-release-env-owner-handoff-redacted-contract.test] ok");
