import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser, LoginResponse } from '@/types/api';

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  set: vi.fn(),
  remove: vi.fn(),
}));

vi.mock('@/cache/storage', () => ({
  storage: {
    get: mocks.get,
    set: mocks.set,
    remove: mocks.remove,
  },
}));

const trustedUser = (): CurrentUser => ({
  userId: 1001,
  userUuid: 'user-uuid-1001',
  username: 'operator',
  sessionId: 'session-1001',
  sessionVersion: 1,
  permissionsVersion: 'permissions-1',
  permissions: ['dashboard:view'],
});

const loginResponse = (overrides: Partial<LoginResponse['user']> = {}): LoginResponse => ({
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: {
    userId: 1001,
    userUuid: 'user-uuid-1001',
    username: 'operator',
    sessionId: 'session-1001',
    sessionVersion: 1,
    permissionsVersion: 'permissions-1',
    permissions: ['dashboard:view'],
    ...overrides,
  },
});

describe('sessionState', () => {
  beforeEach(() => {
    mocks.get.mockReset();
    mocks.set.mockReset();
    mocks.remove.mockReset();
    mocks.get.mockReturnValue(null);
  });

  it('persists a complete trusted current user tuple', async () => {
    const { persistCurrentUser } = await import('@/auth/sessionState');

    const currentUser = trustedUser();
    const persisted = persistCurrentUser(currentUser);

    expect(persisted).toBe(currentUser);
    expect(mocks.set).toHaveBeenCalledWith('current_user_profile', currentUser);
    expect(mocks.set).toHaveBeenCalledWith('current_session_meta', {
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
    });
  });

  it('rejects current users missing server-proven identity fields', async () => {
    const { persistCurrentUser } = await import('@/auth/sessionState');

    expect(() => persistCurrentUser({ ...trustedUser(), sessionId: '' })).toThrow(/trusted session identity/);
    expect(() => persistCurrentUser({ ...trustedUser(), sessionVersion: undefined })).toThrow(/trusted session identity/);
    expect(() => persistCurrentUser({ ...trustedUser(), permissionsVersion: undefined })).toThrow(/trusted session identity/);
    expect(mocks.set).not.toHaveBeenCalled();
  });

  it('allows legacy users whose uuid has not been backfilled yet', async () => {
    const { persistCurrentUser, buildFallbackCurrentUser } = await import('@/auth/sessionState');

    expect(() => persistCurrentUser({ ...trustedUser(), userUuid: null })).not.toThrow();
    const currentUser = buildFallbackCurrentUser(loginResponse({ userUuid: null }));

    expect(currentUser).toMatchObject({
      userId: 1001,
      userUuid: null,
      username: 'operator',
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
    });
  });

  it('does not build fallback users from stale local session metadata', async () => {
    mocks.get.mockImplementation((key: string) => {
      if (key === 'current_session_meta') {
        return {
          sessionId: 'stored-session',
          sessionVersion: 9,
          permissionsVersion: 'stored-permissions',
        };
      }
      if (key === 'current_user_profile') {
        return {
          permissions: ['*'],
        };
      }
      return null;
    });

    const { buildFallbackCurrentUser } = await import('@/auth/sessionState');

    expect(() =>
      buildFallbackCurrentUser(loginResponse({
        sessionId: undefined,
        sessionVersion: undefined,
        permissionsVersion: undefined,
        permissions: undefined,
      })),
    ).toThrow(/trusted session identity/);
  });

  it('builds fallback users only from trusted login response fields', async () => {
    const { buildFallbackCurrentUser } = await import('@/auth/sessionState');

    const currentUser = buildFallbackCurrentUser(loginResponse());

    expect(currentUser).toMatchObject({
      userId: 1001,
      userUuid: 'user-uuid-1001',
      username: 'operator',
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
      permissions: ['dashboard:view'],
      defaultHomePath: '/dashboard/home',
    });
  });

  it('merges profile updates without dropping trusted session identity fields', async () => {
    const { mergeTrustedCurrentUser } = await import('@/auth/sessionState');

    const merged = mergeTrustedCurrentUser(trustedUser(), {
      userId: 1001,
      username: 'operator',
      nickname: 'Updated name',
      permissions: ['dashboard:view', 'profile:update'],
    } as CurrentUser);

    expect(merged).toMatchObject({
      userId: 1001,
      userUuid: 'user-uuid-1001',
      username: 'operator',
      nickname: 'Updated name',
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
      permissions: ['dashboard:view', 'profile:update'],
    });
  });

  it('rejects untrusted profile updates when there is no trusted previous user', async () => {
    const { mergeTrustedCurrentUser } = await import('@/auth/sessionState');

    expect(() => mergeTrustedCurrentUser(undefined, { userId: 1001, username: 'operator' } as CurrentUser))
      .toThrow(/trusted session identity/);
  });
});
