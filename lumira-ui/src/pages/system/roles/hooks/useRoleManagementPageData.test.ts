import { describe, expect, it, vi } from 'vitest';
import { normalizePermissionKeysByPages } from './useRoleManagementPageData';

vi.mock('@umijs/max', () => ({
  getLocale: () => 'zh-CN',
}));

describe('role permission page/action normalization', () => {
  const allPageKeys = new Set(['system:role:view', 'system:user:view']);
  const actionPermissionPageMap = new Map([
    ['system:role:update', 'system:role:view'],
    ['system:role:grant', 'system:role:view'],
    ['system:user:update', 'system:user:view'],
  ]);
  const assignablePermissionKeys = new Set([
    'system:role:view',
    'system:role:update',
    'system:role:grant',
    'system:user:view',
    'system:user:update',
  ]);

  it('keeps action permissions only while their page permission remains selected', () => {
    const normalized = normalizePermissionKeysByPages(
      ['system:role:view', 'system:role:update', 'system:user:view', 'system:user:update'],
      ['system:user:view'],
      allPageKeys,
      actionPermissionPageMap,
      assignablePermissionKeys,
    );

    expect(normalized.sort()).toEqual(['system:user:update', 'system:user:view'].sort());
  });

  it('preserves permissions that are valid for the role but not represented in the page tree', () => {
    const normalized = normalizePermissionKeysByPages(
      ['system:role:view', 'system:role:update', 'system:config:update'],
      ['system:role:view'],
      allPageKeys,
      actionPermissionPageMap,
      assignablePermissionKeys,
    );

    expect(normalized.sort()).toEqual(['system:config:update', 'system:role:update', 'system:role:view'].sort());
  });
});
