import { describe, expect, it } from 'vitest';
import { isResponsiveColumnVisible, resolveEstimatedTableScrollX } from './managementTableLayout';

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

  it('uses the estimated width for compact tables with a fixed action column', () => {
    expect(resolveEstimatedTableScrollX(980, true, false)).toBe(981);
  });

  it('does not force a desktop table below the overflow threshold to 1100px', () => {
    expect(resolveEstimatedTableScrollX(960, false, false)).toBeUndefined();
  });

  it('keeps wide and mobile tables horizontally scrollable', () => {
    expect(resolveEstimatedTableScrollX(1180, false, false)).toBe(1181);
    expect(resolveEstimatedTableScrollX(720, false, true)).toBe(1100);
  });
});
