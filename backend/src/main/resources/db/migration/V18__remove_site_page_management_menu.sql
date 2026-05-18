update sys_menu
set deleted = 1,
    status = 'DISABLED',
    updated_at = now()
where tenant_id = 1001
  and deleted = 0
  and (
        menu_code = 'site.pages'
     or path = '/site/pages'
     or component = '@/pages/site/pages'
     or menu_code = 'site.forms'
     or path = '/site/forms'
     or component = '@/pages/site/forms'
  );

delete from sys_role_permission
where tenant_id = 1001
  and permission_key in (
      'site:page',
      'site:page:create',
      'site:page:update',
      'site:page:publish',
      'site:form',
      'site:form:create',
      'site:form:update',
      'site:form:delete'
  );

update sys_permission
set deleted = 1,
    updated_at = now()
where tenant_id = 1001
  and deleted = 0
  and permission_key in (
      'site:page',
      'site:page:create',
      'site:page:update',
      'site:page:publish',
      'site:form',
      'site:form:create',
      'site:form:update',
      'site:form:delete'
  );
