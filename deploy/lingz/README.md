# Compose Quick

本目录用于本项目的快速一体化部署。

通过这里的 `docker-compose.yml`，可以一次性启动：

- `frontend`
- `backend`
- `mysql`
- `redis`
- `minio`
- `elasticsearch`

适合单机在线部署、快速体验、联调和演示环境。

## 目录说明

- `docker-compose.yml`：Quick 部署主文件
- `.env.example`：Quick 部署环境变量模板
- `config/redis/redis.conf`：Redis 配置
- `db/schema.sql`：MySQL 初始化 SQL
- `data/`：运行时数据目录

## 部署前须知

- 部署方式：单机部署
- 部署平台前，请提前设置好网络等基础环境

## 环境要求

推荐服务器配置：

- 操作系统：CentOS 7.x
- CPU / 内存：8 核 16G
- 磁盘空间：200G
- 可访问互联网

提示：支持云平台部署，安装成功后请检查公有云端口开通情况。

## 安装 Docker CE

### CentOS 安装脚本

```bash
yum -y update && \
 yum install -y yum-utils device-mapper-persistent-data lvm2 && \
 yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo && \
 sudo yum install -y docker-ce && \
 systemctl start docker && \
 systemctl enable docker && \
 docker version
```

异常处理：

- 若出现 `yum` 文件无法访问，请先检查网络、DNS 和镜像源可达性。

### Ubuntu 安装脚本

```bash
# 首先切换到 root 账户
sudo su

# 从阿里云镜像源下载 Docker CE 安装脚本
curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun

# step 1: 安装必要系统工具
sudo apt-get update
sudo apt-get -y install apt-transport-https ca-certificates curl software-properties-common

# step 2: 安装 GPG 证书
curl -fsSL http://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo apt-key add -

# step 3: 写入软件源信息
sudo add-apt-repository "deb [arch=amd64] http://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable"

# step 4: 更新并安装 Docker CE
sudo apt-get -y update
sudo apt-get -y install docker-ce
```

### 华为欧拉

可参考：

```text
https://blog.csdn.net/yumo_fly/article/details/133750395
```

## 配置私有镜像仓库地址

### 添加镜像仓库地址

```bash
sudo mkdir -p /etc/docker

sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "log-driver": "json-file",
  "log-opts": {"max-size": "500m", "max-file": "3"},
  "insecure-registries": ["125.75.152.167:5001"],
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://docker.nju.edu.cn"
  ]
}
EOF
```

### 修改 Docker 镜像及容器的默认存储目录

若需要把 Docker 数据目录迁移到更大磁盘，可将 `/etc/docker/daemon.json` 调整为：

```json
{
  "data-root": "/data2/docker"
}
```

若要同时保留镜像仓库配置与 `data-root`，请合并成同一个 JSON 文件，不要分开写两个独立对象。

### 重启 Docker

```bash
sudo systemctl daemon-reload && \
 sudo systemctl restart docker
```

## 必填配置

启动前请按实际厂商需求填写 `.env` / `release.env` 中的模型参数。
当前配置口径为：

- 模型表维护：`baseUrl`、`path`、`modelName`
- 配置文件维护：`model.qwen.*`、`model.vllm.*` 对应的运行参数

## 快速开始

在当前目录执行：

```bash
cp .env.example .env
docker compose --env-file .env -f docker-compose.yml up -d
```

## 网络说明

本部署默认使用 Docker 服务名互联，不固定子网、不固定 IP。
服务之间通过名称互相访问：

- `mysql`
- `redis`
- `minio`
- `elasticsearch`
- `backend`

前端容器通过 `frontend/nginx.conf` 反向代理 `/api` 到 `backend:5050`，因此浏览器通常只需要访问前端端口。

## 常用命令

在当前目录执行：

```bash
docker compose --env-file .env -f docker-compose.yml up -d
docker compose --env-file .env -f docker-compose.yml logs -f
docker compose --env-file .env -f docker-compose.yml down
```

## 数据目录

运行时数据默认写入：

```text
./data/
```

该目录属于本地运行状态数据，建议忽略版本控制。

## 说明

- Quick 部署已改为统一配置入口，不再通过 `qwen` / `vllm` profile 切换模型配置。
- `frontend` 与 `backend` 都直接从镜像仓库拉取，不在当前 compose 中本地构建。
