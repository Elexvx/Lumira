import { Button, Drawer, Space } from 'antd';
import type { DrawerProps } from 'antd';
import type { ReactNode } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useResponsive } from '@/hooks/useResponsive';
import { useConfirmableDrawerClose } from './drawerCloseConfirm';

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
  className,
  onClose,
  ...props
}: ManagementDrawerProps) => {
  const responsive = useResponsive();
  const drawerClassName = ['saas-management-drawer', className].filter(Boolean).join(' ');
  const handleClose = useConfirmableDrawerClose(onClose);
  const renderedActions = footerActions?.length ? (
    <div className="saas-drawer-footer">
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
    </div>
  ) : undefined;

  return (
    <Drawer {...props} className={drawerClassName} width={width} destroyOnHidden={destroyOnHidden} extra={extra} footer={footer ?? renderedActions} onClose={handleClose}>
      {children}
    </Drawer>
  );
};
