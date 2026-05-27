UPDATE sys_dict_item i
JOIN sys_dict_type t ON t.id = i.dict_type_id
SET i.item_label = i.item_value,
    i.remark = CONCAT('Ant Design icon: ', i.item_value),
    i.updated_at = CURRENT_TIMESTAMP
WHERE t.dict_code = 'menu_icon'
  AND t.deleted = 0
  AND i.deleted = 0
  AND (
    i.item_label <> i.item_value
    OR i.remark IS NULL
    OR i.remark NOT LIKE 'Ant Design icon:%'
  );

UPDATE sys_dict_type
SET remark = 'Ant Design 菜单图标原始名称',
    updated_at = CURRENT_TIMESTAMP
WHERE dict_code = 'menu_icon'
  AND deleted = 0;
