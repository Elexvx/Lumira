# 基础设施启动说明

## 当前可直接启动的官方组件

- MySQL：`mysql:8.4`
- Redis：`redis:7.4`
- Nacos：`nacos/nacos-server:v3.2.1`

## 启动方式

```bash
docker compose -f deploy/docker-compose.yml up -d
```

如果你想直接拉起整个平台，可以从仓库根目录运行：

```bash
node scripts/start-platform.mjs
```

## 后续组件

- Sentinel 控制台
- XXL-Job 调度中心
- Seata Server

这三项会按各自官方文档单独部署，避免把未核准的镜像或非官方包直接写入默认 compose。

当前 compose 已预留 XXL-Job 调度中心容器位，首次启动前需要按官方文档导入 `xxl_job` 表结构，并确认 `xuxueli/xxl-job-admin:3.4.0` 镜像可用。
