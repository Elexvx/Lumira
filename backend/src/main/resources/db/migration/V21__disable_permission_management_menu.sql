UPDATE sys_menu
SET status = 'DISABLED',
    updated_by = 0
WHERE menu_code = 'iam.overview'
  AND deleted = 0;
