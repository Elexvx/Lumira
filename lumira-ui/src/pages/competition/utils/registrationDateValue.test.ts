import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  formatRegistrationYearValue,
  isRegistrationYearField,
  normalizeRegistrationDateValue,
} from './registrationDateValue';

describe('normalizeRegistrationDateValue', () => {
  it('keeps a valid Dayjs value', () => {
    const value = dayjs('2026-09-01');

    expect(normalizeRegistrationDateValue(value)).toBe(value);
  });

  it('restores a serialized registration date', () => {
    expect(normalizeRegistrationDateValue('2026-09-01T00:00:00.000Z')?.isValid()).toBe(true);
    expect(normalizeRegistrationDateValue('2026.09.01')?.format('YYYY-MM-DD')).toBe('2026-09-01');
  });

  it('rejects values that DatePicker cannot consume', () => {
    expect(normalizeRegistrationDateValue('not-a-date')).toBeUndefined();
    expect(normalizeRegistrationDateValue({ value: '2026-09-01' })).toBeUndefined();
  });
});

describe('registration year fields', () => {
  it('recognizes enrollment and graduation fields only', () => {
    expect(isRegistrationYearField('enrollmentDate')).toBe(true);
    expect(isRegistrationYearField('graduation_date')).toBe(true);
    expect(isRegistrationYearField('grantDate')).toBe(false);
  });

  it('displays existing date snapshots as a four-digit year', () => {
    expect(formatRegistrationYearValue('2026-07-01')).toBe('2026');
    expect(formatRegistrationYearValue('2025-07-18T16:00:00.000Z')).toBe('2025');
    expect(formatRegistrationYearValue('2029')).toBe('2029');
    expect(formatRegistrationYearValue('invalid')).toBeUndefined();
  });
});
