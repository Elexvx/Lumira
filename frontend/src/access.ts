import type { CurrentUser } from '@/types/api';
import { tokenManager } from '@/auth/token';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');

export default function access(initialState: { currentUser?: CurrentUser }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  const isLogin = Boolean(initialState?.currentUser?.sessionId) || tokenManager.hasToken();

  return {
    hasPermission: (permission: string) => hasPermission(permissions, permission),
    isLogin,
    canVisitDashboard: isLogin,
    canVisitTenant: isLogin && hasPermission(permissions, 'tenant:view'),
    canVisitAudit: isLogin,
    canVisitProfile: isLogin,
    canVisitUserCenter: isLogin,
    canVisitSystemManagement:
      isLogin &&
      [
        'system:view',
        'system:menu:view',
        'system:dict:view',
        'system:config:view',
        'plugin:management:view',
      ].some((item) => hasPermission(permissions, item)),
    canVisitSystemMonitoring:
      isLogin &&
      ['system:monitor:view', 'system:monitor:service:view', 'system:monitor:redis:view', 'system:monitor:docs:view', 'audit:view'].some(
        (item) => hasPermission(permissions, item),
      ),
    canVisitSystemMonitoringService: isLogin && hasPermission(permissions, 'system:monitor:service:view'),
    canVisitSystemMonitoringRedis: isLogin && hasPermission(permissions, 'system:monitor:redis:view'),
    canVisitSystemMonitoringDocs: isLogin && hasPermission(permissions, 'system:monitor:docs:view'),
    canVisitSystemUsers: isLogin && hasPermission(permissions, 'system:user:view'),
    canVisitSystemRoles: isLogin && hasPermission(permissions, 'system:role:view'),
    canVisitSystemMenus: isLogin && hasPermission(permissions, 'system:menu:view'),
    canVisitSystemDicts: isLogin && hasPermission(permissions, 'system:dict:view'),
    canVisitSystemPersonalization: isLogin && hasPermission(permissions, 'system:config:view'),
    canVisitSystemSecurity: isLogin && hasPermission(permissions, 'system:config:view'),
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins: isLogin && hasPermission(permissions, 'plugin:management:view'),
    canVisitPluginRuntime: isLogin,
  };
}
