import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

test('fresh SQL and forward migration register the same disabled built-in mock SMS plugin', () => {
  const baseline = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608210005__add_builtin_mock_sms_plugin.sql');
  const baselineVersion = read('lumira-backend/sql/saas-baseline-version.txt').trim();

  for (const sql of [baseline, migration]) {
    assert.match(sql, /'builtin-mock-sms'/);
    assert.match(sql, /'sms-provider'/);
    assert.match(sql, /'verification-debug-modal'/);
    assert.match(sql, /'1\.0\.0'/);
    assert.match(sql, /'DISABLED'/);
  }
  assert.equal(baselineVersion, '202608310001');
  assert.match(migration, /verification\.sms\.provider/);
  assert.match(migration, /LOWER\(TRIM\(`config_value`\)\) IN \('debug', 'mock'\)/);
});

test('mock SMS public contract is structured and does not expose the unfinished debugCode field', () => {
  const loginChallenge = read(
    'lumira-backend/libs/lumira-common-api/src/main/java/com/lumira/api/auth/LoginCodeChallengeDTO.java',
  );
  const verificationChallenge = read(
    'lumira-backend/libs/lumira-common-api/src/main/java/com/lumira/api/system/VerificationChallengeDTO.java',
  );
  const delivery = read(
    'lumira-backend/libs/lumira-common-api/src/main/java/com/lumira/api/system/MockSmsDeliveryDTO.java',
  );
  const verificationVo = read(
    'lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/vo/VerificationChallengeVO.java',
  );
  const loginVo = read(
    'lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/auth/vo/LoginCodeChallengeVO.java',
  );

  assert.match(loginChallenge, /MockSmsDeliveryDTO mockSmsDelivery/);
  assert.match(verificationChallenge, /MockSmsDeliveryDTO mockSmsDelivery/);
  assert.doesNotMatch(loginChallenge, /debugCode/);
  assert.doesNotMatch(verificationChallenge, /debugCode/);
  assert.match(verificationVo, /MockSmsDeliveryDTO mockSmsDelivery/);
  assert.match(loginVo, /MockSmsDeliveryDTO mockSmsDelivery/);
  assert.doesNotMatch(verificationVo, /debugCode/);
  assert.doesNotMatch(loginVo, /debugCode/);
  for (const field of [
    'providerCode',
    'phoneNumbers',
    'signName',
    'templateCode',
    'templateParam',
    'resultCode',
    'resultMessage',
    'requestId',
    'bizId',
  ]) {
    assert.match(delivery, new RegExp(`String ${field}`));
  }
});

test('existing verification requests stay on the common request path and use a response adapter', () => {
  const sources = [
    'lumira-ui/src/pages/user/login/hooks/useLoginFlowRuntime.ts',
    'lumira-ui/src/pages/user/Login.tsx',
    'lumira-ui/src/app.layout.tsx',
    'lumira-ui/src/pages/profile/center/hooks/useProfileCenterPageAccess.ts',
  ].map(read).join('\n');

  assert.doesNotMatch(sources, /requestVerificationChallenge|presentMockSmsDelivery/);
  assert.ok(
    (sources.match(/request<(?:LoginCodeChallenge|SecondFactorChallenge|SecondFactorBindingChallenge)>/g) || []).length >= 10,
    'verification business flows must keep using the common request client',
  );

  const commonRequest = read('lumira-ui/src/services/common/requestCoreRequest.ts');
  const adapter = read('lumira-ui/src/services/verification/challengeResponseAdapter.tsx');
  assert.match(commonRequest, /adaptRequestSuccessData\(data\)/);
  assert.match(adapter, /registerRequestSuccessAdapter\(adaptVerificationChallengeResponse/);
});

test('login, registration, reset, second-factor, and binding mappings forward mock SMS delivery', () => {
  const verificationService = read(
    'lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationAppService.java',
  );
  const controller = read(
    'lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/controller/InternalSystemController.java',
  );

  assert.match(verificationService, /startRegistrationCodeChallenge[\s\S]*?setMockSmsDelivery\(mockSmsDelivery\)/);
  assert.match(verificationService, /startContactBindChallenge[\s\S]*?buildChallengeResponse[\s\S]*?mockSmsDelivery/);
  assert.match(verificationService, /collectSecondFactorOptions[\s\S]*?getMockSmsDelivery\(\)/);
  assert.ok(
    (controller.match(/setMockSmsDelivery\(/g) || []).length >= 5,
    'all public challenge conversions must forward mockSmsDelivery',
  );
});
