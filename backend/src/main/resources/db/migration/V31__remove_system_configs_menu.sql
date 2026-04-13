UPDATE sys_menu
SET deleted = 1,
    updated_by = 0
WHERE tenant_id IN (1001, 1002)
  AND menu_code = 'system.configs'
  AND deleted = 0;
