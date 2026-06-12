import { applyLocalePreference } from '@/i18n/locale';
import type { CurrentUser, LoginResponse, SecuritySettings } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { persistSessionActivity } from '@/auth/activity';
import { persistCurrentUser, buildFallbackCurrentUser, persistSessionMeta } from '@/auth/sessionState';
import { loadSecuritySettings } from '@/auth/sessionSecurity';
import { clearAuthSession, tryRefreshToken, withBootstrapFlow } from '@/auth/sessionLifecycle';
import { request } from '@/services/common/request';

export interface SessionBootstrapResult {
  currentUser: CurrentUser;
  securitySettings: SecuritySettings;
}

const LOGIN_CURRENT_USER_TIMEOUT_MS = 2500;
const RESTORE_CURRENT_USER_TIMEOUT_MS = 5000;
const POST_LOGIN_SECURITY_TIMEOUT_MS = 2500;
const RESTORE_SECURITY_TIMEOUT_MS = 5000;

function loadCurrentUserOrFallback(loginResponse: LoginResponse): Promise<CurrentUser>;
function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null>;
async function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null> {
  try {
    const currentUser = await request<CurrentUser>('/v1/auth/current-user', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: loginResponse ? LOGIN_CURRENT_USER_TIMEOUT_MS : RESTORE_CURRENT_USER_TIMEOUT_MS,
    });
    return currentUser;
  } catch {
    return loginResponse ? buildFallbackCurrentUser(loginResponse) : null;
  }
}

export const initializeAfterLogin = async (loginResponse: LoginResponse): Promise<SessionBootstrapResult> =>
  withBootstrapFlow(async () => {
    const currentUser = await initializeLoginSession(loginResponse);
    return currentUser;
  });

export const restoreSession = async (): Promise<SessionBootstrapResult | null> =>
  withBootstrapFlow(async () => {
    if (!tokenManager.hasToken()) {
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
      persistSessionMeta({
        sessionVersion: undefined,
        permissionsVersion: undefined,
      });
      const securitySettings = await loadSecuritySettings({
        allowUnauthorizedWithoutRedirect: true,
        timeoutMs: RESTORE_SECURITY_TIMEOUT_MS,
      });
      return { currentUser: persistedCurrentUser, securitySettings };
    }

    applyLocalePreference(currentUser.locale, false);
    const persistedCurrentUser = persistCurrentUser(currentUser);
    const securitySettings = await loadSecuritySettings({
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: RESTORE_SECURITY_TIMEOUT_MS,
    });
    return { currentUser: persistedCurrentUser, securitySettings };
  });

const initializeLoginSession = async (loginResponse: LoginResponse): Promise<SessionBootstrapResult> => {
  tokenManager.setTokens({
    accessToken: loginResponse.accessToken,
    refreshToken: loginResponse.refreshToken,
    tokenType: loginResponse.tokenType,
    expiresIn: loginResponse.expiresIn,
  });

  persistSessionActivity(Date.now());

  const [currentUser, securitySettings] = await Promise.all([
    loadCurrentUserOrFallback(loginResponse),
    loadSecuritySettings({ timeoutMs: POST_LOGIN_SECURITY_TIMEOUT_MS }),
  ]);
  applyLocalePreference(currentUser?.locale || loginResponse.user.locale, false);
  const persistedCurrentUser = persistCurrentUser(currentUser);
  return { currentUser: persistedCurrentUser, securitySettings };
};
