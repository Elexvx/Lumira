# Deployment, database roles and network boundaries

## Least-privilege database identities

Production must provision six distinct MySQL accounts. Deployment and updater preflight reject root, missing credentials, placeholder secrets and shared usernames.

| Identity | Environment | Scope |
| --- | --- | --- |
| Application | `DB_USERNAME` / `DB_PASSWORD` | DML and execute on the business schema; no DDL |
| Migrator | `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | Controlled DDL/DML on the business schema |
| Backup | `MYSQL_BACKUP_USERNAME` / `MYSQL_BACKUP_PASSWORD` | Dump/read, metadata and backup coordination only |
| Restore | `MYSQL_RESTORE_USERNAME` / `MYSQL_RESTORE_PASSWORD` | Explicit restore operations on the business schema |
| XXL-Job | `XXL_JOB_DB_USERNAME` / `XXL_JOB_DB_PASSWORD` | Only the dedicated `xxl_job` schema |
| Exporter | `MYSQLD_EXPORTER_USERNAME` / `MYSQLD_EXPORTER_PASSWORD` | Read-only monitoring and process/replication metadata |

`deploy/database/init-database-roles.sh` converges these grants idempotently. It accepts passwords only through environment/secret-manager injection, base64-transports them into prepared SQL, and contains no repository password. The bundled MySQL runs it on first initialization. For managed or existing MySQL, run it once with a temporary administrator credential from a trusted host, inspect `SHOW GRANTS`, then remove that credential from the session.

XXL-Job uses `XXL_JOB_DB_URL`; the URL must resolve to the `xxl_job` schema. It never uses `DB_URL`, the application account, or the MySQL root account. Backup and restore scripts similarly have no fallback to application credentials.

## Existing-deployment migration

1. Freeze deployment changes and capture a verified database backup plus current `.update-state.json`/Release Set evidence.
2. Generate six independent secrets in the secret manager. Do not copy the current application or root password.
3. Run the role initialization script as a database administrator and verify every grant. Test that `lumira_app` can perform DML but receives an authorization error for `CREATE TABLE`/`ALTER TABLE`.
4. Initialize the `xxl_job` schema and point `XXL_JOB_DB_URL` at it. Import the XXL schema there if the prior installation placed it in `saas`.
5. Update `.env`, including `MYSQL_ROOT_PASSWORD` only for bundled-MySQL bootstrap/administration. Set `DB_MIGRATION_NETWORK` and `DB_BACKUP_NETWORK` to the Compose data network (normally `deploy_data-network`).
6. Run the deployment preflight and Compose config validation before any migration. A root, empty or shared account is a blocker, not a warning.
7. Run backup, expand-only migration, candidate readiness, worker drain/replacement and atomic UI/API traffic switch through Release Set v3.

Do not change `DB_USERNAME` on a live process before the new account and grants exist. Do not delete the old accounts until the rollback deadline has expired and the previous release is no longer eligible.

The pinned Flyway 12.5.0 base image is published for `linux/amd64`. Build the production
Migrator with `docker build --platform linux/amd64 -f deploy/docker/migrator.Dockerfile .`;
Apple Silicon development hosts require Docker's amd64 emulation. Treat a future native
arm64 base-image change as a separately reviewed infrastructure digest update.

## Network contract

- `edge-network`: edge proxy, API proxy and UI slots.
- `app-network`: API proxy, Server slots, Async, Job Executor and XXL-Job control traffic.
- `data-network` (internal): MySQL, Redis planes, Server/Async data clients and data exporters.
- `observability-network` (internal): runtimes, exporters, Prometheus, Loki, Tempo, Alloy and Grafana.
- external `1panel-network`: only `edge-proxy` and `api-proxy`.

MySQL, Redis, Async, Job Executor, XXL-Job and Server never join `1panel-network`. Server has no public ingress and receives business traffic through `api-proxy`; internal runtime endpoints remain on `app-network` and require scoped tokens. Redis containers run without Linux capabilities, with `no-new-privileges`, a read-only root filesystem and dedicated writable data volumes.

Alloy reads `/var/lib/docker/containers` through a read-only mount. The Docker control socket is not mounted. Hosts that do not use Docker's `json-file` driver must replace this with an explicit OTLP/syslog log pipeline; reintroducing `docker.sock` is not an accepted fallback.

## Release Set v3 deployment and rollback

The signed manifest remains schema v3 and adds `compatibility.redisTopology`. The generated identity is `redis-split-cache-runtime-v1`, with cache policy `allkeys-lru` and runtime policy `noeviction-aof-everysec`. Updater preflight blocks a v3 Release Set whose identity differs from the deployed topology.

All infrastructure image digests remain locked independently from the five application images. Candidate Server must pass the complete readiness contract and must not run write-oriented background work. Every worker source participates in quiesce/drain before replacement. The updater persists and switches the entire Release Set; it never rolls back one component in isolation.

Application rollback does not restore the database and does not replace Redis runtime data. It is allowed only inside the rollback window while database, Event, Session, Permission Snapshot, Plugin API and Redis topology compatibility all remain valid. Database or runtime-Redis restore is a separately authorized disaster-recovery workflow performed under a write freeze.
