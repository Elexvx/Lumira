CREATE TABLE IF NOT EXISTS `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `word` varchar(128) NOT NULL,
  `normalized_word` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_tenant_normalized` (`tenant_id`,`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
