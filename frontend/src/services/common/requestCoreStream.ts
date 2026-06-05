import { captureRequestAuthSnapshot, ensureUniqueWriteRequest } from './requestCoreShared';
import { ApiRequestError, type StreamRequestOptions } from './requestInternalsTypes';
import { buildDuplicateRequestKey } from './requestInternalsSerialization';
import { buildRequestBody } from './requestInternalsPayload';
import { buildRequestHeaders, buildRequestUrl } from './requestInternalsHeaders';
import { buildUnexpectedError } from './requestInternalsFallbackUnexpected';
import { handleApiError } from './requestInternalsApiErrorHandling';
import { buildFileRequestError } from './requestInternalsApiErrorBuilds';
import { readEventStream } from './requestInternalsStream';

export const executeEventStream = async (url: string, options: StreamRequestOptions = {}) => {
  const authSnapshot = captureRequestAuthSnapshot(options.skipAuth);
  const duplicateKey = buildDuplicateRequestKey(url, { ...options, method: options.method || 'POST' });
  const releaseDuplicateKey = ensureUniqueWriteRequest(duplicateKey, options, authSnapshot);

  try {
    const response = await fetch(buildRequestUrl(url, options.params), {
      method: options.method || 'POST',
      headers: {
        ...buildRequestHeaders(options, authSnapshot),
        Accept: 'text/event-stream',
      },
      body: buildRequestBody(options.data, options.method || 'POST'),
    });

    if (!response.ok || !response.body) {
      const fallbackError = await buildFileRequestError(response, options, authSnapshot);
      throw fallbackError;
    }

    await readEventStream(response.body, options.onEvent);
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
