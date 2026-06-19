INSERT INTO `file_storage_space` (
  `tenant_id`,
  `title`,
  `storage_key`,
  `provider`,
  `root_path`,
  `rename_strategy`,
  `max_file_size_mb`,
  `allowed_mime_types`,
  `default_flag`,
  `retain_file_on_record_delete`,
  `status`,
  `created_by`,
  `updated_by`,
  `deleted`
)
SELECT
  t.`id`,
  'AI 知识库文档',
  'ai_knowledge',
  'LOCAL',
  'storage/uploads/ai_knowledge/',
  'APPEND_RANDOM_ID',
  50,
  '*',
  0,
  0,
  'ENABLED',
  1,
  1,
  0
FROM `sys_tenant` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `file_storage_space` s
    WHERE s.`tenant_id` = t.`id`
      AND s.`storage_key` = 'ai_knowledge'
  );
