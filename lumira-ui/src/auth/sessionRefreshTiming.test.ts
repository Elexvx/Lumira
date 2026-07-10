import { describe, expect, it } from 'vitest';
import { resolveTokenRefreshDelayMs } from './sessionRefreshTiming';

describe('resolveTokenRefreshDelayMs', () => {
  it('keeps the legacy 60-second skew for long-lived access tokens', () => {
    expect(resolveTokenRefreshDelayMs(30 * 60 * 1000)).toBe(29 * 60 * 1000);
  });

  it('uses a proportional skew for short-lived access tokens', () => {
    expect(resolveTokenRefreshDelayMs(30_000)).toBe(24_000);
    expect(resolveTokenRefreshDelayMs(5_000)).toBe(4_000);
  });

  it('refreshes immediately when the token is already expired or unusable', () => {
    expect(resolveTokenRefreshDelayMs(0)).toBe(0);
    expect(resolveTokenRefreshDelayMs(-1)).toBe(0);
    expect(resolveTokenRefreshDelayMs(Number.NaN)).toBe(0);
  });
});
