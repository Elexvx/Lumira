ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_owner_queue` (`deleted`, `source_type`, `status`, `next_retry_at`, `created_at`, `id`),
    ALGORITHM = INPLACE,
    LOCK = NONE;
