export async function probeHttp(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 5_000);

  try {
    const response = await fetch(url, { signal: controller.signal, headers: options.headers ?? {} });
    return {
      ok: response.ok,
      status: response.status,
      text: await response.text(),
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      text: err instanceof Error ? err.message : String(err),
    };
  } finally {
    clearTimeout(timeout);
  }
}

export async function waitForHttp(url, label, options = {}) {
  const timeoutMs = options.timeoutMs ?? 240_000;
  const intervalMs = options.intervalMs ?? 3_000;
  const startedAt = Date.now();
  let lastResult = null;

  while (Date.now() - startedAt <= timeoutMs) {
    const result = await probeHttp(url, { timeoutMs: options.requestTimeoutMs ?? 5_000 });
    lastResult = result;

    const body = result.text.toLowerCase();
    const expected = options.includes?.toLowerCase();
    const expectedStatus = options.expectedStatus;
    const statusMatches = expectedStatus ? result.status === expectedStatus : result.ok;
    
    if (statusMatches && (!expected || body.includes(expected))) {
      return result;
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  const status = lastResult?.status ? `status=${lastResult.status}` : 'no HTTP response';
  throw new Error(`${label} is not ready at ${url} (${status}).`);
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
