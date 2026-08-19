import {
  applyBrandingRuntime,
  DEFAULT_BRANDING_SETTINGS,
  getStoredBrandingSettings,
  normalizeBrandingSettings,
  persistBrandingSettings,
} from '@/branding/settings';
import { request } from '@/services/common/request';
import type { BrandingSettings } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';

export const FRONTEND_VERSION_POLL_INTERVAL_MS = 15_000;
export const PUBLIC_BRANDING_POLL_INTERVAL_MS = 5_000;

const FRONTEND_VERSION_STORAGE_KEY = 'lumira:frontend-version';
const PUBLIC_BRANDING_REQUEST_TIMEOUT_MS = 3_000;

type TimerHandle = ReturnType<typeof setTimeout>;

interface PollContext {
  signal: AbortSignal;
  isStopped: () => boolean;
}

interface SerialPollingOptions {
  intervalMs: number;
  setTimer?: (callback: () => void, delayMs: number) => TimerHandle;
  clearTimer?: (handle: TimerHandle) => void;
}

type SerialPoll = (context: PollContext) => Promise<boolean | void>;

export const startSerialPolling = (
  poll: SerialPoll,
  {
    intervalMs,
    setTimer = (callback, delayMs) => setTimeout(callback, delayMs),
    clearTimer = (handle) => clearTimeout(handle),
  }: SerialPollingOptions,
) => {
  let stopped = false;
  let timer: TimerHandle | null = null;
  let controller: AbortController | null = null;

  const run = async () => {
    controller = new AbortController();
    let shouldContinue = true;

    try {
      shouldContinue = (await poll({
        signal: controller.signal,
        isStopped: () => stopped,
      })) !== false;
    } catch (error) {
      if (stopped || (error as { name?: string }).name === 'AbortError') {
        return;
      }
      // Runtime refresh is best-effort. A transient request failure must not
      // stop later checks or surface a global error notification.
    } finally {
      controller = null;
    }

    if (!stopped && shouldContinue) {
      timer = setTimer(() => {
        timer = null;
        void run();
      }, intervalMs);
    }
  };

  void run();

  return () => {
    stopped = true;
    controller?.abort();
    controller = null;
    if (timer !== null) {
      clearTimer(timer);
      timer = null;
    }
  };
};

interface FrontendVersionResponse {
  commit?: unknown;
  short?: unknown;
}

interface FrontendVersionPollingOptions {
  intervalMs?: number;
  fetchVersion?: (url: string, init: RequestInit) => Promise<Response>;
  readStoredVersion?: () => string | null;
  writeStoredVersion?: (version: string) => void;
  getCurrentUrl?: () => string;
  replaceLocation?: (url: string) => void;
}

const normalizeVersion = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value.trim() : '';

export const startFrontendVersionPolling = ({
  intervalMs = FRONTEND_VERSION_POLL_INTERVAL_MS,
  fetchVersion = (url, init) => fetch(url, init),
  readStoredVersion = () => window.localStorage.getItem(FRONTEND_VERSION_STORAGE_KEY),
  writeStoredVersion = (version) => window.localStorage.setItem(FRONTEND_VERSION_STORAGE_KEY, version),
  getCurrentUrl = () => window.location.href,
  replaceLocation = (url) => window.location.replace(url),
}: FrontendVersionPollingOptions = {}) =>
  startSerialPolling(
    async ({ signal }) => {
      const response = await fetchVersion(`/__version.json?_t=${Date.now()}`, {
        cache: 'no-store',
        signal,
      });
      if (!response.ok) {
        return;
      }

      const payload = await response.json() as FrontendVersionResponse;
      const nextVersion = normalizeVersion(payload.commit) || normalizeVersion(payload.short);
      if (!nextVersion) {
        return;
      }

      const currentVersion = readStoredVersion();
      writeStoredVersion(nextVersion);
      if (!currentVersion || currentVersion === nextVersion) {
        return;
      }

      const nextUrl = new URL(getCurrentUrl());
      nextUrl.searchParams.set('_v', (normalizeVersion(payload.short) || nextVersion).slice(0, 12));
      replaceLocation(nextUrl.toString());
      return false;
    },
    { intervalMs },
  );

export const loadPublicBrandingSettings = () =>
  request<BrandingSettings>('/v1/public/branding-settings', {
    method: 'GET',
    params: { _t: Date.now() },
    headers: { 'Cache-Control': 'no-cache' },
    skipAuth: true,
    timeoutMs: PUBLIC_BRANDING_REQUEST_TIMEOUT_MS,
    ...API_OPTS.SILENT_NO_REDIRECT,
  });

const BRANDING_SETTING_KEYS = Object.keys(DEFAULT_BRANDING_SETTINGS) as Array<keyof BrandingSettings>;

const isSameBrandingValue = (
  left: BrandingSettings[keyof BrandingSettings] | undefined,
  right: BrandingSettings[keyof BrandingSettings],
) => {
  if (Array.isArray(left) && Array.isArray(right)) {
    return left.length === right.length && left.every((value, index) => Object.is(value, right[index]));
  }
  return Object.is(left, right);
};

const isSameBrandingSettings = (left: BrandingSettings | null, right: BrandingSettings) =>
  Boolean(left) && BRANDING_SETTING_KEYS.every((key) => isSameBrandingValue(left?.[key], right[key]));

interface PublicBrandingPollingOptions {
  intervalMs?: number;
  loadSettings?: () => Promise<BrandingSettings>;
  readSettings?: () => BrandingSettings | null;
  publishSettings?: (settings: BrandingSettings) => void;
  isVisible?: () => boolean;
}

export const startPublicBrandingPolling = ({
  intervalMs = PUBLIC_BRANDING_POLL_INTERVAL_MS,
  loadSettings = loadPublicBrandingSettings,
  readSettings = getStoredBrandingSettings,
  publishSettings = (settings) => {
    persistBrandingSettings(settings);
    applyBrandingRuntime(settings);
  },
  isVisible = () => typeof document === 'undefined' || document.visibilityState !== 'hidden',
}: PublicBrandingPollingOptions = {}) =>
  startSerialPolling(
    async ({ isStopped }) => {
      if (!isVisible()) {
        return;
      }
      const settings = normalizeBrandingSettings(await loadSettings());
      if (isStopped() || isSameBrandingSettings(readSettings(), settings)) {
        return;
      }
      publishSettings(settings);
    },
    { intervalMs },
  );
