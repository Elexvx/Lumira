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

export type LogoutReason = 'user_initiated' | 'forced_expired';
export type TokenRefreshOutcome = 'refreshed' | 'session_expired' | 'temporarily_unavailable';

const AUTH_LOGOUT_PATH = '/v1/auth/logout';
const AUTH_REFRESH_TOKEN_PATH = '/v1/auth/refresh-token';
export const SESSION_EXPIRED_LOGIN_REASON = 'session_expired';

let refreshTokenPromise: Promise<TokenRefreshOutcome> | null = null;

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
  let v2Error: unknown;
  try {
    const response = await request<RefreshTokenResponse>('/v2/auth/refresh-token', {
      method: 'POST',
      skipAuth: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      credentials: 'include',
    });
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
    try {
      const response = await request<RefreshTokenResponse>(AUTH_REFRESH_TOKEN_PATH, {
        method: 'POST',
        skipAuth: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        credentials: 'include',
      });
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
      return isConfirmedRefreshSessionExpiry(v2Error) || isConfirmedRefreshSessionExpiry(legacyError)
        ? 'session_expired'
        : 'temporarily_unavailable';
    }
  }
};

export const tryRefreshTokenOutcome = async (): Promise<TokenRefreshOutcome> => {
  if (refreshTokenPromise) {
    return refreshTokenPromise;
  }

  refreshTokenPromise = refreshTokenOnce();
  try {
    return await refreshTokenPromise;
  } finally {
    refreshTokenPromise = null;
  }
};

export const tryRefreshToken = async (): Promise<boolean> => (await tryRefreshTokenOutcome()) === 'refreshed';

const refreshTokenOnce = async (): Promise<TokenRefreshOutcome> => {
  return refreshTokenRequest();
};

export const withBootstrapFlow = async <T>(action: () => Promise<T>) => {
  beginBootstrapFlow();
  try {
    return await action();
  } finally {
    endBootstrapFlow();
  }
};
