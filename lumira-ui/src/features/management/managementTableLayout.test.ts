import { describe, expect, it } from 'vitest';
import { isResponsiveColumnVisible } from './managementTableLayout';

describe('management table container responsive layout', () => {
  it('keeps columns without responsive constraints visible', () => {
    expect(isResponsiveColumnVisible(undefined, 640)).toBe(true);
  });

  it('uses the table container width instead of the window width', () => {
    expect(isResponsiveColumnVisible(['lg', 'xl', 'xxl'], 760)).toBe(false);
    expect(isResponsiveColumnVisible(['lg', 'xl', 'xxl'], 992)).toBe(true);
  });

  it('keeps columns visible before the first size measurement', () => {
    expect(isResponsiveColumnVisible(['lg'], 0)).toBe(true);
  });
});
