import { theme as antdTheme } from 'antd';
import { describe, expect, it } from 'vitest';

import { buildAntdThemeConfig } from './antdTheme';

const relativeLuminance = (hexColor: string) => {
  const channels = hexColor
    .replace('#', '')
    .match(/.{2}/g)
    ?.map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) => (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4));

  if (!channels || channels.length !== 3) {
    throw new Error(`Expected a six-digit hexadecimal color, received ${hexColor}`);
  }

  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
};

const contrastRatio = (foreground: string, background: string) => {
  const foregroundLuminance = relativeLuminance(foreground);
  const backgroundLuminance = relativeLuminance(background);
  const lighter = Math.max(foregroundLuminance, backgroundLuminance);
  const darker = Math.min(foregroundLuminance, backgroundLuminance);

  return (lighter + 0.05) / (darker + 0.05);
};

describe('Ant Design light theme accessibility', () => {
  it.each(['light', 'compact'] as const)('keeps %s semantic text and controls above WCAG AA contrast', (themePreference) => {
    const config = buildAntdThemeConfig({
      themePreference,
      resolvedColorMode: 'light',
      isMobile: false,
      viewportTier: 'desktop',
    });
    const token = antdTheme.getDesignToken(config);
    const white = '#ffffff';

    expect(contrastRatio(token.colorTextSecondary, white)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.colorTextDescription, token.colorBgLayout)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.colorTextPlaceholder, white)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.colorPrimary, white)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(white, token.colorPrimary)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.colorPrimaryHover, white)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.colorSuccess, token.colorSuccessBg)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(token.green7, token.green1)).toBeGreaterThanOrEqual(4.5);
  });
});
