# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
ARG JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre
ARG XXL_JOB_VERSION=3.4.0

FROM ${MAVEN_IMAGE} AS builder

ARG XXL_JOB_VERSION
ARG MAVEN_MIRROR_URL=https://mirrors.cloud.tencent.com/nexus/repository/maven-public
ARG MAVEN_FALLBACK_MIRROR_URL=https://repo.huaweicloud.com/repository/maven
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} \
    MAVEN_OPTS="-Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=120000 -Daether.transport.http.connectTimeout=60000 -Daether.transport.http.requestTimeout=120000 -Daether.connector.basic.threads=1"

WORKDIR /workspace

COPY deploy/docker/maven-settings.xml /workspace/maven-settings.xml

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    set -eux; \
    curl --fail --show-error --location --retry 5 --retry-all-errors \
      "https://github.com/xuxueli/xxl-job/archive/refs/tags/v${XXL_JOB_VERSION}.tar.gz" \
      -o /tmp/xxl-job.tar.gz; \
    mkdir -p /workspace/src; \
    tar -xzf /tmp/xxl-job.tar.gz -C /workspace/src --strip-components=1; \
    cd /workspace/src; \
    run_maven() { \
      mvn --settings /workspace/maven-settings.xml -U -pl xxl-job-admin -am -DskipTests package; \
    }; \
    primary_mirror_url="${MAVEN_MIRROR_URL}"; \
    fallback_mirror_url="${MAVEN_FALLBACK_MIRROR_URL}"; \
    (run_maven \
      || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven) \
      || (export MAVEN_MIRROR_URL="$primary_mirror_url"; run_maven) \
      || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven)); \
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
