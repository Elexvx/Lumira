import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import {
  createDefaultActivityRegistrationFields,
  normalizeActivityRegistrationAnswers,
  normalizeActivityRegistrationFields,
  summarizeActivityRegistrationAnswers,
} from './activityRegistrationForm';

describe('activityRegistrationForm', () => {
  it('returns an isolated default field set for each new activity', () => {
    const first = createDefaultActivityRegistrationFields();
    const second = createDefaultActivityRegistrationFields();
    first[0].label = '联系人';
    expect(second[0].label).toBe('姓名');
  });

  it('normalizes choice options and removes options from scalar fields', () => {
    expect(normalizeActivityRegistrationFields([
      { fieldKey: ' audience ', label: ' 参会类型 ', fieldType: 'SELECT', required: true, options: ['嘉宾', ' 嘉宾 ', '观众'] },
      { fieldKey: 'note', label: '说明', fieldType: 'TEXT', required: false, options: ['不会保留'] },
    ])).toEqual([
      { fieldKey: 'audience', label: '参会类型', fieldType: 'SELECT', required: true, options: ['嘉宾', '观众'] },
      { fieldKey: 'note', label: '说明', fieldType: 'TEXT', required: false, options: [] },
    ]);
  });

  it('serializes date and multi-select values for submission', () => {
    const fields = [
      { fieldKey: 'visitDate', label: '参观日期', fieldType: 'DATE' as const, required: true },
      { fieldKey: 'topics', label: '关注方向', fieldType: 'MULTI_SELECT' as const, required: false },
    ];
    expect(normalizeActivityRegistrationAnswers(fields, {
      visitDate: dayjs('2026-08-16'),
      topics: ['AI', '机器人'],
    })).toEqual({ visitDate: '2026-08-16', topics: ['AI', '机器人'] });
  });

  it('summarizes the immutable answer snapshot', () => {
    expect(summarizeActivityRegistrationAnswers([
      { fieldKey: 'name', label: '姓名', fieldType: 'TEXT', value: '林宁' },
      { fieldKey: 'topics', label: '方向', fieldType: 'MULTI_SELECT', value: ['AI', '机器人'] },
      { fieldKey: 'note', label: '备注', fieldType: 'TEXTAREA', value: null },
    ])).toBe('姓名：林宁；方向：AI、机器人');
  });
});
