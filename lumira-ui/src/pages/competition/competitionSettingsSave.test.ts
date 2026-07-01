import { describe, expect, it } from 'vitest';
import { isBasicSettingsPageReadyToSave, isTimelineSettingsPageReadyToSave } from './competitionSettingsSave';

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
});
