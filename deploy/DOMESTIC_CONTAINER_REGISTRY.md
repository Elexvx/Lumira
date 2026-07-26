# 国内容器镜像托管

主分支镜像默认发布到 GHCR。配置以下 GitHub Actions 变量和密钥后，同一组镜像还会同步到国内容器镜像仓库，并让持续发布清单优先引用国内仓库中的 digest 固定镜像。

## GitHub Actions 配置

仓库变量：

- `CN_REGISTRY`：镜像仓库域名，不要包含 `https://`。阿里云示例：`registry.cn-hangzhou.aliyuncs.com`
- `CN_REGISTRY_NAMESPACE`：已创建的命名空间，例如 `aiadc`

仓库密钥：

- `CN_REGISTRY_USERNAME`：镜像仓库登录用户名
- `CN_REGISTRY_PASSWORD`：镜像仓库固定密码或访问凭证

流水线会保留 GHCR 镜像，并把以下镜像同步到国内仓库：

- `lumira-server`
- `lumira-ui`
- `lumira-async`
- `lumira-job-executor`
- `lumira-migrator`

每个镜像同时发布 `main` 和 `sha-<12位提交号>` 标签。持续发布清单仍使用不可变的 `sha256` digest。

## 生产主机配置

在启用国内仓库变量之前，生产主机必须先完成仓库登录，并允许更新器使用国内镜像前缀：

```bash
docker login registry.cn-hangzhou.aliyuncs.com
```

`deploy/.env` 示例：

```text
PLATFORM_UPDATE_ALLOWED_IMAGE_PREFIXES=ghcr.io/elexvx/lumira/,registry.cn-hangzhou.aliyuncs.com/aiadc/
```

修改后重启 `lumira-updater`，再推送主分支或重新运行镜像流水线。不要把仓库密码写入 Git、`deploy/.env.example` 或发布产物。
