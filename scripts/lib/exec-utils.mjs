import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function resolveRepoRoot(importMetaUrl) {
  const scriptDir = path.dirname(fileURLToPath(importMetaUrl));
  return path.resolve(scriptDir, '..'); 
}

export function createLogger(prefix) {
  return function log(message) {
    console.log(`[${prefix}] ${message}`);
  };
}

export function run(command, commandArgs, options = {}) {
  const { check = true, ...spawnOptions } = options;
  const { command: resolvedCommand, args: resolvedArgs, shell } = resolveSpawnCommand(command, commandArgs);
  const result = spawnSync(resolvedCommand, resolvedArgs, {
    stdio: 'inherit',
    shell,
    ...spawnOptions,
  });

  if (result.status !== 0 && check) {
    const err = new Error(`Command failed with exit code ${result.status}`);
    err.status = result.status;
    throw err;
  }
  return result;
}

export function output(command, commandArgs, options = {}) {
  const { check = true, ...spawnOptions } = options;
  const { command: resolvedCommand, args: resolvedArgs, shell } = resolveSpawnCommand(command, commandArgs);
  const result = spawnSync(resolvedCommand, resolvedArgs, {
    encoding: 'utf8',
    shell,
    ...spawnOptions,
  });
  
  if (result.status !== 0 && check) {
    const err = new Error(`${command} failed: ${result.stderr}`);
    err.status = result.status;
    throw err;
  }
  return result.status === 0 ? result.stdout.trim() : '';
}

export function optionalOutput(command, commandArgs, options = {}) {
  const { check = false, ...spawnOptions } = options;
  const { command: resolvedCommand, args: resolvedArgs, shell } = resolveSpawnCommand(command, commandArgs);
  const result = spawnSync(resolvedCommand, resolvedArgs, {
    encoding: 'utf8',
    shell,
    ...spawnOptions,
  });
  return result.status === 0 ? result.stdout.trim() : '';
}

export function commandExists(command) {
  return spawnSync('sh', ['-lc', `command -v ${command} >/dev/null 2>&1`], { stdio: 'ignore' }).status === 0;
}

function resolveSpawnCommand(command, args = []) {
  if (process.platform !== 'win32' || /\.sh$/i.test(command)) {
    return { command, args, shell: false };
  }
  return {
    command: 'cmd.exe',
    args: ['/d', '/c', [quoteCmdArg(command), ...args.map(quoteCmdArg)].join(' ')],
    shell: false,
  };
}

function quoteCmdArg(value) {
  const text = String(value);
  if (!/[\s"&<>|^]/.test(text)) {
    return text;
  }
  return `"${text.replaceAll('"', '\\"')}"`;
}
