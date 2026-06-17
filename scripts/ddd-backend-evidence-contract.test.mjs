#!/usr/bin/env node

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  requiredBackendModules,
  requiredBackendTestClasses,
  validateBackendBuildArtifact,
  validateBackendTestArtifact,
} from "./ddd-backend-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");

function walk(directory, predicate, files = []) {
  if (!fs.existsSync(directory)) {
    return files;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, predicate, files);
    } else if (entry.isFile() && predicate(fullPath)) {
      files.push(fullPath);
    }
  }
  return files;
}

function testClassName(file) {
  const normalized = file.replaceAll(path.sep, "/");
  return normalized
    .replace(/^.*\/src\/test\/java\//, "")
    .replace(/\.java$/, "")
    .replaceAll("/", ".");
}

function testClassesMatching(predicate) {
  return walk(
    path.join(repoRoot, "services"),
    predicate,
  ).map(testClassName).sort();
}

function testArtifact() {
  return {
    status: "PASS",
    summary: {
      suites: requiredBackendTestClasses.length,
      tests: requiredBackendTestClasses.length,
      failures: 0,
      errors: 0,
      skipped: 0,
      required: requiredBackendTestClasses.length,
      requiredPresent: requiredBackendTestClasses.length,
      requiredMissing: 0,
    },
    suites: requiredBackendTestClasses.map((name) => ({
      name,
      tests: 1,
      failures: 0,
      errors: 0,
      skipped: 0,
    })),
  };
}

function buildArtifact() {
  return {
    status: "PASS",
    summary: {
      modules: requiredBackendModules.length,
      deployableModules: requiredBackendModules.filter((item) => item.deployable).length,
      jars: requiredBackendModules.length,
      missingEntrypoints: 0,
      missingClasses: 0,
      missingJars: 0,
    },
    build: {
      skipped: false,
      status: "PASS",
    },
    modules: requiredBackendModules.map((item) => ({
      module: item.module,
      entrypoint: item.entrypoint,
      deployable: item.deployable,
      entrypointPresent: true,
      classesPresent: true,
      jarPresent: true,
      jars: [
        {
          file: `${item.module}/target/${item.module.split("/").at(-1)}-0.1.0.jar`,
          bytes: 100,
          sha256: "a".repeat(64),
        },
      ],
    })),
  };
}

assert.deepEqual(validateBackendTestArtifact(testArtifact()), []);
assert.deepEqual(validateBackendBuildArtifact(buildArtifact()), []);

{
  const domainModelTests = testClassesMatching((file) => file.endsWith("DomainModelsTest.java"));

  assert(domainModelTests.length > 0);
  assert.deepEqual(
    domainModelTests.filter((name) => !requiredBackendTestClasses.includes(name)),
    [],
    "every bounded-context DomainModelsTest must be a required backend release test",
  );
}

{
  const architectureTests = testClassesMatching((file) => (
    file.endsWith("ArchitectureBoundaryTest.java") || file.endsWith("ArchitectureContractTest.java")
  ));

  assert(architectureTests.length > 0);
  assert.deepEqual(
    architectureTests.filter((name) => !requiredBackendTestClasses.includes(name)),
    [],
    "every DDD architecture boundary/contract test must be a required backend release test",
  );
}

{
  const artifact = testArtifact();
  artifact.suites = artifact.suites.filter((suite) => suite.name !== requiredBackendTestClasses[0]);
  assert.deepEqual(validateBackendTestArtifact(artifact), [
    `backend test summary suites mismatch: declared=${requiredBackendTestClasses.length}, actual=${requiredBackendTestClasses.length - 1}`,
    `backend test summary tests mismatch: declared=${requiredBackendTestClasses.length}, actual=${requiredBackendTestClasses.length - 1}`,
    `backend test summary requiredPresent mismatch: declared=${requiredBackendTestClasses.length}, actual=${requiredBackendTestClasses.length - 1}`,
    "backend test summary requiredMissing mismatch: declared=0, actual=1",
    "missing required test classes=1",
  ]);
}

{
  const artifact = testArtifact();
  artifact.suites[0].failures = 2;
  artifact.suites[1].errors = 1;
  assert.deepEqual(validateBackendTestArtifact(artifact), [
    "backend test summary failures mismatch: declared=0, actual=2",
    "backend test summary errors mismatch: declared=0, actual=1",
    "failures=2, errors=1",
  ]);
}

{
  const artifact = testArtifact();
  artifact.suites[0].tests = 0;
  artifact.summary.tests = requiredBackendTestClasses.length - 1;
  assert.deepEqual(validateBackendTestArtifact(artifact), [
    `required test classes with zero tests=${requiredBackendTestClasses[0]}`,
  ]);
}

{
  const artifact = testArtifact();
  artifact.suites[0].skipped = 1;
  artifact.summary.skipped = 1;
  assert.deepEqual(validateBackendTestArtifact(artifact), [
    `required test classes fully skipped=${requiredBackendTestClasses[0]}`,
  ]);
}

{
  const artifact = testArtifact();
  artifact.reportRoot = repoRoot;
  artifact.suites[0].file = path.join(repoRoot, "services/system-service/target/surefire-reports/TEST-example.xml");
  const issues = validateBackendTestArtifact(artifact);
  assert(issues.includes("backend test reportRoot must not include local repo path"));
  assert(issues.includes(`${requiredBackendTestClasses[0]} file must not include local repo path`));
}

{
  const artifact = buildArtifact();
  artifact.build = {
    skipped: true,
    status: "SKIPPED",
  };
  assert.deepEqual(validateBackendBuildArtifact(artifact), [
    "backend package run was skipped",
    "backend package status=SKIPPED",
  ]);
}

{
  const artifact = buildArtifact();
  artifact.build.stdoutTail = `Building jar: ${repoRoot}/services/lumira-server/target/lumira-server.jar`;
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes("backend package stdoutTail must not include local repo path"));
}

{
  const artifact = buildArtifact();
  artifact.build.status = "FAIL";
  assert.deepEqual(validateBackendBuildArtifact(artifact), [
    "backend package status=FAIL",
  ]);
}

{
  const artifact = buildArtifact();
  artifact.modules = artifact.modules.filter((item) => item.module !== "services/ai-service");
  assert.deepEqual(validateBackendBuildArtifact(artifact), [
    `backend build summary modules mismatch: declared=${requiredBackendModules.length}, actual=${requiredBackendModules.length - 1}`,
    `backend build summary deployableModules mismatch: declared=9, actual=8`,
    `backend build summary jars mismatch: declared=${requiredBackendModules.length}, actual=${requiredBackendModules.length - 1}`,
    "missing module report services/ai-service",
  ]);
}

{
  const artifact = buildArtifact();
  artifact.modules.push({ ...artifact.modules[0] });
  artifact.summary.modules += 1;
  artifact.summary.deployableModules += 1;
  artifact.summary.jars += 1;
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes("duplicate module report services/lumira-server"));
}

{
  const artifact = buildArtifact();
  artifact.modules.push({
    module: "services/unknown-service",
    entrypoint: "src/main/java/com/lumira/unknown/UnknownApplication.java",
    deployable: true,
    entrypointPresent: true,
    classesPresent: true,
    jarPresent: true,
    jars: [{ file: "services/unknown-service/target/unknown.jar", bytes: 100, sha256: "b".repeat(64) }],
  });
  artifact.summary.modules += 1;
  artifact.summary.deployableModules += 1;
  artifact.summary.jars += 1;
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes("unknown module report services/unknown-service"));
}

{
  const artifact = buildArtifact();
  artifact.modules[0].entrypointPresent = false;
  artifact.modules[1].classesPresent = false;
  artifact.modules[2].jarPresent = false;
  artifact.modules[2].jars = [];
  assert.deepEqual(validateBackendBuildArtifact(artifact), [
    "backend build summary jars mismatch: declared=10, actual=9",
    "backend build summary missingEntrypoints mismatch: declared=0, actual=1",
    "backend build summary missingClasses mismatch: declared=0, actual=1",
    "backend build summary missingJars mismatch: declared=0, actual=1",
    "services/lumira-server missing Spring Boot entrypoint src/main/java/com/lumira/server/LumiraServerApplication.java",
    "services/auth-service missing target/classes",
    "services/message-service missing target jar",
  ]);
}

{
  const artifact = buildArtifact();
  artifact.modules[1].deployable = false;
  artifact.modules[1].entrypoint = "src/main/java/com/lumira/auth/WrongApplication.java";
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes("backend build summary deployableModules mismatch: declared=9, actual=8"));
  assert(issues.includes("services/auth-service deployable must be true"));
  assert(issues.includes("services/auth-service entrypoint must be src/main/java/com/lumira/auth/AuthServiceApplication.java"));
}

{
  const artifact = buildArtifact();
  artifact.modules[0].jars = [
    {
      file: "",
      bytes: 0,
      sha256: "not-a-sha",
    },
  ];
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes("services/lumira-server jar file path is required"));
  assert(issues.includes("services/lumira-server jar <unknown> bytes must be positive"));
  assert(issues.includes("services/lumira-server jar <unknown> sha256 must be 64 hex characters"));
}

{
  const artifact = testArtifact();
  artifact.summary = {
    ...artifact.summary,
    suites: 0,
    tests: 0,
    required: 0,
  };
  const issues = validateBackendTestArtifact(artifact);
  assert(issues.includes(`backend test summary suites mismatch: declared=0, actual=${requiredBackendTestClasses.length}`));
  assert(issues.includes(`backend test summary tests mismatch: declared=0, actual=${requiredBackendTestClasses.length}`));
  assert(issues.includes(`backend test summary required mismatch: declared=0, actual=${requiredBackendTestClasses.length}`));
}

{
  const artifact = buildArtifact();
  artifact.summary = {
    ...artifact.summary,
    modules: 0,
    deployableModules: 0,
    jars: 0,
  };
  const issues = validateBackendBuildArtifact(artifact);
  assert(issues.includes(`backend build summary modules mismatch: declared=0, actual=${requiredBackendModules.length}`));
  assert(issues.includes("backend build summary deployableModules mismatch: declared=0, actual=9"));
  assert(issues.includes(`backend build summary jars mismatch: declared=0, actual=${requiredBackendModules.length}`));
}

console.log("[ddd-backend-evidence-contract.test] ok");
