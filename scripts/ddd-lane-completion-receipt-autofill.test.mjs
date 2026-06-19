#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-lane-receipt-autofill-"));
const autofillCommand = "node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>";

try {
  const receiptFile = path.join(tmpDir, "receipt.json");
  const intakeFile = path.join(tmpDir, "owner-evidence-intake.json");
  const outputFile = path.join(tmpDir, "autofill.json");

  fs.writeFileSync(receiptFile, `${JSON.stringify({
    status: "BLOCKED",
    redacted: true,
    laneReceipts: [
      {
        owner: "release-infra",
        lane: "p0-docker-images",
        status: "BLOCKED",
        providedArtifacts: [],
        missingArtifacts: [],
        evidenceNotes: [],
        completedAt: null,
        completedBy: null,
      },
      {
        owner: "release-infra",
        lane: "p0-release-env",
        status: "BLOCKED",
        providedArtifacts: [],
        missingArtifacts: [],
        evidenceNotes: [],
        completedAt: null,
        completedBy: null,
      },
    ],
  }, null, 2)}\n`);

  fs.writeFileSync(intakeFile, `${JSON.stringify({
    owners: [
      {
        owner: "release-infra",
        receiptFragments: [
          {
            key: "release-infra:p0-docker-images",
            status: "PASS",
            providedArtifacts: ["artifacts/ddd/build/docker-image-evidence.json"],
            missingArtifacts: [],
          },
          {
            key: "release-infra:p0-release-env",
            status: "BLOCKED",
            providedArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
            missingArtifacts: ["artifacts/ddd/release/release-env-lint.json"],
          },
        ],
      },
    ],
  }, null, 2)}\n`);

  const result = spawnSync("node", [
    "scripts/ddd-lane-completion-receipt-autofill.mjs",
    `--receipt-file=${receiptFile}`,
    `--owner-evidence-intake-file=${intakeFile}`,
    `--output=${outputFile}`,
    "--completed-by=test-runner",
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const summary = JSON.parse(result.stdout);
  assert.equal(summary.autofilledLaneCount, 1);
  assert.deepEqual(summary.autofilled, ["release-infra:p0-docker-images"]);
  assert.equal(summary.blockedLaneCount, 1);
  assert.equal(summary.receiptStatus, "BLOCKED");

  const autofilledReceipt = JSON.parse(fs.readFileSync(outputFile, "utf8"));
  const dockerLane = autofilledReceipt.laneReceipts.find((lane) => lane.lane === "p0-docker-images");
  assert.equal(dockerLane.status, "PASS");
  assert.deepEqual(dockerLane.providedArtifacts, ["artifacts/ddd/build/docker-image-evidence.json"]);
  assert.equal(dockerLane.completedBy, "test-runner");
  assert.match(dockerLane.evidenceNotes.join("\n"), /autofilled from owner-evidence-intake/);

  const releaseEnvLane = autofilledReceipt.laneReceipts.find((lane) => lane.lane === "p0-release-env");
  assert.equal(releaseEnvLane.status, "BLOCKED");
  assert.deepEqual(releaseEnvLane.providedArtifacts, []);
  assert.equal(releaseEnvLane.completedAt, null);

  const overwriteResult = spawnSync("node", [
    "scripts/ddd-lane-completion-receipt-autofill.mjs",
    `--receipt-file=${receiptFile}`,
    `--owner-evidence-intake-file=${intakeFile}`,
    `--output=${outputFile}`,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  assert.notEqual(overwriteResult.status, 0, "autofill should refuse to overwrite without --force");
  assert.match(overwriteResult.stderr, /refusing to overwrite/);

  const commandsResult = spawnSync("node", ["scripts/ddd-staging-execution-checklist.mjs", "--commands"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  assert.equal(commandsResult.status, 0, commandsResult.stderr || commandsResult.stdout);
  assert.match(commandsResult.stdout, new RegExp(`^${autofillCommand.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`, "m"));

  const submissionPlanResult = spawnSync("node", ["scripts/ddd-staging-execution-checklist.mjs", "--lane-completion-submission-plan"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  assert.equal(submissionPlanResult.status, 0, submissionPlanResult.stderr || submissionPlanResult.stdout);
  const submissionPlan = JSON.parse(submissionPlanResult.stdout);
  assert(submissionPlan.commands.includes(autofillCommand));

  const ownerEvidenceIntakeResult = spawnSync("node", ["scripts/ddd-staging-execution-checklist.mjs", "--owner-evidence-intake"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  assert.equal(ownerEvidenceIntakeResult.status, 0, ownerEvidenceIntakeResult.stderr || ownerEvidenceIntakeResult.stdout);
  const ownerEvidenceIntake = JSON.parse(ownerEvidenceIntakeResult.stdout);
  assert(ownerEvidenceIntake.owners.every((owner) => owner.receiptWorkflow.autofillCommand === autofillCommand));

  console.log("[ddd-lane-completion-receipt-autofill.test] ok");
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}
