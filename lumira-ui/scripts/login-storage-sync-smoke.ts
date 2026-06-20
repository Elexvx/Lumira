import assert from 'node:assert/strict';
import { createLoginSessionBroadcastListener } from '../src/auth/loginRedirect';
import { AUTH_SESSION_BROADCAST_CHANNEL } from '../src/auth/token';

const run = () => {
  const OriginalBroadcastChannel = globalThis.BroadcastChannel;
  const listeners: Array<(event: MessageEvent<{ type?: string }>) => void> = [];
  const redirectCalls: string[] = [];

  class MockBroadcastChannel {
    onmessage: ((event: MessageEvent<{ type?: string }>) => void) | null = null;

    constructor(readonly name: string) {
      assert.equal(name, AUTH_SESSION_BROADCAST_CHANNEL);
      listeners.push((event) => this.onmessage?.(event));
    }

    close() {}
  }

  globalThis.BroadcastChannel = MockBroadcastChannel as typeof BroadcastChannel;

  const dispose = createLoginSessionBroadcastListener('/dashboard/home', (target: string) => {
    redirectCalls.push(target);
  });

  listeners.forEach((listener) => listener({ data: { type: 'noop' } } as MessageEvent<{ type?: string }>));
  assert.equal(redirectCalls.length, 0, 'unrelated auth broadcasts should not redirect');

  listeners.forEach((listener) => listener({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>));
  assert.deepEqual(redirectCalls, ['/dashboard/home'], 'auth session updates should trigger login-page redirect');

  dispose();
  globalThis.BroadcastChannel = OriginalBroadcastChannel;

  console.log('login-storage-sync-smoke: ok');
};

run();
