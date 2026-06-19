import { Avatar, Button, Drawer, Dropdown, Form, Input, Space, Tag, type MenuProps } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { getLocale, history, setLocale, useIntl } from '@umijs/max';
import { useAccess, useLocation } from '@umijs/max';
import { useEffect, useMemo, useState } from 'react';
import { STANDARD_DRAWER_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { DEFAULT_HOME_PATH } from '@/app.constants';
import { buildLoggedOutInitialState } from '@/auth/clientRuntimeState';
import { performLogout } from '@/auth/sessionLifecycle';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeLocale } from '@/i18n/locale';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { request } from '@/services/common/request';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import {
  buildSettingsDropdownItems,
  isPluginSettingsShellPath,
  isSettingsShellPath,
  resolveActiveSettingsNavigationPath,
} from '@/navigation/settingsNavigationRuntime';
import { useThemePreference } from '@/theme/ThemePreferenceProvider';
import type { ThemePreference } from '@/theme/settings';
import type { CurrentUser, SecuritySettings } from '@/types/api';
import { showErrorMessage } from '@/utils/errorMessage';
import { MessageCenterDrawer } from '@/layouts/components/MessageCenterDrawer';
import { useConfirmableDrawerClose } from '@/features/management/drawerCloseConfirm';
import './TopActions.css';
import {
  CheckOutlined,
  CompressOutlined,
  GithubOutlined,
  GlobalOutlined,
  LockOutlined,
  LogoutOutlined,
  MoonOutlined,
  ProfileOutlined,
  QuestionCircleOutlined,
  SkinOutlined,
  SettingOutlined,
  SunOutlined,
  SwapOutlined,
  SyncOutlined,
  UserOutlined,
} from '@ant-design/icons';

type PasswordFormValues = {
  currentPassword?: string;
  newPassword?: string;
  confirmPassword?: string;
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

const TopActionsPasswordDrawer = ({
  open,
  isMobile,
  form,
  securitySettings,
  onClose,
  onFinish,
}: {
  open: boolean;
  isMobile: boolean;
  form: ReturnType<typeof Form.useForm<PasswordFormValues>>[0];
  securitySettings: SecuritySettings;
  onClose: () => void;
  onFinish: (values: PasswordFormValues) => Promise<void> | void;
}) => {
  const intl = useIntl();
  const passwordPolicyHint = (() => {
    const parts: string[] = [];
    const minLength = Math.max(1, Number(securitySettings.passwordMinLength || 0));
    parts.push(intl.formatMessage({ id: 'nav.user.password.minLength', defaultMessage: 'At least {length} characters' }, { length: minLength }));

    if (securitySettings.passwordRequireUppercase) {
      parts.push(intl.formatMessage({ id: 'nav.user.password.requireUppercase', defaultMessage: 'Must include uppercase letters' }));
    }

    if (securitySettings.passwordRequireLowercase) {
      parts.push(intl.formatMessage({ id: 'nav.user.password.requireLowercase', defaultMessage: 'Must include lowercase letters' }));
    }

    if (securitySettings.passwordRequireSpecialCharacter) {
      parts.push(intl.formatMessage({ id: 'nav.user.password.requireSpecial', defaultMessage: 'Must include special characters' }));
    }

    return parts.join('，');
  })();

  useEffect(() => {
    if (!open) {
      form.resetFields();
    }
  }, [form, open]);
  const handleDrawerClose = useConfirmableDrawerClose(onClose);

  return (
    <Drawer
      title={intl.formatMessage({ id: 'nav.user.changePassword', defaultMessage: 'Change password' })}
      open={open}
      width={resolveResponsiveValue(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT, isMobile)}
      destroyOnHidden
      onClose={handleDrawerClose}
      footer={
        <Space className="saas-user-password__footer">
          <Button onClick={onClose}>{intl.formatMessage({ id: 'common.cancel', defaultMessage: 'Cancel' })}</Button>
          <Button type="primary" onClick={() => void form.submit()}>
            {intl.formatMessage({ id: 'common.save', defaultMessage: 'Save' })}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" onFinish={onFinish} initialValues={{ currentPassword: '', newPassword: '', confirmPassword: '' }}>
        <Form.Item
          name="currentPassword"
          label={intl.formatMessage({ id: 'nav.user.password.current', defaultMessage: 'Current password' })}
          rules={[{ required: true, message: intl.formatMessage({ id: 'nav.user.password.enterCurrent', defaultMessage: 'Please enter your current password' }) }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label={intl.formatMessage({ id: 'nav.user.password.new', defaultMessage: 'New password' })}
          extra={passwordPolicyHint}
          rules={[
            { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterNew', defaultMessage: 'Please enter a new password' }) },
            {
              min: Math.max(1, Number(securitySettings.passwordMinLength || 0)),
              message: intl.formatMessage({ id: 'nav.user.password.minLength', defaultMessage: 'Password must be at least {length} characters long' }, { length: securitySettings.passwordMinLength || 1 }),
            },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label={intl.formatMessage({ id: 'nav.user.password.confirm', defaultMessage: 'Confirm new password' })}
          dependencies={['newPassword']}
          rules={[
            { required: true, message: intl.formatMessage({ id: 'nav.user.password.enterConfirm', defaultMessage: 'Please re-enter the new password' }) },
            ({ getFieldValue }) => ({
              validator: async (_, value) => {
                if (!value || value === getFieldValue('newPassword')) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error(intl.formatMessage({ id: 'nav.user.password.confirmMismatch', defaultMessage: 'The two passwords do not match' })));
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export const TopActions = () => {
  const intl = useIntl();
  const { initialState, setInitialState } = useInitialStateModel();
  const access = useAccess();
  const location = useLocation();
  const { isMobile } = useResponsive();
  const { themePreference, resolvedColorMode, setThemePreference } = useThemePreference();
  const currentUser = initialState?.currentUser;
  const brandingSettings = (initialState?.brandingSettings || {}) as {
    githubLinkEnabled?: boolean;
    githubLinkUrl?: string;
    helpLinkEnabled?: boolean;
    helpLinkUrl?: string;
  };
  const githubLink = brandingSettings.githubLinkEnabled ? resolveExternalLink(brandingSettings.githubLinkUrl) : '';
  const helpLink = brandingSettings.helpLinkEnabled ? resolveExternalLink(brandingSettings.helpLinkUrl) : '';
  const userName = currentUser?.nickname || currentUser?.realName || currentUser?.username || intl.formatMessage({ id: 'nav.user.menu', defaultMessage: 'User menu' });
  const userAvatarUrl = normalizeUploadUrl(currentUser?.avatarUrl || '');
  const currentLocale = normalizeLocale(currentUser?.locale || getLocale());
  const availableRoles = useMemo(() => currentUser?.availableRoles || [], [currentUser?.availableRoles]);
  const simulatedRoleId = currentUser?.simulatedRoleId ?? null;
  const selectedRoleLabel =
    simulatedRoleId == null
      ? intl.formatMessage({ id: 'nav.user.role.current', defaultMessage: 'Current account permissions' })
      : availableRoles.find((item) => item.id === simulatedRoleId)?.roleName ||
        intl.formatMessage({
          id: 'nav.user.role.current',
          defaultMessage: 'Current account permissions',
        });
  const roleSimulationHint =
    simulatedRoleId == null
      ? ''
      : intl.formatMessage(
          { id: 'nav.user.role.simulationHint', defaultMessage: 'Currently simulating {roleName}' },
          { roleName: selectedRoleLabel },
        );
  const themeButtonIcon =
    themePreference === 'compact' ? (
      <CompressOutlined />
    ) : resolvedColorMode === 'dark' ? (
      <MoonOutlined />
    ) : themePreference === 'system' ? (
      <SyncOutlined />
    ) : themePreference === 'light' ? (
      <SunOutlined />
    ) : (
      <SkinOutlined />
  );
  const [switchingLocale, setSwitchingLocale] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [switchingRole, setSwitchingRole] = useState(false);
  const [passwordDrawerOpen, setPasswordDrawerOpen] = useState(false);
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const securitySettings: SecuritySettings = initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS;
  const handleOpenProfile = () => {
    history.push('/user-center/personal-center/profile');
  };
  const handleSwitchLocale = async (nextLocale: string) => {
    const normalizedNextLocale = normalizeLocale(nextLocale);
    if (normalizedNextLocale === currentLocale) {
      return;
    }

    setSwitchingLocale(true);
    try {
      await request('/v1/profile/locale', {
        method: 'PUT',
        data: { locale: normalizedNextLocale },
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      setLocale(normalizedNextLocale, true);
    } catch (error) {
      showErrorMessage(error, intl.formatMessage({ id: 'common.failure', defaultMessage: 'Operation failed, please try again later' }));
    } finally {
      setSwitchingLocale(false);
    }
  };
  const handleSwitchTheme = (nextThemePreference: ThemePreference) => {
    if (nextThemePreference === themePreference) {
      return;
    }

    setThemePreference(nextThemePreference);
  };
  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await performLogout();
      setInitialState((prev) => ({
        ...prev,
        ...buildLoggedOutInitialState(),
      }));
    } catch (error) {
      showErrorMessage(error, intl.formatMessage({ id: 'common.failure', defaultMessage: 'Operation failed, please try again later' }));
    } finally {
      setLoggingOut(false);
    }
  };
  const handleSwitchRole = async (nextRoleValue: string) => {
    const nextRoleId = Number(nextRoleValue);
    if (!Number.isFinite(nextRoleId) || nextRoleId === simulatedRoleId) {
      return;
    }

    setSwitchingRole(true);
    try {
      const updatedUser = await request<CurrentUser>('/v1/auth/simulated-role', {
        method: 'PUT',
        data: { roleId: nextRoleId },
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      });
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
            }
          : prev,
      );
      message.success(
        intl.formatMessage(
          { id: 'nav.user.role.switchSuccessWithName', defaultMessage: 'Switched to {roleName}' },
          {
            roleName:
              availableRoles.find((role) => role.id === nextRoleId)?.roleName ||
              intl.formatMessage({ id: 'nav.user.role.switchSuccess', defaultMessage: 'Role switched' }),
          },
        ),
      );
      history.replace(DEFAULT_HOME_PATH);
    } catch (error) {
      showErrorMessage(error, intl.formatMessage({ id: 'common.failure', defaultMessage: 'Operation failed, please try again later' }));
    } finally {
      setSwitchingRole(false);
    }
  };
  const canVisitSystemSettings = Boolean(access.canVisitSystemSettings);
  const settingsMenuItems = useMemo(
    () => buildSettingsDropdownItems(initialState?.menuTree, (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]), initialState?.availablePlugins),
    [access, initialState?.availablePlugins, initialState?.menuTree],
  );
  const activeSettingsPath = useMemo(
    () =>
      resolveActiveSettingsNavigationPath(
        location.pathname,
        initialState?.menuTree,
        (accessKey) => Boolean((access as Record<string, unknown>)[accessKey]),
        initialState?.availablePlugins,
      ),
    [access, initialState?.availablePlugins, initialState?.menuTree, location.pathname],
  );
  const settingsMenuSelectedKeys = useMemo(
    () => ((isSettingsShellPath(location.pathname) || isPluginSettingsShellPath(location.pathname, initialState?.availablePlugins)) && activeSettingsPath ? [activeSettingsPath] : []),
    [activeSettingsPath, initialState?.availablePlugins, location.pathname],
  );
  const themeMenuItems: MenuProps['items'] = useMemo(
    () =>
      [
        { key: 'system', label: intl.formatMessage({ id: 'theme.system', defaultMessage: 'Follow system' }), icon: <SyncOutlined /> },
        { key: 'light', label: intl.formatMessage({ id: 'theme.light', defaultMessage: 'Light theme' }), icon: <SunOutlined /> },
        { key: 'dark', label: intl.formatMessage({ id: 'theme.dark', defaultMessage: 'Dark theme' }), icon: <MoonOutlined /> },
        { key: 'compact', label: intl.formatMessage({ id: 'theme.compact', defaultMessage: 'Compact theme' }), icon: <CompressOutlined /> },
      ].map((item) => ({
        key: item.key,
        label: (
          <div className="saas-theme-menu__item">
            <Space size={APP_SPACING.compactSectionGap.desktop}>
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
        label: intl.formatMessage({ id: 'app.locale.zh-CN', defaultMessage: 'Chinese' }),
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
  const availableRoleIds = useMemo(
    () => new Set(availableRoles.map((role) => String(role.id))),
    [availableRoles],
  );
  const roleMenuItems = useMemo<NonNullable<MenuProps['items']>>(() => {
    if (!availableRoles.length) {
      return [];
    }

    return [
      {
        key: 'role-switch',
        icon: <SwapOutlined />,
        label: intl.formatMessage({ id: 'nav.user.switchRole', defaultMessage: 'Switch role' }),
        children: availableRoles.map((role) => ({
          key: String(role.id),
          label: role.roleName,
        })),
      },
    ];
  }, [availableRoles, intl]);
  const userMenuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'profile',
        icon: <ProfileOutlined />,
        label: intl.formatMessage({ id: 'nav.user.profile', defaultMessage: 'Profile' }),
      },
      {
        key: 'password',
        icon: <LockOutlined />,
        label: intl.formatMessage({ id: 'nav.user.changePassword', defaultMessage: 'Change password' }),
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
        label: intl.formatMessage({ id: 'auth.logout', defaultMessage: 'Log out' }),
      },
    ],
    [intl, roleMenuItems],
  );
  const handleOpenPasswordDrawer = () => {
    setPasswordDrawerOpen(true);
  };
  const handlePasswordFinish = async (values: PasswordFormValues) => {
    try {
      await request<boolean>('/v1/profile/password', {
        method: 'PUT',
        data: {
          currentPassword: values.currentPassword || '',
          newPassword: values.newPassword || '',
          confirmPassword: values.confirmPassword || '',
        },
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      });
      message.success(intl.formatMessage({ id: 'nav.user.password.updateSuccess', defaultMessage: 'Password updated' }));
      setPasswordDrawerOpen(false);
    } catch (error) {
      showErrorMessage(error, intl.formatMessage({ id: 'common.failure', defaultMessage: 'Operation failed, please try again later' }));
    }
  };
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
    if (availableRoleIds.has(nextRoleValue)) {
      void handleSwitchRole(nextRoleValue);
    }
  };
  const model = {
    githubLink,
    helpLink,
    userName,
    userAvatarUrl,
    currentLocale,
    selectedRoleLabel,
    roleSimulationHint,
    availableRoles,
    simulatedRoleId,
    switchingLocale,
    loggingOut,
    switchingRole,
    passwordDrawerOpen,
    passwordForm,
    setPasswordDrawerOpen,
    handleUserMenuClick,
    handleSwitchLocale,
    handleSwitchTheme,
    handleOpenPasswordDrawer,
    handlePasswordFinish,
    themeMenuItems,
    localeMenuItems,
    userMenuItems,
    canVisitSystemSettings,
    settingsMenuItems,
    settingsMenuSelectedKeys,
    themeButtonIcon,
    isMobile,
    themePreference,
    resolvedColorMode,
    securitySettings,
  };

  return (
    <Space size={model.isMobile ? APP_SPACING.microGap.mobile : 'small'} align="center" className="saas-top-actions">
      <Space
            size={resolveResponsiveValue(APP_SPACING.tagWrapGap, model.isMobile)}
            className="saas-top-actions__inner"
            wrap={false}
          >
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: model.localeMenuItems,
            selectedKeys: [model.currentLocale],
            onClick: ({ key }) => void model.handleSwitchLocale(key),
          }}
        >
          <Button
            type="text"
            icon={<GlobalOutlined />}
            loading={model.switchingLocale}
            aria-label={intl.formatMessage({
              id: 'app.locale.switch',
              defaultMessage: 'Switch language',
            })}
          />
        </Dropdown>
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: model.themeMenuItems,
            selectedKeys: [model.themePreference],
            onClick: ({ key }) => model.handleSwitchTheme(key as ThemePreference),
          }}
        >
          <Button
            type="text"
            icon={model.themeButtonIcon}
            aria-label={intl.formatMessage(
              { id: 'theme.switch', defaultMessage: 'Theme switch, current {theme}' },
              { theme: intl.formatMessage({ id: `theme.${model.themePreference}`, defaultMessage: 'Theme' }) },
            )}
          />
        </Dropdown>
        {model.roleSimulationHint ? (
          <Tag color="orange" className="saas-role-simulation-tag" title={model.roleSimulationHint}>
            {intl.formatMessage({ id: 'nav.user.role.simulation', defaultMessage: 'Role simulation' })}
          </Tag>
        ) : null}
        {model.helpLink && !model.isMobile ? (
          <Button
            type="text"
            icon={<QuestionCircleOutlined />}
            aria-label={intl.formatMessage({ id: 'help.center', defaultMessage: 'Help center' })}
            onClick={() => {
              const nextUrl = resolveExternalLink(model.helpLink);
              if (nextUrl) {
                window.open(nextUrl, '_blank', 'noopener,noreferrer');
              }
            }}
          />
        ) : null}
        {model.githubLink && !model.isMobile ? (
          <Button
            type="text"
            icon={<GithubOutlined />}
            aria-label={intl.formatMessage({ id: 'github.link', defaultMessage: 'GitHub link' })}
            onClick={() => {
              const nextUrl = resolveExternalLink(model.githubLink);
              if (nextUrl) {
                window.open(nextUrl, '_blank', 'noopener,noreferrer');
              }
            }}
          />
        ) : null}
        {model.canVisitSystemSettings && model.settingsMenuItems?.length ? (
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: model.settingsMenuItems,
              selectedKeys: model.settingsMenuSelectedKeys,
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
            items: model.userMenuItems,
            selectedKeys: [],
            onClick: model.handleUserMenuClick,
          }}
        >
          <Button
            type="text"
            className="saas-user-menu-trigger"
            disabled={model.loggingOut || model.switchingRole}
            data-testid="top-user-menu-button"
            icon={
              <Avatar
                size="small"
                src={model.userAvatarUrl || undefined}
                icon={<UserOutlined />}
              />
            }
          >
            {!model.isMobile ? <span className="saas-user-menu-trigger__name">{model.userName}</span> : null}
          </Button>
        </Dropdown>
      </Space>
      <TopActionsPasswordDrawer
        open={model.passwordDrawerOpen}
        isMobile={model.isMobile}
        form={model.passwordForm}
        securitySettings={model.securitySettings}
        onClose={() => model.setPasswordDrawerOpen(false)}
        onFinish={model.handlePasswordFinish}
      />
    </Space>
  );
};
