UPDATE sys_menu
SET parent_id = (
      SELECT root.id
      FROM (SELECT id, tenant_id FROM sys_menu WHERE menu_code = 'settings.root' AND deleted = 0) root
      WHERE root.tenant_id = sys_menu.tenant_id
      LIMIT 1
    ),
    path = '/settings/api-docs',
    sort_no = 11,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'settings.monitoring.api-docs'
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = (
      SELECT root.id
      FROM (SELECT id, tenant_id FROM sys_menu WHERE menu_code = 'settings.root' AND deleted = 0) root
      WHERE root.tenant_id = sys_menu.tenant_id
      LIMIT 1
    ),
    path = '/settings/audit',
    sort_no = 12,
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'settings.monitoring.audit'
  AND deleted = 0;
