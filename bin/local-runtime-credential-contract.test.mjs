import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const startLocal = readFileSync(path.join(repoRoot, 'bin', 'start-local.mjs'), 'utf8');
const localEnvExample = readFileSync(path.join(repoRoot, 'lumira-backend', '.env.example'), 'utf8');
const adminConfig = readFileSync(
  path.join(repoRoot, 'lumira-backend', 'services', 'lumira-admin', 'src', 'main', 'resources', 'application.yml'),
  'utf8',
);
const localAdminBootstrapTest = readFileSync(path.join(repoRoot, 'bin', 'local-admin-bootstrap.test.mjs'), 'utf8');
const localAdminBootstrapSource = readFileSync(path.join(repoRoot, 'bin', 'lib', 'local-admin-bootstrap.mjs'), 'utf8');
const databaseMigrationContractTest = readFileSync(
  path.join(repoRoot, 'bin', 'database-migration-contract.test.mjs'),
  'utf8',
);
const javaAdminBootstrapSource = readFileSync(
  path.join(repoRoot, 'deploy', 'bootstrap-admin', 'src', 'main', 'java', 'com', 'lumira', 'deploy', 'bootstrap', 'AdminCredentialBootstrap.java'),
  'utf8',
);
const javaAdminBootstrapTest = readFileSync(
  path.join(repoRoot, 'deploy', 'bootstrap-admin', 'src', 'test', 'java', 'com', 'lumira', 'deploy', 'bootstrap', 'AdminCredentialBootstrapTest.java'),
  'utf8',
);

const runtimeSecretKeys = [
  'JWT_SECRET',
  'FIELD_SECRET',
  'PLUGIN_SIGNATURE_SECRET',
  'SPRING_SECURITY_USER_PASSWORD',
  'SAAS_INTERNAL_SYSTEM_TOKEN',
  'SAAS_INTERNAL_AUTH_TOKEN',
  'SAAS_INTERNAL_AUTH_SYSTEM_TOKEN',
  'SAAS_INTERNAL_FILE_TOKEN',
  'SAAS_INTERNAL_MESSAGE_TOKEN',
  'SAAS_INTERNAL_PAYMENT_TOKEN',
  'SAAS_INTERNAL_PLUGIN_TOKEN',
  'SAAS_INTERNAL_TEAM_TOKEN',
  'SAAS_INTERNAL_JOB_TOKEN',
];

function envTemplateValue(key) {
  return localEnvExample.match(new RegExp(`^${key}=(.*)$`, 'm'))?.[1];
}

test('native local startup generates unconfigured runtime secrets without shipping fixed credentials', () => {
  assert.match(startLocal, /import \{ randomBytes \} from 'node:crypto';/);
  assert.match(startLocal, /randomBytes\(32\)\.toString\('base64url'\)/);
  assert.match(
    startLocal,
    /const configuredDbPassword = process\.env\.LUMIRA_LOCAL_DB_PASSWORD \?\? fileEnv\.DB_PASSWORD/,
  );
  assert.match(startLocal, /DB_PASSWORD:\s*configuredDbPassword \?\? ''/);
  assert.match(startLocal, /DB_PASSWORD must be explicitly configured/);

  for (const key of runtimeSecretKeys) {
    assert.match(
      startLocal,
      new RegExp(`${key}:\\s*localRuntimeSecret\\(process\\.env\\.${key}, fileEnv\\.${key}\\)`),
      `${key} must use a configured value or a process-local random secret`,
    );
    assert.equal(envTemplateValue(key), '', `${key} must not have a usable value in the committed local template`);
  }

  assert.equal(envTemplateValue('DB_PASSWORD'), '', 'the committed template must not ship a database password');
  assert.equal(envTemplateValue('REDIS_PASSWORD'), '', 'passwordless local Redis should use an explicit empty value');
});

test('admin configuration does not provide known database, basic-auth, JWT, or field-encryption fallbacks', () => {
  assert.doesNotMatch(adminConfig, /\$\{DB_PASSWORD:[^}]+\}/);
  assert.doesNotMatch(adminConfig, /\$\{SPRING_SECURITY_USER_PASSWORD:[^}]+\}/);
  assert.doesNotMatch(adminConfig, /\$\{JWT_SECRET:[^}]+\}/);
  assert.doesNotMatch(adminConfig, /\$\{FIELD_SECRET:[^}]+\}/);
  assert.match(adminConfig, /\$\{DB_PASSWORD:\}/);
  assert.match(adminConfig, /\$\{SPRING_SECURITY_USER_PASSWORD:\}/);
  assert.match(adminConfig, /\$\{JWT_SECRET:\}/);
  assert.match(adminConfig, /\$\{FIELD_SECRET:\}/);
});

test('security regression tests generate credential fixtures instead of embedding reusable values', () => {
  assert.doesNotMatch(
    localAdminBootstrapTest,
    /(?:DB_PASSWORD|password):\s*(?:\(\)\s*=>\s*)?['"][^'"\r\n]+['"]/,
  );
  assert.doesNotMatch(localAdminBootstrapTest, /passwordFactory:\s*\(\)\s*=>\s*['"][^'"\r\n]+['"]/);
  assert.doesNotMatch(localAdminBootstrapTest, /writeFileSync\([^,\r\n]+,\s*['"][^'"\r\n]+['"]/);
  assert.doesNotMatch(databaseMigrationContractTest, /\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}/);
  assert.doesNotMatch(javaAdminBootstrapSource, /\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}/);
  assert.doesNotMatch(javaAdminBootstrapTest, /\.encode\(['"][^'"\r\n]+['"]\)/);
  assert.doesNotMatch(javaAdminBootstrapTest, /['"][^'"\r\n]+['"]\.toCharArray\(\)/);
});

test('local administrator notices cannot return or print the generated plaintext', () => {
  assert.doesNotMatch(localAdminBootstrapSource, /password:\s*outcome ===/);
  assert.doesNotMatch(localAdminBootstrapSource, /One-time password:\s*\$\{result\.password\}/);
});
