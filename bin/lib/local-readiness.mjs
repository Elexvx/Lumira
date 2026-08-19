const DEFAULT_REQUEST_TIMEOUT_MS = 5_000;
const DEFAULT_POLL_INTERVAL_MS = 500;

function normalizeStatus(value) {
  return typeof value === 'string' ? value.trim().toUpperCase() : '';
}

function responseStatus(body) {
  if (!body || typeof body !== 'object') {
    return '';
  }
  return normalizeStatus(body.data?.status ?? body.status);
}

export function createLocalReadinessTargets({
  backendPort,
  frontendPort,
  asyncPort,
  jobPort,
  includeFrontend = true,
  includeWorkers = false,
}) {
  const targets = [
    {
      label: 'lumira-server',
      url: `http://127.0.0.1:${backendPort}/api/health`,
      expectedStatuses: ['UP'],
    },
  ];
  if (includeWorkers) {
    targets.push(
      {
        label: 'lumira-async',
        url: `http://127.0.0.1:${asyncPort}/api/v2/async/health`,
        expectedStatuses: ['UP'],
      },
      {
        label: 'lumira-job-executor',
        url: `http://127.0.0.1:${jobPort}/api/v2/job/health`,
        expectedStatuses: ['UP'],
      },
    );
  }
  if (includeFrontend) {
    targets.push({
      label: 'lumira-ui',
      url: `http://127.0.0.1:${frontendPort}/`,
    });
  }
  return targets;
}

export async function probeReadinessTarget(
  target,
  { fetchImpl = globalThis.fetch, requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS } = {},
) {
  try {
    const response = await fetchImpl(target.url, {
      headers: { accept: 'application/json,text/html;q=0.9,*/*;q=0.8' },
      signal: AbortSignal.timeout(requestTimeoutMs),
    });
    if (!response.ok) {
      return { ready: false, detail: `HTTP ${response.status}` };
    }
    if (!target.expectedStatuses?.length) {
      return { ready: true, detail: `HTTP ${response.status}` };
    }
    let body;
    try {
      body = await response.json();
    } catch {
      return { ready: false, detail: 'response is not JSON' };
    }
    const actualStatus = responseStatus(body);
    const expectedStatuses = target.expectedStatuses.map(normalizeStatus);
    return expectedStatuses.includes(actualStatus)
      ? { ready: true, detail: actualStatus }
      : { ready: false, detail: `status=${actualStatus || 'missing'}` };
  } catch (error) {
    return {
      ready: false,
      detail: error instanceof Error ? error.message : String(error),
    };
  }
}

export async function waitForLocalReadiness({
  targets,
  timeoutMs = 180_000,
  pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
  requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS,
  fetchImpl = globalThis.fetch,
  cancelled = () => false,
  onProgress = () => {},
  sleep = (durationMs) => new Promise((resolve) => setTimeout(resolve, durationMs)),
}) {
  if (!Array.isArray(targets) || targets.length === 0) {
    throw new Error('At least one local readiness target is required.');
  }
  const deadline = Date.now() + timeoutMs;
  const pending = new Map(targets.map((target) => [target.label, target]));
  const details = new Map();

  while (pending.size > 0 && Date.now() < deadline) {
    if (cancelled()) {
      throw new Error('Local readiness wait was cancelled.');
    }
    const results = await Promise.all([...pending.values()].map(async (target) => ({
      target,
      result: await probeReadinessTarget(target, { fetchImpl, requestTimeoutMs }),
    })));
    for (const { target, result } of results) {
      details.set(target.label, result.detail);
      if (result.ready) {
        pending.delete(target.label);
        onProgress({ label: target.label, url: target.url, detail: result.detail });
      }
    }
    if (pending.size > 0) {
      await sleep(Math.min(pollIntervalMs, Math.max(1, deadline - Date.now())));
    }
  }

  if (pending.size > 0) {
    const unresolved = [...pending.values()]
      .map((target) => `${target.label} (${target.url}: ${details.get(target.label) || 'no response'})`)
      .join(', ');
    throw new Error(`Timed out waiting for local business readiness: ${unresolved}`);
  }
}
