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
    canVisitTenant: isLogin,
    canVisitIam: isLogin && (hasPermission(permissions, 'iam:view') || hasPermission(permissions, 'system:role:view')),
    canVisitAudit: isLogin,
    canVisitProfile: isLogin,
    canVisitSystemManagement:
      isLogin &&
      ['system:view', 'system:user:view', 'system:role:view', 'system:menu:view', 'system:dict:view', 'system:config:view', 'system:online-user:view', 'plugin:management:view'].some(
        (item) => hasPermission(permissions, item),
      ),
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
