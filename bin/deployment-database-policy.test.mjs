import assert from 'node:assert/strict';
import test from 'node:test';

import { deploymentRequiresDatabasePreparation } from './lib/deployment-database-policy.mjs';

test('full and database-runtime deployments require database preparation', () => {
  assert.equal(deploymentRequiresDatabasePreparation([]), true);
  for (const serviceName of [
    'lumira-server-blue',
    'lumira-server-green',
    'lumira-async',
    'lumira-job-executor',
  ]) {
    assert.equal(deploymentRequiresDatabasePreparation([serviceName]), true, serviceName);
  }
  assert.equal(
    deploymentRequiresDatabasePreparation(['lumira-ui', 'lumira-server-blue']),
    true,
  );
});

test('explicit non-database service deployments do not require database preparation', () => {
  assert.equal(deploymentRequiresDatabasePreparation(['lumira-ui']), false);
  assert.equal(deploymentRequiresDatabasePreparation(['api-proxy', 'edge-proxy']), false);
  assert.equal(deploymentRequiresDatabasePreparation(['prometheus', 'grafana']), false);
});
