import { describe, expect, it } from 'vitest';

import {
  isSupportedRegistrationFieldValidationConfig,
  resolveRegistrationFieldValidationRule,
  validateRegistrationFieldValue,
} from './registrationFieldValidation';

describe('resolveRegistrationFieldValidationRule', () => {
  it('automatically validates mobile fields', () => {
    expect(resolveRegistrationFieldValidationRule('MOBILE')).toBe('CHINA_MOBILE');
    expect(resolveRegistrationFieldValidationRule('mobile', 'NONE')).toBe('CHINA_MOBILE');
  });

  it('automatically validates email fields', () => {
    expect(resolveRegistrationFieldValidationRule('EMAIL', 'NONE')).toBe('EMAIL');
  });

  it('keeps an explicit rule for other field types', () => {
    expect(resolveRegistrationFieldValidationRule('TEXT', 'ID_CARD')).toBe('ID_CARD');
    expect(resolveRegistrationFieldValidationRule('TEXT')).toBe('NONE');
  });

  it('fails closed for unsupported field types and validation rules', () => {
    expect(isSupportedRegistrationFieldValidationConfig('TEXT', 'PERSON_NAME')).toBe(true);
    expect(isSupportedRegistrationFieldValidationConfig('SCRIPT', 'NONE')).toBe(false);
    expect(isSupportedRegistrationFieldValidationConfig('TEXT', 'CUSTOM_SCRIPT')).toBe(false);
    expect(validateRegistrationFieldValue('TEXT', 'CUSTOM_SCRIPT', '自定义字段', 'value'))
      .toBe('自定义字段的校验规则配置无效');
  });

  it('upgrades protected standard fields from legacy NONE rules', () => {
    expect(resolveRegistrationFieldValidationRule('TEXT', 'NONE', 'TEAM_FIELD', 'teamName')).toBe('DISPLAY_NAME');
    expect(resolveRegistrationFieldValidationRule('TEXT', 'NONE', 'MEMBER_FIELD', 'memberName')).toBe('PERSON_NAME');
    expect(resolveRegistrationFieldValidationRule('TEXT', undefined, 'PROJECT_FIELD', 'projectName')).toBe('DISPLAY_NAME');
    expect(resolveRegistrationFieldValidationRule('TEXT', 'NONE', 'TEAM_FIELD', 'campus')).toBe('NONE');
  });

  it('rejects invalid values for configured mobile fields', () => {
    expect(validateRegistrationFieldValue('MOBILE', 'NONE', '手机号', '13800138000')).toBeUndefined();
    expect(validateRegistrationFieldValue('MOBILE', 'NONE', '手机号', '123456')).toBe('请输入正确的手机号');
  });

  it('validates email and identity-card rules instead of only rendering them', () => {
    expect(validateRegistrationFieldValue('EMAIL', 'NONE', '邮箱', 'student@example.com')).toBeUndefined();
    expect(validateRegistrationFieldValue('EMAIL', 'NONE', '邮箱', 'not-an-email')).toBe('请输入正确的邮箱');
    expect(validateRegistrationFieldValue('TEXT', 'ID_CARD', '身份证号', '110101200001011234')).toBeUndefined();
    expect(validateRegistrationFieldValue('TEXT', 'ID_CARD', '身份证号', '123')).toBe('请输入正确的身份证号');
  });

  it('rejects unsafe standard names while accepting normal bilingual names', () => {
    expect(validateRegistrationFieldValue('TEXT', 'NONE', '团队名称', 'AIADC 创新团队 2026', 'TEAM_FIELD', 'teamName')).toBeUndefined();
    expect(validateRegistrationFieldValue('TEXT', 'NONE', '项目名称', 'Lumira-AI（杭州）', 'PROJECT_FIELD', 'title')).toBeUndefined();
    expect(validateRegistrationFieldValue('TEXT', 'NONE', '团队名称', "大噶地方刮大风官方阿哥十多个a'f'd'g", 'TEAM_FIELD', 'teamName'))
      .toBe('团队名称只能输入中文、英文字母、数字及常用命名符号');
    expect(validateRegistrationFieldValue('TEXT', 'NONE', '成员姓名', '张三', 'MEMBER_FIELD', 'memberName')).toBeUndefined();
    expect(validateRegistrationFieldValue('TEXT', 'NONE', '成员姓名', 'Alice 1', 'MEMBER_FIELD', 'memberName'))
      .toBe('成员姓名不能包含空格');
  });
});
