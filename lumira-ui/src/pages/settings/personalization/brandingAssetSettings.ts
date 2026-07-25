import { normalizeBrandingSettings } from '@/branding/settings';
import type { BrandingSettings } from '@/types/api';

export type BrandingAssetTarget = 'favicon' | 'logo' | 'loginBackground';

const BRANDING_ASSET_FIELD_BY_TARGET = {
  favicon: 'websiteFaviconUrl',
  logo: 'websiteLogoUrl',
  loginBackground: 'loginBackgroundUrl',
} as const satisfies Record<BrandingAssetTarget, keyof BrandingSettings>;

export const isBrandingAssetTarget = (target: string): target is BrandingAssetTarget =>
  Object.hasOwn(BRANDING_ASSET_FIELD_BY_TARGET, target);

export const buildBrandingAssetSettings = (
  settings: Partial<BrandingSettings>,
  target: BrandingAssetTarget,
  uploadedUrl: string,
): BrandingSettings =>
  normalizeBrandingSettings({
    ...settings,
    [BRANDING_ASSET_FIELD_BY_TARGET[target]]: uploadedUrl,
  });
