#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const releaseDir = path.join(repoRoot, "artifacts", "ddd", "release");
const gatePath = path.join(releaseDir, "release-artifact-integrity-gate.sh");
const packetPath = path.join(releaseDir, "release-artifact-integrity.json");
const failures = [];

function bashPath(file) {
  const resolved = path.resolve(file);
  if (process.platform !== "win32") return resolved;
  return `/mnt/${resolved[0].toLowerCase()}${resolved.slice(2).replaceAll("\\", "/")}`;
}

function bashPathEnv() {
  if (process.platform !== "win32") return process.env.PATH;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-wsl-node-"));
  fs.writeFileSync(path.join(dir, "node"), `#!/usr/bin/env bash
args=()
for arg in "$@"; do
  if [[ "$arg" =~ ^/mnt/([a-zA-Z])/(.*)$ ]]; then
    drive="\${BASH_REMATCH[1]^^}:"
    rest="\${BASH_REMATCH[2]//\\//\\\\}"
    args+=("\${drive}\\\\\${rest}")
  else
    args+=("$arg")
  fi
done
exec "${bashPath(process.execPath)}" "\${args[@]}"
`);
  fs.chmodSync(path.join(dir, "node"), 0o755);
  return `${bashPath(dir)}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`;
}

const bashEnvPath = bashPathEnv();

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

function addFailure(message) {
  failures.push(message);
}

function runGate(packetFile) {
  if (process.platform === "win32") {
    const command = [
      `export PATH=${shellQuote(bashEnvPath)}`,
      `export LUMIRA_REPO_ROOT=${shellQuote(bashPath(repoRoot))}`,
      `export DDD_NODE_BIN=node`,
      `export DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET=${shellQuote(bashPath(packetFile))}`,
      shellQuote(bashPath(gatePath)),
    ].join("; ");
    return spawnSync("bash", ["-lc", command], {
      cwd: repoRoot,
      encoding: "utf8",
    });
  }
  return spawnSync("bash", [bashPath(gatePath)], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      PATH: bashEnvPath,
      LUMIRA_REPO_ROOT: bashPath(repoRoot),
      DDD_NODE_BIN: bashPath(process.execPath),
      DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET: bashPath(packetFile),
    },
  });
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

if (!fs.existsSync(gatePath)) {
  addFailure(`release artifact integrity gate script must exist: ${gatePath}`);
} else {
  const mode = fs.statSync(gatePath).mode & 0o777;
  if (process.platform !== "win32" && (mode & 0o111) === 0) addFailure("release artifact integrity gate script must be executable");
  const source = fs.readFileSync(gatePath, "utf8");
  for (const snippet of [
    "set -euo pipefail",
    "DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET",
    "Release artifact integrity packet does not exist",
    "exit 12",
    "packet.algorithm !== 'sha256'",
    "packet.redacted !== true",
    "allowedStatuses",
    "entries-empty",
    "duplicate-path",
    "packet.selfExcluded !== true",
    "crypto.createHash('sha256')",
    "stat.size !== entry.bytes",
    "executable !== entry.executable",
    "sha256 !== entry.sha256",
    "packet.artifactCount !== entries.length",
    "packet.totalBytes !== totalBytes",
  ]) {
    if (!source.includes(snippet)) addFailure(`release artifact integrity gate script must include ${snippet}`);
  }
}

const syntax = spawnSync("bash", ["-n", bashPath(gatePath)], { cwd: repoRoot, encoding: "utf8" });
if (syntax.status !== 0) addFailure(`release artifact integrity gate bash syntax must pass: ${syntax.stderr}`);

if (!fs.existsSync(packetPath)) {
  addFailure(`release artifact integrity packet must exist: ${packetPath}`);
} else {
  const realRun = runGate(packetPath);
  if (realRun.status !== 0) addFailure(`real artifact integrity gate must pass: ${realRun.stderr || realRun.stdout}`);
  if (!realRun.stdout.includes("[ddd-release-artifact-integrity] ok")) addFailure("real artifact integrity gate must print ok");
}

const missingRun = runGate(path.join(os.tmpdir(), "lumira-missing-integrity-packet.json"));
if (missingRun.status !== 12) addFailure(`missing integrity packet must exit 12, got ${missingRun.status}`);

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-artifact-integrity-gate-contract-"));
try {
  const artifactFile = path.join(tmpDir, "artifact.txt");
  fs.writeFileSync(artifactFile, "stable release artifact\n");
  const packetFile = path.join(tmpDir, "packet.json");
  const entry = {
    name: "tmpArtifact",
    path: artifactFile,
    bytes: fs.statSync(artifactFile).size,
    executable: false,
    sha256: sha256(artifactFile),
  };
  const packet = {
    generatedAt: "2026-06-15T00:00:00.000Z",
    status: "ADVISORY",
    redacted: true,
    algorithm: "sha256",
    selfExcluded: true,
    artifactCount: 1,
    totalBytes: entry.bytes,
    entries: [entry],
  };
  fs.writeFileSync(packetFile, `${JSON.stringify(packet, null, 2)}\n`);
  const validRun = runGate(packetFile);
  if (validRun.status !== 0) addFailure(`temporary valid integrity packet must pass: ${validRun.stderr || validRun.stdout}`);

  fs.appendFileSync(artifactFile, "tampered\n");
  const tamperedRun = runGate(packetFile);
  if (tamperedRun.status !== 12) addFailure(`tampered artifact must exit 12, got ${tamperedRun.status}`);
  if (!tamperedRun.stderr.includes("sha256:") && !tamperedRun.stderr.includes("size:")) {
    addFailure("tampered artifact failure must mention sha256 or size");
  }

  fs.writeFileSync(packetFile, `${JSON.stringify({ ...packet, algorithm: "md5" }, null, 2)}\n`);
  const invalidAlgorithmRun = runGate(packetFile);
  if (invalidAlgorithmRun.status !== 12) addFailure(`invalid algorithm must exit 12, got ${invalidAlgorithmRun.status}`);
} finally {
  fs.rmSync(tmpDir, { recursive: true, force: true });
}

if (failures.length > 0) {
  throw new Error(`release artifact integrity gate contract failed: ${failures.join("; ")}`);
}

console.log("[ddd-release-artifact-integrity-gate-contract] ok");
