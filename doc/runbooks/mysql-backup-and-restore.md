# MySQL 备份与隔离恢复运行手册

## 定位

`deploy/backup-platform.sh` 生成发布前逻辑备份，`deploy/restore-platform.sh` 在隔离库验证该备份。它们是发布迁移门禁和快速逻辑恢复工具，不替代托管 MySQL 的跨可用区实例、物理快照、连续 binlog 归档或对象锁。

发布器会验证 `manifest.json`、`SHA256SUMS`、`.complete`、库名、创建时间、dump 大小与 SHA-256、表数和 schema 指纹；在执行迁移前还会重新查询目标 `@@server_uuid`。任何证据缺失、过期或来自另一台 MySQL，迁移都必须停止。

## 首次配置

1. 创建独立 `lumira_backup` 和 `lumira_restore` 身份，不使用应用长期账号或共享管理员账号。
2. 把密码通过密钥管理系统注入 `MYSQL_BACKUP_PASSWORD`、`MYSQL_RESTORE_PASSWORD`；不要写入命令行、Git 或备份目录。
3. 外部数据库设置 `MYSQL_SSL_MODE=VERIFY_IDENTITY`，并通过只读挂载提供 `MYSQL_SSL_CA_FILE`。
4. `BACKUP_ROOT` 指向独立持久卷或备份挂载，不能只放在应用主机系统盘。
5. 配置受控的 `BACKUP_UPLOAD_HOOK` 把完成目录上传到加密、版本化、不可变的异地存储。上传程序必须自行校验远端对象和返回非零失败码。
6. 默认 `BACKUP_RETENTION_DAYS=0` 不自动删除。只有远端保留和恢复演练通过后，才批准本地保留窗口。

## 创建并验收备份

凭据已由当前受控会话或服务管理器注入后执行：

```bash
bash deploy/backup-platform.sh
```

成功的最后一行是 `Backup completed: <absolute-directory>`。目录必须包含：

- `manifest.json`：`secretsIncluded=false`，并记录 MySQL 版本、server UUID、GTID/binlog 坐标、数据库版本、表数和 schema 指纹；
- `SHA256SUMS`：每个备份 artifact 的标准 SHA-256；
- `.complete`：只有全部 artifact 完成后才原子发布的完成标记；
- MySQL dump，以及按配置生成的 Redis、文件存储和插件存储 artifact。

不要只根据脚本退出码宣布成功。确认上传副本、校验和、备份成功时间指标和独立存储告警均正常。空数据库默认失败；仅首次创建的明确空库可使用 `BACKUP_ALLOW_EMPTY_DATABASE=1`，生产已有库不得打开该开关。

## 恢复前无写入验证

先验证产物和恢复参数，不连接或修改数据库：

```bash
DRY_RUN=1 \
RESTORE_MODE=isolated \
RESTORE_TARGET_DATABASE=saas_restore_drill \
bash deploy/restore-platform.sh /approved/backup/directory
```

任何 symlink、越界路径、缺失 artifact、大小/SHA 不一致、版本不兼容或目标库不安全都必须失败。

## 隔离恢复

1. 创建与生产隔离的 MySQL 8.4 目标和网络，禁用真实支付、短信、邮件、Webhook、Async、Job Executor 与 Outbox relay。
2. 设置 `RESTORE_MODE=isolated` 和一个不等于生产库名的 `RESTORE_TARGET_DATABASE`。
3. 通过密钥管理系统注入恢复账号；需要恢复 Redis、文件或插件时分别显式开启 `RESTORE_REDIS`、`RESTORE_FILE_STORAGE`、`RESTORE_PLUGIN_STORAGE`。
4. 执行 `bash deploy/restore-platform.sh <backup-directory>`。
5. 脚本通过后，再独立核对表数、schema 指纹、数据库版本、关键业务主键/时间窗口 checksum、Outbox 与消费回执，并进行隔离业务烟测。
6. 记录实际 RPO/RTO、备份 ID、server UUID、验证人和失败项，然后清理临时凭据与隔离资源。

## 生产恢复边界

生产恢复不是常规发布动作。必须先停写并冻结异步副作用，确认应用实际数据库名，取得事故指挥和数据库负责人批准。脚本要求 production 模式、精确目标确认、重建确认和停写确认同时满足；即便如此，也必须先在隔离库完成同一备份的恢复和业务验收。

若需要恢复到逻辑事故前时间点，使用 [PITR 恢复手册](mysql-pitr-recovery.md)，不能只导入最新逻辑 dump 覆盖当前生产库。
