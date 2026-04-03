import { useMemo, useState } from 'react';
import { BellOutlined, GlobalOutlined, QuestionCircleOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Badge, Dropdown, Select, Space } from 'antd';
import { DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { performLogout } from '@/auth/session';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { switchTenantAction } from '@/tenant/actions';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import './TopActions.less';

export const TopActions = () => {
  const [loggingOut, setLoggingOut] = useState(false);
  const [switching, setSwitching] = useState(false);
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();

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
        availablePlugins: [],
        securitySettings: prev?.securitySettings || initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS,
        brandingSettings: prev?.brandingSettings || initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      }));
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <div className="saas-top-actions">
      {!isMobile ? (
        <div className="saas-top-actions-tenant">
          <Select
            size="small"
            variant="borderless"
            className="tenant-selector"
            style={{ width: isMobile ? 120 : 150, maxWidth: '100%' }}
            dropdownMatchSelectWidth={false}
            placeholder="选择租户"
            loading={switching}
            disabled={switching || tenantOptions.length <= 1}
            value={initialState?.currentTenant?.tenantId}
            onChange={handleSwitchTenant}
            options={tenantOptions}
            notFoundContent="暂无可用租户"
          />
        </div>
      ) : null}
      <Space size={isMobile ? 8 : 12} wrap={false} className="saas-top-actions-toolbar">
        <span className="saas-top-actions-trigger" aria-label="帮助中心">
          <QuestionCircleOutlined className="saas-top-actions-icon" />
        </span>
        <span className="saas-top-actions-trigger" aria-label="语言切换">
          <GlobalOutlined className="saas-top-actions-icon" />
        </span>
        <Badge dot offset={[-4, 4]} className="saas-top-actions-badge">
          <span className="saas-top-actions-trigger" aria-label="通知中心">
            <BellOutlined className="saas-top-actions-icon" />
          </span>
        </Badge>
        <Dropdown
          menu={{
            items: [
              {
                key: 'logout',
                label: loggingOut ? '退出中...' : '退出登录',
                disabled: loggingOut,
              },
            ],
            onClick: ({ key }) => {
              if (key === 'logout' && !loggingOut) {
                handleLogout();
              }
            },
          }}
        >
          <Space className="saas-top-actions-user" size={8} wrap={false}>
            <Avatar
              size="small"
              icon={<UserOutlined />}
            />
            <span className="saas-top-actions-user-name">{userName}</span>
          </Space>
        </Dropdown>
      </Space>
    </div>
  );
};
