-- Convert payment tables from tenant-scoped keys to global role-protected data.
-- Safe to run repeatedly on the current single-platform schema.

CREATE TABLE IF NOT EXISTS `role_only_payment_backup_payment_event_outbox_20260625` AS SELECT * FROM `payment_event_outbox`;
CREATE TABLE IF NOT EXISTS `role_only_payment_backup_payment_order_20260625` AS SELECT * FROM `payment_order`;
CREATE TABLE IF NOT EXISTS `role_only_payment_backup_payment_provider_config_20260625` AS SELECT * FROM `payment_provider_config`;
CREATE TABLE IF NOT EXISTS `role_only_payment_backup_payment_refund_20260625` AS SELECT * FROM `payment_refund`;
CREATE TABLE IF NOT EXISTS `role_only_payment_backup_payment_webhook_event_20260625` AS SELECT * FROM `payment_webhook_event`;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `uk_payment_outbox_event`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'uk_payment_outbox_event');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `idx_payment_outbox_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'idx_payment_outbox_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `idx_payment_outbox_created_at`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'idx_payment_outbox_created_at');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `idx_payment_outbox_deleted_status_retry_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'idx_payment_outbox_deleted_status_retry_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `idx_payment_outbox_deleted_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'idx_payment_outbox_deleted_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP INDEX `idx_payment_outbox_owner_queue`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND index_name = 'idx_payment_outbox_owner_queue');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_event_outbox` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_event_outbox' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `payment_event_outbox`
    ADD UNIQUE KEY `uk_payment_outbox_event` (`source_type`, `event_type`, `event_key`),
    ADD KEY `idx_payment_outbox_status` (`status`, `next_retry_at`),
    ADD KEY `idx_payment_outbox_created_at` (`created_at`),
    ADD KEY `idx_payment_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`),
    ADD KEY `idx_payment_outbox_deleted_status` (`deleted`, `status`),
    ADD KEY `idx_payment_outbox_owner_queue` (`deleted`, `source_type`, `status`, `next_retry_at`, `created_at`, `id`);

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `uk_payment_order_tenant_order_no`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'uk_payment_order_tenant_order_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `uk_payment_order_order_no`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'uk_payment_order_order_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `uk_payment_order_tenant_idempotency_key`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'uk_payment_order_tenant_idempotency_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `uk_payment_order_idempotency_key`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'uk_payment_order_idempotency_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `idx_payment_order_tenant_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'idx_payment_order_tenant_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `idx_payment_order_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'idx_payment_order_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP INDEX `idx_payment_order_provider`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND index_name = 'idx_payment_order_provider');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_order` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_order' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `payment_order`
    MODIFY COLUMN `order_no` varchar(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD UNIQUE KEY `uk_payment_order_order_no` (`order_no`),
    ADD UNIQUE KEY `uk_payment_order_idempotency_key` (`idempotency_key`),
    ADD KEY `idx_payment_order_status` (`status`),
    ADD KEY `idx_payment_order_provider` (`provider_code`, `provider_order_no`);

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `uk_payment_provider_config_tenant_provider`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'uk_payment_provider_config_tenant_provider');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `uk_payment_provider_config_provider`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'uk_payment_provider_config_provider');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_tenant_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_tenant_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_provider`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_provider');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_provider_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_provider_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_tenant_provider_deleted_id`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_tenant_provider_deleted_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP INDEX `idx_payment_provider_config_provider_deleted_id`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND index_name = 'idx_payment_provider_config_provider_deleted_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_provider_config` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_provider_config' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `payment_provider_config`
    ADD UNIQUE KEY `uk_payment_provider_config_provider` (`provider_code`),
    ADD KEY `idx_payment_provider_config_deleted` (`deleted`),
    ADD KEY `idx_payment_provider_config_provider_deleted` (`provider_code`, `deleted`),
    ADD KEY `idx_payment_provider_config_provider_deleted_id` (`provider_code`, `deleted`, `id`);

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `uk_payment_refund_tenant_refund_no`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'uk_payment_refund_tenant_refund_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `uk_payment_refund_refund_no`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'uk_payment_refund_refund_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `uk_payment_refund_tenant_idempotency_key`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'uk_payment_refund_tenant_idempotency_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `uk_payment_refund_idempotency_key`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'uk_payment_refund_idempotency_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `idx_payment_refund_tenant_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'idx_payment_refund_tenant_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `idx_payment_refund_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'idx_payment_refund_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP INDEX `idx_payment_refund_order_no`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND index_name = 'idx_payment_refund_order_no');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_refund` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_refund' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `payment_refund`
    ADD UNIQUE KEY `uk_payment_refund_refund_no` (`refund_no`),
    ADD UNIQUE KEY `uk_payment_refund_idempotency_key` (`idempotency_key`),
    ADD KEY `idx_payment_refund_status` (`status`),
    ADD KEY `idx_payment_refund_order_no` (`order_no`);

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `uk_payment_webhook_event_tenant_provider_event`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'uk_payment_webhook_event_tenant_provider_event');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `uk_payment_webhook_event_provider_event`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'uk_payment_webhook_event_provider_event');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_nonce`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_nonce');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_tenant_provider_nonce_deleted_received`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_tenant_provider_nonce_deleted_received');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_provider_nonce_deleted_received`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_provider_nonce_deleted_received');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_tenant_provider_event_deleted_id`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_tenant_provider_event_deleted_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP INDEX `idx_payment_webhook_event_provider_event_deleted_id`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND index_name = 'idx_payment_webhook_event_provider_event_deleted_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `payment_webhook_event` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_webhook_event' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `payment_webhook_event`
    ADD UNIQUE KEY `uk_payment_webhook_event_provider_event` (`provider_code`, `event_id`),
    ADD KEY `idx_payment_webhook_event_nonce` (`provider_code`, `nonce`),
    ADD KEY `idx_payment_webhook_event_status` (`processed`, `retry_count`),
    ADD KEY `idx_payment_webhook_event_provider_nonce_deleted_received` (`provider_code`, `nonce`, `deleted`, `received_at`),
    ADD KEY `idx_payment_webhook_event_provider_event_deleted_id` (`provider_code`, `event_id`, `deleted`, `id`);
