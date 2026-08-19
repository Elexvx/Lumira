import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const workflows = path.join(repoRoot, '.github', 'workflows');
const ci = readFileSync(path.join(workflows, 'ci.yml'), 'utf8');
const lifecycle = readFileSync(path.join(workflows, 'competition-lifecycle-e2e.yml'), 'utf8');
const lifecycleReset = readFileSync(path.join(repoRoot, 'bin', 'reset-e2e-platform.mjs'), 'utf8');

test('canonical CI owns frontend lint, typecheck, test, and build', () => {
  assert.equal(existsSync(path.join(workflows, 'frontend-build.yml')), false);
  assert.match(ci, /version: 10\.33\.0/);
  assert.match(ci, /node-version: 24/);
  for (const command of ['pnpm run lint', 'pnpm run typecheck', 'pnpm run test', 'pnpm run build']) {
    assert.match(ci, new RegExp(command.replaceAll(' ', '\\s+')));
  }
});

test('scheduled lifecycle E2E uses the canonical frontend toolchain and local targets', () => {
  assert.match(lifecycle, /version: 10\.33\.0/);
  assert.match(lifecycle, /node-version: 24/);
  assert.match(lifecycle, /docker network inspect 1panel-network[\s\S]*docker network create 1panel-network/);
  assert.match(lifecycle, /BACKUP_ROOT:\s*\$\{\{ runner\.temp \}\}\/lumira-backups/);
  assert.match(lifecycle, /LIFECYCLE_BASE_URL=http:\/\/127\.0\.0\.1:8000/);
  assert.match(lifecycle, /test:competition-lifecycle/);
  assert.match(lifecycle, /Print lifecycle runtime diagnostics[\s\S]*if: failure\(\)/);
  for (const container of ['lumira-api-proxy', 'lumira-async', 'lumira-server-blue']) {
    assert.match(lifecycle, new RegExp(container));
  }
  assert.match(lifecycle, /PLAYWRIGHT_ROLE_MATRIX=true/);
  assert.match(lifecycle, /--project=role-access --project=quality --project=mobile-390/);
});

test('container lifecycle does not assume a host-published backend port', () => {
  assert.doesNotMatch(lifecycle, /DEPLOY_CHECK_BACKEND_URL:\s*http:\/\/127\.0\.0\.1:8080/);
  assert.doesNotMatch(
    lifecycleReset,
    /process\.env\.DEPLOY_CHECK_BACKEND_URL\s*\|\|\s*'http:\/\/127\.0\.0\.1:8080'/,
  );
  assert.match(
    lifecycleReset,
    /\.\.\.\(localBackendUrl \? \{ DEPLOY_CHECK_BACKEND_URL: localBackendUrl \} : \{\}\)/,
  );
});
