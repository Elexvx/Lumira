import { closeSync, existsSync, fsyncSync, openSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

import { assertOperationFence, migrateDeploymentState } from './platform-update-contract.mjs';

export class DeploymentStateRepository {
  constructor(file, { mode = 0o600, beforeRename } = {}) {
    this.file = path.resolve(file);
    this.mode = mode;
    this.beforeRename = beforeRename;
  }

  read(bootstrap = {}) {
    const raw = existsSync(this.file) ? JSON.parse(readFileSync(this.file, 'utf8')) : null;
    return migrateDeploymentState(raw, bootstrap);
  }

  write(state) {
    const next = { ...state, schemaVersion: 3, updatedAt: new Date().toISOString() };
    atomicWriteDurable(this.file, `${JSON.stringify(next, null, 2)}\n`, this.mode, this.beforeRename);
    return next;
  }

  beginCandidate({ taskId, release, activeSlot }) {
    const state = this.read();
    if (state.candidateRelease || state.status === 'UPDATING') throw new Error('another candidate release is already active');
    const operationEpoch = Number(state.operationEpoch || 0) + 1;
    return this.write({
      ...state,
      operationEpoch,
      activeSlot: activeSlot || state.activeSlot,
      status: 'UPDATING',
      candidateRelease: { ...release, taskId: String(taskId), operationEpoch },
    });
  }

  fencedUpdate(fence, mutate) {
    const state = this.read();
    assertOperationFence(state, fence);
    const next = mutate(structuredClone(state));
    if (!next || typeof next !== 'object') throw new Error('fenced deployment state mutation must return a state object');
    assertOperationFence(next, fence);
    return this.write(next);
  }
}

export function atomicWriteDurable(file, content, mode = 0o600, beforeRename) {
  const target = path.resolve(file);
  const temporary = `${target}.${process.pid}.${randomUUID()}.tmp`;
  let descriptor;
  try {
    descriptor = openSync(temporary, 'wx', mode);
    writeFileSync(descriptor, content, { encoding: 'utf8' });
    fsyncSync(descriptor);
    closeSync(descriptor);
    descriptor = undefined;
    beforeRename?.(temporary, target);
    renameSync(temporary, target);
    const directoryDescriptor = openSync(path.dirname(target), 'r');
    try {
      fsyncSync(directoryDescriptor);
    } finally {
      closeSync(directoryDescriptor);
    }
  } catch (error) {
    if (descriptor !== undefined) closeSync(descriptor);
    try { unlinkSync(temporary); } catch {}
    throw error;
  }
}
