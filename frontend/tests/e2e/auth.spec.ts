import { expect, test } from '@playwright/test';

const adminUser = process.env.PLAYWRIGHT_ADMIN_USER || 'admin';

test.describe('authentication surface', () => {
  test('login page renders password form @auth @smoke', async ({ page }) => {
    await page.goto('/user/login');

    await expect(page.getByTestId('login-account-input')).toBeVisible();
    await expect(page.getByTestId('login-password-input')).toBeVisible();
    await expect(page.getByTestId('login-submit-button')).toBeVisible();

    const captchaRefresh = page.getByTestId('login-captcha-refresh');
    if (await captchaRefresh.isVisible().catch(() => false)) {
      await captchaRefresh.click();
      await expect(page.getByTestId('login-captcha-input')).toBeVisible();
    }
  });

  test('protected pages redirect anonymous visitors to login @auth @smoke', async ({ page }) => {
    await page.goto('/dashboard/home');

    await expect(page).toHaveURL(/\/user\/login/);
    await expect(page.getByTestId('login-account-input')).toBeVisible();
  });

  test('wrong password stays on login page with visible feedback @auth', async ({ page }) => {
    await page.goto('/user/login');

    await page.getByTestId('login-account-input').fill(adminUser);
    await page.getByTestId('login-password-input').fill('definitely-not-the-right-password');

    const agreement = page.getByTestId('login-agreement-checkbox');
    if (await agreement.isVisible().catch(() => false)) {
      await agreement.click();
    }

    await page.getByTestId('login-submit-button').click();
    await expect(page).toHaveURL(/\/user\/login/);
    await expect(page.getByText(/账号或密码错误|登录失败|password|failed/i).first()).toBeVisible();
  });
});
