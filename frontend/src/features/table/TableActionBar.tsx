import { MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown, Space } from 'antd';
import type { MenuProps } from 'antd';
import { useMemo } from 'react';
import { getLocale } from '@umijs/max';
import { APP_SPACING } from '@/theme/spacing';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export interface TableActionItem {
  key: string;
  label: React.ReactNode;
  onClick?: () => void;
  icon?: React.ReactNode;
  danger?: boolean;
  disabled?: boolean;
  hidden?: boolean;
  loading?: boolean;
}

interface TableActionBarProps {
  items: TableActionItem[];
  isMobile?: boolean;
  inlineCount?: number;
}

export const TableActionBar = ({ items, isMobile = false, inlineCount }: TableActionBarProps) => {
  const visibleItems = useMemo(() => items.filter((item) => !item.hidden), [items]);
  const maxInlineCount = inlineCount ?? (isMobile ? 0 : 2);
  const inlineItems = visibleItems.slice(0, maxInlineCount);
  const overflowItems = visibleItems.slice(maxInlineCount);
  const menuItems: MenuProps['items'] = overflowItems.map((item) => ({
    key: item.key,
    label: item.label,
    icon: item.icon,
    danger: item.danger,
    disabled: item.disabled,
    onClick: item.onClick,
  }));

  if (!visibleItems.length) {
    return null;
  }

  if (isMobile && visibleItems.length === 1) {
    const [item] = visibleItems;
    return (
      <Button type="link" size="small" danger={item.danger} disabled={item.disabled} loading={item.loading} onClick={item.onClick}>
        {item.label}
      </Button>
    );
  }

  return (
    <Space size={APP_SPACING.microGap.desktop} wrap={false}>
      {inlineItems.map((item) => (
        <Button key={item.key} type="link" size="small" danger={item.danger} disabled={item.disabled} loading={item.loading} onClick={item.onClick}>
          {item.label}
        </Button>
      ))}
      {menuItems.length ? (
        <Dropdown trigger={['click']} menu={{ items: menuItems }} placement="bottomRight">
          <Button type="link" size="small" icon={<MoreOutlined />} aria-label={t('更多', 'More')} title={t('更多', 'More')}>
            {isMobile ? null : t('更多', 'More')}
          </Button>
        </Dropdown>
      ) : null}
    </Space>
  );
};
