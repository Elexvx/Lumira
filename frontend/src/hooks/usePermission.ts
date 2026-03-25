import { useAccess } from '@umijs/max';

export const usePermission = () => {
  const access = useAccess();
  return {
    canAccess: (permission: string) => access.hasPermission(permission),
  };
};
