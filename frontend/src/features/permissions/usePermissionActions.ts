import { useMemo } from 'react';
import { usePermission } from '@/hooks/usePermission';
import type { TableActionItem } from '@/features/table/TableActionBar';

export interface PermissionActionConfig extends Omit<TableActionItem, 'hidden'> {
  permission?: string | string[];
  hidden?: boolean;
}

const normalizePermissionList = (permission?: string | string[]) => {
  if (!permission) {
    return [];
  }

  return Array.isArray(permission) ? permission : [permission];
};

export const usePermissionActions = () => {
  const { canAccess, canAccessAny } = usePermission();

  return useMemo(
    () => ({
      canAccess,
      canAccessAny,
      buildActions: (items: PermissionActionConfig[]): TableActionItem[] =>
        items.map((item) => ({
          ...item,
          hidden: item.hidden || (item.permission ? !canAccessAny(normalizePermissionList(item.permission)) : false),
        })),
    }),
    [canAccess, canAccessAny],
  );
};
