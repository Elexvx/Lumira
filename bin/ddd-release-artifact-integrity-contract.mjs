#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const packetPath = process.env.DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET
  || "artifacts/ddd/release/release-artifact-integrity.json";
const pathLeakReportPath = process.env.DDD_RELEASE_PATH_LEAK_REPORT
  || path.join(path.dirname(packetPath), "release-artifact-path-leak-contract.json");

if (!fs.existsSync(packetPath)) {
  throw new Error(`missing release artifact integrity packet: ${packetPath}`);
}

const packet = JSON.parse(fs.readFileSync(packetPath, "utf8"));
const failures = [];
const entries = Array.isArray(packet.entries) ? packet.entries : [];
const releaseRoot = "artifacts/ddd/release/";
const packetDirectory = path.dirname(packetPath);
const ownerInputPacketPath = path.join(packetDirectory, "release-env-owner-input-packet.json");
const allowedStatuses = new Set(["ADVISORY", "PASS", "FAIL", "NOT_READY"]);
const requiredPaths = [
  "artifacts/ddd/release/release-artifact-integrity-gate.sh",
  "artifacts/ddd/release/release-env-readiness-gate.sh",
  "artifacts/ddd/release/release-final-go-no-go-gate.sh",
  "artifacts/ddd/release/release-preflight-gate.sh",
  "artifacts/ddd/release/release-final-go-no-go.json",
  "artifacts/ddd/release/release-final-go-no-go.md",
  "artifacts/ddd/release/release-config-owner-input-reconciliation.json",
];

function addFailure(message) {
  failures.push(message);
}

if (packet.algorithm !== "sha256") addFailure("algorithm must be sha256");
if (!packet.generatedAt || Number.isNaN(Date.parse(packet.generatedAt))) {
  addFailure("generatedAt must be an ISO-like datetime");
}
if (!allowedStatuses.has(packet.status)) {
  addFailure(`status must be one of ${[...allowedStatuses].join(",")}`);
}
if (packet.redacted !== true) addFailure("packet must be redacted");
if (packet.selfExcluded !== true) addFailure("packet must self-exclude");
if (!Array.isArray(packet.entries)) addFailure("entries must be an array");
if (entries.length === 0) addFailure("entries must not be empty");
if (!Number.isInteger(packet.artifactCount) || packet.artifactCount !== entries.length) {
  addFailure("artifactCount must match entries length");
}

const seenNames = new Set();
const seenPaths = new Set();
let totalBytes = 0;
for (const entry of entries) {
  if (!entry || typeof entry !== "object") {
    addFailure("entry must be an object");
    continue;
  }
  const name = String(entry.name || "");
  const artifactPath = String(entry.path || "");
  if (!name) addFailure("entry name is required");
  if (!artifactPath) addFailure(`entry path is required for ${name || "unknown"}`);
  if (seenNames.has(name)) addFailure(`duplicate entry name: ${name}`);
  if (seenPaths.has(artifactPath)) addFailure(`duplicate entry path: ${artifactPath}`);
  seenNames.add(name);
  seenPaths.add(artifactPath);

  if (path.isAbsolute(artifactPath)) addFailure(`absolute path is forbidden: ${artifactPath}`);
  if (!artifactPath.startsWith(releaseRoot)) addFailure(`path must stay under ${releaseRoot}: ${artifactPath}`);
  if (artifactPath.split(/[\\/]/).includes("..")) addFailure(`path traversal is forbidden: ${artifactPath}`);
  if (artifactPath === packetPath) addFailure("integrity packet must not include itself");
  if (!/^[a-f0-9]{64}$/.test(String(entry.sha256 || ""))) {
    addFailure(`invalid sha256 for ${artifactPath || name || "unknown"}`);
  }
  if (!Number.isInteger(entry.bytes) || entry.bytes < 0) {
    addFailure(`invalid byte size for ${artifactPath || name || "unknown"}`);
  } else {
    totalBytes += entry.bytes;
  }
  if (typeof entry.executable !== "boolean") {
    addFailure(`executable must be boolean for ${artifactPath || name || "unknown"}`);
  }
  if (requiredPaths.includes(artifactPath) && entry.executable !== artifactPath.endsWith(".sh")) {
    addFailure(`required artifact executable flag mismatch: ${artifactPath}`);
  }
}

if (packet.totalBytes !== totalBytes) addFailure("totalBytes must equal entry byte sum");
if (!Number.isInteger(packet.totalBytes) || packet.totalBytes <= 0) addFailure("totalBytes must be a positive integer");
for (const requiredPath of requiredPaths) {
  if (!seenPaths.has(requiredPath)) addFailure(`missing required release artifact: ${requiredPath}`);
}
if (fs.existsSync(ownerInputPacketPath)) {
  try {
    const ownerInputPacket = JSON.parse(fs.readFileSync(ownerInputPacketPath, "utf8"));
    const owners = Array.isArray(ownerInputPacket.owners) ? ownerInputPacket.owners : [];
    if (ownerInputPacket.redacted !== true) addFailure("owner input packet must be redacted when present");
    if (!Array.isArray(ownerInputPacket.owners)) addFailure("owner input packet owners must be an array when present");
    for (const owner of owners) {
      const ownerName = String(owner.owner || "unknown");
      const fileName = String(owner.fileName || "");
      if (!fileName) {
        addFailure(`owner input packet fileName is required for ${ownerName}`);
        continue;
      }
      const expectedJsonPath = `${releaseRoot}release-env-owner-input-packet/${fileName}.json`;
      const expectedMarkdownPath = `${releaseRoot}release-env-owner-input-packet/${fileName}.md`;
      const expectedJsonName = `releaseEnvOwnerInputPacketOwner${String(owners.indexOf(owner) + 1).padStart(2, "0")}Json`;
      const expectedMarkdownName = `releaseEnvOwnerInputPacketOwner${String(owners.indexOf(owner) + 1).padStart(2, "0")}Markdown`;
      if (!seenPaths.has(expectedJsonPath)) addFailure(`missing owner input packet integrity entry: ${expectedJsonPath}`);
      if (!seenPaths.has(expectedMarkdownPath)) addFailure(`missing owner input packet integrity entry: ${expectedMarkdownPath}`);
      if (!seenNames.has(expectedJsonName)) addFailure(`missing owner input packet integrity name: ${expectedJsonName}`);
      if (!seenNames.has(expectedMarkdownName)) addFailure(`missing owner input packet integrity name: ${expectedMarkdownName}`);
    }
  } catch (error) {
    addFailure(`owner input packet must be valid JSON when present: ${error.message}`);
  }
}

if (fs.existsSync(pathLeakReportPath)) {
  try {
    const pathLeakReport = JSON.parse(fs.readFileSync(pathLeakReportPath, "utf8"));
    if (pathLeakReport.status !== "PASS") addFailure("path leak report status must be PASS when present");
    if (pathLeakReport.leakCount !== 0) addFailure("path leak report leakCount must be 0 when present");
  } catch (error) {
    addFailure(`path leak report must be valid JSON when present: ${error.message}`);
  }
}

if (failures.length > 0) {
  throw new Error(`release artifact integrity contract failed: ${failures.join("; ")}`);
}

console.log(`[ddd-release-artifact-integrity-contract] ok artifacts=${entries.length} totalBytes=${packet.totalBytes}`);
