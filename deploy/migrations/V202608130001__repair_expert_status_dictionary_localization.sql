-- Repair expert dictionary labels that were persisted as ASCII question marks
-- by an earlier incorrectly decoded execution of the manual seed script.
-- Only system-owned rows with an all-question-mark payload are touched.
SET NAMES utf8mb4;

UPDATE `sys_dict_type`
SET `dict_name` = CASE `dict_code`
    WHEN 'aiadc_expert_status' THEN '专家状态'
    WHEN 'aiadc_expert_initial_status' THEN '专家申请初始状态'
    WHEN 'aiadc_expert_approval_status' THEN '专家审批状态'
END
WHERE `dict_code` IN (
    'aiadc_expert_status',
    'aiadc_expert_initial_status',
    'aiadc_expert_approval_status'
)
  AND `is_system` = 1
  AND `deleted` = 0
  AND `dict_name` = REPEAT('?', CHAR_LENGTH(`dict_name`));

UPDATE `sys_dict_item` AS item
JOIN `sys_dict_type` AS type
  ON type.`id` = item.`dict_type_id`
 AND type.`deleted` = 0
SET item.`item_label` = CASE
    WHEN type.`dict_code` = 'aiadc_expert_status' AND item.`item_value` = 'active' THEN '启用'
    WHEN type.`dict_code` = 'aiadc_expert_status' AND item.`item_value` = 'inactive' THEN '停用'
    WHEN type.`dict_code` = 'aiadc_expert_initial_status' AND item.`item_value` = 'inactive' THEN '停用'
    WHEN type.`dict_code` = 'aiadc_expert_approval_status' AND item.`item_value` = 'PENDING' THEN '待处理'
    WHEN type.`dict_code` = 'aiadc_expert_approval_status' AND item.`item_value` = 'RUNNING' THEN '审批中'
    WHEN type.`dict_code` = 'aiadc_expert_approval_status' AND item.`item_value` = 'APPROVED' THEN '已通过'
    WHEN type.`dict_code` = 'aiadc_expert_approval_status' AND item.`item_value` = 'REJECTED' THEN '已拒绝'
END
WHERE type.`dict_code` IN (
    'aiadc_expert_status',
    'aiadc_expert_initial_status',
    'aiadc_expert_approval_status'
)
  AND item.`deleted` = 0
  AND item.`item_label` = REPEAT('?', CHAR_LENGTH(item.`item_label`))
  AND (
      (type.`dict_code` = 'aiadc_expert_status' AND item.`item_value` IN ('active', 'inactive'))
      OR (type.`dict_code` = 'aiadc_expert_initial_status' AND item.`item_value` = 'inactive')
      OR (type.`dict_code` = 'aiadc_expert_approval_status' AND item.`item_value` IN ('PENDING', 'RUNNING', 'APPROVED', 'REJECTED'))
  );
