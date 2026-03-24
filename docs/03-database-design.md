# Ant Design Pro 大型 SaaS 系统数据库设计与基础表结构规范

## 1. 文档定位

本规范用于统一大型 SaaS 系统的数据建模思想、命名规则、基础字段、租户隔离、审计追踪、索引策略和核心表结构。

它服务的是整套平台级底座，而不是某个单独业务模块。

## 2. 总体原则

- 统一基础字段。
- 租户隔离优先。
- 读写路径清晰。
- 查询优先设计。
- 可审计、可恢复、可演进。

## 3. 命名规范

- 表名统一小写下划线。
- 平台级表建议使用 `sys_` 前缀。
- 租户运营表建议使用 `tenant_` 前缀。
- 审计与日志表建议使用 `audit_` 或 `log_` 前缀。
- 文件类表建议使用 `file_` 或 `resource_` 前缀。
- 消息类表建议使用 `msg_` 或 `notify_` 前缀。
- 业务域表按领域统一前缀，不混用页面名。

字段命名统一建议如下：

- 主键：`id`
- 租户：`tenant_id`
- 创建时间：`created_at`
- 更新时间：`updated_at`
- 创建人：`created_by`
- 更新人：`updated_by`
- 逻辑删除：`is_deleted`
- 版本号：`version`
- 状态：`status`

## 4. 基础字段规范

所有核心表建议统一具备：

- `id`
- `tenant_id`，租户级表必须有
- `status`
- `is_deleted`
- `created_at`
- `updated_at`
- `created_by`
- `updated_by`
- `version`
- 需要排序时补 `sort_order`
- 需要说明时补 `remark`

主键建议使用 `bigint` 的雪花 ID 或号段 ID 方案，避免直接用 UUID 做聚簇主键。

## 5. 多租户数据分层

### 5.1 平台级公共数据

例如全国地区、平台字典模板、平台功能开关、平台公告模板等。

### 5.2 租户级数据

例如租户信息、套餐、域名、租户配置、租户文件空间、租户消息模板、租户业务数据等。

### 5.3 租户内组织级数据

例如员工、部门、岗位、角色绑定、数据范围、审批记录等。

## 6. 主键与唯一性

- 主键不建议依赖数据库自增作为长期方案。
- 业务编码和主键必须分离。
- 常见唯一约束应优先设计为 `(tenant_id, code)` 这种租户内唯一。
- 平台级唯一项可以使用全局唯一，例如租户编码、域名等。

## 7. 索引规范

- 索引围绕真实查询路径设计。
- 高频列表通常要包含 `tenant_id`、状态字段、时间字段。
- 日志类表重点考虑 `tenant_id + created_at`、`trace_id`、`user_id`。
- 严禁无索引模糊查询和深分页扫表。

## 8. 逻辑删除与审计

- 大多数核心业务表建议逻辑删除。
- 逻辑删除字段必须参与查询条件和索引设计。
- 关键业务变更需要操作审计日志。
- 审计分为表内基础审计和独立审计日志两层。

## 9. 核心表建议

### 9.1 租户中心

- `tenant_info`
- `tenant_package`
- `tenant_domain`
- `tenant_quota`

### 9.2 用户与组织

- `sys_user`
- `sys_user_tenant`
- `sys_user_tenant_profile`
- `sys_department`
- `sys_position`

### 9.3 权限中心

- `sys_role`
- `sys_menu`
- `sys_user_role`
- `sys_role_menu`
- `sys_data_scope_rule`

### 9.4 字典与配置

- `sys_dict_type`
- `sys_dict_item`
- `sys_config`

### 9.5 文件与消息

- `file_object`
- `file_share_record`
- `msg_template`
- `msg_message`

### 9.6 任务与审计

- `task_job`
- `audit_operate_log`
- `audit_login_log`
- `audit_api_log`

## 10. 分库分表与演进预留

- 第一阶段不建议过早上分库分表。
- 主键必须避免强依赖单库自增。
- 大日志表、消息表、任务表、文件表要预留归档和冷热分离空间。
- 租户独立库要保留后续演进接口。

## 11. 开发硬约束

- 新增租户级表必须先判断是否要带 `tenant_id`。
- 新增高频列表必须先设计索引。
- 新增配置表必须明确审计、加密和作用域。
- 新增日志表必须明确归档策略。
- 所有关联表必须定义联合唯一约束。
