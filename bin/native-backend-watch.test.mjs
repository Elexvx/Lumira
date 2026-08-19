import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  createChangeBatcher,
  isBackendSourcePath,
  normalizeWatchPath,
} from './lib/native-backend-watch.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('native backend watcher accepts source, resource, and Maven descriptor changes', () => {
  assert.equal(isBackendSourcePath('services/lumira-system/src/main/java/com/lumira/App.java'), true);
  assert.equal(isBackendSourcePath('services/lumira-admin/src/main/resources/application.yml'), true);
  assert.equal(isBackendSourcePath('services/lumira-admin/pom.xml'), true);
  assert.equal(isBackendSourcePath('pom.xml'), true);
});

test('native backend watcher ignores generated and unrelated files', () => {
  assert.equal(isBackendSourcePath('services/lumira-system/target/classes/App.class'), false);
  assert.equal(isBackendSourcePath('.git/index'), false);
  assert.equal(isBackendSourcePath('services/lumira-system/src/test/java/AppTest.java'), false);
  assert.equal(isBackendSourcePath('README.md'), false);
});

test('native backend watcher normalizes Windows paths', () => {
  assert.equal(
    normalizeWatchPath('services\\lumira-system\\src\\main\\java\\App.java'),
    'services/lumira-system/src/main/java/App.java',
  );
});

test('native backend watcher debounces and deduplicates rapid saves', async () => {
  const batches = [];
  const batcher = createChangeBatcher({
    delayMs: 20,
    onBatch: (files) => batches.push(files),
  });

  batcher.add('services/lumira-system/src/main/java/App.java');
  batcher.add('services/lumira-system/src/main/java/App.java');
  batcher.add('services/lumira-admin/src/main/resources/application.yml');

  await new Promise((resolve) => setTimeout(resolve, 60));
  batcher.close();

  assert.deepEqual(batches, [[
    'services/lumira-admin/src/main/resources/application.yml',
    'services/lumira-system/src/main/java/App.java',
  ]]);
});

test('native local rebuild skips test compilation and isolates the JVM from in-place Maven output', () => {
  const source = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');
  assert.match(source, /-Dmaven\.test\.skip=true/);
  assert.match(source, /Stopping native Java runtime\(s\) before compilation; Umi HMR remains online/);
  assert.match(source, /Java will recover after the next successful save/);
  assert.match(source, /Timed out waiting for the restarted backend runtime\(s\) to listen/);
  assert.match(source, /delayMs: 5_000/);
  assert.match(source, /Backend runtime is ready; live compile is now armed/);
  assert.match(source, /Waiting up to .* for business readiness/);
  assert.match(source, /Local native environment is business-ready/);
  assert.match(source, /waitForLocalReadiness/);
  assert.match(source, /Queued .* backend change\(s\) until the initial Java runtime is ready/);
  assert.match(source, /PLATFORM_UPDATE_TASK_RECONCILE_INITIAL_DELAY_MS/);
});

test('background stop is scoped to the recorded native launcher PID tree', () => {
  const stopSource = readFileSync(path.join(repoRoot, 'bin', 'stop-local.mjs'), 'utf8');
  assert.match(stopSource, /ProcessId = \$\{pid\}/);
  assert.match(stopSource, /bin\[\\\\\/\]start-local\\\.mjs/);
  assert.match(stopSource, /taskkill\.exe', \['\/pid', String\(pid\), '\/t', '\/f'\]/);
  assert.doesNotMatch(stopSource, /Get-Process|docker|Stop-Service|taskkill.*\/im/);
});

test('backend runtime failure preserves Umi and source watching for recovery', () => {
  const startLocalSource = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');
  assert.match(startLocalSource, /Umi HMR and backend source watching remain online/);
  assert.match(startLocalSource, /A restarted backend runtime exited before its port became ready/);
  assert.doesNotMatch(
    startLocalSource,
    /Automatic restart failed:[^\n]*\n\s*stopAll\(\)/,
  );
});

test('native full runtime routes async and owner jobs through the local control plane', () => {
  const source = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');

  assert.match(source, /LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:\s*backendUrl/);
  assert.match(source, /SAAS_JOB_CONTROL_PLANE_BASE_URL:\s*backendUrl/);
  assert.match(source, /SAAS_JOB_ASYNC_RUNTIME_BASE_URL:\s*asyncUrl/);
  assert.match(source, /SAAS_JOB_BACKEND_BASE_URL:\s*asyncUrl/);
  for (const variable of [
    'SAAS_JOB_SYSTEM_SERVICE_BASE_URL',
    'SAAS_JOB_MESSAGE_SERVICE_BASE_URL',
    'SAAS_JOB_FILE_SERVICE_BASE_URL',
    'SAAS_JOB_PAYMENT_SERVICE_BASE_URL',
    'SAAS_JOB_PLUGIN_SERVICE_BASE_URL',
  ]) {
    assert.match(source, new RegExp(`${variable}:\\s*backendUrl`));
  }
  for (const variable of [
    'SAAS_JOB_ADAPTIVE_RELAY_MESSAGE_ENABLED',
    'SAAS_JOB_ADAPTIVE_RELAY_FILE_ENABLED',
    'SAAS_JOB_ADAPTIVE_RELAY_PAYMENT_ENABLED',
    'SAAS_JOB_ADAPTIVE_RELAY_PLUGIN_ENABLED',
  ]) {
    assert.match(source, new RegExp(`${variable}:\\s*'false'`));
  }
});
