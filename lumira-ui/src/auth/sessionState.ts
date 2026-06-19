import { storage } from '@/cache/storage';
import type { CurrentUser, LoginResponse } from '@/types/api';

const USER_PROFILE_KEY = 'current_user_profile';
const SESSION_META_KEY = 'current_session_meta';
const LOCAL_SESSION_ID_PREFIX = 'local-session';

export interface SessionMetaState {
  sessionId?: string;
  sessionVersion?: number;
  permissionsVersion?: string;
}

export const getStoredCurrentUser = (): CurrentUser | null => storage.get<CurrentUser>(USER_PROFILE_KEY);

export const getStoredSessionMeta = (): SessionMetaState | null => storage.get<SessionMetaState>(SESSION_META_KEY);

export const clearStoredSessionState = () => {
  storage.remove(USER_PROFILE_KEY);
  storage.remove(SESSION_META_KEY);
};

export const persistSessionMeta = (meta: SessionMetaState) => {
  storage.set(SESSION_META_KEY, {
    ...getStoredSessionMeta(),
    ...meta,
  });
};

export const persistCurrentUser = (currentUser: CurrentUser): CurrentUser => {
  const normalizedCurrentUser = normalizeCurrentUserSession(currentUser);
  storage.set(USER_PROFILE_KEY, normalizedCurrentUser);
  persistSessionMeta({
    sessionId: normalizedCurrentUser.sessionId,
    sessionVersion: normalizedCurrentUser.sessionVersion,
    permissionsVersion: normalizedCurrentUser.permissionsVersion,
  });
  return normalizedCurrentUser;
};

export const buildFallbackCurrentUser = (loginResponse: LoginResponse): CurrentUser => {
  const storedCurrentUser = getStoredCurrentUser();
  const storedSessionMeta = getStoredSessionMeta();
  const sessionId = loginResponse.user.sessionId?.trim() || storedSessionMeta?.sessionId?.trim() || createLocalSessionId();
  return {
    userId: loginResponse.user.userId,
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
    sessionId,
    permissionsVersion: loginResponse.user.permissionsVersion ?? storedSessionMeta?.permissionsVersion,
    sessionVersion: loginResponse.user.sessionVersion ?? storedSessionMeta?.sessionVersion,
    permissions: loginResponse.user.permissions || storedCurrentUser?.permissions || [],
    requiresPasswordChange: loginResponse.requiresPasswordChange ?? null,
    defaultHomePath: storedCurrentUser?.defaultHomePath || '/dashboard/home',
  };
};

const normalizeCurrentUserSession = (currentUser: CurrentUser): CurrentUser => {
  if (currentUser.sessionId?.trim()) {
    return currentUser;
  }

  const storedSessionMeta = getStoredSessionMeta();
  return {
    ...currentUser,
    sessionId: storedSessionMeta?.sessionId?.trim() || createLocalSessionId(),
  };
};

const createLocalSessionId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `${LOCAL_SESSION_ID_PREFIX}-${crypto.randomUUID()}`;
  }

  return `${LOCAL_SESSION_ID_PREFIX}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};
