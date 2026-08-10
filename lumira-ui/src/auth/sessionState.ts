import type { CurrentUser, LoginResponse } from '@/types/api';

let currentUserSnapshot: CurrentUser | null = null;
let sessionMetaSnapshot: SessionMetaState | null = null;
const sessionStateListeners = new Set<() => void>();

const notifySessionStateListeners = () => {
  sessionStateListeners.forEach((listener) => listener());
};

export interface SessionMetaState {
  sessionId?: string;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export const getStoredCurrentUser = (): CurrentUser | null => currentUserSnapshot;

export const getStoredSessionMeta = (): SessionMetaState | null => sessionMetaSnapshot;

export const clearStoredSessionState = () => {
  currentUserSnapshot = null;
  sessionMetaSnapshot = null;
  notifySessionStateListeners();
};

export const subscribeSessionState = (listener: () => void) => {
  sessionStateListeners.add(listener);
  return () => {
    sessionStateListeners.delete(listener);
  };
};

export const persistSessionMeta = (meta: SessionMetaState) => {
  sessionMetaSnapshot = {
    ...getStoredSessionMeta(),
    ...meta,
  };
};

export const isTrustedCurrentUser = (currentUser?: CurrentUser | null): currentUser is CurrentUser =>
  Boolean(
      currentUser &&
      currentUser.userId > 0 &&
      currentUser.username?.trim() &&
      currentUser.sessionId?.trim() &&
      currentUser.permissionsVersion?.trim() &&
      typeof currentUser.sessionVersion === 'number' &&
      currentUser.sessionVersion > 0,
  );

const assertTrustedCurrentUser = (currentUser: CurrentUser): CurrentUser => {
  if (!isTrustedCurrentUser(currentUser)) {
    throw new Error('Current user is missing trusted session identity fields');
  }
  return currentUser;
};

export const persistCurrentUser = (currentUser: CurrentUser): CurrentUser => {
  const normalizedCurrentUser = assertTrustedCurrentUser(currentUser);
  currentUserSnapshot = normalizedCurrentUser;
  persistSessionMeta({
    sessionId: normalizedCurrentUser.sessionId,
    sessionVersion: normalizedCurrentUser.sessionVersion,
    permissionsVersion: normalizedCurrentUser.permissionsVersion,
  });
  notifySessionStateListeners();
  return normalizedCurrentUser;
};

export const mergeTrustedCurrentUser = (previous: CurrentUser | undefined, next: CurrentUser): CurrentUser => {
  if (!previous) {
    return assertTrustedCurrentUser(next);
  }
  const nextIsTrusted: boolean = isTrustedCurrentUser(next);
  if (nextIsTrusted) {
    const sameUser = previous.userId === next.userId && previous.username === next.username;
    return assertTrustedCurrentUser({
      ...next,
      userUuid: next.userUuid?.trim() || (sameUser ? previous.userUuid : next.userUuid),
    });
  }
  const candidate: CurrentUser = {
    ...previous,
    ...next,
    userId: previous.userId,
    userUuid: previous.userUuid,
    username: previous.username,
    sessionId: previous.sessionId,
    sessionVersion: previous.sessionVersion,
    permissionsVersion: previous.permissionsVersion,
    permissions: next.permissions ?? previous.permissions,
    roleIds: next.roleIds ?? previous.roleIds,
  };
  return assertTrustedCurrentUser(candidate);
};

export const mergeSameSessionCurrentUser = (
  current: CurrentUser | undefined,
  candidate: CurrentUser | undefined,
): CurrentUser | undefined => {
  if (!candidate) {
    return current;
  }
  if (!current) {
    return candidate;
  }
  if (current.userId !== candidate.userId || current.sessionId !== candidate.sessionId) {
    return current;
  }
  return mergeTrustedCurrentUser(current, candidate);
};

export const buildFallbackCurrentUser = (loginResponse: LoginResponse): CurrentUser => {
  return assertTrustedCurrentUser({
    userId: loginResponse.user.userId,
    userUuid: loginResponse.user.userUuid,
    username: loginResponse.user.username,
    nickname: loginResponse.user.nickname,
    realName: loginResponse.user.realName,
    avatarUrl: loginResponse.user.avatarUrl,
    mobile: loginResponse.user.mobile ?? null,
    email: loginResponse.user.email ?? null,
    birthMonth: loginResponse.user.birthMonth ?? null,
    gender: loginResponse.user.gender ?? null,
    region: loginResponse.user.region ?? null,
    availableTime: loginResponse.user.availableTime ?? null,
    idCardNumber: loginResponse.user.idCardNumber ?? null,
    locale: loginResponse.user.locale ?? null,
    simulatedRoleId: null,
    availableRoles: [],
    sessionId: loginResponse.user.sessionId?.trim() || '',
    permissionsVersion: loginResponse.user.permissionsVersion,
    sessionVersion: loginResponse.user.sessionVersion,
    permissions: loginResponse.user.permissions || [],
    roleIds: loginResponse.user.roleIds || [],
    requiresPasswordChange: loginResponse.requiresPasswordChange ?? null,
    defaultHomePath: '/dashboard/home',
  });
};
