# 第一轮工程底座初始化说明

## 前端目录说明（frontend/src）

- `layouts/`：三类布局壳层（Basic/User/Blank）及顶部区域组件。
- `pages/`：只放架构验证所需占位页面（登录、首页、系统占位、异常页）。
- `services/`：按领域拆分 API 服务，统一依赖 `services/common/request.ts`。
- `auth/`：Token、登录态恢复、登出清理。
- `tenant/`：租户上下文管理与租户切换。
- `responsive/`：断点与响应式策略。
- `cache/`：本地缓存封装与租户维度 key 组合。
- `hooks/`：`useResponsive`、`usePermission`、`useTenantContext`。
- `constants/enums/types`：常量、错误码、接口模型。

## 后端目录说明（backend/src/main/java/com/legendary/invention/saas）

- `common/`：统一返回体、错误码、异常、常量、公共工具。
- `infrastructure/`：配置、安全、租户、日志、trace、redis、db、storage 基础设施。
- `modules/`：按业务域拆分模块，每个模块分 `controller/app/domain/mapper/entity/dto/vo/convert`。

## 本轮已完成内容

1. 前后端工程初始化，保证具备本地启动入口。
2. 前端统一请求层（API 前缀、Token、租户 Header、错误码拦截、失效跳转、文件流预留）。
3. 前端三类布局与基础路由。
4. 后端统一响应结构、错误码、全局异常处理。
5. Spring Security、JWT 过滤器、当前用户上下文骨架。
6. TenantContext + TenantFilter 及 trace/requestId 透传。
7. Redis 配置与缓存封装模板。
8. Flyway 初始化脚本，落地第一阶段核心底座表。
9. file/task/audit 模块骨架与占位接口。

## 下一轮推荐开发顺序

1. 认证闭环（登录、签发/刷新 token、Redis 会话管理）。
2. 多租户绑定与租户切换鉴权链路。
3. RBAC 权限模型（菜单、角色、用户角色绑定）。
4. 字典与配置中心。
5. 文件中心（上传、预签名、下载、审计）。
6. 任务中心（任务注册、触发、执行日志）。

## 本地运行方式

### 前端

```bash
cd frontend
pnpm install
pnpm dev
```

默认访问 `http://localhost:8000`。

### 后端

```bash
mvn -pl backend -am spring-boot:run
```

默认访问：
- 健康检查：`http://localhost:8080/api/health`
- OpenAPI：`http://localhost:8080/swagger-ui.html`

### 网关

```bash
mvn -pl gateway-service -am spring-boot:run
```

默认访问：
- 网关：`http://localhost:8081`

## 环境变量样例

- 前端：`frontend/.env.example`
- 后端：`backend/.env.example`

关键变量：数据库连接、Redis 地址、JWT 秘钥、API 前缀、请求超时。
