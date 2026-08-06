import { Button, Space } from 'antd';
import type { ReactNode } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import { useConfirmableDrawerClose } from './drawerCloseConfirm';
import { StandardDrawer, type StandardDrawerProps } from './StandardDrawer';

export interface ManagementDrawerAction {
  key: string;
  label: ReactNode;
  type?: 'primary' | 'default';
  danger?: boolean;
  loading?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}

export interface ManagementDrawerProps extends StandardDrawerProps {
  footerActions?: ManagementDrawerAction[];
}

export const ManagementDrawer = ({
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
    <StandardDrawer {...props} className={drawerClassName} destroyOnHidden={destroyOnHidden} extra={extra} footer={footer ?? renderedActions} onClose={handleClose}>
      {children}
    </StandardDrawer>
  );
};
