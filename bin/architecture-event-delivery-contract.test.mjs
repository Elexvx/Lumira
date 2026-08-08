import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const envExample = parseEnvironment(read(path.join('deploy', '.env.example')));
const composeProd = read(path.join('deploy', 'docker-compose.prod.yml'));
const asyncApplication = read(path.join('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'resources', 'application.yml'));
const serverApplication = read(path.join('lumira-backend', 'services', 'lumira-admin', 'src', 'main', 'resources', 'application.yml'));
const messageApplication = read(path.join('lumira-backend', 'services', 'lumira-message', 'src', 'main', 'resources', 'application.yml'));

function read(...segments) {
  return readFileSync(path.join(repoRoot, ...segments), 'utf8');
}

function parseEnvironment(source) {
  return Object.fromEntries(source
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const index = line.indexOf('=');
      return index < 0 ? [] : [line.slice(0, index), line.slice(index + 1)];
    })
    .filter(([key]) => key));
}

function block(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, `missing configuration block starting with ${startMarker}`);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(end, -1, `missing configuration block ending with ${endMarker}`);
  return source.slice(start, end);
}

function yamlValue(source, key) {
  const match = source.match(new RegExp(`^\\s*${key}:\\s*([^\\r\\n#]+)`, 'm'));
  assert.ok(match, `missing YAML key ${key}`);
  return match[1].trim();
}

function springResolved(value, environment) {
  let resolved = value;
  let previous;
  do {
    previous = resolved;
    resolved = resolved.replace(/\$\{([A-Z0-9_]+):([^${}]*)}/g, (_, key, fallback) => environment[key] ?? fallback);
  } while (resolved !== previous);
  return resolved;
}

function composeServiceBlock(serviceName, nextServiceName) {
  return block(composeProd, `  ${serviceName}:`, `  ${nextServiceName}:`);
}

function composeDefault(source, key) {
  const match = source.match(new RegExp(`^\\s*${key}: \\$\\{${key}:-([^}]+)}`, 'm'));
  assert.ok(match, `missing Compose default for ${key}`);
  return match[1].trim();
}

test('production dispatcher and payment consumer resolve to the same Redis Stream contract', () => {
  const serverService = composeServiceBlock('lumira-server-blue', 'lumira-server-green');
  const asyncService = composeServiceBlock('lumira-async', 'lumira-job-executor');
  const paymentConsumer = block(asyncApplication, '    payment-consumer:', '\nmanagement:');
  const outbox = block(serverApplication, '    outbox:', '  redis:');

  assert.equal(envExample.SAAS_EVENT_OUTBOX_DISPATCHER, 'redis-stream');
  assert.equal(composeDefault(serverService, 'SAAS_EVENT_OUTBOX_DISPATCHER'), 'redis-stream');
  assert.equal(springResolved(yamlValue(outbox, 'dispatcher'), envExample), 'redis-stream');
  assert.doesNotMatch(asyncService, /SAAS_EVENT_OUTBOX_DISPATCHER:/);

  assert.equal(envExample.LUMIRA_PAYMENT_EVENT_CONSUMER_ENABLED, 'true');
  assert.equal(composeDefault(asyncService, 'LUMIRA_PAYMENT_EVENT_CONSUMER_ENABLED'), 'true');
  assert.equal(springResolved(yamlValue(paymentConsumer, 'enabled'), envExample), 'true');

  for (const { environmentKey, yamlKey, expected } of [
    { environmentKey: 'LUMIRA_PAYMENT_EVENT_CONSUMER_STREAM_KEY', yamlKey: 'stream-key', expected: 'lumira.events.payment.v1' },
    { environmentKey: 'LUMIRA_PAYMENT_EVENT_CONSUMER_GROUP_NAME', yamlKey: 'group-name', expected: 'competition-payment-v1' },
    { environmentKey: 'LUMIRA_PAYMENT_EVENT_CONSUMER_PENDING_RECOVERY_MINIMUM_IDLE', yamlKey: 'pending-recovery-minimum-idle', expected: '30s' },
    { environmentKey: 'LUMIRA_PAYMENT_EVENT_CONSUMER_PENDING_RECOVERY_INTERVAL', yamlKey: 'pending-recovery-interval', expected: '30s' },
    { environmentKey: 'LUMIRA_PAYMENT_EVENT_CONSUMER_MAX_DELIVERY_COUNT', yamlKey: 'max-delivery-count', expected: '8' },
  ]) {
    assert.equal(envExample[environmentKey], expected);
    assert.equal(composeDefault(asyncService, environmentKey), expected);
    assert.equal(springResolved(yamlValue(paymentConsumer, yamlKey), envExample), expected);
  }
});

test('production review-result consumer resolves to the dispatcher stream', () => {
  const serverService = composeServiceBlock('lumira-server-blue', 'lumira-server-green');
  const reviewConsumer = block(messageApplication, '    review-result-consumer:', '\nspring.cloud.sentinel.enabled:');

  assert.equal(envExample.SAAS_EVENT_REDIS_STREAM_KEY, 'saas:platform-events');
  assert.equal(envExample.LUMIRA_EVENT_REVIEW_RESULT_CONSUMER_ENABLED, 'true');
  assert.equal(composeDefault(serverService, 'LUMIRA_EVENT_REVIEW_RESULT_CONSUMER_ENABLED'), 'true');
  assert.equal(springResolved(yamlValue(reviewConsumer, 'enabled'), envExample), 'true');
  assert.equal(springResolved(yamlValue(reviewConsumer, 'stream-key'), envExample), envExample.SAAS_EVENT_REDIS_STREAM_KEY);
  assert.equal(springResolved(yamlValue(reviewConsumer, 'group-name'), envExample), envExample.LUMIRA_EVENT_REVIEW_RESULT_CONSUMER_GROUP_NAME);
});

test('logging remains an explicit local diagnostic choice, not a production default', () => {
  assert.notEqual(envExample.SAAS_EVENT_OUTBOX_DISPATCHER, 'logging');
  assert.match(envExample.SAAS_EVENT_OUTBOX_DISPATCHER, /^redis-stream$/);
});

test('async owner relays follow the active control-plane slot without owner service URLs', () => {
  const asyncService = composeServiceBlock('lumira-async', 'lumira-job-executor');
  const ownerRelay = block(asyncApplication, '    owner-relay:', '  event:');

  assert.equal(envExample.LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL, 'http://api-proxy:80');
  assert.equal(composeDefault(asyncService, 'LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL'), 'http://api-proxy:80');
  assert.equal(
    springResolved(yamlValue(ownerRelay, 'control-plane-base-url'), envExample),
    envExample.LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL,
  );
  for (const legacyOwnerUrl of [
    'SYSTEM_SERVICE_BASE_URL',
    'FILE_SERVICE_BASE_URL',
    'MESSAGE_SERVICE_BASE_URL',
    'PAYMENT_SERVICE_BASE_URL',
    'PLUGIN_SERVICE_BASE_URL',
    'COMPETITION_SERVICE_BASE_URL',
  ]) {
    assert.equal(envExample[legacyOwnerUrl], undefined);
    assert.doesNotMatch(asyncService, new RegExp(`${legacyOwnerUrl}:`));
  }
});
