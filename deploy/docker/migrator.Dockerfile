ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
FROM ${MAVEN_IMAGE} AS bootstrap-builder

WORKDIR /workspace
COPY deploy/bootstrap-admin/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY deploy/bootstrap-admin/src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM redgate/flyway:12.5.0

USER root
COPY deploy/migrations /flyway/sql
COPY lumira-backend/sql/saas-baseline-version.txt /opt/lumira/saas-baseline-version.txt
COPY --from=bootstrap-builder /workspace/target/lumira-bootstrap-admin.jar /opt/lumira/lumira-bootstrap-admin.jar
COPY deploy/docker/migrator-entrypoint.sh /usr/local/bin/lumira-migrate
RUN chmod 0755 /usr/local/bin/lumira-migrate

ENTRYPOINT ["/usr/local/bin/lumira-migrate"]
