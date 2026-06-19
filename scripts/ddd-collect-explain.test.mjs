#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { requiredExplainFilesWhenPresent } from "./ddd-explain-evidence-contract.mjs";
import { explainQueries } from "./ddd-explain-query-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const collectScript = fs.readFileSync(path.join(repoRoot, "scripts", "ddd-collect-explain.mjs"), "utf8");
const normalizeScript = fs.readFileSync(path.join(repoRoot, "scripts", "ddd-normalize-explain-artifacts.mjs"), "utf8");

assert.equal(explainQueries.length, requiredExplainFilesWhenPresent.length);

for (const fileName of requiredExplainFilesWhenPresent) {
  const queryName = fileName.replace(/\.json$/u, "");
  assert(
    explainQueries.some((query) => query.name === queryName),
    `shared query contract should include ${queryName}`,
  );
  assert(
    collectScript.includes("explainQueries"),
    "collector should import shared explainQueries",
  );
  assert(
    normalizeScript.includes("explainQueryByName"),
    "normalizer should import shared explainQueryByName",
  );
}

for (const query of explainQueries) {
  assert(
    collectScript.includes("explainSqlSha256(query.sql)"),
    "collector should derive sqlSha256 from shared query SQL",
  );
  assert(
    normalizeScript.includes("explainSqlSha256(query.sql)"),
    "normalizer should derive sqlSha256 from shared query SQL",
  );
}

assert(
  collectScript.includes("DDD_EXPLAIN_STRICT"),
  "collector should expose strict mode for release explain evidence",
);
assert(
  collectScript.includes("DDD_RELEASE_EVIDENCE_STRICT"),
  "collector should honor release strict mode",
);
assert(
  collectScript.includes("strict explain collection requires") && collectScript.includes("DDD_EVIDENCE_ENVIRONMENT"),
  "strict collector should fail before writing artifacts without provenance",
);
assert(
  collectScript.includes("production-equivalent non-local MYSQL_HOST"),
  "strict collector should reject localhost database evidence",
);

const strictMissingProvenance = spawnSync("node", ["scripts/ddd-collect-explain.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_EXPLAIN_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "",
    DDD_RELEASE_CANDIDATE: "",
    DDD_EVIDENCE_OPERATOR: "",
    DDD_EXPLAIN_DATABASE: "",
    MYSQL_HOST: "127.0.0.1",
  },
});
assert.notEqual(strictMissingProvenance.status, 0);
assert.match(
  strictMissingProvenance.stderr,
  /strict explain collection requires DDD_EVIDENCE_ENVIRONMENT, DDD_RELEASE_CANDIDATE, DDD_EVIDENCE_OPERATOR, DDD_EXPLAIN_DATABASE/u,
);

const strictLocalHost = spawnSync("node", ["scripts/ddd-collect-explain.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_EXPLAIN_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "staging",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "release-bot",
    DDD_EXPLAIN_DATABASE: "lumira",
    MYSQL_HOST: "127.0.0.1",
  },
});
assert.notEqual(strictLocalHost.status, 0);
assert.match(
  strictLocalHost.stderr,
  /strict explain collection requires a production-equivalent non-local MYSQL_HOST, got 127\.0\.0\.1/u,
);

const fakeMysqlDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-fake-mysql-"));
const explainOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-explain-output-"));
const fakeMysqlPath = path.join(fakeMysqlDir, "mysql");
fs.writeFileSync(fakeMysqlPath, `#!/usr/bin/env node
console.log(JSON.stringify({
  query_block: {
    table: {
      table_name: "synthetic_table",
      access_type: "range",
      key: "synthetic_idx",
      rows_examined_per_scan: 1
    }
  }
}));
`);
fs.chmodSync(fakeMysqlPath, 0o700);

const strictSuccessfulCollection = spawnSync("node", ["scripts/ddd-collect-explain.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_EXPLAIN_STRICT: "true",
    DDD_EXPLAIN_DIR: explainOutputDir,
    DDD_EVIDENCE_ENVIRONMENT: "staging-prod-equivalent",
    DDD_RELEASE_CANDIDATE: "abc123",
    DDD_EVIDENCE_OPERATOR: "release-bot",
    DDD_EXPLAIN_DATABASE: "lumira",
    MYSQL_HOST: "mysql.staging.internal",
    MYSQL_CLI_NODE_SCRIPT: fakeMysqlPath,
  },
});
assert.equal(strictSuccessfulCollection.status, 0, strictSuccessfulCollection.stderr);
assert.match(strictSuccessfulCollection.stdout, new RegExp(`wrote ${explainQueries.length} explain json file`));
for (const fileName of requiredExplainFilesWhenPresent) {
  const artifact = JSON.parse(fs.readFileSync(path.join(explainOutputDir, fileName), "utf8"));
  assert.equal(artifact.sourceEnvironment, "staging-prod-equivalent");
  assert.equal(artifact.releaseCandidate, "abc123");
  assert.equal(artifact.evidenceOperator, "release-bot");
  assert.equal(artifact.database, "lumira");
  assert.equal(artifact.mysqlHost, "mysql.staging.internal");
  assert.equal(artifact.queryName, fileName.replace(/\.json$/u, ""));
  assert.match(artifact.sqlSha256, /^[a-f0-9]{64}$/u);
  assert(artifact.plan?.query_block?.table, `${fileName} must include the mysql JSON plan`);
}

for (const queryContractText of [
  fs.readFileSync(path.join(repoRoot, "scripts", "ddd-explain-query-contract.mjs"), "utf8"),
]) {
  assert(
    queryContractText.includes("FROM msg_notice n FORCE INDEX (idx_msg_notice_visible_recent)"),
    "message explain SQL should force idx_msg_notice_visible_recent",
  );
}

console.log("[ddd-collect-explain.test] ok");
