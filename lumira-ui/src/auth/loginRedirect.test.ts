import { afterEach, describe, expect, it, vi } from 'vitest';
import { createLoginSessionBroadcastListener, resolveAuthorizedLoginRedirectTarget, resolveLoginPageRuntimeRedirectTarget } from '@/auth/loginRedirect';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import type { CurrentUser } from '@/types/api';

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = [];
  onmessage: ((event: MessageEvent<{ type?: string }>) => void) | null = null;

  constructor(public name: string) {
    FakeBroadcastChannel.instances.push(this);
  }

  close() {
    FakeBroadcastChannel.instances = FakeBroadcastChannel.instances.filter((instance) => instance !== this);
  }
}

const originalBroadcastChannel = globalThis.BroadcastChannel;

const trustedUser = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  userId: 2001,
  userUuid: 'user-uuid-2001',
  username: 'operator',
  sessionId: 'session-1',
  sessionVersion: 1,
  permissionsVersion: 'permissions-1',
  permissions: [],
  availableRoles: [{ id: 1, roleCode: 'operator', roleName: 'Operator', roleType: 'FUNCTIONAL' }],
  ...overrides,
});

afterEach(() => {
  endLoginFlow();
  FakeBroadcastChannel.instances = [];
  vi.unstubAllGlobals();
  if (originalBroadcastChannel) {
    vi.stubGlobal('BroadcastChannel', originalBroadcastChannel);
  }
});

describe('login session broadcast listener', () => {
  it('does not reload the current tab while login flow is still deciding the next step', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate);
    beginLoginFlow();
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('navigates on session updates outside the active login flow', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate);
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).toHaveBeenCalledWith('/dashboard/home');
  });

  it('does not navigate when the caller suppresses login broadcast redirects', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate, () => false);
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).not.toHaveBeenCalled();
  });
});

describe('resolveAuthorizedLoginRedirectTarget', () => {
  it('does not consume the redirect target on the login page before authentication', () => {
    const target = resolveLoginPageRuntimeRedirectTarget({
      pathname: '/user/login',
      search: '?redirect=%2Fcompetitions%2Fregister',
      isAuthenticated: false,
    });

    expect(target).toBe('/dashboard/home');
  });

  it('keeps the redirect target available once authentication is established', () => {
    const target = resolveLoginPageRuntimeRedirectTarget({
      pathname: '/user/login',
      search: '?redirect=%2Fcompetitions%2Fregister',
      isAuthenticated: true,
    });

    expect(target).toBe('/competitions/register');
  });

  it('falls back to the competition registration page when a common user cannot access the dashboard', () => {
    const target = resolveAuthorizedLoginRedirectTarget(
      '',
      trustedUser({
        permissions: ['aiadc:registration:view'],
        roleIds: [1002],
        availableRoles: [{ id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' }],
        defaultHomePath: '/dashboard/home',
      }),
      [],
    );

    expect(target).toBe('/competitions/register');
  });

  it('ignores an inaccessible redirect query and still lands on an accessible page', () => {
    const target = resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fdashboard%2Fhome',
      trustedUser({
        permissions: ['aiadc:registration:view'],
        roleIds: [1002],
        availableRoles: [{ id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' }],
      }),
      [],
    );

    expect(target).toBe('/competitions/register');
  });

  it('lands different roles on their configured accessible default pages', () => {
    const dashboardOperatorTarget = resolveAuthorizedLoginRedirectTarget(
      '',
      trustedUser({
        permissions: ['dashboard:view'],
        defaultHomePath: '/dashboard/home',
      }),
      [{ id: 1, menuCode: 'dashboard.home', name: 'Dashboard', path: '/dashboard/home' }],
    );
    const registrationUserTarget = resolveAuthorizedLoginRedirectTarget(
      '',
      trustedUser({
        permissions: ['aiadc:registration:view'],
        availableRoles: [{ id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' }],
        defaultHomePath: '/competitions/register',
      }),
      [{ id: 2, menuCode: 'competition.registration', name: 'Competition registration', path: '/competitions/register' }],
    );
    const menuAdminTarget = resolveAuthorizedLoginRedirectTarget(
      '',
      trustedUser({
        permissions: ['system:menu:view'],
        defaultHomePath: '/settings/menus',
      }),
      [{ id: 3, menuCode: 'settings.menus', name: 'Menus', path: '/settings/menus' }],
    );

    expect(dashboardOperatorTarget).toBe('/dashboard/home');
    expect(registrationUserTarget).toBe('/competitions/register');
    expect(menuAdminTarget).toBe('/settings/menus');
  });

  it('changes the landing page when role-visible pages are adjusted', () => {
    const beforeAdjustmentTarget = resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fsettings%2Fdicts',
      trustedUser({
        permissions: ['system:menu:view'],
        defaultHomePath: '/settings/menus',
      }),
      [{ id: 4, menuCode: 'settings.menus', name: 'Menus', path: '/settings/menus' }],
    );
    const afterAdjustmentTarget = resolveAuthorizedLoginRedirectTarget(
      '?redirect=%2Fsettings%2Fmenus',
      trustedUser({
        permissions: ['system:dict:view'],
        permissionsVersion: 'permissions-2',
        defaultHomePath: '/settings/dicts',
      }),
      [{ id: 5, menuCode: 'settings.dicts', name: 'Dicts', path: '/settings/dicts' }],
    );

    expect(beforeAdjustmentTarget).toBe('/settings/menus');
    expect(afterAdjustmentTarget).toBe('/settings/dicts');
  });

  it('prefers the selected role default page over the aggregate user default page', () => {
    const target = resolveAuthorizedLoginRedirectTarget(
      '',
      trustedUser({
        simulatedRoleId: 3002,
        permissions: ['system:dict:view'],
        defaultHomePath: '/dashboard/home',
        availableRoles: [
          {
            id: 3001,
            roleCode: 'menu_role',
            roleName: 'Menu Role',
            roleType: 'FUNCTIONAL',
            defaultHomePath: '/settings/menus',
          },
          {
            id: 3002,
            roleCode: 'dict_role',
            roleName: 'Dict Role',
            roleType: 'FUNCTIONAL',
            defaultHomePath: '/settings/dicts',
          },
        ],
      }),
      [{ id: 6, menuCode: 'settings.dicts', name: 'Dicts', path: '/settings/dicts' }],
    );

    expect(target).toBe('/settings/dicts');
  });
});
