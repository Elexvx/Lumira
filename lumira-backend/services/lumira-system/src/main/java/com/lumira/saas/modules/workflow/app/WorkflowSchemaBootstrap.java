package com.lumira.saas.modules.workflow.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowSchemaBootstrap {
    private static final Logger log = LoggerFactory.getLogger(WorkflowSchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public WorkflowSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        try {
            createTables();
            addExpertColumns();
            seedPermissions();
            seedDefaultExpertWorkflow();
            seedActivationConfig();
        } catch (RuntimeException exception) {
            log.warn("Workflow schema bootstrap failed: {}", exception.getMessage(), exception);
            throw exception;
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                create table if not exists workflow_definition (
                  id bigint not null auto_increment,
                  business_type varchar(64) not null,
                  name varchar(128) not null,
                  status varchar(32) not null default 'DRAFT',
                  version_no int not null default 1,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  unique key uk_workflow_definition_business (business_type, deleted),
                  key idx_workflow_definition_status (status, deleted)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists workflow_node (
                  id bigint not null auto_increment,
                  definition_id bigint not null,
                  node_key varchar(64) not null,
                  node_type varchar(32) not null,
                  name varchar(128) not null,
                  position_x int default 0,
                  position_y int default 0,
                  assignment_type varchar(32) default null,
                  approver_user_ids_json json default null,
                  approver_role_ids_json json default null,
                  approval_mode varchar(16) not null default 'ALL',
                  config_json json default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  unique key uk_workflow_node_key (definition_id, node_key, deleted),
                  key idx_workflow_node_definition (definition_id, deleted)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists workflow_edge (
                  id bigint not null auto_increment,
                  definition_id bigint not null,
                  edge_key varchar(64) not null,
                  source_node_key varchar(64) not null,
                  target_node_key varchar(64) not null,
                  condition_expression varchar(255) default null,
                  sort_order int not null default 100,
                  config_json json default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  unique key uk_workflow_edge_key (definition_id, edge_key, deleted),
                  key idx_workflow_edge_source (definition_id, source_node_key, deleted)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists workflow_instance (
                  id bigint not null auto_increment,
                  definition_id bigint not null,
                  definition_version_no int not null,
                  business_type varchar(64) not null,
                  business_id bigint not null,
                  business_uuid varchar(64) default null,
                  business_title varchar(255) default null,
                  status varchar(32) not null,
                  current_node_key varchar(64) default null,
                  snapshot_json json not null,
                  variables_json json default null,
                  applicant_user_id bigint default null,
                  applicant_user_uuid varchar(64) default null,
                  completed_at datetime default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  key idx_workflow_instance_business (business_type, business_id, deleted),
                  key idx_workflow_instance_status (status, deleted, updated_at)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists workflow_task (
                  id bigint not null auto_increment,
                  instance_id bigint not null,
                  node_key varchar(64) not null,
                  node_name varchar(128) not null,
                  approval_mode varchar(16) not null default 'ALL',
                  status varchar(32) not null,
                  approver_user_id bigint default null,
                  approver_role_id bigint default null,
                  completed_by bigint default null,
                  completed_by_uuid varchar(64) default null,
                  completed_by_name varchar(128) default null,
                  completed_at datetime default null,
                  comment varchar(500) default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  key idx_workflow_task_user (approver_user_id, status, deleted, created_at),
                  key idx_workflow_task_role (approver_role_id, status, deleted, created_at),
                  key idx_workflow_task_instance (instance_id, node_key, status, deleted)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists workflow_action_log (
                  id bigint not null auto_increment,
                  instance_id bigint not null,
                  task_id bigint default null,
                  action_type varchar(32) not null,
                  node_key varchar(64) default null,
                  node_name varchar(128) default null,
                  operator_user_id bigint default null,
                  operator_user_uuid varchar(64) default null,
                  operator_username varchar(128) default null,
                  comment varchar(500) default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  key idx_workflow_action_instance (instance_id, created_at, id)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_account_activation_token (
                  id bigint not null auto_increment,
                  token_hash char(64) not null,
                  user_id bigint not null,
                  expert_id bigint default null,
                  expires_at datetime not null,
                  consumed_at datetime default null,
                  created_by bigint default 0,
                  created_at datetime not null default current_timestamp,
                  updated_by bigint default 0,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint not null default 0,
                  primary key (id),
                  unique key uk_account_activation_token_hash (token_hash),
                  key idx_account_activation_user (user_id, consumed_at, deleted),
                  key idx_account_activation_expires (expires_at, consumed_at, deleted)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci
                """);
    }

    private void addExpertColumns() {
        addColumnIfMissing("aiadc_expert", "approval_status", "alter table aiadc_expert add column approval_status varchar(32) not null default 'APPROVED' after status");
        addColumnIfMissing("aiadc_expert", "approval_instance_id", "alter table aiadc_expert add column approval_instance_id bigint default null after approval_status");
        addColumnIfMissing("aiadc_expert", "approved_by", "alter table aiadc_expert add column approved_by bigint default null after approval_instance_id");
        addColumnIfMissing("aiadc_expert", "approved_at", "alter table aiadc_expert add column approved_at datetime default null after approved_by");
        addIndexIfMissing("aiadc_expert", "idx_aiadc_expert_approval", "alter table aiadc_expert add index idx_aiadc_expert_approval (approval_status, deleted, updated_at)");
    }

    private void seedPermissions() {
        jdbcTemplate.update("""
                insert into sys_permission (permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
                values
                    ('workflow:view', 'View workflow', 'workflow', 'CORE', null, 0, 0, 0),
                    ('workflow:config', 'Configure workflow', 'workflow', 'CORE', null, 0, 0, 0),
                    ('workflow:approve', 'Approve workflow', 'workflow', 'CORE', null, 0, 0, 0)
                on duplicate key update
                    permission_name = values(permission_name),
                    permission_group = values(permission_group),
                    source_type = values(source_type),
                    plugin_code = values(plugin_code),
                    updated_by = values(updated_by),
                    deleted = 0
                """);
        Long adminRoleId = adminRoleId();
        if (adminRoleId != null) {
            for (String permission : new String[]{"workflow:view", "workflow:config", "workflow:approve"}) {
                jdbcTemplate.update("""
                        insert into sys_role_permission (role_id, permission_key, created_by, updated_by, deleted)
                        values (?, ?, 0, 0, 0)
                        on duplicate key update deleted = 0, updated_by = 0, updated_at = current_timestamp
                        """, adminRoleId, permission);
            }
        }
    }

    private void seedDefaultExpertWorkflow() {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from workflow_definition where business_type = 'EXPERT_APPLICATION' and deleted = 0",
                Long.class
        );
        if (count != null && count > 0) {
            return;
        }
        Long adminRoleId = adminRoleId();
        jdbcTemplate.update("""
                insert into workflow_definition (business_type, name, status, version_no, created_by, updated_by, deleted)
                values ('EXPERT_APPLICATION', 'Expert application approval', 'ACTIVE', 1, 0, 0, 0)
                """);
        Long definitionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("""
                insert into workflow_node (
                    definition_id, node_key, node_type, name, position_x, position_y, assignment_type,
                    approver_user_ids_json, approver_role_ids_json, approval_mode, config_json,
                    created_by, updated_by, deleted
                ) values
                    (?, 'start', 'START', 'Start', 80, 120, null, '[]', '[]', 'ALL', '{}', 0, 0, 0),
                    (?, 'review', 'APPROVAL', 'Admin review', 320, 120, 'ROLE', '[]', ?, 'ALL', '{}', 0, 0, 0),
                    (?, 'end', 'END', 'End', 580, 120, null, '[]', '[]', 'ALL', '{}', 0, 0, 0)
                """, definitionId, definitionId, "[" + (adminRoleId == null ? 1001L : adminRoleId) + "]", definitionId);
        jdbcTemplate.update("""
                insert into workflow_edge (
                    definition_id, edge_key, source_node_key, target_node_key, condition_expression, sort_order,
                    config_json, created_by, updated_by, deleted
                ) values
                    (?, 'start-review', 'start', 'review', null, 10, '{}', 0, 0, 0),
                    (?, 'review-end', 'review', 'end', null, 10, '{}', 0, 0, 0)
                """, definitionId, definitionId);
    }

    private void seedActivationConfig() {
        jdbcTemplate.update("""
                insert into sys_config (
                    config_key, config_name, config_value, config_scope, is_system, remark,
                    created_by, updated_by, deleted
                ) values (
                    'account.activation.url', 'Account activation URL', 'http://localhost:8000/account-activation',
                    'PLATFORM', 1, 'Frontend account activation page URL', 0, 0, 0
                )
                on duplicate key update deleted = 0
                """);
    }

    private Long adminRoleId() {
        try {
            return jdbcTemplate.queryForObject(
                    "select id from sys_role where role_code = 'ADMIN' and deleted = 0 limit 1",
                    Long.class
            );
        } catch (RuntimeException exception) {
            return 1001L;
        }
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?",
                Long.class,
                table,
                column
        );
        if (count == null || count == 0L) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addIndexIfMissing(String table, String index, String ddl) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from information_schema.statistics where table_schema = database() and table_name = ? and index_name = ?",
                Long.class,
                table,
                index
        );
        if (count == null || count == 0L) {
            jdbcTemplate.execute(ddl);
        }
    }
}
