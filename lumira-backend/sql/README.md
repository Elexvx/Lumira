# 数据库初始化口径

当前生产运行以 `lumira-server` 聚合服务为主，推荐数据库初始化以 `services/lumira-system/src/main/resources/db/migration` 下的 Flyway 迁移为准。

`sql/saas.sql` 是面向开源用户和手动安装场景的全量初始化脚本，可直接导入空库。该脚本会写入已折叠迁移对应的 `flyway_schema_history` 记录，因此后续启动服务时 Flyway 可以从未折叠的后续增量继续执行。

`file-service`、`message-service`、`plugin-service` 下的领域迁移保留给未来拆分服务独立运行时使用；当前聚合运行的最终结构已经合并进 system-service 的主基线，并通过后续迁移补齐旧库清理。

验收口径：

- 空库部署：先启动 MySQL/Redis/Nacos，再启动后端服务，由 Flyway 自动建表和补数据。
- 手动导入：先创建空库，再导入 `sql/saas.sql`，随后启动后端服务补跑后续 Flyway 增量。
- 手动核查：对比 `services/lumira-system/src/main/resources/db/migration/*.sql` 与目标库 `flyway_schema_history`，确认已折叠版本存在且后续版本按需执行。
- 灾备恢复：优先使用 `deploy/backup-platform.sh` 生成的数据库备份恢复；`sql/saas.sql` 只用于全新初始化，不用于覆盖已有业务数据。
