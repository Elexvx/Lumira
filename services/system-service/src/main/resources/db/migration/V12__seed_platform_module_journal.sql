insert into platform_module_definition (
    module_code,
    module_name,
    module_type,
    lifecycle_status,
    source_type,
    description,
    owner_service,
    admin_route_path,
    api_prefixes,
    permission_keys,
    builtin,
    sort_no,
    created_by,
    updated_by,
    deleted
)
select
    'journal',
    '期刊场景',
    'SCENE',
    'PLANNED',
    'DATABASE',
    '用于验证投稿、内容处理、录用和内容发布的第一个数据库注册场景模块。',
    'system-service',
    '/journal',
    '/api/v1/journal/**',
    'journal:view
journal:submission:view
journal:submission:review',
    0,
    100,
    1,
    1,
    0
where not exists (
    select 1
    from platform_module_definition
    where module_code = 'journal'
      and deleted = 0
);

update platform_module_definition
set source_type = 'DATABASE',
    lifecycle_status = 'PLANNED',
    description = '用于验证投稿、内容处理、录用和内容发布的第一个数据库注册场景模块。',
    owner_service = 'system-service',
    admin_route_path = '/journal',
    api_prefixes = '/api/v1/journal/**',
    permission_keys = 'journal:view
journal:submission:view
journal:submission:review',
    builtin = 0,
    sort_no = 100,
    updated_by = 1,
    updated_at = now()
where module_code = 'journal'
  and deleted = 0;

insert into platform_module_dependency (
    module_code,
    dependency_module_code,
    sort_no,
    created_by,
    updated_by,
    deleted
)
select 'journal', seed.dependency_module_code, seed.sort_no, 1, 1, 0
from (
    select 'form' dependency_module_code, 1 sort_no
    union all select 'submission', 2
    union all select 'file', 3
    union all select 'message', 4
    union all select 'site', 5
) seed
where not exists (
    select 1
    from platform_module_dependency d
    where d.module_code = 'journal'
      and d.dependency_module_code = seed.dependency_module_code
      and d.deleted = 0
);
