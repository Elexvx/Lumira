import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const maxBin = require.resolve('@umijs/max/bin/max.js');
const rawArgs = process.argv.slice(2);
const forwardedArgs = rawArgs[0] === '--' ? rawArgs.slice(1) : rawArgs;
const childEnv = { ...process.env };

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

const child = spawn(process.execPath, [maxBin, 'dev', ...forwardedArgs], {
  stdio: 'inherit',
  env: childEnv,
});

const forwardSignal = (signal) => {
  if (!child.killed) {
    child.kill(signal);
  }
};

process.on('SIGINT', () => forwardSignal('SIGINT'));
process.on('SIGTERM', () => forwardSignal('SIGTERM'));

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 0);
});

child.on('error', (error) => {
  console.error(error);
  process.exit(1);
});
