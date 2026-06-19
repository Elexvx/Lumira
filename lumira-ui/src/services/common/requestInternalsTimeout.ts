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

export const fetchWithTimeout = async (input: RequestInfo | URL, init: RequestInit = {}, timeoutOverrideMs?: number) => {
  const timeoutMs = timeoutOverrideMs ?? resolveRequestTimeoutMs();
  if (!timeoutMs || timeoutMs <= 0) {
    return fetch(input, init);
  }

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(buildTimeoutError(timeoutMs)), timeoutMs);

  try {
    return await fetch(input, {
      ...init,
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timeoutId);
  }
};
