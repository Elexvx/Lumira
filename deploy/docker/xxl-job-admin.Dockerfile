# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
ARG XXL_JOB_VERSION=3.4.0

FROM ${MAVEN_IMAGE} AS builder

ARG XXL_JOB_VERSION

WORKDIR /workspace

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    set -eux; \
    curl --fail --show-error --location --retry 5 --retry-all-errors \
      "https://github.com/xuxueli/xxl-job/archive/refs/tags/v${XXL_JOB_VERSION}.tar.gz" \
      -o /tmp/xxl-job.tar.gz; \
    mkdir -p /workspace/src; \
    tar -xzf /tmp/xxl-job.tar.gz -C /workspace/src --strip-components=1; \
    cd /workspace/src; \
    mvn -pl xxl-job-admin -am -DskipTests package; \
    JAR_FILE="$(find xxl-job-admin/target -maxdepth 1 -type f -name 'xxl-job-admin-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | head -n 1)"; \
    test -n "$JAR_FILE"; \
    cp "$JAR_FILE" /workspace/xxl-job-admin.jar

FROM ${JRE_IMAGE}

ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom" \
    PARAMS=""

WORKDIR /app

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl; \
    rm -rf /var/lib/apt/lists/*; \
    addgroup --system app; \
    adduser --system --ingroup app app; \
    mkdir -p /data/applogs/xxl-job; \
    chown -R app:app /app /data/applogs

COPY --from=builder /workspace/xxl-job-admin.jar /app/xxl-job-admin.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=30 --start-period=30s \
  CMD curl -fsS http://127.0.0.1:8080/xxl-job-admin/ >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/xxl-job-admin.jar $PARAMS"]
