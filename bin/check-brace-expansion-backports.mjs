#!/usr/bin/env node

import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const pnpmStore = path.join(repoRoot, 'lumira-ui', 'node_modules', '.pnpm');
const reviewedVersions = ['1.1.18', '2.1.4'];

for (const version of reviewedVersions) {
  const directoryName = readdirSync(pnpmStore).find((name) => name === `brace-expansion@${version}`);
  assert.ok(directoryName, `brace-expansion ${version} must be installed from the reviewed backport`);

  const packageRoot = path.join(pnpmStore, directoryName, 'node_modules', 'brace-expansion');
  const manifest = JSON.parse(readFileSync(path.join(packageRoot, 'package.json'), 'utf8'));
  const source = readFileSync(path.join(packageRoot, 'index.js'), 'utf8');

  assert.equal(manifest.version, version);
  assert.match(source, /CVE-2026-14257/);
  assert.match(source, /EXPANSION_MAX_LENGTH\s*=\s*4000000/);
  assert.match(source, /options\.maxLength\s*==\s*null\s*\?\s*EXPANSION_MAX_LENGTH/);
}

console.log(`Verified brace-expansion CVE-2026-14257 backports: ${reviewedVersions.join(', ')}.`);
