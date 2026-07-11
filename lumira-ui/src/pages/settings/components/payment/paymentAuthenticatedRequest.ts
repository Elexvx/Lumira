import { history } from '@umijs/max';
import { LOGIN_PATH } from '@/app.constants';
import { clearAuthSession } from '@/auth/sessionLifecycle';
import { ErrorCode } from '@/enums/errorCode';
import { request, type RequestOptions } from '@/services/common/request';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { API_OPTS } from '@/utils/errorMessage';

const isExpiredPaymentSession = (error: unknown) =>
  error instanceof ApiRequestError &&
  (error.httpStatus === 401 ||
    error.code === ErrorCode.UNAUTHORIZED ||
    error.code === ErrorCode.SESSION_EXPIRED);

export const buildPaymentLoginRedirect = () => {
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  return `${LOGIN_PATH}?redirect=${encodeURIComponent(currentPath)}`;
};

export const requestPaymentApi = async <T>(url: string, options: RequestOptions = {}) => {
  try {
    return await request<T>(url, {
      ...options,
      ...API_OPTS.NO_REDIRECT,
    });
  } catch (error) {
    if (isExpiredPaymentSession(error)) {
      clearAuthSession();
      history.replace(buildPaymentLoginRedirect());
    }
    throw error;
  }
};
