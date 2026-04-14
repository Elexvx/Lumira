UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.root',
    menu_name = '系统总览',
    menu_type = 'CATALOG',
    path = '/system/overview',
    component = '@/pages/system/Overview',
    icon = 'AppstoreOutlined',
    sort_no = 20,
    permission_key = 'system:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.root';

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'system.root',
    menu_name = '系统总览',
    menu_type = 'CATALOG',
    path = '/system/overview',
    component = '@/pages/system/Overview',
    icon = 'AppstoreOutlined',
    sort_no = 20,
    permission_key = 'system:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.root';
