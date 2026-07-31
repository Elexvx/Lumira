import { describe, expect, it } from 'vitest';

import { resolveActivityCtaTarget } from './activityCtaTarget';

describe('resolveActivityCtaTarget', () => {
  it('normalizes safe internal activity links', () => {
    expect(resolveActivityCtaTarget('/activities/register?id=1')).toEqual({
      kind: 'internal',
      href: '/activities/register?id=1',
    });
    expect(resolveActivityCtaTarget('activities/register')).toEqual({
      kind: 'internal',
      href: '/activities/register',
    });
  });

  it('keeps explicit HTTP links external', () => {
    expect(resolveActivityCtaTarget('https://events.example/register')).toEqual({
      kind: 'external',
      href: 'https://events.example/register',
    });
  });

  it('rejects protocol-relative, backslash, and executable-scheme targets', () => {
    expect(resolveActivityCtaTarget('//evil.example/steal')).toBeNull();
    expect(resolveActivityCtaTarget('/\\evil.example/steal')).toBeNull();
    expect(resolveActivityCtaTarget('activities\\secret')).toBeNull();
    expect(resolveActivityCtaTarget('javascript:alert(1)')).toBeNull();
    expect(resolveActivityCtaTarget('data:text/html,unsafe')).toBeNull();
  });
});
