export interface BackendRouteMeta {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  hideInMenu?: boolean;
}

export const backendRouteMeta: BackendRouteMeta[] = [
  { path: '/dashboard/home', name: '工作台', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/tenant/overview', name: '租户中心', icon: 'ApartmentOutlined', access: 'canVisitTenant' },
  { path: '/system/monitoring/audit', name: '审计中心', icon: 'AuditOutlined', access: 'canVisitAudit' },
  { path: '/user-center', name: '用户中心', icon: 'TeamOutlined', access: 'canVisitUserCenter' },
  { path: '/user-center/users', name: '用户管理', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
  { path: '/user-center/online-users', name: '在线用户', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
  { path: '/user-center/roles', name: '角色管理', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
  { path: '/user-center/profile', name: '个人中心', icon: 'UserOutlined', access: 'canVisitProfile' },
  { path: '/system/overview', name: '系统总览', icon: 'AppstoreOutlined', access: 'canVisitSystemManagement', hideInMenu: true },
  { path: '/system/monitoring', name: '系统监控', icon: 'FundOutlined', access: 'canVisitSystemMonitoring' },
  { path: '/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
  { path: '/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
  { path: '/system/profile-fields', name: '字段管理', icon: 'FormOutlined', access: 'canVisitSystemProfileFields' },
  { path: '/system/personalization', name: '个性化设置', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
  { path: '/system/security', name: '安全设置', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
  { path: '/system/verification', name: '验证管理', icon: 'SafetyOutlined', access: 'canVisitSystemVerification' },
  { path: '/system/notifications', name: '站内信归档', icon: 'NotificationOutlined', access: 'canVisitSystemNotifications' },
  { path: '/system/plugins', name: '插件管理', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
  { path: '/plugins/:pluginCode', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/403', name: '无权限', hideInMenu: true },
  { path: '/404', name: '页面不存在', hideInMenu: true },
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
  { path: '/dashboard/home', component: '@/pages/dashboard/Home', name: '工作台', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/tenant/overview', component: '@/pages/tenant/Overview', name: '租户中心', icon: 'ApartmentOutlined', access: 'canVisitTenant' },
  { path: '/audit/overview', redirect: '/system/monitoring/audit' },
  { path: '/system/users', redirect: '/user-center/users' },
  { path: '/system/online-users', redirect: '/user-center/online-users' },
  { path: '/system/roles', redirect: '/user-center/roles' },
  { path: '/profile/center', redirect: '/user-center/profile' },
  {
    path: '/user-center',
    component: '@/layouts/SettingsLayout',
    name: '用户中心',
    icon: 'TeamOutlined',
    access: 'canVisitUserCenter',
    routes: [
      { path: '/user-center/users', component: '@/pages/system/users', name: '用户管理', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
      { path: '/user-center/online-users', component: '@/pages/system/online-users', name: '在线用户', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
      { path: '/user-center/roles', component: '@/pages/system/roles', name: '角色管理', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
      { path: '/user-center/profile', component: '@/pages/profile/Center', name: '个人中心', icon: 'UserOutlined', access: 'canVisitProfile' },
    ],
  },
  {
    path: '/system',
    component: '@/layouts/SettingsLayout',
    name: '系统总览',
    icon: 'SettingOutlined',
    access: 'canVisitSystemManagement',
    routes: [
      { path: '/system/overview', component: '@/pages/exception/BlankFlow', name: '系统总览', icon: 'AppstoreOutlined', access: 'canVisitSystemManagement', hideInMenu: true },
      { path: '/system/menus', component: '@/pages/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
      { path: '/system/dicts', component: '@/pages/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
      { path: '/system/profile-fields', component: '@/pages/system/profile-fields', name: '字段管理', icon: 'FormOutlined', access: 'canVisitSystemProfileFields' },
      { path: '/system/personalization', component: '@/pages/system/personalization', name: '个性化设置', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
      { path: '/system/security', component: '@/pages/system/security', name: '安全设置', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
      { path: '/system/verification', component: '@/pages/system/verification', name: '验证管理', icon: 'SafetyOutlined', access: 'canVisitSystemVerification' },
      { path: '/system/smtp', redirect: '/system/verification?tab=email' },
      { path: '/system/notifications', component: '@/pages/system/notifications/index', name: '站内信归档', icon: 'NotificationOutlined', access: 'canVisitSystemNotifications' },
      { path: '/system/plugins', component: '@/pages/system/Plugins', name: '插件管理', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
    ],
  },
  {
    path: '/system/monitoring',
    component: '@/pages/system/monitoring/index',
    name: '系统监控',
    icon: 'FundOutlined',
    access: 'canVisitSystemMonitoring',
    routes: [
      { path: '/system/monitoring/service', component: '@/pages/system/monitoring/Service', name: '服务监控', icon: 'RadarChartOutlined', access: 'canVisitSystemMonitoringService' },
      { path: '/system/monitoring/redis', component: '@/pages/system/monitoring/Redis', name: 'Redis监控', icon: 'DatabaseOutlined', access: 'canVisitSystemMonitoringRedis' },
      { path: '/system/monitoring/api-docs', component: '@/pages/system/monitoring/ApiDocs', name: '接口文档', icon: 'FileTextOutlined', access: 'canVisitSystemMonitoringDocs' },
      { path: '/system/monitoring/audit', component: '@/pages/audit/Overview', name: '审计中心', icon: 'AuditOutlined', access: 'canVisitAudit' },
    ],
  },
  { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', component: '@/pages/exception/BlankFlow', layout: false, name: '空白流程', hideInMenu: true },
  { path: '/user/login', component: '@/pages/user/Login', layout: false, name: '登录', hideInMenu: true },
  { path: '/403', component: '@/pages/exception/NoPermission', layout: false, name: '无权限', hideInMenu: true },
  { path: '/404', component: '@/pages/exception/NotFound', layout: false, name: '页面不存在', hideInMenu: true },
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
