import { describe, expect, it } from 'vitest';
import { resolveBuiltinFallbackMessage, shouldUseBuiltinFallback } from './builtinFallbackMessages';

describe('resolveBuiltinFallbackMessage', () => {
  it('returns a complete Chinese fallback for core login copy', () => {
    expect(resolveBuiltinFallbackMessage('page.login.error.pleaseEnterAccount', 'zh-CN')).toBe('请输入账号、手机号或邮箱');
    expect(resolveBuiltinFallbackMessage('page.login.remember', 'zh-CN')).toBe('记住我');
    expect(resolveBuiltinFallbackMessage('page.login.submit.login', 'zh-CN')).toBe('登录');
    expect(resolveBuiltinFallbackMessage('page.login.agreement.and', 'zh-CN')).toBe('和');
  });

  it('returns a complete English fallback for core login copy', () => {
    expect(resolveBuiltinFallbackMessage('page.login.passwordAccount', 'en-US')).toBe('Password login');
    expect(resolveBuiltinFallbackMessage('page.login.otherMethods', 'en-US')).toBe('Other login methods');
    expect(resolveBuiltinFallbackMessage('page.login.agreement.userPlain', 'en-US')).toBe('User Agreement');
  });

  it('does not override unrelated message ids', () => {
    expect(resolveBuiltinFallbackMessage('common.confirm', 'zh-CN')).toBeUndefined();
  });

  it('replaces a database value written in the wrong language', () => {
    expect(shouldUseBuiltinFallback('page.login.remember', 'Remember me', 'zh-CN')).toBe(true);
    expect(shouldUseBuiltinFallback('page.login.remember', '记住我', 'zh-CN')).toBe(false);
    expect(shouldUseBuiltinFallback('page.login.passwordAccount', '密码登录', 'en-US')).toBe(true);
  });
});
