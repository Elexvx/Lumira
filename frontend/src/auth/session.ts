import { authService } from '@/services/auth';
import { systemService } from '@/services/system';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { syncTenantFromServer } from '@/tenant/actions';
import { storage } from '@/cache/storage';
import { clearSessionActivity, persistSessionActivity } from '@/auth/activity';
import {
  DEFAULT_SECURITY_SETTINGS,
  getStoredSecuritySettings,
  normalizeSecuritySettings,
  persistSecuritySettings,
} from '@/auth/securitySettings';
import { beginBootstrapFlow, endBootstrapFlow } from '@/auth/loginFlowState';
import type { CurrentUser, LoginResponse, SecuritySettings } from '@/types/api';
import { history } from '@umijs/max';

const USER_PROFILE_KEY = 'current_user_profile';
const SESSION_META_KEY = 'current_session_meta';

export interface SessionMetaState {
  sessionId?: string;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export interface SessionBootstrapResult {
  currentUser: CurrentUser;
  securitySettings: SecuritySettings;
}

export type LogoutReason = 'user_initiated' | 'forced_expired';

export const isLoggedIn = () => tokenManager.hasToken();

export const getStoredCurrentUser = (): CurrentUser | null => storage.get<CurrentUser>(USER_PROFILE_KEY);

export const getStoredSessionMeta = (): SessionMetaState | null => storage.get<SessionMetaState>(SESSION_META_KEY);

export const clearAuthSession = () => {
  tokenManager.clearTokenState();
  storage.remove(USER_PROFILE_KEY);
  storage.remove(SESSION_META_KEY);
  clearSessionActivity();
  tenantContext.clearTenantContext();
};

export const performLogout = async (options: { reason?: LogoutReason } = {}) => {
  const reason = options.reason || 'user_initiated';

  if (reason === 'user_initiated' && isLoggedIn()) {
    const tokenState = tokenManager.getTokenState();
    const accessTokenExpired = Boolean(tokenState && tokenState.expiresAt <= Date.now());

    if (accessTokenExpired) {
      const refreshed = await tryRefreshToken();
      if (!refreshed) {
        clearAuthSession();
        history.replace('/user/login');
        return;
      }
    }

    try {
      await authService.logout({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
    } catch {
      // 后端会话已失效时继续做本地清理，不阻塞退出流程
    }
  }
  clearAuthSession();
  history.replace('/user/login');
};

export const initializeAfterLogin = async (loginResponse: LoginResponse): Promise<SessionBootstrapResult> => {
  beginBootstrapFlow();
  try {
    tokenManager.setTokens({
      accessToken: loginResponse.accessToken,
      refreshToken: loginResponse.refreshToken,
      tokenType: loginResponse.tokenType,
      expiresIn: loginResponse.expiresIn,
    });

    tenantContext.setMyTenants(loginResponse.tenants || []);
    tenantContext.setCurrentTenant(loginResponse.currentTenant || null);
    persistSessionActivity(Date.now());

    // Use autoRedirectOnUnauthorized: false to prevent the global 401 handler
    // from clearing the just-written token and redirecting away during login.
    // Any failure here is caught below and re-thrown so the login page can
    // display an appropriate error message.
    const currentUser = await authService.currentUser({
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
    });
    persistCurrentUser(currentUser);
    await syncTenantFromServer({
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
    });
    persistSessionActivity(Date.now());
    const securitySettings = await loadSecuritySettings();
    return { currentUser, securitySettings };
  } catch (error) {
    clearAuthSession();
    throw error;
  } finally {
    endBootstrapFlow();
  }
};

export const restoreSession = async (): Promise<SessionBootstrapResult | null> => {
  beginBootstrapFlow();
  try {
    if (!isLoggedIn()) {
      return null;
    }

    try {
      const currentUser = await authService.currentUser({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      persistCurrentUser(currentUser);
      await syncTenantFromServer({
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      const securitySettings = await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true });
      return { currentUser, securitySettings };
    } catch {
      const refreshed = await tryRefreshToken();
      if (!refreshed) {
        clearAuthSession();
        return null;
      }

      try {
        const currentUser = await authService.currentUser({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        });
        persistCurrentUser(currentUser);
        await syncTenantFromServer({
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        });
        persistSessionActivity(Date.now());
        const securitySettings = await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true });
        return { currentUser, securitySettings };
      } catch {
        clearAuthSession();
        return null;
      }
    }
  } finally {
    endBootstrapFlow();
  }
};

export const tryRefreshToken = async (): Promise<boolean> => {
  const refreshToken = tokenManager.getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  try {
    const response = await authService.refreshToken(
      { refreshToken },
      {
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      },
    );
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
};

export const loadSecuritySettings = async (
  options: {
    allowUnauthorizedWithoutRedirect?: boolean;
  } = {},
): Promise<SecuritySettings> => {
  try {
    const requestOptions = options.allowUnauthorizedWithoutRedirect
      ? {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        }
      : {};
    const securitySettings = normalizeSecuritySettings(
      await systemService.securitySettings(requestOptions),
    );
    persistSecuritySettings(securitySettings);
    return securitySettings;
  } catch {
    return normalizeSecuritySettings(getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS);
  }
};

export const saveSecuritySettings = async (securitySettings: SecuritySettings): Promise<SecuritySettings> => {
  const response = await systemService.updateSecuritySettings(securitySettings);
  const normalized = normalizeSecuritySettings(response);
  persistSecuritySettings(normalized);
  return normalized;
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
