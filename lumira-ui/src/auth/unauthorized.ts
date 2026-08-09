import { history } from '@umijs/max';
import { getAuthSessionEpoch, isBootstrapInProgress, isLoginInProgress } from '@/auth/loginFlowState';
import { tokenManager } from '@/auth/token';
import { isRoleSwitchInProgress } from '@/auth/roleSwitchFlowState';
import type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';
export type { AuthRequestSnapshot, UnauthorizedRuntimeState } from '@/auth/unauthorizedDecision';

export const captureAuthRequestSnapshot = (skipAuth = false): AuthRequestSnapshot => ({
  skipAuth,
  accessToken: skipAuth ? '' : tokenManager.getAccessToken(),
  hasAuthToken: skipAuth ? false : tokenManager.hasToken(),
  authSessionEpoch: getAuthSessionEpoch(),
  tokenGeneration: tokenManager.getTokenGeneration(),
});

const currentPathname = () => history?.location?.pathname
  || (typeof window !== 'undefined' ? window.location?.pathname : undefined)
  || '/';

export const buildUnauthorizedRuntimeState = (pathname = currentPathname()): UnauthorizedRuntimeState => ({
  pathname,
  currentAccessToken: tokenManager.getAccessToken(),
  currentAuthSessionEpoch: getAuthSessionEpoch(),
  currentTokenGeneration: tokenManager.getTokenGeneration(),
  loginInProgress: isLoginInProgress(),
  bootstrapInProgress: isBootstrapInProgress(),
  roleSwitchInProgress: isRoleSwitchInProgress(),
});
