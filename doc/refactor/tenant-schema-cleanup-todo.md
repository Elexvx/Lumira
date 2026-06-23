# Tenant Schema Cleanup TODO

The current refactor phase removes business dependence on tenant context while keeping existing `tenant_id` columns as compatibility fields.

Do not drop `tenant_id`, `sys_tenant`, `sys_user_tenant`, or tenant-scoped unique indexes in this phase. Physical cleanup belongs in a later schema cleanup migration after the retained platform data strategy is confirmed, including how existing tenant `1001` data is preserved and how any non-1001 data is handled.
