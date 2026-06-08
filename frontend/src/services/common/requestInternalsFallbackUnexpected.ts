import { ErrorCode } from '@/enums/errorCode';
import { resolveBuiltinMessage } from '@/i18n/messages';
import { getResponseRequestId } from './requestInternalsResponse';
import { ApiRequestError } from './requestInternalsTypes';
import { buildFallbackError } from './requestInternalsFallbackStatusErrors';

export const buildUnexpectedError = (error: unknown, hasAuthToken = true) => {
  const errorLike = error as {
    name?: string;
    type?: string;
    message?: string;
    response?: { status?: number; headers?: { get?: (name: string) => string | null } | Record<string, unknown> };
    data?: unknown;
  };
  const httpStatus = errorLike.response?.status;
  const requestId = getResponseRequestId(errorLike.response?.headers, errorLike.data);
  const rawMessage = errorLike.message || '';
  const normalizedMessage = rawMessage.toLowerCase();

  if (
    errorLike.type === 'Timeout'
    || errorLike.name === 'TimeoutError'
    || errorLike.name === 'AbortError'
    || normalizedMessage.includes('timeout')
    || normalizedMessage.includes('timed out')
  ) {
    const message = resolveBuiltinMessage('common.requestTimeout', '请求超时，请稍后重试');
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (!httpStatus || normalizedMessage.includes('network')) {
    const message = resolveBuiltinMessage('common.networkError', '网络异常，请检查连接后重试');
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  return buildFallbackError(httpStatus, requestId, hasAuthToken);
};
