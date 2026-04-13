INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3027, 1001, 3016, 'system.monitoring.service', '服务监控', 'MENU', '/system/monitoring/service', '@/pages/system/monitoring/Service', 'RadarChartOutlined', 22, 'system:monitor:service:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.monitoring.service');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3028, 1001, 3016, 'system.monitoring.redis', 'Redis监控', 'MENU', '/system/monitoring/redis', '@/pages/system/monitoring/Redis', 'DatabaseOutlined', 23, 'system:monitor:redis:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.monitoring.redis');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3029, 1001, 3016, 'system.monitoring.api-docs', '接口文档', 'MENU', '/system/monitoring/api-docs', '@/pages/system/monitoring/ApiDocs', 'FileTextOutlined', 24, 'system:monitor:docs:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.monitoring.api-docs');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4027, 1002, 4016, 'system.monitoring.service', '服务监控', 'MENU', '/system/monitoring/service', '@/pages/system/monitoring/Service', 'RadarChartOutlined', 22, 'system:monitor:service:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.monitoring.service');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4028, 1002, 4016, 'system.monitoring.redis', 'Redis监控', 'MENU', '/system/monitoring/redis', '@/pages/system/monitoring/Redis', 'DatabaseOutlined', 23, 'system:monitor:redis:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.monitoring.redis');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4029, 1002, 4016, 'system.monitoring.api-docs', '接口文档', 'MENU', '/system/monitoring/api-docs', '@/pages/system/monitoring/ApiDocs', 'FileTextOutlined', 24, 'system:monitor:docs:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.monitoring.api-docs');
