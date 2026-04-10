FROM eclipse-temurin:17-jre
WORKDIR /app
EXPOSE 5050
ENV TZ=Asia/Shanghai

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY backend/target/backend.jar /app/app.jar
COPY skills /app/default-skills
COPY deploy/docker/backend-entrypoint.sh /app/backend-entrypoint.sh

ENV JAVA_OPTS="" \
    SKILL_ROOT="/app/skills"

RUN chmod +x /app/backend-entrypoint.sh

CMD ["/app/backend-entrypoint.sh"]
