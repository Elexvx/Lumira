import { Button, type ButtonProps } from 'antd';
import type { ReactNode } from 'react';
import { useMemo } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import { useActionPermission, type PermissionRequirement } from '@/features/permissions/useActionPermission';

interface PageToolbarButtonConfig {
  key: string;
  label: ReactNode;
  onClick: () => void;
  permission?: PermissionRequirement;
  hidden?: boolean;
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
    actionPermission.buildToolbarActions(
      items.map((item) => ({
        permission: item.permission,
        hidden: item.hidden,
        value: (
          <Button key={item.key} type={item.type} size={buttonSize} onClick={item.onClick}>
            {item.label}
          </Button>
        ),
      })),
    );

  return {
    actionPermission,
    responsive,
    searchConfig,
    buttonSize,
    buildToolbarButtons,
  };
};
