import { spawn } from 'node:child_process';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(import.meta.url);
const maxBin = require.resolve('@umijs/max/bin/max.js');
const forceLoopbackListen = require.resolve('./force-loopback-listen.cjs');
const rawArgs = process.argv.slice(2);
const forwardedArgs = rawArgs[0] === '--' ? rawArgs.slice(1) : rawArgs;
const childEnv = { ...process.env };
const devServerMarker = path.resolve('node_modules/.cache/lumira/dev-server.pid');

fs.mkdirSync(path.dirname(devServerMarker), { recursive: true });
try {
  const existingPid = Number.parseInt(fs.readFileSync(devServerMarker, 'utf8').trim(), 10);
  if (Number.isInteger(existingPid) && existingPid > 0) {
    try {
      process.kill(existingPid, 0);
      console.error(`[dev] Development server marker is already owned by active PID ${existingPid}.`);
      process.exit(1);
    } catch (error) {
      if (error?.code === 'EPERM') {
        console.error(`[dev] Development server marker is already owned by active PID ${existingPid}.`);
        process.exit(1);
      }
      if (error?.code !== 'ESRCH') {
        throw error;
      }
    }
  }
} catch (error) {
  if (error?.code !== 'ENOENT') {
    throw error;
  }
}
fs.writeFileSync(devServerMarker, String(process.pid));

for (let index = 0; index < forwardedArgs.length; index += 1) {
  const arg = forwardedArgs[index];
  const [key, inlineValue] = arg.split('=');
  const nextValue = inlineValue ?? forwardedArgs[index + 1];

  if ((key === '--host' || key === '--hostname') && nextValue && !nextValue.startsWith('--')) {
    childEnv.UMI_DEV_HOST = nextValue;
    childEnv.HOST = nextValue;
  }
  if (key === '--port' && nextValue && !nextValue.startsWith('--')) {
    childEnv.UMI_DEV_PORT = nextValue;
    childEnv.PORT = nextValue;
    childEnv.STRICT_PORT = nextValue;
  }
}

const requestedHost = String(childEnv.UMI_DEV_HOST || childEnv.HOST || '').toLowerCase();
const forceLoopback = ['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1'].includes(requestedHost);
const child = spawn(process.execPath, [
  ...(forceLoopback ? ['--require', forceLoopbackListen] : []),
  maxBin,
  'dev',
  ...forwardedArgs,
], {
  stdio: 'inherit',
  env: childEnv,
});

const forwardSignal = (signal) => {
  if (!child.killed) {
    child.kill(signal);
  }
};

const removeMarker = () => {
  try {
    if (fs.readFileSync(devServerMarker, 'utf8').trim() === String(process.pid)) {
      fs.rmSync(devServerMarker);
    }
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      console.warn(`[dev] Unable to remove marker: ${error.message}`);
    }
  }
};

process.on('SIGINT', () => forwardSignal('SIGINT'));
process.on('SIGTERM', () => forwardSignal('SIGTERM'));

child.on('exit', (code, signal) => {
  removeMarker();
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 0);
});

child.on('error', (error) => {
  removeMarker();
  console.error(error);
  process.exit(1);
});

process.on('exit', removeMarker);
