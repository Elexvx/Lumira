SET @dict_structure_column_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_type' AND column_name = 'structure_type'
);
SET @dict_structure_column_sql = IF(
  @dict_structure_column_exists = 0,
  'ALTER TABLE `sys_dict_type` ADD COLUMN `structure_type` varchar(16) NOT NULL DEFAULT ''FLAT'' AFTER `remark`',
  'SELECT 1'
);
PREPARE dict_structure_column_statement FROM @dict_structure_column_sql;
EXECUTE dict_structure_column_statement;
DEALLOCATE PREPARE dict_structure_column_statement;

SET @dict_parent_column_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_item' AND column_name = 'parent_item_value'
);
SET @dict_parent_column_sql = IF(
  @dict_parent_column_exists = 0,
  'ALTER TABLE `sys_dict_item` ADD COLUMN `parent_item_value` varchar(64) DEFAULT NULL AFTER `remark`',
  'SELECT 1'
);
PREPARE dict_parent_column_statement FROM @dict_parent_column_sql;
EXECUTE dict_parent_column_statement;
DEALLOCATE PREPARE dict_parent_column_statement;

SET @dict_level_column_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_item' AND column_name = 'level_no'
);
SET @dict_level_column_sql = IF(
  @dict_level_column_exists = 0,
  'ALTER TABLE `sys_dict_item` ADD COLUMN `level_no` tinyint NOT NULL DEFAULT ''1'' AFTER `parent_item_value`',
  'SELECT 1'
);
PREPARE dict_level_column_statement FROM @dict_level_column_sql;
EXECUTE dict_level_column_statement;
DEALLOCATE PREPARE dict_level_column_statement;

SET @dict_leaf_column_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_item' AND column_name = 'leaf'
);
SET @dict_leaf_column_sql = IF(
  @dict_leaf_column_exists = 0,
  'ALTER TABLE `sys_dict_item` ADD COLUMN `leaf` tinyint NOT NULL DEFAULT ''1'' AFTER `level_no`',
  'SELECT 1'
);
PREPARE dict_leaf_column_statement FROM @dict_leaf_column_sql;
EXECUTE dict_leaf_column_statement;
DEALLOCATE PREPARE dict_leaf_column_statement;

SET @dict_parent_index_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_dict_item'
    AND index_name = 'idx_sys_dict_item_parent'
);
SET @dict_parent_index_sql = IF(
  @dict_parent_index_exists = 0,
  'CREATE INDEX `idx_sys_dict_item_parent` ON `sys_dict_item` (`dict_type_id`,`parent_item_value`,`status`,`deleted`,`sort_no`)',
  'SELECT 1'
);
PREPARE dict_parent_index_statement FROM @dict_parent_index_sql;
EXECUTE dict_parent_index_statement;
DEALLOCATE PREPARE dict_parent_index_statement;

CREATE TABLE IF NOT EXISTS `sys_dictionary_dataset_installation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset_code` varchar(64) NOT NULL,
  `dataset_version` varchar(64) NOT NULL,
  `file_sha256` char(64) NOT NULL,
  `row_count` int NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'INSTALLED',
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dictionary_dataset_code` (`dataset_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
