import { describe, expect, it } from 'vitest';
import type { BrandingSettings, CurrentUser } from '@/types/api';
import { shouldShowMaintenancePage } from './maintenanceMode';

const enabledSettings = { maintenanceModeEnabled: true } as BrandingSettings;
const operator = {
  permissions: ['system:config:update'],
} as CurrentUser;

describe('maintenance mode route gate', () => {
  it('does not block routes while maintenance mode is disabled', () => {
    expect(
      shouldShowMaintenancePage({
        brandingSettings: { maintenanceModeEnabled: false } as BrandingSettings,
        pathname: '/dashboard/home',
      }),
    ).toBe(false);
  });

  it('blocks public and authenticated application routes while enabled', () => {
    expect(
      shouldShowMaintenancePage({
        brandingSettings: enabledSettings,
        pathname: '/dashboard/home',
        currentUser: operator,
      }),
    ).toBe(true);
  });

  it('keeps the login page reachable', () => {
    expect(
      shouldShowMaintenancePage({
        brandingSettings: enabledSettings,
        pathname: '/user/login',
      }),
    ).toBe(false);
  });

  it('allows only configuration operators to reach personalization settings', () => {
    expect(
      shouldShowMaintenancePage({
        brandingSettings: enabledSettings,
        pathname: '/settings/personalization',
        currentUser: operator,
      }),
    ).toBe(false);
    expect(
      shouldShowMaintenancePage({
        brandingSettings: enabledSettings,
        pathname: '/settings/personalization',
        currentUser: { permissions: ['system:config:view'] } as CurrentUser,
      }),
    ).toBe(true);
  });
});
