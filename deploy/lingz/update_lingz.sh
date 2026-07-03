#!/bin/bash
#===============================================================================
#  灵洲 AI 平台 - 版本更新脚本 v20260703
#
#  功能:
#    1. 自动查找系统中已部署的灵洲目录 (含 docker-compose.yml + .env)
#    2. 用户确认目录或手动输入
#    3. 读取当前版本，输入目标版本
#    4. 修改 .env 中的 IMAGE_TAG
#    5. 拉取新版本镜像并重启服务
#    6. 健康检查 & 输出结果
#
#  用法:
#    chmod +x update_lingz.sh
#    sudo ./update_lingz.sh [选项]
#
#  选项:
#    --dir <目录>         指定灵洲部署目录 (跳过自动查找)
#    --tag <版本号>       指定目标版本 (如 1.8.8，跳过交互输入)
#    --help               显示帮助
#
#  curl 管道运行:
#    curl -sSL <url> | sudo bash
#    curl -sSL <url> | sudo bash -s -- --dir /opt/lingz --tag 1.8.8
#===============================================================================

set -euo pipefail

#======================== 可配置变量 ==========================================
DEPLOY_DIR=""          # 灵洲部署目录 (自动查找或手动指定)
NEW_TAG=""             # 目标版本号
COMPOSE_CMD="docker compose"   # 自动检测: docker compose (v2) 或 docker-compose (v1)

# 搜索范围 (常见部署路径)
SEARCH_PATHS=("/opt" "/home" "/root" "/data" "/usr/local" "/srv" "$(cd "$(dirname "$0")" 2>/dev/null && pwd)")

#======================== 颜色输出 ============================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO]${NC}  $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $*"; }
log_step()    { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${CYAN}$*${NC}"; echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }
log_result()  { echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${GREEN}  $*${NC}"; echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

#======================== 帮助信息 ============================================
show_help() {
    cat <<EOF
灵洲 AI 平台 - 版本更新脚本

用法: $0 [选项]

选项:
  --dir <目录>       指定灵洲部署目录 (跳过自动查找)
  --tag <版本号>     指定目标版本 (如 1.8.8，跳过交互输入)
  --help             显示本帮助信息

示例:
  $0                              # 交互式: 自动查找目录，提示输入版本
  $0 --tag 1.8.8                  # 指定版本，自动查找目录
  $0 --dir /opt/lingz --tag 1.8.8 # 指定目录和版本 (完全非交互)

curl 管道运行 (仍会提示输入版本号):
  curl -sSL <url> | sudo bash

完全非交互式:
  curl -sSL <url> | sudo bash -s -- --dir /opt/lingz --tag 1.8.8
EOF
    exit 0
}

#======================== 参数解析 ============================================
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dir)            DEPLOY_DIR="$2"; shift 2 ;;
            --tag|--image-tag) NEW_TAG="$2"; shift 2 ;;
            --help|-h)        show_help ;;
            *)
                log_error "未知参数: $1"
                echo "使用 --help 查看帮助"
                exit 1 ;;
        esac
    done
}

#======================== 检测 Docker Compose 命令 ============================
detect_compose_cmd() {
    if docker compose version &>/dev/null; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &>/dev/null && docker-compose version &>/dev/null; then
        COMPOSE_CMD="docker-compose"
    else
        log_error "未检测到 Docker Compose，请先安装"
        exit 1
    fi
    log_info "Docker Compose: $($COMPOSE_CMD version --short 2>/dev/null || echo '已就绪')"
}

#======================== 验证目录是否为灵洲部署目录 ============================
is_lingz_deploy_dir() {
    local dir="$1"
    # 必须同时存在 docker-compose 文件和 .env
    local has_compose=false
    [[ -f "$dir/docker-compose.yml" ]] || [[ -f "$dir/docker-compose.yaml" ]] \
        || [[ -f "$dir/compose.yml" ]] || [[ -f "$dir/compose.yaml" ]] && has_compose=true
    if ! $has_compose; then
        return 1
    fi
    # .env 中包含 IMAGE_TAG 才认为是灵洲部署目录
    if [[ -f "$dir/.env" ]] && grep -q '^IMAGE_TAG=' "$dir/.env" 2>/dev/null; then
        return 0
    fi
    return 1
}

#======================== 查找灵洲部署目录 ====================================
find_deploy_dirs() {
    local found_dirs=()

    log_info "正在系统中查找灵洲部署目录..."

    for search_path in "${SEARCH_PATHS[@]}"; do
        [[ ! -d "$search_path" ]] && continue
        # 限制搜索深度为 4 层，避免耗时过长
        while IFS= read -r dir; do
            found_dirs+=("$dir")
        done < <(find "$search_path" -maxdepth 4 -name ".env" -type f 2>/dev/null \
            | while read -r env_file; do
                dir="$(dirname "$env_file")"
                if is_lingz_deploy_dir "$dir"; then
                    echo "$dir"
                fi
            done)
    done

    # 去重
    if [[ ${#found_dirs[@]} -gt 0 ]]; then
        local unique_dirs=()
        declare -A seen
        for d in "${found_dirs[@]}"; do
            [[ -n "${seen[$d]:-}" ]] && continue
            seen[$d]=1
            unique_dirs+=("$d")
        done
        found_dirs=("${unique_dirs[@]}")
    fi

    echo "${found_dirs[@]}"
}

#======================== 读取当前版本号 ======================================
get_current_tag() {
    local env_file="$1/.env"
    if [[ -f "$env_file" ]]; then
        grep '^IMAGE_TAG=' "$env_file" 2>/dev/null | cut -d'=' -f2 | tr -d '[:space:]'
    else
        echo ""
    fi
}

#======================== 交互式选择目录 ======================================
prompt_deploy_dir() {
    log_step "步骤 1/5: 查找并确认部署目录"

    # 如果命令行已指定目录，直接验证
    if [[ -n "$DEPLOY_DIR" ]]; then
        if is_lingz_deploy_dir "$DEPLOY_DIR"; then
            log_info "已指定部署目录: $DEPLOY_DIR"
            return
        else
            log_error "指定目录不是有效的灵洲部署目录: $DEPLOY_DIR"
            log_error "需包含 docker-compose.yml 和 .env (含 IMAGE_TAG)"
            exit 1
        fi
    fi

    # 自动查找
    local found_str
    found_str=$(find_deploy_dirs)
    read -ra found_dirs <<< "$found_str"

    echo ""
    if [[ ${#found_dirs[@]} -eq 0 ]]; then
        log_warn "未自动找到灵洲部署目录"
    else
        log_info "找到以下灵洲部署目录:"
        echo ""
        local i=1
        for dir in "${found_dirs[@]}"; do
            local cur_tag
            cur_tag=$(get_current_tag "$dir")
            echo "  [$i] $dir"
            echo "      当前版本: ${cur_tag:-未知}"
            i=$((i + 1))
        done
        echo "  [0] 手动输入目录路径"
        echo ""
    fi

    # 从 /dev/tty 读取，兼容 curl | bash
    local choice=""
    if [[ -r /dev/tty ]]; then
        read -rp "请选择部署目录 (输入序号，或直接输入目录路径): " choice < /dev/tty
    else
        log_warn "无法读取终端 (/dev/tty 不可用)"
        exit 1
    fi

    # 纯数字 → 选择序号
    if [[ "$choice" =~ ^[0-9]+$ ]]; then
        if [[ "$choice" == "0" ]]; then
            read -rp "请输入灵洲部署目录路径: " DEPLOY_DIR < /dev/tty
        elif [[ "$choice" -ge 1 ]] && [[ "$choice" -le ${#found_dirs[@]} ]]; then
            DEPLOY_DIR="${found_dirs[$((choice - 1))]}"
        else
            log_error "无效的序号: $choice"
            exit 1
        fi
    elif [[ -n "$choice" ]]; then
        # 非数字 → 当作目录路径
        DEPLOY_DIR="$choice"
    else
        log_error "未选择目录"
        exit 1
    fi

    # 验证
    if [[ -z "$DEPLOY_DIR" ]]; then
        log_error "部署目录为空"
        exit 1
    fi

    # 展开 ~ 和相对路径
    DEPLOY_DIR="${DEPLOY_DIR/#\~/$HOME}"
    if [[ ! "$DEPLOY_DIR" = /* ]]; then
        DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)/$DEPLOY_DIR"
    fi

    if ! is_lingz_deploy_dir "$DEPLOY_DIR"; then
        log_error "目录不是有效的灵洲部署目录: $DEPLOY_DIR"
        log_error "需包含 docker-compose.yml 和 .env (含 IMAGE_TAG)"
        exit 1
    fi

    log_info "已确认部署目录: $DEPLOY_DIR"
}

#======================== 交互式输入版本号 ====================================
prompt_version() {
    log_step "步骤 2/5: 确认目标版本"

    local current_tag
    current_tag=$(get_current_tag "$DEPLOY_DIR")
    log_info "当前版本: ${current_tag:-未知}"
    echo ""

    # 如果命令行已指定版本
    if [[ -n "$NEW_TAG" ]]; then
        log_info "目标版本 (命令行指定): $NEW_TAG"
        if [[ "$NEW_TAG" == "$current_tag" ]]; then
            log_warn "目标版本与当前版本相同，仍将继续更新"
        fi
        return
    fi

    # 从 /dev/tty 读取
    if [[ -r /dev/tty ]]; then
        read -rp "请输入目标版本 IMAGE_TAG (如 1.8.8，留空则不修改): " NEW_TAG < /dev/tty
    else
        log_warn "无法读取终端，跳过版本修改"
        return
    fi

    if [[ -z "$NEW_TAG" ]]; then
        log_info "未输入版本号，将不修改 IMAGE_TAG (仅重新拉取镜像并重启)"
    elif [[ "$NEW_TAG" == "$current_tag" ]]; then
        log_warn "目标版本与当前版本相同，仍将继续更新"
    else
        log_info "目标版本: $NEW_TAG"
    fi
}

#======================== 修改 .env 版本号 ====================================
update_env_tag() {
    local env_file="$DEPLOY_DIR/.env"

    if [[ -z "$NEW_TAG" ]]; then
        log_info "未指定新版本，跳过 .env 修改"
        return
    fi

    log_step "步骤 3/5: 修改 .env 版本号"

    if [[ ! -f "$env_file" ]]; then
        log_error ".env 文件不存在: $env_file"
        exit 1
    fi

    local current_tag
    current_tag=$(get_current_tag "$DEPLOY_DIR")
    log_info "修改 IMAGE_TAG: ${current_tag:-空} → ${NEW_TAG}"

    # 备份 .env
    cp "$env_file" "${env_file}.bak.$(date +%Y%m%d%H%M%S)"
    log_info "已备份 .env"

    # 修改版本号
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${NEW_TAG}|" "$env_file"

    # 验证修改结果
    local updated_tag
    updated_tag=$(grep '^IMAGE_TAG=' "$env_file" | cut -d'=' -f2)
    if [[ "$updated_tag" != "$NEW_TAG" ]]; then
        log_error ".env 修改失败，当前值为: $updated_tag"
        exit 1
    fi

    log_info ".env 版本已更新: IMAGE_TAG=${updated_tag}"
}

#======================== 拉取镜像并重启服务 ==================================
pull_and_restart() {
    log_step "步骤 4/5: 拉取新版本镜像并重启服务"

    cd "$DEPLOY_DIR"

    # 确认 compose 文件
    local compose_file=""
    for f in docker-compose.yml docker-compose.yaml compose.yml compose.yaml; do
        if [[ -f "$f" ]]; then
            compose_file="$f"
            break
        fi
    done
    if [[ -z "$compose_file" ]]; then
        log_error "未找到 docker-compose 配置文件"
        exit 1
    fi
    log_info "compose 文件: $compose_file"

    # 1. 拉取新镜像
    log_info "拉取最新镜像 (可能需要几分钟)..."
    if ! $COMPOSE_CMD pull 2>&1; then
        log_warn "部分镜像拉取失败，尝试继续启动..."
    fi

    # 2. 停止旧容器
    log_info "停止旧版本容器..."
    $COMPOSE_CMD down 2>&1 || log_warn "停止容器时出现警告 (可忽略)"

    # 3. 启动新容器
    log_info "启动新版本容器..."
    $COMPOSE_CMD up -d 2>&1

    log_info "服务重启命令执行完毕"
}

#======================== 健康检查 ============================================
is_container_ready() {
    local name="$1"
    local state health has_healthcheck

    state=$(docker inspect --format '{{.State.Status}}' "$name" 2>/dev/null) || return 1
    [[ "$state" != "running" ]] && return 1

    has_healthcheck=$(docker inspect --format '{{if .Config.Healthcheck}}yes{{else}}no{{end}}' "$name" 2>/dev/null)

    if [[ "$has_healthcheck" == "yes" ]]; then
        health=$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null)
        [[ "$health" == "healthy" ]]
    else
        return 0
    fi
}

health_check() {
    log_step "步骤 5/5: 健康检查"

    cd "$DEPLOY_DIR"
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
        log_warn "部分容器在超时时间内未就绪，服务可能仍在启动中"
        log_warn "可使用以下命令查看状态: cd $DEPLOY_DIR && $COMPOSE_CMD ps"
    fi

    echo ""
    log_info "当前容器状态:"
    $COMPOSE_CMD ps 2>/dev/null || docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

#======================== 输出更新结果 ========================================
print_summary() {
    local ip_addr
    ip_addr=$(hostname -I 2>/dev/null | awk '{print $1}')
    [[ -z "$ip_addr" ]] && ip_addr=$(curl -sL --connect-timeout 5 ifconfig.me 2>/dev/null || echo "未知")

    local frontend_port=""
    cd "$DEPLOY_DIR"
    frontend_port=$($COMPOSE_CMD ps --format '{{.Name}} {{.Ports}}' 2>/dev/null | grep -i frontend | grep -oP '\d+(?=->)' | head -1)
    [[ -z "$frontend_port" ]] && frontend_port="80"

    local current_tag
    current_tag=$(get_current_tag "$DEPLOY_DIR")

    log_result "灵洲 AI 平台版本更新完成!"
    echo ""
    echo -e "  ${CYAN}访问地址:${NC}  http://${ip_addr}:${frontend_port}"
    echo -e "  ${CYAN}当前版本:${NC}  ${current_tag:-未知}"
    echo -e "  ${CYAN}部署目录:${NC}  ${DEPLOY_DIR}"
    echo ""
    echo "  常用命令:"
    echo "    查看日志:   cd $DEPLOY_DIR && $COMPOSE_CMD logs backend -f"
    echo "    重启服务:   cd $DEPLOY_DIR && $COMPOSE_CMD restart"
    echo "    查看状态:   cd $DEPLOY_DIR && $COMPOSE_CMD ps"
    echo "    回滚版本:   修改 $DEPLOY_DIR/.env 中 IMAGE_TAG 后重新运行本脚本"
    echo ""
}

#======================== 主流程 ==============================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║          灵洲 AI 平台 - 版本更新脚本                    ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""

    # 解析命令行参数
    parse_args "$@"

    # 检测 Docker Compose
    detect_compose_cmd

    # 步骤 1: 查找并确认部署目录
    prompt_deploy_dir

    # 步骤 2: 确认目标版本
    prompt_version

    # 步骤 3: 修改 .env 版本号
    update_env_tag

    # 步骤 4: 拉取镜像并重启
    pull_and_restart

    # 步骤 5: 健康检查
    health_check

    # 输出结果
    print_summary
}

main "$@"
