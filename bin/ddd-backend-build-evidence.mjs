#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { performance } from "node:perf_hooks";
import { collectProvenanceIssues, redactLocalPaths } from "./ddd-release-evidence-utils.mjs";
import { requiredBackendModules } from "./ddd-backend-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_BACKEND_BUILD_DIR
  ? path.resolve(process.env.DDD_BACKEND_BUILD_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "build");
const outputFile = process.env.DDD_BACKEND_BUILD_REPORT
  ? path.resolve(process.env.DDD_BACKEND_BUILD_REPORT)
  : path.join(outputDir, "backend-build-evidence.json");
const skipBuild = process.env.DDD_BACKEND_BUILD_SKIP_RUN === "true";
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === "true" || process.env.DDD_BACKEND_BUILD_STRICT === "true";
const sourceEnvironment = process.env.DDD_BACKEND_BUILD_ENVIRONMENT || process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || "";
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || "";
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || "";

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function tail(text, max = 12000) {
  if (!text) {
    return "";
  }
  const value = text.length <= max ? text : text.slice(text.length - max);
  return redactLocalPaths(value, { repoRoot, homeDir: process.env.HOME || "" });
}

function runBuild() {
  if (skipBuild) {
    return {
      skipped: true,
      status: "SKIPPED",
      command: "./mvnw -DskipTests package",
      exitCode: null,
      durationMs: 0,
      stdoutTail: "",
      stderrTail: "",
    };
  }
  const startedAt = performance.now();
  const result = spawnSync("./mvnw", ["-DskipTests", "package"], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 40 * 1024 * 1024,
  });
  return {
    skipped: false,
    status: result.status === 0 ? "PASS" : "FAIL",
    command: "./mvnw -DskipTests package",
    exitCode: result.status,
    signal: result.signal || null,
    durationMs: Math.round((performance.now() - startedAt) * 100) / 100,
    stdoutTail: tail(result.stdout),
    stderrTail: tail(result.stderr),
  };
}

function moduleEvidence(item) {
  const moduleDir = path.join(repoRoot, item.module);
  const classesDir = path.join(moduleDir, "target", "classes");
  const targetDir = path.join(moduleDir, "target");
  const jars = fs.existsSync(targetDir)
    ? fs.readdirSync(targetDir)
      .filter((file) => file.endsWith(".jar") && !file.endsWith("-sources.jar") && !file.endsWith("-javadoc.jar"))
      .sort()
      .map((file) => path.join(targetDir, file))
    : [];
  return {
    module: item.module,
    deployable: item.deployable,
    entrypoint: item.entrypoint,
    entrypointPresent: item.entrypoint ? fs.existsSync(path.join(moduleDir, item.entrypoint)) : true,
    classesPresent: fs.existsSync(classesDir),
    jarPresent: jars.length > 0,
    jars: jars.map((file) => ({
      file: path.relative(repoRoot, file),
      bytes: fs.statSync(file).size,
      sha256: sha256(file),
    })),
  };
}

const blockers = [];

if (strictEvidence) {
  for (const issue of collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })) {
    blockers.push(`backend build provenance ${issue}`);
  }
}

const build = blockers.length === 0 ? runBuild() : {
  skipped: true,
  status: "SKIPPED",
  command: "./mvnw -DskipTests package",
  exitCode: null,
  durationMs: 0,
  stdoutTail: "",
  stderrTail: "",
};
const modules = blockers.length === 0 ? requiredBackendModules.map(moduleEvidence) : [];

if (strictEvidence && build.skipped) {
  blockers.push("backend package run was skipped; strict release requires a fresh Maven package execution");
}
if (build.status === "FAIL") {
  blockers.push(`backend package failed with exitCode=${build.exitCode}`);
}
for (const module of modules) {
  if (!module.entrypointPresent) {
    blockers.push(`${module.module} missing Spring Boot entrypoint ${module.entrypoint}`);
  }
  if (!module.classesPresent) {
    blockers.push(`${module.module} missing target/classes`);
  }
  if (!module.jarPresent) {
    blockers.push(`${module.module} missing target jar`);
  }
}

const artifact = {
  generatedAt: new Date().toISOString(),
  sourceEnvironment: sourceEnvironment || null,
  releaseCandidate: releaseCandidate || null,
  evidenceOperator: evidenceOperator || null,
  status: blockers.length === 0 ? "PASS" : "FAIL",
  build,
  summary: {
    modules: modules.length,
    deployableModules: modules.filter((module) => module.deployable).length,
    jars: modules.reduce((sum, module) => sum + module.jars.length, 0),
    missingEntrypoints: modules.filter((module) => !module.entrypointPresent).length,
    missingClasses: modules.filter((module) => !module.classesPresent).length,
    missingJars: modules.filter((module) => !module.jarPresent).length,
  },
  modules,
  blockers,
};

fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, `${JSON.stringify(artifact, null, 2)}\n`);

if (blockers.length > 0) {
  for (const blocker of blockers) {
    console.error(`[ddd-backend-build-evidence] ${blocker}`);
  }
  console.error(`[ddd-backend-build-evidence] wrote artifact to ${outputFile}`);
  process.exit(1);
}

console.log(`[ddd-backend-build-evidence] backend build evidence passed; jars=${artifact.summary.jars}; artifact=${outputFile}`);
