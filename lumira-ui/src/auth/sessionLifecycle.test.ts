import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  setTokens: vi.fn(),
  syncFromStorage: vi.fn(),
  persistSessionMeta: vi.fn(),
  hasToken: { value: true },
  accessToken: { value: 'token-before-refresh' },
  generation: { value: 1 },
}));

vi.mock('@umijs/max', () => ({
  history: {
    replace: vi.fn(),
  },
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: () => mocks.hasToken.value,
    getAccessToken: () => mocks.accessToken.value,
    getTokenGeneration: () => mocks.generation.value,
    setTokens: mocks.setTokens,
    syncFromStorage: mocks.syncFromStorage,
    clearTokenState: vi.fn(),
  },
}));

vi.mock('@/auth/activity', () => ({
  clearSessionActivity: vi.fn(),
}));

vi.mock('@/auth/clientRuntimeState', () => ({
  clearClientRuntimeState: vi.fn(),
}));

vi.mock('@/auth/loginFlowState', () => ({
  beginBootstrapFlow: vi.fn(),
  endBootstrapFlow: vi.fn(),
  getAuthSessionEpoch: () => 1,
  isBootstrapInProgress: () => false,
  isLoginInProgress: () => false,
}));

vi.mock('@/auth/sessionState', () => ({
  persistSessionMeta: mocks.persistSessionMeta,
}));

import {
  tryRefreshToken,
  tryRefreshTokenOutcome,
  withRoleSwitchRefreshBarrier,
} from './sessionLifecycle';
import { AUTH_SESSION_MUTATION_LOCK_NAME } from './authSessionMutationLock';
import { isRoleSwitchInProgress } from './roleSwitchFlowState';
import { shouldSuppressUnauthorizedSideEffects } from './unauthorizedDecision';
import { buildUnauthorizedRuntimeState } from './unauthorized';

const refreshResponse = {
  accessToken: 'refresh-response-token',
  refreshToken: 'refresh-cookie-placeholder',
  tokenType: 'Bearer',
  expiresIn: 3600,
  sessionVersion: 2,
  permissionsVersion: 'permissions-2',
};

describe('tryRefreshTokenOutcome generation guard', () => {
  beforeEach(() => {
    // Cross-tab lock ordering has dedicated tests. Keep these unit tests on
    // the existing same-tab barrier semantics even when Node exposes locks.
    vi.stubGlobal('navigator', {});
    mocks.request.mockReset();
    mocks.setTokens.mockReset();
    mocks.syncFromStorage.mockReset();
    mocks.persistSessionMeta.mockReset();
    mocks.hasToken.value = true;
    mocks.accessToken.value = 'token-before-refresh';
    mocks.generation.value = 1;
    mocks.syncFromStorage.mockReturnValue(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('does not let a pre-switch refresh response overwrite the switched token', async () => {
    let resolveRefresh!: (value: typeof refreshResponse) => void;
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefresh = resolve;
        }),
    );

    const outcomePromise = tryRefreshTokenOutcome();
    await vi.waitFor(() => expect(mocks.request).toHaveBeenCalledOnce());
    mocks.generation.value = 2;
    resolveRefresh(refreshResponse);

    await expect(outcomePromise).resolves.toBe('superseded');
    expect(mocks.setTokens).not.toHaveBeenCalled();
    expect(mocks.persistSessionMeta).not.toHaveBeenCalled();
  });

  it('still stores a refresh response when the auth generation is unchanged', async () => {
    mocks.request.mockResolvedValue(refreshResponse);

    await expect(tryRefreshTokenOutcome()).resolves.toBe('refreshed');
    expect(mocks.setTokens).toHaveBeenCalledWith({
      accessToken: refreshResponse.accessToken,
      tokenType: refreshResponse.tokenType,
      expiresIn: refreshResponse.expiresIn,
    });
    expect(mocks.persistSessionMeta).toHaveBeenCalledWith({
      sessionVersion: refreshResponse.sessionVersion,
      permissionsVersion: refreshResponse.permissionsVersion,
    });
  });

  it('treats a superseding generation as usable only when it still has a token', async () => {
    let resolveRefresh!: (value: typeof refreshResponse) => void;
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefresh = resolve;
        }),
    );

    const refreshPromise = tryRefreshToken();
    await vi.waitFor(() => expect(mocks.request).toHaveBeenCalledOnce());
    mocks.generation.value = 2;
    resolveRefresh(refreshResponse);

    await expect(refreshPromise).resolves.toBe(true);
    expect(mocks.setTokens).not.toHaveBeenCalled();
  });

  it('does not treat a superseding logout generation as refreshed', async () => {
    let resolveRefresh!: (value: typeof refreshResponse) => void;
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefresh = resolve;
        }),
    );

    const refreshPromise = tryRefreshToken();
    await vi.waitFor(() => expect(mocks.request).toHaveBeenCalledOnce());
    mocks.generation.value = 2;
    mocks.hasToken.value = false;
    resolveRefresh(refreshResponse);

    await expect(refreshPromise).resolves.toBe(false);
    expect(mocks.setTokens).not.toHaveBeenCalled();
  });

  it('deduplicates refreshes within one generation without sharing across generations', async () => {
    const resolveRefreshes: Array<(value: typeof refreshResponse) => void> = [];
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefreshes.push(resolve);
        }),
    );

    const oldGenerationFirst = tryRefreshTokenOutcome();
    const oldGenerationSecond = tryRefreshTokenOutcome();
    expect(mocks.request).toHaveBeenCalledTimes(1);

    mocks.generation.value = 2;
    const newGenerationFirst = tryRefreshTokenOutcome();
    expect(mocks.request).toHaveBeenCalledTimes(2);

    resolveRefreshes[0](refreshResponse);
    await expect(oldGenerationFirst).resolves.toBe('superseded');
    await expect(oldGenerationSecond).resolves.toBe('superseded');

    const newGenerationSecond = tryRefreshTokenOutcome();
    expect(mocks.request).toHaveBeenCalledTimes(2);

    resolveRefreshes[1](refreshResponse);
    await expect(newGenerationFirst).resolves.toBe('refreshed');
    await expect(newGenerationSecond).resolves.toBe('refreshed');
  });

  it('does not send a queued refresh after shared token storage supersedes it', async () => {
    mocks.syncFromStorage.mockImplementation(() => {
      mocks.generation.value = 2;
      return true;
    });

    await expect(tryRefreshTokenOutcome()).resolves.toBe('superseded');
    expect(mocks.syncFromStorage).toHaveBeenCalledWith('token-before-refresh');
    expect(mocks.request).not.toHaveBeenCalled();
    expect(mocks.setTokens).not.toHaveBeenCalled();
  });

  it('syncs a newer shared token before a role-switch action starts', async () => {
    const calls: string[] = [];
    mocks.syncFromStorage.mockImplementation(() => {
      calls.push('sync');
      mocks.accessToken.value = 'cross-tab-refreshed-token';
      mocks.generation.value = 2;
      return true;
    });

    await withRoleSwitchRefreshBarrier(async () => {
      calls.push(`action:${mocks.accessToken.value}`);
    });

    expect(calls).toEqual(['sync', 'action:cross-tab-refreshed-token']);
  });

  it('uses the same origin-wide lock for refresh and role-switch mutations', async () => {
    const requestedLockNames: string[] = [];
    vi.stubGlobal('navigator', {
      locks: {
        request: async <T>(name: string, callback: () => Promise<T> | T): Promise<T> => {
          requestedLockNames.push(name);
          return callback();
        },
      },
    });
    mocks.request.mockResolvedValue(refreshResponse);

    await expect(tryRefreshTokenOutcome()).resolves.toBe('refreshed');
    await withRoleSwitchRefreshBarrier(async () => undefined);

    expect(requestedLockNames).toEqual([
      AUTH_SESSION_MUTATION_LOCK_NAME,
      AUTH_SESSION_MUTATION_LOCK_NAME,
    ]);
  });

  it('suppresses old-request unauthorized side effects for the full pending role switch', async () => {
    let finishRoleSwitch!: () => void;
    const roleSwitch = withRoleSwitchRefreshBarrier(
      () =>
        new Promise<void>((resolve) => {
          finishRoleSwitch = resolve;
        }),
    );
    await vi.waitFor(() => expect(finishRoleSwitch).toBeTypeOf('function'));

    expect(isRoleSwitchInProgress()).toBe(true);
    expect(shouldSuppressUnauthorizedSideEffects(
      {
        skipAuth: false,
        accessToken: 'token-before-refresh',
        hasAuthToken: true,
        authSessionEpoch: 1,
        tokenGeneration: 1,
      },
      buildUnauthorizedRuntimeState('/dashboard/home'),
    )).toBe(true);

    finishRoleSwitch();
    await roleSwitch;
    expect(isRoleSwitchInProgress()).toBe(false);
  });

  it('clears role-switch suppression after a failed barrier action', async () => {
    let rejectRoleSwitch!: (error: Error) => void;
    const roleSwitch = withRoleSwitchRefreshBarrier(
      () =>
        new Promise<void>((_resolve, reject) => {
          rejectRoleSwitch = reject;
        }),
    );
    await vi.waitFor(() => expect(rejectRoleSwitch).toBeTypeOf('function'));
    expect(isRoleSwitchInProgress()).toBe(true);

    rejectRoleSwitch(new Error('role switch rejected'));
    await expect(roleSwitch).rejects.toThrow('role switch rejected');
    expect(isRoleSwitchInProgress()).toBe(false);
    expect(shouldSuppressUnauthorizedSideEffects(
      {
        skipAuth: false,
        accessToken: 'token-before-refresh',
        hasAuthToken: true,
        authSessionEpoch: 1,
        tokenGeneration: 1,
      },
      buildUnauthorizedRuntimeState('/dashboard/home'),
    )).toBe(false);
  });

  it('keeps suppression active across queued role-switch barriers', async () => {
    let finishFirst!: () => void;
    let finishSecond!: () => void;
    const first = withRoleSwitchRefreshBarrier(
      () =>
        new Promise<void>((resolve) => {
          finishFirst = resolve;
        }),
    );
    await vi.waitFor(() => expect(finishFirst).toBeTypeOf('function'));

    const second = withRoleSwitchRefreshBarrier(
      () =>
        new Promise<void>((resolve) => {
          finishSecond = resolve;
        }),
    );
    expect(isRoleSwitchInProgress()).toBe(true);

    finishFirst();
    await first;
    await vi.waitFor(() => expect(finishSecond).toBeTypeOf('function'));
    expect(isRoleSwitchInProgress()).toBe(true);

    finishSecond();
    await second;
    expect(isRoleSwitchInProgress()).toBe(false);
  });

  it('waits for active refreshes and blocks new ones while the role switch runs', async () => {
    const resolveRefreshes: Array<(value: typeof refreshResponse) => void> = [];
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefreshes.push(resolve);
        }),
    );
    let finishRoleSwitch!: () => void;
    const roleSwitchAction = vi.fn(
      () => {
        mocks.generation.value = 2;
        return new Promise<void>((resolve) => {
          finishRoleSwitch = resolve;
        });
      },
    );

    const activeRefresh = tryRefreshTokenOutcome();
    const roleSwitch = withRoleSwitchRefreshBarrier(roleSwitchAction);
    const blockedRefresh = tryRefreshTokenOutcome();

    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(roleSwitchAction).not.toHaveBeenCalled();

    resolveRefreshes[0](refreshResponse);
    await expect(activeRefresh).resolves.toBe('refreshed');
    await vi.waitFor(() => expect(roleSwitchAction).toHaveBeenCalledOnce());
    expect(mocks.request).toHaveBeenCalledTimes(1);

    finishRoleSwitch();
    await roleSwitch;
    await expect(blockedRefresh).resolves.toBe('superseded');
    expect(mocks.request).toHaveBeenCalledTimes(1);
  });

  it('releases blocked refreshes when the role switch request fails', async () => {
    let resolveRefresh!: (value: typeof refreshResponse) => void;
    mocks.request.mockImplementation(
      () =>
        new Promise<typeof refreshResponse>((resolve) => {
          resolveRefresh = resolve;
        }),
    );
    let rejectRoleSwitch!: (error: Error) => void;
    const roleSwitch = withRoleSwitchRefreshBarrier(
      () =>
        new Promise<void>((_, reject) => {
          rejectRoleSwitch = reject;
        }),
    );
    const blockedRefresh = tryRefreshTokenOutcome();

    expect(mocks.request).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(rejectRoleSwitch).toBeTypeOf('function'));
    rejectRoleSwitch(new Error('role switch failed'));
    await expect(roleSwitch).rejects.toThrow('role switch failed');
    await vi.waitFor(() => expect(mocks.request).toHaveBeenCalledOnce());

    const sharedRefresh = tryRefreshTokenOutcome();
    resolveRefresh(refreshResponse);
    await expect(blockedRefresh).resolves.toBe('refreshed');
    await expect(sharedRefresh).resolves.toBe('refreshed');
  });
});
