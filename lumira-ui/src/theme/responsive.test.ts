import { describe, expect, it } from 'vitest';

import { getResponsiveProfile, resolveViewportTier } from './responsive';

describe('responsive viewport tiers', () => {
  it.each([
    [0, 'mobile'],
    [767, 'mobile'],
    [768, 'tablet'],
    [1199, 'tablet'],
    [1200, 'desktop'],
    [1599, 'desktop'],
    [1600, 'large'],
    [1919, 'large'],
    [1920, 'wide'],
    [2559, 'wide'],
    [2560, 'ultra'],
  ] as const)('maps %dpx to %s', (width, expectedTier) => {
    expect(resolveViewportTier(width)).toBe(expectedTier);
  });

  it('keeps large-screen typography and controls readable', () => {
    const desktop = getResponsiveProfile('desktop');
    const ultra = getResponsiveProfile('ultra');

    expect(ultra.bodyFontSize).toBeGreaterThan(desktop.bodyFontSize);
    expect(ultra.pageTitleFontSize).toBeGreaterThan(desktop.pageTitleFontSize);
    expect(ultra.controlHeight).toBeGreaterThan(desktop.controlHeight);
    expect(ultra.pageContentMaxWidth).toBe('2160px');
  });
});
