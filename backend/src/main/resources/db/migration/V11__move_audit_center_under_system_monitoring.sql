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
  AND permission_key = 'audit:view'
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
  AND permission_key = 'audit:view'
  AND deleted = 0;
