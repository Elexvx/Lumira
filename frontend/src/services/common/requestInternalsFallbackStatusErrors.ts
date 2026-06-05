import { ErrorCode } from '@/enums/errorCode';
import { resolveHttpStatusFeedback } from '@/services/common/errorFeedback';
import { ApiRequestError } from './requestInternalsTypes';

export const buildFallbackError = (httpStatus?: number, requestId?: string, hasAuthToken = true) => {
  if (httpStatus === 401) {
    const code = hasAuthToken ? ErrorCode.SESSION_EXPIRED : ErrorCode.UNAUTHORIZED;
    const message = hasAuthToken ? '登录状态已失效，请重新登录' : '请先登录后再继续操作';
    return new ApiRequestError(code, message, {
      userMessage: message,
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 403) {
    return new ApiRequestError(ErrorCode.FORBIDDEN, '当前账号没有访问权限', {
      userMessage: '当前账号没有访问权限',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 404) {
    return new ApiRequestError(ErrorCode.NOT_FOUND, '请求的资源不存在', {
      userMessage: '请求的资源不存在',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 400 || httpStatus === 422) {
    return new ApiRequestError(ErrorCode.BAD_REQUEST, '请求内容有误，请检查后重试', {
      userMessage: '请求内容有误，请检查后重试',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 409) {
    return new ApiRequestError(ErrorCode.BIZ_ERROR, '当前操作无法完成，请检查业务状态', {
      userMessage: '当前操作无法完成，请检查业务状态',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 429) {
    return new ApiRequestError(ErrorCode.LOGIN_RATE_LIMITED, '操作过于频繁，请稍后再试', {
      userMessage: '操作过于频繁，请稍后再试',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 502 || httpStatus === 503 || httpStatus === 504) {
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '服务暂时不可用，请稍后再试', {
      userMessage: '服务暂时不可用，请稍后再试',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus && httpStatus >= 500) {
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '系统异常，请稍后重试', {
      userMessage: '系统异常，请稍后重试',
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
