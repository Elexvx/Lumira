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
  'AI 聊天附件',
  'ai_chat',
  'LOCAL',
  'storage/uploads/ai_chat/',
  'APPEND_RANDOM_ID',
  20,
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
    AND `storage_key` = 'ai_chat'
);
