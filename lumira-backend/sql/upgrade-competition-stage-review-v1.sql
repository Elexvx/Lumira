CREATE TABLE IF NOT EXISTS `competition_stage_review_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `score` decimal(10,2) DEFAULT NULL,
  `decision` varchar(32) NOT NULL DEFAULT 'PENDING',
  `review_comment` varchar(1000) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_by_uuid` char(36) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_review_result` (`stage_id`,`registration_id`,`deleted`),
  KEY `idx_competition_stage_review_result_rank` (`competition_id`,`stage_id`,`decision`,`score`,`deleted`),
  KEY `idx_competition_stage_review_result_registration` (`registration_id`,`published_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `registration_material_value_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `field_key` varchar(128) NOT NULL,
  `field_type` varchar(32) NOT NULL,
  `text_value` longtext,
  `file_id` bigint DEFAULT NULL,
  `json_value` longtext,
  `changed_by` bigint NOT NULL,
  `changed_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_registration_material_value_revision` (`submission_id`,`revision_no`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
