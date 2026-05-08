SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `ai_conversation`
  ADD COLUMN `is_pinned` tinyint unsigned NOT NULL DEFAULT '0' AFTER `status`;

CREATE TABLE IF NOT EXISTS `ai_message_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `message_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `public_url` varchar(1024) DEFAULT NULL,
  `preview_url` varchar(1024) DEFAULT NULL,
  `download_url` varchar(1024) DEFAULT NULL,
  `preview_mode` varchar(32) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_attachment_rel` (`tenant_id`,`message_id`,`file_id`),
  KEY `idx_ai_message_attachment_tenant_conversation` (`tenant_id`,`conversation_id`),
  KEY `idx_ai_message_attachment_tenant_message` (`tenant_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_conversation_share` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `share_token` varchar(128) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_share_token` (`tenant_id`,`share_token`),
  KEY `idx_ai_conversation_share_tenant_conversation` (`tenant_id`,`conversation_id`),
  KEY `idx_ai_conversation_share_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
