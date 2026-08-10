import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  ensureLocalAdminCredential,
  ensureLocalAdminSecret,
  formatLocalAdminNotice,
  generateLocalAdminPassword,
  parseJdbcEndpoint,
  resolveLocalAdminSecretPath,
  runLocalAdminBootstrap,
} from './lib/local-admin-bootstrap.mjs';

function generatedDatabasePassword() {
  return randomBytes(24).toString('base64url');
}

test('JDBC endpoint parsing rejects multi-host failover URLs', () => {
  assert.throws(
    () => parseJdbcEndpoint('jdbc:mysql://127.0.0.1:3306,remote.example:3306/lumira'),
    /single-host/,
  );
  assert.deepEqual(
    parseJdbcEndpoint('jdbc:mysql://[::1]:3307/lumira?useSSL=false'),
    { host: '::1', port: 3307 },
  );
});

test('generated local administrator passwords satisfy every bootstrap rule', () => {
  const first = generateLocalAdminPassword();
  const second = generateLocalAdminPassword();

  assert.equal(first.length, 28);
  assert.match(first, /[A-Z]/);
  assert.match(first, /[a-z]/);
  assert.match(first, /[0-9]/);
  assert.match(first, /[^A-Za-z0-9]/);
  assert.notEqual(first, second);
});

test('local administrator secret is stable and owner-only', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-secret-'));
  const secretPath = resolveLocalAdminSecretPath(repoRoot, 'jdbc:mysql://127.0.0.1:3306/lumira', 'lumira');
  const firstPassword = generateLocalAdminPassword();
  const secondPassword = generateLocalAdminPassword();
  const first = ensureLocalAdminSecret(secretPath, () => firstPassword);
  const second = ensureLocalAdminSecret(secretPath, () => secondPassword);

  assert.equal(first.createdThisRun, true);
  assert.equal(first.password, undefined);
  assert.equal(second.createdThisRun, false);
  assert.equal(second.password, undefined);
  assert.equal(readFileSync(secretPath, 'utf8'), firstPassword);
  if (process.platform !== 'win32') {
    assert.equal(statSync(path.dirname(secretPath)).mode & 0o777, 0o700);
    assert.equal(statSync(secretPath).mode & 0o777, 0o600);
  }
});

test('bootstrap passes only the password file path and never the password in argv', () => {
  let invocation;
  const result = runLocalAdminBootstrap({
    jarPath: '/tmp/bootstrap.jar',
    databaseEnv: { DB_URL: 'jdbc:mysql://127.0.0.1/lumira', DB_USERNAME: 'lumira', DB_PASSWORD: generatedDatabasePassword() },
    passwordFile: '/tmp/admin-password',
    initializationSource: 'LOCAL_RANDOM',
    inheritedEnv: { PATH: process.env.PATH, LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE: '/tmp/untrusted' },
    commandRunner(command, args, options) {
      invocation = { command, args, options };
      return { status: 0, stdout: 'Administrator credential bootstrap outcome: INITIALIZED', stderr: '' };
    },
  });

  assert.equal(result.status, 0);
  assert.deepEqual(invocation.args, ['-jar', '/tmp/bootstrap.jar']);
  assert.equal(invocation.options.env.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE, '/tmp/admin-password');
  assert.equal(invocation.options.env.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE, 'LOCAL_RANDOM');
  assert.doesNotMatch(JSON.stringify(invocation.args), /administrator-secret/i);
});

test('pending administrator is initialized from a generated stable secret', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-bootstrap-'));
  const calls = [];
  const generatedPassword = generateLocalAdminPassword();
  const commandRunner = (_command, _args, options) => {
    calls.push(options);
    if (!options.env.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE) {
      return { status: 1, stdout: '', stderr: 'Built-in administrator is pending initialization' };
    }
    return { status: 0, stdout: 'Administrator credential bootstrap outcome: INITIALIZED', stderr: '' };
  };

  const result = ensureLocalAdminCredential({
    repoRoot,
    jarPath: '/tmp/bootstrap.jar',
    databaseEnv: { DB_URL: 'jdbc:mysql://127.0.0.1/lumira', DB_USERNAME: 'lumira', DB_PASSWORD: generatedDatabasePassword() },
    commandRunner,
    passwordFactory: () => generatedPassword,
  });

  assert.equal(result.outcome, 'INITIALIZED');
  assert.equal(result.password, undefined);
  assert.equal(readFileSync(result.secretPath, 'utf8'), generatedPassword);
  assert.equal(calls.length, 2);
  assert.equal(calls[1].env.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE, 'LOCAL_RANDOM');
});

test('an initialized administrator does not generate or reveal another password', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-bootstrap-'));
  const result = ensureLocalAdminCredential({
    repoRoot,
    jarPath: '/tmp/bootstrap.jar',
    databaseEnv: { DB_URL: 'jdbc:mysql://127.0.0.1/lumira', DB_USERNAME: 'lumira', DB_PASSWORD: generatedDatabasePassword() },
    commandRunner: () => ({
      status: 0,
      stdout: 'Administrator credential bootstrap outcome: ALREADY_INITIALIZED',
      stderr: '',
    }),
    passwordFactory: () => {
      throw new Error('password factory must not run');
    },
  });

  assert.deepEqual(result, { outcome: 'ALREADY_INITIALIZED', createdThisRun: false });
  assert.doesNotMatch(formatLocalAdminNotice(result, repoRoot).join('\n'), /password saved|One-time password/i);
});

test('a concurrent initializer never removes the shared candidate secret', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-race-'));
  let calls = 0;
  const generatedPassword = generateLocalAdminPassword();
  const databaseEnv = {
    DB_URL: 'jdbc:mysql://127.0.0.1/lumira',
    DB_USERNAME: 'lumira',
    DB_PASSWORD: generatedDatabasePassword(),
  };
  const result = ensureLocalAdminCredential({
    repoRoot,
    jarPath: '/tmp/bootstrap.jar',
    databaseEnv,
    passwordFactory: () => generatedPassword,
    commandRunner: () => {
      calls += 1;
      return calls === 1
        ? { status: 1, stdout: '', stderr: 'Built-in administrator is pending initialization' }
        : { status: 0, stdout: 'Administrator credential bootstrap outcome: ALREADY_INITIALIZED', stderr: '' };
    },
  });
  const secretPath = resolveLocalAdminSecretPath(repoRoot, databaseEnv.DB_URL, databaseEnv.DB_USERNAME);

  assert.equal(result.outcome, 'ALREADY_INITIALIZED');
  assert.equal(readFileSync(secretPath, 'utf8'), generatedPassword);
});

test('local notices never reveal plaintext credentials', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-notice-'));
  const secretPath = path.join(repoRoot, 'runtime-secrets', 'local-admin.password');
  const generatedPassword = generateLocalAdminPassword();
  const result = { outcome: 'INITIALIZED', secretPath, password: generatedPassword, createdThisRun: true };

  const standardNotice = formatLocalAdminNotice(result, repoRoot).join('\n');
  const compatibilityNotice = formatLocalAdminNotice(result, repoRoot, true).join('\n');
  for (const notice of [standardNotice, compatibilityNotice]) {
    assert.ok(!notice.includes(generatedPassword));
    assert.match(notice, /runtime-secrets/);
  }
});

test('configured password file is supported without replacing its contents', () => {
  const repoRoot = mkdtempSync(path.join(os.tmpdir(), 'lumira-admin-configured-'));
  const configuredSecretPath = path.join(repoRoot, 'configured-password');
  const configuredPassword = generateLocalAdminPassword();
  writeFileSync(configuredSecretPath, configuredPassword, { mode: 0o600 });
  const calls = [];

  const result = ensureLocalAdminCredential({
    repoRoot,
    jarPath: '/tmp/bootstrap.jar',
    configuredSecretPath,
    databaseEnv: { DB_URL: 'jdbc:mysql://127.0.0.1/lumira', DB_USERNAME: 'lumira', DB_PASSWORD: generatedDatabasePassword() },
    commandRunner: (_command, _args, options) => {
      calls.push(options);
      return options.env.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE
        ? { status: 0, stdout: 'Administrator credential bootstrap outcome: INITIALIZED', stderr: '' }
        : { status: 1, stdout: '', stderr: 'Built-in administrator is pending initialization' };
    },
  });

  assert.equal(result.password, undefined);
  assert.equal(calls[1].env.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE, 'LOCAL_SECRET_FILE');
  assert.equal(readFileSync(configuredSecretPath, 'utf8'), configuredPassword);
});

test('native startup keeps checks read-only and binds application runtimes to loopback', () => {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const source = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');
  const checkExit = source.indexOf('if (checkOnly)');
  const bootstrap = source.indexOf('ensureLocalAdminCredential({');
  const processStart = source.indexOf('for (const spec of backendSpecs)');

  assert.ok(checkExit >= 0 && bootstrap > checkExit);
  assert.ok(processStart > bootstrap);
  assert.match(source, /activeProfiles\.includes\('dev'\) && isLoopback\(databaseEndpoint\.host\)/);
  assert.match(source, /--server\.address=127\.0\.0\.1/);
});

test('frontend loopback preload adds the requested host to hostless listeners', () => {
  const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const preloadPath = path.join(repoRoot, 'lumira-ui', 'scripts', 'force-loopback-listen.cjs');
  const probe = spawnSync(process.execPath, [
    '-e',
    `const net=require('node:net');net.Server.prototype.listen=function(...args){process.stdout.write(JSON.stringify(args.slice(0,2)));return this;};require(${JSON.stringify(preloadPath)});new net.Server().listen(8000,()=>{});`,
  ], {
    encoding: 'utf8',
    env: { ...process.env, HOST: '127.0.0.1', UMI_DEV_HOST: '' },
  });

  assert.equal(probe.status, 0, probe.stderr);
  assert.deepEqual(JSON.parse(probe.stdout), [8000, '127.0.0.1']);
});
