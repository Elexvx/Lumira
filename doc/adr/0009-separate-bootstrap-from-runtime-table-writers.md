# ADR-0009: Separate bootstrap schema from runtime table writers

## Status

Accepted

## Context

Flyway is disabled and a fresh database is initialized through
`lumira-backend/sql/saas.sql`.  Several bounded contexts were extracted from
`lumira-system`, but the table-owner manifest previously represented that
historical bootstrap path as a compatible `lumira-system` writer.  A Java SQL
guard therefore accepted a runtime cross-owner write simply because the same
table appears in the bootstrap schema.

## Decision

`doc/27-ddd-owner-table-manifest.csv` records two independent facts:

- `bootstrap_schema_paths` identifies the fresh-schema source that creates a
  table. It is documentation and bootstrap coverage only; it grants no Java
  runtime access.
- `runtime_writer_modules` is the only manifest permission considered by the
  runtime SQL guard. Extracted business contexts have no compatible runtime
  writers: only their owner module may read or write its tables.

The only non-owner runtime access is represented in the architecture test as
an exact table-to-module mapping, currently the shared outbox dispatcher and
the Message idempotency receipt. A context-wide compatibility list is not an
acceptable substitute. Cross-owner reads must use an owner API/port or a
rebuildable projection.

## Consequences

- `saas.sql` remains the new-database bootstrap source without turning
  `lumira-system` into a general runtime writer.
- Java JDBC/custom SQL facade/text-block/annotated SQL scanning rejects direct
  cross-owner access for the extracted Competition context, including payment,
  project, team, expert, and IAM tables.
- A future schema change updates the bootstrap SQL and table manifest, while a
  new runtime exception requires an explicit table-level justification and a
  regression test.
