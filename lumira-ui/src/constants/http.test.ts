// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest';

const originalLocalMode = process.env.UMI_APP_LOCAL_NATIVE_MODE;
const originalApiBaseUrl = process.env.UMI_APP_API_BASE_URL;

afterEach(() => {
  vi.resetModules();
  window.localStorage.clear();
  delete window.__LUMIRA_API_BASE_URL__;
  if (originalLocalMode === undefined) {
    delete process.env.UMI_APP_LOCAL_NATIVE_MODE;
  } else {
    process.env.UMI_APP_LOCAL_NATIVE_MODE = originalLocalMode;
  }
  if (originalApiBaseUrl === undefined) {
    delete process.env.UMI_APP_API_BASE_URL;
  } else {
    process.env.UMI_APP_API_BASE_URL = originalApiBaseUrl;
  }
});

describe('local native API isolation', () => {
  it('keeps runtime overrides available outside native local mode', async () => {
    process.env.UMI_APP_LOCAL_NATIVE_MODE = 'false';
    window.localStorage.setItem('lumira:api_base_url', 'https://example.test/');

    const { getApiBaseUrl } = await import('./http');

    expect(getApiBaseUrl()).toBe('https://example.test');
  });

  it('ignores stale browser overrides in native local mode', async () => {
    process.env.UMI_APP_LOCAL_NATIVE_MODE = 'true';
    process.env.UMI_APP_API_BASE_URL = '';
    window.__LUMIRA_API_BASE_URL__ = 'https://production.example.test';
    window.localStorage.setItem('lumira:api_base_url', 'https://production.example.test');

    const { getApiBaseUrl, getApiPrefix } = await import('./http');

    expect(getApiBaseUrl()).toBe('');
    expect(getApiPrefix()).toBe('/api');
  });
});
