FROM redgate/flyway:12.5.0

USER root
COPY deploy/migrations /flyway/sql
COPY deploy/docker/migrator-entrypoint.sh /usr/local/bin/lumira-migrate
RUN chmod 0755 /usr/local/bin/lumira-migrate

ENTRYPOINT ["/usr/local/bin/lumira-migrate"]
