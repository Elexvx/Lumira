#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  requiredMigrationEvidenceChecklist,
  requiredMigrationLocations,
  requiredMigrationRuntimeDiagnostics,
  requiredMigrationRuntimeProofs,
  validateMigrationEvidenceContract,
} from "./ddd-migration-evidence-contract.mjs";

assert.deepEqual(
  requiredMigrationEvidenceChecklist.map((item) => item.id),
  ["fresh-database-evidence-package", "previous-schema-upgrade-evidence-package"],
);
for (const item of requiredMigrationEvidenceChecklist) {
  assert.equal(item.owner, "database");
  assert.equal(item.requiredEnvKeys.length, 2);
  assert(item.requiredArtifacts.length >= 3);
  assert(item.acceptanceCriteria.length >= 3);
}
assert.deepEqual(requiredMigrationEvidenceChecklist[0].requiredEnvKeys, [
  "DDD_MIGRATION_FRESH_DB_VALIDATED",
  "DDD_MIGRATION_FRESH_DB_EVIDENCE",
]);
assert.deepEqual(requiredMigrationEvidenceChecklist[1].requiredEnvKeys, [
  "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
  "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
]);

function validArtifact() {
  const artifact = {
    generatedAt: "2026-06-14T10:00:00.000Z",
    status: "PASS",
    runtime: {
      freshDatabaseValidated: true,
      upgradeDatabaseValidated: true,
      environment: "staging-prod-equivalent",
      releaseCandidate: "rc-20260614",
      operator: "release-owner",
      completedAt: "2026-06-14T10:00:00.000Z",
      freshDatabaseEvidence: "artifacts/ddd/migration/fresh-db-flyway-schema-history.json",
      upgradeDatabaseEvidence: "artifacts/ddd/migration/old-db-upgrade-schema-history.json",
    },
    summary: {
      locations: requiredMigrationLocations.length,
      migrationFiles: requiredMigrationLocations.length,
      duplicateVersionLocations: 0,
      emptyFiles: 0,
      runtimeReady: true,
      onlineDdlCoverage: null,
    },
    locations: requiredMigrationLocations.map((location, index) => ({
      location,
      exists: true,
      migrationCount: 1,
      duplicateVersions: [],
      emptyFiles: [],
      onlineDdlCoverage: null,
      migrations: [
        {
          version: `${index + 1}`,
          description: "baseline",
          file: `${location}/V${index + 1}__baseline.sql`,
          bytes: 100,
          sha256: "a".repeat(64),
          empty: false,
          onlineDdlCoverage: emptyOnlineDdlCoverage(),
        },
      ],
    })),
  };
  artifact.locations[0].migrations[0].onlineDdlCoverage = {
    required: true,
    checkedStatements: 1,
    onlineStatements: 1,
    blockingStatements: 0,
    onlineIndexes: ["idx_msg_notice_visible_recent"],
    blockingIndexes: [],
    statementDigests: ["b".repeat(64)],
  };
  refreshOnlineDdlCoverage(artifact);
  artifact.runtimeProofs = buildRuntimeProofs(artifact.runtime);
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  return artifact;
}

function emptyOnlineDdlCoverage() {
  return {
    required: false,
    checkedStatements: 0,
    onlineStatements: 0,
    blockingStatements: 0,
    onlineIndexes: [],
    blockingIndexes: [],
    statementDigests: [],
  };
}

function refreshOnlineDdlCoverage(artifact) {
  for (const location of artifact.locations) {
    location.onlineDdlCoverage = summarizeOnlineDdlCoverage(location.migrations);
  }
  artifact.summary.onlineDdlCoverage = summarizeOnlineDdlCoverage(artifact.locations.flatMap((location) => location.migrations));
}

function summarizeOnlineDdlCoverage(migrations) {
  const coverage = {
    requiredMigrations: 0,
    checkedStatements: 0,
    onlineStatements: 0,
    blockingStatements: 0,
    onlineIndexes: [],
    blockingIndexes: [],
  };
  const onlineIndexes = new Set();
  const blockingIndexes = new Set();
  for (const migration of migrations) {
    const migrationCoverage = migration.onlineDdlCoverage || {};
    if (migrationCoverage.required) {
      coverage.requiredMigrations += 1;
    }
    coverage.checkedStatements += Number(migrationCoverage.checkedStatements) || 0;
    coverage.onlineStatements += Number(migrationCoverage.onlineStatements) || 0;
    coverage.blockingStatements += Number(migrationCoverage.blockingStatements) || 0;
    for (const indexName of migrationCoverage.onlineIndexes || []) {
      onlineIndexes.add(indexName);
    }
    for (const indexName of migrationCoverage.blockingIndexes || []) {
      blockingIndexes.add(indexName);
    }
  }
  coverage.onlineIndexes = [...onlineIndexes].sort();
  coverage.blockingIndexes = [...blockingIndexes].sort();
  return coverage;
}

function buildRuntimeProofs(runtime) {
  return requiredMigrationRuntimeProofs.map((proof) => ({
    id: proof.id,
    label: proof.label,
    validated: proof.id === "fresh-database" ? runtime.freshDatabaseValidated : runtime.upgradeDatabaseValidated,
    evidence: proof.id === "fresh-database" ? runtime.freshDatabaseEvidence : runtime.upgradeDatabaseEvidence,
    requiredEnvKeys: proof.envKeys,
    requiredEvidence: `${proof.label} evidence artifact`,
  }));
}

function buildRuntimeDiagnostics(runtime) {
  return requiredMigrationRuntimeDiagnostics.map((diagnostic) => ({
    id: diagnostic.id,
    owner: diagnostic.owner,
    status: diagnosticStatus(diagnostic.id, runtime),
    action: `${diagnostic.id} action`,
    envKeys: diagnostic.envKeys,
    evidence: diagnosticEvidence(diagnostic.id, runtime),
  }));
}

function diagnosticStatus(id, runtime) {
  if (id === "fresh-database-drill") {
    return runtime.freshDatabaseValidated ? "PASS" : "MISSING";
  }
  if (id === "upgrade-database-drill") {
    return runtime.upgradeDatabaseValidated ? "PASS" : "MISSING";
  }
  return diagnosticEvidence(id, runtime) ? "PASS" : "MISSING";
}

function diagnosticEvidence(id, runtime) {
  if (id === "fresh-database-drill") {
    return runtime.freshDatabaseEvidence;
  }
  if (id === "upgrade-database-drill") {
    return runtime.upgradeDatabaseEvidence;
  }
  if (id === "migration-environment") {
    return runtime.environment;
  }
  if (id === "migration-release-candidate") {
    return runtime.releaseCandidate;
  }
  if (id === "migration-operator") {
    return runtime.operator;
  }
  if (id === "migration-completed-at") {
    return runtime.completedAt;
  }
  return null;
}

assert.deepEqual(validateMigrationEvidenceContract(validArtifact(), { strict: true }), []);

assert.deepEqual(validateMigrationEvidenceContract({
  ...validArtifact(),
  generatedAt: "bad",
}, { strict: true }), [
  "generatedAt must be an ISO timestamp",
]);

assert.deepEqual(validateMigrationEvidenceContract({
  ...validArtifact(),
  status: "FAIL",
}, { strict: true }), [
  "status=FAIL",
]);

{
  const artifact = validArtifact();
  artifact.runtime.freshDatabaseValidated = false;
  artifact.runtime.upgradeDatabaseValidated = false;
  artifact.runtimeProofs = buildRuntimeProofs(artifact.runtime);
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  assert.deepEqual(validateMigrationEvidenceContract(artifact, { strict: true }), [
    "migration summary runtimeReady mismatch: declared=true, actual=false",
    "strict release requires fresh database migration validation",
    "strict release requires old database upgrade migration validation",
  ]);
}

{
  const artifact = validArtifact();
  artifact.runtime.releaseCandidate = "";
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  assert.deepEqual(validateMigrationEvidenceContract(artifact, { strict: true }), [
    "migration summary runtimeReady mismatch: declared=true, actual=false",
    "runtime.releaseCandidate is required",
  ]);
}

{
  const artifact = validArtifact();
  artifact.runtime.freshDatabaseEvidence = "operator confirmed fresh migration";
  artifact.runtimeProofs = buildRuntimeProofs(artifact.runtime);
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  assert.deepEqual(validateMigrationEvidenceContract(artifact, { strict: true }), [
    "migration summary runtimeReady mismatch: declared=true, actual=false",
    "runtime.freshDatabaseEvidence must include a concrete Flyway log, schema-history artifact path, object URI, HTTPS link, or ticket id",
  ]);
}

{
  const artifact = validArtifact();
  artifact.runtime.upgradeDatabaseEvidence = artifact.runtime.freshDatabaseEvidence;
  artifact.runtimeProofs = buildRuntimeProofs(artifact.runtime);
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  assert.deepEqual(validateMigrationEvidenceContract(artifact, { strict: true }), [
    "migration summary runtimeReady mismatch: declared=true, actual=false",
    "runtime.freshDatabaseEvidence and runtime.upgradeDatabaseEvidence must reference separate drill evidence",
  ]);
}

{
  const artifact = validArtifact();
  artifact.summary.duplicateVersionLocations = 1;
  artifact.summary.migrationFiles = 0;
  artifact.locations[0].migrationCount = 0;
  artifact.locations[0].duplicateVersions = ["1"];
  const issues = validateMigrationEvidenceContract(artifact, { strict: false });
  assert(issues.includes("duplicate version locations=1"));
  assert(issues.includes("no migration files in services/system-service/src/main/resources/db/migration"));
  assert(issues.includes("migration count mismatch in services/system-service/src/main/resources/db/migration: declared=0, actual=1"));
}

{
  const artifact = validArtifact();
  artifact.locations[0].migrations[0].onlineDdlCoverage = {
    required: true,
    checkedStatements: 1,
    onlineStatements: 0,
    blockingStatements: 1,
    onlineIndexes: [],
    blockingIndexes: ["idx_msg_notice_visible_recent"],
    statementDigests: ["c".repeat(64)],
  };
  refreshOnlineDdlCoverage(artifact);
  const issues = validateMigrationEvidenceContract(artifact, { strict: true });
  assert(issues.includes(`${requiredMigrationLocations[0]} migration ${requiredMigrationLocations[0]}/V1__baseline.sql has non-online msg_notice hot-path DDL statements=1`));
  assert(issues.includes("strict release blocks non-online msg_notice hot-path DDL statements=1"));
}

{
  const artifact = validArtifact();
  artifact.locations = artifact.locations.filter((location) => location.location !== requiredMigrationLocations[0]);
  artifact.summary.locations -= 1;
  artifact.summary.migrationFiles -= 1;
  assert(validateMigrationEvidenceContract(artifact, { strict: false })
    .includes(`missing required migration location report ${requiredMigrationLocations[0]}`));
}

{
  const artifact = validArtifact();
  artifact.locations.push({ ...artifact.locations[0] });
  artifact.summary.locations += 1;
  artifact.summary.migrationFiles += 1;
  assert(validateMigrationEvidenceContract(artifact, { strict: false })
    .includes(`duplicate migration location report ${requiredMigrationLocations[0]}`));
}

{
  const artifact = validArtifact();
  artifact.locations.push({
    location: "services/unknown-service/src/main/resources/db/migration",
    exists: true,
    migrationCount: 1,
    duplicateVersions: [],
    emptyFiles: [],
    migrations: [{
      version: "1",
      description: "baseline",
      file: "services/unknown-service/src/main/resources/db/migration/V1__baseline.sql",
      bytes: 100,
      sha256: "b".repeat(64),
    }],
  });
  artifact.summary.locations += 1;
  artifact.summary.migrationFiles += 1;
  assert(validateMigrationEvidenceContract(artifact, { strict: false })
    .includes("unknown migration location services/unknown-service/src/main/resources/db/migration"));
}

{
  const artifact = validArtifact();
  artifact.locations[0].migrationCount = 2;
  artifact.locations[0].migrations[0] = {
    version: "",
    description: "",
    file: "",
    bytes: 0,
    sha256: "not-a-sha",
  };
  const issues = validateMigrationEvidenceContract(artifact, { strict: false });
  assert(issues.includes(`migration count mismatch in ${requiredMigrationLocations[0]}: declared=2, actual=1`));
  assert(issues.includes(`${requiredMigrationLocations[0]} migration <unknown> version is required`));
  assert(issues.includes(`${requiredMigrationLocations[0]} migration <unknown> description is required`));
  assert(issues.includes(`${requiredMigrationLocations[0]} migration file path is required`));
  assert(issues.includes(`${requiredMigrationLocations[0]} migration <unknown> bytes must be positive`));
  assert(issues.includes(`${requiredMigrationLocations[0]} migration <unknown> sha256 must be 64 hex characters`));
}

{
  const artifact = validArtifact();
  artifact.summary = {
    ...artifact.summary,
    locations: 0,
    migrationFiles: 0,
    duplicateVersionLocations: 1,
    emptyFiles: 1,
  };
  const issues = validateMigrationEvidenceContract(artifact, { strict: false });
  assert(issues.includes(`migration summary locations mismatch: declared=0, actual=${requiredMigrationLocations.length}`));
  assert(issues.includes(`migration summary migrationFiles mismatch: declared=0, actual=${requiredMigrationLocations.length}`));
  assert(issues.includes("migration summary duplicateVersionLocations mismatch: declared=1, actual=0"));
  assert(issues.includes("migration summary emptyFiles mismatch: declared=1, actual=0"));
}

{
  const artifact = validArtifact();
  artifact.summary.runtimeReady = true;
  artifact.runtime.freshDatabaseValidated = false;
  artifact.runtimeProofs = buildRuntimeProofs(artifact.runtime);
  artifact.runtimeDiagnostics = buildRuntimeDiagnostics(artifact.runtime);
  const issues = validateMigrationEvidenceContract(artifact, { strict: true });
  assert(issues.includes("migration summary runtimeReady mismatch: declared=true, actual=false"));
  assert(issues.includes("strict release requires fresh database migration validation"));
}

{
  const artifact = validArtifact();
  artifact.runtimeProofs[0].evidence = "wrong evidence";
  artifact.runtimeDiagnostics[0].status = "PASS";
  artifact.runtimeDiagnostics.push({ ...artifact.runtimeDiagnostics[0] });
  const issues = validateMigrationEvidenceContract(artifact, { strict: false });
  assert(issues.includes("fresh-database migration runtime proof evidence must match runtime evidence"));
  assert(issues.includes("duplicate migration runtime diagnostic fresh-database-drill"));
}

console.log("[ddd-migration-evidence-contract.test] ok");
