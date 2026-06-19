import { describe, expect, it } from 'vitest';
import { resolveRouteAccessStatus } from '@/auth/loginRedirect';

describe('route access for AI sharing', () => {
  it('allows the share page without AI assistant permissions', () => {
    expect(
      resolveRouteAccessStatus('/ai/share/demo-token', {
        userId: 1,
        username: 'guest',
        permissions: [],
        sessionId: 'session-guest',
      }),
    ).toBe('allowed');
  });
});
