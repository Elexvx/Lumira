import { useState } from 'react';
import { BellOutlined, GlobalOutlined, QuestionCircleOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Badge, Dropdown, Space } from 'antd';
import { TenantSelector } from '@/components/TenantSelector';
import { performLogout } from '@/auth/session';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import './TopActions.less';

export const TopActions = () => {
  const [loggingOut, setLoggingOut] = useState(false);
  const { initialState, setInitialState } = useInitialStateModel();
  const { isMobile } = useResponsive();

  const userName =
    initialState?.currentUser?.nickname ||
    initialState?.currentUser?.realName ||
    initialState?.currentUser?.username ||
    '用户菜单';

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
      }));
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <div className="saas-top-actions">
      {!isMobile ? (
        <div className="saas-top-actions-tenant">
          <TenantSelector />
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
