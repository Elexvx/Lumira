import { addLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { request } from '@/services/common/request';
import type { LocalizationRuntimeBundle } from '@/types/api';

const runtimeBundleCache = new Map<string, LocalizationRuntimeBundle | null>();
const runtimeBundleInflight = new Map<string, Promise<LocalizationRuntimeBundle | null>>();

export const loadRuntimeLocalizationBundle = async (localeCode?: string | null) => {
  const normalizedLocale = normalizeLocale(localeCode);
  if (runtimeBundleCache.has(normalizedLocale)) {
    return runtimeBundleCache.get(normalizedLocale) || null;
  }

  const inflight = runtimeBundleInflight.get(normalizedLocale);
  if (inflight) {
    return inflight;
  }

  const loadPromise = (async () => {
    try {
      const bundle = await request<LocalizationRuntimeBundle>(`/v1/localization/runtime/${normalizedLocale}`, {
        method: 'GET',
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      if (bundle?.messages && Object.keys(bundle.messages).length > 0) {
        addLocale(normalizedLocale, bundle.messages, {} as never);
      }
      runtimeBundleCache.set(normalizedLocale, bundle || null);
      return bundle || null;
    } catch {
      runtimeBundleCache.set(normalizedLocale, null);
      return null;
    } finally {
      runtimeBundleInflight.delete(normalizedLocale);
    }
  })();

  runtimeBundleInflight.set(normalizedLocale, loadPromise);
  return loadPromise;
};

export const clearRuntimeLocalizationBundleCache = () => {
  runtimeBundleCache.clear();
  runtimeBundleInflight.clear();
};
