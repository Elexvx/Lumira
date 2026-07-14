import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const deployScript = readFileSync(path.join(repoRoot, 'bin', 'deploy-container.mjs'), 'utf8');
const installScript = readFileSync(path.join(repoRoot, 'bin', 'install-platform.mjs'), 'utf8');
const startScript = readFileSync(path.join(repoRoot, 'bin', 'start-platform.mjs'), 'utf8');
const envExample = readFileSync(path.join(repoRoot, 'deploy', '.env.example'), 'utf8');
const composeProd = readFileSync(path.join(repoRoot, 'deploy', 'docker-compose.prod.yml'), 'utf8');
const apiNginx = readFileSync(path.join(repoRoot, 'deploy', 'nginx', 'api.conf.template'), 'utf8');
const edgeNginx = readFileSync(path.join(repoRoot, 'deploy', 'nginx', 'edge.conf'), 'utf8');
const updaterInstaller = readFileSync(path.join(repoRoot, 'bin', 'install-lumira-updater.mjs'), 'utf8');
const updater = readFileSync(path.join(repoRoot, 'bin', 'lumira-updater.mjs'), 'utf8');
const releaseManifestGenerator = readFileSync(path.join(repoRoot, 'bin', 'generate-release-manifest.mjs'), 'utf8');
const ciWorkflow = readFileSync(path.join(repoRoot, '.github', 'workflows', 'ci.yml'), 'utf8');

test('deploy-container generates every scoped internal token used by production compose', () => {
  for (const key of [
    'SAAS_INTERNAL_SYSTEM_TOKEN',
    'SAAS_INTERNAL_AUTH_TOKEN',
    'SAAS_INTERNAL_AUTH_SYSTEM_TOKEN',
    'SAAS_INTERNAL_FILE_TOKEN',
    'SAAS_INTERNAL_MESSAGE_TOKEN',
    'SAAS_INTERNAL_PAYMENT_TOKEN',
    'SAAS_INTERNAL_PLUGIN_TOKEN',
    'SAAS_INTERNAL_TEAM_TOKEN',
    'SAAS_INTERNAL_JOB_TOKEN',
  ]) {
    assert.match(deployScript, new RegExp(`\\b${key}\\b`), `${key} must be covered by generatedEnvDefaults`);
  }
});

test('production compose does not inject unused system or team tokens into job executor', () => {
  const jobExecutorBlock = composeProd.match(/lumira-job-executor:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(jobExecutorBlock, 'job executor compose block must exist');
  assert.doesNotMatch(jobExecutorBlock[0], /SAAS_INTERNAL_SYSTEM_TOKEN:/, 'job executor must not receive SAAS_INTERNAL_SYSTEM_TOKEN');
  assert.doesNotMatch(jobExecutorBlock[0], /SAAS_INTERNAL_TEAM_TOKEN:/, 'job executor must not receive SAAS_INTERNAL_TEAM_TOKEN');
  for (const key of [
    'SAAS_INTERNAL_FILE_TOKEN',
    'SAAS_INTERNAL_MESSAGE_TOKEN',
    'SAAS_INTERNAL_PAYMENT_TOKEN',
    'SAAS_INTERNAL_PLUGIN_TOKEN',
    'SAAS_INTERNAL_JOB_TOKEN',
  ]) {
    assert.match(jobExecutorBlock[0], new RegExp(`${key}:`), `${key} must remain configured for job executor`);
  }
});

test('production compose does not inject unused owner service base URLs into job executor', () => {
  const jobExecutorBlock = composeProd.match(/lumira-job-executor:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(jobExecutorBlock, 'job executor compose block must exist');
  assert.doesNotMatch(jobExecutorBlock[0], /^\s+AUTH_SERVICE_BASE_URL:/m, 'job executor must not receive AUTH_SERVICE_BASE_URL');
  assert.doesNotMatch(jobExecutorBlock[0], /^\s+SYSTEM_SERVICE_BASE_URL:/m, 'job executor must not receive SYSTEM_SERVICE_BASE_URL');
  assert.doesNotMatch(jobExecutorBlock[0], /^\s+PAYMENT_SERVICE_BASE_URL:/m, 'job executor must not receive PAYMENT_SERVICE_BASE_URL');
  assert.doesNotMatch(jobExecutorBlock[0], /^\s+TEAM_SERVICE_BASE_URL:/m, 'job executor must not receive TEAM_SERVICE_BASE_URL');
});

test('production compose does not inject unused system or team tokens into async runtime', () => {
  const asyncBlock = composeProd.match(/lumira-async:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(asyncBlock, 'async runtime compose block must exist');
  assert.doesNotMatch(asyncBlock[0], /SAAS_INTERNAL_SYSTEM_TOKEN:/, 'async runtime must not receive SAAS_INTERNAL_SYSTEM_TOKEN');
  assert.doesNotMatch(asyncBlock[0], /SAAS_INTERNAL_TEAM_TOKEN:/, 'async runtime must not receive SAAS_INTERNAL_TEAM_TOKEN');
  for (const key of [
    'SAAS_INTERNAL_FILE_TOKEN',
    'SAAS_INTERNAL_MESSAGE_TOKEN',
    'SAAS_INTERNAL_PAYMENT_TOKEN',
    'SAAS_INTERNAL_PLUGIN_TOKEN',
    'SAAS_INTERNAL_JOB_TOKEN',
  ]) {
    assert.match(asyncBlock[0], new RegExp(`${key}:`), `${key} must remain configured for async runtime`);
  }
});

test('production compose does not inject unused owner service base URLs into async runtime', () => {
  const asyncBlock = composeProd.match(/lumira-async:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(asyncBlock, 'async runtime compose block must exist');
  assert.doesNotMatch(asyncBlock[0], /^\s+SYSTEM_SERVICE_BASE_URL:/m, 'async runtime must not receive SYSTEM_SERVICE_BASE_URL');
  assert.doesNotMatch(asyncBlock[0], /^\s+PAYMENT_SERVICE_BASE_URL:/m, 'async runtime must not receive PAYMENT_SERVICE_BASE_URL');
  assert.match(asyncBlock[0], /^\s+AUTH_SERVICE_BASE_URL:/m, 'async runtime must keep AUTH_SERVICE_BASE_URL for remote auth lookups');
  assert.match(asyncBlock[0], /^\s+TEAM_SERVICE_BASE_URL:/m, 'async runtime must keep TEAM_SERVICE_BASE_URL for remote team lookups');
});

test('install-platform keeps generated secrets and required env checks aligned with scoped team token', () => {
  const occurrences = [...installScript.matchAll(/\bSAAS_INTERNAL_TEAM_TOKEN\b/g)].length;
  assert.ok(occurrences >= 2, 'install-platform must both generate and require SAAS_INTERNAL_TEAM_TOKEN');
});

test('install-platform starts async and job runtime services and does not promise an unsupported bundled Nacos path', () => {
  assert.match(
    installScript,
    /composeUp\(options, 'owner async runtime', \['lumira-async'\]\);/,
    'install-platform must start the async runtime as part of the default platform topology'
  );
  assert.match(
    installScript,
    /composeUp\(options, 'job executor', \['lumira-job-executor'\]\);/,
    'install-platform must start the job executor as part of the default platform topology'
  );
  assert.match(
    installScript,
    /assertNoUnsupportedNacosRequest/,
    'install-platform must fail fast when callers request the unsupported bundled Nacos path'
  );
  assert.doesNotMatch(
    installScript,
    /options\.useNacos \? \['nacos'\]/,
    'install-platform must not try to start a nonexistent nacos compose service'
  );
  assert.match(
    installScript,
    /waitForComposeServicesRunning\(\s*options,\s*\[\s*'redis',\s*'xxl-job-admin',\s*'lumira-server-blue',\s*'lumira-async',\s*'lumira-job-executor',/m,
    'install-platform must verify that the core runtime services are actually running after startup'
  );
  assert.match(
    installScript,
    /\.\.\.\(!options\.useLocalMysql \? \['--profile', 'edge'\] : \[\]\)/,
    'install-platform must enable the edge profile outside local-mysql mode'
  );
  assert.match(
    installScript,
    /composeUp\(options, 'edge proxy', !options\.useLocalMysql \? \['edge-proxy'\] : \[\]\);/,
    'install-platform must start the edge proxy when it verifies the public API domain'
  );
  assert.match(
    installScript,
    /const baseUrl = process\.env\.DEPLOY_CHECK_BASE_URL \|\| \(options\.useLocalMysql \? 'http:\/\/127\.0\.0\.1:8000' : `https:\/\/\$\{options\.apiDomain\}`\);/,
    'install-platform must use a localhost verification base URL in local-mysql mode'
  );
  assert.match(
    installScript,
    /assertEdgeTlsFiles\(options\);/,
    'install-platform must verify edge TLS assets before starting non-local edge deployments'
  );
});

test('start-platform rejects legacy skip flags instead of silently ignoring them', () => {
  assert.match(
    startScript,
    /const unsupportedLegacyArgs = \['--skip-infra', '--skip-services', '--skip-lumira-ui'\];/,
    'start-platform must explicitly recognize obsolete skip-* flags'
  );
  assert.match(
    startScript,
    /Unsupported legacy option\(s\)/,
    'start-platform must fail loudly when callers pass obsolete skip-* flags'
  );
  assert.match(
    startScript,
    /Use --no-build to skip the rebuild step/,
    'start-platform must point callers at the supported replacement behavior'
  );
  assert.match(
    startScript,
    /It defaults to --local-mysql/,
    'start-platform help must explain the localhost-oriented default topology'
  );
  assert.match(
    startScript,
    /translatedArgs\.unshift\('--local-mysql'\)/,
    'start-platform must default local startup to the local-mysql deployment path'
  );
});

test('deploy-container allows selected deploys for every compose runtime service', () => {
  for (const serviceName of [
    'lumira-server-blue',
    'lumira-server-green',
    'lumira-async',
    'lumira-job-executor',
    'api-proxy',
    'edge-proxy',
    'lumira-ui',
    'mysql',
    'redis',
    'xxl-job-admin',
    'prometheus',
    'loki',
    'tempo',
    'alloy',
    'grafana',
  ]) {
    assert.match(deployScript, new RegExp(`'${serviceName}'`), `${serviceName} must be selectable with --services`);
  }
});

test('deploy-container shares deployment verification with the standalone checker and rejects unsupported bundled Nacos toggles', () => {
  assert.match(
    deployScript,
    /run\('node', \['bin\/check-deployment\.mjs'\]/,
    'deploy-container must delegate deployment verification to bin/check-deployment.mjs'
  );
  assert.match(
    deployScript,
    /assertNoUnsupportedNacosSelection/,
    'deploy-container must fail fast when bundled Nacos is requested in the unsupported topology'
  );
  assert.doesNotMatch(
    deployScript,
    /Start the bundled Nacos container/,
    'deploy-container help must not claim a bundled Nacos container exists'
  );
  assert.match(
    deployScript,
    /waitForComposeServicesRunning\(runtimeServices, 'default deployment services'\)/,
    'deploy-container must verify the default deployment runtime services reach running state'
  );
  assert.match(
    deployScript,
    /composeArgs\('ps', '--services', '--status', 'running'/,
    'deploy-container must use docker compose ps status filtering for runtime checks'
  );
  assert.match(
    deployScript,
    /ensureEdgeTlsFiles\(\)/,
    'deploy-container must verify edge TLS assets before starting the edge profile'
  );
  assert.match(
    deployScript,
    /Place fullchain\.pem and privkey\.pem under deploy\/data\/tls, or use --local-mysql/,
    'deploy-container must give operators a concrete recovery path when edge TLS assets are missing'
  );
});

test('deployment env example exposes async owner base URL overrides', () => {
  for (const key of [
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
    'SAAS_JOB_MESSAGE_SERVICE_BASE_URL',
    'SAAS_JOB_FILE_SERVICE_BASE_URL',
    'SAAS_JOB_PAYMENT_SERVICE_BASE_URL',
    'SAAS_JOB_PLUGIN_SERVICE_BASE_URL',
  ]) {
    assert.match(envExample, new RegExp(`^${key}=`, 'm'), `${key} must be documented in deploy/.env.example`);
  }
});

test('platform updater is reachable from the backend container and installed as a host service', () => {
  for (const key of [
    'PLATFORM_UPDATE_SOURCE_URL',
    'PLATFORM_UPDATE_MANIFEST_URL',
    'PLATFORM_UPDATE_AGENT_URL',
    'PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS',
    'PLATFORM_UPDATE_AGENT_TOKEN',
  ]) {
    assert.match(composeProd, new RegExp(`\\b${key}:`), `lumira-server must receive ${key}`);
  }
  assert.match(composeProd, /host\.docker\.internal:host-gateway/, 'compose must map the Docker host gateway');
  assert.match(envExample, /PLATFORM_UPDATE_AGENT_URL=http:\/\/host\.docker\.internal:9788/);
  assert.match(envExample, /PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS=host\.docker\.internal/);
  assert.match(installScript, /install-lumira-updater\.mjs/, 'platform installer must install the updater service');
  assert.match(updaterInstaller, /systemctl[\s\S]*enable[\s\S]*lumira-updater\.service/);
  assert.match(updaterInstaller, /systemctl[\s\S]*restart[\s\S]*lumira-updater\.service/);
  assert.doesNotMatch(updaterInstaller, /WorkingDirectory=\$\{quoteSystemd\(/);
  assert.doesNotMatch(updaterInstaller, /EnvironmentFile=\$\{quoteSystemd\(/);
  assert.match(updaterInstaller, /docker[\s\S]*network[\s\S]*inspect[\s\S]*bridge/);
  assert.match(updaterInstaller, /build-identity\.env/, 'updater installer must detect production build identity');
  assert.match(updaterInstaller, /parseEnvFile\(buildIdentityPath\)/, 'updater installer must initialize slots from the deployed build identity');
  assert.match(updaterInstaller, /composeEnvArgs\.push\('--env-file', buildIdentityPath\)/, 'legacy migration must pass the deployed build identity to Compose');
  assert.match(updaterInstaller, /inspectContainerImage\('lumira-async'\)/, 'bootstrap must persist the running async image for rollback');
  assert.match(updaterInstaller, /inspectContainerImage\('lumira-job-executor'\)/, 'bootstrap must persist the running job executor image for rollback');
  assert.match(updaterInstaller, /repairDeploymentWorkerState\(deploymentState, workerImages\)/, 'rerunning bootstrap must repair incomplete worker state');
  assert.match(updaterInstaller, /containerNetworks\('lumira-api-proxy'\)/, 'legacy migration must select a network shared with the API proxy');
  assert.doesNotMatch(updaterInstaller, /range \.NetworkSettings\.Networks/, 'legacy migration must not concatenate addresses from multiple Docker networks');
  assert.match(updater, /targetNetworks[\s\S]*proxyNetworks[\s\S]*sharedNetwork/, 'online updates must probe slots through a network shared with the API proxy');
  assert.doesNotMatch(updater, /range \.NetworkSettings\.Networks/, 'online updates must not concatenate addresses from multiple Docker networks');
  assert.match(updater, /\['\/actuator\/health\/readiness', '\/actuator\/health'\]/, 'slot health checks must support secured readiness groups with a general-health fallback');
  assert.match(updaterInstaller, /if \(!blueHealthy\) \{[\s\S]*stopBlueSlot\(\)/, 'failed pre-switch health checks must clean up the inactive slot');
  assert.match(updaterInstaller, /ls', '-1', '\/etc\/nginx\/conf\.d'/, 'legacy migration must discover the generated API proxy config');
  assert.match(updaterInstaller, /candidate\.includes\('set \$gateway_upstream'\)/, 'legacy migration must select the config that owns API upstreams');
  assert.doesNotMatch(updaterInstaller, /cat', '\/etc\/nginx\/conf\.d\/default\.conf'/, 'legacy migration must not assume the image default config handles API traffic');
  assert.match(updaterInstaller, /lumira-legacy-api\.conf[\s\S]*nginx[\s\S]*reload[\s\S]*stopBlueSlot\(\)/, 'failed proxy cutover must reload the legacy config before cleaning up blue');
});

test('continuous release manifest uses digest-pinned images and a stable GitHub release', () => {
  assert.match(releaseManifestGenerator, /assertDigestPinned\(serverImage/);
  assert.match(releaseManifestGenerator, /assertDigestPinned\(frontendImage/);
  assert.match(ciWorkflow, /steps\.build_server\.outputs\.digest/);
  assert.match(ciWorkflow, /steps\.build_ui\.outputs\.digest/);
  assert.match(ciWorkflow, /gh release (?:view|create) continuous/);
  assert.match(ciWorkflow, /gh release upload continuous/);
  assert.match(envExample, /PLATFORM_UPDATE_MANIFEST_URL=https:\/\/api\.github\.com\/repos\/Elexvx\/Lumira\/releases\/tags\/continuous/);
});

test('frontend preview container stays opt-in for production compose', () => {
  assert.match(
    composeProd,
    /lumira-ui:\r?\n\s+profiles:\r?\n\s+- local-lumira-ui/,
    'lumira-ui must be guarded by the local-lumira-ui profile'
  );
  assert.doesNotMatch(
    composeProd,
    /edge-proxy:[\s\S]*depends_on:\r?\n(?:\s+- .*\r?\n)*\s+- lumira-ui/,
    'edge-proxy must not require lumira-ui when frontend preview is disabled'
  );
  assert.match(
    deployScript,
    /local-lumira-ui/,
    'deploy-container must enable the local-lumira-ui profile when explicitly deploying lumira-ui'
  );
});

test('edge proxy root path does not hard-depend on frontend preview container', () => {
  assert.doesNotMatch(edgeNginx, /proxy_pass http:\/\/lumira-ui/, 'edge proxy must not proxy root traffic to lumira-ui by default');
  assert.match(edgeNginx, /location \/ \{\r?\n\s+return 410;/, 'edge proxy root path must explicitly reject non-API traffic by default');
});

test('api proxy does not override backend CORS policy with a hard-coded origin allowlist', () => {
  assert.doesNotMatch(apiNginx, /Access-Control-Allow-Origin/, 'api proxy must forward backend CORS headers instead of injecting its own ACAO');
  assert.doesNotMatch(apiNginx, /elexvx\.com|bm\.aiadc\.org\.cn|vercel\.app/, 'api proxy must not hard-code deployment-specific origin allowlists');
});

test('api proxy template keeps split-owner routes explicit while defaulting compose upstreams to the monolith entrypoint', () => {
  assert.match(
    composeProd,
    /api-proxy:[\s\S]*AUTH_SERVICE_UPSTREAM: \$\{AUTH_SERVICE_UPSTREAM:-lumira-server:8080\}/,
    'api-proxy must expose env-driven auth upstream overrides'
  );
  assert.match(
    composeProd,
    /api-proxy:[\s\S]*AI_SERVICE_UPSTREAM: \$\{AI_SERVICE_UPSTREAM:-lumira-server:8080\}/,
    'api-proxy must expose env-driven AI upstream overrides'
  );
  assert.match(
    composeProd,
    /api-proxy:[\s\S]*\.\/nginx\/api\.conf\.template:\/etc\/nginx\/templates\/api\.conf\.template:ro/,
    'api-proxy must render its config from the envsubst template'
  );

  for (const [routePattern, upstreamVar] of [
    ['^/api/v(1|2)/auth(/|$)', '$auth_upstream'],
    ['^/api/v(1|2)/files(/|$)', '$file_upstream'],
    ['^/api/v(1|2)/message(/|$)', '$message_upstream'],
    ['^/api/v(1|2)/plugins(/|$)', '$plugin_upstream'],
    ['^/api/v(1|2)/payment(/|$)', '$payment_upstream'],
    ['^/api/v(1|2)/localization(/|$)', '$localization_upstream'],
    ['^/api/v2/ai(/|$)', '$ai_upstream'],
    ['^/api/v2/(teams|team-invites)(/|$)', '$team_upstream'],
    ['^/api/v2/admin/teams(/|$)', '$team_upstream'],
  ]) {
    assert.match(
      apiNginx,
      new RegExp(`location \\~ ${routePattern.replace(/[|()$^/]/g, '\\$&')}[\\s\\S]*proxy_pass http:\\/\\/${upstreamVar.replace('$', '\\$')}\\$request_uri;`),
      `api proxy must pin ${routePattern} to ${upstreamVar}`
    );
  }

  assert.match(apiNginx, /location \^~ \/api\/p\/[\s\S]*proxy_pass http:\/\/\$plugin_upstream\$request_uri;/, 'plugin asset gateway must route to the plugin owner');
  assert.match(apiNginx, /location \^~ \/ws\/message[\s\S]*proxy_pass http:\/\/\$message_upstream\$request_uri;/, 'message websocket traffic must route to the message owner');
});

test('online migrator joins the configurable database network used by production', () => {
  assert.match(envExample, /^DB_MIGRATION_NETWORK=deploy_default$/m);
  assert.match(updater, /env\.DB_MIGRATION_NETWORK \|\| env\.DB_BACKUP_NETWORK \|\| 'deploy_default'/);
  assert.match(updater, /'--force-recreate', `lumira-server-\$\{targetSlot\}`/);
  assert.match(updater, /composeArgs\(\.\.\.args\), \{ env: parseEnvFile\(envPath\) \}/);
  assert.match(updater, /The migration network cannot resolve the configured database host\./);
  assert.match(
    composeProd,
    /mysql:[\s\S]*?networks:\s*\n\s*- default\s*\n\s*- 1panel-network/,
    'bundled MySQL must be reachable through the same database network as production migrations'
  );
});

test('blue and green slots persist independent build identities for safe container recreation', () => {
  for (const slot of ['BLUE', 'GREEN']) {
    for (const field of ['APP_VERSION', 'BUILD_VERSION', 'BUILD_TIME', 'GIT_COMMIT', 'DATABASE_VERSION']) {
      assert.match(envExample, new RegExp(`^LUMIRA_SERVER_${slot}_${field}=`, 'm'));
      assert.match(composeProd, new RegExp(`LUMIRA_SERVER_${slot}_${field}`));
    }
  }
  assert.match(updater, /LUMIRA_SERVER_\$\{targetSlot\.toUpperCase\(\)\}_GIT_COMMIT/);
});
