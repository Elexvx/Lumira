import http from 'node:http';
import https from 'node:https';

export async function probeHttp(url, options = {}) {
  try {
    return await request(url, options, 0);
  } catch (err) {
    return {
      ok: false,
      status: 0,
      text: err instanceof Error ? err.message : String(err),
    };
  }
}

function request(url, options, redirectCount) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const transport = target.protocol === 'https:' ? https : http;
    const req = transport.get(target, { headers: options.headers ?? {} }, (response) => {
      const status = response.statusCode ?? 0;
      const location = response.headers.location;
      if (location && status >= 300 && status < 400 && redirectCount < 5) {
        response.resume();
        resolve(request(new URL(location, target).toString(), options, redirectCount + 1));
        return;
      }
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => resolve({
        ok: status >= 200 && status < 300,
        status,
        text: Buffer.concat(chunks).toString('utf8'),
      }));
    });
    req.setTimeout(options.timeoutMs ?? 5_000, () => {
      req.destroy(new Error('HTTP request timed out'));
    });
    req.on('error', reject);
  });
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
