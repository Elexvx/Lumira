-- Keep upgraded databases aligned with the localization entities and the
-- fresh-schema contract. Older installations missed the audit UUID columns
-- that are selected by MyBatis even on the public runtime bundle read path.

SET @localization_language_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_language'
    AND column_name = 'created_by_uuid'
);
SET @localization_language_audit_sql = IF(
  @localization_language_audit_exists = 0,
  'ALTER TABLE `sys_localization_language` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_language_audit_statement FROM @localization_language_audit_sql;
EXECUTE localization_language_audit_statement;
DEALLOCATE PREPARE localization_language_audit_statement;

SET @localization_namespace_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_namespace'
    AND column_name = 'created_by_uuid'
);
SET @localization_namespace_audit_sql = IF(
  @localization_namespace_audit_exists = 0,
  'ALTER TABLE `sys_localization_namespace` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_namespace_audit_statement FROM @localization_namespace_audit_sql;
EXECUTE localization_namespace_audit_statement;
DEALLOCATE PREPARE localization_namespace_audit_statement;

SET @localization_entry_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_entry'
    AND column_name = 'created_by_uuid'
);
SET @localization_entry_audit_sql = IF(
  @localization_entry_audit_exists = 0,
  'ALTER TABLE `sys_localization_entry` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_entry_audit_statement FROM @localization_entry_audit_sql;
EXECUTE localization_entry_audit_statement;
DEALLOCATE PREPARE localization_entry_audit_statement;

SET @localization_translation_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_translation'
    AND column_name = 'created_by_uuid'
);
SET @localization_translation_audit_sql = IF(
  @localization_translation_audit_exists = 0,
  'ALTER TABLE `sys_localization_translation` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_translation_audit_statement FROM @localization_translation_audit_sql;
EXECUTE localization_translation_audit_statement;
DEALLOCATE PREPARE localization_translation_audit_statement;

SET @localization_usage_ref_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_usage_ref'
    AND column_name = 'created_by_uuid'
);
SET @localization_usage_ref_audit_sql = IF(
  @localization_usage_ref_audit_exists = 0,
  'ALTER TABLE `sys_localization_usage_ref` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_usage_ref_audit_statement FROM @localization_usage_ref_audit_sql;
EXECUTE localization_usage_ref_audit_statement;
DEALLOCATE PREPARE localization_usage_ref_audit_statement;

SET @localization_release_audit_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_release'
    AND column_name = 'published_by_uuid'
);
SET @localization_release_audit_sql = IF(
  @localization_release_audit_exists = 0,
  'ALTER TABLE `sys_localization_release` ADD COLUMN `published_by_uuid` char(36) DEFAULT NULL AFTER `published_by`, ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`, ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
  'SELECT 1'
);
PREPARE localization_release_audit_statement FROM @localization_release_audit_sql;
EXECUTE localization_release_audit_statement;
DEALLOCATE PREPARE localization_release_audit_statement;

SET @localization_language_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_language'
    AND index_name = 'idx_sys_localization_language_creator_uuid'
);
SET @localization_language_audit_index_sql = IF(
  @localization_language_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_language` ADD INDEX `idx_sys_localization_language_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
  'SELECT 1'
);
PREPARE localization_language_audit_index_statement FROM @localization_language_audit_index_sql;
EXECUTE localization_language_audit_index_statement;
DEALLOCATE PREPARE localization_language_audit_index_statement;

SET @localization_namespace_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_namespace'
    AND index_name = 'idx_sys_localization_namespace_creator_uuid'
);
SET @localization_namespace_audit_index_sql = IF(
  @localization_namespace_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_namespace` ADD INDEX `idx_sys_localization_namespace_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
  'SELECT 1'
);
PREPARE localization_namespace_audit_index_statement FROM @localization_namespace_audit_index_sql;
EXECUTE localization_namespace_audit_index_statement;
DEALLOCATE PREPARE localization_namespace_audit_index_statement;

SET @localization_entry_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_entry'
    AND index_name = 'idx_sys_localization_entry_creator_uuid'
);
SET @localization_entry_audit_index_sql = IF(
  @localization_entry_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_entry` ADD INDEX `idx_sys_localization_entry_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
  'SELECT 1'
);
PREPARE localization_entry_audit_index_statement FROM @localization_entry_audit_index_sql;
EXECUTE localization_entry_audit_index_statement;
DEALLOCATE PREPARE localization_entry_audit_index_statement;

SET @localization_translation_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_translation'
    AND index_name = 'idx_sys_localization_translation_creator_uuid'
);
SET @localization_translation_audit_index_sql = IF(
  @localization_translation_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_translation` ADD INDEX `idx_sys_localization_translation_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
  'SELECT 1'
);
PREPARE localization_translation_audit_index_statement FROM @localization_translation_audit_index_sql;
EXECUTE localization_translation_audit_index_statement;
DEALLOCATE PREPARE localization_translation_audit_index_statement;

SET @localization_usage_ref_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_usage_ref'
    AND index_name = 'idx_sys_localization_usage_ref_creator_uuid'
);
SET @localization_usage_ref_audit_index_sql = IF(
  @localization_usage_ref_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_usage_ref` ADD INDEX `idx_sys_localization_usage_ref_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
  'SELECT 1'
);
PREPARE localization_usage_ref_audit_index_statement FROM @localization_usage_ref_audit_index_sql;
EXECUTE localization_usage_ref_audit_index_statement;
DEALLOCATE PREPARE localization_usage_ref_audit_index_statement;

SET @localization_release_audit_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_localization_release'
    AND index_name = 'idx_sys_localization_release_publisher_uuid'
);
SET @localization_release_audit_index_sql = IF(
  @localization_release_audit_index_exists = 0,
  'ALTER TABLE `sys_localization_release` ADD INDEX `idx_sys_localization_release_publisher_uuid` (`published_by`,`published_by_uuid`,`published_at`)',
  'SELECT 1'
);
PREPARE localization_release_audit_index_statement FROM @localization_release_audit_index_sql;
EXECUTE localization_release_audit_index_statement;
DEALLOCATE PREPARE localization_release_audit_index_statement;
