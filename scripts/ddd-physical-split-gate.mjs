#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { buildPhysicalSplitSummary } from "./ddd-physical-split-contract.mjs";
import { collectProvenanceIssues } from "./ddd-release-evidence-utils.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputFile = process.env.DDD_SPLIT_READINESS_FILE
  ? path.resolve(process.env.DDD_SPLIT_READINESS_FILE)
  : path.join(repoRoot, "artifacts", "ddd", "split", "physical-split-readiness.json");
const strict = process.env.DDD_SPLIT_STRICT === "true";
const providedSourceEnvironment = process.env.DDD_SPLIT_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const providedReleaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const providedEvidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const previousArtifact = loadPreviousArtifact(outputFile);
const advisoryOverwriteAllowed = process.env.DDD_SPLIT_ALLOW_ADVISORY_OVERWRITE === "true";
const preserveStrictArtifact = !strict && previousArtifact?.strict === true && !advisoryOverwriteAllowed;
const reportFile = preserveStrictArtifact
  ? advisoryOutputFile(outputFile)
  : outputFile;
const sourceEnvironment = providedSourceEnvironment || previousArtifact?.sourceEnvironment || "";
const releaseCandidate = providedReleaseCandidate || previousArtifact?.releaseCandidate || "";
const evidenceOperator = providedEvidenceOperator || previousArtifact?.evidenceOperator || "";

const contexts = [
  {
    name: "IAM",
    module: "services/system-service",
    route: "/api/v2/iam",
    ownerContext: "IAM",
    internalContracts: [],
    physicalServiceTarget: false,
  },
  {
    name: "Auth",
    module: "services/auth-service",
    route: "/api/v2/auth",
    ownerContext: "AUTH",
    internalContracts: ["AuthInternalController.currentUserBySessionId"],
    physicalServiceTarget: true,
    requiredMigrationTables: [
      "sys_user_passkey_credential",
      "sys_user_wechat_binding",
      "sys_verification_binding",
      "sys_verification_challenge",
    ],
  },
  {
    name: "Platform",
    module: "services/system-service",
    route: "/api/v2/platform",
    ownerContext: "PLATFORM",
    internalContracts: ["SystemInternalApi"],
    physicalServiceTarget: false,
  },
  {
    name: "Message",
    module: "services/message-service",
    route: "/api/v2/message",
    ownerContext: "MESSAGE",
    internalContracts: ["/message/internal/jobs/outbox/relay", "/message/internal/jobs/outbox/{id}/replay"],
    physicalServiceTarget: true,
    requiredMigrationTables: ["msg_notice", "msg_notice_read", "msg_delivery_log", "platform_event_outbox"],
  },
  {
    name: "File",
    module: "services/file-service",
    route: "/api/v2/files",
    ownerContext: "FILE",
    internalContracts: ["/file/internal/jobs/outbox/relay", "/file/internal/jobs/outbox/{id}/replay", "/file/internal/jobs/processing/run"],
    physicalServiceTarget: true,
    requiredMigrationTables: ["file_object", "file_storage_space", "file_processing_task", "file_processing_artifact", "platform_event_outbox"],
  },
  {
    name: "Plugin",
    module: "services/plugin-service",
    route: "/api/v2/plugins",
    ownerContext: "PLUGIN",
    internalContracts: ["/plugin/internal/jobs/outbox/relay", "/plugin/internal/jobs/outbox/{id}/replay"],
    physicalServiceTarget: true,
    requiredMigrationTables: ["sys_plugin_definition", "sys_plugin_version", "sys_plugin_tenant", "plugin_event_outbox"],
  },
  {
    name: "Localization",
    module: "services/localization-service",
    route: "/api/v2/localization",
    ownerContext: "LOCALIZATION",
    internalContracts: [],
    physicalServiceTarget: true,
    requiredMigrationTables: [
      "sys_localization_language",
      "sys_localization_namespace",
      "sys_localization_entry",
      "sys_localization_translation",
      "sys_localization_release",
      "sys_localization_usage_ref",
    ],
  },
  {
    name: "Payment",
    module: "services/payment-service",
    route: "/api/v2/payment",
    ownerContext: "PAYMENT",
    internalContracts: ["/payment/internal/jobs/outbox/relay", "/payment/internal/jobs/outbox/{id}/replay"],
    physicalServiceTarget: true,
    requiredMigrationTables: ["payment_provider_config", "payment_order", "payment_refund", "payment_webhook_event", "payment_event_outbox"],
  },
  {
    name: "AI",
    module: "services/ai-service",
    route: "/api/v2/ai",
    ownerContext: "AI",
    internalContracts: ["/internal/jobs/ai/knowledge-index"],
    physicalServiceTarget: true,
    requiresBusinessController: true,
    requiredMigrationTables: [
      "ai_employee",
      "ai_conversation",
      "ai_message",
      "ai_knowledge_base",
      "ai_knowledge_document",
      "ai_knowledge_chunk",
      "ai_tool_call_plan",
      "ai_tool_audit_log",
    ],
    requiredBusinessEndpoints: [
      { method: "Get", path: "/employees" },
      { method: "Get", path: "/assistant" },
      { method: "Get", path: "/conversations" },
      { method: "Get", path: "/conversations/{id}/messages" },
      { method: "Post", path: "/chat" },
      { method: "Get", path: "/knowledge-bases" },
      { method: "Get", path: "/knowledge-bases/{id}" },
      { method: "Get", path: "/knowledge-bases/{id}/documents" },
      { method: "Post", path: "/knowledge-bases/{id}/documents/upload" },
      { method: "Post", path: "/knowledge-bases/{id}/documents/{documentId}/reindex" },
      { method: "Post", path: "/knowledge-bases/search" },
      { method: "Get", path: "/tools" },
      { method: "Post", path: "/tools/execute" },
      { method: "Post", path: "/tools/propose" },
      { method: "Post", path: "/tools/confirm" },
    ],
  },
  {
    name: "Job",
    module: "services/job-executor",
    route: "/api/v2/job",
    ownerContext: "JOB",
    internalContracts: [],
    physicalServiceTarget: false,
  },
];

const splitGateRows = [
  "Owner 表",
  "API 契约",
  "事件契约",
  "读模型",
  "缓存失效",
  "配置密钥",
  "健康检查",
  "观测指标",
  "回滚方案",
  "兼容窗口",
];

const allowedServiceDependencies = new Set(["lumira-server"]);
const serviceArtifacts = new Set([
  "auth-service",
  "file-service",
  "job-executor",
  "localization-service",
  "message-service",
  "payment-service",
  "plugin-service",
  "system-service",
  "ai-service",
]);

const failures = [];
const report = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  strict,
  summary: {
    contexts: contexts.length,
    failures: 0,
    blockers: 0,
    warnings: 0,
  },
  contexts: [],
  globalChecks: [],
};

function loadPreviousArtifact(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

function advisoryOutputFile(file) {
  const parsed = path.parse(file);
  return path.join(parsed.dir, `${parsed.name}.advisory${parsed.ext || ".json"}`);
}

function fail(message) {
  failures.push(message);
}

if (strict) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    fail(`physical split provenance ${issue}`);
  }
}

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return fs.existsSync(path.join(repoRoot, relativePath));
}

function findFiles(directory, predicate, collected = []) {
  const absoluteDir = path.join(repoRoot, directory);
  if (!fs.existsSync(absoluteDir)) {
    return collected;
  }
  for (const entry of fs.readdirSync(absoluteDir, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "target" || entry.name === "node_modules") {
        continue;
      }
      findFiles(fullPath, predicate, collected);
    } else if (entry.isFile() && predicate(fullPath)) {
      collected.push(fullPath);
    }
  }
  return collected;
}

function recordGlobal(name, status, detail) {
  report.globalChecks.push({ name, status, detail });
  if (status === "fail") {
    fail(`${name}: ${detail}`);
  }
}

function parseOwnerManifest() {
  const manifestPath = "docs/27-ddd-owner-table-manifest.csv";
  if (!exists(manifestPath)) {
    recordGlobal("owner-manifest", "fail", `${manifestPath} missing`);
    return new Map();
  }
  const rows = read(manifestPath)
    .trim()
    .split(/\r?\n/)
    .slice(1)
    .map((line) => line.split(","));
  const byContext = new Map();
  for (const row of rows) {
    byContext.set(row[0], {
      context: row[0],
      ownerModule: row[1],
      ownedTablePatterns: row[2],
      compatibleWriterModules: row[3],
      notes: row.slice(4).join(","),
    });
  }
  return byContext;
}

function pomArtifactIds(modulePath) {
  const pomPath = path.join(modulePath, "pom.xml");
  if (!exists(pomPath)) {
    return [];
  }
  const pom = read(pomPath);
  return [...pom.matchAll(/<artifactId>([^<]+)<\/artifactId>/g)].map((match) => match[1]);
}

function pomHasArtifact(modulePath, artifactId) {
  return pomArtifactIds(modulePath).includes(artifactId);
}

function hasSpringBootApplication(modulePath) {
  return findFiles(path.join(modulePath, "src", "main", "java"), (file) => file.endsWith(".java"))
    .some((file) => read(file).includes("@SpringBootApplication") && read(file).includes("SpringApplication.run"));
}

function moduleCrossServiceDependencies(modulePath) {
  return pomArtifactIds(modulePath)
    .filter((artifactId) => serviceArtifacts.has(artifactId))
    .filter((artifactId) => !allowedServiceDependencies.has(path.basename(modulePath)))
    .filter((artifactId) => artifactId !== path.basename(modulePath));
}

function controllerPathFor(context) {
  return findFiles(path.join(context.module, "src", "main", "java"), (file) => file.endsWith("ReadinessV2Controller.java"))
    .find((file) => read(file).includes(`@RequestMapping("${context.route}")`));
}

function businessControllerPathFor(context) {
  return findFiles(path.join(context.module, "src", "main", "java"), (file) => file.endsWith("Controller.java") && !file.endsWith("ReadinessV2Controller.java"))
    .find((file) => read(file).includes(`@RequestMapping("${context.route}")`));
}

function businessEndpointIsPresent(endpoint, controllerText) {
  const mapping = `@${endpoint.method}Mapping("${endpoint.path}")`;
  const valueMapping = `@${endpoint.method}Mapping(value = "${endpoint.path}"`;
  return controllerText.includes(mapping) || controllerText.includes(valueMapping);
}

function serviceText(modulePath) {
  return findFiles(path.join(modulePath, "src", "main", "java"), (file) => file.endsWith(".java"))
    .map((file) => read(file))
    .join("\n");
}

function migrationFiles(modulePath) {
  return findFiles(path.join(modulePath, "src", "main", "resources", "db", "migration"), (file) => file.endsWith(".sql"));
}

function migrationText(modulePath) {
  return migrationFiles(modulePath)
    .map((file) => read(file))
    .join("\n");
}

function contractIsPresent(contract, ...texts) {
  if (texts.some((text) => text.includes(contract))) {
    return true;
  }
  const withoutQuery = contract.split("?")[0];
  if (withoutQuery !== contract && texts.some((text) => text.includes(withoutQuery))) {
    return true;
  }
  if (!contract.startsWith("/")) {
    return false;
  }
  const segments = withoutQuery.split("/").filter(Boolean);
  const tail = segments.at(-1);
  const parent = segments.slice(0, -1).join("/");
  return texts.some((text) => segments.every((segment) => text.includes(segment)))
    || texts.some((text) => tail && text.includes(`/${tail}`) && text.includes(parent.split("/")[0]));
}

const ownerManifest = parseOwnerManifest();
const splitDoc = exists("docs/29-ddd-physical-split-readiness.md")
  ? read("docs/29-ddd-physical-split-readiness.md")
  : "";
const runbook = exists("docs/31-ddd-operational-runbook.md")
  ? read("docs/31-ddd-operational-runbook.md")
  : "";

recordGlobal(
  "split-gate-document",
  splitDoc ? "pass" : "fail",
  splitDoc ? "docs/29-ddd-physical-split-readiness.md present" : "docs/29-ddd-physical-split-readiness.md missing"
);

for (const rowName of splitGateRows) {
  if (!splitDoc.includes(`| ${rowName} |`)) {
    recordGlobal("split-gate-row", "fail", `missing global split gate row: ${rowName}`);
  }
}

recordGlobal(
  "architecture-boundary-test",
  exists("services/system-service/src/test/java/com/lumira/saas/architecture/DddArchitectureBoundaryTest.java") ? "pass" : "fail",
  "DddArchitectureBoundaryTest guards owner writes and dependency boundaries"
);

for (const context of contexts) {
  const checks = [];
  const blockers = [];
  const warnings = [];
  const manifest = ownerManifest.get(context.ownerContext);
  const controller = controllerPathFor(context);
  const moduleText = serviceText(context.module);
  const moduleMigrationText = migrationText(context.module);
  const moduleMigrationFiles = migrationFiles(context.module);
  const hasStandaloneBoot = hasSpringBootApplication(context.module);
  const crossDeps = moduleCrossServiceDependencies(context.module);
  const businessController = businessControllerPathFor(context);
  const businessControllerText = businessController ? read(businessController) : "";
  const missingBusinessEndpoints = (context.requiredBusinessEndpoints || [])
    .filter((endpoint) => !businessEndpointIsPresent(endpoint, businessControllerText));

  if (!exists(context.module)) {
    checks.push({ name: "module", status: "fail", detail: `${context.module} missing` });
    fail(`${context.name}: module missing ${context.module}`);
  } else {
    checks.push({ name: "module", status: "pass", detail: context.module });
  }

  if (!manifest) {
    checks.push({ name: "owner-manifest", status: "fail", detail: `missing ${context.ownerContext}` });
    fail(`${context.name}: owner manifest missing ${context.ownerContext}`);
  } else {
    checks.push({ name: "owner-manifest", status: "pass", detail: `${manifest.ownerModule}: ${manifest.ownedTablePatterns}` });
  }

  if (!controller) {
    checks.push({ name: "readiness-controller", status: "fail", detail: `missing route ${context.route}` });
    fail(`${context.name}: readiness controller missing route ${context.route}`);
  } else {
    const controllerText = read(controller);
    for (const suffix of ["readiness", "health", "metrics"]) {
      const endpoint = `${context.route}/${suffix}`;
      if (!controllerText.includes(`@GetMapping("/${suffix}")`)) {
        checks.push({ name: `${suffix}-endpoint`, status: "fail", detail: endpoint });
        fail(`${context.name}: missing ${endpoint}`);
      } else {
        checks.push({ name: `${suffix}-endpoint`, status: "pass", detail: endpoint });
      }
    }
  }

  for (const contract of context.internalContracts) {
    if (contractIsPresent(contract, moduleText, runbook, splitDoc, serviceText("services/job-executor"))) {
      checks.push({ name: "internal-contract", status: "pass", detail: contract });
    } else {
      checks.push({ name: "internal-contract", status: "fail", detail: contract });
      fail(`${context.name}: internal contract not documented or implemented: ${contract}`);
    }
  }

  if (crossDeps.length > 0 && context.module !== "services/lumira-server") {
    checks.push({ name: "cross-service-pom-dependency", status: "fail", detail: crossDeps.join(", ") });
    fail(`${context.name}: direct service module dependency in pom: ${crossDeps.join(", ")}`);
  } else {
    checks.push({ name: "cross-service-pom-dependency", status: "pass", detail: "no direct service module dependency" });
  }

  if ((context.requiredMigrationTables || []).length > 0) {
    if (pomHasArtifact(context.module, "spring-boot-starter-flyway") && pomHasArtifact(context.module, "flyway-core")) {
      checks.push({ name: "owner-flyway-dependency", status: "pass", detail: "spring-boot-starter-flyway + flyway-core" });
    } else {
      checks.push({ name: "owner-flyway-dependency", status: "fail", detail: "missing Flyway dependencies" });
      fail(`${context.name}: owner module is missing Flyway dependencies`);
    }
    if (moduleMigrationFiles.length > 0) {
      checks.push({ name: "owner-migration-files", status: "pass", detail: moduleMigrationFiles.join(", ") });
    } else {
      checks.push({ name: "owner-migration-files", status: "fail", detail: "missing db/migration SQL files" });
      fail(`${context.name}: owner module is missing db/migration SQL files`);
    }
    for (const table of context.requiredMigrationTables) {
      if (moduleMigrationText.includes(`\`${table}\``) || moduleMigrationText.includes(` ${table} `)) {
        checks.push({ name: "owner-migration-table", status: "pass", detail: table });
      } else {
        checks.push({ name: "owner-migration-table", status: "fail", detail: table });
        fail(`${context.name}: owner migration missing table ${table}`);
      }
    }
  }

  if (context.physicalServiceTarget && !hasStandaloneBoot) {
    blockers.push("standalone Spring Boot application entrypoint is not present yet");
  }
  if (context.requiresBusinessController && !businessController) {
    blockers.push("standalone module does not yet expose the v2 business API implementation");
  }
  for (const endpoint of missingBusinessEndpoints) {
    blockers.push(`standalone v2 business endpoint is not migrated yet: ${endpoint.method.toUpperCase()} ${context.route}${endpoint.path}`);
  }
  if (context.module === "services/system-service" && context.physicalServiceTarget) {
    blockers.push("context is still co-located in system-service module");
  }
  if (!splitDoc.includes(`###`) || !splitDoc.includes(context.name)) {
    warnings.push("physical split document does not contain a context section");
  }
  if (!runbook.includes(context.name) || !runbook.includes(`${context.route}/readiness`)) {
    warnings.push("operational runbook does not contain readiness drill evidence reference");
  }

  report.contexts.push({
    name: context.name,
    module: context.module,
    route: context.route,
    ownerContext: context.ownerContext,
    physicalServiceTarget: context.physicalServiceTarget,
    standaloneBootApplication: hasStandaloneBoot,
    businessController: businessController || null,
    migrationFiles: moduleMigrationFiles,
    migratedBusinessEndpoints: (context.requiredBusinessEndpoints || [])
      .filter((endpoint) => businessEndpointIsPresent(endpoint, businessControllerText))
      .map((endpoint) => `${endpoint.method.toUpperCase()} ${context.route}${endpoint.path}`),
    missingBusinessEndpoints: missingBusinessEndpoints
      .map((endpoint) => `${endpoint.method.toUpperCase()} ${context.route}${endpoint.path}`),
    checks,
    blockers,
    warnings,
  });
}

report.summary = buildPhysicalSplitSummary(report);

fs.mkdirSync(path.dirname(reportFile), { recursive: true });
fs.writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`);

if (failures.length > 0) {
  for (const failure of failures) {
    console.error(`[ddd-physical-split-gate] ${failure}`);
  }
  console.error(`[ddd-physical-split-gate] wrote report to ${reportFile}`);
  process.exit(1);
}

if (strict && report.summary.blockers > 0) {
  for (const context of report.contexts) {
    for (const blocker of context.blockers) {
      console.error(`[ddd-physical-split-gate] ${context.name}: ${blocker}`);
    }
  }
  console.error(`[ddd-physical-split-gate] wrote report to ${reportFile}`);
  process.exit(1);
}

const mode = strict ? "strict" : "advisory";
const preservationNotice = preserveStrictArtifact ? "; preserved existing strict release artifact" : "";
console.log(`[ddd-physical-split-gate] ${mode} checks passed for ${contexts.length} context(s); blockers=${report.summary.blockers}; report=${reportFile}${preservationNotice}`);
