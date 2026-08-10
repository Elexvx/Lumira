import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser, LoginResponse } from '@/types/api';

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
  beforeEach(async () => {
    const { clearStoredSessionState } = await import('@/auth/sessionState');
    clearStoredSessionState();
  });

  it('persists a complete trusted current user tuple', async () => {
    const { getStoredCurrentUser, getStoredSessionMeta, persistCurrentUser } = await import('@/auth/sessionState');

    const currentUser = trustedUser();
    const persisted = persistCurrentUser(currentUser);

    expect(persisted).toBe(currentUser);
    expect(getStoredCurrentUser()).toBe(currentUser);
    expect(getStoredSessionMeta()).toEqual({
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
    });
  });

  it('notifies consumers when the trusted session changes', async () => {
    const { clearStoredSessionState, persistCurrentUser, subscribeSessionState } = await import('@/auth/sessionState');
    const listener = vi.fn();
    const unsubscribe = subscribeSessionState(listener);

    persistCurrentUser(trustedUser());
    expect(listener).toHaveBeenCalledTimes(1);

    clearStoredSessionState();
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    persistCurrentUser(trustedUser());
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('rejects current users missing server-proven identity fields', async () => {
    const { persistCurrentUser } = await import('@/auth/sessionState');

    expect(() => persistCurrentUser({ ...trustedUser(), sessionId: '' })).toThrow(/trusted session identity/);
    expect(() => persistCurrentUser({ ...trustedUser(), sessionVersion: undefined })).toThrow(/trusted session identity/);
    expect(() => persistCurrentUser({ ...trustedUser(), permissionsVersion: undefined })).toThrow(/trusted session identity/);
    const { getStoredCurrentUser } = await import('@/auth/sessionState');
    expect(getStoredCurrentUser()).toBeNull();
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
      permissions: ['dashboard:view', 'profile:view'],
    } as CurrentUser);

    expect(merged).toMatchObject({
      userId: 1001,
      userUuid: 'user-uuid-1001',
      username: 'operator',
      nickname: 'Updated name',
      sessionId: 'session-1001',
      sessionVersion: 1,
      permissionsVersion: 'permissions-1',
      permissions: ['dashboard:view', 'profile:view'],
    });
  });

  it('keeps the stable UUID when a trusted same-user summary omits it', async () => {
    const { mergeTrustedCurrentUser } = await import('@/auth/sessionState');

    const merged = mergeTrustedCurrentUser(trustedUser(), {
      ...trustedUser(),
      userUuid: undefined,
      nickname: 'Summary name',
    });

    expect(merged.userUuid).toBe('user-uuid-1001');
    expect(merged.nickname).toBe('Summary name');
  });

  it('rejects cached summaries from a different user or session', async () => {
    const { mergeSameSessionCurrentUser } = await import('@/auth/sessionState');
    const current = trustedUser();

    expect(mergeSameSessionCurrentUser(current, {
      ...trustedUser(),
      userId: 2002,
      userUuid: 'user-uuid-2002',
      username: 'other',
      sessionId: 'session-2002',
    })).toBe(current);
    expect(mergeSameSessionCurrentUser(current, {
      ...trustedUser(),
      sessionId: 'new-session-1001',
    })).toBe(current);
  });

  it('rejects untrusted profile updates when there is no trusted previous user', async () => {
    const { mergeTrustedCurrentUser } = await import('@/auth/sessionState');

    expect(() => mergeTrustedCurrentUser(undefined, { userId: 1001, username: 'operator' } as CurrentUser))
      .toThrow(/trusted session identity/);
  });
});
