-- Move built-in storage-space records and legacy bucket migration out of Java code.
UPDATE `file_object`
SET `bucket`='local', `updated_by`=1, `updated_by_uuid`='00000000-0000-0000-0000-000000000000', `updated_at`=NOW()
WHERE `bucket`='system_public' AND `deleted`=0;

UPDATE `file_storage_space`
SET `deleted`=1, `updated_by`=1, `updated_by_uuid`='00000000-0000-0000-0000-000000000000', `updated_at`=NOW()
WHERE `storage_key`='system_public';

INSERT INTO `file_storage_space` (
    `title`, `storage_key`, `provider`, `root_path`, `bucket_name`, `endpoint`, `region`,
    `access_key_id`, `access_key_secret`, `rename_strategy`, `max_file_size_mb`, `allowed_mime_types`,
    `default_flag`, `retain_file_on_record_delete`, `anonymous_access_allowed`, `status`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
VALUES
    ('用户上传文件', 'local', 'LOCAL', 'storage/uploads/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 1, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('下载中心', 'download_center', 'LOCAL', 'storage/uploads/download_center/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 100, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('AI 聊天附件', 'ai_chat', 'LOCAL', 'storage/uploads/ai_chat/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 0, 0, 0, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('头像文件', 'avatar', 'LOCAL', 'storage/uploads/avatar/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 10, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('Support feedback images', 'support_feedback', 'LOCAL', 'storage/uploads/support_feedback/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0)
ON DUPLICATE KEY UPDATE
    `title`=VALUES(`title`), `provider`=VALUES(`provider`), `root_path`=VALUES(`root_path`),
    `rename_strategy`=VALUES(`rename_strategy`), `max_file_size_mb`=VALUES(`max_file_size_mb`),
    `allowed_mime_types`=VALUES(`allowed_mime_types`), `default_flag`=VALUES(`default_flag`),
    `retain_file_on_record_delete`=VALUES(`retain_file_on_record_delete`),
    `anonymous_access_allowed`=VALUES(`anonymous_access_allowed`), `status`=VALUES(`status`), `deleted`=0;
