import { ErrorCode } from '@/enums/errorCode';
import type { RequestOptions } from './requestInternalsTypes';
import type { AuthRequestSnapshot } from '@/auth/unauthorizedDecision';

export const buildAuthorization = (accessToken: string) => {
  return accessToken ? `Bearer ${accessToken}` : '';
};

export const shouldRefreshAndRetryUnauthorized = (
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
  if (
    url.includes('/v1/auth/refresh-token') ||
    url.includes('/v1/auth/logout') ||
    url.includes('/v2/auth/refresh-token') ||
    url.includes('/v2/auth/logout')
  ) {
    return false;
  }
  return httpStatus === 401 || apiCode === ErrorCode.UNAUTHORIZED || apiCode === ErrorCode.SESSION_EXPIRED;
};
