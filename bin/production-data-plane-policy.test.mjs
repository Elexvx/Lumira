import assert from 'node:assert/strict';
import test from 'node:test';

import { assertProductionDataPlaneEnvironment, productionDataPlaneErrors } from './lib/production-data-plane-policy.mjs';

const safeEnvironment = () => ({
  DB_USERNAME: 'lumira_app',
  DB_PASSWORD: 'app-secret',
  DB_MIGRATION_USERNAME: 'lumira_migrator',
  DB_MIGRATION_PASSWORD: 'migration-secret',
  MYSQL_BACKUP_USERNAME: 'lumira_backup',
  MYSQL_BACKUP_PASSWORD: 'backup-secret',
  MYSQL_RESTORE_USERNAME: 'lumira_restore',
  MYSQL_RESTORE_PASSWORD: 'restore-secret',
  XXL_JOB_DB_URL: 'jdbc:mysql://mysql:3306/xxl_job?useSSL=false',
  XXL_JOB_DB_USERNAME: 'xxl_job',
  XXL_JOB_DB_PASSWORD: 'xxl-secret',
  MYSQLD_EXPORTER_USERNAME: 'exporter',
  MYSQLD_EXPORTER_PASSWORD: 'exporter-secret',
  REDIS_CACHE_HOST: 'redis-cache',
  REDIS_CACHE_PORT: '6379',
  REDIS_CACHE_PASSWORD: 'cache-secret',
  REDIS_RUNTIME_HOST: 'redis-runtime',
  REDIS_RUNTIME_PORT: '6379',
  REDIS_RUNTIME_PASSWORD: 'runtime-secret',
});

test('production data-plane policy accepts six distinct database roles and physical Redis split', () => {
  assert.doesNotThrow(() => assertProductionDataPlaneEnvironment(safeEnvironment()));
});

test('production data-plane policy rejects root, empty migrator, and shared operational accounts', () => {
  const environment = safeEnvironment();
  environment.DB_USERNAME = 'root';
  environment.DB_MIGRATION_USERNAME = '';
  environment.MYSQL_BACKUP_USERNAME = 'lumira_restore';
  const errors = productionDataPlaneErrors(environment);
  assert.ok(errors.some((error) => /DB_USERNAME must not use root/u.test(error)));
  assert.ok(errors.some((error) => /DB_MIGRATION_USERNAME is required/u.test(error)));
  assert.ok(errors.some((error) => /MYSQL_RESTORE_USERNAME/u.test(error) && /MYSQL_BACKUP_USERNAME/u.test(error)));
});

test('production data-plane policy rejects XXL on the business schema and logical Redis isolation', () => {
  const environment = safeEnvironment();
  environment.XXL_JOB_DB_URL = 'jdbc:mysql://mysql:3306/saas';
  environment.REDIS_RUNTIME_HOST = environment.REDIS_CACHE_HOST;
  environment.REDIS_RUNTIME_PASSWORD = environment.REDIS_CACHE_PASSWORD;
  const errors = productionDataPlaneErrors(environment);
  assert.ok(errors.some((error) => /dedicated xxl_job schema/u.test(error)));
  assert.ok(errors.some((error) => /not logical database numbers/u.test(error)));
  assert.ok(errors.some((error) => /must be different secrets/u.test(error)));
});
