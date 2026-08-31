const DATABASE_ROLES = Object.freeze([
  ['application', 'DB_USERNAME', 'DB_PASSWORD'],
  ['migration', 'DB_MIGRATION_USERNAME', 'DB_MIGRATION_PASSWORD'],
  ['backup', 'MYSQL_BACKUP_USERNAME', 'MYSQL_BACKUP_PASSWORD'],
  ['restore', 'MYSQL_RESTORE_USERNAME', 'MYSQL_RESTORE_PASSWORD'],
  ['XXL-Job', 'XXL_JOB_DB_USERNAME', 'XXL_JOB_DB_PASSWORD'],
  ['exporter', 'MYSQLD_EXPORTER_USERNAME', 'MYSQLD_EXPORTER_PASSWORD'],
]);

const REDIS_PLANES = Object.freeze([
  ['cache', 'REDIS_CACHE_HOST', 'REDIS_CACHE_PORT', 'REDIS_CACHE_PASSWORD'],
  ['runtime', 'REDIS_RUNTIME_HOST', 'REDIS_RUNTIME_PORT', 'REDIS_RUNTIME_PASSWORD'],
]);

function value(environment, key) {
  return String(environment?.[key] ?? '').trim();
}
function isPlaceholder(candidate) {
  return /^change-me(?:-|$)/iu.test(candidate);
}

function requireCredential(errors, environment, label, usernameKey, passwordKey) {
  const username = value(environment, usernameKey);
  const password = value(environment, passwordKey);
  if (!username) errors.push(`${usernameKey} is required for the dedicated ${label} database role.`);
  if (username.toLowerCase() === 'root') errors.push(`${usernameKey} must not use root.`);
  if (!password) errors.push(`${passwordKey} is required for the dedicated ${label} database role.`);
  if (password && isPlaceholder(password)) errors.push(`${passwordKey} must not use a placeholder secret.`);
  return { label, username, usernameKey, password, passwordKey };
}

function validateDistinctUsernames(errors, credentials) {
  const owners = new Map();
  for (const credential of credentials) {
    if (!credential.username) continue;
    const normalized = credential.username.toLowerCase();
    const previous = owners.get(normalized);
    if (previous) {
      errors.push(`${credential.usernameKey} must not share the ${credential.username} account with ${previous.usernameKey}.`);
    } else {
      owners.set(normalized, credential);
    }
  }
}

function validateXxlSchema(errors, environment) {
  const url = value(environment, 'XXL_JOB_DB_URL');
  if (!url) {
    errors.push('XXL_JOB_DB_URL is required and must target the dedicated xxl_job schema.');
    return;
  }
  const match = url.match(/^jdbc:mysql:\/\/[^/]+\/([^?]+)(?:\?|$)/iu);
  if (!match || decodeURIComponent(match[1]).toLowerCase() !== 'xxl_job') {
    errors.push('XXL_JOB_DB_URL must target the dedicated xxl_job schema.');
  }
}

function validateRedisPlanes(errors, environment) {
  const planes = REDIS_PLANES.map(([label, hostKey, portKey, passwordKey]) => {
    const host = value(environment, hostKey);
    const port = value(environment, portKey);
    const password = value(environment, passwordKey);
    if (!host) errors.push(`${hostKey} is required for the ${label} Redis instance.`);
    if (!/^\d+$/u.test(port) || Number(port) < 1 || Number(port) > 65535) {
      errors.push(`${portKey} must be a valid TCP port.`);
    }
    if (!password || isPlaceholder(password)) {
      errors.push(`${passwordKey} must be a non-placeholder secret.`);
    }
    return { label, host, port, password };
  });
  const [cache, runtime] = planes;
  if (cache.host && cache.port && cache.host.toLowerCase() === runtime.host.toLowerCase() && cache.port === runtime.port) {
    errors.push('Redis cache and runtime planes must use different instances, not logical database numbers.');
  }
  if (cache.password && runtime.password && cache.password === runtime.password) {
    errors.push('REDIS_CACHE_PASSWORD and REDIS_RUNTIME_PASSWORD must be different secrets.');
  }
}

export function productionDataPlaneErrors(environment, { includeExporter = true, includeRedis = true } = {}) {
  const errors = [];
  const credentials = DATABASE_ROLES
    .filter(([label]) => includeExporter || label !== 'exporter')
    .map(([label, usernameKey, passwordKey]) => requireCredential(errors, environment, label, usernameKey, passwordKey));
  validateDistinctUsernames(errors, credentials);
  validateXxlSchema(errors, environment);
  if (includeRedis) validateRedisPlanes(errors, environment);
  return errors;
}

export function assertProductionDataPlaneEnvironment(environment, options) {
  const errors = productionDataPlaneErrors(environment, options);
  if (errors.length > 0) {
    throw new Error(`Production data-plane configuration is unsafe:\n- ${errors.join('\n- ')}`);
  }
}
