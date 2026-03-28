import { useState } from 'react';
import { BellOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Dropdown, Space } from 'antd';
import { TenantSelector } from '@/components/TenantSelector';
import { performLogout } from '@/auth/session';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

export const TopActions = () => {
  const [loggingOut, setLoggingOut] = useState(false);
  const { initialState, setInitialState } = useInitialStateModel();

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
      }));
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <Space size="large">
      <TenantSelector />
      <BellOutlined />
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
        <Space style={{ cursor: 'pointer' }}>
          <Avatar size="small" icon={<UserOutlined />} />
          {userName}
        </Space>
      </Dropdown>
    </Space>
  );
};
