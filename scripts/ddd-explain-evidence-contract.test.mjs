#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  collectPlanTables,
  expectedExplainKeys,
  expectedExplainMaxRowsPerScan,
  missingRequiredExplainFiles,
  requiredExplainFilesWhenPresent,
  validateExplainArtifact,
} from "./ddd-explain-evidence-contract.mjs";
import { expectedExplainSqlSha256ByFile } from "./ddd-explain-query-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const hotPathDoc = fs.readFileSync(path.join(repoRoot, "docs", "28-ddd-hot-path-explain-plan.md"), "utf8");
const operationalRunbook = fs.readFileSync(path.join(repoRoot, "docs", "31-ddd-operational-runbook.md"), "utf8");
const releaseChecklist = fs.readFileSync(path.join(repoRoot, "docs", "34-ddd-release-evidence-checklist.md"), "utf8");
const explainFileHotPathNames = new Map([
  ["platform-runtime-appearance.json", "Platform runtime appearance"],
  ["plugin-bootstrap.json", "Plugin bootstrap"],
  ["message-visible-list.json", "Message visible list"],
  ["message-unread-count.json", "Message unread count"],
  ["message-archive-total.json", "Message archive total"],
  ["ai-knowledge-index-retry.json", "AI knowledge index retry"],
  ["platform-outbox-owner-relay-message.json", "Message outbox owner relay"],
  ["platform-outbox-owner-relay-file.json", "File outbox owner relay"],
]);

function hotPathRows(markdown) {
  return markdown
    .split(/\r?\n/)
    .filter((line) => line.startsWith("| ") && !line.includes("---"))
    .map((line) => line.split("|").slice(1, -1).map((cell) => cell.trim()))
    .filter((cells) => cells.length >= 6 && cells[0] !== "热路径");
}

const hotPathRowByName = new Map(hotPathRows(hotPathDoc).map((row) => [row[0], row]));

for (const fileName of requiredExplainFilesWhenPresent) {
  const hotPathName = explainFileHotPathNames.get(fileName);
  assert(hotPathName, `${fileName} must map to a documented hot path`);
  const row = hotPathRowByName.get(hotPathName);
  assert(row, `${hotPathName} must be documented in docs/28-ddd-hot-path-explain-plan.md`);
  const expectedIndexCell = row[4];
  for (const expectedKey of expectedExplainKeys.get(fileName) || []) {
    assert(
      expectedIndexCell.includes(expectedKey),
      `${hotPathName} expected index cell must include ${expectedKey}`,
    );
    assert(
      operationalRunbook.includes(expectedKey),
      `docs/31-ddd-operational-runbook.md must mention expected EXPLAIN index ${expectedKey}`,
    );
    assert(
      releaseChecklist.includes(expectedKey),
      `docs/34-ddd-release-evidence-checklist.md must mention expected EXPLAIN index ${expectedKey}`,
    );
  }
  const maxRowsPerScan = expectedExplainMaxRowsPerScan.get(fileName);
  assert(Number.isFinite(maxRowsPerScan), `${fileName} must declare a strict max rows per scan`);
  assert(
    hotPathDoc.includes(`${fileName}: ${maxRowsPerScan}`) || hotPathDoc.includes(`\`${fileName}\` | ${maxRowsPerScan}`),
    `docs/28-ddd-hot-path-explain-plan.md must mention ${fileName} max rows per scan ${maxRowsPerScan}`,
  );
  assert(
    operationalRunbook.includes(fileName),
    `docs/31-ddd-operational-runbook.md must mention required EXPLAIN artifact ${fileName}`,
  );
  assert(
    releaseChecklist.includes(fileName),
    `docs/34-ddd-release-evidence-checklist.md must mention required EXPLAIN artifact ${fileName}`,
  );
  assert(
    releaseChecklist.includes("rows_examined_per_scan"),
    "docs/34-ddd-release-evidence-checklist.md must mention strict rows_examined_per_scan limits",
  );
}

function indexedArtifact(overrides = {}) {
  const queryName = overrides.queryName || "platform-outbox-owner-relay-message";
  return {
    generatedAt: "2026-06-14T00:00:00.000Z",
    sourceEnvironment: "staging",
    releaseCandidate: "rc-20260614",
    evidenceOperator: "ci",
    queryName,
    sqlSha256: expectedExplainSqlSha256ByFile.get(`${queryName}.json`) || "a".repeat(64),
    database: "lumira_staging",
    mysqlHost: "mysql.staging.internal",
    plan: {
      query_block: {
        table: {
          table_name: "platform_event_outbox",
          access_type: "range",
          key: "idx_platform_event_outbox_owner_queue",
          rows_examined_per_scan: 1,
        },
      },
    },
    ...overrides,
  };
}

assert.deepEqual(validateExplainArtifact("platform-outbox-owner-relay-message.json", indexedArtifact(), { strict: true }), []);

assert.equal(
  missingRequiredExplainFiles(requiredExplainFilesWhenPresent.filter((file) => file !== "message-visible-list.json"))[0],
  "message-visible-list.json",
);

assert.equal(
  collectPlanTables({ nested: [{ table: { table_name: "sys_config", access_type: "ref", key: "idx_sys_config" } }] }).length,
  1,
);

assert(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    generatedAt: "not-a-date",
    sourceEnvironment: "",
    queryName: "wrong-name",
    sqlSha256: "not-a-sha",
    legacyPlanImport: true,
  }), { strict: true })
    .some((issue) => issue.detail === "message-visible-list.json.generatedAt must be an ISO timestamp"),
);

assert.deepEqual(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "wrong-name",
    sqlSha256: "not-a-sha",
    legacyPlanImport: true,
  }), { strict: true })
    .filter((issue) => issue.scope === "metadata")
    .map((issue) => issue.detail),
  [
    "message-visible-list.json.queryName must be message-visible-list, got wrong-name",
    "message-visible-list.json.sqlSha256 must be a 64-character hex SHA-256",
    "message-visible-list.json.legacyPlanImport must be false for strict release evidence",
  ],
);

assert.deepEqual(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    legacyPlanImport: true,
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "ref",
          key: "idx_msg_notice_tenant_type_status_created",
          rows_examined_per_scan: 1,
        },
      },
    },
  })),
  [],
);

assert.deepEqual(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    sqlSha256: expectedExplainSqlSha256ByFile.get("message-visible-list.json"),
    legacyPlanImport: true,
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "ref",
          key: "idx_msg_notice_tenant_type_status_created",
          rows_examined_per_scan: 1,
        },
      },
    },
  }), { strict: true })
    .map((issue) => issue.detail),
  [
    "message-visible-list.json.legacyPlanImport must be false for strict release evidence",
  ],
);

assert.deepEqual(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    database: "",
    mysqlHost: "",
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "range",
          key: "idx_msg_notice_visible_recent",
          rows_examined_per_scan: 1,
        },
      },
    },
  }), { strict: true })
    .filter((issue) => issue.scope === "metadata")
    .map((issue) => issue.detail),
  [
    "message-visible-list.json.database is required",
    "message-visible-list.json.mysqlHost is required",
  ],
);

assert.deepEqual(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    sourceEnvironment: "local-codex-audit",
    mysqlHost: "127.0.0.1",
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "range",
          key: "idx_msg_notice_visible_recent",
          rows_examined_per_scan: 1,
        },
      },
    },
  }), { strict: true })
    .filter((issue) => issue.scope === "metadata")
    .map((issue) => issue.detail),
  [
    "message-visible-list.json.mysqlHost must be production-equivalent and non-local",
    "message-visible-list.json.sourceEnvironment must be production-equivalent and non-local",
  ],
);

assert(
  validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    sqlSha256: "a".repeat(64),
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "range",
          key: "idx_msg_notice_visible_recent",
          rows_examined_per_scan: 1,
        },
      },
    },
  }), { strict: true })
    .some((issue) => issue.detail === "message-visible-list.json.sqlSha256 must match the current hot-path SQL contract"),
);

assert(
  validateExplainArtifact("message-visible-list.json", {
    plan: { query_block: { table: { table_name: "msg_notice", access_type: "ALL", rows_examined_per_scan: 1000 } } },
  })
    .some((issue) => issue.detail === "message-visible-list.json: msg_notice uses full scan access_type=ALL"),
);

assert(
  validateExplainArtifact("platform-outbox-owner-relay-file.json", indexedArtifact({
    plan: {
      query_block: {
        table: {
          table_name: "platform_event_outbox",
          access_type: "range",
          key: "idx_wrong",
        },
      },
    },
  }))
    .some((issue) => issue.detail.includes("expected index key idx_platform_event_outbox_owner_queue was not used")),
);

assert(
  validateExplainArtifact("message-unread-count.json", indexedArtifact({
    queryName: "message-unread-count",
    sqlSha256: expectedExplainSqlSha256ByFile.get("message-unread-count.json"),
    plan: {
      query_block: {
        table: {
          table_name: "msg_notice",
          access_type: "range",
          key: "idx_msg_notice_visible_recent",
          rows_examined_per_scan: expectedExplainMaxRowsPerScan.get("message-unread-count.json") + 1,
        },
      },
    },
  }), { strict: true })
    .some((issue) => issue.detail === "message-unread-count.json: msg_notice rows_examined_per_scan 501 exceeds strict max 500"),
);

{
  const issues = validateExplainArtifact("platform-runtime-appearance.json", indexedArtifact({
    queryName: "platform-runtime-appearance",
    plan: {
      query_block: {
        table: {
          table_name: "sys_config",
          access_type: "range",
          key: "idx_wrong",
          rows_examined_per_scan: 2,
        },
      },
    },
  }), { strict: true });
  assert(issues.some((issue) => issue.detail === "platform-runtime-appearance.json: expected index key uk_sys_config_key was not used"));
}

{
  const issues = validateExplainArtifact("plugin-bootstrap.json", indexedArtifact({
    queryName: "plugin-bootstrap",
    plan: {
      query_block: {
        nested_loop: [
          { table: { table_name: "t", access_type: "ref", key: "uk_sys_plugin_tenant_rel", rows_examined_per_scan: 1 } },
          { table: { table_name: "d", access_type: "eq_ref", key: "uk_sys_plugin_definition_code", rows_examined_per_scan: 1 } },
        ],
      },
    },
  }), { strict: true });
  assert(issues.some((issue) => issue.detail === "plugin-bootstrap.json: expected index key uk_sys_plugin_version_code_version was not used"));
}

{
  const issues = validateExplainArtifact("message-visible-list.json", indexedArtifact({
    queryName: "message-visible-list",
    plan: {
      query_block: {
        table: {
          key: "idx_msg_notice_visible_recent",
        },
      },
    },
  }), { strict: true });
  assert(issues.some((issue) => issue.detail === "message-visible-list.json: every table plan node must report table_name"));
  assert(issues.some((issue) => issue.detail === "message-visible-list.json: <unknown> must report access_type"));
  assert(issues.some((issue) => issue.detail === "message-visible-list.json: <unknown> must report rows_examined_per_scan or rows"));
}

console.log("[ddd-explain-evidence-contract.test] all assertions passed");
