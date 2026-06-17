ALTER TABLE `sys_user_role`
    ADD INDEX `idx_sys_user_role_tenant_user_deleted` (`tenant_id`, `user_id`, `deleted`, `role_id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_role_permission`
    ADD INDEX `idx_sys_role_permission_tenant_role_deleted_perm` (`tenant_id`, `role_id`, `deleted`, `permission_key`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
