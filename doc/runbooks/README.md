# Lumira MySQL 生产运行手册

本目录把数据库扩容方案落成可执行门禁。当前技术路线是继续使用 MySQL 8.4，先完成高可用、可恢复性、最小权限、TLS、可观测性和容量治理；不要在这些基础能力缺失时用迁移 PostgreSQL 掩盖运维风险。

建议按以下顺序启用：

1. [账户权限、TLS 与密钥](mysql-access-and-tls.md)
2. [备份与隔离恢复](mysql-backup-and-restore.md)
3. [生产只读体检](mysql-production-audit.md)
4. [可观测性与告警](mysql-observability.md)
5. [高可用切换](mysql-high-availability-switch.md)
6. [PITR 恢复](mysql-pitr-recovery.md)
7. [季度恢复演练](mysql-quarterly-recovery-drill.md)
8. [容量、归档与分区](mysql-capacity-and-archival.md)

仓库中的配置和脚本不会自动创建托管数据库实例、对象存储或生产账号。生产完成状态必须同时包含云控制面配置、最近只读体检、备份产物校验、隔离恢复证据和切换演练记录。
