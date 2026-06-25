# Tenant Schema Cleanup TODO

The current refactor phase is now allowed to physically remove tenant-specific IAM schema after preserving existing platform data.

Core IAM tables (`sys_menu`, `sys_permission`, `sys_role`, `sys_role_permission`, `sys_role_data_scope`, `sys_user_department`, and `sys_user_role`) are refreshed by `lumira-backend/sql/role-only-iam-schema-refresh.sql`. That script backs up the previous tenant-scoped tables with a `role_only_iam_backup_*_20260625` prefix before dropping IAM `tenant_id` columns and `sys_tenant`.

Remaining cleanup should continue table-by-table for business modules that still carry compatibility `tenant_id` columns.
