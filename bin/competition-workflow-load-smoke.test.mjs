import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const scriptPath = path.join(repoRoot, 'bin', 'competition-workflow-load-smoke.mjs');
const source = readFileSync(scriptPath, 'utf8');
const scenario = JSON.stringify([{ name: 'save review draft', method: 'PUT', path: '/api/v2/reviews/assignments/1/sheet', body: { score: 90 } }]);

function run(extraEnv) {
  return spawnSync(process.execPath, [scriptPath], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: {
      ...process.env,
      COMPETITION_SMOKE_USERNAME: 'local-e2e',
      COMPETITION_SMOKE_PASSWORD: 'not-used-by-preflight',
      COMPETITION_SMOKE_WRITE_SCENARIOS: scenario,
      ...extraEnv,
    },
  });
}

test('load smoke reports p95 and p99 and carries explicit write scenarios', () => {
  assert.match(source, /COMPETITION_SMOKE_MAX_P99_MS/);
  assert.match(source, /COMPETITION_SMOKE_WRITE_SCENARIOS/);
  assert.match(source, /idempotency-key/);
  assert.match(source, /writes:/);
});

test('write-path load requires an explicit opt-in', () => {
  const result = run({ COMPETITION_SMOKE_BASE_URL: 'http://127.0.0.1:8000' });
  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}\n${result.stderr}`, /COMPETITION_SMOKE_ALLOW_WRITES=true/);
});

test('write-path load refuses every non-loopback target', () => {
  const result = run({
    COMPETITION_SMOKE_ALLOW_WRITES: 'true',
    COMPETITION_SMOKE_BASE_URL: 'https://example.invalid',
  });
  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}\n${result.stderr}`, /only accepts a loopback base URL/);
});
