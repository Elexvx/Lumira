import { MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown, Space } from 'antd';
import type { MenuProps } from 'antd';
import { useMemo } from 'react';
import type { ResponsiveActionItem, ResponsiveTableLevel } from './types';

interface ResponsiveActionsProps {
  items: ResponsiveActionItem[];
  level: ResponsiveTableLevel;
}

const inlineCountByLevel: Record<ResponsiveTableLevel, number> = {
  desktop: 2,
  tablet: 1,
  mobile: 0,
};

export const ResponsiveActions = ({ items, level }: ResponsiveActionsProps) => {
  const visibleItems = useMemo(() => items.filter((item) => !item.hidden), [items]);
  const inlineCount = inlineCountByLevel[level];
  const inlineItems = visibleItems.slice(0, inlineCount);
  const menuItems: MenuProps['items'] = visibleItems.slice(inlineCount).map((item) => ({
    key: item.key,
    label: item.label,
    icon: item.icon,
    danger: item.danger,
    disabled: item.disabled,
    onClick: item.onClick,
  }));

  if (visibleItems.length === 0) {
    return null;
  }

  if (level === 'mobile') {
    if (visibleItems.length === 1) {
      const item = visibleItems[0];
      return (
        <Button type="link" size="small" danger={item.danger} disabled={item.disabled} onClick={item.onClick}>
          {item.label}
        </Button>
      );
    }

    return (
      <Dropdown
        trigger={['click']}
        menu={{ items: menuItems }}
        placement="bottomRight"
      >
        <Button type="link" size="small" icon={<MoreOutlined />}>
          更多
        </Button>
      </Dropdown>
    );
  }

  return (
    <Space size={4} wrap={false}>
      {inlineItems.map((item) => (
        <Button key={item.key} type="link" size="small" danger={item.danger} disabled={item.disabled} onClick={item.onClick}>
          {item.label}
        </Button>
      ))}
      {menuItems.length ? (
        <Dropdown trigger={['click']} menu={{ items: menuItems }} placement="bottomRight">
          <Button type="link" size="small" icon={<MoreOutlined />}>
            更多
          </Button>
        </Dropdown>
      ) : null}
    </Space>
  );
};

