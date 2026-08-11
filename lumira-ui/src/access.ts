import type { CurrentUser, PluginAvailability } from '@/types/api';
import { isSuperAdminUser } from '@/auth/adminAccess';
import { isTrustedCurrentUser } from '@/auth/sessionState';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');
const hasAnyPermission = (permissions: Set<string>, keys: string[]) => keys.some((key) => hasPermission(permissions, key));
const hasPluginRuntimePermission = (permissions: Set<string>) =>
  Array.from(permissions).some((permission) => permission === '*' || /^plugin:[^:]+:(view|create|manage|import)$/.test(permission));

const AI_ASSISTANT_PERMISSIONS = ['ai:view', 'ai:chat:send'];
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
const COMPETITION_REGISTER_PERMISSIONS = [
  'aiadc:registration:view',
  'aiadc:registration:create',
  'aiadc:registration:update',
  'aiadc:registration:pay',
  'aiadc:material:view',
  'aiadc:material:submit',
  'aiadc:stage:view',
  'aiadc:stage:manage',
  'payment:order:view',
];
const ACTIVITY_REGISTER_PERMISSIONS = ['aiadc:activity:create', 'aiadc:activity:view'];
const REVIEW_WORKBENCH_PERMISSIONS = [
  'review:workbench:view',
  'review:plan:manage',
  'review:batch:create',
  'review:assignment:manage',
  'review:roster:manage',
  'review:notification:send',
  'review:checkin:scan',
  'review:task:view',
  'review:score:submit',
  'review:result:aggregate',
  'review:result:finalize',
  'review:result:publish',
  'review:audit:view',
];
const USER_CENTER_MANAGEMENT_PERMISSIONS = ['user:center:view', 'system:user:view', 'system:department:view', 'system:online-user:view', 'system:role:view'];

export default function access(initialState: { currentUser?: CurrentUser; availablePlugins?: PluginAvailability[] }) {
  const trustedUser = isTrustedCurrentUser(initialState?.currentUser) ? initialState.currentUser : undefined;
  const permissions = new Set(trustedUser?.permissions ?? []);
  const availablePlugins = initialState?.availablePlugins ?? [];
  const isLogin = Boolean(trustedUser);
  const isSettingsAdmin = isSuperAdminUser(trustedUser);
  const canVisitPluginRuntime = isLogin && (isSettingsAdmin || availablePlugins.length > 0 || hasPluginRuntimePermission(permissions));
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
  const canVisitDownloadCenter = isLogin && hasPermission(permissions, 'download:center:view');
  const canVisitTeam = isLogin && hasPermission(permissions, 'team:view');
  const canVisitProjects = isLogin && hasPermission(permissions, 'aiadc:project:view');
  const canVisitActivities = isLogin && hasPermission(permissions, 'aiadc:activity:view');
  const canVisitCompetitions = isLogin && hasPermission(permissions, 'aiadc:competition:view');
  const canVisitCompetitionRegistrations =
    canVisitCompetitions && hasAnyPermission(permissions, [
      'aiadc:registration:view',
      'registration:dataset:view',
      'registration:dataset:export',
    ]);
  const canExportCompetitionRegistrations =
    isLogin && hasPermission(permissions, 'registration:dataset:export');
  const canViewSensitiveCompetitionRegistrations =
    isLogin && hasAnyPermission(permissions, [
      'registration:dataset:view-sensitive',
      'registration:dataset:export-sensitive',
    ]);
  const canExportSensitiveCompetitionRegistrations =
    isLogin && hasPermission(permissions, 'registration:dataset:export-sensitive');
  const canDownloadRegistrationMaterials =
    isLogin && hasPermission(permissions, 'registration:material:download');
  const canVisitPaymentOrders = isLogin && hasPermission(permissions, 'payment:order:view');
  const canVisitReviewWorkbench =
    isLogin && hasAnyPermission(permissions, REVIEW_WORKBENCH_PERMISSIONS);
  const canVisitCompetitionReviewResults =
    isLogin && hasPermission(permissions, 'review:appeal:submit');
  const canManageReviewAppeals =
    isLogin && hasPermission(permissions, 'review:appeal:manage');
  const canVisitWorkflowConfig = isLogin && hasPermission(permissions, 'workflow:config');
  const canVisitWorkflowTasks = isLogin && hasPermission(permissions, 'workflow:approve');
  const canVisitCompetitionRegister = isLogin && hasAnyPermission(permissions, COMPETITION_REGISTER_PERMISSIONS);
  const canUseBuiltinMockPayment = isLogin
    && hasPermission(permissions, 'aiadc:registration:pay')
    && availablePlugins.some((plugin) => plugin.pluginCode === 'builtin-mock-payment');
  const canVisitActivityRegister = isLogin && hasAnyPermission(permissions, ACTIVITY_REGISTER_PERMISSIONS);
  const canVisitSensitiveWordsPlugin =
    isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:sensitive-words:view'));
  const canVisitDataManagement =
    [
      canVisitCompetitions,
      canVisitCompetitionRegistrations,
      canVisitActivities,
      canVisitPaymentOrders,
      canVisitDownloadCenter,
    ].some(Boolean);
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
        canVisitSensitiveWordsPlugin,
        canVisitWorkflowConfig,
      ].some(Boolean));
  const canVisitAnyUserCenter =
    isLogin &&
    USER_CENTER_MANAGEMENT_PERMISSIONS.some((item) => hasPermission(permissions, item));

  return {
    hasPermission: (permission: string) => hasPermission(permissions, permission),
    isLogin,
    canVisitDashboard: isLogin && (isSettingsAdmin || hasPermission(permissions, 'dashboard:view')),
    canVisitProfile: isLogin && hasPermission(permissions, 'profile:view'),
    canVisitPersonalCenter: isLogin && (hasPermission(permissions, 'profile:view') || hasPermission(permissions, 'system:file:view')),
    canVisitAnyUserCenter,
    canVisitUserCenter:
      isLogin &&
      USER_CENTER_MANAGEMENT_PERMISSIONS.some((item) => hasPermission(permissions, item)),
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
    canVisitSystemMyFiles: isLogin && hasPermission(permissions, 'system:file:view'),
    canVisitDownloadCenter,
    canVisitTeam,
    canVisitProjects,
    canVisitActivitiesRoot: canVisitActivities,
    canVisitActivities,
    canVisitCompetitions,
    canVisitCompetitionRegistrations,
    canExportCompetitionRegistrations,
    canViewSensitiveCompetitionRegistrations,
    canExportSensitiveCompetitionRegistrations,
    canDownloadRegistrationMaterials,
    canVisitCompetitionRegister,
    canUseBuiltinMockPayment,
    canVisitActivityRegister,
    canVisitPaymentOrders,
    canVisitCertificates: isLogin,
    canVisitCertificateTemplates: isLogin && hasPermission(permissions, 'aiadc:certificate-template:view'),
    canVisitCertificateGenerate: isLogin && hasPermission(permissions, 'aiadc:certificate-batch:create'),
    canVisitCertificateRecords: isLogin && hasPermission(permissions, 'aiadc:certificate:view'),
    canVisitMyCertificates: isLogin,
    canVisitExperts: isLogin && hasPermission(permissions, 'expert:view'),
    canVisitExpertReview:
      isLogin && (
        hasPermission(permissions, 'expert:view')
        || hasAnyPermission(permissions, REVIEW_WORKBENCH_PERMISSIONS)
      ),
    canVisitReviewWorkbench,
    canVisitCompetitionReviewResults,
    canManageReviewAppeals,
    canVisitWorkflow: canVisitWorkflowTasks,
    canVisitWorkflowConfig,
    canVisitWorkflowTasks,
    canVisitSystemAllFiles,
    canVisitLocalization,
    canVisitAudit,
    canVisitSystemSettings,
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins,
    canVisitSensitiveWordsPlugin,
    canVisitWorkOrderFeedbackPlugin: isLogin && (isSettingsAdmin || hasPermission(permissions, 'plugin:work-order-feedback:view')),
    canVisitAi: isLogin && AI_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitAiEmployees: isLogin && isSettingsAdmin,
    canVisitAiKnowledge: isLogin && hasPermission(permissions, 'ai:knowledge:view'),
    canVisitAiAssistant: isLogin && hasAnyPermission(permissions, AI_ASSISTANT_PERMISSIONS),
    canVisitPluginRuntime,
  };
}
