# Compatibility, rollback and remaining risks

## Compatibility and rollback contract

- Release Set v3 remains atomic: Server, UI, Async, Job, migration contract, plugin API
  compatibility and Redis topology identity are evaluated together.
- The inactive Server/UI slot must become ready before traffic switches. Candidate Server
  background writes stay disabled. Every worker work source must drain before replacement.
- Database migrations are expand-only. Application rollback is allowed only while the
  previous readers accept the new schema. Database restore is a separate disaster-recovery
  operation and is never an automatic component rollback.
- Rollback restores the complete `previousRelease`; individual component rollback is
  prohibited. Infrastructure images are immutable digest inputs and must be rotated as an
  explicitly reviewed deployment change.
- Upgraders must provision the database roles and physical Redis split before installing
  this release, run `V202608310002__add_plugin_migration_requests.sql`, and keep both Redis
  data sets until rollback eligibility expires.

## Remaining risks and follow-up work

1. The one-shot privileged plugin Migrator and audited host-side approval CLI are implemented.
   Operators still need to provision a digest-pinned Migrator image and protect access to the
   deployment host and `DB_MIGRATION_PASSWORD`; requests remain safely in `MIGRATION_PENDING`
   until that explicit approval occurs.
2. Outbox replay is currently an XXL-JOB recovery operation. A control-plane administrator
   API still needs explicit authorization, audit records and DLQ-specific replay UX.
3. Redis Stream max-length configuration and observability contracts exist, but producer
   trimming must remain pending-aware. A production dashboard should expose oldest pending
   age, pending count and per-owner DLQ count before enabling automatic retention changes.
4. Recovery fencing is enforced at the Async ingress. Owner replay endpoints should also
   persist the accepted fence before Job-to-Server network access can ever be allowed.
5. Async retains a test-only in-memory fence fallback, but its production readiness contract
   now reports `DEGRADED` when the durable runtime Redis fence store is unavailable.
6. Directory relocation to `runtimes/`, `modules/`, `contracts/` and `platform/` is deferred;
   current Maven and ArchUnit rules are the compatibility bridge, not the final layout.

These risks do not grant Async or Job database access and must not be addressed by
reintroducing in-process plugin DDL, normal Relay scheduling in Job, bean overriding, or
single-Redis logical-database isolation.
