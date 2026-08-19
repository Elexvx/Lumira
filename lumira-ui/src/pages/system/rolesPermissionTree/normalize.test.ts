import { describe, expect, it } from 'vitest';

import { normalizePermissionTree } from './normalize';

describe('normalizePermissionTree', () => {
  it('keeps every permission required to complete a competition registration', () => {
    const normalized = normalizePermissionTree([], new Set(['/competitions/register']));
    const registrationPage = normalized
      .flatMap((node) => node.children ?? [])
      .find((node) => node.routePath === '/competitions/register');

    expect(registrationPage?.actionPermissions?.map((permission) => permission.permissionKey)).toEqual(
      expect.arrayContaining([
        'aiadc:registration:create',
        'aiadc:registration:update',
        'aiadc:registration:pay',
        'aiadc:material:view',
        'aiadc:material:submit',
        'aiadc:stage:view',
      ]),
    );
  });
});
