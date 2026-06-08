import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError, type RequestOptions } from './requestInternalsTypes';
import { buildFallbackError } from './requestInternalsFallbackStatusErrors';
import { getResponseRequestId, isApiResponse } from './requestInternalsResponse';
import { handleApiError } from './requestInternalsApiErrorHandling';
import type { AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export const buildDuplicateRequestError = () => {
  return new ApiRequestError(ErrorCode.REPEAT_SUBMIT, t('请求正在处理中，请勿重复提交', 'The request is still being processed. Please do not submit again.'), {
    userMessage: t('请求正在处理中，请勿重复提交', 'The request is still being processed. Please do not submit again.'),
    httpStatus: 429,
  });
};

export const buildFileRequestError = async (response: Response, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  const requestId = getResponseRequestId(response.headers);
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      const payload = await response.clone().json();
      if (isApiResponse(payload)) {
        const apiError = new ApiRequestError(payload.code, payload.message, {
          userMessage: payload.userMessage || payload.message,
          requestId: payload.requestId || requestId,
          httpStatus: response.status,
        });
        handleApiError(apiError, options, authSnapshot);
        return apiError;
      }
    } catch {
      // Fall through to status-based error handling.
    }
  }

  const fallbackError = buildFallbackError(response.status, requestId, authSnapshot.hasAuthToken);
  handleApiError(fallbackError, options, authSnapshot);
  return fallbackError;
};
