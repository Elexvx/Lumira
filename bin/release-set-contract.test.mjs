import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

import { DeploymentStateRepository } from './lib/deployment-state-repository.mjs';
import { createReleaseEnvelope, verifyReleaseEnvelope } from './lib/release-envelope.mjs';
import { releaseEnvelopeUrl, validateResolvedManifest, validateSourceUrl } from './lib/release-manifest-resolver.mjs';
import { assertOperationFence, buildPreflightReport, createInitialDeploymentState, migrateDeploymentState, normalizeReleaseManifest, reconcileReleaseState } from './lib/platform-update-contract.mjs';

const image = (name, character) => `ghcr.io/elexvx/lumira/${name}@sha256:${character.repeat(64)}`;
const manifest = (overrides = {}) => ({
  schemaVersion: 3,
  app: 'lumira',
  releaseId: 'v2026.08.31.1',
  channel: 'stable',
  version: '2026.08.31.1',
  commit: 'a'.repeat(40),
  releasedAt: '2026-08-31T00:00:00.000Z',
  images: { server: image('server', 'a'), frontend: image('ui', 'b'), async: image('async', 'c'), jobExecutor: image('job', 'd'), migrator: image('migrator', 'e') },
  compatibility: {
    database: { targetVersion: '202608310001', minReadableVersion: '202608300001', maxReadableVersion: '202608319999', migrationMode: 'expand-only', rollbackMode: 'application-only' },
    event: { readMin: 1, readMax: 2, writeVersion: 2 },
    session: { readVersions: [2, 3], writeVersion: 3 },
    permissionSnapshot: { readVersions: [3, 4], writeVersion: 4 },
    pluginApi: { readVersions: [5, 6], writeVersion: 6 },
  },
  frontend: { mode: 'local-blue-green' },
  update: { strategy: 'single-host-release-set-blue-green', minUpdaterProtocol: 3, drainTimeoutSeconds: 120, rollbackWindowSeconds: 1800, databaseRequiredRuntimeMode: 'NORMAL' },
  rollback: { supported: true, applicationRollbackSupported: true, databaseRestoreRequired: false },
  ...overrides,
});

test('Ed25519 envelope verifies raw payload and rejects payload, signature, key, algorithm, and size tampering', () => {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const payload = Buffer.from(JSON.stringify(manifest()));
  const envelope = createReleaseEnvelope(payload, { keyId: 'test-key', privateKey });
  const options = { trustedKeys: new Map([['test-key', publicKey]]), allowedKeyIds: ['test-key'], maxManifestBytes: 128 * 1024 };
  assert.equal(verifyReleaseEnvelope(envelope, options).manifest.releaseId, 'v2026.08.31.1');
  assert.throws(() => verifyReleaseEnvelope({ ...envelope, payload: Buffer.from('tampered').toString('base64url') }, options), /digest/);
  assert.throws(() => verifyReleaseEnvelope({ ...envelope, signature: Buffer.alloc(64, 1).toString('base64url') }, options), /signature/);
  assert.throws(() => verifyReleaseEnvelope({ ...envelope, keyId: 'unknown' }, options), /not trusted/);
  assert.throws(() => verifyReleaseEnvelope({ ...envelope, algorithm: 'RS256' }, options), /algorithm/);
  assert.throws(() => verifyReleaseEnvelope(envelope, { ...options, maxManifestBytes: 10 }), /byte limit/);
});

test('schemaVersion 3 validates full Release Set and release policy', () => {
  const normalized = normalizeReleaseManifest(manifest());
  assert.equal(normalized.releaseId, 'v2026.08.31.1');
  assert.equal(normalized.database.mode, 'expand-only');
  assert.equal(normalized.compatibility.permissionSnapshot.writeVersion, 4);
  assert.throws(() => normalizeReleaseManifest(manifest({ commit: 'abc1234' })), /exactly 40/);
  assert.throws(() => normalizeReleaseManifest(manifest({ releaseId: '../escape' })), /releaseId/);
  assert.throws(() => normalizeReleaseManifest(manifest({ images: { ...manifest().images, frontend: 'ui:latest' } })), /sha256/);
  assert.throws(() => validateResolvedManifest(normalized, { releaseId: normalized.releaseId, allowedChannels: ['stable'], now: Date.parse('2026-09-02T00:00:00Z'), maxAgeSeconds: 3600 }), /maximum age/);
  assert.throws(() => validateResolvedManifest({ ...normalized, expiresAt: '2026-08-30T00:00:00Z' }, { releaseId: normalized.releaseId, allowedChannels: ['stable'], now: Date.parse('2026-08-31T00:00:00Z') }), /expired/);
});

test('release source rejects insecure, untrusted, cross-host, and path traversal inputs', () => {
  assert.equal(releaseEnvelopeUrl('https://releases.example/lumira/{releaseId}', 'v2026.08.31.1'), 'https://releases.example/lumira/v2026.08.31.1');
  assert.throws(() => releaseEnvelopeUrl('https://releases.example/lumira', '../secret'), /releaseId/);
  assert.throws(() => validateSourceUrl('http://releases.example/release', ['releases.example']), /HTTPS/);
  assert.throws(() => validateSourceUrl('https://evil.example/release', ['releases.example']), /not allowed/);
  const first = validateSourceUrl('https://releases.example/release', ['releases.example']);
  const redirected = new URL('https://evil.example/release', first);
  assert.notEqual(first.hostname, redirected.hostname);
});

test('old deployment state migrates and durable writes survive interrupted replacement', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'lumira-state-'));
  try {
    const file = path.join(directory, 'state.json');
    writeFileSync(file, JSON.stringify({ schemaVersion: 1, activeSlot: 'blue', slots: { blue: { version: 'old', commit: 'b'.repeat(40), serverImage: image('server', 'a'), databaseVersion: '202608300001' }, green: null }, workers: { asyncImage: image('async', 'c'), jobExecutorImage: image('job', 'd') } }));
    const migrated = migrateDeploymentState(JSON.parse(readFileSync(file, 'utf8')));
    assert.equal(migrated.schemaVersion, 3);
    assert.equal(migrated.currentRelease.images.async, image('async', 'c'));
    const failing = new DeploymentStateRepository(file, { beforeRename: () => { throw new Error('simulated power loss'); } });
    assert.throws(() => failing.write({ ...migrated, status: 'DEGRADED' }), /power loss/);
    assert.equal(JSON.parse(readFileSync(file, 'utf8')).schemaVersion, 1);
    const repository = new DeploymentStateRepository(file);
    const candidate = repository.beginCandidate({ taskId: 'task-1', release: { releaseId: 'v2' } });
    assert.equal(candidate.operationEpoch, 1);
    assertOperationFence(candidate, { taskId: 'task-1', operationEpoch: 1, candidateReleaseId: 'v2' });
    assert.throws(() => assertOperationFence(candidate, { taskId: 'task-1', operationEpoch: 0, candidateReleaseId: 'v2' }), /epoch/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test('reconciliation never reports healthy when a Release Set component mismatches', () => {
  const release = normalizeReleaseManifest(manifest());
  const state = { ...createInitialDeploymentState(), currentRelease: { ...release, images: release.images, databaseVersion: release.database.targetVersion }, activeSlot: 'blue' };
  const actual = { activeSlot: 'blue', databaseVersion: release.database.targetVersion, components: Object.fromEntries(Object.entries(release.images).map(([role, value]) => [role, { image: value, releaseId: release.releaseId, healthy: true, managed: true }])) };
  assert.equal(reconcileReleaseState(state, actual).status, 'HEALTHY');
  actual.components.frontend.image = image('ui', 'f');
  assert.equal(reconcileReleaseState(state, actual).status, 'PARTIALLY_DEPLOYED');
  actual.components.async.healthy = false;
  assert.equal(reconcileReleaseState(state, actual).status, 'DEGRADED');
});

test('compatibility blockers prevent unsafe install and rollback', () => {
  const target = manifest();
  const state = createInitialDeploymentState({ activeSlot: 'blue' });
  state.currentRelease = { releaseId: 'v2026.08.30.1', version: '2026.08.30.1', databaseVersion: '202608300001', compatibility: { event: { readMin: 1, readMax: 1, writeVersion: 3 }, session: { readVersions: [1], writeVersion: 1 }, permissionSnapshot: { readVersions: [1], writeVersion: 1 }, pluginApi: { readVersions: [1], writeVersion: 1 } } };
  const report = buildPreflightReport({ manifest: target, state, freeMemoryBytes: 2 ** 31, freeDiskBytes: 4 * 2 ** 30 });
  assert.equal(report.ready, false);
  assert.match(report.blockers.join(' '), /Event Schema/);
});

test('formal manifest generation fails closed when the signing key is absent', () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'lumira-release-'));
  const env = {
    ...process.env,
    GIT_COMMIT: 'a'.repeat(40),
    APP_VERSION: '2026.08.31.1',
    LUMIRA_RELEASE_REQUIRE_SIGNATURE: 'true',
    LUMIRA_RELEASE_SIGNING_KEY_ID: '',
    LUMIRA_RELEASE_SIGNING_PRIVATE_KEY_B64: '',
    LUMIRA_RELEASE_MANIFEST_OUT: path.join(directory, 'manifest.json'),
    LUMIRA_SERVER_IMAGE: image('server', 'a'),
    LUMIRA_FRONTEND_IMAGE: image('ui', 'b'),
    LUMIRA_ASYNC_IMAGE: image('async', 'c'),
    LUMIRA_JOB_EXECUTOR_IMAGE: image('job', 'd'),
    LUMIRA_MIGRATOR_IMAGE: image('migrator', 'e'),
  };
  try {
    const result = spawnSync(process.execPath, [path.join(import.meta.dirname, 'generate-release-manifest.mjs')], { env, encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}\n${result.stderr}`, /formal releases require/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
