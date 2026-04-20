UPDATE sys_menu
SET path = '/system',
    component = NULL,
    updated_by = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.root'
  AND deleted = 0;

UPDATE sys_menu
SET path = '/system',
    component = NULL,
    updated_by = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.root'
  AND deleted = 0;
