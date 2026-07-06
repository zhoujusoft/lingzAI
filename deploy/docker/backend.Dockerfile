# syntax=docker/dockerfile:1.7

# 第一阶段：在构建机器架构运行，下载目标平台的 wheel 包
# 使用 BUILDPLATFORM 让此阶段在构建机器原生架构运行，避免 QEMU 模拟，速度更快
FROM --platform=$BUILDPLATFORM python:3.11-slim-bookworm AS runtime-wheelhouse-builder
ARG TARGETARCH
ARG TARGETPLATFORM
ARG BUILDARCH
ARG PIP_PLATFORM="manylinux2014_x86_64 manylinux_2_17_x86_64 manylinux_2_28_x86_64 linux_x86_64"

WORKDIR /tmp/runtime-python

ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

COPY workspaces/public/runtime-envs/python/default/requirements.txt ./default-requirements.txt

# 下载 wheel 包
# 为目标平台同时声明多个 manylinux tag，避免遗漏 newer manylinux wheels。
RUN --mount=type=cache,id=backend-pip-cache-${TARGETARCH},target=/root/.cache/pip,sharing=locked \
    mkdir -p /opt/runtime-wheelhouse/default && \
    echo "Downloading Python packages for platforms: ${PIP_PLATFORM} (TARGETARCH=${TARGETARCH})" && \
    PYTHON_VERSION="$(python3.11 -c 'import sys; print(f"{sys.version_info[0]}.{sys.version_info[1]}")')" && \
    PYTHON_ABI="$(python3.11 -c 'import sys; print(f"cp{sys.version_info[0]}{sys.version_info[1]}")')" && \
    set -- \
        --dest /opt/runtime-wheelhouse/default \
        --only-binary=:all: \
        --implementation cp \
        --python-version "${PYTHON_VERSION}" \
        --abi "${PYTHON_ABI}" && \
    for platform in ${PIP_PLATFORM}; do \
        set -- "$@" --platform "${platform}"; \
    done && \
    python3.11 -m pip download "$@" -r ./default-requirements.txt \
        2>/tmp/runtime-python/default-wheelhouse.err || \
    (cat /tmp/runtime-python/default-wheelhouse.err >&2; \
     echo "Note: Some packages will be installed from source in final image")

COPY workspaces/public/runtime-envs/python/general-code/requirements.txt ./general-code-requirements.txt
RUN --mount=type=cache,id=backend-pip-cache-${TARGETARCH},target=/root/.cache/pip,sharing=locked \
    mkdir -p /opt/runtime-wheelhouse/general-code && \
    echo "Downloading Python packages for platforms: ${PIP_PLATFORM} (TARGETARCH=${TARGETARCH})" && \
    PYTHON_VERSION="$(python3.11 -c 'import sys; print(f"{sys.version_info[0]}.{sys.version_info[1]}")')" && \
    PYTHON_ABI="$(python3.11 -c 'import sys; print(f"cp{sys.version_info[0]}{sys.version_info[1]}")')" && \
    set -- \
        --dest /opt/runtime-wheelhouse/general-code \
        --only-binary=:all: \
        --implementation cp \
        --python-version "${PYTHON_VERSION}" \
        --abi "${PYTHON_ABI}" && \
    for platform in ${PIP_PLATFORM}; do \
        set -- "$@" --platform "${platform}"; \
    done && \
    python3.11 -m pip download "$@" -r ./general-code-requirements.txt \
        2>/tmp/runtime-python/general-wheelhouse.err || \
    (cat /tmp/runtime-python/general-wheelhouse.err >&2; \
     echo "Note: Some packages will be installed from source in final image")

# 第二阶段：在目标平台架构运行
FROM python:3.11-slim-bookworm
ARG TARGETARCH
ARG TARGETPLATFORM
ARG APT_MIRROR_HOST=mirrors.tuna.tsinghua.edu.cn

WORKDIR /app
EXPOSE 5050

ENV TZ=Asia/Shanghai \
    PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    APP_SKILLS_INSTALLER_PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    LINGZ_RUNTIME_PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

# 安装系统依赖
# arm64 需要额外的编译工具来构建 onnxruntime（没有预编译 wheel）
# 注意：使用缓存挂载时不需手动清理 apt 缓存
RUN --mount=type=cache,id=backend-apt-cache-${TARGETARCH},target=/var/cache/apt,sharing=locked \
    --mount=type=cache,id=backend-apt-lists-${TARGETARCH},target=/var/lib/apt/lists,sharing=locked \
    sed -i "s|http://deb.debian.org|http://${APT_MIRROR_HOST}|g" /etc/apt/sources.list.d/debian.sources && \
    DEBIAN_FRONTEND=noninteractive apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        libc6 \
        libstdc++6 \
        zlib1g \
        openjdk-17-jre-headless \
        $(if [ "${TARGETARCH}" = "arm64" ]; then echo "build-essential cmake git"; fi)

RUN java -version && python --version

COPY workspaces/public/runtime-envs/python/default/requirements.txt /tmp/runtime-python/default-requirements.txt
COPY --from=runtime-wheelhouse-builder /opt/runtime-wheelhouse/default/ /tmp/runtime-python/default-vendor/

# 安装 Python 依赖
# 先尝试从 wheelhouse 安装；若缺少个别 wheel，则继续复用 wheelhouse 并仅为缺失包访问网络。
RUN echo "Installing Python packages (TARGETARCH=${TARGETARCH})..." && \
    python3.11 -m pip config set global.timeout 300 && \
    python3.11 -m pip config set global.retries 5 && \
    python3.11 -m pip install --no-cache-dir \
        --no-index \
        --find-links /tmp/runtime-python/default-vendor \
        -r /tmp/runtime-python/default-requirements.txt || \
    (echo "Some packages not in wheelhouse, reusing wheelhouse and downloading only missing packages..." && \
     python3.11 -m pip install --no-cache-dir \
         --find-links /tmp/runtime-python/default-vendor \
         --timeout 300 \
         --retries 5 \
         -r /tmp/runtime-python/default-requirements.txt)

COPY backend/target/backend.jar /app/app.jar
COPY workspaces/public/skills /app/default-workspaces/public/skills
COPY workspaces/public/skillstudio/skills /app/default-workspaces/public/skillstudio/skills
COPY workspaces/public/runtime-envs/python/default/requirements.txt /app/default-workspaces/public/runtime-envs/python/default/requirements.txt
COPY workspaces/public/runtime-envs/python/general-code/requirements.txt /app/default-workspaces/public/runtime-envs/python/general-code/requirements.txt
COPY --from=runtime-wheelhouse-builder /opt/runtime-wheelhouse/default/ /app/default-workspaces/public/runtime-envs/python/default/vendor/
COPY --from=runtime-wheelhouse-builder /opt/runtime-wheelhouse/general-code/ /app/default-workspaces/public/runtime-envs/python/general-code/vendor/
COPY deploy/docker/backend-entrypoint.sh /app/backend-entrypoint.sh

ENV JAVA_OPTS="" \
    SKILL_ROOT="/app/workspaces/public/skills"

RUN chmod +x /app/backend-entrypoint.sh

CMD ["/app/backend-entrypoint.sh"]
