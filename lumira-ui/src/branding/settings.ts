import type { BrandingSettings } from '@/types/api';
import { repairMojibakeText } from '@/utils/textEncoding';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

let currentBrandingSettings: BrandingSettings | null = null;
const brandingSettingsListeners = new Set<() => void>();

export const DEFAULT_BRANDING_SETTINGS: BrandingSettings = {
  websiteName: 'Lumira',
  websiteFaviconUrl: '',
  websiteLogoUrl: '',
  loginBackgroundUrl: '',
  githubLinkEnabled: true,
  githubLinkUrl: '',
  helpLinkEnabled: true,
  helpLinkUrl: '',
  companyName: 'Lumira',
  copyrightStartYear: new Date().getFullYear(),
  footerIcp: '',
  footerPoliceBeian: '',
  footerCopyright: '',
  maintenanceModeEnabled: false,
  maintenanceTitle: '马上回来，精彩不掉线',
  maintenanceMessage: '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。',
  maintenanceEndAt: '',
  maintenanceAllowedRoleIds: [1001],
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
    maintenanceModeEnabled: normalizeBoolean(settings?.maintenanceModeEnabled, false),
    maintenanceTitle: normalizeText(settings?.maintenanceTitle, DEFAULT_BRANDING_SETTINGS.maintenanceTitle || '马上回来，精彩不掉线'),
    maintenanceMessage: normalizeText(
      settings?.maintenanceMessage,
      DEFAULT_BRANDING_SETTINGS.maintenanceMessage || '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。',
    ),
    maintenanceEndAt: normalizeDateTime(settings?.maintenanceEndAt),
    maintenanceAllowedRoleIds: normalizeRoleIds(settings?.maintenanceAllowedRoleIds, DEFAULT_BRANDING_SETTINGS.maintenanceAllowedRoleIds || [1001]),
  };
};

export const buildCopyrightText = (settings?: Partial<BrandingSettings> | null) => {
  const normalized = normalizeBrandingSettings(settings);
  const currentYear = new Date().getFullYear();
  const startYear = normalized.copyrightStartYear ?? currentYear;
  const yearLabel = startYear < currentYear ? `${startYear}-${currentYear}` : String(startYear);
  return `Copyright © ${yearLabel} ${normalized.companyName} All Rights Reserved`;
};

// Runtime-only snapshot. Branding is owned by the backend database; the browser
// must never become a second persistent source of truth.
export const getStoredBrandingSettings = (): BrandingSettings | null => currentBrandingSettings;

export const persistBrandingSettings = (settings: BrandingSettings) => {
  currentBrandingSettings = normalizeBrandingSettings(settings);
  brandingSettingsListeners.forEach((listener) => listener());
};

export const clearBrandingSettings = () => {
  currentBrandingSettings = null;
  brandingSettingsListeners.forEach((listener) => listener());
};

export const getBrandingSettingsSnapshot = (): BrandingSettings =>
  currentBrandingSettings || DEFAULT_BRANDING_SETTINGS;

export const subscribeBrandingSettings = (listener: () => void) => {
  brandingSettingsListeners.add(listener);
  return () => {
    brandingSettingsListeners.delete(listener);
  };
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

export const applyBrandingRuntime = (settings?: Partial<BrandingSettings> | null) => {
  if (typeof document === 'undefined') {
    return;
  }
  const normalized = normalizeBrandingSettings(settings);
  document.title = normalized.websiteName;
  applyFavicon(normalized.websiteFaviconUrl);
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

const normalizeDateTime = (value: unknown) => {
  if (typeof value !== 'string' || !value.trim()) {
    return '';
  }
  const timestamp = Date.parse(value.trim());
  return Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : '';
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

const normalizeRoleIds = (value: unknown, fallback: number[]) => {
  if (!Array.isArray(value)) {
    return [...fallback];
  }
  return [...new Set(value
    .map((item) => (typeof item === 'number' ? item : Number(item)))
    .filter((item) => Number.isInteger(item) && item > 0))].sort((left, right) => left - right);
};
