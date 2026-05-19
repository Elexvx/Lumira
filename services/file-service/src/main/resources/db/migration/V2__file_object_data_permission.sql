ALTER TABLE `file_object`
  ADD COLUMN `department_id` bigint DEFAULT NULL AFTER `uploaded_by_name`,
  ADD KEY `idx_file_object_department` (`tenant_id`,`department_id`,`deleted`);
