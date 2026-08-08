import { describe, expect, it, vi } from 'vitest';
import access from './access';
import type { CurrentUser } from './types/api';

vi.mock('@/auth/token', () => ({
  tokenManager: {
    hasToken: vi.fn(() => false),
  },
}));

const userWithPermissions = (permissions: string[]): CurrentUser =>
  ({
    userId: 2002,
    userUuid: 'user-uuid-2002',
    username: 'operator',
    sessionId: 'session-1',
    sessionVersion: 1,
    permissionsVersion: 'permissions-1',
    permissions,
    availableRoles: [{ roleCode: 'operator', roleName: 'Operator' }],
  }) as CurrentUser;

const commonUserWithPermissions = (permissions: string[]): CurrentUser =>
  ({
    userId: 1002,
    userUuid: 'user-uuid-1002',
    username: 'user',
    sessionId: 'session-2',
    sessionVersion: 1,
    permissionsVersion: 'permissions-2',
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

  it('exposes administrator-only settings while simulating the administrator role', () => {
    const result = access({
      currentUser: {
        ...userWithPermissions([]),
        simulatedRoleId: 1001,
        availableRoles: [
          { id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' },
          { id: 1001, roleCode: 'ADMIN', roleName: 'Administrator', roleType: 'SYSTEM' },
        ],
      },
    });

    expect(result.canVisitSystemSettings).toBe(true);
    expect(result.canVisitSystemPersonalization).toBe(true);
    expect(result.canVisitAiEmployees).toBe(true);
  });

  it('does not inherit administrator access while simulating a non-admin role', () => {
    const result = access({
      currentUser: {
        ...userWithPermissions([]),
        userId: 1001,
        username: 'admin',
        simulatedRoleId: 1002,
        availableRoles: [
          { id: 1002, roleCode: 'commonuser', roleName: 'Common User', roleType: 'FUNCTIONAL' },
          { id: 1001, roleCode: 'ADMIN', roleName: 'Administrator', roleType: 'SYSTEM' },
        ],
      },
    });

    expect(result.canVisitSystemSettings).toBe(false);
    expect(result.canVisitSystemPersonalization).toBe(false);
    expect(result.canVisitAiEmployees).toBe(false);
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

  it('allows ai assistant for users with ai view permission', () => {
    const result = access({ currentUser: userWithPermissions(['ai:view']) });

    expect(result.canVisitAi).toBe(true);
    expect(result.canVisitAiAssistant).toBe(true);
  });

  it('uses role-derived permissions instead of hard-coded common user blocks', () => {
    const result = access({
      currentUser: commonUserWithPermissions([
        'dashboard:view',
        'profile:view',
        'system:user:view',
        'system:role:view',
        'system:online-user:view',
        'system:config:view',
        'team:view',
        'aiadc:project:view',
        'expert:view',
        'workflow:config',
        'plugin:sensitive-words:view',
        'aiadc:activity:view',
        'aiadc:competition:view',
        'aiadc:registration:view',
        'aiadc:registration:create',
      ]),
    });

    expect(result.canVisitDashboard).toBe(true);
    expect(result.canVisitPersonalCenter).toBe(true);
    expect(result.canVisitDataManagement).toBe(true);
    expect(result.canVisitExperts).toBe(true);
    expect(result.canVisitSystemSettings).toBe(true);
    expect(result.canVisitSystemUsers).toBe(true);
    expect(result.canVisitSystemRoles).toBe(true);
    expect(result.canVisitSystemOnlineUsers).toBe(true);
    expect(result.canVisitWorkflow).toBe(true);
    expect(result.canVisitSensitiveWordsPlugin).toBe(true);
    expect(result.canVisitCompetitionRegister).toBe(true);
    expect(result.canVisitActivityRegister).toBe(true);
    expect(result.canVisitPluginRuntime).toBe(true);
  });

  it('does not expose activity registration without role permissions', () => {
    const result = access({ currentUser: commonUserWithPermissions(['aiadc:registration:view']) });

    expect(result.canVisitCompetitionRegister).toBe(true);
    expect(result.canVisitActivityRegister).toBe(false);
  });

  it('shows the settings entry to sensitive-words-only plugin administrators', () => {
    const result = access({ currentUser: userWithPermissions(['plugin:sensitive-words:view']) });

    expect(result.canVisitSensitiveWordsPlugin).toBe(true);
    expect(result.canVisitSystemSettings).toBe(true);
  });

  it('does not expose data management for permissions whose pages were retired', () => {
    const projectOnly = access({ currentUser: userWithPermissions(['aiadc:project:view']) });
    const teamOnly = access({ currentUser: userWithPermissions(['team:view']) });

    expect(projectOnly.canVisitDataManagement).toBe(false);
    expect(teamOnly.canVisitDataManagement).toBe(false);
  });

  it('allows activity registration with create permission without exposing activity management', () => {
    const result = access({ currentUser: commonUserWithPermissions(['aiadc:activity:create']) });

    expect(result.canVisitActivityRegister).toBe(true);
    expect(result.canVisitActivities).toBe(false);
    expect(result.canVisitDataManagement).toBe(false);
  });

  it('keeps the default common user surface to dashboard, registration, and personal center', () => {
    const result = access({
      currentUser: commonUserWithPermissions([
        'dashboard:view',
        'profile:view',
        'system:file:view',
        'system:file:upload',
        'aiadc:registration:view',
        'aiadc:registration:create',
        'aiadc:registration:update',
        'aiadc:registration:pay',
        'aiadc:activity:create',
        'aiadc:material:view',
        'aiadc:material:submit',
        'aiadc:stage:view',
      ]),
    });

    expect(result.canVisitDashboard).toBe(true);
    expect(result.canVisitCompetitionRegister).toBe(true);
    expect(result.canVisitActivityRegister).toBe(true);
    expect(result.canVisitProfile).toBe(true);
    expect(result.canVisitPersonalCenter).toBe(true);
    expect(result.canVisitSystemMyFiles).toBe(true);
    expect(result.canVisitDataManagement).toBe(false);
    expect(result.canVisitDownloadCenter).toBe(false);
    expect(result.canVisitAnyUserCenter).toBe(false);
    expect(result.canVisitUserCenter).toBe(false);
    expect(result.canVisitExperts).toBe(false);
    expect(result.canVisitAi).toBe(false);
    expect(result.canVisitSystemSettings).toBe(false);
    expect(result.canVisitPluginRuntime).toBe(false);
  });

  it('keeps download center access independent after query center removal', () => {
    const result = access({ currentUser: commonUserWithPermissions(['download:center:view']) });

    expect(result.canVisitDataManagement).toBe(true);
    expect(result.canVisitDownloadCenter).toBe(true);
  });

  it('exposes registration dossiers only to competition managers with registration read permission', () => {
    const competitionOnly = access({ currentUser: userWithPermissions(['aiadc:competition:view']) });
    const registrationOnly = access({ currentUser: userWithPermissions(['aiadc:registration:view']) });
    const manager = access({
      currentUser: userWithPermissions(['aiadc:competition:view', 'aiadc:registration:view']),
    });

    expect(competitionOnly.canVisitCompetitionRegistrations).toBe(false);
    expect(registrationOnly.canVisitCompetitionRegistrations).toBe(false);
    expect(manager.canVisitCompetitionRegistrations).toBe(true);
    expect(manager.canVisitDataManagement).toBe(true);
  });

  it('separates dossier viewing, dataset export, and material download permissions', () => {
    const exporter = access({
      currentUser: userWithPermissions([
        'aiadc:competition:view',
        'registration:dataset:export',
      ]),
    });
    const materialReader = access({
      currentUser: userWithPermissions(['registration:material:download']),
    });

    expect(exporter.canVisitCompetitionRegistrations).toBe(true);
    expect(exporter.canExportCompetitionRegistrations).toBe(true);
    expect(exporter.canExportSensitiveCompetitionRegistrations).toBe(false);
    expect(exporter.canDownloadRegistrationMaterials).toBe(false);
    expect(materialReader.canExportCompetitionRegistrations).toBe(false);
    expect(materialReader.canDownloadRegistrationMaterials).toBe(true);
  });

  it('keeps sensitive dossier viewing and export behind dedicated permissions', () => {
    const viewer = access({
      currentUser: userWithPermissions(['registration:dataset:view-sensitive']),
    });
    const exporter = access({
      currentUser: userWithPermissions(['registration:dataset:export-sensitive']),
    });

    expect(viewer.canViewSensitiveCompetitionRegistrations).toBe(true);
    expect(viewer.canExportSensitiveCompetitionRegistrations).toBe(false);
    expect(exporter.canViewSensitiveCompetitionRegistrations).toBe(true);
    expect(exporter.canExportSensitiveCompetitionRegistrations).toBe(true);
  });

  it('exposes the review workbench through review permissions without workflow approval', () => {
    const expert = access({
      currentUser: userWithPermissions([
        'review:workbench:view',
        'review:task:view',
        'review:score:submit',
      ]),
    });
    const reviewManager = access({
      currentUser: userWithPermissions([
        'review:plan:manage',
        'review:batch:create',
      ]),
    });
    const workflowApprover = access({
      currentUser: userWithPermissions(['workflow:approve']),
    });

    expect(expert.canVisitExpertReview).toBe(true);
    expect(expert.canVisitReviewWorkbench).toBe(true);
    expect(expert.canVisitWorkflowTasks).toBe(false);
    expect(reviewManager.canVisitReviewWorkbench).toBe(true);
    expect(workflowApprover.canVisitReviewWorkbench).toBe(false);
  });

  it('keeps participant appeal access separate from the expert review workbench', () => {
    const participant = access({
      currentUser: userWithPermissions(['review:appeal:submit']),
    });
    const appealManager = access({
      currentUser: userWithPermissions(['review:appeal:manage']),
    });

    expect(participant.canVisitCompetitionReviewResults).toBe(true);
    expect(participant.canVisitReviewWorkbench).toBe(false);
    expect(appealManager.canManageReviewAppeals).toBe(true);
    expect(appealManager.canVisitCompetitionReviewResults).toBe(false);
  });

  it('updates visible settings pages when role permissions are adjusted', () => {
    const beforeAdjustment = access({ currentUser: userWithPermissions(['system:menu:view']) });
    const afterAdjustment = access({
      currentUser: {
        ...userWithPermissions(['system:dict:view']),
        permissionsVersion: 'permissions-2',
      },
    });

    expect(beforeAdjustment.canVisitSystemMenus).toBe(true);
    expect(beforeAdjustment.canVisitSystemDicts).toBe(false);
    expect(afterAdjustment.canVisitSystemMenus).toBe(false);
    expect(afterAdjustment.canVisitSystemDicts).toBe(true);
  });

  it('does not expose generic plugin runtime to users who only have a session', () => {
    const result = access({ currentUser: userWithPermissions([]) });

    expect(result.canVisitPluginRuntime).toBe(false);
  });

  it('does not expose a workflow catalog when the role cannot access any workflow page', () => {
    const result = access({ currentUser: userWithPermissions(['workflow:view']) });

    expect(result.canVisitWorkflow).toBe(false);
    expect(result.canVisitWorkflowTasks).toBe(false);
    expect(result.canVisitWorkflowConfig).toBe(false);
  });

  it('does not trust token-only state without a complete current user tuple', async () => {
    const { tokenManager } = await import('@/auth/token');
    vi.mocked(tokenManager.hasToken).mockReturnValueOnce(true);

    const result = access({
      currentUser: {
        userId: 2002,
        username: 'operator',
        sessionId: 'session-1',
        permissions: ['dashboard:view', 'system:config:view'],
      } as CurrentUser,
    });

    expect(result.isLogin).toBe(false);
    expect(result.canVisitDashboard).toBe(false);
    expect(result.canVisitSystemSettings).toBe(false);
  });

  it('does not trust users missing permissions version', () => {
    const result = access({
      currentUser: {
        ...userWithPermissions(['dashboard:view', 'system:config:view']),
        permissionsVersion: undefined,
      },
    });

    expect(result.isLogin).toBe(false);
    expect(result.canVisitDashboard).toBe(false);
    expect(result.canVisitSystemSettings).toBe(false);
  });

  it('allows generic plugin runtime when a user has plugin runtime resources', () => {
    const result = access({
      currentUser: userWithPermissions(['plugin:custom-widget:view']),
      availablePlugins: [
        {
          pluginCode: 'custom-widget',
          pluginName: 'Custom Widget',
          version: '1.0.0',
          manifestPath: '/plugins/custom-widget/manifest.json',
        },
      ],
    });

    expect(result.canVisitPluginRuntime).toBe(true);
  });
});
