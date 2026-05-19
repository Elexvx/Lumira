update sys_menu
set menu_name = '文件管理器', updated_by = 1, updated_at = now()
where tenant_id = 1001
  and menu_code = 'settings.files'
  and deleted = 0;

update sys_menu
set menu_name = '验证管理', updated_by = 1, updated_at = now()
where tenant_id = 1001
  and menu_code = 'settings.verification'
  and deleted = 0;
