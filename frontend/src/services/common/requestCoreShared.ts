import { captureAuthRequestSnapshot } from '@/auth/unauthorized';
import type { AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { buildDuplicateRequestError } from './requestInternalsApiErrorBuilds';
import { handleApiError } from './requestInternalsApiErrorHandling';
import type { RequestOptions } from './requestInternalsTypes';

export const activeWriteRequests = new Set<string>();

export const refreshAuthSession = async () => {
  const { tryRefreshToken } = await import('@/auth/sessionLifecycle');
  return tryRefreshToken();
};

export const ensureUniqueWriteRequest = (duplicateKey: string | null | undefined, options: RequestOptions, authSnapshot: AuthRequestSnapshot) => {
  if (duplicateKey && activeWriteRequests.has(duplicateKey)) {
    const duplicateError = buildDuplicateRequestError();
    handleApiError(duplicateError, options, authSnapshot);
    throw duplicateError;
  }

  if (duplicateKey) {
    activeWriteRequests.add(duplicateKey);
  }

  return () => {
    if (duplicateKey) {
      activeWriteRequests.delete(duplicateKey);
    }
  };
};

export const captureRequestAuthSnapshot = (skipAuth?: boolean) => captureAuthRequestSnapshot(skipAuth === true);
