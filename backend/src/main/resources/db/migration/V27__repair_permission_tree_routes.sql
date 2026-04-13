UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.root',
    menu_name = '系统管理',
    menu_type = 'CATALOG',
    path = '/system/management',
    component = '@/pages/system/Management',
    icon = 'AppstoreOutlined',
    sort_no = 20,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.root',
    menu_name = '系统管理',
    menu_type = 'CATALOG',
    path = '/system/management',
    component = '@/pages/system/Management',
    icon = 'AppstoreOutlined',
    sort_no = 20,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'user.center.root',
    menu_name = '用户中心',
    menu_type = 'CATALOG',
    path = '/user-center',
    component = '@/pages/user-center/index',
    icon = 'TeamOutlined',
    sort_no = 18,
    permission_key = NULL,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'user.center.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'user.center.root',
    menu_name = '用户中心',
    menu_type = 'CATALOG',
    path = '/user-center',
    component = '@/pages/user-center/index',
    icon = 'TeamOutlined',
    sort_no = 18,
    permission_key = NULL,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'user.center.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'system.users',
    menu_name = '用户管理',
    menu_type = 'MENU',
    path = '/user-center/users',
    component = '@/pages/system/users',
    icon = 'UserOutlined',
    sort_no = 21,
    permission_key = 'system:user:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.users'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'system.users',
    menu_name = '用户管理',
    menu_type = 'MENU',
    path = '/user-center/users',
    component = '@/pages/system/users',
    icon = 'UserOutlined',
    sort_no = 21,
    permission_key = 'system:user:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.users'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'system.online-users',
    menu_name = '在线用户',
    menu_type = 'MENU',
    path = '/user-center/online-users',
    component = '@/pages/system/online-users',
    icon = 'UserSwitchOutlined',
    sort_no = 22,
    permission_key = 'system:online-user:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.online-users'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'system.online-users',
    menu_name = '在线用户',
    menu_type = 'MENU',
    path = '/user-center/online-users',
    component = '@/pages/system/online-users',
    icon = 'UserSwitchOutlined',
    sort_no = 22,
    permission_key = 'system:online-user:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.online-users'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'system.roles',
    menu_name = '角色管理',
    menu_type = 'MENU',
    path = '/user-center/roles',
    component = '@/pages/system/roles',
    icon = 'SafetyOutlined',
    sort_no = 23,
    permission_key = 'system:role:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.roles'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'system.roles',
    menu_name = '角色管理',
    menu_type = 'MENU',
    path = '/user-center/roles',
    component = '@/pages/system/roles',
    icon = 'SafetyOutlined',
    sort_no = 23,
    permission_key = 'system:role:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.roles'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'user.center.permissions',
    menu_name = '权限管理',
    menu_type = 'MENU',
    path = '/user-center/permissions',
    component = '@/pages/iam/Overview',
    icon = 'SafetyCertificateOutlined',
    sort_no = 24,
    permission_key = 'iam:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code IN ('iam.overview', 'user.center.permissions')
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'user.center.permissions',
    menu_name = '权限管理',
    menu_type = 'MENU',
    path = '/user-center/permissions',
    component = '@/pages/iam/Overview',
    icon = 'SafetyCertificateOutlined',
    sort_no = 24,
    permission_key = 'iam:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code IN ('iam.overview', 'user.center.permissions')
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    menu_code = 'profile.center',
    menu_name = '个人中心',
    menu_type = 'MENU',
    path = '/user-center/profile',
    component = '@/pages/profile/Center',
    icon = 'UserOutlined',
    sort_no = 25,
    permission_key = 'profile:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'profile.center'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_code = 'profile.center',
    menu_name = '个人中心',
    menu_type = 'MENU',
    path = '/user-center/profile',
    component = '@/pages/profile/Center',
    icon = 'UserOutlined',
    sort_no = 25,
    permission_key = 'profile:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'profile.center'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3016,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4016,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.root'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3016,
    menu_code = 'system.monitoring.service',
    menu_name = '服务监控',
    menu_type = 'MENU',
    path = '/system/monitoring/service',
    component = '@/pages/system/monitoring/Service',
    icon = 'RadarChartOutlined',
    sort_no = 22,
    permission_key = 'system:monitor:service:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.service'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4016,
    menu_code = 'system.monitoring.service',
    menu_name = '服务监控',
    menu_type = 'MENU',
    path = '/system/monitoring/service',
    component = '@/pages/system/monitoring/Service',
    icon = 'RadarChartOutlined',
    sort_no = 22,
    permission_key = 'system:monitor:service:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.service'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3016,
    menu_code = 'system.monitoring.redis',
    menu_name = 'Redis监控',
    menu_type = 'MENU',
    path = '/system/monitoring/redis',
    component = '@/pages/system/monitoring/Redis',
    icon = 'DatabaseOutlined',
    sort_no = 23,
    permission_key = 'system:monitor:redis:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.redis'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4016,
    menu_code = 'system.monitoring.redis',
    menu_name = 'Redis监控',
    menu_type = 'MENU',
    path = '/system/monitoring/redis',
    component = '@/pages/system/monitoring/Redis',
    icon = 'DatabaseOutlined',
    sort_no = 23,
    permission_key = 'system:monitor:redis:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.redis'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3016,
    menu_code = 'system.monitoring.api-docs',
    menu_name = '接口文档',
    menu_type = 'MENU',
    path = '/system/monitoring/api-docs',
    component = '@/pages/system/monitoring/ApiDocs',
    icon = 'FileTextOutlined',
    sort_no = 24,
    permission_key = 'system:monitor:docs:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.api-docs'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4016,
    menu_code = 'system.monitoring.api-docs',
    menu_name = '接口文档',
    menu_type = 'MENU',
    path = '/system/monitoring/api-docs',
    component = '@/pages/system/monitoring/ApiDocs',
    icon = 'FileTextOutlined',
    sort_no = 24,
    permission_key = 'system:monitor:docs:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.api-docs'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3016,
    menu_code = 'system.monitoring.audit',
    menu_name = '审计中心',
    menu_type = 'MENU',
    path = '/system/monitoring/audit',
    component = '@/pages/audit/Overview',
    icon = 'AuditOutlined',
    sort_no = 25,
    permission_key = 'audit:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code IN ('audit.overview', 'system.monitoring.audit')
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4016,
    menu_code = 'system.monitoring.audit',
    menu_name = '审计中心',
    menu_type = 'MENU',
    path = '/system/monitoring/audit',
    component = '@/pages/audit/Overview',
    icon = 'AuditOutlined',
    sort_no = 25,
    permission_key = 'audit:view',
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code IN ('audit.overview', 'system.monitoring.audit')
  AND deleted = 0;
