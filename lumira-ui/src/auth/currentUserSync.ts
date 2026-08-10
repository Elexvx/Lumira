import { request } from '@/services/common/request';
import { tokenManager } from '@/auth/token';
import type { CurrentUser, MenuNode, PluginAvailability } from '@/types/api';
import { shouldFallbackToLegacyEndpoint } from '@/services/common/legacyEndpointFallback';

export const CURRENT_USER_SYNC_EVENT = 'lumira:current-user-sync';
export const CURRENT_USER_SYNC_INTERVAL_MS = 5_000;

const CURRENT_USER_SYNC_TIMEOUT_MS = 8_000;

interface AuthScopedRequest<T> {
  tokenGeneration: number;
  promise: Promise<T>;
}

let currentUserRequest: AuthScopedRequest<CurrentUser> | null = null;
let currentNavigationRequest: AuthScopedRequest<CurrentNavigationSnapshot> | null = null;

export interface CurrentNavigationSnapshot {
  menuTree: MenuNode[];
  availablePlugins: PluginAvailability[];
}

const currentNavigationRequestOptions = {
  method: 'GET',
  autoRedirectOnUnauthorized: false,
  silent: true,
  timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
} as const;

const requestCurrentUser = () =>
  request<CurrentUser>('/v2/auth/current-user', {
    method: 'GET',
    autoRedirectOnUnauthorized: false,
    silent: true,
    timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
  }).catch((error) => {
    if (!shouldFallbackToLegacyEndpoint(error)) {
      throw error;
    }
    return request<CurrentUser>('/v1/auth/current-user', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      silent: true,
      timeoutMs: CURRENT_USER_SYNC_TIMEOUT_MS,
    });
  });

export const loadCurrentUserSnapshot = (): Promise<CurrentUser> => {
  const tokenGeneration = tokenManager.getTokenGeneration();
  if (currentUserRequest?.tokenGeneration === tokenGeneration) {
    return currentUserRequest.promise;
  }

  let attempt!: AuthScopedRequest<CurrentUser>;
  const promise = requestCurrentUser().finally(() => {
    if (currentUserRequest === attempt) {
      currentUserRequest = null;
    }
  });
  attempt = { tokenGeneration, promise };
  currentUserRequest = attempt;
  return attempt.promise;
};

const requestCurrentNavigation = () =>
  request<CurrentNavigationSnapshot>('/v2/plugins/current/bootstrap', currentNavigationRequestOptions)
    .catch((error) => {
      if (!shouldFallbackToLegacyEndpoint(error)) {
        throw error;
      }
      return request<CurrentNavigationSnapshot>('/v1/plugins/current/bootstrap', currentNavigationRequestOptions);
    });

export const loadCurrentNavigationSnapshot = (): Promise<CurrentNavigationSnapshot> => {
  const tokenGeneration = tokenManager.getTokenGeneration();
  if (currentNavigationRequest?.tokenGeneration === tokenGeneration) {
    return currentNavigationRequest.promise;
  }

  let attempt!: AuthScopedRequest<CurrentNavigationSnapshot>;
  const promise = requestCurrentNavigation().finally(() => {
    if (currentNavigationRequest === attempt) {
      currentNavigationRequest = null;
    }
  });
  attempt = { tokenGeneration, promise };
  currentNavigationRequest = attempt;
  return attempt.promise;
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
  // A role mutation may race with the regular polling request. Detach both
  // in-flight snapshots so this explicit signal always starts a request under
  // the latest token generation; the old promises remain safe to finish.
  currentUserRequest = null;
  currentNavigationRequest = null;
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(CURRENT_USER_SYNC_EVENT));
  }
};
