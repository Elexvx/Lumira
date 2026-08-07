import { afterEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import type { BrandingSettings } from '@/types/api';

const requestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/common/request', () => ({
  request: requestMock,
}));

import {
  loadPublicBrandingSettings,
  startFrontendVersionPolling,
  startPublicBrandingPolling,
} from './app.runtime';

const versionResponse = (payload: unknown, ok = true) => ({
  ok,
  json: vi.fn().mockResolvedValue(payload),
}) as unknown as Response;

const brandingSettings = (maintenanceModeEnabled: boolean): BrandingSettings => ({
  ...DEFAULT_BRANDING_SETTINGS,
  maintenanceModeEnabled,
});

describe('runtime refresh polling', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    requestMock.mockReset();
  });

  it('continues checking the frontend version after successful unchanged responses', async () => {
    vi.useFakeTimers();
    let storedVersion: string | null = null;
    const fetchVersion = vi.fn().mockResolvedValue(versionResponse({ commit: 'commit-a', short: 'commit-a' }));
    const stop = startFrontendVersionPolling({
      intervalMs: 100,
      fetchVersion,
      readStoredVersion: () => storedVersion,
      writeStoredVersion: (version) => {
        storedVersion = version;
      },
      getCurrentUrl: () => 'https://bm.aiadc.org.cn/dashboard',
      replaceLocation: vi.fn(),
    });

    await vi.advanceTimersByTimeAsync(0);
    expect(fetchVersion).toHaveBeenCalledTimes(1);
    expect(storedVersion).toBe('commit-a');

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchVersion).toHaveBeenCalledTimes(2);

    stop();
  });

  it('reloads once with a cache-busting version when a new frontend is published', async () => {
    vi.useFakeTimers();
    let storedVersion: string | null = 'commit-a';
    const fetchVersion = vi.fn().mockResolvedValue(versionResponse({ commit: 'commit-b-full', short: 'commit-b' }));
    const replaceLocation = vi.fn();
    startFrontendVersionPolling({
      intervalMs: 100,
      fetchVersion,
      readStoredVersion: () => storedVersion,
      writeStoredVersion: (version) => {
        storedVersion = version;
      },
      getCurrentUrl: () => 'https://bm.aiadc.org.cn/dashboard?view=current',
      replaceLocation,
    });

    await vi.advanceTimersByTimeAsync(0);

    expect(storedVersion).toBe('commit-b-full');
    expect(replaceLocation).toHaveBeenCalledOnce();
    expect(replaceLocation.mock.calls[0][0]).toContain('view=current');
    expect(replaceLocation.mock.calls[0][0]).toContain('_v=commit-b');

    await vi.advanceTimersByTimeAsync(500);
    expect(fetchVersion).toHaveBeenCalledOnce();
  });

  it('keeps polling after a transient frontend version request failure', async () => {
    vi.useFakeTimers();
    const fetchVersion = vi.fn()
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValue(versionResponse({ commit: 'commit-a' }));
    const stop = startFrontendVersionPolling({
      intervalMs: 100,
      fetchVersion,
      readStoredVersion: () => null,
      writeStoredVersion: vi.fn(),
      getCurrentUrl: () => 'https://bm.aiadc.org.cn/',
      replaceLocation: vi.fn(),
    });

    await vi.advanceTimersByTimeAsync(0);
    expect(fetchVersion).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchVersion).toHaveBeenCalledTimes(2);

    stop();
  });

  it('requests public branding without authentication or global error feedback', async () => {
    const response = brandingSettings(false);
    requestMock.mockResolvedValue(response);

    await expect(loadPublicBrandingSettings()).resolves.toBe(response);

    expect(requestMock).toHaveBeenCalledWith('/v1/public/branding-settings', {
      method: 'GET',
      params: { _t: expect.any(Number) },
      headers: { 'Cache-Control': 'no-cache' },
      skipAuth: true,
      timeoutMs: 3000,
      autoRedirectOnUnauthorized: false,
      silent: true,
    });
  });

  it('publishes both maintenance entry and automatic maintenance exit to an open client', async () => {
    vi.useFakeTimers();
    let currentSettings = normalizeBrandingSettings(brandingSettings(false));
    const publishSettings = vi.fn((settings: BrandingSettings) => {
      currentSettings = settings;
    });
    const loadSettings = vi.fn()
      .mockResolvedValueOnce(brandingSettings(true))
      .mockResolvedValue(brandingSettings(false));
    const stop = startPublicBrandingPolling({
      intervalMs: 100,
      loadSettings,
      readSettings: () => currentSettings,
      publishSettings,
    });

    await vi.advanceTimersByTimeAsync(0);
    expect(publishSettings).toHaveBeenLastCalledWith(expect.objectContaining({ maintenanceModeEnabled: true }));

    await vi.advanceTimersByTimeAsync(100);
    expect(publishSettings).toHaveBeenLastCalledWith(expect.objectContaining({ maintenanceModeEnabled: false }));
    expect(publishSettings).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(100);
    expect(loadSettings).toHaveBeenCalledTimes(3);
    expect(publishSettings).toHaveBeenCalledTimes(2);

    stop();
  });

  it('never overlaps branding requests and ignores an in-flight response after cleanup', async () => {
    vi.useFakeTimers();
    let resolveRequest: ((settings: BrandingSettings) => void) | undefined;
    const loadSettings = vi.fn(() => new Promise<BrandingSettings>((resolve) => {
      resolveRequest = resolve;
    }));
    const publishSettings = vi.fn();
    const stop = startPublicBrandingPolling({
      intervalMs: 100,
      loadSettings,
      readSettings: () => null,
      publishSettings,
    });

    await vi.advanceTimersByTimeAsync(500);
    expect(loadSettings).toHaveBeenCalledOnce();

    stop();
    resolveRequest?.(brandingSettings(false));
    await vi.advanceTimersByTimeAsync(0);

    expect(publishSettings).not.toHaveBeenCalled();
    expect(loadSettings).toHaveBeenCalledOnce();
  });

  it('does not poll public branding while the tab is hidden', async () => {
    vi.useFakeTimers();
    const loadSettings = vi.fn().mockResolvedValue(brandingSettings(false));
    const stop = startPublicBrandingPolling({
      intervalMs: 100,
      loadSettings,
      isVisible: () => false,
    });

    await vi.advanceTimersByTimeAsync(500);

    expect(loadSettings).not.toHaveBeenCalled();
    stop();
  });
});
