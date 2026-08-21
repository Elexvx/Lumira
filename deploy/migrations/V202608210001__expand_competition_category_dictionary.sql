INSERT INTO `sys_dict_item`
    (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'INNOVATION', '创新赛', 10, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPLICATION', '应用赛', 20, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'AI_APPLICATION', 'AI 应用赛', 30, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ALGORITHM', '算法赛', 40, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DATA_SCIENCE', '数据科学赛', 50, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ROBOTICS', '机器人赛', 60, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CREATIVE_DESIGN', '创意设计赛', 70, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ENTREPRENEURSHIP', '创业赛', 80, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CHALLENGE', '挑战赛', 90, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SKILLS', '技能赛', 100, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SPECIAL', '专项赛', 110, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INVITATIONAL', '邀请赛', 120, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '其他', 130, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
