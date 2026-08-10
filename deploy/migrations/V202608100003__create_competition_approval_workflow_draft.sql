-- Create the first competition approval workflow as a non-impacting draft.
-- Publishing remains an explicit administrator action in system settings.

INSERT INTO `workflow_definition` (
    `business_type`, `name`, `status`, `version_no`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT 'COMPETITION_APPROVAL', '赛事审批流程', 'DRAFT', 1,
       0, '00000000-0000-0000-0000-000000000000',
       0, '00000000-0000-0000-0000-000000000000', 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `workflow_definition`
    WHERE `business_type` = 'COMPETITION_APPROVAL'
      AND `deleted` = 0
);

INSERT INTO `workflow_node` (
    `definition_id`, `node_key`, `node_type`, `name`, `position_x`, `position_y`, `assignment_type`,
    `approver_user_ids_json`, `approver_role_ids_json`, `approval_mode`, `config_json`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT definition_record.`id`, seed.`node_key`, seed.`node_type`, seed.`name`,
       seed.`position_x`, seed.`position_y`, seed.`assignment_type`,
       JSON_ARRAY(),
       CASE WHEN seed.`node_key` = 'review'
            THEN JSON_ARRAY(COALESCE(admin_role.`id`, 1001))
            ELSE JSON_ARRAY()
       END,
       'ALL', JSON_OBJECT(),
       0, '00000000-0000-0000-0000-000000000000',
       0, '00000000-0000-0000-0000-000000000000', 0
FROM `workflow_definition` AS definition_record
JOIN (
    SELECT 'start' AS `node_key`, 'START' AS `node_type`, '开始' AS `name`,
           80 AS `position_x`, 140 AS `position_y`, NULL AS `assignment_type`
    UNION ALL
    SELECT 'review', 'APPROVAL', '管理员审批', 340, 140, 'ROLE'
    UNION ALL
    SELECT 'end', 'END', '结束', 620, 140, NULL
) AS seed
LEFT JOIN `sys_role` AS admin_role
  ON admin_role.`role_code` = 'ADMIN'
 AND admin_role.`deleted` = 0
WHERE definition_record.`business_type` = 'COMPETITION_APPROVAL'
  AND definition_record.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_at` = `workflow_node`.`updated_at`;

INSERT INTO `workflow_edge` (
    `definition_id`, `edge_key`, `source_node_key`, `target_node_key`,
    `condition_expression`, `sort_order`, `config_json`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT definition_record.`id`, seed.`edge_key`, seed.`source_key`, seed.`target_key`,
       NULL, seed.`sort_order`, JSON_OBJECT(),
       0, '00000000-0000-0000-0000-000000000000',
       0, '00000000-0000-0000-0000-000000000000', 0
FROM `workflow_definition` AS definition_record
JOIN (
    SELECT 'start-review' AS `edge_key`, 'start' AS `source_key`, 'review' AS `target_key`, 1 AS `sort_order`
    UNION ALL
    SELECT 'review-end', 'review', 'end', 2
) AS seed
WHERE definition_record.`business_type` = 'COMPETITION_APPROVAL'
  AND definition_record.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_at` = `workflow_edge`.`updated_at`;
