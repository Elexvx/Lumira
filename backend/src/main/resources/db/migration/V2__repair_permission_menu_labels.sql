update sys_menu
set menu_name = case menu_code
    when 'site.root' then '官网管理'
    when 'site.settings' then '站点设置'
    when 'site.navigation' then '导航管理'
    when 'site.pages' then '页面管理'
    when 'site.contents' then '内容管理'
    when 'site.forms' then '表单管理'
    when 'site.submissions' then '提交记录'
    else menu_name
  end,
  updated_at = current_timestamp
where deleted = 0
  and menu_code in (
    'site.root',
    'site.settings',
    'site.navigation',
    'site.pages',
    'site.contents',
    'site.forms',
    'site.submissions'
  );

update sys_permission
set permission_name = case permission_key
    when 'site:view' then '查看官网管理'
    when 'site:settings' then '查看站点设置'
    when 'site:settings:update' then '编辑站点设置'
    when 'site:navigation' then '查看导航管理'
    when 'site:navigation:create' then '新增导航'
    when 'site:navigation:update' then '编辑导航'
    when 'site:navigation:delete' then '删除导航'
    when 'site:page' then '查看页面管理'
    when 'site:page:create' then '新增页面'
    when 'site:page:update' then '编辑页面'
    when 'site:page:publish' then '发布页面'
    when 'site:content' then '查看内容管理'
    when 'site:content:create' then '新增内容'
    when 'site:content:update' then '编辑内容'
    when 'site:content:publish' then '发布内容'
    when 'site:form' then '查看表单管理'
    when 'site:form:create' then '新增表单'
    when 'site:form:update' then '编辑表单'
    when 'site:form:delete' then '删除表单'
    when 'site:submission' then '查看提交记录'
    when 'site:submission:review' then '审核提交记录'
    else permission_name
  end,
  updated_at = current_timestamp
where deleted = 0
  and permission_key in (
    'site:view',
    'site:settings',
    'site:settings:update',
    'site:navigation',
    'site:navigation:create',
    'site:navigation:update',
    'site:navigation:delete',
    'site:page',
    'site:page:create',
    'site:page:update',
    'site:page:publish',
    'site:content',
    'site:content:create',
    'site:content:update',
    'site:content:publish',
    'site:form',
    'site:form:create',
    'site:form:update',
    'site:form:delete',
    'site:submission',
    'site:submission:review'
  );
