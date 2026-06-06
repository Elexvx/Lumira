import { message } from '@/theme/antdFeedbackBridge';
import { buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { resolveApiErrorFeedback } from '@/services/common/errorFeedback';
import type { RequestOptions } from './requestInternalsTypes';
import { ApiRequestError } from './requestInternalsTypes';

export const handleApiError = (error: ApiRequestError, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
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
