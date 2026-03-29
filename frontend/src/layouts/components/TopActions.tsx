import { useState } from 'react';
import { BellOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Dropdown, Space } from 'antd';
import { TenantSelector } from '@/components/TenantSelector';
import { performLogout } from '@/auth/session';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';

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
    <Space size={isMobile ? 8 : 12} wrap={false}>
      <TenantSelector />
      <BellOutlined style={{ fontSize: 16, color: 'rgba(255, 255, 255, 0.85)' }} />
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
        <Space style={{ cursor: 'pointer', maxWidth: isMobile ? 120 : 160 }}>
          <Avatar
            size="small"
            icon={<UserOutlined />}
            style={{
              background: 'rgba(255, 255, 255, 0.12)',
              color: '#fff',
              border: '1px solid rgba(255, 255, 255, 0.18)',
            }}
          />
          <span style={{ maxWidth: isMobile ? 80 : 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'rgba(255, 255, 255, 0.85)' }}>
            {userName}
          </span>
        </Space>
      </Dropdown>
    </Space>
  );
};
