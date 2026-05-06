import { authService } from '@/services/auth';
import { systemService } from '@/services/system';
import { tokenManager } from '@/auth/token';
import { LOGIN_PATH } from '@/app.constants';
import { storage } from '@/cache/storage';
import { clearSessionActivity, persistSessionActivity } from '@/auth/activity';
import {
  DEFAULT_SECURITY_SETTINGS,
  getStoredSecuritySettings,
  normalizeSecuritySettings,
  persistSecuritySettings,
} from '@/auth/securitySettings';
import { beginBootstrapFlow, endBootstrapFlow } from '@/auth/loginFlowState';
import { applyLocalePreference } from '@/i18n/locale';
import type { CurrentUser, LoginResponse, SecuritySettings } from '@/types/api';
import { history } from '@umijs/max';

const USER_PROFILE_KEY = 'current_user_profile';
const SESSION_META_KEY = 'current_session_meta';
const LOCAL_SESSION_ID_PREFIX = 'local-session';

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
    const tokenState = tokenManager.getTokenState();
    const accessTokenExpired = Boolean(tokenState && tokenState.expiresAt <= Date.now());

    if (accessTokenExpired) {
      const refreshed = await tryRefreshToken();
      if (!refreshed) {
        clearAuthSession();
        redirectToLogin();
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
  redirectToLogin();
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
    persistSessionActivity(Date.now());

    const currentUser = await loadCurrentUserOrFallback(loginResponse);
    applyLocalePreference(currentUser?.locale || loginResponse.user.locale, false);
    const persistedCurrentUser = persistCurrentUser(currentUser);
    const securitySettings = await loadSecuritySettings();
    return { currentUser: persistedCurrentUser, securitySettings };
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

    const currentUser = await loadCurrentUserOrFallback();
    if (!currentUser) {
      const refreshed = await tryRefreshToken();
      if (!refreshed) {
        clearAuthSession();
        return null;
      }

      const refreshedCurrentUser = await loadCurrentUserOrFallback();
      if (!refreshedCurrentUser) {
        clearAuthSession();
        return null;
      }

      applyLocalePreference(refreshedCurrentUser.locale, false);
      const persistedCurrentUser = persistCurrentUser(refreshedCurrentUser);
      persistSessionActivity(Date.now());
      const securitySettings = await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true });
      return { currentUser: persistedCurrentUser, securitySettings };
    }

    applyLocalePreference(currentUser.locale, false);
    const persistedCurrentUser = persistCurrentUser(currentUser);
    persistSessionActivity(Date.now());
    const securitySettings = await loadSecuritySettings({ allowUnauthorizedWithoutRedirect: true });
    return { currentUser: persistedCurrentUser, securitySettings };
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

const persistCurrentUser = (currentUser: CurrentUser): CurrentUser => {
  const normalizedCurrentUser = normalizeCurrentUserSession(currentUser);
  storage.set(USER_PROFILE_KEY, normalizedCurrentUser);
  persistSessionMeta({
    sessionId: normalizedCurrentUser.sessionId,
    sessionVersion: normalizedCurrentUser.sessionVersion,
    permissionsVersion: normalizedCurrentUser.permissionsVersion,
  });
  return normalizedCurrentUser;
};

function loadCurrentUserOrFallback(loginResponse: LoginResponse): Promise<CurrentUser>;
function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null>;
async function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null> {
  try {
    const currentUser = await authService.currentUser({
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
    });
    return currentUser;
  } catch {
    return loginResponse ? buildFallbackCurrentUser(loginResponse) : null;
  }
}

const buildFallbackCurrentUser = (loginResponse: LoginResponse): CurrentUser => {
  const storedCurrentUser = getStoredCurrentUser();
  const storedSessionMeta = getStoredSessionMeta();
  const sessionId = storedSessionMeta?.sessionId?.trim() || createLocalSessionId();
  return {
    userId: loginResponse.user.userId,
    username: loginResponse.user.username,
    nickname: loginResponse.user.nickname,
    realName: loginResponse.user.realName,
    avatarUrl: loginResponse.user.avatarUrl,
    mobile: loginResponse.user.mobile ?? null,
    email: loginResponse.user.email ?? null,
    birthMonth: loginResponse.user.birthMonth ?? null,
    gender: loginResponse.user.gender ?? null,
    region: loginResponse.user.region ?? null,
    availableTime: loginResponse.user.availableTime ?? null,
    idCardNumber: loginResponse.user.idCardNumber ?? null,
    locale: loginResponse.user.locale ?? null,
    currentTenant: loginResponse.currentTenant || null,
    sessionId,
    permissionsVersion: storedSessionMeta?.permissionsVersion,
    sessionVersion: storedSessionMeta?.sessionVersion,
    permissions: storedCurrentUser?.permissions || [],
  };
};

const normalizeCurrentUserSession = (currentUser: CurrentUser): CurrentUser => {
  if (currentUser.sessionId?.trim()) {
    return currentUser;
  }

  const storedSessionMeta = getStoredSessionMeta();
  return {
    ...currentUser,
    sessionId: storedSessionMeta?.sessionId?.trim() || createLocalSessionId(),
  };
};

const createLocalSessionId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${LOCAL_SESSION_ID_PREFIX}-${crypto.randomUUID()}`;
  }

  return `${LOCAL_SESSION_ID_PREFIX}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const persistSessionMeta = (meta: SessionMetaState) => {
  storage.set(SESSION_META_KEY, {
    ...getStoredSessionMeta(),
    ...meta,
  });
};
