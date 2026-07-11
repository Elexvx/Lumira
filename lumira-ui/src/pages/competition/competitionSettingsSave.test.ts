import { describe, expect, it } from 'vitest';
import { isBasicSettingsPageReadyToSave, isConfigModuleReadyToSave, isTimelineSettingsPageReadyToSave } from './competitionSettingsSave';

describe('competition settings page-level save guards', () => {
  it('allows the basic page to save without timeline fields', () => {
    expect(isBasicSettingsPageReadyToSave({
      title: 'AI application contest',
      category: 'OTHER',
      competitionLevel: 'NATIONAL',
      organizers: [{ role: 'Host', name: 'Contest committee' }],
      participationScope: 'Students',
      feeMode: 'TEAM',
      entryFeeMinor: 0,
      currency: 'CNY',
      locale: ['zh'],
    })).toBe(true);
  });

  it('keeps an incomplete basic page quiet instead of autosaving', () => {
    expect(isBasicSettingsPageReadyToSave({
      title: '',
      category: 'OTHER',
      competitionLevel: 'NATIONAL',
      organizers: [{ role: 'Host', name: 'Contest committee' }],
      participationScope: 'Students',
      feeMode: 'TEAM',
      entryFeeMinor: 0,
      currency: 'CNY',
      locale: ['zh'],
    })).toBe(false);
  });

  it('allows a timeline page to save independently from basic fields', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026.07.01 09:00', '2026.07.31 18:00'],
      schedules: [{ timeMode: 'TBD' }],
    })).toBe(true);
  });

  it('requires confirmed timeline rows to be complete before autosave', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026.07.01 09:00', '2026.07.31 18:00'],
      schedules: [{ timeMode: 'CONFIRMED', title: 'Final' }],
    })).toBe(false);
  });

  it('keeps field-module autosave quiet while a new row title is still blank', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '',
        itemKey: 'registration_field-1783613006',
        metadata: { fieldType: 'TEXT' },
      },
    ])).toBe(false);
  });

  it('allows field-module autosave after the new row becomes complete', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '学校',
        itemKey: 'school',
        metadata: { fieldType: 'TEXT' },
      },
    ])).toBe(true);
  });

  it('requires configured options for a select field', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '性别',
        itemKey: 'gender',
        metadata: { fieldType: 'SELECT', options: '' },
      },
    ])).toBe(false);

    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '性别',
        itemKey: 'gender',
        metadata: { fieldType: 'SELECT', options: '男\n女' },
      },
    ])).toBe(true);
  });

  it('requires configured options for a multi-select field', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '兴趣方向',
        itemKey: 'interests',
        metadata: { fieldType: 'MULTI_SELECT', options: '' },
      },
    ])).toBe(false);

    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '兴趣方向',
        itemKey: 'interests',
        metadata: { fieldType: 'MULTI_SELECT', options: '人工智能\n机器人' },
      },
    ])).toBe(true);
  });
});
