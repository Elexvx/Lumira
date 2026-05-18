import { message } from 'antd';
import { API_PREFIX, AUTHORIZATION_HEADER, REQUEST_ID_HEADER, TRACE_ID_HEADER } from '@/constants/http';
import { performLogout } from '@/auth/session';
import { ErrorCode } from '@/enums/errorCode';
import { resolveApiErrorFeedback, resolveHttpStatusFeedback } from '@/services/common/errorFeedback';
import type { ApiResponse } from '@/types/api';
import { buildUnauthorizedRuntimeState, captureAuthRequestSnapshot } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';

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
  allowDuplicate?: boolean;
}

export interface StreamRequestOptions extends RequestOptions {
  onEvent?: (event: { event: string; data: string }) => void;
}

const activeWriteRequests = new Set<string>();
const DEFAULT_REQUEST_TIMEOUT_MS = 10000;

export const request = async <T>(url: string, options: RequestOptions = {}): Promise<T> => {
  let authSnapshot = captureAuthRequestSnapshot(options.skipAuth === true);
  const duplicateKey = buildDuplicateRequestKey(url, options);
  if (duplicateKey && activeWriteRequests.has(duplicateKey)) {
    const duplicateError = buildDuplicateRequestError();
    handleApiError(duplicateError, options, authSnapshot);
    throw duplicateError;
  }
  if (duplicateKey) {
    activeWriteRequests.add(duplicateKey);
  }
  try {
    let refreshedAfterUnauthorized = false;

    while (true) {
      const response = await fetchWithTimeout(buildRequestUrl(url, options.params), {
        method: options.method || 'GET',
        headers: buildRequestHeaders(options, authSnapshot),
        body: buildRequestBody(options.data, options.method),
      });
      const responseData = await parseResponseData(response);
      const httpStatus = response.status;
      const requestId = getResponseRequestId(response.headers, responseData);
      const apiResponse = withRequestId(responseData, requestId);

      if (isApiResponse<T>(apiResponse)) {
        if (apiResponse.code === ErrorCode.SUCCESS) {
          return apiResponse.data;
        }

        if (shouldRefreshAndRetryUnauthorized(url, options, httpStatus, apiResponse.code, refreshedAfterUnauthorized, authSnapshot)) {
          refreshedAfterUnauthorized = true;
          const refreshed = await refreshAuthSession();
          if (refreshed) {
            authSnapshot = captureAuthRequestSnapshot(options.skipAuth === true);
            continue;
          }
        }

        const apiError = new ApiRequestError(apiResponse.code, apiResponse.message, {
          userMessage: apiResponse.userMessage || apiResponse.message,
          requestId: apiResponse.requestId,
          httpStatus,
        });

        handleApiError(apiError, options, authSnapshot);
        throw apiError;
      }

      if (shouldRefreshAndRetryUnauthorized(url, options, httpStatus, undefined, refreshedAfterUnauthorized, authSnapshot)) {
        refreshedAfterUnauthorized = true;
        const refreshed = await refreshAuthSession();
        if (refreshed) {
          authSnapshot = captureAuthRequestSnapshot(options.skipAuth === true);
          continue;
        }
      }

      const fallbackError = buildFallbackError(httpStatus, requestId, authSnapshot.hasAuthToken);
      handleApiError(fallbackError, options, authSnapshot);
      throw fallbackError;
    }
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }

    const fallbackError = buildUnexpectedError(error, authSnapshot.hasAuthToken);
    handleApiError(fallbackError, options, authSnapshot);
    throw fallbackError;
  } finally {
    if (duplicateKey) {
      activeWriteRequests.delete(duplicateKey);
    }
  }
};

export const requestEventStream = async (url: string, options: StreamRequestOptions = {}) => {
  const authSnapshot = captureAuthRequestSnapshot(options.skipAuth === true);
  const duplicateKey = buildDuplicateRequestKey(url, { ...options, method: options.method || 'POST' });
  if (duplicateKey && activeWriteRequests.has(duplicateKey)) {
    const duplicateError = buildDuplicateRequestError();
    handleApiError(duplicateError, options, authSnapshot);
    throw duplicateError;
  }
  if (duplicateKey) {
    activeWriteRequests.add(duplicateKey);
  }
  try {
    const response = await fetch(buildRequestUrl(url, options.params), {
      method: options.method || 'POST',
      headers: {
        ...buildRequestHeaders(options, authSnapshot),
        Accept: 'text/event-stream',
      },
      body: buildRequestBody(options.data, options.method || 'POST'),
    });

    if (!response.ok || !response.body) {
      const fallbackError = await buildFileRequestError(response, options, authSnapshot);
      throw fallbackError;
    }

    await readEventStream(response.body, options.onEvent);
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }

    const fallbackError = buildUnexpectedError(error, authSnapshot.hasAuthToken);
    handleApiError(fallbackError, options, authSnapshot);
    throw fallbackError;
  } finally {
    if (duplicateKey) {
      activeWriteRequests.delete(duplicateKey);
    }
  }
};

export const requestFile = async (url: string, options: RequestOptions = {}) => {
  const authSnapshot = captureAuthRequestSnapshot(options.skipAuth === true);
  const duplicateKey = buildDuplicateRequestKey(url, options);
  if (duplicateKey && activeWriteRequests.has(duplicateKey)) {
    const duplicateError = buildDuplicateRequestError();
    handleApiError(duplicateError, options, authSnapshot);
    throw duplicateError;
  }
  if (duplicateKey) {
    activeWriteRequests.add(duplicateKey);
  }
  try {
    const response = await fetchWithTimeout(buildRequestUrl(url, options.params), {
      method: options.method || 'GET',
      headers: buildRequestHeaders(options, authSnapshot),
      body: buildRequestBody(options.data, options.method),
    });

    if (!response.ok) {
      throw await buildFileRequestError(response, options, authSnapshot);
    }

    return await response.blob();
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }

    const fallbackError = buildUnexpectedError(error, authSnapshot.hasAuthToken);
    handleApiError(fallbackError, options, authSnapshot);
    throw fallbackError;
  } finally {
    if (duplicateKey) {
      activeWriteRequests.delete(duplicateKey);
    }
  }
};

const buildAuthorization = (accessToken: string) => {
  return accessToken ? `Bearer ${accessToken}` : '';
};

const refreshAuthSession = async () => {
  const { tryRefreshToken } = await import('@/auth/session');
  return tryRefreshToken();
};

const shouldRefreshAndRetryUnauthorized = (
  url: string,
  options: RequestOptions,
  httpStatus: number,
  apiCode: string | undefined,
  alreadyRetried: boolean,
  authSnapshot: AuthRequestSnapshot,
) => {
  if (alreadyRetried || options.skipAuth || !authSnapshot.hasAuthToken) {
    return false;
  }
  if (options.allowUnauthorizedWithoutRedirect === true) {
    return false;
  }
  if (url.includes('/v1/auth/refresh-token') || url.includes('/v1/auth/logout')) {
    return false;
  }
  return httpStatus === 401 || apiCode === ErrorCode.UNAUTHORIZED || apiCode === ErrorCode.SESSION_EXPIRED;
};

const handleApiError = (error: ApiRequestError, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  const feedback = resolveApiErrorFeedback(error, authSnapshot.hasAuthToken);

  if (!feedback.redirectToLogin) {
    if (!options.silent) {
      message[feedback.type](feedback.message);
    }
    return;
  }

  if (
    options.allowUnauthorizedWithoutRedirect === true ||
    options.autoRedirectOnUnauthorized === false ||
    shouldSuppressUnauthorizedSideEffects(authSnapshot, buildUnauthorizedRuntimeState())
  ) {
    return;
  }

  if (!options.silent) {
    message[feedback.type](feedback.message);
  }
  void performLogout({ reason: 'forced_expired' });
};

const buildRequestHeaders = (options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  const headers = {
    ...(options.headers || {}),
    [AUTHORIZATION_HEADER]: buildAuthorization(authSnapshot.accessToken),
    [REQUEST_ID_HEADER]: crypto.randomUUID(),
    [TRACE_ID_HEADER]: '',
  };

  if (shouldSendJsonContentType(options.data, options.method) && !hasHeader(headers, 'content-type')) {
    return {
      ...headers,
      'Content-Type': 'application/json',
    };
  }

  return headers;
};

const hasHeader = (headers: Record<string, string>, headerName: string) => {
  const normalizedHeaderName = headerName.toLowerCase();
  return Object.keys(headers).some((key) => key.toLowerCase() === normalizedHeaderName);
};

const buildRequestUrl = (url: string, params?: Record<string, unknown>) => {
  const fullUrl = new URL(`${API_PREFIX}${url}`, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === null) {
        return;
      }
      if (Array.isArray(value)) {
        value.forEach((item) => fullUrl.searchParams.append(key, String(item)));
        return;
      }
      fullUrl.searchParams.set(key, String(value));
    });
  }
  return fullUrl.toString();
};

const buildRequestBody = (data: unknown, method?: RequestOptions['method']) => {
  if (!method || method === 'GET') {
    return undefined;
  }
  if (data === undefined) {
    return undefined;
  }
  if (data instanceof FormData || data instanceof Blob || data instanceof ArrayBuffer) {
    return data;
  }
  if (typeof data === 'string') {
    return data;
  }
  return JSON.stringify(data);
};

const fetchWithTimeout = async (input: RequestInfo | URL, init: RequestInit = {}) => {
  const timeoutMs = resolveRequestTimeoutMs();
  if (!timeoutMs || timeoutMs <= 0) {
    return fetch(input, init);
  }

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(buildTimeoutError(timeoutMs)), timeoutMs);

  try {
    return await fetch(input, {
      ...init,
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timeoutId);
  }
};

const resolveRequestTimeoutMs = () => {
  const raw = process.env.UMI_APP_REQUEST_TIMEOUT;
  if (!raw) {
    return DEFAULT_REQUEST_TIMEOUT_MS;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : DEFAULT_REQUEST_TIMEOUT_MS;
};

const buildTimeoutError = (timeoutMs: number) => {
  return new DOMException(`Request timed out after ${timeoutMs}ms`, 'TimeoutError');
};

const buildDuplicateRequestKey = (url: string, options: RequestOptions) => {
  const method = options.method || 'GET';
  if (options.allowDuplicate || !isWriteMethod(method)) {
    return '';
  }
  return [
    method,
    url,
    stableSerialize(options.params || {}),
    stableSerialize(options.data),
  ].join('|');
};

const isWriteMethod = (method: RequestOptions['method']) => {
  return method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE';
};

const stableSerialize = (value: unknown): string => {
  return JSON.stringify(toStableValue(value));
};

const toStableValue = (value: unknown): unknown => {
  if (value === undefined) {
    return { __type: 'undefined' };
  }
  if (value === null) {
    return null;
  }
  if (value instanceof FormData) {
    return Array.from(value.entries()).map(([key, entry]) => [key, serializeFormDataEntry(entry)]);
  }
  if (value instanceof Blob) {
    return serializeBlob(value);
  }
  if (Array.isArray(value)) {
    return value.map(toStableValue);
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const sorted: Record<string, unknown> = {};
    Object.keys(record).sort().forEach((key) => {
      sorted[key] = toStableValue(record[key]);
    });
    return sorted;
  }
  return value;
};

const serializeFormDataEntry = (entry: FormDataEntryValue) => {
  if (entry instanceof File) {
    return {
      name: entry.name,
      size: entry.size,
      type: entry.type,
      lastModified: entry.lastModified,
    };
  }
  return entry;
};

const serializeBlob = (value: Blob) => ({
  size: value.size,
  type: value.type,
});

const buildDuplicateRequestError = () => {
  return new ApiRequestError(ErrorCode.REPEAT_SUBMIT, '请求正在处理中，请勿重复提交', {
    userMessage: '请求正在处理中，请勿重复提交',
    httpStatus: 429,
  });
};

const readEventStream = async (
  body: ReadableStream<Uint8Array>,
  onEvent?: (event: { event: string; data: string }) => void,
) => {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || '';
    events.forEach((eventBlock) => emitStreamEvent(eventBlock, onEvent));
  }

  if (buffer.trim()) {
    emitStreamEvent(buffer, onEvent);
  }
};

const emitStreamEvent = (eventBlock: string, onEvent?: (event: { event: string; data: string }) => void) => {
  let event = 'message';
  const dataLines: string[] = [];
  eventBlock.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim() || 'message';
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  });
  if (dataLines.length) {
    onEvent?.({ event, data: dataLines.join('\n') });
  }
};

const shouldSendJsonContentType = (data: unknown, method?: RequestOptions['method']) => {
  if (!method || method === 'GET' || data === undefined) {
    return false;
  }
  return !(data instanceof FormData || data instanceof Blob || data instanceof ArrayBuffer);
};

const parseResponseData = async (response: Response) => {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return await response.json();
  }
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
};

const buildFileRequestError = async (response: Response, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  const requestId = resolveHeaderValue(response.headers, REQUEST_ID_HEADER);
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
    return headerGetter.call(headers, headerName) || headerGetter.call(headers, normalizedHeaderName) || undefined;
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

const buildFallbackError = (httpStatus?: number, requestId?: string, hasAuthToken = true) => {
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

const buildUnexpectedError = (error: unknown, hasAuthToken = true) => {
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
    return new ApiRequestError(ErrorCode.SYSTEM_ERROR, '请求超时，请稍后重试', {
      userMessage: '请求超时，请稍后重试',
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

  return buildFallbackError(httpStatus, requestId, hasAuthToken);
};
