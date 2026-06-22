import { describe, expect, it, vi } from 'vitest';
import access from './access';
import type { CurrentUser } from './types/api';

vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: () => false,
  },
}));

const userWithPermissions = (permissions: string[]): CurrentUser =>
  ({
    userId: 2002,
    username: 'operator',
    sessionId: 'session-1',
    permissions,
    availableRoles: [{ roleCode: 'operator', roleName: 'Operator' }],
  }) as CurrentUser;

describe('access', () => {
  it('allows personalization settings for users with system config view permission', () => {
    const result = access({ currentUser: userWithPermissions(['system:config:view']) });

    expect(result.canVisitSystemPersonalization).toBe(true);
  });

  it('allows personalization settings for users with system config update permission', () => {
    const result = access({ currentUser: userWithPermissions(['system:config:update']) });

    expect(result.canVisitSystemPersonalization).toBe(true);
  });

  it('does not expose personalization settings to unrelated permissions', () => {
    const result = access({ currentUser: userWithPermissions(['system:user:view']) });

    expect(result.canVisitSystemPersonalization).toBe(false);
  });
});
