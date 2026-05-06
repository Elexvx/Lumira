import { addLocale } from '@umijs/max';
import { localizationService } from '@/services/localization';
import { normalizeLocale } from '@/i18n/locale';

export const loadRuntimeLocalizationBundle = async (localeCode?: string | null) => {
  const normalizedLocale = normalizeLocale(localeCode);
  try {
    const bundle = await localizationService.runtime(normalizedLocale, {
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
    });
    if (bundle?.messages && Object.keys(bundle.messages).length > 0) {
      addLocale(normalizedLocale, bundle.messages, {} as never);
    }
    return bundle;
  } catch {
    return null;
  }
};
