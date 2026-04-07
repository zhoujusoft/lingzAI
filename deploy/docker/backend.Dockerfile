FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}

COPY pom.xml ./pom.xml
COPY core/pom.xml ./core/pom.xml
COPY backend/pom.xml ./backend/pom.xml
COPY model-gateway-poc/pom.xml ./model-gateway-poc/pom.xml
COPY deploy/config/maven/settings.xml /usr/share/maven/ref/settings-docker.xml

# Pre-fetch dependencies so source code changes can reuse this layer.
RUN mvn -s /usr/share/maven/ref/settings-docker.xml -pl backend -am -DskipTests dependency:go-offline

COPY core ./core
COPY backend ./backend
COPY model-gateway-poc ./model-gateway-poc

RUN mvn -s /usr/share/maven/ref/settings-docker.xml -pl backend -am -DskipTests package
RUN java -Djarmode=layertools -jar /build/backend/target/backend.jar extract --destination /build/backend/layers

FROM eclipse-temurin:17-jre
WORKDIR /app
EXPOSE 5050
ENV TZ=Asia/Shanghai

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY deploy/requirements.txt /app/requirements.txt
COPY --from=builder /build/backend/layers/dependencies/ ./
COPY --from=builder /build/backend/layers/snapshot-dependencies/ ./
COPY --from=builder /build/backend/layers/spring-boot-loader/ ./
COPY --from=builder /build/backend/layers/application/ ./
COPY skills /app/default-skills
COPY deploy/docker/backend-entrypoint.sh /app/backend-entrypoint.sh
COPY deploy/docker/bootstrap-python-runtime.sh /app/bootstrap-python-runtime.sh

ENV JAVA_OPTS="" \
    PATH="/app/runtime/python/venv/bin:${PATH}" \
    PYTHON_RUNTIME_DIR="/app/runtime/python" \
    PYTHON_BOOTSTRAP_STATUS_FILE="/app/runtime/python/bootstrap.status" \
    PYTHON_BOOTSTRAP_READY_FILE="/app/runtime/python/.ready" \
    PYTHON_BOOTSTRAP_ENABLED="true" \
    PIP_INDEX_URL="https://pypi.tuna.tsinghua.edu.cn/simple" \
    PIP_TRUSTED_HOST="pypi.tuna.tsinghua.edu.cn" \
    SKILL_ROOT="/app/skills"

RUN chmod +x /app/backend-entrypoint.sh /app/bootstrap-python-runtime.sh

ENV MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"

CMD ["/app/backend-entrypoint.sh"]
