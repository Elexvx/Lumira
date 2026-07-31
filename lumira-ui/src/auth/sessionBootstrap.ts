import { applyLocalePreference } from '@/i18n/locale';
import type { CurrentUser, LoginResponse, MenuNode, PluginAvailability, RuntimeAppearanceSettings, SecuritySettings, SessionBootstrapPayload } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { persistSessionActivity } from '@/auth/activity';
import { persistCurrentUser, buildFallbackCurrentUser } from '@/auth/sessionState';
import { loadSecuritySettings } from '@/auth/sessionSecurity';
import {
  clearAuthSession,
  hasUsableTokenAfterRefresh,
  tryRefreshTokenOutcome,
  withBootstrapFlow,
} from '@/auth/sessionLifecycle';
import { request } from '@/services/common/request';

export interface SessionBootstrapResult {
  currentUser: CurrentUser;
  securitySettings: SecuritySettings;
  menuTree?: MenuNode[];
  availablePlugins?: PluginAvailability[];
  runtimeAppearanceSettings?: RuntimeAppearanceSettings;
}

export interface InitializeAfterLoginOptions {
  remember?: boolean;
}

const LOGIN_CURRENT_USER_TIMEOUT_MS = 5000;
const RESTORE_CURRENT_USER_TIMEOUT_MS = 10000;
const POST_LOGIN_SECURITY_TIMEOUT_MS = 5000;
const RESTORE_SECURITY_TIMEOUT_MS = 10000;
const AUTH_BOOTSTRAP_TIMEOUT_MS = 8000;

const createSecuritySettingsLoader = (options: Parameters<typeof loadSecuritySettings>[0]) => {
  let promise: Promise<SecuritySettings> | null = null;
  return () => {
    if (!promise) {
      promise = loadSecuritySettings(options);
    }
    return promise;
  };
};

const loadAuthBootstrap = async (): Promise<SessionBootstrapResult | null> => {
  try {
    const bootstrap = await request<SessionBootstrapPayload>('/v2/auth/bootstrap', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
      timeoutMs: AUTH_BOOTSTRAP_TIMEOUT_MS,
    });
    return {
      currentUser: bootstrap.currentUser,
      securitySettings: bootstrap.securitySettings,
      menuTree: bootstrap.menuTree,
      availablePlugins: bootstrap.availablePlugins,
      runtimeAppearanceSettings: bootstrap.runtimeAppearanceSettings,
    };
  } catch {
    return null;
  }
};

function loadCurrentUserOrFallback(loginResponse: LoginResponse): Promise<CurrentUser>;
function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null>;
async function loadCurrentUserOrFallback(loginResponse?: LoginResponse): Promise<CurrentUser | null> {
  try {
    const currentUser = await request<CurrentUser>('/v2/auth/current-user', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
      timeoutMs: loginResponse ? LOGIN_CURRENT_USER_TIMEOUT_MS : RESTORE_CURRENT_USER_TIMEOUT_MS,
    }).catch(() =>
      request<CurrentUser>('/v1/auth/current-user', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
        timeoutMs: loginResponse ? LOGIN_CURRENT_USER_TIMEOUT_MS : RESTORE_CURRENT_USER_TIMEOUT_MS,
      }),
    );
    return currentUser;
  } catch {
    return loginResponse ? buildFallbackCurrentUser(loginResponse) : null;
  }
}

export const initializeAfterLogin = async (
  loginResponse: LoginResponse,
  options: InitializeAfterLoginOptions = {},
): Promise<SessionBootstrapResult> =>
  withBootstrapFlow(async () => {
    const currentUser = await initializeLoginSession(loginResponse, options);
    return currentUser;
  });

export const restoreSession = async (): Promise<SessionBootstrapResult | null> =>
  withBootstrapFlow(async () => {
    if (!tokenManager.hasToken()) {
      const refreshOutcome = await tryRefreshTokenOutcome();
      if (!hasUsableTokenAfterRefresh(refreshOutcome)) {
        return null;
      }
    }

    const getSecuritySettings = createSecuritySettingsLoader({
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: RESTORE_SECURITY_TIMEOUT_MS,
    });
    const authBootstrap = await loadAuthBootstrap();
    if (authBootstrap) {
      applyLocalePreference(authBootstrap.currentUser.locale || undefined, false);
      const persistedCurrentUser = persistCurrentUser(authBootstrap.currentUser);
      return {
        currentUser: persistedCurrentUser,
        securitySettings: authBootstrap.securitySettings,
        menuTree: authBootstrap.menuTree,
        availablePlugins: authBootstrap.availablePlugins,
        runtimeAppearanceSettings: authBootstrap.runtimeAppearanceSettings,
      };
    }

    const currentUser = await loadCurrentUserOrFallback();
    if (!currentUser) {
      const refreshOutcome = await tryRefreshTokenOutcome();
      if (!hasUsableTokenAfterRefresh(refreshOutcome)) {
        if (refreshOutcome === 'session_expired') {
          clearAuthSession();
        }
        return null;
      }

      const [refreshedCurrentUser, securitySettings] = await Promise.all([
        loadCurrentUserOrFallback(),
        getSecuritySettings(),
      ]);
      if (!refreshedCurrentUser) {
        clearAuthSession();
        return null;
      }

      applyLocalePreference(refreshedCurrentUser.locale, false);
      const persistedCurrentUser = persistCurrentUser(refreshedCurrentUser);
      return { currentUser: persistedCurrentUser, securitySettings };
    }

    applyLocalePreference(currentUser.locale, false);
    const persistedCurrentUser = persistCurrentUser(currentUser);
    const securitySettings = await getSecuritySettings();
    return { currentUser: persistedCurrentUser, securitySettings };
  });

const initializeLoginSession = async (
  loginResponse: LoginResponse,
  options: InitializeAfterLoginOptions,
): Promise<SessionBootstrapResult> => {
  tokenManager.setTokens({
    accessToken: loginResponse.accessToken,
    refreshToken: loginResponse.refreshToken,
    tokenType: loginResponse.tokenType,
    expiresIn: loginResponse.expiresIn,
    remember: options.remember,
  });

  persistSessionActivity(Date.now());

  const getSecuritySettings = createSecuritySettingsLoader({ timeoutMs: POST_LOGIN_SECURITY_TIMEOUT_MS });
  const authBootstrap = await loadAuthBootstrap();
  if (authBootstrap) {
    applyLocalePreference(authBootstrap.currentUser.locale || loginResponse.user.locale, false);
    const persistedCurrentUser = persistCurrentUser(authBootstrap.currentUser);
    return {
      currentUser: persistedCurrentUser,
      securitySettings: authBootstrap.securitySettings,
      menuTree: authBootstrap.menuTree,
      availablePlugins: authBootstrap.availablePlugins,
      runtimeAppearanceSettings: authBootstrap.runtimeAppearanceSettings,
    };
  }

  const [currentUser, securitySettings] = await Promise.all([
    Promise.resolve(buildFallbackCurrentUser(loginResponse)),
    getSecuritySettings(),
  ]);
  applyLocalePreference(currentUser?.locale || loginResponse.user.locale, false);
  const persistedCurrentUser = persistCurrentUser(currentUser);
  return { currentUser: persistedCurrentUser, securitySettings };
};
