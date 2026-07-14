#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const migrationDir = path.join(repoRoot, 'deploy', 'migrations');
const forbidden = [
  /\bDROP\s+(?:TABLE|COLUMN|INDEX|KEY)\b/i,
  /\bRENAME\s+(?:TABLE|COLUMN)\b/i,
  /\bALTER\s+COLUMN\b/i,
  /\bMODIFY\s+(?:COLUMN\s+)?\w+/i,
  /\bCHANGE\s+(?:COLUMN\s+)?\w+/i,
  /\bTRUNCATE\b/i,
  /\bDELETE\s+FROM\b(?![\s\S]*\bWHERE\b)/i,
];

const failures = [];
for (const name of readdirSync(migrationDir).filter((value) => value.endsWith('.sql')).sort()) {
  const source = readFileSync(path.join(migrationDir, name), 'utf8')
    .replace(/--.*$/gm, '')
    .replace(/\/\*[\s\S]*?\*\//g, '');
  for (const pattern of forbidden) {
    if (pattern.test(source)) failures.push(`${name}: forbidden online migration pattern ${pattern}`);
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('Expand-only migration policy passed.');
