# 数据库初始化口径

当前项目以各服务 `db/migration` 下的 Flyway 迁移作为生产初始化的唯一来源。

`database/saas.sql` 仅作为全量结构参考样本，不能直接作为生产初始化脚本使用。空库部署、容器部署和升级流程都应通过服务启动时的 Flyway 迁移执行，确保 system/file/message/plugin/localization 等服务的独立迁移都能进入数据库版本表。

验收口径：

- 空库部署：先启动 MySQL/Redis/Nacos，再启动各后端服务，由 Flyway 自动建表和补数据。
- 手动核查：对比 `services/*/src/main/resources/db/migration/*.sql` 与目标库 `flyway_schema_history`。
- 灾备恢复：优先使用 `deploy/backup-platform.sh` 生成的数据库备份恢复，而不是重新导入 `database/saas.sql`。
