-- Keep existing databases aligned with the sensitive-word policy dictionary
-- already present in the fresh-schema baseline.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('sys_sensitive_word_action', '敏感词动作', 'ENABLED', 1, 'Sensitive word action and default order', 0, 0, 0),
    ('sys_sensitive_word_blocking_action', '敏感词阻断动作', 'ENABLED', 1, 'Action treated as blocking', 0, 0, 0),
    ('sys_sensitive_word_default_category', '敏感词默认分类', 'ENABLED', 1, 'Default category for manual entries', 0, 0, 0),
    ('sys_sensitive_word_import_category', '敏感词导入分类', 'ENABLED', 1, 'Category for imported entries', 0, 0, 0),
    ('sys_sensitive_word_default_severity', '敏感词默认级别', 'ENABLED', 1, 'Default severity for sensitive words', 0, 0, 0),
    ('sys_sensitive_word_severity', '敏感词级别优先级', 'ENABLED', 1, 'Database-owned matching priority', 0, 0, 0)
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `is_system`=VALUES(`is_system`), `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'BLOCK', '阻断', 10, 'ENABLED', '默认敏感词动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_action' AND `deleted`=0
UNION ALL SELECT `id`, 'LOG_ONLY', '仅记录', 20, 'ENABLED', '敏感词动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_action' AND `deleted`=0
UNION ALL SELECT `id`, 'BLOCK', '阻断', 10, 'ENABLED', '产生阻断结果的动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_blocking_action' AND `deleted`=0
UNION ALL SELECT `id`, 'DEFAULT', '默认', 10, 'ENABLED', '手工敏感词默认分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_default_category' AND `deleted`=0
UNION ALL SELECT `id`, 'IMPORTED', '导入', 10, 'ENABLED', '导入敏感词分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_import_category' AND `deleted`=0
UNION ALL SELECT `id`, 'MEDIUM', '中', 10, 'ENABLED', '敏感词默认级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_default_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'LOW', '低', 10, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'MEDIUM', '中', 20, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'HIGH', '高', 30, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'CRITICAL', '严重', 40, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`), `status`=VALUES(`status`), `remark`=VALUES(`remark`), `deleted`=0;
