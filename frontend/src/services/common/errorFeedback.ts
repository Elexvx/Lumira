import { ErrorCode } from '@/enums/errorCode';

export type FeedbackType = 'info' | 'warning' | 'error';

export interface ErrorFeedback {
  type: FeedbackType;
  message: string;
  redirectToLogin?: boolean;
}

export interface ApiErrorLike {
  code: string;
  message: string;
  userMessage?: string;
  httpStatus?: number;
}

const WARNING_CODES = new Set<string>([
  ErrorCode.VALIDATION_ERROR,
  ErrorCode.UNAUTHORIZED,
  ErrorCode.LOGIN_FAILED,
  ErrorCode.FORBIDDEN,
  ErrorCode.NOT_FOUND,
  ErrorCode.SESSION_EXPIRED,
  ErrorCode.ACCOUNT_DISABLED,
  ErrorCode.ACCOUNT_NOT_FOUND,
  ErrorCode.PASSWORD_ERROR,
  ErrorCode.BAD_REQUEST,
  ErrorCode.LOGIN_RATE_LIMITED,
  ErrorCode.CAPTCHA_INVALID,
  ErrorCode.BIZ_ERROR,
  ErrorCode.TENANT_ERROR,
  ErrorCode.TENANT_NOT_BOUND,
  ErrorCode.PERMISSION_SNAPSHOT_ERROR,
  ErrorCode.PLUGIN_PACKAGE_INVALID,
  ErrorCode.PLUGIN_SIGNATURE_INVALID,
  ErrorCode.PLUGIN_CHECKSUM_INVALID,
  ErrorCode.PLUGIN_VERSION_INCOMPATIBLE,
  ErrorCode.PLUGIN_DEPENDENCY_CONFLICT,
  ErrorCode.PLUGIN_NOT_ENABLED,
]);

const ERROR_CODES = new Set<string>([
  ErrorCode.SYSTEM_ERROR,
  ErrorCode.PLUGIN_RUNTIME_ERROR,
]);

export const resolveApiErrorFeedback = (error: ApiErrorLike, hasAuthToken = true): ErrorFeedback => {
  const message = error.userMessage || error.message || '操作失败，请稍后重试';

  if (error.code === ErrorCode.SESSION_EXPIRED || error.code === ErrorCode.UNAUTHORIZED || error.httpStatus === 401) {
    return {
      type: 'info',
      message: message || (hasAuthToken ? '登录状态已失效，请重新登录' : '请先登录后再继续操作'),
      redirectToLogin: true,
    };
  }

  if (WARNING_CODES.has(error.code)) {
    return {
      type: 'warning',
      message,
    };
  }

  if (ERROR_CODES.has(error.code)) {
    return {
      type: 'error',
      message,
    };
  }

  if (error.httpStatus && error.httpStatus >= 500) {
    return {
      type: 'error',
      message,
    };
  }

  return {
    type: 'warning',
    message,
  };
};

export const resolveHttpStatusFeedback = (
  httpStatus?: number,
  hasAuthToken = true,
  fallbackMessage?: string,
): ErrorFeedback => {
  if (httpStatus === 401) {
    return {
      type: 'info',
      message: hasAuthToken ? '登录状态已失效，请重新登录' : '请先登录后再继续操作',
      redirectToLogin: true,
    };
  }

  if (httpStatus === 403) {
    return {
      type: 'warning',
      message: '当前账号没有访问权限',
    };
  }

  if (httpStatus === 404) {
    return {
      type: 'warning',
      message: fallbackMessage || '请求的资源不存在',
    };
  }

  if (httpStatus === 400 || httpStatus === 409 || httpStatus === 422 || httpStatus === 429) {
    return {
      type: 'warning',
      message: fallbackMessage || '操作失败，请检查后重试',
    };
  }

  if (httpStatus === 502 || httpStatus === 503 || httpStatus === 504) {
    return {
      type: 'error',
      message: fallbackMessage || '服务暂时不可用，请稍后再试',
    };
  }

  if (httpStatus && httpStatus >= 500) {
    return {
      type: 'error',
      message: fallbackMessage || '系统异常，请稍后重试',
    };
  }

  return {
    type: 'warning',
    message: fallbackMessage || '操作失败，请稍后重试',
  };
};
