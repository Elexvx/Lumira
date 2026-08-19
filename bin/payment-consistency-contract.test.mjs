import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');
const read = (...parts) => readFileSync(path.join(repoRoot, ...parts), 'utf8');

const monitor = read(
  'lumira-backend', 'services', 'lumira-competition', 'src', 'main', 'java', 'com', 'lumira',
  'saas', 'modules', 'competition', 'app', 'CompetitionPaymentConsistencyService.java',
);
const registrationAdapter = read(
  'lumira-backend', 'services', 'lumira-competition', 'src', 'main', 'java', 'com', 'lumira',
  'saas', 'modules', 'competition', 'infrastructure', 'JdbcRegistrationPersistenceAdapter.java',
);
const paymentOutbox = read(
  'lumira-backend', 'services', 'lumira-payment', 'src', 'main', 'java', 'com', 'lumira',
  'payment', 'service', 'PaymentOutboxService.java',
);
const alerts = read('deploy', 'observability', 'grafana', 'provisioning', 'alerting', 'rules.yml');

test('payment consistency checks preserve owner boundaries and recover by durable event replay', () => {
  assert.match(monitor, /PaymentInternalApi/);
  assert.match(monitor, /replayPaidOrderEvent/);
  assert.doesNotMatch(monitor, /JdbcTemplate|payment_order|payment_event_outbox/);

  const consistencyQuery = registrationAdapter.slice(
    registrationAdapter.indexOf('findStalePendingPaymentCandidates'),
    registrationAdapter.indexOf('findPendingPaymentCandidateByOrder'),
  );
  assert.match(consistencyQuery, /from competition_registration cr/);
  assert.doesNotMatch(consistencyQuery, /from payment_order|join payment_order|payment_event_outbox/);

  assert.match(paymentOutbox, /event_type = \? and event_key = \?/);
  assert.match(paymentOutbox, /replayLatestPaidOrderEvent/);
  assert.match(alerts, /uid: lumira-payment-registration-consistency/);
  assert.match(alerts, /competition_payment_paid_registration_pending/);
});
