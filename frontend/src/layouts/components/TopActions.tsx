import { useMemo, useState, type ReactNode } from 'react';
import {
  CheckOutlined,
  CompressOutlined,
  GlobalOutlined,
  GithubOutlined,
  MoonOutlined,
  QuestionCircleOutlined,
  SettingOutlined,
  SkinOutlined,
  SunOutlined,
  SyncOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, setLocale, useAccess, useIntl, useLocation } from '@umijs/max';
import { Avatar, Button, Dropdown, Space, type MenuProps, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { performLogout } from '@/auth/session';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';
import { profileService } from '@/services/profile';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { ThemePreference } from '@/theme/settings';
import { MessageCenterDrawer } from '@/layouts/components/MessageCenterDrawer';
import { normalizeLocale } from '@/i18n/locale';
import {
  buildSettingsDropdownItems,
  isSettingsShellPath,
  resolveActiveSettingsNavigationPath,
} from '@/navigation/settingsNavigation';

const THEME_OPTIONS: Array<{
  key: ThemePreference;
  label: string;
  icon: ReactNode;
}> = [
  {
    key: 'system',
    label: '跟随系统',
    icon: <SyncOutlined />,
  },
  {
    key: 'light',
    label: '浅色主题',
    icon: <SunOutlined />,
  },
  {
    key: 'dark',
    label: '暗黑主题',
    icon: <MoonOutlined />,
  },
  {
    key: 'compact',
    label: '紧凑主题',
    icon: <CompressOutlined />,
  },
];

export const TopActions = () => {
  const [loggingOut, setLoggingOut] = useState(false);
  const [switchingLocale, setSwitchingLocale] = useState(false);
  const { initialState, setInitialState } = useInitialStateModel();
  const access = useAccess();
  const location = useLocation();
  const { isMobile } = useResponsive();
  const { themePreference, resolvedColorMode, setThemePreference } = useThemePreference();
  const intl = useIntl();
  const brandingSettings = useMemo(
    () => normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
    [initialState?.brandingSettings],
  );

  const userName =
    initialState?.currentUser?.nickname ||
    initialState?.currentUser?.realName ||
    initialState?.currentUser?.username ||
    '用户菜单';
  const userAvatarUrl = normalizeUploadUrl(initialState?.currentUser?.avatarUrl || '');
  const currentLocale = normalizeLocale(initialState?.currentUser?.locale || undefined);

  const themeMenuItems: MenuProps['items'] = useMemo(
    () =>
      THEME_OPTIONS.map((item) => ({
        key: item.key,
        label: (
          <div className="saas-theme-menu__item">
            <Space size={10}>
              <span className="saas-theme-menu__item-icon">{item.icon}</span>
              <span>{intl.formatMessage({ id: `theme.${item.key}`, defaultMessage: item.label })}</span>
            </Space>
            {themePreference === item.key ? <CheckOutlined className="saas-theme-menu__item-check" /> : null}
          </div>
        ),
      })),
    [intl, themePreference],
  );

  const localeMenuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'zh-CN',
        label: intl.formatMessage({ id: 'app.locale.zh-CN', defaultMessage: '中文' }),
        icon: currentLocale === 'zh-CN' ? <CheckOutlined /> : undefined,
      },
      {
        key: 'en-US',
        label: intl.formatMessage({ id: 'app.locale.en-US', defaultMessage: 'English' }),
        icon: currentLocale === 'en-US' ? <CheckOutlined /> : undefined,
      },
    ],
    [currentLocale, intl],
  );

  const githubLink = resolveExternalLink(brandingSettings.githubLinkUrl);
  const helpLink = resolveExternalLink(brandingSettings.helpLinkUrl);
  const canVisitSystemSettings = Boolean((access as Record<string, unknown>).canVisitSystemSettings);
  const settingsMenuItems = useMemo(
    () => buildSettingsDropdownItems((accessKey) => Boolean((access as Record<string, unknown>)[accessKey])),
    [access],
  );
  const activeSettingsPath = useMemo(
    () => resolveActiveSettingsNavigationPath(location.pathname, (accessKey) => Boolean((access as Record<string, unknown>)[accessKey])),
    [access, location.pathname],
  );

  const themeButtonIcon = useMemo(() => {
    if (themePreference === 'compact') {
      return <CompressOutlined />;
    }

    if (resolvedColorMode === 'dark') {
      return <MoonOutlined />;
    }

    const matched = THEME_OPTIONS.find((item) => item.key === themePreference);
    return matched?.icon || <SkinOutlined />;
  }, [resolvedColorMode, themePreference]);

  const handleSwitchLocale = async (nextLocale: string) => {
    const normalizedNextLocale = normalizeLocale(nextLocale);
    if (normalizedNextLocale === currentLocale) {
      return;
    }

    setSwitchingLocale(true);
    try {
      await profileService.updateLocale(
        { locale: normalizedNextLocale },
        {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
        },
      );
      setLocale(normalizedNextLocale);
    } catch (error) {
      message.error(error instanceof Error ? error.message : intl.formatMessage({ id: 'common.failure', defaultMessage: '操作失败，请稍后重试' }));
    } finally {
      setSwitchingLocale(false);
    }
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await performLogout();
      setInitialState((prev: AppInitialState | undefined) => ({
        ...prev,
        currentUser: undefined,
        currentTenant: null,
        myTenants: [],
        menuTree: [],
        menuVersion: 0,
        availablePlugins: [],
        securitySettings: prev?.securitySettings || initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS,
        brandingSettings: prev?.brandingSettings || initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      }));
    } finally {
      setLoggingOut(false);
    }
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      label: intl.formatMessage({ id: 'nav.user.profile', defaultMessage: '个人中心' }),
      disabled: loggingOut,
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      label: loggingOut
        ? intl.formatMessage({ id: 'common.loading', defaultMessage: '退出中...' })
        : intl.formatMessage({ id: 'auth.logout', defaultMessage: '退出登录' }),
      disabled: loggingOut,
    },
  ];

  return (
    <Space size="small" align="center">
      <Space size={isMobile ? 4 : 8} wrap={false}>
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: localeMenuItems,
            selectedKeys: [currentLocale],
            onClick: ({ key }) => void handleSwitchLocale(key),
          }}
        >
          <Button
            type="text"
            icon={<GlobalOutlined />}
            loading={switchingLocale}
            aria-label={intl.formatMessage({
              id: 'app.locale.switch',
              defaultMessage: '语言切换',
            })}
          />
        </Dropdown>
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: themeMenuItems,
            selectedKeys: [themePreference],
            onClick: ({ key }) => setThemePreference(key as ThemePreference),
          }}
        >
          <Button
            type="text"
            icon={themeButtonIcon}
            aria-label={intl.formatMessage(
              { id: 'theme.switch', defaultMessage: '主题切换，当前{theme}' },
              { theme: intl.formatMessage({ id: `theme.${themePreference}`, defaultMessage: THEME_OPTIONS.find((item) => item.key === themePreference)?.label || '主题' }) },
            )}
          />
        </Dropdown>
        <Button
          type="text"
          icon={<QuestionCircleOutlined />}
          aria-label={helpLink ? intl.formatMessage({ id: 'help.center', defaultMessage: '帮助中心' }) : intl.formatMessage({ id: 'help.center.unconfigured', defaultMessage: '帮助中心（未配置链接）' })}
          disabled={!helpLink}
          onClick={() => openExternalLink(helpLink)}
        />
        <Button
          type="text"
          icon={<GithubOutlined />}
          aria-label={githubLink ? intl.formatMessage({ id: 'github.link', defaultMessage: 'GitHub 链接' }) : intl.formatMessage({ id: 'github.link.unconfigured', defaultMessage: 'GitHub 链接（未配置）' })}
          disabled={!githubLink}
          onClick={() => openExternalLink(githubLink)}
        />
        {canVisitSystemSettings && settingsMenuItems?.length ? (
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: settingsMenuItems,
              selectedKeys: isSettingsShellPath(location.pathname) ? [activeSettingsPath] : [],
              onClick: ({ key }) => history.push(String(key)),
            }}
          >
            <Button
              type="text"
              icon={<SettingOutlined />}
              aria-label={intl.formatMessage({ id: 'settings.menu', defaultMessage: 'Settings' })}
            />
          </Dropdown>
        ) : null}
        <MessageCenterDrawer />
        <Dropdown
          menu={{
            items: userMenuItems,
            onClick: ({ key }) => {
              if (key === 'profile' && !loggingOut) {
                history.push('/user-center/profile');
                return;
              }
              if (key === 'logout' && !loggingOut) {
                handleLogout();
              }
            },
          }}
        >
          <Button
            type="text"
            icon={
              <Avatar
                size="small"
                src={userAvatarUrl || undefined}
                icon={<UserOutlined />}
              />
            }
          >
            {!isMobile ? userName : null}
          </Button>
        </Dropdown>
      </Space>
    </Space>
  );
};

const resolveExternalLink = (value?: string | null) => {
  const trimmed = value?.trim() || '';
  if (!trimmed) {
    return '';
  }
  if (/^(https?:|mailto:|tel:)/i.test(trimmed) || trimmed.startsWith('/') || trimmed.startsWith('#')) {
    return trimmed;
  }
  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(trimmed)) {
    return '';
  }
  return `https://${trimmed}`;
};

const openExternalLink = (url?: string) => {
  if (!url || typeof window === 'undefined') {
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
};
