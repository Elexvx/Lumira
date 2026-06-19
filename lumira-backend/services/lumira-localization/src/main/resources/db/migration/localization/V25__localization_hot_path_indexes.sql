ALTER TABLE `sys_localization_entry`
    ADD INDEX `idx_sys_localization_entry_namespace_deleted_status` (`namespace_id`, `deleted`, `status`, `updated_at`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_localization_translation`
    ADD INDEX `idx_sys_localization_translation_locale_deleted_entry` (`locale_code`, `deleted`, `entry_id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;

ALTER TABLE `sys_localization_namespace`
    ADD INDEX `idx_sys_localization_namespace_deleted_sort` (`deleted`, `sort_no`, `id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
