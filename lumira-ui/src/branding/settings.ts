import { storage } from '@/cache/storage';
import type { BrandingSettings } from '@/types/api';
import { repairMojibakeText } from '@/utils/textEncoding';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

const BRANDING_SETTINGS_KEY = 'branding_settings';

export const DEFAULT_BRANDING_SETTINGS: BrandingSettings = {
  websiteName: '宏翔商道',
  websiteFaviconUrl: '',
  websiteLogoUrl: '',
  loginBackgroundUrl: '',
  githubLinkEnabled: true,
  githubLinkUrl: '',
  helpLinkEnabled: true,
  helpLinkUrl: '',
  companyName: '宏翔商道',
  copyrightStartYear: new Date().getFullYear(),
  footerIcp: '',
  footerPoliceBeian: '',
  footerCopyright: '',
};

export const normalizeBrandingSettings = (settings?: Partial<BrandingSettings> | null): BrandingSettings => {
  const websiteName = normalizeText(settings?.websiteName, DEFAULT_BRANDING_SETTINGS.websiteName);
  return {
    websiteName,
    websiteFaviconUrl: normalizeUploadUrl(settings?.websiteFaviconUrl),
    websiteLogoUrl: normalizeUploadUrl(settings?.websiteLogoUrl),
    loginBackgroundUrl: normalizeUploadUrl(settings?.loginBackgroundUrl),
    githubLinkEnabled: normalizeBoolean(settings?.githubLinkEnabled, true),
    githubLinkUrl: normalizeLink(settings?.githubLinkUrl),
    helpLinkEnabled: normalizeBoolean(settings?.helpLinkEnabled, true),
    helpLinkUrl: normalizeLink(settings?.helpLinkUrl),
    companyName: normalizeText(settings?.companyName, websiteName),
    copyrightStartYear: normalizeYear(settings?.copyrightStartYear, new Date().getFullYear()),
    footerIcp: normalizeText(settings?.footerIcp, ''),
    footerPoliceBeian: normalizeText(settings?.footerPoliceBeian, ''),
    footerCopyright: normalizeText(settings?.footerCopyright, ''),
  };
};

export const buildCopyrightText = (settings?: Partial<BrandingSettings> | null) => {
  const normalized = normalizeBrandingSettings(settings);
  const currentYear = new Date().getFullYear();
  const startYear = normalized.copyrightStartYear ?? currentYear;
  const yearLabel = startYear < currentYear ? `${startYear}-${currentYear}` : String(startYear);
  return `Copyright © ${yearLabel} ${normalized.companyName} All Rights Reserved`;
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
  const href = normalizeUploadUrl(faviconUrl);
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
  const trimmed = repairMojibakeText(value).trim();
  return trimmed || fallback;
};

const normalizeYear = (value: unknown, fallback: number) => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number.parseInt(value.trim(), 10);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return fallback;
};

const normalizeLink = (value?: string | null) => {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim();
};

const normalizeBoolean = (value: unknown, fallback: boolean) => {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    return value.trim().toLowerCase() === 'true';
  }
  return fallback;
};
