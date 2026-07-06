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
- `scripts/vllm-tool-calls/`：vLLM OpenAI-compatible tool calls 调试脚本与请求体样例
- `data/`：运行时数据目录

## 部署前须知

- 部署方式：单机部署
- 部署平台前，请提前设置好网络等基础环境

## 环境要求

### 基础环境

- 操作系统：CentOS 7.x、Ubuntu 20.04+，或其他可稳定运行 Docker / Docker Compose 的 Linux 发行版
- 磁盘类型：推荐 SSD
- 网络要求：可访问镜像仓库、系统依赖源，以及外部模型服务地址

提示：支持云平台部署，安装成功后请检查公有云端口开通情况。

### 服务器配置建议

当前 `deploy/lingz` 一体化部署默认启动以下服务：

- `frontend`
- `backend`
- `mysql`
- `redis`
- `minio`
- `elasticsearch`

说明：

- 当前 Quick 部署 **不包含 vLLM 容器**
- 聊天、向量、Rerank 等模型服务通常通过系统内模型配置连接到外部模型地址
- 因此，大多数场景下应用服务器 **不需要 GPU**

####  仅部署本项目应用栈，模型服务走外部 vLLM / 其他外部模型服务

推荐配置：

- CPU / 内存：8 核 16G
- 磁盘空间：200G SSD

较稳妥配置（适合知识库、ES 索引和多人并发更高的场景）：

- CPU / 内存：8 核 32G
- 磁盘空间：300G SSD 或以上

适用说明：
- 若知识库文档量较大、Elasticsearch 索引持续增长，建议直接使用 `8C32G`

#### 模型服务器硬件资源概览 

| 组件 | 规格 | 备注 |
| :--- | :--- | :--- |
| **显存 (VRAM)** | **128GB** | 支持 FP16 全参数加载 32B 模型并保留充足上下文空间 |
| **内存 (RAM)** | **128GB** | 满足模型加载时的内存交换及高并发数据预处理需求 |
| **存储 (SSD)** | **500GB NVMe** | 建议用于存放模型权重（Qwen32B 约占 65G+）及索引数据库 |
| **适用场景** | 生产级 RAG 工作流 | 支持高频对话 + 深度知识库检索 |

---
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

当前 `deploy/lingz` 部署方式不再要求在 `.env` / `release.env` 中预先填写模型 Key。

当前口径为：

- 部署前只需维护基础环境配置，例如镜像、端口、数据库、Redis、MinIO、ES 等运行参数
- 平台版本号默认来自 `.env` 中的 `IMAGE_TAG`，不需要额外维护 `APP_VERSION`
- Flyway baseline 不来自 `IMAGE_TAG` 或环境变量；新库 baseline 由 `deploy/lingz/db/schema.sql` 内的 `flyway_schema_history` 初始化记录决定
- 构建阶段建议只维护基础版本 `IMAGE_TAG=x.y.z`，再通过 `--platform linux/arm64` 等参数生成带后缀的镜像 tag
- 部署阶段 `.env` 中的 `IMAGE_TAG` 需要填写最终要拉取的真实镜像 tag；例如 arm64 镜像应填写 `1.4.2-arm64`
- 聊天历史窗口默认也在后端 `application.yml` 中维护；如需控成本，可通过 `.env` 中的 `APP_CHAT_CONTEXT_MAX_HISTORY_TOKENS`、`APP_CHAT_CONTEXT_MAX_*_MESSAGE_CHARS` 覆盖
- 模型厂商与模型实例相关配置统一在系统内维护，不再依赖 `deploy/lingz` 侧提前写死模型 Key
- 如果 `.env.example` 中仍保留部分模型相关项，可视为兼容保留字段；当前部署不是必填项

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
- `deploy/lingz` 当前不要求额外配置模型 Key，模型相关能力由系统内配置接管。
- `frontend` 与 `backend` 都直接从镜像仓库拉取，不在当前 compose 中本地构建。




基于您提供的 128G 显存、128G 内存的高配服务器，这是一套非常理想的**“全栈生产级私有化 AI 部署”**方案。

以下是为您整理的《模型运行服务器配置要求与部署指南》，您可以将其用于技术文档或系统说明：

---

# 🚀 模型服务器配置要求与部署方案 (高配版)

本配置基于 **128G 显存** 核心硬件，旨在实现大语言模型 (LLM)、向量模型 (Embedding) 及重排序模型 (Reranker) 的全并发、低延迟运行。


## 2. 模型负载分配方案 (Resource Allocation)

在该配置下，建议按照以下显存权重进行分配，以达到最佳吞吐量：

### A. 大语言模型：Qwen-2.5-32B
*   **显存占用**：约 64GB (FP16 精度) 或 36GB (Int8 量化)。
*   **部署建议**：
    *   利用 **vLLM** 或 **Text-Generation-Inference (TGI)** 部署。
    *   **上下文长度**：设定为 32k - 128k。128G 显存允许在加载 32B 模型后，留出超过 60GB 空间给 **KV Cache**，这意味着可以支持极长的对话上下文或极高的并发请求。
*   **角色**：负责逻辑推理、文本生成、多轮对话。

### B. 向量模型：Qwen-Embedding
*   **显存占用**：约 2GB - 4GB。
*   **部署建议**：
    *   作为常驻服务，建议使用 **TEI (Text-Embeddings-Inference)** 部署以获得极低的推理延迟。
*   **角色**：将用户提问和知识库文档转化为数学向量。

### C. 重排序模型：BGE-Reranker
*   **显存占用**：约 2GB - 8GB（取决于输入长度）。
*   **部署建议**：
    *   在 RAG 流程中至关重要，用于对检索到的知识片段进行二次精校。
*   **角色**：提高知识库检索的准确率，过滤无关信息。

---

## 3. 软件架构推荐 (Production Stack)

为了最大化利用 128G 显存，建议采用以下技术栈：

*   **推理引擎**：`vLLM` (支持 PagedAttention，显存利用率最高)。
*   **容器化**：`Docker` + `NVIDIA Container Toolkit`。
*   **中间件**：`Ollama` (快速测试) 或 `Open-WebUI` (前端交互)。
*   **API 协议**：OpenAI 兼容格式 (便于集成到各编排器)。

---

## 4. 配置优势分析

1.  **无损推理**：大部分 32B 模型在 128G 显存下无需进行极致压缩（如 4-bit 量化），可以使用 **FP16 或 BF16** 推理，保持模型原生智力。
2.  **全异步流水线**：由于显存充裕，LLM、Embedding 和 Reranker 可以**同时常驻显存**，无需在切换任务时清理显存，响应时间缩短至毫秒级。
3.  **超长上下文 (RAG 增强)**：充足的显存空间可以容纳更多的 Top-K 检索片段（Rerank 后的结果），显著提升复杂任务的回答质量。

---

## 5. 运维优化建议

*   **显存监控**：安装 `nvitop` 或 `nvidia-smi` 实时监控显存碎片。
*   **存储预警**：500G SSD 在存储多个版本的 32B 模型（每个约 60G-70G）和向量索引后会较紧凑。**建议定期清理模型缓存**或扩容至 1TB/2TB。
*   **并发控制**：在 vLLM 中通过 `--max-model-len` 和 `--gpu-memory-utilization` 调节 LLM 的显存比例，确保为 Embedding 模型预留固定空间。

---

**总结**：您的这台服务器是目前部署 **Qwen-32B 级别 RAG 系统** 的“黄金配置”，智力水平与运行速度达到了私有化部署的平衡点。
