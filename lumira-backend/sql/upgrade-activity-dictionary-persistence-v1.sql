-- Move activity locale, lifecycle status, and public visibility rules out of Java code.
INSERT INTO `sys_dict_type`
    (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_activity_locale', '活动语言', 'ENABLED', 1, 'Activity locale and default order', 0, 0, 0),
    ('aiadc_activity_status', '活动状态', 'ENABLED', 1, 'Activity status and default order', 0, 0, 0),
    ('aiadc_activity_public_status', '活动公开状态', 'ENABLED', 1, 'Activity statuses visible to public queries', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item`
    (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'zh', '中文', 10, 'ENABLED', '活动默认语言', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'en', 'English', 20, 'ENABLED', 'Activity locale', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'draft', '草稿', 10, 'ENABLED', '活动默认状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 20, 'ENABLED', '活动状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 10, 'ENABLED', '公开查询可见状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_public_status' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
