export interface BackendRouteMeta {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  hideInMenu?: boolean;
}

export const backendRouteMeta: BackendRouteMeta[] = [
  { path: '/dashboard/home', name: '控制台', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/tenant/overview', name: '租户中心', icon: 'ApartmentOutlined', access: 'canVisitTenant' },
  { path: '/system/monitoring/audit', name: '审计中心', icon: 'AuditOutlined', access: 'canVisitAudit' },
  { path: '/user-center', name: '用户中心', icon: 'TeamOutlined', access: 'canVisitUserCenter' },
  { path: '/user-center/users', name: '用户管理', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
  { path: '/user-center/online-users', name: '在线用户', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
  { path: '/user-center/roles', name: '角色管理', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
  { path: '/user-center/profile', name: '个人中心', icon: 'UserOutlined', access: 'canVisitProfile' },
  { path: '/system/management', name: '系统管理', icon: 'SettingOutlined', access: 'canVisitSystemManagement' },
  { path: '/system/monitoring', name: '系统监控', icon: 'FundOutlined', access: 'canVisitSystemMonitoring' },
  { path: '/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
  { path: '/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
  { path: '/system/personalization', name: '个性化设置', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
  { path: '/system/security', name: '安全设置', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
  { path: '/system/smtp', name: 'SMTP 配置', icon: 'MailOutlined', access: 'canVisitSystemSecurity' },
  { path: '/system/plugins', name: '插件管理', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
  { path: '/plugins/2fa', name: '2FA绑定', hideInMenu: true },
  { path: '/plugins/:pluginCode', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/403', name: '无权限', hideInMenu: true },
  { path: '/404', name: '页面不存在', hideInMenu: true },
];

export const backendRoutes = [
  { path: '/', redirect: '/dashboard/home' },
  { path: '/dashboard/home', component: '@/pages/dashboard/Home', name: '控制台', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/tenant/overview', component: '@/pages/tenant/Overview', name: '租户中心', icon: 'ApartmentOutlined', access: 'canVisitTenant' },
  { path: '/audit/overview', redirect: '/system/monitoring/audit' },
  { path: '/system/users', redirect: '/user-center/users' },
  { path: '/system/online-users', redirect: '/user-center/online-users' },
  { path: '/system/roles', redirect: '/user-center/roles' },
  { path: '/profile/center', redirect: '/user-center/profile' },
  {
    path: '/user-center',
    component: '@/pages/user-center/index',
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
    name: '系统管理',
    icon: 'SettingOutlined',
    access: 'canVisitSystemManagement',
    routes: [
      { path: '/system/management', component: '@/pages/system/Management', name: '系统总览', icon: 'AppstoreOutlined', access: 'canVisitSystemManagement' },
      { path: '/system/menus', component: '@/pages/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
      { path: '/system/dicts', component: '@/pages/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
      { path: '/system/personalization', component: '@/pages/system/personalization.tsx', name: '个性化设置', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
      { path: '/system/security', component: '@/pages/system/security', name: '安全设置', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
      { path: '/system/smtp', component: '@/pages/system/smtp', name: 'SMTP 配置', icon: 'MailOutlined', access: 'canVisitSystemSecurity' },
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
  { path: '/plugins/2fa', redirect: '/user-center/profile?tab=second-factor' },
  { path: '/plugins/sms', redirect: '/user-center/profile?tab=second-factor' },
  { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', component: '@/pages/exception/BlankFlow', layout: false, name: '空白流程', hideInMenu: true },
  { path: '/user/login', component: '@/pages/user/Login', layout: false, name: '登录', hideInMenu: true },
  { path: '/403', component: '@/pages/exception/NoPermission', layout: false, name: '无权限', hideInMenu: true },
  { path: '/404', component: '@/pages/exception/NotFound', layout: false, name: '页面不存在', hideInMenu: true },
  { path: '*', redirect: '/404' },
];
