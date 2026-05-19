create table if not exists file_storage_space (
  id bigint not null auto_increment,
  tenant_id bigint not null,
  title varchar(128) not null,
  storage_key varchar(64) not null,
  provider varchar(32) not null,
  root_path varchar(255) default null,
  bucket_name varchar(128) default null,
  endpoint varchar(255) default null,
  region varchar(128) default null,
  access_key_id varchar(255) default null,
  access_key_secret varchar(512) default null,
  rename_strategy varchar(32) not null default 'APPEND_RANDOM_ID',
  max_file_size_mb int not null default 20,
  allowed_mime_types varchar(1024) not null default '*',
  default_flag tinyint not null default 0,
  retain_file_on_record_delete tinyint not null default 0,
  status varchar(32) not null default 'ENABLED',
  created_by bigint default 0,
  created_at datetime not null default current_timestamp,
  updated_by bigint default 0,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  deleted tinyint not null default 0,
  primary key (id),
  unique key uk_file_storage_space_key (tenant_id, storage_key),
  key idx_file_storage_space_default (tenant_id, default_flag, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

insert into file_storage_space (
  tenant_id, title, storage_key, provider, root_path, rename_strategy, max_file_size_mb,
  allowed_mime_types, default_flag, retain_file_on_record_delete, status, created_by, updated_by, deleted
)
select 1001, 'Local storage', 'local', 'LOCAL', 'storage/uploads/', 'APPEND_RANDOM_ID', 20, '*', 1, 0, 'ENABLED', 1, 1, 0
where not exists (
  select 1 from file_storage_space where tenant_id = 1001 and storage_key = 'local'
);

update file_object
set bucket = 'local'
where tenant_id = 1001
  and deleted = 0
  and (bucket is null or bucket = '');
