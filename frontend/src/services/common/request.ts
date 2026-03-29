import { message } from 'antd';
import { history, request as umiRequest } from 'umi';
import { API_PREFIX, AUTHORIZATION_HEADER, REQUEST_ID_HEADER, TENANT_HEADER, TRACE_ID_HEADER } from '@/constants/http';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { ErrorCode } from '@/enums/errorCode';
import type { ApiResponse } from '@/types/api';

export class ApiRequestError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  data?: unknown;
  params?: Record<string, unknown>;
  headers?: Record<string, string>;
  autoRedirectOnUnauthorized?: boolean;
  skipAuth?: boolean;
}

export const request = async <T>(url: string, options: RequestOptions = {}): Promise<T> => {
  const response = await umiRequest<ApiResponse<T>>(`${API_PREFIX}${url}`, {
    timeout: Number(process.env.UMI_APP_REQUEST_TIMEOUT || 10000),
    method: options.method,
    params: options.params,
    data: options.data,
    getResponse: true,
    errorHandler: undefined,
    headers: {
      ...(options.headers || {}),
      [AUTHORIZATION_HEADER]: options.skipAuth ? '' : buildAuthorization(),
      [TENANT_HEADER]: tenantContext.getTenantId(),
      [REQUEST_ID_HEADER]: crypto.randomUUID(),
      [TRACE_ID_HEADER]: '',
    },
  });

  const serverRequestId = (response as any).response?.headers?.get?.(REQUEST_ID_HEADER) || response.data.requestId;
  if (serverRequestId) {
    (response.data as ApiResponse<T>).requestId = serverRequestId;
  }

  const responseCode = response.data.errorCode || response.data.code;
  const responseMessage = response.data.errorMessage || response.data.message;
  const userTip = response.data.userTip || responseMessage;

  if (responseCode === ErrorCode.SUCCESS) {
    return response.data.data;
  }

  if (responseCode === ErrorCode.UNAUTHORIZED) {
    if (options.autoRedirectOnUnauthorized !== false) {
      message.error(userTip || '登录已失效，请重新登录');
      cleanUnauthorizedState();
      history.replace('/user/login');
    }
    throw new ApiRequestError(responseCode, responseMessage || '未登录');
  }

  const errorMessage = userTip || responseMessage || '请求失败';
  message.error(errorMessage);
  throw new ApiRequestError(responseCode, errorMessage);
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

const cleanUnauthorizedState = () => {
  tokenManager.clearTokenState();
  tenantContext.clearTenantContext();
};
