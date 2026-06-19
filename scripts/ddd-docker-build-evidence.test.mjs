#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { requiredDockerImages } from "./ddd-docker-evidence-contract.mjs";

const repoRoot = path.resolve(import.meta.dirname, "..");
function pathForBash(file) {
  const resolved = path.resolve(file);
  const match = /^([A-Za-z]):\\(.*)$/.exec(resolved);
  if (!match) return file.replaceAll("\\", "/");
  return `/mnt/${match[1].toLowerCase()}/${match[2].replaceAll("\\", "/")}`;
}

const helpOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-help-"));
const helpResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs", "--help"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: helpOutputDir,
  },
});
assert.equal(helpResult.status, 0, helpResult.stderr || helpResult.stdout);
assert.match(helpResult.stdout, /DDD Docker image evidence/);
assert.match(helpResult.stdout, /--check/);
assert.equal(fs.existsSync(path.join(helpOutputDir, "docker-image-evidence.json")), false, "docker evidence help should not write an artifact");

const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-"));
const result = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: outputDir,
    DDD_DOCKER_COMMAND: "definitely-not-a-docker-binary",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});

assert.notEqual(result.status, 0);
assert.match(result.stderr, /docker CLI is not available/);

const artifact = JSON.parse(fs.readFileSync(path.join(outputDir, "docker-image-evidence.json"), "utf8"));
assert.equal(artifact.status, "FAIL");
assert.equal(artifact.summary.skipped, requiredDockerImages.length);
assert.equal(artifact.summary.failed, 0);
assert(artifact.blockers.some((blocker) => blocker.includes("docker CLI is not available")));
assert.equal(artifact.remediation.dockerUnavailable, true);
assert.equal(artifact.remediation.transientRegistryFailure, false);
assert(artifact.remediation.nextActions.some((action) => action.id === "docker-daemon-ready"));
assert.deepEqual(
  artifact.images.map((image) => image.name),
  requiredDockerImages.map((image) => image.name),
);
for (const image of artifact.images) {
  assert.equal(image.status, "SKIPPED");
  assert.match(image.skipReason, /docker CLI is not available/);
  assert(image.blockers.some((blocker) => blocker.includes("docker CLI is not available")));
  assert.equal(image.staticDockerfile.status, "PASS");
  assert.equal(image.staticDockerfile.exists, true);
  assert.equal(image.staticDockerfile.checks.exposesExpectedPort, true);
  assert.equal(image.staticDockerfile.checks.definesEntrypointOrCmd, true);
}
assert.equal(JSON.stringify(artifact).includes(os.homedir()), false, "docker evidence artifact must not leak the local home path");

const unavailableCheckDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-unavailable-check-"));
const unavailableCheckResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs", "--check"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: unavailableCheckDir,
    DDD_DOCKER_COMMAND: "definitely-not-a-docker-binary",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});
assert.notEqual(unavailableCheckResult.status, 0);
const unavailableCheck = JSON.parse(unavailableCheckResult.stdout);
assert.equal(unavailableCheck.status, "BLOCKED");
assert.equal(unavailableCheck.willWriteFiles, false);
assert.equal(unavailableCheck.recommendedMode, "external-runner-required");
assert.equal(unavailableCheck.remediation.dockerUnavailable, true);
assert(unavailableCheck.remediation.nextActions.some((action) => action.id === "docker-daemon-ready"));
const unavailableExistingInspect = unavailableCheck.remediation.nextActions.find((action) => action.id === "docker-existing-image-inspect");
assert(unavailableExistingInspect);
assert.match(unavailableExistingInspect.exampleCommand, /DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url>/);
assert.equal(fs.existsSync(path.join(unavailableCheckDir, "docker-image-evidence.json")), false, "docker evidence --check should not write an artifact when Docker is unavailable");

const retryOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-retry-"));
const fakeDocker = path.join(retryOutputDir, "docker-fake.sh");
const fakeState = path.join(retryOutputDir, "build-count");
fs.writeFileSync(fakeState, "0");
fs.writeFileSync(fakeDocker, `#!/usr/bin/env bash
set -euo pipefail
state=${JSON.stringify(pathForBash(fakeState))}
if [[ "$1" == "--version" ]]; then
  echo "Docker version fake"
  exit 0
fi
if [[ "$1" == "info" ]]; then
  echo '"29.4.1"'
  exit 0
fi
if [[ "$1" == "build" ]]; then
  count=$(cat "$state")
  count=$((count + 1))
  echo "$count" > "$state"
  if [[ "$count" == "1" ]]; then
    echo 'failed to fetch anonymous token: dial tcp 108.160.170.43:443: i/o timeout' >&2
    exit 1
  fi
  exit 0
fi
if [[ "$1" == "image" && "$2" == "inspect" ]]; then
  tag="$3"
  if [[ "$tag" == *"/lumira-server:"* ]]; then
    printf '%s\\n' '{"Id":"sha256:server","RepoTags":["lumira/lumira-server:test"],"Size":123456,"Created":"2026-01-01T00:00:00Z","Architecture":"arm64","Os":"linux","Config":{"User":"lumira","Entrypoint":["java"],"Cmd":[],"WorkingDir":"/app","ExposedPorts":{"8080/tcp":{}}}}'
  else
    printf '%s\\n' '{"Id":"sha256:frontend","RepoTags":["lumira/frontend:test"],"Size":123456,"Created":"2026-01-01T00:00:00Z","Architecture":"arm64","Os":"linux","Config":{"User":"","Entrypoint":[],"Cmd":["nginx"],"WorkingDir":"","ExposedPorts":{"80/tcp":{}}}}'
  fi
  exit 0
fi
echo "unexpected fake docker args: $*" >&2
exit 2
`);
fs.chmodSync(fakeDocker, 0o755);

const retryResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: retryOutputDir,
    DDD_DOCKER_COMMAND: fakeDocker,
    DDD_DOCKER_TAG_SUFFIX: "test",
    DDD_DOCKER_BUILD_RETRIES: "1",
    DDD_DOCKER_MAVEN_IMAGE: "registry.local/maven:3.9.11-eclipse-temurin-21",
    DDD_DOCKER_JRE_IMAGE: "registry.local/eclipse-temurin:21-jre",
    DDD_DOCKER_OTEL_JAVAAGENT_URL: "https://artifacts.local/opentelemetry-javaagent.jar",
    DDD_DOCKER_NODE_IMAGE: "registry.local/node:22-bookworm-slim",
    DDD_DOCKER_NGINX_IMAGE: "registry.local/nginx:1.29-alpine",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});

assert.equal(retryResult.status, 0, retryResult.stderr || retryResult.stdout);
const retryArtifact = JSON.parse(fs.readFileSync(path.join(retryOutputDir, "docker-image-evidence.json"), "utf8"));
assert.equal(retryArtifact.status, "PASS");
assert.equal(retryArtifact.summary.passed, requiredDockerImages.length);
assert.equal(retryArtifact.images[0].build.attemptCount, 2);
assert.equal(retryArtifact.images[0].build.retryCount, 1);
assert.equal(retryArtifact.images[0].build.attempts[0].transientFailure, undefined);
assert.match(retryArtifact.images[0].build.attempts[0].stderrTail, /i\/o timeout/);
assert.equal(retryArtifact.images.find((image) => image.name === "lumira-server").buildArgs.MAVEN_IMAGE, "registry.local/maven:3.9.11-eclipse-temurin-21");
assert.equal(retryArtifact.images.find((image) => image.name === "lumira-server").buildArgs.JRE_IMAGE, "registry.local/eclipse-temurin:21-jre");
assert.equal(retryArtifact.images.find((image) => image.name === "lumira-server").buildArgs.OTEL_JAVAAGENT_URL, "https://artifacts.local/opentelemetry-javaagent.jar");
assert.equal(retryArtifact.images.find((image) => image.name === "frontend").buildArgs.NODE_IMAGE, "registry.local/node:22-bookworm-slim");
assert.equal(retryArtifact.images.find((image) => image.name === "frontend").buildArgs.NGINX_IMAGE, "registry.local/nginx:1.29-alpine");
assert.equal(retryArtifact.remediation.transientRegistryFailure, false);

const existingImageOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-existing-"));
const fakeExistingDocker = path.join(existingImageOutputDir, "docker-fake.sh");
fs.writeFileSync(fakeExistingDocker, `#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "--version" ]]; then
  echo "Docker version fake"
  exit 0
fi
if [[ "$1" == "info" ]]; then
  echo '"29.4.1"'
  exit 0
fi
if [[ "$1" == "build" ]]; then
  echo "build should not be called for existing image evidence" >&2
  exit 9
fi
if [[ "$1" == "image" && "$2" == "inspect" ]]; then
  tag="$3"
  if [[ "$tag" == "registry.local/lumira-server:rc1" ]]; then
    printf '%s\\n' '{"Id":"sha256:server-existing","RepoTags":["registry.local/lumira-server:rc1"],"Size":123456,"Created":"2026-01-01T00:00:00Z","Architecture":"arm64","Os":"linux","Config":{"User":"lumira","Entrypoint":["java"],"Cmd":[],"WorkingDir":"/app","ExposedPorts":{"8080/tcp":{}}}}'
  elif [[ "$tag" == "registry.local/frontend:rc1" ]]; then
    printf '%s\\n' '{"Id":"sha256:frontend-existing","RepoTags":["registry.local/frontend:rc1"],"Size":123456,"Created":"2026-01-01T00:00:00Z","Architecture":"arm64","Os":"linux","Config":{"User":"","Entrypoint":[],"Cmd":["nginx"],"WorkingDir":"","ExposedPorts":{"80/tcp":{}}}}'
  else
    echo "unexpected inspect tag: $tag" >&2
    exit 3
  fi
  exit 0
fi
echo "unexpected fake docker args: $*" >&2
exit 2
`);
fs.chmodSync(fakeExistingDocker, 0o755);

const existingImageCheckDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-existing-check-"));
const existingImageCheckResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs", "--check"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: existingImageCheckDir,
    DDD_DOCKER_COMMAND: fakeExistingDocker,
    DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE: "registry.local/lumira-server:rc1",
    DDD_DOCKER_EXISTING_FRONTEND_IMAGE: "registry.local/frontend:rc1",
    DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE: "gh-run-12345-artifacts/docker-build-provenance.json",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});
assert.equal(existingImageCheckResult.status, 0, existingImageCheckResult.stderr || existingImageCheckResult.stdout);
const existingImageCheck = JSON.parse(existingImageCheckResult.stdout);
assert.equal(existingImageCheck.status, "PASS");
assert.equal(existingImageCheck.willWriteFiles, false);
assert.equal(existingImageCheck.recommendedMode, "existing-image-inspect");
assert.equal(existingImageCheck.dockerAvailable, true);
assert.equal(existingImageCheck.existingImageInputs.length, requiredDockerImages.length);
assert(existingImageCheck.existingImageInputs.every((image) => image.valuePresent === true));
assert.match(existingImageCheck.nextCommand, /DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>\/lumira-server:<release-candidate>/);
assert.equal(fs.existsSync(path.join(existingImageCheckDir, "docker-image-evidence.json")), false, "docker evidence --check should not write an artifact");

const existingImageResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: existingImageOutputDir,
    DDD_DOCKER_COMMAND: fakeExistingDocker,
    DDD_DOCKER_TAG_SUFFIX: "test",
    DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE: "registry.local/lumira-server:rc1",
    DDD_DOCKER_EXISTING_FRONTEND_IMAGE: "registry.local/frontend:rc1",
    DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE: "gh-run-12345-artifacts/docker-build-provenance.json",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});

assert.equal(existingImageResult.status, 0, existingImageResult.stderr || existingImageResult.stdout);
const existingImageArtifact = JSON.parse(fs.readFileSync(path.join(existingImageOutputDir, "docker-image-evidence.json"), "utf8"));
assert.equal(existingImageArtifact.status, "PASS");
assert.equal(existingImageArtifact.summary.passed, requiredDockerImages.length);
assert.equal(existingImageArtifact.existingImageBuildEvidence, "gh-run-12345-artifacts/docker-build-provenance.json");
for (const image of existingImageArtifact.images) {
  assert.equal(image.evidenceMode, "existing-image");
  assert.equal(image.build, null);
  assert.equal(image.inspect.command.status, 0);
  assert.equal(image.tag, image.name === "lumira-server" ? "registry.local/lumira-server:rc1" : "registry.local/frontend:rc1");
  assert.match(image.targetTag, new RegExp(`lumira/${image.name}:test`));
  assert.match(image.existingImageEnvKey, /^DDD_DOCKER_EXISTING_/);
}

const transientFailureOutputDir = fs.mkdtempSync(path.join(os.tmpdir(), "lumira-docker-evidence-transient-"));
const fakeFailingDocker = path.join(transientFailureOutputDir, "docker-fake.sh");
fs.writeFileSync(fakeFailingDocker, `#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "--version" ]]; then
  echo "Docker version fake"
  exit 0
fi
if [[ "$1" == "info" ]]; then
  echo '"29.4.1"'
  exit 0
fi
if [[ "$1" == "build" ]]; then
  echo 'failed to fetch anonymous token: net/http: request canceled while waiting for connection (Client.Timeout exceeded while awaiting headers)' >&2
  exit 1
fi
echo "unexpected fake docker args: $*" >&2
exit 2
`);
fs.chmodSync(fakeFailingDocker, 0o755);

const transientFailureResult = spawnSync("node", ["scripts/ddd-docker-build-evidence.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  env: {
    ...process.env,
    DDD_DOCKER_BUILD_DIR: transientFailureOutputDir,
    DDD_DOCKER_COMMAND: fakeFailingDocker,
    DDD_DOCKER_TAG_SUFFIX: "test",
    DDD_DOCKER_BUILD_RETRIES: "2",
    DDD_RELEASE_EVIDENCE_STRICT: "true",
    DDD_EVIDENCE_ENVIRONMENT: "docker-contract-test",
    DDD_RELEASE_CANDIDATE: "docker-contract-sha",
    DDD_EVIDENCE_OPERATOR: "docker-contract-runner",
  },
});

assert.notEqual(transientFailureResult.status, 0);
const transientFailureArtifact = JSON.parse(fs.readFileSync(path.join(transientFailureOutputDir, "docker-image-evidence.json"), "utf8"));
assert.equal(transientFailureArtifact.status, "FAIL");
assert.equal(transientFailureArtifact.remediation.transientRegistryFailure, true);
assert.equal(transientFailureArtifact.remediation.dockerUnavailable, false);
assert.equal(transientFailureArtifact.remediation.transientImages.length, requiredDockerImages.length);
assert(transientFailureArtifact.remediation.transientImages.every((image) => image.attempts === 3));
assert(transientFailureArtifact.remediation.nextActions.some((action) => action.id === "docker-registry-mirror-retry"));
const mirrorRetryAction = transientFailureArtifact.remediation.nextActions.find((action) => action.id === "docker-registry-mirror-retry");
assert.deepEqual(mirrorRetryAction.envKeys, [
  "DDD_DOCKER_BUILD_RETRIES",
  "DDD_DOCKER_MAVEN_IMAGE",
  "DDD_DOCKER_JRE_IMAGE",
  "DDD_DOCKER_NODE_IMAGE",
  "DDD_DOCKER_NGINX_IMAGE",
]);
assert.match(mirrorRetryAction.exampleCommand, /DDD_DOCKER_BUILD_RETRIES=4/);
assert.match(mirrorRetryAction.exampleCommand, /DDD_DOCKER_MAVEN_IMAGE=<registry>\/maven:3\.9\.11-eclipse-temurin-21/);
assert.match(mirrorRetryAction.exampleCommand, /node scripts\/ddd-docker-build-evidence\.mjs/);
const existingInspectAction = transientFailureArtifact.remediation.nextActions.find((action) => action.id === "docker-existing-image-inspect");
assert(existingInspectAction);
assert.deepEqual(existingInspectAction.envKeys, [
  "DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE",
  "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
  "DDD_DOCKER_EXISTING_FRONTEND_IMAGE",
]);
assert.match(existingInspectAction.exampleCommand, /DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url>/);
assert.match(existingInspectAction.exampleCommand, /DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>\/lumira-server:<release-candidate>/);

console.log("[ddd-docker-build-evidence.test] ok");
