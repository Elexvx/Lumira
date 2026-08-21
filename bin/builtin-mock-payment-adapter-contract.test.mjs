import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('built-in mock payment uses the shared checkout adapter instead of a standalone page', () => {
  const routes = read('lumira-ui/src/routes/meta.ts');
  const registry = read('lumira-ui/src/services/payment/paymentCheckout.ts');
  const adapter = read('lumira-ui/src/services/payment/builtinMockPaymentCheckoutAdapter.tsx');
  const registration = read('lumira-ui/src/pages/competition/CompetitionRegistrationPage.tsx');
  const sandbox = read('lumira-ui/src/pages/settings/components/payment/SandboxPaymentOrderTab.tsx');

  assert.doesNotMatch(routes, /mock-payment\/checkout/);
  assert.match(registry, /registerPaymentCheckoutAdapter/);
  assert.match(adapter, /providerCode:\s*PROVIDER_CODE/);
  assert.match(registration, /presentPaymentCheckout\(registrationCheckoutOrder/);
  assert.match(sandbox, /presentPaymentCheckout\(order/);
});

test('mock order creation remains on the main payment order path without a redirect url', () => {
  const transactionService = read('lumira-backend/services/lumira-payment/src/main/java/com/lumira/payment/service/PaymentTransactionService.java');
  const competitionService = read('lumira-backend/services/lumira-competition/src/main/java/com/lumira/saas/modules/competition/app/CompetitionRegistrationAppService.java');

  assert.match(
    transactionService,
    /BuiltinMockPaymentAvailability\.PROVIDER_CODE\.equals\(order\.getProviderCode\(\)\)[\s\S]*?return null;/,
  );
  assert.doesNotMatch(transactionService, /mock-payment\/checkout/);
  assert.match(competitionService, /order\.setProviderCode\(source\.providerCode\(\)\)/);
});

test('fresh and upgraded plugin definitions contribute a checkout adapter and no checkout route', () => {
  const seed = read('lumira-backend/sql/saas.sql');
  const migration = read('deploy/migrations/V202608210006__adapt_builtin_mock_payment_checkout.sql');
  const runtime = read('lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/app/PluginManagementAppService.java');
  const baseline = read('lumira-backend/sql/saas-baseline-version.txt').trim();

  for (const source of [seed, migration, runtime]) {
    assert.match(source, /checkout-adapter/);
  }
  assert.doesNotMatch(migration, /checkout-route/);
  assert.match(runtime, /BUILTIN_MOCK_PAYMENT_PLUGIN,[\s\S]*?new BuiltinPluginRuntime\(\s*List\.of\(\)/);
  assert.equal(baseline, '202608210007');
});
