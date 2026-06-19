import type { RequestOptions } from './requestInternalsTypes';

export const shouldSendJsonContentType = (data: unknown, method?: RequestOptions['method']) => {
  if (!method || method === 'GET' || data === undefined) {
    return false;
  }
  return !(data instanceof FormData || data instanceof Blob || data instanceof ArrayBuffer);
};

export const buildRequestBody = (data: unknown, method?: RequestOptions['method']) => {
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
