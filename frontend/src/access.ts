import type { CurrentUser } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { isProtectedAdminAccount } from '@/auth/admin';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');

const SYSTEM_MANAGEMENT_PERMISSIONS = [
  'system:menu:view',
  'system:dict:view',
  'system:config:view',
  'system:department:view',
  'system:verification:view',
  'system:verification:manage',
  'system:notification:view',
  'system:notification:write',
  'system:file:view',
  'system:file:manage',
  'system:monitor:view',
  'system:monitor:service:view',
  'system:monitor:redis:view',
  'system:monitor:docs:view',
  'ai:view',
  'audit:view',
  'audit:login:view',
  'audit:operation:view',
  'message:message:view',
  'plugin:management:view',
  'system:tenant:view',
];

const SYSTEM_MONITORING_PERMISSIONS = [
  'system:monitor:view',
  'system:monitor:service:view',
  'system:monitor:redis:view',
  'system:monitor:docs:view',
  'audit:view',
  'audit:login:view',
  'audit:operation:view',
];

const AUDIT_PERMISSIONS = ['audit:view', 'audit:login:view', 'audit:operation:view'];
const SITE_PERMISSIONS = ['site:view', 'site:settings', 'site:navigation', 'site:carousel', 'site:page', 'site:content', 'site:form', 'site:submission'];

export default function access(initialState: { currentUser?: CurrentUser }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  const isLogin = Boolean(initialState?.currentUser?.sessionId) || tokenManager.hasToken();

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
    canVisitSystemManagement: isLogin && SYSTEM_MANAGEMENT_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitSystemMonitoring: isLogin && SYSTEM_MONITORING_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitSystemMonitoringService: isLogin && hasPermission(permissions, 'system:monitor:service:view'),
    canVisitSystemMonitoringRedis: isLogin && hasPermission(permissions, 'system:monitor:redis:view'),
    canVisitSystemMonitoringDocs: isLogin && hasPermission(permissions, 'system:monitor:docs:view'),
    canVisitSystemUsers: isLogin && hasPermission(permissions, 'system:user:view'),
    canVisitSystemDepartments: isLogin && hasPermission(permissions, 'system:department:view'),
    canVisitSystemRoles: isLogin && hasPermission(permissions, 'system:role:view'),
    canVisitSystemTenants: isLogin && hasPermission(permissions, 'system:tenant:view'),
    canVisitSystemModules: isLogin && hasPermission(permissions, 'system:menu:view'),
    canVisitSystemMenus: isLogin && hasPermission(permissions, 'system:menu:view'),
    canVisitSystemDicts: isLogin && hasPermission(permissions, 'system:dict:view'),
    canVisitSystemProfileFields: isLogin && hasPermission(permissions, 'system:config:view'),
    canVisitSystemPersonalization: isLogin && hasPermission(permissions, 'system:config:view'),
    canVisitSystemSecurity: isLogin && hasPermission(permissions, 'system:config:view'),
    canVisitSystemVerification:
      isLogin &&
      (hasPermission(permissions, 'system:verification:view') ||
        hasPermission(permissions, 'system:verification:manage') ||
        hasPermission(permissions, 'system:config:view') ||
        hasPermission(permissions, 'system:config:update')),
    canVisitSystemNotifications:
      isLogin &&
      (hasPermission(permissions, 'system:notification:view') ||
        hasPermission(permissions, 'message:message:view')),
    canVisitSystemFiles: isLogin && hasPermission(permissions, 'system:file:manage'),
    canVisitSystemMyFiles: isLogin && hasPermission(permissions, 'system:file:view'),
    canVisitSystemAllFiles: isLogin && hasPermission(permissions, 'system:file:manage'),
    canVisitLocalization: isLogin && hasPermission(permissions, 'localization:view'),
    canVisitAudit: isLogin && AUDIT_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitSystemSettings:
      isLogin &&
      !initialState?.currentUser?.simulatedRoleId &&
      (isProtectedAdminAccount(initialState?.currentUser) ||
        SYSTEM_MANAGEMENT_PERMISSIONS.some((item) => hasPermission(permissions, item))),
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins: isLogin && hasPermission(permissions, 'plugin:management:view'),
    canVisitAiEmployees: isLogin && hasPermission(permissions, 'ai:view'),
    canVisitAiAssistant: isLogin && hasPermission(permissions, 'ai:chat:send'),
    canVisitTasks: isLogin && hasPermission(permissions, 'task:view'),
    canVisitApprovals: isLogin && hasPermission(permissions, 'approval:view'),
    canVisitEvaluations: isLogin && hasPermission(permissions, 'evaluation:view'),
    canVisitSite: isLogin && SITE_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitSiteSettings: isLogin && hasPermission(permissions, 'site:settings'),
    canVisitSiteNavigation: isLogin && hasPermission(permissions, 'site:navigation'),
    canVisitSiteCarousels: isLogin && hasPermission(permissions, 'site:carousel'),
    canVisitSitePages: isLogin && hasPermission(permissions, 'site:page'),
    canVisitSiteContents: isLogin && hasPermission(permissions, 'site:content'),
    canVisitSiteForms: isLogin && hasPermission(permissions, 'site:form'),
    canVisitSiteSubmissions: isLogin && hasPermission(permissions, 'site:submission'),
    canVisitPluginRuntime: isLogin,
  };
}
