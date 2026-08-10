import { describe, expect, it } from 'vitest';
import {
  DEFAULT_SETTING_ROUTE_ORDER,
  SETTINGS_FALLBACK_PATH_SET,
} from './settingsNavigationConfig';

describe('settings navigation config', () => {
  it('keeps every fallback settings route in the default route order', () => {
    expect(new Set(DEFAULT_SETTING_ROUTE_ORDER)).toEqual(SETTINGS_FALLBACK_PATH_SET);
    expect(DEFAULT_SETTING_ROUTE_ORDER).toContain('/settings/payment');
    expect(DEFAULT_SETTING_ROUTE_ORDER).toContain('/settings/workflows');
  });
});
