delete from sys_role_permission
where permission_key in ('system:module:view', 'system:module:create', 'system:module:validate');

delete from sys_permission
where permission_key in ('system:module:view', 'system:module:create', 'system:module:validate');

delete from sys_menu
where menu_code = 'settings.modules'
   or path = '/settings/modules'
   or component = '@/pages/settings/modules';

drop table if exists platform_module_dependency;
drop table if exists platform_module_definition;
