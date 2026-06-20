import { API_PREFIX, AUTHORIZATION_HEADER, CSRF_TOKEN_COOKIE, CSRF_TOKEN_HEADER, REQUEST_ID_HEADER, TRACE_ID_HEADER } from '@/constants/http';
import type { RequestOptions } from './requestInternalsTypes';
import { buildAuthorization } from './requestInternalsAuth';
import type { AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { shouldSendJsonContentType } from './requestInternalsPayload';

export const buildRequestHeaders = (options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  const headers = {
    ...(options.headers || {}),
    [AUTHORIZATION_HEADER]: buildAuthorization(authSnapshot.accessToken),
    [REQUEST_ID_HEADER]: crypto.randomUUID(),
    [TRACE_ID_HEADER]: '',
  };

  if (shouldSendJsonContentType(options.data, options.method) && !hasHeader(headers, 'content-type')) {
    return appendCsrfHeader({
      ...headers,
      'Content-Type': 'application/json',
    }, options.method);
  }

  return appendCsrfHeader(headers, options.method);
};

export const hasHeader = (headers: Record<string, string>, headerName: string) => {
  const normalizedHeaderName = headerName.toLowerCase();
  return Object.keys(headers).some((key) => key.toLowerCase() === normalizedHeaderName);
};

export const buildRequestUrl = (url: string, params?: Record<string, unknown>) => {
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

const appendCsrfHeader = (headers: Record<string, string>, method?: string) => {
  if (!shouldSendCsrf(method) || hasHeader(headers, CSRF_TOKEN_HEADER)) {
    return headers;
  }
  const token = readCookie(CSRF_TOKEN_COOKIE);
  if (!token) {
    return headers;
  }
  return {
    ...headers,
    [CSRF_TOKEN_HEADER]: token,
  };
};

const shouldSendCsrf = (method?: string) => {
  const normalizedMethod = (method || 'GET').toUpperCase();
  return !['GET', 'HEAD', 'OPTIONS'].includes(normalizedMethod);
};

const readCookie = (name: string) => {
  if (typeof document === 'undefined') {
    return '';
  }
  const prefix = `${encodeURIComponent(name)}=`;
  return document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(prefix))
    ?.slice(prefix.length) || '';
};
