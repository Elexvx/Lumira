import { describe, expect, it, vi } from 'vitest';
import {
  SESSION_EXPIRED_NOTICE_DURATION_SECONDS,
  SESSION_EXPIRED_NOTICE_KEY,
  showSessionExpiredNotice,
} from './sessionExpiredNotice';

describe('showSessionExpiredNotice', () => {
  it('shows a keyed warning bubble without reserving form layout space', () => {
    const warning = vi.fn();

    showSessionExpiredNotice({ warning }, '登录状态已失效，请重新登录');

    expect(warning).toHaveBeenCalledOnce();
    expect(warning).toHaveBeenCalledWith({
      key: SESSION_EXPIRED_NOTICE_KEY,
      content: '登录状态已失效，请重新登录',
      duration: SESSION_EXPIRED_NOTICE_DURATION_SECONDS,
    });
  });
});
