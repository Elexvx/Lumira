#!/usr/bin/env node

import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

import { createReleaseEnvelope } from './lib/release-envelope.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outPath = process.env.LUMIRA_RELEASE_MANIFEST_OUT || path.join(repoRoot, 'tmp', 'release', 'lumira-release-manifest.json');
const envelopeOutPath = process.env.LUMIRA_RELEASE_ENVELOPE_OUT || path.join(path.dirname(outPath), 'lumira-release-envelope.json');
const commit = first(process.env.GIT_COMMIT, process.env.GITHUB_SHA) || 'unknown';
const version = first(process.env.APP_VERSION, process.env.BUILD_VERSION, process.env.GITHUB_REF_NAME, '0.1.0');
const releaseId = first(process.env.LUMIRA_RELEASE_ID, process.env.GITHUB_REF_TYPE === 'tag' ? process.env.GITHUB_REF_NAME : '', `v${version}`);
const owner = (process.env.GITHUB_REPOSITORY_OWNER || 'elexvx').toLowerCase();
const serverImage = first(process.env.LUMIRA_SERVER_IMAGE, `ghcr.io/${owner}/lumira/lumira-server:sha-${commit}`);
const frontendImage = first(process.env.LUMIRA_FRONTEND_IMAGE, `ghcr.io/${owner}/lumira/lumira-ui:sha-${commit}`);
const asyncImage = first(process.env.LUMIRA_ASYNC_IMAGE, `ghcr.io/${owner}/lumira/lumira-async:sha-${commit}`);
const jobExecutorImage = first(process.env.LUMIRA_JOB_EXECUTOR_IMAGE, `ghcr.io/${owner}/lumira/lumira-job-executor:sha-${commit}`);
const migratorImage = first(process.env.LUMIRA_MIGRATOR_IMAGE, `ghcr.io/${owner}/lumira/lumira-migrator:sha-${commit}`);
const plugins = parsePlugins(process.env.LUMIRA_RELEASE_PLUGINS_JSON);

assertDigestPinned(serverImage, 'LUMIRA_SERVER_IMAGE');
assertDigestPinned(frontendImage, 'LUMIRA_FRONTEND_IMAGE');
assertDigestPinned(asyncImage, 'LUMIRA_ASYNC_IMAGE');
assertDigestPinned(jobExecutorImage, 'LUMIRA_JOB_EXECUTOR_IMAGE');
assertDigestPinned(migratorImage, 'LUMIRA_MIGRATOR_IMAGE');
assertDistinctRuntimeDigests({
  LUMIRA_SERVER_IMAGE: serverImage,
  LUMIRA_ASYNC_IMAGE: asyncImage,
  LUMIRA_JOB_EXECUTOR_IMAGE: jobExecutorImage,
});

const manifest = {
  schemaVersion: 3,
  app: 'lumira',
  releaseId,
  channel: process.env.LUMIRA_RELEASE_CHANNEL || 'stable',
  version,
  commit,
  releasedAt: process.env.LUMIRA_RELEASED_AT || new Date().toISOString(),
  ...(process.env.LUMIRA_RELEASE_EXPIRES_AT ? { expiresAt: process.env.LUMIRA_RELEASE_EXPIRES_AT } : {}),
  serverImage,
  frontendImage,
  images: {
    server: serverImage,
    frontend: frontendImage,
    async: asyncImage,
    jobExecutor: jobExecutorImage,
    migrator: migratorImage,
  },
  plugins,
  update: {
    strategy: 'single-host-release-set-blue-green',
    minUpdaterProtocol: 3,
    drainTimeoutSeconds: Number(process.env.LUMIRA_RELEASE_DRAIN_TIMEOUT_SECONDS || 120),
    rollbackWindowSeconds: Number(process.env.LUMIRA_RELEASE_ROLLBACK_WINDOW_SECONDS || 1800),
    databaseRequiredRuntimeMode: process.env.LUMIRA_RELEASE_DATABASE_RUNTIME_MODE || 'NORMAL',
  },
  compatibility: {
    database: {
      targetVersion: process.env.LUMIRA_RELEASE_DATABASE_VERSION || '',
      minReadableVersion: process.env.LUMIRA_RELEASE_DATABASE_READ_MIN || '',
      maxReadableVersion: process.env.LUMIRA_RELEASE_DATABASE_READ_MAX || '',
      migrationMode: process.env.LUMIRA_RELEASE_MIGRATION_MODE || 'expand-only',
      rollbackMode: process.env.LUMIRA_RELEASE_DATABASE_ROLLBACK_MODE || 'application-only',
    },
    event: integerRange('EVENT', 1),
    session: versionSet('SESSION', 1),
    permissionSnapshot: versionSet('PERMISSION_SNAPSHOT', 1),
    pluginApi: versionSet('PLUGIN_API', 1),
    redisTopology: {
      identity: process.env.LUMIRA_REDIS_TOPOLOGY_IDENTITY || 'redis-split-cache-runtime-v1',
      cachePolicy: 'allkeys-lru',
      runtimePolicy: 'noeviction-aof-everysec',
    },
  },
  frontend: { mode: process.env.LUMIRA_RELEASE_FRONTEND_MODE || 'local-blue-green' },
  minVersion: process.env.LUMIRA_RELEASE_MIN_VERSION || '',
  migrationRequired: process.env.LUMIRA_RELEASE_MIGRATION_REQUIRED === 'true',
  rollback: {
    supported: process.env.LUMIRA_RELEASE_ROLLBACK_SUPPORTED !== 'false',
    applicationRollbackSupported: process.env.LUMIRA_RELEASE_APPLICATION_ROLLBACK_SUPPORTED !== 'false',
    databaseRestoreRequired: process.env.LUMIRA_RELEASE_DATABASE_RESTORE_REQUIRED === 'true',
  },
  releaseNotes: process.env.LUMIRA_RELEASE_NOTES || `Lumira ${version}`,
};

mkdirSync(path.dirname(outPath), { recursive: true });
writeFileSync(outPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Release manifest written: ${outPath}`);

const signingPrivateKeyBase64 = first(process.env.LUMIRA_RELEASE_SIGNING_PRIVATE_KEY_B64);
const signingKeyId = first(process.env.LUMIRA_RELEASE_SIGNING_KEY_ID);
const signatureRequired = process.env.LUMIRA_RELEASE_REQUIRE_SIGNATURE === 'true' || process.env.GITHUB_REF_TYPE === 'tag';
if (signingPrivateKeyBase64 && signingKeyId) {
  const privateKeyBytes = Buffer.from(signingPrivateKeyBase64, 'base64');
  const privateKey = privateKeyBytes.includes(Buffer.from('-----BEGIN'))
    ? privateKeyBytes.toString('utf8')
    : { key: privateKeyBytes, format: 'der', type: 'pkcs8' };
  const envelope = createReleaseEnvelope(Buffer.from(JSON.stringify(manifest)), { keyId: signingKeyId, privateKey });
  writeFileSync(envelopeOutPath, `${JSON.stringify(envelope, null, 2)}\n`, { mode: 0o600 });
  console.log(`Signed release envelope written: ${envelopeOutPath}`);
} else if (signatureRequired) {
  throw new Error('formal releases require LUMIRA_RELEASE_SIGNING_PRIVATE_KEY_B64 and LUMIRA_RELEASE_SIGNING_KEY_ID');
}

function first(...values) {
  return values.map((value) => String(value || '').trim()).find(Boolean);
}

function assertDigestPinned(image, name) {
  if (!/^[A-Za-z0-9][A-Za-z0-9._/:@-]+@sha256:[0-9a-f]{64}$/i.test(image)) {
    throw new Error(`${name} must be pinned to a sha256 digest`);
  }
}

function assertDistinctRuntimeDigests(images) {
  const namesByDigest = new Map();
  for (const [name, image] of Object.entries(images)) {
    const digest = image.slice(image.lastIndexOf('@sha256:') + 1).toLowerCase();
    const previousName = namesByDigest.get(digest);
    if (previousName) {
      throw new Error(`${previousName} and ${name} must use distinct image digests`);
    }
    namesByDigest.set(digest, name);
  }
}

function integerRange(name, fallback) {
  const writeVersion = Number(process.env[`LUMIRA_RELEASE_${name}_WRITE_VERSION`] || fallback);
  return {
    readMin: Number(process.env[`LUMIRA_RELEASE_${name}_READ_MIN`] || writeVersion),
    readMax: Number(process.env[`LUMIRA_RELEASE_${name}_READ_MAX`] || writeVersion),
    writeVersion,
  };
}

function versionSet(name, fallback) {
  const writeVersion = Number(process.env[`LUMIRA_RELEASE_${name}_WRITE_VERSION`] || fallback);
  const configured = String(process.env[`LUMIRA_RELEASE_${name}_READ_VERSIONS`] || writeVersion)
    .split(',').map(Number).filter((item) => Number.isInteger(item) && item > 0);
  return { readVersions: [...new Set(configured)], writeVersion };
}

function parsePlugins(raw) {
  if (!String(raw || '').trim()) return [];
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`LUMIRA_RELEASE_PLUGINS_JSON must be valid JSON: ${error.message}`);
  }
  if (!Array.isArray(parsed)) throw new Error('LUMIRA_RELEASE_PLUGINS_JSON must contain an array');
  return parsed;
}
