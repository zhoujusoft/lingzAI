#!/usr/bin/env bash
# =============================================================================
# build-lingzhou-arm.sh — 从 x86 镜像提取内容，重建为 ARM 镜像并导出 tar
#
# 支持一次构建多个镜像（backend + frontend）
# 用法:
#   ./build-lingzhou-arm.sh <版本号> [目标平台]
#   ./build-lingzhou-arm.sh 1.7.0
#   ./build-lingzhou-arm.sh 1.7.0 linux/arm/v7
# =============================================================================

set -euo pipefail

# ===== 镜像配置 =====
CFG_BACKEND_NAME="125.75.152.167:5001/lingzhou-backend"
CFG_BACKEND_DOCKERFILE="https://gitee.com/zhoujusoft/lingzai/raw/main/deploy/docker/backend.Dockerfile"

CFG_FRONTEND_NAME="125.75.152.167:5001/lingzhou-frontend"
CFG_FRONTEND_DOCKERFILE="https://gitee.com/zhoujusoft/lingzai/raw/main/deploy/docker/frontend.Dockerfile"

BUILDER_NAME="arm-builder"

# ===== 颜色 =====
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${BLUE}[STEP]${NC}  $*"; }

usage() {
    echo ""
    echo "用法: $0 <版本号> [目标平台]"
    echo "  $0 1.7.0"
    echo "  $0 1.7.0 linux/arm/v7"
    exit 1
}

[[ $# -lt 1 ]] && { log_error "缺少版本号"; usage; }

IMAGE_TAG="$1"
TARGET_PLATFORM="${2:-linux/arm64}"
APT_MIRROR_HOST="${APT_MIRROR_HOST:-deb.debian.org}"
PIP_INDEX_URL="${PIP_INDEX_URL:-https://pypi.org/simple}"
PIP_PLATFORM="${PIP_PLATFORM:-}"

if [[ -z "${PIP_PLATFORM}" ]]; then
    case "${TARGET_PLATFORM}" in
        linux/arm64)
            PIP_PLATFORM="manylinux2014_aarch64 manylinux_2_17_aarch64 manylinux_2_28_aarch64 linux_aarch64"
            ;;
        linux/amd64)
            PIP_PLATFORM="manylinux2014_x86_64 manylinux_2_17_x86_64 manylinux_2_28_x86_64 linux_x86_64"
            ;;
    esac
fi

OUTPUT_TARS=()
WORK_DIR=$(mktemp -d -t lingzhou-build-XXXXXX)
trap 'rm -rf "${WORK_DIR}"' EXIT

# =============================================================================
# 0. 环境检查
# =============================================================================
check_env() {
    log_step "检查运行环境..."
    command -v docker &>/dev/null || { log_error "未找到 docker"; exit 1; }
    command -v curl  &>/dev/null || { log_error "未找到 curl";  exit 1; }
    docker info &>/dev/null         || { log_error "Docker daemon 未运行"; exit 1; }
    docker buildx version &>/dev/null || { log_error "docker buildx 未安装"; exit 1; }
    log_info "Docker: $(docker --version)"
    log_info "Buildx: $(docker buildx version)"
}

# =============================================================================
# 1. 从 x86 镜像提取内容
# =============================================================================

extract_backend() {
    local CID="$1" CTX="$2"
    mkdir -p \
        "${CTX}/backend/target" \
        "${CTX}/workspaces/public/skills" \
        "${CTX}/workspaces/public/skillstudio/skills" \
        "${CTX}/workspaces/public/runtime-envs/python/default" \
        "${CTX}/workspaces/public/runtime-envs/python/general-code" \
        "${CTX}/deploy/docker"

    docker cp "${CID}:/app/app.jar" "${CTX}/backend/target/backend.jar" 2>/dev/null || {
        log_error "提取 app.jar 失败"; return 1
    }
    log_info "  backend.jar     : ✓ ($(du -sh "${CTX}/backend/target/backend.jar" | cut -f1))"

    docker cp "${CID}:/app/default-workspaces/public/skills/." "${CTX}/workspaces/public/skills/" 2>/dev/null ||
    docker cp "${CID}:/app/workspaces/public/skills/." "${CTX}/workspaces/public/skills/" 2>/dev/null ||
    docker cp "${CID}:/app/default-skills/." "${CTX}/workspaces/public/skills/" 2>/dev/null ||
    log_warn "  workspaces/public/skills/ 为空"
    log_info "  skills/         : ✓"

    docker cp "${CID}:/app/default-workspaces/public/skillstudio/skills/." "${CTX}/workspaces/public/skillstudio/skills/" 2>/dev/null ||
    docker cp "${CID}:/app/workspaces/public/skillstudio/skills/." "${CTX}/workspaces/public/skillstudio/skills/" 2>/dev/null ||
    log_warn "  workspaces/public/skillstudio/skills/ 为空"
    log_info "  skillstudio/    : ✓"

    docker cp "${CID}:/app/default-workspaces/public/runtime-envs/python/default/requirements.txt" "${CTX}/workspaces/public/runtime-envs/python/default/requirements.txt" 2>/dev/null ||
    docker cp "${CID}:/app/workspaces/public/runtime-envs/python/default/requirements.txt" "${CTX}/workspaces/public/runtime-envs/python/default/requirements.txt" 2>/dev/null || {
        log_error "提取 default requirements.txt 失败"; return 1
    }
    log_info "  default req     : ✓"

    docker cp "${CID}:/app/default-workspaces/public/runtime-envs/python/general-code/requirements.txt" "${CTX}/workspaces/public/runtime-envs/python/general-code/requirements.txt" 2>/dev/null ||
    docker cp "${CID}:/app/workspaces/public/runtime-envs/python/general-code/requirements.txt" "${CTX}/workspaces/public/runtime-envs/python/general-code/requirements.txt" 2>/dev/null || {
        log_error "提取 general-code requirements.txt 失败"; return 1
    }
    log_info "  general req     : ✓"

    docker cp "${CID}:/app/backend-entrypoint.sh" "${CTX}/deploy/docker/backend-entrypoint.sh" 2>/dev/null || log_warn "  entrypoint 缺失"
    chmod +x "${CTX}/deploy/docker/backend-entrypoint.sh" 2>/dev/null || true
    log_info "  entrypoint.sh   : ✓"
}

extract_frontend() {
    local CID="$1" CTX="$2"
    mkdir -p "${CTX}/dist" "${CTX}"

    docker cp "${CID}:/usr/share/nginx/html/." "${CTX}/dist/" 2>/dev/null || {
        docker cp "${CID}:/app/dist/." "${CTX}/dist/" 2>/dev/null || {
            log_error "提取静态文件失败"; return 1
        }
    }
    log_info "  dist/           : ✓ ($(find "${CTX}/dist" -type f 2>/dev/null | wc -l) files)"

    docker cp "${CID}:/etc/nginx/conf.d/default.conf" "${CTX}/nginx.conf" 2>/dev/null || {
        log_warn "  nginx.conf 缺失，用默认"
        cat > "${CTX}/nginx.conf" << 'NGX'
server {
    listen 80;
    location / { root /usr/share/nginx/html; index index.html; try_files $uri $uri/ /index.html; }
}
NGX
    }
    log_info "  nginx.conf      : ✓"
}

pull_and_extract() {
    local IMAGE_NAME="$1" TAG="$2" FN="$3" CTX="$4"
    local SRC="${IMAGE_NAME}:${TAG}"

    log_step "拉取 x86 镜像: ${SRC}"
    docker pull --platform linux/amd64 "${SRC}" 2>/dev/null || {
        log_error "拉取失败: ${SRC}"; return 1
    }
    log_info "  拉取成功"

    local CID
    CID=$(docker create --platform linux/amd64 "${SRC}")
    "${FN}" "${CID}" "${CTX}" || { docker rm "${CID}" &>/dev/null; return 1; }
    docker rm "${CID}" &>/dev/null
}

# =============================================================================
# 2. 下载 Dockerfile
# =============================================================================
fetch_dockerfile() {
    local URL="$1" OUT="$2" LABEL="$3"

    log_info "下载 Dockerfile: ${LABEL}"
    local CODE
    CODE=$(curl -sL -w "%{http_code}" "${URL}" -o "${OUT}")
    if [[ ! "${CODE}" =~ ^200$|^302$|^301$ ]]; then
        log_error "下载失败 (HTTP ${CODE}): ${URL}"; return 1
    fi

    # 替换为国内镜像站
    sed -i 's|FROM eclipse-temurin:|FROM docker.m.daocloud.io/library/eclipse-temurin:|' "${OUT}"
    sed -i 's|FROM node:|FROM docker.m.daocloud.io/library/node:|'             "${OUT}"
    sed -i 's|FROM nginx:|FROM docker.m.daocloud.io/library/nginx:|'           "${OUT}"
    sed -i "s#https://pypi.tuna.tsinghua.edu.cn/simple#${PIP_INDEX_URL}#g"     "${OUT}"
    log_info "  ${LABEL}: ✓"
}

# =============================================================================
# 3. Frontend 专用：生成简化 ARM Dockerfile
# =============================================================================
gen_frontend_dockerfile() {
    local OUT="$1" HAS_NGX="$2"
    cat > "${OUT}" << 'EOF'
FROM docker.m.daocloud.io/library/nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
EOF
    [[ "${HAS_NGX}" == "true" ]] && echo "COPY nginx.conf /etc/nginx/conf.d/default.conf" >> "${OUT}"
    cat >> "${OUT}" << 'EOF'
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
    log_info "  生成简化 ARM Dockerfile"
}

# =============================================================================
# 4. QEMU 注册（必须在 Builder 创建前）
# =============================================================================
setup_qemu() {
    log_step "注册 QEMU 多架构模拟器..."

    # 先拉取 QEMU 镜像
    docker pull multiarch/qemu-user-static:latest 2>/dev/null || {
        log_warn "拉取多架构 QEMU 镜像失败，尝试继续..."
        return
    }

    # 注册 binfmt_misc（需要 --privileged）
    docker run --rm --privileged multiarch/qemu-user-static --reset -p yes 2>&1 || true

    # 验证是否注册成功
    if grep -q "interpreter /usr/bin/qemu-aarch64" /proc/sys/fs/binfmt_misc/qemu-aarch64 2>/dev/null; then
        log_info "QEMU arm64 注册成功 ✓"
    elif grep -q "interpreter.*qemu-aarch64" /proc/sys/fs/binfmt_misc/* 2>/dev/null; then
        log_info "QEMU arm64 注册成功 ✓"
    else
        log_warn "QEMU arm64 可能未注册，后续构建可能降级为 amd64"
    fi
}

# =============================================================================
# 5. 创建 Builder（必须在 QEMU 之后）
# =============================================================================
setup_builder() {
    log_step "配置 buildx builder: ${BUILDER_NAME}"
    docker buildx rm "${BUILDER_NAME}" 2>/dev/null || true

    local CFG="${WORK_DIR}/buildkitd.toml"
    cat > "${CFG}" << 'EOF'
[registry."docker.io"]
  mirrors = [
    "https://docker.m.daocloud.io",
    "https://docker.mirrors.ustc.edu.cn",
    "https://mirror.ccs.tencentyun.com"
  ]
EOF

    docker buildx create \
        --name "${BUILDER_NAME}" \
        --driver docker-container \
        --driver-opt network=host \
        --config "${CFG}" \
        --use

    # Bootstrap 并显示支持的平台
    echo -n "  可用平台: "
    docker buildx inspect "${BUILDER_NAME}" --bootstrap 2>&1 | grep -oP 'Platforms:.*' || log_warn " 无法获取平台列表"
}

# =============================================================================
# 6. 验证 tar 包架构
# =============================================================================
verify_tar_arch() {
    local TAR="$1"
    log_info "验证架构: $(basename "${TAR}")"

    # 解出 manifest.json
    local MANIFEST CONFIG
    MANIFEST=$(tar xf "${TAR}" manifest.json -O 2>/dev/null)
    if [[ -z "${MANIFEST}" ]]; then
        log_warn "  无法解出 manifest.json"
        return 1
    fi

    # 取第一个镜像的 Config 文件路径
    CONFIG=$(echo "${MANIFEST}" | python3 -c "import json,sys; print(json.load(sys.stdin)[0].get('Config',''))" 2>/dev/null)
    if [[ -z "${CONFIG}" ]]; then
        log_warn "  无法解析 manifest.json"
        return 1
    fi

    # 解出 Config 文件获取架构
    local ARCH
    ARCH=$(tar xf "${TAR}" "${CONFIG}" -O 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('architecture','?'),d.get('os','?'))" 2>/dev/null)

    if echo "${ARCH}" | grep -q "arm\|aarch"; then
        log_info "  架构: ${ARCH}  ← ARM ✓"
        return 0
    else
        log_error "  架构: ${ARCH}  ← 期望 arm64，实际是 amd64！"
        return 1
    fi
}

# =============================================================================
# 7. 构建并导出（核心）
# =============================================================================
build_and_export() {
    local IMG="$1" DOCKERFILE="$2" CTX="$3"
    local SAFE=$(echo "${IMG}" | tr '/:' '_')
    local PLAT_SAFE="${TARGET_PLATFORM//\//_}"
    local OUT="$(pwd)/${SAFE}_${IMAGE_TAG}_${PLAT_SAFE}.tar"
    local LOG="$(pwd)/${SAFE}_${IMAGE_TAG}_${PLAT_SAFE}.build.log"
    local BUILD_ARGS=(--build-arg "APT_MIRROR_HOST=${APT_MIRROR_HOST}")
    [[ -n "${PIP_PLATFORM}" ]] && BUILD_ARGS+=(--build-arg "PIP_PLATFORM=${PIP_PLATFORM}")

    log_step "交叉编译: ${IMG}:${IMAGE_TAG} → ${TARGET_PLATFORM}"
    log_info "APT 镜像源: ${APT_MIRROR_HOST}"
    log_info "PIP 镜像源: ${PIP_INDEX_URL}"
    [[ -n "${PIP_PLATFORM}" ]] && log_info "PIP 平台标签: ${PIP_PLATFORM}"
    echo ""

    # 先构建到本地 Docker daemon（单平台可用 --load）
    if ! docker buildx build \
        --builder "${BUILDER_NAME}" \
        --platform "${TARGET_PLATFORM}" \
        "${BUILD_ARGS[@]}" \
        --file "$(realpath "${DOCKERFILE}")" \
        --tag "${IMG}:${IMAGE_TAG}" \
        --load \
        "$(realpath "${CTX}")" \
        2>&1 | tee "${LOG}" | tail -80; then
        log_error "构建失败，完整日志: ${LOG}"
        return 1
    fi

    # 从本地 daemon 导出 tar
    log_info "导出镜像到 tar..."
    docker save "${IMG}:${IMAGE_TAG}" -o "${OUT}"

    # 验证
    if [[ -f "${OUT}" ]]; then
        local SZ
        SZ=$(du -sh "${OUT}" | cut -f1)
        if verify_tar_arch "${OUT}"; then
            log_info "✔ $(basename "${OUT}") (${SZ})"
            OUTPUT_TARS+=("${OUT}")
        else
            log_error "架构验证失败！请将构建日志截图发给我排查"
            rm -f "${OUT}"
            return 1
        fi
    else
        log_error "导出失败"; return 1
    fi
}

# =============================================================================
# 主流程
# =============================================================================
main() {
    echo ""
    echo "============================================================"
    echo "  lingzhou  x86 → ARM 镜像转换"
    echo "  版本: ${IMAGE_TAG}    目标平台: ${TARGET_PLATFORM}"
    echo "============================================================"
    echo ""

    check_env
    setup_qemu
    setup_builder

    # ──────────────────────────────────
    # [1/2] Backend
    # ──────────────────────────────────
    echo ""
    echo "──── [1/2] Backend ────"
    echo ""

    local BC="${WORK_DIR}/backend-ctx"
    mkdir -p "${BC}"

    if pull_and_extract "${CFG_BACKEND_NAME}" "${IMAGE_TAG}" extract_backend "${BC}"; then
        local BDF="${WORK_DIR}/backend.Dockerfile"
        if fetch_dockerfile "${CFG_BACKEND_DOCKERFILE}" "${BDF}" "backend.Dockerfile"; then
            build_and_export "${CFG_BACKEND_NAME}" "${BDF}" "${BC}" || true
        fi
    fi

    # ──────────────────────────────────
    # [2/2] Frontend
    # ──────────────────────────────────
    echo ""
    echo "──── [2/2] Frontend ────"
    echo ""

    local FC="${WORK_DIR}/frontend-ctx"
    mkdir -p "${FC}"

    if pull_and_extract "${CFG_FRONTEND_NAME}" "${IMAGE_TAG}" extract_frontend "${FC}"; then
        local FDF="${WORK_DIR}/frontend.Dockerfile"
        local HAS_NGX="false"
        [[ -f "${FC}/nginx.conf" ]] && HAS_NGX="true"
        gen_frontend_dockerfile "${FDF}" "${HAS_NGX}"
        build_and_export "${CFG_FRONTEND_NAME}" "${FDF}" "${FC}" || true
    fi

    # ──────────────────────────────────
    # 汇总
    # ──────────────────────────────────
    echo ""
    echo "============================================================"
    echo "  构建完成 (${#OUTPUT_TARS[@]}/2)"
    echo "============================================================"
    echo ""
    for TF in "${OUTPUT_TARS[@]}"; do
        echo "  📦 $(basename "${TF}")  ($(du -sh "${TF}" | cut -f1))"
    done
    echo ""
    echo "  ARM 机器上加载:"
    for TF in "${OUTPUT_TARS[@]}"; do
        echo "    docker load -i $(basename "${TF}")"
    done
    echo ""
}

main "$@"
