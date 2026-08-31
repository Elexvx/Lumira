#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const migrationDir = path.join(repoRoot, 'deploy', 'migrations');
const ownershipManifest = readFileSync(path.join(repoRoot, 'docs', 'architecture', 'module-data-ownership.yaml'), 'utf8');
const allowedOwners = new Set([...ownershipManifest.matchAll(/^\s+- module:\s*([a-z0-9-]+)\s*$/gmu)].map((match) => match[1]));
const ownedTablesByModule = new Map(
  [...ownershipManifest.matchAll(/^\s+- module:\s*([a-z0-9-]+)\s*$[\s\S]*?^\s+ownedTables:\s*\[([^\]]*)\]/gmu)]
    .map((match) => [
      match[1],
      match[2].split(',').map((value) => value.trim()).filter(Boolean),
    ]),
);
const forbidden = [
  /\bDROP\s+(?:TABLE|COLUMN|INDEX|KEY)\b/i,
  /\bRENAME\s+(?:TABLE|COLUMN)\b/i,
  /\bALTER\s+COLUMN\b/i,
  /\bMODIFY\s+(?:COLUMN\s+)?\w+/i,
  /\bCHANGE\s+(?:COLUMN\s+)?\w+/i,
  /\bTRUNCATE\b/i,
  /\bDELETE\s+FROM\b(?![\s\S]*\bWHERE\b)/i,
];
const metadataRequiredFrom = '202608310001';
const requiredMetadata = [
  /^-- lumira:owner=([a-z0-9-]+)$/m,
  /^-- lumira:migration-phase=expand$/m,
  /^-- lumira:rollback=(?:application-only|not-required)$/m,
  /^-- lumira:compatible-readers=\S+$/m,
  /^-- lumira:cleanup-after=\S+$/m,
];

const failures = [];
const tablePattern = /\b(?:CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?|ALTER\s+TABLE|INSERT\s+INTO|REPLACE\s+INTO|DELETE\s+FROM)\s+`?([a-zA-Z0-9_]+)`?|(?:^|;)\s*UPDATE\s+`?([a-zA-Z0-9_]+)`?|\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+`?[a-zA-Z0-9_]+`?\s+ON\s+`?([a-zA-Z0-9_]+)`?/gimu;

function ownerAllowsTable(owner, table) {
  return (ownedTablesByModule.get(owner) || []).some((ownedPattern) => {
    const escaped = ownedPattern.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replaceAll('*', '.*');
    return new RegExp(`^${escaped}$`, 'iu').test(table);
  });
}

for (const name of readdirSync(migrationDir).filter((value) => value.endsWith('.sql')).sort()) {
  const rawSource = readFileSync(path.join(migrationDir, name), 'utf8');
  const version = /^V(\d+)__/u.exec(name)?.[1];
  if (version && version.localeCompare(metadataRequiredFrom) >= 0) {
    for (const pattern of requiredMetadata) {
      if (!pattern.test(rawSource)) failures.push(`${name}: missing migration metadata ${pattern}`);
    }
    const owner = /^-- lumira:owner=([a-z0-9-]+)$/m.exec(rawSource)?.[1];
    if (owner && !allowedOwners.has(owner)) failures.push(`${name}: unknown migration owner ${owner}`);
    if (owner && allowedOwners.has(owner)) {
      const touchedTables = new Set(
        [...rawSource.matchAll(tablePattern)].map((match) => (match[1] || match[2] || match[3]).toLowerCase()),
      );
      for (const table of touchedTables) {
        if (!ownerAllowsTable(owner, table)) {
          failures.push(`${name}: owner ${owner} cannot modify table ${table}`);
        }
      }
    }
  }
  const source = rawSource
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
