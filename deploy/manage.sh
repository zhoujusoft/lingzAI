#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

usage() {
  cat <<'EOF'
Usage:
  ./deploy/manage.sh build [build-args...]
  ./deploy/manage.sh fix-build [build-args...]
  ./deploy/manage.sh build-images [options] [release.env path]
  ./deploy/manage.sh quick-up [compose-args...]
  ./deploy/manage.sh dev-up [compose-args...]
  ./deploy/manage.sh up [compose-args...]
  ./deploy/manage.sh down [compose-args...]
  ./deploy/manage.sh logs [compose-args...]
  ./deploy/manage.sh release [options] [release.env path]
  ./deploy/manage.sh release-prepare [release.env path]
  ./deploy/manage.sh release-publish [options] [release.env path]
  ./deploy/manage.sh middleware-up
  ./deploy/manage.sh middleware-db-update
  ./deploy/manage.sh middleware-down
  ./deploy/manage.sh middleware-logs

Defaults:
  release env path: deploy/release.env
EOF
}

cmd="${1:-}"
if [ -z "$cmd" ]; then
  usage
  exit 1
fi
shift || true

case "$cmd" in
  build)
    exec "$SCRIPT_DIR/scripts/build.sh" "$@"
    ;;
  fix-build)
    exec "$SCRIPT_DIR/scripts/fix-build.sh" "$@"
    ;;
  build-images)
    exec "$SCRIPT_DIR/scripts/build-images.sh" "$@"
    ;;
  quick-up)
    exec "$SCRIPT_DIR/scripts/up.sh" "$@"
    ;;
  dev-up)
    exec "$SCRIPT_DIR/scripts/dev-up.sh" "$@"
    ;;
  up)
    exec "$SCRIPT_DIR/scripts/up.sh" "$@"
    ;;
  down)
    exec "$SCRIPT_DIR/scripts/down.sh" "$@"
    ;;
  logs)
    exec "$SCRIPT_DIR/scripts/logs.sh" "$@"
    ;;
  release)
    if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
      echo "Usage: ./deploy/manage.sh release [options] [deploy/release.env]"
      exit 0
    fi
    exec "$SCRIPT_DIR/scripts/release.sh" "$@"
    ;;
  release-prepare)
    if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
      echo "Usage: ./deploy/manage.sh release-prepare [deploy/release.env]"
      exit 0
    fi
    env_file="${1:-deploy/release.env}"
    exec "$SCRIPT_DIR/scripts/release-prepare.sh" "$env_file"
    ;;
  release-publish)
    if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
      echo "Usage: ./deploy/manage.sh release-publish [options] [deploy/release.env]"
      exit 0
    fi
    exec "$SCRIPT_DIR/scripts/build-and-push-images.sh" "$@"
    ;;
  middleware-up)
    cd "$SCRIPT_DIR"
    exec docker compose --env-file .env.dev -f docker-compose.dev.yml up -d
    ;;
  middleware-db-update)
    exec "$SCRIPT_DIR/scripts/dev-db-update.sh"
    ;;
  middleware-down)
    cd "$SCRIPT_DIR"
    exec docker compose --env-file .env.dev -f docker-compose.dev.yml down
    ;;
  middleware-logs)
    cd "$SCRIPT_DIR"
    exec docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown command: $cmd" >&2
    usage
    exit 1
    ;;
esac
