import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const readRepoFile = (...segments) => readFileSync(path.join(repoRoot, ...segments), 'utf8');

const categoryValues = [
  'INNOVATION',
  'APPLICATION',
  'AI_APPLICATION',
  'ALGORITHM',
  'DATA_SCIENCE',
  'ROBOTICS',
  'CREATIVE_DESIGN',
  'ENTREPRENEURSHIP',
  'CHALLENGE',
  'SKILLS',
  'SPECIAL',
  'INVITATIONAL',
  'OTHER',
];

test('competition category fallback and database dictionary stay aligned', () => {
  const competitionPage = readRepoFile('lumira-ui', 'src', 'pages', 'competition', 'CompetitionPage.tsx');
  const bootstrapSql = readRepoFile('lumira-backend', 'sql', 'saas.sql');
  const upgradeSql = readRepoFile('deploy', 'migrations', 'V202608210001__expand_competition_category_dictionary.sql');

  for (const value of categoryValues) {
    assert.match(competitionPage, new RegExp(`value: '${value}'`));
    assert.match(bootstrapSql, new RegExp(`'${value}'`));
    assert.match(upgradeSql, new RegExp(`'${value}'`));
  }
});

test('competition settings exposes confirmed archive and delete danger actions', () => {
  const settingsPage = readRepoFile('lumira-ui', 'src', 'pages', 'competition', 'CompetitionSettingsPage.tsx');

  assert.match(settingsPage, />\s*\u5371\u9669\u64cd\u4f5c\s*</);
  assert.match(settingsPage, /status: 'archived'/);
  assert.match(settingsPage, /title="\u786e\u8ba4\u5f52\u6863\u8be5\u8d5b\u4e8b\uff1f"/);
  assert.match(settingsPage, /title="\u786e\u8ba4\u5220\u9664\u8be5\u8d5b\u4e8b\uff1f"/);
  assert.match(settingsPage, /await deleteCompetition\(competition\.id\)/);
  assert.match(settingsPage, /disabled=\{competition\.status === 'archived'\}/);
  assert.match(settingsPage, />\s*\u5f52\u6863\s*<\/Button>/);
  assert.match(settingsPage, />\s*\u5220\u9664\s*<\/Button>/);
  assert.equal((settingsPage.match(/okButtonProps=\{\{ danger: true \}\}/g) || []).length, 2);
});
