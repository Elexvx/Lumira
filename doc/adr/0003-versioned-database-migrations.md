# ADR-0003: 统一全新初始化与存量数据库升级

## Status

Accepted

## Context

Lumira 同时支持全新空库安装和保留业务数据的在线升级。`lumira-backend/sql/saas.sql` 是全新初始化入口，MySQL 只会在空数据卷首次启动时执行它；存量数据库不会再次执行该文件。项目已经具备 Flyway 迁移镜像和蓝绿更新流程，但在线迁移目录未覆盖活动报名表和活动字典，CI 的数据库目标版本也曾固定为旧版本，导致应用代码与生产库结构漂移。

数据库升级必须满足以下约束：

- 全新部署和存量升级最终得到相同的业务表与系统种子。
- 在线迁移仅允许 expand-only 变更，确保旧应用槽位仍可回滚。
- 迁移必须在应用容器启动或切流之前完成，失败时停止发布。
- 数据库凭据只通过部署环境传入迁移容器，不写入仓库或命令参数值。
- 同一迁移可在已经人工补齐结构的数据库上安全执行。

## Decision

1. 保留 `lumira-backend/sql/saas.sql` 作为全新空库的唯一完整初始化入口。
2. 使用 `deploy/migrations/V<version>__<name>.sql` 作为存量数据库的唯一有序在线迁移链，并由 Flyway 表 `lumira_platform_update_schema_history` 记录执行状态。
3. 活动报名持久化以 `V202607190001__activity_registration_persistence.sql` 进入在线迁移链；脚本使用 `CREATE TABLE IF NOT EXISTS` 和字典 upsert，可兼容已经人工修复的生产库。
4. 常规容器部署在启动后端、异步任务或调度执行器前运行迁移；蓝绿更新继续在启动空闲槽位前运行同一迁移镜像。
5. CI 从迁移文件名自动计算最新数据库目标版本，并验证完整初始化与在线迁移都包含活动报名契约，避免目标版本再次手工写死。
6. Flyway 迁移容器必须遵守发布清单给出的目标版本，防止旧应用误执行更高版本迁移。

## Consequences

### Positive

- 新服务器空库安装和旧服务器升级都有明确、可测试的入口。
- 漏表、漏字典会在发布阶段暴露，不再等到用户打开页面才发现。
- 生产库已经存在目标表或字典时，迁移仍可安全登记到 Flyway 历史中。
- 数据库版本与发布清单自动同步，可审计、可重复。

### Negative

- 每次新增持久化变更都必须同时维护完整初始化 SQL 和版本化在线迁移。
- 常规后端部署会增加一次短暂的迁移容器执行过程。
- 收缩性变更仍不能随在线发布自动执行，需要独立维护窗口。

### Neutral

- Flyway 继续由独立迁移容器运行，应用服务自身仍默认关闭 Flyway。
- 自动回滚只回滚应用流量，不反向执行数据库迁移；因此迁移必须保持向后兼容。

## Alternatives Considered

**仅依赖 `saas.sql`**

- 不采用：MySQL 对已有数据卷不会重复执行初始化目录，无法升级存量数据库。

**继续手工执行 `upgrade-*.sql`**

- 不采用：缺少执行记录和发布门禁，容易再次漏执行。

**由每个应用实例启动时执行 Flyway**

- 不采用：蓝绿并行启动会增加迁移竞争和职责混杂；独立迁移容器更容易在切流前失败停止。

## References

- `lumira-backend/sql/README.md`
- `deploy/docker/migrator-entrypoint.sh`
- `bin/lumira-updater.mjs`
- `bin/deploy-container.mjs`
