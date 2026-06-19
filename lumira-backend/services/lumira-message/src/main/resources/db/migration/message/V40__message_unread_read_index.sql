ALTER TABLE `msg_notice_read`
    ADD INDEX `idx_msg_notice_read_tenant_notice_user_deleted` (`tenant_id`, `notice_id`, `user_id`, `deleted`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
