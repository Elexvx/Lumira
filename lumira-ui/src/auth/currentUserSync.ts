import { request } from '@/services/common/request';
import type { CurrentUser, MenuNode, PluginAvailability } from '@/types/api';

export const CURRENT_USER_SYNC_EVENT = 'lumira:current-user-sync';
export const CURRENT_USER_SYNC_INTERVAL_MS = 5_000;

const CURRENT_USER_SYNC_TIMEOUT_MS = 8_000;
let currentUserRequest: Promise<CurrentUser> | null = null;
let currentNavigationRequest: Promise<CurrentNavigationSnapshot> | null = null;

export interface CurrentNavigationSnapshot {
  menuTree: MenuNode[];
  availablePlugins: PluginAvailability[];
}

const currentNavigationRequestOptions = {
  method: 'GET',
  autoRedirectOnUnauthorized: false,
  allowUnauthorizedWithoutRedirect: true,
  silent: true,
  timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
} as const;

const requestCurrentUser = () =>
  request<CurrentUser>('/v2/auth/current-user', {
    method: 'GET',
    autoRedirectOnUnauthorized: false,
    allowUnauthorizedWithoutRedirect: true,
    silent: true,
    timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
  }).catch(() =>
    request<CurrentUser>('/v1/auth/current-user', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
      timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
    }),
  );

export const loadCurrentUserSnapshot = (): Promise<CurrentUser> => {
  if (currentUserRequest) {
    return currentUserRequest;
  }

  currentUserRequest = requestCurrentUser().finally(() => {
    currentUserRequest = null;
  });
  return currentUserRequest;
};

const requestCurrentNavigation = () =>
  request<CurrentNavigationSnapshot>('/v2/plugins/current/bootstrap', currentNavigationRequestOptions)
    .catch(() => request<CurrentNavigationSnapshot>('/v1/plugins/current/bootstrap', currentNavigationRequestOptions));

export const loadCurrentNavigationSnapshot = (): Promise<CurrentNavigationSnapshot> => {
  if (currentNavigationRequest) {
    return currentNavigationRequest;
  }

  currentNavigationRequest = requestCurrentNavigation().finally(() => {
    currentNavigationRequest = null;
  });
  return currentNavigationRequest;
};

export const hasCurrentUserSnapshotChanged = (
  previous: CurrentUser | undefined,
  next: CurrentUser,
) => JSON.stringify(previous ?? null) !== JSON.stringify(next);

export const hasCurrentUserNavigationChanged = (
  previous: CurrentUser | undefined,
  next: CurrentUser,
) =>
  previous?.permissionsVersion !== next.permissionsVersion
  || previous?.simulatedRoleId !== next.simulatedRoleId
  || JSON.stringify(previous?.roleIds ?? []) !== JSON.stringify(next.roleIds ?? [])
  || JSON.stringify(previous?.availableRoles ?? []) !== JSON.stringify(next.availableRoles ?? []);

export const notifyCurrentUserSync = () => {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(CURRENT_USER_SYNC_EVENT));
  }
};
