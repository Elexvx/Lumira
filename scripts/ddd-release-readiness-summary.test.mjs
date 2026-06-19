#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync as rawSpawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  expectedRuntimeReadinessChecks,
  runtimeReadinessContextLabels,
} from "./ddd-runtime-readiness-contract.mjs";
import {
  requiredFileProcessingArtifacts,
  requiredFileProcessingTasks,
} from "./ddd-business-e2e-evidence-contract.mjs";
import { requiredJobSmokeEndpoints } from "./ddd-outbox-job-evidence-contract.mjs";
import { requiredAuthenticatedPerformanceEndpoints } from "./ddd-performance-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const artifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-"));
const explainDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-explain-"));
let bashNodeWrapperPath = null;
let bashNodeShimDir = null;

function toBashPath(file) {
  if (process.platform === "win32" && typeof file === "string" && /^(?:\/mnt\/[A-Za-z]\/|\/[A-Za-z]\/)/.test(file)) {
    return file;
  }
  const resolved = path.resolve(file);
  if (process.platform !== "win32") {
    return resolved;
  }
  return resolved.replace(/^([A-Za-z]):\\/, (_, drive) => `/mnt/${drive.toLowerCase()}/`).replaceAll("\\", "/");
}

function shellSingleQuoted(value) {
  return `'${String(value ?? "").replace(/'/g, "'\\''")}'`;
}

function createBashNodeWrapper() {
  if (process.platform !== "win32") {
    return process.execPath;
  }
  if (bashNodeWrapperPath) {
    return bashNodeWrapperPath;
  }
  const wrapperPath = path.join(artifactRoot, "node-for-bash.sh");
  const script = [
    "#!/bin/bash",
    "set -euo pipefail",
    `node_bin=${shellSingleQuoted(toBashPath(process.execPath))}`,
    "convert_wsl_path() {",
    "  local value=\"$1\"",
    "  if [[ \"${value}\" == /* ]] && command -v wslpath >/dev/null 2>&1; then",
    "    wslpath -w \"${value}\"",
    "    return",
    "  fi",
    "  printf '%s' \"${value}\"",
    "}",
    "repo_root_bash=\"${LUMIRA_REPO_ROOT:-}\"",
    "for name in DDD_RELEASE_ENV_FILE DDD_RELEASE_CANONICAL_ENV_FILE DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT DDD_RELEASE_OWNER_ENV_TEMPLATE_DIR DDD_RELEASE_EVIDENCE_DIR DDD_RELEASE_DIR DDD_RELEASE_CONFIG_REPORT DDD_RELEASE_PREFLIGHT_REPORT DDD_EXPLAIN_DIR LUMIRA_REPO_ROOT; do",
    "  value=\"${!name:-}\"",
    "  if [[ -n \"${value}\" ]]; then",
    "    export \"${name}=$(convert_wsl_path \"${value}\")\"",
    "  fi",
    "done",
    "args=()",
    "for arg in \"$@\"; do",
    "  if [[ \"${arg}\" == scripts/* || \"${arg}\" == artifacts/* ]]; then",
    "    args+=(\"$(convert_wsl_path \"${repo_root_bash}/${arg}\")\")",
    "  else",
    "    args+=(\"$(convert_wsl_path \"${arg}\")\")",
    "  fi",
    "done",
    "exec \"${node_bin}\" \"${args[@]}\"",
    "",
  ].join("\n");
  fs.writeFileSync(wrapperPath, script);
  fs.chmodSync(wrapperPath, 0o700);
  bashNodeShimDir = path.join(artifactRoot, "bash-bin");
  fs.mkdirSync(bashNodeShimDir, { recursive: true });
  const shimPath = path.join(bashNodeShimDir, "node");
  fs.writeFileSync(shimPath, `#!/bin/bash\nexec ${shellSingleQuoted(toBashPath(wrapperPath))} "$@"\n`);
  fs.chmodSync(shimPath, 0o700);
  const bashShimPath = path.join(bashNodeShimDir, "bash");
  fs.writeFileSync(bashShimPath, [
    "#!/bin/bash",
    "set -euo pipefail",
    "if [[ \"${1:-}\" == *.sh && -f \"${1}\" ]]; then",
    "  normalized=\"${TMPDIR:-/tmp}/lumira-normalized-bash-$$.sh\"",
    "  tr -d '\\r' < \"${1}\" > \"${normalized}\"",
    "  chmod +x \"${normalized}\"",
    "  shift",
    "  exec /bin/bash \"${normalized}\" \"$@\"",
    "fi",
    "exec /bin/bash \"$@\"",
    "",
  ].join("\n"));
  fs.chmodSync(bashShimPath, 0o700);
  bashNodeWrapperPath = toBashPath(wrapperPath);
  return bashNodeWrapperPath;
}

function bashEnvValue(value) {
  const text = String(value ?? "");
  if (process.platform === "win32" && /^[A-Za-z]:[\\/]/.test(text)) {
    return toBashPath(text);
  }
  return text;
}

function bashEnvAssignments(env = {}) {
  if (process.platform === "win32") {
    createBashNodeWrapper();
  }
  const effectiveEnv = {
    ...env,
    ...(bashNodeShimDir ? { PATH: `${toBashPath(bashNodeShimDir)}:$PATH` } : {}),
  };
  const assignments = Object.entries(effectiveEnv)
    .filter(([key, value]) => key === "DDD_NODE_BIN" || process.env[key] !== value)
    .map(([key, value]) => (key === "PATH"
      ? `${key}=${shellSingleQuoted(`${toBashPath(bashNodeShimDir)}:/usr/local/bin:/usr/bin:/bin`)}`
      : `${key}=${shellSingleQuoted(bashEnvValue(value))}`))
    .join(" ");
  return assignments ? `${assignments} ` : "";
}

function minimalWindowsBashEnv() {
  if (process.platform !== "win32") {
    return process.env;
  }
  return {
    SystemRoot: process.env.SystemRoot || "C:\\Windows",
    WINDIR: process.env.WINDIR || "C:\\Windows",
    PATH: "C:\\Windows\\System32;C:\\Windows",
  };
}

function runBashWithEnv(scriptPath, env = {}) {
  return rawSpawnSync("bash", ["-lc", `${bashEnvAssignments(env)}/bin/bash ${shellSingleQuoted(toBashPath(scriptPath))}`], {
    cwd: repoRoot,
    encoding: "utf8",
    env: minimalWindowsBashEnv(),
  });
}

function spawnSync(command, args = [], options = {}) {
  if (command !== "bash") {
    return rawSpawnSync(command, args, options);
  }
  const cwd = options.cwd || repoRoot;
  const encoding = options.encoding || "utf8";
  const env = { ...(options.env || {}) };
  if (env.DDD_NODE_BIN === undefined) {
    env.DDD_NODE_BIN = createBashNodeWrapper();
  }
  if (Array.isArray(args) && args[0] === "-n" && args[1]) {
    return rawSpawnSync("bash", ["-n", toBashPath(args[1])], { ...options, cwd, encoding, env: minimalWindowsBashEnv() });
  }
  if (Array.isArray(args) && args.length === 1 && args[0]) {
    return rawSpawnSync("bash", ["-lc", `${bashEnvAssignments(env)}/bin/bash ${shellSingleQuoted(toBashPath(args[0]))}`], {
      cwd,
      encoding,
      env: minimalWindowsBashEnv(),
    });
  }
  return rawSpawnSync(command, args, options);
}

function writeJson(relativePath, data) {
  const file = path.join(artifactRoot, relativePath);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function writeBashEnvFixture(fileName, content, mode = "600") {
  if (process.platform !== "win32") {
    const file = path.join(artifactRoot, "release", fileName);
    fs.writeFileSync(file, content);
    fs.chmodSync(file, Number.parseInt(mode, 8));
    return file;
  }
  const bashPath = `/tmp/lumira-${process.pid}-${fileName}`;
  const run = rawSpawnSync("bash", ["-lc", `cat > ${shellSingleQuoted(bashPath)} && chmod ${mode} ${shellSingleQuoted(bashPath)}`], {
    input: content,
    encoding: "utf8",
    env: minimalWindowsBashEnv(),
  });
  assert.equal(run.status, 0, run.stderr);
  return bashPath;
}

function writeExplain(fileName, data) {
  const file = path.join(explainDir, fileName);
  fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
}

function parseCsvRows(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];

    if (char === "\"") {
      if (quoted && next === "\"") {
        cell += "\"";
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      row.push(cell);
      cell = "";
    } else if (char === "\n" && !quoted) {
      row.push(cell);
      if (row.some((value) => value !== "")) {
        rows.push(row);
      }
      row = [];
      cell = "";
    } else if (char !== "\r") {
      cell += char;
    }
  }

  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }

  return rows;
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function writeRuntimeEndpointArtifact(context, suffix) {
  const file = path.join(artifactRoot, "readiness", "endpoint-artifacts", `${context}-${suffix}.json`);
  const label = runtimeReadinessContextLabels.get(context) || context;
  const baseData = {
    context: label,
    ownerModule: context,
    status: "UP",
  };
  const data = suffix === "readiness"
    ? {
      ...baseData,
      ownerTablePatterns: [`${context}_*`],
      apiContracts: [`/api/v2/${context}`],
      healthChecks: [`${context}.health`],
      metrics: [`${context}.latency`],
      dependencies: ["database"],
      rollbackSteps: [`rollback ${context}`],
      blockers: [],
    }
    : {
      ...baseData,
      observedAt: "2026-06-14T00:00:00.000Z",
      healthChecks: [
        {
          name: `${context}.health`,
          status: "UP",
          description: `${label} health check`,
        },
      ],
      metrics: [
        {
          name: `${context}.latency`,
          type: "gauge",
          unit: "ms",
          description: `${label} runtime latency`,
        },
      ],
    };
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify({ httpStatus: 200, data }, null, 2)}\n`);
  return file;
}

const releaseGateBlockers = [
  "runtime-readiness-freshness: checkedAt is 28.1h old; limit=24h",
  "authenticated-performance-freshness: checkedAt is 28.1h old; limit=24h",
  "file-processing-freshness: finishedAt is 27.8h old; limit=24h",
  "payment-webhook-freshness: finishedAt is 27.6h old; limit=24h",
  "job-e2e-freshness: checkedAt is 27.4h old; limit=24h",
  "migration-evidence: status=FAIL",
  "release-config-evidence: status=FAIL, blockers=2",
  "authenticated-performance-shape: authenticated performance actual productionEquivalence is required for strict release evidence",
  "file-processing-production-equivalence: strict file processing E2E requires HTTPS baseUrl evidence",
  "file-processing-e2e: file processing productionEquivalence is required for strict release evidence",
  "payment-webhook-e2e: payment webhook productionEquivalence is required for strict release evidence",
  "job-e2e-smoke: job E2E productionEquivalence is required for strict release evidence",
  "release-evidence-orchestrator-preflight-backend-runtime-base-url: missing backend runtime base URL",
  "release-evidence-orchestrator-preflight-frontend-runtime-base-url: missing deployed frontend base URL",
  "release-evidence-orchestrator-preflight-ai-provider-remote-expectation: DDD_AI_EXPECT_PROVIDER_REMOTE=true is required for strict AI runtime evidence",
  "rollback-drill: IAM status must be PASS or DEFERRED",
  "rollback-drill: Auth status must be PASS or DEFERRED",
];

writeJson("release/release-evidence-gate.json", {
  strict: true,
  blockerCount: 999,
  summary: {
    blockers: 17,
    warnings: 0,
  },
  blockers: releaseGateBlockers,
  blockerDetails: releaseGateBlockers.map((blocker) => ({
    check: blocker.split(":")[0],
    detail: blocker.slice(blocker.indexOf(":") + 1).trim(),
    file: null,
  })),
});
writeJson("release/evidence-manifest.json", {
  status: "FAIL",
  summary: {
    requiredArtifacts: 19,
    presentArtifacts: 19,
    optionalArtifacts: 1,
    invalidJsonArtifacts: 0,
    provenanceIssueArtifacts: 0,
    explainFiles: 1,
    blockers: 1,
  },
  artifacts: [
    {
      relativePath: "release/release-final-owner-queue-run-report.json",
      present: true,
      status: "PRESENT",
      bytes: 256,
      sha256: "a".repeat(64),
      timestamp: { field: "generatedAt", value: "2026-06-14T00:00:00.000Z" },
      contractIssues: [],
    },
  ],
  blockers: ["missing artifact performance/authenticated-runtime-baseline.json"],
});
writeJson("release/release-final-owner-queue-run-report.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  reportStatus: "FAIL",
  exitCode: 1,
  ownerFilter: "release-performance",
  statusFilter: "ACTIONABLE",
  entries: [],
});
writeJson("release/release-final-owner-queue-env-init-receipt.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  templatePath: "artifacts/ddd/release/release-final-owner-queue-env.template.env",
  targetPath: ".env.release.local",
  targetModeOctal: "600",
  permissionSafe: true,
  unresolvedTemplateKeyCount: 2,
  unresolvedTemplateKeys: ["JWT_SECRET", "DB_PASSWORD"],
  nextCommands: [
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-env-file-lint.mjs",
    "DDD_RELEASE_ENV_FILE=.env.release.local node scripts/ddd-release-readiness-summary.mjs",
  ],
});
writeJson("release/explain-gate-report.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "FAIL",
  strict: true,
  explainDir,
  scannedExplainFileCount: 1,
  blockerCount: 8,
  issues: [
    {
      scope: "metadata",
      detail: "message-visible-list.json.legacyPlanImport must be false for strict release evidence",
    },
  ],
});
writeJson("release/orchestrator-report.json", {
  mode: "plan",
  strict: true,
  summary: {
    steps: 2,
    executed: 0,
    failed: 0,
  },
  preflight: {
    status: "FAIL",
    blockers: 8,
    warnings: 0,
    checks: [
      {
        id: "backend-runtime-base-url",
        status: "BLOCKER",
        detail: "missing backend runtime base URL",
        envKeys: ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
      },
      {
        id: "frontend-runtime-base-url",
        status: "BLOCKER",
        detail: "missing deployed frontend base URL",
        envKeys: ["PLAYWRIGHT_BASE_URL", "FRONTEND_BASE_URL"],
      },
      {
        id: "frontend-deployed-expectation",
        status: "BLOCKER",
        detail: "DDD_FRONTEND_EXPECT_DEPLOYED=true is required for strict frontend smoke",
        envKeys: ["DDD_FRONTEND_EXPECT_DEPLOYED"],
      },
      {
        id: "ai-runtime-base-url",
        status: "BLOCKER",
        detail: "missing AI runtime base URL",
        envKeys: ["LUMIRA_AI_BASE_URL", "LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
      },
      {
        id: "ai-provider-remote-expectation",
        status: "BLOCKER",
        detail: "DDD_AI_EXPECT_PROVIDER_REMOTE=true is required for strict AI runtime evidence",
        envKeys: ["DDD_AI_EXPECT_PROVIDER_REMOTE"],
      },
      {
        id: "ai-owner-gateway-remote-expectation",
        status: "BLOCKER",
        detail: "DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true is required for strict AI runtime evidence",
        envKeys: ["DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE"],
      },
      {
        id: "docker-daemon",
        status: "BLOCKER",
        detail: "Docker daemon is not available",
        envKeys: ["DDD_DOCKER_COMMAND"],
      },
      {
        id: "migration-runtime-evidence",
        status: "BLOCKER",
        detail: "missing migration drill env",
        envKeys: [
          "DDD_MIGRATION_FRESH_DB_VALIDATED",
          "DDD_MIGRATION_UPGRADE_DB_VALIDATED",
          "DDD_MIGRATION_FRESH_DB_EVIDENCE",
          "DDD_MIGRATION_UPGRADE_DB_EVIDENCE",
        ],
      },
    ],
  },
  selectedSteps: [
    {
      id: "release-config-evidence",
      label: "Production-equivalent config evidence",
      optional: false,
      enabled: true,
      runtime: false,
      heavy: false,
      envKeys: ["DDD_RELEASE_EVIDENCE_STRICT"],
    },
    {
      id: "runtime-readiness",
      label: "Runtime readiness smoke",
      optional: false,
      enabled: true,
      runtime: true,
      heavy: false,
      envKeys: ["DDD_RELEASE_EVIDENCE_STRICT"],
    },
  ],
  results: [],
});
writeJson("readiness/summary.json", {
  baseUrl: "http://127.0.0.1:8080",
  checkedAt: "2026-06-14T00:00:00.000Z",
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  evidenceOperator: "codex",
  failures: [],
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: null,
    issues: [
      "strict runtime readiness requires HTTPS baseUrl evidence",
      "strict runtime readiness requires non-local baseUrl, got http://127.0.0.1:8080",
    ],
  },
  summary: expectedRuntimeReadinessChecks().map(({ context, suffix }) => ({
    context,
    suffix,
    status: 200,
    artifact: writeRuntimeEndpointArtifact(context, suffix),
  })),
});
writeJson("config/release-config-evidence.json", {
  status: "FAIL",
  envFile: null,
  inputKind: "process-environment-only",
  generatedMissingTemplate: false,
  envFileExists: false,
  summary: {
    requiredChecks: 2,
    runtimePresentRequiredChecks: 0,
    envFileCoveredRequiredChecks: 0,
    templateCoveredRequiredChecks: 2,
    workflowCoveredRequiredChecks: 2,
    blockers: 2,
    primaryBlockers: 2,
    releaseConfigBlockersFromPlaceholders: 0,
    releaseConfigBlockersAfterPlaceholders: 2,
  },
  coverageMatrix: [
    {
      group: "runtime",
      owner: "release-infra",
      check: "backend base url",
      required: true,
      keys: ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL"],
      runtimePresent: false,
      envFileCovered: false,
      templateCovered: true,
      workflowCovered: true,
    },
    {
      group: "ai",
      owner: "ai-owner",
      check: "provider api key",
      required: true,
      keys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "LUMIRA_AI_PROVIDER_API_KEY"],
      runtimePresent: false,
      envFileCovered: false,
      templateCovered: true,
      workflowCovered: true,
    },
  ],
  blockers: [
    "runtime.backend base url: missing LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL",
    "ai.provider api key: missing LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY",
  ],
  blockerDetails: [
    {
      blocker: "runtime.backend base url: missing LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL",
      group: "runtime",
      owner: "release-infra",
      check: "backend base url",
      reason: "missing LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL",
      envKeys: ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL"],
    },
    {
      blocker: "ai.provider api key: missing LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY",
      group: "ai",
      owner: "ai-owner",
      check: "provider api key",
      reason: "missing LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY",
      envKeys: [
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
        "LUMIRA_AI_PROVIDER_API_KEY",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_BASE_URL",
        "LUMIRA_AI_PROVIDER_BASE_URL",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_CHAT_MODEL",
        "LUMIRA_AI_CHAT_MODEL",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_EMBEDDING_MODEL",
        "LUMIRA_AI_EMBEDDING_MODEL",
        "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED",
      ],
    },
  ],
  blockersByGroup: {
    ai: 1,
    runtime: 1,
  },
  blockersByOwner: {
    "ai-owner": 1,
    "release-infra": 1,
  },
  warnings: [
    "DDD_RELEASE_ENV_FILE is not set; evidence was generated from current process environment only",
  ],
});
writeJson("build/docker-image-evidence.json", {
  status: "FAIL",
  dockerCommand: "docker",
  summary: {
    passed: 0,
    failed: 0,
    skipped: 1,
  },
  blockers: ["docker daemon is not available"],
  remediation: {
    transientRegistryFailure: true,
    dockerUnavailable: true,
    transientImages: [
      {
        name: "lumira-server",
        attempts: 3,
        retries: 2,
        dockerfile: "deploy/docker/service.Dockerfile",
      },
    ],
    nextActions: [
      {
        id: "docker-registry-mirror-retry",
        owner: "release-infra",
        action: "Rerun Docker evidence with registry-local mirror images and a higher retry budget.",
        envKeys: [
          "DDD_DOCKER_BUILD_RETRIES",
          "DDD_DOCKER_MAVEN_IMAGE",
          "DDD_DOCKER_JRE_IMAGE",
          "DDD_DOCKER_NODE_IMAGE",
          "DDD_DOCKER_NGINX_IMAGE",
        ],
        exampleCommand: "DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21 node scripts/ddd-docker-build-evidence.mjs",
      },
      {
        id: "docker-existing-image-inspect",
        owner: "release-infra",
        action: "If CI already built and pushed the release candidate images, pull them and rerun Docker evidence in explicit inspect-only mode.",
        envKeys: [
          "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE",
          "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
          "DDD_DOCKER_EXISTING_FRONTEND_IMAGE",
        ],
        exampleCommand: "DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> node scripts/ddd-docker-build-evidence.mjs",
      },
    ],
  },
  preflight: {
    version: {
      status: 0,
      stdoutTail: "Docker version 29.4.1",
    },
    info: {
      status: 1,
      stderrTail: "Cannot connect to the Docker daemon",
    },
  },
  images: [
    {
      name: "lumira-server",
      dockerfile: "deploy/docker/service.Dockerfile",
      dockerfileSha256: "abc123",
      tag: "lumira/lumira-server:test",
      expectedExposedPort: "8080/tcp",
      requireNonRootUser: true,
      staticDockerfile: {
        status: "PASS",
        exists: true,
        dockerfileSha256: "abc123",
        issues: [],
        checks: {
          exposesExpectedPort: true,
          definesEntrypointOrCmd: true,
          nonRootUser: true,
        },
      },
      status: "SKIPPED",
      skipReason: "docker daemon is not available",
      blockers: ["docker daemon is not available"],
    },
  ],
});
writeJson("release/release-env-lint.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  evidenceOperator: "codex",
  status: "FAIL",
  envFile: "/secure/.env.release",
  inputKind: "release-env-file",
  generatedMissingTemplate: false,
  envFileSecurity: {
    checked: true,
    reason: "env-file",
    mode: 384,
    modeOctal: "600",
    permissionSafe: true,
    permissionCheckSkipped: false,
    generatedMissingTemplate: false,
    requiredMode: "600",
  },
  summary: {
    keys: 4,
    canonicalKeys: 4,
    duplicateKeys: 0,
    envFileSecurityChecked: true,
    envFilePermissionSafe: true,
    envFilePermissionCheckSkipped: false,
    envFileModeOctal: "600",
    unresolvedTemplateKeys: 3,
    canonicalUnresolvedTemplateKeys: 3,
    releaseConfigBlockers: 3,
    releaseConfigBlockersFromPlaceholders: 3,
    releaseConfigBlockersAfterPlaceholders: 0,
    canonicalReleaseConfigBlockerKeys: 3,
    warnings: 0,
    blockers: 2,
    primaryBlockers: 1,
  },
  keys: ["LUMIRA_BASE_URL", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "JWT_SECRET", "DB_PASSWORD"],
  canonicalKeys: ["LUMIRA_BASE_URL", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "JWT_SECRET", "DB_PASSWORD"],
  duplicateKeys: [],
  unresolvedTemplateKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "JWT_SECRET", "DB_PASSWORD"],
  canonicalUnresolvedTemplateKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  canonicalReleaseConfigBlockerKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  releaseConfigBlockerDetails: [{
    blocker: "ai.provider api key: must be at least 32 characters",
    group: "ai",
    owner: "ai-owner",
    check: "provider api key",
    reason: "must be at least 32 characters",
    envKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY", "LUMIRA_AI_PROVIDER_API_KEY"],
    canonicalEnvKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
    matchedKey: "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
    canonicalMatchedKey: "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY",
    blockedByPlaceholderKey: true,
    required: true,
  }],
  blockers: [
    "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY: __REQUIRED__ placeholder must be replaced",
    "ai.provider api key: must be at least 32 characters",
  ],
  warnings: [],
});
writeJson("performance/authenticated-runtime-actual.json", {
  baseUrl: "http://127.0.0.1:8080",
  checkedAt: "2026-06-14T00:00:00.000Z",
  durationMs: 1200,
  concurrency: 4,
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  evidenceOperator: "codex",
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: null,
    issues: [
      "strict authenticated performance actual requires HTTPS baseUrl evidence",
      "strict authenticated performance actual requires non-local baseUrl, got http://127.0.0.1:8080",
    ],
  },
  ok: 90,
  failed: 0,
  samples: 90,
  p50: 45,
  p95: 88,
  p99: 110,
  upload: {
    path: "/api/v2/files/upload",
    status: 200,
    elapsedMs: 84,
    fileId: 7,
  },
  endpoints: requiredAuthenticatedPerformanceEndpoints.map((endpoint) => {
    const [method, ...pathParts] = endpoint.split(" ");
    return {
      method,
      path: pathParts.join(" "),
    };
  }),
  oneShots: [
    {
      name: "POST /api/v2/auth/session/keepalive",
      status: 200,
      elapsedMs: 18,
    },
  ],
  perEndpoint: Object.fromEntries(requiredAuthenticatedPerformanceEndpoints.map((endpoint) => [
    endpoint,
    {
      samples: 10,
      p50: 40,
      p95: 80,
      p99: 100,
      statusCounts: {
        200: 10,
      },
    },
  ])),
});
writeJson("performance/authenticated-runtime-baseline-promotion.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  status: "FAIL",
  sourceFile: "/tmp/authenticated-runtime-actual.json",
  outputFile: "/tmp/authenticated-runtime-baseline.json",
  sourceArtifact: "artifacts/ddd/performance/authenticated-runtime-actual.json",
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  acceptedBy: "release-owner",
  requiredEnvKeys: [
    "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY",
    "DDD_AUTH_PERF_BASELINE_ENVIRONMENT",
    "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT",
    "DDD_RELEASE_CANDIDATE",
  ],
  sourceActual: {
    baseUrl: "http://127.0.0.1:8080",
    localOnly: true,
    sourceEnvironment: "local-dev",
    releaseCandidate: "local-worktree",
    evidenceOperator: "codex",
    failed: 0,
    p95: 88,
    uploadStatus: 200,
    uploadElapsedMs: 84,
    endpointCount: 9,
  },
  baseline: null,
  blockers: [
    "source actual artifact must be production-equivalent and non-local, got http://127.0.0.1:8080",
  ],
});
writeJson("file/file-processing-e2e.json", {
  status: "PASS",
  baseUrl: "http://127.0.0.1:8080",
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: null,
    issues: [
      "strict file processing E2E requires HTTPS baseUrl evidence",
      "strict file processing E2E requires non-local baseUrl, got http://127.0.0.1:8080",
    ],
  },
  upload: {
    elapsedMs: 100,
    fileId: 7,
  },
  finalState: {
    tasks: requiredFileProcessingTasks.map((taskType) => ({
      taskType,
      status: "SUCCEEDED",
    })),
    artifacts: requiredFileProcessingArtifacts.map((artifactType) => ({
      artifactType,
    })),
  },
});
writeJson("payment/payment-webhook-e2e.json", {
  status: "PASS",
  baseUrl: "http://127.0.0.1:8080",
  productionEquivalence: {
    strict: true,
    https: false,
    localOnly: true,
    deploymentEvidence: null,
    issues: [
      "strict payment webhook E2E requires HTTPS baseUrl evidence",
      "strict payment webhook E2E requires non-local baseUrl, got http://127.0.0.1:8080",
    ],
  },
  provider: {
    configured: true,
  },
  finalState: {
    order: { status: "PAID" },
    webhookEvents: [
      { eventId: "evt-valid", processed: 1, signatureValid: 1, processMessage: "支付 webhook 已处理" },
      { eventId: "evt-replay", processed: 0, signatureValid: 0, processMessage: "请求已被重放" },
      { eventId: "evt-bad", processed: 0, signatureValid: 0, processMessage: "签名校验失败" },
    ],
  },
  webhooks: {
    first: { eventId: "evt-valid", processed: true, signatureValid: true, elapsedMs: 20 },
    duplicate: { eventId: "evt-valid", processed: true, signatureValid: true, elapsedMs: 7 },
    nonceReplay: { eventId: "evt-replay", processed: false, signatureValid: false, elapsedMs: 10 },
    badSignature: { eventId: "evt-bad", processed: false, signatureValid: false, elapsedMs: 13 },
  },
});
writeJson("jobs/job-e2e-smoke.json", {
  baseUrl: "http://127.0.0.1:8080",
  unauthorized: {
    status: 401,
  },
  summary: {
    failed: 0,
  },
  endpoints: requiredJobSmokeEndpoints.map((endpoint) => ({
    name: endpoint.name,
    path: endpoint.path,
    status: 200,
    data: endpoint.dataType === "boolean" ? true : 0,
    elapsedMs: 10,
  })),
  diagnostics: {
    outboxOwnership: {
      crossOwnerPayloadFailuresDelta: 0,
    },
  },
});
writeJson("frontend/frontend-smoke.json", {
  status: "FAIL",
  baseUrl: "http://127.0.0.1:8010",
  expectDeployed: false,
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  evidenceOperator: "codex",
  summary: {
    total: 0,
    passed: 0,
    failed: 1,
    skipped: 0,
    requiredFlows: 1,
    missingRequiredFlows: 1,
  },
  blockers: ["missing required flows=1"],
  diagnostics: {
    playwrightReport: {
      present: false,
      file: "/tmp/playwright-smoke-results.json",
      reason: "missing Playwright JSON report",
    },
    staticSpecCoverage: {
      present: true,
      file: "/repo/frontend/tests/e2e/app.spec.ts",
      covered: 1,
      missing: 0,
      coverage: [
        {
          flow: "dashboard page is reachable",
          status: "covered",
          reason: null,
        },
      ],
    },
  },
  flowCoverage: [
    {
      flow: "dashboard page is reachable",
      status: "missing",
      reason: "missing Playwright JSON report",
    },
  ],
});
writeJson("migration/migration-evidence.json", {
  status: "FAIL",
  runtimeReady: false,
  runtime: {
    freshDatabaseValidated: false,
    upgradeDatabaseValidated: false,
    environment: "local-dev",
    releaseCandidate: "local-worktree",
    freshDatabaseEvidence: "",
    upgradeDatabaseEvidence: "",
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
  ],
  runtimeDiagnostics: [
    {
      id: "fresh-database-drill",
      owner: "database",
      status: "MISSING",
      action: "Run Flyway against an empty database.",
      envKeys: ["DDD_MIGRATION_FRESH_DB_VALIDATED", "DDD_MIGRATION_FRESH_DB_EVIDENCE"],
      evidence: null,
    },
  ],
});
writeJson("ai/ai-runtime-drill.json", {
  status: "FAIL",
  baseUrl: "http://127.0.0.1:8080",
  summary: {
    failed: 1,
    failureCategories: {
      endpoint: 1,
    },
  },
  remoteEvidence: {
    provider: { remoteConfigured: false },
    ownerGateway: { configuredOwnerCount: 0 },
  },
  failures: ["AI runtime endpoint request failed: fetch failed"],
  failureDetails: [
    {
      message: "AI runtime endpoint request failed: fetch failed",
      category: "endpoint",
      owner: "ai",
    },
  ],
});
writeJson("rollback/rollback-drill.json", {
  status: "FAIL",
  environment: "local-dev",
  releaseVersion: "local-worktree",
  contextDiagnostics: [
    {
      context: "IAM",
      status: "MISSING",
      owner: "iam-owner",
      action: "Exercise IAM rollback.",
      evidenceRequirements: [
        "permission snapshot version before and after rollback",
        "audit entry or command log for the rollback action",
      ],
      evidence: null,
      ready: false,
      missingEvidence: true,
    },
  ],
  contexts: [
    {
      context: "IAM",
      status: "MISSING",
      rollbackAction: null,
      drillEvidence: null,
    },
  ],
});
writeExplain("message-visible-list.json", {
  generatedAt: "2026-06-14T00:00:00.000Z",
  sourceEnvironment: "local-dev",
  releaseCandidate: "local-worktree",
  evidenceOperator: "codex",
  queryName: "message-visible-list",
  sqlSha256: "a".repeat(64),
  legacyPlanImport: true,
  plan: {
    query_block: {
      table: {
        table_name: "msg_notice",
        access_type: "ref",
        key: "idx_msg_notice_visible_recent",
      },
    },
  },
});

const staleRedactedHandoffDir = path.join(artifactRoot, "release", "release-env-owner-handoff-redacted");
fs.mkdirSync(staleRedactedHandoffDir, { recursive: true });
fs.writeFileSync(path.join(staleRedactedHandoffDir, "99-stale-owner.md"), "# stale owner handoff\n");

const result = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: artifactRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});

assert.equal(result.status, 0, result.stderr || result.stdout);
assert.match(result.stdout, /releaseClosureWaveReceipts=.*release-closure-wave-receipts\.json/);
assert.match(result.stdout, /releaseClosureWaveBlockerMap=.*release-closure-wave-blocker-map\.json/);
assert.match(result.stdout, /releasePerformanceBaselineClosure=.*release-performance-baseline-closure\.json/);
assert.match(result.stdout, /releasePerformanceBaselineCommands=.*release-performance-baseline-commands\.sh/);
assert.match(result.stdout, /releaseFinalGoNoGo=.*release-final-go-no-go\.json/);
assert.match(result.stdout, /releaseFinalGoNoGoGate=.*release-final-go-no-go-gate\.sh/);
assert.match(result.stdout, /releasePreflightGate=.*release-preflight-gate\.sh/);
assert.match(result.stdout, /releaseFinalOwnerQueue=.*release-final-owner-queue\.json/);
assert.match(result.stdout, /releaseFinalOwnerQueueCommands=.*release-final-owner-queue-commands\.sh/);
assert.match(result.stdout, /releaseFinalOwnerQueueEnvInit=.*release-final-owner-queue-env-init\.sh/);
assert.match(result.stdout, /releaseEnvFillPriority=.*release-env-fill-priority\.json/);
assert.match(result.stdout, /releaseEnvReadinessRedacted=.*release-env-readiness-redacted\.json/);
assert.match(result.stdout, /releaseEnvReadinessGate=.*release-env-readiness-gate\.sh/);
assert.match(result.stdout, /releaseEnvOwnerHandoffRedacted=.*release-env-owner-handoff-redacted\.json/);
assert.match(result.stdout, /releaseEnvOwnerHandoffRedactedCsv=.*release-env-owner-handoff-redacted\.csv/);
assert.match(result.stdout, /releaseArtifactIntegrity=.*release-artifact-integrity\.json/);
assert.match(result.stdout, /releaseArtifactIntegrityGate=.*release-artifact-integrity-gate\.sh/);
const summary = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/readiness-summary.json"), "utf8"));
const ownerRollup = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/owner-action-rollup.json"), "utf8"));
const ownerRollupCsv = fs.readFileSync(path.join(artifactRoot, "release/owner-action-rollup.csv"), "utf8");
const ownerRollupMarkdown = fs.readFileSync(path.join(artifactRoot, "release/owner-action-rollup.md"), "utf8");
const sourceRollup = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/source-action-rollup.json"), "utf8"));
const sourceRollupCsv = fs.readFileSync(path.join(artifactRoot, "release/source-action-rollup.csv"), "utf8");
const sourceRollupMarkdown = fs.readFileSync(path.join(artifactRoot, "release/source-action-rollup.md"), "utf8");
const releaseBlockerMap = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-blocker-map.json"), "utf8"));
const releaseBlockerMapCsv = fs.readFileSync(path.join(artifactRoot, "release/release-blocker-map.csv"), "utf8");
const releaseBlockerMapMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-blocker-map.md"), "utf8");
const releaseFastTrack = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-fast-track.json"), "utf8"));
const releaseCutoverChecklistCsv = fs.readFileSync(path.join(artifactRoot, "release/release-cutover-checklist.csv"), "utf8");
const releaseCutoverOwnerMatrix = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-cutover-owner-matrix.json"), "utf8"));
const releaseCutoverOwnerMatrixCsv = fs.readFileSync(path.join(artifactRoot, "release/release-cutover-owner-matrix.csv"), "utf8");
const releaseCutoverOwnerMatrixMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-cutover-owner-matrix.md"), "utf8");
const releaseSprintBoard = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-sprint-board.json"), "utf8"));
const releaseSprintBoardCsv = fs.readFileSync(path.join(artifactRoot, "release/release-sprint-board.csv"), "utf8");
const releaseSprintBoardMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-sprint-board.md"), "utf8");
const releaseCommandCatalog = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-command-catalog.json"), "utf8"));
const releaseCommandCatalogCsv = fs.readFileSync(path.join(artifactRoot, "release/release-command-catalog.csv"), "utf8");
const releaseCommandCatalogMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-command-catalog.md"), "utf8");
const releaseOwnerHandoff = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-owner-handoff.json"), "utf8"));
const releaseOwnerHandoffCsv = fs.readFileSync(path.join(artifactRoot, "release/release-owner-handoff.csv"), "utf8");
const releaseOwnerHandoffMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-owner-handoff.md"), "utf8");
const releaseOwnerReceipts = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-owner-receipts.json"), "utf8"));
const releaseOwnerReceiptsCsv = fs.readFileSync(path.join(artifactRoot, "release/release-owner-receipts.csv"), "utf8");
const releaseOwnerReceiptsMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-owner-receipts.md"), "utf8");
const releaseNextActionQueue = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-next-action-queue.json"), "utf8"));
const releaseNextActionQueueCsv = fs.readFileSync(path.join(artifactRoot, "release/release-next-action-queue.csv"), "utf8");
const releaseNextActionQueueMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-next-action-queue.md"), "utf8");
const releaseNextActionCommands = fs.readFileSync(path.join(artifactRoot, "release/release-next-action-commands.sh"), "utf8");
const releaseBlockerClosurePlan = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-blocker-closure-plan.json"), "utf8"));
const releaseBlockerClosurePlanCsv = fs.readFileSync(path.join(artifactRoot, "release/release-blocker-closure-plan.csv"), "utf8");
const releaseBlockerClosurePlanMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-blocker-closure-plan.md"), "utf8");
const releaseBlockerClosureCommandsPath = path.join(artifactRoot, "release/release-blocker-closure-commands.sh");
const releaseBlockerClosureCommands = fs.readFileSync(releaseBlockerClosureCommandsPath, "utf8");
const releaseClosureWaveEnvMatrix = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-env-matrix.json"), "utf8"));
const releaseClosureWaveEnvMatrixCsv = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-env-matrix.csv"), "utf8");
const releaseClosureWaveEnvMatrixMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-env-matrix.md"), "utf8");
const releaseClosureWaveEnvTemplate = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-env.template.env"), "utf8");
const releaseClosureWaveReceipts = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-receipts.json"), "utf8"));
const releaseClosureWaveReceiptsCsv = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-receipts.csv"), "utf8");
const releaseClosureWaveReceiptsMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-receipts.md"), "utf8");
const releaseClosureWaveBlockerMap = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-blocker-map.json"), "utf8"));
const releaseClosureWaveBlockerMapCsv = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-blocker-map.csv"), "utf8");
const releaseClosureWaveBlockerMapMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-closure-wave-blocker-map.md"), "utf8");
const releasePerformanceBaselineClosure = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-performance-baseline-closure.json"), "utf8"));
const releasePerformanceBaselineClosureMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-performance-baseline-closure.md"), "utf8");
const releasePerformanceBaselineCommandsPath = path.join(artifactRoot, "release/release-performance-baseline-commands.sh");
const releasePerformanceBaselineCommands = fs.readFileSync(releasePerformanceBaselineCommandsPath, "utf8");
const releaseFinalGoNoGo = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-final-go-no-go.json"), "utf8"));
const releaseFinalGoNoGoCsv = fs.readFileSync(path.join(artifactRoot, "release/release-final-go-no-go.csv"), "utf8");
const releaseFinalGoNoGoMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-final-go-no-go.md"), "utf8");
const releaseFinalGoNoGoGatePath = path.join(artifactRoot, "release/release-final-go-no-go-gate.sh");
const releaseFinalGoNoGoGate = fs.readFileSync(releaseFinalGoNoGoGatePath, "utf8");
const releasePreflightGatePath = path.join(artifactRoot, "release/release-preflight-gate.sh");
const releasePreflightGate = fs.readFileSync(releasePreflightGatePath, "utf8");
const releaseFinalOwnerQueue = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-final-owner-queue.json"), "utf8"));
const releaseFinalOwnerQueueCsv = fs.readFileSync(path.join(artifactRoot, "release/release-final-owner-queue.csv"), "utf8");
const releaseFinalOwnerQueueMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-final-owner-queue.md"), "utf8");
const releaseFinalOwnerQueueCommandsPath = path.join(artifactRoot, "release/release-final-owner-queue-commands.sh");
const releaseFinalOwnerQueueCommands = fs.readFileSync(releaseFinalOwnerQueueCommandsPath, "utf8");
const releaseFinalOwnerQueueEnvTemplate = fs.readFileSync(path.join(artifactRoot, "release/release-final-owner-queue-env.template.env"), "utf8");
const releaseFinalOwnerQueueEnvInitPath = path.join(artifactRoot, "release/release-final-owner-queue-env-init.sh");
const releaseFinalOwnerQueueEnvInit = fs.readFileSync(releaseFinalOwnerQueueEnvInitPath, "utf8");
const releaseEnvBootstrapPath = path.join(artifactRoot, "release/release-env-bootstrap.sh");
const releaseEnvBootstrap = fs.readFileSync(releaseEnvBootstrapPath, "utf8");
const releaseFastTrackMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-fast-track.md"), "utf8");
const releasePriority = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-action-priority.json"), "utf8"));
const releasePriorityCsv = fs.readFileSync(path.join(artifactRoot, "release/release-action-priority.csv"), "utf8");
const releasePriorityMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-action-priority.md"), "utf8");
const releaseBatches = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-action-batches.json"), "utf8"));
const releaseBatchesCsv = fs.readFileSync(path.join(artifactRoot, "release/release-action-batches.csv"), "utf8");
const releaseBatchesMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-action-batches.md"), "utf8");
const releaseDependencyGraph = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-action-dependency-graph.json"), "utf8"));
const releaseDependencyGraphMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-action-dependency-graph.md"), "utf8");
const releaseExecutionQueue = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-execution-queue.json"), "utf8"));
const releaseExecutionQueueCsv = fs.readFileSync(path.join(artifactRoot, "release/release-execution-queue.csv"), "utf8");
const releaseExecutionQueueMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-execution-queue.md"), "utf8");
const releaseExecutionCommandsPath = path.join(artifactRoot, "release/release-execution-commands.sh");
const releaseExecutionCommands = fs.readFileSync(releaseExecutionCommandsPath, "utf8");
const releaseMissingEnv = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-missing.json"), "utf8"));
const releaseEnvOwnerMatrix = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-matrix.json"), "utf8"));
const releaseEnvOwnerMatrixCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-matrix.csv"), "utf8");
const releaseEnvOwnerMatrixMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-matrix.md"), "utf8");
const releaseEnvFillPriority = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-fill-priority.json"), "utf8"));
const releaseEnvFillPriorityCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-fill-priority.csv"), "utf8");
const releaseEnvFillPriorityMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-fill-priority.md"), "utf8");
const releaseEnvCanonicalFill = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-canonical-fill.json"), "utf8"));
const releaseEnvCanonicalFillCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-canonical-fill.csv"), "utf8");
const releaseEnvCanonicalFillMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-canonical-fill.md"), "utf8");
const releaseEnvCanonicalFillTemplate = fs.readFileSync(path.join(artifactRoot, "release/release-env-canonical-fill.template.env"), "utf8");
for (const relativePath of [
  "release/readiness-summary.json",
  "release/release-final-go-no-go.json",
  "release/release-final-go-no-go.md",
  "release/release-owner-input-receipt.json",
  "release/release-owner-input-receipt.csv",
  "release/release-owner-input-receipt-items.csv",
  "release/release-owner-input-receipt-items.md",
  "release/release-owner-input-receipt.md",
  "release/release-env-canonical-fill.json",
  "release/release-env-canonical-fill.md",
  "release/release-blocker-closure-plan.json",
  "release/owner-action-rollup.json",
]) {
  const generated = fs.readFileSync(path.join(artifactRoot, relativePath), "utf8");
  assert.doesNotMatch(generated, new RegExp(escapeRegExp(repoRoot)), `${relativePath} should not include repo absolute paths`);
}
const releaseEnvReadinessRedacted = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-readiness-redacted.json"), "utf8"));
const releaseEnvReadinessRedactedCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-readiness-redacted.csv"), "utf8");
const releaseEnvReadinessRedactedMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-readiness-redacted.md"), "utf8");
const releaseEnvReadinessGatePath = path.join(artifactRoot, "release/release-env-readiness-gate.sh");
const releaseEnvReadinessGate = fs.readFileSync(releaseEnvReadinessGatePath, "utf8");
const releaseEnvOwnerHandoffRedacted = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff-redacted.json"), "utf8"));
const releaseEnvOwnerInputPacket = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-input-packet.json"), "utf8"));
const releaseEnvOwnerInputPacketCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-input-packet.csv"), "utf8");
const releaseEnvOwnerInputPacketMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-input-packet.md"), "utf8");
const releaseEnvOwnerInputPacketDir = path.join(artifactRoot, "release/release-env-owner-input-packet");
const ownerInputReceiptItemChecklistByOwner = new Map((releaseEnvOwnerInputPacket.owners || []).map((owner) => [
  owner.owner,
  `artifacts/ddd/release/release-owner-input-receipt-items/${owner.fileName}.md`,
]));
const releaseConfigOwnerInputReconciliation = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-config-owner-input-reconciliation.json"), "utf8"));
const releaseOwnerInputReceipt = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-owner-input-receipt.json"), "utf8"));
const releaseOwnerInputReceiptCsv = fs.readFileSync(path.join(artifactRoot, "release/release-owner-input-receipt.csv"), "utf8");
const releaseOwnerInputReceiptItemsCsv = fs.readFileSync(path.join(artifactRoot, "release/release-owner-input-receipt-items.csv"), "utf8");
const releaseOwnerInputReceiptItemsMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-owner-input-receipt-items.md"), "utf8");
const releaseOwnerInputReceiptItemsDir = path.join(artifactRoot, "release/release-owner-input-receipt-items");
const releaseOwnerInputReceiptMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-owner-input-receipt.md"), "utf8");
const releaseArtifactIntegrity = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-artifact-integrity.json"), "utf8"));
const releaseArtifactIntegrityMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-artifact-integrity.md"), "utf8");
const releaseArtifactIntegrityGatePath = path.join(artifactRoot, "release/release-artifact-integrity-gate.sh");
const releaseArtifactIntegrityGate = fs.readFileSync(releaseArtifactIntegrityGatePath, "utf8");
const releaseEnvOwnerHandoffRedactedCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff-redacted.csv"), "utf8");
const releaseEnvOwnerHandoffRedactedMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff-redacted.md"), "utf8");
const releaseEnvOwnerHandoff = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff.json"), "utf8"));
const releaseEnvOwnerHandoffCsv = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff.csv"), "utf8");
const releaseEnvOwnerHandoffMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-handoff.md"), "utf8");
const releaseEnvOwnerTemplates = JSON.parse(fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-templates.json"), "utf8"));
const releaseEnvOwnerTemplatesMarkdown = fs.readFileSync(path.join(artifactRoot, "release/release-env-owner-templates.md"), "utf8");
const releaseMissingEnvTemplate = fs.readFileSync(path.join(artifactRoot, "release/release-env-missing.template.env"), "utf8");

function assertReleaseScriptPortable(scriptName, scriptText) {
  assert(
    scriptText.includes("SCRIPT_DIR=$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)"),
    `${scriptName} must infer its location before resolving the repo root`,
  );
  assert(
    scriptText.includes("if [[ -f \"scripts/ddd-release-readiness-summary.mjs\" ]]; then"),
    `${scriptName} must detect when it is already running from the repo root`,
  );
  assert(
    scriptText.includes("LUMIRA_REPO_ROOT=$(pwd)"),
    `${scriptName} must use the current repo root when invoked from the repository`,
  );
  assert(
    scriptText.includes("LUMIRA_REPO_ROOT=$(cd \"${SCRIPT_DIR}/../../..\" && pwd)"),
    `${scriptName} must fall back to resolving LUMIRA_REPO_ROOT from the script location`,
  );
  assert(!scriptText.includes(repoRoot), `${scriptName} must not embed the local repo path`);
}

function assertReleaseEnvFileSafety(scriptName, scriptText) {
  assert(
    scriptText.includes("safe_load_release_env_file"),
    `${scriptName} must safely parse DDD_RELEASE_ENV_FILE instead of sourcing it`,
  );
  assert.doesNotMatch(
    scriptText,
    /^\s*source "\$\{DDD_RELEASE_ENV_FILE\}"/m,
    `${scriptName} must not source DDD_RELEASE_ENV_FILE during execution`,
  );
  for (const templateName of [
    "release-env-missing.template.env",
    "release-closure-wave-env.template.env",
    "release-final-owner-queue-env.template.env",
  ]) {
    assert(
      scriptText.includes(templateName),
      `${scriptName} must reject ${templateName} as release evidence`,
    );
  }
  assert(
    scriptText.includes("Template env files are worksheets, not release evidence"),
    `${scriptName} must explain template env rejection`,
  );
  assert(
    scriptText.includes("DDD_RELEASE_ENV_FILE_MODE="),
    `${scriptName} must inspect release env file permissions before sourcing`,
  );
  assert(
    scriptText.includes("Release env file permissions are too broad"),
    `${scriptName} must reject group/other-readable release env files`,
  );
  assert(
    scriptText.includes("use chmod 600"),
    `${scriptName} must tell operators how to fix release env file permissions`,
  );
}

for (const [scriptName, scriptText] of [
  ["release-artifact-integrity-gate.sh", releaseArtifactIntegrityGate],
  ["release-env-readiness-gate.sh", releaseEnvReadinessGate],
  ["release-final-go-no-go-gate.sh", releaseFinalGoNoGoGate],
  ["release-preflight-gate.sh", releasePreflightGate],
  ["release-env-bootstrap.sh", releaseEnvBootstrap],
  ["release-execution-commands.sh", releaseExecutionCommands],
  ["release-next-action-commands.sh", releaseNextActionCommands],
  ["release-blocker-closure-commands.sh", releaseBlockerClosureCommands],
  ["release-performance-baseline-commands.sh", releasePerformanceBaselineCommands],
  ["release-final-owner-queue-commands.sh", releaseFinalOwnerQueueCommands],
  ["release-final-owner-queue-env-init.sh", releaseFinalOwnerQueueEnvInit],
]) {
  assertReleaseScriptPortable(scriptName, scriptText);
}

assert(releasePreflightGate.includes("advisoryOnly: !enforce"));
assert(releasePreflightGate.includes("advisoryFailureCount"));
assert(releasePreflightGate.includes("advisoryFailures"));
assert(releasePreflightGate.includes("cutoverAllowed"));
assert(releasePreflightGate.includes("releaseEnvFileCutoverSafe"));
assert(releasePreflightGate.includes("finalRecommendation"));
assert(releasePreflightGate.includes("gateBlockers"));
assert(releasePreflightGate.includes("cutoverDecisionSource: 'artifacts/ddd/release/release-final-go-no-go.json'"));
assert(releasePreflightGate.includes("Default preflight PASS means checks completed; it is not cutover approval."));

const finalGoNoGoEnforceCommand = "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh";
assert(releaseFinalGoNoGoGate.includes("DDD_STAGING_FINAL_REVIEW_ENFORCE"));
assert(releaseFinalGoNoGoGate.includes("DDD_NODE_BIN"));
assert(releaseFinalGoNoGoGate.includes("\"${DDD_NODE_BIN}\" scripts/ddd-staging-execution-checklist.mjs --final-review-enforce"));
assert(releaseFinalGoNoGoGate.includes("[ddd-final-go-no-go][staging-final-review-blocked]"));
for (const [scriptName, scriptText] of [
  ["release-env-bootstrap.sh", releaseEnvBootstrap],
  ["release-execution-commands.sh", releaseExecutionCommands],
  ["release-next-action-commands.sh", releaseNextActionCommands],
  ["release-blocker-closure-commands.sh", releaseBlockerClosureCommands],
  ["release-performance-baseline-commands.sh", releasePerformanceBaselineCommands],
  ["release-final-owner-queue-commands.sh", releaseFinalOwnerQueueCommands],
]) {
  assert(
    scriptText.includes(finalGoNoGoEnforceCommand),
    `${scriptName} must force final go/no-go before completing a release execution path`,
  );
}

for (const [scriptName, scriptText] of [
  ["release-execution-commands.sh", releaseExecutionCommands],
  ["release-next-action-commands.sh", releaseNextActionCommands],
  ["release-blocker-closure-commands.sh", releaseBlockerClosureCommands],
  ["release-performance-baseline-commands.sh", releasePerformanceBaselineCommands],
  ["release-final-owner-queue-commands.sh", releaseFinalOwnerQueueCommands],
]) {
  assertReleaseEnvFileSafety(scriptName, scriptText);
}
assert.match(releaseEnvBootstrap, /ddd-release-env-owner-templates-merge\.mjs/);
assert.match(releaseEnvBootstrap, /ddd-release-env-canonical-merge\.mjs/);
assert.match(releaseEnvBootstrap, /ddd-release-env-safe-defaults\.mjs/);
assert.match(releaseEnvBootstrap, /ddd-release-provenance-defaults\.mjs/);
assert.match(releaseEnvBootstrap, /ddd-release-env-alias-sync\.mjs/);
assert.match(releaseEnvBootstrap, /ddd-release-env-file-lint\.mjs/);
assert.match(releaseEnvBootstrap, /DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts\/ddd\/release\/release-env-readiness-gate\.sh/);
assert.match(releaseEnvBootstrap, /DDD_RELEASE_MANIFEST_CHECK_ENV=true "\$\{DDD_NODE_BIN\}" scripts\/ddd-release-evidence-manifest\.mjs/);
assert.match(releaseEnvBootstrap, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
assert.match(releaseEnvBootstrap, /DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT/);
assert.match(releaseEnvBootstrap, /DDD_NODE_BIN="\$\{DDD_NODE_BIN:-node\}"/);
assert.match(releaseEnvBootstrap, /export DDD_NODE_BIN/);
assert.match(releaseEnvBootstrap, /write_bootstrap_receipt\(\)/);
assert.match(releaseEnvBootstrap, /trap on_bootstrap_exit EXIT/);
assert.match(releaseEnvBootstrap, /Refusing to use a non-canonical generated env file as DDD_RELEASE_CANONICAL_ENV_FILE/);
for (const step of [
  "owner-templates-merge",
  "canonical-merge",
  "safe-defaults",
  "provenance-defaults",
  "alias-sync",
  "canonical-lint",
  "env-readiness-gate",
  "release-env-lint",
  "release-config-evidence",
  "manifest-provenance-env",
  "readiness-summary",
  "final-go-no-go",
  "complete",
]) {
  assert(
    releaseEnvBootstrap.includes(`DDD_RELEASE_ENV_BOOTSTRAP_STEP="${step}"`),
    `release env bootstrap must record the ${step} step`,
  );
}
assert.match(releaseEnvBootstrap, /failedStep:/);
assert.match(releaseEnvBootstrap, /completedStep:/);
assert.match(releaseEnvBootstrap, /FAIL/);
assert.match(releaseEnvBootstrap, /PASS/);
assert.match(releaseEnvBootstrap, /nextCommand:/);
assert.match(releaseEnvBootstrap, /artifactIntegrityGateCommand:/);
assert.match(releaseEnvBootstrap, /envReadinessGateCommand:/);
assert.match(releaseEnvBootstrap, /ownerHandoffCsv:/);
assert.match(releaseEnvBootstrap, /finalGoNoGoGateCommand:/);
assert.match(releaseEnvBootstrap, /finalGoNoGoPacket:/);
assert.match(releaseEnvBootstrap, /finalGoNoGoMarkdown:/);
assert.doesNotMatch(releaseEnvBootstrap, /\bsource\s+/);
const releaseEnvBootstrapReceiptPath = path.join(artifactRoot, "release", "release-env-bootstrap-test-receipt.json");
const releaseEnvBootstrapTarget = path.join(artifactRoot, "release", ".env.bootstrap-test.local");
const releaseEnvBootstrapNodeBin = createBashNodeWrapper();
fs.writeFileSync(releaseEnvBootstrapTarget, "TRUST_FORWARDED_HEADERS=true\n");
fs.chmodSync(releaseEnvBootstrapTarget, 0o600);
if (process.platform !== "win32") {
  const releaseEnvBootstrapRun = runBashWithEnv(releaseEnvBootstrapPath, {
    LUMIRA_REPO_ROOT: toBashPath(repoRoot),
    DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
    DDD_RELEASE_ENV_FILE: toBashPath(releaseEnvBootstrapTarget),
    DDD_RELEASE_CANONICAL_ENV_FILE: toBashPath(path.join(artifactRoot, "release/release-env-missing.template.env")),
    DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT: toBashPath(releaseEnvBootstrapReceiptPath),
  });
  assert.notEqual(releaseEnvBootstrapRun.status, 0);
  const releaseEnvBootstrapReceipt = JSON.parse(fs.readFileSync(releaseEnvBootstrapReceiptPath, "utf8"));
  assert.equal(releaseEnvBootstrapReceipt.status, "FAIL");
  assert(
    ["init", "canonical-merge", "canonical-lint"].includes(releaseEnvBootstrapReceipt.failedStep),
    `unexpected bootstrap failedStep: ${releaseEnvBootstrapReceipt.failedStep}`,
  );
  assert.equal(releaseEnvBootstrapReceipt.artifactIntegrityGateCommand, "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
  assert.equal(releaseEnvBootstrapReceipt.artifactIntegrityArtifact, "artifacts/ddd/release/release-artifact-integrity.json");
  assert.equal(releaseEnvBootstrapReceipt.artifactIntegrityMarkdown, "artifacts/ddd/release/release-artifact-integrity.md");
  assert.equal(releaseEnvBootstrapReceipt.envSafeDefaultsCommand, "node scripts/ddd-release-env-safe-defaults.mjs");
  assert.equal(releaseEnvBootstrapReceipt.envSafeDefaultsArtifact, "artifacts/ddd/release/release-env-safe-defaults.json");
  assert.equal(releaseEnvBootstrapReceipt.provenanceDefaultsCommand, "node scripts/ddd-release-provenance-defaults.mjs");
  assert.equal(releaseEnvBootstrapReceipt.provenanceDefaultsArtifact, "artifacts/ddd/release/release-provenance-defaults.json");
  assert.equal(releaseEnvBootstrapReceipt.envReadinessGateCommand, "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh");
  assert.equal(releaseEnvBootstrapReceipt.envReadinessArtifact, "artifacts/ddd/release/release-env-readiness-redacted.json");
  assert.equal(releaseEnvBootstrapReceipt.envReadinessCsv, "artifacts/ddd/release/release-env-readiness-redacted.csv");
  assert.equal(releaseEnvBootstrapReceipt.ownerHandoffArtifact, "artifacts/ddd/release/release-env-owner-handoff-redacted.json");
  assert.equal(releaseEnvBootstrapReceipt.ownerHandoffCsv, "artifacts/ddd/release/release-env-owner-handoff-redacted.csv");
  assert.equal(releaseEnvBootstrapReceipt.ownerHandoffDir, "artifacts/ddd/release/release-env-owner-handoff-redacted");
  assert.equal(releaseEnvBootstrapReceipt.finalGoNoGoGateCommand, "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
  assert.equal(releaseEnvBootstrapReceipt.finalGoNoGoPacket, "artifacts/ddd/release/release-final-go-no-go.json");
  assert.equal(releaseEnvBootstrapReceipt.finalGoNoGoMarkdown, "artifacts/ddd/release/release-final-go-no-go.md");
  assert(!JSON.stringify(releaseEnvBootstrapReceipt).includes("__REQUIRED__"));
  const releaseEnvBootstrapWrongCanonicalRun = runBashWithEnv(releaseEnvBootstrapPath, {
    LUMIRA_REPO_ROOT: toBashPath(repoRoot),
    DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
    DDD_RELEASE_ENV_FILE: toBashPath(releaseEnvBootstrapTarget),
    DDD_RELEASE_CANONICAL_ENV_FILE: toBashPath(path.join(artifactRoot, "release/release-env-missing.template.env")),
    DDD_RELEASE_ENV_BOOTSTRAP_RECEIPT: toBashPath(path.join(artifactRoot, "release", "release-env-bootstrap-wrong-canonical-receipt.json")),
  });
  assert.notEqual(releaseEnvBootstrapWrongCanonicalRun.status, 0);
  assert.match(releaseEnvBootstrapWrongCanonicalRun.stderr, /Refusing to use a non-canonical generated env file as DDD_RELEASE_CANONICAL_ENV_FILE/);
}

assert.equal(summary.status, "NOT_READY");
assert.equal(summary.gate.blockers, 17);
assert.equal(summary.inputArtifacts.releaseGate.present, true);
assert.equal(summary.inputArtifacts.releaseGate.relativePath, "release/release-evidence-gate.json");
assert.equal(summary.inputArtifacts.releaseGate.blockers, 17);
assert.equal(summary.inputArtifacts.releaseGate.warnings, 0);
assert.match(summary.inputArtifacts.releaseGate.modifiedAt, /^\d{4}-\d{2}-\d{2}T/);
assert.equal(summary.inputArtifacts.ownerQueueRunReport.present, true);
assert.equal(summary.inputArtifacts.ownerQueueRunReport.relativePath, "release/release-final-owner-queue-run-report.json");
assert.equal(summary.inputArtifacts.ownerQueueRunReport.status, "FAIL");
assert.equal(summary.inputArtifacts.ownerQueueRunReport.generatedAt, "2026-06-14T00:00:00.000Z");
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("inputArtifacts.ownerQueueRunReport")));
assert.equal(summary.inputArtifacts.ownerQueueEnvInitReceipt.present, true);
assert.equal(summary.inputArtifacts.ownerQueueEnvInitReceipt.relativePath, "release/release-final-owner-queue-env-init-receipt.json");
assert.equal(summary.inputArtifacts.ownerQueueEnvInitReceipt.generatedAt, "2026-06-14T00:00:00.000Z");
assert.equal(summary.diagnostics.ownerQueueEnvInitReceipt.permissionSafe, true);
assert.equal(summary.diagnostics.ownerQueueEnvInitReceipt.targetModeOctal, "600");
assert.equal(summary.diagnostics.ownerQueueEnvInitReceipt.unresolvedTemplateKeyCount, 2);
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("inputArtifacts.ownerQueueEnvInitReceipt")));
assert.equal(summary.inputArtifacts.explainGateReport.present, true);
assert.equal(summary.inputArtifacts.explainGateReport.relativePath, "release/explain-gate-report.json");
assert.equal(summary.inputArtifacts.explainGateReport.status, "FAIL");
assert.equal(summary.inputArtifacts.explainGateReport.blockers, 8);
assert.equal(summary.inputArtifacts.explainGateReport.generatedAt, "2026-06-14T00:00:00.000Z");
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("inputArtifacts.explainGateReport")));
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("safetySignals.releaseEnvFile")));
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("releaseFinalGoNoGo safetySignals")));
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("releaseFinalOwnerQueue safetySignals")));
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("releaseCommandCatalog safetySignals")));
assert(!summary.diagnostics.readinessSummary.contractIssues.some((issue) => issue.includes("releaseExecutionQueue safetySignals")));
assert.equal(ownerRollup.status, "NOT_READY");
assert.equal(ownerRollup.gate.blockers, 17);
assert.deepEqual(ownerRollup.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(ownerRollup.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.deepEqual(ownerRollup.inputArtifacts.explainGateReport, summary.inputArtifacts.explainGateReport);
assert.equal(ownerRollup.ownerCount, Object.keys(summary.ownerActionRollup).length);
assert.equal(ownerRollup.activeOwnerCount, ownerRollup.activeOwners.length);
assert.equal(ownerRollup.collapsedOnlyOwnerCount, ownerRollup.collapsedOnlyOwners.length);
assert.deepEqual(ownerRollup.collapsedOnlyOwners, []);
assert.equal(ownerRollup.totalPendingItems, Object.values(summary.ownerActionRollup).reduce((sum, plan) => sum + plan.pendingItems, 0));
assert.equal(ownerRollup.totalCollapsedItems, Object.values(summary.ownerActionRollup).reduce((sum, plan) => sum + (plan.collapsedItems || 0), 0));
assert.equal(ownerRollup.owners["release-performance"].pendingItems, 8);
assert.equal(ownerRollup.owners.database.sources.explain, 8);
assert.equal(ownerRollup.owners.ai.collapsedItems, 3);
assert.equal(ownerRollup.owners.database.collapsedItems, 2);
assert.equal(ownerRollup.owners.frontend.collapsedItems, 2);
assert.equal(ownerRollup.owners["release-infra"].collapsedItems, 2);
assert.equal(ownerRollup.owners["release-infra"].collapsed[0].id, "orchestrator-preflight-backend-runtime-base-url");
assert.deepEqual(ownerRollup.owners["release-infra"].collapsed[0].coveredBy, {
  source: "release-config",
  id: "backend base url",
});
assert.equal(ownerRollup.owners["release-infra"].collapsed[1].id, "orchestrator-preflight-docker-daemon");
assert.deepEqual(ownerRollup.owners["release-infra"].collapsed[1].coveredBy, {
  source: "docker",
  id: "docker-daemon",
});
assert.equal(sourceRollup.status, "NOT_READY");
assert.equal(sourceRollup.gate.blockers, 17);
assert.deepEqual(sourceRollup.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(sourceRollup.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(sourceRollup.sourceCount, Object.keys(summary.sourceActionRollup).length);
assert.equal(sourceRollup.totalPendingItems, ownerRollup.totalPendingItems);
assert.equal(sourceRollup.sources.manifest.pendingItems, 1);
assert.equal(sourceRollup.sources.explain.owners.database, 8);
assert.equal(sourceRollup.sources.docker.owners["release-infra"], 1);
assert.equal(releaseBlockerMap.status, "NOT_READY");
assert.equal(releaseBlockerMap.gate.blockers, 17);
assert.deepEqual(releaseBlockerMap.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(releaseBlockerMap.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(releaseBlockerMap.totalBlockers, summary.actions.length);
assert.equal(releaseBlockerMap.ownerCount, releaseBlockerMap.owners.length);
assert.equal(releaseBlockerMap.categories.reduce((sum, category) => sum + category.blockerCount, 0), summary.actions.length);
assert.equal(releaseBlockerMap.owners.reduce((sum, owner) => sum + owner.blockerCount, 0), summary.actions.length);
assert(releaseBlockerMap.owners.some((owner) => (
  owner.blockers.some((blocker) => blocker.check === "runtime-readiness-freshness" && blocker.structured === true)
)));
assert(releaseBlockerMap.categories.some((category) => (
  category.blockers.some((blocker) => blocker.check === "runtime-readiness-freshness" && blocker.structured === true)
)));
const releaseBlockerMapCsvRows = parseCsvRows(releaseBlockerMapCsv);
assert.deepEqual(
  releaseBlockerMapCsvRows[0],
  ["owner", "blockerCount", "categories", "readyBatchIds", "blockedBatchIds", "commands", "expectedArtifacts", "sampleBlockers"],
);
assert.equal(releaseBlockerMapCsvRows.length - 1, releaseBlockerMap.ownerCount);
const releaseBlockerMapCsvOwners = new Map(releaseBlockerMapCsvRows.slice(1).map((row) => [row[0], row]));
assert.equal(releaseBlockerMapCsvOwners.size, releaseBlockerMap.ownerCount);
for (const owner of releaseBlockerMap.owners) {
  const row = releaseBlockerMapCsvOwners.get(owner.owner);
  assert(row, `release-blocker-map.csv must include owner ${owner.owner}`);
  assert.equal(Number(row[1]), owner.blockerCount);
}
assert(releaseBlockerMap.owners.some((owner) => (
  owner.owner === "database"
    && owner.blockerCount > 0
    && owner.categories.migration > 0
    && owner.expectedArtifacts.includes("tmp/ddd-explain/*.json")
)));
assert(releaseBlockerMap.categories.some((category) => (
  category.category === "rollback-context-drills"
    && category.blockerCount > 0
    && category.blockers.length > 0
)));
assert(releaseBlockerMap.categories.some((category) => (
  category.category === "migration"
    && category.expectedArtifacts.includes("tmp/ddd-explain/*.json")
)));
assert.equal(releasePriority.status, "NOT_READY");
assert.equal(releasePriority.gate.blockers, 17);
assert.deepEqual(releasePriority.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(releasePriority.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(releasePriority.totalPendingItems, ownerRollup.totalPendingItems);
assert.equal(releasePriority.items.length, ownerRollup.totalPendingItems);
assert.equal(releasePriority.items[0].priority, "P0");
assert.equal(releasePriority.items[0].source, "release-env-lint");
assert.equal(releasePriority.items[0].check, releasePriority.items[0].id);
assert.equal(releasePriority.items[0].structured, false);
assert.match(releasePriority.items[0].action, /DDD_RELEASE_ENV_FILE/);
assert.equal(releasePriority.bySource.explain, 8);
assert(releasePriority.items.some((item) => (
  item.source === "explain"
    && item.id === "platform-runtime-appearance.json"
    && item.reason === "missing required EXPLAIN artifact"
)));
assert.equal(releasePriority.bySource.docker, 1);
assert.equal(releasePriority.bySource["release-env-lint"], 2);
assert.equal(releasePriority.policy.prioritySourceTiers.explain, "P2");
assert.equal(releasePriority.policy.prioritySourceTiers.orchestrator, "P3");
assert(releasePriority.items.some((item) => item.priority === "P1" && item.source === "frontend-smoke"));
assert(releasePriority.items.some((item) => item.priority === "P2" && item.id === "message-visible-list.json"));
assert(releasePriority.items.filter((item) => item.source === "orchestrator").every((item) => item.priority === "P3"));
const runtimePriority = releasePriority.items.find((item) => item.source === "runtime-readiness");
assert(runtimePriority.check);
assert(runtimePriority.detail);
assert.equal(releaseBatches.status, "NOT_READY");
assert.equal(releaseBatches.gate.blockers, 17);
assert.deepEqual(releaseBatches.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert(releaseBatches.batches.some((batch) => (
  batch.items.some((item) => item.check && item.detail)
)));
assert.deepEqual(releaseBatches.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(releaseBatches.totalPendingItems, releasePriority.totalPendingItems);
assert.equal(releaseDependencyGraph.status, "NOT_READY");
assert.equal(releaseDependencyGraph.gate.blockers, 17);
assert.deepEqual(releaseDependencyGraph.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(releaseDependencyGraph.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(releaseDependencyGraph.batchCount, releaseBatches.batchCount);
assert.equal(releaseDependencyGraph.nodes.length, releaseBatches.batches.length);
assert.equal(releaseDependencyGraph.edgeCount, releaseBatches.batches.reduce((sum, batch) => sum + batch.dependsOn.length, 0));
assert.equal(releaseDependencyGraph.graphDensity, Number((releaseDependencyGraph.edgeCount / (releaseDependencyGraph.batchCount * (releaseDependencyGraph.batchCount - 1))).toFixed(4)));
assert(releaseDependencyGraph.compressedEdgeCount > 0);
assert.equal(releaseDependencyGraph.compressedEdgeCount, releaseDependencyGraph.compressedEdges.length);
assert.equal(releaseExecutionQueue.status, "NOT_READY");
assert.equal(releaseExecutionQueue.gate.blockers, 17);
assert.deepEqual(releaseExecutionQueue.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(releaseExecutionQueue.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert.equal(releaseExecutionQueue.batchCount, releaseDependencyGraph.batchCount);
assert.equal(releaseExecutionQueue.readyBatchCount, releaseDependencyGraph.readyBatchIds.length);
assert.equal(releaseExecutionQueue.blockedBatchCount, releaseDependencyGraph.blockedBatchIds.length);
assert.deepEqual([...releaseExecutionQueue.nextBatchIds].sort(), [...releaseDependencyGraph.readyBatchIds].sort());
assert.equal(releaseExecutionQueue.nextPriority, "P0");
assert.equal(releasePriority.items.filter((item) => !item.reason).length, 0);
assert.equal(releasePriority.items.filter((item) => !item.action).length, 0);
assert.equal(releaseBatches.batches.filter((batch) => batch.commands.length === 0).length, 0);
assert.equal(releaseBatches.batches.filter((batch) => batch.commands.length > 0 && batch.envKeys.length === 0).length, 0);
assert.equal(releaseBatches.batches.filter((batch) => batch.expectedArtifacts.length === 0).length, 0);
assert.equal(releaseBatches.batches.filter((batch) => batch.exitCriteria.length === 0).length, 0);
const releasePriorityIdentities = releasePriority.items.map((item) => `${item.priority}:${item.source}:${item.owner}:${item.id}`).sort();
const releaseBatchIdentities = releaseBatches.batches.flatMap((batch) => (
  batch.items.map((item) => `${batch.priority}:${batch.source}:${batch.owner}:${item.id}`)
)).sort();
assert.equal(new Set(releasePriorityIdentities).size, releasePriorityIdentities.length);
assert.deepEqual(releaseBatchIdentities, releasePriorityIdentities);
assert.equal(releaseBatches.batches[0].priority, "P0");
assert.equal(releaseBatches.batches[0].id, "p0-release-env-lint-release-infra");
assert.deepEqual(releaseBatches.batches[0].dependsOn, []);
assert.equal(releaseBatches.batches[0].canRunImmediately, true);
assert.equal(releaseBatches.batches[0].source, "release-env-lint");
assert.equal(releaseBatches.batches[0].owner, "release-infra");
assert.deepEqual(releaseBatches.batches[0].commands, [
  "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs",
  "node scripts/ddd-release-config-evidence.mjs",
]);
assert.deepEqual(releaseBatches.batches[0].expectedArtifacts, [
  "artifacts/ddd/release/release-env-lint.json",
  "artifacts/ddd/config/release-config-evidence.json",
]);
assert(releaseBatches.batches[0].exitCriteria.some((criterion) => criterion.includes("completed DDD_RELEASE_ENV_FILE")));
const firstP1Batch = releaseBatches.batches.find((batch) => batch.priority === "P1");
const p0BatchIds = releaseBatches.batches.filter((batch) => batch.priority === "P0").map((batch) => batch.id);
assert.deepEqual(firstP1Batch.dependsOn, p0BatchIds);
assert.equal(firstP1Batch.canRunImmediately, false);
const aiRuntimeBatch = releaseBatches.batches.find((batch) => batch.source === "ai-runtime" && batch.owner === "ai");
assert(aiRuntimeBatch);
assert.deepEqual([...releaseDependencyGraph.readyBatchIds].sort(), [...p0BatchIds].sort());
assert(releaseDependencyGraph.blockedBatchIds.includes(aiRuntimeBatch.id));
assert.deepEqual(aiRuntimeBatch.dependsOn, p0BatchIds);
assert(releaseDependencyGraph.nodes.some((node) => (
  node.id === aiRuntimeBatch.id
    && node.canRunImmediately === false
    && node.dependsOn.includes("p0-release-env-lint-release-infra")
)));
assert(releaseDependencyGraph.edges.some((edge) => (
  edge.from === "p0-release-env-lint-release-infra"
    && edge.to === aiRuntimeBatch.id
)));
assert.deepEqual([...releaseDependencyGraph.byPriority.P0].sort(), [...p0BatchIds].sort());
assert(releaseDependencyGraph.executionLevels.some((level) => (
  level.priority === "P0"
    && level.ready === p0BatchIds.length
    && level.blocked === 0
)));
assert(releaseDependencyGraph.compressedEdges.some((edge) => edge.fromPriority === "P0" && edge.toPriority === "P1"));
assert(releaseDependencyGraph.compressedEdges.some((edge) => edge.fromPriority === "P1" && edge.toPriority === "P2"));
assert(releaseDependencyGraph.compressedEdges.some((edge) => edge.fromPriority === "P2" && edge.toPriority === "P3"));
const runtimeGraphNode = releaseDependencyGraph.nodes.find((node) => node.id === "p0-runtime-readiness-release-infra");
assert(runtimeGraphNode.envKeys.includes("LUMIRA_BASE_URL"));
assert(runtimeGraphNode.envCheckGroups.some((group) => group.spec === "LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL"));
assert.deepEqual([...releaseExecutionQueue.readyBatches.map((batch) => batch.id)].sort(), [...p0BatchIds].sort());
assert(releaseExecutionQueue.readyBatches.every((batch) => batch.priority === "P0"));
assert(releaseExecutionQueue.readyBatches.every((batch) => batch.commands.length > 0));
assert(releaseExecutionQueue.readyBatches.every((batch) => batch.expectedArtifacts.length > 0));
assert(releaseExecutionQueue.readyBatches.every((batch) => batch.exitCriteria.length > 0));
assert(releaseExecutionQueue.readyBatches.every((batch) => Array.isArray(batch.envCheckGroups) && batch.envCheckGroups.length > 0));
const runtimeQueueBatch = releaseExecutionQueue.readyBatches.find((batch) => batch.id === "p0-runtime-readiness-release-infra");
assert(runtimeQueueBatch.envCheckGroups.some((group) => (
  group.label === "LUMIRA_BASE_URL"
    && group.spec === "LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL"
)));
assert(releaseExecutionQueue.blockedBatches.some((batch) => (
  batch.id === aiRuntimeBatch.id
    && batch.unmetDependencyCount === p0BatchIds.length
    && batch.unmetDependencies.some((dependency) => dependency.id === "p0-release-env-lint-release-infra")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "manifest"
    && batch.owner === "release-performance"
    && batch.expectedArtifacts.includes("artifacts/ddd/release/evidence-manifest.json")
    && batch.expectedArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json")
    && batch.expectedArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "orchestrator"
    && batch.commands.includes("node scripts/ddd-release-evidence-orchestrator.mjs")
    && batch.commands.includes("DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict")
    && batch.expectedArtifacts.includes("artifacts/ddd/release/orchestrator-report.json")
    && batch.exitCriteria.some((criterion) => criterion.includes("strict run mode"))
)));
assert.equal(releaseBatches.batches.at(-1).source, "orchestrator");
assert.equal(releaseBatches.batches.at(-1).canRunImmediately, false);
assert(releaseBatches.batches.at(-1).dependsOn.length > p0BatchIds.length);
assert(releaseBatches.batches.some((batch) => (
  batch.source === "runtime-readiness"
    && batch.commands.includes("node scripts/ddd-runtime-readiness-smoke.mjs")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "authenticated-performance"
    && batch.commands.includes("node scripts/ddd-authenticated-performance-smoke.mjs")
    && batch.commands.includes("node scripts/ddd-promote-performance-baseline.mjs")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "frontend-smoke"
    && batch.commands.includes("node scripts/ddd-frontend-playwright-smoke.mjs")
    && batch.commands.includes("node scripts/ddd-frontend-smoke-evidence.mjs")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "migration"
    && batch.commands.includes("node scripts/ddd-migration-evidence.mjs")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "rollback"
    && batch.envKeys.includes("DDD_ROLLBACK_DRILL_FILE")
    && batch.envKeys.includes("DDD_ROLLBACK_DRILL_CHECK_ENV")
    && batch.commands.includes("DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs")
    && batch.envKeys.includes("DDD_ROLLBACK_DRILL_DEFERRAL_FILE")
    && batch.envCheckGroups.some((group) => group.spec === "DDD_ROLLBACK_DRILL_FILE=DDD_ROLLBACK_DRILL_FILE")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "explain"
    && batch.envKeys.includes("DDD_EXPLAIN_DIR")
    && batch.envKeys.includes("MYSQL_HOST")
    && batch.expectedArtifacts.includes("artifacts/ddd/release/explain-gate-report.json")
    && batch.envCheckGroups.some((group) => group.spec === "MYSQL_HOST=MYSQL_HOST")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "docker"
    && batch.owner === "release-infra"
    && batch.pendingItems === 1
    && batch.commands.includes("DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs")
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "business-e2e"
    && batch.owner === "file-owner"
    && batch.commands.includes("node scripts/ddd-file-processing-e2e-smoke.mjs")
    && JSON.stringify(batch.expectedArtifacts) === JSON.stringify(["artifacts/ddd/file/file-processing-e2e.json"])
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "business-e2e"
    && batch.owner === "payment-owner"
    && batch.commands.includes("node scripts/ddd-payment-webhook-e2e-smoke.mjs")
    && JSON.stringify(batch.expectedArtifacts) === JSON.stringify(["artifacts/ddd/payment/payment-webhook-e2e.json"])
)));
assert(releaseBatches.batches.some((batch) => (
  batch.source === "business-e2e"
    && batch.owner === "job-owner"
    && batch.commands.includes("node scripts/ddd-job-e2e-smoke.mjs")
    && JSON.stringify(batch.expectedArtifacts) === JSON.stringify(["artifacts/ddd/jobs/job-e2e-smoke.json"])
)));
assert.equal(releaseMissingEnv.status, "NOT_READY");
assert.equal(releaseMissingEnv.gate.blockers, 17);
assert.deepEqual(releaseMissingEnv.inputArtifacts.releaseGate, summary.inputArtifacts.releaseGate);
assert.deepEqual(releaseMissingEnv.inputArtifacts.ownerQueueRunReport, summary.inputArtifacts.ownerQueueRunReport);
assert(releaseMissingEnv.groups.every((group) => Array.isArray(group.expectedArtifacts) && group.expectedArtifacts.length > 0));
assert(releaseMissingEnv.groups.every((group) => Array.isArray(group.exitCriteria) && group.exitCriteria.length > 0));
assert(releaseMissingEnv.groups.every((group) => Array.isArray(group.envCheckGroups) && group.envCheckGroups.length > 0));
assert(releaseMissingEnv.groups.some((group) => (
  group.source === "manifest"
    && group.expectedArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json")
)));
assert(releaseMissingEnv.groups.some((group) => (
  group.source === "runtime-readiness"
    && group.envCheckGroups.some((envGroup) => envGroup.spec === "LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL")
)));
assert(releaseMissingEnv.uniqueEnvKeyCount > 0);
assert(releaseMissingEnv.uniqueEnvKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"));
assert.deepEqual(releaseMissingEnv.templateControlKeys, ["DDD_RELEASE_ENV_FILE"]);
assert(releaseMissingEnv.templateEnvKeyCount < releaseMissingEnv.uniqueEnvKeyCount);
assert(releaseMissingEnv.templateAliasMappings.some((mapping) => (
  mapping.alias === "LUMIRA_AI_PROVIDER_API_KEY"
    && mapping.canonical === "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"
)));
assert(!releaseMissingEnv.templateEnvKeys.includes("DDD_RELEASE_ENV_FILE"));
assert(releaseMissingEnv.groups.some((group) => group.source === "release-config" && group.owner === "ai-owner"));
assert.equal(releaseEnvOwnerMatrix.groupCount, releaseMissingEnv.groupCount);
assert.equal(releaseEnvOwnerMatrix.templateEnvKeyCount, releaseMissingEnv.templateEnvKeyCount);
const expectedOwnerMatrixUnresolvedKeys = ["JWT_SECRET", "DB_PASSWORD"]
  .filter((key) => releaseMissingEnv.templateEnvKeys.includes(key));
assert.equal(releaseEnvOwnerMatrix.uniqueUnresolvedTemplateKeyCount, expectedOwnerMatrixUnresolvedKeys.length);
assert(releaseEnvOwnerMatrix.unresolvedOwnerAssignmentCount >= releaseEnvOwnerMatrix.uniqueUnresolvedTemplateKeyCount);
assert.deepEqual(
  [...new Set(releaseEnvOwnerMatrix.owners.flatMap((owner) => owner.templateEnvKeys))].sort(),
  [...releaseMissingEnv.templateEnvKeys].sort(),
);
assert.deepEqual(
  [...new Set(releaseEnvOwnerMatrix.owners.flatMap((owner) => owner.unresolvedTemplateKeys))].sort(),
  expectedOwnerMatrixUnresolvedKeys.sort(),
);
assert(releaseEnvOwnerMatrix.owners.some((owner) => (
  owner.owner === "release-infra"
    && owner.readyBatchIds.includes("p0-release-env-lint-release-infra")
)));
assert(releaseEnvOwnerMatrix.owners.some((owner) => owner.templateEnvKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY")));
assert(releaseEnvOwnerMatrix.owners.some((owner) => (
  owner.owner === "database"
    && owner.readyBatchIds.includes("p0-migration-database")
)));
assert.equal(
  releaseEnvOwnerMatrixCsv.split("\n")[0],
  "owner,groupCount,readyGroupCount,blockedGroupCount,templateEnvKeyCount,unresolvedTemplateKeyCount,unresolvedTemplateKeys,templateEnvKeys,aliasMappings,batchIds,readyBatchIds,blockedBatchIds,commands,expectedArtifacts,exitCriteria",
);
assert(releaseEnvOwnerMatrixCsv.includes("release-infra"));
assert.match(releaseEnvOwnerMatrixMarkdown, /^# DDD Release Env Owner Matrix/m);
assert.match(releaseEnvOwnerMatrixMarkdown, new RegExp(`Unique unresolved template env keys: ${expectedOwnerMatrixUnresolvedKeys.length}`));
assert.match(releaseEnvOwnerMatrixMarkdown, /Unresolved owner assignments: \d+/);
assert.match(releaseEnvOwnerMatrixMarkdown, /## release-infra/);
assert.equal(releaseEnvFillPriority.uniqueUnresolvedTemplateKeyCount, releaseEnvOwnerMatrix.uniqueUnresolvedTemplateKeyCount);
assert.equal(releaseEnvFillPriority.unresolvedOwnerAssignmentCount, releaseEnvOwnerMatrix.unresolvedOwnerAssignmentCount);
assert.equal(releaseEnvFillPriority.ownerCount, releaseEnvFillPriority.owners.length);
assert.equal(
  releaseEnvFillPriority.filledOwnerAssignmentCount
    + releaseEnvFillPriority.placeholderOwnerAssignmentCount
    + releaseEnvFillPriority.missingOwnerAssignmentCount,
  releaseEnvFillPriority.unresolvedOwnerAssignmentCount,
);
assert(releaseEnvFillPriority.owners.every((owner, index) => owner.fillOrder === index + 1));
assert(releaseEnvFillPriority.owners.every((owner) => owner.unresolvedTemplateKeyCount > 0));
assert(releaseEnvFillPriority.owners.every((owner) => (
  owner.filledTemplateKeyCount + owner.placeholderTemplateKeyCount + owner.missingTemplateKeyCount
    === owner.unresolvedTemplateKeyCount
)));
assert(releaseEnvFillPriority.owners.some((owner) => owner.placeholderTemplateKeys.includes("DB_PASSWORD")));
assert(releaseEnvFillPriority.owners.some((owner) => (owner.fillStatusByKey || []).some((item) => (
  item.envKey === "DB_PASSWORD" && item.status === "placeholder"
))));
const firstWaitingFillOwnerIndex = releaseEnvFillPriority.owners.findIndex((owner) => owner.priority === "WAITING");
if (firstWaitingFillOwnerIndex >= 0) {
  assert(releaseEnvFillPriority.owners.slice(0, firstWaitingFillOwnerIndex).every((owner) => owner.priority === "RUN_NOW"));
}
assert.equal(
  releaseEnvFillPriorityCsv.split("\n")[0],
  "fillOrder,owner,priority,readyGroupCount,blockedGroupCount,unresolvedTemplateKeyCount,filledTemplateKeyCount,placeholderTemplateKeyCount,missingTemplateKeyCount,unresolvedTemplateKeys,filledTemplateKeys,placeholderTemplateKeys,missingTemplateKeys,fillStatusByKey,readyBatchIds,blockedBatchIds,commands,exitCriteria",
);
assert.match(releaseEnvFillPriorityMarkdown, /^# DDD Release Env Fill Priority/m);
assert.match(releaseEnvFillPriorityMarkdown, /Run now owners: \d+/);
assert.match(releaseEnvFillPriorityMarkdown, /Placeholder owner assignments: \d+/);
assert.match(releaseEnvFillPriorityMarkdown, /DB_PASSWORD` \(placeholder\)/);
assert.match(releaseEnvFillPriorityMarkdown, /## 1\. /);
assert.equal(releaseEnvCanonicalFill.canonicalFillItemCount, releaseEnvCanonicalFill.items.length);
assert(releaseEnvCanonicalFill.canonicalFillItemCount > 0);
assert(releaseEnvCanonicalFill.unresolvedAliasCount >= 0);
assert(releaseEnvCanonicalFill.items.every((item, index) => item.fillOrder === index + 1));
for (const canonicalKey of [
  "REDIS_PASSWORD",
  "TRUST_FORWARDED_HEADERS",
  "DDD_PAYMENT_WEBHOOK_SECRET",
  "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED",
  "LUMIRA_AI_OWNER_INTEGRATIONS_IAM_ENABLED",
  "LUMIRA_AI_OWNER_INTEGRATIONS_PLATFORM_ENABLED",
  "LUMIRA_AI_OWNER_INTEGRATIONS_FILE_ENABLED",
]) {
  assert(
    releaseEnvCanonicalFill.items.some((item) => item.canonicalKey === canonicalKey),
    `canonical fill must include ${canonicalKey}`,
  );
}
assert(releaseEnvCanonicalFill.items.some((item) => (
  item.canonicalKey === "DB_PASSWORD"
    && item.valueClass === "secret"
    && item.secret === true
    && item.safeToPreFill === false
    && /secret manager/.test(item.fillGuidance)
    && item.unresolvedAliases.includes("DB_PASSWORD")
    && item.aliases.includes("MYSQL_PASSWORD")
    && item.aliasSyncCommand.includes("ddd-release-env-alias-sync.mjs")
)));
assert.equal(
  releaseEnvCanonicalFillCsv.split("\n")[0],
  "fillOrder,owner,owners,group,requirement,canonicalKey,valueClass,secret,safeToPreFill,fillGuidance,aliasCount,unresolvedAliasCount,aliases,unresolvedAliases,required,https,nonLocal,minLength,expectedValues,pattern,disallowValues,aliasSyncCommand",
);
assert.match(releaseEnvCanonicalFillMarkdown, /^# DDD Release Env Canonical Fill/m);
assert.match(releaseEnvCanonicalFillMarkdown, /Fill the canonical key once, then run alias sync/);
assert.match(releaseEnvCanonicalFillMarkdown, /## \d+\. DB_PASSWORD/);
assert.match(releaseEnvCanonicalFillMarkdown, /Value class: secret; secret=true; safeToPreFill=false/);
assert.match(releaseEnvCanonicalFillTemplate, /^# Lumira DDD canonical release environment fill template\./m);
assert.match(releaseEnvCanonicalFillTemplate, /^DB_PASSWORD=__REQUIRED__$/m);
assert.match(releaseEnvCanonicalFillTemplate, /^REDIS_PASSWORD=$/m);
assert.match(releaseEnvCanonicalFillTemplate, /^TRUST_FORWARDED_HEADERS=true$/m);
assert.match(releaseEnvCanonicalFillTemplate, /^DDD_PAYMENT_WEBHOOK_SECRET=$/m);
assert.match(releaseEnvCanonicalFillTemplate, /^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_ENABLED=true$/m);
assert.match(releaseEnvCanonicalFillTemplate, /# Aliases: DB_PASSWORD, SPRING_DATASOURCE_PASSWORD, MYSQL_PASSWORD/);
assert.match(releaseEnvCanonicalFillTemplate, /# Value class: secret; secret=true; safeToPreFill=false/);
assert.match(releaseEnvCanonicalFillTemplate, /ddd-release-env-canonical-merge\.mjs/);
assert.doesNotMatch(releaseEnvCanonicalFillTemplate, /^MYSQL_PASSWORD=__REQUIRED__$/m);
const releaseEnvCanonicalFillTemplateKeys = [...releaseEnvCanonicalFillTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=(.*)$/gm)].map((match) => match[1]);
assert.equal(new Set(releaseEnvCanonicalFillTemplateKeys).size, releaseEnvCanonicalFillTemplateKeys.length);
assert.deepEqual(
  releaseEnvCanonicalFillTemplateKeys.sort(),
  releaseEnvCanonicalFill.items.map((item) => item.canonicalKey).sort(),
);
assert.equal(releaseEnvReadinessRedacted.redacted, true);
assert.match(releaseEnvReadinessRedacted.valuePolicy, /No concrete environment values are emitted/);
assert.equal(releaseEnvReadinessRedacted.summary.totalCanonicalKeys, releaseEnvCanonicalFill.canonicalFillItemCount);
assert.equal(releaseEnvReadinessRedacted.items.length, releaseEnvCanonicalFill.items.length);
assert(releaseEnvReadinessRedacted.summary.secretKeys > 0);
assert.equal(
  releaseEnvReadinessRedacted.summary.blockingSafeDefaultAvailable,
  releaseEnvReadinessRedacted.items.filter((item) => item.safeDefaultAvailable === true).length,
);
assert.equal(
  releaseEnvReadinessRedacted.summary.blockingRequiresOwnerInput,
  releaseEnvReadinessRedacted.items.filter((item) => item.requiresOwnerInput === true).length,
);
assert.equal(
  releaseEnvReadinessRedacted.summary.safeDefaultsExhausted,
  releaseEnvReadinessRedacted.summary.blockingSafeDefaultAvailable === 0,
);
assert(releaseEnvReadinessRedacted.items.some((item) => item.canonicalKey === "DB_PASSWORD" && item.secret === true && item.status === "PLACEHOLDER" && item.requiresOwnerInput === true && item.ownerInputReason === "secret-manager"));
assert(releaseEnvReadinessRedacted.items.some((item) => item.canonicalKey === "LUMIRA_BASE_URL" && item.status === "FILLED_REDACTED" && item.blocker === false && item.ownerInputReason === "not-blocking"));
assert(!JSON.stringify(releaseEnvReadinessRedacted).includes("__REQUIRED__"));
assert.doesNotMatch(releaseEnvReadinessRedactedCsv, /DB_PASSWORD=/);
assert.match(releaseEnvReadinessRedactedCsv.split("\n")[0], /canonicalKey,status,required,secret,valueClass/);
assert.match(releaseEnvReadinessRedactedCsv.split("\n")[0], /safeDefaultAvailable,requiresOwnerInput,ownerInputReason/);
assert.match(releaseEnvReadinessRedactedMarkdown, /^# DDD Release Env Readiness Redacted/m);
assert.match(releaseEnvReadinessRedactedMarkdown, /Safe defaults exhausted:/);
assert.match(releaseEnvReadinessRedactedMarkdown, /Concrete values are intentionally omitted/);
assert.match(releaseEnvReadinessGate, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert.match(releaseEnvReadinessGate, /DDD_RELEASE_ENV_READINESS_ENFORCE/);
assert.match(releaseEnvReadinessGate, /DDD_NODE_BIN="\$\{DDD_NODE_BIN:-node\}"/);
assert.match(releaseEnvReadinessGate, /"\$\{DDD_NODE_BIN\}" --input-type=module/);
assert.match(releaseEnvReadinessGate, /Exit codes: 21 means release env values are unresolved; 22 means the redacted readiness packet is invalid/);
assert.match(releaseEnvReadinessGate, /exit\(21\)/);
const releaseEnvReadinessGateSyntax = spawnSync("bash", ["-n", toBashPath(releaseEnvReadinessGatePath)], { encoding: "utf8" });
assert.equal(releaseEnvReadinessGateSyntax.status, 0, releaseEnvReadinessGateSyntax.stderr);
const releaseEnvReadinessGateDefault = runBashWithEnv(releaseEnvReadinessGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
});
assert.equal(releaseEnvReadinessGateDefault.status, 0, releaseEnvReadinessGateDefault.stderr);
assert.match(releaseEnvReadinessGateDefault.stdout, /ddd-release-env-readiness.*blockers=\d+/);
assert.match(releaseEnvReadinessGateDefault.stdout, /exitCodes unresolved=21 invalidPacket=22/);
assert.match(releaseEnvReadinessGateDefault.stdout, /handoff=.*release-env-owner-handoff-redacted\.md/);
assert.match(releaseEnvReadinessGateDefault.stdout, /handoffCsv=.*release-env-owner-handoff-redacted\.csv/);
assert.match(releaseEnvReadinessGateDefault.stdout, /dir=artifacts\/ddd\/release\/release-env-owner-handoff-redacted/);
const releaseEnvReadinessGateEnforced = runBashWithEnv(releaseEnvReadinessGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
  DDD_RELEASE_ENV_READINESS_ENFORCE: "1",
});
assert.equal(releaseEnvReadinessGateEnforced.status, 21);
assert.match(releaseEnvReadinessGateEnforced.stderr, /unresolved release env values remain/);
const releaseEnvReadinessGateInvalidPacketPath = path.join(artifactRoot, "release", "release-env-readiness-invalid.json");
fs.writeFileSync(releaseEnvReadinessGateInvalidPacketPath, `${JSON.stringify({ status: "NOT_READY", summary: {} }, null, 2)}\n`);
const releaseEnvReadinessGateInvalidPacket = runBashWithEnv(releaseEnvReadinessGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
  DDD_RELEASE_ENV_READINESS_PACKET: toBashPath(releaseEnvReadinessGateInvalidPacketPath),
});
assert.equal(releaseEnvReadinessGateInvalidPacket.status, 22);
assert.match(releaseEnvReadinessGateInvalidPacket.stderr, /invalid-packet/);
const releaseEnvReadinessGateInvalidCountsPath = path.join(artifactRoot, "release", "release-env-readiness-invalid-counts.json");
fs.writeFileSync(releaseEnvReadinessGateInvalidCountsPath, `${JSON.stringify({
  ...releaseEnvReadinessRedacted,
  summary: {
    ...releaseEnvReadinessRedacted.summary,
    blockers: releaseEnvReadinessRedacted.summary.blockers + 1,
  },
}, null, 2)}\n`);
const releaseEnvReadinessGateInvalidCounts = runBashWithEnv(releaseEnvReadinessGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
  DDD_RELEASE_ENV_READINESS_PACKET: toBashPath(releaseEnvReadinessGateInvalidCountsPath),
});
assert.equal(releaseEnvReadinessGateInvalidCounts.status, 22);
assert.match(releaseEnvReadinessGateInvalidCounts.stderr, /invalid-counts/);
const releaseEnvReadinessGateInvalidOwnerCountsPath = path.join(artifactRoot, "release", "release-env-readiness-invalid-owner-counts.json");
fs.writeFileSync(releaseEnvReadinessGateInvalidOwnerCountsPath, `${JSON.stringify({
  ...releaseEnvReadinessRedacted,
  byOwner: releaseEnvReadinessRedacted.byOwner.map((owner, index) => index === 0
    ? { ...owner, blockers: owner.blockers + 1 }
    : owner),
}, null, 2)}\n`);
const releaseEnvReadinessGateInvalidOwnerCounts = runBashWithEnv(releaseEnvReadinessGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
  DDD_RELEASE_ENV_READINESS_PACKET: toBashPath(releaseEnvReadinessGateInvalidOwnerCountsPath),
});
assert.equal(releaseEnvReadinessGateInvalidOwnerCounts.status, 22);
assert.match(releaseEnvReadinessGateInvalidOwnerCounts.stderr, /invalid-owner-counts/);
assert.equal(releaseEnvOwnerHandoffRedacted.redacted, true);
assert.equal(releaseEnvOwnerHandoffRedacted.ownerCount, releaseEnvReadinessRedacted.summary.ownerCount);
assert(releaseEnvOwnerHandoffRedacted.owners.some((owner) => owner.owner === "release-infra" && owner.placeholders > 0));
assert.equal(releaseEnvOwnerHandoffRedacted.fastPath.commands.at(-1), finalGoNoGoEnforceCommand);
assert(releaseEnvOwnerHandoffRedacted.owners.every((owner) => owner.nextCommand === "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh"));
assert.match(releaseEnvOwnerHandoffRedactedMarkdown, /^# DDD Release Env Owner Handoff Redacted/m);
assert.match(releaseEnvOwnerHandoffRedactedMarkdown, /## Fast Path/);
assert(releaseEnvOwnerHandoffRedacted.validationCommands.includes(finalGoNoGoEnforceCommand));
assert.match(releaseEnvOwnerHandoffRedactedMarkdown, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
assert.match(releaseEnvOwnerHandoffRedactedMarkdown, /release-env-owner-handoff-redacted\/\d+-release-infra\.md/);
assert.match(releaseEnvOwnerHandoffRedactedCsv.split("\n")[0], /owner,handoffPath,ownerTotalKeys,ownerBlockers/);
assert.match(releaseEnvOwnerHandoffRedactedCsv.split("\n")[0], /nextCommand,canonicalKey/);
assert.match(releaseEnvOwnerHandoffRedactedCsv.split("\n")[0], /ownerSafeDefaultAvailable,ownerRequiresOwnerInput,ownerInputReasons/);
assert.match(releaseEnvOwnerHandoffRedactedCsv.split("\n")[0], /safeDefaultAvailable,requiresOwnerInput,ownerInputReason/);
assert.match(releaseEnvOwnerHandoffRedactedCsv, /release-infra,.*DB_PASSWORD,PLACEHOLDER/);
assert.doesNotMatch(releaseEnvOwnerHandoffRedactedCsv, /DB_PASSWORD=/);
assert.doesNotMatch(releaseEnvOwnerHandoffRedactedCsv, /__REQUIRED__/);
assert.equal(releaseEnvOwnerInputPacket.redacted, true);
assert.equal(releaseEnvOwnerInputPacket.summary.requiredOwnerInputs, releaseEnvReadinessRedacted.summary.blockingRequiresOwnerInput);
assert.equal(releaseEnvOwnerInputPacket.summary.blockingSafeDefaultAvailable, releaseEnvReadinessRedacted.summary.blockingSafeDefaultAvailable);
assert.equal(releaseEnvOwnerInputPacket.summary.safeDefaultsExhausted, releaseEnvReadinessRedacted.summary.safeDefaultsExhausted);
assert(releaseEnvOwnerInputPacket.items.every((item) => item.requiresOwnerInput === true && item.safeDefaultAvailable === false));
assert.equal(releaseEnvOwnerInputPacket.postCollectionReceipt.redacted, true);
assert.equal(releaseEnvOwnerInputPacket.postCollectionReceipt.passCriteria.releaseEnvReadinessBlockers, 0);
assert.equal(releaseEnvOwnerInputPacket.postCollectionReceipt.passCriteria.releaseEnvReadinessPlaceholders, 0);
assert.equal(releaseEnvOwnerInputPacket.postCollectionReceipt.passCriteria.configOwnerInputReconciliationStatus, "PASS");
assert(releaseEnvOwnerInputPacket.postCollectionReceipt.commands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-config-evidence.mjs"));
assert(releaseEnvOwnerInputPacket.postCollectionReceipt.commands.includes("node scripts/ddd-release-config-owner-input-reconciliation.mjs"));
assert(releaseEnvOwnerInputPacket.postCollectionReceipt.commands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-preflight-gate.sh"));
assert.match(releaseEnvOwnerInputPacketCsv.split("\n")[0], /inputOrder,owner,canonicalKey,aliases,group,requirement,status,valueClass,ownerInputReason/);
assert.match(releaseEnvOwnerInputPacketMarkdown, /^# DDD Release Env Owner Input Packet/m);
assert.match(releaseEnvOwnerInputPacketMarkdown, /## Receipt Gate/);
assert.match(releaseEnvOwnerInputPacketMarkdown, /Concrete values are intentionally omitted/);
assert.doesNotMatch(JSON.stringify(releaseEnvOwnerInputPacket), /__REQUIRED__/);
assert.doesNotMatch(releaseEnvOwnerInputPacketCsv, /DB_PASSWORD=/);
assert.doesNotMatch(releaseEnvOwnerInputPacketMarkdown, /DB_PASSWORD=/);
assert(fs.statSync(releaseEnvOwnerInputPacketDir).isDirectory());
assert.deepEqual(
  fs.readdirSync(releaseEnvOwnerInputPacketDir).filter((file) => file.endsWith(".json") || file.endsWith(".md")).sort(),
  releaseEnvOwnerInputPacket.owners.flatMap((owner) => [`${owner.fileName}.json`, `${owner.fileName}.md`]).sort(),
);
const releaseInfraOwnerInputPacket = releaseEnvOwnerInputPacket.owners.find((owner) => owner.owner === "release-infra");
assert(releaseInfraOwnerInputPacket);
assert.equal(releaseInfraOwnerInputPacket.packetPath, `artifacts/ddd/release/release-env-owner-input-packet/${releaseInfraOwnerInputPacket.fileName}.json`);
assert.equal(releaseInfraOwnerInputPacket.packetMarkdownPath, `artifacts/ddd/release/release-env-owner-input-packet/${releaseInfraOwnerInputPacket.fileName}.md`);
const releaseInfraOwnerInputPacketJson = JSON.parse(fs.readFileSync(path.join(releaseEnvOwnerInputPacketDir, `${releaseInfraOwnerInputPacket.fileName}.json`), "utf8"));
const releaseInfraOwnerInputPacketText = fs.readFileSync(path.join(releaseEnvOwnerInputPacketDir, `${releaseInfraOwnerInputPacket.fileName}.md`), "utf8");
assert.equal(releaseInfraOwnerInputPacketJson.redacted, true);
assert.equal(releaseInfraOwnerInputPacketJson.owner, "release-infra");
assert(releaseInfraOwnerInputPacketJson.items.every((item) => item.owner === "release-infra"));
assert.deepEqual(releaseInfraOwnerInputPacketJson.postCollectionReceipt, releaseEnvOwnerInputPacket.postCollectionReceipt);
assert.match(releaseInfraOwnerInputPacketText, /^# DDD Release Env Owner Input Packet: release-infra/m);
assert.match(releaseInfraOwnerInputPacketText, /`DB_PASSWORD`/);
assert.match(releaseInfraOwnerInputPacketText, /## Receipt Gate/);
assert.match(releaseInfraOwnerInputPacketText, /DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts\/ddd\/release\/release-preflight-gate\.sh/);
assert.doesNotMatch(JSON.stringify(releaseInfraOwnerInputPacketJson), /DB_PASSWORD=/);
assert.doesNotMatch(JSON.stringify(releaseInfraOwnerInputPacketJson), /__REQUIRED__/);
assert.doesNotMatch(releaseInfraOwnerInputPacketText, /DB_PASSWORD=/);
assert.doesNotMatch(releaseInfraOwnerInputPacketText, /__REQUIRED__/);
const releaseInfraRedactedHandoff = releaseEnvOwnerHandoffRedacted.owners.find((owner) => owner.owner === "release-infra");
assert(releaseInfraRedactedHandoff);
const releaseInfraRedactedHandoffText = fs.readFileSync(path.join(artifactRoot, "release", "release-env-owner-handoff-redacted", releaseInfraRedactedHandoff.fileName), "utf8");
assert.match(releaseInfraRedactedHandoffText, /^# DDD Release Env Owner Handoff: release-infra/m);
assert.match(releaseInfraRedactedHandoffText, /DB_PASSWORD.*status=PLACEHOLDER/);
assert.match(releaseInfraRedactedHandoffText, /Next command: `DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts\/ddd\/release\/release-env-readiness-gate\.sh`/);
assert.match(releaseInfraRedactedHandoffText, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
assert.match(releaseInfraRedactedHandoffText, /Concrete values are intentionally omitted/);
assert.doesNotMatch(releaseInfraRedactedHandoffText, /^DB_PASSWORD=/m);
assert.doesNotMatch(releaseInfraRedactedHandoffText, /__REQUIRED__/);
assert.deepEqual(
  fs.readdirSync(path.join(artifactRoot, "release", "release-env-owner-handoff-redacted")).filter((file) => file.endsWith(".md")).sort(),
  releaseEnvOwnerHandoffRedacted.owners.map((owner) => owner.fileName).sort(),
);
assert(releaseEnvOwnerHandoff.owners.every((owner) => owner.postFillCommands.includes(finalGoNoGoEnforceCommand)));
assert.match(releaseEnvOwnerHandoffMarkdown, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
assert.match(releaseEnvOwnerHandoffCsv, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
assert.equal(releaseArtifactIntegrity.redacted, true);
assert.equal(releaseArtifactIntegrity.algorithm, "sha256");
assert.equal(releaseArtifactIntegrity.selfExcluded, true);
assert(releaseArtifactIntegrity.artifactCount > 40);
assert(!releaseArtifactIntegrity.entries.some((entry) => entry.path.endsWith("release-artifact-integrity.json")));
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseFinalGoNoGo" && entry.path.endsWith("release-final-go-no-go.json")));
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseFinalGoNoGoGate" && entry.executable === true && entry.path.endsWith("release-final-go-no-go-gate.sh")));
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseArtifactIntegrityGate" && entry.executable === true && entry.path.endsWith("release-artifact-integrity-gate.sh")));
assert.equal(releaseConfigOwnerInputReconciliation.status, "PASS");
assert.equal(releaseConfigOwnerInputReconciliation.summary.unmappedConfigPlaceholderKeys, 0);
assert.equal(releaseConfigOwnerInputReconciliation.summary.mappedConfigPlaceholderKeys, releaseConfigOwnerInputReconciliation.summary.uniqueConfigPlaceholderKeys);
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseConfigOwnerInputReconciliation" && entry.path.endsWith("release-config-owner-input-reconciliation.json")));
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseEnvOwnerInputPacketOwner01Json" && entry.path.includes("release-env-owner-input-packet/") && entry.path.endsWith(".json")));
assert(releaseArtifactIntegrity.entries.some((entry) => entry.name === "releaseEnvOwnerInputPacketOwner01Markdown" && entry.path.includes("release-env-owner-input-packet/") && entry.path.endsWith(".md")));
assert.equal(
  releaseArtifactIntegrity.entries.filter((entry) => entry.path.includes("release-env-owner-input-packet/")).length,
  releaseEnvOwnerInputPacket.owners.length * 2,
);
assert(releaseArtifactIntegrity.entries.every((entry) => /^[a-f0-9]{64}$/.test(entry.sha256)));
assert.equal(
  releaseArtifactIntegrity.totalBytes,
  releaseArtifactIntegrity.entries.reduce((sum, entry) => sum + entry.bytes, 0),
);
assert.match(releaseArtifactIntegrityMarkdown, /^# DDD Release Artifact Integrity/m);
assert.match(releaseArtifactIntegrityMarkdown, /release-final-go-no-go\.json/);
assert.match(releaseArtifactIntegrityMarkdown, /release-config-owner-input-reconciliation\.json/);
assert.match(releaseArtifactIntegrityGate, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert.match(releaseArtifactIntegrityGate, /Exit code 12/);
assert.match(releaseArtifactIntegrityGate, /DDD_NODE_BIN="\$\{DDD_NODE_BIN:-node\}"/);
assert.match(releaseArtifactIntegrityGate, /"\$\{DDD_NODE_BIN\}" --input-type=module/);
const releaseArtifactIntegrityGateSyntax = spawnSync("bash", ["-n", toBashPath(releaseArtifactIntegrityGatePath)], { encoding: "utf8" });
assert.equal(releaseArtifactIntegrityGateSyntax.status, 0, releaseArtifactIntegrityGateSyntax.stderr);
const releaseArtifactIntegrityGateOk = runBashWithEnv(releaseArtifactIntegrityGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
});
assert.equal(releaseArtifactIntegrityGateOk.status, 0, releaseArtifactIntegrityGateOk.stderr);
assert.match(releaseArtifactIntegrityGateOk.stdout, /ddd-release-artifact-integrity.*ok artifacts=\d+/);
const releaseArtifactIntegrityInvalidPath = path.join(artifactRoot, "release", "release-artifact-integrity-invalid.json");
fs.writeFileSync(releaseArtifactIntegrityInvalidPath, `${JSON.stringify({
  ...releaseArtifactIntegrity,
  entries: releaseArtifactIntegrity.entries.map((entry, index) => index === 0 ? { ...entry, sha256: "0".repeat(64) } : entry),
}, null, 2)}\n`);
const releaseArtifactIntegrityGateInvalid = runBashWithEnv(releaseArtifactIntegrityGatePath, {
  DDD_NODE_BIN: releaseEnvBootstrapNodeBin,
  DDD_RELEASE_ARTIFACT_INTEGRITY_PACKET: toBashPath(releaseArtifactIntegrityInvalidPath),
});
assert.equal(releaseArtifactIntegrityGateInvalid.status, 12);
assert.match(releaseArtifactIntegrityGateInvalid.stderr, /invalid/);
assert.equal(releaseEnvOwnerHandoff.canonicalFillItemCount, releaseEnvCanonicalFill.canonicalFillItemCount);
assert.equal(releaseEnvOwnerHandoff.unresolvedAliasCount, releaseEnvCanonicalFill.unresolvedAliasCount);
assert(releaseEnvOwnerHandoff.ownerCount > 0);
assert.equal(releaseEnvOwnerHandoff.fastPath.commands.at(-1), finalGoNoGoEnforceCommand);
assert(releaseEnvOwnerHandoff.owners.every((owner) => owner.nextCommand === "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh"));
assert(releaseEnvOwnerHandoff.owners.some((owner) => (
  owner.owner === "release-infra"
    && owner.canonicalKeys.includes("DB_PASSWORD")
    && owner.secretCanonicalKeys.includes("DB_PASSWORD")
    && owner.secretCanonicalKeyCount > 0
    && owner.postFillCommands[0].includes("ddd-release-env-owner-templates-merge.mjs")
    && owner.postFillCommands[1].includes("ddd-release-env-canonical-merge.mjs")
    && owner.postFillCommands[2].includes("ddd-release-env-safe-defaults.mjs")
    && owner.postFillCommands[3].includes("ddd-release-provenance-defaults.mjs")
    && owner.postFillCommands[4].includes("ddd-release-env-alias-sync.mjs")
    && owner.postFillCommands[5].includes("ddd-release-env-canonical-lint.mjs")
    && owner.postFillCommands.some((command) => command.includes("ddd-release-env-owner-templates-merge.mjs"))
    && owner.postFillCommands.some((command) => command.includes("ddd-release-env-canonical-merge.mjs"))
    && owner.postFillCommands.some((command) => command.includes("ddd-release-env-safe-defaults.mjs"))
    && owner.postFillCommands.some((command) => command.includes("ddd-release-provenance-defaults.mjs"))
    && owner.postFillCommands.some((command) => command.includes("ddd-release-env-alias-sync.mjs"))
    && owner.postFillCommands.some((command) => command.includes("DDD_RELEASE_ENV_FILE=") && command.endsWith("node scripts/ddd-release-env-file-lint.mjs"))
    && owner.postFillCommands.includes("node scripts/ddd-release-config-evidence.mjs")
    && owner.postFillCommands.includes("DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh")
)));
assert.equal(
  releaseEnvOwnerHandoffCsv.split("\n")[0],
  "owner,queueOrder,queueStatus,canExecute,canonicalFillItemCount,unresolvedAliasCount,secretCanonicalKeyCount,safeToPreFillCanonicalKeyCount,canonicalKeys,secretCanonicalKeys,safeToPreFillCanonicalKeys,requiredCanonicalKeys,readyBatchIds,blockedBatchIds,nextCommand,postFillCommands",
);
assert.match(releaseEnvOwnerHandoffMarkdown, /^# DDD Release Env Owner Handoff/m);
assert.match(releaseEnvOwnerHandoffMarkdown, /## Fast Path/);
assert.match(releaseEnvOwnerHandoffMarkdown, /## release-infra/);
assert.match(releaseEnvOwnerHandoffMarkdown, /Fill canonical keys:/);
assert.match(releaseEnvOwnerHandoffMarkdown, /Secret canonical keys:/);
assert.equal(releaseEnvOwnerTemplates.ownerCount, releaseEnvOwnerHandoff.ownerCount);
assert.equal(releaseEnvOwnerTemplates.canonicalFillItemCount, releaseEnvOwnerHandoff.canonicalFillItemCount);
assert.match(releaseEnvOwnerTemplatesMarkdown, /^# DDD Release Env Owner Templates/m);
assert.match(releaseEnvOwnerTemplatesMarkdown, /release-env-owner-templates\/\d+-release-infra\.env/);
assert.match(releaseEnvOwnerTemplatesMarkdown, /DDD_RELEASE_ENV_FILE=[^`\s]+ node scripts\/ddd-release-env-file-lint\.mjs/);
assert.deepEqual(
  fs.readdirSync(path.join(artifactRoot, "release", "release-env-owner-templates")).filter((file) => file.endsWith(".env")).sort(),
  releaseEnvOwnerTemplates.owners.map((owner) => owner.fileName).sort(),
);
const releaseInfraOwnerTemplate = releaseEnvOwnerTemplates.owners.find((owner) => owner.owner === "release-infra");
assert(releaseInfraOwnerTemplate);
const releaseInfraOwnerTemplateText = fs.readFileSync(path.join(artifactRoot, "release", "release-env-owner-templates", releaseInfraOwnerTemplate.fileName), "utf8");
assert.match(releaseInfraOwnerTemplateText, /^# Owner: release-infra$/m);
assert.match(releaseInfraOwnerTemplateText, /^DB_PASSWORD=__REQUIRED__$/m);
assert.match(releaseInfraOwnerTemplateText, /# Value class: secret; secret=true; safeToPreFill=false/);
assert.doesNotMatch(releaseInfraOwnerTemplateText, /^LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=/m);
assert(releaseInfraOwnerTemplate.postFillCommands[0].includes("ddd-release-env-owner-templates-merge.mjs"));
assert(releaseInfraOwnerTemplate.postFillCommands.some((command) => command.includes("DDD_RELEASE_ENV_FILE=") && command.endsWith("node scripts/ddd-release-env-file-lint.mjs")));
assert(releaseInfraOwnerTemplate.postFillCommands.includes("DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh"));
assert.equal(ownerRollupCsv.split("\n")[0], "owner,pendingItems,source,id,reason,envKeys,action");
assert(ownerRollupCsv.includes("release-performance,8,manifest,manifest-missing-performance-authenticated-runtime-baseline-json"));
assert.match(ownerRollupCsv, /database,\d+,explain,message-visible-list\.json/);
assert.match(ownerRollupCsv, /release-infra,\d+,docker,docker-daemon/);
assert.equal(sourceRollupCsv.split("\n")[0], "source,pendingItems,owner,id,reason,envKeys,action");
assert(sourceRollupCsv.includes("manifest,1,release-performance,manifest-missing-performance-authenticated-runtime-baseline-json"));
assert(sourceRollupCsv.includes("explain,8,database,message-visible-list.json"));
assert(sourceRollupCsv.includes("docker,1,release-infra,docker-daemon"));
assert.equal(releasePriorityCsv.split("\n")[0], "priority,source,owner,id,check,reason,detail,structured,envKeys,action");
assert(releasePriorityCsv.includes("P0,release-env-lint,release-infra,release-env-lint-placeholders"));
assert(releasePriorityCsv.includes("P0,release-config,ai-owner"));
assert(releasePriorityCsv.includes("node scripts/ddd-release-config-evidence.mjs"));
assert(releasePriorityCsv.includes("P0,docker,release-infra,docker-daemon"));
assert(releasePriorityCsv.includes("P2,explain,database,message-visible-list.json"));
assert.equal(releaseBatchesCsv.split("\n")[0], "order,priority,source,owner,id,pendingItems,canRunImmediately,dependsOn,commands,envKeys,envCheckGroups,expectedArtifacts,exitCriteria,itemIds");
assert(releaseBatchesCsv.includes("p0-runtime-readiness-release-infra"));
assert(releaseBatchesCsv.includes("LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL"));
assert.match(releaseBatchesCsv, /9,P0,authenticated-performance,release-performance,p0-authenticated-performance-release-performance,7,true,/);
assert.match(releaseBatchesCsv, /P1,ai-runtime,ai,p1-ai-runtime-ai,4,false,[^\n]*p0-release-env-lint-release-infra[^\n]*p0-docker-release-infra/);
assert.equal(releaseExecutionQueueCsv.split("\n")[0], "queueStatus,priority,source,owner,id,pendingItems,dependsOn,commands,envKeys,envCheckGroups,expectedArtifacts,exitCriteria");
assert(releaseExecutionQueueCsv.includes("ready,P0,release-env-lint,release-infra,p0-release-env-lint-release-infra"));
assert(releaseExecutionQueueCsv.includes("node scripts/ddd-release-env-file-lint.mjs"));
assert(releaseExecutionQueueCsv.includes("artifacts/ddd/release/release-env-lint.json"));
assert(releaseExecutionQueueCsv.includes("LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL"));
assert(releaseExecutionQueueCsv.includes("blocked,P1,ai-runtime,ai,p1-ai-runtime-ai"));
assert.match(releaseExecutionQueueCsv, /blocked,P1,ai-runtime,ai,p1-ai-runtime-ai,[^\n]*p0-release-env-lint-release-infra[^\n]*p0-docker-release-infra/);
assert.match(ownerRollupMarkdown, /^# DDD Owner Action Rollup/m);
assert.match(ownerRollupMarkdown, /Status: NOT_READY/);
assert.match(ownerRollupMarkdown, /## release-performance/);
assert.match(ownerRollupMarkdown, /\[manifest\] manifest-missing-performance-authenticated-runtime-baseline-json/);
assert.match(ownerRollupMarkdown, /## database/);
assert.match(ownerRollupMarkdown, /\[explain\] message-visible-list\.json/);
assert.match(ownerRollupMarkdown, /## release-infra/);
assert.match(ownerRollupMarkdown, /\[docker\] docker-daemon/);
assert.match(sourceRollupMarkdown, /^# DDD Source Action Rollup/m);
assert.match(sourceRollupMarkdown, /Status: NOT_READY/);
assert.match(sourceRollupMarkdown, /## manifest/);
assert.match(sourceRollupMarkdown, /\[release-performance\] manifest-missing-performance-authenticated-runtime-baseline-json/);
assert.match(sourceRollupMarkdown, /## explain/);
assert.match(sourceRollupMarkdown, /\[database\] message-visible-list\.json/);
assert.match(sourceRollupMarkdown, /## docker/);
assert.match(sourceRollupMarkdown, /\[release-infra\] docker-daemon/);
assert.match(releaseBlockerMapMarkdown, /^# DDD Release Blocker Map/m);
assert.match(releaseBlockerMapMarkdown, /Category count: \d+/);
assert.match(releaseBlockerMapMarkdown, /Owner count: \d+/);
assert.match(releaseBlockerMapMarkdown, /## Owners/);
assert.match(releaseBlockerMapMarkdown, /### database/);
assert.match(releaseBlockerMapMarkdown, /runtime-readiness-freshness: checkedAt is 28\.1h old/);
assert.match(releaseBlockerMapMarkdown, /## Categories/);
assert.match(releaseBlockerMapMarkdown, /### rollback-context-drills/);
assert.match(releaseBlockerMapMarkdown, /### migration/);
assert.match(releaseBlockerMapMarkdown, /- Expected artifacts:[\s\S]*`tmp\/ddd-explain\/\*\.json`/);
assert.equal(releaseFastTrack.recommendation, "NO_GO_STRICT");
assert.equal(releaseFastTrack.noAutoWaivers, true);
assert.equal(releaseFastTrack.summary.totalPendingItems, releasePriority.totalPendingItems);
assert(releaseFastTrack.summary.nonWaivableItems > 0);
assert.equal(releaseFastTrack.summary.cutoverChecklistItems, releaseFastTrack.cutoverChecklist.length);
assert.equal(releaseFastTrack.summary.blockedCutoverItems, releaseFastTrack.cutoverChecklist.filter((item) => item.status !== "PASS").length);
assert(releaseFastTrack.fastestSafePath.some((step) => step.includes("DDD_RELEASE_CHECK_ENV_ONLY=1")));
assert(releaseFastTrack.cutoverChecklist.some((item) => (
  item.id === "strict-release-gate"
    && item.status === "BLOCKED"
    && item.pendingItems === summary.gate.blockers
)));
assert(releaseFastTrack.cutoverChecklist.some((item) => (
  item.id === "rollback-safety"
    && item.required === true
    && item.status === "BLOCKED"
)));
assert(releaseFastTrack.lanes.some((lane) => (
  lane.lane === "environment"
    && lane.safetyClass === "non-waivable"
    && lane.envCheckGroups.some((group) => group === "LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL")
)));
assert(releaseFastTrack.lanes.some((lane) => (
  lane.lane === "rollback-safety"
    && lane.safetyClass === "non-waivable"
)));
assert.match(releaseFastTrackMarkdown, /^# DDD Fast Track Release Decision/m);
assert.match(releaseFastTrackMarkdown, /Recommendation: NO_GO_STRICT/);
assert.match(releaseFastTrackMarkdown, /No auto waivers: true/);
assert.match(releaseFastTrackMarkdown, /## Cutover Checklist/);
assert.match(releaseFastTrackMarkdown, /\[BLOCKED\] strict-release-gate/);
assert.equal(releaseFastTrack.safetySignals.releaseEnvFile.ready, false);
assert.equal(releaseFastTrack.safetySignals.releaseEnvFile.securityChecked, true);
assert.equal(releaseFastTrack.safetySignals.releaseEnvFile.permissionSafe, true);
assert.match(releaseFastTrackMarkdown, /## Safety Signals/);
assert.match(releaseFastTrackMarkdown, /releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true/);
assert.match(releaseFastTrackMarkdown, /securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false/);
assert.match(releaseFastTrackMarkdown, /### rollback-safety/);
assert.equal(releaseCutoverChecklistCsv.split("\n")[0], "recommendation,noAutoWaivers,id,title,required,status,pendingItems,lanes,readyBatchIds,blockedBatchIds,batchIds");
assert(releaseCutoverChecklistCsv.includes("NO_GO_STRICT,true,strict-release-gate"));
const strictCutoverCsvLine = releaseCutoverChecklistCsv.split("\n").find((line) => line.includes(",strict-release-gate,"));
assert(strictCutoverCsvLine.includes(`,BLOCKED,${summary.gate.blockers},`));
assert(releaseCutoverChecklistCsv.includes("rollback-safety"));
assert.equal(releaseCutoverOwnerMatrix.recommendation, "NO_GO_STRICT");
assert.equal(releaseCutoverOwnerMatrix.noAutoWaivers, true);
assert.equal(releaseCutoverOwnerMatrix.releaseEnvFileCutoverSafe, false);
assert.deepEqual(releaseCutoverOwnerMatrix.safetySignals.releaseEnvFile, releaseFastTrack.safetySignals.releaseEnvFile);
assert(releaseCutoverOwnerMatrix.summary.ownerCount >= 3);
assert(releaseCutoverOwnerMatrix.owners.some((owner) => owner.owner === "release-owner" && owner.items.some((item) => item.checklistId === "strict-release-gate")));
assert(releaseCutoverOwnerMatrix.owners.some((owner) => owner.owner === "release-infra"));
assert(releaseCutoverOwnerMatrix.owners.some((owner) => owner.owner === "database"));
assert(releaseCutoverOwnerMatrix.owners.some((owner) => owner.owner === "frontend"));
assert(releaseCutoverOwnerMatrix.owners.some((owner) => owner.items.some((item) => item.checklistId === "rollback-safety")));
assert.equal(
  releaseCutoverOwnerMatrixCsv.split("\n")[0],
  "recommendation,noAutoWaivers,owner,blockedItems,totalItems,checklistId,status,required,pendingItems,lanes,batchIds,readyBatchIds,blockedBatchIds,commands,expectedArtifacts,envCheckGroups,exitCriteria",
);
assert(releaseCutoverOwnerMatrixCsv.includes("NO_GO_STRICT,true,release-owner"));
assert(releaseCutoverOwnerMatrixCsv.includes("strict-release-gate"));
assert.match(releaseCutoverOwnerMatrixMarkdown, /^# DDD Cutover Owner Matrix/m);
assert.match(releaseCutoverOwnerMatrixMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseCutoverOwnerMatrixMarkdown, /## release-infra/);
assert.match(releaseCutoverOwnerMatrixMarkdown, /strict-release-gate/);
assert.equal(releaseSprintBoard.recommendation, "NO_GO_STRICT");
assert.equal(releaseSprintBoard.noAutoWaivers, true);
assert.equal(releaseSprintBoard.summary.batchCount, releaseBatches.batchCount);
assert.equal(releaseSprintBoard.summary.readyBatchCount, releaseExecutionQueue.readyBatchCount);
assert.equal(releaseSprintBoard.summary.blockedBatchCount, releaseExecutionQueue.blockedBatchCount);
assert.deepEqual([...releaseSprintBoard.nextWave.batchIds].sort(), [...releaseExecutionQueue.nextBatchIds].sort());
assert(releaseSprintBoard.priorities.some((priority) => priority.priority === "P0" && priority.readyBatchIds.length > 0));
assert(releaseSprintBoard.owners.some((owner) => owner.owner === "release-infra" && owner.nextReadyBatchIds.length > 0));
assert(releaseSprintBoard.batchCards.some((card) => card.id === "p0-release-env-lint-release-infra" && card.status === "READY"));
assert.equal(
  releaseSprintBoardCsv.split("\n")[0],
  "recommendation,noAutoWaivers,priority,status,owner,source,batchId,pendingItems,dependsOn,unmetDependencies,lanes,cutoverChecklistIds,commands,expectedArtifacts,envCheckGroups,exitCriteria,itemIds",
);
assert(releaseSprintBoardCsv.includes("NO_GO_STRICT,true,P0,READY,release-infra,release-env-lint,p0-release-env-lint-release-infra"));
assert.match(releaseSprintBoardMarkdown, /^# DDD Release Sprint Board/m);
assert.match(releaseSprintBoardMarkdown, /## Next Wave/);
assert.match(releaseSprintBoardMarkdown, /### P0/);
assert.equal(releaseCommandCatalog.recommendation, "NO_GO_STRICT");
assert.equal(releaseCommandCatalog.noAutoWaivers, true);
assert.equal(releaseCommandCatalog.finalDecision.finalRecommendation, releaseFinalGoNoGo.finalRecommendation);
assert.equal(releaseCommandCatalog.finalDecision.cutoverAllowed, false);
assert.equal(releaseCommandCatalog.finalDecision.releaseEnvFileCutoverSafe, releaseFinalGoNoGo.releaseEnvFileCutoverSafe);
assert.equal(releaseCommandCatalog.finalDecision.gateBlockers, releaseFinalGoNoGo.gate.blockers);
assert.equal(releaseCommandCatalog.finalDecision.blockedCutoverItems, releaseFinalGoNoGo.summary.blockedCutoverItems);
assert.equal(releaseCommandCatalog.finalDecision.stopReasonCoverage, "catalog-snapshot");
assert.equal(releaseCommandCatalog.finalDecision.cutoverAuthority, "final-go-no-go-gate");
assert.equal(releaseCommandCatalog.finalDecision.requiresFinalGate, true);
assert.equal(releaseCommandCatalog.releaseEnvFileCutoverSafe, false);
assert.deepEqual(releaseCommandCatalog.safetySignals.releaseEnvFile, releaseFastTrack.safetySignals.releaseEnvFile);
assert.equal(releaseCommandCatalog.summary.batchCommandCount, releaseExecutionQueue.readyBatchCount);
assert.equal(releaseCommandCatalog.summary.ownerCommandCount, releaseSprintBoard.nextWave.owners.length);
assert.equal(releaseCommandCatalog.summary.nextPriority, releaseExecutionQueue.nextPriority);
assert(releaseCommandCatalog.ownerCommands.some((owner) => (
  owner.owner === "release-infra"
    && owner.commands.list.includes("DDD_RELEASE_OWNER=release-infra")
    && owner.commands.envCheck.includes("DDD_RELEASE_CHECK_ENV_ONLY=1")
)));
assert(releaseCommandCatalog.batchCommands.some((batch) => (
  batch.batchId === "p0-docker-release-infra"
    && batch.commands.dryRun.includes("DDD_RELEASE_BATCH=p0-docker-release-infra")
    && batch.commands.execute.endsWith("bash artifacts/ddd/release/release-execution-commands.sh")
)));
assert.equal(
  releaseCommandCatalogCsv.split("\n")[0],
  "scope,finalRecommendation,cutoverAllowed,stopReasonCount,owner,priority,batchId,readyBatchIds,expectedArtifacts,listCommand,envCheckCommand,dryRunCommand,executeCommand",
);
assert(releaseCommandCatalogCsv.includes("owner,NO_GO_STRICT,false,"));
assert.match(releaseCommandCatalogMarkdown, /^# DDD Release Command Catalog/m);
assert.match(releaseCommandCatalogMarkdown, /## Final Cutover Decision/);
assert.match(releaseCommandCatalogMarkdown, /cutoverAllowed: false/);
assert.match(releaseCommandCatalogMarkdown, /cutoverAuthority: final-go-no-go-gate/);
assert.match(releaseCommandCatalogMarkdown, /## Safety Signals/);
assert.match(releaseCommandCatalogMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseCommandCatalogMarkdown, /releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true/);
assert.match(releaseCommandCatalogMarkdown, /securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false/);
assert.match(releaseCommandCatalogMarkdown, /## Owners/);
assert.match(releaseCommandCatalogMarkdown, /DDD_RELEASE_OWNER=release-infra/);
assert.equal(releaseOwnerHandoff.recommendation, "NO_GO_STRICT");
assert.equal(releaseOwnerHandoff.noAutoWaivers, true);
assert.deepEqual(releaseOwnerHandoff.finalDecision, releaseCommandCatalog.finalDecision);
assert.equal(releaseOwnerHandoff.releaseEnvFileCutoverSafe, false);
assert.deepEqual(releaseOwnerHandoff.safetySignals.releaseEnvFile, releaseCommandCatalog.safetySignals.releaseEnvFile);
assert.equal(releaseOwnerHandoff.summary.readyBatchCount, releaseExecutionQueue.readyBatchCount);
assert.deepEqual(
  [...new Set(releaseOwnerHandoff.owners.flatMap((owner) => owner.readyBatchIds))].sort(),
  [...releaseExecutionQueue.nextBatchIds].sort(),
);
assert(releaseOwnerHandoff.owners.some((owner) => (
  owner.owner === "release-infra"
    && owner.status === "READY"
    && owner.commandSet?.envCheck?.includes("DDD_RELEASE_OWNER=release-infra")
    && owner.readyBatchIds.includes("p0-release-env-lint-release-infra")
)));
assert(releaseOwnerHandoff.owners.some((owner) => (
  owner.owner === "database"
    && owner.readyBatchIds.includes("p0-migration-database")
)));
assert(releaseOwnerHandoff.owners.some((owner) => owner.templateEnvKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY")));
assert.equal(
  releaseOwnerHandoffCsv.split("\n")[0],
  "finalRecommendation,cutoverAllowed,stopReasonCount,owner,status,pendingItems,priorities,readyBatchIds,blockedBatchIds,blockedByBatchIds,templateEnvKeys,aliasMappings,listCommand,envCheckCommand,dryRunCommand,executeCommand,expectedArtifacts,exitCriteria",
);
assert(releaseOwnerHandoffCsv.includes("NO_GO_STRICT,false,"));
assert.match(releaseOwnerHandoffMarkdown, /^# DDD Release Owner Handoff/m);
assert.match(releaseOwnerHandoffMarkdown, /## Final Cutover Decision/);
assert.match(releaseOwnerHandoffMarkdown, /cutoverAllowed: false/);
assert.match(releaseOwnerHandoffMarkdown, /cutoverAuthority: final-go-no-go-gate/);
assert.match(releaseOwnerHandoffMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseOwnerHandoffMarkdown, /## release-infra/);
assert.equal(releaseOwnerReceipts.recommendation, "NO_GO_STRICT");
assert.equal(releaseOwnerReceipts.noAutoWaivers, true);
assert.equal(releaseOwnerReceipts.summary.ownerCount, releaseOwnerHandoff.summary.ownerCount);
assert.deepEqual(
  releaseOwnerReceipts.owners.map((owner) => owner.owner).sort(),
  releaseOwnerHandoff.owners.map((owner) => owner.owner).sort(),
);
assert.equal(
  releaseOwnerReceiptsCsv.split("\n")[0],
  "owner,status,receiptStatus,readyBatchIds,blockedBatchIds,expectedArtifactCount,presentArtifactCount,missingArtifactCount,pendingActionCount,collapsedActionCount,presentArtifacts,missingArtifacts,pendingActionIds,nextCheck",
);
assert(releaseOwnerReceipts.owners.some((owner) => (
  owner.owner === "release-infra"
    && owner.readyBatchIds.includes("p0-release-env-lint-release-infra")
    && owner.receiptStatus === "CONTENT_BLOCKED"
    && owner.pendingActionCount > 0
)));
assert.equal(
  releaseOwnerReceipts.summary.expectedArtifactCount,
  releaseOwnerReceipts.owners.reduce((sum, owner) => sum + owner.expectedArtifactCount, 0),
);
assert.equal(
  releaseOwnerReceipts.summary.missingArtifactCount,
  releaseOwnerReceipts.owners.reduce((sum, owner) => sum + owner.missingArtifactCount, 0),
);
assert.equal(
  releaseOwnerReceipts.summary.pendingActionCount,
  releaseOwnerReceipts.owners.reduce((sum, owner) => sum + owner.pendingActionCount, 0),
);
assert.match(releaseOwnerReceiptsMarkdown, /^# DDD Release Owner Receipts/m);
assert.match(releaseOwnerReceiptsMarkdown, /## release-infra/);
assert.equal(releaseNextActionQueue.recommendation, "NO_GO_STRICT");
assert.equal(releaseNextActionQueue.noAutoWaivers, true);
assert.deepEqual(releaseNextActionQueue.finalDecision, releaseOwnerHandoff.finalDecision);
assert.equal(releaseNextActionQueue.releaseEnvFileCutoverSafe, false);
assert.deepEqual(releaseNextActionQueue.safetySignals.releaseEnvFile, releaseOwnerHandoff.safetySignals.releaseEnvFile);
assert.equal(releaseNextActionQueue.summary.itemCount, releaseOwnerReceipts.summary.ownerCount);
assert.equal(releaseNextActionQueue.summary.runNowCount, releaseOwnerReceipts.summary.readyOwnerCount);
assert.deepEqual(releaseNextActionQueue.ownerInputReceipt, releaseFinalOwnerQueue.ownerInputReceipt);
assert.equal(releaseNextActionQueue.summary.ownerInputReceiptStatus, releaseOwnerInputReceipt.status);
assert.equal(releaseNextActionQueue.summary.ownerInputReceiptCutoverReady, releaseOwnerInputReceipt.cutoverReady);
assert.equal(releaseNextActionQueue.summary.ownerInputReceiptRequiredOwnerInputs, releaseOwnerInputReceipt.summary.requiredOwnerInputs);
assert.equal(releaseNextActionQueue.summary.ownerInputReceiptPendingOwnerCount, releaseOwnerInputReceipt.summary.pendingOwnerCount);
assert.equal(releaseNextActionQueue.summary.ownerInputReceiptMissingCriteriaCount, releaseOwnerInputReceipt.missingCriteria.length);
assert.deepEqual(
  releaseNextActionQueue.items.map((item) => item.owner).sort(),
  releaseOwnerReceipts.owners.map((owner) => owner.owner).sort(),
);
assert.equal(
  releaseNextActionQueueCsv.split("\n")[0],
  "order,finalRecommendation,cutoverAllowed,stopReasonCount,owner,queueStatus,receiptStatus,strictGateBlockerCount,readyBatchIds,blockedBatchIds,missingArtifacts,pendingActionCount,collapsedActionCount,nextAction,reason,commandHint,executableCommands,envKeys",
);
assert(releaseNextActionQueueCsv.includes("NO_GO_STRICT,false,"));
assert(releaseNextActionQueue.items[0].queueStatus === "RUN_NOW");
assert(releaseNextActionQueue.items[0].strictGateBlockerCount > 0);
assert.match(releaseNextActionQueue.items[0].reason, /^strictGate=/);
assert(releaseNextActionQueue.items.filter((item) => item.queueStatus === "RUN_NOW").every((item) => item.executableCommands.length > 0));
const databaseNextAction = releaseNextActionQueue.items.find((item) => item.owner === "database");
assert(databaseNextAction);
assert(databaseNextAction.strictGateBlockerCount > 0);
assert.match(databaseNextAction.reason, /^strictGate=/);
assert.match(databaseNextAction.nextAction, /Flyway drills|migration evidence/i);
assert.equal(databaseNextAction.executableCommands[0], "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert(databaseNextAction.executableCommands.includes("node scripts/ddd-migration-evidence.mjs"));
assert(databaseNextAction.envKeys.includes("DDD_MIGRATION_FRESH_DB_EVIDENCE"));
assert(databaseNextAction.envKeys.includes("DDD_MIGRATION_UPGRADE_DB_EVIDENCE"));
assert(databaseNextAction.envKeys.includes("DDD_MIGRATION_HANDOFF_FILE"));
const databaseOwnerReceipt = releaseOwnerReceipts.owners.find((owner) => owner.owner === "database");
assert(databaseOwnerReceipt?.pendingActions?.some((action) => (
  action.source === "explain"
    && String(action.action || "").includes("ddd-collect-explain.mjs")
)));
assert.match(releaseNextActionQueueMarkdown, /Strict gate blockers: [1-9]/);
assert.match(releaseNextActionQueueMarkdown, /^# DDD Release Next Action Queue/m);
assert.match(releaseNextActionQueueMarkdown, /## Final Cutover Decision/);
assert.match(releaseNextActionQueueMarkdown, /Owner input receipt status: PENDING_OWNER_INPUT/);
assert.match(releaseNextActionQueueMarkdown, /## Owner Input Receipt/);
assert.match(releaseNextActionQueueMarkdown, /Required owner inputs: \d+/);
assert.match(releaseNextActionQueueMarkdown, /cutoverAllowed: false/);
assert.match(releaseNextActionQueueMarkdown, /cutoverAuthority: final-go-no-go-gate/);
assert.match(releaseNextActionQueueMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseNextActionQueueMarkdown, /## 1\. /);
assert.match(releaseNextActionCommands, /^#!\/usr\/bin\/env bash/);
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_EXECUTE"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_ORDER"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_OWNER"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_LIST"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_DETAIL"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_CHECK_ENV"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_REPORT=\"${DDD_RELEASE_NEXT_ACTION_REPORT:-artifacts/ddd/release/release-next-action-run-report.json}\""));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_REPORT_TMP=\"${DDD_RELEASE_NEXT_ACTION_REPORT}.jsonl.$$\""));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_REPORT_FINALIZED=0"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES=0"));
assert(releaseNextActionCommands.includes("SCRIPT_DIR=$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)"));
assert(releaseNextActionCommands.includes("LUMIRA_REPO_ROOT=$(cd \"${SCRIPT_DIR}/../../..\" && pwd)"));
assert(!releaseNextActionCommands.includes(repoRoot), "release next-action commands must not embed the local repo path");
assert(releaseNextActionCommands.includes("[ddd-release-next-action][env-missing]"));
assert(releaseNextActionCommands.includes("[ddd-release-next-action][env-ok]"));
assert(releaseNextActionCommands.includes("append_next_action_report_entry()"));
assert(releaseNextActionCommands.includes("finalize_next_action_report()"));
assert(releaseNextActionCommands.includes("ddd-release-next-action-run-report-contract.mjs"));
assert(releaseNextActionCommands.includes("trap 'status=$?; finalize_next_action_report \"${status}\"; exit \"${status}\"' EXIT"));
assert(releaseNextActionCommands.includes("[ddd-release-next-action][command-failed] status=${status} command=${command}"));
assert(releaseNextActionCommands.includes("continuing because DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=${DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR}"));
assert(releaseNextActionCommands.includes("[ddd-release-next-action][completed-with-failures] commandFailures=${DDD_RELEASE_NEXT_ACTION_COMMAND_FAILURES}"));
assert(releaseNextActionCommands.includes("[ddd-release-next-action][report] ${DDD_RELEASE_NEXT_ACTION_REPORT}"));
assert(releaseNextActionCommands.includes("RUN_NOW release next actions:"));
assert(releaseNextActionCommands.includes("commands:"));
assert(releaseNextActionCommands.includes("DDD_RELEASE_ENV_FILE is required when executing or checking release next-action env."));
assert(releaseNextActionCommands.includes("Template env files are worksheets, not release evidence"));
assert(releaseNextActionCommands.includes("release-env-missing.template.env"));
assert(releaseNextActionCommands.includes("release-closure-wave-env.template.env"));
assert(releaseNextActionCommands.includes("release-final-owner-queue-env.template.env"));
assert(releaseNextActionCommands.includes("Release env file permissions are too broad"));
assert(releaseNextActionCommands.includes("safe_load_release_env_file"));
assert.doesNotMatch(releaseNextActionCommands, /^\s*source "\$\{DDD_RELEASE_ENV_FILE\}"/m);
assert(releaseNextActionCommands.includes("[ddd-release-next-action][dry-run]"));
assert((releaseBatches.batches || []).some((batch) => (
  batch.source === "explain"
    && (batch.commands || []).includes("node scripts/ddd-collect-explain.mjs")
)));
assert(releaseNextActionCommands.includes("node scripts/ddd-release-readiness-summary.mjs"));
assert(releaseNextActionCommands.includes("run_next_action_command '0' 'release-next-action' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'"));
const releaseNextActionCommandsSyntax = spawnSync("bash", ["-n", path.join(artifactRoot, "release/release-next-action-commands.sh")], { encoding: "utf8" });
assert.equal(releaseNextActionCommandsSyntax.status, 0, releaseNextActionCommandsSyntax.stderr);
const nextActionBroadModeEnvFile = path.join(artifactRoot, "release", "next-action-broad-mode.env");
fs.writeFileSync(nextActionBroadModeEnvFile, "DDD_RELEASE_EVIDENCE_STRICT=true\n");
fs.chmodSync(nextActionBroadModeEnvFile, 0o644);
const releaseNextActionCommandsBroadModeEnv = spawnSync("bash", [path.join(artifactRoot, "release/release-next-action-commands.sh")], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_FILE: nextActionBroadModeEnvFile,
    DDD_RELEASE_NEXT_ACTION_CHECK_ENV: "1",
    DDD_RELEASE_NEXT_ACTION_ORDER: "1",
  },
});
assert.notEqual(releaseNextActionCommandsBroadModeEnv.status, 0);
assert.match(releaseNextActionCommandsBroadModeEnv.stderr, /Release env file permissions are too broad:/);
assert.equal(releaseBlockerClosurePlan.recommendation, "NO_GO_STRICT");
assert.equal(releaseBlockerClosurePlan.noAutoWaivers, true);
assert.equal(releaseBlockerClosurePlan.releaseEnvFileCutoverSafe, false);
assert.deepEqual(releaseBlockerClosurePlan.safetySignals.releaseEnvFile, releaseNextActionQueue.safetySignals.releaseEnvFile);
assert.deepEqual(releaseBlockerClosurePlan.ownerInputReceipt, releaseNextActionQueue.ownerInputReceipt);
assert.equal(releaseBlockerClosurePlan.summary.itemCount, releasePriority.items.length);
assert.equal(releaseBlockerClosurePlan.summary.ownerInputReceiptStatus, releaseOwnerInputReceipt.status);
assert.equal(releaseBlockerClosurePlan.summary.ownerInputReceiptCutoverReady, releaseOwnerInputReceipt.cutoverReady);
assert.equal(releaseBlockerClosurePlan.summary.ownerInputReceiptRequiredOwnerInputs, releaseOwnerInputReceipt.summary.requiredOwnerInputs);
assert.equal(releaseBlockerClosurePlan.summary.ownerInputReceiptPendingOwnerCount, releaseOwnerInputReceipt.summary.pendingOwnerCount);
assert.equal(releaseBlockerClosurePlan.summary.ownerInputReceiptMissingCriteriaCount, releaseOwnerInputReceipt.missingCriteria.length);
assert.deepEqual(
  releaseBlockerClosurePlan.items.map((item) => item.id).sort(),
  releasePriority.items.map((item) => item.id).sort(),
);
assert(releaseBlockerClosurePlan.summary.runNowWithRealEnvCount > 0);
assert(releaseBlockerClosurePlan.summary.waitingForDependenciesCount > 0);
assert(releaseBlockerClosurePlan.summary.runnableWaveCount > 0);
assert.equal(releaseBlockerClosurePlan.summary.runnableWaveCount, releaseBlockerClosurePlan.waves.length);
assert.deepEqual(
  releaseBlockerClosurePlan.waves.flatMap((wave) => wave.itemIds).sort(),
  releaseBlockerClosurePlan.items.filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES").map((item) => item.id).sort(),
);
assert(releaseBlockerClosurePlan.waves.some((wave) => (
  wave.batchId === "p0-docker-release-infra"
    && wave.commands.some((command) => command.includes("ddd-docker-build-evidence.mjs"))
)));
assert(releaseBlockerClosurePlan.items.some((item) => (
  item.id === "docker-daemon"
    && item.closureKind === "RUN_NOW_WITH_REAL_ENV"
    && item.commands.some((command) => command.includes("ddd-docker-build-evidence.mjs"))
)));
assert(releaseBlockerClosurePlan.items.some((item) => (
  item.id === "message-visible-list.json"
    && item.closureKind === "WAIT_FOR_DEPENDENCIES"
    && item.expectedArtifacts.some((artifactPath) => artifactPath.includes("tmp/ddd-explain/*.json"))
    && item.expectedArtifacts.includes("artifacts/ddd/release/explain-gate-report.json")
)));
assert.equal(
  releaseBlockerClosurePlanCsv.split("\n")[0],
  "order,closureKind,priority,source,owner,id,batchId,batchReady,dependencies,envKeys,commands,expectedArtifacts,reason,action",
);
assert.match(releaseBlockerClosurePlanMarkdown, /^# DDD Release Blocker Closure Plan/m);
assert.match(releaseBlockerClosurePlanMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseBlockerClosurePlanMarkdown, /Owner input receipt status: PENDING_OWNER_INPUT/);
assert.match(releaseBlockerClosurePlanMarkdown, /## Owner Input Receipt/);
assert.match(releaseBlockerClosurePlanMarkdown, /RUN_NOW_WITH_REAL_ENV:/);
assert.match(releaseBlockerClosureCommands, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_EXECUTE"));
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_DETAIL"));
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_CHECK_ENV"));
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_KIND"));
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR"));
assert(releaseBlockerClosureCommands.includes("DDD_RELEASE_CLOSURE_COMMAND_FAILURES=0"));
assert(releaseBlockerClosureCommands.includes("Runnable release blocker closure waves:"));
assert(releaseBlockerClosureCommands.includes("Runnable release blocker closure items:"));
assert(releaseBlockerClosureCommands.includes("[ddd-release-closure][env-missing]"));
assert(releaseBlockerClosureCommands.includes("[ddd-release-closure][dry-run]"));
assert(releaseBlockerClosureCommands.includes("[ddd-release-closure][command-failed] status=${status} command=${command}"));
assert(releaseBlockerClosureCommands.includes("continuing because DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=${DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR}"));
assert(releaseBlockerClosureCommands.includes("[ddd-release-closure][completed-with-failures] commandFailures=${DDD_RELEASE_CLOSURE_COMMAND_FAILURES}"));
assert(releaseBlockerClosureCommands.includes("Template env files are worksheets, not release evidence"));
assert(releaseBlockerClosureCommands.includes("release-env-missing.template.env"));
assert(releaseBlockerClosureCommands.includes("release-closure-wave-env.template.env"));
assert(releaseBlockerClosureCommands.includes("release-final-owner-queue-env.template.env"));
assert(releaseBlockerClosureCommands.includes("Release env file permissions are too broad"));
assert(releaseBlockerClosureCommands.includes("DDD_DOCKER_BUILD_STRICT=true node scripts/ddd-docker-build-evidence.mjs"));
assert(releaseBlockerClosureCommands.includes("node scripts/ddd-release-readiness-summary.mjs"));
const releaseBlockerClosureCommandsSyntax = spawnSync("bash", ["-n", releaseBlockerClosureCommandsPath], { encoding: "utf8" });
assert.equal(releaseBlockerClosureCommandsSyntax.status, 0, releaseBlockerClosureCommandsSyntax.stderr);
const releaseBlockerClosureCommandsList = spawnSync("bash", [releaseBlockerClosureCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
  },
});
assert.equal(releaseBlockerClosureCommandsList.status, 0, releaseBlockerClosureCommandsList.stderr);
assert.match(releaseBlockerClosureCommandsList.stdout, /Runnable release blocker closure waves:/);
assert.match(releaseBlockerClosureCommandsList.stdout, /Runnable release blocker closure items:/);
assert.match(releaseBlockerClosureCommandsList.stdout, /owner=release-infra batch=p0-docker-release-infra items=\d+/);
assert.match(releaseBlockerClosureCommandsList.stdout, /RUN_NOW_WITH_REAL_ENV owner=release-infra id=docker-daemon/);
const releaseBlockerClosureCommandsDetail = spawnSync("bash", [releaseBlockerClosureCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_CLOSURE_DETAIL: "1",
    DDD_RELEASE_CLOSURE_ORDER: "3",
  },
});
assert.equal(releaseBlockerClosureCommandsDetail.status, 0, releaseBlockerClosureCommandsDetail.stderr);
assert.match(releaseBlockerClosureCommandsDetail.stdout, /order=3/);
assert.match(releaseBlockerClosureCommandsDetail.stdout, /closureKind=RUN_NOW_WITH_REAL_ENV/);
assert.match(releaseBlockerClosureCommandsDetail.stdout, /commands:/);
const releaseBlockerClosureCommandsMissingEnv = spawnSync("bash", [releaseBlockerClosureCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_CLOSURE_CHECK_ENV: "1",
    DDD_RELEASE_CLOSURE_ORDER: "3",
  },
});
assert.notEqual(releaseBlockerClosureCommandsMissingEnv.status, 0);
assert.match(releaseBlockerClosureCommandsMissingEnv.stderr, /DDD_RELEASE_ENV_FILE is required when executing or checking closure env/);
const closureBroadModeEnvFile = path.join(artifactRoot, "release", "closure-broad-mode.env");
fs.writeFileSync(closureBroadModeEnvFile, "DDD_RELEASE_EVIDENCE_STRICT=true\n");
fs.chmodSync(closureBroadModeEnvFile, 0o644);
const releaseBlockerClosureCommandsBroadModeEnv = spawnSync("bash", [releaseBlockerClosureCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_ENV_FILE: closureBroadModeEnvFile,
    DDD_RELEASE_CLOSURE_CHECK_ENV: "1",
    DDD_RELEASE_CLOSURE_ORDER: "3",
  },
});
assert.notEqual(releaseBlockerClosureCommandsBroadModeEnv.status, 0);
assert.match(releaseBlockerClosureCommandsBroadModeEnv.stderr, /Release env file permissions are too broad:/);
assert.equal(releaseClosureWaveEnvMatrix.recommendation, "NO_GO_STRICT");
assert.equal(releaseClosureWaveEnvMatrix.noAutoWaivers, true);
assert.equal(releaseClosureWaveEnvMatrix.summary.waveCount, releaseBlockerClosurePlan.waves.length);
assert.deepEqual(
  releaseClosureWaveEnvMatrix.waves.map((wave) => wave.batchId).sort(),
  releaseBlockerClosurePlan.waves.map((wave) => wave.batchId).sort(),
);
assert.deepEqual(
  releaseClosureWaveEnvMatrix.uniqueEnvKeys.sort(),
  [...new Set(releaseClosureWaveEnvMatrix.waves.flatMap((wave) => wave.envKeys))].sort(),
);
assert.equal(
  releaseClosureWaveEnvMatrixCsv.split("\n")[0],
  "wave,owner,batchId,priority,closureKinds,itemOrders,itemIds,envKeyCount,envKeys,commands,expectedArtifacts,blockerHints",
);
assert(releaseClosureWaveEnvMatrix.waves.every((wave) => wave.expectedArtifacts.every((value) => /^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(value))));
assert(releaseClosureWaveEnvMatrix.waves.every((wave) => Array.isArray(wave.blockerHints)));
assert.match(releaseClosureWaveEnvMatrixMarkdown, /^# DDD Release Closure Wave Env Matrix/m);
assert.match(releaseClosureWaveEnvMatrixMarkdown, /## Wave \d+\. release-infra \/ p0-docker-release-infra/);
assert.match(releaseClosureWaveEnvTemplate, /^# Lumira DDD closure wave release environment template\./m);
assert.match(releaseClosureWaveEnvTemplate, /# Wave \d+: release-infra \/ p0-docker-release-infra/);
assert.match(releaseClosureWaveEnvTemplate, /DDD_DOCKER_BUILD_STRICT=__REQUIRED__/);
assert.match(releaseClosureWaveEnvTemplate, /# DDD_RELEASE_CLOSURE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-blocker-closure-commands\.sh/);
assert.match(releaseClosureWaveEnvTemplate, /# DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1 DDD_RELEASE_CLOSURE_EXECUTE=1 bash artifacts\/ddd\/release\/release-blocker-closure-commands\.sh # diagnostic only; final exit remains non-zero on failures/);
assert(releaseBlockerClosureCommands.includes("run_closure_command 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh'"));
const releaseClosureWaveEnvTemplateKeys = [...releaseClosureWaveEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);
assert.equal(new Set(releaseClosureWaveEnvTemplateKeys).size, releaseClosureWaveEnvTemplateKeys.length);
assert.equal(releaseClosureWaveReceipts.recommendation, "NO_GO_STRICT");
assert.equal(releaseClosureWaveReceipts.noAutoWaivers, true);
assert.equal(releaseClosureWaveReceipts.summary.waveCount, releaseClosureWaveEnvMatrix.waves.length);
assert.equal(
  releaseClosureWaveReceipts.summary.missingArtifactCount,
  releaseClosureWaveReceipts.waves.reduce((sum, wave) => sum + wave.missingArtifactCount, 0),
);
assert.equal(
  releaseClosureWaveReceipts.summary.contentBlockedCount,
  releaseClosureWaveReceipts.waves.filter((wave) => wave.receiptStatus === "CONTENT_BLOCKED").length,
);
assert(releaseClosureWaveReceipts.waves.every((wave) => (
  (wave.blockerHints || []).length === 0 || wave.receiptStatus === "CONTENT_BLOCKED"
)));
assert(releaseClosureWaveReceipts.waves.every((wave) => wave.rerunCommands.includes("node scripts/ddd-release-readiness-summary.mjs")));
assert.equal(
  releaseClosureWaveReceiptsCsv.split("\n")[0],
  "wave,owner,batchId,priority,receiptStatus,itemOrders,itemIds,expectedArtifactCount,presentArtifactCount,missingArtifactCount,missingArtifacts,blockerHints,rerunCommands",
);
assert(releaseClosureWaveReceipts.waves.every((wave) => wave.missingArtifacts.every((value) => /^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(value))));
assert(releaseClosureWaveReceipts.waves.every((wave) => Array.isArray(wave.blockerHints)));
assert.match(releaseClosureWaveReceiptsMarkdown, /^# DDD Release Closure Wave Receipts/m);
assert.match(releaseClosureWaveReceiptsMarkdown, /Rerun commands:/);
assert.match(releaseClosureWaveReceiptsMarkdown, /Content blocked:/);
assert.equal(releaseClosureWaveBlockerMap.recommendation, "NO_GO_STRICT");
assert.equal(releaseClosureWaveBlockerMap.noAutoWaivers, true);
assert.equal(releaseClosureWaveBlockerMap.summary.waveCount, releaseBlockerClosurePlan.waves.length);
assert.equal(
  releaseClosureWaveBlockerMap.summary.mappedActionCount,
  releaseBlockerClosurePlan.items.filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES").length,
);
assert.deepEqual(
  releaseClosureWaveBlockerMap.mappedItemIds.sort(),
  releaseBlockerClosurePlan.items.filter((item) => item.closureKind !== "WAIT_FOR_DEPENDENCIES").map((item) => item.id).sort(),
);
assert(releaseClosureWaveBlockerMap.waves.every((wave) => wave.mappingConfidence === "candidate"));
assert(releaseClosureWaveBlockerMap.waves.every((wave) => wave.commands.length > 0));
assert(releaseClosureWaveBlockerMap.waves.every((wave) => wave.expectedArtifacts.length > 0));
assert.equal(
  releaseClosureWaveBlockerMapCsv.split("\n")[0],
  "wave,owner,batchId,priority,mappingConfidence,itemIds,sources,categoryHints,candidateBlockerCount,commands,expectedArtifacts,blockerHints,rerunCommands",
);
assert.match(releaseClosureWaveBlockerMapMarkdown, /^# DDD Release Closure Wave Blocker Map/m);
assert.match(releaseClosureWaveBlockerMapMarkdown, /strict release evidence gate remains authoritative/);
assert.match(releaseClosureWaveBlockerMapMarkdown, /p0-docker-release-infra|performance-baseline/);
assert.equal(releasePerformanceBaselineClosure.recommendation, "NO_GO_STRICT");
assert.equal(releasePerformanceBaselineClosure.noAutoWaivers, true);
assert.equal(releasePerformanceBaselineClosure.productionEquivalenceRequired.https, true);
assert.equal(releasePerformanceBaselineClosure.productionEquivalenceRequired.nonLocal, true);
assert.deepEqual(releasePerformanceBaselineClosure.evidenceChecklist.map((item) => item.id), [
  "authenticated-runtime-actual-evidence",
  "authenticated-runtime-baseline-promotion-evidence",
  "baseline-release-gate-acceptance-evidence",
]);
assert(releasePerformanceBaselineClosure.evidenceChecklist[0].requiredFields.includes("productionEquivalence.deploymentEvidence"));
assert(releasePerformanceBaselineClosure.evidenceChecklist[1].requiredArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json"));
assert(releasePerformanceBaselineClosure.evidenceChecklist[2].acceptanceCriteria.some((criterion) => criterion.includes("Final go/no-go gate")));
assert.equal(releasePerformanceBaselineClosure.nextCommand, "DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh");
assert.equal(releasePerformanceBaselineClosure.fastPath.commands.at(-1), finalGoNoGoEnforceCommand);
assert(releasePerformanceBaselineClosure.requiredEnvKeys.includes("DDD_AUTH_PERF_BASELINE_ACCEPTED_BY"));
assert(releasePerformanceBaselineClosure.commands.includes("DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh"));
assert(releasePerformanceBaselineClosure.commands.includes("DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs"));
assert(releasePerformanceBaselineClosure.commands.includes("node scripts/ddd-promote-performance-baseline.mjs"));
assert(releasePerformanceBaselineClosure.commands.includes("DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs"));
assert(releasePerformanceBaselineClosure.commands.includes(finalGoNoGoEnforceCommand));
assert(releasePerformanceBaselineClosure.expectedArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json"));
assert.match(releasePerformanceBaselineClosureMarkdown, /^# DDD Release Performance Baseline Closure/m);
assert.match(releasePerformanceBaselineClosureMarkdown, /Ready to promote:/);
assert.match(releasePerformanceBaselineClosureMarkdown, /## Fast Path/);
assert.match(releasePerformanceBaselineClosureMarkdown, /## Production Equivalence Required/);
assert.match(releasePerformanceBaselineClosureMarkdown, /## Evidence Checklist/);
assert.match(releasePerformanceBaselineClosureMarkdown, /authenticated-runtime-actual-evidence/);
assert.match(releasePerformanceBaselineClosureMarkdown, /Next command: DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-performance-baseline-commands\.sh/);
assert.match(releasePerformanceBaselineCommands, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert(releasePerformanceBaselineCommands.includes("DDD_AUTH_PERF_BASELINE_EXECUTE"));
assert(releasePerformanceBaselineCommands.includes("DDD_AUTH_PERF_BASELINE_CHECK_ENV"));
assert(releasePerformanceBaselineCommands.includes("Template env files are worksheets"));
assert(releasePerformanceBaselineCommands.includes("release-final-owner-queue-env.template.env"));
assert(releasePerformanceBaselineCommands.includes("Release env file permissions are too broad"));
assert(releasePerformanceBaselineCommands.includes("[ddd-auth-perf-baseline][env-missing]"));
assert(releasePerformanceBaselineCommands.includes("[ddd-auth-perf-baseline][env-placeholder]"));
assert(releasePerformanceBaselineCommands.includes("[ddd-auth-perf-baseline][env-not-https]"));
assert(releasePerformanceBaselineCommands.includes("[ddd-auth-perf-baseline][env-local-url]"));
assert(releasePerformanceBaselineCommands.includes("DDD_AUTH_PERF_STRICT=true node scripts/ddd-authenticated-performance-smoke.mjs"));
assert(releasePerformanceBaselineCommands.includes("DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs"));
assert(releasePerformanceBaselineCommands.includes("DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts/ddd-release-evidence-manifest.mjs"));
assert.doesNotMatch(releasePerformanceBaselineCommands, /run_command 'node scripts\/ddd-release-evidence-manifest\.mjs'/);
assert.doesNotMatch(releasePerformanceBaselineCommands, /echo '- node scripts\/ddd-release-evidence-manifest\.mjs'/);
assert.equal(releaseFinalGoNoGo.recommendation, releaseFastTrack.recommendation);
assert.equal(releaseFinalGoNoGo.finalRecommendation, releaseFinalGoNoGo.recommendation);
assert.equal(releaseFinalGoNoGo.noAutoWaivers, true);
assert.equal(
  releaseFinalGoNoGo.cutoverAllowed,
  releaseFastTrack.recommendation === "GO_STRICT"
    && releaseFinalGoNoGo.releaseEnvFileCutoverSafe === true
    && releaseFinalGoNoGo.summary.ownerInputReceiptCutoverReady === true,
);
assert.equal(releaseFinalGoNoGo.releaseEnvFileCutoverSafe, false);
assert.equal(releaseFinalGoNoGo.gate.blockers, summary.gate.blockers);
assert.deepEqual(releaseFinalGoNoGo.safetySignals.releaseEnvFile, releaseFastTrack.safetySignals.releaseEnvFile);
assert.equal(
  releaseFinalGoNoGo.summary.blockedCutoverItems,
  releaseFastTrack.cutoverChecklist.filter((item) => item.status !== "PASS").length,
);
assert.equal(releaseFinalGoNoGo.summary.runnableClosureWaves, releaseBlockerClosurePlan.waves.length);
assert.equal(releaseFinalGoNoGo.summary.receiptMissingArtifactWaves, releaseClosureWaveReceipts.summary.artifactMissingCount);
assert.equal(releaseFinalGoNoGo.summary.receiptContentBlockedWaves, releaseClosureWaveReceipts.summary.contentBlockedCount);
assert.equal(releaseFinalGoNoGo.summary.performanceBaselineStatus, releasePerformanceBaselineClosure.status);
assert.equal(releaseFinalGoNoGo.summary.ownerInputReceiptStatus, releaseOwnerInputReceipt.status);
assert.equal(releaseFinalGoNoGo.summary.ownerInputReceiptCutoverReady, releaseOwnerInputReceipt.cutoverReady);
assert.equal(releaseFinalGoNoGo.summary.ownerInputReceiptMissingCriteria, releaseOwnerInputReceipt.missingCriteria.length);
assert.deepEqual(releaseFinalGoNoGo.ciSummary.ownerInputReceipt, {
  artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
  csv: "artifacts/ddd/release/release-owner-input-receipt.csv",
  itemsCsv: "artifacts/ddd/release/release-owner-input-receipt-items.csv",
  itemsMarkdown: "artifacts/ddd/release/release-owner-input-receipt-items.md",
  markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
  status: releaseOwnerInputReceipt.status,
  cutoverReady: releaseOwnerInputReceipt.cutoverReady,
  requiredOwnerInputs: releaseOwnerInputReceipt.summary.requiredOwnerInputs,
  ownerCount: releaseOwnerInputReceipt.summary.ownerCount,
  readyOwnerCount: releaseOwnerInputReceipt.summary.readyOwnerCount,
  pendingOwnerCount: releaseOwnerInputReceipt.summary.pendingOwnerCount,
  missingCriteria: releaseOwnerInputReceipt.missingCriteria,
});
assert(releaseFinalGoNoGo.currentStopReasons.some((reason) => reason.startsWith("owner input receipt pending:")));
assert.match(releaseOwnerInputReceiptCsv, /owner,ready,requiredOwnerInputs/);
assert.match(releaseOwnerInputReceiptCsv, /PENDING_OWNER_INPUT/);
assert.doesNotMatch(releaseOwnerInputReceiptCsv, /__REQUIRED__|DDD_RELEASE_ENV_FILE=\.env|DB_PASSWORD=/);
assert.equal(releaseOwnerInputReceipt.summary.itemReceiptCount, releaseOwnerInputReceipt.itemReceipts.length);
assert.equal(releaseOwnerInputReceipt.itemReceipts.length, releaseEnvOwnerInputPacket.items.length);
assert.match(releaseOwnerInputReceiptItemsCsv, /inputOrder,fillOrder,owner,ownerReady,canonicalKey/);
assert.match(releaseOwnerInputReceiptItemsCsv, /PENDING|PLACEHOLDER/);
assert.doesNotMatch(releaseOwnerInputReceiptItemsCsv, /__REQUIRED__|DDD_RELEASE_ENV_FILE=\.env|DB_PASSWORD=/);
assert.match(releaseOwnerInputReceiptItemsMarkdown, /^# DDD Release Owner Input Receipt Items/m);
assert.match(releaseOwnerInputReceiptItemsMarkdown, /## Items/);
assert.doesNotMatch(releaseOwnerInputReceiptItemsMarkdown, /__REQUIRED__|DDD_RELEASE_ENV_FILE=\.env|DB_PASSWORD=/);
const ownerReceiptItemFiles = fs.readdirSync(releaseOwnerInputReceiptItemsDir).filter((file) => file.endsWith(".md")).sort();
assert.equal(ownerReceiptItemFiles.length, releaseEnvOwnerInputPacket.owners.length);
assert(ownerReceiptItemFiles.some((file) => file.includes("ai-owner")));
for (const fileName of ownerReceiptItemFiles) {
  const ownerReceiptItemMarkdown = fs.readFileSync(path.join(releaseOwnerInputReceiptItemsDir, fileName), "utf8");
  assert.match(ownerReceiptItemMarkdown, /^# DDD Release Owner Input Receipt Items:/m);
  assert.match(ownerReceiptItemMarkdown, /## Items/);
  assert.doesNotMatch(ownerReceiptItemMarkdown, /__REQUIRED__|DDD_RELEASE_ENV_FILE=\.env|DB_PASSWORD=/);
}
assert(releaseOwnerInputReceiptMarkdown.includes("Concrete values are intentionally omitted"));
assert(releaseFinalGoNoGo.currentStopReasons.length > 0);
assert(releaseFinalGoNoGo.nextCommands.includes("node scripts/ddd-release-readiness-summary.mjs"));
assert.equal(releaseFinalGoNoGo.nextCommands[0], "bash artifacts/ddd/release/release-preflight-gate.sh");
assert.equal(releaseFinalGoNoGo.nextCommands[1], "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
assert.equal(releaseFinalGoNoGo.nextCommands[2], "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh");
assert.equal(releaseFinalGoNoGo.nextCommands[3], "DDD_RELEASE_ENV_FILE=<release-env-file> bash artifacts/ddd/release/release-env-bootstrap.sh");
assert.equal(releaseFinalGoNoGo.nextCommands[4], "node scripts/ddd-release-env-owner-templates-merge.mjs artifacts/ddd/release/release-env-owner-templates artifacts/ddd/release/release-env-canonical-fill.template.env");
assert.equal(releaseFinalGoNoGo.nextCommands[5], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-merge.mjs artifacts/ddd/release/release-env-canonical-fill.template.env <release-env-file>");
assert.equal(releaseFinalGoNoGo.nextCommands[6], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs");
assert.equal(releaseFinalGoNoGo.nextCommands[7], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs");
assert.equal(releaseFinalGoNoGo.nextCommands[8], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-alias-sync.mjs");
assert.equal(releaseFinalGoNoGo.nextCommands[9], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-canonical-lint.mjs artifacts/ddd/release/release-env-canonical-fill.template.env");
assert.equal(releaseFinalGoNoGo.nextCommands[10], "DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs");
assert.doesNotMatch(JSON.stringify(releaseFinalGoNoGo), /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)/);
assert.doesNotMatch(JSON.stringify(releaseFinalGoNoGo), /(^|\s)(?:[^\s`|]*\/)?\.env\.release(?:\.[A-Za-z0-9_-]+)?(?=\s|`|\)|,|$)/);
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs"));
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs"));
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts/ddd/release/release-performance-baseline-commands.sh"));
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-safe-defaults.mjs"));
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-provenance-defaults.mjs"));
assert(releaseFinalGoNoGo.nextCommands.includes("DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs"));
assert.equal(releaseFinalGoNoGo.ciSummary.firstNextCommand, releaseFinalGoNoGo.nextCommands[0]);
assert.equal(releaseFinalGoNoGo.ciSummary.firstOwnerAction.owner, releaseNextActionQueue.items[0].owner);
assert.equal(releaseFinalGoNoGo.ciSummary.firstOwnerActionCommand, releaseNextActionQueue.items[0].executableCommands[0]);
const releaseInfraNextAction = releaseNextActionQueue.items.find((item) => item.owner === "release-infra");
assert(releaseInfraNextAction);
if (releaseInfraNextAction.strictGateBlockerCount === 0) {
  assert.match(releaseInfraNextAction.reason, /release-env-lint/);
  assert.match(releaseInfraNextAction.executableCommands[0], /ddd-release-env-file-lint\.mjs/);
}
assert.equal(releaseFinalGoNoGo.ciSummary.enforceCommand, "DDD_RELEASE_PREFLIGHT_ENFORCE=1 bash artifacts/ddd/release/release-preflight-gate.sh");
assert.equal(releaseFinalGoNoGo.ciSummary.finalGoNoGoEnforceCommand, "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
assert.equal(releaseFinalGoNoGo.ciSummary.rerunCommands[0], "bash artifacts/ddd/release/release-preflight-gate.sh");
assert.equal(releaseFinalGoNoGo.ciSummary.rerunCommands[1], "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
assert.equal(releaseFinalGoNoGo.ciSummary.nonGoExitCode, 10);
assert.deepEqual(releaseFinalGoNoGo.ciSummary.exitCodeMap, {
  finalNoGo: 10,
  finalPacketInvalid: 11,
  releaseEnvUnresolved: 21,
  releaseEnvInvalidPacket: 22,
});
assert(releaseFinalGoNoGo.ciSummary.enforceCommand.includes("DDD_RELEASE_PREFLIGHT_ENFORCE=1"));
assert(releaseFinalGoNoGo.ciSummary.stopOwners.includes("release-performance"));
assert.equal(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.totalCanonicalKeys,
  releaseEnvReadinessRedacted.summary.totalCanonicalKeys,
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.blockers,
  releaseEnvReadinessRedacted.summary.blockers,
);
assert.equal(
  releaseFinalGoNoGo.safetySignals.releaseEnvFile.blockingSafeDefaultAvailable,
  releaseEnvReadinessRedacted.summary.blockingSafeDefaultAvailable,
);
assert.equal(
  releaseFinalGoNoGo.safetySignals.releaseEnvFile.blockingRequiresOwnerInput,
  releaseEnvReadinessRedacted.summary.blockingRequiresOwnerInput,
);
assert.equal(
  releaseFinalGoNoGo.safetySignals.releaseEnvFile.safeDefaultsExhausted,
  releaseEnvReadinessRedacted.summary.safeDefaultsExhausted,
);
assert.deepEqual(
  releaseNextActionQueue.safetySignals.releaseEnvFile.ownerInputReasonCounts,
  releaseEnvReadinessRedacted.summary.ownerInputReasonCounts,
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerCount,
  releaseEnvReadinessRedacted.summary.ownerCount,
);
assert.deepEqual(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerBlockerSummary.map((owner) => owner.owner),
  releaseEnvOwnerHandoffRedacted.owners
    .filter((owner) => owner.blockers > 0 || owner.placeholders > 0 || owner.missing > 0)
    .map((owner) => owner.owner),
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.firstEnvOwnerAction.owner,
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerBlockerSummary[0].owner,
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.firstEnvOwnerAction.handoffPath,
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerBlockerSummary[0].handoffPath,
);
assert.equal(releaseFinalGoNoGo.ciSummary.orchestratorPreflight.artifact, "artifacts/ddd/release/orchestrator-report.json");
assert.equal(releaseFinalGoNoGo.ciSummary.orchestratorPreflight.mode, summary.diagnostics.orchestrator.mode);
assert.equal(releaseFinalGoNoGo.ciSummary.orchestratorPreflight.status, summary.diagnostics.orchestrator.preflight.status);
assert.equal(releaseFinalGoNoGo.ciSummary.orchestratorPreflight.blockers, summary.diagnostics.orchestrator.preflight.blockers);
assert.deepEqual(
  releaseFinalGoNoGo.ciSummary.orchestratorPreflight.ownerActionSummary.map((owner) => owner.owner).sort(),
  ["ai", "database", "frontend", "release-infra"].sort(),
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.firstOrchestratorPreflightAction.owner,
  releaseFinalGoNoGo.ciSummary.orchestratorPreflight.ownerActionSummary[0].owner,
);
assert.match(releaseFinalGoNoGo.ciSummary.firstOrchestratorPreflightAction.id, /^orchestrator-preflight-/);
assert.equal(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerHandoffDir,
  "artifacts/ddd/release/release-env-owner-handoff-redacted",
);
assert.equal(
  releaseFinalGoNoGo.ciSummary.releaseEnvReadiness.ownerHandoffCsv,
  "artifacts/ddd/release/release-env-owner-handoff-redacted.csv",
);
assert(releaseFinalGoNoGo.ciSummary.blockedArtifactPaths.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json"));
assert.deepEqual(
  releaseFinalGoNoGo.ciSummary.blockedContentHints.slice().sort(),
  [...new Set(releaseFinalGoNoGo.closureWaves.flatMap((wave) => wave.blockerHints || []))].sort(),
);
assert.equal(
  releaseFinalGoNoGoCsv.split("\n")[0],
  "recommendation,finalRecommendation,cutoverAllowed,releaseEnvFileCutoverSafe,gateBlockers,blockedCutoverItems,runnableClosureWaves,receiptMissingArtifactWaves,receiptContentBlockedWaves,performanceBaselineStatus,stopReasons,nextCommands",
);
assert.match(releaseFinalGoNoGoCsv, /^NO_GO_STRICT,NO_GO_STRICT,false,false,/m);
assert.match(releaseFinalGoNoGoMarkdown, /^# DDD Final Go\/No-Go Packet/m);
assert.match(releaseFinalGoNoGoMarkdown, /Final recommendation: NO_GO_STRICT/);
assert.match(releaseFinalGoNoGoMarkdown, /No automatic waivers are allowed/);
assert.match(releaseFinalGoNoGoMarkdown, /## CI Summary/);
assert.match(releaseFinalGoNoGoMarkdown, /Exit codes: finalNoGo=10, finalPacketInvalid=11, releaseEnvUnresolved=21, releaseEnvInvalidPacket=22/);
assert.match(releaseFinalGoNoGoMarkdown, /Release env readiness: blockers=/);
assert.match(releaseFinalGoNoGoMarkdown, /Owner input receipt: status=PENDING_OWNER_INPUT/);
assert.match(releaseFinalGoNoGoMarkdown, /Owner input receipt status: PENDING_OWNER_INPUT/);
assert.match(releaseFinalGoNoGoMarkdown, /Release env owner blockers: /);
assert.match(releaseFinalGoNoGoMarkdown, /First release env owner action: .*blockers=/);
assert.match(releaseFinalGoNoGoMarkdown, /Orchestrator preflight: mode=.* status=.* blockers=/);
assert.match(releaseFinalGoNoGoMarkdown, /Orchestrator preflight owners: /);
assert.match(releaseFinalGoNoGoMarkdown, /First orchestrator preflight action: /);
assert.match(releaseFinalGoNoGoMarkdown, /Release env redacted handoff: artifacts\/ddd\/release\/release-env-owner-handoff-redacted/);
assert.match(releaseFinalGoNoGoMarkdown, /Release env redacted handoff CSV: artifacts\/ddd\/release\/release-env-owner-handoff-redacted\.csv/);
assert.match(releaseFinalGoNoGoMarkdown, /DDD_MIGRATION_CHECK_ENV=true node scripts\/ddd-migration-evidence\.mjs/);
assert.match(releaseFinalGoNoGoMarkdown, /DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts\/ddd-rollback-drill-evidence\.mjs/);
assert.match(releaseFinalGoNoGoMarkdown, /DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-performance-baseline-commands\.sh/);
assert.match(releaseFinalGoNoGoMarkdown, /## Stop Reasons/);
assert.match(releaseFinalGoNoGoMarkdown, /## Safety Signals/);
assert.match(releaseFinalGoNoGoMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseFinalGoNoGoMarkdown, /releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true/);
assert.match(releaseFinalGoNoGoMarkdown, /securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false/);
assert.match(releaseFinalGoNoGoMarkdown, /safeDefaultsExhausted=/);
assert.match(releaseFinalGoNoGoMarkdown, /blockingSafeDefaultAvailable=/);
assert.match(releaseFinalGoNoGoMarkdown, /blockingRequiresOwnerInput=/);
assert.match(releaseFinalGoNoGoMarkdown, /ownerInputReasons=/);
assert.match(releaseFinalGoNoGoMarkdown, /ownerInputOwners=/);
assert.match(releaseFinalGoNoGoMarkdown, /GO only when the release env file is a completed release-env-file with checked chmod 600 permissions\./);
assert.match(releaseFinalGoNoGoMarkdown, /Receipt content blocked waves:/);
assert.match(releaseFinalGoNoGoMarkdown, /Blocked content hints:/);
assert.match(releaseFinalGoNoGoGate, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert(releaseFinalGoNoGoGate.includes("DDD_FINAL_GO_NO_GO_ENFORCE"));
assert(releaseFinalGoNoGoGate.includes("exit 10"));
assert.match(releaseFinalGoNoGoGate, /const exitCodeMap = packet\.ciSummary\?\.exitCodeMap/);
assert.match(releaseFinalGoNoGoGate, /exitCodes finalNoGo=/);
const releaseFinalGoNoGoGateSyntax = spawnSync("bash", ["-n", releaseFinalGoNoGoGatePath], { encoding: "utf8" });
assert.equal(releaseFinalGoNoGoGateSyntax.status, 0, releaseFinalGoNoGoGateSyntax.stderr);
const releaseFinalGoNoGoGateDefault = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env },
});
assert.equal(releaseFinalGoNoGoGateDefault.status, 0, releaseFinalGoNoGoGateDefault.stderr);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /recommendation=NO_GO_STRICT/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /finalRecommendation=NO_GO_STRICT/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /gateBlockers=\d+ stopReasons=\d+/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /ci stopOwners=/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /exitCodes finalNoGo=10 finalPacketInvalid=11 envUnresolved=21 envInvalidPacket=22/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /releaseEnvReadiness blockers=\d+ placeholders=\d+ missing=\d+ filledRedacted=\d+ owners=\d+ handoff=artifacts\/ddd\/release\/release-env-owner-handoff-redacted handoffCsv=artifacts\/ddd\/release\/release-env-owner-handoff-redacted\.csv/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /releaseEnvOwnerBlockers /);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /orchestratorPreflight mode=.* status=.* blockers=\d+ warnings=\d+ selectedSteps=\d+ executedResults=\d+ artifact=artifacts\/ddd\/release\/orchestrator-report\.json/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /orchestratorPreflightOwners /);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /first env owner action: owner=.*blockers=\d+ placeholders=\d+ missing=\d+ handoff=artifacts\/ddd\/release\/release-env-owner-handoff-redacted\//);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /first orchestrator preflight action: owner=.*check=.*reason=.*envKeys=.*command=DDD_RELEASE_EVIDENCE_STRICT=true node scripts\/ddd-release-evidence-orchestrator\.mjs --run --strict/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /safety releaseEnvFile ready=false status=FAIL inputKind=release-env-file envFilePresent=true securityChecked=true permissionSafe=true mode=600 requiredMode=600/);
assert(releaseFinalGoNoGoGate.includes("releaseEnvFileCutoverSafe"));
assert(releaseFinalGoNoGoGate.includes("releaseEnvFile must be PASS release-env-file with checked chmod 600 permissions"));
assert.match(releaseFinalGoNoGoGateDefault.stdout, /blockedContentHints=\d+/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /first next command:/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /first owner action:/);
assert.match(releaseFinalGoNoGoGateDefault.stdout, /first owner next action:/);
assert.match(releasePreflightGate, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert.match(releasePreflightGate, /DDD_RELEASE_PREFLIGHT_ENFORCE/);
assert.match(releasePreflightGate, /DDD_RELEASE_DIR="\$\{DDD_RELEASE_DIR:-\$\{DDD_RELEASE_EVIDENCE_DIR:-artifacts\/ddd\}\/release\}"/);
assert.match(releasePreflightGate, /release-artifact-integrity-gate\.sh/);
assert.match(releasePreflightGate, /DDD_RELEASE_MANIFEST_CHECK_ENV=true/);
assert.match(releasePreflightGate, /ddd-release-evidence-manifest\.mjs/);
assert.match(releasePreflightGate, /ddd-release-artifact-path-leak-contract\.mjs/);
assert.match(releasePreflightGate, /ddd-release-unblock-brief\.mjs/);
assert.match(releasePreflightGate, /ddd-release-unblock-brief-contract\.mjs/);
assert.match(releasePreflightGate, /ddd-release-env-owner-handoff-redacted-contract\.mjs/);
assert.match(releasePreflightGate, /ddd-release-config-owner-input-reconciliation\.mjs/);
assert.match(releasePreflightGate, /ddd-release-owner-input-receipt\.mjs/);
assert.match(releasePreflightGate, /ddd-release-owner-input-receipt-contract\.mjs/);
assert.match(releasePreflightGate, /release-env-readiness-gate\.sh/);
assert.match(releasePreflightGate, /release-final-go-no-go-gate\.sh/);
const releasePreflightGateSyntax = spawnSync("bash", ["-n", releasePreflightGatePath], { encoding: "utf8" });
assert.equal(releasePreflightGateSyntax.status, 0, releasePreflightGateSyntax.stderr);
const releasePreflightGateDefaultReportPath = path.join(artifactRoot, "release", "release-preflight-report-default.json");
const releasePreflightGateDefault = spawnSync("bash", [releasePreflightGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: artifactRoot,
    DDD_EXPLAIN_DIR: explainDir,
    DDD_RELEASE_PREFLIGHT_REPORT: releasePreflightGateDefaultReportPath,
  },
});
assert.equal(releasePreflightGateDefault.status, 0, releasePreflightGateDefault.stderr);
assert.match(releasePreflightGateDefault.stdout, /step=artifact-integrity/);
assert.match(releasePreflightGateDefault.stdout, /step=manifest-provenance-preflight/);
assert.match(releasePreflightGateDefault.stdout, /step=artifact-path-leak/);
assert.match(releasePreflightGateDefault.stdout, /step=unblock-brief/);
assert.match(releasePreflightGateDefault.stdout, /step=unblock-brief-contract/);
assert.match(releasePreflightGateDefault.stdout, /step=env-owner-handoff-redacted/);
assert.match(releasePreflightGateDefault.stdout, /step=config-owner-input-reconciliation/);
assert.match(releasePreflightGateDefault.stdout, /step=owner-input-receipt/);
assert.match(releasePreflightGateDefault.stdout, /step=env-readiness/);
assert.match(releasePreflightGateDefault.stdout, /step=final-go-no-go/);
assert.match(releasePreflightGateDefault.stdout, /release-preflight-report-default\.json/);
assert.match(releasePreflightGateDefault.stdout, /complete enforce=false/);
const releasePreflightGateDefaultReport = JSON.parse(fs.readFileSync(releasePreflightGateDefaultReportPath, "utf8"));
assert.equal(releasePreflightGateDefaultReport.status, "PASS");
assert.equal(releasePreflightGateDefaultReport.enforce, false);
assert.equal(releasePreflightGateDefaultReport.releaseEnvFileCutoverSafe, false);
assert.equal(releasePreflightGateDefaultReport.failedStep, null);
assert.deepEqual(releasePreflightGateDefaultReport.steps.map((step) => step.name), [
  "artifact-integrity",
  "manifest-provenance-preflight",
  "artifact-path-leak",
  "unblock-brief",
  "env-owner-handoff-redacted",
  "env-owner-input-packet",
  "config-owner-input-reconciliation",
  "owner-input-receipt",
  "env-readiness",
  "final-go-no-go",
]);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "artifact-integrity")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "artifact-path-leak")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "unblock-brief")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "env-owner-handoff-redacted")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "config-owner-input-reconciliation")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "owner-input-receipt")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "env-readiness")?.exitCode, 0);
assert.equal(releasePreflightGateDefaultReport.steps.find((step) => step.name === "final-go-no-go")?.exitCode, 0);
const releasePreflightGateDefaultFailures = releasePreflightGateDefaultReport.steps.filter((step) => step.exitCode > 0);
assert.equal(releasePreflightGateDefaultReport.advisoryFailureCount, releasePreflightGateDefaultFailures.length);
assert.deepEqual(releasePreflightGateDefaultReport.advisoryFailures, releasePreflightGateDefaultFailures.map((step) => ({
  name: step.name,
  exitCode: step.exitCode,
  command: step.command,
})));
assert.match(releasePreflightGateDefaultReport.advisoryNotice, new RegExp(`advisoryFailureCount=${releasePreflightGateDefaultFailures.length}`));
const releasePreflightGateEnforcedReportPath = path.join(artifactRoot, "release", "release-preflight-report-enforced.json");
const releasePreflightGateEnforced = spawnSync("bash", [releasePreflightGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: artifactRoot,
    DDD_EXPLAIN_DIR: explainDir,
    DDD_RELEASE_PREFLIGHT_ENFORCE: "1",
    DDD_RELEASE_PREFLIGHT_REPORT: releasePreflightGateEnforcedReportPath,
  },
});
assert.equal(releasePreflightGateEnforced.status, releasePreflightGateDefaultReport.steps.find((step) => step.name === "manifest-provenance-preflight")?.exitCode);
assert.match(releasePreflightGateEnforced.stderr, /(?:manifest provenance sourceEnvironment is required|missing explain directory|no explain JSON files)/);
const releasePreflightGateEnforcedReport = JSON.parse(fs.readFileSync(releasePreflightGateEnforcedReportPath, "utf8"));
assert.equal(releasePreflightGateEnforcedReport.status, "NO_GO");
assert.equal(releasePreflightGateEnforcedReport.enforce, true);
assert.equal(releasePreflightGateEnforcedReport.advisoryFailureCount, 0);
assert.deepEqual(releasePreflightGateEnforcedReport.advisoryFailures, []);
assert.equal(releasePreflightGateEnforcedReport.releaseEnvFileCutoverSafe, false);
assert.equal(releasePreflightGateEnforcedReport.failedStep, "manifest-provenance-preflight");
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "artifact-path-leak").exitCode, 0);
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "unblock-brief").exitCode, -1);
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "env-owner-handoff-redacted").exitCode, -1);
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "owner-input-receipt").exitCode, -1);
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "env-readiness").exitCode, -1);
assert.equal(releasePreflightGateEnforcedReport.steps.find((step) => step.name === "final-go-no-go").exitCode, -1);
const releaseFinalGoNoGoGateEnforced = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env, DDD_FINAL_GO_NO_GO_ENFORCE: "1" },
});
assert.equal(releaseFinalGoNoGoGateEnforced.status, 10);
assert.match(releaseFinalGoNoGoGateEnforced.stderr, /cutover blocked/);
const releaseFinalGoNoGoInvalidPacketPath = path.join(artifactRoot, "release", "release-final-go-no-go-invalid.json");
fs.writeFileSync(releaseFinalGoNoGoInvalidPacketPath, `${JSON.stringify({ finalRecommendation: "NO_GO_STRICT" }, null, 2)}\n`);
const releaseFinalGoNoGoInvalidPacket = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env, DDD_FINAL_GO_NO_GO_PACKET: releaseFinalGoNoGoInvalidPacketPath },
});
assert.equal(releaseFinalGoNoGoInvalidPacket.status, 11);
assert.match(releaseFinalGoNoGoInvalidPacket.stderr, /invalid-packet/);
const releaseFinalGoNoGoMismatchedEnvSafetyPacketPath = path.join(artifactRoot, "release", "release-final-go-no-go-mismatched-env-safety.json");
fs.writeFileSync(releaseFinalGoNoGoMismatchedEnvSafetyPacketPath, `${JSON.stringify({
  ...releaseFinalGoNoGo,
  releaseEnvFileCutoverSafe: true,
}, null, 2)}\n`);
const releaseFinalGoNoGoMismatchedEnvSafetyPacket = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env, DDD_FINAL_GO_NO_GO_PACKET: releaseFinalGoNoGoMismatchedEnvSafetyPacketPath },
});
assert.equal(releaseFinalGoNoGoMismatchedEnvSafetyPacket.status, 11);
assert.match(releaseFinalGoNoGoMismatchedEnvSafetyPacket.stderr, /releaseEnvFileCutoverSafeMismatch/);
const unsafeCutoverPacket = path.join(artifactRoot, "release", "unsafe-cutover-packet.json");
fs.writeFileSync(unsafeCutoverPacket, `${JSON.stringify({
  ...releaseFinalGoNoGo,
  recommendation: "GO_STRICT",
  finalRecommendation: "GO_STRICT",
  cutoverAllowed: true,
  gate: { blockers: 0, warnings: 0 },
  currentStopReasons: [],
  safetySignals: {
    ...releaseFinalGoNoGo.safetySignals,
    releaseEnvFile: {
      ...releaseFinalGoNoGo.safetySignals.releaseEnvFile,
      ready: false,
    },
  },
}, null, 2)}\n`);
const releaseFinalGoNoGoGateUnsafeCutover = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_GO_NO_GO_PACKET: unsafeCutoverPacket,
    DDD_FINAL_GO_NO_GO_ENFORCE: "1",
  },
});
assert.equal(releaseFinalGoNoGoGateUnsafeCutover.status, 4);
assert.match(releaseFinalGoNoGoGateUnsafeCutover.stderr, /releaseEnvFile\.ready must be true before cutoverAllowed can be true/);
const unsafeGoWithStopReasonsPacket = path.join(artifactRoot, "release", "unsafe-go-with-stop-reasons-packet.json");
fs.writeFileSync(unsafeGoWithStopReasonsPacket, `${JSON.stringify({
  ...releaseFinalGoNoGo,
  recommendation: "GO_STRICT",
  finalRecommendation: "GO_STRICT",
  cutoverAllowed: true,
  releaseEnvFileCutoverSafe: true,
  gate: { blockers: 0, warnings: 0 },
  currentStopReasons: ["cutover checklist blocked: release-environment"],
  safetySignals: {
    ...releaseFinalGoNoGo.safetySignals,
    releaseEnvFile: {
      ready: true,
      status: "PASS",
      inputKind: "release-env-file",
      envFilePresent: true,
      generatedMissingTemplate: false,
      securityChecked: true,
      permissionSafe: true,
      permissionCheckSkipped: false,
      modeOctal: "600",
      requiredMode: "600",
    },
  },
}, null, 2)}\n`);
const releaseFinalGoNoGoGateUnsafeGoWithStopReasons = spawnSync("bash", [releaseFinalGoNoGoGatePath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_GO_NO_GO_PACKET: unsafeGoWithStopReasonsPacket,
    DDD_FINAL_GO_NO_GO_ENFORCE: "1",
  },
});
assert.equal(releaseFinalGoNoGoGateUnsafeGoWithStopReasons.status, 11);
assert.match(releaseFinalGoNoGoGateUnsafeGoWithStopReasons.stderr, /goWithStopReasons/);
assert.equal(releaseFinalOwnerQueue.recommendation, releaseFinalGoNoGo.recommendation);
assert.equal(releaseFinalOwnerQueue.finalRecommendation, releaseFinalGoNoGo.finalRecommendation);
assert.equal(releaseFinalOwnerQueue.cutoverAllowed, releaseFinalGoNoGo.cutoverAllowed);
assert.equal(releaseFinalOwnerQueue.releaseEnvFileCutoverSafe, releaseFinalGoNoGo.releaseEnvFileCutoverSafe);
assert.equal(releaseFinalOwnerQueue.noAutoWaivers, true);
assert.deepEqual(releaseFinalOwnerQueue.safetySignals.releaseEnvFile, releaseFinalGoNoGo.safetySignals.releaseEnvFile);
assert.deepEqual(releaseFinalOwnerQueue.ownerInputReceipt, {
  artifact: "artifacts/ddd/release/release-owner-input-receipt.json",
  csv: "artifacts/ddd/release/release-owner-input-receipt.csv",
  itemsCsv: "artifacts/ddd/release/release-owner-input-receipt-items.csv",
  itemsMarkdown: "artifacts/ddd/release/release-owner-input-receipt-items.md",
  markdown: "artifacts/ddd/release/release-owner-input-receipt.md",
  status: releaseOwnerInputReceipt.status,
  cutoverReady: releaseOwnerInputReceipt.cutoverReady,
  requiredOwnerInputs: releaseOwnerInputReceipt.summary.requiredOwnerInputs,
  ownerCount: releaseOwnerInputReceipt.summary.ownerCount,
  readyOwnerCount: releaseOwnerInputReceipt.summary.readyOwnerCount,
  pendingOwnerCount: releaseOwnerInputReceipt.summary.pendingOwnerCount,
  missingCriteria: releaseOwnerInputReceipt.missingCriteria.slice().sort(),
  pendingOwners: releaseOwnerInputReceipt.ownerReceipts
    .filter((owner) => owner.ready !== true)
    .map((owner) => ({
      owner: owner.owner,
      requiredOwnerInputs: owner.requiredOwnerInputs,
      remainingPlaceholders: owner.remainingPlaceholders,
      remainingMissing: owner.remainingMissing,
      packetPath: owner.packetPath,
      handoffPath: owner.handoffPath,
      itemChecklistPath: ownerInputReceiptItemChecklistByOwner.get(owner.owner),
    }))
    .sort((left, right) => right.requiredOwnerInputs - left.requiredOwnerInputs || left.owner.localeCompare(right.owner)),
});
assert.equal(releaseFinalOwnerQueue.summary.ownerInputReceiptStatus, releaseOwnerInputReceipt.status);
assert.equal(releaseFinalOwnerQueue.summary.ownerInputReceiptCutoverReady, releaseOwnerInputReceipt.cutoverReady);
assert.equal(releaseFinalOwnerQueue.summary.ownerInputReceiptRequiredOwnerInputs, releaseOwnerInputReceipt.summary.requiredOwnerInputs);
assert.equal(releaseFinalOwnerQueue.summary.ownerInputReceiptPendingOwnerCount, releaseOwnerInputReceipt.summary.pendingOwnerCount);
assert.equal(releaseFinalOwnerQueue.summary.ownerInputReceiptMissingCriteriaCount, releaseOwnerInputReceipt.missingCriteria.length);
assert.match(releaseFinalOwnerQueueMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseFinalOwnerQueueMarkdown, /Owner input receipt status: PENDING_OWNER_INPUT/);
assert.match(releaseFinalOwnerQueueMarkdown, /## Owner Input Receipt/);
assert.match(releaseFinalOwnerQueueMarkdown, /Required owner inputs: \d+/);
assert.deepEqual(
  releaseFinalOwnerQueue.ownerQueues.map((owner) => owner.owner).sort(),
  releaseFinalGoNoGo.ciSummary.stopOwners.slice().sort(),
);
assert(releaseFinalOwnerQueue.summary.actionableOwnerCount > 0);
const releasePerformanceOwnerQueueForCounts = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.owner === "release-performance");
assert(releasePerformanceOwnerQueueForCounts);
const databaseOwnerQueueForCounts = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.owner === "database");
assert(databaseOwnerQueueForCounts);
assert.equal(databaseOwnerQueueForCounts.firstCommand, "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert.equal(databaseOwnerQueueForCounts.commands[0], "DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs");
assert(databaseOwnerQueueForCounts.commands.includes("node scripts/ddd-migration-evidence.mjs"));
assert(releaseFinalOwnerQueue.ownerQueues.every((owner, index) => owner.queueOrder === index + 1));
const actionableFinalOwnerQueues = releaseFinalOwnerQueue.ownerQueues.filter((owner) => owner.canExecute === true);
assert(actionableFinalOwnerQueues.every((owner) => Number.isFinite(owner.executionOrderHint)));
assert(actionableFinalOwnerQueues.every((owner) => owner.commands.includes("node scripts/ddd-release-readiness-summary.mjs")));
assert(actionableFinalOwnerQueues.every((owner) => owner.commands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh")));
assert(actionableFinalOwnerQueues.every((owner) => owner.commands.at(-2) === "node scripts/ddd-release-readiness-summary.mjs"));
assert(actionableFinalOwnerQueues.every((owner) => owner.commands.at(-1) === "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"));
const prioritizedFinalOwnerQueues = actionableFinalOwnerQueues.filter((owner) => owner.firstOwnerActionPriority === true);
assert.equal(prioritizedFinalOwnerQueues.length, 1);
assert.equal(prioritizedFinalOwnerQueues[0].owner, releaseFinalGoNoGo.ciSummary.firstOwnerAction.owner);
assert.equal(prioritizedFinalOwnerQueues[0].firstCommand, releaseFinalGoNoGo.ciSummary.firstOwnerAction.command);
assert(actionableFinalOwnerQueues.every((owner, index, owners) => {
  if (index === 0) return true;
  const previousPriority = owners[index - 1].firstOwnerActionPriority === true ? 0 : 1;
  const currentPriority = owner.firstOwnerActionPriority === true ? 0 : 1;
  if (previousPriority !== currentPriority) {
    return previousPriority <= currentPriority;
  }
  return owners[index - 1].executionOrderHint <= owner.executionOrderHint;
}));
const releaseFinalOwnerQueueFirstExecutable = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.canExecute === true);
assert(releaseFinalOwnerQueueFirstExecutable);
assert.equal(releaseFinalOwnerQueueFirstExecutable.owner, releaseFinalGoNoGo.ciSummary.firstOwnerAction.owner);
assert.equal(releaseFinalOwnerQueueFirstExecutable.firstOwnerActionPriority, true);
assert.equal(
  releaseFinalOwnerQueueFirstExecutable.firstCommand,
  releaseFinalGoNoGo.ciSummary.firstOwnerAction.command,
);
for (const envKey of releaseFinalGoNoGo.ciSummary.firstOwnerAction.envKeys) {
  assert(
    releaseFinalOwnerQueueFirstExecutable.envKeys.includes(envKey),
    `first executable owner envKeys must include first owner action env key ${envKey}`,
  );
}
assert.equal(releaseFinalOwnerQueue.summary.nextExecutableOwner, releaseFinalOwnerQueueFirstExecutable.owner);
assert.equal(releaseFinalOwnerQueue.summary.nextExecutableQueueOrder, releaseFinalOwnerQueueFirstExecutable.queueOrder);
assert.equal(releaseFinalOwnerQueue.summary.nextExecutableCommand, releaseFinalOwnerQueueFirstExecutable.firstCommand);
assert.equal(releaseFinalOwnerQueue.fastPath.owner, releaseFinalOwnerQueueFirstExecutable.owner);
assert.equal(releaseFinalOwnerQueue.fastPath.queueOrder, releaseFinalOwnerQueueFirstExecutable.queueOrder);
assert.equal(releaseFinalOwnerQueue.fastPath.firstCommand, releaseFinalOwnerQueueFirstExecutable.firstCommand);
assert.equal(releaseFinalOwnerQueue.fastPath.finalGateCommand, finalGoNoGoEnforceCommand);
assert.equal(releaseFinalOwnerQueue.fastPath.commands.at(-1), finalGoNoGoEnforceCommand);
assert(releaseFinalOwnerQueue.fastPath.commands.includes("node scripts/ddd-release-readiness-summary.mjs"));
assert.equal(releaseFinalOwnerQueue.fastPath.releaseEnvFileRequired, true);
assert(releaseFinalGoNoGo.nextCommands.includes(releaseFinalOwnerQueue.summary.nextExecutableCommand));
assert.equal(releaseFinalOwnerQueue.summary.nextExecutableEnvKeyCount, releaseFinalOwnerQueueFirstExecutable.envKeyCount);
assert.equal(releaseFinalOwnerQueue.summary.nextExecutableMissingArtifactCount, releaseFinalOwnerQueueFirstExecutable.missingArtifactCount);
assert.equal(
  releaseFinalOwnerQueue.summary.contentBlockerCount,
  [...new Set(releaseFinalOwnerQueue.ownerQueues.flatMap((owner) => owner.contentBlockers || []))].length,
);
const firstWaitingOwnerIndex = releaseFinalOwnerQueue.ownerQueues.findIndex((owner) => owner.queueStatus === "WAITING");
assert(firstWaitingOwnerIndex > 0);
assert(releaseFinalOwnerQueue.ownerQueues.slice(0, firstWaitingOwnerIndex).every((owner) => owner.canExecute === true));
assert(releaseFinalOwnerQueue.ownerQueues.slice(firstWaitingOwnerIndex).every((owner) => owner.canExecute === false));
assert.equal(releasePerformanceOwnerQueueForCounts.canExecute, true);
assert.equal(releasePerformanceOwnerQueueForCounts.commandCount, releasePerformanceOwnerQueueForCounts.commands.length);
assert.equal(releasePerformanceOwnerQueueForCounts.envKeyCount, releasePerformanceOwnerQueueForCounts.envKeys.length);
assert.equal(releasePerformanceOwnerQueueForCounts.missingArtifactCount, releasePerformanceOwnerQueueForCounts.missingArtifacts.length);
assert.equal(releasePerformanceOwnerQueueForCounts.contentBlockerCount, releasePerformanceOwnerQueueForCounts.contentBlockers.length);
assert.equal(releasePerformanceOwnerQueueForCounts.stopReasonCount, releasePerformanceOwnerQueueForCounts.stopReasons.length);
assert(releasePerformanceOwnerQueueForCounts.missingArtifacts.includes("artifacts/ddd/performance/authenticated-runtime-baseline.json"));
assert(releasePerformanceOwnerQueueForCounts.commands.includes("DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs"));
assert(releaseFinalOwnerQueue.ownerQueues.every((owner) => (
  owner.missingArtifacts || []
).every((artifactPath) => /^(artifacts\/ddd\/|tmp\/ddd-explain\/)[^\s]+$/.test(artifactPath))));
assert(releaseFinalOwnerQueue.ownerQueues.every((owner) => Array.isArray(owner.contentBlockers)));
assert(releaseFinalOwnerQueue.ownerQueues.every((owner) => owner.contentBlockerCount === owner.contentBlockers.length));
assert(releasePerformanceOwnerQueueForCounts.stopReasons.some((reason) => reason.includes("source actual artifact must be production-equivalent")));
const frontendOwnerQueueForCounts = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.owner === "frontend");
assert(frontendOwnerQueueForCounts);
assert.equal(frontendOwnerQueueForCounts.canExecute, false);
assert.equal(frontendOwnerQueueForCounts.queueStatus, "WAITING");
assert.equal(
  releaseFinalOwnerQueueCsv.split("\n")[0],
  "owner,queueOrder,executionOrderHint,queueStatus,canExecute,commandCount,envKeyCount,missingArtifactCount,contentBlockerCount,stopReasonCount,cutoverItems,readyBatchIds,blockedBatchIds,closureWaves,envKeys,missingArtifacts,contentBlockers,firstCommand,rerunCommands,stopReasons",
);
assert.match(releaseFinalOwnerQueueMarkdown, /^# DDD Final Owner Queue/m);
assert.match(releaseFinalOwnerQueueMarkdown, /## Fast Path/);
assert.match(releaseFinalOwnerQueueMarkdown, /Release env file required: true/);
assert.match(releaseFinalOwnerQueueMarkdown, /## release-performance/);
assert.match(releaseFinalOwnerQueueMarkdown, /Waiting owners: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Unique missing artifacts: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Unique content blockers: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Next executable owner: \S+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Next executable command: .+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Queue order: ACTIONABLE owners first, then WAITING owners\./);
assert.match(releaseFinalOwnerQueueMarkdown, /## Safety Signals/);
assert.match(releaseFinalOwnerQueueMarkdown, /releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true/);
assert.match(releaseFinalOwnerQueueMarkdown, /securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false/);
assert.match(releaseFinalOwnerQueueMarkdown, /Queue order: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Execution order hint: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Can execute: true/);
assert.match(releaseFinalOwnerQueueMarkdown, /Missing artifacts: \d+/);
assert.match(releaseFinalOwnerQueueMarkdown, /Content blockers: \d+/);
assert(releaseFinalOwnerQueue.ownerQueues.some((owner) => owner.owner === "release-performance" && owner.envKeys.includes("DDD_AUTH_PERF_BASELINE_ACCEPTED_BY")));
assert.match(releaseFinalOwnerQueueCommands, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_EXECUTE"));
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_OWNER"));
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_CHECK_ENV"));
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR"));
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES=0"));
assert(releaseFinalOwnerQueueCommands.includes("[ddd-final-owner-queue][env-missing]"));
assert(releaseFinalOwnerQueueCommands.includes("[ddd-final-owner-queue][dry-run]"));
assert(releaseFinalOwnerQueueCommands.includes("[ddd-final-owner-queue][command-failed] owner=${owner} status=${status} command=${command}"));
assert(releaseFinalOwnerQueueCommands.includes("continuing because DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=${DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR}"));
assert(releaseFinalOwnerQueueCommands.includes("[ddd-final-owner-queue][completed-with-failures] commandFailures=${DDD_FINAL_OWNER_QUEUE_COMMAND_FAILURES}"));
assert(releaseFinalOwnerQueueCommands.includes("env_file_has_owner_queue_key"));
assert(releaseFinalOwnerQueueCommands.includes("node --input-type=module -e"));
assert(releaseFinalOwnerQueueCommands.includes("release-final-owner-queue-env.template.env"));
assert(releaseFinalOwnerQueueCommands.includes("Release env file permissions are too broad"));
assert(releaseFinalOwnerQueueCommands.includes("DDD_FINAL_OWNER_QUEUE_REPORT"));
assert(releaseFinalOwnerQueueCommands.includes("append_owner_queue_report_entry"));
assert(releaseFinalOwnerQueueCommands.includes("expected 6 legacy args or 8 indexed args"));
assert(releaseFinalOwnerQueueCommands.includes("elif [[ \"$#\" -eq 6 ]]; then"));
assert(releaseFinalOwnerQueueCommands.includes("commandIndex: Number(commandIndex)"));
assert(releaseFinalOwnerQueueCommands.includes("commandCount: Number(commandCount)"));
assert(releaseFinalOwnerQueueCommands.includes("finalize_owner_queue_report"));
assert(releaseFinalOwnerQueueCommands.includes("ddd-final-owner-queue-run-report-contract.mjs"));
assert(releaseFinalOwnerQueueCommands.includes("safe_load_release_env_file"));
assert.doesNotMatch(releaseFinalOwnerQueueCommands, /^\s*source "\$\{DDD_RELEASE_ENV_FILE\}"/m);
const releaseFinalOwnerQueueCommandsSyntax = spawnSync("bash", ["-n", releaseFinalOwnerQueueCommandsPath], { encoding: "utf8" });
assert.equal(releaseFinalOwnerQueueCommandsSyntax.status, 0, releaseFinalOwnerQueueCommandsSyntax.stderr);
const releaseFinalOwnerQueueCommandsList = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env },
});
assert.equal(releaseFinalOwnerQueueCommandsList.status, 0, releaseFinalOwnerQueueCommandsList.stderr);
assert.match(releaseFinalOwnerQueueCommandsList.stdout, /Final owner queue:/);
assert.match(releaseFinalOwnerQueueCommandsList.stdout, /order=\d+ owner=release-performance status=ACTIONABLE/);
const releaseFinalOwnerQueueCommandsDetail = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env, DDD_FINAL_OWNER_QUEUE_DETAIL: "1", DDD_FINAL_OWNER_QUEUE_OWNER: "release-performance" },
});
assert.equal(releaseFinalOwnerQueueCommandsDetail.status, 0, releaseFinalOwnerQueueCommandsDetail.stderr);
assert.match(releaseFinalOwnerQueueCommandsDetail.stdout, /order=\d+ owner=release-performance status=ACTIONABLE/);
assert.match(releaseFinalOwnerQueueCommandsDetail.stdout, /envKeys:/);
assert.match(releaseFinalOwnerQueueCommandsDetail.stdout, /missingArtifacts:/);
assert.match(releaseFinalOwnerQueueCommandsDetail.stdout, /contentBlockers:/);
assert.match(releaseFinalOwnerQueueCommandsDetail.stdout, /authenticated-runtime-baseline\.json/);
const releaseFinalOwnerQueueWaitingDetail = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_DETAIL: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "frontend",
    DDD_FINAL_OWNER_QUEUE_STATUS: "WAITING",
  },
});
assert.equal(releaseFinalOwnerQueueWaitingDetail.status, 0, releaseFinalOwnerQueueWaitingDetail.stderr);
assert.match(releaseFinalOwnerQueueWaitingDetail.stdout, /order=\d+ owner=frontend status=WAITING/);
assert.match(releaseFinalOwnerQueueWaitingDetail.stdout, /frontend-smoke\.json/);
const releaseFinalOwnerQueueCommandsMissingEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: { ...process.env, DDD_FINAL_OWNER_QUEUE_CHECK_ENV: "1", DDD_FINAL_OWNER_QUEUE_OWNER: "release-performance" },
});
assert.notEqual(releaseFinalOwnerQueueCommandsMissingEnv.status, 0);
assert.match(releaseFinalOwnerQueueCommandsMissingEnv.stderr, /DDD_RELEASE_ENV_FILE is required when executing or checking final owner queue env/);
const releasePerformanceOwnerQueue = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.owner === "release-performance");
assert(releasePerformanceOwnerQueue);
const frontendOwnerQueue = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.owner === "frontend");
assert(frontendOwnerQueue);
const releaseFinalOwnerQueueStaticEnvFile = path.join(artifactRoot, "release", "final-owner-queue-static.env");
fs.writeFileSync(
  releaseFinalOwnerQueueStaticEnvFile,
  [
    "SHOULD_NOT_SOURCE_FINAL_OWNER_QUEUE_ENV=filled",
    ...releasePerformanceOwnerQueue.envKeys.map((key) => `${key}=filled`),
    ...frontendOwnerQueue.envKeys.map((key) => `${key}=filled`),
    "",
  ].join("\n"),
);
fs.chmodSync(releaseFinalOwnerQueueStaticEnvFile, 0o600);
const releaseFinalOwnerQueueCommandsStaticEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_CHECK_ENV: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "release-performance",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueStaticEnvFile,
  },
});
if (process.platform === "win32") {
  assert.notEqual(releaseFinalOwnerQueueCommandsStaticEnv.status, 0);
  assert.match(releaseFinalOwnerQueueCommandsStaticEnv.stderr, /Release env file permissions are too broad:/);
} else {
  assert.equal(releaseFinalOwnerQueueCommandsStaticEnv.status, 0, releaseFinalOwnerQueueCommandsStaticEnv.stderr);
  assert.match(releaseFinalOwnerQueueCommandsStaticEnv.stdout, /\[ddd-final-owner-queue\]\[env-ok\]/);
  assert.doesNotMatch(releaseFinalOwnerQueueCommandsStaticEnv.stderr, /SHOULD_NOT_SOURCE_FINAL_OWNER_QUEUE_ENV/);
}
const releaseFinalOwnerQueueCommandsWaitingStaticEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_CHECK_ENV: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "frontend",
    DDD_FINAL_OWNER_QUEUE_STATUS: "WAITING",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueStaticEnvFile,
  },
});
if (process.platform === "win32") {
  assert.notEqual(releaseFinalOwnerQueueCommandsWaitingStaticEnv.status, 0);
  assert.match(releaseFinalOwnerQueueCommandsWaitingStaticEnv.stderr, /Release env file permissions are too broad:/);
} else {
  assert.equal(releaseFinalOwnerQueueCommandsWaitingStaticEnv.status, 0, releaseFinalOwnerQueueCommandsWaitingStaticEnv.stderr);
  assert.match(releaseFinalOwnerQueueCommandsWaitingStaticEnv.stdout, /owner=frontend status=WAITING/);
  assert.match(releaseFinalOwnerQueueCommandsWaitingStaticEnv.stdout, /\[ddd-final-owner-queue\]\[env-ok\]/);
}
const releaseFinalOwnerQueueBroadEnvFile = path.join(artifactRoot, "release", "final-owner-queue-broad.env");
fs.writeFileSync(
  releaseFinalOwnerQueueBroadEnvFile,
  [
    ...releasePerformanceOwnerQueue.envKeys.map((key) => `${key}=filled`),
    "",
  ].join("\n"),
);
fs.chmodSync(releaseFinalOwnerQueueBroadEnvFile, 0o644);
const releaseFinalOwnerQueueCommandsBroadEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_CHECK_ENV: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "release-performance",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueBroadEnvFile,
  },
});
assert.notEqual(releaseFinalOwnerQueueCommandsBroadEnv.status, 0);
assert.match(releaseFinalOwnerQueueCommandsBroadEnv.stderr, /Release env file permissions are too broad/);
const releaseFinalOwnerQueueExecuteEnvFile = path.join(artifactRoot, "release", "final-owner-queue-execute.env");
fs.writeFileSync(releaseFinalOwnerQueueExecuteEnvFile, "DDD_TEST_OWNER_QUEUE_EXECUTE_ENV=filled\n");
fs.chmodSync(releaseFinalOwnerQueueExecuteEnvFile, 0o600);
const releaseFinalOwnerQueueRunReportPath = path.join(artifactRoot, "release", "final-owner-queue-run-report.json");
const releaseFinalOwnerQueueCommandsExecuteNoMatch = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_EXECUTE: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "not-a-real-owner",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueExecuteEnvFile,
    DDD_FINAL_OWNER_QUEUE_REPORT: releaseFinalOwnerQueueRunReportPath,
  },
});
assert.notEqual(releaseFinalOwnerQueueCommandsExecuteNoMatch.status, 0);
if (process.platform === "win32") {
  assert.match(releaseFinalOwnerQueueCommandsExecuteNoMatch.stderr, /Release env file permissions are too broad:/);
} else {
  assert.match(releaseFinalOwnerQueueCommandsExecuteNoMatch.stdout, /\[ddd-final-owner-queue\]\[report\]/);
  assert.match(releaseFinalOwnerQueueCommandsExecuteNoMatch.stdout, /ddd-final-owner-queue-run-report-contract\] ok/);
  const releaseFinalOwnerQueueRunReport = JSON.parse(fs.readFileSync(releaseFinalOwnerQueueRunReportPath, "utf8"));
  assert.equal(releaseFinalOwnerQueueRunReport.reportStatus, "FAIL");
  assert.equal(releaseFinalOwnerQueueRunReport.exitCode, releaseFinalOwnerQueueCommandsExecuteNoMatch.status);
  assert.equal(releaseFinalOwnerQueueRunReport.ownerFilter, "not-a-real-owner");
  assert.deepEqual(releaseFinalOwnerQueueRunReport.summary, {
    totalEntries: 0,
    succeededEntries: 0,
    failedEntries: 0,
  });
  assert.deepEqual(releaseFinalOwnerQueueRunReport.entries, []);
}
const releaseFinalOwnerQueueUnsafeEnvFile = path.join(artifactRoot, "release", "final-owner-queue-unsafe.env");
const releaseFinalOwnerQueueUnsafeReportPath = path.join(artifactRoot, "release", "final-owner-queue-unsafe-run-report.json");
fs.writeFileSync(
  releaseFinalOwnerQueueUnsafeEnvFile,
  [
    "DDD_TEST_OWNER_QUEUE_EXECUTE_ENV=filled",
    "echo SHOULD_NOT_SOURCE_FINAL_OWNER_QUEUE_ENV >&2",
    "",
  ].join("\n"),
);
fs.chmodSync(releaseFinalOwnerQueueUnsafeEnvFile, 0o600);
const releaseFinalOwnerQueueCommandsUnsafeEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_EXECUTE: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "not-a-real-owner",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueUnsafeEnvFile,
    DDD_FINAL_OWNER_QUEUE_REPORT: releaseFinalOwnerQueueUnsafeReportPath,
  },
});
assert.notEqual(releaseFinalOwnerQueueCommandsUnsafeEnv.status, 0);
assert.match(
  releaseFinalOwnerQueueCommandsUnsafeEnv.stderr,
  process.platform === "win32" ? /Release env file permissions are too broad:/ : /\[ddd-release-env\]\[env-invalid\] line=2/,
);
assert.doesNotMatch(releaseFinalOwnerQueueCommandsUnsafeEnv.stderr, /SHOULD_NOT_SOURCE_FINAL_OWNER_QUEUE_ENV/);
const releaseFinalOwnerQueueWaitingRunReportPath = path.join(artifactRoot, "release", "final-owner-queue-waiting-run-report.json");
const releaseFinalOwnerQueueCommandsExecuteWaiting = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_EXECUTE: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "frontend",
    DDD_FINAL_OWNER_QUEUE_STATUS: "WAITING",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueueStaticEnvFile,
    DDD_FINAL_OWNER_QUEUE_REPORT: releaseFinalOwnerQueueWaitingRunReportPath,
  },
});
assert.notEqual(releaseFinalOwnerQueueCommandsExecuteWaiting.status, 0);
if (process.platform === "win32") {
  assert.match(releaseFinalOwnerQueueCommandsExecuteWaiting.stderr, /Release env file permissions are too broad:/);
} else {
  assert.match(releaseFinalOwnerQueueCommandsExecuteWaiting.stderr, /\[ddd-final-owner-queue\]\[blocked\] owner=frontend status=WAITING/);
  assert.match(releaseFinalOwnerQueueCommandsExecuteWaiting.stdout, /\[ddd-final-owner-queue\]\[report\]/);
  const releaseFinalOwnerQueueWaitingRunReport = JSON.parse(fs.readFileSync(releaseFinalOwnerQueueWaitingRunReportPath, "utf8"));
  assert.equal(releaseFinalOwnerQueueWaitingRunReport.reportStatus, "FAIL");
  assert.equal(releaseFinalOwnerQueueWaitingRunReport.ownerFilter, "frontend");
  assert.equal(releaseFinalOwnerQueueWaitingRunReport.statusFilter, "WAITING");
  assert.deepEqual(releaseFinalOwnerQueueWaitingRunReport.summary, {
    totalEntries: 0,
    succeededEntries: 0,
    failedEntries: 0,
  });
}
const releaseFinalOwnerQueuePlaceholderEnvFile = path.join(artifactRoot, "release", "final-owner-queue-placeholder.env");
fs.writeFileSync(
  releaseFinalOwnerQueuePlaceholderEnvFile,
  [
    ...releasePerformanceOwnerQueue.envKeys.map((key) => `${key}=${key === "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY" ? "__REQUIRED__" : "filled"}`),
    "",
  ].join("\n"),
);
fs.chmodSync(releaseFinalOwnerQueuePlaceholderEnvFile, 0o600);
const releaseFinalOwnerQueueCommandsPlaceholderEnv = spawnSync("bash", [releaseFinalOwnerQueueCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_CHECK_ENV: "1",
    DDD_FINAL_OWNER_QUEUE_OWNER: "release-performance",
    DDD_RELEASE_ENV_FILE: releaseFinalOwnerQueuePlaceholderEnvFile,
  },
});
assert.notEqual(releaseFinalOwnerQueueCommandsPlaceholderEnv.status, 0);
assert.match(
  releaseFinalOwnerQueueCommandsPlaceholderEnv.stderr,
  process.platform === "win32" ? /Release env file permissions are too broad:/ : /key=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY/,
);
assert.match(releaseFinalOwnerQueueEnvTemplate, /^# Lumira DDD final owner queue environment template\./m);
assert.match(releaseFinalOwnerQueueEnvTemplate, /# Owner: release-performance/);
assert.match(releaseFinalOwnerQueueEnvTemplate, /# Queue order: \d+/);
assert.match(releaseFinalOwnerQueueEnvTemplate, /# Can execute: true/);
assert.match(releaseFinalOwnerQueueEnvTemplate, /DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=__REQUIRED__/);
assert.match(releaseFinalOwnerQueueEnvTemplate, /# Safe default: non-secret release automation value; override if your environment differs\./);
assert.match(releaseFinalOwnerQueueEnvTemplate, /^DDD_EXPLAIN_STRICT=true$/m);
const releaseFinalOwnerQueueNextOwner = releaseFinalOwnerQueue.ownerQueues.find((owner) => owner.canExecute === true)?.owner;
assert(releaseFinalOwnerQueueNextOwner);
assert.match(
  releaseFinalOwnerQueueEnvTemplate,
  new RegExp(`# DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1 DDD_FINAL_OWNER_QUEUE_OWNER=${releaseFinalOwnerQueueNextOwner}`),
);
assert.match(
  releaseFinalOwnerQueueEnvTemplate,
  new RegExp(`# DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1 DDD_FINAL_OWNER_QUEUE_EXECUTE=1 DDD_FINAL_OWNER_QUEUE_OWNER=${releaseFinalOwnerQueueNextOwner}`),
);
const releaseFinalOwnerQueueEnvTemplateKeys = [...releaseFinalOwnerQueueEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);
const releaseFinalOwnerQueueSafeDefaultKeys = [...releaseFinalOwnerQueueEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=(?!__REQUIRED__)([^#\n]+)$/gm)]
  .filter((match) => releaseFinalOwnerQueue.ownerQueues.some((owner) => owner.envKeys.includes(match[1])))
  .map((match) => match[1]);
assert.equal(new Set(releaseFinalOwnerQueueEnvTemplateKeys).size, releaseFinalOwnerQueueEnvTemplateKeys.length);
assert.equal(new Set(releaseFinalOwnerQueueSafeDefaultKeys).size, releaseFinalOwnerQueueSafeDefaultKeys.length);
assert(releaseFinalOwnerQueueSafeDefaultKeys.includes("DDD_EXPLAIN_STRICT"));
assert.deepEqual(
  [...releaseFinalOwnerQueueEnvTemplateKeys, ...releaseFinalOwnerQueueSafeDefaultKeys].sort(),
  [...new Set(releaseFinalOwnerQueue.ownerQueues.flatMap((owner) => owner.envKeys))].sort(),
);
assert.match(releaseFinalOwnerQueueEnvInit, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert.match(releaseFinalOwnerQueueEnvInit, /DDD_FINAL_OWNER_QUEUE_ENV_TARGET/);
assert.match(releaseFinalOwnerQueueEnvInit, /DDD_FINAL_OWNER_QUEUE_ENV_FORCE/);
assert.match(releaseFinalOwnerQueueEnvInit, /DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT/);
assert.match(releaseFinalOwnerQueueEnvInit, /chmod 600/);
assert.match(releaseFinalOwnerQueueEnvInit, /Refusing to use a generated template as the populated release env target/);
const releaseFinalOwnerQueueEnvInitSyntax = spawnSync("bash", ["-n", releaseFinalOwnerQueueEnvInitPath], { encoding: "utf8" });
assert.equal(releaseFinalOwnerQueueEnvInitSyntax.status, 0, releaseFinalOwnerQueueEnvInitSyntax.stderr);
const releaseFinalOwnerQueueEnvInitTarget = path.join(artifactRoot, "release", ".env.release.local");
const releaseFinalOwnerQueueEnvInitReceipt = path.join(artifactRoot, "release", "release-final-owner-queue-env-init-receipt.json");
const releaseFinalOwnerQueueEnvInitRun = spawnSync("bash", [releaseFinalOwnerQueueEnvInitPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE: path.join(artifactRoot, "release/release-final-owner-queue-env.template.env"),
    DDD_FINAL_OWNER_QUEUE_ENV_TARGET: releaseFinalOwnerQueueEnvInitTarget,
    DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT: releaseFinalOwnerQueueEnvInitReceipt,
  },
});
assert.equal(releaseFinalOwnerQueueEnvInitRun.status, 0, releaseFinalOwnerQueueEnvInitRun.stderr);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /\[ddd-final-owner-queue\]\[env-init\]/);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /release-final-owner-queue-env-init-receipt\.json/);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /bash artifacts\/ddd\/release\/release-artifact-integrity-gate\.sh/);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /node scripts\/ddd-release-env-file-lint\.mjs/);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts\/ddd\/release\/release-env-readiness-gate\.sh/);
assert.match(
  releaseFinalOwnerQueueEnvInitRun.stdout,
  new RegExp(`DDD_FINAL_OWNER_QUEUE_OWNER=${releaseFinalOwnerQueueNextOwner}`),
);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /node scripts\/ddd-release-readiness-summary\.mjs/);
assert.match(releaseFinalOwnerQueueEnvInitRun.stdout, /DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh/);
const releaseFinalOwnerQueueEnvInitTargetText = fs.readFileSync(releaseFinalOwnerQueueEnvInitTarget, "utf8");
const releaseFinalOwnerQueueTemplateHasEnvFileKey = releaseFinalOwnerQueueEnvTemplateKeys.includes("DDD_RELEASE_ENV_FILE");
if (releaseFinalOwnerQueueTemplateHasEnvFileKey) {
  assert.notEqual(releaseFinalOwnerQueueEnvInitTargetText, releaseFinalOwnerQueueEnvTemplate);
  assert.match(releaseFinalOwnerQueueEnvInitTargetText, new RegExp(`^DDD_RELEASE_ENV_FILE=${releaseFinalOwnerQueueEnvInitTarget.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`, "m"));
} else {
  assert.equal(releaseFinalOwnerQueueEnvInitTargetText, releaseFinalOwnerQueueEnvTemplate);
}
if (process.platform !== "win32") {
  assert.equal((fs.statSync(releaseFinalOwnerQueueEnvInitTarget).mode & 0o777), 0o600);
}
const releaseFinalOwnerQueueEnvInitReceiptJson = JSON.parse(fs.readFileSync(releaseFinalOwnerQueueEnvInitReceipt, "utf8"));
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.targetPath, releaseFinalOwnerQueueEnvInitTarget);
if (process.platform === "win32") {
  assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.targetModeOctal, "666");
  assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.permissionSafe, false);
} else {
  assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.targetModeOctal, "600");
  assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.permissionSafe, true);
}
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.dynamicDefaultKeyCount, releaseFinalOwnerQueueTemplateHasEnvFileKey ? 1 : 0);
assert.deepEqual(releaseFinalOwnerQueueEnvInitReceiptJson.dynamicDefaultKeys, releaseFinalOwnerQueueTemplateHasEnvFileKey ? ["DDD_RELEASE_ENV_FILE"] : []);
const releaseFinalOwnerQueueEnvInitExpectedUnresolvedKeys = releaseFinalOwnerQueueEnvTemplateKeys.filter((key) => key !== "DDD_RELEASE_ENV_FILE");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.unresolvedTemplateKeyCount, releaseFinalOwnerQueueEnvInitExpectedUnresolvedKeys.length);
assert.deepEqual(releaseFinalOwnerQueueEnvInitReceiptJson.unresolvedTemplateKeys.sort(), releaseFinalOwnerQueueEnvInitExpectedUnresolvedKeys.slice().sort());
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.safeDefaultKeyCount, releaseFinalOwnerQueueSafeDefaultKeys.length);
assert.deepEqual(releaseFinalOwnerQueueEnvInitReceiptJson.safeDefaultKeys.sort(), releaseFinalOwnerQueueSafeDefaultKeys.slice().sort());
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.artifactIntegrityGateCommand, "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.artifactIntegrityArtifact, "artifacts/ddd/release/release-artifact-integrity.json");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.artifactIntegrityMarkdown, "artifacts/ddd/release/release-artifact-integrity.md");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.provenanceDefaultsCommand, "node scripts/ddd-release-provenance-defaults.mjs");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.provenanceDefaultsArtifact, "artifacts/ddd/release/release-provenance-defaults.json");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.finalGoNoGoGateCommand, "DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.finalGoNoGoPacket, "artifacts/ddd/release/release-final-go-no-go.json");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.finalGoNoGoMarkdown, "artifacts/ddd/release/release-final-go-no-go.md");
assert(releaseFinalOwnerQueueEnvInitRun.stdout.includes("ddd-release-env-owner-templates-merge.mjs"));
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[0], "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[1].includes("ddd-release-env-owner-templates-merge.mjs"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[2].includes("ddd-release-env-canonical-merge.mjs"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[3].includes("ddd-release-env-safe-defaults.mjs"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[4].includes("ddd-release-provenance-defaults.mjs"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands[5].includes("ddd-release-env-alias-sync.mjs"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.some((command) => command.includes("ddd-release-env-safe-defaults.mjs")));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.some((command) => command.includes("ddd-release-provenance-defaults.mjs")));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.some((command) => command.includes("ddd-release-env-file-lint.mjs")));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.some((command) => command.includes("ddd-release-env-canonical-lint.mjs")));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.includes("DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh"));
assert(releaseFinalOwnerQueueEnvInitReceiptJson.nextCommands.includes("DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh"));
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.envReadinessGateCommand, "DDD_RELEASE_ENV_READINESS_ENFORCE=1 bash artifacts/ddd/release/release-env-readiness-gate.sh");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.envReadinessArtifact, "artifacts/ddd/release/release-env-readiness-redacted.json");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.envReadinessCsv, "artifacts/ddd/release/release-env-readiness-redacted.csv");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.ownerHandoffArtifact, "artifacts/ddd/release/release-env-owner-handoff-redacted.json");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.ownerHandoffCsv, "artifacts/ddd/release/release-env-owner-handoff-redacted.csv");
assert.equal(releaseFinalOwnerQueueEnvInitReceiptJson.ownerHandoffDir, "artifacts/ddd/release/release-env-owner-handoff-redacted");
assert(!JSON.stringify(releaseFinalOwnerQueueEnvInitReceiptJson).includes("__REQUIRED__"));
const releaseFinalOwnerQueueEnvInitNoOverwrite = spawnSync("bash", [releaseFinalOwnerQueueEnvInitPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE: path.join(artifactRoot, "release/release-final-owner-queue-env.template.env"),
    DDD_FINAL_OWNER_QUEUE_ENV_TARGET: releaseFinalOwnerQueueEnvInitTarget,
  },
});
assert.notEqual(releaseFinalOwnerQueueEnvInitNoOverwrite.status, 0);
assert.match(releaseFinalOwnerQueueEnvInitNoOverwrite.stderr, /Release env target already exists/);
for (const generatedTemplateTarget of [
  "release-final-owner-queue-env.template.env",
  "release-env-missing.template.env",
  "release-closure-wave-env.template.env",
  "release-env-canonical-fill.template.env",
]) {
  const releaseFinalOwnerQueueEnvInitRejectTemplateTarget = spawnSync("bash", [releaseFinalOwnerQueueEnvInitPath], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE: path.join(artifactRoot, "release/release-final-owner-queue-env.template.env"),
      DDD_FINAL_OWNER_QUEUE_ENV_TARGET: path.join(artifactRoot, "release", generatedTemplateTarget),
      DDD_FINAL_OWNER_QUEUE_ENV_FORCE: "1",
    },
  });
  assert.notEqual(releaseFinalOwnerQueueEnvInitRejectTemplateTarget.status, 0);
  assert.match(releaseFinalOwnerQueueEnvInitRejectTemplateTarget.stderr, /Refusing to use a generated template as the populated release env target/);
}
const releasePerformanceBaselineCommandsSyntax = spawnSync("bash", ["-n", releasePerformanceBaselineCommandsPath], { encoding: "utf8" });
assert.equal(releasePerformanceBaselineCommandsSyntax.status, 0, releasePerformanceBaselineCommandsSyntax.stderr);
const releasePerformanceBaselineCommandsDetail = spawnSync("bash", [releasePerformanceBaselineCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_AUTH_PERF_BASELINE_DETAIL: "1",
  },
});
assert.equal(releasePerformanceBaselineCommandsDetail.status, 0, releasePerformanceBaselineCommandsDetail.stderr);
assert.match(releasePerformanceBaselineCommandsDetail.stdout, /readyToPromote=/);
assert.match(releasePerformanceBaselineCommandsDetail.stdout, /commands:/);
assert.match(releasePerformanceBaselineCommandsDetail.stdout, /DDD_AUTH_PERF_BASELINE_CHECK_ENV=1 bash artifacts\/ddd\/release\/release-performance-baseline-commands\.sh/);
const releasePerformanceBaselineCommandsMissingEnv = spawnSync("bash", [releasePerformanceBaselineCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_AUTH_PERF_BASELINE_CHECK_ENV: "1",
  },
});
assert.notEqual(releasePerformanceBaselineCommandsMissingEnv.status, 0);
assert.match(releasePerformanceBaselineCommandsMissingEnv.stderr, /DDD_RELEASE_ENV_FILE is required when executing or checking performance baseline env/);
const releasePerformanceBaselineBroadEnvFile = path.join(artifactRoot, "release", "performance-baseline-broad.env");
fs.writeFileSync(releasePerformanceBaselineBroadEnvFile, "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=release-owner\n");
fs.chmodSync(releasePerformanceBaselineBroadEnvFile, 0o644);
const releasePerformanceBaselineCommandsBroadEnv = spawnSync("bash", [releasePerformanceBaselineCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_AUTH_PERF_BASELINE_CHECK_ENV: "1",
    DDD_RELEASE_ENV_FILE: releasePerformanceBaselineBroadEnvFile,
  },
});
assert.notEqual(releasePerformanceBaselineCommandsBroadEnv.status, 0);
assert.match(releasePerformanceBaselineCommandsBroadEnv.stderr, /Release env file permissions are too broad:/);
const releasePerformanceBaselinePlaceholderEnvFile = path.join(artifactRoot, "release", "performance-baseline-placeholder.env");
fs.writeFileSync(releasePerformanceBaselinePlaceholderEnvFile, [
  "BASE_URL=__REQUIRED__",
  "DDD_AUTH_PASSWORD=__REQUIRED__",
  "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=__REQUIRED__",
  "DDD_AUTH_PERF_BASELINE_ENVIRONMENT=__REQUIRED__",
  "DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=__REQUIRED__",
  "DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=__REQUIRED__",
  "DDD_AUTH_PERF_ENVIRONMENT=__REQUIRED__",
  "DDD_AUTH_USERNAME=__REQUIRED__",
  "DDD_EVIDENCE_OPERATOR=__REQUIRED__",
  "DDD_RELEASE_CANDIDATE=__REQUIRED__",
  "DEPLOY_CHECK_BASE_URL=__REQUIRED__",
  "LUMIRA_BASE_URL=__REQUIRED__",
  "",
].join("\n"));
fs.chmodSync(releasePerformanceBaselinePlaceholderEnvFile, 0o600);
const releasePerformanceBaselineCommandsPlaceholderEnv = spawnSync("bash", [releasePerformanceBaselineCommandsPath], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_AUTH_PERF_BASELINE_CHECK_ENV: "1",
    DDD_RELEASE_ENV_FILE: releasePerformanceBaselinePlaceholderEnvFile,
  },
});
assert.notEqual(releasePerformanceBaselineCommandsPlaceholderEnv.status, 0);
assert.match(
  releasePerformanceBaselineCommandsPlaceholderEnv.stderr,
  process.platform === "win32" ? /Release env file permissions are too broad:/ : /\[ddd-auth-perf-baseline\]\[env-placeholder\] key=BASE_URL/,
);
assert.match(releasePriorityMarkdown, /^# DDD Release Action Priority/m);
assert.match(releasePriorityMarkdown, /Total pending items: \d+/);
assert.match(releasePriorityMarkdown, /\[P0\] \[release-env-lint\] release-infra: release-env-lint-placeholders/);
assert.match(releasePriorityMarkdown, /\[P0\] \[release-config\] ai-owner/);
assert.match(releasePriorityMarkdown, /\[P0\] \[docker\] release-infra: docker-daemon/);
assert.match(releasePriorityMarkdown, /\[P2\] \[explain\] database: message-visible-list\.json/);
assert.match(releaseBatchesMarkdown, /^# DDD Release Action Batches/m);
assert.match(releaseBatchesMarkdown, /Batch `id`, `dependsOn`, and `canRunImmediately` define the machine-readable execution graph/);
assert.match(releaseBatchesMarkdown, /current release gate remains authoritative after every batch; strict mode is required for final release approval/);
assert.match(releaseBatchesMarkdown, /1\. P0 release-env-lint -> release-infra/);
assert.match(releaseBatchesMarkdown, /- Batch id: p0-release-env-lint-release-infra/);
assert.match(releaseBatchesMarkdown, /- Depends on: none/);
assert.match(releaseBatchesMarkdown, /- Can run immediately: true/);
assert.match(releaseBatchesMarkdown, /P1 ai-runtime -> ai[\s\S]*- Depends on: p0-release-env-lint-release-infra/);
assert.match(releaseBatchesMarkdown, /P1 ai-runtime -> ai[\s\S]*- Can run immediately: false/);
assert.match(releaseBatchesMarkdown, /- Env keys: \d+ keys\n  - [A-Z0-9_]+, [A-Z0-9_]+/);
assert.match(releaseBatchesMarkdown, /- Env check groups: \d+ groups[\s\S]*  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL\|LUMIRA_BASE_URL`/);
assert.match(releaseBatchesMarkdown, /- Commands:\n  - `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts\/ddd-release-env-file-lint\.mjs`\n  - `node scripts\/ddd-release-config-evidence\.mjs`/);
assert.doesNotMatch(JSON.stringify(releaseBatches), /DDD_RELEASE_ENV_FILE=(?!<release-env-file>)/);
assert.match(releaseBatchesMarkdown, /- Expected artifacts:\n  - `artifacts\/ddd\/release\/release-env-lint\.json`\n  - `artifacts\/ddd\/config\/release-config-evidence\.json`/);
assert.match(releaseBatchesMarkdown, /- Exit criteria:\n  - Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing\.template\.env\./);
assert.match(releaseBatchesMarkdown, /P0 release-config -> ai-owner/);
assert.match(releaseBatchesMarkdown, /P0 docker -> release-infra/);
assert.match(releaseBatchesMarkdown, /node scripts\/ddd-authenticated-performance-smoke\.mjs/);
assert.match(releaseBatchesMarkdown, /node scripts\/ddd-frontend-playwright-smoke\.mjs/);
assert.match(releaseBatchesMarkdown, /DDD_RELEASE_EVIDENCE_STRICT=true node scripts\/ddd-release-evidence-orchestrator\.mjs --run --strict/);
assert.match(releaseDependencyGraphMarkdown, /^# DDD Release Action Dependency Graph/m);
assert.match(releaseDependencyGraphMarkdown, /Graph density: \d+\.\d+/);
assert.match(releaseDependencyGraphMarkdown, /Compressed edge count: \d+/);
assert.match(releaseDependencyGraphMarkdown, /## Execution Levels/);
assert.match(releaseDependencyGraphMarkdown, /- P0: \d+ batches, \d+ ready, 0 blocked/);
assert.match(releaseDependencyGraphMarkdown, /## Compressed Graph/);
assert.match(releaseDependencyGraphMarkdown, /p_P0\["P0: \d+ batches \/ \d+ ready \/ 0 blocked"\]/);
assert.match(releaseDependencyGraphMarkdown, /p_P0 --> p_P1/);
assert.match(releaseDependencyGraphMarkdown, /## Full Graph/);
assert.match(releaseDependencyGraphMarkdown, /## Full Graph[\s\S]*```mermaid\nflowchart TD/);
assert.match(releaseDependencyGraphMarkdown, /b_p0_release_env_lint_release_infra\["P0 release-env-lint \/ release-infra"\]/);
assert.match(releaseDependencyGraphMarkdown, /b_p0_release_env_lint_release_infra --> b_p1_ai_runtime_ai/);
assert.match(releaseDependencyGraphMarkdown, /## Ready Batches[\s\S]*p0-release-env-lint-release-infra/);
assert.match(releaseDependencyGraphMarkdown, /## Blocked Batches[\s\S]*p1-ai-runtime-ai: waits for p0-release-env-lint-release-infra/);
assert.match(releaseExecutionQueueMarkdown, /^# DDD Release Execution Queue/m);
assert.deepEqual(releaseExecutionQueue.safetySignals.releaseEnvFile, releaseFastTrack.safetySignals.releaseEnvFile);
assert.equal(releaseExecutionQueue.releaseEnvFileCutoverSafe, false);
assert.match(releaseExecutionQueueMarkdown, /Ready batches: \d+/);
assert.match(releaseExecutionQueueMarkdown, /Blocked batches: \d+/);
assert.match(releaseExecutionQueueMarkdown, /Next priority: P0/);
assert.match(releaseExecutionQueueMarkdown, /## Safety Signals/);
assert.match(releaseExecutionQueueMarkdown, /releaseEnvFileCutoverSafe: false/);
assert.match(releaseExecutionQueueMarkdown, /releaseEnvFile: ready=false status=FAIL inputKind=release-env-file envFilePresent=true/);
assert.match(releaseExecutionQueueMarkdown, /securityChecked=true permissionSafe=true mode=600 requiredMode=600 reason=env-file permissionCheckSkipped=false/);
assert.match(releaseExecutionQueueMarkdown, /## Ready Now[\s\S]*### p0-release-env-lint-release-infra/);
assert.match(releaseExecutionQueueMarkdown, /- Env check groups:\n  - `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL\|LUMIRA_BASE_URL`/);
assert.match(releaseExecutionQueueMarkdown, /### p0-release-env-lint-release-infra[\s\S]*- Commands:\n  - `DDD_RELEASE_ENV_FILE=\.env\.release\.local node scripts\/ddd-release-env-file-lint\.mjs`/);
assert.match(releaseExecutionQueueMarkdown, /### p0-release-env-lint-release-infra[\s\S]*- Expected artifacts:\n  - `artifacts\/ddd\/release\/release-env-lint\.json`/);
assert.match(releaseExecutionQueueMarkdown, /## Blocked Later[\s\S]*p1-ai-runtime-ai: waits for p0-release-env-lint-release-infra/);
const blockedBatchWithArtifacts = releaseExecutionQueue.blockedBatches.find((batch) => batch.expectedArtifacts.length > 0);
assert.ok(blockedBatchWithArtifacts, "fixture should include a blocked execution batch with expected artifacts");
const blockedBatchMarkdownOffset = releaseExecutionQueueMarkdown.indexOf(`- ${blockedBatchWithArtifacts.id}: waits for`);
assert.notEqual(blockedBatchMarkdownOffset, -1, "blocked batch should be rendered in execution queue markdown");
const blockedBatchMarkdown = releaseExecutionQueueMarkdown.slice(blockedBatchMarkdownOffset);
assert.ok(blockedBatchMarkdown.includes("  - Expected artifacts:"));
for (const artifactPath of blockedBatchWithArtifacts.expectedArtifacts) {
  assert.ok(blockedBatchMarkdown.includes(`    - \`${artifactPath}\``));
}
assert.match(releaseExecutionCommands, /^#!\/usr\/bin\/env bash\nset -euo pipefail/m);
assert.match(releaseExecutionCommands, /SCRIPT_DIR=\$\(cd "\$\(dirname "\$\{BASH_SOURCE\[0\]\}"\)" && pwd\)/);
assert.match(releaseExecutionCommands, /if \[\[ -f "scripts\/ddd-release-readiness-summary\.mjs" \]\]; then\n    LUMIRA_REPO_ROOT=\$\(pwd\)/);
assert.match(releaseExecutionCommands, /LUMIRA_REPO_ROOT=\$\(cd "\$\{SCRIPT_DIR\}\/\.\.\/\.\.\/\.\." && pwd\)/);
assert(!releaseExecutionCommands.includes(repoRoot), "release execution commands must not embed the local repo path");
assert.match(releaseExecutionCommands, /DDD_RELEASE_LIST_BATCHES:-/);
assert.match(releaseExecutionCommands, /echo 'p0-release-env-lint-release-infra P0 release-env-lint->release-infra owner=release-infra priority=P0'/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_ENV_FILE is required and must point to a completed release env file/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_ENV_FILE does not exist: \$\{DDD_RELEASE_ENV_FILE\}/);
assert.match(releaseExecutionCommands, /release-env-missing\.template\.env/);
assert.match(releaseExecutionCommands, /release-closure-wave-env\.template\.env/);
assert.match(releaseExecutionCommands, /release-final-owner-queue-env\.template\.env/);
assert.match(releaseExecutionCommands, /Release env file permissions are too broad: \$\{DDD_RELEASE_ENV_FILE\} mode=\$\{DDD_RELEASE_ENV_FILE_MODE\}; use chmod 600\./);
assert(releaseExecutionCommands.includes("safe_load_release_env_file"));
assert.doesNotMatch(releaseExecutionCommands, /^\s*source "\$\{DDD_RELEASE_ENV_FILE\}"/m);
assert.match(releaseExecutionCommands, /DDD_RELEASE_BATCH="\$\{DDD_RELEASE_BATCH:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_OWNER="\$\{DDD_RELEASE_OWNER:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_PRIORITY="\$\{DDD_RELEASE_PRIORITY:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_DRY_RUN="\$\{DDD_RELEASE_DRY_RUN:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_CHECK_ENV_ONLY="\$\{DDD_RELEASE_CHECK_ENV_ONLY:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_ALLOW_MISSING_ENV="\$\{DDD_RELEASE_ALLOW_MISSING_ENV:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_CONTINUE_ON_ERROR="\$\{DDD_RELEASE_CONTINUE_ON_ERROR:-\}"/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_EXECUTION_REPORT="\$\{DDD_RELEASE_EXECUTION_REPORT:-artifacts\/ddd\/release\/release-execution-run-report\.json\}"/);
assert.match(releaseExecutionCommands, /append_release_execution_report_entry\(\) \{/);
assert.match(releaseExecutionCommands, /finalize_release_execution_report\(\) \{/);
assert.match(releaseExecutionCommands, /ddd-release-execution-run-report-contract\.mjs/);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[report-contract\] failed/);
assert.match(releaseExecutionCommands, /trap 'status=\$\?; finalize_release_execution_report "\$\{status\}"; exit "\$\{status\}"' EXIT/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_NEEDS_ENV=1/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_NEEDS_ENV=0/);
assert.match(releaseExecutionCommands, /if \[\[ "\$\{DDD_RELEASE_NEEDS_ENV\}" == "1" \]\]; then/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_COMMAND_FAILURES=0/);
assert.match(releaseExecutionCommands, /print_missing_env_groups\(\) \{/);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[env-check\] \$\{batch_id\} missing env groups/);
assert.match(releaseExecutionCommands, /return 1/);
assert.match(releaseExecutionCommands, /continuing because DDD_RELEASE_ALLOW_MISSING_ENV=\$\{DDD_RELEASE_ALLOW_MISSING_ENV\}/);
assert.match(releaseExecutionCommands, /LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL\|LUMIRA_BASE_URL/);
assert.match(releaseExecutionCommands, /# Env keys: /);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[env-check-only\] skip \$\{command\}/);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[dry-run\] \$\{command\}/);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[command-failed\] status=\$\{status\} command=\$\{command\}/);
assert.match(releaseExecutionCommands, /continuing because DDD_RELEASE_CONTINUE_ON_ERROR=\$\{DDD_RELEASE_CONTINUE_ON_ERROR\}/);
assert.match(releaseExecutionCommands, /\[ddd-release-execution\]\[completed-with-failures\] commandFailures=\$\{DDD_RELEASE_COMMAND_FAILURES\}/);
assert.match(releaseExecutionCommands, /run_batch 'p0-release-env-lint-release-infra' 'release-infra' 'P0'/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_OWNER/);
assert.match(releaseExecutionCommands, /DDD_RELEASE_PRIORITY/);
assert.match(releaseExecutionCommands, /No ready release batch matched DDD_RELEASE_BATCH=\$\{DDD_RELEASE_BATCH\}/);
assert.match(releaseExecutionCommands, /No ready release batch matched DDD_RELEASE_OWNER=\$\{DDD_RELEASE_OWNER\}/);
assert.match(releaseExecutionCommands, /No ready release batch matched DDD_RELEASE_PRIORITY=\$\{DDD_RELEASE_PRIORITY\}/);
assert.match(releaseExecutionCommands, /# p0-release-env-lint-release-infra: P0 release-env-lint -> release-infra/);
assert.match(releaseExecutionCommands, /run_command 'p0-release-env-lint-release-infra' 'release-infra' 'P0' 'DDD_RELEASE_ENV_FILE=\.env\.release\.local node scripts\/ddd-release-env-file-lint\.mjs'/);
assert.match(releaseExecutionCommands, /run_command 'p0-docker-release-infra' 'release-infra' 'P0' 'DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence\.mjs'/);
assert.match(releaseExecutionCommands, /run_command 'p0-manifest-release-performance' 'release-performance' 'P0' 'DDD_RELEASE_MANIFEST_STRICT=true DDD_RELEASE_MANIFEST_EXIT_ON_BLOCKERS=false node scripts\/ddd-release-evidence-manifest\.mjs'/);
assert.doesNotMatch(releaseExecutionCommands, /run_command '.*' '.*' '.*' 'node scripts\/ddd-release-evidence-manifest\.mjs'/);
assert.match(releaseExecutionCommands, /# Expected artifacts: artifacts\/ddd\/release\/release-env-lint\.json; artifacts\/ddd\/config\/release-config-evidence\.json/);
assert.match(
  releaseExecutionCommands,
  /run_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts\/ddd-release-evidence-gate\.mjs'\nrun_command 'release-execution-rerun' 'release-owner' 'RERUN' 'node scripts\/ddd-release-readiness-summary\.mjs'\nrun_command 'release-execution-rerun' 'release-owner' 'RERUN' 'DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts\/ddd\/release\/release-final-go-no-go-gate\.sh'/,
);
const releaseExecutionCommandsSyntax = spawnSync("bash", ["-n", releaseExecutionCommandsPath], { encoding: "utf8" });
assert.equal(releaseExecutionCommandsSyntax.status, 0, releaseExecutionCommandsSyntax.stderr);
const releaseExecutionCommandsList = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_LIST_BATCHES: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsList.status, 0, releaseExecutionCommandsList.stderr);
assert.match(releaseExecutionCommandsList.stdout, /Ready release batches:/);
assert.match(releaseExecutionCommandsList.stdout, /p0-release-env-lint-release-infra P0 release-env-lint->release-infra/);
const releaseExecutionCommandsListOwner = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_LIST_BATCHES: "1",
    DDD_RELEASE_OWNER: "release-infra",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsListOwner.status, 0, releaseExecutionCommandsListOwner.stderr);
assert.match(releaseExecutionCommandsListOwner.stdout, /p0-release-env-lint-release-infra P0 release-env-lint->release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsListOwner.stdout, /p0-docker-release-infra P0 docker->release-infra owner=release-infra priority=P0/);
assert.doesNotMatch(releaseExecutionCommandsListOwner.stdout, /owner=database/);
const releaseExecutionCommandsListPriority = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_LIST_BATCHES: "1",
    DDD_RELEASE_PRIORITY: "P0",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsListPriority.status, 0, releaseExecutionCommandsListPriority.stderr);
assert.match(releaseExecutionCommandsListPriority.stdout, /priority=P0/);
assert.doesNotMatch(releaseExecutionCommandsListPriority.stdout, /priority=P1/);
const releaseExecutionCommandsListUnmatched = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_LIST_BATCHES: "1",
    DDD_RELEASE_OWNER: "not-a-real-owner",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsListUnmatched.status, 0);
assert.match(releaseExecutionCommandsListUnmatched.stderr, /No ready release batches matched/);
const releaseExecutionCommandsMissingEnv = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsMissingEnv.status, 0);
assert.match(releaseExecutionCommandsMissingEnv.stderr, /DDD_RELEASE_ENV_FILE is required/);
const releaseExecutionCommandsDryRunWithoutEnv = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_BATCH: "p0-docker-release-infra",
    DDD_RELEASE_DRY_RUN: "1",
    DDD_RELEASE_ALLOW_MISSING_ENV: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsDryRunWithoutEnv.status, 0, releaseExecutionCommandsDryRunWithoutEnv.stderr);
assert.match(releaseExecutionCommandsDryRunWithoutEnv.stdout, /\[ddd-release-execution\] running p0-docker-release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsDryRunWithoutEnv.stdout, /\[ddd-release-execution\]\[dry-run\] DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(releaseExecutionCommandsDryRunWithoutEnv.stderr, /\[ddd-release-execution\]\[env-check\] p0-docker-release-infra missing env groups:/);
const unmatchedBatchEnvFile = writeBashEnvFixture("valid-release.env", "DDD_RELEASE_EVIDENCE_STRICT=true\n");
const broadModeEnvFile = path.join(artifactRoot, "release", "broad-mode-release.env");
fs.writeFileSync(broadModeEnvFile, "DDD_RELEASE_EVIDENCE_STRICT=true\n");
fs.chmodSync(broadModeEnvFile, 0o644);
const releaseExecutionCommandsBroadModeEnv = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: broadModeEnvFile,
    DDD_RELEASE_BATCH: "p0-does-not-exist",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsBroadModeEnv.status, 0);
assert.match(releaseExecutionCommandsBroadModeEnv.stderr, /Release env file permissions are too broad:/);
const releaseExecutionCommandsUnmatchedBatch = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_BATCH: "p0-does-not-exist",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsUnmatchedBatch.status, 0);
assert.match(releaseExecutionCommandsUnmatchedBatch.stderr, /No ready release batch matched DDD_RELEASE_BATCH=p0-does-not-exist/);
const releaseExecutionCommandsDryRun = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_BATCH: "p0-docker-release-infra",
    DDD_RELEASE_DRY_RUN: "1",
    DDD_RELEASE_ALLOW_MISSING_ENV: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsDryRun.status, 0, releaseExecutionCommandsDryRun.stderr);
assert.match(releaseExecutionCommandsDryRun.stdout, /\[ddd-release-execution\] running p0-docker-release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsDryRun.stdout, /\[ddd-release-execution\]\[dry-run\] DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(releaseExecutionCommandsDryRun.stderr, /\[ddd-release-execution\]\[env-check\] p0-docker-release-infra missing env groups: DDD_DOCKER_BUILD_STRICT\(DDD_DOCKER_BUILD_STRICT\) DDD_DOCKER_COMMAND\(DDD_DOCKER_COMMAND\)/);
assert.match(releaseExecutionCommandsDryRun.stderr, /continuing because DDD_RELEASE_ALLOW_MISSING_ENV=1/);
assert.doesNotMatch(releaseExecutionCommandsDryRun.stdout, /\[ddd-release-execution\] running p0-release-env-lint-release-infra/);
const releaseExecutionCommandsOwnerDryRun = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_OWNER: "release-infra",
    DDD_RELEASE_DRY_RUN: "1",
    DDD_RELEASE_ALLOW_MISSING_ENV: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsOwnerDryRun.status, 0, releaseExecutionCommandsOwnerDryRun.stderr);
assert.match(releaseExecutionCommandsOwnerDryRun.stdout, /\[ddd-release-execution\] running p0-docker-release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsOwnerDryRun.stdout, /\[ddd-release-execution\] running p0-release-env-lint-release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsOwnerDryRun.stdout, /\[ddd-release-execution\] running p0-runtime-readiness-release-infra owner=release-infra priority=P0/);
assert.doesNotMatch(releaseExecutionCommandsOwnerDryRun.stdout, /owner=database/);
const releaseExecutionCommandsPriorityDryRun = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_PRIORITY: "P0",
    DDD_RELEASE_DRY_RUN: "1",
    DDD_RELEASE_ALLOW_MISSING_ENV: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsPriorityDryRun.status, 0, releaseExecutionCommandsPriorityDryRun.stderr);
assert.match(releaseExecutionCommandsPriorityDryRun.stdout, /\[ddd-release-execution\] running p0-release-env-lint-release-infra owner=release-infra priority=P0/);
assert.match(releaseExecutionCommandsPriorityDryRun.stdout, /\[ddd-release-execution\] running p0-migration-database owner=database priority=P0/);
assert.doesNotMatch(releaseExecutionCommandsPriorityDryRun.stdout, /priority=P1/);
const releaseExecutionCommandsUnmatchedPriority = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_PRIORITY: "P1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsUnmatchedPriority.status, 0);
assert.match(releaseExecutionCommandsUnmatchedPriority.stderr, /No ready release batch matched DDD_RELEASE_PRIORITY=P1/);
const releaseExecutionCommandsUnmatchedOwner = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_OWNER: "not-a-real-owner",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsUnmatchedOwner.status, 0);
assert.match(releaseExecutionCommandsUnmatchedOwner.stderr, /No ready release batch matched DDD_RELEASE_OWNER=not-a-real-owner/);
const releaseExecutionCommandsMissingEnvGroup = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_BATCH: "p0-docker-release-infra",
    DDD_RELEASE_CHECK_ENV_ONLY: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.notEqual(releaseExecutionCommandsMissingEnvGroup.status, 0);
assert.match(releaseExecutionCommandsMissingEnvGroup.stderr, /\[ddd-release-execution\]\[env-check\] p0-docker-release-infra missing env groups/);
const releaseExecutionCommandsEnvCheckOnly = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: unmatchedBatchEnvFile,
    DDD_RELEASE_BATCH: "p0-docker-release-infra",
    DDD_RELEASE_CHECK_ENV_ONLY: "1",
    DDD_RELEASE_ALLOW_MISSING_ENV: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsEnvCheckOnly.status, 0, releaseExecutionCommandsEnvCheckOnly.stderr);
assert.match(releaseExecutionCommandsEnvCheckOnly.stdout, /\[ddd-release-execution\] running p0-docker-release-infra/);
assert.match(releaseExecutionCommandsEnvCheckOnly.stdout, /\[ddd-release-execution\]\[env-check-only\] skip DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(releaseExecutionCommandsEnvCheckOnly.stderr, /\[ddd-release-execution\]\[env-check\] p0-docker-release-infra missing env groups: DDD_DOCKER_BUILD_STRICT\(DDD_DOCKER_BUILD_STRICT\) DDD_DOCKER_COMMAND\(DDD_DOCKER_COMMAND\)/);
assert.match(releaseExecutionCommandsEnvCheckOnly.stderr, /continuing because DDD_RELEASE_ALLOW_MISSING_ENV=1/);
const aliasEnvFile = writeBashEnvFixture("alias-release.env", [
  "BASE_URL=https://api.alias.example.test",
  "DEPLOY_CHECK_BASE_URL=https://api.alias.example.test",
  "DDD_DEPLOYMENT_EVIDENCE=change-123",
  "DDD_EVIDENCE_ENVIRONMENT=staging",
  "DDD_EVIDENCE_OPERATOR=release-runner",
  "DDD_RELEASE_CANDIDATE=rc-1",
  "",
].join("\n"));
const releaseExecutionCommandsAliasEnv = spawnSync("bash", [releaseExecutionCommandsPath], {
  cwd: os.tmpdir(),
  encoding: "utf8",
  env: {
    PATH: process.env.PATH,
    DDD_RELEASE_ENV_FILE: aliasEnvFile,
    DDD_RELEASE_BATCH: "p0-runtime-readiness-release-infra",
    DDD_RELEASE_CHECK_ENV_ONLY: "1",
    LUMIRA_REPO_ROOT: repoRoot,
  },
});
assert.equal(releaseExecutionCommandsAliasEnv.status, 0, releaseExecutionCommandsAliasEnv.stderr);
assert.doesNotMatch(releaseExecutionCommandsAliasEnv.stderr, /LUMIRA_BASE_URL\(DEPLOY_CHECK_BASE_URL or LUMIRA_BASE_URL\)/);
assert.match(releaseMissingEnvTemplate, /^# Lumira DDD missing release evidence environment template\./m);
assert.match(releaseMissingEnvTemplate, /delete unused alias placeholder lines/);
assert.match(releaseMissingEnvTemplate, /# Batch id: p0-release-env-lint-release-infra/);
assert.match(releaseMissingEnvTemplate, /# Depends on: none/);
assert.match(releaseMissingEnvTemplate, /# Can run immediately: true/);
assert.match(releaseMissingEnvTemplate, /# Covers: release-env-lint-placeholders, release-env-lint-status/);
assert.match(releaseMissingEnvTemplate, /# Batch id: p1-ai-runtime-ai/);
assert.match(releaseMissingEnvTemplate, /# Depends on: p0-release-env-lint-release-infra/);
assert.match(releaseMissingEnvTemplate, /# Can run immediately: false/);
assert.match(releaseMissingEnvTemplate, /# Expected artifacts: artifacts\/ddd\/release\/release-env-lint\.json; artifacts\/ddd\/config\/release-config-evidence\.json/);
assert.match(releaseMissingEnvTemplate, /# - Use a completed DDD_RELEASE_ENV_FILE, not release-env-missing\.template\.env\./);
assert.match(releaseMissingEnvTemplate, /# Expected artifacts: artifacts\/ddd\/release\/evidence-manifest\.json; artifacts\/ddd\/performance\/authenticated-runtime-baseline\.json; artifacts\/ddd\/performance\/authenticated-runtime-baseline-promotion\.json/);
assert.match(releaseMissingEnvTemplate, /LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY=__REQUIRED__/);
assert.doesNotMatch(releaseMissingEnvTemplate, /^LUMIRA_AI_PROVIDER_API_KEY=__REQUIRED__$/m);
assert.doesNotMatch(releaseMissingEnvTemplate, /^DDD_RELEASE_ENV_FILE=/m);
const releaseMissingEnvTemplateKeys = [...releaseMissingEnvTemplate.matchAll(/^([A-Z][A-Z0-9_]*)=__REQUIRED__$/gm)].map((match) => match[1]);
assert.equal(new Set(releaseMissingEnvTemplateKeys).size, releaseMissingEnvTemplateKeys.length);
assert.deepEqual(
  [...releaseMissingEnvTemplateKeys].sort(),
  [...releaseMissingEnv.templateEnvKeys].sort(),
);
assert.match(releaseMissingEnvTemplate, /# export DDD_RELEASE_ENV_FILE=\/secure\/path\/to\/\.env\.release/);
assert.match(releaseMissingEnvTemplate, /# node scripts\/ddd-release-env-file-lint\.mjs/);
assert.match(releaseMissingEnvTemplate, /# node scripts\/ddd-release-config-evidence\.mjs/);
assert.match(releaseMissingEnvTemplate, /# DDD_RELEASE_CONTINUE_ON_ERROR=1 bash artifacts\/ddd\/release\/release-execution-commands\.sh # diagnostic only; final exit remains non-zero on failures/);
assert.match(releaseMissingEnvTemplate, /# DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=1 DDD_RELEASE_NEXT_ACTION_EXECUTE=1 bash artifacts\/ddd\/release\/release-next-action-commands\.sh # diagnostic only; final exit remains non-zero on failures/);
assert.match(releaseMissingEnvTemplate, /# DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1 DDD_RELEASE_CLOSURE_EXECUTE=1 bash artifacts\/ddd\/release\/release-blocker-closure-commands\.sh # diagnostic only; final exit remains non-zero on failures/);
assert.match(releaseMissingEnvTemplate, /# DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1 DDD_FINAL_OWNER_QUEUE_EXECUTE=1 bash artifacts\/ddd\/release\/release-final-owner-queue-commands\.sh # diagnostic only; final exit remains non-zero on failures/);
assert.equal(summary.manifest.missingArtifacts[0], "missing artifact performance/authenticated-runtime-baseline.json");
assert.equal(summary.manifest.summary.optionalArtifacts, 1);
assert.equal(summary.manifest.optionalOwnerQueueRunReport.relativePath, "release/release-final-owner-queue-run-report.json");
assert.equal(summary.manifest.optionalOwnerQueueRunReport.contractIssues.length, 0);
assert.equal(summary.manifest.actionPlan["release-performance"].pendingItems, 1);
assert.equal(summary.manifest.actionPlan["release-performance"].items[0].id, "manifest-missing-performance-authenticated-runtime-baseline-json");
assert(summary.manifest.actionPlan["release-performance"].items[0].envKeys.includes("DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT"));
assert.equal(summary.ownerActionRollup["release-performance"].pendingItems, 8);
assert.equal(summary.ownerActionRollup["release-performance"].sources.manifest, 1);
assert.equal(summary.ownerActionRollup["release-performance"].sources["authenticated-performance"], 7);
assert(summary.ownerActionRollup["release-performance"].envKeys.includes("DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT"));
assert.equal(summary.ownerActionRollup.database.pendingItems, 11);
assert.equal(summary.ownerActionRollup.database.sources.explain, 8);
assert.equal(summary.ownerActionRollup.database.sources.migration, 2);
assert.equal(summary.ownerActionRollup["release-infra"].pendingItems, 9);
assert.equal(summary.ownerActionRollup.ai.collapsedItems, 3);
assert.equal(summary.ownerActionRollup.database.collapsedItems, 2);
assert.equal(summary.ownerActionRollup.frontend.collapsedItems, 2);
assert.equal(summary.ownerActionRollup["release-infra"].collapsedItems, 2);
assert.equal(summary.ownerActionRollup["release-infra"].sources.docker, 1);
assert.equal(summary.ownerActionRollup["release-infra"].sources["release-env-lint"], 2);
assert.equal(summary.ownerActionRollup["release-infra"].sources["runtime-readiness"], 5);
assert.equal(summary.ownerActionRollup["release-infra"].sources.orchestrator, undefined);
assert.equal(summary.ownerActionRollup["release-infra"].collapsedSources.orchestrator, 2);
assert.equal(summary.sourceActionRollup["release-env-lint"].pendingItems, 2);
assert.equal(summary.sourceActionRollup["release-env-lint"].owners["release-infra"], 2);
assert.equal(summary.actionsByOwner["release-performance"].length, 2);
assert.equal(summary.actionsByOwner["file-owner"].length, 3);
assert.equal(summary.actionsByOwner["payment-owner"].length, 2);
assert.equal(summary.actionsByOwner["job-owner"].length, 2);
assert.equal(summary.actionsByOwner.frontend.length, 1);
assert.equal(summary.actionsByOwner.ai.length, 1);
assert.equal(summary.actionsByOwner["release-infra"].length, 3);
assert.equal(summary.actionsByOwner["iam-owner"].length, 1);
assert.equal(summary.actionsByOwner["auth-owner"].length, 1);
assert.equal(summary.actionsByCategory["production-equivalent-runtime"].length, 6);
assert.equal(summary.actionsByCategory["runtime-freshness"].length, 1);
assert.equal(summary.actionsByCategory["performance-freshness"].length, 1);
assert.equal(summary.actionsByCategory["business-e2e-freshness"].length, 3);
assert.equal(summary.actionsByCategory["frontend-smoke"].length, 1);
assert.equal(summary.actionsByCategory["ai-runtime"].length, 1);
assert.equal(summary.actionsByCategory["rollback-context-drills"].length, 2);
assert.equal(summary.diagnostics.releaseGate.structuredBlockers, 17);
assert.equal(summary.diagnostics.releaseGate.blockerDetails.length, 17);
assert.equal(summary.actions[0].structured, true);
assert.equal(summary.actions[0].check, "runtime-readiness-freshness");
assert.equal(summary.actions[0].detail, "checkedAt is 28.1h old; limit=24h");
assert.equal(summary.diagnostics.runtimeReadiness.localOnly, true);
assert.equal(summary.diagnostics.runtimeReadiness.actualChecks, 30);
assert.equal(summary.diagnostics.runtimeReadiness.contexts.find((context) => context.context === "ai").ready, true);
assert.equal(summary.diagnostics.runtimeReadiness.actionPlan.owner, "release-infra");
assert.equal(summary.diagnostics.runtimeReadiness.actionPlan.pendingItems, 5);
assert(summary.diagnostics.runtimeReadiness.actionPlan.items.some((item) => item.id === "runtime-readiness-production-equivalence"));
assert(summary.diagnostics.runtimeReadiness.actionPlan.items.some((item) => item.id.startsWith("runtime-readiness-contract-")));
assert(summary.diagnostics.runtimeReadiness.actionPlan.items
  .find((item) => item.id === "runtime-readiness-production-equivalence").envKeys.includes("LUMIRA_BASE_URL"));
assert.equal(summary.diagnostics.releaseConfig.status, "FAIL");
assert.equal(summary.diagnostics.releaseConfig.generatedMissingTemplate, false);
assert.equal(summary.diagnostics.releaseConfig.blockers.length, 2);
assert.equal(summary.diagnostics.releaseConfig.blockerDetails[1].owner, "ai-owner");
assert.equal(summary.diagnostics.releaseConfig.blockersByGroup.ai, 1);
assert.equal(summary.diagnostics.releaseConfig.actionPlan["release-infra"].missingChecks, 1);
assert(summary.diagnostics.releaseConfig.actionPlan["release-infra"].envKeys.includes("LUMIRA_BASE_URL"));
assert.equal(summary.diagnostics.releaseEnvLint.status, "FAIL");
assert.equal(summary.diagnostics.releaseEnvLint.envFile, "/secure/.env.release");
assert.equal(summary.diagnostics.releaseEnvLint.summary.unresolvedTemplateKeys, 3);
assert.equal(summary.diagnostics.releaseEnvLint.summary.releaseConfigBlockers, 3);
assert.equal(summary.diagnostics.releaseEnvLint.actionPlan.owner, "release-infra");
assert.equal(summary.diagnostics.releaseEnvLint.actionPlan.pendingItems, 2);
assert(summary.diagnostics.releaseEnvLint.actionPlan.envKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"));
assert.equal(summary.diagnostics.releaseConfig.actionPlan["ai-owner"].missingChecks, 1);
assert(summary.diagnostics.releaseConfig.actionPlan["ai-owner"].envKeys.includes("LUMIRA_AI_PROVIDER_API_KEY"));
assert(summary.diagnostics.releaseConfig.contractIssues.includes("release config coverageMatrix missing runtime.jwt secret"));
assert.deepEqual(summary.diagnostics.readinessSummary.contractIssues, []);
assert.equal(summary.diagnostics.authenticatedPerformance.actual.localOnly, true);
assert.equal(summary.diagnostics.authenticatedPerformance.actual.productionEquivalence.https, false);
assert.equal(summary.diagnostics.authenticatedPerformance.actual.endpointCount, 9);
assert.equal(summary.diagnostics.authenticatedPerformance.baselinePromotion.status, "FAIL");
assert.equal(summary.diagnostics.authenticatedPerformance.baselinePromotion.sourceActual.localOnly, true);
assert.equal(summary.diagnostics.authenticatedPerformance.baseline.present, false);
assert.equal(summary.diagnostics.authenticatedPerformance.baseline.requiredEnvKeys[0], "DDD_AUTH_PERF_BASELINE_ACCEPTED_BY");
assert.equal(summary.diagnostics.authenticatedPerformance.actionPlan.owner, "release-performance");
assert.equal(summary.diagnostics.authenticatedPerformance.actionPlan.pendingItems, 7);
assert.deepEqual(summary.diagnostics.authenticatedPerformance.actionPlan.items.map((item) => item.id), [
  "performance-actual-production-equivalence",
  "performance-actual-shape-2",
  "performance-actual-shape-3",
  "performance-actual-shape-4",
  "performance-actual-shape-5",
  "performance-baseline",
  "performance-baseline-promotion-7",
]);
assert.equal(summary.diagnostics.businessE2e.fileProcessing.localOnly, true);
assert.equal(summary.diagnostics.businessE2e.fileProcessing.productionEquivalence.https, false);
assert.equal(summary.diagnostics.businessE2e.fileProcessing.requiredTasks[0].status, "SUCCEEDED");
assert.equal(summary.diagnostics.businessE2e.paymentWebhook.orderStatus, "PAID");
assert.equal(summary.diagnostics.businessE2e.paymentWebhook.productionEquivalence.localOnly, true);
assert.equal(summary.diagnostics.businessE2e.jobE2e.unauthorizedStatus, 401);
assert.equal(summary.diagnostics.businessE2e.jobE2e.productionEquivalence.strict, true);
assert.equal(summary.diagnostics.businessE2e.actionPlan["file-owner"].pendingItems, 1);
assert.equal(summary.diagnostics.businessE2e.actionPlan["file-owner"].items[0].id, "file-processing-production-equivalence");
assert.equal(summary.diagnostics.businessE2e.actionPlan["payment-owner"].pendingItems, 1);
assert.equal(summary.diagnostics.businessE2e.actionPlan["payment-owner"].items[0].id, "payment-webhook-production-equivalence");
assert.equal(summary.diagnostics.businessE2e.actionPlan["job-owner"].pendingItems, 1);
assert.equal(summary.diagnostics.businessE2e.actionPlan["job-owner"].items[0].id, "job-e2e-production-equivalence");
assert.equal(summary.diagnostics.docker.cliStatus, 0);
assert.equal(summary.diagnostics.docker.daemonStatus, 1);
assert.equal(summary.diagnostics.docker.images[0].expectedExposedPort, "8080/tcp");
assert.equal(summary.diagnostics.docker.skippedImages[0].skipReason, "docker daemon is not available");
assert.equal(summary.diagnostics.docker.actionPlan.owner, "release-infra");
assert.equal(summary.diagnostics.docker.actionPlan.pendingItems, 1);
assert.deepEqual(summary.diagnostics.docker.actionPlan.items.map((item) => item.id), [
  "docker-daemon",
]);
assert.equal(summary.diagnostics.frontendSmoke.localOnly, true);
assert.equal(summary.diagnostics.frontendSmoke.https, false);
assert.equal(summary.diagnostics.frontendSmoke.expectDeployed, false);
assert.equal(summary.diagnostics.frontendSmoke.productionEquivalence.localOnly, true);
assert.equal(summary.diagnostics.frontendSmoke.missingFlows[0].flow, "dashboard page is reachable");
assert.equal(summary.diagnostics.frontendSmoke.playwrightReport.present, false);
assert.equal(summary.diagnostics.frontendSmoke.staticSpecCoverage.covered, 1);
assert.equal(summary.diagnostics.frontendSmoke.actionPlan.owner, "frontend");
assert.equal(summary.diagnostics.frontendSmoke.actionPlan.pendingItems, 3);
assert.deepEqual(summary.diagnostics.frontendSmoke.actionPlan.items.map((item) => item.id), [
  "frontend-base-url",
  "frontend-deployed-expectation",
  "frontend-playwright-report",
]);
assert.equal(summary.diagnostics.frontendSmoke.actionPlan.items.find((item) => item.id === "frontend-flow-coverage"), undefined);
assert.equal(summary.diagnostics.migration.runtime.releaseCandidate, "local-worktree");
assert.equal(summary.diagnostics.migration.runtimeReady, false);
assert.equal(summary.diagnostics.migration.runtimeProofs[0].id, "fresh-database");
assert.equal(summary.diagnostics.migration.runtimeDiagnostics[0].owner, "database");
assert.equal(summary.diagnostics.migration.actionPlan.database.pendingItems, 4);
assert.deepEqual(summary.diagnostics.migration.actionPlan.database.items.map((item) => item.id), [
  "migration-diagnostic-fresh-database-drill",
  "migration-fresh-database-drill",
  "migration-proof-fresh-database",
  "migration-upgrade-database-drill",
]);
assert.equal(summary.diagnostics.migration.actionPlan["release-owner"].pendingItems, 1);
assert.equal(summary.diagnostics.migration.actionPlan["release-owner"].items[0].id, "migration-runtime-ready");
assert.equal(summary.diagnostics.explain.fileCount, 1);
assert.equal(summary.diagnostics.explain.legacyPlanImports[0], "message-visible-list.json");
assert(summary.diagnostics.explain.missingRequiredFiles.includes("platform-runtime-appearance.json"));
assert(summary.diagnostics.explain.missingRequiredFiles.includes("message-unread-count.json"));
assert(summary.diagnostics.explain.missingRequiredFiles.includes("message-archive-total.json"));
assert(summary.diagnostics.explain.issues.some((issue) => issue.detail === "message-visible-list.json.legacyPlanImport must be false for strict release evidence"));
assert.equal(summary.diagnostics.explain.actionPlan.owner, "database");
assert.equal(summary.diagnostics.explain.actionPlan.pendingFiles, 8);
assert(summary.diagnostics.explain.actionPlan.items.some((item) => (
  item.file === "message-visible-list.json"
    && item.reasons.some((reason) => reason.includes("legacyPlanImport=true"))
)));
assert(summary.diagnostics.explain.actionPlan.items.some((item) => (
  item.file === "platform-runtime-appearance.json"
    && item.reasons.includes("missing required EXPLAIN artifact")
)));
assert.equal(summary.diagnostics.aiRuntime.failures[0], "AI runtime endpoint request failed: fetch failed");
assert.equal(summary.diagnostics.aiRuntime.failureDetails[0].category, "endpoint");
assert.equal(summary.diagnostics.aiRuntime.failureCategories.endpoint, 1);
assert.equal(summary.diagnostics.aiRuntime.failureOwners.ai, 1);
assert.equal(summary.diagnostics.aiRuntime.localOnly, true);
assert.equal(summary.diagnostics.aiRuntime.productionEquivalence.https, false);
assert.equal(summary.diagnostics.aiRuntime.actionPlan.owner, "ai");
assert(summary.diagnostics.aiRuntime.actionPlan.items.some((item) => item.id === "ai-runtime-base-url"));
assert(summary.diagnostics.aiRuntime.actionPlan.items.some((item) => item.id === "ai-provider-runtime"));
assert(summary.diagnostics.aiRuntime.actionPlan.items.some((item) => item.id === "ai-owner-gateway"));
assert(summary.diagnostics.aiRuntime.actionPlan.items.some((item) => item.id === "ai-failure-endpoint-ai"));
assert.equal(summary.diagnostics.rollback.contexts[0].context, "IAM");
assert.equal(summary.diagnostics.rollback.contextDiagnostics[0].owner, "iam-owner");
assert.equal(summary.diagnostics.rollback.summary.requiredContexts, 10);
assert.equal(summary.diagnostics.rollback.actionPlan["iam-owner"].pendingContexts, 1);
assert.equal(summary.diagnostics.rollback.actionPlan["iam-owner"].missingEvidence, 1);
assert.equal(summary.diagnostics.rollback.actionPlan["iam-owner"].items[0].context, "IAM");
assert.equal(
  summary.diagnostics.rollback.actionPlan["iam-owner"].items[0].reason,
  "IAM rollback drill requires PASS evidence or approved DEFERRED risk acceptance; status=MISSING",
);
assert.match(
  summary.diagnostics.rollback.actionPlan["iam-owner"].items[0].action,
  /Required evidence: permission snapshot version before and after rollback; audit entry or command log for the rollback action\./,
);
assert(releasePriority.items.some((item) => (
  item.source === "rollback"
    && item.owner === "iam-owner"
    && item.id === "IAM"
    && item.reason === "IAM rollback drill requires PASS evidence or approved DEFERRED risk acceptance; status=MISSING"
    && item.action.includes("permission snapshot version before and after rollback")
)));
assert.equal(summary.diagnostics.orchestrator.preflight.blockers, 8);
assert.equal(summary.diagnostics.orchestrator.blockerChecks[0].envKeys[0], "LUMIRA_BASE_URL");
assert.equal(summary.diagnostics.orchestrator.selectedStepCount, 2);
assert.equal(summary.diagnostics.orchestrator.executedResultCount, 0);
assert.deepEqual(summary.diagnostics.orchestrator.missingResults, []);
assert.equal(summary.diagnostics.orchestrator.actionPlan.ai.pendingItems, 3);
assert.equal(summary.diagnostics.orchestrator.actionPlan.database.pendingItems, 1);
assert.equal(summary.diagnostics.orchestrator.actionPlan.frontend.pendingItems, 2);
assert.equal(summary.diagnostics.orchestrator.actionPlan["release-infra"].pendingItems, 2);
assert.equal(summary.diagnostics.orchestrator.actionPlan["release-infra"].items[0].id, "orchestrator-preflight-backend-runtime-base-url");
assert.equal(summary.diagnostics.orchestrator.actionPlan["release-owner"].pendingItems, 1);
assert.equal(summary.diagnostics.orchestrator.actionPlan["release-owner"].items[0].id, "orchestrator-run-mode");

const markdown = fs.readFileSync(path.join(artifactRoot, "release/readiness-summary.md"), "utf8");
assert.match(markdown, /Release gate mode: strict/);
assert.match(markdown, /Release gate blockers: 17/);
assert.match(markdown, /Release gate warnings: 0/);
assert.doesNotMatch(markdown, /Strict gate blockers/);
assert.doesNotMatch(markdown, /Strict gate warnings/);
assert.match(ownerRollupMarkdown, /Release gate mode: strict/);
assert.doesNotMatch(ownerRollupMarkdown, /Strict gate blockers/);
assert.match(releasePriorityMarkdown, /Release gate mode: strict/);
assert.doesNotMatch(releasePriorityMarkdown, /Strict gate blockers/);
assert.match(releaseMissingEnvTemplate, /# Release gate mode: strict/);
assert.doesNotMatch(releaseMissingEnvTemplate, /Strict gate blockers/);
assert.match(markdown, /## Missing Manifest Artifacts/);
assert.match(markdown, /Manifest optional artifacts: 1/);
assert.match(markdown, /Optional owner queue run report: PRESENT; bytes=256; contractIssues=0/);
assert.match(markdown, /Owner queue env init receipt: PRESENT; permissionSafe=true; mode=600; unresolvedTemplateKeys=2/);
assert.match(markdown, /missing artifact performance\/authenticated-runtime-baseline\.json/);
assert.match(markdown, /manifestAction: manifest-missing-performance-authenticated-runtime-baseline-json; owner=release-performance; reason=missing artifact performance\/authenticated-runtime-baseline\.json; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE; artifact=performance\/authenticated-runtime-baseline\.json/);
assert.match(markdown, /## Owner Action Rollup/);
assert.match(markdown, /owner=release-performance pendingItems=8 collapsedItems=0 sources=authenticated-performance=7,manifest=1/);
assert.match(markdown, /ownerAction: source=manifest; id=manifest-missing-performance-authenticated-runtime-baseline-json/);
assert.match(markdown, /owner=ai pendingItems=4 collapsedItems=3 sources=ai-runtime=4 collapsedSources=orchestrator=3/);
assert.match(markdown, /owner=database pendingItems=11 collapsedItems=2 sources=explain=8,migration=2,orchestrator=1 collapsedSources=migration=2/);
assert.match(markdown, /owner=frontend pendingItems=3 collapsedItems=2 sources=frontend-smoke=3 collapsedSources=orchestrator=2/);
assert.match(markdown, /owner=release-infra pendingItems=9 collapsedItems=2 sources=docker=1,release-config=1,release-env-lint=2,runtime-readiness=5 collapsedSources=orchestrator=2/);
assert.match(markdown, /ownerActionCollapsed: source=orchestrator; id=orchestrator-preflight-backend-runtime-base-url; coveredBy=release-config:backend base url/);
assert.match(markdown, /ownerActionCollapsed: source=orchestrator; id=orchestrator-preflight-docker-daemon; coveredBy=docker:docker-daemon/);
assert.match(markdown, /ownerActionCollapsed: source=orchestrator; id=orchestrator-preflight-frontend-runtime-base-url; coveredBy=frontend-smoke:frontend-base-url/);
assert.match(markdown, /ownerActionCollapsed: source=orchestrator; id=orchestrator-preflight-ai-provider-remote-expectation; coveredBy=ai-runtime:ai-provider-runtime/);
assert.match(markdown, /## Runtime Readiness Diagnostics/);
assert.match(markdown, /productionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /productionEquivalenceIssue: strict runtime readiness requires HTTPS baseUrl evidence/);
assert.match(markdown, /checks: 30\/30/);
assert.match(markdown, /ai: ready=true/);
assert.match(markdown, /actionPlan: owner=release-infra pendingItems=2/);
assert.match(markdown, /runtimeAction: runtime-readiness-production-equivalence; owner=release-infra; reason=strict runtime readiness requires HTTPS baseUrl evidence; strict runtime readiness requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /## Release Env Lint/);
assert.match(markdown, /status: FAIL inputKind=release-env-file envFile=\/secure\/\.env\.release keys=4 blockers=2 primaryBlockers=1/);
assert.match(markdown, /envFileSecurity: checked=true mode=600 permissionSafe=true permissionCheckSkipped=false reason=env-file requiredMode=600/);
assert.match(markdown, /unresolvedTemplateKeys: 3/);
assert.match(markdown, /releaseConfigBlockers: 3/);
assert.match(markdown, /releaseConfigBlockersFromPlaceholders: 3/);
assert.match(markdown, /releaseConfigBlockersAfterPlaceholders: 0/);
assert.match(markdown, /envLintAction: release-env-lint-placeholders; owner=release-infra; reason=unresolvedTemplateKeys=3; envKeys=LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY/);
assert.match(markdown, /## Release Config Blockers/);
assert.match(markdown, /status: FAIL inputKind=process-environment-only envFile=missing envFileExists=false/);
assert.match(markdown, /blockerSummary: blockers=2 primaryBlockers=2 fromPlaceholders=0 afterPlaceholders=2/);
assert.match(markdown, /coverage: required=2 runtimePresent=0 envFile=0 template=2 workflow=2/);
assert.match(markdown, /missingRuntimeRequiredChecks: 2/);
assert.match(markdown, /owners: ai-owner=1, release-infra=1/);
assert.match(markdown, /ownerPlan: ai-owner missingChecks=1; envKeys=9 keys/);
assert.match(markdown, /  - envKeys: 9 keys\n    - LUMIRA_AI_CHAT_MODEL,LUMIRA_AI_EMBEDDING_MODEL,LUMIRA_AI_PROVIDER_API_KEY,LUMIRA_AI_PROVIDER_BASE_URL/);
assert.match(markdown, /\[release-infra\]\[runtime\] backend base url: missing LUMIRA_BASE_URL or DEPLOY_CHECK_BASE_URL; envKeys=DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL/);
assert.match(markdown, /\[ai-owner\]\[ai\] provider api key: missing LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY or LUMIRA_AI_PROVIDER_API_KEY; envKeys=9 keys/);
assert.match(markdown, /configContractIssue: release config coverageMatrix missing runtime\.jwt secret/);
assert.match(markdown, /## Authenticated Performance Diagnostics/);
assert.match(markdown, /actualLocalOnly: true/);
assert.match(markdown, /authenticatedPerformanceActualProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /authenticatedPerformanceActualProductionEquivalenceIssue: strict authenticated performance actual requires HTTPS baseUrl evidence/);
assert.match(markdown, /baseline: missing; file=.*authenticated-runtime-baseline\.json; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE/);
assert.match(markdown, /baselinePromotion: status=FAIL sourceFile=\/tmp\/authenticated-runtime-actual\.json outputFile=\/tmp\/authenticated-runtime-baseline\.json/);
assert.match(markdown, /baselinePromotionActual: localOnly=true failed=0 p95=88 endpointCount=9/);
assert.match(markdown, /baselinePromotionBlocker: source actual artifact must be production-equivalent and non-local, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /actionPlan: owner=release-performance pendingItems=7/);
assert.match(markdown, /performanceAction: performance-actual-production-equivalence; owner=release-performance; reason=strict authenticated performance actual requires HTTPS baseUrl evidence; strict authenticated performance actual requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /performanceAction: performance-baseline; owner=release-performance; reason=missing authenticated performance baseline .*authenticated-runtime-baseline\.json; envKeys=DDD_AUTH_PERF_BASELINE_ACCEPTED_BY,DDD_AUTH_PERF_BASELINE_ENVIRONMENT,DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT,DDD_RELEASE_CANDIDATE/);
assert.match(markdown, /performanceAction: performance-baseline-promotion-7; owner=release-performance; reason=source actual artifact must be production-equivalent and non-local, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /## Business Runtime E2E Diagnostics/);
assert.match(markdown, /fileProcessing: status=PASS localOnly=true uploadMs=100 fileId=7/);
assert.match(markdown, /fileProcessingProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /fileProcessingProductionEquivalenceIssue: strict file processing E2E requires HTTPS baseUrl evidence/);
assert.match(markdown, /paymentWebhook: status=PASS localOnly=true orderStatus=PAID providerConfigured=true/);
assert.match(markdown, /paymentWebhookProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /jobE2e: localOnly=true unauthorizedStatus=401 failed=0 endpointCount=9/);
assert.match(markdown, /jobE2eProductionEquivalenceIssue: strict job E2E requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /actionPlan: owner=file-owner pendingItems=1 envKeys=.*LUMIRA_UPLOAD_STORAGE_ROOT/);
assert.match(markdown, /businessAction: file-processing-production-equivalence; owner=file-owner; reason=strict file processing E2E requires HTTPS baseUrl evidence; strict file processing E2E requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /actionPlan: owner=job-owner pendingItems=1 envKeys=.*LUMIRA_JOB_INTERNAL_TOKEN/);
assert.match(markdown, /businessAction: job-e2e-production-equivalence; owner=job-owner; reason=strict job E2E requires HTTPS baseUrl evidence; strict job E2E requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /actionPlan: owner=payment-owner pendingItems=1 envKeys=.*PAYMENT_PUBLIC_BASE_URL/);
assert.match(markdown, /businessAction: payment-webhook-production-equivalence; owner=payment-owner; reason=strict payment webhook E2E requires HTTPS baseUrl evidence; strict payment webhook E2E requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /## Docker Diagnostics/);
assert.match(markdown, /cliVersion: Docker version 29\.4\.1/);
assert.match(markdown, /daemonError: Cannot connect to the Docker daemon/);
assert.match(markdown, /image lumira-server: status=SKIPPED dockerfile=deploy\/docker\/service\.Dockerfile tag=lumira\/lumira-server:test expectedPort=8080\/tcp nonRoot=true/);
assert.match(markdown, /staticDockerfile: status=PASS exists=true sha256=abc123/);
assert.match(markdown, /action: Start Docker daemon or run DDD_DOCKER_BUILD_STRICT=true node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(markdown, /actionPlan: owner=release-infra pendingItems=1 envKeys=DDD_DOCKER_BUILD_STRICT,DDD_DOCKER_COMMAND/);
assert.match(markdown, /remediation: transientRegistryFailure=true dockerUnavailable=true/);
assert.match(markdown, /dockerTransientImage: lumira-server; attempts=3; retries=2; dockerfile=deploy\/docker\/service\.Dockerfile/);
assert.match(markdown, /dockerRemediationAction: docker-registry-mirror-retry; owner=release-infra; envKeys=DDD_DOCKER_BUILD_RETRIES,DDD_DOCKER_MAVEN_IMAGE,DDD_DOCKER_JRE_IMAGE,DDD_DOCKER_NODE_IMAGE,DDD_DOCKER_NGINX_IMAGE; action=Rerun Docker evidence with registry-local mirror images and a higher retry budget\.; exampleCommand=DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_BUILD_RETRIES=4 DDD_DOCKER_MAVEN_IMAGE=<registry>\/maven:3\.9\.11-eclipse-temurin-21 node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(markdown, /dockerRemediationAction: docker-existing-image-inspect; owner=release-infra; envKeys=DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE,DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE,DDD_DOCKER_EXISTING_FRONTEND_IMAGE; action=If CI already built and pushed the release candidate images, pull them and rerun Docker evidence in explicit inspect-only mode\.; exampleCommand=DDD_DOCKER_BUILD_STRICT=true DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>\/lumira-server:<release-candidate> DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>\/frontend:<release-candidate> node scripts\/ddd-docker-build-evidence\.mjs/);
assert.match(markdown, /dockerAction: docker-daemon; owner=release-infra; reason=Cannot connect to the Docker daemon; envKeys=DDD_DOCKER_COMMAND,DDD_DOCKER_BUILD_STRICT; action=.*inspect-only evidence/);
assert.doesNotMatch(markdown, /dockerAction: docker-image-lumira-server-skipped/);
assert.match(markdown, /lumira-server: docker daemon is not available/);
assert.match(markdown, /## Frontend Smoke Missing Flows/);
assert.match(markdown, /baseUrl: http:\/\/127\.0\.0\.1:8010/);
assert.match(markdown, /https: false/);
assert.match(markdown, /expectDeployed: false/);
assert.match(markdown, /frontendSmokeProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /frontendSmokeProductionEquivalenceIssue: strict frontend smoke requires HTTPS baseUrl evidence/);
assert.match(markdown, /requiredFlows: 1; missing=1/);
assert.match(markdown, /playwrightReport: present=false file=\/tmp\/playwright-smoke-results\.json/);
assert.match(markdown, /staticSpecCoverage: present=true covered=1 missing=0 file=\/repo\/frontend\/tests\/e2e\/app\.spec\.ts/);
assert.match(markdown, /actionPlan: owner=frontend pendingItems=3/);
assert.match(markdown, /frontendAction: frontend-base-url; owner=frontend; reason=strict frontend smoke requires HTTPS baseUrl evidence; strict frontend smoke requires non-local baseUrl, got http:\/\/127\.0\.0\.1:8010/);
assert.match(markdown, /frontendAction: frontend-deployed-expectation; owner=frontend; reason=strict release requires deployed frontend smoke expectation; envKeys=DDD_FRONTEND_EXPECT_DEPLOYED/);
assert.doesNotMatch(markdown, /frontendAction: frontend-flow-coverage/);
assert.match(markdown, /frontendAction: frontend-playwright-report; owner=frontend; reason=missing Playwright JSON report \/tmp\/playwright-smoke-results\.json/);
assert.match(markdown, /dashboard page is reachable: missing Playwright JSON report; action=Run deployed Playwright @smoke coverage for this flow and regenerate frontend-smoke\.json\./);
assert.match(markdown, /## Migration Runtime Evidence/);
assert.match(markdown, /releaseCandidate: local-worktree/);
assert.match(markdown, /freshDatabaseValidated: false/);
assert.match(markdown, /runtimeReady: false/);
assert.match(markdown, /proof fresh-database: validated=false; evidence=missing; required=Flyway log plus schema-history artifact from an empty production-equivalent database.; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE/);
assert.match(markdown, /fresh-database-drill: MISSING; owner=database; evidence=missing; action=Run Flyway against an empty database.; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE/);
assert.match(markdown, /actionPlan: owner=database pendingItems=4 envKeys=.*DDD_MIGRATION_FRESH_DB_EVIDENCE/);
assert.match(markdown, /migrationAction: migration-fresh-database-drill; owner=database; reason=freshDatabaseValidated=false evidence=missing; envKeys=DDD_MIGRATION_FRESH_DB_VALIDATED,DDD_MIGRATION_FRESH_DB_EVIDENCE/);
assert.match(markdown, /migrationAction: migration-upgrade-database-drill; owner=database; reason=upgradeDatabaseValidated=false evidence=missing; envKeys=DDD_MIGRATION_UPGRADE_DB_VALIDATED,DDD_MIGRATION_UPGRADE_DB_EVIDENCE/);
assert.match(markdown, /migrationAction: migration-runtime-ready; owner=release-owner; reason=migration runtime evidence is not ready; envKeys=DDD_MIGRATION_ENVIRONMENT,DDD_MIGRATION_OPERATOR,DDD_MIGRATION_COMPLETED_AT/);
assert.match(markdown, /## EXPLAIN Evidence Diagnostics/);
assert.match(markdown, /files: 1/);
assert.match(markdown, /missingRequiredFiles: 7/);
assert.match(markdown, /legacyPlanImports: 1/);
assert.match(markdown, /legacyExplainFile: message-visible-list\.json/);
assert.match(markdown, /actionPlan: owner=database pendingFiles=8/);
assert.match(markdown, /explainAction: message-visible-list\.json; reasons=.*legacyPlanImport=true/);
assert.match(markdown, /explainAction: platform-runtime-appearance\.json; reasons=missing required EXPLAIN artifact/);
assert.match(markdown, /gateReport: present=true status=FAIL blockers=8 generatedAt=2026-06-14T00:00:00\.000Z/);
assert.match(markdown, /explainIssue: \[metadata\] message-visible-list\.json\.legacyPlanImport must be false for strict release evidence/);
assert.match(markdown, /## AI Runtime Diagnostics/);
assert.match(markdown, /baseUrl: http:\/\/127\.0\.0\.1:8080/);
assert.match(markdown, /aiRuntimeProductionEquivalence: strict=true https=false localOnly=true deploymentEvidence=missing/);
assert.match(markdown, /aiRuntimeProductionEquivalenceIssue: strict AI runtime drill requires HTTPS baseUrl evidence/);
assert.match(markdown, /providerRemoteConfigured: false/);
assert.match(markdown, /actionPlan: owner=ai pendingItems=4/);
assert.match(markdown, /aiAction: ai-runtime-base-url; owner=ai; reason=.*strict AI runtime drill requires HTTPS baseUrl evidence/);
assert.match(markdown, /aiAction: ai-provider-runtime; owner=ai; reason=provider status=missing remoteConfigured=false/);
assert.match(markdown, /aiAction: ai-owner-gateway; owner=ai; reason=ownerGateway status=missing configuredOwners=0/);
assert.match(markdown, /aiAction: ai-failure-endpoint-ai; owner=ai; reason=AI runtime endpoint request failed: fetch failed/);
assert.match(markdown, /failureCategories: endpoint=1/);
assert.match(markdown, /failureOwners: ai=1/);
assert.match(markdown, /\[endpoint\]\[ai\] AI runtime endpoint request failed: fetch failed/);
assert.match(markdown, /AI runtime endpoint request failed: fetch failed/);
assert.match(markdown, /## Rollback Drill Contexts/);
assert.match(markdown, /summary: ready=0\/10 pass=0 deferred=0 missing=1 blockers=0/);
assert.match(markdown, /ownerPlan: iam-owner pending=1 ready=0 missingEvidence=1/);
assert.match(markdown, /IAM: MISSING; owner=iam-owner; reason=IAM rollback drill requires PASS evidence or approved DEFERRED risk acceptance; status=MISSING; evidence=missing; action=Exercise IAM rollback\./);
assert.match(markdown, /## Orchestrator Preflight/);
assert.match(markdown, /selectedSteps: 2; executedResults: 0/);
assert.match(markdown, /action: Run `DDD_RELEASE_EVIDENCE_STRICT=true node scripts\/ddd-release-evidence-orchestrator\.mjs --run --strict` after resolving preflight blockers\./);
assert.match(markdown, /actionPlan: owner=release-infra pendingItems=2 envKeys=BASE_URL,DDD_DOCKER_COMMAND,DEPLOY_CHECK_BASE_URL,LUMIRA_BASE_URL/);
assert.match(markdown, /orchestratorAction: orchestrator-preflight-backend-runtime-base-url; owner=release-infra; reason=missing backend runtime base URL; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL; checkId=backend-runtime-base-url/);
assert.match(markdown, /orchestratorAction: orchestrator-preflight-docker-daemon; owner=release-infra; reason=Docker daemon is not available; envKeys=DDD_DOCKER_COMMAND; checkId=docker-daemon/);
assert.match(markdown, /actionPlan: owner=release-owner pendingItems=1 envKeys=DDD_RELEASE_EVIDENCE_STRICT/);
assert.match(markdown, /orchestratorAction: orchestrator-run-mode; owner=release-owner; reason=strict release requires run mode report, got plan; envKeys=DDD_RELEASE_EVIDENCE_STRICT/);
assert.match(markdown, /backend-runtime-base-url: missing backend runtime base URL; envKeys=LUMIRA_BASE_URL,DEPLOY_CHECK_BASE_URL,BASE_URL/);
assert.match(markdown, /step release-config-evidence: not-run; envKeys=DDD_RELEASE_EVIDENCE_STRICT/);
assert.match(markdown, /step runtime-readiness: not-run; flags=runtime; envKeys=DDD_RELEASE_EVIDENCE_STRICT/);

const driftArtifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-drift-"));
fs.mkdirSync(path.join(driftArtifactRoot, "release"), { recursive: true });
fs.writeFileSync(path.join(driftArtifactRoot, "release/release-evidence-gate.json"), `${JSON.stringify({
  strict: true,
  summary: {
    blockers: 3,
    warnings: 0,
  },
  checks: [
    {
      name: "one",
      status: "blocker",
      detail: "one",
      file: null,
    },
    {
      name: "two",
      status: "blocker",
      detail: "two",
      file: null,
    },
  ],
  blockers: ["one", "two"],
  warnings: [],
}, null, 2)}\n`);
const driftResult = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: driftArtifactRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});
assert.equal(driftResult.status, 0, driftResult.stderr || driftResult.stdout);
const driftSummary = JSON.parse(fs.readFileSync(path.join(driftArtifactRoot, "release/readiness-summary.json"), "utf8"));
assert.equal(driftSummary.status, "NOT_READY");
assert(driftSummary.diagnostics.releaseGate.contractIssues.some((issue) => issue.includes("release gate summary blockers mismatch")));
assert(driftSummary.diagnostics.readinessSummary.contractIssues.includes("actions.length must match gate.blockers, got 2 actions and 3 blockers"));
assert(driftSummary.diagnostics.readinessSummary.contractIssues.includes("inputArtifacts.releaseGate.blockers must match actions.length, got 2 and 2") === false);
assert.equal(driftSummary.inputArtifacts.releaseGate.blockers, 2);
assert.equal(driftSummary.inputArtifacts.releaseGate.warnings, 0);

const configDriftArtifactRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-config-drift-"));
fs.mkdirSync(path.join(configDriftArtifactRoot, "release"), { recursive: true });
fs.writeFileSync(path.join(configDriftArtifactRoot, "release/release-evidence-gate.json"), `${JSON.stringify({
  strict: true,
  summary: {
    checks: 0,
    blockers: 0,
    warnings: 0,
  },
  checks: [],
  blockers: [],
  warnings: [],
}, null, 2)}\n`);
fs.mkdirSync(path.join(configDriftArtifactRoot, "config"), { recursive: true });
fs.writeFileSync(path.join(configDriftArtifactRoot, "config/release-config-evidence.json"), `${JSON.stringify({
  status: "FAIL",
  summary: {
    groups: 0,
    checks: 0,
    requiredChecks: 0,
    runtimePresentRequiredChecks: 0,
    envFileCoveredRequiredChecks: 0,
    templateCoveredRequiredChecks: 0,
    workflowCoveredRequiredChecks: 0,
    blockers: 1,
    warnings: 0,
  },
  groups: [],
  coverageMatrix: [],
  blockers: ["runtime.jwt secret: must be at least 32 characters"],
  blockerDetails: [{
    blocker: "runtime.jwt secret: must be at least 32 characters",
    group: "runtime",
    owner: "release-infra",
    check: "jwt secret",
    reason: "must be at least 32 characters",
    envKeys: ["JWT_SECRET"],
    matchedKey: "JWT_SECRET",
    required: true,
  }],
  blockersByGroup: { runtime: 1 },
  blockersByOwner: { "release-infra": 1 },
  warnings: [],
}, null, 2)}\n`);
const configDriftResult = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: configDriftArtifactRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});
assert.equal(configDriftResult.status, 0, configDriftResult.stderr || configDriftResult.stdout);
const configDriftSummary = JSON.parse(fs.readFileSync(path.join(configDriftArtifactRoot, "release/readiness-summary.json"), "utf8"));
assert(configDriftSummary.diagnostics.releaseConfig.contractIssues.includes("release config coverageMatrix missing runtime.jwt secret"));
const configDriftMarkdown = fs.readFileSync(path.join(configDriftArtifactRoot, "release/readiness-summary.md"), "utf8");
assert.match(configDriftMarkdown, /configContractIssue: release config coverageMatrix missing runtime\.jwt secret/);

const missingEnvRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-missing-env-"));
fs.mkdirSync(path.join(missingEnvRoot, "release"), { recursive: true });
fs.writeFileSync(path.join(missingEnvRoot, "release/release-evidence-gate.json"), `${JSON.stringify({
  strict: true,
  summary: {
    checks: 1,
    blockers: 1,
    warnings: 0,
  },
  checks: [{
    name: "release-env-lint-real-env-file",
    status: "blocker",
    detail: "release env lint was run against generated missing-env template",
    file: "release/release-env-lint.json",
  }],
  blockers: [
    "release-env-lint-real-env-file: release env lint was run against generated missing-env template; strict release requires a completed DDD_RELEASE_ENV_FILE",
  ],
  warnings: [],
}, null, 2)}\n`);
fs.writeFileSync(path.join(missingEnvRoot, "release/release-env-lint.json"), `${JSON.stringify({
  status: "FAIL",
  envFile: path.join(missingEnvRoot, "release/release-env-missing.template.env"),
  inputKind: "generated-missing-template",
  generatedMissingTemplate: true,
  missingEnv: {
    uniqueEnvKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_API_KEY"],
    templateEnvKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  },
  summary: {
    blockers: 1,
    warnings: 0,
    unresolvedTemplateKeys: 2,
    canonicalUnresolvedTemplateKeys: 2,
    releaseConfigBlockers: 2,
    canonicalReleaseConfigBlockerKeys: 2,
  },
  keys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_API_KEY"],
  canonicalKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  duplicateKeys: [],
  unresolvedTemplateKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_API_KEY"],
  canonicalUnresolvedTemplateKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  canonicalMissingEnvKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  canonicalReleaseConfigBlockerKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  releaseConfigBlockerDetails: [{
    blocker: "ai.provider api key: missing LUMIRA_AI_PROVIDER_API_KEY",
    group: "ai",
    owner: "ai-owner",
    check: "provider api key",
    reason: "missing LUMIRA_AI_PROVIDER_API_KEY",
    envKeys: ["LUMIRA_AI_PROVIDER_API_KEY"],
    canonicalEnvKeys: ["LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
    matchedKey: null,
    canonicalMatchedKey: null,
    required: true,
  }],
  blockers: ["generated missing template is not release evidence"],
  warnings: [],
}, null, 2)}\n`);
fs.writeFileSync(path.join(missingEnvRoot, "release/orchestrator-report.json"), `${JSON.stringify({
  mode: "plan",
  strict: true,
  summary: {
    steps: 1,
    executed: 0,
    failed: 0,
  },
  preflight: {
    status: "FAIL",
    blockers: 1,
    warnings: 0,
    checks: [{
      id: "backend-runtime-base-url",
      status: "BLOCKER",
      detail: "missing backend runtime base URL",
      envKeys: ["LUMIRA_BASE_URL", "DEPLOY_CHECK_BASE_URL", "BASE_URL"],
    }],
  },
  selectedSteps: [],
  results: [],
}, null, 2)}\n`);
fs.mkdirSync(path.join(missingEnvRoot, "config"), { recursive: true });
fs.writeFileSync(path.join(missingEnvRoot, "config/release-config-evidence.json"), `${JSON.stringify({
  status: "FAIL",
  inputKind: "missing-release-env-file",
  envFile: null,
  envFileExists: false,
  summary: {
    groups: 2,
    checks: 2,
    requiredChecks: 2,
    runtimePresentRequiredChecks: 0,
    envFileCoveredRequiredChecks: 0,
    templateCoveredRequiredChecks: 2,
    workflowCoveredRequiredChecks: 2,
    blockers: 2,
    warnings: 0,
  },
  groups: [],
  coverageMatrix: [
    { group: "runtime", check: "jwt secret", required: true },
    { group: "ai", check: "provider api key", required: true },
  ],
  blockers: [
    "runtime.jwt secret: missing JWT_SECRET",
    "ai.provider api key: missing LUMIRA_AI_PROVIDER_API_KEY",
  ],
  blockerDetails: [
    {
      blocker: "runtime.jwt secret: missing JWT_SECRET",
      group: "runtime",
      owner: "release-infra",
      check: "jwt secret",
      reason: "missing JWT_SECRET",
      envKeys: ["JWT_SECRET"],
      required: true,
    },
    {
      blocker: "ai.provider api key: missing LUMIRA_AI_PROVIDER_API_KEY",
      group: "ai",
      owner: "ai-owner",
      check: "provider api key",
      reason: "missing LUMIRA_AI_PROVIDER_API_KEY",
      envKeys: ["LUMIRA_AI_PROVIDER_API_KEY"],
      required: true,
    },
  ],
  blockersByGroup: { ai: 1, runtime: 1 },
  blockersByOwner: { "ai-owner": 1, "release-infra": 1 },
  warnings: [],
}, null, 2)}\n`);
const missingEnvResult = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: missingEnvRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});
assert.equal(missingEnvResult.status, 0, missingEnvResult.stderr || missingEnvResult.stdout);
const missingEnvSummary = JSON.parse(fs.readFileSync(path.join(missingEnvRoot, "release/readiness-summary.json"), "utf8"));
const missingEnvOwnerRollup = JSON.parse(fs.readFileSync(path.join(missingEnvRoot, "release/owner-action-rollup.json"), "utf8"));
const missingEnvPriority = JSON.parse(fs.readFileSync(path.join(missingEnvRoot, "release/release-action-priority.json"), "utf8"));
assert.equal(missingEnvSummary.diagnostics.releaseEnvLint.generatedMissingTemplate, true);
assert(missingEnvSummary.diagnostics.releaseEnvLint.actionPlan.envKeys.includes("LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"));
assert.equal(missingEnvSummary.diagnostics.releaseEnvLint.actionPlan.envKeys.includes("LUMIRA_AI_PROVIDER_API_KEY"), false);
assert.equal(missingEnvSummary.diagnostics.releaseConfig.actionPlan["ai-owner"].missingChecks, 1);
assert.equal(missingEnvOwnerRollup.totalPendingItems, missingEnvPriority.totalPendingItems);
assert.equal(missingEnvOwnerRollup.totalCollapsedItems, 3);
assert(missingEnvOwnerRollup.activeOwners.includes("release-infra"));
assert.deepEqual(missingEnvOwnerRollup.collapsedOnlyOwners, ["ai-owner"]);
assert.equal(missingEnvOwnerRollup.collapsedOnlyOwnerCount, 1);
assert(missingEnvOwnerRollup.owners["release-infra"].items.some((item) => item.id === "release-env-lint-real-env-file"));
assert.equal(missingEnvOwnerRollup.owners["release-infra"].collapsedItems, 2);
assert.equal(missingEnvOwnerRollup.owners["ai-owner"].pendingItems, 0);
assert.equal(missingEnvOwnerRollup.owners["ai-owner"].collapsedItems, 1);
assert.equal(missingEnvOwnerRollup.owners["ai-owner"].collapsedSources["release-config"], 1);
assert.equal(missingEnvOwnerRollup.owners["release-infra"].collapsedSources["release-config"], 1);
assert.deepEqual(missingEnvOwnerRollup.owners["ai-owner"].collapsed[0].coveredBy, {
  source: "release-env-lint",
  id: "release-env-lint-real-env-file",
  owner: "release-infra",
});
assert.equal(missingEnvPriority.bySource["release-config"], undefined);
assert.equal(missingEnvPriority.bySource["release-env-lint"], 1);
assert.equal(
  missingEnvPriority.items.some((item) => item.id === "orchestrator-preflight-backend-runtime-base-url"),
  false,
);
assert.equal(
  missingEnvOwnerRollup.owners["release-infra"].collapsed
    .some((item) => item.id === "orchestrator-preflight-backend-runtime-base-url"
      && item.coveredBy?.id === "release-env-lint-real-env-file"),
  true,
);
assert.equal(missingEnvPriority.items[0].id, "release-env-lint-real-env-file");

const processEnvOnlyRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-process-env-only-"));
fs.mkdirSync(path.join(processEnvOnlyRoot, "release"), { recursive: true });
fs.writeFileSync(path.join(processEnvOnlyRoot, "release/release-evidence-gate.json"), `${JSON.stringify({
  strict: true,
  summary: {
    checks: 1,
    blockers: 1,
    warnings: 0,
  },
  checks: [{
    name: "release-env-lint-real-env-file",
    status: "blocker",
    detail: "DDD_RELEASE_ENV_FILE is required",
    file: "release/release-env-lint.json",
  }],
  blockers: [
    "release-env-lint-real-env-file: DDD_RELEASE_ENV_FILE is required",
  ],
  warnings: [],
}, null, 2)}\n`);
fs.writeFileSync(path.join(processEnvOnlyRoot, "release/release-env-lint.json"), `${JSON.stringify({
  status: "FAIL",
  envFile: null,
  inputKind: "process-environment-only",
  generatedMissingTemplate: false,
  summary: {
    blockers: 2,
    warnings: 0,
    primaryBlockers: 2,
    releaseConfigBlockers: 2,
    releaseConfigBlockersFromPlaceholders: 0,
    releaseConfigBlockersAfterPlaceholders: 2,
  },
  keys: [],
  canonicalKeys: [],
  duplicateKeys: [],
  unresolvedTemplateKeys: [],
  canonicalUnresolvedTemplateKeys: [],
  canonicalMissingEnvKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  canonicalReleaseConfigBlockerKeys: ["JWT_SECRET", "LUMIRA_AI_PROVIDER_OPENAI_COMPATIBLE_API_KEY"],
  releaseConfigBlockerDetails: [],
  blockers: [
    "DDD_RELEASE_ENV_FILE or positional env file path is required",
    "runtime.jwt secret: missing JWT_SECRET",
  ],
  warnings: [],
}, null, 2)}\n`);
fs.mkdirSync(path.join(processEnvOnlyRoot, "config"), { recursive: true });
fs.writeFileSync(path.join(processEnvOnlyRoot, "config/release-config-evidence.json"), `${JSON.stringify({
  status: "FAIL",
  inputKind: "process-environment-only",
  envFile: null,
  envFileExists: false,
  summary: {
    groups: 2,
    checks: 2,
    requiredChecks: 2,
    runtimePresentRequiredChecks: 0,
    envFileCoveredRequiredChecks: 0,
    templateCoveredRequiredChecks: 2,
    workflowCoveredRequiredChecks: 2,
    blockers: 2,
    warnings: 0,
  },
  groups: [],
  coverageMatrix: [
    { group: "runtime", check: "jwt secret", required: true },
    { group: "ai", check: "provider api key", required: true },
  ],
  blockers: [
    "runtime.jwt secret: missing JWT_SECRET",
    "ai.provider api key: missing LUMIRA_AI_PROVIDER_API_KEY",
  ],
  blockerDetails: [
    {
      blocker: "runtime.jwt secret: missing JWT_SECRET",
      group: "runtime",
      owner: "release-infra",
      check: "jwt secret",
      reason: "missing JWT_SECRET",
      envKeys: ["JWT_SECRET"],
      required: true,
    },
    {
      blocker: "ai.provider api key: missing LUMIRA_AI_PROVIDER_API_KEY",
      group: "ai",
      owner: "ai-owner",
      check: "provider api key",
      reason: "missing LUMIRA_AI_PROVIDER_API_KEY",
      envKeys: ["LUMIRA_AI_PROVIDER_API_KEY"],
      required: true,
    },
  ],
  blockersByGroup: { ai: 1, runtime: 1 },
  blockersByOwner: { "ai-owner": 1, "release-infra": 1 },
  warnings: [],
}, null, 2)}\n`);
const processEnvOnlyResult = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: processEnvOnlyRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});
assert.equal(processEnvOnlyResult.status, 0, processEnvOnlyResult.stderr || processEnvOnlyResult.stdout);
const processEnvOnlyOwnerRollup = JSON.parse(fs.readFileSync(path.join(processEnvOnlyRoot, "release/owner-action-rollup.json"), "utf8"));
const processEnvOnlyPriority = JSON.parse(fs.readFileSync(path.join(processEnvOnlyRoot, "release/release-action-priority.json"), "utf8"));
const processEnvOnlyFinalGoNoGo = JSON.parse(fs.readFileSync(path.join(processEnvOnlyRoot, "release/release-final-go-no-go.json"), "utf8"));
assert.equal(processEnvOnlyOwnerRollup.owners["ai-owner"].pendingItems, 0);
assert.equal(processEnvOnlyOwnerRollup.owners["ai-owner"].collapsedItems, 1);
assert.equal(processEnvOnlyOwnerRollup.owners["release-infra"].collapsedSources["release-config"], 1);
assert.equal(processEnvOnlyPriority.bySource["release-config"], undefined);
assert.equal(processEnvOnlyPriority.items[0].source, "release-env-lint");
assert.equal(processEnvOnlyFinalGoNoGo.safetySignals.releaseEnvFile.envFilePresent, false);
assert.equal(processEnvOnlyFinalGoNoGo.nextCommands[0], "bash artifacts/ddd/release/release-preflight-gate.sh");
assert.equal(processEnvOnlyFinalGoNoGo.nextCommands[1], "bash artifacts/ddd/release/release-artifact-integrity-gate.sh");
assert.equal(processEnvOnlyFinalGoNoGo.nextCommands[2], "bash artifacts/ddd/release/release-final-owner-queue-env-init.sh");
assert.equal(processEnvOnlyFinalGoNoGo.ciSummary.firstNextCommand, "bash artifacts/ddd/release/release-preflight-gate.sh");

const broadEnvPermissionRoot = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-readiness-summary-env-permission-"));
fs.mkdirSync(path.join(broadEnvPermissionRoot, "release"), { recursive: true });
fs.writeFileSync(path.join(broadEnvPermissionRoot, "release/release-evidence-gate.json"), `${JSON.stringify({
  strict: true,
  summary: {
    checks: 1,
    blockers: 1,
    warnings: 0,
  },
  checks: [{
    name: "release-env-lint",
    status: "blocker",
    detail: "env file permissions are too broad",
    file: "release/release-env-lint.json",
  }],
  blockers: [
    "release-env-lint: env file permissions are too broad: /secure/.env.release mode=644; use chmod 600",
  ],
  warnings: [],
}, null, 2)}\n`);
fs.writeFileSync(path.join(broadEnvPermissionRoot, "release/release-env-lint.json"), `${JSON.stringify({
  status: "FAIL",
  envFile: "/secure/.env.release",
  inputKind: "release-env-file",
  generatedMissingTemplate: false,
  envFileSecurity: {
    checked: true,
    reason: "env-file",
    mode: 420,
    modeOctal: "644",
    permissionSafe: false,
    permissionCheckSkipped: false,
    generatedMissingTemplate: false,
    requiredMode: "600",
  },
  summary: {
    blockers: 1,
    warnings: 0,
    primaryBlockers: 1,
    envFileSecurityChecked: true,
    envFilePermissionSafe: false,
    envFilePermissionCheckSkipped: false,
    envFileModeOctal: "644",
    unresolvedTemplateKeys: 0,
    canonicalUnresolvedTemplateKeys: 0,
    releaseConfigBlockers: 0,
    releaseConfigBlockersFromPlaceholders: 0,
    releaseConfigBlockersAfterPlaceholders: 0,
  },
  keys: ["LUMIRA_BASE_URL"],
  canonicalKeys: ["LUMIRA_BASE_URL"],
  duplicateKeys: [],
  unresolvedTemplateKeys: [],
  canonicalUnresolvedTemplateKeys: [],
  canonicalMissingEnvKeys: [],
  canonicalReleaseConfigBlockerKeys: [],
  releaseConfigBlockerDetails: [],
  blockers: [
    "env file permissions are too broad: /secure/.env.release mode=644; use chmod 600",
  ],
  warnings: [],
}, null, 2)}\n`);
const broadEnvPermissionResult = spawnSync("node", ["scripts/ddd-release-readiness-summary.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_RELEASE_EVIDENCE_DIR: broadEnvPermissionRoot,
    DDD_EXPLAIN_DIR: explainDir,
  },
});
assert.equal(broadEnvPermissionResult.status, 0, broadEnvPermissionResult.stderr || broadEnvPermissionResult.stdout);
const broadEnvPermissionSummary = JSON.parse(fs.readFileSync(path.join(broadEnvPermissionRoot, "release/readiness-summary.json"), "utf8"));
const broadEnvPermissionPriority = JSON.parse(fs.readFileSync(path.join(broadEnvPermissionRoot, "release/release-action-priority.json"), "utf8"));
assert.equal(broadEnvPermissionSummary.diagnostics.releaseEnvLint.envFileSecurity.permissionSafe, false);
assert.equal(broadEnvPermissionSummary.diagnostics.releaseEnvLint.envFileSecurity.modeOctal, "644");
assert.equal(broadEnvPermissionSummary.diagnostics.releaseEnvLint.actionPlan.items[0].id, "release-env-lint-permissions");
assert.equal(broadEnvPermissionSummary.diagnostics.releaseEnvLint.actionPlan.items[0].reason, "envFileMode=644 requiredMode=600");
assert.match(broadEnvPermissionSummary.diagnostics.releaseEnvLint.actionPlan.items[0].action, /chmod 600/);
assert.equal(broadEnvPermissionPriority.items.some((item) => item.id === "release-env-lint-permissions"), true);

console.log("[ddd-release-readiness-summary.test] ok");
