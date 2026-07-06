#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BUILDER_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$BUILDER_DIR/builder.env"
CLI_IMAGE_TAG=""

usage() {
  cat <<'EOF'
Usage:
  ./deploy/lingz-builder/scripts/build-and-push.sh
  ./deploy/lingz-builder/scripts/build-and-push.sh <image-tag>
  ./deploy/lingz-builder/scripts/build-and-push.sh --tag <image-tag> [--env <builder.env>]
  ./deploy/lingz-builder/scripts/build-and-push.sh --env <builder.env> [--tag <image-tag>]

Defaults:
  env file:  ../builder.env relative to this script
  image tag: IMAGE_TAG from builder.env

Examples:
  sh ./scripts/build-and-push.sh
  sh ./scripts/build-and-push.sh 1.7.1
  sh ./scripts/build-and-push.sh --tag 1.7.1
  sh ./scripts/build-and-push.sh --tag 1.7.1 --env ./builder.env
  sh ./scripts/build-and-push.sh ./builder.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --tag)
      shift
      if [ "$#" -eq 0 ]; then
        echo "[lingz-builder] Missing value for --tag." >&2
        exit 1
      fi
      CLI_IMAGE_TAG="$1"
      ;;
    --env)
      shift
      if [ "$#" -eq 0 ]; then
        echo "[lingz-builder] Missing value for --env." >&2
        exit 1
      fi
      ENV_FILE="$1"
      ;;
    -h|--help|help)
      usage
      exit 0
      ;;
    -*)
      echo "[lingz-builder] Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [ -f "$1" ]; then
        ENV_FILE="$1"
        shift
        continue
      fi
      if [ -n "$CLI_IMAGE_TAG" ]; then
        echo "[lingz-builder] Only one image tag may be specified." >&2
        exit 1
      fi
      CLI_IMAGE_TAG="$1"
      ;;
  esac
  shift
done

if [ ! -f "$ENV_FILE" ]; then
  echo "[lingz-builder] Config not found: $ENV_FILE" >&2
  echo "[lingz-builder] Copy $BUILDER_DIR/builder.env.example to $BUILDER_DIR/builder.env first." >&2
  exit 1
fi

read_dotenv_value() {
  key="$1"
  file="$2"
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$file" | tail -n 1 || true)"
  if [ -z "$line" ]; then
    return 0
  fi
  value="${line#*=}"
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  printf '%s' "$value"
}

load_env_if_unset() {
  key="$1"
  file="$2"
  eval "current_value=\${$key:-}"
  if [ -n "$current_value" ]; then
    return 0
  fi
  loaded_value="$(read_dotenv_value "$key" "$file")"
  if [ -n "$loaded_value" ]; then
    export "$key=$loaded_value"
  fi
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[lingz-builder] Missing command: $1" >&2
    exit 1
  fi
}

git_with_auth() {
  if [ -z "${GIT_HTTP_TOKEN:-}" ]; then
    git "$@"
    return 0
  fi
  askpass_script="$(mktemp "${TMPDIR:-/tmp}/lingz-git-askpass.XXXXXX")"
  cat > "$askpass_script" <<EOF
#!/bin/sh
case "\$1" in
  *Username*) printf '%s\n' "\$GIT_ASKPASS_USERNAME" ;;
  *Password*) printf '%s\n' "\$GIT_ASKPASS_PASSWORD" ;;
  *) printf '%s\n' "\$GIT_ASKPASS_PASSWORD" ;;
esac
EOF
  chmod 700 "$askpass_script"
  set +e
  GIT_ASKPASS_USERNAME="${GIT_HTTP_USERNAME:-oauth2}" \
  GIT_ASKPASS_PASSWORD="$GIT_HTTP_TOKEN" \
  GIT_ASKPASS="$askpass_script" \
  GIT_TERMINAL_PROMPT=0 \
    git "$@"
  git_status="$?"
  set -e
  rm -f "$askpass_script"
  return "$git_status"
}

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

validate_image_tag() {
  tag="$1"
  [ -n "$tag" ] && printf '%s' "$tag" | grep -Eq '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$'
}

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    echo "[lingz-builder] Root privilege is required to install packages, but sudo is not available." >&2
    exit 1
  fi
}

package_manager() {
  if command -v apt-get >/dev/null 2>&1; then
    printf '%s\n' "apt"
  elif command -v dnf >/dev/null 2>&1; then
    printf '%s\n' "dnf"
  elif command -v yum >/dev/null 2>&1; then
    printf '%s\n' "yum"
  else
    printf '%s\n' ""
  fi
}

is_centos7() {
  [ -f /etc/centos-release ] && grep -Eq 'CentOS.* 7\.' /etc/centos-release
}

configure_centos7_yum_repo() {
  if ! is_true "$AUTO_CONFIGURE_CENTOS7_YUM"; then
    return 0
  fi
  if ! is_centos7; then
    return 0
  fi

  repo_file="/etc/yum.repos.d/CentOS-Base.repo"
  if [ -f "$repo_file" ] && grep -q "$CENTOS7_YUM_MIRROR" "$repo_file"; then
    return 0
  fi

  echo "[lingz-builder] Configuring CentOS 7 yum repo: $CENTOS7_YUM_MIRROR"
  if [ -f "$repo_file" ]; then
    run_as_root cp -a "$repo_file" "$repo_file.lingz-builder.bak.$(date +%Y%m%d%H%M%S)"
  fi

  tmp_repo="$(mktemp)"
  cat > "$tmp_repo" <<EOF
[base]
name=CentOS-7 - Base
baseurl=${CENTOS7_YUM_MIRROR}/os/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7

[updates]
name=CentOS-7 - Updates
baseurl=${CENTOS7_YUM_MIRROR}/updates/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7

[extras]
name=CentOS-7 - Extras
baseurl=${CENTOS7_YUM_MIRROR}/extras/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7
EOF
  run_as_root cp "$tmp_repo" "$repo_file"
  rm -f "$tmp_repo"
  run_as_root yum clean all || true
}

install_packages() {
  pm="$(package_manager)"
  if [ -z "$pm" ]; then
    echo "[lingz-builder] Unsupported package manager. Please install manually: $*" >&2
    exit 1
  fi

  case "$pm" in
    apt)
      run_as_root apt-get update
      run_as_root apt-get install -y "$@"
      ;;
    dnf)
      run_as_root dnf install -y "$@"
      ;;
    yum)
      configure_centos7_yum_repo
      run_as_root yum install -y "$@"
      ;;
  esac
}

install_base_tools() {
  missing=""
  command -v git >/dev/null 2>&1 || missing="$missing git"
  if [ "$BACKEND_BUILD_MODE" != "docker" ]; then
    command -v mvn >/dev/null 2>&1 || missing="$missing maven"
  fi
  command -v curl >/dev/null 2>&1 || missing="$missing curl"

  if [ -n "$missing" ]; then
    echo "[lingz-builder] Installing base tools:$missing"
    install_packages $missing ca-certificates
  fi
}

node_major_version() {
  if ! command -v node >/dev/null 2>&1; then
    return 1
  fi
  node -v | sed -E 's/^v([0-9]+).*/\1/'
}

install_node() {
  current_major="$(node_major_version 2>/dev/null || true)"
  if [ -n "$current_major" ] && [ "$current_major" -ge "$NODE_MAJOR_VERSION" ]; then
    return 0
  fi

  echo "[lingz-builder] Installing Node.js $NODE_MAJOR_VERSION..."
  pm="$(package_manager)"
  case "$pm" in
    apt)
      run_as_root mkdir -p /etc/apt/keyrings
      curl -fsSL "https://deb.nodesource.com/setup_${NODE_MAJOR_VERSION}.x" | run_as_root bash -
      run_as_root apt-get install -y nodejs
      ;;
    dnf|yum)
      curl -fsSL "https://rpm.nodesource.com/setup_${NODE_MAJOR_VERSION}.x" | run_as_root bash -
      install_packages nodejs
      ;;
    *)
      echo "[lingz-builder] Unsupported package manager for Node.js installation." >&2
      exit 1
      ;;
  esac

  current_major="$(node_major_version 2>/dev/null || true)"
  if [ -z "$current_major" ] || [ "$current_major" -lt "$NODE_MAJOR_VERSION" ]; then
    echo "[lingz-builder] Node.js $NODE_MAJOR_VERSION+ is required after installation." >&2
    exit 1
  fi
}

install_pnpm() {
  if command -v pnpm >/dev/null 2>&1; then
    return 0
  fi

  echo "[lingz-builder] Installing pnpm $PNPM_VERSION..."
  if command -v npm >/dev/null 2>&1; then
    npm config set registry "$NPM_REGISTRY"
  fi
  if command -v corepack >/dev/null 2>&1; then
    run_as_root corepack enable
    run_as_root corepack prepare "pnpm@${PNPM_VERSION}" --activate
  elif command -v npm >/dev/null 2>&1; then
    run_as_root npm install -g "pnpm@${PNPM_VERSION}"
  else
    echo "[lingz-builder] npm/corepack is required to install pnpm." >&2
    exit 1
  fi
}

install_docker() {
  if command -v docker >/dev/null 2>&1; then
    return 0
  fi

  echo "[lingz-builder] Installing Docker..."
  pm="$(package_manager)"
  case "$pm" in
    apt)
      install_packages docker.io docker-compose-plugin docker-buildx-plugin || install_packages docker.io
      ;;
    dnf)
      install_packages docker docker-compose-plugin docker-buildx-plugin || install_packages docker
      ;;
    yum)
      install_packages docker docker-compose-plugin docker-buildx-plugin || install_packages docker
      ;;
    *)
      echo "[lingz-builder] Unsupported package manager for Docker installation." >&2
      exit 1
      ;;
  esac

  if command -v systemctl >/dev/null 2>&1; then
    run_as_root systemctl enable --now docker || run_as_root systemctl start docker || true
  fi
}

install_buildx_if_missing() {
  if docker buildx version >/dev/null 2>&1; then
    return 0
  fi

  echo "[lingz-builder] Installing docker buildx plugin..."
  pm="$(package_manager)"
  case "$pm" in
    apt) install_packages docker-buildx-plugin ;;
    dnf) install_packages docker-buildx-plugin ;;
    yum) install_packages docker-buildx-plugin ;;
    *)
      echo "[lingz-builder] Unsupported package manager for buildx installation." >&2
      exit 1
      ;;
  esac
}

configure_package_mirrors() {
  export MAVEN_MIRROR_URL
  if [ "$FRONTEND_BUILD_MODE" = "docker" ]; then
    return 0
  fi
  if command -v npm >/dev/null 2>&1; then
    npm config set registry "$NPM_REGISTRY"
  fi
  if command -v pnpm >/dev/null 2>&1; then
    pnpm config set registry "$PNPM_REGISTRY"
    pnpm config set fetch-retries 5
    pnpm config set fetch-timeout 120000
  fi
}

ensure_build_tools() {
  if is_true "$INSTALL_MISSING_TOOLS"; then
    install_base_tools
    if [ "$FRONTEND_BUILD_MODE" != "docker" ]; then
      install_node
      install_pnpm
    fi
    install_docker
    install_buildx_if_missing
  fi

  require_cmd git
  require_cmd docker
  if [ "$BACKEND_BUILD_MODE" != "docker" ]; then
    require_cmd mvn
  fi
  if [ "$FRONTEND_BUILD_MODE" != "docker" ]; then
    require_cmd node
    require_cmd pnpm
  fi

  if ! docker version >/dev/null 2>&1; then
    if command -v systemctl >/dev/null 2>&1; then
      run_as_root systemctl start docker || true
    fi
  fi
  if ! docker version >/dev/null 2>&1; then
    echo "[lingz-builder] Docker daemon is not accessible. Start Docker or run this script with a Docker-enabled user." >&2
    exit 1
  fi

  if ! docker buildx version >/dev/null 2>&1; then
    echo "[lingz-builder] docker buildx is required." >&2
    if ! is_true "$INSTALL_MISSING_TOOLS"; then
      echo "[lingz-builder] Set INSTALL_MISSING_TOOLS=true to let the script try installing it." >&2
    fi
    exit 1
  fi
}

effective_image_tag() {
  base_tag="$1"
  target_platform="$2"
  case "$target_platform" in
    ""|linux/amd64)
      printf '%s\n' "$base_tag"
      ;;
    linux/*)
      suffix="${target_platform#linux/}"
      printf '%s-%s\n' "$base_tag" "$(printf '%s' "$suffix" | tr '/' '-')"
      ;;
    *)
      echo "[lingz-builder] Invalid TARGET_PLATFORM: $target_platform" >&2
      exit 1
      ;;
  esac
}

pip_platform_for_target() {
  target_platform="$1"
  arch="${target_platform#linux/}"
  case "$arch" in
    amd64|x86_64) printf '%s\n' "manylinux2014_x86_64 manylinux_2_17_x86_64 manylinux_2_28_x86_64 linux_x86_64" ;;
    arm64|aarch64) printf '%s\n' "manylinux2014_aarch64 manylinux_2_17_aarch64 manylinux_2_27_aarch64 manylinux_2_28_aarch64 linux_aarch64" ;;
    *) printf '%s\n' "manylinux2014_${arch}" ;;
  esac
}

ensure_clean_checkout() {
  repo_dir="$1"
  if [ ! -d "$repo_dir/.git" ]; then
    return 0
  fi
  if [ -n "$(cd "$repo_dir" && git_with_auth status --porcelain)" ]; then
    echo "[lingz-builder] Repo has local changes: $repo_dir" >&2
    echo "[lingz-builder] Please commit/stash/clean them before running this build script." >&2
    exit 1
  fi
}

prepare_git_tag() {
  if ! is_true "$CREATE_GIT_TAG"; then
    return 0
  fi

  if ! (cd "$REPO_DIR" && git check-ref-format "refs/tags/$GIT_TAG_NAME"); then
    echo "[lingz-builder] Invalid Git tag name: $GIT_TAG_NAME" >&2
    exit 1
  fi

  if (cd "$REPO_DIR" && git_with_auth ls-remote --exit-code --tags origin "refs/tags/$GIT_TAG_NAME" >/dev/null 2>&1); then
    echo "[lingz-builder] Git tag already exists on origin: $GIT_TAG_NAME" >&2
    echo "[lingz-builder] Use a new image version before building." >&2
    exit 1
  fi

  if (cd "$REPO_DIR" && git rev-parse -q --verify "refs/tags/$GIT_TAG_NAME" >/dev/null 2>&1); then
    local_tag_commit="$(cd "$REPO_DIR" && git rev-list -n 1 "$GIT_TAG_NAME")"
    if [ "$local_tag_commit" != "$COMMIT_FULL_SHA" ]; then
      echo "[lingz-builder] Local Git tag points to another commit: $GIT_TAG_NAME" >&2
      exit 1
    fi
    GIT_TAG_REUSE="1"
    echo "[lingz-builder] Reusing local Git tag from a previous failed push: $GIT_TAG_NAME"
  fi
}

create_and_push_git_tag() {
  if ! is_true "$CREATE_GIT_TAG"; then
    return 0
  fi

  if [ "$GIT_TAG_REUSE" != "1" ]; then
    (
      cd "$REPO_DIR"
      git \
        -c "user.name=$GIT_TAGGER_NAME" \
        -c "user.email=$GIT_TAGGER_EMAIL" \
        tag -a "$GIT_TAG_NAME" "$COMMIT_FULL_SHA" \
        -m "Build images $FRONTEND_IMAGE and $BACKEND_IMAGE"
    )
  fi

  echo "[lingz-builder] Pushing Git tag: $GIT_TAG_NAME"
  (cd "$REPO_DIR" && git_with_auth push origin "refs/tags/$GIT_TAG_NAME")
}

prepare_maven_settings() {
  settings_file="$REPO_DIR/deploy/config/maven/settings.xml"
  if [ -f "$settings_file" ]; then
    printf '%s\n' "$settings_file"
    return 0
  fi

  settings_file="$WORK_DIR/maven-settings.xml"
  echo "[lingz-builder] Maven settings not found in repo, generating: $settings_file" >&2
  cat > "$settings_file" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>domestic-public-mirror</id>
            <name>Domestic Maven Mirror</name>
            <url>${env.MAVEN_MIRROR_URL}</url>
            <mirrorOf>*,!spring-milestones,!spring-snapshots</mirrorOf>
        </mirror>
    </mirrors>
</settings>
EOF
  printf '%s\n' "$settings_file"
}

build_backend_with_host() {
  maven_settings="$1"
  if [ "$SKIP_TESTS" = "true" ]; then
    mvn -s "$maven_settings" -f "$REPO_DIR/pom.xml" -pl backend -am -DskipTests package
  else
    mvn -s "$maven_settings" -f "$REPO_DIR/pom.xml" -pl backend -am package
  fi
}

build_backend_with_docker() {
  maven_settings="$1"
  mkdir -p "$WORK_DIR/m2"
  skip_tests_arg=""
  if [ "$SKIP_TESTS" = "true" ]; then
    skip_tests_arg="-DskipTests"
  fi
  docker run --rm \
    -e "MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL" \
    -v "$REPO_DIR:/workspace" \
    -v "$WORK_DIR/m2:/root/.m2" \
    -v "$maven_settings:/tmp/maven-settings.xml:ro" \
    -w /workspace \
    "$BACKEND_BUILD_IMAGE" \
    sh -lc "mvn -s /tmp/maven-settings.xml -f /workspace/pom.xml -pl backend -am $skip_tests_arg package"
}

build_backend() {
  maven_settings="$(prepare_maven_settings)"
  case "$BACKEND_BUILD_MODE" in
    docker)
      build_backend_with_docker "$maven_settings"
      ;;
    host)
      build_backend_with_host "$maven_settings"
      ;;
    *)
      echo "[lingz-builder] Invalid BACKEND_BUILD_MODE: $BACKEND_BUILD_MODE (expected docker or host)" >&2
      exit 1
      ;;
  esac
}

build_frontend_with_host() {
  (
    cd "$REPO_DIR/frontend"
    npm config set registry "$NPM_REGISTRY"
    pnpm config set registry "$PNPM_REGISTRY"
    pnpm config set fetch-retries 5
    pnpm config set fetch-timeout 120000
    if [ "$SKIP_FRONTEND_INSTALL" != "true" ]; then
      pnpm install
    fi
    pnpm build
  )
}

build_frontend_with_docker() {
  docker run --rm \
    --user "$FRONTEND_BUILD_CONTAINER_USER" \
    -e "PNPM_REGISTRY=$PNPM_REGISTRY" \
    -e "NPM_REGISTRY=$NPM_REGISTRY" \
    -e "VITE_BASE_PATH=$VITE_BASE_PATH" \
    -e "VITE_BASE_URL=$VITE_BASE_URL" \
    -v "$REPO_DIR/frontend:/workspace" \
    -w /workspace \
    "$FRONTEND_BUILD_IMAGE" \
    sh -lc "corepack enable && corepack prepare pnpm@${PNPM_VERSION} --activate && npm config set registry \"\$NPM_REGISTRY\" && pnpm config set registry \"\$PNPM_REGISTRY\" && pnpm config set fetch-retries 5 && pnpm config set fetch-timeout 120000 && if [ \"$SKIP_FRONTEND_INSTALL\" != \"true\" ]; then pnpm install; fi && pnpm build"
}

build_frontend() {
  case "$FRONTEND_BUILD_MODE" in
    docker)
      build_frontend_with_docker
      ;;
    host)
      build_frontend_with_host
      ;;
    *)
      echo "[lingz-builder] Invalid FRONTEND_BUILD_MODE: $FRONTEND_BUILD_MODE (expected docker or host)" >&2
      exit 1
      ;;
  esac
}

load_env_if_unset "REPO_URL" "$ENV_FILE"
load_env_if_unset "REPO_REF" "$ENV_FILE"
load_env_if_unset "GIT_HTTP_USERNAME" "$ENV_FILE"
load_env_if_unset "GIT_HTTP_TOKEN" "$ENV_FILE"
load_env_if_unset "CREATE_GIT_TAG" "$ENV_FILE"
load_env_if_unset "GIT_TAG_PREFIX" "$ENV_FILE"
load_env_if_unset "GIT_TAGGER_NAME" "$ENV_FILE"
load_env_if_unset "GIT_TAGGER_EMAIL" "$ENV_FILE"
load_env_if_unset "WORK_DIR" "$ENV_FILE"
load_env_if_unset "REPO_DIR" "$ENV_FILE"
load_env_if_unset "REGISTRY" "$ENV_FILE"
load_env_if_unset "IMAGE_TAG" "$ENV_FILE"
load_env_if_unset "FRONTEND_IMAGE_NAME" "$ENV_FILE"
load_env_if_unset "BACKEND_IMAGE_NAME" "$ENV_FILE"
load_env_if_unset "TARGET_PLATFORM" "$ENV_FILE"
load_env_if_unset "SKIP_TESTS" "$ENV_FILE"
load_env_if_unset "BACKEND_BUILD_MODE" "$ENV_FILE"
load_env_if_unset "BACKEND_BUILD_IMAGE" "$ENV_FILE"
load_env_if_unset "SKIP_FRONTEND_INSTALL" "$ENV_FILE"
load_env_if_unset "FRONTEND_BUILD_MODE" "$ENV_FILE"
load_env_if_unset "FRONTEND_BUILD_IMAGE" "$ENV_FILE"
load_env_if_unset "FRONTEND_BUILD_CONTAINER_USER" "$ENV_FILE"
load_env_if_unset "MAVEN_MIRROR_URL" "$ENV_FILE"
load_env_if_unset "NPM_REGISTRY" "$ENV_FILE"
load_env_if_unset "PNPM_REGISTRY" "$ENV_FILE"
load_env_if_unset "PNPM_VERSION" "$ENV_FILE"
load_env_if_unset "VITE_BASE_PATH" "$ENV_FILE"
load_env_if_unset "VITE_BASE_URL" "$ENV_FILE"
load_env_if_unset "INSTALL_MISSING_TOOLS" "$ENV_FILE"
load_env_if_unset "NODE_MAJOR_VERSION" "$ENV_FILE"
load_env_if_unset "AUTO_CONFIGURE_CENTOS7_YUM" "$ENV_FILE"
load_env_if_unset "CENTOS7_YUM_MIRROR" "$ENV_FILE"
load_env_if_unset "PIP_PLATFORM" "$ENV_FILE"
load_env_if_unset "REGISTRY_USERNAME" "$ENV_FILE"
load_env_if_unset "REGISTRY_PASSWORD" "$ENV_FILE"

REPO_URL="${REPO_URL:-}"
REPO_REF="${REPO_REF:-dev}"
GIT_HTTP_USERNAME="${GIT_HTTP_USERNAME:-oauth2}"
GIT_HTTP_TOKEN="${GIT_HTTP_TOKEN:-}"
CREATE_GIT_TAG="${CREATE_GIT_TAG:-true}"
GIT_TAG_PREFIX="${GIT_TAG_PREFIX:-}"
GIT_TAGGER_NAME="${GIT_TAGGER_NAME:-Lingz Builder}"
GIT_TAGGER_EMAIL="${GIT_TAGGER_EMAIL:-lingz-builder@localhost}"
WORK_DIR="${WORK_DIR:-/opt/lingz-builder/workspace}"
REPO_DIR="${REPO_DIR:-$WORK_DIR/lingzhou-agent}"
REGISTRY="${REGISTRY:-125.75.152.167:5001}"
IMAGE_TAG="${CLI_IMAGE_TAG:-${IMAGE_TAG:-}}"
FRONTEND_IMAGE_NAME="${FRONTEND_IMAGE_NAME:-lingzhou-frontend}"
BACKEND_IMAGE_NAME="${BACKEND_IMAGE_NAME:-lingzhou-backend}"
TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
SKIP_TESTS="${SKIP_TESTS:-true}"
BACKEND_BUILD_MODE="${BACKEND_BUILD_MODE:-docker}"
BACKEND_BUILD_IMAGE="${BACKEND_BUILD_IMAGE:-maven:3.9-eclipse-temurin-17}"
SKIP_FRONTEND_INSTALL="${SKIP_FRONTEND_INSTALL:-false}"
FRONTEND_BUILD_MODE="${FRONTEND_BUILD_MODE:-docker}"
FRONTEND_BUILD_IMAGE="${FRONTEND_BUILD_IMAGE:-node:20-alpine}"
FRONTEND_BUILD_CONTAINER_USER="${FRONTEND_BUILD_CONTAINER_USER:-root}"
MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
PNPM_REGISTRY="${PNPM_REGISTRY:-https://registry.npmmirror.com}"
PNPM_VERSION="${PNPM_VERSION:-9.15.9}"
VITE_BASE_PATH="${VITE_BASE_PATH:-/}"
VITE_BASE_URL="${VITE_BASE_URL:-}"
INSTALL_MISSING_TOOLS="${INSTALL_MISSING_TOOLS:-false}"
NODE_MAJOR_VERSION="${NODE_MAJOR_VERSION:-20}"
AUTO_CONFIGURE_CENTOS7_YUM="${AUTO_CONFIGURE_CENTOS7_YUM:-true}"
CENTOS7_YUM_MIRROR="${CENTOS7_YUM_MIRROR:-https://mirrors.aliyun.com/centos-vault/7.9.2009}"
PIP_PLATFORM="${PIP_PLATFORM:-$(pip_platform_for_target "$TARGET_PLATFORM")}"

if [ -z "$REPO_URL" ]; then
  echo "[lingz-builder] REPO_URL is required." >&2
  exit 1
fi
if [ -z "$IMAGE_TAG" ]; then
  echo "[lingz-builder] IMAGE_TAG is required. Pass it directly or configure it in $ENV_FILE." >&2
  exit 1
fi
if ! validate_image_tag "$IMAGE_TAG"; then
  echo "[lingz-builder] Invalid image tag: $IMAGE_TAG" >&2
  echo "[lingz-builder] Allowed: letters, numbers, underscore, dot and dash; maximum 128 characters." >&2
  exit 1
fi

ensure_build_tools
configure_package_mirrors

EFFECTIVE_IMAGE_TAG="$(effective_image_tag "$IMAGE_TAG" "$TARGET_PLATFORM")"
FRONTEND_IMAGE="${REGISTRY}/${FRONTEND_IMAGE_NAME}:${EFFECTIVE_IMAGE_TAG}"
BACKEND_IMAGE="${REGISTRY}/${BACKEND_IMAGE_NAME}:${EFFECTIVE_IMAGE_TAG}"
GIT_TAG_NAME="${GIT_TAG_PREFIX}${EFFECTIVE_IMAGE_TAG}"
GIT_TAG_REUSE="0"

echo "[lingz-builder] Config: $ENV_FILE"
echo "[lingz-builder] Repo URL: $REPO_URL"
echo "[lingz-builder] Repo ref: $REPO_REF"
if [ -n "$GIT_HTTP_TOKEN" ]; then
  echo "[lingz-builder] Git HTTP auth: enabled for user $GIT_HTTP_USERNAME"
fi
echo "[lingz-builder] Repo dir: $REPO_DIR"
echo "[lingz-builder] Target platform: $TARGET_PLATFORM"
echo "[lingz-builder] Backend build mode: $BACKEND_BUILD_MODE"
if [ "$BACKEND_BUILD_MODE" = "docker" ]; then
  echo "[lingz-builder] Backend build image: $BACKEND_BUILD_IMAGE"
fi
echo "[lingz-builder] Frontend build mode: $FRONTEND_BUILD_MODE"
if [ "$FRONTEND_BUILD_MODE" = "docker" ]; then
  echo "[lingz-builder] Frontend build image: $FRONTEND_BUILD_IMAGE"
fi
echo "[lingz-builder] Maven mirror: $MAVEN_MIRROR_URL"
echo "[lingz-builder] npm registry: $NPM_REGISTRY"
echo "[lingz-builder] pnpm registry: $PNPM_REGISTRY"
echo "[lingz-builder] Frontend image: $FRONTEND_IMAGE"
echo "[lingz-builder] Backend image: $BACKEND_IMAGE"
if is_true "$CREATE_GIT_TAG"; then
  echo "[lingz-builder] Git tag after image push: $GIT_TAG_NAME"
fi

mkdir -p "$WORK_DIR"

if [ -n "${REGISTRY_USERNAME:-}" ] && [ -n "${REGISTRY_PASSWORD:-}" ]; then
  echo "[lingz-builder] Docker login: $REGISTRY"
  printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USERNAME" --password-stdin
fi

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "[lingz-builder] Cloning repo..."
  git_with_auth clone "$REPO_URL" "$REPO_DIR"
else
  ensure_clean_checkout "$REPO_DIR"
fi

echo "[lingz-builder] Updating checkout..."
(
  cd "$REPO_DIR"
  git_with_auth fetch --all --tags --prune
  git_with_auth checkout "$REPO_REF"
)
if current_branch="$(cd "$REPO_DIR" && git_with_auth symbolic-ref -q --short HEAD)"; then
  (cd "$REPO_DIR" && git_with_auth pull --ff-only origin "$current_branch")
else
  echo "[lingz-builder] Detached checkout, skip git pull."
fi
COMMIT_SHA="$(cd "$REPO_DIR" && git_with_auth rev-parse --short HEAD)"
COMMIT_FULL_SHA="$(cd "$REPO_DIR" && git_with_auth rev-parse HEAD)"
echo "[lingz-builder] Building commit: $COMMIT_SHA"
prepare_git_tag

echo "[lingz-builder] Building backend jar..."
build_backend

if [ ! -f "$REPO_DIR/backend/target/backend.jar" ]; then
  echo "[lingz-builder] Backend jar not found after Maven build." >&2
  exit 1
fi

echo "[lingz-builder] Building frontend dist..."
build_frontend

if [ ! -d "$REPO_DIR/frontend/dist" ]; then
  echo "[lingz-builder] Frontend dist not found after pnpm build." >&2
  exit 1
fi

echo "[lingz-builder] Building and pushing frontend image..."
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  -f "$REPO_DIR/deploy/docker/frontend.Dockerfile" \
  -t "$FRONTEND_IMAGE" \
  --build-arg "PNPM_REGISTRY=$PNPM_REGISTRY" \
  --build-arg "VITE_BASE_PATH=$VITE_BASE_PATH" \
  --build-arg "VITE_BASE_URL=$VITE_BASE_URL" \
  --provenance=false \
  --sbom=false \
  --push \
  "$REPO_DIR/frontend"

echo "[lingz-builder] Building and pushing backend image..."
docker buildx build \
  --platform "$TARGET_PLATFORM" \
  -f "$REPO_DIR/deploy/docker/backend.Dockerfile" \
  -t "$BACKEND_IMAGE" \
  --build-arg "PIP_PLATFORM=$PIP_PLATFORM" \
  --provenance=false \
  --sbom=false \
  --push \
  "$REPO_DIR"

create_and_push_git_tag

echo "[lingz-builder] Done."
echo "[lingz-builder] Commit: $COMMIT_SHA"
echo "[lingz-builder] Pushed frontend: $FRONTEND_IMAGE"
echo "[lingz-builder] Pushed backend: $BACKEND_IMAGE"
if is_true "$CREATE_GIT_TAG"; then
  echo "[lingz-builder] Pushed Git tag: $GIT_TAG_NAME"
fi
