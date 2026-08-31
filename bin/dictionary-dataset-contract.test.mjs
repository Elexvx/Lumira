import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath));
const text = (relativePath) => read(relativePath).toString('utf8');

const datasets = [
  ['reference-data/dictionaries/schools/2026-06-17/manifest.json', 3196],
  ['reference-data/dictionaries/cn-administrative-divisions/2025-12-31/manifest.json', 3217],
];

test('bundled dictionary manifests match their data files', () => {
  for (const [manifestPath, expectedRows] of datasets) {
    const manifest = JSON.parse(text(manifestPath));
    const dataPath = path.join(path.dirname(manifestPath), manifest.dataFile);
    const checksum = crypto.createHash('sha256').update(read(dataPath)).digest('hex');
    assert.equal(manifest.rowCount, expectedRows);
    assert.equal(checksum, manifest.fileSha256);
  }
});

test('large dictionary data stays outside the bootstrap SQL and ships with the service image', () => {
  const bootstrap = text('lumira-backend/sql/saas.sql');
  const dockerfile = text('deploy/docker/service.Dockerfile');
  const compose = text('deploy/docker-compose.prod.yml');
  assert.doesNotMatch(bootstrap, /4111010003/);
  assert.doesNotMatch(bootstrap, /sys_cn_administrative_division:2025-12-31/);
  assert.match(dockerfile, /COPY (?:--chown=app:app )?reference-data\/dictionaries/);
  assert.match(compose, /LUMIRA_DICTIONARY_DATASET_ROOT/);
});

test('upgrades normalize tree roots and link only existing school fields', () => {
  const roots = text('deploy/migrations/V202608300002__normalize_dictionary_tree_roots.sql');
  const schools = text('deploy/migrations/V202608300003__link_existing_school_fields_to_system_dictionary.sql');
  assert.match(roots, /SET parent_item_value = NULL/);
  assert.match(schools, /'\$\.fieldType', 'SELECT'/);
  assert.match(schools, /'\$\.optionSource', 'DICTIONARY'/);
  assert.match(schools, /'\$\.dictCode', 'sys_school'/);
  assert.match(schools, /item_key/);
  assert.doesNotMatch(schools, /UPDATE competition_config_item_template/);
  assert.ok(BigInt(text('lumira-backend/sql/saas-baseline-version.txt').trim()) >= 202608300003n);
});
