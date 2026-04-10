import { useMemo, useState, type ReactNode } from 'react';
import {
  BellOutlined,
  CheckOutlined,
  CompressOutlined,
  GithubOutlined,
  MoonOutlined,
  QuestionCircleOutlined,
  SkinOutlined,
  SunOutlined,
  SyncOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown, Select, Space, type MenuProps } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { performLogout } from '@/auth/session';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { switchTenantAction } from '@/tenant/actions';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import type { ThemePreference } from '@/theme/settings';

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
  const [switching, setSwitching] = useState(false);
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();
  const { themePreference, resolvedColorMode, setThemePreference } = useThemePreference();
  const brandingSettings = useMemo(
    () => normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
    [initialState?.brandingSettings],
  );

  const userName =
    initialState?.currentUser?.nickname ||
    initialState?.currentUser?.realName ||
    initialState?.currentUser?.username ||
    '用户菜单';

  const tenantOptions = useMemo(
    () =>
      (initialState?.myTenants ?? []).map((tenant) => ({
        label: tenant.tenantShortName || tenant.tenantName,
        value: tenant.tenantId,
      })),
    [initialState?.myTenants],
  );

  const themeMenuItems: MenuProps['items'] = useMemo(
    () =>
      THEME_OPTIONS.map((item) => ({
        key: item.key,
        label: (
          <div className="saas-theme-menu__item">
            <Space size={10}>
              <span className="saas-theme-menu__item-icon">{item.icon}</span>
              <span>{item.label}</span>
            </Space>
            {themePreference === item.key ? <CheckOutlined className="saas-theme-menu__item-check" /> : null}
          </div>
        ),
      })),
    [themePreference],
  );

  const githubLink = resolveExternalLink(brandingSettings.githubLinkUrl);
  const helpLink = resolveExternalLink(brandingSettings.helpLinkUrl);

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

  const handleSwitchTenant = async (nextTenantId: number) => {
    const currentTenantId = initialState?.currentTenant?.tenantId;
    if (currentTenantId === nextTenantId) {
      return;
    }
    setSwitching(true);
    try {
      const response = await switchTenantAction(nextTenantId);
      const [currentUser, menuTree, availablePlugins] = await Promise.all([
        authService.currentUser({ autoRedirectOnUnauthorized: false }),
        pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
      ]);
      setInitialState((prev: AppInitialState | undefined) => ({
        ...prev,
        currentUser,
        currentTenant: response.currentTenant,
        myTenants: prev?.myTenants ?? [],
        menuTree,
        menuVersion: (prev?.menuVersion ?? 0) + 1,
        availablePlugins,
        securitySettings: prev?.securitySettings || initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS,
        brandingSettings: prev?.brandingSettings || initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      }));
    } finally {
      setSwitching(false);
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
      key: 'logout',
      label: loggingOut ? '退出中...' : '退出登录',
      disabled: loggingOut,
    },
  ];

  return (
    <Space size="small" align="center">
      {!isMobile ? (
        <Select
          size="small"
          variant="borderless"
          style={{ width: 160 }}
          popupMatchSelectWidth={false}
          placeholder="选择租户"
          loading={switching}
          disabled={switching || tenantOptions.length <= 1}
          value={initialState?.currentTenant?.tenantId}
          onChange={handleSwitchTenant}
          options={tenantOptions}
          notFoundContent="暂无可用租户"
        />
      ) : null}
      <Space size={isMobile ? 4 : 8} wrap={false}>
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
            aria-label={`主题切换，当前${THEME_OPTIONS.find((item) => item.key === themePreference)?.label || '主题'}`}
          />
        </Dropdown>
        <Button
          type="text"
          icon={<QuestionCircleOutlined />}
          aria-label={helpLink ? '帮助中心' : '帮助中心（未配置链接）'}
          disabled={!helpLink}
          onClick={() => openExternalLink(helpLink)}
        />
        <Button
          type="text"
          icon={<GithubOutlined />}
          aria-label={githubLink ? 'GitHub 链接' : 'GitHub 链接（未配置）'}
          disabled={!githubLink}
          onClick={() => openExternalLink(githubLink)}
        />
        <Button type="text" icon={<BellOutlined />} aria-label="通知中心" />
        <Dropdown
          menu={{
            items: userMenuItems,
            onClick: ({ key }) => {
              if (key === 'logout' && !loggingOut) {
                handleLogout();
              }
            },
          }}
        >
          <Button type="text" icon={<Avatar size="small" icon={<UserOutlined />} />}>
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
