import type { ApiResponse } from '@/types/api';
import { REQUEST_ID_HEADER } from '@/constants/http';
import { repairMojibakePayload } from '@/utils/textEncoding';

export const parseResponseData = async (response: Response) => {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return repairMojibakePayload(await response.json());
  }
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return repairMojibakePayload(JSON.parse(text));
  } catch {
    return repairMojibakePayload(text);
  }
};

export const getResponseRequestId = (
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

export const resolveHeaderValue = (
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

export const withRequestId = <T>(responseData: unknown, requestId?: string) => {
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

export const isApiResponse = <T>(payload: unknown): payload is ApiResponse<T> => {
  if (!payload || typeof payload !== 'object') {
    return false;
  }
  const candidate = payload as Partial<ApiResponse<T>>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string';
};
