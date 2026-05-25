CREATE TABLE IF NOT EXISTS `file_storage_space` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `storage_key` varchar(64) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `root_path` varchar(255) DEFAULT NULL,
  `bucket_name` varchar(128) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `access_key_id` varchar(255) DEFAULT NULL,
  `access_key_secret` varchar(512) DEFAULT NULL,
  `rename_strategy` varchar(32) NOT NULL DEFAULT 'APPEND_RANDOM_ID',
  `max_file_size_mb` int NOT NULL DEFAULT '20',
  `allowed_mime_types` varchar(1024) NOT NULL DEFAULT '*',
  `default_flag` tinyint NOT NULL DEFAULT '0',
  `retain_file_on_record_delete` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_space_key` (`tenant_id`,`storage_key`),
  KEY `idx_file_storage_space_default` (`tenant_id`,`default_flag`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `file_storage_space` (
  `tenant_id`, `title`, `storage_key`, `provider`, `root_path`, `rename_strategy`,
  `max_file_size_mb`, `allowed_mime_types`, `default_flag`, `retain_file_on_record_delete`,
  `status`, `created_by`, `updated_by`, `deleted`
)
SELECT 1001, 'Local storage', 'local', 'LOCAL', 'storage/uploads/', 'APPEND_RANDOM_ID',
       20, '*', 1, 0, 'ENABLED', 1, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `file_storage_space` WHERE `tenant_id` = 1001 AND `storage_key` = 'local'
);

UPDATE `file_object`
SET `bucket` = 'local'
WHERE `tenant_id` = 1001
  AND `deleted` = 0
  AND (`bucket` IS NULL OR `bucket` = '');
