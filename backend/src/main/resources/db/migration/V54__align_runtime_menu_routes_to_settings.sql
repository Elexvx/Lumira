-- Align persisted runtime menus with the current frontend route ownership.
-- Older databases can still contain /system/** and /files/** menu paths; the
-- main sidebar intentionally hides those compatibility routes, so they must be
-- normalized to the active /settings/** and /user-center/** routes.

UPDATE sys_menu
SET menu_code = 'settings.root',
    menu_name = '系统设置',
    path = '/settings',
    component = NULL,
    sort_no = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.root', 'settings.root');

UPDATE sys_menu child
JOIN sys_menu root
  ON root.tenant_id = child.tenant_id
 AND root.menu_code = 'settings.root'
 AND root.deleted = 0
SET child.parent_id = root.id,
    child.updated_at = CURRENT_TIMESTAMP
WHERE child.deleted = 0
  AND child.menu_code IN (
    'system.plugins',
    'system.menus',
    'system.dicts',
    'system.profile-fields',
    'system.security',
    'system.personalization',
    'system.notifications',
    'system.verification',
    'settings.ai-employees',
    'localization.root',
    'files.root',
    'files.all',
    'settings.monitoring.root',
    'system.monitoring.root',
    'settings.monitoring.api-docs',
    'system.monitoring.api-docs',
    'settings.monitoring.audit',
    'system.monitoring.audit'
  );

UPDATE sys_menu
SET menu_code = 'settings.plugins',
    path = '/settings/plugins',
    component = '@/pages/settings/plugins',
    sort_no = 8,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.plugins', 'settings.plugins');

UPDATE sys_menu
SET menu_code = 'settings.menus',
    path = '/settings/menus',
    component = '@/pages/settings/menus',
    sort_no = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.menus', 'settings.menus');

UPDATE sys_menu
SET menu_code = 'settings.dicts',
    path = '/settings/dicts',
    component = '@/pages/settings/dicts',
    sort_no = 2,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.dicts', 'settings.dicts');

UPDATE sys_menu
SET menu_code = 'settings.profile-fields',
    path = '/settings/profile-fields',
    component = '@/pages/settings/profile-fields',
    sort_no = 3,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.profile-fields', 'settings.profile-fields');

UPDATE sys_menu
SET menu_code = 'settings.personalization',
    path = '/settings/personalization',
    component = '@/pages/settings/personalization',
    sort_no = 4,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.personalization', 'settings.personalization');

UPDATE sys_menu
SET menu_code = 'settings.security',
    path = '/settings/security',
    component = '@/pages/settings/security',
    sort_no = 5,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.security', 'settings.security');

UPDATE sys_menu
SET menu_code = 'settings.verification',
    path = '/settings/verification',
    component = '@/pages/settings/verification',
    sort_no = 6,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.verification', 'settings.verification');

UPDATE sys_menu
SET menu_code = 'settings.notifications',
    path = '/settings/notifications',
    component = '@/pages/settings/notifications/index',
    sort_no = 7,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.notifications', 'settings.notifications');

UPDATE sys_menu
SET path = '/settings/ai-employees',
    component = '@/pages/settings/ai-employees',
    sort_no = 24,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code = 'settings.ai-employees';

UPDATE sys_menu
SET path = '/settings/localization',
    component = '@/pages/settings/localization',
    sort_no = 29,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code = 'localization.root';

UPDATE sys_menu
SET deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code = 'files.root';

UPDATE sys_menu
SET menu_code = 'settings.files',
    menu_name = '全站文件管理',
    path = '/settings/files/all',
    component = '@/pages/settings/files/Center',
    permission_key = 'system:file:manage',
    sort_no = 9,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('files.all', 'settings.files');

UPDATE sys_menu
SET menu_code = 'settings.monitoring.root',
    path = '/settings/monitoring',
    component = '@/pages/settings/monitoring/index',
    sort_no = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.monitoring.root', 'settings.monitoring.root');

UPDATE sys_menu child
JOIN sys_menu monitoring
  ON monitoring.tenant_id = child.tenant_id
 AND monitoring.menu_code = 'settings.monitoring.root'
 AND monitoring.deleted = 0
SET child.parent_id = monitoring.id,
    child.updated_at = CURRENT_TIMESTAMP
WHERE child.deleted = 0
  AND child.menu_code IN ('system.monitoring.service', 'system.monitoring.redis');

UPDATE sys_menu
SET menu_code = 'settings.monitoring.service',
    path = '/settings/monitoring/service',
    component = 'redirect:/settings/monitoring?tab=service',
    sort_no = 22,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.monitoring.service', 'settings.monitoring.service');

UPDATE sys_menu
SET menu_code = 'settings.monitoring.redis',
    path = '/settings/monitoring/redis',
    component = 'redirect:/settings/monitoring?tab=redis',
    sort_no = 23,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.monitoring.redis', 'settings.monitoring.redis');

UPDATE sys_menu
SET menu_code = 'settings.monitoring.api-docs',
    path = '/settings/api-docs',
    component = '@/pages/settings/monitoring/ApiDocs',
    sort_no = 11,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.monitoring.api-docs', 'settings.monitoring.api-docs');

UPDATE sys_menu
SET menu_code = 'settings.monitoring.audit',
    path = '/settings/audit',
    component = '@/pages/settings/monitoring/Audit',
    sort_no = 12,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code IN ('system.monitoring.audit', 'settings.monitoring.audit');

UPDATE sys_menu
SET path = '/user-center/files',
    component = '@/pages/files/Center',
    sort_no = 26,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code = 'files.my';
