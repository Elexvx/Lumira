CREATE TABLE IF NOT EXISTS `aiadc_activity_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT, `application_no` varchar(32) NOT NULL, `activity_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL, `mobile` varchar(32) DEFAULT NULL, `email` varchar(255) DEFAULT NULL,
  `organization` varchar(255) DEFAULT NULL, `position` varchar(128) DEFAULT NULL, `remark` varchar(1000) DEFAULT NULL,
  `form_data_json` longtext,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED', `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `owner_user_id` bigint NOT NULL, `owner_user_uuid` char(36) NOT NULL, `owner_username` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL, `created_by_uuid` char(36) NOT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL, `updated_by_uuid` char(36) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_aiadc_activity_registration_no` (`application_no`),
  KEY `idx_aiadc_activity_registration_owner` (`owner_user_id`,`deleted`,`submitted_at`),
  KEY `idx_aiadc_activity_registration_activity` (`activity_id`,`deleted`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
