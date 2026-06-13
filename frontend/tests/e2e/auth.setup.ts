import { test } from '@playwright/test';
import { authenticateAdmin } from './helpers/auth';

test('authenticate admin and persist storage state', async ({ page }) => {
  await authenticateAdmin(page);
});
