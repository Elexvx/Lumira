import { existsSync, mkdirSync, readdirSync, renameSync } from 'node:fs';
import path from 'node:path';

const root = process.env.WORKER_QUEUE;
if (!root) throw new Error('WORKER_QUEUE is required');
const pending = path.join(root, 'pending');
const processing = path.join(root, 'processing');
const done = path.join(root, 'done');
const duplicates = path.join(root, 'duplicates');
for (const directory of [pending, processing, done, duplicates]) mkdirSync(directory, { recursive: true });

for (const name of readdirSync(processing)) {
  const target = path.join(pending, name);
  if (!existsSync(target)) renameSync(path.join(processing, name), target);
}

let stopping = false;
let busy = false;
process.on('SIGTERM', () => {
  stopping = true;
  if (!busy) process.exit(0);
});

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
while (!stopping) {
  const name = readdirSync(pending).sort()[0];
  if (!name) {
    await delay(20);
    continue;
  }
  const source = path.join(pending, name);
  const claimed = path.join(processing, name);
  try {
    renameSync(source, claimed);
  } catch {
    continue;
  }
  busy = true;
  await delay(25);
  const destination = path.join(done, name);
  if (existsSync(destination)) renameSync(claimed, path.join(duplicates, `${Date.now()}-${name}`));
  else renameSync(claimed, destination);
  busy = false;
}
process.exit(0);
