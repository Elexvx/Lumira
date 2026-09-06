import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateRedisKeyOwnership } from './check-redis-key-ownership.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const compose = readFileSync(path.join(repoRoot, 'deploy', 'docker-compose.prod.yml'), 'utf8');
const redisConfig = readFileSync(
  path.join(repoRoot, 'lumira-backend', 'services', 'lumira-system', 'src', 'main', 'java', 'com', 'lumira', 'saas', 'infrastructure', 'redis', 'RedisConfig.java'),
  'utf8',
);
const indicator = readFileSync(
  path.join(repoRoot, 'lumira-backend', 'services', 'lumira-system', 'src', 'main', 'java', 'com', 'lumira', 'saas', 'infrastructure', 'redis', 'RedisPlaneHealthIndicator.java'),
  'utf8',
);
const messageCacheTemplate = readFileSync(
  path.join(repoRoot, 'lumira-backend', 'services', 'lumira-message', 'src', 'main', 'java', 'com', 'lumira', 'message', 'infrastructure', 'redis', 'CacheTemplate.java'),
  'utf8',
);
const validationDoc = readFileSync(
  path.join(repoRoot, 'docs', 'architecture', 'redis-runtime-validation.md'),
  'utf8',
);
const validationScript = readFileSync(
  path.join(repoRoot, 'bin', 'redis-runtime-chaos-test.mjs'),
  'utf8',
);

test('Redis key ownership registry is valid and required runtime/cache keys are classified', () => {
  const registry = validateRedisKeyOwnership({ root: repoRoot });
  assert.equal(registry.keys.length >= 18, true);
});

test('application wiring uses a dedicated cache connection when production isolation is enabled', () => {
  assert.match(redisConfig, /REDIS_CACHE_ENABLED/);
  assert.match(redisConfig, /cacheRedisConnectionFactory/);
  assert.match(redisConfig, /cacheRedisTemplate/);
  assert.match(redisConfig, /Primary/);
  assert.match(indicator, /physicalIsolation/);
  assert.match(indicator, /lumira\.redis\.plane\.available/);
  assert.match(indicator, /lumira\.redis\.plane\.isolated/);
  assert.match(indicator, /redis_runtime_evicted_keys/);
  assert.match(messageCacheTemplate, /@Qualifier\("cacheRedisTemplate"\)/);
});

test('runtime validation requires eviction, persistence and Stream evidence', () => {
  assert.match(validationDoc, /XPENDING/);
  assert.match(validationDoc, /MAXLEN/);
  assert.match(validationDoc, /evicted_keys/);
  assert.match(validationScript, /CONFIG.*maxmemory-policy/s);
  assert.match(validationScript, /XPENDING/);
  assert.match(validationScript, /XLEN/);
  assert.doesNotMatch(validationScript, /FLUSHDB/);
});

test('production Compose enables the cache plane explicitly while keeping runtime settings separate', () => {
  assert.match(compose, /REDIS_CACHE_ENABLED: \$\{REDIS_CACHE_ENABLED:-true\}/);
  assert.match(compose, /REDIS_HOST: \$\{REDIS_RUNTIME_HOST:-redis-runtime\}/);
  assert.match(compose, /REDIS_CACHE_HOST: \$\{REDIS_CACHE_HOST:-redis-cache\}/);
});
