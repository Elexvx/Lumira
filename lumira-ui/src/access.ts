import type { CurrentUser, PluginAvailability } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { isSuperAdminUser } from '@/auth/adminAccess';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');
const hasAnyPermission = (permissions: Set<string>, keys: string[]) => keys.some((key) => hasPermission(permissions, key));

const AI_ASSISTANT_PERMISSIONS = ['ai:assistant:view', 'ai:chat:send'];
const AI_PERMISSIONS = [...AI_ASSISTANT_PERMISSIONS, 'ai:knowledge:view'];
const SYSTEM_CONFIG_PERMISSIONS = ['system:config:view', 'system:config:update'];

export default function access(initialState: { currentUser?: CurrentUser; availablePlugins?: PluginAvailability[] }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  const isLogin = Boolean(initialState?.currentUser?.sessionId) || tokenManager.hasToken();
  const isSettingsAdmin = isSuperAdminUser(initialState?.currentUser);
  const canVisitSystemConfig = isSettingsAdmin || hasAnyPermission(permissions, SYSTEM_CONFIG_PERMISSIONS);
  return {
    hasPermission: (permission: string) => hasPermission(permissions, permission),
    isLogin,
    canVisitDashboard: isLogin,
    canVisitProfile: isLogin,
    canVisitPersonalCenter: isLogin && (hasPermission(permissions, 'profile:view') || hasPermission(permissions, 'system:file:view')),
    canVisitUserCenter:
      isLogin &&
      ['user:center:view', 'system:user:view', 'system:department:view', 'system:online-user:view', 'system:role:view'].some((item) =>
        hasPermission(permissions, item),
      ),
    canVisitSystemManagement: isLogin && isSettingsAdmin,
    canVisitSystemMonitoring: isLogin && isSettingsAdmin,
    canVisitSystemMonitoringService: isLogin && isSettingsAdmin,
    canVisitSystemMonitoringRedis: isLogin && isSettingsAdmin,
    canVisitSystemMonitoringDocs: isLogin && isSettingsAdmin,
    canVisitPlatformUpdate: isLogin && isSettingsAdmin,
    canVisitSystemUsers: isLogin && hasPermission(permissions, 'system:user:view'),
    canVisitSystemDepartments: isLogin && hasPermission(permissions, 'system:department:view'),
    canVisitSystemRoles: isLogin && hasPermission(permissions, 'system:role:view'),
    canVisitSystemMenus: isLogin && isSettingsAdmin,
    canVisitSystemDicts: isLogin && isSettingsAdmin,
    canVisitSystemProfileFields: isLogin && isSettingsAdmin,
    canVisitSystemPersonalization: isLogin && canVisitSystemConfig,
    canVisitSystemSecurity: isLogin && isSettingsAdmin,
    canVisitSystemVerification: isLogin && isSettingsAdmin,
    canVisitSystemPayment: isLogin && isSettingsAdmin,
    canVisitSystemNotifications: isLogin && isSettingsAdmin,
    canVisitSystemFiles: isLogin && isSettingsAdmin,
    canVisitSystemMyFiles: isLogin && hasPermission(permissions, 'system:file:view'),
    canVisitDownloadCenter: isLogin && hasPermission(permissions, 'download:center:view'),
    canVisitTeam: isLogin && hasPermission(permissions, 'team:view'),
    canVisitProjects: isLogin && hasPermission(permissions, 'aiadc:project:view'),
    canVisitActivitiesRoot: isLogin && hasPermission(permissions, 'aiadc:activity:view'),
    canVisitActivities: isLogin && hasPermission(permissions, 'aiadc:activity:view'),
    canVisitCompetitions: isLogin && hasPermission(permissions, 'aiadc:competition:view'),
    canVisitPaymentOrders: isLogin && hasPermission(permissions, 'payment:order:view'),
    canVisitCertificates:
      isLogin &&
      hasAnyPermission(permissions, [
        'aiadc:certificate-template:view',
        'aiadc:certificate-batch:view',
        'aiadc:certificate-batch:create',
        'aiadc:certificate:view',
      ]),
    canVisitCertificateTemplates: isLogin && hasPermission(permissions, 'aiadc:certificate-template:view'),
    canVisitCertificateGenerate: isLogin && hasPermission(permissions, 'aiadc:certificate-batch:create'),
    canVisitCertificateRecords: isLogin && hasPermission(permissions, 'aiadc:certificate:view'),
    canVisitExperts: isLogin && hasPermission(permissions, 'expert:view'),
    canVisitWorkflow: isLogin && hasAnyPermission(permissions, ['workflow:view', 'workflow:config', 'workflow:approve']),
    canVisitWorkflowConfig: isLogin && hasPermission(permissions, 'workflow:config'),
    canVisitWorkflowTasks: isLogin && hasPermission(permissions, 'workflow:approve'),
    canVisitSystemAllFiles: isLogin && isSettingsAdmin,
    canVisitLocalization: isLogin && isSettingsAdmin,
    canVisitAudit: isLogin && isSettingsAdmin,
    canVisitSystemSettings: isLogin && isSettingsAdmin,
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:management:view')),
    canVisitSensitiveWordsPlugin: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:sensitive-words:view')),
    canVisitWorkOrderFeedbackPlugin: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:work-order-feedback:view')),
    canVisitAi: isLogin && AI_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitAiEmployees: isLogin && isSettingsAdmin,
    canVisitAiKnowledge: isLogin && hasPermission(permissions, 'ai:knowledge:view'),
    canVisitAiAssistant: isLogin && hasAnyPermission(permissions, AI_ASSISTANT_PERMISSIONS),
    canVisitPluginRuntime: isLogin,
  };
}
