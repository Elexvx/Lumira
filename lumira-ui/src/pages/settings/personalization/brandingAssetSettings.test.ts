import { describe, expect, it } from 'vitest';
import { buildBrandingAssetSettings, isBrandingAssetTarget } from './brandingAssetSettings';

describe('branding asset settings', () => {
  it('maps the login background upload into the persisted branding field', () => {
    const settings = buildBrandingAssetSettings(
      {
        websiteName: '赛事报名系统',
        websiteFaviconUrl: '/api/uploads/favicon.png',
        footerIcp: 'ICP-123',
      },
      'loginBackground',
      '/api/uploads/login-background.png',
    );

    expect(settings.loginBackgroundUrl).toBe('/api/uploads/login-background.png');
    expect(settings.websiteFaviconUrl).toBe('/api/uploads/favicon.png');
    expect(settings.websiteName).toBe('赛事报名系统');
    expect(settings.footerIcp).toBe('ICP-123');
  });

  it('distinguishes branding assets from other personalization uploads', () => {
    expect(isBrandingAssetTarget('favicon')).toBe(true);
    expect(isBrandingAssetTarget('logo')).toBe(true);
    expect(isBrandingAssetTarget('loginBackground')).toBe(true);
    expect(isBrandingAssetTarget('watermark')).toBe(false);
    expect(isBrandingAssetTarget('floatingQr')).toBe(false);
  });
});
