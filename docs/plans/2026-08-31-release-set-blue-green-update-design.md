# Lumira Release Set 蓝绿更新实施设计

日期：2026-08-31

## 目标与边界

本轮将单机 Docker Compose 在线更新从“Server 蓝绿 + 其他组件重启”升级为一个受签名、可核对、可回滚的 Release Set。Release Set 同时绑定 Frontend、Server、Async、Job Executor、Migrator、数据库兼容范围以及 Event、Session、Permission Snapshot、Plugin API 协议。updater 仍是宿主机控制面；业务数据库只保存管理端任务审计，不保存签名私钥，也不成为部署状态的唯一事实来源。

本实施不推送代码、不部署、不连接生产数据库、不生成或写入真实私钥。数据库恢复仍是独立灾难恢复流程，普通应用回滚不得自动恢复数据库备份。

## 当前基线与风险

旧协议允许后端把完整 Manifest 原样提交给 updater；Server 有蓝绿槽位，但本地 Frontend 是单实例，Worker 通过停止、删除、重建更新；状态文件没有 fsync/目录 fsync、进程启动身份和单调 epoch；维护状态依赖任务 `updated_at` 的 30 秒租约；健康检查主要覆盖 HTTP health/version。

主要风险是调用方篡改 Manifest、同一 releaseId 漂移、UI/Worker 版本混用、Worker 在处理中被杀、数据库向前迁移后旧应用不可读、崩溃恢复误认成功，以及关键阶段 Agent 失联后错误 fail-open。

## Release Manifest v3 与信任边界

生产调用方只提交 `releaseId`。updater 用 HTTPS allowlist 模板解析 `${releaseId}.envelope.json`，拒绝 URL 凭据、非 443、跨主机跳转、私网/保留地址解析、超限响应、错误 Content-Type、路径穿越、过期 Release 和不允许的 channel。

Envelope 使用 Ed25519 对 base64url `payload` 的原始字节签名，包含固定 algorithm 和 keyId。updater 从宿主机 trust-store 加载公钥，执行 key allowlist、payload 大小、SHA-256 摘要和签名校验，再解析 JSON。生产默认关闭 inline Manifest；仅测试通过显式开关启用。私钥只允许来自 CI secret，不能进入镜像、仓库、日志或业务数据库。

Manifest v3 必须给出完整 40 位 commit、不可变 releaseId、所有组件的 digest-pinned image、数据库读范围和 migration/rollback mode、Event 读写范围、Session/Permission Snapshot/Plugin API 读写版本、Frontend 管理模式、Drain/rollback 窗口和迁移期运行模式。

## 状态机与持久化

宿主机 `.update-state.json` schema v3 保存 `operationEpoch`、`status`、`currentRelease`、`previousRelease`、`candidateRelease`、rollback deadline、完整 image/compatibility 集合。写入使用同目录临时文件、文件 fsync、原子 rename 和目录 fsync。

锁文件绑定 taskId、operationEpoch、PID、进程启动身份和 host boot identity。每次操作递增 epoch；旧进程、复用 PID 或旧锁不能覆盖新状态。启动恢复会读取持久化任务和状态，重新取得 fence，并按实际组件进行对账；只有所有组件 identity、image、健康、active slot 和数据库版本一致时才允许 HEALTHY。部分一致返回 PARTIALLY_DEPLOYED，关键不确定返回 DEGRADED。

安装顺序为：Preflight → Backup → Pull → expand migration → 启动并验证 inactive UI/Server → Worker compatibility → Async quiesce/drain/replace/verify/resume → Job quiesce/drain/replace/verify/resume → Nginx 同时切换 UI/API → 公网验证 → 排空旧 Server → 持久化 current/previous Release Set。切流前失败恢复 Worker 并删除候选；切流后失败执行 Release Set 回滚。

## Frontend 蓝绿

Compose 提供 `lumira-ui-blue` 和 `lumira-ui-green`，Nginx 生成 upstream 同时绑定 Server 与 Frontend。UI 镜像导出 `__version.json`，包含 releaseId/commit；候选 UI 在切流前启动并校验，旧 UI 保留到 rollback deadline。`external-managed` 没有原子部署/回滚回调时，完整自动更新被 Preflight 阻断。

## Worker Drain

Async 和 Job Executor 提供只允许内部 Token 的 `/internal/runtime/quiesce`、`drain-status`、`resume`、`version` 和 `health`。Token 必须独立于管理员 token，长度至少 24 字节，常量时间比较。quiesce 停止获取新工作；已取得 lease 的任务继续完成。drain-status 暴露 acceptingNewWork、inflight、unacked、oldest age 和 safeToStop。updater 只有在 safeToStop 后才替换容器，随后核对 serviceName、releaseId、commit、generation 和 Event Schema 再 resume；超时则中止并恢复旧 Worker。

## 数据库兼容与迁移门禁

在线链只允许 expand migration。新增 migration 必须声明 phase、rollback、compatible-readers 和 cleanup-after 元数据；静态检查拒绝 DROP、RENAME、TRUNCATE、ALTER/MODIFY/CHANGE COLUMN 等破坏性模式。`V202608310001` 只增加 nullable 审计字段，旧槽位仍可读写。

安装 Preflight 验证当前数据库版本落入目标 Release 可读范围；回滚 Preflight 验证 previous Release 可读当前数据库版本，并同时验证 Event、Session、Permission Snapshot、Plugin API 和 Frontend 可回滚性。普通回滚只回退应用和组件；需要数据库 restore 的 Release 必须阻断快速回滚。

cleanup/contract migration 必须在至少两个稳定 Release 之后单独规划，在确认旧读者、旧 Worker 和回滚窗口全部退出后执行，不与 expand migration 混在一个在线发布中。

## 维护模式

任务持久化 `maintenance_mode` 与 reason。NORMAL 不拦截；WRITE_DRAIN、READ_ONLY、FULL_MAINTENANCE 在服务端写入过滤器阻止非安全 HTTP 方法，更新控制端点保留给原有 RBAC 保护的管理员恢复路径。非关键阶段失联在租约到期后可回到 NORMAL；MIGRATING/SWITCHING_TRAFFIC 等关键阶段失败持久化 READ_ONLY，必须由恢复对账或后续成功任务解除，不能靠一次短暂网络恢复自动清除。

## Readiness、Smoke 与可观测性

组件控制接口已经提供 identity、健康和 Drain 数据；完整业务 Readiness 仍需在 Server 内部端点聚合数据库 rollback-only 写探测、Redis、Session、Permission Snapshot、Event、Outbox、Plugin Registry、文件存储及内部依赖。正式 stable channel 若未配置认证 Smoke 凭据，应由 Preflight 阻断。公网 Smoke 不得增加匿名后门，应使用生产配置注入的专用低权限账号覆盖登录、刷新、允许/拒绝接口、安全写探测、Async 和审计链路。

日志不得输出 Token、私钥、完整数据库 URL、密码、Cookie、Authorization、Session 或完整 Manifest payload。任务日志保留 releaseId、phase、slot、epoch、keyId、digest、耗时和脱敏失败摘要，并限制条数和单条长度。

## CI/CD

Release 构建必须先运行 Maven、Frontend lint/typecheck/test/build、updater/state/签名测试、migration policy、Compose config 和真实 Docker 演练，再构建五个独立镜像并生成 SBOM/provenance。正式 tag 生成不可变 releaseId；若 release 已存在必须失败；解析 registry digest 后再生成 Manifest；tag 发布必须要求 CI secret 中的 Ed25519 私钥并上传 envelope。continuous/main 可作为明确的非正式测试 channel，但不能被生产 updater allowlist 接受。

## 回滚与人工恢复

自动回滚只在 deadline 内、previous Release Set 完整、数据库和所有协议兼容、旧 UI/Server/Worker 可验证时开放。超窗、数据库不可读、签名/状态不一致或实际组件混版时返回 blocker，并保留 DEGRADED/READ_ONLY。人工恢复先冻结写入，保存 `.update-state.json`、锁、任务日志、Compose/Nginx 配置和数据库版本证据，再决定完成候选、回退应用或进入独立数据库灾难恢复。

## 验收路径

最小本地验收包括：签名正确/篡改/未知 key/错误 algorithm/超限；releaseId URL 安全；旧状态迁移、原子写中断和 epoch fence；组件不一致不得 HEALTHY；安装/回滚兼容 blocker；生产 inline Manifest 拒绝；UI/API Compose 双槽；Worker Drain 单元测试；migration policy/baseline；Java 模块测试；Frontend lint/typecheck/test/build；真实 Docker 更新、切流、Worker 排空、回滚和刷新后任务恢复。

无法在无生产凭据和无生产环境的本地工作区证明的项目必须明确列入发布前人工检查，不得写成已完成。
