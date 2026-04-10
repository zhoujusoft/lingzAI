#!/bin/sh
set -eu

mkdir -p /app/uploads /app/skills

if [ -d /app/default-skills ] && [ -z "$(find /app/skills -mindepth 1 -print -quit 2>/dev/null)" ]; then
    cp -R /app/default-skills/. /app/skills/
fi

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
