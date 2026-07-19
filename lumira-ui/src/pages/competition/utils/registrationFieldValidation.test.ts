import { describe, expect, it } from 'vitest';

import {
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

  it('rejects invalid values for configured mobile fields', () => {
    expect(validateRegistrationFieldValue('MOBILE', 'NONE', '手机号', '13800138000')).toBeUndefined();
    expect(validateRegistrationFieldValue('MOBILE', 'NONE', '手机号', '123456')).toBe('请输入正确的手机号');
  });
});
