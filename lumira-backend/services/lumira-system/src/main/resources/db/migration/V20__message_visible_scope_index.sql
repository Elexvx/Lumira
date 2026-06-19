ALTER TABLE `msg_notice`
    DROP INDEX `idx_msg_notice_visible_recent`,
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_recent` (`tenant_id`, `publish_status`, `deleted`, `target_scope`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
