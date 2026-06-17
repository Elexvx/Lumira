import { expect, test } from '@playwright/test';

const protectedPages = [
  { path: '/dashboard/home', label: 'dashboard', tag: '@smoke' },
  { path: '/download-center', label: 'download center', tag: '@smoke' },
  { path: '/ai/assistant', label: 'AI assistant', tag: '@smoke' },
  { path: '/ai/knowledge', label: 'AI knowledge' },
  { path: '/user-center/users', label: 'users', tag: '@smoke' },
  { path: '/user-center/roles', label: 'roles', tag: '@smoke' },
  { path: '/settings/security', label: 'security settings', tag: '@smoke' },
  { path: '/settings/payment', label: 'payment settings', tag: '@smoke' },
  { path: '/settings/notifications', label: 'notification settings' },
  { path: '/settings/files/all', label: 'system files', tag: '@smoke' },
  { path: '/settings/plugins', label: 'plugins', tag: '@smoke' },
  { path: '/settings/localization', label: 'localization', tag: '@smoke' },
];

test.describe('authenticated application', () => {
  for (const pageCase of protectedPages) {
    test(`${pageCase.label} page is reachable ${pageCase.tag || ''}`, async ({ page }) => {
      await page.goto(pageCase.path);

      await expect(page).not.toHaveURL(/\/user\/login/);
      await expect(page.locator('body')).toBeVisible();
      await expect(page.locator('body')).not.toContainText(/404|403|Not Found|Forbidden|无权限|页面不存在/);
    });
  }

  test('session survives a browser refresh @smoke', async ({ page }) => {
    await page.goto('/dashboard/home');
    await expect(page).toHaveURL(/\/dashboard\/home/);

    await page.reload();
    await expect(page).toHaveURL(/\/dashboard\/home/);
    await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
  });

  test('message center can be opened @smoke', async ({ page }) => {
    await page.goto('/dashboard/home');
    await expect(page).toHaveURL(/\/dashboard\/home/);

    const messageCenterButton = page.getByTestId('top-message-center-button');
    await expect(messageCenterButton).toBeVisible();
    await messageCenterButton.click();

    await expect(page.getByRole('dialog', { name: /消息中心|Message center/i })).toBeVisible();
    await expect(page.locator('body')).not.toContainText(/500|系统异常|System error/i);
  });

  test('user can log out from the top menu @smoke', async ({ page }) => {
    await page.goto('/dashboard/home');
    await page.getByTestId('top-user-menu-button').click();
    await page.getByRole('menuitem', { name: /退出|Log out/i }).click();

    await expect(page).toHaveURL(/\/user\/login/);
    await expect(page.getByTestId('login-account-input')).toBeVisible();
  });
});
