import { Button, type ButtonProps } from 'antd';
import type { ReactNode } from 'react';
import { useMemo } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import { useActionPermission, type PermissionRequirement } from '@/features/permissions/useActionPermission';

interface PageToolbarButtonConfig {
  key: string;
  label: ReactNode;
  onClick: () => void;
  icon?: ReactNode;
  permission?: PermissionRequirement;
  hidden?: boolean;
  disabled?: boolean;
  unauthorizedMode?: 'hide' | 'disable';
  type?: ButtonProps['type'];
}

export const usePagePermissionActions = () => {
  const actionPermission = useActionPermission();
  const responsive = useResponsive();

  const searchConfig = useMemo(
    () => ({
      labelWidth: 'auto' as const,
      span: responsive.isMobile ? 24 : 8,
    }),
    [responsive.isMobile],
  );

  const buttonSize: ButtonProps['size'] = responsive.isMobile ? 'small' : 'middle';

  const buildToolbarButtons = (items: PageToolbarButtonConfig[]) =>
    items
      .filter((item) => {
        if (item.hidden) {
          return false;
        }
        if (!item.permission || item.unauthorizedMode === 'disable' || item.unauthorizedMode == null) {
          return true;
        }
        return actionPermission.can(item.permission);
      })
      .map((item) => {
        const allowed = actionPermission.can(item.permission);
        return (
          <Button
            key={item.key}
            type={item.type}
            size={buttonSize}
            icon={item.icon}
            disabled={item.disabled || !allowed}
            onClick={item.onClick}
          >
            {item.label}
          </Button>
        );
      });

  return {
    actionPermission,
    responsive,
    searchConfig,
    buttonSize,
    buildToolbarButtons,
  };
};
