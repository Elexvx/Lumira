UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.root';

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.root';
