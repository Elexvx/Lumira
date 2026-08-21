import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { resolveRepoRoot } from './lib/exec-utils.mjs';

const repoRoot = resolveRepoRoot(import.meta.url);
const read = (file) => readFileSync(path.join(repoRoot, file), 'utf8');

test('registration and password reset are adapted into the existing login surface', () => {
  const login = read('lumira-ui/src/pages/user/Login.tsx');
  const fields = read('lumira-ui/src/pages/user/login/components/LoginFormFields.tsx');
  const routes = read('lumira-ui/src/routes/meta.ts');

  assert.match(login, /type AuthEntryMode = 'login' \| 'registration' \| 'password-reset'/);
  assert.match(login, /data-auth-entry-mode=\{authEntryMode\}/);
  assert.match(login, /activeLoginMode=\{authEntryMode === 'registration' \? 'sms' : presentedLoginMode\}/);
  assert.match(login, /<PasswordResetPanel onBack=\{showLogin\} onComplete=\{showLogin\} \/>/);
  assert.doesNotMatch(login, /const PasswordResetModal/);
  assert.match(fields, /variant\?: 'login' \| 'registration'/);
  assert.match(fields, /data-testid="registration-form-fields"/);
  assert.doesNotMatch(routes, /path:\s*['"]\/user\/(?:register|forgot-password|password-reset)['"]/);
});

test('registration reuses SMS login challenge and completion instead of adding a parallel API', () => {
  const login = read('lumira-ui/src/pages/user/Login.tsx');
  const runtime = read('lumira-ui/src/pages/user/login/hooks/useLoginFlowRuntime.ts');
  const controller = read('lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/controller/InternalSystemController.java');

  assert.match(login, /loginFlow\.setActiveLoginMode\('sms'\)/);
  assert.match(runtime, /\/v1\/auth\/login\/code\/challenge/);
  assert.match(runtime, /\/v1\/auth\/login\/code\/complete/);
  assert.match(controller, /startPendingLoginCodeChallenge\(normalizedAccount, normalizedLoginType\)/);
  assert.match(controller, /registerLoginCodeUser\(normalizedAccount, normalizedLoginType\)/);
  assert.doesNotMatch(controller, /requireRegistrationEnabled/);
});

test('registration toggle is absent from runtime types, settings UI, and security persistence', () => {
  const securityPage = read('lumira-ui/src/pages/settings/security.tsx');
  const apiTypes = read('lumira-ui/src/types/api.ts');
  const settingsService = read('lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/service/SecuritySettingsService.java');
  const baseline = read('lumira-backend/sql/saas.sql');

  for (const source of [securityPage, apiTypes, settingsService, baseline]) {
    assert.doesNotMatch(source, /registrationEnabled|security\.registration-enabled/);
  }
});

test('password reset remains on the shared verification challenge response path', () => {
  const login = read('lumira-ui/src/pages/user/Login.tsx');
  const adapter = read('lumira-ui/src/services/verification/challengeResponseAdapter.tsx');

  assert.match(login, /\/v1\/auth\/password-reset\/challenge/);
  assert.match(login, /\/v1\/auth\/password-reset\/complete/);
  assert.match(adapter, /registerRequestSuccessAdapter\(adaptVerificationChallengeResponse/);
});
