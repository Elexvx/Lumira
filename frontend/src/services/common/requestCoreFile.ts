import { captureRequestAuthSnapshot, ensureUniqueWriteRequest } from './requestCoreShared';
import { ApiRequestError, type RequestOptions } from './requestInternalsTypes';
import { buildDuplicateRequestKey } from './requestInternalsSerialization';
import { buildRequestBody } from './requestInternalsPayload';
import { buildRequestHeaders, buildRequestUrl } from './requestInternalsHeaders';
import { buildUnexpectedError } from './requestInternalsFallbackUnexpected';
import { buildFileRequestError } from './requestInternalsApiErrorBuilds';
import { fetchWithTimeout } from './requestInternalsTimeout';
import { handleApiError } from './requestInternalsApiErrorHandling';

export const executeFileRequest = async (url: string, options: RequestOptions = {}) => {
  const authSnapshot = captureRequestAuthSnapshot(options.skipAuth);
  const duplicateKey = buildDuplicateRequestKey(url, options);
  const releaseDuplicateKey = ensureUniqueWriteRequest(duplicateKey, options, authSnapshot);

  try {
    const response = await fetchWithTimeout(
      buildRequestUrl(url, options.params),
      {
        method: options.method || 'GET',
        headers: buildRequestHeaders(options, authSnapshot),
        body: buildRequestBody(options.data, options.method),
      },
      options.timeoutMs,
    );

    if (!response.ok) {
      throw await buildFileRequestError(response, options, authSnapshot);
    }

    return await response.blob();
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
