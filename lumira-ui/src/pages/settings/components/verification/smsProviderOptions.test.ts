import { describe, expect, it } from 'vitest';
import { resolveSelectedSmsProviderCode, resolveVisibleSmsProviderCodes } from './smsProviderOptions';

describe('SMS provider visibility', () => {
  it('shows the mock provider only while the plugin is available', () => {
    expect(resolveVisibleSmsProviderCodes(false)).toEqual(['aliyun']);
    expect(resolveVisibleSmsProviderCodes(undefined)).toEqual(['aliyun']);
    expect(resolveVisibleSmsProviderCodes(true)).toEqual(['aliyun', 'builtin_mock_sms']);
  });

  it('does not silently fall back to Aliyun when a disabled mock provider remains saved', () => {
    expect(resolveSelectedSmsProviderCode('builtin_mock_sms', false)).toBeUndefined();
    expect(resolveSelectedSmsProviderCode('builtin_mock_sms', true)).toBe('builtin_mock_sms');
    expect(resolveSelectedSmsProviderCode('aliyun', false)).toBe('aliyun');
  });
});
