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
  { path: '/iam/overview', name: '权限中心', icon: 'SafetyCertificateOutlined', access: 'canVisitIam' },
  { path: '/audit/overview', name: '审计中心', icon: 'AuditOutlined', access: 'canVisitAudit' },
  { path: '/profile/center', name: '个人中心', icon: 'UserOutlined', access: 'canVisitProfile' },
  { path: '/system/management', name: '系统管理', icon: 'SettingOutlined', access: 'canVisitSystemManagement' },
  { path: '/system/users', name: '用户管理', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
  { path: '/system/roles', name: '角色管理', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
  { path: '/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
  { path: '/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
  { path: '/system/configs', name: '参数配置', icon: 'ControlOutlined', access: 'canVisitSystemConfigs' },
  { path: '/system/plugins', name: '插件管理', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
  { path: '/plugins/:pluginCode', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/403', name: '无权限', hideInMenu: true },
  { path: '/404', name: '页面不存在', hideInMenu: true },
];

export const backendRoutes = [
  { path: '/', redirect: '/dashboard/home' },
  { path: '/dashboard/home', component: '@/pages/dashboard/Home', name: '控制台', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
  { path: '/tenant/overview', component: '@/pages/tenant/Overview', name: '租户中心', icon: 'ApartmentOutlined', access: 'canVisitTenant' },
  { path: '/iam/overview', component: '@/pages/iam/Overview', name: '权限中心', icon: 'SafetyCertificateOutlined', access: 'canVisitIam' },
  { path: '/audit/overview', component: '@/pages/audit/Overview', name: '审计中心', icon: 'AuditOutlined', access: 'canVisitAudit' },
  { path: '/profile/center', component: '@/pages/profile/Center', name: '个人中心', icon: 'UserOutlined', access: 'canVisitProfile' },
  {
    path: '/system',
    name: '系统管理',
    icon: 'SettingOutlined',
    access: 'canVisitSystemManagement',
    routes: [
      { path: '/system/management', component: '@/pages/system/Management', name: '系统总览', icon: 'AppstoreOutlined', access: 'canVisitSystemManagement' },
      { path: '/system/users', component: '@/pages/system/users', name: '用户管理', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
      { path: '/system/roles', component: '@/pages/system/roles', name: '角色管理', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
      { path: '/system/menus', component: '@/pages/system/menus', name: '菜单管理', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
      { path: '/system/dicts', component: '@/pages/system/dicts', name: '字典管理', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
      { path: '/system/configs', component: '@/pages/system/configs', name: '参数配置', icon: 'ControlOutlined', access: 'canVisitSystemConfigs' },
      { path: '/system/plugins', component: '@/pages/system/Plugins', name: '插件管理', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
    ],
  },
  { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer', name: '插件页面', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', component: '@/pages/exception/BlankFlow', layout: false, name: '空白流程', hideInMenu: true },
  { path: '/user/login', component: '@/pages/user/Login', layout: false, name: '登录', hideInMenu: true },
  { path: '/403', component: '@/pages/exception/NoPermission', layout: false, name: '无权限', hideInMenu: true },
  { path: '/404', component: '@/pages/exception/NotFound', layout: false, name: '页面不存在', hideInMenu: true },
  { path: '*', redirect: '/404' },
];
