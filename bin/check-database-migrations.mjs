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
const requiredDatabaseContracts = [
  'CREATE TABLE `aiadc_activity_registration`',
  'CREATE TABLE `sys_config_metadata`',
  'CREATE TABLE `sys_config_version_head`',
  'CREATE TABLE `sys_config_version`',
  'CREATE TABLE `sys_config_version_item`',
  "'aiadc_activity_locale'",
  "'aiadc_activity_status'",
  "'aiadc_activity_public_status'",
  'CREATE TABLE `sys_profile_field_definition`',
  "'profile_settings_page_key'",
  "'branding.maintenance-end-at'",
];

for (const contract of requiredDatabaseContracts) {
  const idempotentContract = contract.replace('CREATE TABLE `', 'CREATE TABLE IF NOT EXISTS `');
  if (!bootstrapSql.includes(contract) && !bootstrapSql.includes(idempotentContract)) {
    throw new Error(`Fresh database bootstrap is missing: ${contract}`);
  }
  if (!migrationChain.includes(idempotentContract)) {
    throw new Error(`Online migration chain is missing: ${contract}`);
  }
}

if (process.argv.includes('--print-version')) {
  process.stdout.write(latest.version);
} else {
  console.log(`Database migration contract passed. Latest version: ${latest.version}`);
}
