-- Move project locale, lifecycle status, rating, defaults, and filter wildcard out of Java code.
INSERT INTO `sys_dict_type`
    (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_project_locale', '项目语言', 'ENABLED', 1, 'Project locale and default order', 0, 0, 0),
    ('aiadc_project_status', '项目状态', 'ENABLED', 1, 'Project status and default order', 0, 0, 0),
    ('aiadc_project_rating', '项目评级', 'ENABLED', 1, 'Project rating and default order', 0, 0, 0),
    ('aiadc_project_filter_all', '项目全部筛选标记', 'ENABLED', 1, 'Project query wildcard value', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item`
    (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'zh', '中文', 10, 'ENABLED', '项目默认语言', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'en', 'English', 20, 'ENABLED', 'Project locale', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'draft', '草稿', 10, 'ENABLED', '项目默认状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 20, 'ENABLED', '项目状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'popular', '热门', 10, 'ENABLED', '项目默认评级', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'excellent', '优秀', 20, 'ENABLED', '项目评级', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'new', '最新', 30, 'ENABLED', '项目评级', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'all', '全部', 10, 'ENABLED', '项目查询全部筛选标记', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_filter_all' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
