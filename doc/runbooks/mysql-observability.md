# MySQL 可观测性运行手册

## 覆盖范围

`--observability` 会启动受限的 `mysqld-exporter` 和只启用 textfile collector 的 `backup-metrics-exporter`。Prometheus 采集 MySQL、备份时间、蓝绿应用槽位、Async 与 Job Executor；Grafana 自动加载 `Lumira MySQL and Data Reliability` 仪表盘和数据库告警。

监控容器不发布宿主端口，不挂载宿主根目录，不把数据库密码写入 Compose、命令行、Prometheus 配置或 dashboard。部署工具把 exporter 密码写入权限为 `0600` 的 `deploy/.generated/secrets/mysql-exporter-password`；也可用 `MYSQLD_EXPORTER_PASSWORD_FILE` 指向密钥管理系统提供的普通非符号链接文件。由于 Compose 文件型 secret 保留宿主所有者和 root-only 权限，固定入口脚本只在启动阶段以 root 和最小的读取/降权能力读取 secret；外部库的 CA 与规范化配置会复制到 `noexec,nosuid,nodev` 的内存目录，随后用 `chpst` 永久降权为 UID/GID 65534、清空有效能力，再启动 exporter。

## 账号准备

内置 `--local-mysql` 由 `bin/deploy-container.mjs` 自动创建并轮换 `MYSQLD_EXPORTER_USERNAME`，限制最多 3 个连接。外部或托管 MySQL 必须由 DBA 先创建同名账号，并把密码写入 `MYSQLD_EXPORTER_PASSWORD` 或受控文件。建议权限基线：

```sql
CREATE USER `exporter`@'<monitoring-network>' IDENTIFIED BY '<from-secret-manager>' REQUIRE SSL
  WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO `exporter`@'<monitoring-network>';
GRANT SELECT ON `saas`.* TO `exporter`@'<monitoring-network>';
GRANT SELECT ON `performance_schema`.* TO `exporter`@'<monitoring-network>';
GRANT SELECT ON `sys`.* TO `exporter`@'<monitoring-network>';
```

把 `<monitoring-network>` 收紧到实际私网来源。通过受控数据库会话输入密码，不把真实密码放入 shell 历史、工单或文档。若托管产品权限模型不同，由 DBA 缩减并实测 collector；不要用 `GRANT ALL` 代替。

外部数据库还要设置：

```text
MYSQLD_EXPORTER_ADDRESS=mysql-ha.internal:3306
MYSQLD_EXPORTER_USERNAME=exporter
MYSQLD_EXPORTER_CONFIG_FILE=secrets/mysql-exporter.cnf
MYSQLD_EXPORTER_CA_FILE=secrets/mysql-ca.pem
```

配置文件至少包含：

```ini
[client]
ssl-ca = /run/secrets/mysql_exporter_ca
tls = custom
```

该文件不写入地址、用户名或密码：入口脚本从独立 password secret 导出密码，pinned exporter 从受控 flags 补齐地址和用户名。部署工具要求 `MYSQLD_EXPORTER_ADDRESS` 与 `DB_URL` 的 host:port 完全一致，并只接受上述两个 TLS 指令，防止 my.cnf 覆盖到另一台数据库或关闭校验。驱动会以连接地址作为证书主机名；服务端 TLS 1.2+ 下限由托管数据库控制面强制。应用、审计和备份连接仍必须使用 `VERIFY_IDENTITY`，详见 [mysql-access-and-tls.md](mysql-access-and-tls.md)。

## 启动与验证

```powershell
node bin/start-platform.mjs production --observability
```

部署验收不仅检查 exporter HTTP 存活，还要求 Prometheus 中 `mysql_up{job="mysql"}=1`，并要求 Prometheus、至少一个蓝绿后端槽位、Async、Job Executor 和备份指标采集目标全部在线。若 exporter 账号不存在、密码不匹配或权限不足，部署健康检查会失败，不能把“容器已运行”当成监控可用。

Grafana 默认仅绑定 `127.0.0.1:3001`，Prometheus 默认仅绑定 `127.0.0.1:9090`。通过堡垒机或现有受控反向代理访问，不要直接暴露公网。

## 备份指标

环境模板默认：

```text
BACKUP_METRICS_FILE=deploy/.generated/backup-metrics/lumira-mysql.prom
```

成功备份会原子更新：

- `lumira_mysql_backup_last_success_timestamp_seconds`
- `lumira_mysql_backup_dump_bytes`
- `lumira_mysql_backup_info`

失败备份不会推进成功时间。`lumira-mysql-backup-stale` 据此发现超时；生产仍需把备份上传到独立存储并执行隔离恢复演练，单有时间序列不证明可恢复。

## 指标与告警边界

内置告警覆盖 MySQL 不可达、连接使用率、慢查询增量、锁等待、死锁、复制状态/延迟、备份过期、Hikari 等待/超时以及 Outbox 堆积和死信。

`lumira_mysql_storage_used_ratio` 是外部指标接口：托管 MySQL 应从云监控或经批准的采集器注入实例真实磁盘利用率。当前容器不启用 node-exporter filesystem collector，因为它会把宿主机/容器虚拟盘误报为数据库独占空间。未接入该指标前，磁盘告警属于未完成项；同时使用只读审计的表/索引容量和云数据库磁盘告警。

告警阈值是初始基线。上线后至少收集两周数据，再根据峰值连接、复制延迟、备份窗口、Outbox 正常波动和业务 RPO/RTO 调整。任何调阈值都要保留变更依据，不能通过放宽阈值掩盖持续故障。
