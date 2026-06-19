import { message } from '@/theme/antdFeedbackBridge';
import type { RequestOptions } from '@/services/common/request';
import { resolveBuiltinMessage } from '@/i18n/messages';

export const extractErrorMessage = (error: unknown, fallbackMessage = resolveBuiltinMessage('common.failure', '操作失败，请稍后重试')): string => {
  if (error && typeof error === 'object') {
    const errorLike = error as { userMessage?: unknown; message?: unknown };
    if (typeof errorLike.userMessage === 'string' && errorLike.userMessage.trim()) {
      return errorLike.userMessage;
    }
    if (typeof errorLike.message === 'string' && errorLike.message.trim()) {
      return errorLike.message;
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallbackMessage;
};

export const showErrorMessage = (error: unknown, fallbackMessage = resolveBuiltinMessage('common.failure', '操作失败，请稍后重试')): void => {
  message.error(extractErrorMessage(error, fallbackMessage));
};

export const API_OPTS = {
  NO_REDIRECT: { autoRedirectOnUnauthorized: false } as RequestOptions,
  SILENT: { silent: true } as RequestOptions,
  SILENT_NO_REDIRECT: { autoRedirectOnUnauthorized: false, silent: true } as RequestOptions,
};
