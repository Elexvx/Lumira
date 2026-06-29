# Tenant Schema Cleanup

The tenant-schema cleanup has been consolidated into `lumira-backend/sql/saas.sql`.
Fresh local and prelaunch databases should use that single role-only initialization
script instead of the archived one-off refresh scripts.

The former `role-only-*.sql` and project-menu repair scripts were temporary
upgrade helpers for already-running local databases. Their final table shapes,
indexes, IAM bootstrap rows, project-management menu entries, and role permissions
now live in `saas.sql`.
