const normalizeBaseUrl = (value?: string) => value?.trim().replace(/\/+$/, '');

const DEFAULT_PRODUCTION_API_BASE_URL = 'https://api.elexvx.com';
const API_BASE_URL = normalizeBaseUrl(
  process.env.UMI_APP_API_BASE_URL || (process.env.NODE_ENV === 'production' ? DEFAULT_PRODUCTION_API_BASE_URL : undefined),
);

export const API_PREFIX = API_BASE_URL ? `${API_BASE_URL}/api` : process.env.UMI_APP_API_PREFIX || '/api';
export const API_ORIGIN = API_BASE_URL || '';
export const REQUEST_ID_HEADER = 'X-Request-Id';
export const TRACE_ID_HEADER = 'X-Trace-Id';
export const AUTHORIZATION_HEADER = 'Authorization';
