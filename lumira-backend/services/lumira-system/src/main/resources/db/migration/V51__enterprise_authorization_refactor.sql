create table if not exists iam_subject (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    subject_type varchar(32) not null,
    ref_id bigint not null,
    subject_code varchar(128) null,
    display_name varchar(128) null,
    status varchar(32) not null default 'ENABLED',
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_subject_tenant_type_ref (tenant_id, subject_type, ref_id, deleted)
);

create table if not exists iam_permission (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    permission_key varchar(128) not null,
    resource_code varchar(128) not null,
    action_code varchar(64) not null,
    permission_name varchar(128) not null,
    permission_group varchar(128) null,
    risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 0,
    require_approval tinyint not null default 0,
    data_scope_required tinyint not null default 0,
    source_type varchar(32) not null default 'SYSTEM',
    plugin_code varchar(128) null,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_permission_key (tenant_id, permission_key, deleted)
);

create table if not exists iam_subject_role (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    subject_id bigint not null,
    role_id bigint not null,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_subject_role (tenant_id, subject_id, role_id, deleted)
);

create table if not exists iam_delegation_grant (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    delegator_subject_id bigint not null,
    delegate_subject_id bigint not null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    permission_key varchar(128) null,
    tool_code varchar(128) null,
    scope_type varchar(32) not null default 'SELF',
    max_risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 1,
    require_approval tinyint not null default 0,
    valid_from datetime null,
    expires_at datetime null,
    status varchar(32) not null default 'ENABLED',
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    key idx_delegation_delegate (tenant_id, delegate_subject_id, deleted),
    key idx_delegation_delegator (tenant_id, delegator_subject_id, deleted)
);

create table if not exists ai_employee_tool_grant (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    employee_id bigint not null,
    tool_code varchar(128) not null,
    permission_key varchar(128) null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    permission_mode varchar(32) not null default 'DENY',
    max_risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 1,
    require_approval tinyint not null default 0,
    data_scope_type varchar(32) not null default 'SELF',
    enabled tinyint not null default 1,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_ai_employee_tool_grant (tenant_id, employee_id, tool_code, deleted)
);

create table if not exists ai_employee_tool_grant_dept (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    grant_id bigint not null,
    dept_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_dept (tenant_id, grant_id, deleted)
);

create table if not exists ai_employee_tool_grant_user (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    grant_id bigint not null,
    user_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_user (tenant_id, grant_id, deleted)
);

create table if not exists ai_tool_execution_audit (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    user_id bigint not null,
    employee_id bigint null,
    conversation_id bigint null,
    pending_tool_call_id bigint null,
    tool_code varchar(128) not null,
    permission_key varchar(128) null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    risk_level varchar(32) not null,
    execution_status varchar(32) not null,
    arguments_hash varchar(128) null,
    result_summary varchar(1000) null,
    error_message varchar(1000) null,
    created_at datetime default current_timestamp,
    key idx_ai_tool_execution_audit_tenant_created (tenant_id, created_at),
    key idx_ai_tool_execution_audit_employee (tenant_id, employee_id, created_at)
);

alter table ai_tool_call_plan
    add column arguments_hash varchar(128) null,
    add column authorization_snapshot_json longtext null,
    add column approval_required tinyint not null default 0,
    add column approved_by bigint null,
    add column approved_at datetime null;

insert ignore into iam_subject (
    tenant_id, subject_type, ref_id, subject_code, display_name, status, created_by, updated_by, deleted
)
select 1001, 'HUMAN_USER', u.id, u.username, coalesce(u.nickname, u.username), u.status, 0, 0, 0
from sys_user u
where u.deleted = 0;

insert ignore into iam_subject_role (
    tenant_id, subject_id, role_id, created_by, updated_by, deleted
)
select s.tenant_id, s.id, ur.role_id, 0, 0, 0
from iam_subject s
join sys_user_role ur
 on ur.user_id = s.ref_id
 and ur.tenant_id = s.tenant_id
 and ur.deleted = 0
where s.subject_type = 'HUMAN_USER'
  and s.deleted = 0;
