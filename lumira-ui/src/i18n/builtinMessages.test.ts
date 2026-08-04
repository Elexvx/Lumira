import { describe, expect, it } from 'vitest';
import {
  EN_US_BUILTIN_MESSAGES,
  isMessageCompatibleWithLocale,
  ZH_CN_BUILTIN_MESSAGES,
  resolveBuiltinLocaleMessage,
} from './builtinMessages';

describe('builtin login messages', () => {
  it('keeps the Chinese and English catalogs structurally aligned', () => {
    expect(Object.keys(ZH_CN_BUILTIN_MESSAGES).sort()).toEqual(
      Object.keys(EN_US_BUILTIN_MESSAGES).sort(),
    );
  });

  it('resolves login copy from the requested locale', () => {
    expect(resolveBuiltinLocaleMessage('zh-CN', 'page.login.submit.login')).toBe('登录');
    expect(resolveBuiltinLocaleMessage('en-US', 'page.login.submit.login')).toBe('Log in');
    expect(resolveBuiltinLocaleMessage('zh', 'page.login.agreement.and')).toBe('和');
  });

  it('returns undefined for messages outside the built-in fallback catalog', () => {
    expect(resolveBuiltinLocaleMessage('zh-CN', 'page.unknown')).toBeUndefined();
  });

  it('rejects stale translations that use the wrong writing system', () => {
    expect(isMessageCompatibleWithLocale('zh-CN', 'Remember me', '记住我')).toBe(false);
    expect(isMessageCompatibleWithLocale('zh-CN', '记住此设备', '记住我')).toBe(true);
    expect(isMessageCompatibleWithLocale('en-US', '记住我', 'Remember me')).toBe(false);
    expect(isMessageCompatibleWithLocale('en-US', 'Keep me signed in', 'Remember me')).toBe(true);
  });
});
