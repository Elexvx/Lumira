# 持久化边界治理

持久化边界是后端分层中隔离业务编排和数据库访问的规则。Lumira 允许后端写数据库，但数据库读写必须收口到 Repository、Mapper、DAO、Persistence Adapter、Read Model Repository 或 Batch Repository。Controller、Application Service、Domain Service、AI Tool Runtime 和 Job Handler 不应直接拼接写库 SQL，也不应直接调用低层数据库写操作。

## 标准调用链

```text
Controller
  -> Application Service
  -> Domain Service / Policy
  -> Repository Interface
  -> Repository Implementation / Mapper
  -> Database
```

Controller 只负责协议适配、参数接收和调用应用服务，禁止直接访问数据库、Mapper 或 Repository 实现。Internal Controller 和 frontend-facing API service 也遵守同一规则。

Application Service 负责权限校验、业务编排、事务边界、审计、事件发布和调用 Repository。它可以依赖 Repository interface，但禁止直接调用 `jdbcTemplate.update(...)`、`jdbcTemplate.batchUpdate(...)`、`MyBatisQueryOperations.update(...)`，也禁止直接拼接 `insert`、`update`、`delete` 写库 SQL。

Domain Service / Policy 负责领域规则和业务判断，禁止直接写库。需要持久化时，由 Application Service 编排 Repository 完成。

Repository 负责数据读取、数据写入、封装 SQL 或 Mapper。Repository 不负责业务审计，不做复杂业务判断，也不把 SQL 语义暴露给上层方法名。

AI Tool Runtime 和 Job Handler 禁止直接写业务表。它们如需跨模块写操作，必须走 Application Service、Internal API、Domain Event 或 Outbox，由目标业务模块完成持久化。

## 允许直接 SQL 的位置

- migration
- repository
- mapper
- DAO
- persistence adapter
- read model repository
- batch repository
- test fixture

## 禁止直接 SQL 的位置

- controller
- app service
- domain service
- AI tool runtime
- job handler
- internal controller
- frontend-facing API service

## 跨模块写操作

跨模块写操作必须走 Application Service、Internal API、Domain Event 或 Outbox。调用方不能 import 其他模块的 mapper/entity，也不能直接写其他模块业务表。需要读模型时，由拥有方提供 API、事件投影或明确归属的 read model repository。

## Repository 命名

Repository 方法必须表达业务语义，例如 `createTeam`、`updateTeamProfile`、`softDeleteTeam`、`addOwner`、`findActiveMember`、`transferOwner`、`createInvite`、`consumeInviteQuota`、`createPendingJoinRequest`、`approveJoinRequest`、`rejectJoinRequest`。

禁止使用暴露 SQL 语义的命名，例如 `executeSql`、`rawUpdate`、`updateBySql`、`insertBySql`、`queryForObjectForTeam`、`doJdbcUpdate`。

## Team 样板

Team 模块是本规则的样板模块。`TeamAppService` 和 `TeamInviteService` 保留事务、权限、参数校验、业务编排和审计；`TeamRepository`、`TeamMemberRepository`、`TeamInviteRepository`、`TeamJoinRequestRepository` 及其 JDBC 实现负责 SQL 和数据库写入。

Team 的未来事件可以围绕这些语义预留：`team.created`、`team.updated`、`team.deleted`、`team.member.joined`、`team.member.removed`、`team.owner.transferred`、`team.invite.created`、`team.join-request.approved`、`team.join-request.rejected`。
