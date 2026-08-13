export const DEFAULT_REQUEST_TIMEOUT_MS = 10000;

export const resolveRequestTimeoutMs = () => {
  const raw = process.env.UMI_APP_REQUEST_TIMEOUT;
  if (!raw) {
    return DEFAULT_REQUEST_TIMEOUT_MS;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : DEFAULT_REQUEST_TIMEOUT_MS;
};

export const buildTimeoutError = (timeoutMs: number) => {
  return new DOMException(`Request timed out after ${timeoutMs}ms`, 'TimeoutError');
};

export const fetchWithTimeout = async (
  input: RequestInfo | URL,
  init: RequestInit = {},
  timeoutOverrideMs?: number,
  externalSignal?: AbortSignal,
) => {
  const timeoutMs = timeoutOverrideMs ?? resolveRequestTimeoutMs();
  if (!timeoutMs || timeoutMs <= 0) {
    return fetch(input, { ...init, signal: externalSignal ?? init.signal });
  }

  const controller = new AbortController();
  const abortFromExternalSignal = () => controller.abort(externalSignal?.reason);
  if (externalSignal?.aborted) {
    abortFromExternalSignal();
  } else {
    externalSignal?.addEventListener('abort', abortFromExternalSignal, { once: true });
  }
  const timeoutId = window.setTimeout(() => controller.abort(buildTimeoutError(timeoutMs)), timeoutMs);

  try {
    return await fetch(input, {
      ...init,
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timeoutId);
    externalSignal?.removeEventListener('abort', abortFromExternalSignal);
  }
};
