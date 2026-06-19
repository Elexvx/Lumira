import { evidenceValueIssue, isIsoTimestamp } from "./ddd-release-evidence-utils.mjs";
import { expectedExplainSqlSha256ByFile } from "./ddd-explain-query-contract.mjs";

export const requiredExplainFilesWhenPresent = [
  "platform-runtime-appearance.json",
  "plugin-bootstrap.json",
  "message-visible-list.json",
  "message-unread-count.json",
  "message-archive-total.json",
  "ai-knowledge-index-retry.json",
  "platform-outbox-owner-relay-message.json",
  "platform-outbox-owner-relay-file.json",
];

export const expectedExplainKeys = new Map([
  ["platform-runtime-appearance.json", ["uk_sys_config_key"]],
  ["plugin-bootstrap.json", [
    "uk_sys_plugin_tenant_rel",
    "uk_sys_plugin_definition_code",
    "uk_sys_plugin_version_code_version",
  ]],
  ["message-visible-list.json", ["idx_msg_notice_visible_recent"]],
  ["message-unread-count.json", ["idx_msg_notice_visible_recent"]],
  ["message-archive-total.json", ["idx_msg_notice_visible_recent"]],
  ["ai-knowledge-index-retry.json", ["idx_ai_knowledge_document_index_retry"]],
  ["platform-outbox-owner-relay-message.json", ["idx_platform_event_outbox_owner_queue"]],
  ["platform-outbox-owner-relay-file.json", ["idx_platform_event_outbox_owner_queue"]],
]);

export const expectedExplainMaxRowsPerScan = new Map([
  ["platform-runtime-appearance.json", 20],
  ["plugin-bootstrap.json", 1000],
  ["message-visible-list.json", 100],
  ["message-unread-count.json", 500],
  ["message-archive-total.json", 2000],
  ["ai-knowledge-index-retry.json", 200],
  ["platform-outbox-owner-relay-message.json", 500],
  ["platform-outbox-owner-relay-file.json", 500],
]);

export function collectPlanTables(node, tables = []) {
  if (!node || typeof node !== "object") {
    return tables;
  }
  if (node.table && typeof node.table === "object") {
    tables.push(node.table);
  }
  if (Array.isArray(node)) {
    for (const item of node) {
      collectPlanTables(item, tables);
    }
    return tables;
  }
  for (const value of Object.values(node)) {
    collectPlanTables(value, tables);
  }
  return tables;
}

export function explainPlan(parsed) {
  return parsed && typeof parsed === "object" && parsed.plan ? parsed.plan : parsed;
}

export function missingRequiredExplainFiles(fileNames) {
  const present = new Set(fileNames);
  return requiredExplainFilesWhenPresent.filter((fileName) => !present.has(fileName));
}

export function validateExplainArtifact(fileName, parsed, { strict = false } = {}) {
  const issues = [];
  if (!parsed || typeof parsed !== "object") {
    issues.push({ scope: "plan", detail: `${fileName} must be a JSON object` });
    return issues;
  }

  if (strict) {
    for (const field of ["sourceEnvironment", "releaseCandidate", "evidenceOperator", "queryName", "sqlSha256"]) {
      const issue = evidenceValueIssue(parsed[field]);
      if (issue) {
        issues.push({ scope: "metadata", detail: `${fileName}.${field} ${issue}` });
      }
    }
    const expectedQueryName = fileName.replace(/\.json$/i, "");
    if (parsed.queryName && parsed.queryName !== expectedQueryName) {
      issues.push({ scope: "metadata", detail: `${fileName}.queryName must be ${expectedQueryName}, got ${parsed.queryName}` });
    }
    if (parsed.sqlSha256 && !/^[a-f0-9]{64}$/i.test(parsed.sqlSha256)) {
      issues.push({ scope: "metadata", detail: `${fileName}.sqlSha256 must be a 64-character hex SHA-256` });
    }
    const expectedSqlSha256 = expectedExplainSqlSha256ByFile.get(fileName);
    if (expectedSqlSha256 && parsed.sqlSha256 && /^[a-f0-9]{64}$/i.test(parsed.sqlSha256) && parsed.sqlSha256 !== expectedSqlSha256) {
      issues.push({
        scope: "metadata",
        detail: `${fileName}.sqlSha256 must match the current hot-path SQL contract`,
      });
    }
    if (parsed.legacyPlanImport === true) {
      issues.push({ scope: "metadata", detail: `${fileName}.legacyPlanImport must be false for strict release evidence` });
    }
    if (!isIsoTimestamp(parsed.generatedAt)) {
      issues.push({ scope: "metadata", detail: `${fileName}.generatedAt must be an ISO timestamp` });
    }
    for (const field of ["database", "mysqlHost"]) {
      const issue = evidenceValueIssue(parsed[field]);
      if (issue) {
        issues.push({ scope: "metadata", detail: `${fileName}.${field} ${issue}` });
      }
    }
    if (typeof parsed.mysqlHost === "string" && /^(localhost|127\.0\.0\.1|0\.0\.0\.0|::1)$/u.test(parsed.mysqlHost.trim())) {
      issues.push({ scope: "metadata", detail: `${fileName}.mysqlHost must be production-equivalent and non-local` });
    }
    if (typeof parsed.sourceEnvironment === "string" && /\b(local|dev|codex|worktree)\b/iu.test(parsed.sourceEnvironment.trim())) {
      issues.push({ scope: "metadata", detail: `${fileName}.sourceEnvironment must be production-equivalent and non-local` });
    }
  }
  if (parsed.legacyPlanImport === true) {
    return issues;
  }

  const tables = collectPlanTables(explainPlan(parsed));
  if (tables.length === 0) {
    issues.push({ scope: "plan", detail: `${fileName} has no table plan nodes` });
    return issues;
  }

  const expectedKeys = expectedExplainKeys.get(fileName) || [];
  const maxRowsPerScan = expectedExplainMaxRowsPerScan.get(fileName);
  const matchedExpectedKeys = new Set();
  for (const table of tables) {
    const tableName = table.table_name || table.table || "<unknown>";
    const accessType = table.access_type || table.type;
    const key = table.key || table.key_used;
    const rowsPerScan = Number(table.rows_examined_per_scan ?? table.rows);
    if (strict) {
      if (!table.table_name && !table.table) {
        issues.push({ scope: "plan", detail: `${fileName}: every table plan node must report table_name` });
      }
      if (!accessType) {
        issues.push({ scope: "plan", detail: `${fileName}: ${tableName} must report access_type` });
      }
      if (table.rows_examined_per_scan === undefined && table.rows === undefined) {
        issues.push({ scope: "plan", detail: `${fileName}: ${tableName} must report rows_examined_per_scan or rows` });
      }
      if (Number.isFinite(maxRowsPerScan) && Number.isFinite(rowsPerScan) && rowsPerScan > maxRowsPerScan) {
        issues.push({
          scope: "plan",
          detail: `${fileName}: ${tableName} rows_examined_per_scan ${rowsPerScan} exceeds strict max ${maxRowsPerScan}`,
        });
      }
    }
    if (expectedKeys.includes(key)) {
      matchedExpectedKeys.add(key);
    }
    if (accessType === "ALL") {
      issues.push({ scope: "plan", detail: `${fileName}: ${tableName} uses full scan access_type=ALL` });
    }
    if (!key && accessType && !["const", "system", "eq_ref"].includes(accessType)) {
      issues.push({
        scope: "plan",
        detail: `${fileName}: ${tableName} does not report an index key for access_type=${accessType}`,
      });
    }
  }

  for (const expectedKey of expectedKeys) {
    if (!matchedExpectedKeys.has(expectedKey)) {
      issues.push({ scope: "plan", detail: `${fileName}: expected index key ${expectedKey} was not used` });
    }
  }

  return issues;
}
