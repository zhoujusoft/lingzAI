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

启动前请按实际厂商需求填写 `deploy/release.env` 中的模型参数。
当前配置口径为：

- 模型表维护：`baseUrl`、`path`、`modelName`
- 配置文件维护：`model.qwen.*`、`model.vllm.*` 对应的运行参数

## 发布流程

若你要使用预构建镜像发布，请在仓库根目录执行：

```bash
./deploy/manage.sh release
```

发布版本文件为：`deploy/release.env`。
该文件中仅保留仍在配置文件中的模型运行参数，不再按 `qwen/vllm profile` 切换。

`deploy/release.env` 中的 `IMAGE_TAG` 现仅维护基础版本号 `x.y.z`。
默认不指定平台时，构建/发布产物直接使用该版本号作为镜像 tag，例如：

- `IMAGE_TAG=1.4.2` -> 默认构建 `linux/amd64`，镜像 tag 为 `1.4.2`

当显式指定平台时，脚本会自动把平台后缀追加到最终镜像 tag：

```bash
./deploy/manage.sh release --platform linux/arm64
```

对应最终镜像 tag 为：

- `IMAGE_TAG=1.4.2` + `--platform linux/arm64` -> `1.4.2-arm64`
- `IMAGE_TAG=1.4.2` + `--platform linux/riscv64` -> `1.4.2-riscv64`

若需分步发布：

```bash
./deploy/manage.sh release-prepare
./deploy/manage.sh release-publish --platform linux/arm64
```

## 统一构建（本地）

若你要一次性构建前后端产物（不推送镜像），可在仓库根目录执行：

```bash
./deploy/manage.sh build
```

若你只构建后端并希望自动处理 `target` 目录权限问题（例如 `backend.jar is read-only`），推荐使用：

```bash
./deploy/manage.sh fix-build --backend-only
```

说明：

- `fix-build` 会先检查 `core/target`、`backend/target` 的可写性
- 检测到权限异常时会尝试执行 `sudo chown -R $USER:$USER core/target backend/target`
- 然后再进入正常构建流程，减少容器/宿主混合构建导致的只读报错

默认会生成：

- 后端：`backend/target/backend.jar`
- 前端：`frontend/dist/`

可选参数：

```bash
./deploy/manage.sh build --backend-only
./deploy/manage.sh build --frontend-only
./deploy/manage.sh build --with-tests
./deploy/manage.sh build --skip-frontend-install
```

## 本地构建 Docker 镜像

若你需要一次性构建前后端 Docker 镜像（仅本地构建，不推送仓库），可执行：

```bash
./deploy/manage.sh build-images
```

对应脚本关系：

- 统一入口命令：`./deploy/manage.sh build-images`
- 实际执行脚本：`deploy/scripts/build-images.sh`
- 发版流程脚本：`./deploy/manage.sh release`（内部调用 `deploy/scripts/release-prepare.sh` + `deploy/scripts/build-and-push-images.sh`）

默认读取 `deploy/release.env` 中的 `REGISTRY`、`IMAGE_TAG`、`FRONTEND_IMAGE_NAME`、`BACKEND_IMAGE_NAME` 作为镜像名与标签。
`IMAGE_TAG` 仅表示基础版本，目标平台通过命令参数决定：

- 未指定 `--platform` -> `linux/amd64`，镜像 tag 直接使用 `IMAGE_TAG`
- `--platform linux/arm64` -> 镜像 tag 自动变为 `IMAGE_TAG-arm64`
- `--platform linux/<arch>` -> 镜像 tag 自动变为 `IMAGE_TAG-<arch>`

也可指定其他配置文件：

```bash
./deploy/manage.sh build-images deploy/release.env
```

如需构建后直接推送（需提前自行 `docker login`）：

```bash
./deploy/manage.sh build-images --push
```

说明：

- `--push` 仅负责推送镜像，不会自动修改 `deploy/release.env` 中的 `IMAGE_TAG`
- `--platform` 只影响本次构建/发布的目标平台与最终镜像 tag，不会回写 `deploy/release.env`

如需在构建前自动升级 `IMAGE_TAG`（patch +1）再推送，可执行：

```bash
./deploy/manage.sh build-images --auto-bump --push
```

例如：

```bash
# release.env 中假设 IMAGE_TAG=1.4.2
./deploy/manage.sh build-images --platform linux/arm64 --push
# 实际推送 tag: 1.4.2-arm64

./deploy/manage.sh build-images --platform linux/riscv64 --push
# 实际推送 tag: 1.4.2-riscv64
```

### 打包 arm64（当前推荐方式）

若你当前要打包 `arm64` 镜像，请直接维护基础版本号，例如：

```bash
# deploy/release.env
IMAGE_TAG=1.4.2
```

然后在命令上显式传入平台：

```bash
./deploy/manage.sh build-images --platform linux/arm64 deploy/release.env
```

若要在构建完成后直接推送镜像仓库：

```bash
./deploy/manage.sh build-images --platform linux/arm64 --push deploy/release.env
```

若要走完整 release 流程：

```bash
./deploy/manage.sh release --platform linux/arm64 deploy/release.env
```

说明：

- `deploy/release.env` 中仍然只写基础版本，例如 `IMAGE_TAG=1.4.2`
- 当传入 `--platform linux/arm64` 时，最终镜像 tag 会自动变成 `1.4.2-arm64`
- 默认不传 `--platform` 时，仍按 `linux/amd64` 构建，最终镜像 tag 仍是 `1.4.2`

构建后可用以下命令检查镜像是否为 `arm64`：

```bash
# 本地镜像
docker inspect 125.75.152.167:5001/lingzhou-frontend:1.4.2-arm64 --format '{{.Os}}/{{.Architecture}}'
docker inspect 125.75.152.167:5001/lingzhou-backend:1.4.2-arm64 --format '{{.Os}}/{{.Architecture}}'

# 仓库镜像
docker buildx imagetools inspect 125.75.152.167:5001/lingzhou-frontend:1.4.2-arm64
docker buildx imagetools inspect 125.75.152.167:5001/lingzhou-backend:1.4.2-arm64
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
