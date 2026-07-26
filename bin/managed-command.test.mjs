import assert from 'node:assert/strict';
import test from 'node:test';

import { runManagedCommand } from './lib/managed-command.mjs';

test('managed command cancellation terminates the active child', { timeout: 5_000 }, async () => {
  let controller;
  const command = runManagedCommand({
    command: process.execPath,
    args: ['-e', 'setInterval(() => {}, 1000)'],
    env: process.env,
    killGraceMs: 100,
    onController(value) {
      controller = value;
    },
  });
  const error = new Error('cancelled by test');
  error.code = 'UPDATE_CANCELLED';
  controller.cancel(error);
  await assert.rejects(command, (caught) => caught.code === 'UPDATE_CANCELLED');
});

test('managed command terminates a child that makes no progress', { timeout: 5_000 }, async () => {
  await assert.rejects(runManagedCommand({
    command: process.execPath,
    args: ['-e', 'setInterval(() => {}, 1000)'],
    env: process.env,
    noProgressTimeoutMs: 100,
    killGraceMs: 100,
  }), (error) => error.code === 'COMMAND_STALLED');
});

test('managed command enforces its overall timeout', { timeout: 5_000 }, async () => {
  await assert.rejects(runManagedCommand({
    command: process.execPath,
    args: ['-e', 'console.log("started"); setInterval(() => console.log("progress"), 100)'],
    env: process.env,
    noProgressTimeoutMs: 1_000,
    timeoutMs: 500,
    killGraceMs: 100,
  }), (error) => error.code === 'COMMAND_TIMEOUT');
});

test('managed command resets the no-progress timer when output arrives', { timeout: 5_000 }, async () => {
  const output = await runManagedCommand({
    command: process.execPath,
    args: ['-e', "let count=0; const timer=setInterval(()=>{ console.log(++count); if(count===3){ clearInterval(timer); } }, 100)"],
    env: process.env,
    noProgressTimeoutMs: 500,
    killGraceMs: 100,
  });
  assert.match(output, /3/);
});
