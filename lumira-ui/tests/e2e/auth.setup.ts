import { test } from '@playwright/test';
import {
  authenticateAdmin,
  authenticateUser,
  expertAuthFile,
  participantAuthFile,
} from './helpers/auth';

const roleMatrixEnabled = process.env.PLAYWRIGHT_ROLE_MATRIX === 'true';
const participantUser = process.env.PLAYWRIGHT_PARTICIPANT_USER || '';
const participantPassword = process.env.PLAYWRIGHT_PARTICIPANT_PASSWORD || '';
const expertUser = process.env.PLAYWRIGHT_EXPERT_USER || '';
const expertPassword = process.env.PLAYWRIGHT_EXPERT_PASSWORD || '';

test('authenticate admin and persist storage state', async ({ page }) => {
  await authenticateAdmin(page);
});

test('authenticate participant and persist storage state', async ({ page }) => {
  test.skip(!roleMatrixEnabled, 'Set PLAYWRIGHT_ROLE_MATRIX=true to prepare role browser states.');
  await authenticateUser(page, {
    username: participantUser,
    resolvedPassword: participantPassword,
    landingPath: '/competitions/register',
    storageStatePath: participantAuthFile,
  });
});

test('authenticate expert and persist storage state', async ({ page }) => {
  test.skip(!roleMatrixEnabled, 'Set PLAYWRIGHT_ROLE_MATRIX=true to prepare role browser states.');
  await authenticateUser(page, {
    username: expertUser,
    resolvedPassword: expertPassword,
    landingPath: '/expert-review/reviews',
    storageStatePath: expertAuthFile,
  });
});
