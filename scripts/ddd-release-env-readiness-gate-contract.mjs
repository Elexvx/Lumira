#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const releaseDir = process.env.DDD_RELEASE_DIR || "artifacts/ddd/release";
const gatePath = path.join(releaseDir, "release-env-readiness-gate.sh");
const packetPath = path.join(releaseDir, "release-env-readiness-redacted.json");

function readText(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env readiness gate artifact: ${file}`);
  }
  return fs.readFileSync(file, "utf8");
}

function readJson(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`missing release env readiness gate artifact: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const gate = readText(gatePath);
const packet = readJson(packetPath);
const failures = [];

function addFailure(message) {
  failures.push(message);
}

const requiredSnippets = [
  "#!/usr/bin/env bash",
  "set -euo pipefail",
  "DDD_RELEASE_ENV_READINESS_PACKET",
  "DDD_RELEASE_ENV_READINESS_ENFORCE",
  "Exit codes: 21 means release env values are unresolved; 22 means the redacted readiness packet is invalid.",
  "packet.redacted !== true",
  "items must be an array",
  "byOwner must be an array",
  "invalid-counts",
  "invalid-owner-counts",
  "process.exit(22)",
  "process.exit(21)",
  "unresolved release env values remain",
  "release-env-owner-handoff-redacted.md",
  "release-env-owner-handoff-redacted.csv",
];

for (const snippet of requiredSnippets) {
  if (!gate.includes(snippet)) addFailure(`readiness gate must include ${snippet}`);
}
if (/\bsource\s+/.test(gate)) addFailure("readiness gate must not source env files");
if (!packet.redacted || !packet.summary || !Array.isArray(packet.items) || !Array.isArray(packet.byOwner)) {
  addFailure("readiness packet must be redacted and include summary/items/byOwner");
}

function runGate(env = {}) {
  return spawnSync("bash", [gatePath], {
    cwd: path.resolve(releaseDir, "..", "..", ".."),
    encoding: "utf8",
    env: { ...process.env, DDD_RELEASE_ENV_READINESS_PACKET: packetPath, ...env },
  });
}

const syntax = spawnSync("bash", ["-n", gatePath], { encoding: "utf8" });
if (syntax.status !== 0) addFailure(`readiness gate bash syntax failed: ${syntax.stderr}`);

const defaultRun = runGate();
if (defaultRun.status !== 0) addFailure(`default readiness gate must not fail without enforce: status=${defaultRun.status}`);
if (!/ddd-release-env-readiness.*blockers=\d+/.test(defaultRun.stdout)) addFailure("default readiness gate must print blocker summary");
if (!/exitCodes unresolved=21 invalidPacket=22/.test(defaultRun.stdout)) addFailure("default readiness gate must print exit code summary");

const enforceRun = runGate({ DDD_RELEASE_ENV_READINESS_ENFORCE: "1" });
const unresolved = Number(packet.summary?.blockers || 0) + Number(packet.summary?.missing || 0) + Number(packet.summary?.placeholders || 0);
if (unresolved > 0) {
  if (enforceRun.status !== 21) addFailure(`enforced readiness gate must exit 21 while unresolved values remain: status=${enforceRun.status}`);
  if (!/unresolved release env values remain/.test(enforceRun.stderr)) addFailure("enforced readiness gate must explain unresolved values");
} else if (enforceRun.status !== 0) {
  addFailure(`enforced readiness gate must pass when no unresolved values remain: status=${enforceRun.status}`);
}

const invalidPacketPath = path.join(releaseDir, "release-env-readiness-contract-invalid.json");
fs.writeFileSync(invalidPacketPath, `${JSON.stringify({ status: "NOT_READY", summary: {} }, null, 2)}\n`);
try {
  const invalidRun = runGate({ DDD_RELEASE_ENV_READINESS_PACKET: invalidPacketPath });
  if (invalidRun.status !== 22) addFailure(`invalid readiness packet must exit 22: status=${invalidRun.status}`);
  if (!/invalid-packet/.test(invalidRun.stderr)) addFailure("invalid readiness packet must print invalid-packet");
} finally {
  fs.rmSync(invalidPacketPath, { force: true });
}

if (failures.length > 0) {
  throw new Error(`release env readiness gate contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-env-readiness-gate-contract] ok unresolved=${unresolved}`);
