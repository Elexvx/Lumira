import { ErrorCode } from '@/enums/errorCode';
import { resolveHttpStatusFeedback } from '@/services/common/errorFeedback';
import { resolveBuiltinMessage } from '@/i18n/messages';
import { ApiRequestError } from './requestInternalsTypes';

export const buildFallbackError = (httpStatus?: number, requestId?: string, hasAuthToken = true) => {
  if (httpStatus === 401) {
    const code = hasAuthToken ? ErrorCode.SESSION_EXPIRED : ErrorCode.UNAUTHORIZED;
    const message = resolveBuiltinMessage(hasAuthToken ? 'common.sessionExpired' : 'common.pleaseLogin', hasAuthToken ? '登录状态已失效，请重新登录' : '请先登录后再继续操作');
    return new ApiRequestError(code, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 403) {
    const message = resolveBuiltinMessage('common.noPermission', '当前账号没有访问权限');
    return new ApiRequestError(ErrorCode.FORBIDDEN, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 404) {
    const message = resolveBuiltinMessage('common.resourceNotFound', '请求的资源不存在');
    return new ApiRequestError(ErrorCode.NOT_FOUND, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 400 || httpStatus === 422) {
    const message = resolveBuiltinMessage('common.badRequest', '请求内容有误，请检查后重试');
    return new ApiRequestError(ErrorCode.BAD_REQUEST, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 409) {
    const message = resolveBuiltinMessage('common.bizError', '当前操作无法完成，请检查业务状态');
    return new ApiRequestError(ErrorCode.BIZ_ERROR, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 429) {
    const message = resolveBuiltinMessage('common.tooManyRequests', '操作过于频繁，请稍后再试');
    return new ApiRequestError(ErrorCode.LOGIN_RATE_LIMITED, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 502 || httpStatus === 503 || httpStatus === 504) {
    const message = resolveBuiltinMessage('common.serviceUnavailable', '服务暂时不可用，请稍后再试');
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus && httpStatus >= 500) {
    const message = resolveBuiltinMessage('common.systemError', '系统异常，请稍后重试');
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  const feedback = resolveHttpStatusFeedback(httpStatus, hasAuthToken);
  return new ApiRequestError(ErrorCode.BIZ_ERROR, feedback.message, {
    userMessage: feedback.message,
    requestId,
    httpStatus,
  });
};
