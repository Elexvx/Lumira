import { useCallback, useEffect, useRef, type Dispatch, type SetStateAction } from 'react';
import type { AppInitialState } from '@/app';
import {
  CURRENT_USER_SYNC_EVENT,
  CURRENT_USER_SYNC_INTERVAL_MS,
  hasCurrentUserNavigationChanged,
  hasCurrentUserSnapshotChanged,
  loadCurrentNavigationSnapshot,
  loadCurrentUserSnapshot,
  type CurrentNavigationSnapshot,
} from '@/auth/currentUserSync';
import { normalizeAuthenticatedMenuTree } from '@/auth/authenticatedMenuTree';
import { persistCurrentUser } from '@/auth/sessionState';
import { tokenManager } from '@/auth/token';
import type { CurrentUser } from '@/types/api';

interface CurrentUserRealtimeSyncOptions {
  currentUser?: CurrentUser;
  setInitialState: Dispatch<SetStateAction<AppInitialState | undefined>>;
}

export const mergeCurrentUserRuntimeState = (
  previousState: AppInitialState | undefined,
  refreshedUser: CurrentUser,
  refreshedNavigation?: CurrentNavigationSnapshot,
): AppInitialState | undefined => {
  const previousUser = previousState?.currentUser;
  if (
    !previousState
    || !previousUser
    || previousUser.userId !== refreshedUser.userId
    || previousUser.sessionId !== refreshedUser.sessionId
    || !hasCurrentUserSnapshotChanged(previousUser, refreshedUser)
  ) {
    return previousState;
  }

  return {
    ...previousState,
    currentUser: persistCurrentUser(refreshedUser),
    ...(refreshedNavigation
      ? {
          menuTree: normalizeAuthenticatedMenuTree(refreshedNavigation.menuTree, refreshedUser),
          availablePlugins: refreshedNavigation.availablePlugins,
          menuVersion: (previousState.menuVersion ?? 0) + 1,
        }
      : {}),
  };
};

export const useCurrentUserRealtimeSync = ({
  currentUser,
  setInitialState,
}: CurrentUserRealtimeSyncOptions) => {
  const currentUserRef = useRef(currentUser);
  const refreshAttemptRef = useRef(0);

  useEffect(() => {
    currentUserRef.current = currentUser;
  }, [currentUser]);

  const refreshCurrentUser = useCallback(async () => {
    const attemptId = ++refreshAttemptRef.current;
    const expectedUser = currentUserRef.current;
    if (!expectedUser || !tokenManager.hasToken()) {
      return;
    }

    try {
      const refreshedUser = await loadCurrentUserSnapshot();
      if (
        attemptId !== refreshAttemptRef.current ||
        refreshedUser.userId !== expectedUser.userId ||
        refreshedUser.sessionId !== expectedUser.sessionId
      ) {
        return;
      }

      const navigationChanged = hasCurrentUserNavigationChanged(expectedUser, refreshedUser);
      const refreshedNavigation = navigationChanged
        ? await loadCurrentNavigationSnapshot()
        : undefined;
      if (attemptId !== refreshAttemptRef.current) {
        return;
      }

      setInitialState((previousState) => (
        attemptId === refreshAttemptRef.current
          ? mergeCurrentUserRuntimeState(previousState, refreshedUser, refreshedNavigation)
          : previousState
      ));
    } catch {
      // Background synchronization is best effort. Normal requests retain the
      // existing session-expiry and user-facing error behavior.
    }
  }, [setInitialState]);

  useEffect(() => {
    if (!currentUser?.sessionId || typeof window === 'undefined') {
      return undefined;
    }

    const refreshWhenVisible = () => {
      if (typeof document === 'undefined' || document.visibilityState === 'visible') {
        void refreshCurrentUser();
      }
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void refreshCurrentUser();
      }
    };

    const interval = window.setInterval(refreshWhenVisible, CURRENT_USER_SYNC_INTERVAL_MS);
    window.addEventListener('focus', refreshWhenVisible);
    window.addEventListener(CURRENT_USER_SYNC_EVENT, refreshWhenVisible);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', refreshWhenVisible);
      window.removeEventListener(CURRENT_USER_SYNC_EVENT, refreshWhenVisible);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [currentUser?.sessionId, refreshCurrentUser]);
};
