-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202607140001..202608319999
-- lumira:cleanup-after=two-stable-releases
-- Disabled built-in runtimes have already passed runtime verification. The
-- definition status remains DISABLED; this only replaces the legacy lifecycle
-- value so activation can use the fenced state machine.

UPDATE `sys_plugin_version`
SET `lifecycle_status` = 'RUNTIME_VERIFIED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `plugin_code` IN ('builtin-mock-payment', 'builtin-mock-sms', 'builtin-alerting')
  AND `lifecycle_status` = 'DISABLED'
  AND `schema_status` = 'READY'
  AND `deleted` = 0;
