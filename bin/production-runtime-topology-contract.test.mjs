import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const composeProd = read('deploy', 'docker-compose.prod.yml');
const envExample = read('deploy', '.env.example');
const apiProxyTemplate = read('deploy', 'nginx', 'api.conf.template');
const serverApplication = read('lumira-backend', 'services', 'lumira-admin', 'src', 'main', 'resources', 'application.yml');
const asyncApplication = read('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'resources', 'application.yml');
const jobApplication = read('lumira-backend', 'services', 'lumira-quartz', 'src', 'main', 'resources', 'application.yml');
const asyncRuntime = read('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'java', 'com', 'lumira', 'asyncruntime', 'LumiraAsyncApplication.java');
const asyncRelay = read('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'java', 'com', 'lumira', 'asyncruntime', 'OutboxRelayCoordinator.java');
const asyncReadiness = read('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'java', 'com', 'lumira', 'asyncruntime', 'AsyncRuntimeReadinessV2Controller.java');
const asyncVersion = read('lumira-backend', 'services', 'lumira-async', 'src', 'main', 'java', 'com', 'lumira', 'asyncruntime', 'AsyncVersionController.java');
const jobReadiness = read('lumira-backend', 'services', 'lumira-quartz', 'src', 'main', 'java', 'com', 'lumira', 'job', 'JobReadinessV2Controller.java');
const jobVersion = read('lumira-backend', 'services', 'lumira-quartz', 'src', 'main', 'java', 'com', 'lumira', 'job', 'VersionController.java');
const jobHandler = read('lumira-backend', 'services', 'lumira-quartz', 'src', 'main', 'java', 'com', 'lumira', 'job', 'OutboxRelayJobHandler.java');
const serverVersion = read('lumira-backend', 'services', 'lumira-system', 'src', 'main', 'java', 'com', 'lumira', 'saas', 'modules', 'config', 'controller', 'VersionController.java');

function read(...segments) {
  return readFileSync(path.join(repoRoot, ...segments), 'utf8');
}

function serviceBlock(name, nextName) {
  const start = composeProd.indexOf(`  ${name}:`);
  const end = nextName
    ? composeProd.indexOf(`  ${nextName}:`, start + 1)
    : composeProd.indexOf('\nvolumes:', start + 1);
  assert.notEqual(start, -1, `missing ${name} service`);
  assert.notEqual(end, -1, `missing boundary after ${name}`);
  return composeProd.slice(start, end);
}

const ownerUpstreamKeys = [
  'GATEWAY_UPSTREAM',
  'SYSTEM_SERVICE_UPSTREAM',
  'AUTH_SERVICE_UPSTREAM',
  'FILE_SERVICE_UPSTREAM',
  'MESSAGE_SERVICE_UPSTREAM',
  'PLUGIN_SERVICE_UPSTREAM',
  'PAYMENT_SERVICE_UPSTREAM',
  'LOCALIZATION_SERVICE_UPSTREAM',
  'TEAM_SERVICE_UPSTREAM',
  'AI_SERVICE_UPSTREAM',
];

const legacyJobServiceUrlKeys = [
  'SAAS_JOB_BACKEND_BASE_URL',
  'SAAS_JOB_SYSTEM_SERVICE_BASE_URL',
  'SAAS_JOB_MESSAGE_SERVICE_BASE_URL',
  'SAAS_JOB_FILE_SERVICE_BASE_URL',
  'SAAS_JOB_PAYMENT_SERVICE_BASE_URL',
  'SAAS_JOB_PLUGIN_SERVICE_BASE_URL',
];

test('formal production topology contains only server, async, and job runtime roles', () => {
  assert.match(serviceBlock('lumira-server-blue', 'lumira-server-green'), /&lumira-server[\s\S]*target: lumira-server-image/);
  assert.match(serviceBlock('lumira-server-green', 'lumira-async'), /<<: \*lumira-server/);
  assert.match(serviceBlock('lumira-async', 'lumira-job-executor'), /target: lumira-async-image/);
  assert.match(serviceBlock('lumira-job-executor'), /target: lumira-job-executor-image/);
  for (const pseudoService of ['lumira-system', 'lumira-auth', 'lumira-file', 'lumira-message', 'lumira-plugin', 'lumira-payment', 'lumira-localization', 'lumira-team', 'lumira-ai']) {
    assert.doesNotMatch(composeProd, new RegExp(`^  ${pseudoService}:`, 'm'));
  }
});

test('production proxy and template cannot select independently deployed owner services', () => {
  const apiProxy = serviceBlock('api-proxy', 'lumira-ui');
  for (const key of ownerUpstreamKeys) {
    assert.doesNotMatch(envExample, new RegExp(`^${key}=`, 'm'));
    assert.doesNotMatch(apiProxy, new RegExp(`${key}:`));
  }
  assert.match(apiProxyTemplate, /include \/etc\/nginx\/lumira-upstreams\/active-upstreams\.conf;/);
  assert.match(apiProxyTemplate, /proxy_pass http:\/\/\$auth_upstream\$request_uri;/);
  assert.match(apiProxyTemplate, /proxy_pass http:\/\/\$payment_upstream\$request_uri;/);
});

test('job executor reaches only the async and active control-plane runtime targets', () => {
  const jobExecutor = serviceBlock('lumira-job-executor');
  assert.match(jobExecutor, /SAAS_JOB_ASYNC_RUNTIME_BASE_URL: \$\{SAAS_JOB_ASYNC_RUNTIME_BASE_URL:-http:\/\/lumira-async:8080}/);
  assert.match(jobExecutor, /SAAS_JOB_CONTROL_PLANE_BASE_URL: \$\{SAAS_JOB_CONTROL_PLANE_BASE_URL:-http:\/\/api-proxy:80}/);
  for (const key of legacyJobServiceUrlKeys) {
    assert.doesNotMatch(envExample, new RegExp(`^${key}=`, 'm'));
    assert.doesNotMatch(jobExecutor, new RegExp(`${key}:`));
  }
  assert.match(jobApplication, /backend-base-url: \$\{SAAS_JOB_ASYNC_RUNTIME_BASE_URL:/);
  assert.match(jobApplication, /system-service-base-url: \$\{SAAS_JOB_CONTROL_PLANE_BASE_URL:/);
});

test('stateless runtimes receive no database, owner-service, or owner-storage configuration', () => {
  const asyncRuntimeService = serviceBlock('lumira-async', 'lumira-job-executor');
  const jobRuntimeService = serviceBlock('lumira-job-executor');
  const forbiddenEnvironmentKeys = [
    'DB_URL',
    'DB_USERNAME',
    'DB_PASSWORD',
    'SPRING_DATASOURCE_',
    'AUTH_SERVICE_BASE_URL',
    'TEAM_SERVICE_BASE_URL',
    'UPLOAD_STORAGE_ROOT',
    'PLUGIN_STORAGE_ROOT',
    'PLUGIN_STAGING_ROOT',
  ];

  assert.doesNotMatch(composeProd, /x-common-env:/);
  for (const key of forbiddenEnvironmentKeys) {
    assert.doesNotMatch(asyncRuntimeService, new RegExp(`${key}:`));
    assert.doesNotMatch(jobRuntimeService, new RegExp(`${key}:`));
  }
  assert.doesNotMatch(asyncRuntimeService, /\n    volumes:/);
  assert.doesNotMatch(asyncApplication, /spring\.datasource|mybatis-plus|springdoc:|spring:\n[\s\S]*?flyway:|saas:\n[\s\S]*?security:/);
  assert.doesNotMatch(jobApplication, /datasource:|data:\n\s+redis:|saas:\n\s+security:/);
});

test('three production runtimes publish distinct identity, health, readiness, version, and scheduling roles', () => {
  const server = serviceBlock('lumira-server-blue', 'lumira-server-green');
  const async = serviceBlock('lumira-async', 'lumira-job-executor');
  const job = serviceBlock('lumira-job-executor');

  for (const [service, expectedName] of [
    [server, 'lumira-server'],
    [async, 'lumira-async'],
    [job, 'lumira-job-executor'],
  ]) {
    assert.match(service, new RegExp(`OTEL_SERVICE_NAME: ${expectedName}`));
    assert.match(service, /SERVER_PORT: 8080/);
  }
  assert.match(async, /SPRING_APPLICATION_NAME: lumira-async/);
  assert.match(job, /SPRING_APPLICATION_NAME: lumira-job-executor/);
  assert.match(serverApplication, /name: lumira-server/);
  assert.match(asyncApplication, /name: \$\{SPRING_APPLICATION_NAME:lumira-async}/);
  assert.match(jobApplication, /name: \$\{SPRING_APPLICATION_NAME:lumira-job-executor}/);

  assert.match(serverVersion, /@GetMapping\("\/api\/v1\/system\/version"\)/);
  assert.match(asyncReadiness, /@RequestMapping\("\/api\/v2\/async"\)/);
  assert.match(asyncReadiness, /@GetMapping\("\/readiness"\)/);
  assert.match(asyncReadiness, /@GetMapping\("\/health"\)/);
  assert.match(asyncVersion, /@GetMapping\("\/api\/v1\/async\/version"\)/);
  assert.match(jobReadiness, /@RequestMapping\("\/api\/v2\/job"\)/);
  assert.match(jobVersion, /@GetMapping\("\/api\/v1\/job\/version"\)/);

  assert.match(server, /LUMIRA_RUNTIME_ASYNC_ENABLED: \$\{LUMIRA_SERVER_RUNTIME_ASYNC_ENABLED:-false}/);
  assert.match(asyncRuntime, /@EnableScheduling/);
  assert.match(asyncRelay, /@Scheduled\(/);
  assert.match(jobHandler, /@XxlJob/);
});
