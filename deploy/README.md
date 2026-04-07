# 部署指南

本目录是容器化部署的统一入口。

## 文档边界

- 快速一体化部署：本文档（`deploy/README.md`）
- 本地开发中间件流程：`deploy/README.dev.md`

## 快速开始

推荐在仓库根目录执行：

```bash
cp deploy/compose-quick/.env.example deploy/compose-quick/.env
./deploy/manage.sh quick-up
```

或直接执行：

```bash
cd deploy/compose-quick
docker compose --env-file .env -f docker-compose.yml up -d
```

## 网络说明

本部署默认使用 Docker 服务名互联（不固定子网 / 不固定 IP）。
服务通过名称互相访问（`mysql`、`redis`、`minio`、`elasticsearch`），可避免常见网段冲突。

前端容器通过 `frontend/nginx.conf` 反向代理 `/api` 到 `backend:5050`，因此浏览器默认直接访问前端端口即可。

也可在仓库根目录执行：

```bash
./deploy/manage.sh quick-up
```

## 迁移根目录旧 `.env`

若你的旧部署配置在仓库根目录 `.env`，可一次性迁移：

```bash
cp ../.env ./.env
```

迁移后请统一维护 `deploy/compose-quick/.env`。

## Quick 部署说明

`compose-quick/docker-compose.yml` 会直接启动：

- `frontend`
- `backend`
- `mysql`
- `redis`
- `minio`
- `elasticsearch`

适合单机快速体验、联调和演示环境。

启动前至少需要填写这些变量：

- `APP_CHAT_QWEN_ONLINE_API_KEY`
- `APP_EMBEDDING_API_KEY`
- `APP_RAG_RERANK_API_KEY`

若三者使用同一个 DashScope Key，也可以填成同一个值。

## 发布流程

若你要使用预构建镜像发布，请在仓库根目录执行：

```bash
./deploy/manage.sh release
```

发布版本文件为：`deploy/release.env`。

若需分步发布：

```bash
./deploy/manage.sh release-prepare
./deploy/manage.sh release-publish
```

## 部署辅助命令

在仓库根目录执行：

```bash
./deploy/manage.sh up
./deploy/manage.sh logs
./deploy/manage.sh down
```

## 数据目录

Compose Quick 默认运行时数据目录为：

```text
deploy/compose-quick/data/
```

该目录属于本地运行状态数据，已在 `.gitignore` 中忽略。

## 本地开发中间件（Dev）

请直接参考：`deploy/README.dev.md`。
