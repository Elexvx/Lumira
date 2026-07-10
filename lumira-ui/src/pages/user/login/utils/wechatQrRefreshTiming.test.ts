import { describe, expect, it } from 'vitest';
import { resolveWechatQrRefreshDelayMs } from './wechatQrRefreshTiming';

describe('resolveWechatQrRefreshDelayMs', () => {
  it('falls back to a safe refresh interval when ttl is missing', () => {
    expect(resolveWechatQrRefreshDelayMs()).toBe(240000);
  });

  it('refreshes one-minute states before they expire', () => {
    expect(resolveWechatQrRefreshDelayMs(1)).toBe(45000);
  });

  it('refreshes ten-minute states one minute early', () => {
    expect(resolveWechatQrRefreshDelayMs(10)).toBe(540000);
  });
});
