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
  1001,
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
WHERE NOT EXISTS (
  SELECT 1
  FROM `file_storage_space`
  WHERE `tenant_id` = 1001
    AND `storage_key` = 'ai_knowledge'
);
