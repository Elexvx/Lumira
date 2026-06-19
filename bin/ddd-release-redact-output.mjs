#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(import.meta.dirname, "..");
const repoRootPattern = new RegExp(`${repoRoot.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}${path.sep.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`, "g");

export function redactReleaseOutput(text = "") {
  return String(text)
    .replace(/\b([A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|API_KEY))=("[^"]*"|'[^']*'|[^\s`|]+)/g, "$1=<redacted>")
    .replace(/\bDDD_RELEASE_ENV_FILE=(?!<release-env-file>)[^\s`|]+/g, "DDD_RELEASE_ENV_FILE=<release-env-file>")
    .replace(/(?:<repo>|\/[^\s`|]+)?\/?\.env\.release[^\s`|]*/g, "<release-env-file>")
    .replace(repoRootPattern, "<repo>/")
    .replaceAll("\\", "/")
    .replaceAll("<repo>/<release-env-file>", "<release-env-file>");
}

function main() {
  const input = fs.readFileSync(0, "utf8");
  process.stdout.write(redactReleaseOutput(input));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
