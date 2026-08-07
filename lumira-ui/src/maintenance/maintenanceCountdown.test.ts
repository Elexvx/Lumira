import { describe, expect, it } from 'vitest';
import {
  formatMaintenanceCountdown,
  getMaintenanceRemainingSeconds,
} from './maintenanceCountdown';

describe('maintenance countdown', () => {
  it('does not create a countdown for an empty or invalid value', () => {
    expect(getMaintenanceRemainingSeconds('')).toBeNull();
    expect(getMaintenanceRemainingSeconds('invalid')).toBeNull();
  });

  it('returns the remaining seconds and clamps an expired countdown to zero', () => {
    const endAt = '2026-08-07T12:00:10.000Z';
    const now = Date.parse('2026-08-07T12:00:00.500Z');

    expect(getMaintenanceRemainingSeconds(endAt, now)).toBe(10);
    expect(getMaintenanceRemainingSeconds(endAt, Date.parse('2026-08-07T12:00:11Z'))).toBe(0);
  });

  it('formats hours, minutes, seconds, and multi-day durations predictably', () => {
    expect(formatMaintenanceCountdown(0)).toBe('00:00:00');
    expect(formatMaintenanceCountdown(3661)).toBe('01:01:01');
    expect(formatMaintenanceCountdown(90_061)).toBe('1d 01:01:01');
  });
});
