ALTER TABLE `sys_config`
    ADD INDEX `idx_sys_config_scope_key_tenant_deleted` (`config_scope`, `config_key`, `tenant_id`, `deleted`);

ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_current` (`tenant_id`, `enabled`, `deleted`, `plugin_code`, `plugin_version`);

ALTER TABLE `audit_login_log`
    ADD INDEX `idx_audit_login_user_result_recent` (`tenant_id`, `user_id`, `login_result`, `created_at`, `id`);

ALTER TABLE `audit_operation_log`
    ADD INDEX `idx_audit_operation_tenant_user_recent` (`tenant_id`, `username`, `created_at`, `id`);

ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_recent` (`tenant_id`, `publish_status`, `deleted`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_user_recent` (`tenant_id`, `publish_status`, `deleted`, `target_user_id`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_role_recent` (`tenant_id`, `publish_status`, `deleted`, `target_role_id`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
