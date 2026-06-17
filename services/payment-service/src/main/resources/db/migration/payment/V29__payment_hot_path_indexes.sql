ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_deleted_status` (`deleted`, `status`),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE `payment_webhook_event`
    ADD INDEX `idx_payment_webhook_event_tenant_provider_nonce_deleted_received` (`tenant_id`, `provider_code`, `nonce`, `deleted`, `received_at`),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE `payment_webhook_event`
    ADD INDEX `idx_payment_webhook_event_tenant_provider_event_deleted_id` (`tenant_id`, `provider_code`, `event_id`, `deleted`, `id`),
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE `payment_provider_config`
    ADD INDEX `idx_payment_provider_config_tenant_provider_deleted_id` (`tenant_id`, `provider_code`, `deleted`, `id`),
    ALGORITHM = INPLACE,
    LOCK = NONE;
