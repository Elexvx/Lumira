import { addLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { request } from '@/services/common/request';
import type { LocalizationRuntimeBundle } from '@/types/api';
import { clearDatabaseMessages, installDatabaseMessages } from './databaseMessage';

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
      const bundle = await request<LocalizationRuntimeBundle>(`/v2/localization/runtime/${normalizedLocale}`, {
        method: 'GET',
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      if (bundle?.messages && Object.keys(bundle.messages).length > 0) {
        installDatabaseMessages(normalizedLocale, bundle.messages);
        addLocale(normalizedLocale, bundle.messages, {} as never);
      }
      runtimeBundleCache.set(normalizedLocale, bundle || null);
      return bundle || null;
    } catch {
      // A transient localization outage must remain retryable. The frontend no
      // longer ships the full message catalog, so caching a failed request would
      // leave the rest of the session rendering message keys.
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
  clearDatabaseMessages();
};
