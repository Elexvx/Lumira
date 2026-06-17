ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
