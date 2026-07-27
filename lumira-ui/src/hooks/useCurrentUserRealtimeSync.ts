import { useCallback, useEffect, useRef, type Dispatch, type SetStateAction } from 'react';
import type { AppInitialState } from '@/app';
import {
  CURRENT_USER_SYNC_EVENT,
  CURRENT_USER_SYNC_INTERVAL_MS,
  hasCurrentUserSnapshotChanged,
  loadCurrentUserSnapshot,
} from '@/auth/currentUserSync';
import { persistCurrentUser } from '@/auth/sessionState';
import { tokenManager } from '@/auth/token';
import type { CurrentUser } from '@/types/api';

interface CurrentUserRealtimeSyncOptions {
  currentUser?: CurrentUser;
  setInitialState: Dispatch<SetStateAction<AppInitialState | undefined>>;
}

export const useCurrentUserRealtimeSync = ({
  currentUser,
  setInitialState,
}: CurrentUserRealtimeSyncOptions) => {
  const currentUserRef = useRef(currentUser);

  useEffect(() => {
    currentUserRef.current = currentUser;
  }, [currentUser]);

  const refreshCurrentUser = useCallback(async () => {
    const expectedUser = currentUserRef.current;
    if (!expectedUser || !tokenManager.hasToken()) {
      return;
    }

    try {
      const refreshedUser = await loadCurrentUserSnapshot();
      if (
        refreshedUser.userId !== expectedUser.userId ||
        refreshedUser.sessionId !== expectedUser.sessionId
      ) {
        return;
      }

      setInitialState((previousState) => {
        const previousUser = previousState?.currentUser;
        if (
          !previousState ||
          !previousUser ||
          previousUser.userId !== refreshedUser.userId ||
          previousUser.sessionId !== refreshedUser.sessionId ||
          !hasCurrentUserSnapshotChanged(previousUser, refreshedUser)
        ) {
          return previousState;
        }

        return {
          ...previousState,
          currentUser: persistCurrentUser(refreshedUser),
        };
      });
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
