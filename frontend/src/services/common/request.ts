import { message } from 'antd';
import { history, request as umiRequest } from 'umi';
import { API_PREFIX, AUTHORIZATION_HEADER, REQUEST_ID_HEADER, TENANT_HEADER, TRACE_ID_HEADER } from '@/constants/http';
import { clearAuthSession } from '@/auth/session';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { ErrorCode } from '@/enums/errorCode';
import type { ApiResponse } from '@/types/api';

export class ApiRequestError extends Error {
  code: string;
  userMessage?: string;
  requestId?: string;
  httpStatus?: number;

  constructor(code: string, message: string, options: { userMessage?: string; requestId?: string; httpStatus?: number } = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.userMessage = options.userMessage;
    this.requestId = options.requestId;
    this.httpStatus = options.httpStatus;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  data?: unknown;
  params?: Record<string, unknown>;
  headers?: Record<string, string>;
  autoRedirectOnUnauthorized?: boolean;
  allowUnauthorizedWithoutRedirect?: boolean;
  skipAuth?: boolean;
  silent?: boolean;
}

export const request = async <T>(url: string, options: RequestOptions = {}): Promise<T> => {
  try {
    const response = await umiRequest<ApiResponse<T>>(`${API_PREFIX}${url}`, {
      timeout: Number(process.env.UMI_APP_REQUEST_TIMEOUT || 10000),
      method: options.method,
      params: options.params,
      data: options.data,
      getResponse: true,
      validateStatus: () => true,
      errorHandler: undefined,
      headers: {
        ...(options.headers || {}),
        [AUTHORIZATION_HEADER]: options.skipAuth ? '' : buildAuthorization(),
        [TENANT_HEADER]: tenantContext.getTenantId(),
        [REQUEST_ID_HEADER]: crypto.randomUUID(),
        [TRACE_ID_HEADER]: '',
      },
    });

    const httpStatus = response.status;
    const requestId = getResponseRequestId(response.headers, response.data);
    const responseData = withRequestId(response.data, requestId);

    if (isApiResponse<T>(responseData)) {
      if (responseData.code === ErrorCode.SUCCESS) {
        return responseData.data;
      }

      const apiError = new ApiRequestError(responseData.code, responseData.message, {
        userMessage: responseData.userMessage || responseData.message,
        requestId: responseData.requestId,
        httpStatus,
      });

      handleApiError(apiError, options);
      throw apiError;
    }

    const fallbackError = buildFallbackError(httpStatus, requestId);
    handleApiError(fallbackError, options);
    throw fallbackError;
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }

    const fallbackError = buildUnexpectedError(error);
    handleApiError(fallbackError, options);
    throw fallbackError;
  }
};

export const requestFile = async (url: string, options: RequestOptions = {}) => {
  return umiRequest(`${API_PREFIX}${url}`, {
    method: options.method,
    params: options.params,
    data: options.data,
    responseType: 'blob',
  });
};

const buildAuthorization = () => {
  const accessToken = tokenManager.getAccessToken();
  return accessToken ? `Bearer ${accessToken}` : '';
};

const handleApiError = (error: ApiRequestError, options: RequestOptions) => {
  const bypassUnauthorizedRedirect =
    options.autoRedirectOnUnauthorized === false && options.allowUnauthorizedWithoutRedirect === true;

  if (shouldRedirectOnUnauthorized(error.code) && !bypassUnauthorizedRedirect) {
    cleanUnauthorizedState();
    if (!options.silent) {
      message.error(error.userMessage || error.message);
    }
    history.replace('/user/login');
    return;
  }

  if (!options.silent) {
    message.error(error.userMessage || error.message);
  }
};

const shouldRedirectOnUnauthorized = (code: string) => {
  return code === ErrorCode.UNAUTHORIZED || code === ErrorCode.SESSION_EXPIRED;
};

const getResponseRequestId = (
  headers?: { get?: (name: string) => string | null } | Record<string, unknown>,
  responseData?: unknown,
) => {
  const headerRequestId = resolveHeaderValue(headers, REQUEST_ID_HEADER);

  if (headerRequestId) {
    return headerRequestId;
  }
  if (isApiResponse(responseData)) {
    return responseData.requestId;
  }
  return undefined;
};

const resolveHeaderValue = (
  headers: { get?: (name: string) => string | null } | Record<string, unknown> | undefined,
  headerName: string,
) => {
  const normalizedHeaderName = headerName.toLowerCase();
  const headerGetter = headers && 'get' in headers ? headers.get : undefined;
  if (typeof headerGetter === 'function') {
    return headerGetter(headerName) || headerGetter(normalizedHeaderName) || undefined;
  }
  if (!headers || typeof headers !== 'object') {
    return undefined;
  }
  const headerRecord = headers as Record<string, unknown>;
  const headerValue = headerRecord[headerName] ?? headerRecord[normalizedHeaderName];
  return typeof headerValue === 'string' ? headerValue : undefined;
};

const withRequestId = <T>(responseData: unknown, requestId?: string) => {
  if (!isApiResponse<T>(responseData)) {
    return responseData;
  }
  if (!responseData.requestId && requestId) {
    return {
      ...responseData,
      requestId,
    };
  }
  return responseData;
};

const isApiResponse = <T>(payload: unknown): payload is ApiResponse<T> => {
  if (!payload || typeof payload !== 'object') {
    return false;
  }
  const candidate = payload as Partial<ApiResponse<T>>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string' && 'data' in candidate;
};

const buildFallbackError = (httpStatus?: number, requestId?: string) => {
  if (httpStatus === 502 || httpStatus === 503 || httpStatus === 504) {
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '服务暂时不可用，请稍后再试', {
      userMessage: '服务暂时不可用，请稍后再试',
      requestId,
      httpStatus,
    });
  }

  if (httpStatus === 401) {
    return new ApiRequestError(ErrorCode.SESSION_EXPIRED, '登录状态已失效，请重新登录', {
      userMessage: '登录状态已失效，请重新登录',
      requestId,
      httpStatus,
    });
  }

  return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '系统异常，请稍后重试', {
    userMessage: '系统异常，请稍后重试',
    requestId,
    httpStatus,
  });
};

const buildUnexpectedError = (error: unknown) => {
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

  if (errorLike.type === 'Timeout' || errorLike.name === 'TimeoutError' || normalizedMessage.includes('timeout')) {
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '请求超时，请稍后重试', {
      userMessage: '请求超时，请稍后重试',
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

  if (!httpStatus || normalizedMessage.includes('network')) {
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '网络异常，请检查连接后重试', {
      userMessage: '网络异常，请检查连接后重试',
      requestId,
      httpStatus,
    });
  }

  return buildFallbackError(httpStatus, requestId);
};

const cleanUnauthorizedState = () => {
  clearAuthSession();
};
