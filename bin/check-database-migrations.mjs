#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const migrationDir = path.join(repoRoot, 'deploy', 'migrations');
const migrationPattern = /^V(\d+)__[a-z0-9_]+\.sql$/;
const migrations = readdirSync(migrationDir)
  .filter((name) => name.endsWith('.sql'))
  .map((name) => {
    const match = migrationPattern.exec(name);
    if (!match) throw new Error(`Invalid database migration filename: ${name}`);
    return { name, version: match[1], source: readFileSync(path.join(migrationDir, name), 'utf8') };
  })
  .sort((left, right) => left.version.localeCompare(right.version));

if (migrations.length === 0) throw new Error('No versioned database migrations were found.');

const versions = migrations.map((migration) => migration.version);
if (new Set(versions).size !== versions.length) throw new Error('Duplicate database migration versions were found.');

const latest = migrations.at(-1);
const bootstrapSql = readFileSync(path.join(repoRoot, 'lumira-backend', 'sql', 'saas.sql'), 'utf8');
const migrationChain = migrations.map((migration) => migration.source).join('\n');
const requiredActivityContracts = [
  'CREATE TABLE `aiadc_activity_registration`',
  "'aiadc_activity_locale'",
  "'aiadc_activity_status'",
  "'aiadc_activity_public_status'",
];

for (const contract of requiredActivityContracts) {
  if (!bootstrapSql.includes(contract)) throw new Error(`Fresh database bootstrap is missing: ${contract}`);
  if (!migrationChain.includes(contract.replace('CREATE TABLE `', 'CREATE TABLE IF NOT EXISTS `'))) {
    throw new Error(`Online migration chain is missing: ${contract}`);
  }
}

if (process.argv.includes('--print-version')) {
  process.stdout.write(latest.version);
} else {
  console.log(`Database migration contract passed. Latest version: ${latest.version}`);
}
