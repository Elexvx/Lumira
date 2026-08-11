import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(import.meta.url);
const maxBin = require.resolve('@umijs/max/bin/max.js');
const devServerMarker = path.resolve('node_modules/.cache/lumira/dev-server.pid');

const readActiveDevServerPid = () => {
  let pid;
  try {
    pid = Number.parseInt(fs.readFileSync(devServerMarker, 'utf8').trim(), 10);
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
  if (!Number.isInteger(pid) || pid <= 0) {
    return null;
  }
  try {
    process.kill(pid, 0);
    return pid;
  } catch (error) {
    if (error?.code === 'EPERM') {
      return pid;
    }
    if (error?.code !== 'ESRCH') {
      throw error;
    }
    return null;
  }
};

const activeDevServerPid = readActiveDevServerPid();
if (activeDevServerPid) {
  console.error(
    `[build] Refusing to build while the development server (PID ${activeDevServerPid}) is running. ` +
      'Umi/Utoopack production generation invalidates the active development chunks and causes a blank page. ' +
      'Stop the local environment before building.',
  );
  process.exit(1);
}

try {
  fs.rmSync(devServerMarker);
} catch (error) {
  if (error?.code !== 'ENOENT') {
    throw error;
  }
}

const result = spawnSync(process.execPath, [maxBin, 'build'], {
  stdio: 'inherit',
  env: process.env,
});

if (result.error) {
  throw result.error;
}
process.exit(result.status ?? 1);
