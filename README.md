# legendary-invention

企业级多租户 SaaS 平台第一轮工程底座仓库。

## 仓库结构

- `frontend/`：Ant Design Pro + React 18 + TypeScript + Umi Max 前端骨架。
- `backend/`：Spring Boot 3 + Java 21 + MyBatis Plus + Security + Redis + Flyway 后端骨架。
- `docs/`：架构与实施规范文档。

## 快速启动

### 前端

```bash
cd frontend
pnpm install
pnpm dev
```

### 后端

```bash
cd backend
mvn spring-boot:run
```

详细说明请见 `docs/11-bootstrap-setup.md`。
