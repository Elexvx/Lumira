import { describe, expect, it } from 'vitest';
import {
  MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE,
  STANDARD_DRAWER_WIDTH,
  STANDARD_DRAWER_WIDTH_BY_BREAKPOINT,
} from './ui';

describe('management drawer widths', () => {
  it('uses one global desktop width for every drawer content type', () => {
    Object.values(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE).forEach((widths) => {
      expect(widths).toBe(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT);
      expect(widths.desktop).toContain('--saas-standard-drawer-width');
    });
  });

  it('keeps every size viewport-safe and uses the full mobile viewport', () => {
    Object.values(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE).forEach((widths) => {
      expect(widths.desktop).toContain('100vw');
      expect(widths.mobile).toBe('100vw');
    });
  });

  it('uses the shared width for utility drawers too', () => {
    expect(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT.desktop).toContain('--saas-standard-drawer-width');
    expect(STANDARD_DRAWER_WIDTH).toBe(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT.desktop);
  });
});
