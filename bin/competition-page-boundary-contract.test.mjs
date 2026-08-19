import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const competitionPages = path.join(repoRoot, 'lumira-ui', 'src', 'pages', 'competition');
const readPage = (name) => readFileSync(path.join(competitionPages, name), 'utf8');
const lineCount = (source) => source.split(/\r?\n/).length;

test('competition route delegates registration and settings to dedicated page modules', () => {
  const route = readPage('index.tsx');

  assert.match(route, /import CompetitionRegistrationPage from '.\/CompetitionRegistrationPage'/);
  assert.match(route, /import CompetitionSettingsPage from '.\/CompetitionSettingsPage'/);
  assert.match(route, /location\.pathname === '\/competitions\/register'/);
  assert.match(route, /\^\\\/competitions\\\/\[\^\/\]\+\\\/settings\$/);
});

test('competition and review entry pages stay below their modularity budgets', () => {
  const competition = readPage('CompetitionPage.tsx');
  const registration = readPage('CompetitionRegistrationPage.tsx');
  const settings = readPage('CompetitionSettingsPage.tsx');
  const review = readPage('CompetitionReviewPage.tsx');
  const reviewAdmin = readPage('CompetitionReviewAdminWorkbench.tsx');

  assert.ok(lineCount(competition) < 2_500, 'CompetitionPage.tsx should remain an entry/management module');
  assert.ok(lineCount(review) < 500, 'CompetitionReviewPage.tsx should remain a thin role router');
  assert.match(registration, /const CompetitionRegistrationPage = \(\) =>/);
  assert.match(settings, /const CompetitionSettingsPage = \(\) =>/);
  assert.match(reviewAdmin, /const ReviewAdminWorkbench/);
  assert.match(reviewAdmin, /export \{[\s\S]*ReviewAdminWorkbench/);
  assert.doesNotMatch(competition, /const CompetitionRegistrationPage = \(\) =>/);
});

test('shared competition configuration helpers are not duplicated across page modules', () => {
  const shared = readPage(path.join('utils', 'competitionConfigShared.ts'));
  const registration = readPage('CompetitionRegistrationPage.tsx');
  const settings = readPage('CompetitionSettingsPage.tsx');

  for (const helper of ['parseConfigItemMetadata', 'getTeamMemberLimits', 'normalizeCollectedFieldConfigKey']) {
    assert.match(shared, new RegExp(`export const ${helper}`));
    assert.match(registration, new RegExp(helper));
    assert.match(settings, new RegExp(helper));
  }
});
