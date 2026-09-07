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
const bootstrapVersion = readFileSync(
  path.join(repoRoot, 'lumira-backend', 'sql', 'saas-baseline-version.txt'),
  'utf8',
).trim();
if (!/^\d+$/u.test(bootstrapVersion)) {
  throw new Error(`Invalid fresh database baseline version: ${bootstrapVersion || '<empty>'}`);
}
if (bootstrapVersion !== latest.version) {
  throw new Error(
    `Fresh database baseline version ${bootstrapVersion} does not match latest migration ${latest.version}. `
    + 'Update saas.sql completely before advancing the baseline marker.',
  );
}
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
  'CREATE TABLE `file_event_receipt`',
  'CREATE TABLE `file_event_projection`',
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

const ownerManifest = readFileSync(
  path.join(repoRoot, 'doc', '27-ddd-owner-table-manifest.csv'),
  'utf8',
)
  .trim()
  .split(/\r?\n/u)
  .slice(1)
  .filter(Boolean)
  .map((line) => {
    const [context, ownerModule, tablePatterns] = line.split(',', 3);
    return {
      context,
      ownerModule,
      patterns: tablePatterns.split('|').filter((pattern) => pattern && pattern !== '-'),
    };
  });
const tableDdlPattern = /\b(?:create\s+table\s+(?:if\s+not\s+exists\s+)?|alter\s+table\s+)`?([a-zA-Z0-9_]+)`?/giu;
const stripSqlComments = (source) => source
  .replace(/\/\*[\s\S]*?\*\//gu, '')
  .replace(/^\s*--.*$/gmu, '');
const ownerPatternMatches = (pattern, table) => {
  const escaped = pattern.replace(/[.+?^${}()|[\]\\]/gu, '\\$&').replaceAll('*', '.*');
  return new RegExp(`^${escaped}$`, 'iu').test(table);
};
const ownersForTable = (table) => ownerManifest.filter((rule) =>
  rule.patterns.some((pattern) => ownerPatternMatches(pattern, table))
);

for (const migration of migrations) {
  const source = stripSqlComments(migration.source);
  for (const match of source.matchAll(tableDdlPattern)) {
    const table = match[1];
    if (ownersForTable(table).length === 0) {
      throw new Error(`Migration ${migration.name} declares ${table} but no owner rule matches.`);
    }
  }
}

const bootstrapTables = new Set();
for (const match of stripSqlComments(bootstrapSql).matchAll(tableDdlPattern)) {
  bootstrapTables.add(match[1]);
}
for (const table of bootstrapTables) {
  const owners = ownersForTable(table);
  if (owners.length !== 1) {
    const ownerSummary = owners.map((owner) => `${owner.context}:${owner.ownerModule}`).join(', ') || '<none>';
    throw new Error(`Bootstrap table ${table} must have exactly one owner; found ${ownerSummary}.`);
  }
}

if (process.argv.includes('--print-version')) {
  process.stdout.write(latest.version);
} else {
  console.log(`Database migration contract passed. Fresh baseline and latest migration: ${latest.version}`);
}
