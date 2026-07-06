#!/bin/sh
set -eu

copy_dir_if_target_empty() {
    source_dir="$1"
    target_dir="$2"

    if [ ! -d "$source_dir" ]; then
        return 0
    fi

    mkdir -p "$target_dir"

    if [ -z "$(find "$target_dir" -mindepth 1 -print -quit 2>/dev/null)" ]; then
        cp -R "$source_dir"/. "$target_dir"/
    fi
}

copy_file_if_target_missing() {
    source_file="$1"
    target_file="$2"

    if [ ! -f "$source_file" ]; then
        return 0
    fi

    mkdir -p "$(dirname "$target_file")"

    if [ ! -f "$target_file" ]; then
        cp "$source_file" "$target_file"
    fi
}

requirements_sha256() {
    file_path="$1"
    sha256sum "$file_path" | awk '{print $1}'
}

prune_legacy_general_code_runtime_artifacts() {
    general_code_dir="/app/workspaces/public/runtime-envs/python/general-code"

    if [ ! -d "$general_code_dir" ]; then
        return 0
    fi

    rm -rf \
        "$general_code_dir/.venv" \
        "$general_code_dir/.requirements.sha256"
    rm -f \
        "$general_code_dir/manifest.json" \
        "$general_code_dir/install.log" \
        "$general_code_dir/requirements.source.txt" \
        "$general_code_dir/requirements.lock.txt"
}

ensure_runtime_venv() {
    env_name="$1"
    env_dir="/app/workspaces/public/runtime-envs/python/$env_name"
    requirements_file="$env_dir/requirements.txt"
    vendor_dir="$env_dir/vendor"
    venv_dir="$env_dir/.venv"
    marker_file="$env_dir/.requirements.sha256"

    if [ ! -f "$requirements_file" ]; then
        return 0
    fi

    current_hash="$(requirements_sha256 "$requirements_file")"
    installed_hash=""
    if [ -f "$marker_file" ]; then
        installed_hash="$(cat "$marker_file")"
    fi

    if [ -x "$venv_dir/bin/python" ] && [ "$current_hash" = "$installed_hash" ]; then
        return 0
    fi

    if [ -z "$(find "$vendor_dir" -mindepth 1 -type f -print -quit 2>/dev/null)" ]; then
        echo "[offline bootstrap] missing vendor packages: $vendor_dir" >&2
        echo "[offline bootstrap] refusing online install for $env_name runtime env" >&2
        return 1
    fi

    rm -rf "$venv_dir"
    python3.11 -m venv "$venv_dir"
    "$venv_dir/bin/python" -m pip install --no-cache-dir \
        --no-index \
        --find-links "$vendor_dir" \
        -r "$requirements_file"
    printf '%s' "$current_hash" > "$marker_file"
}

mkdir -p \
    /app/uploads \
    /app/workspaces/public/skills \
    /app/workspaces/public/skillstudio/skills \
    /app/workspaces/public/runtime-envs/python/default \
    /app/workspaces/public/runtime-envs/python/default/vendor \
    /app/workspaces/public/runtime-envs/python/general-code \
    /app/workspaces/public/runtime-envs/python/general-code/vendor \
    /app/workspaces/public/runtime-envs/python/skills \
    /app/workspaces/public/runtime-envs/caches/pip

copy_dir_if_target_empty /app/default-workspaces/public/skills /app/workspaces/public/skills
copy_dir_if_target_empty /app/default-workspaces/public/skillstudio/skills /app/workspaces/public/skillstudio/skills
copy_file_if_target_missing \
    /app/default-workspaces/public/runtime-envs/python/default/requirements.txt \
    /app/workspaces/public/runtime-envs/python/default/requirements.txt
copy_file_if_target_missing \
    /app/default-workspaces/public/runtime-envs/python/general-code/requirements.txt \
    /app/workspaces/public/runtime-envs/python/general-code/requirements.txt
copy_dir_if_target_empty \
    /app/default-workspaces/public/runtime-envs/python/default/vendor \
    /app/workspaces/public/runtime-envs/python/default/vendor
copy_dir_if_target_empty \
    /app/default-workspaces/public/runtime-envs/python/general-code/vendor \
    /app/workspaces/public/runtime-envs/python/general-code/vendor

prune_legacy_general_code_runtime_artifacts

# 预热默认环境；general-code 共享 requirements/vendor，但 .venv 按用户隔离，运行时再按用户构建。
export PIP_NO_INDEX=1
ensure_runtime_venv default
unset PIP_NO_INDEX

endpoint="$(printf '%s' "${MINIO_ENDPOINT:-}" | tr -d '[:space:]')"
if [ -n "$endpoint" ]; then
    url="${endpoint%/}/minio/health/ready"
    deadline=$(( $(date +%s) + 120 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
            break
        fi
        sleep 2
    done
fi

if [ -f /app/app.jar ]; then
    exec sh -c "java $JAVA_OPTS -jar /app/app.jar"
fi

main_class="${MAIN_CLASS:-org.springframework.boot.loader.launch.JarLauncher}"
exec sh -c "java $JAVA_OPTS $main_class"
