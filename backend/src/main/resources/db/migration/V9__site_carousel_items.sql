create table if not exists `site_carousel_item` (
  `id` bigint not null auto_increment,
  `tenant_id` bigint not null,
  `site_id` bigint not null,
  `title` varchar(160) not null,
  `subtitle` varchar(500) default null,
  `image_file_id` bigint default null,
  `image_url` varchar(500) default null,
  `link_type` varchar(32) not null default 'NONE',
  `link_target` varchar(500) default null,
  `open_type` varchar(32) not null default 'SELF',
  `sort_order` int not null default 0,
  `status` varchar(32) not null default 'VISIBLE',
  `created_by` bigint not null,
  `created_at` datetime not null default current_timestamp,
  `updated_by` bigint not null,
  `updated_at` datetime not null default current_timestamp on update current_timestamp,
  `deleted` tinyint not null default 0,
  `version` bigint not null default 0,
  primary key (`id`),
  key `idx_site_carousel_sort` (`tenant_id`,`site_id`,`status`,`deleted`,`sort_order`,`id`),
  key `idx_site_carousel_site` (`tenant_id`,`site_id`,`deleted`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

insert into sys_menu (
    tenant_id, parent_id, menu_code, menu_name, menu_type, path, component,
    created_by, updated_by, deleted, icon, sort_no, permission_key, status
)
select 1001, m.id, 'site.carousels', '轮播管理', 'MENU', '/site/carousels', '@/pages/site/carousels',
       1, 1, 0, 'PictureOutlined', 3, 'site:carousel', 'ENABLED'
from sys_menu m
where m.tenant_id = 1001
  and m.menu_code = 'site.root'
  and m.deleted = 0
  and not exists (
    select 1 from sys_menu existing
    where existing.tenant_id = 1001
      and existing.menu_code = 'site.carousels'
      and existing.deleted = 0
  )
limit 1;

update sys_menu
set sort_no = case menu_code
    when 'site.settings' then 1
    when 'site.navigation' then 2
    when 'site.carousels' then 3
    when 'site.pages' then 4
    when 'site.contents' then 5
    when 'site.forms' then 6
    when 'site.submissions' then 7
    else sort_no
  end,
  updated_by = 1,
  updated_at = now()
where tenant_id = 1001
  and deleted = 0
  and menu_code in (
    'site.settings',
    'site.navigation',
    'site.carousels',
    'site.pages',
    'site.contents',
    'site.forms',
    'site.submissions'
  );

insert into sys_permission (
    tenant_id, permission_key, permission_name, permission_group, source_type,
    plugin_code, created_by, updated_by, deleted
)
select 1001, permission_key, permission_name, 'site', 'CORE', null, 1, 1, 0
from (
    select 'site:carousel' permission_key, '查看轮播管理' permission_name
    union all select 'site:carousel:create', '新增轮播'
    union all select 'site:carousel:update', '编辑轮播'
    union all select 'site:carousel:delete', '删除轮播'
) seed
where not exists (
    select 1 from sys_permission p
    where p.tenant_id = 1001
      and p.permission_key = seed.permission_key
      and p.deleted = 0
);

insert into sys_role_permission (
    tenant_id, role_id, permission_key, created_by, updated_by, deleted
)
select 1001, r.id, seed.permission_key, 1, 1, 0
from sys_role r
cross join (
    select 'site:carousel' permission_key
    union all select 'site:carousel:create'
    union all select 'site:carousel:update'
    union all select 'site:carousel:delete'
) seed
where r.tenant_id = 1001
  and r.role_code = 'ADMIN'
  and r.deleted = 0
  and not exists (
    select 1 from sys_role_permission rp
    where rp.tenant_id = 1001
      and rp.role_id = r.id
      and rp.permission_key = seed.permission_key
      and rp.deleted = 0
  );
