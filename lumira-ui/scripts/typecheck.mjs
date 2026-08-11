import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const generatedTsconfig = path.join(projectRoot, 'src', '.umi', 'tsconfig.json');
const maxBin = require.resolve('@umijs/max/bin/max.js');
const tscBin = require.resolve('typescript/bin/tsc');

const runNodeCommand = (entry, args) => {
  const result = spawnSync(process.execPath, [entry, ...args], {
    cwd: projectRoot,
    env: process.env,
    stdio: 'inherit',
  });

  if (result.error) {
    throw result.error;
  }
  if (result.signal) {
    process.kill(process.pid, result.signal);
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
};

// `max setup` removes src/.umi before regenerating it. Reusing the files that
// an active dev server already maintains keeps typechecking from invalidating
// every loaded development chunk and turning the browser into a white page.
if (!existsSync(generatedTsconfig)) {
  runNodeCommand(maxBin, ['setup']);
}

runNodeCommand(tscBin, ['--noEmit']);
