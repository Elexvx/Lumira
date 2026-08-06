import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const readRepoFile = (relativePath) => readFileSync(path.join(repoRoot, relativePath), 'utf8');

const serviceDockerfile = readRepoFile('deploy/docker/service.Dockerfile');
const frontendDockerfile = readRepoFile('deploy/docker/lumira-ui.Dockerfile');
const composeProd = readRepoFile('deploy/docker-compose.prod.yml');
const deployScript = readRepoFile('bin/deploy-container.mjs');

test('backend runtime images share one Maven reactor build', () => {
  assert.match(
    serviceDockerfile,
    /-pl services\/lumira-admin,services\/lumira-async,services\/lumira-quartz/,
    'all runtime artifacts must be produced by the same Maven invocation'
  );
  assert.doesNotMatch(serviceDockerfile, /SERVICE_MODULE/, 'per-image Maven module builds must not return');
  assert.doesNotMatch(composeProd, /SERVICE_MODULE:/, 'Compose must not split the shared Maven layer');
  assert.doesNotMatch(serviceDockerfile, /SERVICE_DIR/, 'runtime selection must use Docker targets, not cache-splitting build arguments');
  for (const target of [
    'lumira-server-image',
    'lumira-async-image',
    'lumira-job-executor-image',
  ]) {
    assert.match(serviceDockerfile, new RegExp(`FROM runtime AS ${target}`));
    assert.match(composeProd, new RegExp(`target: ${target}`));
  }
});

test('frontend release metadata cannot invalidate dependency installation', () => {
  const installIndex = frontendDockerfile.indexOf('pnpm install --frozen-lockfile');
  const sourceIndex = frontendDockerfile.indexOf('COPY lumira-ui/ ./');
  const metadataIndex = frontendDockerfile.indexOf('ARG BUILD_TIME=');
  assert.ok(installIndex >= 0 && sourceIndex > installIndex, 'dependencies must install before application sources are copied');
  assert.ok(metadataIndex > installIndex, 'volatile build metadata must be declared after dependency installation');
  assert.match(frontendDockerfile, /id=lumira-pnpm-store/, 'pnpm downloads must use a persistent BuildKit cache');
});

test('deployment preserves fresh Buildx cache and prunes the selected builder safely', () => {
  assert.match(deployScript, /\['buildx', 'prune', '--force', '--filter'/);
  assert.doesNotMatch(deployScript, /\['builder', 'prune', '-af'\]/);
  assert.equal(
    [...deployScript.matchAll(/maybePruneDockerBuildCache\(/g)].length,
    2,
    'the function definition and the pre-build call should be the only occurrences'
  );
});
