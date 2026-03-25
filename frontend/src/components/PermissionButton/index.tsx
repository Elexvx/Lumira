import { Button } from 'antd';
import type { ButtonProps } from 'antd';
import { usePermission } from '@/hooks/usePermission';

interface PermissionButtonProps extends ButtonProps {
  permission: string;
}

export const PermissionButton = ({ permission, ...props }: PermissionButtonProps) => {
  const { canAccess } = usePermission();
  if (!canAccess(permission)) {
    return null;
  }
  return <Button {...props} />;
};
