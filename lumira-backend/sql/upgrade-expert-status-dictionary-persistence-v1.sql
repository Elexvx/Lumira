-- Move expert status, application initial status, and approval lifecycle out of Java code.
SET NAMES utf8mb4;

INSERT INTO `sys_dict_type`
    (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_expert_status', '专家状态', 'ENABLED', 1, 'Expert status and default order', 0, 0, 0),
    ('aiadc_expert_initial_status', '专家申请初始状态', 'ENABLED', 1, 'Initial status for expert applications', 0, 0, 0),
    ('aiadc_expert_approval_status', '专家审批状态', 'ENABLED', 1, 'Expert approval lifecycle and initial order', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`), `status` = VALUES(`status`), `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`), `updated_by` = VALUES(`updated_by`), `deleted` = 0;

INSERT INTO `sys_dict_item`
    (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'active', '启用', 10, 'ENABLED', '专家默认状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'inactive', '停用', 20, 'ENABLED', '专家状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'inactive', '停用', 10, 'ENABLED', '专家申请初始状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_initial_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PENDING', '待处理', 10, 'ENABLED', '专家审批初始状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'RUNNING', '审批中', 20, 'ENABLED', '专家审批状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPROVED', '已通过', 30, 'ENABLED', '专家审批状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'REJECTED', '已拒绝', 40, 'ENABLED', '专家审批状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`), `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`), `deleted` = 0;
