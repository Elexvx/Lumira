#!/usr/bin/env node

import process from 'node:process';

import { probeHttp } from './lib/http-utils.mjs';

const baseUrl = process.env.DEPLOY_CHECK_BASE_URL || (process.env.API_DOMAIN ? `https://${process.env.API_DOMAIN}` : 'http://127.0.0.1:8000');
const backendUrl = process.env.DEPLOY_CHECK_BACKEND_URL || process.env.DEPLOY_CHECK_GATEWAY_URL || 'http://127.0.0.1:8080';
const includeBackendCheck = process.env.DEPLOY_CHECK_BACKEND_URL || process.env.DEPLOY_CHECK_GATEWAY_URL || process.env.DEPLOY_CHECK_ACTUATOR === 'true';

const checks = [
  { label: 'API proxy', url: `${baseUrl}/health` },
  { label: 'system API through API proxy', url: `${baseUrl}/api/health` },
  ...(includeBackendCheck ? [{ label: 'lumira-server actuator', url: `${backendUrl}/actuator/health` }] : []),
  { label: 'runtime version API', url: `${baseUrl}/api/v2/runtime/version` },
  { label: 'public login capabilities API', url: `${baseUrl}/api/v1/public/login-capabilities` },
  { label: 'public localization runtime bundle', url: `${baseUrl}/api/v2/localization/runtime/zh-CN` },
  { label: 'auth owner API is routed', url: `${baseUrl}/api/v2/auth/current-user`, expectedStatus: 401 },
  { label: 'file owner API is routed', url: `${baseUrl}/api/v2/files`, expectedStatus: 401 },
  { label: 'message owner API is routed', url: `${baseUrl}/api/v2/message/messages`, expectedStatus: 401 },
  { label: 'plugin owner API is routed', url: `${baseUrl}/api/v2/plugins/current/available`, expectedStatus: 401 },
  { label: 'payment owner API is routed', url: `${baseUrl}/api/v2/payment/providers`, expectedStatus: 401 },
  { label: 'protected localization management API is routed', url: `${baseUrl}/api/v1/localization/languages`, expectedStatus: 401 },
  { label: 'localization v2 owner API is routed', url: `${baseUrl}/api/v2/localization/languages`, expectedStatus: 401 },
  { label: 'team owner API is routed', url: `${baseUrl}/api/v2/teams/my`, expectedStatus: 401 },
  { label: 'AI owner API is routed', url: `${baseUrl}/api/v2/ai/employees`, expectedStatus: 401 },
];

function log(message) {
  console.log(`[check] ${message}`);
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
