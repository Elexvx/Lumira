# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace

ARG OTEL_JAVAAGENT_URL=
RUN set -eux; \
    if [ -n "$OTEL_JAVAAGENT_URL" ]; then \
      curl --fail --show-error --location --retry 3 --retry-all-errors --connect-timeout 10 --max-time 180 "$OTEL_JAVAAGENT_URL" -o /workspace/opentelemetry-javaagent.jar; \
      jar tf /workspace/opentelemetry-javaagent.jar >/dev/null; \
    else \
      : > /workspace/opentelemetry-javaagent.jar; \
    fi

COPY pom.xml ./
COPY services/system-service/pom.xml services/system-service/pom.xml
COPY libs/common-core/pom.xml libs/common-core/pom.xml
COPY libs/common-domain/pom.xml libs/common-domain/pom.xml
COPY libs/common-security/pom.xml libs/common-security/pom.xml
COPY libs/common-web/pom.xml libs/common-web/pom.xml
COPY libs/lumira-api/pom.xml libs/lumira-api/pom.xml
COPY libs/plugin-api/pom.xml libs/plugin-api/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/file-service/pom.xml services/file-service/pom.xml
COPY services/message-service/pom.xml services/message-service/pom.xml
COPY services/plugin-service/pom.xml services/plugin-service/pom.xml
COPY services/localization-service/pom.xml services/localization-service/pom.xml
COPY services/job-executor/pom.xml services/job-executor/pom.xml
COPY services/lumira-server/pom.xml services/lumira-server/pom.xml

COPY . .

ARG SERVICE_MODULE
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    if [ -n "$SERVICE_MODULE" ]; then \
      mvn -pl "$SERVICE_MODULE" -am -Dmaven.test.skip=true package; \
    else \
      mvn -Dmaven.test.skip=true package; \
    fi

ARG SERVICE_DIR
RUN set -eux; \
    test -n "$SERVICE_DIR"; \
    JAR_FILE="$(find "$SERVICE_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | head -n 1)"; \
    test -n "$JAR_FILE"; \
    cp "$JAR_FILE" /workspace/app.jar

FROM ${JRE_IMAGE}

ENV JAVA_OPTS="" \
    SERVER_PORT=8080 \
    OTEL_JAVAAGENT_ENABLED=false \
    OTEL_JAVAAGENT_PATH=/app/opentelemetry-javaagent.jar

WORKDIR /app

RUN addgroup --system app \
    && adduser --system --ingroup app app \
    && mkdir -p /tmp/nacos /tmp/sentinel /data/uploads /data/plugins /data/plugin-staging \
    && chown -R app:app /tmp/nacos /tmp/sentinel /data

COPY --from=builder /workspace/app.jar /app/app.jar
COPY --from=builder /workspace/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "AGENT_OPTS=''; if [ \"$OTEL_JAVAAGENT_ENABLED\" = \"true\" ]; then if [ ! -s \"$OTEL_JAVAAGENT_PATH\" ]; then echo \"OTEL_JAVAAGENT_ENABLED=true but $OTEL_JAVAAGENT_PATH is missing or empty; rebuild with OTEL_JAVAAGENT_URL\" >&2; exit 64; fi; AGENT_OPTS=\"-javaagent:$OTEL_JAVAAGENT_PATH\"; fi; exec java -DJM.LOG.PATH=/tmp/nacos -Dcsp.sentinel.log.dir=/tmp/sentinel $AGENT_OPTS $JAVA_OPTS -jar /app/app.jar"]
