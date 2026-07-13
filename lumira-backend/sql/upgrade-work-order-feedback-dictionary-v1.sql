

-- Database-owned work-order status, priority, terminal-state, and upload defaults.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('work_order_feedback_status', '工单反馈状态', 'ENABLED', 1, 'remark=TERMINAL marks handled states', 0, 0, 0),
    ('work_order_feedback_priority', '工单反馈优先级', 'ENABLED', 1, 'Work order feedback priorities', 0, 0, 0),
    ('work_order_feedback_default', '工单反馈默认配置', 'ENABLED', 1, 'item_value=setting key, item_label=setting value', 0, 0, 0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`), `status`='ENABLED', `is_system`=1,
    `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'OPEN', '待处理', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'PROCESSING', '处理中', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'RESOLVED', '已解决', 30, 'ENABLED', 'TERMINAL', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'CLOSED', '已关闭', 40, 'ENABLED', 'TERMINAL', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'LOW', '低', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'NORMAL', '普通', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'HIGH', '高', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'URGENT', '紧急', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'INITIAL_STATUS', 'OPEN', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'DEFAULT_PRIORITY', 'NORMAL', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'UPLOAD_BUCKET', 'support_feedback', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'IMAGE_CATEGORY', '工单反馈', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'IMAGE_REMARK', '工单反馈富文本图片', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`),
    `status`='ENABLED', `remark`=VALUES(`remark`), `deleted`=0;
