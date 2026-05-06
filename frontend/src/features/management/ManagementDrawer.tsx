import { Button, Drawer, Space } from 'antd';
import type { DrawerProps } from 'antd';
import type { ReactNode } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';

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
  footer,
  children,
  ...props
}: ManagementDrawerProps) => {
  const resolvedFooter =
    footer ??
    (footerActions?.length ? (
      <div className="saas-drawer-footer">
        <Space>
          {footerActions.map((action) => (
            <Button
              key={action.key}
              type={action.type}
              danger={action.danger}
              loading={action.loading}
              disabled={action.disabled}
              onClick={action.onClick}
            >
              {action.label}
            </Button>
          ))}
        </Space>
      </div>
    ) : undefined);

  return (
    <Drawer {...props} width={width} destroyOnHidden={destroyOnHidden} footer={resolvedFooter}>
      {children}
    </Drawer>
  );
};
