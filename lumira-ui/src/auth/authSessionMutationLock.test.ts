import { describe, expect, it, vi } from 'vitest';
import {
  AUTH_SESSION_MUTATION_LOCK_NAME,
  type AuthSessionMutationLockManager,
  withAuthSessionMutationLock,
} from './authSessionMutationLock';

const createQueuedLockManager = () => {
  let tail: Promise<unknown> = Promise.resolve();
  const requestedNames: string[] = [];
  const manager: AuthSessionMutationLockManager = {
    request: <T>(name: string, callback: () => Promise<T> | T) => {
      requestedNames.push(name);
      const result = tail.then(callback);
      tail = result.then(
        () => undefined,
        () => undefined,
      );
      return result;
    },
  };
  return { manager, requestedNames };
};

describe('withAuthSessionMutationLock', () => {
  it('serializes refresh and role-switch mutations through one origin-wide lock', async () => {
    const { manager, requestedNames } = createQueuedLockManager();
    const calls: string[] = [];
    let finishRefresh!: () => void;

    const refresh = withAuthSessionMutationLock(
      async () => {
        calls.push('refresh:start');
        await new Promise<void>((resolve) => {
          finishRefresh = resolve;
        });
        calls.push('refresh:end');
      },
      manager,
    );
    await vi.waitFor(() => expect(calls).toEqual(['refresh:start']));

    const roleSwitch = withAuthSessionMutationLock(
      async () => {
        calls.push('role-switch:start');
        calls.push('role-switch:end');
      },
      manager,
    );
    await Promise.resolve();
    expect(calls).toEqual(['refresh:start']);

    finishRefresh();
    await Promise.all([refresh, roleSwitch]);

    expect(calls).toEqual([
      'refresh:start',
      'refresh:end',
      'role-switch:start',
      'role-switch:end',
    ]);
    expect(requestedNames).toEqual([
      AUTH_SESSION_MUTATION_LOCK_NAME,
      AUTH_SESSION_MUTATION_LOCK_NAME,
    ]);
  });

  it('runs directly when Web Locks are unavailable', async () => {
    const action = vi.fn().mockResolvedValue('completed');

    await expect(withAuthSessionMutationLock(action, null)).resolves.toBe('completed');
    expect(action).toHaveBeenCalledOnce();
  });

  it('falls back when lock acquisition fails before the action starts', async () => {
    const manager: AuthSessionMutationLockManager = {
      request: async () => {
        throw new Error('locks unavailable in this context');
      },
    };
    const action = vi.fn().mockResolvedValue('completed');

    await expect(withAuthSessionMutationLock(action, manager)).resolves.toBe('completed');
    expect(action).toHaveBeenCalledOnce();
  });

  it('does not repeat an action that fails while holding the lock', async () => {
    const manager: AuthSessionMutationLockManager = {
      request: async (_name, callback) => callback(),
    };
    const action = vi.fn().mockRejectedValue(new Error('mutation failed'));

    await expect(withAuthSessionMutationLock(action, manager)).rejects.toThrow('mutation failed');
    expect(action).toHaveBeenCalledOnce();
  });
});
