import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import { normalizeRegistrationDateValue } from './registrationDateValue';

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
