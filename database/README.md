# 数据库初始化口径

当前生产运行以 `legendary-server` 聚合服务为主，数据库初始化以 `services/system-service/src/main/resources/db/migration` 下的 Flyway 迁移为准。

`database/saas.sql` 仅作为全量结构参考样本，不能直接作为生产初始化脚本使用。空库部署、容器部署和升级流程都应通过服务启动时的 Flyway 迁移执行。

`file-service`、`message-service`、`plugin-service` 下的领域迁移保留给未来拆分服务独立运行时使用；当前聚合运行的最终结构已经合并进 system-service 的主基线，并通过后续迁移补齐旧库清理。

验收口径：

- 空库部署：先启动 MySQL/Redis/Nacos，再启动后端服务，由 Flyway 自动建表和补数据。
- 手动核查：对比 `services/system-service/src/main/resources/db/migration/*.sql` 与目标库 `flyway_schema_history`。
- 灾备恢复：优先使用 `deploy/backup-platform.sh` 生成的数据库备份恢复，而不是重新导入 `database/saas.sql`。
