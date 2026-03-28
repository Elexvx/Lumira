import { useAccess } from 'umi';

export const usePermission = () => {
  const access = useAccess();
  return {
    canAccess: (permission: string) => access.hasPermission(permission),
  };
};
