# 数据库初始化入口

`sql/saas.sql` 是 Lumira 当前的全量初始化脚本，用于全新空库部署。服务端默认关闭 Flyway，部署时应先让 MySQL 在初始化空数据卷时导入该脚本，或在启动后端前手动导入该脚本。

该脚本也包含 XXL-JOB Admin 运行所需的 `xxl_job_*` 调度表和 `lumira-server` 执行器分组。部署时不要再单独维护一份 XXL-JOB 建表脚本，否则容易出现后端 executor 注册失败或调度中心启动后缺表的问题。

推荐路径：

- Docker Compose 部署：`deploy/docker-compose.yml` 和 `deploy/docker-compose.prod.yml` 会将 `sql/saas.sql` 挂载到 MySQL 的 `/docker-entrypoint-initdb.d/001-saas.sql`，空数据卷首次启动时自动导入。
- 手动部署：先创建空库，再导入 `sql/saas.sql`，确认表结构完成后启动后端服务。
- 灾备恢复：优先使用 `deploy/backup-platform.sh` 生成的数据库备份恢复；`sql/saas.sql` 只用于全新初始化，不用于覆盖已有业务数据。

注意：MySQL 只会在数据目录为空时执行 `/docker-entrypoint-initdb.d` 下的脚本；已有数据卷不会重复导入。
