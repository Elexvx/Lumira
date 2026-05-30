-- Baseline message domain schema for standalone message-service.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `msg_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_type` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_role_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `publish_status` varchar(32) NOT NULL DEFAULT 'PUBLISHED',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_notice_tenant_type_status_created` (`tenant_id`,`notice_type`,`publish_status`,`created_at`),
  KEY `idx_msg_notice_tenant_target_created` (`tenant_id`,`target_user_id`,`created_at`),
  KEY `idx_msg_notice_tenant_target_role_created` (`tenant_id`,`target_role_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `msg_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `read_at` datetime NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_notice_read` (`tenant_id`,`notice_id`,`user_id`),
  KEY `idx_msg_notice_read_user_created` (`tenant_id`,`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `platform_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `dispatch_status` varchar(32) NOT NULL DEFAULT 'RECORDED',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_event_outbox_tenant_status` (`tenant_id`,`dispatch_status`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `msg_delivery_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_user_name` varchar(64) DEFAULT NULL,
  `target_email` varchar(128) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `send_status` varchar(32) NOT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_delivery_log_tenant_channel_created` (`tenant_id`,`channel`,`created_at`),
  KEY `idx_msg_delivery_log_tenant_status_created` (`tenant_id`,`send_status`,`created_at`),
  KEY `idx_msg_delivery_log_notice` (`tenant_id`,`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
