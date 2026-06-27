#!/usr/bin/env node

import { closeSync, existsSync, mkdirSync, openSync, readFileSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';

import { parseEnvFile } from './lib/env-utils.mjs';
import { createLogger, output as execOutput, resolveRepoRoot, run as execRun } from './lib/exec-utils.mjs';
import { sleep } from './lib/http-utils.mjs';

const log = createLogger('database');
const repoRoot = resolveRepoRoot(import.meta.url);
const envPath = path.join(repoRoot, 'deploy', '.env');
const buildIdentityPath = process.env.BUILD_IDENTITY_FILE || path.join(repoRoot, 'deploy', 'build-identity.env');
const composeFile = path.join(repoRoot, 'deploy', 'docker-compose.prod.yml');
const backupDir = path.join(repoRoot, 'deploy', '.backup');
const mysqlContainerName = 'lumira-mysql';
const confirmPhrase = 'REIMPORT_LUMIRA_DATABASE';
const rawArgs = process.argv.slice(2);
const command = rawArgs[0] || 'help';
let dockerInvocation;

function resolveComposeProjectName() {
  const env = existsSync(envPath) ? parseEnvFile(envPath) : {};
  return process.env.COMPOSE_PROJECT_NAME || env.COMPOSE_PROJECT_NAME || path.basename(path.dirname(composeFile));
}

function mysqlVolumeName() {
  return `${resolveComposeProjectName()}_mysql_data`;
}

function usage() {
  console.log(`Usage:
  node bin/database-maintenance.mjs verify
  node bin/database-maintenance.mjs backup
  node bin/database-maintenance.mjs reimport --confirm ${confirmPhrase}

Commands:
  verify    Start local MySQL if needed, then verify core tables and database version metadata.
  backup    Start local MySQL if needed, then write a mysqldump file under deploy/.backup.
  reimport  Backup first, stop the stack, remove only ${mysqlVolumeName()}, then import saas.sql into a fresh MySQL volume.
`);
}

function composeArgs(...extraArgs) {
  const toComposePath = (filePath) => path.relative(repoRoot, filePath).replaceAll(path.sep, '/');
  const args = ['compose', '--env-file', toComposePath(envPath)];
  if (existsSync(buildIdentityPath)) {
    args.push('--env-file', toComposePath(buildIdentityPath));
  }
  args.push('-f', toComposePath(composeFile), '--profile', 'local-mysql', ...extraArgs);
  return args;
}

function run(commandName, commandArgs, options = {}) {
  try {
    return execRun(commandName, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    console.error(`[database] Command failed with exit code ${err.status ?? 1}: ${[commandName, ...commandArgs].join(' ')}`);
    if (err.message) {
      console.error(`[database] ${err.message}`);
    }
    process.exit(err.status ?? 1);
  }
}

function output(commandName, commandArgs, options = {}) {
  try {
    return execOutput(commandName, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    console.error(`[database] Command failed with exit code ${err.status ?? 1}: ${[commandName, ...commandArgs].join(' ')}`);
    if (err.message) {
      console.error(`[database] ${err.message}`);
    }
    process.exit(err.status ?? 1);
  }
}

function directOutput(commandName, commandArgs) {
  const result = directResult(commandName, commandArgs);
  if (result.status !== 0) {
    console.error(`[database] Command failed with exit code ${result.status ?? 1}: ${[commandName, ...commandArgs].join(' ')}`);
    if (result.stdout) {
      console.error(result.stdout.trimEnd());
    }
    if (result.stderr) {
      console.error(result.stderr.trimEnd());
    }
    process.exit(result.status ?? 1);
  }
  return result.stdout.trim();
}

function directResult(commandName, commandArgs) {
  const invocation = resolveDirectInvocation(commandName, commandArgs);
  return spawnSync(invocation.command, invocation.args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: 'pipe',
    shell: false,
  });
}

function directStreamStdout(commandName, commandArgs, stdoutFd) {
  const invocation = resolveDirectInvocation(commandName, commandArgs);
  return spawnSync(invocation.command, invocation.args, {
    cwd: repoRoot,
    stdio: ['ignore', stdoutFd, 'inherit'],
    shell: false,
  });
}

function resolveDirectInvocation(commandName, commandArgs) {
  if (commandName !== 'docker' || process.platform !== 'win32') {
    return { command: commandName, args: commandArgs };
  }
  if (!dockerInvocation) {
    dockerInvocation = resolveDockerInvocation();
  }
  return {
    command: dockerInvocation.command,
    args: [...dockerInvocation.prefixArgs, ...commandArgs],
  };
}

function resolveDockerInvocation() {
  const result = spawnSync('where.exe', ['docker'], {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: 'pipe',
    shell: false,
  });
  const dockerPath = result.status === 0
    ? result.stdout.split(/\r?\n/).map((line) => line.trim()).find(Boolean)
    : '';
  if (dockerPath && /\.cmd$/i.test(dockerPath) && existsSync(dockerPath)) {
    const wrapper = readFileSync(dockerPath, 'utf8');
    const match = wrapper.match(/\bwsl\s+-d\s+(\S+)\s+-u\s+(\S+)\s+--\s+docker\b/i);
    if (match) {
      return {
        command: 'wsl.exe',
        prefixArgs: ['-d', match[1], '-u', match[2], '--', 'docker'],
      };
    }
  }
  return { command: dockerPath || 'docker', prefixArgs: [] };
}

function mergedEnv() {
  return {
    ...parseEnvFile(envPath),
    ...(existsSync(buildIdentityPath) ? parseEnvFile(buildIdentityPath) : {}),
    ...Object.fromEntries(Object.entries(process.env).filter(([, value]) => value !== undefined)),
  };
}

function databaseName() {
  return mergedEnv().MYSQL_DATABASE || 'saas';
}

function mysqlPassword() {
  const password = mergedEnv().DB_PASSWORD || mergedEnv().MYSQL_ROOT_PASSWORD;
  if (!password) {
    console.error('[database] DB_PASSWORD or MYSQL_ROOT_PASSWORD must be set in deploy/.env.');
    process.exit(1);
  }
  return password;
}

function mysqlBaseArgs() {
  return [
    'exec',
    '-e',
    `MYSQL_PWD=${mysqlPassword()}`,
    mysqlContainerName,
    'mysql',
    '--protocol=socket',
    '--socket=/var/run/mysqld/mysqld.sock',
    '-uroot',
  ];
}

async function ensureMysqlReady() {
  if (!containerRunning(mysqlContainerName)) {
    run('docker', composeArgs('up', '-d', 'mysql'));
  }

  const startedAt = Date.now();
  while (Date.now() - startedAt < 180_000) {
    const status = execOutput('docker', ['inspect', '-f', '{{.State.Health.Status}}', mysqlContainerName], {
      cwd: repoRoot,
      check: false,
      encoding: 'utf8',
      stdio: 'pipe',
    }).trim();
    if (status === 'healthy' && mysqlReady()) {
      log('MySQL is healthy.');
      return;
    }
    log(`Waiting for MySQL readiness, current health=${status || 'unknown'}...`);
    await sleep(3000);
  }

  run('docker', ['logs', '--tail=120', mysqlContainerName]);
  console.error('[database] MySQL did not become healthy within 180 seconds.');
  process.exit(1);
}

function containerRunning(containerName) {
  const result = directResult('docker', [
    'inspect',
    '-f',
    '{{.State.Running}}',
    containerName,
  ]);
  return result.status === 0 && result.stdout.trim() === 'true';
}

function mysqlReady() {
  const result = directResult('docker', [
    ...mysqlBaseArgs(),
    '-N',
    '-B',
    '-e',
    'SELECT 1',
  ]);
  return result.status === 0 && result.stdout.trim() === '1';
}

function mysqlQuery(sql, { includeDatabase = true } = {}) {
  const args = [
    ...mysqlBaseArgs(),
    ...(includeDatabase ? [databaseName()] : []),
    '-N',
    '-B',
    '-e',
    sql,
  ];
  return directOutput('docker', args).trim();
}

function timestamp() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

async function backupDatabase() {
  await ensureMysqlReady();
  mkdirSync(backupDir, { recursive: true });

  const targetPath = path.join(backupDir, `saas-before-reimport-${timestamp()}.sql`);
  const fd = openSync(targetPath, 'w');
  const args = [
    'exec',
    '-e',
    `MYSQL_PWD=${mysqlPassword()}`,
    mysqlContainerName,
    'mysqldump',
    '--protocol=socket',
    '--socket=/var/run/mysqld/mysqld.sock',
    '-uroot',
    '--single-transaction',
    '--routines',
    '--triggers',
    '--default-character-set=utf8mb4',
    databaseName(),
  ];

  log(`Writing backup: ${targetPath}`);
  const result = directStreamStdout('docker', args, fd);
  closeSync(fd);

  if (result.status !== 0) {
    console.error(`[database] Backup failed with exit code ${result.status ?? 1}. Database volume was not changed.`);
    process.exit(result.status ?? 1);
  }

  const size = statSync(targetPath).size;
  if (size < 1024) {
    console.error(`[database] Backup file is unexpectedly small (${size} bytes). Database volume was not changed.`);
    process.exit(1);
  }

  log(`Backup complete: ${targetPath} (${size} bytes)`);
  return targetPath;
}

function requireReimportConfirmation() {
  const confirmIndex = rawArgs.indexOf('--confirm');
  const value = confirmIndex >= 0 ? rawArgs[confirmIndex + 1] : '';
  if (value !== confirmPhrase) {
    console.error(`[database] Reimport removes only Docker volume ${mysqlVolumeName()}, but it is destructive.`);
    console.error(`[database] Re-run with: node bin/database-maintenance.mjs reimport --confirm ${confirmPhrase}`);
    process.exit(1);
  }
}

async function reimportDatabase() {
  requireReimportConfirmation();
  const backupPath = await backupDatabase();
  log(`Safe backup exists before reset: ${backupPath}`);

  run('docker', composeArgs('down', '--remove-orphans'));
  const removeResult = execRun('docker', ['volume', 'rm', mysqlVolumeName()], {
    cwd: repoRoot,
    check: false,
    encoding: 'utf8',
    stdio: 'pipe',
  });
  if (removeResult.status !== 0) {
    log(`MySQL volume ${mysqlVolumeName()} was not removed, likely because it did not exist yet.`);
  } else {
    log(`Removed MySQL volume: ${mysqlVolumeName()}`);
  }

  await ensureMysqlReady();
  await verifyDatabase();
}

async function verifyDatabase() {
  await ensureMysqlReady();
  const dbName = databaseName();
  const tableCount = Number(mysqlQuery(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${dbName.replaceAll("'", "''")}'`, { includeDatabase: false }));
  const coreTables = ['sys_user', 'sys_config', 'ai_knowledge_base', 'xxl_job_info'];
  const missingTables = coreTables.filter((tableName) => {
    const found = Number(mysqlQuery(
      `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='${tableName}'`
    ));
    return found === 0;
  });
  const databaseVersion = missingTables.includes('sys_config')
    ? ''
    : mysqlQuery("SELECT config_value FROM sys_config WHERE config_key='platform.database.version' LIMIT 1");

  log(`Database=${dbName}, tables=${tableCount}`);
  log(`Database version metadata=${databaseVersion || '(not recorded yet; backend records it after startup)'}`);

  if (tableCount < 50 || missingTables.length > 0) {
    console.error(`[database] Verification failed. Missing core tables: ${missingTables.join(', ') || 'none'}`);
    process.exit(1);
  }
  log('Database verification passed.');
}

switch (command) {
  case 'verify':
    await verifyDatabase();
    break;
  case 'backup':
    await backupDatabase();
    break;
  case 'reimport':
    await reimportDatabase();
    break;
  case 'help':
  case '--help':
  case '-h':
    usage();
    break;
  default:
    console.error(`[database] Unknown command: ${command}`);
    usage();
    process.exit(1);
}
