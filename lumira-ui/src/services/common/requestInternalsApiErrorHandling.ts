import { message } from '@/theme/antdFeedbackBridge';
import { buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import { ErrorCode } from '@/enums/errorCode';
import type { RequestOptions } from './requestInternalsTypes';
import { ApiRequestError } from './requestInternalsTypes';

const shouldSuppressLoginPageForbiddenFeedback = (error: ApiRequestError, authSnapshot: AuthRequestSnapshot) => {
  if (error.code !== ErrorCode.FORBIDDEN || !authSnapshot.hasAuthToken) {
    return false;
  }

  const runtime = buildUnauthorizedRuntimeState();
  return runtime.pathname === '/user/login' || runtime.loginInProgress;
};

export const handleApiError = (error: ApiRequestError, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  if (shouldSuppressLoginPageForbiddenFeedback(error, authSnapshot)) {
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

  if (
    options.allowUnauthorizedWithoutRedirect === true ||
    options.autoRedirectOnUnauthorized === false ||
    shouldSuppressUnauthorizedSideEffects(authSnapshot, buildUnauthorizedRuntimeState())
  ) {
    return;
  }

  if (!options.silent) {
    notify();
  }
};
