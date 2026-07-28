import type { RunTimeLayoutConfig } from '@umijs/max';
import type { ProSettings } from '@ant-design/pro-components';
import { formatMessage, history, useIntl, useLocation } from '@umijs/max';
import type { ReactNode } from 'react';
import { ArrowLeftOutlined, MoreOutlined, QrcodeOutlined, ReloadOutlined, VerticalAlignTopOutlined } from '@ant-design/icons';
import { Alert, Button, FloatButton, Form, Input, Modal, Popover, Radio, Space, Tooltip, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useQuery } from '@tanstack/react-query';
import { createPortal } from 'react-dom';
import { DEFAULT_HOME_PATH, LOGIN_PATH, isPublicPath } from '@/app.constants';
import buildAccess from '@/access';
import { DEFAULT_FLOATING_WINDOW_SETTINGS, normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import { isLoggedIn } from '@/auth/sessionLifecycle';
import { clearSessionActivity, getSessionActivityStorageKey, getStoredSessionActivityAt, persistSessionActivity } from '@/auth/activity';
import { isTrustedCurrentUser, mergeTrustedCurrentUser } from '@/auth/sessionState';
import { AUTH_SESSION_BROADCAST_CHANNEL, tokenManager } from '@/auth/token';
import { resolveTokenRefreshDelayMs } from '@/auth/sessionRefreshTiming';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { getStoredSecuritySettings } from '@/auth/securitySettingsStorage';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { performLogout, tryRefreshTokenOutcome } from '@/auth/sessionLifecycle';
import { request } from '@/services/common/request';
import { resolveAuthorizedLoginRedirectTarget, resolveRouteAccessStatus } from '@/auth/loginRedirect';
import { TopActions } from '@/layouts/components/TopActions';
import NoPermission from '@/pages/exception/NoPermission';
import { applyBrandingRuntime, buildCopyrightText, normalizeBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import type { AppInitialState, RuntimeMenuDataItem } from '@/app.types';
import type { BrandingSettings, CurrentUser, FloatingWindowSettings, MenuNode, SecondFactorChallenge, SecondFactorProviderStatus, SecuritySettings } from '@/types/api';
import { resolveBuiltinMessage } from '@/i18n/messages';
import { buildVisibleSettingsNavigationItems, resolveActiveSettingsNavigationPath } from '@/navigation/settingsNavigationRuntime';
import { resolveNavigationIcon } from '@/navigation/settingsNavigationIcon';
import { isMainMenuHiddenMonitoringPath, isMainMenuHiddenSettingPath, isSettingsShellPath } from '@/navigation/settingsNavigationRuntime';
import { backendRouteMeta, realPageRouteMetaMap, resolveCanonicalRoutePath } from '@/routes/meta';
import { API_OPTS } from '@/utils/errorMessage';
import { useResponsive } from '@/hooks/useResponsive';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import type { ThemePreference } from '@/theme/settings';
import { resolveThemeRuntimeSnapshot } from '@/theme/runtime';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import policeBeianIcon from '@/assets/police-beian.png';
import GlobalSensitiveWordGuard from '@/components/GlobalSensitiveWordGuard';
import './layouts/components/GlobalFloatActions.css';
import { buildBreadcrumbItems } from '@/features/management/ManagementPage';

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
const realPagePathSet = new Set(realPageRouteMetaMap.keys());
const resolveIsMobileViewport = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia('(max-width: 767px)').matches;
const STABLE_MAIN_ROUTE_PATHS = ['/dashboard/home', '/data-management', '/certificates', '/experts', '/user-center'];
const DASHBOARD_GROUP_PATH = '/dashboard';
const DASHBOARD_HOME_PATH = '/dashboard/home';
const LEGACY_COMPETITION_ROOT_MENU_CODE = 'competition.root';
const USER_CENTER_GROUP_PATH = '/user-center';
const PERSONAL_CENTER_GROUP_PATH = '/user-center/personal-center';
const PERSONAL_CENTER_CHILD_PATHS = ['/user-center/personal-center/profile', '/user-center/personal-center/files'];
const DATA_MANAGEMENT_GROUP_PATH = '/data-management';
const DATA_MANAGEMENT_DIRECT_CHILD_PATHS = [
  '/competitions/management',
  '/activities/management',
  '/payments/management',
  '/data-management/download-center',
];
const DATA_SOURCE_GROUP_PATHS = ['/activities', '/competitions', '/projects', '/team', '/payments'];
const HIDDEN_MAIN_MENU_LEAF_PATHS = new Set(['/user-center/personal-center']);
const ACTIVE_MAIN_MENU_PATH_BY_ROUTE: Array<[RegExp, string]> = [
  [/^\/competitions\/create$/, '/competitions/management'],
  [/^\/competitions\/[^/]+\/settings$/, '/competitions/management'],
  [/^\/projects\/create$/, '/data-management'],
  [/^\/team\/create$/, '/data-management'],
];
const MAIN_MENU_KEY_BY_PATH: Record<string, string> = {
  [USER_CENTER_GROUP_PATH]: 'main:user-center',
  [PERSONAL_CENTER_GROUP_PATH]: 'main:personal-center',
  [DATA_MANAGEMENT_GROUP_PATH]: 'main:data-management',
};
const STORAGE_ACTIVITY_KEY = getSessionActivityStorageKey();
const MOUSE_MOVE_THROTTLE_MS = 1000;
const KEEPALIVE_THROTTLE_MS = 60_000;
const TOKEN_REFRESH_RETRY_MS = 5_000;
const KEEPALIVE_ENDPOINTS = {
  v2: '/v2/auth/session/keepalive',
  v1: '/v1/auth/session/keepalive',
};
const WECHAT_CONTACT_BIND_REQUIRED_KEY = 'lumira_wechat_contact_bind_required';

type WechatContactBindFormValues = {
  contactType: 'mobile' | 'email';
  value?: string;
  currentVerificationCode?: string;
  verificationCode?: string;
};

const useSessionActivityTimers = ({ securitySettings }: { securitySettings: SecuritySettings }) => {
  const timerRef = useRef<number | null>(null);
  const tokenExpireTimerRef = useRef<number | null>(null);
  const redirectingRef = useRef(false);
  const scheduleTokenExpirationRef = useRef<() => void>(() => {});
  const lastActivityRef = useRef<number>(Date.now());
  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const clearTokenExpireTimer = useCallback(() => {
    if (tokenExpireTimerRef.current !== null) {
      window.clearTimeout(tokenExpireTimerRef.current);
      tokenExpireTimerRef.current = null;
    }
  }, []);

  const forceLogout = useCallback(
    (reason?: 'token_expired') => {
      if (redirectingRef.current) {
        return;
      }
      redirectingRef.current = true;
      clearTimer();
      clearTokenExpireTimer();
      if (reason === 'token_expired') {
        message.info(resolveBuiltinMessage('common.sessionExpired', '登录状态已过期，请重新登录'));
      }
      clearSessionActivity();
      void performLogout({ reason: 'forced_expired' });
    },
    [clearTimer, clearTokenExpireTimer],
  );

  const scheduleTimeout = useCallback(
    (lastActivityAt: number) => {
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
    },
    [clearTimer, forceLogout, securitySettings.idleTimeoutSeconds],
  );

  const refreshAccessToken = useCallback(async () => {
    if (!tokenManager.hasToken()) {
      return false;
    }

    const refreshOutcome = await tryRefreshTokenOutcome();
    if (refreshOutcome === 'session_expired') {
      forceLogout('token_expired');
      return false;
    }
    if (refreshOutcome === 'temporarily_unavailable') {
      clearTokenExpireTimer();
      tokenExpireTimerRef.current = window.setTimeout(() => {
        void refreshAccessToken();
      }, TOKEN_REFRESH_RETRY_MS);
      return false;
    }
    scheduleTokenExpirationRef.current();
    scheduleTimeout(lastActivityRef.current);
    return true;
  }, [clearTokenExpireTimer, forceLogout, scheduleTimeout]);

  const scheduleTokenExpiration = useCallback(() => {
    clearTokenExpireTimer();
    const tokenState = tokenManager.getTokenState();
    if (!tokenState) {
      return;
    }

    const remainingMs = tokenState.expiresAt - Date.now();
    const refreshDelayMs = resolveTokenRefreshDelayMs(remainingMs);

    if (remainingMs <= 0 || refreshDelayMs === 0) {
      void refreshAccessToken();
      return;
    }

    tokenExpireTimerRef.current = window.setTimeout(() => {
      void refreshAccessToken();
    }, refreshDelayMs);
  }, [clearTokenExpireTimer, refreshAccessToken]);

  useEffect(() => {
    scheduleTokenExpirationRef.current = scheduleTokenExpiration;
  }, [scheduleTokenExpiration]);

  const setLastActivityAt = useCallback((activityAt: number) => {
    lastActivityRef.current = activityAt;
  }, []);

  const getLastActivityAt = useCallback(() => lastActivityRef.current, []);
  const resetLogoutGuard = useCallback(() => {
    redirectingRef.current = false;
  }, []);

  return useMemo(
    () => ({
      clearTimer,
      clearTokenExpireTimer,
      forceLogout,
      scheduleTimeout,
      scheduleTokenExpiration,
      setLastActivityAt,
      getLastActivityAt,
      resetLogoutGuard,
    }),
    [clearTimer, clearTokenExpireTimer, forceLogout, scheduleTimeout, scheduleTokenExpiration, setLastActivityAt, getLastActivityAt, resetLogoutGuard],
  );
};

const useSessionActivityController = ({ securitySettings }: { securitySettings: SecuritySettings }) => {
  const timers = useSessionActivityTimers({ securitySettings });
  const lastBroadcastRef = useRef(0);
  const lastKeepaliveRef = useRef(0);
  const keepaliveInFlightRef = useRef(false);

  const pingSession = useCallback(async () => {
    if (keepaliveInFlightRef.current) {
      return;
    }

    const now = Date.now();
    if (now - lastKeepaliveRef.current < KEEPALIVE_THROTTLE_MS) {
      return;
    }

    keepaliveInFlightRef.current = true;
    lastKeepaliveRef.current = now;
    try {
      await request(KEEPALIVE_ENDPOINTS.v2, {
        method: 'POST',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }).catch(() =>
        request(KEEPALIVE_ENDPOINTS.v1, {
          method: 'POST',
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        }),
      );
    } catch {
      lastKeepaliveRef.current = 0;
    } finally {
      keepaliveInFlightRef.current = false;
    }
  }, []);

  const recordActivity = useCallback(
    (source: string) => {
      const now = Date.now();
      if (source === 'mousemove' && now - lastBroadcastRef.current < MOUSE_MOVE_THROTTLE_MS) {
        return;
      }

      lastBroadcastRef.current = now;
      timers.setLastActivityAt(now);
      persistSessionActivity(now);
      timers.scheduleTimeout(now);
      void pingSession();
    },
    [pingSession, timers],
  );

  const applyExternalActivityAt = useCallback(
    (activityAt: number) => {
      timers.setLastActivityAt(activityAt);
      timers.scheduleTimeout(activityAt);
    },
    [timers],
  );

  const primeStoredActivity = useCallback(() => {
    const storedActivityAt = getStoredSessionActivityAt() || Date.now();
    if (!getStoredSessionActivityAt()) {
      persistSessionActivity(storedActivityAt);
    }
    timers.setLastActivityAt(storedActivityAt);
    return storedActivityAt;
  }, [timers]);

  return useMemo(
    () => ({
      ...timers,
      recordActivity,
      applyExternalActivityAt,
      primeStoredActivity,
      getLastActivityAt: timers.getLastActivityAt,
    }),
    [applyExternalActivityAt, primeStoredActivity, recordActivity, timers],
  );
};

const resolveSiderMenuMode = (pathname: string, _initialState: AppInitialState | undefined) =>
  isSettingsShellPath(pathname) ? 'settings' : 'main';
const isPluginRuntimePath = (path?: string) => Boolean(path && (path === '/plugins' || path.startsWith('/plugins/')));

const CollapsedButtonWithReturn = ({ defaultDom }: { defaultDom: ReactNode }) => {
  const location = useLocation();
  const { isMobile } = useResponsive();
  const { initialState } = useInitialStateModel();

  if (!isMobile || (resolveSiderMenuMode(location.pathname, initialState) !== 'settings')) {
    return <>{defaultDom}</>;
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: resolveResponsiveValue(APP_SPACING.microGap, isMobile) }}>
      {defaultDom}
      <Tooltip title={resolveBuiltinMessage('app.layout.backToMainRoute', '返回主路由')}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          aria-label={resolveBuiltinMessage('app.layout.backToMainRoute', '返回主路由')}
          onClick={() => history.push(DEFAULT_HOME_PATH)}
        />
      </Tooltip>
    </div>
  );
};

const buildThemeRuntimeRevisionKey = (themePreference: ThemePreference, resolvedColorMode: 'light' | 'dark') =>
  `${themePreference}:${resolvedColorMode}`;

const shouldAdvanceThemeRevision = (previousThemeKey: string | undefined, nextThemeKey: string) =>
  Boolean(previousThemeKey && previousThemeKey !== nextThemeKey);

const resolveProLayoutNavTheme = (): NonNullable<ProSettings['navTheme']> =>
  (resolveThemeRuntimeSnapshot().resolvedColorMode === 'dark' ? 'realDark' : 'light');

const resolveProLayoutThemeSettings = (): Pick<ProSettings, 'navTheme'> => ({
  navTheme: resolveProLayoutNavTheme(),
});

const ThemeRuntimeBridge = () => {
  const { themePreference, resolvedColorMode } = useThemePreference();
  const { setInitialState } = useInitialStateModel();
  const previousThemeKeyRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    const nextThemeKey = buildThemeRuntimeRevisionKey(themePreference, resolvedColorMode);
    const shouldAdvanceRevision = shouldAdvanceThemeRevision(previousThemeKeyRef.current, nextThemeKey);
    previousThemeKeyRef.current = nextThemeKey;

    if (!shouldAdvanceRevision) {
      return;
    }

    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            themeRevision: (prev.themeRevision ?? 0) + 1,
          }
        : prev,
    );
  }, [resolvedColorMode, setInitialState, themePreference]);

  return null;
};

const SessionActivityGuard = ({ children }: { children: ReactNode }) => {
  const { initialState } = useInitialStateModel();
  const location = useLocation();
  const securitySettings = normalizeSecuritySettings(initialState?.securitySettings || getStoredSecuritySettings() || DEFAULT_SECURITY_SETTINGS);
  const controller = useSessionActivityController({ securitySettings });
  const trustedCurrentUser = isTrustedCurrentUser(initialState?.currentUser) ? initialState.currentUser : undefined;
  const trustedSessionId = trustedCurrentUser?.sessionId;

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      controller.clearTimer();
      controller.clearTokenExpireTimer();
      return;
    }

    controller.resetLogoutGuard();
    controller.scheduleTokenExpiration();
    controller.scheduleTimeout(controller.primeStoredActivity());

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
      controller.recordActivity(event.type);
    };
    activityEvents.forEach((eventName) => {
      window.addEventListener(eventName, handleUserActivity, { passive: true });
    });

    const handleStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_ACTIVITY_KEY && event.newValue) {
        const parsed = Number(event.newValue);
        if (Number.isFinite(parsed) && parsed > 0) {
          controller.applyExternalActivityAt(parsed);
        }
      }

    };
    window.addEventListener('storage', handleStorage);
    const authChannel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(AUTH_SESSION_BROADCAST_CHANNEL);
    if (authChannel) {
      authChannel.onmessage = (event: MessageEvent<{ type?: string }>) => {
        if (event.data?.type === 'updated') {
          if (tokenManager.syncFromStorage()) {
            controller.resetLogoutGuard();
            controller.scheduleTokenExpiration();
            controller.scheduleTimeout(controller.getLastActivityAt());
          }
          return;
        }
        if (event.data?.type === 'cleared') {
          controller.forceLogout();
        }
      };
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      const elapsedMs = Date.now() - controller.getLastActivityAt();
      if (elapsedMs >= securitySettings.idleTimeoutSeconds * 1000) {
        controller.forceLogout();
        return;
      }
      controller.scheduleTimeout(controller.getLastActivityAt());
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    const handleFocus = () => {
      const elapsedMs = Date.now() - controller.getLastActivityAt();
      if (elapsedMs >= securitySettings.idleTimeoutSeconds * 1000) {
        controller.forceLogout();
        return;
      }
      controller.scheduleTimeout(controller.getLastActivityAt());
    };
    window.addEventListener('focus', handleFocus);

    return () => {
      controller.clearTimer();
      controller.clearTokenExpireTimer();
      activityEvents.forEach((eventName) => {
        window.removeEventListener(eventName, handleUserActivity);
      });
      window.removeEventListener('storage', handleStorage);
      authChannel?.close();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
    // Rebuild activity bindings when auth/session context changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controller, securitySettings.idleTimeoutSeconds, trustedSessionId]);

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      controller.clearTimer();
      controller.clearTokenExpireTimer();
      return;
    }
    controller.recordActivity('route');
    // Route changes count as activity, so we keep the idle timer in sync.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, location.search, controller]);

  useEffect(() => {
    if (!tokenManager.hasToken()) {
      controller.clearTimer();
      controller.clearTokenExpireTimer();
      return;
    }

    controller.scheduleTokenExpiration();
    controller.scheduleTimeout(controller.getLastActivityAt());
    // Recompute when the policy changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controller, securitySettings.accessTokenExpireSeconds, securitySettings.idleTimeoutSeconds, securitySettings.refreshTokenExpireSeconds]);

  return children;
};

const GlobalFloatActions = () => {
  const intl = useIntl();
  const { isMobile } = useResponsive();
  const { initialState } = useInitialStateModel();
  const floatActionZIndex = 980;
  const floatingSettingsQuery = useQuery({
    queryKey: ['floating-window-settings'],
    queryFn: () =>
      request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
        method: 'GET',
        ...API_OPTS.SILENT_NO_REDIRECT,
      }),
    enabled: isLoggedIn() && !initialState?.floatingWindowSettings,
    initialData: initialState?.floatingWindowSettings,
    staleTime: 5 * 60 * 1000,
  });
  const floatingSettings = normalizeFloatingWindowSettings(floatingSettingsQuery.data || DEFAULT_FLOATING_WINDOW_SETTINGS);
  const showApiDocsQr = floatingSettings.apiDocsQrEnabled;
  const hasApiDocsQrImage = Boolean(floatingSettings.apiDocsQrImageUrl);
  const floatButtonRight = isMobile ? 16 : 32;
  const floatButtonBottom = isMobile ? 24 : 40;

  if (!isLoggedIn()) {
    return null;
  }

  const floatButtonGroup = (
    <FloatButton.Group
      className="saas-global-float-actions"
      shape="circle"
      trigger="hover"
      placement="top"
      type="primary"
      icon={<MoreOutlined />}
      tooltip={intl.formatMessage({ id: 'global.float.more', defaultMessage: '快捷操作' })}
      style={{
        direction: 'ltr',
        right: floatButtonRight,
        bottom: floatButtonBottom,
        zIndex: floatActionZIndex,
      }}
    >
      {showApiDocsQr ? (
        <Popover
          overlayClassName="saas-global-float-actions__qr-popover"
          placement="left"
          trigger={['hover', 'click']}
          zIndex={floatActionZIndex + 1}
          content={
            <div className="saas-global-float-actions__qr-card">
              <Typography.Text className="saas-global-float-actions__qr-title" type="secondary">
                {floatingSettings.apiDocsQrTitle}
              </Typography.Text>
              <div className="saas-global-float-actions__qr-image-wrap">
                {hasApiDocsQrImage ? (
                  <img className="saas-global-float-actions__qr-image" src={floatingSettings.apiDocsQrImageUrl} alt={floatingSettings.apiDocsQrTitle} />
                ) : (
                  <Typography.Text className="saas-global-float-actions__qr-placeholder" type="secondary">
                    {intl.formatMessage({
                      id: 'app.layout.uploadQrHint',
                      defaultMessage: '请在个性化设置上传二维码',
                    })}
                  </Typography.Text>
                )}
              </div>
            </div>
          }
        >
          <FloatButton
            icon={<QrcodeOutlined />}
            tooltip={intl.formatMessage({ id: 'global.float.qrCode', defaultMessage: '二维码' })}
            aria-label={intl.formatMessage({ id: 'global.float.qrCode', defaultMessage: '二维码' })}
          />
        </Popover>
      ) : null}
      <FloatButton
        icon={<ReloadOutlined />}
        tooltip={intl.formatMessage({ id: 'global.float.refresh', defaultMessage: '刷新页面' })}
        aria-label={intl.formatMessage({ id: 'global.float.refresh', defaultMessage: '刷新页面' })}
        onClick={() => window.location.reload()}
      />
      <FloatButton.BackTop
        icon={<VerticalAlignTopOutlined />}
        tooltip={intl.formatMessage({ id: 'global.float.backTop', defaultMessage: '回到顶部' })}
        aria-label={intl.formatMessage({ id: 'global.float.backTop', defaultMessage: '回到顶部' })}
        visibilityHeight={0}
      />
    </FloatButton.Group>
  );

  return typeof document === 'undefined' ? floatButtonGroup : createPortal(floatButtonGroup, document.body);
};

const WechatContactBindGuard = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const [form] = Form.useForm<WechatContactBindFormValues>();
  const contactType = Form.useWatch('contactType', form) || 'mobile';
  const contactValue = Form.useWatch('value', form)?.trim() || '';
  const [challenge, setChallenge] = useState<SecondFactorChallenge | null>(null);
  const [challengeTarget, setChallengeTarget] = useState('');
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [alertMessage, setAlertMessage] = useState<string | null>(null);
  const currentUser = initialState?.currentUser;
  const loginCapabilities = initialState?.loginCapabilities;
  const mobileAvailable = Boolean(loginCapabilities?.smsLoginAvailable);
  const emailAvailable = Boolean(loginCapabilities?.emailLoginAvailable);
  const shouldOpen = useMemo(() => {
    if (typeof window === 'undefined' || !isLoggedIn() || !currentUser) {
      return false;
    }
    return (
      window.sessionStorage.getItem(WECHAT_CONTACT_BIND_REQUIRED_KEY) === '1' &&
      !currentUser.mobile &&
      !currentUser.email &&
      (mobileAvailable || emailAvailable)
    );
  }, [currentUser, emailAvailable, mobileAvailable]);
  const verificationProvidersQuery = useQuery({
    queryKey: ['wechat-contact-bind-verification-providers', currentUser?.userId],
    enabled: shouldOpen,
    queryFn: () =>
      request<SecondFactorProviderStatus[]>('/v1/auth/verification/providers', {
        autoRedirectOnUnauthorized: false,
      }),
  });
  const currentVerificationProvider = useMemo(
    () => (verificationProvidersQuery.data || []).find((item) => item.factorCode === 'totp' && item.bound && item.systemEnabled !== false) || null,
    [verificationProvidersQuery.data],
  );
  const [currentVerificationChallenge, setCurrentVerificationChallenge] = useState<SecondFactorChallenge | null>(null);
  const [currentVerificationLoading, setCurrentVerificationLoading] = useState(false);
  const canUseSelectedType = contactType === 'mobile' ? mobileAvailable : emailAvailable;
  const codeMatchesValue = Boolean(challenge?.challengeId && challengeTarget === contactValue);

  useEffect(() => {
    if (!shouldOpen) {
      return;
    }
    form.setFieldsValue({
      contactType: mobileAvailable ? 'mobile' : 'email',
      value: '',
      currentVerificationCode: undefined,
      verificationCode: undefined,
    });
    setChallenge(null);
    setChallengeTarget('');
    setCurrentVerificationChallenge(null);
    setAlertMessage(null);
  }, [form, mobileAvailable, shouldOpen]);

  useEffect(() => {
    setChallenge(null);
    setChallengeTarget('');
    setAlertMessage(null);
    form.setFieldsValue({ value: '', currentVerificationCode: undefined, verificationCode: undefined });
  }, [contactType, form]);

  const requestCurrentVerificationChallenge = useCallback(async () => {
    if (!currentVerificationProvider) {
      return null;
    }
    setCurrentVerificationLoading(true);
    try {
      const nextChallenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${currentVerificationProvider.factorCode}/challenge`, {
        method: 'POST',
        ...API_OPTS.SILENT_NO_REDIRECT,
      });
      setCurrentVerificationChallenge(nextChallenge);
      form.setFieldValue('currentVerificationCode', undefined);
      return nextChallenge;
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : 'Failed to load the current verification method. Please try again later.');
      return null;
    } finally {
      setCurrentVerificationLoading(false);
    }
  }, [currentVerificationProvider, form]);

  useEffect(() => {
    if (!shouldOpen || !currentVerificationProvider) {
      return;
    }
    void requestCurrentVerificationChallenge();
  }, [currentVerificationProvider, requestCurrentVerificationChallenge, shouldOpen]);

  useEffect(() => {
    if (currentUser?.mobile || currentUser?.email) {
      window.sessionStorage.removeItem(WECHAT_CONTACT_BIND_REQUIRED_KEY);
    }
  }, [currentUser?.email, currentUser?.mobile]);

  const requestCode = useCallback(async () => {
    try {
      const values = await form.validateFields(['contactType', 'value']);
      const nextType = values.contactType || 'mobile';
      const nextValue = values.value?.trim();
      if (!nextValue) {
        return false;
      }
      let nextCurrentVerificationChallenge = currentVerificationChallenge;
      if (currentVerificationProvider) {
        const currentVerificationCode = form.getFieldValue('currentVerificationCode')?.trim();
        if (!currentVerificationCode) {
          setAlertMessage('Please enter the current verification code or a recovery code first.');
          return false;
        }
        if (!nextCurrentVerificationChallenge?.challengeId) {
          nextCurrentVerificationChallenge = await requestCurrentVerificationChallenge();
          if (!nextCurrentVerificationChallenge?.challengeId) {
            return false;
          }
        }
      }
      setSending(true);
      setAlertMessage(null);
      const nextChallenge = await request<SecondFactorChallenge>('/v1/profile/contact-bind/challenge', {
        method: 'POST',
        data: {
          contactType: nextType,
          value: nextValue,
          currentFactorCode: currentVerificationProvider?.factorCode,
          currentChallengeId: nextCurrentVerificationChallenge?.challengeId,
          currentVerificationCode: currentVerificationProvider ? form.getFieldValue('currentVerificationCode')?.trim() : undefined,
        },
        ...API_OPTS.SILENT_NO_REDIRECT,
      });
      setChallenge(nextChallenge);
      setChallengeTarget(nextValue);
      setCurrentVerificationChallenge(null);
      form.setFieldsValue({ currentVerificationCode: undefined, verificationCode: undefined });
      message.success('Verification code sent. Enter it to finish binding.');
      return true;
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : 'Failed to send the verification code. Please try again later.');
      return false;
    } finally {
      setSending(false);
    }
  }, [currentVerificationChallenge, currentVerificationProvider, form, requestCurrentVerificationChallenge]);

  const handleSubmit = useCallback(async () => {
    if (!canUseSelectedType) {
      setAlertMessage(contactType === 'mobile' ? '短信验证码未启用，无法绑定手机号' : '邮箱验证码未启用，无法绑定邮箱');
      return;
    }

    const values = await form.validateFields().catch(() => null);
    if (!values?.value?.trim()) {
      return;
    }

    const nextValue = values.value.trim();
    if (!challenge?.challengeId || challengeTarget !== nextValue) {
      await requestCode();
      return;
    }

    if (!values.verificationCode?.trim()) {
      setAlertMessage('请输入收到的验证码');
      return;
    }

    setSubmitting(true);
    setAlertMessage(null);
    try {
      const updatedUser = await request<CurrentUser>('/v1/profile/contact-bind', {
        method: 'PUT',
        data: {
          contactType: values.contactType,
          value: nextValue,
          challengeId: challenge.challengeId,
          verificationCode: values.verificationCode.trim(),
        },
        ...API_OPTS.NO_REDIRECT,
      });
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: mergeTrustedCurrentUser(prev.currentUser, updatedUser),
            }
          : prev,
      );
      window.sessionStorage.removeItem(WECHAT_CONTACT_BIND_REQUIRED_KEY);
      message.success(values.contactType === 'mobile' ? '手机号已绑定' : '邮箱已绑定');
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : '绑定失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  }, [canUseSelectedType, challenge, challengeTarget, contactType, form, requestCode, setInitialState]);

  if (!shouldOpen) {
    return null;
  }

  return (
    <Modal
      open
      title="绑定手机号或邮箱"
      closable={false}
      maskClosable={false}
      keyboard={false}
      okText={codeMatchesValue ? '确认绑定' : '发送验证码'}
      cancelButtonProps={{ style: { display: 'none' } }}
      confirmLoading={sending || submitting || currentVerificationLoading}
      onOk={() => void handleSubmit()}
      destroyOnHidden
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          showIcon
          type="warning"
          message="微信登录后需要先绑定手机号或邮箱"
          description="绑定后可用于验证码登录、找回账号和接收安全通知。"
        />
        {currentVerificationProvider ? (
          <Alert
            showIcon
            type="info"
            message="请先确认当前身份"
            description={
              currentVerificationChallenge?.promptMessage
              || '请先输入当前验证方式中的验证码或恢复码，确认成功后系统才会向新的联系方式发送验证码。'
            }
          />
        ) : null}
        {alertMessage ? <Alert showIcon type="error" message={alertMessage} /> : null}
        <Form<WechatContactBindFormValues>
          form={form}
          layout="vertical"
          initialValues={{ contactType: mobileAvailable ? 'mobile' : 'email' }}
        >
          <Form.Item name="contactType" label="绑定方式" rules={[{ required: true, message: '请选择绑定方式' }]}>
            <Radio.Group>
              <Radio.Button value="mobile" disabled={!mobileAvailable}>手机号</Radio.Button>
              <Radio.Button value="email" disabled={!emailAvailable}>邮箱</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="value"
            label={contactType === 'mobile' ? '手机号' : '邮箱'}
            rules={[
              { required: true, message: contactType === 'mobile' ? '请输入手机号' : '请输入邮箱' },
              ...(contactType === 'mobile'
                ? [{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号' }]
                : [{ type: 'email' as const, message: '请输入有效邮箱地址' }]),
            ]}
          >
            <Input
              placeholder={contactType === 'mobile' ? '请输入手机号' : '请输入邮箱地址'}
              autoComplete={contactType === 'mobile' ? 'tel' : 'email'}
              inputMode={contactType === 'mobile' ? 'tel' : 'email'}
            />
          </Form.Item>
          {currentVerificationProvider ? (
            <>
              <Form.Item label="当前验证方式">
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Typography.Text>
                    {currentVerificationChallenge?.factorName || currentVerificationProvider.factorName || currentVerificationProvider.factorCode}
                    {currentVerificationChallenge?.maskedContact || currentVerificationProvider.maskedContact
                      ? ` · ${currentVerificationChallenge?.maskedContact || currentVerificationProvider.maskedContact}`
                      : ''}
                  </Typography.Text>
                  {!currentVerificationChallenge && !currentVerificationLoading ? (
                    <Button onClick={() => void requestCurrentVerificationChallenge()}>重新获取当前验证信息</Button>
                  ) : null}
                </Space>
              </Form.Item>
              <Form.Item
                name="currentVerificationCode"
                label="当前验证码"
                rules={[{ required: true, message: '请输入当前验证码或恢复码' }]}
              >
                <Input autoComplete="one-time-code" placeholder="请输入当前验证码或恢复码" disabled={currentVerificationLoading} />
              </Form.Item>
            </>
          ) : null}
          {challenge?.challengeId && challengeTarget === contactValue ? (
            <Form.Item
              name="verificationCode"
              label="验证码"
              rules={[
                { required: true, message: '请输入验证码' },
                { pattern: /^\d{6}$/, message: '验证码必须为 6 位数字' },
              ]}
              extra={challenge.promptMessage || (challenge.maskedContact ? `验证码已发送至 ${challenge.maskedContact}` : undefined)}
            >
              <Input maxLength={6} autoComplete="one-time-code" inputMode="numeric" placeholder="请输入 6 位验证码" />
            </Form.Item>
          ) : null}
        </Form>
      </Space>
    </Modal>
  );
};

const createLayoutOnPageChange = ({ initialState }: { initialState: AppInitialState | undefined }) => () => {
  const { location } = history;
  const path = resolveCanonicalRoutePath(location.pathname);
  const canonicalLocation = `${path}${location.search || ''}`;

  if (canonicalLocation !== `${location.pathname}${location.search || ''}`) {
    history.replace(canonicalLocation);
    return;
  }

  const loggedIn = isLoggedIn();
  const isPublicRoute = isPublicPath(path);
  const requiresPasswordChange = Boolean(initialState?.currentUser?.requiresPasswordChange);

  if (!loggedIn && !isPublicRoute) {
    const redirect = `${path}${location.search || ''}`;
    history.replace(`${LOGIN_PATH}?redirect=${encodeURIComponent(redirect)}`);
    return;
  }

  if (loggedIn && requiresPasswordChange && path !== LOGIN_PATH) {
    const redirect = `${path}${location.search || ''}`;
    history.replace(`${LOGIN_PATH}?forcePasswordChange=1&redirect=${encodeURIComponent(redirect)}`);
    return;
  }

  if (loggedIn && path === LOGIN_PATH) {
    if (requiresPasswordChange) {
      return;
    }
    if (initialState?.currentUser) {
      history.replace(resolveAuthorizedLoginRedirectTarget(location.search || '', initialState.currentUser, initialState.menuTree, DEFAULT_HOME_PATH));
    }
    return;
  }

  if (loggedIn && !isPublicRoute && initialState?.currentUser) {
    const routeAccessStatus = resolveRouteAccessStatus(path, initialState.currentUser);
    if (routeAccessStatus === 'denied') {
      history.replace('/403');
    }
  }
};

const ICP_QUERY_URL = 'https://beian.miit.gov.cn/';
const POLICE_BEIAN_QUERY_URL = 'https://beian.mps.gov.cn/#/query/webSearch';

const resolvePoliceBeianQueryUrl = (text: string) => {
  const recordCode = text.match(/\d{13,}/)?.[0];
  return recordCode ? `${POLICE_BEIAN_QUERY_URL}?code=${encodeURIComponent(recordCode)}` : POLICE_BEIAN_QUERY_URL;
};

const renderBeianLink = (text: string, href: string, icon?: string) => (
  <a className="saas-layout-footer__link" href={href} target="_blank" rel="noreferrer">
    {icon ? <img className="saas-layout-footer__police-beian-icon" src={icon} alt="" aria-hidden="true" /> : null}
    <span>{text}</span>
  </a>
);

const renderLayoutFooter = (brandingSettings: BrandingSettings) => {
  const copyrightText = brandingSettings.footerCopyright || buildCopyrightText(brandingSettings);

  if (!brandingSettings.footerIcp && !brandingSettings.footerPoliceBeian && !copyrightText) {
    return null;
  }

  return (
    <div className="saas-layout-footer">
      {brandingSettings.footerIcp || brandingSettings.footerPoliceBeian ? (
        <div className="saas-layout-footer__line saas-layout-footer__beian-line">
          {brandingSettings.footerIcp ? renderBeianLink(brandingSettings.footerIcp, ICP_QUERY_URL) : null}
          {brandingSettings.footerIcp && brandingSettings.footerPoliceBeian ? <span className="saas-layout-footer__separator" aria-hidden="true" /> : null}
          {brandingSettings.footerPoliceBeian
            ? renderBeianLink(brandingSettings.footerPoliceBeian, resolvePoliceBeianQueryUrl(brandingSettings.footerPoliceBeian), policeBeianIcon)
            : null}
        </div>
      ) : null}
      {copyrightText ? <div className="saas-layout-footer__line">{copyrightText}</div> : null}
    </div>
  );
};

const renderBrandHomeLink = ({ logo, brandName }: { logo: ReactNode; brandName: string }) => (
  <button
    type="button"
    className="saas-layout-brand"
    aria-label={formatMessage({ id: 'app.brand.backHome', defaultMessage: '返回首页' })}
    title={brandName}
    onClick={() => history.push(DEFAULT_HOME_PATH)}
  >
    {logo || <span className="saas-layout-brand__text">{brandName}</span>}
  </button>
);

const flattenLocalMenuMap = (items: RuntimeMenuDataItem[], map = new Map<string, RuntimeMenuDataItem>()) => {
  items.forEach((item) => {
    if (item.path && !(item as RuntimeMenuDataItem & { redirect?: string }).redirect) {
      map.set(item.path, item);
    }
    if (item.children?.length) {
      flattenLocalMenuMap(item.children, map);
    }
  });

  return map;
};

const collectMenuPaths = (items: RuntimeMenuDataItem[], paths = new Set<string>()) => {
  items.forEach((item) => {
    if (item.path) {
      paths.add(item.path);
    }
    if (item.children?.length) {
      collectMenuPaths(item.children, paths);
    }
  });

  return paths;
};

const collectMenuNodePaths = (items: MenuNode[] | undefined, paths = new Set<string>()) => {
  items?.forEach((item) => {
    const normalizedPath = item.path ? resolveCanonicalRoutePath(item.path) : undefined;
    if (normalizedPath) {
      paths.add(normalizedPath);
    }

    if (item.children?.length) {
      collectMenuNodePaths(item.children, paths);
    }
  });

  return paths;
};

const removeLegacyCompetitionRootMenus = (items: MenuNode[] | undefined): MenuNode[] =>
  (items || []).flatMap((item) => {
    const children = removeLegacyCompetitionRootMenus(item.children);
    if (item.menuCode === LEGACY_COMPETITION_ROOT_MENU_CODE) {
      return children;
    }
    return {
      ...item,
      children: children.length ? children : undefined,
    };
  });

const resolveSelectedMenuPath = (pathname: string, menuTree: MenuNode[] | undefined) => {
  const normalizedPathname = resolveCanonicalRoutePath(pathname);
  const visiblePaths = Array.from(collectMenuNodePaths(menuTree));
  const activeMenuPath = ACTIVE_MAIN_MENU_PATH_BY_ROUTE.find(([pattern]) => pattern.test(normalizedPathname))?.[1];

  if (!visiblePaths.length) {
    return activeMenuPath || normalizedPathname;
  }

  if (activeMenuPath && visiblePaths.includes(activeMenuPath)) {
    return activeMenuPath;
  }

  const exactMatch = visiblePaths.find((path) => path === normalizedPathname);
  if (exactMatch) {
    return exactMatch;
  }

  const prefixMatch = visiblePaths
    .filter((path) => normalizedPathname.startsWith(`${path}/`))
    .sort((left, right) => right.length - left.length)[0];

  return prefixMatch || normalizedPathname;
};

const hasMenuPathOrChild = (paths: Set<string>, targetPath: string) =>
  paths.has(targetPath) || [...paths].some((path) => path.startsWith(`${targetPath}/`));

const looksLikeRoutePath = (value?: string | null) => Boolean(value?.trim().startsWith('/'));

const resolveNavigationMenuName = (labelId?: string | null, fallback?: string | null) =>
  resolveBuiltinMessage(
    labelId,
    fallback && !looksLikeRoutePath(fallback) ? fallback : undefined,
  );

const removeMenuPathsForLayout = (
  items: RuntimeMenuDataItem[],
  pathsToRemove: Set<string>,
): RuntimeMenuDataItem[] =>
  items.flatMap((item) => {
    const normalizedPath = item.path ? resolveCanonicalRoutePath(item.path) : item.path;
    const children = item.children?.length ? removeMenuPathsForLayout(item.children, pathsToRemove) : [];

    if (normalizedPath && pathsToRemove.has(normalizedPath)) {
      return children;
    }

    if (!normalizedPath && item.children?.length && !children.length) {
      return [];
    }

    return {
      ...item,
      children: children.length ? children : undefined,
    };
  });

const buildDashboardMenuGroupForLayout = (
  fallbackByPath: Map<string, RuntimeMenuDataItem>,
  accessMap: Record<string, unknown>,
): RuntimeMenuDataItem | null => {
  const groupMeta = routeMetaMap.get(DASHBOARD_GROUP_PATH);
  const childMeta = routeMetaMap.get(DASHBOARD_HOME_PATH);
  if (!childMeta || (childMeta.access && !accessMap[childMeta.access])) {
    return null;
  }

  const localChild = fallbackByPath.get(DASHBOARD_HOME_PATH);
  const childMenu: RuntimeMenuDataItem = localChild || {
    path: childMeta.path,
    name: resolveBuiltinMessage(childMeta.name, formatMessage({ id: childMeta.name, defaultMessage: childMeta.name })),
    locale: false as const,
    icon: resolveNavigationIcon(childMeta.icon),
    hideInMenu: childMeta.hideInMenu,
  };

  return {
    path: DASHBOARD_GROUP_PATH,
    name: resolveBuiltinMessage(
      groupMeta?.name || childMeta.name,
      formatMessage({ id: groupMeta?.name || childMeta.name, defaultMessage: groupMeta?.name || childMeta.name }),
    ),
    locale: false as const,
    icon: resolveNavigationIcon(groupMeta?.icon || childMeta.icon),
    hideInMenu: false,
    children: [{ ...childMenu, hideInMenu: false }],
  };
};

const buildStableRouteMenuItemForLayout = (
  path: string,
  fallbackByPath: Map<string, RuntimeMenuDataItem>,
  accessMap: Record<string, unknown>,
): RuntimeMenuDataItem | null => {
  const localMenu = fallbackByPath.get(path);
  const meta = routeMetaMap.get(path);
  if (!meta || (meta.access && !accessMap[meta.access])) {
    return null;
  }

  if (localMenu) {
    return {
      ...localMenu,
      name: resolveNavigationMenuName(meta.name, typeof localMenu.name === 'string' ? localMenu.name : undefined),
      locale: false as const,
      icon: resolveNavigationIcon(localMenu.icon) ?? resolveNavigationIcon(meta.icon),
      hideInMenu: false,
    };
  }

  return {
    path: meta.path,
    name: resolveNavigationMenuName(meta.name, formatMessage({ id: meta.name, defaultMessage: meta.name })),
    locale: false as const,
    icon: resolveNavigationIcon(meta.icon),
    hideInMenu: false,
  };
};

const buildPersonalCenterMenuGroupForLayout = (
  fallbackByPath: Map<string, RuntimeMenuDataItem>,
  accessMap: Record<string, unknown>,
): RuntimeMenuDataItem | null => {
  const groupMeta = routeMetaMap.get(PERSONAL_CENTER_GROUP_PATH);
  const children = PERSONAL_CENTER_CHILD_PATHS
    .map((path) => buildStableRouteMenuItemForLayout(path, fallbackByPath, accessMap))
    .filter(Boolean) as RuntimeMenuDataItem[];

  if (!groupMeta || !children.length) {
    return null;
  }

  return {
    key: MAIN_MENU_KEY_BY_PATH[PERSONAL_CENTER_GROUP_PATH],
    path: PERSONAL_CENTER_GROUP_PATH,
    name: resolveNavigationMenuName(
      groupMeta.name,
      formatMessage({ id: groupMeta.name, defaultMessage: groupMeta.name }),
    ),
    locale: false as const,
    icon: resolveNavigationIcon(groupMeta.icon),
    hideInMenu: false,
    children,
  };
};

const buildDataManagementMenuGroupForLayout = (
  fallbackByPath: Map<string, RuntimeMenuDataItem>,
  accessMap: Record<string, unknown>,
): RuntimeMenuDataItem | null => {
  const groupMeta = routeMetaMap.get(DATA_MANAGEMENT_GROUP_PATH);
  const directChildren = DATA_MANAGEMENT_DIRECT_CHILD_PATHS
    .map((path) => buildStableRouteMenuItemForLayout(path, fallbackByPath, accessMap))
    .filter(Boolean) as RuntimeMenuDataItem[];
  const children = directChildren;

  if (!groupMeta || !children.length) {
    return null;
  }

  return {
    key: MAIN_MENU_KEY_BY_PATH[DATA_MANAGEMENT_GROUP_PATH],
    path: DATA_MANAGEMENT_GROUP_PATH,
    name: resolveNavigationMenuName(
      groupMeta.name,
      formatMessage({ id: groupMeta.name, defaultMessage: groupMeta.name }),
    ),
    locale: false as const,
    icon: resolveNavigationIcon(groupMeta.icon),
    hideInMenu: false,
    children,
  };
};

const translateVisibleLocalMenuDataForLayout = (
  initialState: AppInitialState | undefined,
  items: RuntimeMenuDataItem[],
): RuntimeMenuDataItem[] => {
  const access = buildAccess({ currentUser: initialState?.currentUser, availablePlugins: initialState?.availablePlugins }) as Record<string, unknown>;

  return items
    .map((item) => {
      const localItem = item as RuntimeMenuDataItem & { redirect?: string };
      const normalizedPath = item.path ? resolveCanonicalRoutePath(item.path) : item.path;
      if (localItem.redirect) {
        return null;
      }

      const routeMeta = normalizedPath ? routeMetaMap.get(normalizedPath) : undefined;
      const hasRealPageRoute = normalizedPath ? realPagePathSet.has(normalizedPath) : false;
      const children = item.children?.length ? translateVisibleLocalMenuDataForLayout(initialState, item.children) : [];
      if ((!routeMeta || !hasRealPageRoute) && !children.length) {
        return null;
      }
      if (routeMeta?.hideInMenu && !children.length) {
        return null;
      }
      if (routeMeta?.access && !access[routeMeta.access] && !children.length) {
        return null;
      }

      const labelId = typeof item.locale === 'string' ? item.locale : item.name || item.title || item.path;
      return {
        ...item,
        path: routeMeta?.path || normalizedPath || item.path,
        name: typeof labelId === 'string'
          ? resolveBuiltinMessage(labelId, typeof item.name === 'string' ? item.name : undefined)
          : item.name,
        locale: false as const,
        hideInMenu: routeMeta?.hideInMenu,
        children: children.length ? children : undefined,
      };
    })
    .filter(Boolean) as RuntimeMenuDataItem[];
};

const composeMenuItemForLayout = (
  backendNode: MenuNode,
  localByPath: Map<string, RuntimeMenuDataItem>,
): RuntimeMenuDataItem | null => {
  if (isMainMenuHiddenSettingPath(backendNode.path) || isMainMenuHiddenMonitoringPath(backendNode.path)) {
    return null;
  }

  const backendPath = backendNode.path || '';
  const normalizedPath = resolveCanonicalRoutePath(backendPath);
  const localMeta = localByPath.get(normalizedPath);
  const mergedMeta = routeMetaMap.get(backendPath) || routeMetaMap.get(normalizedPath);
  const hasLocalRoute = Boolean(
    (backendNode.path && realPagePathSet.has(normalizedPath))
      || isPluginRuntimePath(backendNode.path),
  );
  const children = (backendNode.children || [])
    .map((child) => composeMenuItemForLayout(child, localByPath))
    .filter(Boolean) as RuntimeMenuDataItem[];

  if (!hasLocalRoute && !children.length) {
    return null;
  }

  const { children: _localChildren, routes: _localRoutes, ...localItemMeta } =
    (localMeta || {}) as RuntimeMenuDataItem & { routes?: RuntimeMenuDataItem[] };
  const icon = resolveNavigationIcon(backendNode.icon) ?? resolveNavigationIcon(localMeta?.icon) ?? resolveNavigationIcon(mergedMeta?.icon);
  const isRedirectGroup = children.length > 0 && Boolean(backendNode.component?.startsWith('redirect:'));
  const isUserCenterMenuGroup = normalizedPath === USER_CENTER_GROUP_PATH && children.length > 0;
  const menuLabelId = backendNode.name && !looksLikeRoutePath(backendNode.name)
    ? backendNode.name
    : mergedMeta?.name || backendNode.menuCode;

  return {
    ...localItemMeta,
    key: MAIN_MENU_KEY_BY_PATH[normalizedPath] || localItemMeta.key,
    path: isRedirectGroup || isUserCenterMenuGroup ? undefined : normalizedPath || localMeta?.path,
    name: resolveNavigationMenuName(menuLabelId, mergedMeta?.name || backendNode.name),
    locale: false as const,
    icon,
    hideInMenu: localMeta?.hideInMenu || mergedMeta?.hideInMenu,
    children: children.length ? children : undefined,
  };
};

const resolveStableMainMenuSortPath = (item: RuntimeMenuDataItem) => {
  if (item.path === DASHBOARD_GROUP_PATH) {
    return DASHBOARD_HOME_PATH;
  }
  if (item.path) {
    return item.path;
  }
  if (item.key) {
    const matchedPath = Object.entries(MAIN_MENU_KEY_BY_PATH).find(([, key]) => key === item.key)?.[0];
    if (matchedPath) {
      return matchedPath;
    }
  }
  return '';
};

const buildMainMenuDataForLayout = (
  initialState: AppInitialState | undefined,
  menuData: RuntimeMenuDataItem[],
  fallbackSourceMenuData: RuntimeMenuDataItem[] = menuData,
  options: { allowMissingStableMenus?: boolean } = {},
) => {
  const access = buildAccess({ currentUser: initialState?.currentUser, availablePlugins: initialState?.availablePlugins });
  const accessMap = access as Record<string, unknown>;
  const fallbackByPath = flattenLocalMenuMap(fallbackSourceMenuData);
  const sourcePaths = collectMenuPaths(menuData);
  const allowMissingStableMenus = options.allowMissingStableMenus ?? false;
  const hasDashboardSource = hasMenuPathOrChild(sourcePaths, DASHBOARD_HOME_PATH) || hasMenuPathOrChild(sourcePaths, DASHBOARD_GROUP_PATH);
  const hasPersonalCenterSource =
    hasMenuPathOrChild(sourcePaths, PERSONAL_CENTER_GROUP_PATH)
    || PERSONAL_CENTER_CHILD_PATHS.some((path) => hasMenuPathOrChild(sourcePaths, path));
  const hasDataManagementSource =
    hasMenuPathOrChild(sourcePaths, DATA_MANAGEMENT_GROUP_PATH)
    || DATA_MANAGEMENT_DIRECT_CHILD_PATHS.some((path) => hasMenuPathOrChild(sourcePaths, path))
    || DATA_SOURCE_GROUP_PATHS.some((path) => hasMenuPathOrChild(sourcePaths, path));
  const dashboardMenu = allowMissingStableMenus || hasDashboardSource
    ? buildDashboardMenuGroupForLayout(fallbackByPath, accessMap)
    : null;
  const dataManagementMenu = allowMissingStableMenus || hasDataManagementSource
    ? buildDataManagementMenuGroupForLayout(fallbackByPath, accessMap)
    : null;
  const personalCenterMenu = allowMissingStableMenus || hasPersonalCenterSource
    ? buildPersonalCenterMenuGroupForLayout(fallbackByPath, accessMap)
    : null;
  const pathsToRemove = new Set([
    ...(dashboardMenu ? [DASHBOARD_HOME_PATH] : []),
    ...(dataManagementMenu
      ? [
          DATA_MANAGEMENT_GROUP_PATH,
          ...DATA_MANAGEMENT_DIRECT_CHILD_PATHS,
          ...DATA_SOURCE_GROUP_PATHS,
        ]
      : []),
    ...(personalCenterMenu ? [PERSONAL_CENTER_GROUP_PATH, ...PERSONAL_CENTER_CHILD_PATHS] : []),
  ]);
  const visibleMenus = pathsToRemove.size
    ? removeMenuPathsForLayout(menuData as RuntimeMenuDataItem[], pathsToRemove)
    : ([...menuData] as RuntimeMenuDataItem[]);
  const existingPaths = collectMenuPaths(visibleMenus);

  const fallbackMenus = allowMissingStableMenus
    ? STABLE_MAIN_ROUTE_PATHS
      .filter((path) => path !== DASHBOARD_HOME_PATH)
      .filter((path) => path !== DATA_MANAGEMENT_GROUP_PATH)
      .filter((path) => !hasMenuPathOrChild(existingPaths, path))
      .map((path) => {
        const localMenu = fallbackByPath.get(path);
        if (localMenu) {
          return {
            ...localMenu,
            key: MAIN_MENU_KEY_BY_PATH[path] || localMenu.key,
            path: path === USER_CENTER_GROUP_PATH && localMenu.children?.length ? undefined : localMenu.path,
          };
        }

        const meta = routeMetaMap.get(path);
        if (!meta || (meta.access && !accessMap[meta.access])) {
          return null;
        }

        return {
          key: MAIN_MENU_KEY_BY_PATH[path],
          path: meta.path,
          name: resolveBuiltinMessage(meta.name, formatMessage({ id: meta.name, defaultMessage: meta.name })),
          locale: false as const,
          icon: resolveNavigationIcon(meta.icon),
          hideInMenu: meta.hideInMenu,
        };
      })
      .filter(Boolean) as RuntimeMenuDataItem[]
    : [];

  return [
    ...(dashboardMenu ? [dashboardMenu] : []),
    ...(dataManagementMenu ? [dataManagementMenu] : []),
    ...fallbackMenus,
    ...visibleMenus,
    ...(personalCenterMenu ? [personalCenterMenu] : []),
  ].sort((a, b) => {
    const leftPath = resolveStableMainMenuSortPath(a);
    const rightPath = resolveStableMainMenuSortPath(b);
    const leftIndex = STABLE_MAIN_ROUTE_PATHS.indexOf(leftPath);
    const rightIndex = STABLE_MAIN_ROUTE_PATHS.indexOf(rightPath);
    if (leftIndex !== -1 || rightIndex !== -1) {
      return (leftIndex === -1 ? Number.MAX_SAFE_INTEGER : leftIndex) - (rightIndex === -1 ? Number.MAX_SAFE_INTEGER : rightIndex);
    }
    return 0;
  });
};

const removeRedundantParentPathItemsForLayout = (
  items: RuntimeMenuDataItem[],
  ancestorGroupPaths = new Set<string>(),
): RuntimeMenuDataItem[] =>
  items
    .flatMap((item) => {
      const nextAncestorGroupPaths = new Set(ancestorGroupPaths);
      if (item.path && item.children?.length) {
        nextAncestorGroupPaths.add(item.path);
      }

      const children = item.children?.length
        ? removeRedundantParentPathItemsForLayout(item.children, nextAncestorGroupPaths)
        : [];

      if (item.path && ancestorGroupPaths.has(item.path)) {
        return children;
      }

      return {
        ...item,
        children: children.length ? children : undefined,
      };
    })
    .filter((item) => {
      if (!item.path || item.children?.length) {
        return true;
      }
      return !ancestorGroupPaths.has(item.path) && !HIDDEN_MAIN_MENU_LEAF_PATHS.has(item.path);
    });

export const createLayoutConfig: RunTimeLayoutConfig = ({ initialState }) => {
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const brandName = brandingSettings.websiteName;
  const hasBrandLogo = Boolean(brandingSettings.websiteLogoUrl);
  const currentPathname = history.location.pathname;
  const siderMenuMode = resolveSiderMenuMode(currentPathname, initialState);
  const mainMenuTree = removeLegacyCompetitionRootMenus(initialState?.menuTree);
  const access = buildAccess({ currentUser: initialState?.currentUser, availablePlugins: initialState?.availablePlugins });
  const selectedMenuPath =
    siderMenuMode === 'settings'
      ? resolveActiveSettingsNavigationPath(
          currentPathname,
          initialState?.menuTree,
          (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
          initialState?.availablePlugins,
        )
      : resolveSelectedMenuPath(currentPathname, mainMenuTree);
  const isMobile = resolveIsMobileViewport();
  const LAYOUT_HEADER_HEIGHT = resolveResponsiveValue(APP_SPACING.layout.headerHeight, isMobile);
  const LAYOUT_SIDER_WIDTH = resolveResponsiveValue(APP_SPACING.layout.siderWidth, isMobile);

  applyBrandingRuntime(brandingSettings);

  return {
    title: brandName,
    logo: hasBrandLogo ? brandingSettings.websiteLogoUrl : false,
    fixedHeader: false,
    fixSiderbar: true,
    siderWidth: LAYOUT_SIDER_WIDTH,
    layout: 'mix',
    token: {
      header: {
        heightLayoutHeader: LAYOUT_HEADER_HEIGHT,
      },
    },
    ...resolveProLayoutThemeSettings(),
    splitMenus: false,
    breadcrumbRender: (routers = []) => {
      const pathname = history.location.pathname;
      if (pathname.startsWith('/settings')) {
        return [];
      }
      const menuBreadcrumb = buildBreadcrumbItems(mainMenuTree, pathname);
      return menuBreadcrumb.length
        ? menuBreadcrumb
        : (routers as RuntimeMenuDataItem[]).map((item) => {
            const breadcrumbTitle = item.title || item.name || item.path || '';
            return {
              key: item.path || item.name || breadcrumbTitle,
              path: item.path,
              title:
                typeof breadcrumbTitle === 'string'
                  ? resolveBuiltinMessage(
                      breadcrumbTitle,
                      formatMessage({ id: breadcrumbTitle, defaultMessage: breadcrumbTitle }),
                    )
                  : breadcrumbTitle,
            };
          });
    },
    breadcrumbProps: {
      minLength: 1,
    },
    headerTitleRender: (logo) => renderBrandHomeLink({ logo, brandName }),
    menuHeaderRender: false,
    menuFooterRender: false,
    menuExtraRender: false,
    collapsedButtonRender: (_, defaultDom) => <CollapsedButtonWithReturn defaultDom={defaultDom} />,
    menuRender: (_, defaultDom) => defaultDom,
    childrenRender: (dom) => (
      <SessionActivityGuard>
        <ThemeRuntimeBridge />
        {dom}
        <GlobalSensitiveWordGuard />
        <WechatContactBindGuard />
        <GlobalFloatActions />
      </SessionActivityGuard>
    ),
    headerContentRender: () => null,
    rightContentRender: () => <TopActions />,
    actionsRender: () => <TopActions />,
    footerRender: () => renderLayoutFooter(brandingSettings),
    unAccessible: <NoPermission />,
    pageTitleRender: (props, defaultTitle) => (!props?.title ? defaultTitle || brandName : `${props.title} - ${brandName}`),
    selectedKeys: [selectedMenuPath],
    openKeys: siderMenuMode === 'settings' ? undefined : false,
    menuTextRender: (item, defaultDom) =>
      typeof defaultDom === 'string'
        ? resolveBuiltinMessage(
            typeof item.locale === 'string' ? item.locale : item.name,
            defaultDom,
          )
        : defaultDom,
    menu: {
      params: {
        pathname: currentPathname,
        siderMenuMode,
        menuVersion: initialState?.menuVersion ?? 0,
        themeRevision: initialState?.themeRevision ?? 0,
        brandingRevision: initialState?.brandingRevision ?? 0,
      },
    },
    menuDataRender: (menuData) => {
      if (siderMenuMode === 'settings') {
        return buildVisibleSettingsNavigationItems(
          initialState?.menuTree,
          (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
          initialState?.availablePlugins,
        );
      }

      const backendMenus = mainMenuTree;
      const translatedLocalMenus = translateVisibleLocalMenuDataForLayout(initialState, menuData as RuntimeMenuDataItem[]);
      if (!backendMenus.length) {
        return [];
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);

      const composedMenus = backendMenus
        .map((node) => composeMenuItemForLayout(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];

      return removeRedundantParentPathItemsForLayout(
        buildMainMenuDataForLayout(initialState, composedMenus, translatedLocalMenus, { allowMissingStableMenus: false }),
      );
    },
    onPageChange: createLayoutOnPageChange({ initialState }),
  };
};

