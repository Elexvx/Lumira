import type { RunTimeLayoutConfig } from '@umijs/max';
import type { ProSettings } from '@ant-design/pro-components';
import { formatMessage, history, useIntl, useLocation } from '@umijs/max';
import type { ReactNode } from 'react';
import { ArrowLeftOutlined, MoreOutlined, QrcodeOutlined, ReloadOutlined, VerticalAlignTopOutlined } from '@ant-design/icons';
import { Button, FloatButton, Popover, Tooltip, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useQuery } from '@tanstack/react-query';
import { createPortal } from 'react-dom';
import { DEFAULT_HOME_PATH, LOGIN_PATH, isPublicPath } from '@/app.constants';
import buildAccess from '@/access';
import { DEFAULT_FLOATING_WINDOW_SETTINGS, normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import { isLoggedIn } from '@/auth/sessionLifecycle';
import { clearSessionActivity, getSessionActivityStorageKey, getStoredSessionActivityAt, persistSessionActivity } from '@/auth/activity';
import { buildStorageKey } from '@/cache/storage';
import { TOKEN_STORAGE_KEY, tokenManager } from '@/auth/token';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { getStoredSecuritySettings } from '@/auth/securitySettingsStorage';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { performLogout, tryRefreshToken } from '@/auth/sessionLifecycle';
import { request } from '@/services/common/request';
import { resolveAuthorizedLoginRedirectTarget, resolveRouteAccessStatus } from '@/auth/loginRedirect';
import { TopActions } from '@/layouts/components/TopActions';
import NoPermission from '@/pages/exception/NoPermission';
import { applyFavicon, buildCopyrightText, normalizeBrandingSettings, DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import type { AppInitialState, RuntimeMenuDataItem } from '@/app.types';
import type { BrandingSettings, FloatingWindowSettings, MenuNode, SecuritySettings } from '@/types/api';
import { resolveBuiltinMessage } from '@/i18n/messages';
import { buildVisibleSettingsNavigationItems, resolveActiveSettingsNavigationPath } from '@/navigation/settingsNavigationRuntime';
import { resolveNavigationIcon } from '@/navigation/settingsNavigationIcon';
import { isMainMenuHiddenMonitoringPath, isMainMenuHiddenSettingPath, isPluginSettingsShellPath, isSettingsShellPath } from '@/navigation/settingsNavigationRuntime';
import { backendRouteMeta, realPageRouteMetaMap, resolveCanonicalRoutePath } from '@/routes/meta';
import { API_OPTS } from '@/utils/errorMessage';
import { useResponsive } from '@/hooks/useResponsive';
import { useCallback, useEffect, useRef } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import type { ThemePreference } from '@/theme/settings';
import { resolveThemeRuntimeSnapshot } from '@/theme/runtime';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import GlobalSensitiveWordGuard from '@/components/GlobalSensitiveWordGuard';
import './layouts/components/GlobalFloatActions.css';
import { buildBreadcrumbItems } from '@/features/management/ManagementPage';

const routeMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
const realPagePathSet = new Set(realPageRouteMetaMap.keys());
const resolveIsMobileViewport = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia('(max-width: 767px)').matches;
const STABLE_MAIN_ROUTE_PATHS = ['/dashboard/home', '/ai'];
const DASHBOARD_GROUP_PATH = '/dashboard';
const DASHBOARD_HOME_PATH = '/dashboard/home';
const PERSONAL_CENTER_GROUP_PATH = '/user-center/personal-center';
const PERSONAL_CENTER_CHILD_PATHS = ['/user-center/personal-center/profile', '/user-center/files'];
const HIDDEN_MAIN_MENU_LEAF_PATHS = new Set(['/user-center/personal-center']);
const STORAGE_ACTIVITY_KEY = getSessionActivityStorageKey();
const STORAGE_TOKEN_KEY = buildStorageKey(TOKEN_STORAGE_KEY);
const MOUSE_MOVE_THROTTLE_MS = 1000;
const KEEPALIVE_THROTTLE_MS = 60_000;
const TOKEN_REFRESH_SKEW_MS = 60_000;
const KEEPALIVE_ENDPOINTS = {
  v2: '/v2/auth/session/keepalive',
  v1: '/v1/auth/session/keepalive',
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

    const refreshed = await tryRefreshToken();
    if (!refreshed) {
      forceLogout('token_expired');
      return false;
    }
    scheduleTokenExpirationRef.current();
    scheduleTimeout(lastActivityRef.current);
    return true;
  }, [forceLogout, scheduleTimeout]);

  const scheduleTokenExpiration = useCallback(() => {
    clearTokenExpireTimer();
    const tokenState = tokenManager.getTokenState();
    if (!tokenState) {
      return;
    }

    const remainingMs = tokenState.expiresAt - Date.now();
    const refreshDelayMs = Math.max(0, remainingMs - TOKEN_REFRESH_SKEW_MS);

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

  return {
    clearTimer,
    clearTokenExpireTimer,
    forceLogout,
    scheduleTimeout,
    scheduleTokenExpiration,
    setLastActivityAt,
    getLastActivityAt,
    resetLogoutGuard,
  };
};

const useSessionActivityController = ({ securitySettings }: { securitySettings: SecuritySettings }) => {
  const timers = useSessionActivityTimers({ securitySettings });
  const lastBroadcastRef = useRef(0);
  const lastKeepaliveRef = useRef(0);
  const keepaliveInFlightRef = useRef(false);
  const lastActivityRef = useRef<number>(Date.now());

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
      lastActivityRef.current = now;
      persistSessionActivity(now);
      timers.scheduleTimeout(now);
      void pingSession();
    },
    [pingSession, timers],
  );

  const applyExternalActivityAt = useCallback(
    (activityAt: number) => {
      lastActivityRef.current = activityAt;
      timers.scheduleTimeout(activityAt);
    },
    [timers],
  );

  const primeStoredActivity = useCallback(() => {
    const storedActivityAt = getStoredSessionActivityAt() || Date.now();
    if (!getStoredSessionActivityAt()) {
      persistSessionActivity(storedActivityAt);
    }
    lastActivityRef.current = storedActivityAt;
    return storedActivityAt;
  }, []);

  const getLastActivityAt = useCallback(() => lastActivityRef.current, []);

  return {
    ...timers,
    recordActivity,
    applyExternalActivityAt,
    primeStoredActivity,
    getLastActivityAt,
  };
};

const resolveSiderMenuMode = (pathname: string, initialState: AppInitialState | undefined) =>
  isSettingsShellPath(pathname) || isPluginSettingsShellPath(pathname, initialState?.availablePlugins) ? 'settings' : 'main';
const isPluginRuntimePath = (path?: string) => Boolean(path && /^\/plugins\/[^/]+$/.test(path));

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

      if (event.key === STORAGE_TOKEN_KEY) {
        if (tokenManager.hasToken()) {
          controller.resetLogoutGuard();
          controller.scheduleTokenExpiration();
          controller.scheduleTimeout(controller.getLastActivityAt());
        } else {
          controller.forceLogout();
        }
      }
    };
    window.addEventListener('storage', handleStorage);

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
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
    // Rebuild activity bindings when auth/session context changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controller, securitySettings.idleTimeoutSeconds, initialState?.currentUser?.sessionId]);

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

const renderBeianLink = (text: string, href: string) => (
  <a className="saas-layout-footer__link" href={href} target="_blank" rel="noreferrer">
    {text}
  </a>
);

const renderLayoutFooter = (brandingSettings: BrandingSettings) => {
  const copyrightText = brandingSettings.footerCopyright || buildCopyrightText(brandingSettings);

  if (!brandingSettings.footerIcp && !brandingSettings.footerPoliceBeian && !copyrightText) {
    return null;
  }

  return (
    <div className="saas-layout-footer">
      {brandingSettings.footerIcp ? <div className="saas-layout-footer__line">{renderBeianLink(brandingSettings.footerIcp, ICP_QUERY_URL)}</div> : null}
      {brandingSettings.footerPoliceBeian ? <div className="saas-layout-footer__line">{renderBeianLink(brandingSettings.footerPoliceBeian, POLICE_BEIAN_QUERY_URL)}</div> : null}
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

const resolveSelectedMenuPath = (pathname: string, menuTree: MenuNode[] | undefined) => {
  const normalizedPathname = resolveCanonicalRoutePath(pathname);
  const visiblePaths = Array.from(collectMenuNodePaths(menuTree));

  if (!visiblePaths.length) {
    return normalizedPathname;
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
    return { ...localMenu, hideInMenu: false };
  }

  return {
    path: meta.path,
    name: resolveBuiltinMessage(meta.name, formatMessage({ id: meta.name, defaultMessage: meta.name })),
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
    path: PERSONAL_CENTER_GROUP_PATH,
    name: resolveBuiltinMessage(
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

  const normalizedPath = resolveCanonicalRoutePath(backendNode.path || '');
  const localMeta = localByPath.get(normalizedPath);
  const mergedMeta = routeMetaMap.get(normalizedPath);
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
  const menuLabelId = mergedMeta?.name || backendNode.name || backendNode.menuCode;
  const isRedirectGroup = children.length > 0 && Boolean(backendNode.component?.startsWith('redirect:'));

  return {
    ...localItemMeta,
    path: isRedirectGroup ? undefined : normalizedPath || localMeta?.path,
    name: resolveBuiltinMessage(menuLabelId, formatMessage({ id: menuLabelId, defaultMessage: backendNode.name })),
    locale: false as const,
    icon,
    hideInMenu: localMeta?.hideInMenu || mergedMeta?.hideInMenu,
    children: children.length ? children : undefined,
  };
};

const buildMainMenuDataForLayout = (
  initialState: AppInitialState | undefined,
  menuData: RuntimeMenuDataItem[],
  fallbackSourceMenuData: RuntimeMenuDataItem[] = menuData,
) => {
  const access = buildAccess({ currentUser: initialState?.currentUser, availablePlugins: initialState?.availablePlugins });
  const accessMap = access as Record<string, unknown>;
  const fallbackByPath = flattenLocalMenuMap(fallbackSourceMenuData);
  const dashboardMenu = buildDashboardMenuGroupForLayout(fallbackByPath, accessMap);
  const personalCenterMenu = buildPersonalCenterMenuGroupForLayout(fallbackByPath, accessMap);
  const pathsToRemove = new Set([
    ...(dashboardMenu ? [DASHBOARD_HOME_PATH] : []),
    ...(personalCenterMenu ? [PERSONAL_CENTER_GROUP_PATH, ...PERSONAL_CENTER_CHILD_PATHS] : []),
  ]);
  const visibleMenus = pathsToRemove.size
    ? removeMenuPathsForLayout(menuData as RuntimeMenuDataItem[], pathsToRemove)
    : ([...menuData] as RuntimeMenuDataItem[]);
  const existingPaths = collectMenuPaths(visibleMenus);

  const fallbackMenus = STABLE_MAIN_ROUTE_PATHS
    .filter((path) => path !== DASHBOARD_HOME_PATH)
    .filter((path) => !hasMenuPathOrChild(existingPaths, path))
    .map((path) => {
      const localMenu = fallbackByPath.get(path);
      if (localMenu) {
        return localMenu;
      }

      const meta = routeMetaMap.get(path);
      if (!meta || (meta.access && !accessMap[meta.access])) {
        return null;
      }

      return {
        path: meta.path,
        name: resolveBuiltinMessage(meta.name, formatMessage({ id: meta.name, defaultMessage: meta.name })),
        locale: false as const,
        icon: resolveNavigationIcon(meta.icon),
        hideInMenu: meta.hideInMenu,
      };
    })
    .filter(Boolean) as RuntimeMenuDataItem[];

  return [...(dashboardMenu ? [dashboardMenu] : []), ...fallbackMenus, ...visibleMenus, ...(personalCenterMenu ? [personalCenterMenu] : [])].sort((a, b) => {
    const leftPath = a.path === DASHBOARD_GROUP_PATH ? DASHBOARD_HOME_PATH : a.path || '';
    const rightPath = b.path === DASHBOARD_GROUP_PATH ? DASHBOARD_HOME_PATH : b.path || '';
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
  const access = buildAccess({ currentUser: initialState?.currentUser, availablePlugins: initialState?.availablePlugins });
  const selectedMenuPath =
    siderMenuMode === 'settings'
      ? resolveActiveSettingsNavigationPath(
          currentPathname,
          initialState?.menuTree,
          (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
          initialState?.availablePlugins,
        )
      : resolveSelectedMenuPath(currentPathname, initialState?.menuTree);
  const isMobile = resolveIsMobileViewport();
  const LAYOUT_HEADER_HEIGHT = resolveResponsiveValue(APP_SPACING.layout.headerHeight, isMobile);
  const LAYOUT_SIDER_WIDTH = resolveResponsiveValue(APP_SPACING.layout.siderWidth, isMobile);

  applyFavicon(brandingSettings.websiteFaviconUrl);

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
      const menuBreadcrumb = buildBreadcrumbItems(initialState?.menuTree, pathname);
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

      const backendMenus: MenuNode[] = initialState?.menuTree || [];
      const translatedLocalMenus = translateVisibleLocalMenuDataForLayout(initialState, menuData as RuntimeMenuDataItem[]);
      if (!backendMenus.length) {
        return buildMainMenuDataForLayout(initialState, translatedLocalMenus, translatedLocalMenus);
      }

      const localByPath = flattenLocalMenuMap(menuData as RuntimeMenuDataItem[]);

      const composedMenus = backendMenus
        .map((node) => composeMenuItemForLayout(node, localByPath))
        .filter(Boolean) as RuntimeMenuDataItem[];

      return removeRedundantParentPathItemsForLayout(buildMainMenuDataForLayout(initialState, composedMenus, translatedLocalMenus));
    },
    onPageChange: createLayoutOnPageChange({ initialState }),
  };
};
