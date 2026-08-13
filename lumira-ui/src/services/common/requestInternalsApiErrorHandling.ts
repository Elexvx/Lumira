import { message } from '@/theme/antdFeedbackBridge';
import { buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { ErrorCode } from '@/enums/errorCode';
import { tokenManager } from '@/auth/token';
import { withAuthSessionMutationLock } from '@/auth/authSessionMutationLock';
import type { RequestOptions } from './requestInternalsTypes';
import { ApiRequestError } from './requestInternalsTypes';

export interface ApiErrorHandlingContext {
  authenticatedRefreshSucceeded?: boolean;
  refreshSuperseded?: boolean;
  refreshTemporarilyUnavailable?: boolean;
}

const shouldSuppressForbiddenFeedback = (
  error: ApiRequestError,
  authSnapshot: AuthRequestSnapshot,
) => {
  if (error.code !== ErrorCode.FORBIDDEN) {
    return false;
  }

  const runtime = buildUnauthorizedRuntimeState();
  if (runtime.pathname === '/user/login' || runtime.loginInProgress) {
    return true;
  }

  return (
    authSnapshot.hasAuthToken &&
    shouldSuppressUnauthorizedSideEffects(authSnapshot, runtime)
  );
};

const runAfterAuthSnapshotRevalidation = (
  authSnapshot: AuthRequestSnapshot,
  action: () => Promise<void> | void,
) => {
  const revalidateAndRun = async () => {
    if (authSnapshot.hasAuthToken) {
      // A role switch or refresh in another tab can commit while this stale
      // request is returning 401. The shared mutation lock lets that writer
      // persist its new token before we decide whether logout is still valid.
      tokenManager.syncFromStorage(authSnapshot.accessToken);
      if (
        shouldSuppressUnauthorizedSideEffects(
          authSnapshot,
          buildUnauthorizedRuntimeState(),
        )
      ) {
        return;
      }
    }

    await action();
  };

  const pending = authSnapshot.hasAuthToken
    ? withAuthSessionMutationLock(revalidateAndRun)
    : revalidateAndRun();
  void pending.catch(() => undefined);
};

const triggerForcedSessionLogout = (
  authSnapshot: AuthRequestSnapshot,
  onConfirmed: () => void,
) => {
  runAfterAuthSnapshotRevalidation(authSnapshot, async () => {
    onConfirmed();
    const { performLogout } = await import('@/auth/sessionLifecycle');
    await performLogout({ reason: 'forced_expired' });
  });
};

export const handleApiError = (
  error: ApiRequestError,
  options: RequestOptions,
  authSnapshot: AuthRequestSnapshot,
  context: ApiErrorHandlingContext = {},
) => {
  if (shouldSuppressForbiddenFeedback(error, authSnapshot)) {
    return;
  }

  const feedback = resolveApiErrorFeedback(error, authSnapshot.hasAuthToken);
  const notify = () => {
    switch (feedback.type) {
      case 'info':
        message.info(feedback.message);
        break;
      case 'warning':
        message.warning(feedback.message);
        break;
      case 'error':
        message.error(feedback.message);
        break;
    }
  };

  if (!feedback.redirectToLogin) {
    if (!options.silent) {
      if (error.code === ErrorCode.FORBIDDEN && authSnapshot.hasAuthToken) {
        runAfterAuthSnapshotRevalidation(authSnapshot, notify);
      } else {
        notify();
      }
    }
    return;
  }

  if (options.preserveAuthSessionOnUnauthorized === true) {
    if (!options.silent) {
      notify();
    }
    return;
  }

  if (
    (!authSnapshot.hasAuthToken &&
      (options.allowUnauthorizedWithoutRedirect === true || options.autoRedirectOnUnauthorized === false)) ||
    context.refreshSuperseded === true ||
    context.refreshTemporarilyUnavailable === true ||
    (context.authenticatedRefreshSucceeded === true && options.forceSessionLogoutOnUnauthorized !== true) ||
    shouldSuppressUnauthorizedSideEffects(authSnapshot, buildUnauthorizedRuntimeState())
  ) {
    if (!options.silent && (context.authenticatedRefreshSucceeded || context.refreshTemporarilyUnavailable)) {
      message.warning(feedback.message);
    }
    return;
  }

  triggerForcedSessionLogout(authSnapshot, () => {
    if (!options.silent || options.notifyOnUnauthorized === true) {
      notify();
    }
  });
};
