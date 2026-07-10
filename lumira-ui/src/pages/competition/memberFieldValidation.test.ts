import { describe, expect, it } from 'vitest';
import { validateMemberTextField } from './memberFieldValidation';

describe('validateMemberTextField', () => {
  it('accepts ordinary Chinese and English names', () => {
    expect(validateMemberTextField('memberName', '姓名', '张三')).toBeUndefined();
    expect(validateMemberTextField('memberName', '姓名', 'Alice')).toBeUndefined();
    expect(validateMemberTextField('memberName', '姓名', '买买提·明')).toBeUndefined();
  });

  it('rejects spaces, numbers, and symbols in names', () => {
    expect(validateMemberTextField('memberName', '姓名', ' ')).toContain('空格');
    expect(validateMemberTextField('memberName', '姓名', '022222')).toContain('只能输入');
    expect(validateMemberTextField('memberName', '姓名', '---')).toContain('只能输入');
  });

  it('recognizes configured school fields and rejects dirty values', () => {
    expect(validateMemberTextField('custom-school', '学校', '清华大学')).toBeUndefined();
    expect(validateMemberTextField('custom-school', '学校', 'Peking University')).toBeUndefined();
    expect(validateMemberTextField('custom-school', '学校', '++++++')).toContain('只能输入');
    expect(validateMemberTextField('custom-school', '学校', 'Peking  University')).toContain('连续空格');
  });
});
