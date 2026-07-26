import { spawn } from 'node:child_process';
import process from 'node:process';

function terminateProcessTree(child, signal) {
  if (!child?.pid) return;
  if (process.platform !== 'win32') {
    try {
      process.kill(-child.pid, signal);
      return;
    } catch {}
  }
  try {
    child.kill(signal);
  } catch {}
}

export function runManagedCommand({
  command,
  args = [],
  cwd,
  env,
  onController,
  onOutput,
  noProgressTimeoutMs = 0,
  timeoutMs = 0,
  killGraceMs = 5_000,
}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      shell: false,
      env,
      detached: process.platform !== 'win32',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    let settled = false;
    let forcedError = null;
    let noProgressTimer = null;
    let timeoutTimer = null;
    let forceKillTimer = null;

    const clearTimers = () => {
      if (noProgressTimer) clearTimeout(noProgressTimer);
      if (timeoutTimer) clearTimeout(timeoutTimer);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      noProgressTimer = null;
      timeoutTimer = null;
      forceKillTimer = null;
    };

    const cancel = (error) => {
      if (settled || forcedError) return;
      forcedError = error instanceof Error ? error : new Error(String(error || 'Command cancelled.'));
      terminateProcessTree(child, 'SIGTERM');
      forceKillTimer = setTimeout(() => terminateProcessTree(child, 'SIGKILL'), Math.max(100, killGraceMs));
      forceKillTimer.unref?.();
    };

    const armNoProgressTimer = () => {
      if (!(noProgressTimeoutMs > 0) || settled || forcedError) return;
      if (noProgressTimer) clearTimeout(noProgressTimer);
      noProgressTimer = setTimeout(() => {
        const error = new Error(`${command} made no progress for ${Math.ceil(noProgressTimeoutMs / 1000)} seconds and was terminated.`);
        error.code = 'COMMAND_STALLED';
        cancel(error);
      }, noProgressTimeoutMs);
      noProgressTimer.unref?.();
    };

    const handleOutput = (chunk) => {
      const text = chunk.toString();
      output += text;
      onOutput?.(text);
      armNoProgressTimer();
    };

    child.stdout.on('data', handleOutput);
    child.stderr.on('data', handleOutput);
    child.once('error', (error) => {
      if (settled) return;
      settled = true;
      clearTimers();
      reject(forcedError || error);
    });
    child.once('close', (code, signal) => {
      if (settled) return;
      settled = true;
      clearTimers();
      if (forcedError) {
        reject(forcedError);
        return;
      }
      if (code === 0) {
        resolve(output.trim());
        return;
      }
      reject(new Error(`${command} exited with ${code ?? signal ?? 'unknown status'}`));
    });

    onController?.({ cancel, child });
    armNoProgressTimer();
    if (timeoutMs > 0) {
      timeoutTimer = setTimeout(() => {
        const error = new Error(`${command} exceeded its ${Math.ceil(timeoutMs / 1000)} second timeout and was terminated.`);
        error.code = 'COMMAND_TIMEOUT';
        cancel(error);
      }, timeoutMs);
      timeoutTimer.unref?.();
    }
  });
}
