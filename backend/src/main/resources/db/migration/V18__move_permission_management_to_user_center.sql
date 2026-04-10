UPDATE sys_menu
SET parent_id = 3020,
    menu_name = '权限管理',
    path = '/user-center/permissions',
    component = '@/pages/iam/Overview',
    icon = 'SafetyCertificateOutlined',
    sort_no = 24,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'iam.overview'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    sort_no = 25,
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'profile.center'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    menu_name = '权限管理',
    path = '/user-center/permissions',
    component = '@/pages/iam/Overview',
    icon = 'SafetyCertificateOutlined',
    sort_no = 24,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'iam.overview'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    sort_no = 25,
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'profile.center'
  AND deleted = 0;
