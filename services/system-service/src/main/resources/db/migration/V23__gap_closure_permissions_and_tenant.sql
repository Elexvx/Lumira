CREATE TABLE IF NOT EXISTS `sys_tenant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`tenant_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'platform', '平台默认租户', 'ENABLED', '系统默认租户，用于兼容既有平台数据。', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `tenant_name` = VALUES(`tenant_name`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1001, 'system:role:delete', '删除角色', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:tenant:view', '查看租户', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:tenant:create', '新增租户', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:tenant:update', '编辑租户', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:tenant:delete', '删除租户', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:menu:delete', '删除菜单', 'system', 'CORE', 0, 0, 0),
  (1001, 'system:dict:delete', '删除字典', 'system', 'CORE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1001, 2001, 'system:role:delete', 0, 0, 0),
  (1001, 2001, 'system:tenant:view', 0, 0, 0),
  (1001, 2001, 'system:tenant:create', 0, 0, 0),
  (1001, 2001, 'system:tenant:update', 0, 0, 0),
  (1001, 2001, 'system:tenant:delete', 0, 0, 0),
  (1001, 2001, 'system:menu:delete', 0, 0, 0),
  (1001, 2001, 'system:dict:delete', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES
  (3032, 1001, 3002, 'settings.tenants', '租户管理', 'MENU', '/settings/tenants', '@/pages/settings/tenants', 'ApartmentOutlined', 0, 'system:tenant:view', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = VALUES(`permission_key`),
  `status` = VALUES(`status`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
