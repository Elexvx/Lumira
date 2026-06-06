import { message } from '@/theme/antdFeedbackBridge';
import type { RequestOptions } from '@/services/common/request';

export const extractErrorMessage = (error: unknown, fallbackMessage = '操作失败，请稍后重试'): string => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallbackMessage;
};

export const showErrorMessage = (error: unknown, fallbackMessage = '操作失败，请稍后重试'): void => {
  message.error(extractErrorMessage(error, fallbackMessage));
};

export const API_OPTS = {
  NO_REDIRECT: { autoRedirectOnUnauthorized: false } as RequestOptions,
  SILENT: { silent: true } as RequestOptions,
  SILENT_NO_REDIRECT: { autoRedirectOnUnauthorized: false, silent: true } as RequestOptions,
};
