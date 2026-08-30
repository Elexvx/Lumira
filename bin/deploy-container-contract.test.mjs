import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const deployScript = readFileSync(path.join(repoRoot, 'bin', 'deploy-container.mjs'), 'utf8');
const installScript = readFileSync(path.join(repoRoot, 'bin', 'install-platform.mjs'), 'utf8');
const startScript = readFileSync(path.join(repoRoot, 'bin', 'start-platform.mjs'), 'utf8');
const localStartScript = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');
const productionStartScript = readFileSync(path.join(repoRoot, 'bin', 'start-production.mjs'), 'utf8');
const rootPackage = JSON.parse(readFileSync(path.join(repoRoot, 'package.json'), 'utf8'));
const dockerignore = readFileSync(path.join(repoRoot, '.dockerignore'), 'utf8');
const envExample = readFileSync(path.join(repoRoot, 'deploy', '.env.example'), 'utf8');
const composeProd = readFileSync(path.join(repoRoot, 'deploy', 'docker-compose.prod.yml'), 'utf8');
const serviceDockerfile = readFileSync(path.join(repoRoot, 'deploy', 'docker', 'service.Dockerfile'), 'utf8');
const apiNginx = readFileSync(path.join(repoRoot, 'deploy', 'nginx', 'api.conf.template'), 'utf8');
const edgeNginx = readFileSync(path.join(repoRoot, 'deploy', 'nginx', 'edge.conf'), 'utf8');
const uiNginx = readFileSync(path.join(repoRoot, 'deploy', 'nginx', 'lumira-ui.conf'), 'utf8');
const updaterInstaller = readFileSync(path.join(repoRoot, 'bin', 'install-lumira-updater.mjs'), 'utf8');
const updater = readFileSync(path.join(repoRoot, 'bin', 'lumira-updater.mjs'), 'utf8');
const releaseManifestGenerator = readFileSync(path.join(repoRoot, 'bin', 'generate-release-manifest.mjs'), 'utf8');
const ciWorkflow = readFileSync(path.join(repoRoot, '.github', 'workflows', 'ci.yml'), 'utf8');
const gitignore = readFileSync(path.join(repoRoot, '.gitignore'), 'utf8');
const frontendAssetAdapter = readFileSync(path.join(repoRoot, 'lumira-ui', 'scripts', 'adapt-cdn-assets.mjs'), 'utf8');
const swaggerBootstrap = readFileSync(path.join(repoRoot, 'lumira-ui', 'public', 'swagger-ui-bootstrap.js'), 'utf8');
const monitoringPage = readFileSync(path.join(repoRoot, 'lumira-ui', 'src', 'pages', 'settings', 'monitoring', 'MonitoringPage.tsx'), 'utf8');
const apiDocsShell = readFileSync(path.join(repoRoot, 'lumira-ui', 'src', 'pages', 'settings', 'monitoring', 'apiDocsShell.ts'), 'utf8');

test('generated updater state stays local to the deployment host', () => {
  for (const entry of [
    'deploy/.update-state.json',
    'deploy/.update.lock',
    'deploy/.update-tasks/',
    'deploy/.update-preflights/',
  ]) {
    assert.match(gitignore, new RegExp(`^${entry.replaceAll('.', '\\.')}\\r?$`, 'm'));
  }
});

test('frontend builds always receive a traceable release identity', () => {
  const buildStep = ciWorkflow.slice(
    ciWorkflow.indexOf('- name: Build lumira-ui image'),
    ciWorkflow.indexOf('- name: Build lumira-async image'),
  );
  for (const buildArg of ['FRONTEND_VERSION', 'BUILD_TIME', 'GIT_COMMIT', 'GIT_BRANCH']) {
    assert.match(buildStep, new RegExp(`^\\s+${buildArg}=`, 'm'), `lumira-ui image must receive ${buildArg}`);
  }
  assert.match(frontendAssetAdapter, /readGitValue\('rev-parse', 'HEAD'\)/);
  assert.match(frontendAssetAdapter, /readGitValue\('rev-parse', '--abbrev-ref', 'HEAD'\)/);
  assert.match(updater, /const targetFrontendVersion = `\$\{manifest\.version\}\+\$\{manifest\.commit\.slice\(0, 12\)\}`/);
  assert.match(updater, /FRONTEND_VERSION: targetFrontendVersion/);
  assert.match(updater, /GIT_BRANCH: 'main'/);
});

test('service images carry immutable release identity independent of stale host environment', () => {
  const requiredIdentityFields = [
    'APP_VERSION',
    'BUILD_VERSION',
    'FRONTEND_VERSION',
    'BACKEND_VERSION',
    'DATABASE_VERSION',
    'BUILD_TIME',
    'GIT_COMMIT',
    'GIT_BRANCH',
  ];
  for (const buildName of ['lumira-server', 'lumira-async', 'lumira-job-executor']) {
    const start = ciWorkflow.indexOf(`- name: Build ${buildName} image`);
    const end = ciWorkflow.indexOf('- name: Build ', start + 1);
    const buildStep = ciWorkflow.slice(start, end < 0 ? ciWorkflow.length : end);
    for (const field of requiredIdentityFields) {
      assert.match(buildStep, new RegExp(`^\\s+${field}=`, 'm'), `${buildName} image must receive ${field}`);
      assert.match(serviceDockerfile, new RegExp(`LUMIRA_IMAGE_${field}=\\$\\{${field}\\}`));
    }
  }
  assert.match(updater, /BACKEND_VERSION: targetBackendVersion/);
});

test('service images do not advertise an unavailable Nacos or JM logging integration', () => {
  assert.doesNotMatch(serviceDockerfile, /NACOS|nacos|JM\.LOG\.PATH|\/tmp\/nacos/);
  assert.match(serviceDockerfile, /csp\.sentinel\.log\.dir=\/tmp\/sentinel/);
});

test('main release workflow has a guarded manual dispatch fallback', () => {
  assert.match(ciWorkflow, /\n  workflow_dispatch:\n/);
  assert.match(ciWorkflow, /release_notes:/);
  assert.match(ciWorkflow, /github\.event_name == 'workflow_dispatch'/);
  assert.match(
    ciWorkflow,
    /push: \$\{\{ \(github\.event_name == 'push' \|\| github\.event_name == 'workflow_dispatch'\) && github\.ref == 'refs\/heads\/main' \}\}/,
  );
  assert.match(
    ciWorkflow,
    /LUMIRA_RELEASE_NOTES: \$\{\{ github\.event\.head_commit\.message \|\| inputs\.release_notes/,
  );
  assert.doesNotMatch(ciWorkflow, /github\.event_name == 'pull_request'[^\n]*(?:push|provenance|sbom)/);
});

test('deployment configuration does not expose an unavailable Nacos capability', () => {
  for (const [name, content] of [
    ['production compose', composeProd],
    ['environment example', envExample],
    ['service image', serviceDockerfile],
  ]) {
    assert.doesNotMatch(content, /NACOS|nacos|JM\.LOG\.PATH|\/tmp\/nacos/, `${name} must not advertise Nacos`);
  }
});

test('CI builds each service image from its matching Docker target', () => {
  const expectedTargets = new Map([
    ['lumira-server', 'lumira-server-image'],
    ['lumira-async', 'lumira-async-image'],
    ['lumira-job-executor', 'lumira-job-executor-image'],
  ]);
  for (const [buildName, target] of expectedTargets) {
    const start = ciWorkflow.indexOf(`- name: Build ${buildName} image`);
    const end = ciWorkflow.indexOf('- name: Build ', start + 1);
    const buildStep = ciWorkflow.slice(start, end < 0 ? ciWorkflow.length : end);
    assert.match(buildStep, new RegExp(`^\\s+target: ${target}\\r?$`, 'm'));
  }
});

test('frontend and edge nginx enforce browser security headers', () => {
  for (const [name, config] of [['frontend', uiNginx], ['edge', edgeNginx]]) {
    assert.match(config, /Content-Security-Policy/ , `${name} nginx must emit a CSP`);
    assert.match(config, /X-Content-Type-Options\s+"nosniff"/, `${name} nginx must prevent MIME sniffing`);
    assert.match(config, /X-Frame-Options\s+"DENY"/, `${name} nginx must prevent framing`);
    assert.match(config, /Referrer-Policy\s+"strict-origin-when-cross-origin"/, `${name} nginx must set a referrer policy`);
    assert.match(config, /Permissions-Policy/, `${name} nginx must restrict browser capabilities`);
    assert.match(config, /add_header_inherit merge/, `${name} nginx must retain headers in locations with cache headers`);
    assert.match(
      config,
      /script-src 'self' https:\/\/res\.wx\.qq\.com https:\/\/cdn\.jsdelivr\.net;/,
      `${name} nginx must allow only the approved executable origins`,
    );
    assert.match(
      config,
      /style-src 'self' 'unsafe-inline' https:\/\/cdn\.jsdelivr\.net;/,
      `${name} nginx must allow the pinned Swagger UI stylesheet origin`,
    );
    assert.match(
      config,
      /frame-src 'self' https:\/\/open\.weixin\.qq\.com;/,
      `${name} nginx must allow the official WeChat QR login iframe`,
    );
    assert.doesNotMatch(
      config,
      /(?:script-src|frame-src)[^;]*https:\/\/\*/,
      `${name} nginx must not use wildcard HTTPS origins for executable or framed content`,
    );
  }
});

test('embedded API docs initialize through a same-origin CSP-compatible bootstrap', () => {
  assert.match(swaggerBootstrap, /event\.source !== window\.parent/);
  assert.match(swaggerBootstrap, /event\.data\.type !== 'lumira:swagger-spec'/);
  assert.match(swaggerBootstrap, /supportedSubmitMethods: \[\]/);
  assert.doesNotMatch(swaggerBootstrap, /\beval\s*\(|new Function\s*\(/);
  assert.match(apiDocsShell, /bootstrap:\s*`\$\{SWAGGER_UI_VENDOR_ROOT\}\/lumira-bootstrap\.js\?v=[a-z0-9.-]+`/i);
  assert.match(monitoringPage, /SWAGGER_UI_SHELL_HTML/);
  assert.match(monitoringPage, /srcDoc=\{SWAGGER_UI_SHELL_HTML\}/);
  assert.match(monitoringPage, /postMessage\([\s\S]*window\.location\.origin/);
  assert.doesNotMatch(monitoringPage, /postMessage\([\s\S]*['"]\*['"]/);
});

test('frontend and edge nginx enforce browser security headers', () => {
  for (const [name, config] of [['frontend', uiNginx], ['edge', edgeNginx]]) {
    assert.match(config, /Content-Security-Policy/ , `${name} nginx must emit a CSP`);
    assert.match(config, /X-Content-Type-Options\s+"nosniff"/, `${name} nginx must prevent MIME sniffing`);
    assert.match(config, /X-Frame-Options\s+"DENY"/, `${name} nginx must prevent framing`);
    assert.match(config, /Referrer-Policy\s+"strict-origin-when-cross-origin"/, `${name} nginx must set a referrer policy`);
    assert.match(config, /Permissions-Policy/, `${name} nginx must restrict browser capabilities`);
    assert.match(config, /add_header_inherit merge/, `${name} nginx must retain headers in locations with cache headers`);
  }
});

test('production disables the Hikari periodic JDBC keepalive explicitly', () => {
  assert.match(
    composeProd,
    /SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME: \$\{SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME:-0\}/,
    'production compose must not inherit HikariCP 6.2+ periodic keepalive defaults'
  );
  assert.match(
    envExample,
    /^SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME=0$/m,
    'the production env template must document the keepalive safety override'
  );
});

test('api proxy retires old keep-alive clients before the blue-green drain deadline', () => {
  assert.match(apiNginx, /^\s*keepalive_timeout 15s;$/m);
  assert.match(apiNginx, /^\s*keepalive_time 30s;$/m);
  assert.match(apiNginx, /^\s*keepalive_requests 100;$/m);
});

test('production disables the Hikari periodic JDBC keepalive explicitly', () => {
  assert.match(
    composeProd,
    /SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME: \$\{SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME:-0\}/,
    'production compose must not inherit HikariCP 6.2+ periodic keepalive defaults'
  );
  assert.match(
    envExample,
    /^SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME=0$/m,
    'the production env template must document the keepalive safety override'
  );
});

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

test('deploy-container generates and migrates the production Redis credential', () => {
  assert.match(deployScript, /REDIS_PASSWORD:\s*randomSecret\('redis'\)/);
  assert.match(
    deployScript,
    /\['REDIS_PASSWORD', 'change-me-at-least-24-characters-redis-password'\]/,
  );
});

test('Windows WSL Docker deployments forward migration credentials without putting them in argv', () => {
  assert.match(
    deployScript,
    /WSLENV:\s*wslForwardedEnvironment\(\['DB_URL', 'DB_USERNAME', 'DB_PASSWORD'\]\)/,
    'database credentials must cross the docker.cmd-to-WSL boundary through WSLENV'
  );
  assert.doesNotMatch(
    deployScript,
    /'-e',\s*`DB_(?:URL|USERNAME|PASSWORD)=/,
    'database credentials must not be embedded in docker process arguments'
  );
});

test('Windows WSL backups receive the effective database target and translate path variables', () => {
  for (const variableName of [
    'DB_URL', 'DB_HOST', 'DB_PORT', 'MYSQL_DATABASE', 'DB_USERNAME', 'DB_PASSWORD',
    'MYSQL_SSL_MODE', 'MYSQL_SSL_CA_FILE', 'MYSQL_BACKUP_USERNAME', 'MYSQL_BACKUP_PASSWORD',
  ]) {
    assert.match(deployScript, new RegExp(`'${variableName}'`), `${variableName} must cross the WSL backup boundary`);
  }
  assert.match(deployScript, /\.\.\.effectiveBackupEnvironment,\s*BACKUP_ALLOW_EMPTY_DATABASE:/s);
  assert.match(deployScript, /const backupEndpoint = databaseEndpointFromEnvironment\(environment\)/);
  assert.match(deployScript, /MYSQL_DATABASE: backupDatabaseName, DB_NAME: backupDatabaseName/);
  assert.match(
    deployScript,
    /\['BACKUP_ROOT', 'BACKUP_UPLOAD_HOOK', 'BACKUP_METRICS_FILE', 'MYSQL_SSL_CA_FILE'\]/,
    'Windows backup paths must use WSL path translation',
  );
});

test('database migrations require verified backup evidence for the configured database', () => {
  assert.match(deployScript, /createVerifiedDatabaseBackup\(\{/);
  assert.match(deployScript, /validateBackupEvidence\(hostReadableBackupPath\(reportedPath\)/);
  assert.match(deployScript, /expectedDatabaseName:\s*databaseNameFromEnvironment\(environment\)/);
  assert.match(deployScript, /Database backup evidence validation failed/);
  assert.match(deployScript, /--execute=SELECT @@server_uuid;/);
  assert.match(deployScript, /verifyBackupMatchesMigrationTarget\(backupEvidence, env\)/);
  assert.match(deployScript, /Backup server UUID .* does not match migration target UUID/);
  assert.match(deployScript, /function isTransientMysqlReadinessError/);
  assert.match(deployScript, /2002\|2003\|2005\|2013\|1049/);
  assert.match(deployScript, /Migration target MySQL is not ready yet/);
  assert.match(deployScript, /Atomics\.wait/);
  assert.match(deployScript, /not retrying authentication, permission, or SQL failures/);
  assert.ok(
    deployScript.indexOf('createVerifiedDatabaseBackup({') < deployScript.indexOf("log('Database migrations completed before application startup.')"),
    'backup verification must happen before the migrator completes',
  );
});

test('online updater binds backup evidence to the exact migration server', () => {
  assert.match(updater, /await verifyBackupMatchesMigrationTarget\(task, backupEvidence, deploymentEnv\)/);
  assert.match(updater, /if \(manifest\.database\.mode !== 'none' && manifest\.images\.migrator\)/);
  assert.match(updater, /--execute=SELECT @@server_uuid;/);
  assert.match(updater, /Backup server UUID .* does not match migration target UUID/);
  assert.match(updater, /effectiveBackupEnvironment/);
  assert.match(updater, /WSLENV: wslForwardedEnvironment\(/);
});

test('observability materializes exporter credentials without exposing the password in container argv', () => {
  assert.match(deployScript, /MYSQLD_EXPORTER_PASSWORD:\s*randomSecret\('mysql-exporter'\)/);
  assert.match(deployScript, /ensureMysqlExporterSecret\(env\)/);
  assert.match(deployScript, /atomicWriteProtectedFile\(secretPath/);
  assert.match(deployScript, /provisionLocalMysqlExporterAccount\(\)/);
  assert.match(deployScript, /mysqlAuthenticated/);
  assert.match(deployScript, /mysql_up%7Bjob%3D%22mysql%22%7D/);
  assert.doesNotMatch(deployScript, /'-e',\s*`?MYSQLD_EXPORTER_PASSWORD=/);
  assert.doesNotMatch(composeProd, /\$\{MYSQLD_EXPORTER_PASSWORD(?::[^}]*)?\}/);
  assert.match(deployScript, /External MYSQLD_EXPORTER_ADDRESS must exactly match the DB_URL target/);
  assert.match(installScript, /External MYSQLD_EXPORTER_ADDRESS must exactly match the DB_URL target/);
  assert.match(deployScript, /directives\.length !== 3/);
  assert.match(installScript, /directives\.length !== 3/);
});

test('local MySQL exporter provisioning converges privileges and is part of installer readiness', () => {
  for (const [label, source] of [
    ['deploy-container', deployScript],
    ['install-platform', installScript],
  ]) {
    assert.match(source, /readMysqlExporterPassword\(resolveMysqlExporterSecretPath\(environment\)\)/, `${label} must provision from the mounted secret source`);
    assert.match(source, /REVOKE ALL PRIVILEGES, GRANT OPTION FROM/, `${label} must remove stale privileges before granting the monitoring set`);
    assert.match(source, /SHOW GRANTS FOR/, `${label} must verify the converged account grants`);
    assert.doesNotMatch(source, /Skipping automatic local exporter-account provisioning/, `${label} must not confuse an external password file with an existing DB account`);
  }

  assert.match(deployScript, /await ensureLocalMysqlExporterAccount\(\);\s*await runDatabaseMigrations\(\);/);
  assert.match(installScript, /composeUp\(options, 'infrastructure',[\s\S]*?await provisionLocalMysqlExporterAccount\(options\);/);
  assert.match(installScript, /\['mysqld-exporter', 'backup-metrics-exporter', 'prometheus'/);
  assert.match(installScript, /mysql_up%7Bjob%3D%22mysql%22%7D/);
  assert.match(installScript, /MySQL observability is not ready/);
});

test('admin bootstrap credential is mounted into the one-shot migrator and never injected into the business runtime', () => {
  assert.match(
    envExample,
    /^LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE=/m,
    'the deployment template must accept a secret-file path instead of a plaintext password'
  );
  assert.doesNotMatch(envExample, /^LUMIRA_INITIAL_ADMIN_PASSWORD=/m);
  assert.doesNotMatch(composeProd, /LUMIRA_INITIAL_ADMIN_PASSWORD:/);
  assert.match(
    deployScript,
    /lumira_bootstrap_admin_password/,
    'deploy-container must mount the admin bootstrap secret into the migrator'
  );
  assert.match(
    updater,
    /lumira_bootstrap_admin_password/,
    'online updates must mount the same secret into the migrator'
  );
  assert.doesNotMatch(
    deployScript,
    /'-e',\s*`LUMIRA_BOOTSTRAP_ADMIN_PASSWORD=/,
    'the plaintext bootstrap password must never appear in docker argv'
  );
});

test('database-backed deployments cannot bypass administrator bootstrap', () => {
  assert.match(deployScript, /validateMigrationSkipScope\(\)/);
  assert.match(
    deployScript,
    /deploymentRequiresDatabasePreparation\(serviceNames\)/,
    'full and backend deployments must share one database-preparation policy',
  );
  assert.match(
    deployScript,
    /Refusing --skip-migrations for a full or database-backed deployment/,
    'unsafe migration skips must fail before deployment starts',
  );
  assert.ok(
    deployScript.indexOf('validateMigrationSkipScope();') < deployScript.indexOf('ensureEnvFile();'),
    'the unsafe-skip guard must run before deployment files or Docker state are changed',
  );
});

test('local migrator rebuilds honor configured registry and Maven mirrors', () => {
  for (const buildArg of ['MAVEN_IMAGE', 'MAVEN_MIRROR_URL', 'MAVEN_FALLBACK_MIRROR_URL', 'FLYWAY_IMAGE']) {
    assert.match(deployScript, new RegExp(`'${buildArg}'`), `migrator build must forward ${buildArg}`);
  }
  assert.match(envExample, /^MAVEN_MIRROR_URL=/m);
  assert.match(envExample, /^MAVEN_FALLBACK_MIRROR_URL=/m);
  assert.match(envExample, /^FLYWAY_IMAGE=/m);
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

test('production compose keeps the async runtime free of business service base URLs', () => {
  const asyncBlock = composeProd.match(/lumira-async:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(asyncBlock, 'async runtime compose block must exist');
  assert.doesNotMatch(asyncBlock[0], /^\s+SYSTEM_SERVICE_BASE_URL:/m, 'async runtime must not receive SYSTEM_SERVICE_BASE_URL');
  assert.doesNotMatch(asyncBlock[0], /^\s+PAYMENT_SERVICE_BASE_URL:/m, 'async runtime must not receive PAYMENT_SERVICE_BASE_URL');
  assert.doesNotMatch(asyncBlock[0], /^\s+AUTH_SERVICE_BASE_URL:/m, 'async runtime must not receive AUTH_SERVICE_BASE_URL');
  assert.doesNotMatch(asyncBlock[0], /^\s+TEAM_SERVICE_BASE_URL:/m, 'async runtime must not receive TEAM_SERVICE_BASE_URL');
  assert.match(asyncBlock[0], /^\s+LUMIRA_ASYNC_CONTROL_PLANE_BASE_URL:/m, 'async runtime must use the active control-plane URL');
});

test('install-platform keeps generated secrets and required env checks aligned with scoped team token', () => {
  const occurrences = [...installScript.matchAll(/\bSAAS_INTERNAL_TEAM_TOKEN\b/g)].length;
  assert.ok(occurrences >= 2, 'install-platform must both generate and require SAAS_INTERNAL_TEAM_TOKEN');
});

test('install-platform starts the supported async and job runtime topology', () => {
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
  assert.doesNotMatch(installScript, /NACOS|nacos|useNacos|--nacos/, 'install-platform must not expose an unsupported config/discovery toggle');
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

test('startup exposes exactly the native local and container production modes', () => {
  assert.match(startScript, /\['1', 'local', 'dev', 'development'\]/);
  assert.match(startScript, /\['2', 'prod', 'production'\]/);
  assert.match(startScript, /start-local\.mjs/);
  assert.match(startScript, /start-production\.mjs/);
  assert.doesNotMatch(startScript, /translatedArgs\.unshift\('--local-mysql'\)/);
  assert.equal(rootPackage.scripts['start:local'], 'node bin/start-platform.mjs local');
  assert.equal(rootPackage.scripts['start:production'], 'node bin/start-platform.mjs production');
});

test('local startup runs host toolchains and cannot inherit an online frontend target', () => {
  assert.match(localStartScript, /Docker was not invoked/);
  assert.match(localStartScript, /'services\/lumira-admin\/pom\.xml'/);
  assert.match(localStartScript, /'services\/lumira-async\/pom\.xml'/);
  assert.match(localStartScript, /'services\/lumira-quartz\/pom\.xml'/);
  assert.match(localStartScript, /UMI_APP_API_BASE_URL: ''/);
  assert.match(localStartScript, /UMI_APP_LOCAL_NATIVE_MODE: 'true'/);
  assert.match(localStartScript, /UMI_DEV_API_TARGET: backendUrl/);
  assert.match(localStartScript, /SPRING_PROFILES_ACTIVE: localProfile/);
  assert.doesNotMatch(localStartScript, /deploy-container\.mjs|docker-compose/);
});

test('production startup delegates only to the production container path', () => {
  assert.match(productionStartScript, /deploy-container\.mjs/);
  assert.match(productionStartScript, /SPRING_PROFILES_ACTIVE: 'prod'/);
  assert.match(productionStartScript, /--local-mysql belongs to a local container topology/);
  assert.doesNotMatch(productionStartScript, /start-local\.mjs|spring-boot:run|pnpm.*dev/);
});

test('production image contexts exclude host secrets, state, backups, and image archives', () => {
  for (const entry of [
    'deploy/.env',
    'deploy/.backup',
    'deploy/.generated',
    'deploy/.update-state.json',
    'deploy/.update-tasks',
    'deploy/data',
    'deploy/secrets',
    '*.tar',
  ]) {
    assert.match(dockerignore, new RegExp(`^${entry.replaceAll('.', '\\.').replaceAll('*', '\\*')}\\r?$`, 'm'));
  }
});

test('certificate render output is writable and durable across control-plane slots', () => {
  assert.match(serviceDockerfile, /mkdir -p[^\n]*\/app\/storage/);
  assert.match(serviceDockerfile, /chown -R app:app[^\n]*\/app\/storage/);
  assert.match(
    composeProd,
    /lumira-server-blue:[\s\S]*?volumes:[\s\S]*?- certificate_data:\/app\/storage/,
    'the shared server anchor must persist generated certificates'
  );
  assert.match(composeProd, /^  certificate_data: null$/m);
  assert.match(deployScript, /\{ key: 'certificate_data', mountPath: '\/mnt\/certificate_data' \}/);
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

test('deploy-container shares deployment verification and exposes only supported topology controls', () => {
  assert.match(
    deployScript,
    /run\('node', \['bin\/check-deployment\.mjs'\]/,
    'deploy-container must delegate deployment verification to bin/check-deployment.mjs'
  );
  assert.doesNotMatch(deployScript, /NACOS|nacos|--nacos/, 'deploy-container must not expose an unsupported config/discovery toggle');
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
    /waitForHttp\(`\$\{baseUrl\}\/api\/health`, 'lumira-server health API'\)/,
    'deploy-container must wait for the active Spring application before broad route checks'
  );
  assert.match(
    deployScript,
    /if \(localMysql && !pullImages && localImageStatus\.status === 0\) \{[\s\S]*?migratorImage = localMigratorImage/,
    'local-mysql deployment must reuse a cached local migrator unless an explicit pull was requested'
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

test('deployment env example exposes only real cross-runtime URLs', () => {
  for (const key of [
    'AUTH_SERVICE_BASE_URL',
    'TEAM_SERVICE_BASE_URL',
    'SAAS_JOB_ASYNC_RUNTIME_BASE_URL',
    'SAAS_JOB_CONTROL_PLANE_BASE_URL',
  ]) {
    assert.match(envExample, new RegExp(`^${key}=`, 'm'), `${key} must be documented in deploy/.env.example`);
  }
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
    'SAAS_JOB_BACKEND_BASE_URL',
    'SAAS_JOB_SYSTEM_SERVICE_BASE_URL',
    'SAAS_JOB_MESSAGE_SERVICE_BASE_URL',
    'SAAS_JOB_FILE_SERVICE_BASE_URL',
    'SAAS_JOB_PAYMENT_SERVICE_BASE_URL',
    'SAAS_JOB_PLUGIN_SERVICE_BASE_URL',
  ]) {
    assert.doesNotMatch(envExample, new RegExp(`^${key}=`, 'm'), `${key} must not reintroduce a split production topology`);
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
  assert.match(releaseManifestGenerator, /assertDistinctRuntimeDigests\(/);
  assert.match(ciWorkflow, /concurrency:\r?\n\s+group: ci-\$\{\{ github\.ref \}\}\r?\n\s+cancel-in-progress: true/);
  assert.match(ciWorkflow, /steps\.build_server\.outputs\.digest/);
  assert.match(ciWorkflow, /steps\.build_ui\.outputs\.digest/);
  assert.match(ciWorkflow, /gh release edit continuous[\s\S]*?--target "\$GITHUB_SHA"/);
  assert.match(ciWorkflow, /gh release (?:view|create) continuous/);
  assert.match(ciWorkflow, /gh release upload continuous/);
  assert.match(envExample, /PLATFORM_UPDATE_MANIFEST_URL=https:\/\/api\.github\.com\/repos\/Elexvx\/Lumira\/releases\/tags\/continuous/);
});

test('frontend blue-green containers stay opt-in and independently addressable', () => {
  const localUiService = composeProd.slice(composeProd.indexOf('  lumira-ui-blue:'), composeProd.indexOf('  edge-proxy:'));
  assert.match(
    composeProd,
    /lumira-ui-blue:[\s\S]*?profiles:\r?\n\s+- local-lumira-ui\r?\n\s+- blue[\s\S]*?lumira-ui-green:[\s\S]*?- green/,
    'both UI slots must be guarded by local and slot profiles'
  );
  assert.doesNotMatch(
    composeProd,
    /edge-proxy:[\s\S]*depends_on:\r?\n(?:\s+- .*\r?\n)*\s+- lumira-ui-(?:blue|green)/,
    'edge-proxy must not start a UI slot implicitly'
  );
  assert.match(
    deployScript,
    /local-lumira-ui/,
    'deploy-container must enable the local-lumira-ui profile when explicitly deploying lumira-ui'
  );
  assert.doesNotMatch(
    localUiService,
    /lumira-server/,
    'local lumira-ui must not depend on the removed legacy server service'
  );
});

test('edge proxy root path follows the atomically generated frontend upstream', () => {
  assert.doesNotMatch(edgeNginx, /proxy_pass http:\/\/lumira-ui-(?:blue|green)/, 'edge proxy must not hard-code a UI slot');
  assert.match(edgeNginx, /proxy_pass http:\/\/\$frontend_upstream\$request_uri;/, 'edge proxy root path must use the active frontend upstream');
});

test('api proxy does not override backend CORS policy with a hard-coded origin allowlist', () => {
  assert.doesNotMatch(apiNginx, /Access-Control-Allow-Origin/, 'api proxy must forward backend CORS headers instead of injecting its own ACAO');
  assert.doesNotMatch(apiNginx, /elexvx\.com|bm\.aiadc\.org\.cn|vercel\.app/, 'api proxy must not hard-code deployment-specific origin allowlists');
});

test('api proxy keeps logical routes but rejects independent owner upstreams in production', () => {
  const apiProxyBlock = composeProd.match(/api-proxy:[\s\S]*?(?=\n  [a-z0-9-]+:|\nvolumes:|\nnetworks:|\n$)/i);
  assert.ok(apiProxyBlock, 'api-proxy compose block must exist');
  assert.doesNotMatch(
    apiProxyBlock[0],
    /(?:GATEWAY|SYSTEM_SERVICE|AUTH_SERVICE|FILE_SERVICE|MESSAGE_SERVICE|PLUGIN_SERVICE|PAYMENT_SERVICE|LOCALIZATION_SERVICE|TEAM_SERVICE|AI_SERVICE)_UPSTREAM:/,
    'api-proxy must not expose un-deployed owner-service upstream overrides'
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

test('api proxy routes control-plane jobs to the active control plane slot', () => {
  for (const route of [
    'outbox',
    'online-session',
    'event-catalog',
    'export',
    'user-export',
    'registration-export',
    'reviews',
    'competition',
  ]) {
    assert.match(
      apiNginx,
      new RegExp(`location \\^~ \\/internal\\/jobs\\/${route}[\\s\\S]*proxy_pass http:\\/\\/\\$system_upstream\\$request_uri`),
      `${route} jobs must reach the active control plane instead of the async runtime`
    );
  }
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
    for (const field of ['APP_VERSION', 'BUILD_VERSION', 'FRONTEND_VERSION', 'BACKEND_VERSION', 'BUILD_TIME', 'GIT_COMMIT', 'GIT_BRANCH', 'DATABASE_VERSION']) {
      assert.match(envExample, new RegExp(`^LUMIRA_SERVER_${slot}_${field}=`, 'm'));
      assert.match(composeProd, new RegExp(`LUMIRA_SERVER_${slot}_${field}`));
    }
  }
  assert.match(updater, /LUMIRA_SERVER_\$\{targetSlot\.toUpperCase\(\)\}_GIT_COMMIT/);
  assert.match(
    composeProd,
    /lumira-server-blue:[\s\S]*?extra_hosts:\r?\n\s+- host\.docker\.internal:host-gateway/,
    'both blue-green slots must be able to reach the host updater through the anchored server definition'
  );
});

test('local frontend routes APIs through the stable blue-green proxy', () => {
  assert.doesNotMatch(uiNginx, /lumira-server/, 'local frontend must not resolve the removed legacy server name');
  assert.match(uiNginx, /proxy_pass http:\/\/api-proxy:80\/api\//);
  assert.match(uiNginx, /proxy_pass http:\/\/api-proxy:80\/ws\//);
});
