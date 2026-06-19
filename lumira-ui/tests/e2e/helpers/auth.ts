import { expect, type Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import path from 'node:path';

export const authFile = path.join(process.cwd(), 'test-results', '.auth', 'admin.json');

const adminUser = process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const initialAdminPassword = process.env.PLAYWRIGHT_ADMIN_PASSWORD || '123456';
const changedAdminPassword = process.env.PLAYWRIGHT_NEW_PASSWORD || 'E2eAdmin123!';
const captchaCode = process.env.PLAYWRIGHT_CAPTCHA_CODE || '';

type LoginOutcome = 'logged-in' | 'forced-change' | 'stayed-on-login';

const ensurePasswordMode = async (page: Page) => {
  const accountInput = page.getByTestId('login-account-input');
  if (await accountInput.isVisible().catch(() => false)) {
    return;
  }

  const passwordTab = page.getByText(/密码登录|Password login/i).first();
  if (await passwordTab.isVisible().catch(() => false)) {
    await passwordTab.click();
  }
};

const acceptAgreementIfPresent = async (page: Page) => {
  const agreement = page.getByTestId('login-agreement-checkbox');
  if (await agreement.isVisible().catch(() => false)) {
    await agreement.click();
  }
};

const fillCaptchaIfPresent = async (page: Page) => {
  const captchaInput = page.getByTestId('login-captcha-input');
  if (!(await captchaInput.isVisible().catch(() => false))) {
    return;
  }
  if (!captchaCode) {
    throw new Error('Login captcha is enabled. Set PLAYWRIGHT_CAPTCHA_CODE or disable captcha in the E2E baseline settings.');
  }
  await captchaInput.fill(captchaCode);
};

const waitForLoginOutcome = async (page: Page): Promise<LoginOutcome> => {
  const forcedPasswordInput = page.getByTestId('forced-password-new-input');
  return Promise.race([
    forcedPasswordInput.waitFor({ state: 'visible', timeout: 10_000 }).then((): LoginOutcome => 'forced-change'),
    page.waitForURL((url) => !url.pathname.startsWith('/user/login'), { timeout: 10_000 }).then((): LoginOutcome => 'logged-in'),
  ]).catch((): LoginOutcome => 'stayed-on-login');
};

const submitPasswordLogin = async (page: Page, password: string): Promise<LoginOutcome> => {
  await page.goto('/user/login');
  await ensurePasswordMode(page);
  await expect(page.getByTestId('login-account-input')).toBeVisible();
  await page.getByTestId('login-account-input').fill(adminUser);
  await page.getByTestId('login-password-input').fill(password);
  await acceptAgreementIfPresent(page);
  await fillCaptchaIfPresent(page);
  await page.getByTestId('login-submit-button').click();
  return waitForLoginOutcome(page);
};

const completeForcedPasswordChange = async (page: Page) => {
  await page.getByTestId('forced-password-new-input').fill(changedAdminPassword);
  await page.getByTestId('forced-password-confirm-input').fill(changedAdminPassword);
  await page.getByTestId('forced-password-submit').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/user/login'), { timeout: 20_000 });
};

const completeForcedPasswordChangeIfPresent = async (page: Page) => {
  const forcedPasswordInput = page.getByTestId('forced-password-new-input');
  if (await forcedPasswordInput.isVisible().catch(() => false)) {
    await completeForcedPasswordChange(page);
  }
};

export const authenticateAdmin = async (page: Page) => {
  let outcome = await submitPasswordLogin(page, initialAdminPassword);

  if (outcome === 'stayed-on-login' && initialAdminPassword === '123456') {
    outcome = await submitPasswordLogin(page, changedAdminPassword);
  }

  if (outcome === 'forced-change') {
    await completeForcedPasswordChange(page);
  } else if (outcome !== 'logged-in') {
    throw new Error('Unable to authenticate the Playwright admin user.');
  }

  await page.goto('/dashboard/home');
  await completeForcedPasswordChangeIfPresent(page);
  await expect(page).toHaveURL(/\/dashboard\/home/);
  await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
  mkdirSync(path.dirname(authFile), { recursive: true });
  await page.context().storageState({ path: authFile });
};
