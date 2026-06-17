import { evidenceValueIssue, isIsoTimestamp } from "./ddd-release-evidence-utils.mjs";

export const requiredMigrationLocations = Object.freeze([
  "services/system-service/src/main/resources/db/migration",
  "services/auth-service/src/main/resources/db/migration/auth",
  "services/message-service/src/main/resources/db/migration/message",
  "services/file-service/src/main/resources/db/migration/file",
  "services/plugin-service/src/main/resources/db/migration/plugin",
  "services/localization-service/src/main/resources/db/migration/localization",
  "services/payment-service/src/main/resources/db/migration/payment",
  "services/ai-service/src/main/resources/db/migration/ai",
]);

export const requiredMigrationRuntimeProofs = Object.freeze([
  {
    id: "fresh-database",
    label: "Fresh database Flyway drill",
    envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
  },
  {
    id: "upgrade-database",
    label: "Previous schema upgrade Flyway drill",
    envKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
  },
]);

export const requiredMigrationRuntimeDiagnostics = Object.freeze([
  {
    id: "fresh-database-drill",
    owner: "database",
    envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
  },
  {
    id: "upgrade-database-drill",
    owner: "database",
    envKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
  },
  {
    id: "migration-environment",
    owner: "release-infra",
    envKeys: ["DDD_MIGRATION_ENVIRONMENT", "DDD_EVIDENCE_ENVIRONMENT", "DDD_RELEASE_ENVIRONMENT"],
  },
  {
    id: "migration-release-candidate",
    owner: "release-infra",
    envKeys: ["DDD_RELEASE_CANDIDATE", "GITHUB_SHA"],
  },
  {
    id: "migration-operator",
    owner: "release-owner",
    envKeys: ["DDD_MIGRATION_OPERATOR", "DDD_EVIDENCE_OPERATOR", "GITHUB_ACTOR"],
  },
  {
    id: "migration-completed-at",
    owner: "release-owner",
    envKeys: ["DDD_MIGRATION_COMPLETED_AT"],
  },
]);

export const requiredMigrationEvidenceChecklist = Object.freeze([
  {
    id: "fresh-database-evidence-package",
    owner: "database",
    drill: "fresh-database",
    requiredEnvKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
    requiredArtifacts: [
      "Flyway migrate log from an empty production-equivalent database.",
      "Schema history export after the fresh migration completes.",
      "Database product/version and migration command provenance.",
    ],
    acceptanceCriteria: [
      "DDD_MIGRATION_FRESH_DB_VALIDATED=true is set only after the drill succeeds.",
      "DDD_MIGRATION_FRESH_DB_EVIDENCE points to the fresh database evidence package.",
      "Fresh database evidence is separate from previous-schema upgrade evidence.",
    ],
  },
  {
    id: "previous-schema-upgrade-evidence-package",
    owner: "database",
    drill: "previous-schema-upgrade",
    requiredEnvKeys: ["DDD_MIGRATION_UPGRADE_DB_VALIDATED", "DDD_MIGRATION_UPGRADE_DB_EVIDENCE"],
    requiredArtifacts: [
      "Before/after schema history export from a copy of the previous production schema.",
      "Flyway migrate log for the upgrade drill.",
      "Rollback or restore point reference for the copied previous-schema database.",
    ],
    acceptanceCriteria: [
      "DDD_MIGRATION_UPGRADE_DB_VALIDATED=true is set only after the upgrade drill succeeds.",
      "DDD_MIGRATION_UPGRADE_DB_EVIDENCE points to the previous-schema upgrade evidence package.",
      "Upgrade evidence references a previous-schema source, not a fresh empty database.",
    ],
  },
]);

export const onlineDdlRequiredIndexes = Object.freeze([
  "idx_msg_notice_visible_recent",
  "idx_msg_notice_visible_target_user_recent",
  "idx_msg_notice_visible_target_role_recent",
]);

export function validateMigrationEvidenceContract(artifact, { strict = false, checkStatus = true } = {}) {
  const issues = [];
  const locations = Array.isArray(artifact?.locations) ? artifact.locations : [];
  const actualMigrationFiles = locations.reduce((total, location) => total + (Number(location.migrationCount) || 0), 0);
  const actualDuplicateVersionLocations = locations
    .filter((location) => (location.duplicateVersions || []).length > 0).length;
  const actualEmptyFiles = locations
    .reduce((total, location) => total + (location.emptyFiles || []).length, 0);
  const actualOnlineDdlCoverage = summarizeOnlineDdlCoverage(locations.flatMap((location) => location.migrations || []));
  const locationCounts = countValues(locations.map((location) => location.location).filter(Boolean));
  const requiredLocationSet = new Set(requiredMigrationLocations);

  if (checkStatus && artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (!isIsoTimestamp(artifact?.generatedAt)) {
    issues.push("generatedAt must be an ISO timestamp");
  }
  if ((artifact?.summary?.locations || 0) !== locations.length) {
    issues.push(`migration summary locations mismatch: declared=${artifact?.summary?.locations || 0}, actual=${locations.length}`);
  }
  if ((artifact?.summary?.migrationFiles || 0) !== actualMigrationFiles) {
    issues.push(`migration summary migrationFiles mismatch: declared=${artifact?.summary?.migrationFiles || 0}, actual=${actualMigrationFiles}`);
  }
  if ((artifact?.summary?.duplicateVersionLocations || 0) !== actualDuplicateVersionLocations) {
    issues.push(`migration summary duplicateVersionLocations mismatch: declared=${artifact?.summary?.duplicateVersionLocations || 0}, actual=${actualDuplicateVersionLocations}`);
  }
  if ((artifact?.summary?.emptyFiles || 0) !== actualEmptyFiles) {
    issues.push(`migration summary emptyFiles mismatch: declared=${artifact?.summary?.emptyFiles || 0}, actual=${actualEmptyFiles}`);
  }
  validateOnlineDdlCoverage(
    artifact?.summary?.onlineDdlCoverage,
    actualOnlineDdlCoverage,
    "migration summary onlineDdlCoverage",
    issues,
  );
  if ((artifact?.summary?.duplicateVersionLocations || 0) > 0) {
    issues.push(`duplicate version locations=${artifact.summary.duplicateVersionLocations}`);
  }
  if ((artifact?.summary?.emptyFiles || 0) > 0) {
    issues.push(`empty migration files=${artifact.summary.emptyFiles}`);
  }

  for (const location of locations) {
    if (!requiredLocationSet.has(location.location)) {
      issues.push(`unknown migration location ${location.location || "missing"}`);
    }
    if (!location.exists) {
      issues.push(`missing migration location ${location.location}`);
    }
    if ((location.migrationCount || 0) === 0) {
      issues.push(`no migration files in ${location.location}`);
    }
    if (Array.isArray(location.migrations) && location.migrationCount !== location.migrations.length) {
      issues.push(`migration count mismatch in ${location.location}: declared=${location.migrationCount || 0}, actual=${location.migrations.length}`);
    }
    validateOnlineDdlCoverage(
      location.onlineDdlCoverage,
      summarizeOnlineDdlCoverage(Array.isArray(location.migrations) ? location.migrations : []),
      `${location.location} onlineDdlCoverage`,
      issues,
    );
    for (const migration of Array.isArray(location.migrations) ? location.migrations : []) {
      if (!migration.version) {
        issues.push(`${location.location} migration ${migration.file || "<unknown>"} version is required`);
      }
      if (!migration.description) {
        issues.push(`${location.location} migration ${migration.file || "<unknown>"} description is required`);
      }
      if (!migration.file) {
        issues.push(`${location.location} migration file path is required`);
      }
      if (!Number.isFinite(Number(migration.bytes)) || Number(migration.bytes) <= 0) {
        issues.push(`${location.location} migration ${migration.file || "<unknown>"} bytes must be positive`);
      }
      if (!/^[a-f0-9]{64}$/i.test(String(migration.sha256 || ""))) {
        issues.push(`${location.location} migration ${migration.file || "<unknown>"} sha256 must be 64 hex characters`);
      }
      validateMigrationOnlineDdlCoverage(location.location, migration, issues);
    }
  }
  for (const requiredLocation of requiredMigrationLocations) {
    if (!locationCounts.has(requiredLocation)) {
      issues.push(`missing required migration location report ${requiredLocation}`);
    }
  }
  for (const [location, count] of locationCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate migration location report ${location}`);
    }
  }

  validateRuntimeProofs(artifact, issues);
  validateRuntimeDiagnostics(artifact, issues);

  if (!strict) {
    return issues;
  }

  const runtime = artifact?.runtime || {};
  const runtimeIssues = [];
  if (runtime.freshDatabaseValidated !== true) {
    runtimeIssues.push("strict release requires fresh database migration validation");
  }
  if (runtime.upgradeDatabaseValidated !== true) {
    runtimeIssues.push("strict release requires old database upgrade migration validation");
  }
  requireRealText(runtime.environment, "runtime.environment", runtimeIssues);
  requireRealText(runtime.releaseCandidate, "runtime.releaseCandidate", runtimeIssues);
  requireRealText(runtime.operator, "runtime.operator", runtimeIssues);
  if (!isIsoTimestamp(runtime.completedAt)) {
    runtimeIssues.push("runtime.completedAt must be an ISO timestamp");
  }
  requireRealText(runtime.freshDatabaseEvidence, "runtime.freshDatabaseEvidence", runtimeIssues);
  requireRealText(runtime.upgradeDatabaseEvidence, "runtime.upgradeDatabaseEvidence", runtimeIssues);
  requireEvidenceReference(runtime.freshDatabaseEvidence, "runtime.freshDatabaseEvidence", runtimeIssues);
  requireEvidenceReference(runtime.upgradeDatabaseEvidence, "runtime.upgradeDatabaseEvidence", runtimeIssues);
  if (runtime.freshDatabaseEvidence
    && runtime.upgradeDatabaseEvidence
    && String(runtime.freshDatabaseEvidence).trim() === String(runtime.upgradeDatabaseEvidence).trim()) {
    runtimeIssues.push("runtime.freshDatabaseEvidence and runtime.upgradeDatabaseEvidence must reference separate drill evidence");
  }
  if ((artifact?.summary?.runtimeReady === true) !== (runtimeIssues.length === 0)) {
    issues.push(`migration summary runtimeReady mismatch: declared=${artifact?.summary?.runtimeReady === true}, actual=${runtimeIssues.length === 0}`);
  }
  if (actualOnlineDdlCoverage.checkedStatements === 0) {
    issues.push("strict release requires online DDL evidence for msg_notice hot-path indexes");
  }
  if (actualOnlineDdlCoverage.blockingStatements > 0) {
    issues.push(`strict release blocks non-online msg_notice hot-path DDL statements=${actualOnlineDdlCoverage.blockingStatements}`);
  }
  issues.push(...runtimeIssues);

  return issues;
}

function validateMigrationOnlineDdlCoverage(location, migration, issues) {
  const coverage = migration.onlineDdlCoverage;
  if (!coverage) {
    return;
  }
  if (coverage.required !== ((Number(coverage.checkedStatements) || 0) > 0)) {
    issues.push(`${location} migration ${migration.file || "<unknown>"} onlineDdlCoverage required mismatch`);
  }
  if ((Number(coverage.onlineStatements) || 0) + (Number(coverage.blockingStatements) || 0) !== (Number(coverage.checkedStatements) || 0)) {
    issues.push(`${location} migration ${migration.file || "<unknown>"} onlineDdlCoverage statement totals mismatch`);
  }
  for (const indexName of [...(coverage.onlineIndexes || []), ...(coverage.blockingIndexes || [])]) {
    if (!onlineDdlRequiredIndexes.includes(indexName)) {
      issues.push(`${location} migration ${migration.file || "<unknown>"} unknown online DDL index ${indexName}`);
    }
  }
  if ((Number(coverage.blockingStatements) || 0) > 0) {
    issues.push(`${location} migration ${migration.file || "<unknown>"} has non-online msg_notice hot-path DDL statements=${coverage.blockingStatements}`);
  }
}

function validateOnlineDdlCoverage(declared, actual, label, issues) {
  if (!declared) {
    issues.push(`${label} is required`);
    return;
  }
  for (const field of ["requiredMigrations", "checkedStatements", "onlineStatements", "blockingStatements"]) {
    if ((Number(declared[field]) || 0) !== actual[field]) {
      issues.push(`${label} ${field} mismatch: declared=${Number(declared[field]) || 0}, actual=${actual[field]}`);
    }
  }
  for (const field of ["onlineIndexes", "blockingIndexes"]) {
    const declaredIndexes = [...(declared[field] || [])].sort();
    if (JSON.stringify(declaredIndexes) !== JSON.stringify(actual[field])) {
      issues.push(`${label} ${field} mismatch: declared=${declaredIndexes.join(",") || "<none>"}, actual=${actual[field].join(",") || "<none>"}`);
    }
  }
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

function validateRuntimeProofs(artifact, issues) {
  const proofs = Array.isArray(artifact?.runtimeProofs) ? artifact.runtimeProofs : [];
  const proofCounts = countValues(proofs.map((proof) => proof.id).filter(Boolean));
  for (const required of requiredMigrationRuntimeProofs) {
    const proof = proofs.find((entry) => entry.id === required.id);
    if (!proof) {
      issues.push(`missing migration runtime proof ${required.id}`);
      continue;
    }
    if (proof.label !== required.label) {
      issues.push(`${required.id} migration runtime proof label mismatch`);
    }
    if (JSON.stringify(proof.requiredEnvKeys || []) !== JSON.stringify(required.envKeys)) {
      issues.push(`${required.id} migration runtime proof envKeys mismatch`);
    }
    if (typeof proof.requiredEvidence !== "string" || proof.requiredEvidence.trim().length === 0) {
      issues.push(`${required.id} migration runtime proof requiredEvidence is required`);
    }
    const expectedValidated = required.id === "fresh-database"
      ? artifact?.runtime?.freshDatabaseValidated === true
      : artifact?.runtime?.upgradeDatabaseValidated === true;
    if (proof.validated !== expectedValidated) {
      issues.push(`${required.id} migration runtime proof validated must be ${expectedValidated}`);
    }
    const expectedEvidence = required.id === "fresh-database"
      ? artifact?.runtime?.freshDatabaseEvidence || null
      : artifact?.runtime?.upgradeDatabaseEvidence || null;
    if ((proof.evidence || null) !== expectedEvidence) {
      issues.push(`${required.id} migration runtime proof evidence must match runtime evidence`);
    }
  }
  for (const proof of proofs) {
    if (!requiredMigrationRuntimeProofs.some((required) => required.id === proof.id)) {
      issues.push(`unknown migration runtime proof ${proof.id || "missing"}`);
    }
  }
  for (const [id, count] of proofCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate migration runtime proof ${id}`);
    }
  }
}

function validateRuntimeDiagnostics(artifact, issues) {
  const diagnostics = Array.isArray(artifact?.runtimeDiagnostics) ? artifact.runtimeDiagnostics : [];
  const diagnosticCounts = countValues(diagnostics.map((diagnostic) => diagnostic.id).filter(Boolean));
  for (const required of requiredMigrationRuntimeDiagnostics) {
    const diagnostic = diagnostics.find((entry) => entry.id === required.id);
    if (!diagnostic) {
      issues.push(`missing migration runtime diagnostic ${required.id}`);
      continue;
    }
    if (diagnostic.owner !== required.owner) {
      issues.push(`${required.id} migration runtime diagnostic owner must be ${required.owner}`);
    }
    if (JSON.stringify(diagnostic.envKeys || []) !== JSON.stringify(required.envKeys)) {
      issues.push(`${required.id} migration runtime diagnostic envKeys mismatch`);
    }
    if (typeof diagnostic.action !== "string" || diagnostic.action.trim().length === 0) {
      issues.push(`${required.id} migration runtime diagnostic action is required`);
    }
    const expectedStatus = migrationRuntimeDiagnosticStatus(required.id, artifact?.runtime || {});
    if (diagnostic.status !== expectedStatus) {
      issues.push(`${required.id} migration runtime diagnostic status must be ${expectedStatus}`);
    }
    const expectedEvidence = migrationRuntimeDiagnosticEvidence(required.id, artifact?.runtime || {});
    if ((diagnostic.evidence || null) !== expectedEvidence) {
      issues.push(`${required.id} migration runtime diagnostic evidence must match runtime evidence`);
    }
  }
  for (const diagnostic of diagnostics) {
    if (!requiredMigrationRuntimeDiagnostics.some((required) => required.id === diagnostic.id)) {
      issues.push(`unknown migration runtime diagnostic ${diagnostic.id || "missing"}`);
    }
  }
  for (const [id, count] of diagnosticCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate migration runtime diagnostic ${id}`);
    }
  }
}

function migrationRuntimeDiagnosticStatus(id, runtime) {
  if (id === "fresh-database-drill") {
    return runtime.freshDatabaseValidated === true ? "PASS" : "MISSING";
  }
  if (id === "upgrade-database-drill") {
    return runtime.upgradeDatabaseValidated === true ? "PASS" : "MISSING";
  }
  return evidenceValueIssue(migrationRuntimeDiagnosticEvidence(id, runtime)) ? "MISSING" : "PASS";
}

function migrationRuntimeDiagnosticEvidence(id, runtime) {
  if (id === "fresh-database-drill") {
    return runtime.freshDatabaseEvidence || null;
  }
  if (id === "upgrade-database-drill") {
    return runtime.upgradeDatabaseEvidence || null;
  }
  if (id === "migration-environment") {
    return runtime.environment || null;
  }
  if (id === "migration-release-candidate") {
    return runtime.releaseCandidate || null;
  }
  if (id === "migration-operator") {
    return runtime.operator || null;
  }
  if (id === "migration-completed-at") {
    return runtime.completedAt || null;
  }
  return null;
}

function requireRealText(value, label, issues) {
  const issue = evidenceValueIssue(value);
  if (issue) {
    issues.push(`${label} ${issue}`);
  }
}

function requireEvidenceReference(value, label, issues) {
  if (typeof value !== "string") {
    return;
  }
  const trimmed = value.trim();
  const hasReference = /^https:\/\//i.test(trimmed)
    || /^(s3|gs):\/\//i.test(trimmed)
    || /\b[A-Z][A-Z0-9]+-\d+\b/.test(trimmed)
    || /(^|[\s:])(\.?\.?\/)?(artifacts|logs|reports|tmp|docs)\//i.test(trimmed)
    || /\/[A-Za-z0-9._-]+/.test(trimmed);
  if (!hasReference) {
    issues.push(`${label} must include a concrete Flyway log, schema-history artifact path, object URI, HTTPS link, or ticket id`);
  }
}

function countValues(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}
