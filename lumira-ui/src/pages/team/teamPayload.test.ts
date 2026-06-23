import { describe, expect, it } from 'vitest';
import { normalizeTeamCreatePayload, pruneBlankDraftMembers } from './teamPayload';

describe('normalizeTeamCreatePayload', () => {
  it('prunes empty rows while keeping partially filled rows for validation', () => {
    expect(
      pruneBlankDraftMembers([
        { memberName: '', employeeNo: '', departmentName: '', role: 'MEMBER', remark: '' },
        { memberName: ' ', role: 'MEMBER' },
        { memberName: '', employeeNo: 'E002', departmentName: '', role: 'MEMBER', remark: '' },
        { memberName: 'Bob', role: 'MANAGER' },
      ]),
    ).toEqual([
      { memberName: '', employeeNo: 'E002', departmentName: '', role: 'MEMBER', remark: '' },
      { memberName: 'Bob', role: 'MANAGER' },
    ]);
  });

  it('keeps draft member form fields without requiring a registered user', () => {
    expect(
      normalizeTeamCreatePayload({
        teamName: 'Core Team',
        initialMembers: [
          {
            memberName: ' Alice ',
            employeeNo: ' E001 ',
            departmentName: ' Product ',
            remark: ' Lead ',
          },
        ],
      }),
    ).toEqual({
      teamName: 'Core Team',
      initialMembers: [
        {
          memberName: 'Alice',
          employeeNo: 'E001',
          departmentName: 'Product',
          role: 'MEMBER',
          remark: 'Lead',
        },
      ],
    });
  });

  it('filters blank member rows before submit', () => {
    expect(
      normalizeTeamCreatePayload({
        teamName: 'Core Team',
        initialMembers: [
          { memberName: ' ', role: 'MEMBER' },
          { memberName: 'Bob', role: 'MANAGER' },
        ],
      }).initialMembers,
    ).toEqual([{ memberName: 'Bob', employeeNo: undefined, departmentName: undefined, role: 'MANAGER', remark: undefined }]);
  });
});
