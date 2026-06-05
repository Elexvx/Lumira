import { addLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { request } from '@/services/common/request';
import type { LocalizationRuntimeBundle } from '@/types/api';

export const loadRuntimeLocalizationBundle = async (localeCode?: string | null) => {
  const normalizedLocale = normalizeLocale(localeCode);
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
    return bundle;
  } catch {
    return null;
  }
};
