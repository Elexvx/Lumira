import { storage } from '@/cache/storage';
import type { BrandingSettings } from '@/types/api';

const BRANDING_SETTINGS_KEY = 'branding_settings';

export const DEFAULT_BRANDING_SETTINGS: BrandingSettings = {
  websiteName: '宏翔商道',
  websiteFaviconUrl: '',
  websiteLogoUrl: '',
  footerIcp: '',
  footerCopyright: '',
};

export const normalizeBrandingSettings = (settings?: Partial<BrandingSettings> | null): BrandingSettings => {
  return {
    websiteName: normalizeText(settings?.websiteName, DEFAULT_BRANDING_SETTINGS.websiteName),
    websiteFaviconUrl: normalizeText(settings?.websiteFaviconUrl, ''),
    websiteLogoUrl: normalizeText(settings?.websiteLogoUrl, ''),
    footerIcp: normalizeText(settings?.footerIcp, ''),
    footerCopyright: normalizeText(settings?.footerCopyright, ''),
  };
};

export const getStoredBrandingSettings = (): BrandingSettings | null => storage.get<BrandingSettings>(BRANDING_SETTINGS_KEY);

export const persistBrandingSettings = (settings: BrandingSettings) => {
  storage.set(BRANDING_SETTINGS_KEY, normalizeBrandingSettings(settings));
};

export const clearBrandingSettings = () => {
  storage.remove(BRANDING_SETTINGS_KEY);
};

export const applyFavicon = (faviconUrl?: string) => {
  if (typeof document === 'undefined') {
    return;
  }
  const href = normalizeText(faviconUrl, '');
  const selector = 'link[rel="icon"]';
  const head = document.head;
  if (!head) {
    return;
  }
  let iconLink = head.querySelector<HTMLLinkElement>(selector);
  if (!href) {
    if (iconLink) {
      iconLink.remove();
    }
    return;
  }
  if (!iconLink) {
    iconLink = document.createElement('link');
    iconLink.rel = 'icon';
    head.appendChild(iconLink);
  }
  iconLink.href = href;
};

const normalizeText = (value: unknown, fallback: string) => {
  if (typeof value !== 'string') {
    return fallback;
  }
  const trimmed = value.trim();
  return trimmed || fallback;
};
