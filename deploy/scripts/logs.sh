#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
QUICK_DIR="$DEPLOY_DIR/compose-quick"

cd "$QUICK_DIR"
docker compose --env-file .env -f docker-compose.yml logs -f "$@"
