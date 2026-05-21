#!/bin/bash
#===============================================================================
#  灵洲 AI 平台 - 一键自动化部署脚本
#  文档来源: http://doc.zhoujusoft.com/docs/ai/start/installation/docker
#
#  功能:
#    1. 环境检测 (OS / CPU / 内存 / 磁盘 / 网络)
#    2. 安装 Docker CE (自动适配 CentOS / Ubuntu / openEuler)
#    3. 配置 Docker (镜像加速 / 私有仓库 / 日志限制)
#    4. 通过 Git 稀疏检出拉取部署文件
#    5. Docker Compose 一键启动
#    6. 健康检查 & 输出访问信息
#
#  用法:
#    chmod +x deploy_lingz.sh
#    sudo ./deploy_lingz.sh [选项]
#
#  选项:
#    --skip-docker       跳过 Docker 安装/配置 (已有 Docker 时使用)
#    --data-root <path>  自定义 Docker 数据存储目录
#    --install-dir <dir> 指定灵洲安装目录 (默认: /opt/lingz)
#    --branch <name>     指定 Git 分支 (默认: main)
#    --help              显示帮助
#===============================================================================

set -euo pipefail

#======================== 可配置变量 ==========================================
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)/lingz"
GIT_REPO="https://gitee.com/zhoujusoft/lingzai.git"
GIT_BRANCH="main"
SPARSE_PATH="deploy/lingz"
CUSTOM_DATA_ROOT=""
SKIP_DOCKER=false

#======================== 颜色输出 ============================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'  # No Color

log_info()    { echo -e "${GREEN}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_step()    { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${CYAN}$*${NC}"; echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }
log_result()  { echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${GREEN}  $*${NC}"; echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

#======================== 帮助信息 ============================================
show_help() {
    cat <<EOF
灵洲 AI 平台 - 一键自动化部署脚本

用法: sudo $0 [选项]

选项:
  --skip-docker       跳过 Docker 安装/配置 (已在服务器上装好 Docker 时使用)
  --data-root <path>  自定义 Docker 数据存储目录 (例: /data2/docker)
  --install-dir <dir> 指定灵洲安装目录 (默认: /opt/lingz)
  --branch <name>     指定 Git 分支 (默认: main)
  --help              显示本帮助信息

示例:
  sudo $0                              # 完整安装
  sudo $0 --skip-docker                # 仅部署灵洲 (已有 Docker)
  sudo $0 --data-root /data2/docker    # 自定义 Docker 存储目录
  sudo $0 --install-dir /home/app/lingz --branch dev  # 自定义目录和分支
EOF
    exit 0
}

#======================== 参数解析 ============================================
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-docker)    SKIP_DOCKER=true; shift ;;
            --data-root)
                CUSTOM_DATA_ROOT="$2"; shift 2 ;;
            --install-dir)
                INSTALL_DIR="$2"; shift 2 ;;
            --branch)
                GIT_BRANCH="$2"; shift 2 ;;
            --help|-h)        show_help ;;
            *)
                log_error "未知参数: $1"
                echo "使用 --help 查看帮助"
                exit 1 ;;
        esac
    done
}

#======================== 环境检测 ============================================
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "请使用 root 权限运行此脚本"
        echo "  sudo $0 $*"
        exit 1
    fi
}

detect_os() {
    log_step "步骤 1/6: 环境检测"

    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        OS_ID="${ID}"
        OS_VERSION="${VERSION_ID}"
        OS_NAME="${PRETTY_NAME}"
    elif [[ -f /etc/redhat-release ]]; then
        OS_ID="centos"
        OS_VERSION=$(cat /etc/redhat-release | grep -oP '[\d]+' | head -1)
        OS_NAME=$(cat /etc/redhat-release)
    else
        log_error "无法识别操作系统"
        exit 1
    fi

    log_info "操作系统: $OS_NAME"
    log_info "系统版本: $OS_VERSION"

    # 判断包管理器
    case "$OS_ID" in
        centos|rhel|rocky|almalinux|ol)
            PKG_MANAGER="yum"
            ;;
        ubuntu|debian|linuxmint)
            PKG_MANAGER="apt"
            ;;
        openeuler|euleros)
            PKG_MANAGER="yum"
            log_warn "检测到华为欧拉系统，将使用 yum 安装 Docker"
            ;;
        fedora)
            PKG_MANAGER="dnf"
            ;;
        *)
            log_error "不支持的操作系统: $OS_ID ($OS_NAME)"
            log_error "当前仅支持: CentOS / RHEL / Rocky / AlmaLinux / Ubuntu / Debian / openEuler"
            exit 1
            ;;
    esac

    log_info "包管理器: $PKG_MANAGER"
}

check_hardware() {
    # CPU
    local cpu_cores
    cpu_cores=$(nproc)
    log_info "CPU 核心数: ${cpu_cores}"
    if [[ $cpu_cores -lt 4 ]]; then
        log_warn "CPU 核心数低于推荐值 (推荐: 8 核)，性能可能不足"
    fi

    # 内存
    local mem_total_gb
    mem_total_gb=$(free -g | awk '/^Mem:/ {print $2}')
    log_info "内存: ${mem_total_gb}G"
    if [[ $mem_total_gb -lt 8 ]]; then
        log_warn "内存低于推荐值 (推荐: 16G)，可能导致服务不稳定"
    fi

    # 磁盘
    local disk_root_gb
    disk_root_gb=$(df -BG / | awk 'NR==2 {print $4}' | tr -d 'G')
    log_info "根分区可用磁盘: ${disk_root_gb}G"
    if [[ $disk_root_gb -lt 50 ]]; then
        log_error "磁盘空间严重不足 (推荐: 200G 可用空间)，请清理或扩容后重试"
        exit 1
    elif [[ $disk_root_gb -lt 100 ]]; then
        log_warn "磁盘空间偏少 (推荐: 200G)，建议清理或扩容"
    fi
}

check_network() {
    log_info "检查网络连通性..."

    # 检查 DNS 解析
    if ! host gitee.com &>/dev/null; then
        log_warn "DNS 解析 gitee.com 失败，可能影响 Git 拉取"
    else
        log_info "DNS 解析: 正常"
    fi

    # 检查 Gitee 可达性
    if curl -sL --connect-timeout 10 -o /dev/null -w "%{http_code}" "https://gitee.com" | grep -qE "^[23]"; then
        log_info "Gitee 访问: 正常"
    else
        log_warn "Gitee 访问异常，请检查网络"
    fi

    # 检查 Docker 镜像源可达性
    if curl -sL --connect-timeout 10 -o /dev/null -w "%{http_code}" "https://registry-1.docker.io/v2/" 2>/dev/null | grep -qE "^[23]"; then
        log_info "Docker Hub 访问: 正常"
    else
        log_warn "Docker Hub 直接访问受限，将通过国内镜像加速拉取"
    fi
}

#======================== 安装 Docker =========================================
install_docker_centos() {
    log_info "使用 yum 安装 Docker CE (CentOS/RHEL 系列)..."

    yum -y update
    yum install -y yum-utils device-mapper-persistent-data lvm2
    yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
    yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl start docker
    systemctl enable docker

    log_info "Docker CE 安装完成"
}

install_docker_ubuntu() {
    log_info "使用 apt 安装 Docker CE (Ubuntu/Debian 系列)..."

    apt-get update
    apt-get -y install apt-transport-https ca-certificates curl software-properties-common gnupg lsb-release

    local arch=$(dpkg --print-architecture)
    # 尝试使用阿里云源
    if curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg 2>/dev/null; then
        echo "deb [arch=${arch} signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" \
            > /etc/apt/sources.list.d/docker.list
    else
        # 回退到官方源
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=${arch} signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
            > /etc/apt/sources.list.d/docker.list
    fi

    apt-get update
    apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl start docker
    systemctl enable docker

    log_info "Docker CE 安装完成"
}

install_docker_fedora() {
    log_info "使用 dnf 安装 Docker CE (Fedora 系列)..."

    dnf -y install dnf-plugins-core
    dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
    dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl start docker
    systemctl enable docker

    log_info "Docker CE 安装完成"
}

install_docker() {
    if $SKIP_DOCKER; then
        log_warn "跳过 Docker 安装 (--skip-docker)"
        if ! command -v docker &>/dev/null; then
            log_error "Docker 未安装，不能跳过此步骤"
            exit 1
        fi
        return
    fi

    if command -v docker &>/dev/null; then
        local docker_ver
        docker_ver=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unknown")
        log_info "检测到 Docker 已安装 (版本: $docker_ver)"

        # 检查 docker compose 插件
        if docker compose version &>/dev/null; then
            log_info "Docker Compose 插件已就绪"
            return
        else
            log_warn "Docker Compose 插件未检测到，将尝试安装"
        fi
    fi

    log_info "开始安装 Docker CE..."

    case "$PKG_MANAGER" in
        yum)  install_docker_centos ;;
        apt)  install_docker_ubuntu ;;
        dnf)  install_docker_fedora ;;
    esac

    # 验证安装
    docker version
    log_info "Docker 版本: $(docker version --format '{{.Server.Version}}')"
}

#======================== 配置 Docker =========================================
configure_docker() {
    if $SKIP_DOCKER; then
        log_warn "跳过 Docker 配置 (--skip-docker)"
        return
    fi

    log_info "配置 Docker daemon..."

    mkdir -p /etc/docker

    # 构建 daemon.json
    local daemon_json
    daemon_json=$(cat <<'DAEMON_EOF'
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
DAEMON_EOF
)

    # 如果指定了自定义 data-root，合并配置
    if [[ -n "$CUSTOM_DATA_ROOT" ]]; then
        log_info "自定义 Docker 数据目录: $CUSTOM_DATA_ROOT"
        mkdir -p "$CUSTOM_DATA_ROOT"
        daemon_json=$(echo "$daemon_json" | python3 -c "
import sys, json
config = json.load(sys.stdin)
config['data-root'] = '$CUSTOM_DATA_ROOT'
print(json.dumps(config, indent=2, ensure_ascii=False))
" 2>/dev/null || {
            log_warn "python3 不可用，使用 sed 注入 data-root"
            echo "$daemon_json" | sed "s|{|{  \\n  \"data-root\": \"$CUSTOM_DATA_ROOT\",|"
        })
    fi

    echo "$daemon_json" > /etc/docker/daemon.json
    log_info "daemon.json 内容:"
    cat /etc/docker/daemon.json

    systemctl daemon-reload
    systemctl restart docker
    log_info "Docker 配置完成并已重启"
}

#======================== Git 拉取部署文件 =====================================
git_clone_deploy() {
    log_info "准备拉取灵洲部署文件..."

    # 确保 git 已安装
    if ! command -v git &>/dev/null; then
        log_info "安装 git..."
        case "$PKG_MANAGER" in
            yum|dnf) ${PKG_MANAGER} install -y git ;;
            apt)     apt-get install -y git ;;
        esac
    fi
    log_info "Git 版本: $(git --version)"

    # 创建安装目录
    if [[ -d "$INSTALL_DIR" ]]; then
        log_warn "安装目录 $INSTALL_DIR 已存在"
        read -rp "是否清除并重新部署？(y/N): " confirm
        if [[ "$confirm" =~ ^[Yy]$ ]]; then
            log_info "清除旧目录..."
            # 先停止可能运行的容器
            if [[ -f "$INSTALL_DIR/deploy/lingz/docker-compose.yml" ]] || [[ -f "$INSTALL_DIR/deploy/lingz/docker-compose.yaml" ]]; then
                cd "$INSTALL_DIR/deploy/lingz" && docker compose down 2>/dev/null || true
            fi
            rm -rf "$INSTALL_DIR"
        else
            log_info "保留现有目录，尝试更新..."
            cd "$INSTALL_DIR"
            git fetch origin "${GIT_BRANCH}"
            git reset --hard "origin/${GIT_BRANCH}"
            cd "deploy/${SPARSE_PATH#*/}"
            return
        fi
    fi

    mkdir -p "$INSTALL_DIR"
    cd "$INSTALL_DIR"

    # 初始化仓库
    git init
    git remote add origin "$GIT_REPO"

    # 稀疏检出
    git config core.sparsecheckout true
    mkdir -p .git/info
    echo "${SPARSE_PATH}/" > .git/info/sparse-checkout

    # 拉取代码
    log_info "从 ${GIT_REPO} 拉取分支 ${GIT_BRANCH} (稀疏检出: ${SPARSE_PATH}/)..."
    git pull origin "$GIT_BRANCH"

    if [[ ! -d "${SPARSE_PATH}" ]]; then
        log_error "拉取失败，目录 ${SPARSE_PATH} 不存在"
        log_error "请检查 Git 仓库地址和分支是否正确"
        exit 1
    fi

    cd "${SPARSE_PATH}"
    log_info "部署文件就绪: $(pwd)"
    log_info "文件列表:"
    ls -la
}

#======================== 启动服务 ============================================
start_services() {
    local compose_dir="$INSTALL_DIR/deploy/lingz"
    cd "$compose_dir"

    # 检查 docker-compose 文件
    local compose_file=""
    if [[ -f "docker-compose.yml" ]]; then
        compose_file="docker-compose.yml"
    elif [[ -f "docker-compose.yaml" ]]; then
        compose_file="docker-compose.yaml"
    elif [[ -f "compose.yml" ]]; then
        compose_file="compose.yml"
    elif [[ -f "compose.yaml" ]]; then
        compose_file="compose.yaml"
    else
        log_error "未找到 docker-compose 配置文件"
        exit 1
    fi

    log_info "使用 compose 文件: $compose_file"
    log_info "开始拉取镜像并启动服务 (首次启动可能较慢)..."

    docker compose up -d

    log_info "服务启动命令执行完毕"
}

#======================== 健康检查 ============================================
# 判断单个容器是否就绪:
#   - 有 healthcheck → 等待状态为 healthy
#   - 无 healthcheck → 状态为 running 即视为就绪
is_container_ready() {
    local name="$1"
    local state health has_healthcheck

    state=$(docker inspect --format '{{.State.Status}}' "$name" 2>/dev/null) || return 1

    # 容器未在运行 → 未就绪
    [[ "$state" != "running" ]] && return 1

    # 检查是否定义了 healthcheck
    has_healthcheck=$(docker inspect --format '{{if .Config.Healthcheck}}yes{{else}}no{{end}}' "$name" 2>/dev/null)

    if [[ "$has_healthcheck" == "yes" ]]; then
        health=$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null)
        [[ "$health" == "healthy" ]]
    else
        # 没有 healthcheck 的容器，running 就算就绪
        return 0
    fi
}

health_check() {
    local compose_dir="$INSTALL_DIR/deploy/lingz"
    cd "$compose_dir"

    log_info "等待容器启动 (最多等待 5 分钟)..."

    local max_attempts=60
    local interval=5
    local attempt=1
    local all_healthy=false

    while [[ $attempt -le $max_attempts ]]; do
        local not_ready=""
        local total=0

        while IFS= read -r name; do
            [[ -z "$name" ]] && continue
            total=$((total + 1))
            if ! is_container_ready "$name"; then
                not_ready="$not_ready $name"
            fi
        done < <(docker compose ps --format '{{.Name}}' 2>/dev/null)

        if [[ -z "$not_ready" ]]; then
            all_healthy=true
            break
        fi

        log_info "[$attempt/$max_attempts] ${total} 个容器中，以下尚未就绪:${not_ready}"
        sleep "$interval"
        attempt=$((attempt + 1))
    done

    if $all_healthy; then
        log_info "所有容器已就绪!"
    else
        log_warn "部分容器在超时时间内未就绪，但服务可能仍在启动中"
        log_warn "可使用以下命令手动查看状态:"
        echo "  cd $compose_dir && docker compose ps"
    fi

    # 显示容器状态
    echo ""
    log_info "当前容器状态:"
    docker compose ps 2>/dev/null || docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

#======================== 输出访问信息 ========================================
print_summary() {
    local ip_addr
    ip_addr=$(hostname -I 2>/dev/null | awk '{print $1}')
    [[ -z "$ip_addr" ]] && ip_addr=$(curl -sL --connect-timeout 5 ifconfig.me 2>/dev/null || echo "未知")

    # 尝试获取前端端口
    local frontend_port=""
    local compose_dir="$INSTALL_DIR/deploy/lingz"
    cd "$compose_dir"
    frontend_port=$(docker compose ps --format '{{.Name}} {{.Ports}}' 2>/dev/null | grep -i frontend | grep -oP '\d+(?=->)' | head -1)
    [[ -z "$frontend_port" ]] && frontend_port="80"

    log_result "灵洲 AI 平台部署完成!"
    echo ""
    echo -e "  ${CYAN}访问地址:${NC}  http://${ip_addr}:${frontend_port}"
    echo -e "  ${CYAN}登录账号:${NC}  admin"
    echo -e "  ${CYAN}默认密码:${NC}  admin123456"
    echo -e "  ${CYAN}部署目录:${NC}  ${INSTALL_DIR}/deploy/lingz"
    echo -e "  ${CYAN}数据目录:${NC}  ${INSTALL_DIR}/deploy/lingz/data/"
    echo ""
    echo -e "  ${YELLOW}安全提醒: 登录后请立即修改默认密码!${NC}"
    echo ""
    echo "  常用命令:"
    echo "    查看日志:   cd $compose_dir && docker compose logs backend -f"
    echo "    停止服务:   cd $compose_dir && docker compose down"
    echo "    重启服务:   cd $compose_dir && docker compose restart"
    echo "    查看状态:   cd $compose_dir && docker compose ps"
    echo ""
}

#======================== 记录日志 ============================================
setup_logging() {
    LOG_FILE="/var/log/lingz_deploy_$(date +%Y%m%d_%H%M%S).log"
    exec > >(tee -a "$LOG_FILE") 2>&1
    log_info "部署日志: $LOG_FILE"
}

#======================== 主流程 ==============================================
main() {
    parse_args "$@"
    setup_logging

    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║          灵洲 AI 平台 - 一键自动化部署脚本              ║"
    echo "║          文档: http://doc.zhoujusoft.com               ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""

    check_root

    # 步骤 1: 环境检测
    detect_os
    check_hardware
    check_network

    # 步骤 2: 安装 Docker
    log_step "步骤 2/6: 安装 Docker CE"
    install_docker

    # 步骤 3: 配置 Docker
    log_step "步骤 3/6: 配置 Docker"
    configure_docker

    # 步骤 4: 拉取部署文件
    log_step "步骤 4/6: 拉取灵洲部署文件 (Git)"
    git_clone_deploy

    # 步骤 5: 启动服务
    log_step "步骤 5/6: 启动灵洲 AI 平台"
    start_services

    # 步骤 6: 健康检查 & 输出结果
    log_step "步骤 6/6: 健康检查"
    health_check
    print_summary
}

main "$@"
