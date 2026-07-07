export interface BackendRouteMeta {
  path: string;
  name: string;
  icon?: string;
  access?: string;
  hideInMenu?: boolean;
}

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

const WORKBENCH_ROUTE_ALIASES: Record<string, string> = {
  '/dashboard': '/dashboard/home',
  '/dashboard/': '/dashboard/home',
  '/dashboard/home/': '/dashboard/home',
  '/team': '/team/management',
  '/team/': '/team/management',
  '/team/management/': '/team/management',
  '/team/search/': '/team/search',
  '/activities': '/activities/register',
  '/activities/': '/activities/register',
  '/activities/management/': '/activities/management',
  '/activities/register/': '/activities/register',
  '/activities/search/': '/activities/search',
  '/registration': '/competitions/register',
  '/registration/': '/competitions/register',
  '/competitions': '/competitions/register',
  '/competitions/': '/competitions/register',
  '/competitions/management/': '/competitions/management',
  '/competitions/register/': '/competitions/register',
  '/competitions/activity-register': '/activities/register',
  '/competitions/activity-register/': '/activities/register',
  '/competitions/expert-apply/': '/competitions/expert-apply',
  '/expert-review': '/expert-review/reviews',
  '/expert-review/': '/expert-review/reviews',
  '/expert-review/reviews/': '/expert-review/reviews',
  '/projects': '/projects/management',
  '/projects/': '/projects/management',
  '/projects/management/': '/projects/management',
  '/projects/search/': '/projects/search',
  '/payments': '/payments/management',
  '/payments/': '/payments/management',
  '/payments/management/': '/payments/management',
  '/payments/status/': '/payments/status',
  '/certificates': '/certificates/templates',
  '/certificates/': '/certificates/templates',
  '/certificates/templates/': '/certificates/templates',
  '/certificates/generate/': '/certificates/generate',
  '/certificates/records/': '/certificates/records',
  '/experts': '/experts/management',
  '/experts/': '/experts/management',
  '/experts/management/': '/experts/management',
  '/experts/query/': '/experts/query',
  '/workflows': '/workflows/tasks',
  '/workflows/': '/workflows/tasks',
  '/workflows/tasks/': '/workflows/tasks',
  '/workflows/config/': '/workflows/config',
  '/user-center/files': '/user-center/personal-center/files',
  '/user-center/files/': '/user-center/personal-center/files',
  '/user-center/personal-center/files/': '/user-center/personal-center/files',
};

export const resolveCanonicalRoutePath = (path: string) => {
  if (typeof path !== 'string') {
    return path;
  }

  const trimmed = path.trim();
  if (!trimmed) {
    return '/';
  }

  const pathnameOnly = trimmed.split('?')[0].split('#')[0];
  const normalized = pathnameOnly.replace(/\/+$/, '');
  const canonical = normalized || '/';
  return WORKBENCH_ROUTE_ALIASES[canonical] || canonical;
};

export const systemRouteMeta: BackendRouteMeta[] = [
  { path: '/settings', name: 'nav.settings.root', icon: 'SettingOutlined', hideInMenu: true },
  { path: '/settings/monitoring', name: 'nav.system.monitoring.root', icon: 'FundOutlined', access: 'canVisitSystemMonitoring' },
  { path: '/settings/menus', name: 'nav.system.menus', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
  { path: '/settings/dicts', name: 'nav.system.dicts', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
  { path: '/settings/profile-fields', name: 'nav.system.profileFields', icon: 'FormOutlined', access: 'canVisitSystemProfileFields' },
  { path: '/settings/personalization', name: 'nav.system.personalization', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
  { path: '/settings/security', name: 'nav.system.security', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
  { path: '/settings/verification', name: 'nav.system.verification', icon: 'SafetyOutlined', access: 'canVisitSystemVerification' },
  { path: '/settings/payment', name: 'nav.system.payment', icon: 'CreditCardOutlined', access: 'canVisitSystemPayment' },
  { path: '/settings/notifications', name: 'nav.system.notifications', icon: 'NotificationOutlined', access: 'canVisitSystemNotifications' },
  { path: '/settings/files/all', name: 'nav.files.all', icon: 'FolderOpenOutlined', access: 'canVisitSystemAllFiles' },
  { path: '/settings/plugins', name: 'nav.system.plugins', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
  { path: '/settings/api-docs', name: 'nav.system.monitoring.apiDocs', icon: 'FileTextOutlined', access: 'canVisitSystemMonitoringDocs' },
  { path: '/settings/audit', name: 'nav.system.monitoring.audit', icon: 'AuditOutlined', access: 'canVisitAudit' },
  { path: '/settings/localization', name: 'nav.localization.root', icon: 'TranslationOutlined', access: 'canVisitLocalization' },
];

export const systemRoutes: BackendRouteRecord[] = [
  { path: '/audit/overview', redirect: '/settings/audit' },
  { path: '/system', redirect: '/settings' },
  { path: '/system/overview', redirect: '/settings' },
  { path: '/system/files', redirect: '/user-center/personal-center/files' },
  { path: '/system/files/my', redirect: '/user-center/personal-center/files' },
  { path: '/system/files/all', redirect: '/settings/files/all' },
  { path: '/files', redirect: '/settings/files/all' },
  { path: '/files/my', redirect: '/user-center/personal-center/files' },
  { path: '/files/all', redirect: '/settings/files/all' },
  { path: '/download-center', redirect: '/data-management/download-center', hideInMenu: true },
  { path: '/files/download-center', redirect: '/data-management/download-center', hideInMenu: true },
  { path: '/system/menus', redirect: '/settings/menus' },
  { path: '/system/dicts', redirect: '/settings/dicts' },
  { path: '/system/profile-fields', redirect: '/settings/profile-fields' },
  { path: '/system/personalization', redirect: '/settings/personalization' },
  { path: '/system/security', redirect: '/settings/security' },
  { path: '/system/verification', redirect: '/settings/verification' },
  { path: '/system/payment', redirect: '/settings/payment' },
  { path: '/system/smtp', redirect: '/settings/verification?tab=email' },
  { path: '/system/notifications', redirect: '/settings/notifications' },
  { path: '/system/plugins', redirect: '/settings/plugins' },
  { path: '/system/monitoring', redirect: '/settings/monitoring' },
  { path: '/system/monitoring/service', redirect: '/settings/monitoring?tab=service' },
  { path: '/system/monitoring/redis', redirect: '/settings/monitoring?tab=redis' },
  { path: '/system/monitoring/api-docs', redirect: '/settings/api-docs' },
  { path: '/system/monitoring/audit', redirect: '/settings/audit' },
  {
    path: '/settings',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.settings.root',
    icon: 'SettingOutlined',
    routes: [
      { path: '/settings/overview', redirect: '/settings' },
      { path: '/settings/menus', component: '@/pages/settings/menus', name: 'nav.system.menus', icon: 'AppstoreOutlined', access: 'canVisitSystemMenus' },
      { path: '/settings/dicts', component: '@/pages/settings/dicts', name: 'nav.system.dicts', icon: 'DatabaseOutlined', access: 'canVisitSystemDicts' },
      { path: '/settings/profile-fields', component: '@/pages/settings/profile-fields', name: 'nav.system.profileFields', icon: 'FormOutlined', access: 'canVisitSystemProfileFields' },
      { path: '/settings/personalization', component: '@/pages/settings/personalization', name: 'nav.system.personalization', icon: 'SkinOutlined', access: 'canVisitSystemPersonalization' },
      { path: '/settings/security', component: '@/pages/settings/security', name: 'nav.system.security', icon: 'SafetyOutlined', access: 'canVisitSystemSecurity' },
      { path: '/settings/verification', component: '@/pages/settings/verification', name: 'nav.system.verification', icon: 'SafetyOutlined', access: 'canVisitSystemVerification' },
      { path: '/settings/payment', component: '@/pages/settings/payment', name: 'nav.system.payment', icon: 'CreditCardOutlined', access: 'canVisitSystemPayment' },
      { path: '/settings/smtp', redirect: '/settings/verification?tab=email' },
      { path: '/settings/notifications', component: '@/pages/settings/notifications/NotificationsPage', name: 'nav.system.notifications', icon: 'NotificationOutlined', access: 'canVisitSystemNotifications' },
      { path: '/settings/plugins', component: '@/pages/settings/plugins/PluginsPage', name: 'nav.system.plugins', icon: 'ApiOutlined', access: 'canVisitSystemPlugins' },
      { path: '/settings/localization', component: '@/pages/settings/localization/LocalizationPage', name: 'nav.localization.root', icon: 'TranslationOutlined', access: 'canVisitLocalization' },
      { path: '/settings/files', redirect: '/settings/files/all' },
      { path: '/settings/files/all', component: '@/pages/files/Center', name: 'nav.files.all', icon: 'FolderOpenOutlined', access: 'canVisitSystemAllFiles' },
      { path: '/settings/monitoring', component: '@/pages/settings/monitoring/MonitoringPage', name: 'nav.system.monitoring.root', icon: 'FundOutlined', access: 'canVisitSystemMonitoring' },
      { path: '/settings/monitoring/service', redirect: '/settings/monitoring?tab=service' },
      { path: '/settings/monitoring/redis', redirect: '/settings/monitoring?tab=redis' },
      { path: '/settings/api-docs', component: '@/pages/settings/monitoring/MonitoringPage', name: 'nav.system.monitoring.apiDocs', icon: 'FileTextOutlined', access: 'canVisitSystemMonitoringDocs' },
      { path: '/settings/audit', component: '@/pages/settings/monitoring/Audit', name: 'nav.system.monitoring.audit', icon: 'AuditOutlined', access: 'canVisitAudit' },
    ],
  },
];

const userCenterRouteMeta: BackendRouteMeta[] = [
  { path: '/user-center', name: 'nav.user.center', icon: 'TeamOutlined', access: 'canVisitAnyUserCenter' },
  { path: '/user-center/users', name: 'nav.user.users', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
  { path: '/user-center/departments', name: 'nav.user.departments', icon: 'ApartmentOutlined', access: 'canVisitSystemDepartments' },
  { path: '/user-center/online-users', name: 'nav.user.onlineUsers', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
  { path: '/user-center/roles', name: 'nav.user.roles', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
  { path: '/user-center/personal-center', name: 'nav.user.personalCenter', icon: 'IdcardOutlined', access: 'canVisitPersonalCenter' },
  { path: '/user-center/personal-center/profile', name: 'nav.user.profile', icon: 'UserOutlined', access: 'canVisitProfile' },
  { path: '/user-center/personal-center/files', name: 'nav.files.my', icon: 'FileOutlined', access: 'canVisitSystemMyFiles' },
];

const userCenterRoutes: BackendRouteRecord[] = [
  { path: '/system/users', redirect: '/user-center/users' },
  { path: '/system/departments', redirect: '/user-center/departments' },
  { path: '/system/online-users', redirect: '/user-center/online-users' },
  { path: '/system/roles', redirect: '/user-center/roles' },
  { path: '/profile/center', redirect: '/user-center/personal-center/profile' },
  { path: '/user-center/profile', redirect: '/user-center/personal-center/profile' },
  {
    path: '/user-center',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.user.center',
    icon: 'TeamOutlined',
    access: 'canVisitAnyUserCenter',
    routes: [
      { path: '/user-center/users', component: '@/pages/system/users', name: 'nav.user.users', icon: 'TeamOutlined', access: 'canVisitSystemUsers' },
      { path: '/user-center/departments', component: '@/pages/system/departments', name: 'nav.user.departments', icon: 'ApartmentOutlined', access: 'canVisitSystemDepartments' },
      { path: '/user-center/online-users', component: '@/pages/system/online-users', name: 'nav.user.onlineUsers', icon: 'UserSwitchOutlined', access: 'canVisitSystemOnlineUsers' },
      { path: '/user-center/roles', component: '@/pages/system/roles', name: 'nav.user.roles', icon: 'SafetyOutlined', access: 'canVisitSystemRoles' },
    ],
  },
  {
    path: '/user-center/personal-center',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.user.personalCenter',
    icon: 'IdcardOutlined',
    access: 'canVisitPersonalCenter',
    routes: [
      { path: '/user-center/personal-center', redirect: '/user-center/personal-center/profile', hideInMenu: true },
      { path: '/user-center/personal-center/profile', component: '@/pages/profile/Center', name: 'nav.user.profile', icon: 'UserOutlined', access: 'canVisitProfile' },
      { path: '/user-center/personal-center/files', component: '@/pages/files/Center', name: 'nav.files.my', icon: 'FileOutlined', access: 'canVisitSystemMyFiles' },
    ],
  },
  { path: '/user-center/files', redirect: '/user-center/personal-center/files', hideInMenu: true },
];

const dashboardRouteMeta: BackendRouteMeta[] = [
  { path: '/dashboard', name: 'nav.dashboard.root', icon: 'DashboardOutlined', access: 'canVisitDashboard', hideInMenu: true },
  { path: '/dashboard/home', name: 'nav.dashboard.home', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
];

const dashboardRoutes: BackendRouteRecord[] = [
  { path: '/', component: '@/pages/RootRedirect', hideInMenu: true },
  {
    path: '/dashboard',
    name: 'nav.dashboard.root',
    icon: 'DashboardOutlined',
    access: 'canVisitDashboard',
    hideInMenu: true,
    routes: [
      { path: '/dashboard', redirect: '/dashboard/home', hideInMenu: true },
      { path: '/dashboard/home', component: '@/pages/dashboard/DashboardHomePage', name: 'nav.dashboard.home', icon: 'DashboardOutlined', access: 'canVisitDashboard' },
    ],
  },
];

const teamRouteMeta: BackendRouteMeta[] = [
  { path: '/team', name: 'nav.team.root', icon: 'TeamOutlined', access: 'canVisitTeam' },
  { path: '/team/management', name: 'nav.team.management', icon: 'TeamOutlined', access: 'canVisitTeam' },
  { path: '/team/search', name: 'nav.team.search', icon: 'SearchOutlined', access: 'canVisitTeam' },
  { path: '/team/create', name: 'nav.team.create', icon: 'PlusOutlined', access: 'canVisitTeam', hideInMenu: true },
  { path: '/team/:teamId', name: 'nav.team.detail', icon: 'TeamOutlined', access: 'canVisitTeam', hideInMenu: true },
  { path: '/team/:teamId/members', name: 'nav.team.members', icon: 'UsergroupAddOutlined', access: 'canVisitTeam', hideInMenu: true },
  { path: '/team/:teamId/invites', name: 'nav.team.invites', icon: 'LinkOutlined', access: 'canVisitTeam', hideInMenu: true },
  { path: '/team/join', name: 'nav.team.join', icon: 'LinkOutlined', access: 'canVisitTeam', hideInMenu: true },
];

const teamRoutes: BackendRouteRecord[] = [
  {
    path: '/team',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.team.root',
    icon: 'TeamOutlined',
    access: 'canVisitTeam',
    routes: [
      { path: '/team', redirect: '/team/management', hideInMenu: true },
      { path: '/team/management', component: '@/pages/team', name: 'nav.team.management', icon: 'TeamOutlined', access: 'canVisitTeam' },
      { path: '/team/search', component: '@/pages/team', name: 'nav.team.search', icon: 'SearchOutlined', access: 'canVisitTeam' },
      { path: '/team/create', component: '@/pages/team', name: 'nav.team.create', icon: 'PlusOutlined', access: 'canVisitTeam', hideInMenu: true },
      { path: '/team/join', component: '@/pages/team', name: 'nav.team.join', icon: 'LinkOutlined', access: 'canVisitTeam', hideInMenu: true },
      { path: '/team/:teamId', component: '@/pages/team', name: 'nav.team.detail', icon: 'TeamOutlined', access: 'canVisitTeam', hideInMenu: true },
      { path: '/team/:teamId/members', component: '@/pages/team', name: 'nav.team.members', icon: 'UsergroupAddOutlined', access: 'canVisitTeam', hideInMenu: true },
      { path: '/team/:teamId/invites', component: '@/pages/team', name: 'nav.team.invites', icon: 'LinkOutlined', access: 'canVisitTeam', hideInMenu: true },
    ],
  },
];

const activityRouteMeta: BackendRouteMeta[] = [
  { path: '/activities', name: 'nav.activities.root', icon: 'CalendarOutlined', access: 'canVisitActivityRegister' },
  { path: '/activities/management', name: 'nav.activities.activities', icon: 'CalendarOutlined', access: 'canVisitActivities' },
  { path: '/activities/register', name: 'nav.activities.activityRegister', icon: 'FormOutlined', access: 'canVisitActivityRegister' },
  { path: '/activities/search', name: 'nav.activities.activitySearch', icon: 'SearchOutlined', access: 'canVisitActivities' },
];

const activityRoutes: BackendRouteRecord[] = [
  {
    path: '/activities',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.activities.root',
    icon: 'CalendarOutlined',
    access: 'canVisitActivityRegister',
    routes: [
      { path: '/activities', redirect: '/activities/register', hideInMenu: true },
      { path: '/activities/management', component: '@/pages/activity', name: 'nav.activities.activities', icon: 'CalendarOutlined', access: 'canVisitActivities' },
      { path: '/activities/register', component: '@/pages/competition', name: 'nav.activities.activityRegister', icon: 'FormOutlined', access: 'canVisitActivityRegister' },
      { path: '/activities/search', component: '@/pages/activity', name: 'nav.activities.activitySearch', icon: 'SearchOutlined', access: 'canVisitActivities' },
    ],
  },
];

const competitionRouteMeta: BackendRouteMeta[] = [
  { path: '/competitions', name: 'nav.competitions.root', icon: 'TrophyOutlined', access: 'isLogin' },
  { path: '/competitions/management', name: 'nav.competitions.management', icon: 'TrophyOutlined', access: 'canVisitCompetitions', hideInMenu: true },
  { path: '/competitions/register', name: 'nav.competitions.register', icon: 'FormOutlined', access: 'canVisitCompetitionRegister' },
  { path: '/competitions/activity-register', name: 'nav.activities.activityRegister', icon: 'CalendarOutlined', access: 'canVisitActivityRegister', hideInMenu: true },
  { path: '/competitions/expert-apply', name: 'nav.competitions.expertApply', icon: 'SolutionOutlined', access: 'canVisitExperts', hideInMenu: true },
  { path: '/competitions/create', name: 'nav.competitions.create', icon: 'TrophyOutlined', access: 'canVisitCompetitions', hideInMenu: true },
  { path: '/competitions/:competitionUuid/settings', name: 'nav.competitions.settings', icon: 'SettingOutlined', access: 'canVisitCompetitions', hideInMenu: true },
];

const projectRouteMeta: BackendRouteMeta[] = [
  { path: '/projects', name: 'nav.projects.root', icon: 'ProjectOutlined', access: 'canVisitProjects', hideInMenu: true },
  { path: '/projects/management', name: 'nav.projects.management', icon: 'ProjectOutlined', access: 'canVisitProjects' },
  { path: '/projects/search', name: 'nav.projects.search', icon: 'SearchOutlined', access: 'canVisitProjects' },
  { path: '/projects/create', name: 'nav.projects.create', icon: 'ProjectOutlined', access: 'canVisitProjects', hideInMenu: true },
];

const paymentRouteMeta: BackendRouteMeta[] = [
  { path: '/payments', name: 'nav.payments.root', icon: 'CreditCardOutlined', access: 'canVisitPaymentOrders', hideInMenu: true },
  { path: '/payments/management', name: 'nav.payments.management', icon: 'CreditCardOutlined', access: 'canVisitPaymentOrders' },
  { path: '/payments/status', name: 'nav.payments.status', icon: 'SearchOutlined', access: 'canVisitPaymentOrders' },
];

const certificateRouteMeta: BackendRouteMeta[] = [
  { path: '/certificates', name: 'nav.certificates.root', icon: 'FileProtectOutlined', access: 'canVisitCertificates' },
  { path: '/certificates/templates', name: 'nav.certificates.templates', icon: 'FileProtectOutlined', access: 'canVisitCertificateTemplates' },
  { path: '/certificates/templates/:id/designer', name: 'nav.certificates.designer', icon: 'FileProtectOutlined', access: 'canVisitCertificateTemplates', hideInMenu: true },
  { path: '/certificates/generate', name: 'nav.certificates.generate', icon: 'FileDoneOutlined', access: 'canVisitCertificateGenerate' },
  { path: '/certificates/records', name: 'nav.certificates.records', icon: 'AuditOutlined', access: 'canVisitCertificateRecords' },
];

const competitionRoutes: BackendRouteRecord[] = [
  {
    path: '/competitions',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.competitions.root',
    icon: 'TrophyOutlined',
    access: 'isLogin',
    routes: [
      { path: '/competitions', redirect: '/competitions/register', hideInMenu: true },
      { path: '/competitions/management', component: '@/pages/competition', name: 'nav.competitions.management', icon: 'TrophyOutlined', access: 'canVisitCompetitions', hideInMenu: true },
      { path: '/competitions/register', component: '@/pages/competition', name: 'nav.competitions.register', icon: 'FormOutlined', access: 'canVisitCompetitionRegister' },
      { path: '/competitions/activity-register', redirect: '/activities/register', name: 'nav.activities.activityRegister', icon: 'CalendarOutlined', access: 'canVisitActivityRegister', hideInMenu: true },
      { path: '/competitions/expert-apply', component: '@/pages/competition', name: 'nav.competitions.expertApply', icon: 'SolutionOutlined', access: 'canVisitExperts', hideInMenu: true },
      { path: '/competitions/create', component: '@/pages/competition', name: 'nav.competitions.create', icon: 'TrophyOutlined', access: 'canVisitCompetitions', hideInMenu: true },
      { path: '/competitions/:competitionUuid/settings', component: '@/pages/competition', name: 'nav.competitions.settings', icon: 'SettingOutlined', access: 'canVisitCompetitions', hideInMenu: true },
    ],
  },
];

const projectRoutes: BackendRouteRecord[] = [
  {
    path: '/projects',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.projects.root',
    icon: 'ProjectOutlined',
    access: 'canVisitProjects',
    routes: [
      { path: '/projects', redirect: '/projects/management', hideInMenu: true },
      { path: '/projects/management', component: '@/pages/project', name: 'nav.projects.management', icon: 'ProjectOutlined', access: 'canVisitProjects' },
      { path: '/projects/search', component: '@/pages/project', name: 'nav.projects.search', icon: 'SearchOutlined', access: 'canVisitProjects' },
      { path: '/projects/create', component: '@/pages/project', name: 'nav.projects.create', icon: 'ProjectOutlined', access: 'canVisitProjects', hideInMenu: true },
    ],
  },
];

const paymentRoutes: BackendRouteRecord[] = [
  {
    path: '/payments',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.payments.root',
    icon: 'CreditCardOutlined',
    access: 'canVisitPaymentOrders',
    routes: [
      { path: '/payments', redirect: '/payments/management', hideInMenu: true },
      { path: '/payments/management', component: '@/pages/payment', name: 'nav.payments.management', icon: 'CreditCardOutlined', access: 'canVisitPaymentOrders' },
      { path: '/payments/status', component: '@/pages/payment', name: 'nav.payments.status', icon: 'SearchOutlined', access: 'canVisitPaymentOrders' },
    ],
  },
];

const certificateRoutes: BackendRouteRecord[] = [
  {
    path: '/certificates',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.certificates.root',
    icon: 'FileProtectOutlined',
    access: 'canVisitCertificates',
    routes: [
      { path: '/certificates', redirect: '/certificates/templates', hideInMenu: true },
      { path: '/certificates/templates', component: '@/pages/certificates/TemplatesPage', name: 'nav.certificates.templates', icon: 'FileProtectOutlined', access: 'canVisitCertificateTemplates' },
      { path: '/certificates/templates/:id/designer', component: '@/pages/certificates/DesignerPage', name: 'nav.certificates.designer', icon: 'FileProtectOutlined', access: 'canVisitCertificateTemplates', hideInMenu: true },
      { path: '/certificates/generate', component: '@/pages/certificates/GeneratePage', name: 'nav.certificates.generate', icon: 'FileDoneOutlined', access: 'canVisitCertificateGenerate' },
      { path: '/certificates/records', component: '@/pages/certificates/RecordsPage', name: 'nav.certificates.records', icon: 'AuditOutlined', access: 'canVisitCertificateRecords' },
    ],
  },
];

const expertRouteMeta: BackendRouteMeta[] = [
  { path: '/expert-review', name: 'nav.expertReview.root', icon: 'SolutionOutlined', access: 'canVisitExpertReview' },
  { path: '/expert-review/reviews', name: '\u8bc4\u5ba1\u5217\u8868', icon: 'AuditOutlined', access: 'canVisitWorkflowTasks' },
  { path: '/experts', name: 'nav.experts.root', icon: 'SolutionOutlined', access: 'canVisitExperts' },
  { path: '/experts/management', name: 'nav.experts.management', icon: 'SolutionOutlined', access: 'canVisitExperts' },
  { path: '/experts/query', name: 'nav.experts.query', icon: 'SearchOutlined', access: 'canVisitExperts' },
];

const expertRoutes: BackendRouteRecord[] = [
  {
    path: '/expert-review',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.expertReview.root',
    icon: 'SolutionOutlined',
    access: 'canVisitExpertReview',
    routes: [
      { path: '/expert-review', redirect: '/expert-review/reviews', hideInMenu: true },
      { path: '/expert-review/reviews', component: '@/pages/workflow/WorkflowTasksPage', name: '\u8bc4\u5ba1\u5217\u8868', icon: 'AuditOutlined', access: 'canVisitWorkflowTasks' },
    ],
  },
  {
    path: '/experts',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.experts.root',
    icon: 'SolutionOutlined',
    access: 'canVisitExperts',
    routes: [
      { path: '/experts', redirect: '/experts/management', hideInMenu: true },
      { path: '/experts/management', component: '@/pages/expert', name: 'nav.experts.management', icon: 'SolutionOutlined', access: 'canVisitExperts' },
      { path: '/experts/query', component: '@/pages/expert', name: 'nav.experts.query', icon: 'SearchOutlined', access: 'canVisitExperts' },
    ],
  },
];

const workflowRouteMeta: BackendRouteMeta[] = [
  { path: '/workflows', name: 'nav.workflow.root', icon: 'BranchesOutlined', access: 'canVisitWorkflow' },
  { path: '/workflows/tasks', name: 'nav.workflow.tasks', icon: 'AuditOutlined', access: 'canVisitWorkflowTasks' },
  { path: '/workflows/config', name: 'nav.workflow.config', icon: 'BranchesOutlined', access: 'canVisitWorkflowConfig' },
];

const workflowRoutes: BackendRouteRecord[] = [
  {
    path: '/workflows',
    component: '@/layouts/SettingsLayout/SettingsLayout',
    name: 'nav.workflow.root',
    icon: 'BranchesOutlined',
    access: 'canVisitWorkflow',
    routes: [
      { path: '/workflows', redirect: '/workflows/tasks', hideInMenu: true },
      { path: '/workflows/tasks', component: '@/pages/workflow/WorkflowTasksPage', name: 'nav.workflow.tasks', icon: 'AuditOutlined', access: 'canVisitWorkflowTasks' },
      { path: '/workflows/config', component: '@/pages/workflow/WorkflowConfigPage', name: 'nav.workflow.config', icon: 'BranchesOutlined', access: 'canVisitWorkflowConfig' },
    ],
  },
];

const dataManagementRouteMeta: BackendRouteMeta[] = [
  { path: '/data-management', name: 'nav.data.management', icon: 'DatabaseOutlined', access: 'canVisitDataManagement' },
  ...competitionRouteMeta,
  ...activityRouteMeta.map((item) => item.path === '/activities' ? { ...item, hideInMenu: true } : item),
  ...projectRouteMeta,
  ...teamRouteMeta.map((item) => item.path === '/team' ? { ...item, hideInMenu: true } : item),
  ...paymentRouteMeta,
  { path: '/data-management/download-center', name: 'nav.files.downloadCenter', icon: 'DownloadOutlined', access: 'canVisitDownloadCenter' },
  { path: '/data-management/query-center', name: 'nav.data.queryCenter', icon: 'SearchOutlined', access: 'canVisitQueryCenter' },
];

const dataSourceRoutes = [
  ...activityRoutes,
  ...competitionRoutes,
  ...projectRoutes,
  ...teamRoutes,
  ...paymentRoutes,
];

const dataManagementRoutes: BackendRouteRecord[] = [
  { path: '/registration', redirect: '/competitions/register', name: 'nav.registration.root', icon: 'FormOutlined', hideInMenu: true },
  ...dataSourceRoutes,
  { path: '/data-management', redirect: '/competitions/management', name: 'nav.data.management', icon: 'DatabaseOutlined', access: 'canVisitDataManagement' },
  { path: '/data-management/download-center', component: '@/pages/files/DownloadCenter', name: 'nav.files.downloadCenter', icon: 'DownloadOutlined', access: 'canVisitDownloadCenter' },
  { path: '/data-management/query-center', redirect: '/team/search', name: 'nav.data.queryCenter', icon: 'SearchOutlined', access: 'canVisitQueryCenter', hideInMenu: true },
];

const publicRouteMeta: BackendRouteMeta[] = [
  { path: '/plugins/sensitive-words', name: 'nav.system.plugins', access: 'canVisitSensitiveWordsPlugin', hideInMenu: true },
  { path: '/plugins/work-order-feedback', name: 'nav.system.plugins', access: 'canVisitWorkOrderFeedbackPlugin', hideInMenu: true },
  { path: '/plugins/:pluginCode', name: 'nav.system.plugins', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', name: 'common.failure', hideInMenu: true },
  { path: '/public/certificate/verify', name: 'nav.certificates.verify', hideInMenu: true },
  { path: '/certificate/verify/:publicToken', name: 'nav.certificates.verify', hideInMenu: true },
  { path: '/account-activation', name: 'nav.account.activation', hideInMenu: true },
  { path: '/user/login', name: 'page.login.title', hideInMenu: true },
  { path: '/403', name: 'common.failure', hideInMenu: true },
  { path: '/404', name: 'common.failure', hideInMenu: true },
  { path: '/500', name: 'common.failure', hideInMenu: true },
];

const publicRoutes: BackendRouteRecord[] = [
  { path: '/plugins/sensitive-words', component: '@/pages/plugins/SensitiveWordsPage', name: 'nav.system.plugins', access: 'canVisitSensitiveWordsPlugin', hideInMenu: true },
  { path: '/plugins/work-order-feedback', component: '@/pages/plugins/WorkOrderFeedbackPage', name: 'nav.system.plugins', access: 'canVisitWorkOrderFeedbackPlugin', hideInMenu: true },
  { path: '/plugins/:pluginCode', component: '@/pages/plugins/RuntimeContainer', name: 'nav.system.plugins', access: 'canVisitPluginRuntime', hideInMenu: true },
  { path: '/blank/workflow', redirect: '/404', name: 'common.failure', hideInMenu: true },
  { path: '/public/certificate/verify', component: '@/pages/certificates/PublicVerifyPage', layout: false, name: 'nav.certificates.verify', hideInMenu: true },
  { path: '/certificate/verify/:publicToken', component: '@/pages/certificates/PublicVerifyPage', layout: false, name: 'nav.certificates.verify', hideInMenu: true },
  { path: '/account-activation', component: '@/pages/account/ActivationPage', layout: false, name: 'nav.account.activation', hideInMenu: true },
  { path: '/user/login', component: '@/pages/user/Login', layout: false, name: 'page.login.title', hideInMenu: true },
  { path: '/403', component: '@/pages/exception/NoPermission', name: 'common.failure', hideInMenu: true },
  { path: '/404', component: '@/pages/exception/NotFound', name: 'common.failure', hideInMenu: true },
  { path: '/500', component: '@/pages/exception/ServerError', name: 'common.failure', hideInMenu: true },
  { path: '*', redirect: '/404' },
];

export const backendRouteMeta: BackendRouteMeta[] = [
  ...dashboardRouteMeta,
  ...dataManagementRouteMeta,
  ...certificateRouteMeta,
  ...expertRouteMeta,
  ...workflowRouteMeta,
  ...systemRouteMeta,
  ...userCenterRouteMeta,
  ...publicRouteMeta,
];

export const backendRoutes: BackendRouteRecord[] = [
  ...dashboardRoutes,
  ...dataManagementRoutes,
  ...certificateRoutes,
  ...expertRoutes,
  ...workflowRoutes,
  ...systemRoutes,
  ...userCenterRoutes,
  ...publicRoutes,
];

const NON_AUTHORIZED_ROUTE_PATHS = new Set(['/user/login', '/account-activation', '/403', '/404', '/500', '/blank/workflow']);

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
export const backendRouteMetaMap = new Map(backendRouteMeta.map((item) => [item.path, item]));
