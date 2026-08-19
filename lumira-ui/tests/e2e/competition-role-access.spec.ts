import { expect, test, type Page } from '@playwright/test';
import {
  authFile,
  expertAuthFile,
  loginWithPassword,
  participantAuthFile,
} from './helpers/auth';

const roleMatrixEnabled = process.env.PLAYWRIGHT_ROLE_MATRIX === 'true';
const adminUser = process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const adminPassword = process.env.PLAYWRIGHT_NEW_PASSWORD || process.env.PLAYWRIGHT_ADMIN_PASSWORD || '';
const participantUser = process.env.PLAYWRIGHT_PARTICIPANT_USER || '';
const participantPassword = process.env.PLAYWRIGHT_PARTICIPANT_PASSWORD || '';
const expertUser = process.env.PLAYWRIGHT_EXPERT_USER || '';
const expertPassword = process.env.PLAYWRIGHT_EXPERT_PASSWORD || '';

const deniedSurfacePattern = /403|Forbidden|无权限|没有访问该页面的权限/i;
const brokenSurfacePattern = /500|Internal Server Error|系统异常|TypeError:|ReferenceError:/i;

const sidebarItemNames: Record<string, RegExp> = {
  '/competitions/register': /赛事报名|Competition registration/i,
  '/expert-review/reviews': /我的评审|My reviews/i,
  '/settings/payment': /支付设置|Payment settings/i,
};

const sidebarItem = (page: Page, pathname: keyof typeof sidebarItemNames) => page
  .getByRole('complementary')
  .getByRole('menuitem', { name: sidebarItemNames[pathname] });

const attachSurfaceMonitor = (page: Page) => {
  const pageErrors: string[] = [];
  const serverErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('response', (response) => {
    if (response.status() >= 500) {
      serverErrors.push(`${response.status()} ${response.url()}`);
    }
  });

  return async () => {
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('body')).not.toContainText(brokenSurfacePattern);
    expect(pageErrors, `Unhandled page errors: ${pageErrors.join('\n')}`).toEqual([]);
    expect(serverErrors, `Server error responses: ${serverErrors.join('\n')}`).toEqual([]);
  };
};

const expectAllowed = async (page: Page, pathname: string) => {
  await page.goto(pathname);
  await expect(page).toHaveURL(new RegExp(`${pathname.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?:[?#]|$)`));
  await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
  await expect(page.locator('body')).not.toContainText(deniedSurfacePattern);
};

const expectDenied = async (page: Page, pathname: string) => {
  await page.goto(pathname);
  await expect.poll(async () => {
    const currentPathname = new URL(page.url()).pathname;
    const body = await page.locator('body').innerText().catch(() => '');
    return currentPathname !== pathname || deniedSurfacePattern.test(body);
  }, { message: `${pathname} remained accessible to an unauthorized role` }).toBe(true);
  await expect(page).not.toHaveURL(/\/user\/login/);
  await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
};

const logout = async (page: Page) => {
  await page.getByTestId('top-user-menu-button').click();
  await page.getByRole('menuitem', { name: /退出|Log out/i }).click();
  await expect(page).toHaveURL(/\/user\/login/);
};

test.describe('competition role access matrix', () => {
  test.skip(!roleMatrixEnabled, 'Set PLAYWRIGHT_ROLE_MATRIX=true to run the multi-role browser matrix.');

  test.describe('anonymous', () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test('protected routes redirect to login without rendering a broken page', async ({ page }) => {
      const assertHealthy = attachSurfaceMonitor(page);
      for (const pathname of [
        '/competitions/management',
        '/competitions/register',
        '/payments/management',
        '/expert-review/reviews',
        '/settings/payment',
      ]) {
        await page.goto(pathname);
        await expect(page).toHaveURL(/\/user\/login/);
        await expect(page.getByTestId('login-account-input')).toBeVisible();
      }
      await assertHealthy();
    });
  });

  test.describe('administrator', () => {
    test.use({ storageState: authFile });

    test('can open management surfaces and operate the create control', async ({ page }) => {
      const assertHealthy = attachSurfaceMonitor(page);
      await expectAllowed(page, '/competitions/management');
      await expect(page.getByRole('button', { name: /新增赛事|创建赛事|Create competition/i })).toBeVisible();
      await expectAllowed(page, '/payments/management');
      await expectAllowed(page, '/settings/payment');
      await expectAllowed(page, '/workflows/tasks');
      await assertHealthy();
    });
  });

  test.describe('participant', () => {
    test.use({ storageState: participantAuthFile });

    test('can use own registration while administrator surfaces stay blocked', async ({ page }) => {
      const assertHealthy = attachSurfaceMonitor(page);
      await expectAllowed(page, '/competitions/register');
      await expect(page.getByRole('button', { name: /新增报名|New registration/i })).toBeVisible();
      await expect(sidebarItem(page, '/competitions/register')).toBeVisible();
      for (const pathname of [
        '/competitions/management',
        '/competitions/create',
        '/payments/management',
        '/settings/payment',
        '/expert-review/reviews',
        '/user-center/users',
      ]) {
        await expectDenied(page, pathname);
      }
      await assertHealthy();
    });
  });

  test.describe('expert', () => {
    test.use({ storageState: expertAuthFile });

    test('can open my reviews while participant and administrator controls stay blocked', async ({ page }) => {
      const assertHealthy = attachSurfaceMonitor(page);
      await expectAllowed(page, '/expert-review/reviews');
      await expect(page.getByText(/我的评审|My reviews/i).first()).toBeVisible();
      await expect(sidebarItem(page, '/expert-review/reviews')).toBeVisible();
      await expect(page.getByRole('button', { name: /新增赛事|新增报名|Create competition|New registration/i })).toHaveCount(0);
      for (const pathname of [
        '/competitions/register',
        '/competitions/management',
        '/competitions/create',
        '/payments/management',
        '/settings/payment',
        '/user-center/users',
      ]) {
        await expectDenied(page, pathname);
      }
      await assertHealthy();
    });
  });

  test.describe('role transitions', () => {
    test.use({ storageState: authFile });

    test('logout and relogin immediately replace the previous role menu', async ({ page }) => {
      const assertHealthy = attachSurfaceMonitor(page);

      await expectAllowed(page, '/settings/payment');
      await expect(sidebarItem(page, '/settings/payment')).toBeVisible();
      await logout(page);

      await loginWithPassword(page, participantUser, participantPassword);
      await expectAllowed(page, '/competitions/register');
      await expect(sidebarItem(page, '/competitions/register')).toBeVisible();
      await expect(sidebarItem(page, '/settings/payment')).toHaveCount(0);
      await expect(sidebarItem(page, '/expert-review/reviews')).toHaveCount(0);
      await logout(page);

      await loginWithPassword(page, expertUser, expertPassword);
      await expectAllowed(page, '/expert-review/reviews');
      await expect(sidebarItem(page, '/expert-review/reviews')).toBeVisible();
      await expect(sidebarItem(page, '/competitions/register')).toHaveCount(0);
      await expect(sidebarItem(page, '/settings/payment')).toHaveCount(0);
      await logout(page);

      await loginWithPassword(page, adminUser, adminPassword);
      await expectAllowed(page, '/competitions/management');
      await expect(page.getByRole('button', { name: /新增赛事|创建赛事|Create competition/i })).toBeVisible();
      await assertHealthy();
    });
  });
});
