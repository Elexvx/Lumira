import { useEffect, useMemo, useRef, type ReactNode } from 'react';
import { message } from 'antd';
import { history, useLocation } from '@umijs/max';
import { clearSessionActivity, getSessionActivityStorageKey, getStoredSessionActivityAt, persistSessionActivity } from '@/auth/activity';
import { performLogout } from '@/auth/session';
import {
  DEFAULT_SECURITY_SETTINGS,
  getStoredSecuritySettings,
  normalizeSecuritySettings,
} from '@/auth/securitySettings';
import { buildStorageKey } from '@/cache/storage';
import { TOKEN_STORAGE_KEY, tokenManager } from '@/auth/token';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

const STORAGE_ACTIVITY_KEY = getSessionActivityStorageKey();
const STORAGE_TOKEN_KEY = buildStorageKey(TOKEN_STORAGE_KEY);
const MOUSE_MOVE_THROTTLE_MS = 1000;

export const SessionActivityGuard = ({ children }: { children: ReactNode }) => {
  const { initialState } = useInitialStateModel();
  const location = useLocation();
  const timerRef = useRef<number | null>(null);
  const tokenExpireTimerRef = useRef<number | null>(null);
  const redirectingRef = useRef(false);
  const lastBroadcastRef = useRef(0);
  const lastActivityRef = useRef<number>(Date.now());

  const securitySettings = useMemo(
    () => normalizeSecuritySettings(initialState?.securitySettings || getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS),
    [initialState?.securitySettings],
  );

  const clearTimer = () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const clearTokenExpireTimer = () => {
    if (tokenExpireTimerRef.current !== null) {
      window.clearTimeout(tokenExpireTimerRef.current);
      tokenExpireTimerRef.current = null;
    }
  };

  const forceLogout = (reason?: 'token_expired') => {
    if (redirectingRef.current) {
      return;
    }
    redirectingRef.current = true;
    clearTimer();
    clearTokenExpireTimer();
    if (reason === 'token_expired') {
      message.info('登录状态已过期，请重新登录');
    }
    clearSessionActivity();
    void performLogout();
    // redirectingRef is intentionally NOT reset here.
    // It will be reset once a new valid token is detected (i.e. after a successful
    // re-login), which happens in the useEffect that watches for token changes.
  };

  const scheduleTimeout = (lastActivityAt: number) => {
    clearTimer();
    if (!tokenManager.hasToken()) {
      return;
    }

    const timeoutMs = securitySettings.idleTimeoutSeconds * 1000;
    const elapsedMs = Date.now() - lastActivityAt;
    const remainingMs = Math.max(timeoutMs - elapsedMs, 0);
    timerRef.current = window.setTimeout(() => {
      if (tokenManager.hasToken()) {
        forceLogout();
      }
    }, remainingMs);
  };

  const scheduleTokenExpiration = () => {
    clearTokenExpireTimer();
    const tokenState = tokenManager.getTokenState();
    if (!tokenState) {
      return;
    }

    const remainingMs = tokenState.expiresAt - Date.now();
    if (remainingMs <= 0) {
      forceLogout('token_expired');
      return;
    }

    tokenExpireTimerRef.current = window.setTimeout(() => {
      const latestTokenState = tokenManager.getTokenState();
      if (!latestTokenState) {
        return;
      }
      if (latestTokenState.expiresAt <= Date.now()) {
        forceLogout('token_expired');
        return;
      }
      scheduleTokenExpiration();
    }, remainingMs);
  };

  const markActivity = (source: string) => {
    if (!tokenManager.hasToken()) {
      return;
    }

    const now = Date.now();
    if (source === 'mousemove' && now - lastBroadcastRef.current < MOUSE_MOVE_THROTTLE_MS) {
      return;
    }

    lastBroadcastRef.current = now;
    lastActivityRef.current = now;
    persistSessionActivity(now);
    scheduleTimeout(now);
  };

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      clearTimer();
      clearTokenExpireTimer();
      return;
    }

    // A valid token exists – the user is (or has just become) authenticated.
    // Reset the redirecting guard so that future idle/token-expiry logouts can
    // fire correctly. This handles the same-tab re-login case where the storage
    // event listener doesn't receive its own tab's writes.
    redirectingRef.current = false;

    scheduleTokenExpiration();

    const storedActivityAt = getStoredSessionActivityAt() || Date.now();
    if (!getStoredSessionActivityAt()) {
      persistSessionActivity(storedActivityAt);
    }
    lastActivityRef.current = storedActivityAt;
    scheduleTimeout(storedActivityAt);

    const activityEvents: Array<keyof WindowEventMap> = [
      'click',
      'keydown',
      'mousedown',
      'mousemove',
      'pointerdown',
      'scroll',
      'touchstart',
    ];
    const handleUserActivity = (event: Event) => {
      markActivity(event.type);
    };
    activityEvents.forEach((eventName) => {
      window.addEventListener(eventName, handleUserActivity, { passive: true });
    });

    const handleStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_ACTIVITY_KEY && event.newValue) {
        const parsed = Number(event.newValue);
        if (Number.isFinite(parsed) && parsed > 0) {
          lastActivityRef.current = parsed;
          scheduleTimeout(parsed);
        }
      }

      if (event.key === STORAGE_TOKEN_KEY) {
        if (tokenManager.hasToken()) {
          // A new token was stored (e.g. after re-login or token refresh in another tab).
          // Reset the redirecting guard so the session becomes active again.
          redirectingRef.current = false;
          scheduleTokenExpiration();
          scheduleTimeout(lastActivityRef.current);
        } else if (!redirectingRef.current) {
          // Token was removed by another tab while we are not already logging out.
          forceLogout();
        }
      }
    };
    window.addEventListener('storage', handleStorage);

    const handleVisibilityChange = () => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      const elapsedMs = Date.now() - lastActivityRef.current;
      if (elapsedMs >= securitySettings.idleTimeoutSeconds * 1000) {
        forceLogout();
        return;
      }
      scheduleTimeout(lastActivityRef.current);
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    const handleFocus = () => {
      const elapsedMs = Date.now() - lastActivityRef.current;
      if (elapsedMs >= securitySettings.idleTimeoutSeconds * 1000) {
        forceLogout();
        return;
      }
      scheduleTimeout(lastActivityRef.current);
    };
    window.addEventListener('focus', handleFocus);

    return () => {
      clearTimer();
      clearTokenExpireTimer();
      activityEvents.forEach((eventName) => {
        window.removeEventListener(eventName, handleUserActivity);
      });
      window.removeEventListener('storage', handleStorage);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, [securitySettings.idleTimeoutSeconds, initialState?.currentUser?.sessionId]);

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      clearTimer();
      clearTokenExpireTimer();
      return;
    }
    markActivity('route');
    // Route changes count as activity, so we keep the idle timer in sync.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      clearTimer();
      clearTokenExpireTimer();
      return;
    }

    scheduleTokenExpiration();

    const currentStoredActivity = getStoredSessionActivityAt() || lastActivityRef.current;
    lastActivityRef.current = currentStoredActivity;
    scheduleTimeout(currentStoredActivity);
    // Recompute when the policy changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [securitySettings.idleTimeoutSeconds, securitySettings.accessTokenExpireSeconds, securitySettings.refreshTokenExpireSeconds]);

  return children;
};
