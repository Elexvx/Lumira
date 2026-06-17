#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  requiredMigrationEvidenceChecklist,
  requiredMigrationLocations,
  validateMigrationEvidenceContract,
} from "./ddd-migration-evidence-contract.mjs";
import { expectedExplainKeys } from "./ddd-explain-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const ownerManifestPath = path.join(repoRoot, "docs", "27-ddd-owner-table-manifest.csv");
const evidenceScriptPath = path.join(repoRoot, "scripts", "ddd-migration-evidence.mjs");

const expectedOwnerModules = [
  "system-service",
  "auth-service",
  "message-service",
  "file-service",
  "plugin-service",
  "localization-service",
  "payment-service",
  "ai-service",
];

assert.equal(
  new Set(requiredMigrationLocations).size,
  requiredMigrationLocations.length,
  "requiredMigrationLocations must not contain duplicates",
);

for (const ownerModule of expectedOwnerModules) {
  const matchingLocations = requiredMigrationLocations.filter((location) => {
    return location.startsWith(`services/${ownerModule}/`);
  });
  assert.equal(
    matchingLocations.length,
    1,
    `${ownerModule} must have exactly one required migration evidence location`,
  );
}

const ownerManifest = fs.readFileSync(ownerManifestPath, "utf8");
for (const ownerModule of expectedOwnerModules) {
  assert.match(
    ownerManifest,
    new RegExp(`(^|\\n)[^\\n,]+,${escapeRegExp(ownerModule)},`),
    `${ownerModule} must be represented in docs/27-ddd-owner-table-manifest.csv`,
  );
}

const scriptSource = fs.readFileSync(evidenceScriptPath, "utf8");
assert.match(
  scriptSource,
  /import\s*\{[\s\S]*requiredMigrationEvidenceChecklist[\s\S]*requiredMigrationLocations[\s\S]*\}\s*from "\.\/ddd-migration-evidence-contract\.mjs"/,
  "ddd-migration-evidence.mjs must import migration handoff contracts from the shared contract",
);
assert.doesNotMatch(
  scriptSource,
  /const\s+requiredLocations\s*=\s*\[/,
  "ddd-migration-evidence.mjs must not maintain a private requiredLocations array",
);
assert.doesNotMatch(
  scriptSource,
  /function\s+buildMigrationEvidenceChecklist/,
  "ddd-migration-evidence.mjs must not maintain a private migration evidence checklist",
);
assert.deepEqual(
  requiredMigrationEvidenceChecklist.map((item) => item.id),
  ["fresh-database-evidence-package", "previous-schema-upgrade-evidence-package"],
);
assert.match(
  scriptSource,
  /DDD_MIGRATION_CHECK_ENV/,
  "ddd-migration-evidence.mjs must keep the check-env preflight mode for fast safe release handoff",
);
assert.match(
  scriptSource,
  /migration-evidence-handoff\.md/,
  "ddd-migration-evidence.mjs must generate the migration evidence handoff artifact",
);
assert.match(
  scriptSource,
  /DDD_MIGRATION_HANDOFF_FILE/,
  "ddd-migration-evidence.mjs must allow callers to redirect the migration handoff artifact",
);

const migrationSqlByLocation = new Map(requiredMigrationLocations.map((location) => [
  location,
  migrationSql(path.join(repoRoot, location)),
]));

const explainMigrationLocations = new Map([
  ["platform-runtime-appearance.json", ["services/system-service/src/main/resources/db/migration"]],
  ["plugin-bootstrap.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/plugin-service/src/main/resources/db/migration/plugin",
  ]],
  ["message-visible-list.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/message-service/src/main/resources/db/migration/message",
  ]],
  ["message-unread-count.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/message-service/src/main/resources/db/migration/message",
  ]],
  ["message-archive-total.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/message-service/src/main/resources/db/migration/message",
  ]],
  ["ai-knowledge-index-retry.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/ai-service/src/main/resources/db/migration/ai",
  ]],
  ["platform-outbox-owner-relay-message.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/message-service/src/main/resources/db/migration/message",
  ]],
  ["platform-outbox-owner-relay-file.json", [
    "services/system-service/src/main/resources/db/migration",
    "services/file-service/src/main/resources/db/migration/file",
  ]],
]);

for (const [fileName, expectedIndexes] of expectedExplainKeys.entries()) {
  const migrationLocations = explainMigrationLocations.get(fileName) || [];
  assert(migrationLocations.length > 0, `${fileName} must map to owner migration locations`);
  for (const location of migrationLocations) {
    assert(requiredMigrationLocations.includes(location), `${fileName} migration location must be required: ${location}`);
    const sql = migrationSqlByLocation.get(location) || "";
    for (const indexName of expectedIndexes) {
      assert.match(
        sql,
        new RegExp(`\\b${indexName}\\b`),
        `${location} must include EXPLAIN expected index ${indexName} for ${fileName}`,
      );
    }
  }
}

const messageMigrationSql = migrationSqlByLocation.get("services/message-service/src/main/resources/db/migration/message") || "";
const systemMigrationSql = migrationSqlByLocation.get("services/system-service/src/main/resources/db/migration") || "";
for (const indexName of [
  "idx_msg_notice_visible_recent",
  "idx_msg_notice_visible_target_user_recent",
  "idx_msg_notice_visible_target_role_recent",
]) {
  assert.match(
    messageMigrationSql,
    new RegExp(`\\b${indexName}\\b`),
    `message-service migrations must include ${indexName} for message hot paths`,
  );
}
for (const [location, sql] of [
  ["services/system-service/src/main/resources/db/migration", systemMigrationSql],
  ["services/message-service/src/main/resources/db/migration/message", messageMigrationSql],
]) {
  assert.match(
    sql,
    /ADD INDEX `idx_msg_notice_visible_recent` \(`tenant_id`, `publish_status`, `deleted`, `target_scope`, `id`\)/,
    `${location} must upgrade idx_msg_notice_visible_recent to include target_scope before id`,
  );
  assert.match(
    sql,
    /ADD INDEX `idx_msg_notice_visible_target_user_recent` \(`tenant_id`, `publish_status`, `deleted`, `target_scope`, `target_user_id`, `id`\)/,
    `${location} must upgrade idx_msg_notice_visible_target_user_recent to include target_scope before target_user_id`,
  );
  assert.match(
    sql,
    /ADD INDEX `idx_msg_notice_visible_target_role_recent` \(`tenant_id`, `publish_status`, `deleted`, `target_scope`, `target_role_id`, `id`\)/,
    `${location} must upgrade idx_msg_notice_visible_target_role_recent to include target_scope before target_role_id`,
  );
  const onlineDdlMatches = sql.match(/ALTER TABLE `msg_notice`[\s\S]*?(?:DROP INDEX|ADD INDEX) `idx_msg_notice_visible(?:_target_(?:user|role))?_recent`[\s\S]*?ALGORITHM=INPLACE,[\s\S]*?LOCK=NONE;/g) || [];
  assert(
    onlineDdlMatches.length >= 6,
    `${location} message visible index migrations must use online DDL with ALGORITHM=INPLACE and LOCK=NONE`,
  );
}

const aiMigrationSql = migrationSqlByLocation.get("services/ai-service/src/main/resources/db/migration/ai") || "";
for (const token of [
  "index_retry_count",
  "index_next_retry_at",
  "index_last_error",
  "idx_ai_knowledge_document_index_retry",
]) {
  assert.match(
    aiMigrationSql,
    new RegExp(`\\b${token}\\b`),
    `ai-service migrations must include ${token} for AI knowledge index retry governance`,
  );
}

const locations = requiredMigrationLocations.map((location) => {
  const absolute = path.join(repoRoot, location);
  const exists = fs.existsSync(absolute);
  const migrations = exists
    ? fs.readdirSync(absolute)
      .filter((file) => /^V[^/]+__.+\.sql$/.test(file))
      .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
    : [];
  const versions = migrations.map((file) => file.match(/^V([^_]+)__/)?.[1] || "");
  const duplicateVersions = versions.filter((version, index) => versions.indexOf(version) !== index);
  return {
    location,
    exists,
    migrationCount: migrations.length,
    duplicateVersions: [...new Set(duplicateVersions)].map((version) => ({ version })),
    emptyFiles: migrations.filter((file) => {
      return fs.readFileSync(path.join(absolute, file), "utf8").trim().length === 0;
    }),
    onlineDdlCoverage: emptyOnlineDdlCoverageSummary(),
  };
});

const artifact = {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "PASS",
  runtime: {
    freshDatabaseValidated: false,
    upgradeDatabaseValidated: false,
    environment: "local-structure",
    releaseCandidate: "local-worktree",
    operator: "codex",
    completedAt: "2026-06-14T00:00:00.000Z",
    freshDatabaseEvidence: "",
    upgradeDatabaseEvidence: "",
  },
  summary: {
    locations: locations.length,
    migrationFiles: locations.reduce((sum, location) => sum + location.migrationCount, 0),
    duplicateVersionLocations: locations.filter((location) => location.duplicateVersions.length > 0).length,
    emptyFiles: locations.reduce((sum, location) => sum + location.emptyFiles.length, 0),
    runtimeReady: false,
    onlineDdlCoverage: emptyOnlineDdlCoverageSummary(),
  },
  runtimeProofs: [
    {
      id: "fresh-database",
      label: "Fresh database Flyway drill",
      validated: false,
      evidence: null,
      requiredEnvKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      requiredEvidence: "Flyway log plus schema-history artifact from an empty production-equivalent database.",
    },
    {
      id: "upgrade-database",
      label: "Previous schema upgrade Flyway drill",
      validated: false,
      evidence: null,
      requiredEnvKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      requiredEvidence: "Before/after schema-history artifact plus Flyway log from a copy of the previous production schema.",
    },
  ],
  runtimeDiagnostics: [
    {
      id: "fresh-database-drill",
      owner: "database",
      status: "MISSING",
      action: "Run Flyway against an empty production-equivalent database and archive schema history plus Flyway logs.",
      envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      evidence: null,
    },
    {
      id: "upgrade-database-drill",
      owner: "database",
      status: "MISSING",
      action: "Run Flyway against a copy of the previous production schema and archive before/after schema history plus Flyway logs.",
      envKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
      evidence: null,
    },
    {
      id: "migration-environment",
      owner: "release-infra",
      status: "PASS",
      action: "Set the production-equivalent migration environment name before generating evidence.",
      envKeys: ["DDD_MIGRATION_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"],
      evidence: "local-structure",
    },
    {
      id: "migration-release-candidate",
      owner: "release-infra",
      status: "PASS",
      action: "Set the immutable release candidate or commit SHA for the migration drill.",
      envKeys: ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"],
      evidence: "local-worktree",
    },
    {
      id: "migration-operator",
      owner: "release-owner",
      status: "PASS",
      action: "Record the operator or CI actor who executed the migration drill.",
      envKeys: ["DDD_MIGRATION_OPERATOR", "DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"],
      evidence: "codex",
    },
    {
      id: "migration-completed-at",
      owner: "release-owner",
      status: "PASS",
      action: "Record the ISO timestamp when both migration drills completed.",
      envKeys: ["DDD_MIGRATION_COMPLETED_AT"],
      evidence: "2026-06-14T00:00:00.000Z",
    },
  ],
  requiredLocations: requiredMigrationLocations,
  locations,
};

assert.deepEqual(validateMigrationEvidenceContract(artifact, { strict: false }), []);
console.log("[ddd-migration-evidence-sync.test] ok");

function emptyOnlineDdlCoverageSummary() {
  return {
    requiredMigrations: 0,
    checkedStatements: 0,
    onlineStatements: 0,
    blockingStatements: 0,
    onlineIndexes: [],
    blockingIndexes: [],
  };
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function migrationSql(directory) {
  return fs.readdirSync(directory)
    .filter((file) => /^V[^/]+__.+\.sql$/.test(file))
    .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
    .map((file) => fs.readFileSync(path.join(directory, file), "utf8"))
    .join("\n");
}
