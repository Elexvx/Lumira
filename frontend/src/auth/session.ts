import { history } from '@umijs/max';
import { authService } from '@/services/auth';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { syncTenantFromServer } from '@/tenant/actions';
import { storage } from '@/cache/storage';
import type { CurrentUser, LoginResponse } from '@/types/api';

const USER_PROFILE_KEY = 'current_user_profile';
const SESSION_META_KEY = 'current_session_meta';

export interface SessionMetaState {
  sessionId?: string;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export interface SessionBootstrapResult {
  currentUser: CurrentUser;
}

export const isLoggedIn = () => tokenManager.hasToken();

export const getStoredCurrentUser = (): CurrentUser | null => storage.get<CurrentUser>(USER_PROFILE_KEY);

export const getStoredSessionMeta = (): SessionMetaState | null => storage.get<SessionMetaState>(SESSION_META_KEY);

export const clearAuthSession = () => {
  tokenManager.clearTokenState();
  storage.remove(USER_PROFILE_KEY);
  storage.remove(SESSION_META_KEY);
  tenantContext.clearTenantContext();
};

export const performLogout = async () => {
  if (isLoggedIn()) {
    try {
      await authService.logout({ autoRedirectOnUnauthorized: false });
    } catch (error) {
      // 后端会话已失效时继续做本地清理，不阻塞退出流程
    }
  }
  clearAuthSession();
  history.replace('/user/login');
};

export const initializeAfterLogin = async (loginResponse: LoginResponse): Promise<SessionBootstrapResult> => {
  tokenManager.setTokens({
    accessToken: loginResponse.accessToken,
    refreshToken: loginResponse.refreshToken,
    tokenType: loginResponse.tokenType,
    expiresIn: loginResponse.expiresIn,
  });

  tenantContext.setMyTenants(loginResponse.tenants || []);
  tenantContext.setCurrentTenant(loginResponse.currentTenant || null);

  const currentUser = await authService.currentUser();
  persistCurrentUser(currentUser);

  await syncTenantFromServer();
  return { currentUser };
};

export const restoreSession = async (): Promise<SessionBootstrapResult | null> => {
  if (!isLoggedIn()) {
    return null;
  }

  try {
    const currentUser = await authService.currentUser({ autoRedirectOnUnauthorized: false });
    persistCurrentUser(currentUser);
    await syncTenantFromServer();
    return { currentUser };
  } catch (error) {
    const refreshed = await tryRefreshToken();
    if (!refreshed) {
      clearAuthSession();
      return null;
    }

    const currentUser = await authService.currentUser({ autoRedirectOnUnauthorized: false });
    persistCurrentUser(currentUser);
    await syncTenantFromServer();
    return { currentUser };
  }
};

export const tryRefreshToken = async (): Promise<boolean> => {
  const refreshToken = tokenManager.getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  try {
    const response = await authService.refreshToken({ refreshToken }, { autoRedirectOnUnauthorized: false });
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
  } catch (error) {
    return false;
  }
};

const persistCurrentUser = (currentUser: CurrentUser) => {
  storage.set(USER_PROFILE_KEY, currentUser);
  persistSessionMeta({
    sessionId: currentUser.sessionId,
    sessionVersion: currentUser.sessionVersion,
    permissionsVersion: currentUser.permissionsVersion,
  });
};

const persistSessionMeta = (meta: SessionMetaState) => {
  storage.set(SESSION_META_KEY, {
    ...getStoredSessionMeta(),
    ...meta,
  });
};
