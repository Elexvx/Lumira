ALTER TABLE `sys_plugin_definition`
    ADD INDEX `idx_sys_plugin_definition_deleted_status_sort_code` (`deleted`, `status`, `sort_no`, `plugin_code`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_version`
    ADD INDEX `idx_sys_plugin_version_plugin_deleted_status_created` (`plugin_code`, `deleted`, `created_at`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_tenant_deleted_enabled_code` (`tenant_id`, `deleted`, `enabled`, `plugin_code`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_code_deleted_enabled` (`plugin_code`, `deleted`, `enabled`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_runtime_log`
    ADD INDEX `idx_sys_plugin_runtime_log_code_deleted_id` (`plugin_code`, `deleted`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_menu_rel`
    ADD INDEX `idx_sys_plugin_menu_rel_code_version_deleted_sort` (`plugin_code`, `plugin_version`, `deleted`, `sort_no`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_plugin_permission_rel`
    ADD INDEX `idx_sys_plugin_permission_rel_code_version_deleted` (`plugin_code`, `plugin_version`, `deleted`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
