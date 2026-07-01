import { describe, expect, it, vi } from 'vitest';
import access from './access';
import type { CurrentUser } from './types/api';

vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: () => false,
  },
}));

const userWithPermissions = (permissions: string[]): CurrentUser =>
  ({
    userId: 2002,
    username: 'operator',
    sessionId: 'session-1',
    permissions,
    availableRoles: [{ roleCode: 'operator', roleName: 'Operator' }],
  }) as CurrentUser;

const commonUserWithPermissions = (permissions: string[]): CurrentUser =>
  ({
    userId: 1002,
    username: 'user',
    sessionId: 'session-2',
    permissions,
    availableRoles: [{ roleCode: 'commonuser', roleName: 'Common User' }],
  }) as CurrentUser;

describe('access', () => {
  it('allows personalization settings for users with system config view permission', () => {
    const result = access({ currentUser: userWithPermissions(['system:config:view']) });

    expect(result.canVisitSystemPersonalization).toBe(true);
  });

  it('allows personalization settings for users with system config update permission', () => {
    const result = access({ currentUser: userWithPermissions(['system:config:update']) });

    expect(result.canVisitSystemPersonalization).toBe(true);
  });

  it('does not expose personalization settings to unrelated permissions', () => {
    const result = access({ currentUser: userWithPermissions(['system:user:view']) });

    expect(result.canVisitSystemPersonalization).toBe(false);
  });

  it('allows system security, profile fields, and verification for config viewers', () => {
    const result = access({ currentUser: userWithPermissions(['system:config:view']) });

    expect(result.canVisitSystemProfileFields).toBe(true);
    expect(result.canVisitSystemSecurity).toBe(true);
    expect(result.canVisitSystemVerification).toBe(true);
    expect(result.canVisitSystemSettings).toBe(true);
  });

  it('allows menu, dict, file, monitoring, audit, localization, and plugin settings by page permissions', () => {
    const result = access({
      currentUser: userWithPermissions([
        'system:menu:view',
        'system:dict:view',
        'system:file:manage',
        'system:monitor:view',
        'system:monitor:docs:view',
        'audit:view',
        'localization:view',
        'plugin:management:view',
      ]),
    });

    expect(result.canVisitSystemMenus).toBe(true);
    expect(result.canVisitSystemDicts).toBe(true);
    expect(result.canVisitSystemAllFiles).toBe(true);
    expect(result.canVisitSystemMonitoring).toBe(true);
    expect(result.canVisitSystemMonitoringService).toBe(true);
    expect(result.canVisitSystemMonitoringRedis).toBe(true);
    expect(result.canVisitSystemMonitoringDocs).toBe(true);
    expect(result.canVisitAudit).toBe(true);
    expect(result.canVisitLocalization).toBe(true);
    expect(result.canVisitSystemPlugins).toBe(true);
    expect(result.canVisitSystemSettings).toBe(true);
  });

  it('allows payment settings for payment config permissions', () => {
    const result = access({ currentUser: userWithPermissions(['payment:config:view']) });

    expect(result.canVisitSystemPayment).toBe(true);
    expect(result.canVisitSystemSettings).toBe(true);
  });

  it('limits common users to registration-facing entry points', () => {
    const result = access({
      currentUser: commonUserWithPermissions([
        'dashboard:view',
        'profile:view',
        'team:view',
        'aiadc:project:view',
        'expert:view',
        'aiadc:activity:view',
        'aiadc:competition:view',
        'aiadc:registration:view',
        'aiadc:registration:create',
      ]),
    });

    expect(result.canVisitDashboard).toBe(false);
    expect(result.canVisitPersonalCenter).toBe(false);
    expect(result.canVisitDataManagement).toBe(false);
    expect(result.canVisitExperts).toBe(false);
    expect(result.canVisitCompetitionRegister).toBe(true);
    expect(result.canVisitActivityRegister).toBe(true);
  });
});
