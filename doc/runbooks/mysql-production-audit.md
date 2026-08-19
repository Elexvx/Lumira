# MySQL 生产只读体检运行手册

## 目的与边界

`bin/database-production-audit.mjs` 用于采集 Lumira 生产 MySQL 的版本、字符集、时区、PITR 前置参数、连接、InnoDB、锁、慢查询摘要、复制、容量、关键 Outbox/审计/任务表及数据库版本。它只包含固定的 `SELECT`、`SHOW`、`SET SESSION TRANSACTION READ ONLY`、`START TRANSACTION READ ONLY` 和 `COMMIT`，不接受外部 SQL。

该工具不会：修改参数、创建表、清理数据、执行备份、触发故障切换，或证明某个备份可以恢复。表行数是 InnoDB 估算值；增长率必须用两次不同时间的报告计算。

## 专用账号

使用 `lumira_audit` 一类的专用只读账号，不使用应用账号、迁移账号或管理员账号。账号权限、来源网段和 TLS 要求见 [mysql-access-and-tls.md](mysql-access-and-tls.md)。报告中的 `least-privilege-auditor` 门禁必须通过；任何 DML、DDL、`FILE`、`SUPER`、用户/权限管理能力都视为不合格。

## 前置检查

1. 明确目标是生产只读体检，并取得当班负责人批准。
2. 确认客户端到数据库走私网；数据库不应开放公网 3306。
3. 准备 MySQL 8.4 本机客户端，或预先拉取固定镜像 `mysql:8.4`。
4. 将数据库密码放在仅当前运维账号可读的密码文件，或已有受控的生产 env 文件；不要把密码写在命令行或聊天记录。
5. 若使用容器访问 Compose 内部 DNS，先确认正确的 Docker network；不要猜测 network 名称。
6. 选择业务低峰。状态聚合有单条 SELECT 超时保护，但关键表很大时仍可能给存储带来短时读负载。

## 执行

本机 MySQL 8.4 客户端：

```powershell
node bin/database-production-audit.mjs `
  --env-file deploy/.env `
  --password-file C:\secure\lumira-audit.password `
  --client local `
  --ssl-ca C:\secure\mysql-ca.pem `
  --output-dir artifacts\mysql-audit
```

固定 `mysql:8.4` 客户端容器：

```powershell
node bin/database-production-audit.mjs `
  --env-file deploy/.env `
  --password-file C:\secure\lumira-audit.password `
  --client docker `
  --docker-network lumira_default `
  --ssl-ca C:\secure\mysql-ca.pem `
  --output-dir artifacts\mysql-audit
```

`--password-file` 优先于 env 文件中的 `DB_PASSWORD`。脚本只把密码放入子进程环境变量 `MYSQL_PWD`，不会放入 argv、标准输出或报告。容器执行期间仍应限制谁能调用 Docker inspect；完成后容器由 `--rm` 删除。

## 增长基线

第一次报告本身就是基线。至少间隔 24 小时后执行：

```powershell
node bin/database-production-audit.mjs `
  --env-file deploy/.env `
  --password-file C:\secure\lumira-audit.password `
  --client docker `
  --docker-network lumira_default `
  --ssl-ca C:\secure\mysql-ca.pem `
  --baseline artifacts\mysql-audit\mysql-production-audit-BASELINE.json `
  --output-dir artifacts\mysql-audit
```

工具会校验基线的脱敏主机指纹、数据库名和时间，拒绝跨实例或时间倒置的对比。建议每日固定时间采集，至少保留 35 天，以形成周同比基线。

## 门禁

以下任一条件未满足，都不能据此宣布“生产数据库高可用/PITR 已就绪”：

- `session-read-only` 必须通过；否则工具拒绝写报告。
- MySQL 必须处于批准的 8.4 补丁版本。
- JDBC/审计连接为 `VERIFY_IDENTITY`，CA 和主机名校验成功。
- `log_bin=ON`、`gtid_mode=ON`、`binlog_format=ROW`。
- 目标耐久参数为 `sync_binlog=1`、`innodb_flush_log_at_trx_commit=1`；若因压测结果例外，必须有风险批准和补偿措施。
- 审计账号没有危险权限。
- Outbox 死信为 0；非 0 时必须关联故障单并确认幂等重放方案。
- 复制通道存在时，I/O 和 SQL 线程均运行，延迟在业务批准阈值内。
- 报告警告中不能有权限不足、超时或缺失核心采集段；部分报告不能当作验收证据。

## 结果保存

工具生成同名 JSON 和 Markdown。JSON 用于下一次 `--baseline`，Markdown 用于人工复核。两者不包含密码、JDBC URL、数据库用户名、真实主机名、SQL digest 文本或复制源地址，但仍包含容量和运行状态，应按内部运维资料保护。

Linux/macOS 会以 `0600` 创建报告；Windows 依赖父目录 ACL，首次使用前必须确认目录只对授权运维人员开放。

## 失败处理

- 连接失败：检查私网、DNS、证书、Docker network 和账号来源限制，不要临时开放公网或关闭证书校验。
- 权限不足：只补充运行手册列出的最小只读能力，不要授予管理员权限。
- 查询超时：保留部分报告与错误时间，先在低峰重试；不得取消超时后直接扫描超大表。
- 报告疑似含密钥：立即隔离文件并轮换相应凭据。脚本有密钥不落盘断言，但运维仍需执行事件响应。
