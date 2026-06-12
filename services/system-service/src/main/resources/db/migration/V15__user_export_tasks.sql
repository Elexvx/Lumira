create table if not exists `sys_export_task` (
  `id` bigint not null auto_increment,
  `tenant_id` bigint not null,
  `module_key` varchar(128) not null,
  `status` varchar(32) not null,
  `request_payload` json default null,
  `selected_fields` json default null,
  `total_count` bigint default 0,
  `file_id` bigint default null,
  `file_name` varchar(255) default null,
  `error_message` varchar(1000) default null,
  `created_by` bigint default null,
  `created_at` datetime not null default current_timestamp,
  `started_at` datetime default null,
  `finished_at` datetime default null,
  `deleted` tinyint not null default 0,
  primary key (`id`),
  key `idx_sys_export_task_tenant_creator` (`tenant_id`, `created_by`, `created_at`),
  key `idx_sys_export_task_status` (`status`, `created_at`)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into `sys_permission`
  (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
select 1001, 'system:user:export', '导出用户', 'system', 'CORE', null, 0, current_timestamp, 0, current_timestamp, 0
where not exists (
  select 1 from `sys_permission` where `tenant_id` = 1001 and `permission_key` = 'system:user:export' and `deleted` = 0
);

insert into `sys_role_permission`
  (`tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
select 1001, 2001, 'system:user:export', 0, current_timestamp, 0, current_timestamp, 0
where not exists (
  select 1 from `sys_role_permission`
  where `tenant_id` = 1001 and `role_id` = 2001 and `permission_key` = 'system:user:export' and `deleted` = 0
);
