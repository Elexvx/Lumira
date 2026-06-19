export interface AuthRequestSnapshot {
  skipAuth: boolean;
  accessToken: string;
  hasAuthToken: boolean;
  authSessionEpoch: number;
  tokenGeneration: number;
}

export interface UnauthorizedRuntimeState {
  pathname: string;
  currentAccessToken: string;
  currentAuthSessionEpoch: number;
  currentTokenGeneration: number;
  loginInProgress: boolean;
  bootstrapInProgress: boolean;
}

export const shouldSuppressUnauthorizedSideEffects = (
  snapshot: AuthRequestSnapshot,
  runtime: UnauthorizedRuntimeState,
) => {
  if (snapshot.skipAuth) {
    return true;
  }

  if (runtime.loginInProgress || runtime.bootstrapInProgress || runtime.pathname === '/user/login') {
    return true;
  }

  if (snapshot.authSessionEpoch !== runtime.currentAuthSessionEpoch) {
    return true;
  }

  if (snapshot.tokenGeneration !== runtime.currentTokenGeneration) {
    return true;
  }

  if (!snapshot.accessToken) {
    return !runtime.currentAccessToken;
  }

  return snapshot.accessToken !== runtime.currentAccessToken;
};
