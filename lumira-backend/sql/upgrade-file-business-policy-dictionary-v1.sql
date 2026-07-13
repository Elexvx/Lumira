

-- Database-owned file service providers, strategies, preview rules, and runtime defaults.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('file_storage_provider', '文件存储提供商', 'ENABLED', 1, 'File service storage providers', 0, 0, 0),
    ('file_rename_strategy', '文件重命名策略', 'ENABLED', 1, 'File service rename strategies', 0, 0, 0),
    ('file_storage_status', '文件存储状态', 'ENABLED', 1, 'File service storage statuses', 0, 0, 0),
    ('file_preview_extension', '文件预览扩展名规则', 'ENABLED', 1, 'item_value=extension, item_label=preview mode', 0, 0, 0),
    ('file_preview_content_type', '文件预览 MIME 规则', 'ENABLED', 1, 'item_value=MIME, item_label=preview mode, remark=EXACT/PREFIX', 0, 0, 0),
    ('file_runtime_default', '文件服务运行默认值', 'ENABLED', 1, 'item_value=setting key, item_label=setting value', 0, 0, 0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`), `status`='ENABLED', `is_system`=1,
    `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'LOCAL', 'Local storage', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'ALIYUN_OSS', '阿里云 OSS', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'TENCENT_COS', '腾讯云 COS', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'APPEND_RANDOM_ID', '追加随机标识', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'RANDOM_STRING', '随机字符串', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'KEEP_ORIGINAL', '保留原名', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'ENABLED', '启用', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_status'
UNION ALL SELECT `id`, 'DISABLED', '停用', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_status'
UNION ALL SELECT `id`, 'png', 'IMAGE', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'jpg', 'IMAGE', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'jpeg', 'IMAGE', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'gif', 'IMAGE', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'bmp', 'IMAGE', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'ico', 'IMAGE', 60, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'pdf', 'PDF', 70, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'txt', 'TEXT', 80, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'md', 'TEXT', 90, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'csv', 'TEXT', 100, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'json', 'TEXT', 110, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'xml', 'TEXT', 120, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'image/', 'IMAGE', 10, 'ENABLED', 'PREFIX', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'application/pdf', 'PDF', 20, 'ENABLED', 'EXACT', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'text/', 'TEXT', 30, 'ENABLED', 'PREFIX', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'STORAGE_PROVIDER', 'LOCAL', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'STORAGE_KEY', 'local', 15, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'ROOT_PATH', 'storage/uploads/', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'RENAME_STRATEGY', 'APPEND_RANDOM_ID', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'MAX_FILE_SIZE_MB', '20', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'ALLOWED_MIME_TYPES', '*', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'DOCUMENT_CATEGORY', '我的文件', 60, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'IMAGE_CATEGORY', '图片', 70, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'UNSUPPORTED_PREVIEW_MODE', 'UNSUPPORTED', 80, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'STORAGE_STATUS', 'ENABLED', 90, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`),
    `status`='ENABLED', `remark`=VALUES(`remark`), `deleted`=0;
