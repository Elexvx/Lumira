import { useActionPermission } from '@/features/permissions/useActionPermission';

export const usePermissionActions = () => {
  const actionPermission = useActionPermission();

  return {
    ...actionPermission,
    buildActions: actionPermission.buildTableActions,
  };
};
