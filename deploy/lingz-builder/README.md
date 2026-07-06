# Lingz 服务器侧镜像构建脚本

本目录用于在服务器上“一键拉代码、打包前后端、构建 Docker 镜像并 push”。

## 准备

服务器需要以下工具：

```bash
git
docker
docker buildx
```

默认 `BACKEND_BUILD_MODE=docker`，后端会在 `maven:3.9-eclipse-temurin-17` 容器里执行 Maven package，宿主机不需要安装 Maven。只有把 `BACKEND_BUILD_MODE=host` 时，才需要宿主机安装 Maven 3.2.5+ 和 JDK 17。

默认 `FRONTEND_BUILD_MODE=docker`，前端会在 `node:20-alpine` 容器里执行 `pnpm install && pnpm build`，宿主机不需要安装 Node/pnpm。只有把 `FRONTEND_BUILD_MODE=host` 时，才需要宿主机安装 Node 20+ 和 pnpm。

脚本支持自动安装缺失工具。默认不开启；如需让脚本自动安装，把 `builder.env` 中改为：

```env
INSTALL_MISSING_TOOLS=true
NODE_MAJOR_VERSION=20
PNPM_VERSION=9.15.9
```

自动安装当前支持 `apt-get`、`dnf`、`yum` 三类 Linux 包管理器。安装系统包需要 root 权限；非 root 用户执行时脚本会尝试使用 `sudo`。

CentOS 7 官方 `mirrorlist.centos.org` 常见不可用。脚本默认会在自动安装工具前把 CentOS 7 yum 源切到阿里云 vault：

```env
AUTO_CONFIGURE_CENTOS7_YUM=true
CENTOS7_YUM_MIRROR=https://mirrors.aliyun.com/centos-vault/7.9.2009
```

如果使用私有 HTTP 仓库，例如 `125.75.152.167:5001`，需要在 Docker 中配置 `insecure-registries`。

## 使用

复制配置：

```bash
cp deploy/lingz-builder/builder.env.example deploy/lingz-builder/builder.env
vim deploy/lingz-builder/builder.env
```

至少修改：

```env
REPO_URL=git@example.com:your-org/lingzhou-agent.git
REPO_REF=dev
REGISTRY=125.75.152.167:5001
IMAGE_TAG=1.7.0
```

你的 GitLab HTTP 仓库可以这样配置：

```env
REPO_URL=http://git.zhoujusoft.com/ai/lingzhou-agent.git
REPO_REF=dev
GIT_HTTP_USERNAME=oauth2
```

token 不建议写进 `builder.env`。执行时从环境变量传入：

```bash
GIT_HTTP_TOKEN='<your-gitlab-token>' ./deploy/lingz-builder/scripts/build-and-push.sh deploy/lingz-builder/builder.env
```

脚本会在镜像全部推送成功后创建并推送 Git tag，因此 token 至少需要 GitLab 的 `read_repository` 和 `write_repository` 权限。

默认已使用国内源：

```env
BACKEND_BUILD_MODE=docker
BACKEND_BUILD_IMAGE=maven:3.9-eclipse-temurin-17
FRONTEND_BUILD_MODE=docker
FRONTEND_BUILD_IMAGE=node:20-alpine
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
NPM_REGISTRY=https://registry.npmmirror.com
PNPM_REGISTRY=https://registry.npmmirror.com
```

如果希望脚本首次运行时顺便安装缺失依赖：

```env
INSTALL_MISSING_TOOLS=true
```

默认读取当前 `lingz-builder` 目录下的 `builder.env`，并使用其中的 `IMAGE_TAG` 构建并推送：

```bash
cd deploy/lingz-builder
sh ./scripts/build-and-push.sh
```

推荐每次构建时直接指定一个新的镜像 tag：

```bash
sh ./scripts/build-and-push.sh 1.7.1
```

本次构建会给前后端镜像使用同一个 Docker tag：

```text
125.75.152.167:5001/lingzhou-frontend:1.7.1
125.75.152.167:5001/lingzhou-backend:1.7.1
```

两个镜像都推送成功后，脚本会在本次构建的 commit 上创建同名 Git tag `1.7.1`，并 push 到 GitLab。Git tag 已存在时脚本会在构建前失败，避免覆盖历史版本。

Git tag 配置：

```env
CREATE_GIT_TAG=true
GIT_TAG_PREFIX=
GIT_TAGGER_NAME=Lingz Builder
GIT_TAGGER_EMAIL=lingz-builder@localhost
```

如需生成 `v1.7.1` 格式的 Git tag，可以设置：

```env
GIT_TAG_PREFIX=v
```

也可以使用显式参数：

```bash
sh ./scripts/build-and-push.sh --tag 1.7.1
sh ./scripts/build-and-push.sh --tag 1.7.1 --env ./builder.env
```

仍兼容原来的调用方式：

```bash
sh ./scripts/build-and-push.sh ./builder.env
```

构建完成后会推送：

```text
125.75.152.167:5001/lingzhou-frontend:1.7.1
125.75.152.167:5001/lingzhou-backend:1.7.1
```

如果 `TARGET_PLATFORM=linux/arm64`，镜像 tag 会自动追加平台后缀：

```text
1.7.0-arm64
```

## 常用覆盖参数

临时指定分支或 tag：

```bash
REPO_REF=v1.7.0 ./deploy/lingz-builder/scripts/build-and-push.sh deploy/lingz-builder/builder.env
```

临时指定版本：

```bash
sh ./scripts/build-and-push.sh 1.7.1
```

arm64 构建：

```bash
TARGET_PLATFORM=linux/arm64 ./deploy/lingz-builder/scripts/build-and-push.sh deploy/lingz-builder/builder.env
```

## 和 deploy/lingz 的关系

- `deploy/lingz-builder`：负责构建并推送镜像。
- `deploy/lingz`：负责从镜像仓库拉取镜像并运行服务。

发布后，到部署目录把 `.env` 中的 `IMAGE_TAG` 改成实际推送 tag，再执行：

```bash
cd deploy/lingz
docker compose --env-file .env -f docker-compose.yml pull
docker compose --env-file .env -f docker-compose.yml up -d
```
