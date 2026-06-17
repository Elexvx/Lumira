#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { defaultRequiredFrontendFlows } from "./ddd-frontend-smoke-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const appSpecPath = path.join(repoRoot, "frontend", "tests", "e2e", "app.spec.ts");
const appSpec = fs.readFileSync(appSpecPath, "utf8");

for (const flow of defaultRequiredFrontendFlows) {
  if (flow.endsWith(" page is reachable")) {
    const label = flow.slice(0, -" page is reachable".length);
    assert.match(
      appSpec,
      new RegExp(`\\{\\s*path:\\s*'[^']+',\\s*label:\\s*'${escapeRegExp(label)}',\\s*tag:\\s*'@smoke'\\s*\\}`),
      `frontend protected page smoke must cover required flow "${flow}"`,
    );
    continue;
  }

  assert.match(
    appSpec,
    new RegExp(`test\\(\\s*'${escapeRegExp(flow)}\\s+@smoke'`),
    `frontend smoke spec must cover required flow "${flow}"`,
  );
}

console.log("[ddd-frontend-smoke-flow-sync.test] ok");

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
