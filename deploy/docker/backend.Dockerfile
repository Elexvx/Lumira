# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /workspace

RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Central Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
COPY libs/common-core/pom.xml libs/common-core/pom.xml
COPY libs/common-security/pom.xml libs/common-security/pom.xml
COPY libs/common-web/pom.xml libs/common-web/pom.xml
COPY libs/legendary-api/pom.xml libs/legendary-api/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/file-service/pom.xml services/file-service/pom.xml
COPY services/message-service/pom.xml services/message-service/pom.xml
COPY services/plugin-service/pom.xml services/plugin-service/pom.xml
COPY services/localization-service/pom.xml services/localization-service/pom.xml
COPY services/gateway-service/pom.xml services/gateway-service/pom.xml
COPY services/job-executor/pom.xml services/job-executor/pom.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests package

ARG SERVICE_DIR
RUN set -eux; \
    test -n "$SERVICE_DIR"; \
    JAR_FILE="$(find "$SERVICE_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | head -n 1)"; \
    test -n "$JAR_FILE"; \
    cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:21-jre

ENV JAVA_OPTS="" \
    SERVER_PORT=8080

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app && mkdir -p /tmp/nacos /tmp/sentinel && chown -R app:app /tmp/nacos /tmp/sentinel

COPY --from=builder /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -DJM.LOG.PATH=/tmp/nacos -Dcsp.sentinel.log.dir=/tmp/sentinel $JAVA_OPTS -jar /app/app.jar"]
