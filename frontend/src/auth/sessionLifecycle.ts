import { history } from '@umijs/max';
import { LOGIN_PATH } from '@/app.constants';
import { request } from '@/services/common/request';
import { tokenManager } from '@/auth/token';
import { clearSessionActivity } from '@/auth/activity';
import { clearClientRuntimeState } from '@/auth/clientRuntimeState';
import { beginBootstrapFlow, endBootstrapFlow } from '@/auth/loginFlowState';
import { persistSessionMeta } from '@/auth/sessionState';
import type { RefreshTokenResponse } from '@/types/api';

export type LogoutReason = 'user_initiated' | 'forced_expired';

const AUTH_LOGOUT_PATH = '/v1/auth/logout';
const AUTH_REFRESH_TOKEN_PATH = '/v1/auth/refresh-token';

let refreshTokenPromise: Promise<boolean> | null = null;

export const isLoggedIn = () => tokenManager.hasToken();

export const clearAuthSession = () => {
  clearClientRuntimeState();
  tokenManager.clearTokenState();
  clearSessionActivity();
};

export const performLogout = async (options: { reason?: LogoutReason; hardReload?: boolean } = {}) => {
  const reason = options.reason || 'user_initiated';
  const redirectToLogin = () => {
    if (options.hardReload) {
      window.location.replace(LOGIN_PATH);
      return;
    }

    history.replace(LOGIN_PATH);
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

const refreshTokenRequest = async () => {
  const refreshToken = tokenManager.getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  const requestBody = { refreshToken };
  try {
    const response = await request<RefreshTokenResponse>('/v2/auth/refresh-token', {
      method: 'POST',
      data: requestBody,
      skipAuth: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
    });
    tokenManager.setTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      tokenType: response.tokenType,
      expiresIn: response.expiresIn,
    });
    persistSessionMeta({
      sessionVersion: response.sessionVersion,
      permissionsVersion: response.permissionsVersion,
    });
    return true;
  } catch {
    try {
      const response = await request<RefreshTokenResponse>(AUTH_REFRESH_TOKEN_PATH, {
        method: 'POST',
        data: requestBody,
        skipAuth: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      tokenManager.setTokens({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        tokenType: response.tokenType,
        expiresIn: response.expiresIn,
      });
      persistSessionMeta({
        sessionVersion: response.sessionVersion,
        permissionsVersion: response.permissionsVersion,
      });
      return true;
    } catch {
      return false;
    }
  }
};


export const tryRefreshToken = async (): Promise<boolean> => {
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

const refreshTokenOnce = async (): Promise<boolean> => {
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
