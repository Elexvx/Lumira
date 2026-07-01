import type { CurrentUser, PluginAvailability } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { isSuperAdminUser } from '@/auth/adminAccess';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');
const hasAnyPermission = (permissions: Set<string>, keys: string[]) => keys.some((key) => hasPermission(permissions, key));
const COMMON_USER_ROLE_CODE = 'commonuser';
const COMMON_USER_ROLE_ID = 1002;

const AI_ASSISTANT_PERMISSIONS = ['ai:assistant:view', 'ai:chat:send'];
const AI_PERMISSIONS = [...AI_ASSISTANT_PERMISSIONS, 'ai:knowledge:view'];
const SYSTEM_CONFIG_PERMISSIONS = ['system:config:view', 'system:config:update'];
const SYSTEM_MENU_PERMISSIONS = ['system:menu:view', 'system:menu:create', 'system:menu:update', 'system:menu:status', 'system:menu:delete'];
const SYSTEM_DICT_PERMISSIONS = ['system:dict:view', 'system:dict:create', 'system:dict:update', 'system:dict:delete'];
const SYSTEM_VERIFICATION_PERMISSIONS = ['system:verification:view', 'system:verification:manage', ...SYSTEM_CONFIG_PERMISSIONS];
const PAYMENT_SETTINGS_PERMISSIONS = ['payment:view', 'payment:config:view', 'payment:config:update', 'payment:config:test'];
const SYSTEM_NOTIFICATION_PERMISSIONS = ['system:notification:view', 'system:notification:write', 'system:verification:manage', 'system:config:update'];
const SYSTEM_MONITORING_PERMISSIONS = ['system:monitor:view', 'system:monitor:service:view', 'system:monitor:redis:view', 'system:update:view'];
const SYSTEM_MONITORING_SERVICE_PERMISSIONS = ['system:monitor:view', 'system:monitor:service:view'];
const SYSTEM_MONITORING_REDIS_PERMISSIONS = ['system:monitor:view', 'system:monitor:redis:view'];
const SYSTEM_MONITORING_DOCS_PERMISSIONS = ['system:monitor:docs:view'];
const SYSTEM_UPDATE_PERMISSIONS = ['system:update:view', 'system:update:check', 'system:update:install', 'system:update:rollback'];
const SYSTEM_FILE_MANAGEMENT_PERMISSIONS = ['system:file:manage', 'system:file:manage:delete'];
const SYSTEM_PLUGIN_PERMISSIONS = ['plugin:management:view'];
const AUDIT_PERMISSIONS = ['audit:view', 'audit:login:view', 'audit:operation:view'];
const LOCALIZATION_PERMISSIONS = ['localization:view'];

export default function access(initialState: { currentUser?: CurrentUser; availablePlugins?: PluginAvailability[] }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  const roleCodes = new Set((initialState?.currentUser?.availableRoles ?? []).map((role) => role.roleCode?.trim().toLowerCase()).filter(Boolean));
  const roleIds = new Set(initialState?.currentUser?.roleIds ?? []);
  const isLogin = Boolean(initialState?.currentUser?.sessionId) || tokenManager.hasToken();
  const isCommonUserOnly =
    (roleCodes.size > 0 && Array.from(roleCodes).every((roleCode) => roleCode === COMMON_USER_ROLE_CODE))
    || (roleIds.size > 0 && Array.from(roleIds).every((roleId) => roleId === COMMON_USER_ROLE_ID));
  const isSettingsAdmin = isSuperAdminUser(initialState?.currentUser);
  const canAccessSettings = (keys: string[]) => isLogin && (isSettingsAdmin || hasAnyPermission(permissions, keys));
  const canVisitSystemConfig = canAccessSettings(SYSTEM_CONFIG_PERMISSIONS);
  const canVisitSystemMenus = canAccessSettings(SYSTEM_MENU_PERMISSIONS);
  const canVisitSystemDicts = canAccessSettings(SYSTEM_DICT_PERMISSIONS);
  const canVisitSystemProfileFields = canVisitSystemConfig;
  const canVisitSystemPersonalization = canVisitSystemConfig;
  const canVisitSystemSecurity = canVisitSystemConfig;
  const canVisitSystemVerification = canAccessSettings(SYSTEM_VERIFICATION_PERMISSIONS);
  const canVisitSystemPayment = canAccessSettings(PAYMENT_SETTINGS_PERMISSIONS);
  const canVisitSystemNotifications = canAccessSettings(SYSTEM_NOTIFICATION_PERMISSIONS);
  const canVisitSystemAllFiles = canAccessSettings(SYSTEM_FILE_MANAGEMENT_PERMISSIONS);
  const canVisitSystemMonitoring = canAccessSettings(SYSTEM_MONITORING_PERMISSIONS);
  const canVisitSystemMonitoringService = canAccessSettings(SYSTEM_MONITORING_SERVICE_PERMISSIONS);
  const canVisitSystemMonitoringRedis = canAccessSettings(SYSTEM_MONITORING_REDIS_PERMISSIONS);
  const canVisitSystemMonitoringDocs = canAccessSettings(SYSTEM_MONITORING_DOCS_PERMISSIONS);
  const canVisitPlatformUpdate = canAccessSettings(SYSTEM_UPDATE_PERMISSIONS);
  const canVisitSystemPlugins = canAccessSettings(SYSTEM_PLUGIN_PERMISSIONS);
  const canVisitAudit = canAccessSettings(AUDIT_PERMISSIONS);
  const canVisitLocalization = canAccessSettings(LOCALIZATION_PERMISSIONS);
  const canVisitDownloadCenter = isLogin && !isCommonUserOnly && hasPermission(permissions, 'download:center:view');
  const canVisitTeam = isLogin && !isCommonUserOnly && hasPermission(permissions, 'team:view');
  const canVisitProjects = isLogin && !isCommonUserOnly && hasPermission(permissions, 'aiadc:project:view');
  const canVisitActivities = isLogin && !isCommonUserOnly && hasPermission(permissions, 'aiadc:activity:view');
  const canVisitCompetitions = isLogin && !isCommonUserOnly && hasPermission(permissions, 'aiadc:competition:view');
  const canVisitPaymentOrders = isLogin && !isCommonUserOnly && hasPermission(permissions, 'payment:order:view');
  const canVisitCompetitionRegister = isLogin && hasAnyPermission(permissions, ['aiadc:registration:view', 'aiadc:registration:create']);
  const canVisitActivityRegister = isLogin;
  const canVisitDataManagement =
    !isCommonUserOnly &&
    [canVisitCompetitions, canVisitActivities, canVisitProjects, canVisitTeam, canVisitPaymentOrders, canVisitDownloadCenter].some(Boolean);
  const canVisitSystemSettings =
    isLogin &&
    (isSettingsAdmin ||
      hasPermission(permissions, 'system:view') ||
      [
        canVisitSystemMenus,
        canVisitSystemDicts,
        canVisitSystemProfileFields,
        canVisitSystemPersonalization,
        canVisitSystemSecurity,
        canVisitSystemVerification,
        canVisitSystemPayment,
        canVisitSystemNotifications,
        canVisitSystemAllFiles,
        canVisitSystemMonitoring,
        canVisitSystemMonitoringDocs,
        canVisitPlatformUpdate,
        canVisitSystemPlugins,
        canVisitAudit,
        canVisitLocalization,
      ].some(Boolean));
  const canVisitAnyUserCenter =
    isLogin &&
    !isCommonUserOnly &&
    (
      ['user:center:view', 'system:user:view', 'system:department:view', 'system:online-user:view', 'system:role:view'].some((item) =>
        hasPermission(permissions, item),
      )
      || hasPermission(permissions, 'profile:view')
      || hasPermission(permissions, 'system:file:view')
    );

  return {
    hasPermission: (permission: string) => hasPermission(permissions, permission),
    isLogin,
    canVisitDashboard: isLogin && !isCommonUserOnly && (isSettingsAdmin || hasPermission(permissions, 'dashboard:view')),
    canVisitProfile: isLogin && !isCommonUserOnly && hasPermission(permissions, 'profile:view'),
    canVisitPersonalCenter: isLogin && !isCommonUserOnly && (hasPermission(permissions, 'profile:view') || hasPermission(permissions, 'system:file:view')),
    canVisitAnyUserCenter,
    canVisitUserCenter:
      isLogin &&
      !isCommonUserOnly &&
      ['user:center:view', 'system:user:view', 'system:department:view', 'system:online-user:view', 'system:role:view'].some((item) =>
        hasPermission(permissions, item),
      ),
    canVisitDataManagement,
    canVisitSystemManagement: canVisitSystemSettings,
    canVisitSystemMonitoring,
    canVisitSystemMonitoringService,
    canVisitSystemMonitoringRedis,
    canVisitSystemMonitoringDocs,
    canVisitPlatformUpdate,
    canVisitSystemUsers: isLogin && hasPermission(permissions, 'system:user:view'),
    canVisitSystemDepartments: isLogin && hasPermission(permissions, 'system:department:view'),
    canVisitSystemRoles: isLogin && hasPermission(permissions, 'system:role:view'),
    canVisitSystemMenus,
    canVisitSystemDicts,
    canVisitSystemProfileFields,
    canVisitSystemPersonalization,
    canVisitSystemSecurity,
    canVisitSystemVerification,
    canVisitSystemPayment,
    canVisitSystemNotifications,
    canVisitSystemFiles: canVisitSystemAllFiles,
    canVisitSystemMyFiles: isLogin && !isCommonUserOnly && hasPermission(permissions, 'system:file:view'),
    canVisitDownloadCenter,
    canVisitTeam,
    canVisitProjects,
    canVisitActivitiesRoot: canVisitActivities,
    canVisitActivities,
    canVisitCompetitions,
    canVisitCompetitionRegister,
    canVisitActivityRegister,
    canVisitPaymentOrders,
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
    canVisitExperts: isLogin && !isCommonUserOnly && hasPermission(permissions, 'expert:view'),
    canVisitWorkflow: isLogin && hasAnyPermission(permissions, ['workflow:view', 'workflow:config', 'workflow:approve']),
    canVisitWorkflowConfig: isLogin && hasPermission(permissions, 'workflow:config'),
    canVisitWorkflowTasks: isLogin && hasPermission(permissions, 'workflow:approve'),
    canVisitSystemAllFiles,
    canVisitLocalization,
    canVisitAudit,
    canVisitSystemSettings,
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins,
    canVisitSensitiveWordsPlugin: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:sensitive-words:view')),
    canVisitWorkOrderFeedbackPlugin: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:work-order-feedback:view')),
    canVisitAi: isLogin && AI_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitAiEmployees: isLogin && isSettingsAdmin,
    canVisitAiKnowledge: isLogin && hasPermission(permissions, 'ai:knowledge:view'),
    canVisitAiAssistant: isLogin && hasAnyPermission(permissions, AI_ASSISTANT_PERMISSIONS),
    canVisitPluginRuntime: isLogin,
  };
}
