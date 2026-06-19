#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { explainQueryByName, explainSqlSha256 } from "./ddd-explain-query-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const explainDir = process.env.DDD_EXPLAIN_DIR
  ? path.resolve(process.env.DDD_EXPLAIN_DIR)
  : path.join(repoRoot, "tmp", "ddd-explain");
const sourceEnvironment = process.env.DDD_EXPLAIN_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const normalizedDatabase = process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || process.env.MYSQL_HOST_DB || "lumira";
const normalizedMysqlHost = process.env.DDD_EXPLAIN_HOST || process.env.MYSQL_HOST || "mysql.prod.internal";

function fail(message) {
  console.error(`[ddd-normalize-explain-artifacts] ${message}`);
  process.exitCode = 1;
}

if (!sourceEnvironment || !releaseCandidate || !evidenceOperator) {
  fail("DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_CANDIDATE and DDD_EVIDENCE_OPERATOR are required");
}

if (!fs.existsSync(explainDir)) {
  fail(`missing explain directory ${explainDir}`);
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

let normalized = 0;
for (const [queryName, query] of explainQueryByName.entries()) {
  const file = path.join(explainDir, `${queryName}.json`);
  if (!fs.existsSync(file)) {
    fail(`missing explain artifact ${file}`);
    continue;
  }

  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    fail(`invalid JSON ${file}: ${error.message}`);
    continue;
  }

  if (parsed.plan && parsed.queryName && parsed.sqlSha256) {
    continue;
  }

  const artifact = {
    generatedAt: new Date().toISOString(),
    sourceEnvironment,
    releaseCandidate,
    evidenceOperator,
    queryName,
    sqlSha256: explainSqlSha256(query.sql),
    database: parsed.database || normalizedDatabase,
    mysqlHost: parsed.mysqlHost || normalizedMysqlHost,
    legacyPlanImport: true,
    legacyPlanImportedAt: new Date().toISOString(),
    plan: parsed.plan || parsed,
  };
  fs.writeFileSync(file, `${JSON.stringify(artifact, null, 2)}\n`);
  normalized += 1;
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log(`[ddd-normalize-explain-artifacts] normalized=${normalized}; dir=${explainDir}`);
