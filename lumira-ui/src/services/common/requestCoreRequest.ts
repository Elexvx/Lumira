import { ErrorCode } from '@/enums/errorCode';
import { captureRequestAuthSnapshot, ensureUniqueWriteRequest, refreshAuthSession } from './requestCoreShared';
import { handleApiError } from './requestInternalsApiErrorHandling';
import { buildFallbackError } from './requestInternalsFallbackStatusErrors';
import { buildUnexpectedError } from './requestInternalsFallbackUnexpected';
import { buildRequestBody } from './requestInternalsPayload';
import { buildRequestHeaders, buildRequestUrl } from './requestInternalsHeaders';
import { fetchWithTimeout } from './requestInternalsTimeout';
import { getResponseRequestId, isApiResponse, parseResponseData, withRequestId } from './requestInternalsResponse';
import { shouldRefreshAndRetryUnauthorized } from './requestInternalsAuth';
import { buildDuplicateRequestKey } from './requestInternalsSerialization';
import { ApiRequestError, type RequestOptions } from './requestInternalsTypes';

export const executeRequest = async <T>(url: string, options: RequestOptions = {}): Promise<T> => {
  let authSnapshot = captureRequestAuthSnapshot(options.skipAuth);
  const duplicateKey = buildDuplicateRequestKey(url, options);
  const releaseDuplicateKey = ensureUniqueWriteRequest(duplicateKey, options, authSnapshot);

  try {
    let refreshedAfterUnauthorized = false;

    while (true) {
      const response = await fetchWithTimeout(
        buildRequestUrl(url, options.params),
        {
          method: options.method || 'GET',
          headers: buildRequestHeaders(options, authSnapshot),
          body: buildRequestBody(options.data, options.method),
        },
        options.timeoutMs,
      );
      const responseData = await parseResponseData(response);
      const httpStatus = response.status;
      const requestId = getResponseRequestId(response.headers, responseData);
      const apiResponse = withRequestId(responseData, requestId);

      if (isApiResponse<T>(apiResponse)) {
        if (apiResponse.code === ErrorCode.SUCCESS) {
          return apiResponse.data as T;
        }

        if (shouldRefreshAndRetryUnauthorized(url, options, httpStatus, apiResponse.code, refreshedAfterUnauthorized, authSnapshot)) {
          refreshedAfterUnauthorized = true;
          const refreshed = await refreshAuthSession();
          if (refreshed) {
            authSnapshot = captureRequestAuthSnapshot(options.skipAuth);
            continue;
          }
        }

        const apiError = new ApiRequestError(apiResponse.code, apiResponse.message, {
          userMessage: apiResponse.userMessage || apiResponse.message,
          requestId: apiResponse.requestId,
          httpStatus,
        });

        handleApiError(apiError, options, authSnapshot);
        throw apiError;
      }

      if (shouldRefreshAndRetryUnauthorized(url, options, httpStatus, undefined, refreshedAfterUnauthorized, authSnapshot)) {
        refreshedAfterUnauthorized = true;
        const refreshed = await refreshAuthSession();
        if (refreshed) {
          authSnapshot = captureRequestAuthSnapshot(options.skipAuth);
          continue;
        }
      }

      const fallbackError = buildFallbackError(httpStatus, requestId, authSnapshot.hasAuthToken);
      handleApiError(fallbackError, options, authSnapshot);
      throw fallbackError;
    }
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }

    const fallbackError = buildUnexpectedError(error, authSnapshot.hasAuthToken);
    handleApiError(fallbackError, options, authSnapshot);
    throw fallbackError;
  } finally {
    releaseDuplicateKey();
  }
};
