import { useEffect, useMemo, useRef, type ReactNode } from 'react';
import { message } from 'antd';
import { history, useLocation } from 'umi';
import { clearSessionActivity, getSessionActivityStorageKey, getStoredSessionActivityAt, persistSessionActivity } from '@/auth/activity';
import { clearAuthSession } from '@/auth/session';
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
      message.warning('登录状态已过期，请重新登录');
    }
    clearSessionActivity();
    clearAuthSession();
    history.replace('/user/login');
    window.setTimeout(() => {
      redirectingRef.current = false;
    }, 0);
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

      if (event.key === STORAGE_TOKEN_KEY && !tokenManager.hasToken()) {
        forceLogout();
        return;
      }
      if (event.key === STORAGE_TOKEN_KEY) {
        scheduleTokenExpiration();
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
