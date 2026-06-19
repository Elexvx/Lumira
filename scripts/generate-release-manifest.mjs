#!/usr/bin/env node

import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outPath = process.env.LUMIRA_RELEASE_MANIFEST_OUT || path.join(repoRoot, 'artifacts', 'release', 'lumira-release-manifest.json');
const commit = first(process.env.GIT_COMMIT, process.env.GITHUB_SHA)?.slice(0, 12) || 'unknown';
const version = first(process.env.APP_VERSION, process.env.BUILD_VERSION, process.env.GITHUB_REF_NAME, '0.1.0');
const owner = (process.env.GITHUB_REPOSITORY_OWNER || 'elexvx').toLowerCase();
const serverImage = first(process.env.LUMIRA_SERVER_IMAGE, `ghcr.io/${owner}/lumira/lumira-server:sha-${commit}`);
const frontendImage = first(process.env.LUMIRA_FRONTEND_IMAGE, `ghcr.io/${owner}/lumira/frontend:sha-${commit}`);

const manifest = {
  app: 'lumira',
  channel: process.env.LUMIRA_RELEASE_CHANNEL || 'stable',
  version,
  commit,
  releasedAt: new Date().toISOString(),
  serverImage,
  frontendImage,
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
