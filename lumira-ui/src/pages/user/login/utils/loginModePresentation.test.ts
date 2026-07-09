import { describe, expect, it } from 'vitest';
import { resolvePresentedLoginMode, resolvePresentedLoginModes } from './loginModePresentation';

describe('login mode presentation', () => {
  it('keeps all backend-enabled modes on mobile', () => {
    expect(resolvePresentedLoginModes(true, ['password', 'sms', 'wechat', 'passkey'])).toEqual(['password', 'sms', 'wechat', 'passkey']);
  });

  it('keeps desktop forms aligned with backend modes while avoiding duplicate wechat panels', () => {
    expect(resolvePresentedLoginModes(false, ['password', 'sms', 'wechat', 'passkey'])).toEqual(['password', 'sms', 'passkey']);
  });

  it('falls back to the first desktop-capable mode when desktop active mode points at wechat', () => {
    expect(resolvePresentedLoginMode(false, 'wechat', ['password', 'wechat', 'passkey'])).toBe('password');
  });

  it('keeps wechat when it is the only backend-enabled mode', () => {
    expect(resolvePresentedLoginModes(false, ['wechat'])).toEqual(['wechat']);
    expect(resolvePresentedLoginMode(false, 'wechat', ['wechat'])).toBe('wechat');
  });
});
