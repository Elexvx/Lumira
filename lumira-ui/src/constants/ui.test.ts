import { describe, expect, it } from 'vitest';
import {
  MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE,
  STANDARD_DRAWER_WIDTH,
  STANDARD_DRAWER_WIDTH_BY_BREAKPOINT,
} from './ui';

describe('management drawer widths', () => {
  it('uses progressively wider desktop sizes for richer content', () => {
    expect(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE.compact.desktop).toContain('--saas-spacing-560');
    expect(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE.default.desktop).toContain('--saas-spacing-700');
    expect(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE.wide.desktop).toContain('--saas-spacing-840');
  });

  it('keeps every size viewport-safe and uses the full mobile viewport', () => {
    Object.values(MANAGEMENT_DRAWER_WIDTH_BY_CONTENT_SIZE).forEach((widths) => {
      expect(widths.desktop).toContain('100vw');
      expect(widths.mobile).toBe('100vw');
    });
  });

  it('does not widen unrelated utility drawers', () => {
    expect(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT.desktop).toContain('--saas-spacing-560');
    expect(STANDARD_DRAWER_WIDTH).toBe(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT.desktop);
  });
});
