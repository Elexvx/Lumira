import { storage } from '@/cache/storage';
import type { BrandingSettings } from '@/types/api';
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

const MOJIBAKE_MARKER_PATTERN = /[ÃÂÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]/;
const CJK_PATTERN = /[\u3400-\u9fff]/g;
const WINDOWS_1252_REVERSE_MAP: Record<number, number> = {
  0x20ac: 0x80,
  0x201a: 0x82,
  0x0192: 0x83,
  0x201e: 0x84,
  0x2026: 0x85,
  0x2020: 0x86,
  0x2021: 0x87,
  0x02c6: 0x88,
  0x2030: 0x89,
  0x0160: 0x8a,
  0x2039: 0x8b,
  0x0152: 0x8c,
  0x017d: 0x8e,
  0x2018: 0x91,
  0x2019: 0x92,
  0x201c: 0x93,
  0x201d: 0x94,
  0x2022: 0x95,
  0x2013: 0x96,
  0x2014: 0x97,
  0x02dc: 0x98,
  0x2122: 0x99,
  0x0161: 0x9a,
  0x203a: 0x9b,
  0x0153: 0x9c,
  0x017e: 0x9e,
  0x0178: 0x9f,
};

const countCjk = (value: string) => value.match(CJK_PATTERN)?.length ?? 0;

const repairMojibakeText = (value: string) => {
  if (!MOJIBAKE_MARKER_PATTERN.test(value)) {
    return value;
  }
  const bytes: number[] = [];
  for (const char of value) {
    const code = char.charCodeAt(0);
    const byte = code <= 0xff ? code : WINDOWS_1252_REVERSE_MAP[code];
    if (byte === undefined) {
      return value;
    }
    bytes.push(byte);
  }
  const repaired = new TextDecoder('utf-8', { fatal: false }).decode(Uint8Array.from(bytes));
  return countCjk(repaired) > countCjk(value) ? repaired : value;
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
