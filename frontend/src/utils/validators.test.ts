import { describe, expect, it } from 'vitest';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from './validators';

describe('validators', () => {
  it('trims string values without changing non-string values', () => {
    expect(trimString('  admin  ')).toBe('admin');
    expect(trimString(1001)).toBe(1001);
  });

  it('accepts empty or valid mobile values', async () => {
    await expect(validateOptionalChinaMobile({}, '')).resolves.toBeUndefined();
    await expect(validateOptionalChinaMobile({}, '13800000000')).resolves.toBeUndefined();
  });

  it('rejects invalid mobile values', async () => {
    await expect(validateOptionalChinaMobile({}, '123456')).rejects.toThrow('请输入有效手机号');
  });

  it('validates China ID card checksum when length is 18', async () => {
    await expect(validateOptionalChinaIdCard({}, '11010519491231002X')).resolves.toBeUndefined();
    await expect(validateOptionalChinaIdCard({}, '110105194912310021')).rejects.toThrow('请输入有效身份证号码');
  });
});
