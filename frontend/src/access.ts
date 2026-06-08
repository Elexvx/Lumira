import type { CurrentUser } from '@/types/api';
import { tokenManager } from '@/auth/token';
import { isSuperAdminUser } from '@/auth/adminAccess';

const hasPermission = (permissions: Set<string>, key: string) => permissions.has(key) || permissions.has('*');

const AI_PERMISSIONS = ['ai:chat:send', 'ai:knowledge:view'];

export default function access(initialState: { currentUser?: CurrentUser }) {
  const permissions = new Set(initialState?.currentUser?.permissions ?? []);
  const isLogin = Boolean(initialState?.currentUser?.sessionId) || tokenManager.hasToken();
  const isSettingsAdmin = isSuperAdminUser(initialState?.currentUser);

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
    canVisitSystemPersonalization: isLogin && isSettingsAdmin,
    canVisitSystemSecurity: isLogin && isSettingsAdmin,
    canVisitSystemVerification: isLogin && isSettingsAdmin,
    canVisitSystemPayment: isLogin && isSettingsAdmin,
    canVisitSystemNotifications: isLogin && isSettingsAdmin,
    canVisitSystemFiles: isLogin && isSettingsAdmin,
    canVisitSystemMyFiles: isLogin && hasPermission(permissions, 'system:file:view'),
    canVisitSystemAllFiles: isLogin && isSettingsAdmin,
    canVisitLocalization: isLogin && isSettingsAdmin,
    canVisitAudit: isLogin && isSettingsAdmin,
    canVisitSystemSettings: isLogin && isSettingsAdmin,
    canVisitSystemOnlineUsers: isLogin && hasPermission(permissions, 'system:online-user:view'),
    canVisitSystemPlugins: isLogin && isSettingsAdmin,
    canVisitAi: isLogin && AI_PERMISSIONS.some((item) => hasPermission(permissions, item)),
    canVisitAiEmployees: isLogin && isSettingsAdmin,
    canVisitAiKnowledge: isLogin && hasPermission(permissions, 'ai:knowledge:view'),
    canVisitAiAssistant: isLogin && hasPermission(permissions, 'ai:chat:send'),
    canVisitPluginRuntime: isLogin,
  };
}
