-- Move the localization center from the legacy top-level route into the settings route tree.

UPDATE sys_menu
SET parent_id = CASE tenant_id
                    WHEN 1001 THEN 3002
                    WHEN 1002 THEN 4002
                    ELSE parent_id
                END,
    path = '/settings/localization',
    sort_no = 29,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND menu_code = 'localization.root'
  AND path = '/localization';
