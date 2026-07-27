import { request } from '@/services/common/request';
import type { CurrentUser } from '@/types/api';

export const CURRENT_USER_SYNC_EVENT = 'lumira:current-user-sync';
export const CURRENT_USER_SYNC_INTERVAL_MS = 5_000;

const CURRENT_USER_SYNC_TIMEOUT_MS = 8_000;
let currentUserRequest: Promise<CurrentUser> | null = null;

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

export const hasCurrentUserSnapshotChanged = (
  previous: CurrentUser | undefined,
  next: CurrentUser,
) => JSON.stringify(previous ?? null) !== JSON.stringify(next);

export const notifyCurrentUserSync = () => {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(CURRENT_USER_SYNC_EVENT));
  }
};
