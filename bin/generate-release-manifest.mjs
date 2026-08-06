#!/usr/bin/env node

import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outPath = process.env.LUMIRA_RELEASE_MANIFEST_OUT || path.join(repoRoot, 'tmp', 'release', 'lumira-release-manifest.json');
const commit = first(process.env.GIT_COMMIT, process.env.GITHUB_SHA) || 'unknown';
const version = first(process.env.APP_VERSION, process.env.BUILD_VERSION, process.env.GITHUB_REF_NAME, '0.1.0');
const owner = (process.env.GITHUB_REPOSITORY_OWNER || 'elexvx').toLowerCase();
const serverImage = first(process.env.LUMIRA_SERVER_IMAGE, `ghcr.io/${owner}/lumira/lumira-server:sha-${commit}`);
const frontendImage = first(process.env.LUMIRA_FRONTEND_IMAGE, `ghcr.io/${owner}/lumira/lumira-ui:sha-${commit}`);
const asyncImage = first(process.env.LUMIRA_ASYNC_IMAGE, `ghcr.io/${owner}/lumira/lumira-async:sha-${commit}`);
const jobExecutorImage = first(process.env.LUMIRA_JOB_EXECUTOR_IMAGE, `ghcr.io/${owner}/lumira/lumira-job-executor:sha-${commit}`);
const migratorImage = first(process.env.LUMIRA_MIGRATOR_IMAGE, `ghcr.io/${owner}/lumira/lumira-migrator:sha-${commit}`);

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
  schemaVersion: 2,
  app: 'lumira',
  channel: process.env.LUMIRA_RELEASE_CHANNEL || 'stable',
  version,
  commit,
  releasedAt: new Date().toISOString(),
  serverImage,
  frontendImage,
  images: {
    server: serverImage,
    frontend: frontendImage,
    async: asyncImage,
    jobExecutor: jobExecutorImage,
    migrator: migratorImage,
  },
  update: {
    strategy: 'single-host-blue-green',
    minUpdaterProtocol: 2,
    drainTimeoutSeconds: Number(process.env.LUMIRA_RELEASE_DRAIN_TIMEOUT_SECONDS || 60),
    rollbackWindowSeconds: Number(process.env.LUMIRA_RELEASE_ROLLBACK_WINDOW_SECONDS || 1800),
    database: {
      mode: process.env.LUMIRA_RELEASE_MIGRATION_MODE || 'expand-only',
      targetVersion: process.env.LUMIRA_RELEASE_DATABASE_VERSION || '',
      rollbackMode: 'forward-compatible',
    },
  },
  minVersion: process.env.LUMIRA_RELEASE_MIN_VERSION || '',
  migrationRequired: process.env.LUMIRA_RELEASE_MIGRATION_REQUIRED === 'true',
  rollbackSupported: process.env.LUMIRA_RELEASE_ROLLBACK_SUPPORTED !== 'false',
  releaseNotes: process.env.LUMIRA_RELEASE_NOTES || `Lumira ${version}`,
};

mkdirSync(path.dirname(outPath), { recursive: true });
writeFileSync(outPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Release manifest written: ${outPath}`);

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
