#!/usr/bin/env node

import http from 'node:http';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { parseEnvFile, setEnvValue } from './lib/env-utils.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const deployDir = path.join(repoRoot, 'deploy');
const envPath = path.join(deployDir, '.env');
const tasksDir = path.join(deployDir, '.update-tasks');
const host = process.env.LUMIRA_UPDATER_HOST || '127.0.0.1';
const port = Number(process.env.LUMIRA_UPDATER_PORT || 9788);
const token = process.env.PLATFORM_UPDATE_AGENT_TOKEN || process.env.LUMIRA_UPDATER_TOKEN || '';
const dryRun = process.argv.includes('--dry-run') || process.env.LUMIRA_UPDATER_DRY_RUN === 'true';

mkdirSync(tasksDir, { recursive: true });

function json(res, statusCode, body) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 1024 * 1024) {
        reject(new Error('Request body too large'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(body ? JSON.parse(body) : {}));
    req.on('error', reject);
  });
}

function taskPath(taskId) {
  return path.join(tasksDir, `${taskId}.json`);
}

function writeTask(task) {
  writeFileSync(taskPath(task.taskId), JSON.stringify(task, null, 2), { mode: 0o600 });
}

function readTask(taskId) {
  const file = taskPath(taskId);
  if (!existsSync(file)) {
    return null;
  }
  return JSON.parse(readFileSync(file, 'utf8'));
}

function appendLog(task, message) {
  const backupMatch = String(message).match(/Backup completed:\s*(.+)$/);
  if (backupMatch) {
    task.backupPath = backupMatch[1].trim();
  }
  task.message = message;
  task.log = [...(task.log || []), `${new Date().toISOString()} ${message}`].slice(-80);
  writeTask(task);
}

function runCommand(task, command, args, options = {}) {
  appendLog(task, `$ ${command} ${args.join(' ')}`);
  if (dryRun) {
    appendLog(task, `[dry-run] skipped ${command}`);
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: repoRoot, shell: false, env: { ...process.env, ...options.env } });
    child.stdout.on('data', (chunk) => appendLog(task, chunk.toString().trim()));
    child.stderr.on('data', (chunk) => appendLog(task, chunk.toString().trim()));
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${command} exited with ${code}`));
      }
    });
  });
}

function updateEnvFile(task, values) {
  if (!existsSync(envPath)) {
    throw new Error('deploy/.env not found');
  }
  const backupPath = path.join(tasksDir, `${task.taskId}.env.backup`);
  const original = readFileSync(envPath, 'utf8');
  writeFileSync(backupPath, original, { mode: 0o600 });
  task.envBackupPath = backupPath;
  let next = original;
  for (const [key, value] of Object.entries(values)) {
    if (value) {
      next = setEnvValue(next, key, value);
    }
  }
  if (!dryRun) {
    const tmpPath = `${envPath}.${task.taskId}.tmp`;
    writeFileSync(tmpPath, next, { mode: 0o600 });
    renameSync(tmpPath, envPath);
  }
  appendLog(task, dryRun ? '[dry-run] deploy/.env would be updated' : 'deploy/.env updated');
}

function restoreEnvFile(task) {
  if (!task.envBackupPath || !existsSync(task.envBackupPath)) {
    return;
  }
  if (!dryRun) {
    renameSync(task.envBackupPath, envPath);
  }
  appendLog(task, dryRun ? '[dry-run] deploy/.env would be restored' : 'deploy/.env restored');
}

async function runInstall(task, request) {
  const env = parseEnvFile(envPath);
  task.previous = {
    serverImage: env.LUMIRA_SERVER_IMAGE || '',
    frontendImage: env.LUMIRA_FRONTEND_IMAGE || '',
    appVersion: env.APP_VERSION || '',
    buildVersion: env.BUILD_VERSION || '',
    gitCommit: env.GIT_COMMIT || '',
  };
  if (!request.serverImage) {
    throw new Error('serverImage is required');
  }
  await runCommand(task, 'bash', ['deploy/backup-platform.sh']);
  task.backupPath = task.backupPath || 'created-by-backup-platform.sh';
  if (request.serverImage) {
    await runCommand(task, 'docker', ['pull', request.serverImage]);
  }
  if (request.frontendImage) {
    await runCommand(task, 'docker', ['pull', request.frontendImage]);
  }
  updateEnvFile(task, {
    LUMIRA_SERVER_IMAGE: request.serverImage,
    LUMIRA_FRONTEND_IMAGE: request.frontendImage,
    APP_VERSION: request.targetVersion,
    BUILD_VERSION: request.targetVersion && request.targetCommit ? `${request.targetVersion}+${request.targetCommit}` : request.targetVersion,
    GIT_COMMIT: request.targetCommit,
  });
  try {
    await runCommand(task, 'node', ['scripts/deploy-container.mjs', '--pull']);
    await runCommand(task, 'node', ['scripts/check-deployment.mjs']);
  } catch (error) {
    appendLog(task, `install failed, rolling back: ${error.message}`);
    restoreEnvFile(task);
    await runCommand(task, 'node', ['scripts/deploy-container.mjs', '--pull']).catch((rollbackError) => {
      appendLog(task, `rollback deploy failed: ${rollbackError.message}`);
    });
    throw error;
  }
}

async function runRollback(task) {
  const backups = readdirSync(tasksDir)
    .filter((name) => name.endsWith('.env.backup'))
    .map((name) => path.join(tasksDir, name))
    .sort()
    .reverse();
  const backup = backups.find((item) => existsSync(item));
  if (!backup) {
    throw new Error('No previous env backup found for rollback.');
  }
  task.envBackupPath = backup;
  restoreEnvFile(task);
  await runCommand(task, 'node', ['scripts/deploy-container.mjs', '--pull']);
  await runCommand(task, 'node', ['scripts/check-deployment.mjs']);
}

function startTask(type, request) {
  const task = {
    taskId: randomUUID(),
    type,
    platformTaskId: request.platformTaskId,
    status: 'RUNNING',
    targetVersion: request.targetVersion || '',
    targetCommit: request.targetCommit || '',
    serverImage: request.serverImage || '',
    frontendImage: request.frontendImage || '',
    backupPath: '',
    message: 'Task accepted',
    log: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  writeTask(task);
  setImmediate(async () => {
    try {
      if (type === 'INSTALL') {
        await runInstall(task, request);
      } else {
        await runRollback(task, request);
      }
      task.status = type === 'ROLLBACK' ? 'ROLLED_BACK' : 'SUCCEEDED';
      appendLog(task, 'Task completed');
    } catch (error) {
      task.status = 'FAILED';
      task.errorMessage = error instanceof Error ? error.message : String(error);
      appendLog(task, task.errorMessage);
    } finally {
      task.updatedAt = new Date().toISOString();
      task.finishedAt = new Date().toISOString();
      writeTask(task);
    }
  });
  return task;
}

function authorized(req) {
  if (!token) {
    return false;
  }
  return req.headers['x-lumira-updater-token'] === token;
}

const server = http.createServer(async (req, res) => {
  try {
    if (!authorized(req)) {
      json(res, 401, { errorMessage: 'Unauthorized' });
      return;
    }
    if (req.method === 'GET' && req.url === '/v1/health') {
      json(res, 200, { status: 'UP', dryRun });
      return;
    }
    if (req.method === 'POST' && req.url === '/v1/update/install') {
      const task = startTask('INSTALL', await readBody(req));
      json(res, 202, task);
      return;
    }
    if (req.method === 'POST' && req.url === '/v1/update/rollback') {
      const task = startTask('ROLLBACK', await readBody(req));
      json(res, 202, task);
      return;
    }
    const taskMatch = req.url?.match(/^\/v1\/update\/tasks\/([^/]+)$/);
    if (req.method === 'GET' && taskMatch) {
      const task = readTask(taskMatch[1]);
      if (!task) {
        json(res, 404, { errorMessage: 'Task not found' });
        return;
      }
      json(res, 200, task);
      return;
    }
    json(res, 404, { errorMessage: 'Not found' });
  } catch (error) {
    json(res, 500, { errorMessage: error instanceof Error ? error.message : String(error) });
  }
});

server.listen(port, host, () => {
  console.log(`[lumira-updater] listening on http://${host}:${port} dryRun=${dryRun}`);
});
