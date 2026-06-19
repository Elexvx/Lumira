#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { explainQueries, explainSqlSha256 } from "./ddd-explain-query-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_EXPLAIN_DIR
  ? path.resolve(process.env.DDD_EXPLAIN_DIR)
  : path.join(repoRoot, "tmp", "ddd-explain");
const mysqlCommand = process.env.MYSQL_CLI || "mysql";
const mysqlNodeScript = process.env.MYSQL_CLI_NODE_SCRIPT || "";
const mysqlDatabase = process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || "";
const mysqlHost = process.env.MYSQL_HOST || "";
const mysqlPort = process.env.MYSQL_PORT || "";
const mysqlUser = process.env.MYSQL_USER || "";
const mysqlPassword = process.env.MYSQL_PASSWORD || "";
const sourceEnvironment = process.env.DDD_EXPLAIN_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const strict = process.env.DDD_EXPLAIN_STRICT === "true" || process.env.DDD_RELEASE_EVIDENCE_STRICT === "true";

function fail(message) {
  console.error(`[ddd-collect-explain] ${message}`);
  process.exit(1);
}

function mysqlArgs(sql) {
  const args = ["--batch", "--raw", "--skip-column-names"];
  if (mysqlHost) {
    args.push(`--host=${mysqlHost}`);
  }
  if (mysqlPort) {
    args.push(`--port=${mysqlPort}`);
  }
  if (mysqlUser) {
    args.push(`--user=${mysqlUser}`);
  }
  if (mysqlPassword) {
    args.push(`--password=${mysqlPassword}`);
  }
  if (mysqlDatabase) {
    args.push(mysqlDatabase);
  }
  args.push("--execute", `EXPLAIN FORMAT=JSON ${sql}`);
  return args;
}

fs.mkdirSync(outputDir, { recursive: true });

if (strict) {
  const missing = [];
  if (!sourceEnvironment) {
    missing.push("DDD_EVIDENCE_ENVIRONMENT");
  }
  if (!releaseCandidate) {
    missing.push("DDD_RELEASE_CANDIDATE");
  }
  if (!evidenceOperator) {
    missing.push("DDD_EVIDENCE_OPERATOR");
  }
  if (!mysqlDatabase) {
    missing.push("DDD_EXPLAIN_DATABASE");
  }
  if (missing.length > 0) {
    fail(`strict explain collection requires ${missing.join(", ")}`);
  }
  if (!mysqlHost) {
    fail("strict explain collection requires MYSQL_HOST or DDD_EXPLAIN host evidence");
  }
  if (/^(localhost|127\.0\.0\.1|0\.0\.0\.0|::1)$/u.test(mysqlHost)) {
    fail(`strict explain collection requires a production-equivalent non-local MYSQL_HOST, got ${mysqlHost}`);
  }
}

for (const query of explainQueries) {
  let output;
  try {
    output = execFileSync(mysqlNodeScript ? process.execPath : mysqlCommand, [
      ...(mysqlNodeScript ? [mysqlNodeScript] : []),
      ...mysqlArgs(query.sql),
    ], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch (error) {
    fail(`mysql explain failed for ${query.name}: ${error.stderr?.toString() || error.message}`);
  }
  try {
    JSON.parse(output);
  } catch (error) {
    fail(`mysql explain did not return JSON for ${query.name}: ${error.message}`);
  }
  const artifact = {
    generatedAt: new Date().toISOString(),
    sourceEnvironment: sourceEnvironment || null,
    releaseCandidate: releaseCandidate || null,
    evidenceOperator: evidenceOperator || null,
    queryName: query.name,
    sqlSha256: explainSqlSha256(query.sql),
    database: mysqlDatabase || null,
    mysqlHost: mysqlHost || null,
    plan: JSON.parse(output),
  };
  fs.writeFileSync(path.join(outputDir, `${query.name}.json`), `${JSON.stringify(artifact, null, 2)}\n`);
}

console.log(`[ddd-collect-explain] wrote ${explainQueries.length} explain json file(s) to ${outputDir}`);
