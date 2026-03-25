import { message } from 'antd';
import { history, request as umiRequest } from '@umijs/max';
import { API_PREFIX, AUTHORIZATION_HEADER, REQUEST_ID_HEADER, TENANT_HEADER, TRACE_ID_HEADER } from '@/constants/http';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { ErrorCode } from '@/enums/errorCode';
import type { ApiResponse } from '@/types/api';

export const request = async <T>(url: string, options: Record<string, unknown> = {}) => {
  const response = await umiRequest<ApiResponse<T>>(`${API_PREFIX}${url}`, {
    timeout: Number(process.env.UMI_APP_REQUEST_TIMEOUT || 10000),
    ...options,
    headers: {
      ...(options.headers as Record<string, string>),
      [AUTHORIZATION_HEADER]: tokenManager.getToken() ? `Bearer ${tokenManager.getToken()}` : '',
      [TENANT_HEADER]: tenantContext.getTenantId() || '',
      [REQUEST_ID_HEADER]: crypto.randomUUID(),
      [TRACE_ID_HEADER]: '',
    },
    getResponse: true,
    errorHandler: undefined,
  });

  const serverRequestId = response.response.headers.get(REQUEST_ID_HEADER) || response.data.requestId;
  if (serverRequestId) {
    (response.data as ApiResponse<T>).requestId = serverRequestId;
  }

  if (response.data.code === ErrorCode.SUCCESS) {
    return response.data.data;
  }

  if (response.data.code === ErrorCode.UNAUTHORIZED) {
    message.error('登录已失效，请重新登录');
    tokenManager.clearToken();
    history.push('/user/login');
    throw new Error('Unauthorized');
  }

  message.error(response.data.message || '请求失败');
  throw new Error(response.data.message);
};

export const requestFile = async (url: string, options: Record<string, unknown> = {}) => {
  return umiRequest(`${API_PREFIX}${url}`, {
    ...options,
    responseType: 'blob',
  });
};
