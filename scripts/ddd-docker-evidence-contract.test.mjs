#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  requiredDockerImages,
  validateDockerBuildArtifact,
} from "./ddd-docker-evidence-contract.mjs";

function imageReport(required, overrides = {}) {
  return {
    name: required.name,
    dockerfile: required.dockerfile,
    expectedExposedPort: required.expectedExposedPort,
    requireNonRootUser: required.requireNonRootUser,
    staticDockerfile: {
      status: "PASS",
      exists: true,
      dockerfileSha256: "a".repeat(64),
      issues: [],
      checks: {
        exposesExpectedPort: true,
        definesEntrypointOrCmd: true,
        nonRootUser: true,
      },
    },
    dockerfileSha256: "a".repeat(64),
    tag: `lumira/${required.name}:test`,
    status: "PASS",
    inspect: {
      image: {
        size: 123456,
        user: required.requireNonRootUser ? "10001" : "",
        entrypoint: ["/entrypoint.sh"],
        cmd: [],
        exposedPorts: [required.expectedExposedPort],
      },
    },
    ...overrides,
  };
}

function passingArtifact(overrides = {}) {
  return {
    status: "PASS",
    summary: {
      images: requiredDockerImages.length,
      passed: requiredDockerImages.length,
      failed: 0,
      skipped: 0,
      blockers: 0,
    },
    blockers: [],
    images: requiredDockerImages.map((required) => imageReport(required)),
    ...overrides,
  };
}

assert.deepEqual(validateDockerBuildArtifact(passingArtifact()), []);

assert.deepEqual(validateDockerBuildArtifact(passingArtifact({
  status: "FAIL",
  summary: { images: 2, passed: 0, failed: 0, skipped: 2, blockers: 1 },
  blockers: ["docker daemon is not available"],
  images: requiredDockerImages.map((required) => imageReport(required, {
    status: "SKIPPED",
    skipReason: "docker daemon is not available",
    blockers: ["docker daemon is not available"],
  })),
})), [
  "status=FAIL",
  "skipped images=2",
]);

assert(
  validateDockerBuildArtifact(passingArtifact({
    status: "FAIL",
    summary: { images: 2, passed: 0, failed: 0, skipped: 2, blockers: 1 },
    blockers: ["docker daemon is not available"],
    images: requiredDockerImages.map((required) => imageReport(required, {
      status: "SKIPPED",
      blockers: [],
    })),
  })).includes("lumira-server skipped image must include skipReason"),
);

assert(
  validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages
      .filter((required) => required.name !== "frontend")
      .map((required) => imageReport(required)),
  })).includes("missing image report frontend"),
);

{
  const artifact = passingArtifact();
  artifact.images.push(imageReport(requiredDockerImages[0]));
  artifact.summary.images += 1;
  artifact.summary.passed += 1;
  const issues = validateDockerBuildArtifact(artifact);
  assert(issues.includes("duplicate image report lumira-server"));
}

{
  const artifact = passingArtifact();
  artifact.images.push(imageReport({
    name: "unknown",
    dockerfile: "Dockerfile",
    expectedExposedPort: "1234/tcp",
    requireNonRootUser: false,
  }));
  artifact.summary.images += 1;
  artifact.summary.passed += 1;
  const issues = validateDockerBuildArtifact(artifact);
  assert(issues.includes("unknown image report unknown"));
}

{
  const artifact = passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        dockerfile: "wrong.Dockerfile",
        expectedExposedPort: "8081/tcp",
        requireNonRootUser: false,
        tag: "",
      })
      : imageReport(required)),
  });
  const issues = validateDockerBuildArtifact(artifact);
  assert(issues.includes("lumira-server dockerfile must be deploy/docker/service.Dockerfile"));
  assert(issues.includes("lumira-server expectedExposedPort must be 8080/tcp"));
  assert(issues.includes("lumira-server requireNonRootUser must be true"));
  assert(issues.includes("lumira-server image tag is required"));
}

{
  const artifact = passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        dockerfileSha256: "bad",
        staticDockerfile: {
          status: "PASS",
          exists: true,
          dockerfileSha256: "b".repeat(64),
          issues: [],
          checks: {
            exposesExpectedPort: true,
            definesEntrypointOrCmd: true,
            nonRootUser: true,
          },
        },
      })
      : imageReport(required)),
  });
  const issues = validateDockerBuildArtifact(artifact);
  assert(issues.includes("lumira-server dockerfileSha256 must be 64 hex characters"));
  assert(issues.includes("lumira-server dockerfile checksum mismatch between image and staticDockerfile"));
}

assert(
  validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, { inspect: { image: { size: 0, user: "root", entrypoint: [], cmd: [], exposedPorts: [] } } })
      : imageReport(required)),
  })).includes("lumira-server must run as a non-root user"),
);

assert(
  validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, { inspect: null })
      : imageReport(required)),
  })).includes("lumira-server missing inspect image metadata"),
);

assert(
  validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        staticDockerfile: {
          status: "FAIL",
          exists: true,
          issues: ["Dockerfile must expose 8080/tcp"],
          checks: {
            exposesExpectedPort: false,
            definesEntrypointOrCmd: true,
            nonRootUser: true,
          },
        },
      })
      : imageReport(required)),
  })).includes("lumira-server Dockerfile must expose 8080/tcp"),
);

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    status: "PASS",
    summary: {
      images: 2,
      passed: 2,
      failed: 0,
      skipped: 0,
      blockers: 0,
    },
    blockers: ["docker daemon is not available"],
    images: requiredDockerImages.map((required) => required.name === "frontend"
      ? imageReport(required, {
        status: "SKIPPED",
        skipReason: "docker daemon is not available",
        blockers: ["docker daemon is not available"],
      })
      : imageReport(required)),
  }));
  assert(issues.includes("docker status must be FAIL, got PASS"));
  assert(issues.includes("docker summary passed mismatch: declared=2, actual=1"));
  assert(issues.includes("docker summary skipped mismatch: declared=0, actual=1"));
  assert(issues.includes("docker summary blockers mismatch: declared=0, actual=1"));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    status: "FAIL",
    summary: { images: 2, passed: 0, failed: 0, skipped: 2, blockers: 1 },
    blockers: ["operator rewrote docker daemon blocker"],
    images: requiredDockerImages.map((required) => imageReport(required, {
      status: "SKIPPED",
      skipReason: "docker daemon is not available",
      blockers: ["docker daemon is not available"],
    })),
  }));
  assert(issues.includes("docker blockers[0] mismatch: declared=operator rewrote docker daemon blocker, actual=docker daemon is not available"));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    status: "FAIL",
    summary: { images: 2, passed: 1, failed: 1, skipped: 0, blockers: 1 },
    blockers: ["frontend: docker build failed with status 1"],
    images: requiredDockerImages.map((required) => required.name === "frontend"
      ? imageReport(required, {
        status: "FAIL",
        blockers: ["docker build failed with status 1"],
      })
      : imageReport(required)),
  }));
  assert(issues.includes("status=FAIL"));
  assert(issues.includes("failed images=1"));
  assert(!issues.some((issue) => issue.startsWith("docker blockers")));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    status: "FAIL",
    summary: { images: 2, passed: 0, failed: 0, skipped: 2, blockers: 1 },
    blockers: ["docker daemon is not available"],
    images: requiredDockerImages.map((required) => imageReport(required, {
      status: "SKIPPED",
      skipReason: "different reason",
      blockers: ["docker daemon is not available"],
    })),
  }));
  assert(issues.includes("lumira-server skipped image skipReason must match blockers"));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, { blockers: ["leftover blocker"] })
      : imageReport(required)),
  }));
  assert(issues.includes("docker blockers length mismatch: declared=0, actual=0") === false);
  assert(issues.includes("lumira-server passing image must not include blockers"));
}

{
  const artifact = passingArtifact({
    existingImageBuildEvidence: "gh-run-12345-artifacts/docker-build-provenance.json",
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        evidenceMode: "existing-image",
        existingImageEnvKey: "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
        targetTag: "lumira/lumira-server:test",
        tag: "registry.local/lumira-server:rc1",
      })
      : imageReport(required)),
  });
  assert.deepEqual(validateDockerBuildArtifact(artifact), []);
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        evidenceMode: "existing-image",
        existingImageEnvKey: "DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE",
        targetTag: "lumira/lumira-server:test",
        tag: "registry.local/lumira-server:rc1",
      })
      : imageReport(required)),
  }));
  assert(issues.includes("existing image evidence requires existingImageBuildEvidence"));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, {
        evidenceMode: "unknown-mode",
        existingImageEnvKey: "WRONG",
        targetTag: "",
      })
      : imageReport(required)),
  }));
  assert(issues.includes("lumira-server evidenceMode must be build, existing-image, or skipped"));
  assert(issues.includes("lumira-server existingImageEnvKey must be DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE"));
  assert(issues.includes("lumira-server targetTag must be a non-empty string when present"));
}

{
  const issues = validateDockerBuildArtifact(passingArtifact({
    images: requiredDockerImages.map((required) => required.name === "lumira-server"
      ? imageReport(required, { evidenceMode: "skipped" })
      : imageReport(required)),
  }));
  assert(issues.includes("lumira-server passing image cannot use skipped evidenceMode"));
}

console.log("[ddd-docker-evidence-contract.test] ok");
