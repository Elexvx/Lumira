import { describe, expect, it } from 'vitest';
import { isDeprecatedRegistrationContactField, resolveRegistrationFieldScope } from './registrationFieldScope';

describe('resolveRegistrationFieldScope', () => {
  it('identifies the deprecated registration contact field', () => {
    expect(isDeprecatedRegistrationContactField({
      itemType: 'REGISTRATION_FIELD',
      itemKey: 'contact-name',
    })).toBe(true);
    expect(isDeprecatedRegistrationContactField({
      itemType: 'MEMBER_FIELD',
      itemKey: 'contact-name',
    })).toBe(false);
  });

  it('moves a legacy contact-name field into member fields', () => {
    expect(resolveRegistrationFieldScope({
      itemType: 'TEAM_FIELD',
      itemKey: 'contact-name',
      title: '姓名',
    })).toBe('MEMBER_FIELD');
  });

  it('moves a legacy memberName field into member fields', () => {
    expect(resolveRegistrationFieldScope({
      itemType: 'TEAM_FIELD',
      itemKey: 'memberName',
      title: '成员姓名',
    })).toBe('MEMBER_FIELD');
  });

  it('keeps a real team field in the team scope', () => {
    expect(resolveRegistrationFieldScope({
      itemType: 'TEAM_FIELD',
      itemKey: 'teamName',
      title: '团队名称',
    })).toBe('TEAM_FIELD');
  });

  it('respects an explicit member field scope', () => {
    expect(resolveRegistrationFieldScope({
      itemType: 'TEAM_FIELD',
      itemKey: 'contact-name',
      title: '联系人姓名',
      contentJson: JSON.stringify({ fieldScope: 'MEMBER_FIELD' }),
    })).toBe('MEMBER_FIELD');
  });
});
