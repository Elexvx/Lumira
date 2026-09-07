# IAM authorization versioning

Lumira authorization sessions use a version vector rather than one global
permission-snapshot counter. The opaque session value carries four fenced
dimensions:

- `subjectVersion` for status/profile facts that affect one subject;
- `bindingVersion` for that subject's role and department bindings;
- `roleVersion` for each role represented in the session;
- `dataPolicyVersion` for each represented role plus the global department
  hierarchy policy.

Role mutation increments only that role's version. User-role or user-department
mutation increments only that subject's binding version. Therefore modifying
one role no longer expires unrelated users' sessions. Department hierarchy
changes advance the global data-policy dimension because they can affect data
scope expansion for many roles.

Request validation reads all required dimension keys from `redis-runtime` in a
single multi-get. A missing key is rehydrated from `ddd_read_model_version` and
emits `AUTHZ_VERSION_REHYDRATE`. A Redis error, database rehydrate error, or
invalid version vector is fail-closed. Stale, unavailable, rehydrate, and
session removal paths emit `AUTHZ_VERSION_STALE`,
`AUTHZ_VERSION_UNAVAILABLE`, `AUTHZ_VERSION_REHYDRATE`, and
`AUTHZ_SESSION_REVOKED` respectively.

Authorization mutations bump the database version and write through to
`redis-runtime` inside the application mutation boundary. Any write-through
failure aborts the mutation. A stale session is rejected on its next request
and removed with compare-and-delete semantics; activity refresh does not extend
or revive stale authorization state.

Legacy global `vN:data-scope-cache-v4` values are deliberately rejected after
the rollout. This causes a one-time safe reauthentication rather than allowing
old sessions to bypass the new dimensions.
