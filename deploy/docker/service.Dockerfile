ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
ARG JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace

ARG MAVEN_MIRROR_URL=https://mirrors.cloud.tencent.com/nexus/repository/maven-public
ARG MAVEN_FALLBACK_MIRROR_URL=https://repo.huaweicloud.com/repository/maven
ARG OTEL_JAVAAGENT_URL=
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} \
    MAVEN_OPTS="-Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=120000 -Daether.transport.http.connectTimeout=60000 -Daether.transport.http.requestTimeout=120000 -Daether.connector.basic.threads=1"
COPY deploy/docker/maven-settings.xml /workspace/maven-settings.xml
RUN set -eux; \
    if [ -n "$OTEL_JAVAAGENT_URL" ]; then \
      curl --fail --show-error --location --retry 3 --retry-all-errors --connect-timeout 10 --max-time 180 "$OTEL_JAVAAGENT_URL" -o /workspace/opentelemetry-javaagent.jar; \
      jar tf /workspace/opentelemetry-javaagent.jar >/dev/null; \
    else \
      : > /workspace/opentelemetry-javaagent.jar; \
    fi

COPY lumira-backend/pom.xml ./
COPY lumira-backend/services/lumira-system/pom.xml services/lumira-system/pom.xml
COPY lumira-backend/libs/lumira-common-core/pom.xml libs/lumira-common-core/pom.xml
COPY lumira-backend/libs/lumira-common-domain/pom.xml libs/lumira-common-domain/pom.xml
COPY lumira-backend/libs/lumira-common-security/pom.xml libs/lumira-common-security/pom.xml
COPY lumira-backend/libs/lumira-common-web/pom.xml libs/lumira-common-web/pom.xml
COPY lumira-backend/libs/lumira-common-api/pom.xml libs/lumira-common-api/pom.xml
COPY lumira-backend/libs/lumira-plugin-api/pom.xml libs/lumira-plugin-api/pom.xml
COPY lumira-backend/services/lumira-auth/pom.xml services/lumira-auth/pom.xml
COPY lumira-backend/services/lumira-file/pom.xml services/lumira-file/pom.xml
COPY lumira-backend/services/lumira-message/pom.xml services/lumira-message/pom.xml
COPY lumira-backend/services/lumira-alerting/pom.xml services/lumira-alerting/pom.xml
COPY lumira-backend/services/lumira-plugin/pom.xml services/lumira-plugin/pom.xml
COPY lumira-backend/services/lumira-localization/pom.xml services/lumira-localization/pom.xml
COPY lumira-backend/services/lumira-quartz/pom.xml services/lumira-quartz/pom.xml
COPY lumira-backend/services/lumira-admin/pom.xml services/lumira-admin/pom.xml
COPY lumira-backend/services/lumira-async/pom.xml services/lumira-async/pom.xml

COPY lumira-backend/services services
COPY lumira-backend/libs libs

# Build every runtime artifact in one reactor. The three Compose image builds
# share this exact layer, so common modules are compiled once instead of once
# per service image.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    run_maven() { \
      mvn \
        --settings /workspace/maven-settings.xml \
        -U \
        -pl services/lumira-admin,services/lumira-async,services/lumira-quartz \
        -am \
        -Dmaven.test.skip=true \
        package; \
    }; \
    primary_mirror_url="${MAVEN_MIRROR_URL}"; \
    fallback_mirror_url="${MAVEN_FALLBACK_MIRROR_URL}"; \
    run_maven \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$primary_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven)

FROM ${JRE_IMAGE} AS runtime

ARG APP_VERSION=
ARG BUILD_VERSION=
ARG FRONTEND_VERSION=
ARG BACKEND_VERSION=
ARG DATABASE_VERSION=
ARG BUILD_TIME=
ARG GIT_COMMIT=
ARG GIT_BRANCH=

ENV JAVA_OPTS="" \
    SERVER_PORT=8080 \
    OTEL_JAVAAGENT_ENABLED=false \
    OTEL_JAVAAGENT_PATH=/app/opentelemetry-javaagent.jar \
    LUMIRA_IMAGE_APP_VERSION=${APP_VERSION} \
    LUMIRA_IMAGE_BUILD_VERSION=${BUILD_VERSION} \
    LUMIRA_IMAGE_FRONTEND_VERSION=${FRONTEND_VERSION} \
    LUMIRA_IMAGE_BACKEND_VERSION=${BACKEND_VERSION} \
    LUMIRA_IMAGE_DATABASE_VERSION=${DATABASE_VERSION} \
    LUMIRA_IMAGE_BUILD_TIME=${BUILD_TIME} \
    LUMIRA_IMAGE_GIT_COMMIT=${GIT_COMMIT} \
    LUMIRA_IMAGE_GIT_BRANCH=${GIT_BRANCH}

WORKDIR /app

RUN addgroup --system app \
    && adduser --system --ingroup app app \
    && mkdir -p /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging /app/storage \
    && chown -R app:app /tmp/sentinel /data /app/storage

COPY --from=builder /workspace/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "AGENT_OPTS=''; if [ \"$OTEL_JAVAAGENT_ENABLED\" = \"true\" ]; then if [ ! -s \"$OTEL_JAVAAGENT_PATH\" ]; then echo \"OTEL_JAVAAGENT_ENABLED=true but $OTEL_JAVAAGENT_PATH is missing or empty; rebuild with OTEL_JAVAAGENT_URL\" >&2; exit 64; fi; AGENT_OPTS=\"-javaagent:$OTEL_JAVAAGENT_PATH\"; fi; exec java -Dcsp.sentinel.log.dir=/tmp/sentinel $AGENT_OPTS $JAVA_OPTS -jar /app/app.jar"]

FROM runtime AS lumira-server-image
COPY --from=builder /workspace/services/lumira-admin/target/lumira-server-*.jar /app/app.jar

FROM runtime AS lumira-async-image
COPY --from=builder /workspace/services/lumira-async/target/lumira-async-*.jar /app/app.jar

FROM runtime AS lumira-job-executor-image
COPY --from=builder /workspace/services/lumira-quartz/target/job-executor-*.jar /app/app.jar
