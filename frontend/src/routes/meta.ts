export interface BackendRouteMeta {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  hideInMenu?: boolean;
}

export const backendRouteMeta: BackendRouteMeta[] = [
  { path: '/dashboard/home', name: 'nav.dashboard.home', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/ai/share/:token', name: 'nav.ai.assistant', icon: 'RobotOutlined', access: 'canVisitAiAssistant', hideInMenu: true },
  { path: '/ai', name: 'nav.ai.assistant', icon: 'RobotOutlined', access: 'canVisitAiAssistant' },
  { path: '/tasks', name: 'nav.tasks.root', icon: 'CheckSquareOutlined', access: 'canVisitTasks' },
  { path: '/approvals', name: 'nav.approvals.root', icon: 'AuditOutlined', access: 'canVisitApprovals' },
  { path: '/evaluations', name: 'nav.evaluations.root', icon: 'StarOutlined', access: 'canVisitEvaluations' },
  { path: '/settings', name: 'nav.system.root', icon: 'SettingOutlined', access: 'canVisitSystemSettings', hideInMenu: true },
  { path: '/settings/monitoring', name: 'nav.system.monitoring.root', icon: 'FundOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/menus', name: 'nav.system.menus', icon: 'AppstoreOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/dicts', name: 'nav.system.dicts', icon: 'DatabaseOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/profile-fields', name: 'nav.system.profileFields', icon: 'FormOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/personalization', name: 'nav.system.personalization', icon: 'SkinOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/security', name: 'nav.system.security', icon: 'SafetyOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/verification', name: 'nav.system.verification', icon: 'SafetyOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/notifications', name: 'nav.system.notifications', icon: 'NotificationOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/ai-employees', name: 'nav.system.aiEmployees', icon: 'RobotOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/files/all', name: 'nav.files.all', icon: 'FolderOpenOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/plugins', name: 'nav.system.plugins', icon: 'ApiOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/api-docs', name: 'nav.system.monitoring.apiDocs', icon: 'FileTextOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/audit', name: 'nav.system.monitoring.audit', icon: 'AuditOutlined', access: 'canVisitSystemSettings' },
  { path: '/settings/localization', name: 'nav.localization.root', icon: 'TranslationOutlined', access: 'canVisitSystemSettings' },
  { path: '/user-center', name: 'nav.user.center', icon: 'TeamOutlined', access: 'canVisitUserCenter' },
  { path: '/user-center/users', name: 'nav.user.users', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
  { path: '/user-center/online-users', name: 'nav.user.onlineUsers', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
  { path: '/user-center/roles', name: 'nav.user.roles', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
  { path: '/user-center/personal-center', name: 'nav.user.personalCenter', icon: 'IdcardOutlined', access: 'canVisitUserCenter' },
  { path: '/user-center/profile', name: 'nav.user.profile', icon: 'UserOutlined', access: 'canVisitProfile' },
  { path: '/user-center/files', name: 'nav.files.my', icon: 'FileOutlined', access: 'canVisitSystemMyFiles' },
  { path: '/plugins/:pluginCode', name: 'nav.system.plugins', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/403', name: 'common.failure', hideInMenu: true },
  { path: '/404', name: 'common.failure', hideInMenu: true },
];

export interface BackendRouteRecord {
  path: string;
  name?: string;
  icon?: string;
  access?: string;
  hideInMenu?: boolean;
  component?: string;
  redirect?: string;
  layout?: boolean;
  routes?: BackendRouteRecord[];
}

export const backendRoutes: BackendRouteRecord[] = [
  { path: '/', redirect: '/dashboard/home' },
  { path: '/dashboard/home', component: '@/pages/dashboard/Home', name: 'nav.dashboard.home', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/ai/share/:token', component: '@/pages/ai/Assistant', name: 'nav.ai.assistant', icon: 'RobotOutlined', access: 'canVisitAiAssistant', hideInMenu: true },
  { path: '/ai', component: '@/pages/ai/Assistant', name: 'nav.ai.assistant', icon: 'RobotOutlined', access: 'canVisitAiAssistant' },
  { path: '/tasks', component: '@/pages/tasks', name: 'nav.tasks.root', icon: 'CheckSquareOutlined', access: 'canVisitTasks' },
  { path: '/approvals', component: '@/pages/approvals', name: 'nav.approvals.root', icon: 'AuditOutlined', access: 'canVisitApprovals' },
  { path: '/evaluations', component: '@/pages/evaluations', name: 'nav.evaluations.root', icon: 'StarOutlined', access: 'canVisitEvaluations' },
  { path: '/audit/overview', redirect: '/settings/audit' },
  { path: '/system', redirect: '/settings' },
  { path: '/system/overview', redirect: '/settings' },
  { path: '/system/files', redirect: '/user-center/files' },
  { path: '/system/files/my', redirect: '/user-center/files' },
  { path: '/system/files/all', redirect: '/settings/files/all' },
  { path: '/files', redirect: '/settings/files/all' },
  { path: '/files/my', redirect: '/user-center/files' },
  { path: '/files/all', redirect: '/settings/files/all' },
  { path: '/system/users', redirect: '/user-center/users' },
  { path: '/system/online-users', redirect: '/user-center/online-users' },
  { path: '/system/roles', redirect: '/user-center/roles' },
  { path: '/profile/center', redirect: '/user-center/profile' },
  { path: '/system/menus', redirect: '/settings/menus' },
  { path: '/system/dicts', redirect: '/settings/dicts' },
  { path: '/system/profile-fields', redirect: '/settings/profile-fields' },
  { path: '/system/personalization', redirect: '/settings/personalization' },
  { path: '/system/security', redirect: '/settings/security' },
  { path: '/system/verification', redirect: '/settings/verification' },
  { path: '/system/smtp', redirect: '/settings/verification?tab=email' },
  { path: '/system/notifications', redirect: '/settings/notifications' },
  { path: '/system/plugins', redirect: '/settings/plugins' },
  { path: '/system/monitoring', redirect: '/settings/monitoring' },
  { path: '/system/monitoring/service', redirect: '/settings/monitoring?tab=service' },
  { path: '/system/monitoring/redis', redirect: '/settings/monitoring?tab=redis' },
  { path: '/system/monitoring/api-docs', redirect: '/settings/api-docs' },
  { path: '/system/monitoring/audit', redirect: '/settings/audit' },
  { path: '/settings/monitoring/api-docs', redirect: '/settings/api-docs' },
  { path: '/settings/monitoring/audit', redirect: '/settings/audit' },
  {
    path: '/user-center',
    component: '@/layouts/SettingsLayout',
    name: 'nav.user.center',
    icon: 'TeamOutlined',
    access: 'canVisitUserCenter',
    routes: [
      { path: '/user-center/users', component: '@/pages/system/users', name: 'nav.user.users', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
      { path: '/user-center/online-users', component: '@/pages/system/online-users', name: 'nav.user.onlineUsers', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
      { path: '/user-center/roles', component: '@/pages/system/roles', name: 'nav.user.roles', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
      { path: '/user-center/personal-center', redirect: '/user-center/profile' },
      { path: '/user-center/profile', component: '@/pages/profile/Center', name: 'nav.user.profile', icon: 'UserOutlined', access: 'canVisitProfile' },
      { path: '/user-center/files', component: '@/pages/files/Center', name: 'nav.files.my', icon: 'FileOutlined', access: 'canVisitSystemMyFiles' },
    ],
  },
  {
    path: '/settings',
    component: '@/layouts/SettingsLayout',
    name: 'nav.system.root',
    icon: 'SettingOutlined',
    access: 'canVisitSystemSettings',
    routes: [
      { path: '/settings/overview', redirect: '/settings' },
      { path: '/settings/menus', component: '@/pages/settings/menus', name: 'nav.system.menus', icon: 'AppstoreOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/dicts', component: '@/pages/settings/dicts', name: 'nav.system.dicts', icon: 'DatabaseOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/profile-fields', component: '@/pages/settings/profile-fields', name: 'nav.system.profileFields', icon: 'FormOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/personalization', component: '@/pages/settings/personalization', name: 'nav.system.personalization', icon: 'SkinOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/security', component: '@/pages/settings/security', name: 'nav.system.security', icon: 'SafetyOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/verification', component: '@/pages/settings/verification', name: 'nav.system.verification', icon: 'SafetyOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/smtp', redirect: '/settings/verification?tab=email' },
      { path: '/settings/notifications', component: '@/pages/settings/notifications/index', name: 'nav.system.notifications', icon: 'NotificationOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/ai-employees', component: '@/pages/settings/ai-employees', name: 'nav.system.aiEmployees', icon: 'RobotOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/plugins', component: '@/pages/settings/plugins', name: 'nav.system.plugins', icon: 'ApiOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/localization', component: '@/pages/settings/localization', name: 'nav.localization.root', icon: 'TranslationOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/files', redirect: '/settings/files/all' },
      { path: '/settings/files/all', component: '@/pages/settings/files/Center', name: 'nav.files.all', icon: 'FolderOpenOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/monitoring', component: '@/pages/settings/monitoring/index', name: 'nav.system.monitoring.root', icon: 'FundOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/monitoring/service', redirect: '/settings/monitoring?tab=service' },
      { path: '/settings/monitoring/redis', redirect: '/settings/monitoring?tab=redis' },
      { path: '/settings/api-docs', component: '@/pages/settings/monitoring/ApiDocs', name: 'nav.system.monitoring.apiDocs', icon: 'FileTextOutlined', access: 'canVisitSystemSettings' },
      { path: '/settings/audit', component: '@/pages/settings/monitoring/Audit', name: 'nav.system.monitoring.audit', icon: 'AuditOutlined', access: 'canVisitSystemSettings' },
    ],
  },
  { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer', name: 'nav.system.plugins', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', component: '@/pages/exception/BlankFlow', layout: false, name: 'common.failure', hideInMenu: true },
  { path: '/user/login', component: '@/pages/user/Login', layout: false, name: 'page.login.title', hideInMenu: true },
  { path: '/403', component: '@/pages/exception/NoPermission', name: 'common.failure', hideInMenu: true },
  { path: '/404', component: '@/pages/exception/NotFound', layout: false, name: 'common.failure', hideInMenu: true },
  { path: '*', redirect: '/404' },
];

const NON_AUTHORIZED_ROUTE_PATHS = new Set(['/user/login', '/403', '/404', '/blank/workflow']);

const collectRealPageRouteMeta = (routes: BackendRouteRecord[], result = new Map<string, BackendRouteMeta>()) => {
  routes.forEach((route) => {
    if (route.component && route.path && route.path !== '*' && !route.redirect && !NON_AUTHORIZED_ROUTE_PATHS.has(route.path)) {
      result.set(route.path, {
        path: route.path,
        name: route.name || route.path,
        icon: route.icon,
        access: route.access,
        hideInMenu: route.hideInMenu,
      });
    }

    if (route.routes?.length) {
      collectRealPageRouteMeta(route.routes, result);
    }
  });
  return result;
};

export const realPageRouteMetaMap = collectRealPageRouteMeta(backendRoutes);
export const realPageRouteMetaList = Array.from(realPageRouteMetaMap.values());
export const realPageRoutePaths = new Set(realPageRouteMetaMap.keys());
