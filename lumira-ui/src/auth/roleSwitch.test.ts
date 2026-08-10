import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUserRoleOption } from '@/types/api';
import { TOKEN_STORAGE_KEY } from '@/auth/token';
import {
  ROLE_SWITCH_BROADCAST_TYPE,
  ROLE_SWITCH_STORAGE_KEY,
  ROLE_SIMULATION_EXIT_KEY,
  buildRoleSwitchOptions,
  buildSimulatedRoleSwitchRequestOptions,
  canSwitchRole,
  completeRoleSwitchClientTransition,
  createRoleSwitchRequestGuard,
  handleRoleSwitchBroadcastMessage,
  handleRoleSwitchStorageEvent,
  notifyOtherTabsOfRoleSwitch,
  resolveRoleSwitchRequestTarget,
  resolveRoleSwitchTarget,
  transitionRoleScopedClientState,
} from './roleSwitch';

const roles: CurrentUserRoleOption[] = [
  {
    id: 7,
    roleCode: 'reviewer',
    roleName: 'Reviewer',
    roleType: 'FUNCTIONAL',
  },
  {
    id: 9,
    roleCode: 'operator',
    roleName: 'Operator',
    roleType: 'FUNCTIONAL',
  },
];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('role switch options', () => {
  it('does not expose role switching while an initial password change is required', () => {
    expect(canSwitchRole(roles, true)).toBe(false);
    expect(canSwitchRole([], true)).toBe(false);
  });

  it('exposes role switching for an eligible session with available role state', () => {
    expect(canSwitchRole(roles, false)).toBe(true);
    expect(canSwitchRole([], false)).toBe(false);
  });

  it('keeps the exit action available when the active simulation has no role options', () => {
    expect(canSwitchRole([], false, 9)).toBe(true);
    expect(canSwitchRole([], true, 9)).toBe(false);
  });

  it('only exposes selectable roles and marks the simulated role as selected', () => {
    const options = buildRoleSwitchOptions(roles, 9);

    expect(options).toHaveLength(2);
    expect(options.map((option) => option.label)).toEqual(['Reviewer', 'Operator']);
    expect(options.every((option) => option.roleId != null)).toBe(true);
    expect(options.find((option) => option.roleId === 9)?.selected).toBe(true);
  });

  it('does not mark a role as selected outside role simulation', () => {
    const options = buildRoleSwitchOptions(roles, null);

    expect(options.every((option) => !option.selected)).toBe(true);
  });

  it('resolves the explicit exit action to the null simulation target', () => {
    const options = buildRoleSwitchOptions(roles, 9);

    expect(resolveRoleSwitchRequestTarget(ROLE_SIMULATION_EXIT_KEY, options)).toBeNull();
    expect(resolveRoleSwitchRequestTarget('missing-role', options)).toBeUndefined();
    expect(resolveRoleSwitchRequestTarget('9', options)).toBe(9);
  });

  it('sends the reset target and rotated refresh cookie through the role switch request', () => {
    expect(buildSimulatedRoleSwitchRequestOptions(null)).toMatchObject({
      method: 'PUT',
      data: { roleId: null },
      credentials: 'include',
      allowUnauthorizedWithoutRedirect: true,
      preserveAuthSessionOnUnauthorized: true,
      silent: true,
    });
  });
});

describe('role switch request guard', () => {
  it('rejects a second submit until the active switch finishes', () => {
    const guard = createRoleSwitchRequestGuard();

    expect(guard.tryStart()).toBe(true);
    expect(guard.tryStart()).toBe(false);
    guard.finish();
    expect(guard.tryStart()).toBe(true);
  });
});

describe('role-scoped client transition', () => {
  it('still hard reloads to a safe fallback when post-response local preparation throws', async () => {
    const transition = vi.fn().mockResolvedValue(undefined);

    await completeRoleSwitchClientTransition(
      () => {
        throw new Error('current user snapshot rejected');
      },
      '/dashboard/home',
      transition,
    );

    expect(transition).toHaveBeenCalledWith('/dashboard/home');
  });

  it('notifies other tabs before awaiting old-role query cancellation', async () => {
    const calls: string[] = [];
    let finishCancellation!: () => void;

    const transitionPromise = transitionRoleScopedClientState('/dashboard/home', {
      cancelQueries: () =>
        new Promise<void>((resolve) => {
          finishCancellation = resolve;
          calls.push('cancel');
        }),
      clearQueries: () => {
        calls.push('clear');
      },
      notifyRoleSwitch: () => {
        calls.push('notify');
      },
      replaceLocation: (target) => {
        calls.push(`replace:${target}`);
      },
    });

    expect(calls).toEqual(['notify', 'cancel']);
    finishCancellation();
    await transitionPromise;

    expect(calls).toEqual(['notify', 'cancel', 'clear', 'replace:/dashboard/home']);
  });

  it('still clears and reloads the initiating tab when query cancellation fails', async () => {
    const calls: string[] = [];

    await transitionRoleScopedClientState('/dashboard/home', {
      cancelQueries: async () => {
        calls.push('cancel');
        throw new Error('query cancellation failed');
      },
      clearQueries: () => {
        calls.push('clear');
      },
      notifyRoleSwitch: () => {
        calls.push('notify');
      },
      replaceLocation: (target) => {
        calls.push(`replace:${target}`);
      },
    });

    expect(calls).toEqual(['notify', 'cancel', 'clear', 'replace:/dashboard/home']);
  });

  it('still hard reloads when clearing old-role queries fails', async () => {
    const calls: string[] = [];

    await expect(
      transitionRoleScopedClientState('/dashboard/home', {
        cancelQueries: async () => {
          calls.push('cancel');
        },
        clearQueries: () => {
          calls.push('clear');
          throw new Error('query clearing failed');
        },
        notifyRoleSwitch: () => {
          calls.push('notify');
        },
        replaceLocation: (target) => {
          calls.push(`replace:${target}`);
        },
      }),
    ).rejects.toThrow('query clearing failed');

    expect(calls).toEqual(['notify', 'cancel', 'clear', 'replace:/dashboard/home']);
  });

  it('reloads only for the dedicated cross-tab role-switch storage event', () => {
    const syncToken = vi.fn();
    const reload = vi.fn();
    const runtime = { syncToken, reload };

    expect(handleRoleSwitchStorageEvent({ key: 'unrelated', newValue: '1' }, runtime)).toBe(false);
    expect(handleRoleSwitchStorageEvent({ key: TOKEN_STORAGE_KEY, newValue: '{"accessToken":"new"}' }, runtime)).toBe(false);
    expect(handleRoleSwitchStorageEvent({ key: ROLE_SWITCH_STORAGE_KEY, newValue: null }, runtime)).toBe(false);
    expect(syncToken).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();

    expect(handleRoleSwitchStorageEvent({ key: ROLE_SWITCH_STORAGE_KEY, newValue: 'switch-2' }, runtime)).toBe(true);
    expect(syncToken).toHaveBeenCalledOnce();
    expect(reload).toHaveBeenCalledOnce();
  });

  it('reloads for the role-switch broadcast fallback without claiming a token sync', () => {
    const reload = vi.fn();

    expect(handleRoleSwitchBroadcastMessage({ type: 'updated' }, { reload })).toBe(false);
    expect(reload).not.toHaveBeenCalled();

    expect(handleRoleSwitchBroadcastMessage({
      type: ROLE_SWITCH_BROADCAST_TYPE,
      occurrenceId: 'broadcast-switch-1',
    }, { reload })).toBe(true);
    expect(reload).toHaveBeenCalledOnce();
  });

  it('handles the same storage and broadcast occurrence only once', () => {
    const syncToken = vi.fn();
    const reload = vi.fn();
    const occurrenceId = 'dual-channel-switch-1';
    const signal = {
      type: ROLE_SWITCH_BROADCAST_TYPE,
      occurrenceId,
      occurredAt: Date.now(),
    };

    expect(handleRoleSwitchStorageEvent({
      key: ROLE_SWITCH_STORAGE_KEY,
      newValue: JSON.stringify(signal),
    }, { syncToken, reload })).toBe(true);
    expect(handleRoleSwitchBroadcastMessage(signal, { reload })).toBe(false);
    expect(syncToken).toHaveBeenCalledOnce();
    expect(reload).toHaveBeenCalledOnce();
  });

  it('deduplicates a delayed broadcast after a newer storage occurrence', () => {
    const syncToken = vi.fn();
    const reload = vi.fn();
    const first = {
      type: ROLE_SWITCH_BROADCAST_TYPE,
      occurrenceId: 'interleaved-switch-a',
      occurredAt: Date.now(),
    };
    const second = {
      type: ROLE_SWITCH_BROADCAST_TYPE,
      occurrenceId: 'interleaved-switch-b',
      occurredAt: Date.now() + 1,
    };

    expect(handleRoleSwitchStorageEvent({
      key: ROLE_SWITCH_STORAGE_KEY,
      newValue: JSON.stringify(first),
    }, { syncToken, reload })).toBe(true);
    expect(handleRoleSwitchStorageEvent({
      key: ROLE_SWITCH_STORAGE_KEY,
      newValue: JSON.stringify(second),
    }, { syncToken, reload })).toBe(true);
    expect(handleRoleSwitchBroadcastMessage(first, { reload })).toBe(false);

    expect(syncToken).toHaveBeenCalledTimes(2);
    expect(reload).toHaveBeenCalledTimes(2);
  });

  it('broadcasts a reload signal when the storage notification cannot be written', () => {
    const postMessage = vi.fn();
    const close = vi.fn();
    vi.stubGlobal('window', {
      localStorage: {
        setItem: () => {
          throw new Error('storage unavailable');
        },
      },
    });
    vi.stubGlobal(
      'BroadcastChannel',
      class {
        postMessage = postMessage;
        close = close;
      },
    );

    expect(() => notifyOtherTabsOfRoleSwitch()).not.toThrow();
    expect(postMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        type: ROLE_SWITCH_BROADCAST_TYPE,
        occurrenceId: expect.any(String),
      }),
    );
    expect(close).toHaveBeenCalledOnce();
  });

  it('does not block the initiating transition when all notification channels fail', () => {
    vi.stubGlobal('window', {
      get localStorage() {
        throw new Error('storage unavailable');
      },
    });
    vi.stubGlobal(
      'BroadcastChannel',
      class {
        constructor() {
          throw new Error('broadcast unavailable');
        }
      },
    );

    expect(() => notifyOtherTabsOfRoleSwitch()).not.toThrow();
  });
});
