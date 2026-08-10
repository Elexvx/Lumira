import { history } from '@umijs/max';
import { LOGIN_PATH } from '@/app.constants';
import { request } from '@/services/common/request';
import { tokenManager } from '@/auth/token';
import { clearSessionActivity } from '@/auth/activity';
import { clearClientRuntimeState } from '@/auth/clientRuntimeState';
import { beginBootstrapFlow, endBootstrapFlow } from '@/auth/loginFlowState';
import { persistSessionMeta } from '@/auth/sessionState';
import type { RefreshTokenResponse } from '@/types/api';
import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { withAuthSessionMutationLock } from '@/auth/authSessionMutationLock';
import { beginRoleSwitchFlow } from '@/auth/roleSwitchFlowState';
import { shouldFallbackToLegacyEndpoint } from '@/services/common/legacyEndpointFallback';

export type LogoutReason = 'user_initiated' | 'forced_expired';
export type TokenRefreshOutcome = 'refreshed' | 'superseded' | 'session_expired' | 'temporarily_unavailable';

export const hasUsableTokenAfterRefresh = (outcome: TokenRefreshOutcome) =>
  outcome === 'refreshed' || (outcome === 'superseded' && tokenManager.hasToken());

const AUTH_LOGOUT_PATH = '/v1/auth/logout';
const AUTH_REFRESH_TOKEN_PATH = '/v1/auth/refresh-token';
export const SESSION_EXPIRED_LOGIN_REASON = 'session_expired';

interface RefreshTokenAttempt {
  tokenGeneration: number;
  promise: Promise<TokenRefreshOutcome>;
}

let refreshTokenAttempt: RefreshTokenAttempt | null = null;
const activeRefreshTokenPromises = new Set<Promise<TokenRefreshOutcome>>();
type RefreshBarrierOutcome = 'completed' | 'failed';
let roleSwitchRefreshBarrier: Promise<RefreshBarrierOutcome> | null = null;

export const isLoggedIn = () => tokenManager.hasToken();

export const clearAuthSession = () => {
  clearClientRuntimeState();
  tokenManager.clearTokenState();
  clearSessionActivity();
};

export const buildLogoutRedirectTarget = (
  reason: LogoutReason,
  location: Pick<Location, 'pathname' | 'search' | 'hash'> = typeof window === 'undefined'
    ? { pathname: LOGIN_PATH, search: '', hash: '' }
    : window.location,
) => {
  if (reason !== 'forced_expired' || location.pathname === LOGIN_PATH) {
    return LOGIN_PATH;
  }

  const redirect = `${location.pathname}${location.search}${location.hash}`;
  return `${LOGIN_PATH}?redirect=${encodeURIComponent(redirect)}&reason=${SESSION_EXPIRED_LOGIN_REASON}`;
};

export const isSessionExpiredLoginSearch = (search: string) =>
  new URLSearchParams(search).get('reason') === SESSION_EXPIRED_LOGIN_REASON;

export const performLogout = async (options: { reason?: LogoutReason; hardReload?: boolean; loginTarget?: string } = {}) => {
  const reason = options.reason || 'user_initiated';
  const loginTarget = options.loginTarget || buildLogoutRedirectTarget(reason);
  const redirectToLogin = () => {
    if (options.hardReload) {
      window.location.replace(loginTarget);
      return;
    }

    history.replace(loginTarget);
  };

  if (reason === 'user_initiated' && isLoggedIn()) {
    void revokeServerSession();
  }
  clearAuthSession();
  redirectToLogin();
};

const revokeServerSession = async () => {
  try {
    await request<boolean>('/v2/auth/logout', {
      method: 'POST',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      allowDuplicate: true,
      silent: true,
      timeoutMs: 3000,
    }).catch(() =>
      request<boolean>(AUTH_LOGOUT_PATH, {
        method: 'POST',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        allowDuplicate: true,
        silent: true,
        timeoutMs: 3000,
      }),
    );
  } catch {
    // 后端会话已失效或网络异常时，本地退出流程不被阻塞。
  }
};

const isConfirmedRefreshSessionExpiry = (error: unknown) =>
  error instanceof ApiRequestError &&
  error.httpStatus === 401 &&
  (error.code === ErrorCode.SESSION_EXPIRED || error.code === ErrorCode.UNAUTHORIZED);

const refreshTokenRequest = async (): Promise<TokenRefreshOutcome> => {
  const startingTokenGeneration = tokenManager.getTokenGeneration();
  const startingAccessToken = tokenManager.getAccessToken();
  const wasSuperseded = () => {
    // A storage event can be queued behind this fetch callback. Pull the
    // shared token first so another tab's newer auth generation wins before
    // this tab can persist an older refresh response.
    tokenManager.syncFromStorage(startingAccessToken);
    return tokenManager.getTokenGeneration() !== startingTokenGeneration;
  };
  let v2Error: unknown;
  try {
    const response = await request<RefreshTokenResponse>('/v2/auth/refresh-token', {
      method: 'POST',
      skipAuth: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      credentials: 'include',
      silent: true,
    });
    if (wasSuperseded()) {
      return 'superseded';
    }
    tokenManager.setTokens({
      accessToken: response.accessToken,
      tokenType: response.tokenType,
      expiresIn: response.expiresIn,
    });
    persistSessionMeta({
      sessionVersion: response.sessionVersion,
      permissionsVersion: response.permissionsVersion,
    });
    return 'refreshed';
  } catch (error) {
    v2Error = error;
    if (wasSuperseded()) {
      return 'superseded';
    }
    if (!shouldFallbackToLegacyEndpoint(error)) {
      return isConfirmedRefreshSessionExpiry(error)
        ? 'session_expired'
        : 'temporarily_unavailable';
    }
    try {
      const response = await request<RefreshTokenResponse>(AUTH_REFRESH_TOKEN_PATH, {
        method: 'POST',
        skipAuth: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        credentials: 'include',
        silent: true,
      });
      if (wasSuperseded()) {
        return 'superseded';
      }
      tokenManager.setTokens({
        accessToken: response.accessToken,
        tokenType: response.tokenType,
        expiresIn: response.expiresIn,
      });
      persistSessionMeta({
        sessionVersion: response.sessionVersion,
        permissionsVersion: response.permissionsVersion,
      });
      return 'refreshed';
    } catch (legacyError) {
      if (wasSuperseded()) {
        return 'superseded';
      }
      return isConfirmedRefreshSessionExpiry(v2Error) || isConfirmedRefreshSessionExpiry(legacyError)
        ? 'session_expired'
        : 'temporarily_unavailable';
    }
  }
};

export const tryRefreshTokenOutcome = async (): Promise<TokenRefreshOutcome> => {
  const requestedTokenGeneration = tokenManager.getTokenGeneration();
  const activeBarrier = roleSwitchRefreshBarrier;
  if (activeBarrier) {
    const barrierOutcome = await activeBarrier;
    if (
      barrierOutcome === 'completed' ||
      tokenManager.getTokenGeneration() !== requestedTokenGeneration
    ) {
      return 'superseded';
    }
    return tryRefreshTokenOutcome();
  }

  const tokenGeneration = tokenManager.getTokenGeneration();
  if (refreshTokenAttempt?.tokenGeneration === tokenGeneration) {
    return refreshTokenAttempt.promise;
  }

  const startingAccessToken = tokenManager.getAccessToken();
  const promise = refreshTokenOnce(tokenGeneration, startingAccessToken);
  const attempt: RefreshTokenAttempt = {
    tokenGeneration,
    promise,
  };
  refreshTokenAttempt = attempt;
  activeRefreshTokenPromises.add(promise);
  void promise.then(
    () => activeRefreshTokenPromises.delete(promise),
    () => activeRefreshTokenPromises.delete(promise),
  );
  try {
    return await attempt.promise;
  } finally {
    if (refreshTokenAttempt === attempt) {
      refreshTokenAttempt = null;
    }
  }
};

const runWithRoleSwitchRefreshBarrier = async <T>(
  action: () => Promise<T>,
): Promise<T> => {
  if (roleSwitchRefreshBarrier) {
    await roleSwitchRefreshBarrier;
    return runWithRoleSwitchRefreshBarrier(action);
  }

  let resolveBarrier!: (outcome: RefreshBarrierOutcome) => void;
  const barrier = new Promise<RefreshBarrierOutcome>((resolve) => {
    resolveBarrier = resolve;
  });
  roleSwitchRefreshBarrier = barrier;

  try {
    await Promise.allSettled(Array.from(activeRefreshTokenPromises));
    const result = await withAuthSessionMutationLock(async () => {
      const currentAccessToken = tokenManager.getAccessToken();
      tokenManager.syncFromStorage(currentAccessToken);
      return action();
    });
    resolveBarrier('completed');
    return result;
  } catch (error) {
    resolveBarrier('failed');
    throw error;
  } finally {
    if (roleSwitchRefreshBarrier === barrier) {
      roleSwitchRefreshBarrier = null;
    }
  }
};

export const withRoleSwitchRefreshBarrier = async <T>(
  action: () => Promise<T>,
): Promise<T> => {
  const finishRoleSwitchFlow = beginRoleSwitchFlow();
  try {
    return await runWithRoleSwitchRefreshBarrier(action);
  } finally {
    finishRoleSwitchFlow();
  }
};

export const tryRefreshToken = async (): Promise<boolean> =>
  hasUsableTokenAfterRefresh(await tryRefreshTokenOutcome());

const refreshTokenOnce = async (
  startingTokenGeneration: number,
  startingAccessToken: string,
): Promise<TokenRefreshOutcome> => {
  return withAuthSessionMutationLock(async () => {
    // A cross-tab role switch or refresh can complete while this attempt waits
    // for the origin-wide lock. Reconcile by token identity before sending a
    // cookie-rotating request; the same token merely expiring is not a change.
    tokenManager.syncFromStorage(startingAccessToken);
    if (tokenManager.getTokenGeneration() !== startingTokenGeneration) {
      return 'superseded';
    }
    return refreshTokenRequest();
  });
};

export const withBootstrapFlow = async <T>(action: () => Promise<T>) => {
  beginBootstrapFlow();
  try {
    return await action();
  } finally {
    endBootstrapFlow();
  }
};
