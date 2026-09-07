ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
ARG FLYWAY_IMAGE=docker.m.daocloud.io/redgate/flyway:12.5.0
FROM ${MAVEN_IMAGE} AS bootstrap-builder

ARG MAVEN_MIRROR_URL=https://mirrors.cloud.tencent.com/nexus/repository/maven-public
ARG MAVEN_FALLBACK_MIRROR_URL=https://repo.huaweicloud.com/repository/maven
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} \
    MAVEN_OPTS="-Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=120000 -Daether.transport.http.connectTimeout=60000 -Daether.transport.http.requestTimeout=120000 -Daether.connector.basic.threads=1"
WORKDIR /workspace
COPY deploy/docker/maven-settings.xml /workspace/maven-settings.xml
COPY deploy/bootstrap-admin/pom.xml ./pom.xml
COPY deploy/bootstrap-admin/src ./src
RUN run_maven() { \
      mvn --settings /workspace/maven-settings.xml -U -B -ntp -DskipTests clean package; \
    }; \
    primary_mirror_url="${MAVEN_MIRROR_URL}"; \
    fallback_mirror_url="${MAVEN_FALLBACK_MIRROR_URL}"; \
    run_maven \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$primary_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven)

FROM ${MAVEN_IMAGE} AS plugin-migrator-builder

ARG MAVEN_MIRROR_URL=https://mirrors.cloud.tencent.com/nexus/repository/maven-public
ARG MAVEN_FALLBACK_MIRROR_URL=https://repo.huaweicloud.com/repository/maven
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} \
    MAVEN_OPTS="-Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=120000 -Daether.transport.http.connectTimeout=60000 -Daether.transport.http.requestTimeout=120000 -Daether.connector.basic.threads=1"
WORKDIR /workspace
COPY deploy/docker/maven-settings.xml /workspace/maven-settings.xml
COPY deploy/plugin-migrator/pom.xml ./pom.xml
COPY deploy/plugin-migrator/src ./src
RUN run_maven() { \
      mvn --settings /workspace/maven-settings.xml -U -B -ntp -DskipTests clean package; \
    }; \
    primary_mirror_url="${MAVEN_MIRROR_URL}"; \
    fallback_mirror_url="${MAVEN_FALLBACK_MIRROR_URL}"; \
    run_maven \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$primary_mirror_url"; run_maven) \
    || (export MAVEN_MIRROR_URL="$fallback_mirror_url"; run_maven)

FROM ${FLYWAY_IMAGE}

USER root
COPY deploy/migrations /flyway/sql
COPY lumira-backend/sql/saas-baseline-version.txt /opt/lumira/saas-baseline-version.txt
COPY --from=bootstrap-builder /workspace/target/lumira-bootstrap-admin.jar /opt/lumira/lumira-bootstrap-admin.jar
COPY --from=plugin-migrator-builder /workspace/target/lumira-plugin-migrator.jar /opt/lumira/lumira-plugin-migrator.jar
COPY deploy/docker/migrator-entrypoint.sh /usr/local/bin/lumira-migrate
RUN chmod 0755 /usr/local/bin/lumira-migrate

ENTRYPOINT ["/usr/local/bin/lumira-migrate"]
