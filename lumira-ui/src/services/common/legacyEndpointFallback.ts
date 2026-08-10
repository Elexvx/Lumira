import { ApiRequestError } from './requestInternalsTypes';

/**
 * A legacy endpoint is a compatibility path, not a retry path. Only an HTTP
 * response proving that the versioned route is absent may fall back; auth,
 * concurrency, rate-limit, and network failures must retain their semantics.
 */
export const shouldFallbackToLegacyEndpoint = (error: unknown) =>
  error instanceof ApiRequestError
  && (error.httpStatus === 404 || error.httpStatus === 405);
