#!/bin/bash
#===============================================================================
#  灵洲 AI 平台 - 一键自动化部署脚本 v20160701
#  文档来源: http://doc.zhoujusoft.com/docs/ai/start/installation/docker
#
#  功能:
#    1. 环境检测 (OS / CPU / 内存 / 磁盘 / 网络)
#    2. 安装 Docker CE (自动适配 CentOS / Ubuntu / openEuler)
#    3. 配置 Docker (镜像加速 / 私有仓库 / 日志限制)
#    4. 通过 Git clone 拉取部署文件
#    5. Docker Compose 一键启动
#    6. 健康检查 & 输出访问信息
#
#  用法:
#    chmod +x deploy_lingz.sh
#    sudo ./deploy_lingz.sh [选项]
#
#  选项:
#    --skip-docker       跳过 Docker 安装/配置 (已有 Docker 时使用)
#    --install-dir <dir> 指定灵洲安装目录 (默认: 脚本目录/lingz)
#    --help              显示帮助
#===============================================================================

set -euo pipefail

#======================== 可配置变量 ==========================================
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)/lingz"
GIT_REPO="https://gitee.com/zhoujusoft/lingzai.git"
GIT_BRANCH="main"
SPARSE_PATH="deploy/lingz"  # git 仓库中的原始路径（clone 后提取）
IMAGE_TAG=""           # 指定镜像版本，非空时自动修改 .env 中的 IMAGE_TAG
SKIP_DOCKER=false
COMPOSE_CMD="docker compose"   # 自动检测: docker compose (v2) 或 docker-compose (v1)

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

用法: $0 [选项] (无需 root，脚本会自动 sudo)

选项:
  --skip-docker        跳过 Docker 安装/配置 (已在服务器上装好 Docker 时使用)
  --install-dir <dir>  指定灵洲安装目录 (默认: 脚本所在目录/lingz)
  --help               显示本帮助信息

示例:
  $0                                # 完整安装 (交互式引导输入版本)
  $0 --skip-docker                  # 仅部署灵洲 (已有 Docker)

非交互式运行 (curl | bash):
  curl -sSL https://gitee.com/zhoujusoft/lingzai/raw/main/deploy/lingz/deploy_lingz.sh | sudo bash -s -- --skip-docker
EOF
    exit 0
}

#======================== 参数解析 ============================================
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-docker)    SKIP_DOCKER=true; shift ;;
            --install-dir)
                INSTALL_DIR="$2"; shift 2 ;;
            --help|-h)        show_help ;;
            *)
                log_error "未知参数: $1"
                echo "使用 --help 查看帮助"
                exit 1 ;;
        esac
    done
}

#======================== 环境检测 ============================================

detect_os() {
    log_step "步骤 1/7: 环境检测"

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
    if [[ $disk_root_gb -lt 10 ]]; then
        log_error "磁盘空间不足 (至少需要 10G 可用空间)，请清理或扩容后重试"
        exit 1
    elif [[ $disk_root_gb -lt 20 ]]; then
        log_warn "磁盘空间偏少 (推荐: 20G 以上)，建议清理或扩容"
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

    sudo yum -y update
    sudo yum install -y yum-utils device-mapper-persistent-data lvm2
    sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
    sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo systemctl start docker
    sudo systemctl enable docker

    log_info "Docker CE 安装完成"
}

install_docker_ubuntu() {
    log_info "使用 apt 安装 Docker CE (Ubuntu/Debian 系列)..."

    sudo apt-get update
    sudo apt-get -y install apt-transport-https ca-certificates curl software-properties-common gnupg lsb-release

    local arch=$(dpkg --print-architecture)
    # 尝试使用阿里云源
    if curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg 2>/dev/null; then
        echo "deb [arch=${arch} signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" \
            | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    else
        # 回退到官方源
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=${arch} signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
            | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    fi

    sudo apt-get update
    sudo apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo systemctl start docker
    sudo systemctl enable docker

    log_info "Docker CE 安装完成"
}

install_docker_fedora() {
    log_info "使用 dnf 安装 Docker CE (Fedora 系列)..."

    sudo dnf -y install dnf-plugins-core
    sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo systemctl start docker
    sudo systemctl enable docker

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

        # 检查 $COMPOSE_CMD（兼容 v2 插件和 v1 独立版）
        if $COMPOSE_CMD version &>/dev/null; then
            log_info "Docker Compose (插件版) 已就绪"
            return
        elif docker-compose --version &>/dev/null; then
            COMPOSE_CMD="docker-compose"
            log_info "Docker Compose (独立版) 已就绪"
            return
        else
            log_warn "Docker Compose 未检测到，将尝试安装"
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
    # --skip-docker 时直接跳过
    if $SKIP_DOCKER; then
        log_warn "跳过 Docker 配置 (--skip-docker)"
        return
    fi

    # Docker 未安装时也跳过（install_docker 可能因已存在而未实际执行）
    if ! command -v docker &>/dev/null; then
        log_warn "Docker 未安装，跳过配置"
        return
    fi

    # daemon.json 已包含私有仓库地址，说明之前已配置过
    if [[ -f /etc/docker/daemon.json ]] && grep -q "125.75.152.167:5001" /etc/docker/daemon.json 2>/dev/null; then
        log_info "Docker daemon.json 已存在且包含私有仓库配置，跳过重新配置"
        return
    fi

    log_info "配置 Docker daemon..."

    sudo mkdir -p /etc/docker

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

    echo "$daemon_json" | sudo tee /etc/docker/daemon.json > /dev/null
    log_info "daemon.json 内容:"
    cat /etc/docker/daemon.json

    sudo systemctl daemon-reload
    sudo systemctl restart docker
    log_info "Docker 配置完成并已重启"
}

#======================== DNS 修复辅助 ==========================================
fix_gitee_dns() {
    log_info "尝试解析 gitee.com 的 IP 地址..."

    # 尝试多种方式获取 IP
    local gitee_ip=""
    # 方式1: 使用公共 DNS over HTTPS (Cloudflare)
    gitee_ip=$(curl -fs --connect-timeout 10 "https://cloudflare-dns.com/dns-query?name=gitee.com&type=A" \
        -H "accept: application/dns-json" 2>/dev/null | grep -oP '"data"\s*:\s*"\K[^"]+' | head -1)

    # 方式2: 使用 Google DoH
    if [[ -z "$gitee_ip" ]]; then
        gitee_ip=$(curl -fs --connect-timeout 10 "https://dns.google/resolve?name=gitee.com&type=A" 2>/dev/null \
            | grep -oP '"data"\s*:\s*"\K[^"]+' | head -1)
    fi

    # 方式3: 使用 nslookup + 公共 DNS (如果可用)
    if [[ -z "$gitee_ip" ]]; then
        gitee_ip=$(nslookup gitee.com 114.114.114.114 2>/dev/null | grep -oP 'Address:\s*\K[\d.]+' | tail -1)
    fi

    # 方式4: 使用 nslookup + 8.8.8.8
    if [[ -z "$gitee_ip" ]]; then
        gitee_ip=$(nslookup gitee.com 8.8.8.8 2>/dev/null | grep -oP 'Address:\s*\K[\d.]+' | tail -1)
    fi

    if [[ -z "$gitee_ip" ]]; then
        log_error "所有 DNS 解析方式均失败，请手动在网络正常的机器上查询后添加到 /etc/hosts"
        echo "  命令: sudo sh -c 'echo <GITEE_IP> gitee.com >> /etc/hosts'"
        return 1
    fi

    log_info "解析到 gitee.com IP: $gitee_ip"

    # 写入 /etc/hosts（避免重复）
    if grep -q 'gitee.com' /etc/hosts 2>/dev/null; then
        log_info "/etc/hosts 中已存在 gitee.com 条目，更新 IP..."
        sudo sed -i "s/^.*gitee.com.*$/${gitee_ip} gitee.com/" /etc/hosts
    else
        echo "${gitee_ip} gitee.com" | sudo tee -a /etc/hosts > /dev/null
        log_info "已将 gitee.com (${gitee_ip}) 写入 /etc/hosts"
    fi

    # 验证
    if ping -c 1 -W 3 gitee.com &>/dev/null; then
        log_info "DNS 修复成功！gitee.com 现已可达"
        return 0
    else
        log_warn "/etc/hosts 已更新但仍无法 ping 通，IP 可能不可用或被防火墙拦截"
        return 1
    fi
}

#======================== Git clone 拉取部署文件 =====================================
git_clone_deploy() {
    log_info "准备拉取灵洲部署文件..."

    # 确保 git 已安装
    if ! command -v git &>/dev/null; then
        log_info "安装 git..."
        case "$PKG_MANAGER" in
            yum|dnf) sudo ${PKG_MANAGER} install -y git ;;
            apt)     sudo apt-get install -y git ;;
        esac
    fi
    log_info "Git 版本: $(git --version)"

    # 禁止 git 弹出交互式认证提示
    export GIT_TERMINAL_PROMPT=0
    export GIT_ASKPASS=echo

    # 处理已存在的安装目录
    if [[ -d "$INSTALL_DIR" ]]; then
        log_warn "安装目录 $INSTALL_DIR 已存在"
        read -rp "是否清除并重新部署？(y/N): " confirm
        if [[ "$confirm" =~ ^[Yy]$ ]]; then
            log_info "清除旧目录..."
            local compose_yml="$INSTALL_DIR/docker-compose.yml"
            local compose_yaml="$INSTALL_DIR/docker-compose.yaml"
            if [[ -f "$compose_yml" ]] || [[ -f "$compose_yaml" ]]; then
                cd "$INSTALL_DIR" && $COMPOSE_CMD down 2>/dev/null || true
            fi
            rm -rf "$INSTALL_DIR"
        else
            log_info "保留现有部署目录，退出"
            exit 0
        fi
    fi

    # 检查 DNS 解析
    if ! host gitee.com &>/dev/null && ! getent hosts gitee.com &>/dev/null; then
        log_error "DNS 解析失败: 无法解析 gitee.com"
        echo ""
        echo "  可能的原因及解决方法:"
        echo "  1) 服务器未配置 DNS 或 DNS 服务不可用"
        echo "     检查: cat /etc/resolv.conf"
        echo "     修复: sudo sh -c 'echo \"nameserver 8.8.8.8\" >> /etc/resolv.conf'"
        echo ""
        echo "  2) 防火墙阻止了 DNS 查询 (UDP 53)"
        echo ""
        echo "  3) 脚本可尝试将 gitee.com 的 IP 写入 /etc/hosts 绕过 DNS"
        read -rp "  是否尝试自动修复 (解析 IP 并写入 /etc/hosts)? (y/N): " auto_fix_dns
        if [[ "$auto_fix_dns" =~ ^[Yy]$ ]]; then
            fix_gitee_dns || { log_error "DNS 修复失败，无法继续"; exit 1; }
        else
            log_error "DNS 不可用，无法 git clone，请先修复 DNS"
            exit 1
        fi
    fi

    # === 克隆到临时目录，提取后删除临时目录 ===
    local install_parent="$(dirname "$INSTALL_DIR")"
    local clone_dir="${install_parent}/_lingz_clone_tmp"

    log_info "从 Gitee 克隆代码: $GIT_REPO (分支: $GIT_BRANCH, 深度: 1) ..."
    if ! git clone --depth=1 "$GIT_REPO" "$clone_dir" 2>&1; then
        rm -rf "$clone_dir"
        log_error "git clone 失败"
        log_error "请检查:"
        echo "  1. 服务器是否能访问外网: curl -I https://gitee.com"
        echo "  2. DNS 是否正常: ping gitee.com 或 host gitee.com"
        echo "  3. 如 DNS 失败，可手动添加: sudo sh -c 'echo <IP> gitee.com >> /etc/hosts'"
        echo "  4. 确认仓库为公开仓库（不需要登录）"
        echo "  5. 检查是否有 git 凭证缓存导致认证弹窗: git config --global credential.helper"
        exit 1
    fi

    # 提取 deploy/lingz → ling-z（平行于克隆目录），删除克隆目录，改名为 lingz
    extract_and_cleanup "$clone_dir" "$install_parent"

    cd "$INSTALL_DIR"

    # 验证部署文件存在
    if [[ ! -f "$INSTALL_DIR/docker-compose.yml" ]] && [[ ! -f "$INSTALL_DIR/docker-compose.yaml" ]] \
       && [[ ! -f "$INSTALL_DIR/compose.yml" ]] && [[ ! -f "$INSTALL_DIR/compose.yaml" ]]; then
        log_error "拉取失败，部署文件不存在"
        log_error "请检查仓库地址 (${GIT_REPO}) 是否正确"
        exit 1
    fi

    log_info "部署文件就绪: $INSTALL_DIR"
    log_info "文件列表:"
    ls -la "$INSTALL_DIR"
}

#======================== 提取 deploy/lingz → 清理 → 重命名 =====================
# 流程:
#   1. 删除 clone_dir 内除 deploy/ 外的所有内容
#   2. mv deploy/lingz → ling-z（平行于 clone_dir）
#   3. 删除 deploy/ 目录
#   4. 删除 clone_dir（临时克隆目录）
#   5. mv ling-z → lingz（最终安装目录）
extract_and_cleanup() {
    local clone_dir="$1"       # 临时克隆目录 (如 _lingz_clone_tmp)
    local parent_dir="$2"      # clone_dir 和 ling-z/lingz 所在的父目录

    local src_path="${clone_dir}/${SPARSE_PATH}"   # deploy/lingz
    local staging_dir="${parent_dir}/ling-z"        # 平行于克隆目录

    if [[ ! -d "$src_path" ]]; then
        log_error "仓库中 ${SPARSE_PATH} 目录不存在"
        rm -rf "$clone_dir"
        return 1
    fi

    # 第一步：删除 clone_dir 内除 deploy/ 外的所有文件和目录
    log_info "清理无关代码: 删除除 deploy/ 外的所有内容 ..."
    for item in "${clone_dir}"/* "${clone_dir}"/.[!.]* "${clone_dir}/..?*"; do
        [[ ! -e "$item" ]] && continue
        local name="$(basename "$item")"
        [[ "$name" == "deploy" ]] && continue
        rm -rf "$item"
    done

    log_info "已删除无关文件，clone_dir 内仅剩 deploy/"

    # 第二步：将 deploy/lingz 移到 ling-z（与克隆目录平行）
    log_info "移动 ${SPARSE_PATH} → ling-z/ (平行于克隆目录) ..."
    mv "$src_path" "$staging_dir"

    # 第三步：删除 deploy/ 目录
    log_info "删除 deploy/ 目录 ..."
    rm -rf "${clone_dir}/deploy"

    # 第四步：删除整个克隆临时目录
    log_info "删除临时克隆目录 ..."
    rm -rf "$clone_dir"

    # 第五步：将 ling-z 改名为 lingz
    log_info "重命名 ling-z → lingz ..."
    mv "$staging_dir" "$INSTALL_DIR"

    log_info "提取完成: 部署文件已就绪于 $INSTALL_DIR"
}

#======================== 配置 .env ==========================================
configure_env() {
    local env_file="$INSTALL_DIR/.env"

    if [[ ! -f "$env_file" ]]; then
        log_warn ".env 文件不存在 ($env_file)，跳过版本配置"
        return
    fi

    # 读取当前版本
    local current_tag
    current_tag=$(grep '^IMAGE_TAG=' "$env_file" | cut -d'=' -f2)
    log_info "当前 .env 中的 IMAGE_TAG: ${current_tag}"

    # 用户指定了版本则修改
    if [[ -n "$IMAGE_TAG" ]]; then
        log_info "修改 IMAGE_TAG: ${current_tag} → ${IMAGE_TAG}"
        sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" "$env_file"
        log_info ".env 版本已更新为: ${IMAGE_TAG}"
    else
        log_info "未指定版本，使用 .env 默认版本: ${current_tag}"
    fi

    # 显示关键配置
    log_info ".env 关键配置:"
    grep -E '^(IMAGE_TAG|REGISTRY|FRONTEND_IMAGE_NAME|BACKEND_IMAGE_NAME|FRONTEND_PORT)=' "$env_file"
}

#======================== 启动服务 ============================================
start_services() {
    local compose_dir="$INSTALL_DIR"
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

    $COMPOSE_CMD up -d

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
    local compose_dir="$INSTALL_DIR"
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
        done < <($COMPOSE_CMD ps --format '{{.Name}}' 2>/dev/null)

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
        echo "  cd $compose_dir && $COMPOSE_CMD ps"
    fi

    # 显示容器状态
    echo ""
    log_info "当前容器状态:"
    $COMPOSE_CMD ps 2>/dev/null || docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

#======================== 输出访问信息 ========================================
print_summary() {
    local ip_addr
    ip_addr=$(hostname -I 2>/dev/null | awk '{print $1}')
    [[ -z "$ip_addr" ]] && ip_addr=$(curl -sL --connect-timeout 5 ifconfig.me 2>/dev/null || echo "未知")

    # 尝试获取前端端口
    local frontend_port=""
    local compose_dir="$INSTALL_DIR"
    cd "$compose_dir"
    frontend_port=$($COMPOSE_CMD ps --format '{{.Name}} {{.Ports}}' 2>/dev/null | grep -i frontend | grep -oP '\d+(?=->)' | head -1)
    [[ -z "$frontend_port" ]] && frontend_port="80"

    local deployed_ver
    if [[ -n "$IMAGE_TAG" ]]; then
        deployed_ver="$IMAGE_TAG"
    else
        # 从 .env 读取实际版本
        deployed_ver=$(grep '^IMAGE_TAG=' "$compose_dir/.env" 2>/dev/null | cut -d'=' -f2 || echo "unknown")
    fi

    log_result "灵洲 AI 平台部署完成!"
    echo ""
    echo -e "  ${CYAN}访问地址:${NC}  http://${ip_addr}:${frontend_port}"
    echo -e "  ${CYAN}部署版本:${NC}  ${deployed_ver}"
    echo -e "  ${CYAN}登录账号:${NC}  admin"
    echo -e "  ${CYAN}默认密码:${NC}  admin123456"
    echo -e "  ${CYAN}部署目录:${NC}  ${INSTALL_DIR}"
    echo -e "  ${CYAN}数据目录:${NC}  ${INSTALL_DIR}/data/"
    echo ""
    echo -e "  ${YELLOW}安全提醒: 登录后请立即修改默认密码!${NC}"
    echo ""
    echo "  常用命令:"
    echo "    查看日志:   cd $compose_dir && $COMPOSE_CMD logs backend -f"
    echo "    停止服务:   cd $compose_dir && $COMPOSE_CMD down"
    echo "    重启服务:   cd $compose_dir && $COMPOSE_CMD restart"
    echo "    查看状态:   cd $compose_dir && $COMPOSE_CMD ps"
    echo ""
}

#======================== 参数引导 ============================================
prompt_args() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║          灵洲 AI 平台 - 一键自动化部署脚本              ║"
    echo "║          文档: http://doc.zhoujusoft.com               ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    echo "可用参数:"
    echo "  --skip-docker        跳过 Docker 安装/配置 (已有 Docker 时使用)"
    echo "  --install-dir <dir>  指定灵洲安装目录 (默认: 脚本目录/lingz)"
    echo ""
    echo "非交互式运行 (curl | bash):"
    echo "  curl -sSL https://gitee.com/zhoujusoft/lingzai/raw/main/deploy/lingz/deploy_lingz.sh | sudo bash -s -- --skip-docker"
    echo ""
    echo "──────────────────────────────────────────────────────────"
    echo ""

    # 非交互式（管道/cron）直接走默认配置，避免 read 读取 EOF 导致退出
    if [[ ! -t 0 ]]; then
        log_warn "检测到非交互式运行（stdin 不是终端），将使用默认配置继续部署"
        log_info "如需自定义参数，请下载脚本后本地运行，或在上面的 curl 命令末尾添加参数"
        return
    fi

    # 交互式询问（所有输入直接赋值变量）
    local input_version=""
    local input_skip_docker=""

    # 镜像版本（直接赋值变量，不再通过命令行参数传递）
    read -rp "请输入镜像版本 IMAGE_TAG (如 1.8.7，留空则使用 .env 默认版本): " input_version
    if [[ -n "$input_version" ]]; then
        IMAGE_TAG="$input_version"
    fi

    # 是否跳过 Docker
    read -rp "是否跳过 Docker 安装/配置? (服务器已装好 Docker 输入 y，否则留空): " input_skip_docker
    if [[ "$input_skip_docker" =~ ^[Yy]$ ]]; then
        SKIP_DOCKER=true
    fi

    # 自定义安装目录
    local input_dir=""
    read -rp "自定义安装目录? (留空则用默认 ./lingz): " input_dir
    if [[ -n "$input_dir" ]]; then
        INSTALL_DIR="$input_dir"
    fi

    echo ""
    log_info "将使用参数: IMAGE_TAG=${IMAGE_TAG:-默认} SKIP_DOCKER=$SKIP_DOCKER INSTALL_DIR=$INSTALL_DIR"
    echo ""

    # 只处理仍需 parse_args 的参数
    if $SKIP_DOCKER; then
        parse_args --skip-docker
    fi
}

#======================== 记录日志 ============================================
setup_logging() {
    LOG_FILE="/var/log/lingz_deploy_$(date +%Y%m%d_%H%M%S).log"
    exec > >(tee -a "$LOG_FILE") 2>&1
    log_info "部署日志: $LOG_FILE"
}

#======================== 主流程 ==============================================
main() {
    # 无参数时进入交互式引导
    if [[ $# -eq 0 ]]; then
        prompt_args
    else
        parse_args "$@"
    fi

    setup_logging

    # 步骤 1: 环境检测
    detect_os
    check_hardware
    check_network

    # 步骤 2: 安装 Docker
    log_step "步骤 2/7: 安装 Docker CE"
    install_docker

    # 步骤 3: 配置 Docker
    log_step "步骤 3/7: 配置 Docker"
    configure_docker

    # 步骤 4: 拉取部署文件
    log_step "步骤 4/7: 拉取灵洲部署文件 (Git clone)"
    git_clone_deploy

    # 步骤 5: 配置版本
    log_step "步骤 5/7: 配置部署版本 (.env)"
    configure_env

    # 步骤 6: 启动服务
    log_step "步骤 6/7: 启动灵洲 AI 平台"
    start_services

    # 步骤 7: 健康检查 & 输出结果
    log_step "步骤 7/7: 健康检查"
    health_check
    print_summary
}

main "$@"
