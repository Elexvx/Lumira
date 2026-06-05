import { describe, expect, it } from 'vitest';
import { sanitizeLoginInputValue, shouldBlockLoginInputKey, shouldBlockLoginInputPaste } from './hooks/useLoginFlowRuntime';

describe('loginInputGuards', () => {
  it('removes unsupported account characters while keeping only conservative email-safe symbols', () => {
    expect(sanitizeLoginInputValue('admin*&^%$#@!?:|+_-.<script>@example.com', 'account')).toBe('admin@_-.script@example.com');
    expect(sanitizeLoginInputValue('test+owner@example.com', 'email')).toBe('testowner@example.com');
  });

  it('keeps mobile and verification code inputs inside backend-compatible formats', () => {
    expect(sanitizeLoginInputValue('13a8 0000-0000', 'mobile')).toBe('13800000000');
    expect(sanitizeLoginInputValue('12-AB<script>', 'verificationCode')).toBe('12ABscript');
  });

  it('blocks unsafe typing and paste payloads', () => {
    expect(shouldBlockLoginInputKey('account', { key: '<' })).toBe(true);
    expect(shouldBlockLoginInputKey('email', { key: '+' })).toBe(true);
    expect(shouldBlockLoginInputKey('verificationCode', { key: '-' })).toBe(true);
    expect(shouldBlockLoginInputPaste('account', { clipboardData: { getData: () => 'admin;drop' } })).toBe(true);
  });
});
