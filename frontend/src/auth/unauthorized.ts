import { history } from '@umijs/max';
import { getAuthSessionEpoch, isLoginInProgress } from '@/auth/loginFlowState';
import { tokenManager } from '@/auth/token';
import type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';
export type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';

export const captureAuthRequestSnapshot = (skipAuth = false): AuthRequestSnapshot => ({
  skipAuth,
  accessToken: skipAuth ? '' : tokenManager.getAccessToken(),
  hasAuthToken: skipAuth ? false : tokenManager.hasToken(),
  authSessionEpoch: getAuthSessionEpoch(),
});

export const buildUnauthorizedRuntimeState = (pathname = history.location.pathname): UnauthorizedRuntimeState => ({
  pathname,
  currentAccessToken: tokenManager.getAccessToken(),
  currentAuthSessionEpoch: getAuthSessionEpoch(),
  loginInProgress: isLoginInProgress(),
});
