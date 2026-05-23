delete from sys_role_permission
where permission_key like 'site:%';

delete from sys_permission
where permission_key like 'site:%'
   or permission_group = 'site';

delete from sys_menu
where menu_code like 'site.%'
   or path = '/site'
   or path like '/site/%'
   or component = 'redirect:/site/settings'
   or component like '@/pages/site/%';

drop table if exists site_carousel_item;
drop table if exists site_form_submission;
drop table if exists site_form;
drop table if exists site_page_version;
drop table if exists site_page;
drop table if exists site_content;
drop table if exists site_content_category;
drop table if exists site_navigation;
drop table if exists site_site;
