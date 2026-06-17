#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { collectProvenanceIssues, evidenceValueIssue, redactLocalPaths } from "./ddd-release-evidence-utils.mjs";
import { requiredDockerImages } from "./ddd-docker-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const dockerCommand = process.env.DDD_DOCKER_COMMAND || "docker";
const outputDir = process.env.DDD_DOCKER_BUILD_DIR
  ? path.resolve(process.env.DDD_DOCKER_BUILD_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "build");
const outputFile = process.env.DDD_DOCKER_BUILD_REPORT
  ? path.resolve(process.env.DDD_DOCKER_BUILD_REPORT)
  : path.join(outputDir, "docker-image-evidence.json");
const tagPrefix = process.env.DDD_DOCKER_TAG_PREFIX || "lumira";
const tagSuffix = process.env.DDD_DOCKER_TAG_SUFFIX || `ddd-evidence-${Date.now()}`;
const noCache = process.env.DDD_DOCKER_NO_CACHE === "true";
const buildRetries = Math.max(0, Number.parseInt(process.env.DDD_DOCKER_BUILD_RETRIES || "2", 10) || 0);
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_DOCKER_BUILD_STRICT === "true";
const sourceEnvironment = process.env.DDD_DOCKER_BUILD_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";
const existingImageBuildEvidence = process.env.DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE || "";

function optionalBuildArg(envKey, argName) {
  const value = process.env[envKey];
  return typeof value === "string" && value.trim() !== "" ? { [argName]: value.trim() } : {};
}

function existingImageEnvKey(imageName) {
  return `DDD_DOCKER_EXISTING_${imageName.toUpperCase().replace(/[^A-Z0-9]+/g, "_")}_IMAGE`;
}

const images = requiredDockerImages.map((image) => ({
  ...image,
  tag: `${tagPrefix}/${image.name}:${tagSuffix}`,
  existingImageEnvKey: existingImageEnvKey(image.name),
  existingImage: process.env[existingImageEnvKey(image.name)]?.trim() || "",
  buildArgs: image.name === "lumira-server"
    ? {
        ...optionalBuildArg("DDD_DOCKER_MAVEN_IMAGE", "MAVEN_IMAGE"),
        ...optionalBuildArg("DDD_DOCKER_JRE_IMAGE", "JRE_IMAGE"),
        ...optionalBuildArg("DDD_DOCKER_OTEL_JAVAAGENT_URL", "OTEL_JAVAAGENT_URL"),
        SERVICE_DIR: "services/lumira-server",
        SERVICE_MODULE: "services/lumira-server",
      }
    : {
        ...optionalBuildArg("DDD_DOCKER_NODE_IMAGE", "NODE_IMAGE"),
        ...optionalBuildArg("DDD_DOCKER_NGINX_IMAGE", "NGINX_IMAGE"),
      },
}));

function run(command, args, options = {}) {
  const startedAt = Date.now();
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 20,
    ...options,
  });
  return {
    command,
    args,
    status: result.status,
    signal: result.signal,
    elapsedMs: Date.now() - startedAt,
    stdoutTail: redactOutput(tail(result.stdout || "")),
    stderrTail: redactOutput(tail(result.stderr || "")),
    error: result.error ? redactOutput(result.error.message) : null,
  };
}

function tail(text) {
  return text.split(/\r?\n/).slice(-80).join("\n").trim();
}

function redactOutput(text) {
  return redactLocalPaths(text, { repoRoot, homeDir: os.homedir() });
}

function isTransientDockerBuildFailure(result) {
  const text = `${result?.stdoutTail || ""}\n${result?.stderrTail || ""}\n${result?.error || ""}`;
  return /failed to fetch anonymous token|i\/o timeout|deadlineexceeded|tls handshake timeout|temporary failure|connection reset|connection refused|network is unreachable|service unavailable|too many requests/i.test(text);
}

function dockerRemediation(imageReports, blockerList) {
  const transientImages = imageReports
    .filter((image) => image.build?.transientFailure === true)
    .map((image) => ({
      name: image.name,
      attempts: image.build.attemptCount,
      retries: image.build.retryCount,
      dockerfile: image.dockerfile,
    }));
  const dockerUnavailable = blockerList.some((blocker) => /docker CLI is not available|docker daemon is not available/i.test(blocker));
  const hasTransientFailure = transientImages.length > 0;
  const nextActions = [];

  if (dockerUnavailable) {
    nextActions.push({
      id: "docker-daemon-ready",
      owner: "release-infra",
      action: "Start Docker Desktop or run this evidence script on a CI runner with Docker Buildx available.",
      envKeys: ["DDD_DOCKER_COMMAND"],
    });
  }
  if (hasTransientFailure) {
    nextActions.push({
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
      exampleCommand: [
        "DDD_DOCKER_BUILD_STRICT=true",
        "DDD_DOCKER_BUILD_RETRIES=4",
        "DDD_DOCKER_MAVEN_IMAGE=<registry>/maven:3.9.11-eclipse-temurin-21",
        "DDD_DOCKER_JRE_IMAGE=<registry>/eclipse-temurin:21-jre",
        "DDD_DOCKER_NODE_IMAGE=<registry>/node:22-bookworm-slim",
        "DDD_DOCKER_NGINX_IMAGE=<registry>/nginx:1.29-alpine",
        "node scripts/ddd-docker-build-evidence.mjs",
      ].join(" "),
    });
    nextActions.push({
      id: "docker-existing-image-inspect",
      owner: "release-infra",
      action: "If CI already built and pushed the release candidate images, pull them and rerun Docker evidence in explicit inspect-only mode.",
      envKeys: [
        "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE",
        "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
        "DDD_DOCKER_EXISTING_FRONTEND_IMAGE",
      ],
      exampleCommand: [
        "DDD_DOCKER_BUILD_STRICT=true",
        "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url>",
        "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate>",
        "DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate>",
        "node scripts/ddd-docker-build-evidence.mjs",
      ].join(" "),
    });
  }

  return {
    transientRegistryFailure: hasTransientFailure,
    dockerUnavailable,
    transientImages,
    nextActions,
  };
}

function waitBeforeRetry(attempt) {
  const delayMs = Math.min(1000 * attempt, 3000);
  if (delayMs <= 0) {
    return;
  }
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, delayMs);
}

function runDockerBuild(args) {
  const attempts = [];
  for (let attempt = 1; attempt <= buildRetries + 1; attempt += 1) {
    const result = run(dockerCommand, args);
    attempts.push({ attempt, ...result });
    if (result.status === 0 || !isTransientDockerBuildFailure(result) || attempt > buildRetries) {
      return {
        ...result,
        attempts,
        attemptCount: attempts.length,
        retryCount: attempts.length - 1,
        transientFailure: result.status !== 0 && isTransientDockerBuildFailure(result),
      };
    }
    waitBeforeRetry(attempt);
  }
  return attempts.at(-1);
}

function sha256(file) {
  return createHash("sha256").update(fs.readFileSync(path.join(repoRoot, file))).digest("hex");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function staticDockerfileEvidence(image) {
  const absolute = path.join(repoRoot, image.dockerfile);
  const issues = [];
  if (!fs.existsSync(absolute)) {
    return {
      status: "FAIL",
      exists: false,
      dockerfileSha256: null,
      issues: [`missing Dockerfile ${image.dockerfile}`],
      checks: {},
    };
  }
  const source = fs.readFileSync(absolute, "utf8");
  const checks = {
    exposesExpectedPort: new RegExp(`EXPOSE\\s+${escapeRegExp(image.expectedExposedPort.replace("/tcp", ""))}\\b`).test(source),
    definesEntrypointOrCmd: /\bENTRYPOINT\s+\[/.test(source) || /\bCMD\s+\[/.test(source),
    nonRootUser: image.requireNonRootUser ? /\bUSER\s+(?!root\b|0\b)\S+/.test(source) : true,
    createsApplicationUser: image.requireNonRootUser ? /\badduser\b|\buseradd\b/.test(source) : true,
    serviceModuleBuildArgs: image.name !== "lumira-server" || (/\bARG\s+SERVICE_MODULE\b/.test(source) && /\bARG\s+SERVICE_DIR\b/.test(source)),
    optionalOtelJavaagentDownload: image.name !== "lumira-server" || (/\bARG\s+OTEL_JAVAAGENT_URL=\s*(?:\r?\n|$)/.test(source) && /if \[ -n "\$OTEL_JAVAAGENT_URL" \]/.test(source)),
    otelJavaagentRuntimeGuard: image.name !== "lumira-server" || (/OTEL_JAVAAGENT_ENABLED/.test(source) && /\[\s+!\s+-s\s+\\?"\$OTEL_JAVAAGENT_PATH\\?"/.test(source)),
    frontendProductionBuild: image.name !== "frontend" || (/pnpm\s+install\s+--frozen-lockfile/.test(source) && /pnpm\s+build/.test(source)),
  };
  if (!checks.exposesExpectedPort) {
    issues.push(`Dockerfile must expose ${image.expectedExposedPort}`);
  }
  if (!checks.definesEntrypointOrCmd) {
    issues.push("Dockerfile must define ENTRYPOINT or CMD");
  }
  if (!checks.nonRootUser) {
    issues.push("Dockerfile must switch to a non-root USER");
  }
  if (!checks.createsApplicationUser) {
    issues.push("Dockerfile must create an application user");
  }
  if (!checks.serviceModuleBuildArgs) {
    issues.push("service Dockerfile must keep SERVICE_MODULE and SERVICE_DIR build args");
  }
  if (!checks.optionalOtelJavaagentDownload) {
    issues.push("service Dockerfile must keep OpenTelemetry javaagent download optional by default");
  }
  if (!checks.otelJavaagentRuntimeGuard) {
    issues.push("service Dockerfile must fail fast when OTEL_JAVAAGENT_ENABLED=true without a javaagent");
  }
  if (!checks.frontendProductionBuild) {
    issues.push("frontend Dockerfile must install with frozen lockfile and run production build");
  }
  return {
    status: issues.length === 0 ? "PASS" : "FAIL",
    exists: true,
    dockerfileSha256: sha256(image.dockerfile),
    issues,
    checks,
  };
}

function inspectImage(tag) {
  const result = run(dockerCommand, [
    "image",
    "inspect",
    tag,
    "--format",
    "{{json .}}",
  ]);
  if (result.status !== 0) {
    return { command: result, image: null };
  }
  try {
    const image = JSON.parse(result.stdoutTail);
    return {
      command: result,
      image: {
        id: image.Id,
        repoTags: image.RepoTags || [],
        size: image.Size || 0,
        created: image.Created || null,
        architecture: image.Architecture || null,
        os: image.Os || null,
        user: image.Config?.User || "",
        entrypoint: image.Config?.Entrypoint || [],
        cmd: image.Config?.Cmd || [],
        workingDir: image.Config?.WorkingDir || "",
        exposedPorts: Object.keys(image.Config?.ExposedPorts || {}).sort(),
      },
    };
  } catch (error) {
    return { command: result, image: null, parseError: error.message };
  }
}

const blockers = [];
const imageReports = [];

if (strictEvidence) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`docker build provenance ${issue}`);
  }
  if (images.some((image) => image.existingImage)) {
    const buildEvidenceIssue = evidenceValueIssue(existingImageBuildEvidence);
    if (buildEvidenceIssue) {
      blockers.push(`existing docker image build evidence ${buildEvidenceIssue}`);
    }
  }
}

const preflight = blockers.length === 0 ? {
  version: run(dockerCommand, ["--version"]),
  info: run(dockerCommand, ["info", "--format", "{{json .ServerVersion}}"]),
} : {
  version: null,
  info: null,
};

if (preflight.version && preflight.version.status !== 0) {
  blockers.push(`docker CLI is not available: ${preflight.version.error || preflight.version.stderrTail || "unknown error"}`);
}
if (preflight.info && preflight.info.status !== 0) {
  blockers.push(`docker daemon is not available: ${preflight.info.stderrTail || preflight.info.error || "docker info failed"}`);
}

if (blockers.length === 0) {
  for (const image of images) {
    const staticEvidence = staticDockerfileEvidence(image);
    if (!staticEvidence.exists) {
      blockers.push(`${image.name}: missing Dockerfile ${image.dockerfile}`);
      imageReports.push({ ...image, status: "FAIL", staticDockerfile: staticEvidence, blockers: [`missing Dockerfile ${image.dockerfile}`] });
      continue;
    }
    const args = [
      "build",
      "-f",
      image.dockerfile,
      "-t",
      image.tag,
      ...Object.entries(image.buildArgs).flatMap(([key, value]) => ["--build-arg", `${key}=${value}`]),
      ...(noCache ? ["--no-cache"] : []),
      ".",
    ];
    const usingExistingImage = image.existingImage.length > 0;
    const build = usingExistingImage ? null : runDockerBuild(args);
    const inspectTag = usingExistingImage ? image.existingImage : image.tag;
    const inspect = usingExistingImage || build.status === 0 ? inspectImage(inspectTag) : { command: null, image: null };
    const reportBlockers = [];
    if (!usingExistingImage && build.status !== 0) {
      reportBlockers.push(build.transientFailure
        ? `docker build failed after ${build.attemptCount} attempt(s) with transient registry/network error status ${build.status}`
        : `docker build failed with status ${build.status}`);
    }
    if (usingExistingImage && inspect.command?.status !== 0) {
      reportBlockers.push(`existing docker image inspect failed for ${image.existingImage}`);
    }
    for (const issue of staticEvidence.issues) {
      reportBlockers.push(`static Dockerfile check failed: ${issue}`);
    }
    if ((usingExistingImage || build.status === 0) && !inspect.image) {
      reportBlockers.push("docker image inspect did not return image metadata");
    }
    if (inspect.image && (!Number.isFinite(inspect.image.size) || inspect.image.size <= 0)) {
      reportBlockers.push("docker image size is missing");
    }
    if (inspect.image && image.expectedExposedPort && !inspect.image.exposedPorts.includes(image.expectedExposedPort)) {
      reportBlockers.push(`docker image does not expose ${image.expectedExposedPort}`);
    }
    if (inspect.image && image.requireNonRootUser && (!inspect.image.user || inspect.image.user === "root" || inspect.image.user === "0")) {
      reportBlockers.push("docker image must run as a non-root user");
    }
    if (inspect.image && inspect.image.entrypoint.length === 0 && inspect.image.cmd.length === 0) {
      reportBlockers.push("docker image has no entrypoint or command");
    }
    blockers.push(...reportBlockers.map((blocker) => `${image.name}: ${blocker}`));
    imageReports.push({
      name: image.name,
      dockerfile: image.dockerfile,
      dockerfileSha256: staticEvidence.dockerfileSha256,
      tag: inspectTag,
      targetTag: image.tag,
      evidenceMode: usingExistingImage ? "existing-image" : "build",
      existingImageEnvKey: image.existingImageEnvKey,
      buildArgs: image.buildArgs,
      expectedExposedPort: image.expectedExposedPort,
      requireNonRootUser: image.requireNonRootUser,
      staticDockerfile: staticEvidence,
      status: reportBlockers.length === 0 ? "PASS" : "FAIL",
      build,
      inspect,
      blockers: reportBlockers,
    });
  }
} else {
  const preflightBlockers = blockers.length > 0 ? blockers : ["docker preflight failed"];
  for (const image of images) {
    const staticEvidence = staticDockerfileEvidence(image);
    imageReports.push({
      name: image.name,
      dockerfile: image.dockerfile,
      dockerfileSha256: staticEvidence.dockerfileSha256,
      tag: image.tag,
      targetTag: image.tag,
      evidenceMode: "skipped",
      existingImageEnvKey: image.existingImageEnvKey,
      buildArgs: image.buildArgs,
      expectedExposedPort: image.expectedExposedPort,
      requireNonRootUser: image.requireNonRootUser,
      staticDockerfile: staticEvidence,
      status: "SKIPPED",
      skipReason: preflightBlockers.join("; "),
      build: null,
      inspect: null,
      blockers: preflightBlockers,
    });
  }
}

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  dockerCommand,
  noCache,
  existingImageBuildEvidence: existingImageBuildEvidence ? redactOutput(existingImageBuildEvidence) : null,
  preflight,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  summary: {
    images: imageReports.length,
    passed: imageReports.filter((image) => image.status === "PASS").length,
    failed: imageReports.filter((image) => image.status === "FAIL").length,
    skipped: imageReports.filter((image) => image.status === "SKIPPED").length,
    blockers: blockers.length,
  },
  images: imageReports,
  blockers,
};
artifact.remediation = dockerRemediation(imageReports, blockers);

fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-docker-build-evidence] ${blocker}`);
  }
  console.error(`[ddd-docker-build-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-docker-build-evidence] docker image evidence passed; images=${artifact.summary.images}; artifact=${outputFile}`);
