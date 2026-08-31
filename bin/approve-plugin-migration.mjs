#!/usr/bin/env node

import { existsSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';

import { parseEnvFile } from './lib/env-utils.mjs';
import { run, resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const values = parseArguments(process.argv.slice(2));
const envPath = path.resolve(repoRoot, values.get('env-file') || 'deploy/.env');
const fileEnvironment = existsSync(envPath) ? parseEnvFile(envPath) : {};
const environment = { ...fileEnvironment, ...process.env };

if (values.has('help')) {
  printUsage();
  process.exit(0);
}

const requestId = positiveInteger('request-id');
const operationEpoch = positiveInteger('operation-epoch');
const packageDigest = digest('package-digest');
const migrationDigest = digest('migration-digest');
const confirmedDigest = digest('confirm-migration-digest');
if (confirmedDigest !== migrationDigest) fail('The migration digest confirmation does not match.');
const releaseId = required('release-id', 128);
const approver = required('approver', 128);
if (!/^[A-Za-z0-9@._:-]{3,128}$/u.test(approver)) fail('Approver contains unsupported characters.');
const reason = required('reason', 512);
if (reason.length < 8) fail('Approval reason must contain at least 8 characters.');

const databaseUrl = requiredEnvironment('DB_URL');
const databaseUsername = requiredEnvironment('DB_MIGRATION_USERNAME');
const databasePassword = requiredEnvironment('DB_MIGRATION_PASSWORD');
if (databaseUsername !== 'lumira_migrator') fail('DB_MIGRATION_USERNAME must be the dedicated lumira_migrator identity.');
const network = String(environment.DB_MIGRATION_NETWORK || environment.DB_BACKUP_NETWORK || 'deploy_data-network').trim();
if (!/^[A-Za-z0-9_.-]{1,128}$/u.test(network)) fail('Database migration network is invalid.');
const image = String(values.get('image') || environment.LUMIRA_MIGRATOR_IMAGE || '').trim();
if (!image) fail('A migrator image is required through --image or LUMIRA_MIGRATOR_IMAGE.');
const pinnedImage = /@sha256:[a-f0-9]{64}$/iu.test(image);
if (!pinnedImage && !values.has('allow-local-image')) {
  fail('Approval requires a digest-pinned migrator image; use --allow-local-image only for disposable local testing.');
}

const forwarded = {
  ...process.env,
  DB_URL: databaseUrl,
  DB_USERNAME: databaseUsername,
  DB_PASSWORD: databasePassword,
  PLUGIN_MIGRATION_MODE: 'plugin-approve',
  PLUGIN_MIGRATION_RELEASE_ID: releaseId,
  PLUGIN_MIGRATION_REQUEST_ID: requestId,
  PLUGIN_MIGRATION_OPERATION_EPOCH: operationEpoch,
  PLUGIN_MIGRATION_PACKAGE_DIGEST: packageDigest,
  PLUGIN_MIGRATION_DIGEST: migrationDigest,
  PLUGIN_MIGRATION_APPROVER: approver,
  PLUGIN_MIGRATION_APPROVAL_REASON: reason,
};
runMigrator(image, network, forwarded, [
  'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'PLUGIN_MIGRATION_MODE', 'PLUGIN_MIGRATION_RELEASE_ID',
  'PLUGIN_MIGRATION_REQUEST_ID', 'PLUGIN_MIGRATION_OPERATION_EPOCH', 'PLUGIN_MIGRATION_PACKAGE_DIGEST',
  'PLUGIN_MIGRATION_DIGEST', 'PLUGIN_MIGRATION_APPROVER', 'PLUGIN_MIGRATION_APPROVAL_REASON',
]);
console.log(`[plugin-migration] Approved request ${requestId}; actor and reason were persisted in the plugin audit log.`);

if (values.has('execute')) {
  const executorId = `approval-cli:${approver}:${releaseId}`;
  const executeEnvironment = {
    ...process.env,
    DB_URL: databaseUrl,
    DB_USERNAME: databaseUsername,
    DB_PASSWORD: databasePassword,
    PLUGIN_MIGRATION_MODE: 'plugin-execute',
    PLUGIN_MIGRATION_RELEASE_ID: releaseId,
    PLUGIN_MIGRATION_EXECUTOR_ID: executorId,
  };
  runMigrator(image, network, executeEnvironment, [
    'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'PLUGIN_MIGRATION_MODE',
    'PLUGIN_MIGRATION_RELEASE_ID', 'PLUGIN_MIGRATION_EXECUTOR_ID',
  ]);
  console.log(`[plugin-migration] Executed approved requests for release ${releaseId}.`);
}

function runMigrator(imageName, networkName, childEnvironment, names) {
  run('docker', [
    'run', '--rm', '--network', networkName,
    ...names.flatMap((name) => ['-e', name]),
    imageName,
  ], { cwd: repoRoot, env: childEnvironment });
}

function parseArguments(args) {
  const result = new Map();
  for (let index = 0; index < args.length; index += 1) {
    const value = args[index];
    if (value === '--help' || value === '-h') {
      result.set('help', 'true');
    } else if (value === '--execute' || value === '--allow-local-image') {
      result.set(value.slice(2), 'true');
    } else if (value.startsWith('--') && value.includes('=')) {
      const separator = value.indexOf('=');
      result.set(value.slice(2, separator), value.slice(separator + 1));
    } else if (value.startsWith('--')) {
      const next = args[index + 1];
      if (!next || next.startsWith('--')) fail(`${value} requires a value.`);
      result.set(value.slice(2), next);
      index += 1;
    } else {
      fail(`Unsupported argument: ${value}`);
    }
  }
  return result;
}

function positiveInteger(name) {
  const value = required(name, 20);
  if (!/^[1-9][0-9]*$/u.test(value)) fail(`--${name} must be a positive integer.`);
  return value;
}

function digest(name) {
  const value = required(name, 64).toLowerCase();
  if (!/^[a-f0-9]{64}$/u.test(value)) fail(`--${name} must be a SHA-256 digest.`);
  return value;
}

function required(name, maxLength) {
  const value = String(values.get(name) || '').trim();
  if (!value) fail(`--${name} is required.`);
  if (value.length > maxLength) fail(`--${name} exceeds ${maxLength} characters.`);
  return value;
}

function requiredEnvironment(name) {
  const value = String(environment[name] || '').trim();
  if (!value) fail(`${name} is required in the environment or ${envPath}.`);
  return value;
}

function fail(message) {
  console.error(`[plugin-migration] ${message}`);
  process.exit(1);
}

function printUsage() {
  console.log(`Usage: node bin/approve-plugin-migration.mjs \\
  --request-id ID --operation-epoch EPOCH \\
  --package-digest SHA256 --migration-digest SHA256 \\
  --confirm-migration-digest SHA256 --release-id RELEASE \\
  --approver IDENTITY --reason TEXT [--execute]\n\nThe command uses DB_MIGRATION_USERNAME/DB_MIGRATION_PASSWORD and never passes the password in Docker argv.`);
}
