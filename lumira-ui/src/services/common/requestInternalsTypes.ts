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
  timeoutMs?: number;
  credentials?: RequestCredentials;
}

export interface StreamRequestOptions extends RequestOptions {
  onEvent?: (event: { event: string; data: string }) => void;
}
