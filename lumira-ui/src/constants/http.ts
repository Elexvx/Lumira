declare global {
  interface Window {
    __LUMIRA_API_BASE_URL__?: string;
  }
}

export const API_BASE_URL_STORAGE_KEY = 'lumira:api_base_url';

const normalizeBaseUrl = (value?: string | null) => value?.trim().replace(/\/+$/, '') || '';

const API_BASE_URL_FROM_ENV = normalizeBaseUrl(process.env.UMI_APP_API_BASE_URL);
const API_PREFIX_FROM_ENV = process.env.UMI_APP_API_PREFIX || '/api';

const readRuntimeApiBaseUrl = () => {
  if (typeof window === 'undefined') {
    return '';
  }

  const globalBaseUrl = normalizeBaseUrl(window.__LUMIRA_API_BASE_URL__);
  if (globalBaseUrl) {
    return globalBaseUrl;
  }

  try {
    return normalizeBaseUrl(window.localStorage?.getItem(API_BASE_URL_STORAGE_KEY));
  } catch {
    return '';
  }
};

export const getApiBaseUrl = () => readRuntimeApiBaseUrl() || API_BASE_URL_FROM_ENV;

export const getApiPrefix = () => {
  const apiBaseUrl = getApiBaseUrl();
  return apiBaseUrl ? `${apiBaseUrl}/api` : API_PREFIX_FROM_ENV;
};

export const getApiOrigin = () => getApiBaseUrl();

export const API_PREFIX = getApiPrefix();
export const API_ORIGIN = getApiOrigin();
export const REQUEST_ID_HEADER = 'X-Request-Id';
export const TRACE_ID_HEADER = 'X-Trace-Id';
export const AUTHORIZATION_HEADER = 'Authorization';
export const CSRF_TOKEN_COOKIE = 'csrf_token';
export const CSRF_TOKEN_HEADER = 'X-CSRF-Token';
