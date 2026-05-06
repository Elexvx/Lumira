import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  CheckOutlined,
  CompressOutlined,
  LogoutOutlined,
  LockOutlined,
  GlobalOutlined,
  GithubOutlined,
  MoonOutlined,
  QuestionCircleOutlined,
  ProfileOutlined,
  SwapOutlined,
  SettingOutlined,
  SkinOutlined,
  SunOutlined,
  SyncOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, setLocale, useAccess, useIntl, useLocation } from '@umijs/max';
import { Avatar, Button, Drawer, Dropdown, Form, Input, Space, type MenuProps, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { authService } from '@/services/auth';
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
import { DEFAULT_HOME_PATH } from '@/app.constants';
import type { SecuritySettings } from '@/types/api';
import './TopActions.css';

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

const DEFAULT_ROLE_VALUE = '__default__';

export const TopActions = () => {
  const [loggingOut, setLoggingOut] = useState(false);
  const [switchingLocale, setSwitchingLocale] = useState(false);
  const [switchingRole, setSwitchingRole] = useState(false);
  const [passwordDrawerOpen, setPasswordDrawerOpen] = useState(false);
  const [passwordForm] = Form.useForm<{
    currentPassword?: string;
    newPassword?: string;
    confirmPassword?: string;
  }>();
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
  const securitySettings = initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS;
  const currentUser = initialState?.currentUser;
  const userName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '用户菜单';
  const userAvatarUrl = normalizeUploadUrl(currentUser?.avatarUrl || '');
  const currentLocale = normalizeLocale(currentUser?.locale || undefined);
  const availableRoles = currentUser?.availableRoles || [];
  const simulatedRoleId = currentUser?.simulatedRoleId ?? null;
  const selectedRoleLabel =
    availableRoles.find((item) => item.id === simulatedRoleId)?.roleName ||
    intl.formatMessage({ id: 'nav.user.role.default', defaultMessage: '默认权限' });

  useEffect(() => {
    if (!passwordDrawerOpen) {
      passwordForm.resetFields();
    }
  }, [passwordDrawerOpen, passwordForm]);

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
    () => buildSettingsDropdownItems(initialState?.menuTree, (accessKey) => Boolean((access as Record<string, unknown>)[accessKey])),
    [access, initialState?.menuTree],
  );
  const activeSettingsPath = useMemo(
    () =>
      resolveActiveSettingsNavigationPath(
        location.pathname,
        initialState?.menuTree,
        (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
      ),
    [access, initialState?.menuTree, location.pathname],
  );

  const roleMenuItems = useMemo<NonNullable<MenuProps['items']>>(() => {
    if (!availableRoles.length) {
      return [];
    }

    return [
      {
        key: 'role-switch',
        icon: <SwapOutlined />,
        label: intl.formatMessage({ id: 'nav.user.switchRole', defaultMessage: '切换角色' }),
        children: [
          {
            key: DEFAULT_ROLE_VALUE,
            label: intl.formatMessage({ id: 'nav.user.role.default', defaultMessage: '默认权限' }),
          },
          ...availableRoles.map((role) => ({
            key: String(role.id),
            label: role.roleName,
          })),
        ],
      },
    ];
  }, [availableRoles, intl]);

  const userMenuItems = useMemo<MenuProps['items']>(
    () => [
      {
        key: 'user-header',
        disabled: true,
        label: (
          <div className="saas-user-menu__header-item">
            <div className="saas-user-menu__nickname">{userName}</div>
            <div className="saas-user-menu__role">{selectedRoleLabel}</div>
          </div>
        ),
      },
      { type: 'divider' },
      {
        key: 'profile',
        icon: <ProfileOutlined />,
        label: intl.formatMessage({ id: 'nav.user.profile', defaultMessage: '个人资料' }),
      },
      {
        key: 'password',
        icon: <LockOutlined />,
        label: intl.formatMessage({ id: 'nav.user.changePassword', defaultMessage: '修改密码' }),
      },
      ...(roleMenuItems.length
        ? [
            { type: 'divider' as const },
            ...roleMenuItems,
          ]
        : []),
      { type: 'divider' },
      {
        key: 'logout',
        danger: true,
        icon: <LogoutOutlined />,
        label: intl.formatMessage({ id: 'auth.logout', defaultMessage: '注销' }),
      },
    ],
    [intl, roleMenuItems, selectedRoleLabel, userName],
  );

  const handleUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'profile') {
      handleOpenProfile();
      return;
    }

    if (key === 'password') {
      handleOpenPasswordDrawer();
      return;
    }

    if (key === 'logout') {
      void handleLogout();
      return;
    }

    const nextRoleValue = String(key);
    const availableRoleIds = new Set(availableRoles.map((role) => String(role.id)));
    if (nextRoleValue === DEFAULT_ROLE_VALUE || availableRoleIds.has(nextRoleValue)) {
      void handleSwitchRole(nextRoleValue);
    }
  };

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

  const handleOpenProfile = () => {
    history.push('/user-center/profile');
  };

  const handleOpenPasswordDrawer = () => {
    setPasswordDrawerOpen(true);
  };

  const handleSwitchRole = async (nextRoleValue: string) => {
    const nextRoleId = nextRoleValue === DEFAULT_ROLE_VALUE ? null : Number(nextRoleValue);
    if (nextRoleId === simulatedRoleId) {
      return;
    }

    setSwitchingRole(true);
    try {
      const updatedUser = await authService.simulatedRole(
        { roleId: nextRoleId },
        {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        },
      );
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
              currentTenant: updatedUser.currentTenant || prev.currentTenant || null,
            }
          : prev,
      );
      message.success(
        nextRoleId == null
          ? intl.formatMessage({ id: 'nav.user.role.restoreSuccess', defaultMessage: '已恢复默认权限' })
          : intl.formatMessage({ id: 'nav.user.role.switchSuccess', defaultMessage: '角色已切换' }),
      );
      window.setTimeout(() => {
        window.location.replace(DEFAULT_HOME_PATH);
      }, 200);
    } catch (error) {
      message.error(error instanceof Error ? error.message : intl.formatMessage({ id: 'common.failure', defaultMessage: '操作失败，请稍后重试' }));
    } finally {
      setSwitchingRole(false);
    }
  };

  const handlePasswordFinish = async (values: { currentPassword?: string; newPassword?: string; confirmPassword?: string }) => {
    try {
      await profileService.changePassword(
        {
          currentPassword: values.currentPassword || '',
          newPassword: values.newPassword || '',
          confirmPassword: values.confirmPassword || '',
        },
        {
          autoRedirectOnUnauthorized: false,
          allowUnauthorizedWithoutRedirect: true,
          silent: true,
        },
      );
      message.success(intl.formatMessage({ id: 'nav.user.password.updateSuccess', defaultMessage: '密码已修改' }));
      setPasswordDrawerOpen(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : intl.formatMessage({ id: 'common.failure', defaultMessage: '操作失败，请稍后重试' }));
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
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: userMenuItems,
            selectedKeys: simulatedRoleId == null ? [DEFAULT_ROLE_VALUE] : [String(simulatedRoleId)],
            onClick: handleUserMenuClick,
          }}
        >
          <Button
            type="text"
            className="saas-user-menu-trigger"
            disabled={loggingOut || switchingRole}
            icon={
              <Avatar
                size="small"
                src={userAvatarUrl || undefined}
                icon={<UserOutlined />}
              />
            }
          >
            {!isMobile ? <span className="saas-user-menu-trigger__name">{userName}</span> : null}
          </Button>
        </Dropdown>
      </Space>
      <Drawer
        title={intl.formatMessage({ id: 'nav.user.changePassword', defaultMessage: '修改密码' })}
        open={passwordDrawerOpen}
        width={isMobile ? '100%' : 420}
        destroyOnHidden
        onClose={() => setPasswordDrawerOpen(false)}
        footer={
          <Space className="saas-user-password__footer">
            <Button onClick={() => setPasswordDrawerOpen(false)}>
              {intl.formatMessage({ id: 'common.cancel', defaultMessage: '取消' })}
            </Button>
            <Button type="primary" onClick={() => void passwordForm.submit()}>
              {intl.formatMessage({ id: 'common.save', defaultMessage: '保存' })}
            </Button>
          </Space>
        }
      >
        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={handlePasswordFinish}
          initialValues={{ currentPassword: '', newPassword: '', confirmPassword: '' }}
        >
          <Form.Item
            name="currentPassword"
            label={intl.formatMessage({ id: 'nav.user.password.current', defaultMessage: '当前密码' })}
            rules={[{ required: true, message: intl.formatMessage({ id: 'nav.user.password.enterCurrent', defaultMessage: '请输入当前密码' }) }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label={intl.formatMessage({ id: 'nav.user.password.new', defaultMessage: '新密码' })}
            extra={buildPasswordPolicyHint(securitySettings, intl)}
            rules={[
              { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterNew', defaultMessage: '请输入新密码' }) },
              {
                min: Math.max(1, Number(securitySettings.passwordMinLength || 0)),
                message: intl.formatMessage(
                  { id: 'nav.user.password.minLength', defaultMessage: '密码长度至少为 {length} 位' },
                  { length: securitySettings.passwordMinLength || 1 },
                ),
              },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label={intl.formatMessage({ id: 'nav.user.password.confirm', defaultMessage: '确认新密码' })}
            dependencies={['newPassword']}
            rules={[
              { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterConfirm', defaultMessage: '请再次输入新密码' }) },
              ({ getFieldValue }) => ({
                validator: async (_, value) => {
                  if (!value || value === getFieldValue('newPassword')) {
                    return;
                  }
                  throw new Error(intl.formatMessage({ id: 'nav.user.password.confirmMismatch', defaultMessage: '两次输入的新密码不一致' }));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Drawer>
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

const buildPasswordPolicyHint = (securitySettings: SecuritySettings, intl: ReturnType<typeof useIntl>) => {
  const rules: string[] = [];
  const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
  if (minLength > 0) {
    rules.push(
      intl.formatMessage(
        { id: 'nav.user.password.policy.minLength', defaultMessage: '至少 {length} 位' },
        { length: minLength },
      ),
    );
  }
  if (securitySettings.passwordRequireUppercase) {
    rules.push(intl.formatMessage({ id: 'nav.user.password.policy.uppercase', defaultMessage: '需包含大写字母' }));
  }
  if (securitySettings.passwordRequireLowercase) {
    rules.push(intl.formatMessage({ id: 'nav.user.password.policy.lowercase', defaultMessage: '需包含小写字母' }));
  }
  if (securitySettings.passwordRequireSpecialCharacter) {
    rules.push(intl.formatMessage({ id: 'nav.user.password.policy.special', defaultMessage: '需包含特殊字符' }));
  }
  if (!rules.length) {
    return '';
  }
  return `${intl.formatMessage({ id: 'nav.user.password.policy.title', defaultMessage: '密码规则：' })}${rules.join('，')}`;
};
