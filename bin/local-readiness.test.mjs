import assert from 'node:assert/strict';
import test from 'node:test';

import {
  createLocalReadinessTargets,
  probeReadinessTarget,
  waitForLocalReadiness,
} from './lib/local-readiness.mjs';

test('local full readiness covers backend, async, job, and frontend HTTP contracts', () => {
  assert.deepEqual(
    createLocalReadinessTargets({
      backendPort: 8180,
      frontendPort: 8002,
      asyncPort: 8281,
      jobPort: 8282,
      includeWorkers: true,
    }).map(({ label, url, expectedStatuses }) => ({ label, url, expectedStatuses })),
    [
      { label: 'lumira-server', url: 'http://127.0.0.1:8180/api/health', expectedStatuses: ['UP'] },
      { label: 'lumira-async', url: 'http://127.0.0.1:8281/api/v2/async/health', expectedStatuses: ['UP'] },
      { label: 'lumira-job-executor', url: 'http://127.0.0.1:8282/api/v2/job/health', expectedStatuses: ['UP'] },
      { label: 'lumira-ui', url: 'http://127.0.0.1:8002/', expectedStatuses: undefined },
    ],
  );
});

test('readiness probe rejects a nominal HTTP response with a degraded business status', async () => {
  const result = await probeReadinessTarget(
    { label: 'async', url: 'http://local/health', expectedStatuses: ['UP'] },
    {
      fetchImpl: async () => new Response(JSON.stringify({ data: { status: 'DEGRADED' } }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    },
  );
  assert.deepEqual(result, { ready: false, detail: 'status=DEGRADED' });
});

test('readiness wait retries until every target is business-ready', async () => {
  let backendAttempts = 0;
  const ready = [];
  await waitForLocalReadiness({
    targets: [
      { label: 'backend', url: 'http://local/backend', expectedStatuses: ['UP'] },
      { label: 'frontend', url: 'http://local/frontend' },
    ],
    timeoutMs: 1_000,
    pollIntervalMs: 1,
    sleep: async () => {},
    onProgress: ({ label }) => ready.push(label),
    fetchImpl: async (url) => {
      if (url.endsWith('/backend')) {
        backendAttempts += 1;
        return new Response(JSON.stringify({ data: { status: backendAttempts > 1 ? 'UP' : 'STARTING' } }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }
      return new Response('<html></html>', { status: 200 });
    },
  });
  assert.equal(backendAttempts, 2);
  assert.deepEqual(ready.sort(), ['backend', 'frontend']);
});
