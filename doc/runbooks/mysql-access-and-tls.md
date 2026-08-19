# MySQL 账户权限、TLS 与密钥运行手册

## 账户分离

生产至少分离以下身份，不允许应用长期使用 `root` 或数据库管理员账号：

| 账号角色 | 用途 | 允许能力 | 禁止能力 |
| --- | --- | --- | --- |
| `lumira_app` | Lumira 同步业务运行时 | 业务 schema 所需 DML、序列/视图读取 | DDL、用户管理、`FILE`、`SUPER` |
| `lumira_migrator` | 受控发布窗口 | 经过评审的 schema 迁移 | 日常应用连接、长期在线 |
| `lumira_audit` | 生产只读体检 | `SELECT`、`SHOW VIEW`、必要的 `PROCESS`、`REPLICATION CLIENT` | 所有 DML/DDL、导出文件、用户管理 |
| `lumira_backup` | 备份系统 | 备份产品要求的最小只读/元数据能力 | 应用写入、交互登录 |
| `lumira_exporter` | 指标采集 | exporter 所需只读状态权限 | 业务表读取（非必要时）、任何写入 |
| 应急管理员 | 故障处理 | 临时提权、堡垒机/MFA | 应用配置、共享使用、永久令牌 |

具体授权必须由数据库管理员根据托管产品和 MySQL 8.4 实际权限模型生成并复核。不要复制互联网中的 `GRANT ALL` 示例。`lumira_audit` 首选 schema 级 `SELECT`；`PROCESS` 会扩大可见会话范围，只在锁/事务诊断确实需要时授予。

## 账号创建门禁

1. 账号来源限制为应用私网、堡垒机或监控网段，不允许任意来源。
2. 账号名称和用途能从审计系统追溯到负责人。
3. 使用独立随机密钥，不复用 SSH、Redis、JWT 或其他数据库密码。
4. 应用、迁移、备份、审计、监控账号分别存入密钥管理系统。
5. 对人工管理员启用 MFA/堡垒机和临时授权；数据库本身不支持的控制在云 IAM 层完成。
6. 用 `SHOW GRANTS` 人工复核；运行只读审计确认 `least-privilege-auditor` 门禁通过。

## TLS 基线

- 数据库只监听私网，安全组不开放公网 3306。
- 服务端启用 TLS 1.2 或更高版本，禁用弱密码套件和过期证书。
- Lumira JDBC URL 使用 `sslMode=VERIFY_IDENTITY`，不能以 `useSSL=false`、`PREFERRED` 或只加密不校验身份作为生产完成状态。
- 客户端信任明确的 CA，并校验证书中的 DNS 名称与稳定数据库端点一致；不要把 IP 临时替换进 JDBC URL 绕过主机名校验。
- CA 文件、客户端证书和私钥不进入 Git、普通备份或应用镜像。
- 证书到期至少提前 30 天告警；轮换必须覆盖蓝/绿槽位、async/job、审计、备份和监控客户端。

示例形态（不含凭据）：

```text
jdbc:mysql://mysql-ha.internal:3306/saas?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8
```

## 密钥轮换

1. 在密钥管理系统创建新版本，记录轮换工单和回退时间窗。
2. 数据库创建/更新新凭据，但暂不撤销旧凭据。
3. 先更新一个非活动 Lumira 槽位，验证 TLS、登录、业务读写和 Outbox。
4. 更新活动槽位、async/job、备份、审计和 exporter，确认旧凭据连接数归零。
5. 撤销旧凭据并验证无法连接；保留审计证据，不保留明文密码。
6. 若新凭据失败，在旧凭据仍有效且没有数据分叉时回退客户端密钥版本，修复后重新轮换。

密码不得出现在命令行、进程参数、CI 日志或 Markdown 报告中。使用密钥文件时确认其为普通文件而非符号链接，并限制 ACL。

## 定期复核

- 每月：列出账号、来源、最后使用时间和权限差异。
- 每季度：回收无负责人、90 天未使用或临时项目账号。
- 每半年：轮换服务账号密钥并演练 CA/服务端证书轮换。
- 每次发布：确认迁移账号仅在迁移窗口可用，应用账号未获得 DDL。

发现 `ALL PRIVILEGES`、`FILE`、`SUPER`、用户管理、授权转授或审计账号 DML/DDL 时，立即按高风险权限偏差处理；先限制账号，再分析是否有未授权操作。

Exporter 的具体部署、file-backed secret 和验证步骤见 [mysql-observability.md](mysql-observability.md)。
