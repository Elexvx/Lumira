export const requiredDockerImages = [
  {
    name: "lumira-server",
    dockerfile: "deploy/docker/service.Dockerfile",
    expectedExposedPort: "8080/tcp",
    requireNonRootUser: true,
  },
  {
    name: "frontend",
    dockerfile: "deploy/docker/frontend.Dockerfile",
    expectedExposedPort: "80/tcp",
    requireNonRootUser: false,
  },
];

export function validateDockerBuildArtifact(artifact) {
  const issues = [];
  const images = Array.isArray(artifact?.images) ? artifact.images : [];
  const actualPassed = images.filter((image) => image.status === "PASS").length;
  const actualFailed = images.filter((image) => image.status === "FAIL").length;
  const actualSkipped = images.filter((image) => image.status === "SKIPPED").length;
  const actualBlockers = Array.isArray(artifact?.blockers) ? artifact.blockers.length : 0;
  const expectedBlockers = expectedDockerBlockers(images);
  const expectedStatus = actualBlockers === 0 && actualFailed === 0 && actualSkipped === 0 ? "PASS" : "FAIL";
  const requiredByName = new Map(requiredDockerImages.map((image) => [image.name, image]));
  const imageNameCounts = countValues(images.map((image) => image.name).filter(Boolean));
  const hasExistingImageEvidence = images.some((image) => image?.evidenceMode === "existing-image");

  if (artifact?.status !== "PASS") {
    issues.push(`status=${artifact?.status ?? "missing"}`);
  }
  if (artifact?.status !== expectedStatus) {
    issues.push(`docker status must be ${expectedStatus}, got ${artifact?.status ?? "missing"}`);
  }
  if ((artifact?.summary?.images || 0) !== images.length) {
    issues.push(`docker summary images mismatch: declared=${artifact?.summary?.images || 0}, actual=${images.length}`);
  }
  if ((artifact?.summary?.passed || 0) !== actualPassed) {
    issues.push(`docker summary passed mismatch: declared=${artifact?.summary?.passed || 0}, actual=${actualPassed}`);
  }
  if ((artifact?.summary?.failed || 0) !== actualFailed) {
    issues.push(`docker summary failed mismatch: declared=${artifact?.summary?.failed || 0}, actual=${actualFailed}`);
  }
  if ((artifact?.summary?.skipped || 0) !== actualSkipped) {
    issues.push(`docker summary skipped mismatch: declared=${artifact?.summary?.skipped || 0}, actual=${actualSkipped}`);
  }
  if ((artifact?.summary?.blockers || 0) !== actualBlockers) {
    issues.push(`docker summary blockers mismatch: declared=${artifact?.summary?.blockers || 0}, actual=${actualBlockers}`);
  }
  if (hasExistingImageEvidence
    && (typeof artifact?.existingImageBuildEvidence !== "string" || artifact.existingImageBuildEvidence.trim().length === 0)) {
    issues.push("existing image evidence requires existingImageBuildEvidence");
  }
  compareStringArrays("docker blockers", Array.isArray(artifact?.blockers) ? artifact.blockers : [], expectedBlockers, issues);
  if ((artifact?.summary?.failed || 0) > 0) {
    issues.push(`failed images=${artifact.summary.failed}`);
  }
  if ((artifact?.summary?.skipped || 0) > 0) {
    issues.push(`skipped images=${artifact.summary.skipped}`);
  }

  const imageByName = new Map(images.map((image) => [image.name, image]));
  for (const required of requiredDockerImages) {
    const image = imageByName.get(required.name);
    if (!image) {
      issues.push(`missing image report ${required.name}`);
      continue;
    }
    if (image.dockerfile !== required.dockerfile) {
      issues.push(`${required.name} dockerfile must be ${required.dockerfile}`);
    }
    if (image.expectedExposedPort !== required.expectedExposedPort) {
      issues.push(`${required.name} expectedExposedPort must be ${required.expectedExposedPort}`);
    }
    if (image.requireNonRootUser !== required.requireNonRootUser) {
      issues.push(`${required.name} requireNonRootUser must be ${required.requireNonRootUser}`);
    }
    if (!image.tag || typeof image.tag !== "string") {
      issues.push(`${required.name} image tag is required`);
    }
    if (image.targetTag !== undefined && (typeof image.targetTag !== "string" || image.targetTag.trim().length === 0)) {
      issues.push(`${required.name} targetTag must be a non-empty string when present`);
    }
    if (image.existingImageEnvKey !== undefined) {
      const expectedEnvKey = `DDD_DOCKER_EXISTING_${required.name.toUpperCase().replace(/[^A-Z0-9]+/g, "_")}_IMAGE`;
      if (image.existingImageEnvKey !== expectedEnvKey) {
        issues.push(`${required.name} existingImageEnvKey must be ${expectedEnvKey}`);
      }
    }
    if (image.evidenceMode !== undefined && !["build", "existing-image", "skipped"].includes(image.evidenceMode)) {
      issues.push(`${required.name} evidenceMode must be build, existing-image, or skipped`);
    }
    if (image.status === "PASS" && image.evidenceMode === "skipped") {
      issues.push(`${required.name} passing image cannot use skipped evidenceMode`);
    }
    if (image.dockerfileSha256 && !/^[a-f0-9]{64}$/i.test(image.dockerfileSha256)) {
      issues.push(`${required.name} dockerfileSha256 must be 64 hex characters`);
    }
    if (image.staticDockerfile) {
      if (image.staticDockerfile.status !== "PASS") {
        issues.push(`${required.name} static Dockerfile checks failed`);
      }
      if (image.staticDockerfile.exists !== true) {
        issues.push(`${required.name} Dockerfile is missing`);
      }
      if (image.staticDockerfile.dockerfileSha256 && !/^[a-f0-9]{64}$/i.test(image.staticDockerfile.dockerfileSha256)) {
        issues.push(`${required.name} static Dockerfile sha256 must be 64 hex characters`);
      }
      if (image.dockerfileSha256 && image.staticDockerfile.dockerfileSha256
        && image.dockerfileSha256 !== image.staticDockerfile.dockerfileSha256) {
        issues.push(`${required.name} dockerfile checksum mismatch between image and staticDockerfile`);
      }
      if (!image.staticDockerfile.checks?.exposesExpectedPort) {
        issues.push(`${required.name} Dockerfile must expose ${required.expectedExposedPort}`);
      }
      if (!image.staticDockerfile.checks?.definesEntrypointOrCmd) {
        issues.push(`${required.name} Dockerfile must define entrypoint or command`);
      }
      if (required.requireNonRootUser && !image.staticDockerfile.checks?.nonRootUser) {
        issues.push(`${required.name} Dockerfile must use a non-root user`);
      }
    }
    if (image.status !== "PASS") {
      if (image.status === "SKIPPED") {
        if (typeof image.skipReason !== "string" || image.skipReason.trim().length === 0) {
          issues.push(`${required.name} skipped image must include skipReason`);
        }
        if (!Array.isArray(image.blockers) || image.blockers.length === 0) {
          issues.push(`${required.name} skipped image must include blockers`);
        }
        if (Array.isArray(image.blockers) && image.skipReason !== image.blockers.join("; ")) {
          issues.push(`${required.name} skipped image skipReason must match blockers`);
        }
      } else if (image.status === "FAIL") {
        if (!Array.isArray(image.blockers) || image.blockers.length === 0) {
          issues.push(`${required.name} failed image must include blockers`);
        }
      } else {
        issues.push(`${required.name} image status must be PASS, FAIL, or SKIPPED`);
      }
      continue;
    }
    if (Array.isArray(image.blockers) && image.blockers.length > 0) {
      issues.push(`${required.name} passing image must not include blockers`);
    }
    const metadata = image.inspect?.image;
    if (!metadata) {
      issues.push(`${required.name} missing inspect image metadata`);
      continue;
    }
    if (!Number.isFinite(metadata.size) || metadata.size <= 0) {
      issues.push(`${required.name} image size is missing`);
    }
    if (!Array.isArray(metadata.exposedPorts) || !metadata.exposedPorts.includes(required.expectedExposedPort)) {
      issues.push(`${required.name} must expose ${required.expectedExposedPort}`);
    }
    if (required.requireNonRootUser && (!metadata.user || metadata.user === "root" || metadata.user === "0")) {
      issues.push(`${required.name} must run as a non-root user`);
    }
    if ((!Array.isArray(metadata.entrypoint) || metadata.entrypoint.length === 0)
      && (!Array.isArray(metadata.cmd) || metadata.cmd.length === 0)) {
      issues.push(`${required.name} must define entrypoint or command`);
    }
  }
  for (const [imageName, count] of imageNameCounts.entries()) {
    if (count > 1) {
      issues.push(`duplicate image report ${imageName}`);
    }
    if (!requiredByName.has(imageName)) {
      issues.push(`unknown image report ${imageName}`);
    }
  }

  return issues;
}

function expectedDockerBlockers(images) {
  const blockers = [];
  const seenSkipped = new Set();
  for (const image of images) {
    const imageBlockers = Array.isArray(image?.blockers) ? image.blockers : [];
    if (image?.status === "SKIPPED") {
      for (const blocker of imageBlockers) {
        if (!seenSkipped.has(blocker)) {
          seenSkipped.add(blocker);
          blockers.push(blocker);
        }
      }
    }
    if (image?.status === "FAIL") {
      for (const blocker of imageBlockers) {
        blockers.push(`${image.name}: ${blocker}`);
      }
    }
  }
  return blockers;
}

function compareStringArrays(label, declared, expected, issues) {
  if (declared.length !== expected.length) {
    issues.push(`${label} length mismatch: declared=${declared.length}, actual=${expected.length}`);
    return;
  }
  for (let index = 0; index < expected.length; index += 1) {
    if (declared[index] !== expected[index]) {
      issues.push(`${label}[${index}] mismatch: declared=${declared[index] ?? "missing"}, actual=${expected[index]}`);
    }
  }
}

function countValues(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item, (counts.get(item) || 0) + 1);
  }
  return counts;
}
