import { describe, expect, it } from 'vitest';
import {
  DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
  isIndependentMemberRoleField,
  normalizeIndependentMemberRoleMetadata,
  reorderScopedConfigItems,
} from './competitionFieldConfig';

describe('competition field configuration', () => {
  it('converts the legacy system role field into an independent single select', () => {
    expect(normalizeIndependentMemberRoleMetadata('MEMBER_FIELD', 'role', { fieldType: 'ROLE' })).toEqual({
      fieldType: 'SELECT',
      options: DEFAULT_INDEPENDENT_MEMBER_ROLE_OPTIONS,
    });
    expect(isIndependentMemberRoleField('MEMBER_FIELD', 'role', 'SELECT')).toBe(true);
  });

  it('keeps configured independent role options', () => {
    expect(normalizeIndependentMemberRoleMetadata('MEMBER_FIELD', 'role', {
      fieldType: 'SELECT',
      options: '指导老师\n参赛学生',
    })).toEqual({
      fieldType: 'SELECT',
      options: '指导老师\n参赛学生',
    });
  });

  it('reorders only the selected scope and normalizes its sort order', () => {
    const items = [
      { key: 'team', sortOrder: 10 },
      { key: 'member-a', sortOrder: 20 },
      { key: 'project', sortOrder: 10 },
      { key: 'member-b', sortOrder: 40 },
    ];
    expect(reorderScopedConfigItems(items, [1, 3], 1, 0)).toEqual([
      { key: 'team', sortOrder: 10 },
      { key: 'member-b', sortOrder: 10 },
      { key: 'project', sortOrder: 10 },
      { key: 'member-a', sortOrder: 20 },
    ]);
  });
});
