import { describe, expect, it } from 'vitest';
import { resolvePresentedLoginMode, resolvePresentedLoginModes } from './loginModePresentation';

describe('login mode presentation', () => {
  it('keeps all backend-enabled modes on mobile without duplicating wechat', () => {
    expect(resolvePresentedLoginModes(true, ['password', 'sms', 'wechat', 'passkey'])).toEqual(['password', 'sms', 'wechat', 'passkey']);
  });

  it('keeps wechat in the unified desktop login modes', () => {
    expect(resolvePresentedLoginModes(false, ['password', 'sms', 'wechat', 'passkey'])).toEqual(['password', 'sms', 'wechat', 'passkey']);
  });

  it('does not present disabled wechat when backend capabilities exclude it', () => {
    expect(resolvePresentedLoginModes(false, ['password'])).toEqual(['password']);
    expect(resolvePresentedLoginMode(false, 'wechat', ['password'])).toBe('password');
    expect(resolvePresentedLoginModes(false, [])).toEqual(['password']);
    expect(resolvePresentedLoginMode(false, 'wechat', [])).toBe('password');
  });

  it('keeps wechat when it is the only backend-enabled mode', () => {
    expect(resolvePresentedLoginModes(false, ['wechat'])).toEqual(['wechat']);
    expect(resolvePresentedLoginMode(false, 'wechat', ['wechat'])).toBe('wechat');
  });
});
