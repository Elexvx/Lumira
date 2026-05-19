SET @file_object_department_column_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'file_object'
      AND column_name = 'department_id'
);

SET @file_object_department_column_sql := IF(
    @file_object_department_column_exists = 0,
    'ALTER TABLE `file_object` ADD COLUMN `department_id` bigint DEFAULT NULL AFTER `uploaded_by_name`',
    'SELECT 1'
);
PREPARE file_object_department_column_stmt FROM @file_object_department_column_sql;
EXECUTE file_object_department_column_stmt;
DEALLOCATE PREPARE file_object_department_column_stmt;

SET @file_object_department_index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'file_object'
      AND index_name = 'idx_file_object_department'
);

SET @file_object_department_index_sql := IF(
    @file_object_department_index_exists = 0,
    'ALTER TABLE `file_object` ADD KEY `idx_file_object_department` (`tenant_id`,`department_id`,`deleted`)',
    'SELECT 1'
);
PREPARE file_object_department_index_stmt FROM @file_object_department_index_sql;
EXECUTE file_object_department_index_stmt;
DEALLOCATE PREPARE file_object_department_index_stmt;
