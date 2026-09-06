import assert from 'node:assert/strict';
import test from 'node:test';

import {
  UPDATE_PHASES,
  buildPreflightReport,
  createInitialDeploymentState,
  inactiveSlot,
  migrateLegacyApiProxyConfig,
  normalizeReleaseManifest,
  parseRuntimeVersionIdentity,
  phaseProgress,
  renderActiveUpstreams,
  repairDeploymentWorkerState,
} from './lib/platform-update-contract.mjs';

const digestCharacters = { server: 'a', frontend: 'b', async: 'c', job: 'd', migrator: 'e' };
const digest = (name) => `ghcr.io/elexvx/lumira/${name}@sha256:${(digestCharacters[name] || 'f').repeat(64)}`;
const manifestV2 = (overrides = {}) => ({
  schemaVersion: 2,
  version: '2.0.0',
  commit: 'abcdef0123456789',
  images: {
    server: digest('server'),
    frontend: digest('frontend'),
    async: digest('async'),
    jobExecutor: digest('job'),
    migrator: digest('migrator'),
  },
  update: {
    strategy: 'single-host-blue-green',
    minUpdaterProtocol: 2,
    database: { mode: 'expand-only', targetVersion: '202607140001' },
  },
  ...overrides,
});

test('manifest v2 requires digest-pinned runtime images', () => {
  const normalized = normalizeReleaseManifest(manifestV2());
  assert.equal(normalized.images.async, digest('async'));
  assert.throws(() => normalizeReleaseManifest(manifestV2({ images: { server: 'server:latest' } })), /sha256 digest/);
});

test('release manifest carries normalized plugin migration metadata', () => {
  const normalized = normalizeReleaseManifest(manifestV2({
    schemaVersion: 3,
    commit: 'abcdef0123456789abcdef0123456789abcdef01',
    releaseId: 'v2026.09.07',
    plugins: [{
      pluginCode: 'payment-extension',
      pluginVersion: '1.3.0',
      migrationVersion: '5',
      schemaDigest: 'A'.repeat(64),
      migrationDigest: `sha256:${'b'.repeat(64)}`,
      compatibleReaders: ['1.2.x', '1.3.x', '1.3.x'],
      phase: 'EXPAND',
      rollbackMode: 'application_only',
    }],
  }));
  assert.deepEqual(normalized.plugins, [{
    pluginCode: 'payment-extension',
    pluginVersion: '1.3.0',
    migrationVersion: '5',
    schemaDigest: `sha256:${'a'.repeat(64)}`,
    migrationDigest: `sha256:${'b'.repeat(64)}`,
    compatibleReaders: ['1.2.x', '1.3.x'],
    phase: 'expand',
    rollbackMode: 'APPLICATION_ONLY',
  }]);
});

test('release manifest rejects duplicate or non-expand plugin migrations', () => {
  const base = manifestV2({ plugins: [{
    pluginCode: 'payment-extension', pluginVersion: '1.3.0', migrationVersion: '5',
    schemaDigest: 'a'.repeat(64), migrationDigest: 'b'.repeat(64), compatibleReaders: ['1.3.x'],
    phase: 'expand', rollbackMode: 'APPLICATION_ONLY',
  }] });
  assert.throws(() => normalizeReleaseManifest({ ...base, plugins: [base.plugins[0], base.plugins[0]] }), /duplicates/);
  assert.throws(() => normalizeReleaseManifest({ ...base, plugins: [{ ...base.plugins[0], phase: 'contract' }] }), /phase/);
});

test('manifest v2 rejects service roles that resolve to the same image digest', () => {
  const repeatedDigest = `sha256:${'a'.repeat(64)}`;
  const manifest = manifestV2();
  manifest.images.async = `ghcr.io/elexvx/lumira/async@${repeatedDigest}`;
  assert.throws(() => normalizeReleaseManifest(manifest), /server.*async.*distinct image digests/);
});

test('runtime identity parsing exposes the actual service behind a release image', () => {
  assert.deepEqual(parseRuntimeVersionIdentity(JSON.stringify({
    data: { serviceName: 'lumira-server', artifact: 'lumira-server', commitId: 'abcdef012345' },
  })), {
    serviceName: 'lumira-server',
    artifact: 'lumira-server',
    commitId: 'abcdef012345',
  });
  assert.equal(parseRuntimeVersionIdentity('not-json'), null);
});

test('manifest v1 remains readable for compatibility but is blocked for online updates', () => {
  const legacy = { schemaVersion: 1, commit: 'abcdef0', serverImage: digest('server') };
  assert.equal(normalizeReleaseManifest(legacy).strategy, 'legacy-recreate');
  const report = buildPreflightReport({ manifest: legacy, state: createInitialDeploymentState(), freeMemoryBytes: 2 ** 31, freeDiskBytes: 4 * 2 ** 30 });
  assert.equal(report.ready, false);
  assert.match(report.blockers.join(' '), /blue-green/);
});

test('preflight selects the inactive slot and enforces overlap resources', () => {
  const state = createInitialDeploymentState({ activeSlot: 'green' });
  const ready = buildPreflightReport({ manifest: manifestV2(), state, freeMemoryBytes: 2 ** 30, freeDiskBytes: 4 * 2 ** 30 });
  assert.equal(ready.ready, true);
  assert.equal(ready.targetSlot, 'blue');
  const blocked = buildPreflightReport({ manifest: manifestV2(), state, freeMemoryBytes: 128, freeDiskBytes: 128 });
  assert.equal(blocked.ready, false);
  assert.equal(blocked.blockers.length, 2);
});

test('preflight preserves database migration fields after manifest normalization', () => {
  const normalized = normalizeReleaseManifest(manifestV2());
  const report = buildPreflightReport({
    manifest: normalized,
    state: createInitialDeploymentState(),
    freeMemoryBytes: 2 ** 30,
    freeDiskBytes: 4 * 2 ** 30,
  });
  assert.equal(report.ready, true);
  assert.equal(report.migrationMode, 'expand-only');
  assert.equal(report.databaseTargetVersion, '202607140001');
});

test('upstream rendering pins every logical API route to the active control-plane slot', () => {
  const rendered = renderActiveUpstreams('green', {
    AUTH_SERVICE_UPSTREAM: 'auth-service:8080',
    PAYMENT_SERVICE_UPSTREAM: 'payment-service:8080',
  });
  assert.equal((rendered.match(/lumira-server-green:8080/g) || []).length, 10);
  assert.doesNotMatch(rendered, /auth-service:8080|payment-service:8080/);
  assert.equal(inactiveSlot('green'), 'blue');
});

test('legacy API proxy migration preserves CRLF boundaries and is idempotent', () => {
  const legacy = [
    'server {',
    '    resolver 127.0.0.11 valid=10s ipv6=off;',
    '    set $gateway_upstream lumira-server:8080;',
    '    set $system_upstream lumira-server:8080;',
    '',
    '    client_max_body_size 200m;',
    '}',
    '',
  ].join('\r\n');
  const migrated = migrateLegacyApiProxyConfig(legacy);
  assert.doesNotMatch(migrated, /set \$(?:gateway|system)_upstream/);
  assert.match(migrated, /resolver 127\.0\.0\.11 valid=10s ipv6=off;\r\n    include \/etc\/nginx\/lumira-upstreams\/active-upstreams\.conf;\r\n\r\n    client_max_body_size/);
  assert.equal(migrateLegacyApiProxyConfig(migrated), migrated);
});

test('worker state repair fills missing bootstrap images without overwriting known images', () => {
  const initial = createInitialDeploymentState({ asyncImage: 'async:known' });
  const repaired = repairDeploymentWorkerState(initial, {
    asyncImage: 'async:detected',
    jobExecutorImage: 'job:detected',
  });
  assert.equal(repaired.changed, true);
  assert.equal(repaired.state.workers.asyncImage, 'async:known');
  assert.equal(repaired.state.workers.jobExecutorImage, 'job:detected');
  assert.equal(repairDeploymentWorkerState(repaired.state, {}).changed, false);
});

test('phase progress is monotonic and reaches finalization', () => {
  const progress = UPDATE_PHASES.map(phaseProgress);
  assert.deepEqual([...progress].sort((a, b) => a - b), progress);
  assert.ok(progress.at(-1) >= 90);
});
