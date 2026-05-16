INSERT INTO approval_template (
    tenant_id,
    template_name,
    business_type,
    description,
    enabled,
    create_time,
    update_time
)
SELECT
    1001,
    '通用审批',
    'GENERAL_APPROVAL',
    '用于日常事项、临时申请等通用审批场景。',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM approval_template
    WHERE tenant_id = 1001
      AND business_type = 'GENERAL_APPROVAL'
);

UPDATE approval_template t
SET t.enabled = 1,
    t.update_time = NOW()
WHERE t.tenant_id = 1001
  AND t.business_type = 'GENERAL_APPROVAL'
  AND t.enabled = 0
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT id
          FROM approval_template
          WHERE tenant_id = 1001
            AND enabled = 1
          LIMIT 1
      ) enabled_template
  );

INSERT INTO approval_template_node (
    tenant_id,
    template_id,
    node_name,
    sort_order,
    approval_policy,
    approver_type,
    approver_id,
    create_time,
    update_time
)
SELECT
    1001,
    t.id,
    '管理员审批',
    0,
    'ANY_ONE',
    'ROLE',
    2001,
    NOW(),
    NOW()
FROM approval_template t
WHERE t.tenant_id = 1001
  AND t.business_type = 'GENERAL_APPROVAL'
  AND NOT EXISTS (
      SELECT 1
      FROM approval_template_node n
      WHERE n.tenant_id = t.tenant_id
        AND n.template_id = t.id
  );
