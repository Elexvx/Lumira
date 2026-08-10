import { queryClient } from '@/query/queryClient';
import type { RequestOptions } from '@/services/common/requestInternalsTypes';
import type { CurrentUserRoleOption } from '@/types/api';
import { AUTH_SESSION_BROADCAST_CHANNEL, tokenManager } from '@/auth/token';

export const ROLE_SWITCH_STORAGE_KEY = 'auth_role_switch_epoch';
export const ROLE_SWITCH_BROADCAST_TYPE = 'role-switched';
export const ROLE_SIMULATION_EXIT_KEY = 'role-simulation-exit';

interface RoleSwitchSignal {
  type: typeof ROLE_SWITCH_BROADCAST_TYPE;
  occurrenceId: string;
  occurredAt: number;
}

const MAX_RECENT_ROLE_SWITCH_OCCURRENCES = 32;
const recentRoleSwitchOccurrenceIds = new Set<string>();
const recentRoleSwitchOccurrenceOrder: string[] = [];

const rememberRoleSwitchOccurrence = (occurrenceId: string) => {
  if (!occurrenceId || recentRoleSwitchOccurrenceIds.has(occurrenceId)) {
    return false;
  }
  recentRoleSwitchOccurrenceIds.add(occurrenceId);
  recentRoleSwitchOccurrenceOrder.push(occurrenceId);
  if (recentRoleSwitchOccurrenceOrder.length > MAX_RECENT_ROLE_SWITCH_OCCURRENCES) {
    const expiredOccurrenceId = recentRoleSwitchOccurrenceOrder.shift();
    if (expiredOccurrenceId) {
      recentRoleSwitchOccurrenceIds.delete(expiredOccurrenceId);
    }
  }
  return true;
};

const resolveStoredRoleSwitchOccurrenceId = (value: string) => {
  try {
    const signal = JSON.parse(value) as Partial<RoleSwitchSignal>;
    if (signal.type === ROLE_SWITCH_BROADCAST_TYPE && signal.occurrenceId) {
      return signal.occurrenceId;
    }
  } catch {
    // Legacy storage markers used the raw value as their occurrence id.
  }
  return value;
};

export interface RoleSwitchOption {
  key: string;
  roleId: number;
  label: string;
  selected: boolean;
}

export const canSwitchRole = (
  availableRoles: CurrentUserRoleOption[],
  requiresPasswordChange: boolean,
  simulatedRoleId: number | null = null,
) => !requiresPasswordChange && (availableRoles.length > 0 || simulatedRoleId != null);

export const buildRoleSwitchOptions = (
  availableRoles: CurrentUserRoleOption[],
  simulatedRoleId: number | null,
): RoleSwitchOption[] =>
  availableRoles.map((role) => ({
    key: String(role.id),
    roleId: role.id,
    label: role.roleName,
    selected: simulatedRoleId === role.id,
  }));

export const resolveRoleSwitchTarget = (
  value: string,
  options: RoleSwitchOption[],
): number | undefined => options.find((option) => option.key === value)?.roleId;

export const resolveRoleSwitchRequestTarget = (
  value: string,
  options: RoleSwitchOption[],
): number | null | undefined => value === ROLE_SIMULATION_EXIT_KEY
  ? null
  : resolveRoleSwitchTarget(value, options);

export const buildSimulatedRoleSwitchRequestOptions = (
  roleId: number | null,
): RequestOptions => ({
  method: 'PUT',
  data: { roleId },
  autoRedirectOnUnauthorized: false,
  allowUnauthorizedWithoutRedirect: true,
  preserveAuthSessionOnUnauthorized: true,
  silent: true,
  credentials: 'include',
});

export const createRoleSwitchRequestGuard = () => {
  let inFlight = false;

  return {
    tryStart: () => {
      if (inFlight) {
        return false;
      }
      inFlight = true;
      return true;
    },
    finish: () => {
      inFlight = false;
    },
  };
};

export const notifyOtherTabsOfRoleSwitch = () => {
  if (typeof window === 'undefined') {
    return;
  }
  const occurredAt = Date.now();
  const signal: RoleSwitchSignal = {
    type: ROLE_SWITCH_BROADCAST_TYPE,
    occurrenceId: `${tokenManager.getTokenGeneration()}:${occurredAt}:${Math.random().toString(36).slice(2)}`,
    occurredAt,
  };
  try {
    const storage = window.localStorage;
    if (storage) {
      storage.setItem(
        ROLE_SWITCH_STORAGE_KEY,
        JSON.stringify(signal),
      );
    }
  } catch {
    // BroadcastChannel below remains an independent notification path.
  }
  if (typeof BroadcastChannel === 'undefined') {
    return;
  }
  let channel: BroadcastChannel | null = null;
  try {
    channel = new BroadcastChannel(AUTH_SESSION_BROADCAST_CHANNEL);
    channel.postMessage(signal);
  } catch {
    // Cross-tab synchronization is best effort; the initiating tab still reloads.
  } finally {
    try {
      channel?.close();
    } catch {
      // Closing a restricted channel is also best-effort.
    }
  }
};

export interface RoleScopedClientTransitionRuntime {
  cancelQueries: () => Promise<void>;
  clearQueries: () => void;
  notifyRoleSwitch: () => void;
  replaceLocation: (target: string) => void;
}

const defaultRoleScopedClientTransitionRuntime: RoleScopedClientTransitionRuntime = {
  cancelQueries: () => queryClient.cancelQueries(),
  clearQueries: () => queryClient.clear(),
  notifyRoleSwitch: notifyOtherTabsOfRoleSwitch,
  replaceLocation: (target) => window.location.replace(target),
};

export const transitionRoleScopedClientState = async (
  target: string,
  runtime: RoleScopedClientTransitionRuntime = defaultRoleScopedClientTransitionRuntime,
) => {
  // The new token is already in storage. Notify other tabs synchronously
  // before awaiting local query cancellation so they cannot adopt only the
  // token while continuing to render the previous role.
  runtime.notifyRoleSwitch();
  try {
    await runtime.cancelQueries();
  } catch {
    // Query cancellation is best effort; clearing and hard navigation are the
    // authorization boundary and must still complete.
  } finally {
    try {
      runtime.clearQueries();
    } finally {
      runtime.replaceLocation(target);
    }
  }
};

export const completeRoleSwitchClientTransition = async (
  prepareTarget: () => string,
  fallbackTarget: string,
  transition: (target: string) => Promise<void> = transitionRoleScopedClientState,
) => {
  let target = fallbackTarget;
  try {
    target = prepareTarget() || fallbackTarget;
  } catch {
    // The server has already rotated the token. A hard navigation is the
    // recovery boundary even if the local current-user snapshot is malformed.
  }
  await transition(target);
};

export interface RoleSwitchStorageRuntime {
  syncToken: () => void;
  reload: () => void;
}

const defaultRoleSwitchStorageRuntime: RoleSwitchStorageRuntime = {
  syncToken: () => {
    tokenManager.syncFromStorage();
  },
  reload: () => window.location.reload(),
};

export const handleRoleSwitchStorageEvent = (
  event: Pick<StorageEvent, 'key' | 'newValue'>,
  runtime: RoleSwitchStorageRuntime = defaultRoleSwitchStorageRuntime,
) => {
  if (event.key !== ROLE_SWITCH_STORAGE_KEY || !event.newValue) {
    return false;
  }

  const occurrenceId = resolveStoredRoleSwitchOccurrenceId(event.newValue);
  if (!rememberRoleSwitchOccurrence(occurrenceId)) {
    return false;
  }

  runtime.syncToken();
  runtime.reload();
  return true;
};

export const handleRoleSwitchBroadcastMessage = (
  data: { type?: string; occurrenceId?: string; occurredAt?: number } | null | undefined,
  runtime: Pick<RoleSwitchStorageRuntime, 'reload'> = {
    reload: () => window.location.reload(),
  },
) => {
  if (data?.type !== ROLE_SWITCH_BROADCAST_TYPE) {
    return false;
  }
  const occurrenceId = data.occurrenceId || `${ROLE_SWITCH_BROADCAST_TYPE}:${data.occurredAt || 'legacy'}`;
  if (!rememberRoleSwitchOccurrence(occurrenceId)) {
    return false;
  }
  runtime.reload();
  return true;
};
