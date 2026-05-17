import { Button, Drawer, Space } from 'antd';
import type { DrawerProps } from 'antd';
import type { ReactNode } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useResponsive } from '@/hooks/useResponsive';

export interface ManagementDrawerAction {
  key: string;
  label: ReactNode;
  type?: 'primary' | 'default';
  danger?: boolean;
  loading?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}

export interface ManagementDrawerProps extends DrawerProps {
  footerActions?: ManagementDrawerAction[];
}

export const ManagementDrawer = ({
  width = STANDARD_DRAWER_WIDTH,
  destroyOnHidden = true,
  footerActions,
  extra,
  footer,
  children,
  ...props
}: ManagementDrawerProps) => {
  const responsive = useResponsive();
  const renderedActions = footerActions?.length ? (
    <Space wrap>
      {footerActions.map((action) => (
        <Button
          key={action.key}
          type={action.type}
          size={responsive.isMobile ? 'small' : 'middle'}
          danger={action.danger}
          loading={action.loading}
          disabled={action.disabled}
          onClick={action.onClick}
        >
          {action.label}
        </Button>
      ))}
    </Space>
  ) : undefined;

  return (
    <Drawer {...props} width={width} destroyOnHidden={destroyOnHidden} extra={extra ?? renderedActions} footer={footer}>
      {children}
    </Drawer>
  );
};
