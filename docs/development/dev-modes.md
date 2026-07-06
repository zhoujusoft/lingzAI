# Devcontainer 启动方式说明

本文档只说明 `lingzhou-agent` 这套 `.devcontainer` 在 Windows + WSL 下的两种模式，并刻意把它们区分开。

## 两种模式对照

| 维度 | 模式 A：IDEA Dev Container | 模式 B：WSL 当前环境模式 |
| --- | --- | --- |
| 主要入口 | IDEA `Create Dev Container and Mount Sources...` | `./.devcontainer/start-dev.sh` |
| 应用运行位置 | `workspace` 容器 | 当前 WSL 环境 |
| Docker 服务 | `workspace/mysql/redis/minio/elasticsearch` | `mysql/redis/minio/elasticsearch` |
| 典型重启命令 | 重新打开或重建 Dev Container | `./.devcontainer/restart-dev.sh` |
| `restart` 会做什么 | 重新连/重建容器环境 | 停止 WSL 前后端，停止中间件，再重新拉起 |
| 适用场景 | 希望开发环境完全容器化 | 希望前后端直接跑在当前 WSL |
| 日志 | `.devcontainer/logs/app.log` `.devcontainer/logs/frontend.log` | `.devcontainer/logs/wsl-backend.log` `.devcontainer/logs/wsl-frontend.log` |

## 模式 A：IDEA Dev Container

特点：

- IDEA 读取 `.devcontainer/devcontainer.json`
- `devcontainer.json` 会启动 `workspace` 服务并自动执行 `bash .devcontainer/scripts/run-workspace-app.sh`
- 后端和前端都跑在 `workspace` 容器里

适合：

- 追求环境一致性
- 希望 IDE、JDK、Node、Maven 都以容器为准

如果你需要让 `workspace` 容器里的用户与宿主机一致，推荐在 `.devcontainer/.env` 中使用：

- `HOST_USER`
- `HOST_UID`
- `HOST_GID`

## 模式 B：WSL 当前环境模式

特点：

- 使用 `.devcontainer/start-dev.sh`
- 只启动 `mysql / redis / minio / elasticsearch`
- 后端 `mvn spring-boot:run` 与前端 `pnpm dev` 直接跑在当前 WSL
- 不启动 `workspace` 容器

前置条件：

- 当前 WSL 已具备 `JDK 17`
- 当前 WSL 已具备 `Maven`
- 当前 WSL 已具备 `Node.js` 与 `pnpm`

常用命令：

```bash
./.devcontainer/start-dev.sh
./.devcontainer/start-dev.sh --rebuild-db
./.devcontainer/stop-dev.sh
./.devcontainer/restart-dev.sh
```

`restart-dev.sh` 的语义是：

1. 停止当前 WSL 里运行的后端和前端
2. 停止 `mysql / redis / minio / elasticsearch`
3. 重新拉起中间件
4. 再在当前 WSL 环境启动后端和前端

## 选择建议

- 想严格遵守 Dev Container 原意，用模式 A。
- 想让前后端直接使用当前 WSL 里的 JDK、Maven、Node、pnpm，用模式 B。

## 不要混用

- 模式 A 运行时，不要再执行 `start-dev.sh`
- 模式 B 运行时，不要保留 `workspace` 容器继续占用 `20050 / 20517 / 20055`
- 不要把 `.devcontainer` 体系和 `deploy/README.dev.md` 的本地开发流程混用
