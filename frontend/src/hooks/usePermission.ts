import { useAccess } from '@umijs/max';

export const usePermission = () => {
  const access = useAccess();

  return {
    canAccess: (permission: string) => access.hasPermission(permission),
    canAccessAny: (permissions: string[]) => permissions.some((permission) => access.hasPermission(permission)),
  };
};
