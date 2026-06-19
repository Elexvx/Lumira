#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  validateAuthenticatedPerformanceBaselineMetadata,
  validateAuthenticatedPerformanceShape,
} from "./ddd-performance-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const exampleFile = path.join(repoRoot, "docs", "30-ddd-performance-baseline.example.json");
const example = JSON.parse(fs.readFileSync(exampleFile, "utf8"));

assert.deepEqual(validateAuthenticatedPerformanceShape("authenticated performance baseline example", example), []);
assert.deepEqual(validateAuthenticatedPerformanceBaselineMetadata(example, { strict: true }), []);

console.log("[ddd-performance-baseline-example.test] ok");
