import assert from 'node:assert/strict';
import test from 'node:test';

import { contractDigest, readContractFiles, validateContractSources } from './check-event-contracts.mjs';

test('event contract governance validates the checked-in envelope contracts', () => {
  const contracts = validateContractSources(readContractFiles());
  assert.equal(contracts.length, 4);
  assert.ok(contracts.every((contract) => contract.schemaDigest === contractDigest(
    readContractFiles().find((entry) => entry.relativePath === contract.relativePath).source,
  )));
});

test('backward event contract governance rejects removed required fields and type changes', () => {
  const source = (version, requiredFields, eventIdType = 'string') => `eventType: SAMPLE_EVENT
schemaVersion: ${version}
sourceModule: sample
producer: sample
schemaDigest: ${'sha256:' + '0'.repeat(64)}
compatibility:
  mode: backward
  requiredFields:
${requiredFields.map((field) => `    - ${field}`).join('\n')}
  payloadTypes:
${requiredFields.map((field) => `    ${field}: ${field === 'eventId' ? eventIdType : 'string'}`).join('\n')}
`;
  const fields = ['eventId', 'eventType', 'sourceModule', 'producer', 'aggregateId', 'schemaVersion', 'occurredAt', 'payload'];
  const validV1 = source(1, fields);
  const validV2 = source(2, [...fields, 'newField']);
  const entries = [
    { relativePath: 'sample/SAMPLE_EVENT.v1.yaml', source: validV1.replace('schemaDigest: sha256:' + '0'.repeat(64), `schemaDigest: ${contractDigest(validV1)}`) },
    { relativePath: 'sample/SAMPLE_EVENT.v2.yaml', source: validV2.replace('schemaDigest: sha256:' + '0'.repeat(64), `schemaDigest: ${contractDigest(validV2)}`) },
  ];
  assert.doesNotThrow(() => validateContractSources(entries));
  const previousWithLegacy = source(1, [...fields, 'legacyField']).replace('schemaDigest: sha256:' + '0'.repeat(64), `schemaDigest: ${contractDigest(source(1, [...fields, 'legacyField']))}`);
  const removed = source(2, fields).replace('schemaDigest: sha256:' + '0'.repeat(64), `schemaDigest: ${contractDigest(source(2, fields))}`);
  assert.throws(() => validateContractSources([
    { relativePath: 'sample/SAMPLE_EVENT.v1.yaml', source: previousWithLegacy },
    { relativePath: 'sample/SAMPLE_EVENT.v2.yaml', source: removed },
  ]), /removed required field/);
  const changed = source(2, fields, 'object').replace('schemaDigest: sha256:' + '0'.repeat(64), `schemaDigest: ${contractDigest(source(2, fields, 'object'))}`);
  assert.throws(() => validateContractSources([entries[0], { relativePath: 'sample/SAMPLE_EVENT.v2.yaml', source: changed }]), /changed type/);
});
