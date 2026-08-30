UPDATE sys_dict_item
SET parent_item_value = NULL
WHERE parent_item_value IS NOT NULL
  AND TRIM(parent_item_value) = '';
