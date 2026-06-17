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
