import { describe, expect, it } from 'vitest';
import {
  resolvePermissionAwareTableActions,
  resolvePermissionAwareToolbarActions,
} from './useActionPermission';

describe('useActionPermission helpers', () => {
  const canAccessAny = (allowedPermissions: string[]) => (permissions: string[]) =>
    permissions.some((permission) => allowedPermissions.includes(permission));

  it('disables unauthorized table actions by default', () => {
    const actions = resolvePermissionAwareTableActions(
      [
        { key: 'detail', label: 'Detail', permission: 'team:view' },
        { key: 'edit', label: 'Edit', permission: 'team:update' },
      ],
      canAccessAny(['team:view']),
    );

    expect(actions).toMatchObject([
      { key: 'detail', disabled: false, hidden: false },
      { key: 'edit', disabled: true, hidden: false },
    ]);
  });

  it('hides unauthorized table actions when requested', () => {
    const actions = resolvePermissionAwareTableActions(
      [{ key: 'delete', label: 'Delete', permission: 'team:delete', unauthorizedMode: 'hide' }],
      canAccessAny([]),
    );

    expect(actions).toMatchObject([{ key: 'delete', hidden: true, disabled: false }]);
  });

  it('filters toolbar actions by permission', () => {
    const actions = resolvePermissionAwareToolbarActions(
      [
        { value: 'create', permission: 'team:create' },
        { value: 'refresh' },
      ],
      canAccessAny([]),
    );

    expect(actions).toEqual(['refresh']);
  });
});
