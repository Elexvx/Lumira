export const AUTH_SESSION_MUTATION_LOCK_NAME = 'lumira-auth-session-mutation';

export interface AuthSessionMutationLockManager {
  request: <T>(name: string, callback: () => Promise<T> | T) => Promise<T>;
}

const resolveBrowserLockManager = (): AuthSessionMutationLockManager | null => {
  try {
    if (typeof navigator === 'undefined' || !navigator.locks) {
      return null;
    }
    const lockManager = navigator.locks;
    return {
      request: (name, callback) => lockManager.request(name, () => callback()),
    };
  } catch {
    return null;
  }
};

export const withAuthSessionMutationLock = async <T>(
  action: () => Promise<T> | T,
  lockManager: AuthSessionMutationLockManager | null = resolveBrowserLockManager(),
): Promise<T> => {
  if (!lockManager) {
    return action();
  }

  let actionStarted = false;
  try {
    return await lockManager.request(AUTH_SESSION_MUTATION_LOCK_NAME, async () => {
      actionStarted = true;
      return action();
    });
  } catch (error) {
    // A browser can expose navigator.locks but reject acquisition in a
    // restricted context. Fall back only before the callback starts so an
    // action failure is never executed a second time.
    if (!actionStarted) {
      return action();
    }
    throw error;
  }
};
