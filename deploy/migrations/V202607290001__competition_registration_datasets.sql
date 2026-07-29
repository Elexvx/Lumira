-- Expand-only migration for competition-scoped registration datasets and export queue support.
CREATE TABLE IF NOT EXISTS `competition_registration_dataset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `dataset_code` varchar(96) NOT NULL,
  `dataset_name` varchar(255) NOT NULL,
  `schema_json` longtext,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_registration_dataset_competition` (`competition_id`,`deleted`),
  UNIQUE KEY `uk_competition_registration_dataset_code` (`dataset_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_registration_dataset_row` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `owner_user_uuid` char(36) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_registration_dataset_row` (`registration_id`,`deleted`),
  KEY `idx_competition_registration_dataset_rows` (`dataset_id`,`deleted`,`registration_id`),
  KEY `idx_competition_registration_dataset_owner` (`dataset_id`,`owner_user_id`,`owner_user_uuid`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `competition_registration_dataset`
(`competition_id`,`dataset_code`,`dataset_name`,`schema_json`,`status`,
 `created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT competition.`id`,
       CONCAT('competition-registration-', competition.`id`),
       CONCAT(competition.`title`, ' · 报名数据表'),
       '{"version":1,"rowType":"COMPETITION_REGISTRATION","columns":["registration","team","project","members","materials"]}',
       'ENABLED',
       competition.`created_by`, NULL,
       competition.`updated_by`, NULL, 0
  FROM `aiadc_competition` competition
 WHERE competition.`deleted` = 0
   AND NOT EXISTS (
       SELECT 1
         FROM `competition_registration_dataset` dataset
        WHERE dataset.`competition_id` = competition.`id` AND dataset.`deleted` = 0
   );

INSERT INTO `competition_registration_dataset_row`
(`dataset_id`,`registration_id`,`owner_user_id`,`owner_user_uuid`,
 `created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT dataset.`id`, registration.`id`, registration.`owner_user_id`, NULL,
       registration.`created_by`, NULL,
       registration.`updated_by`, NULL, 0
  FROM `competition_registration` registration
  JOIN `competition_registration_dataset` dataset
    ON dataset.`competition_id` = registration.`competition_id`
   AND dataset.`status` = 'ENABLED'
   AND dataset.`deleted` = 0
 WHERE registration.`deleted` = 0
   AND NOT EXISTS (
       SELECT 1
         FROM `competition_registration_dataset_row` dataset_row
        WHERE dataset_row.`registration_id` = registration.`id` AND dataset_row.`deleted` = 0
   );

SET @export_created_by_uuid_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND column_name = 'created_by_uuid'
);
SET @export_created_by_uuid_sql = IF(
    @export_created_by_uuid_exists = 0,
    'ALTER TABLE `sys_export_task` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`',
    'SELECT 1'
);
PREPARE export_created_by_uuid_statement FROM @export_created_by_uuid_sql;
EXECUTE export_created_by_uuid_statement;
DEALLOCATE PREPARE export_created_by_uuid_statement;

SET @export_claimed_by_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND column_name = 'claimed_by'
);
SET @export_claimed_by_sql = IF(
    @export_claimed_by_exists = 0,
    'ALTER TABLE `sys_export_task` ADD COLUMN `claimed_by` varchar(128) DEFAULT NULL AFTER `created_at`',
    'SELECT 1'
);
PREPARE export_claimed_by_statement FROM @export_claimed_by_sql;
EXECUTE export_claimed_by_statement;
DEALLOCATE PREPARE export_claimed_by_statement;

SET @export_claim_token_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND column_name = 'claim_token'
);
SET @export_claim_token_sql = IF(
    @export_claim_token_exists = 0,
    'ALTER TABLE `sys_export_task` ADD COLUMN `claim_token` varchar(128) DEFAULT NULL AFTER `claimed_by`',
    'SELECT 1'
);
PREPARE export_claim_token_statement FROM @export_claim_token_sql;
EXECUTE export_claim_token_statement;
DEALLOCATE PREPARE export_claim_token_statement;

SET @export_claim_expires_at_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND column_name = 'claim_expires_at'
);
SET @export_claim_expires_at_sql = IF(
    @export_claim_expires_at_exists = 0,
    'ALTER TABLE `sys_export_task` ADD COLUMN `claim_expires_at` datetime DEFAULT NULL AFTER `claim_token`',
    'SELECT 1'
);
PREPARE export_claim_expires_at_statement FROM @export_claim_expires_at_sql;
EXECUTE export_claim_expires_at_statement;
DEALLOCATE PREPARE export_claim_expires_at_statement;

SET @export_creator_uuid_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND index_name = 'idx_sys_export_task_creator_uuid'
);
SET @export_creator_uuid_index_sql = IF(
    @export_creator_uuid_index_exists = 0,
    'ALTER TABLE `sys_export_task` ADD INDEX `idx_sys_export_task_creator_uuid` (`created_by_uuid`,`created_at`)',
    'SELECT 1'
);
PREPARE export_creator_uuid_index_statement FROM @export_creator_uuid_index_sql;
EXECUTE export_creator_uuid_index_statement;
DEALLOCATE PREPARE export_creator_uuid_index_statement;

SET @export_claim_token_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND index_name = 'idx_sys_export_task_claim_token'
);
SET @export_claim_token_index_sql = IF(
    @export_claim_token_index_exists = 0,
    'ALTER TABLE `sys_export_task` ADD INDEX `idx_sys_export_task_claim_token` (`claim_token`)',
    'SELECT 1'
);
PREPARE export_claim_token_index_statement FROM @export_claim_token_index_sql;
EXECUTE export_claim_token_index_statement;
DEALLOCATE PREPARE export_claim_token_index_statement;

SET @registration_export_queue_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_export_task'
       AND index_name = 'idx_sys_export_task_module_queue'
);
SET @registration_export_queue_index_sql = IF(
    @registration_export_queue_index_exists = 0,
    'ALTER TABLE `sys_export_task` ADD INDEX `idx_sys_export_task_module_queue` (`module_key`,`deleted`,`status`,`claim_expires_at`,`created_at`,`id`)',
    'SELECT 1'
);
PREPARE registration_export_queue_index_statement FROM @registration_export_queue_index_sql;
EXECUTE registration_export_queue_index_statement;
DEALLOCATE PREPARE registration_export_queue_index_statement;
