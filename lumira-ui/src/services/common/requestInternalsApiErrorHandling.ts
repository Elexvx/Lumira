import { message } from '@/theme/antdFeedbackBridge';
import { buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { ErrorCode } from '@/enums/errorCode';
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

const triggerForcedSessionLogout = () => {
  void import('@/auth/sessionLifecycle')
    .then(({ performLogout }) => performLogout({ reason: 'forced_expired' }))
    .catch(() => undefined);
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
      notify();
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
    context.authenticatedRefreshSucceeded === true ||
    context.refreshSuperseded === true ||
    context.refreshTemporarilyUnavailable === true ||
    shouldSuppressUnauthorizedSideEffects(authSnapshot, buildUnauthorizedRuntimeState())
  ) {
    if (!options.silent && (context.authenticatedRefreshSucceeded || context.refreshTemporarilyUnavailable)) {
      message.warning(feedback.message);
    }
    return;
  }

  if (!options.silent) {
    notify();
  }
  triggerForcedSessionLogout();
};
