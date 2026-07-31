import { describe, expect, it, vi } from 'vitest';
import { consumeWechatOAuthCallback } from './wechatOAuthCallback';

describe('consumeWechatOAuthCallback', () => {
  it('removes the single-use callback before returning it for exchange', () => {
    const replaceLocation = vi.fn();

    const callback = consumeWechatOAuthCallback({
      locationSearch: '?code=code-1&state=state-1',
      locationPathname: '/user/login',
      loginAvailable: true,
      replaceLocation,
    });

    expect(replaceLocation).toHaveBeenCalledWith('/user/login');
    expect(callback).toEqual({ code: 'code-1', state: 'state-1' });
  });

  it('does not consume incomplete or unavailable callbacks', () => {
    const replaceLocation = vi.fn();

    expect(consumeWechatOAuthCallback({
      locationSearch: '?code=code-1',
      locationPathname: '/user/login',
      loginAvailable: true,
      replaceLocation,
    })).toBeNull();
    expect(consumeWechatOAuthCallback({
      locationSearch: '?code=code-1&state=state-1',
      locationPathname: '/user/login',
      loginAvailable: false,
      replaceLocation,
    })).toBeNull();
    expect(replaceLocation).not.toHaveBeenCalled();
  });
});
