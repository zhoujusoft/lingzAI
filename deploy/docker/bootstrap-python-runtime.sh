#!/bin/sh
set -eu

STATUS_FILE="${PYTHON_BOOTSTRAP_STATUS_FILE:-/app/runtime/python/bootstrap.status}"
READY_FILE="${PYTHON_BOOTSTRAP_READY_FILE:-/app/runtime/python/.ready}"
RUNTIME_DIR="${PYTHON_RUNTIME_DIR:-/app/runtime/python}"
VENV_DIR="${RUNTIME_DIR}/venv"
LOCK_DIR="${RUNTIME_DIR}/.bootstrap.lock"
REQ_FILE="${PYTHON_REQUIREMENTS_FILE:-/app/requirements.txt}"
BOOTSTRAP_ENABLED="${PYTHON_BOOTSTRAP_ENABLED:-true}"

log() {
  printf '[python-bootstrap] %s\n' "$1"
}

write_status() {
  mkdir -p "$(dirname "$STATUS_FILE")"
  printf '%s\n' "$1" > "$STATUS_FILE"
}

if [ "$BOOTSTRAP_ENABLED" = "false" ] || [ "$BOOTSTRAP_ENABLED" = "0" ]; then
  log "bootstrap disabled by PYTHON_BOOTSTRAP_ENABLED=${BOOTSTRAP_ENABLED}"
  write_status "DISABLED"
  exit 0
fi

mkdir -p "$RUNTIME_DIR"

if [ -f "$READY_FILE" ]; then
  log "runtime already prepared, skip install"
  write_status "READY"
  exit 0
fi

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  log "another bootstrap process is running, skip duplicate start"
  exit 0
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

write_status "INSTALLING"
log "start installing python runtime"

if ! command -v python3 >/dev/null 2>&1; then
  log "python3 not found, installing python3/pip/venv via apt"
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-pip python3-venv
  rm -rf /var/lib/apt/lists/*
fi

if [ ! -d "$VENV_DIR" ]; then
  log "creating venv at $VENV_DIR"
  python3 -m venv "$VENV_DIR"
fi

if [ -f "$REQ_FILE" ]; then
  log "installing python dependencies from $REQ_FILE"
  "$VENV_DIR/bin/pip" install --upgrade pip setuptools wheel
  "$VENV_DIR/bin/pip" install -r "$REQ_FILE"
else
  log "requirements file not found, skip dependency install: $REQ_FILE"
fi

touch "$READY_FILE"
write_status "READY"
log "python runtime is ready"
