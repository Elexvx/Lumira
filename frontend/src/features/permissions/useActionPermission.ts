import { useCallback, useMemo } from 'react';
import { useAccess } from '@umijs/max';
import { isSettingsPermission, isSuperAdminUser } from '@/auth/adminAccess';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { TableActionItem } from '@/features/table/TableActionBar';

export type PermissionRequirement = string | string[];
type UnauthorizedMode = 'hide' | 'disable';

export interface PermissionAwareTableAction extends Omit<TableActionItem, 'hidden' | 'disabled'> {
  permission?: PermissionRequirement;
  hidden?: boolean;
  disabled?: boolean;
  unauthorizedMode?: UnauthorizedMode;
}

export interface PermissionAwareToolbarAction<TValue> {
  value: TValue;
  permission?: PermissionRequirement;
  hidden?: boolean;
}

const normalizePermissions = (permission?: PermissionRequirement): string[] => {
  if (!permission) {
    return [];
  }

  return Array.isArray(permission) ? permission : [permission];
};

export const useActionPermission = () => {
  const access = useAccess();
  const { initialState } = useInitialStateModel();
  const isSettingsAdmin = isSuperAdminUser(initialState?.currentUser);
  const canAccess = useCallback(
    (permission: string) => (isSettingsAdmin && isSettingsPermission(permission)) || access.hasPermission(permission),
    [access, isSettingsAdmin],
  );
  const canAccessAny = useCallback((permissions: string[]) => permissions.some((permission) => canAccess(permission)), [canAccess]);

  return useMemo(
    () => ({
      can: (permission?: PermissionRequirement) => {
        const permissionList = normalizePermissions(permission);
        if (!permissionList.length) {
          return true;
        }

        return canAccessAny(permissionList);
      },
      buildTableActions: (items: PermissionAwareTableAction[]): TableActionItem[] =>
        items.map((item) => {
          const allowed = item.permission ? canAccessAny(normalizePermissions(item.permission)) : true;
          const unauthorizedMode = item.unauthorizedMode ?? 'disable';
          const hidden = Boolean(item.hidden) || (!allowed && unauthorizedMode === 'hide');
          const disabled = Boolean(item.disabled) || (!allowed && unauthorizedMode === 'disable');

          return {
            ...item,
            hidden,
            disabled,
          };
        }),
      buildToolbarActions: <TValue,>(items: PermissionAwareToolbarAction<TValue>[]) =>
        items
          .filter((item) => {
            if (item.hidden) {
              return false;
            }
            if (!item.permission) {
              return true;
            }

            return canAccessAny(normalizePermissions(item.permission));
          })
          .map((item) => item.value),
      withPermissionGuard: <TValue,>(
        permission: PermissionRequirement | undefined,
        allowedValue: TValue,
        fallbackValue: TValue | null = null,
      ) => (permission && !canAccessAny(normalizePermissions(permission)) ? fallbackValue : allowedValue),
      canAccess,
      canAccessAny,
    }),
    [canAccess, canAccessAny],
  );
};
