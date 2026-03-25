import { BellOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Dropdown, Space } from 'antd';
import { TenantSelector } from '@/components/TenantSelector';

const items = [{ key: 'logout', label: '退出登录' }];

export const TopActions = () => {
  return (
    <Space size="large">
      <TenantSelector />
      <BellOutlined />
      <Dropdown menu={{ items }}>
        <Space>
          <Avatar size="small" icon={<UserOutlined />} />
          用户菜单
        </Space>
      </Dropdown>
    </Space>
  );
};
