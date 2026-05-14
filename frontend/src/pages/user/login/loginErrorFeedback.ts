import { ErrorCode } from '@/enums/errorCode';
import { resolveApiErrorFeedback, type ApiErrorLike, type ErrorFeedback } from '@/services/common/errorFeedback';

const LOGIN_WARNING_CODES = new Set<string>([
  ErrorCode.LOGIN_FAILED,
  ErrorCode.ACCOUNT_NOT_FOUND,
  ErrorCode.PASSWORD_ERROR,
  ErrorCode.ACCOUNT_DISABLED,
]);

export const resolveLoginErrorFeedback = (error: ApiErrorLike): ErrorFeedback => {
  const feedback = resolveApiErrorFeedback(error, false);

  if (LOGIN_WARNING_CODES.has(error.code)) {
    return {
      type: 'warning',
      message: feedback.message,
    };
  }

  return feedback.type === 'info'
    ? {
        ...feedback,
        type: 'warning',
      }
    : feedback;
};
