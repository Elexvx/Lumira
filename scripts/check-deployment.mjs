#!/usr/bin/env node

import process from 'node:process';

const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || (process.env.API_DOMAIN ? `https://${process.env.API_DOMAIN}` : 'http://127.0.0.1:8000');
const backendUrl = process.env.DEPLOY_CHECK_BACKEND_URL || process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8080';

const checks = [
  { label: 'API proxy', url: `${baseUrl}/health` },
  { label: 'system API through API proxy', url: `${baseUrl}/api/health` },
  { label: 'lumira-server actuator', url: `${backendUrl}/actuator/health` },
  { label: 'public login capabilities API', url: `${baseUrl}/api/v1/public/login-capabilities` },
  { label: 'protected localization management API is routed', url: `${baseUrl}/api/v1/localization/languages`, expectedStatus: 401 },
];

function log(message) {
  console.log(`[check] ${message}`);
}

async function probeHttp(url) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5_000);

  try {
    const response = await fetch(url, { signal: controller.signal });
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

let failed = false;

for (const check of checks) {
  // eslint-disable-next-line no-await-in-loop
  const result = await probeHttp(check.url);
  const body = result.text.toLowerCase();
  const expected = check.includes?.toLowerCase();

  if ((check.expectedStatus ? result.status === check.expectedStatus : result.ok) && (!expected || body.includes(expected))) {
    log(`OK ${check.label}: ${check.url}`);
    continue;
  }

  failed = true;
  const status = result.status ? `HTTP ${result.status}` : 'no HTTP response';
  log(`FAIL ${check.label}: ${check.url} (${status})`);
}

if (failed) {
  process.exit(1);
}

log('Deployment checks passed.');
