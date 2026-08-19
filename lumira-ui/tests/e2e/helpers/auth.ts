import { expect, type Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import path from 'node:path';

export const authFile = path.join(process.cwd(), 'test-results', '.auth', 'admin.json');
export const participantAuthFile = path.join(process.cwd(), 'test-results', '.auth', 'participant.json');
export const expertAuthFile = path.join(process.cwd(), 'test-results', '.auth', 'expert.json');

const adminUser = process.env.PLAYWRIGHT_ADMIN_USER || 'admin';
const initialAdminPassword = process.env.PLAYWRIGHT_ADMIN_PASSWORD || '';
const changedAdminPassword = process.env.PLAYWRIGHT_NEW_PASSWORD || 'E2eAdmin123!';
const captchaCode = process.env.PLAYWRIGHT_CAPTCHA_CODE || '';

type LoginOutcome = 'logged-in' | 'forced-change' | 'stayed-on-login';

type AuthenticateUserOptions = {
  username: string;
  initialPassword?: string;
  resolvedPassword: string;
  landingPath: string;
  storageStatePath: string;
};

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

const waitForApiReady = async (page: Page) => {
  await expect
    .poll(
      async () => {
        try {
          const response = await page.request.get('/api/health', { timeout: 5_000 });
          if (!response.ok()) {
            return `HTTP ${response.status()}`;
          }
          const payload = (await response.json()) as { data?: { status?: string }; status?: string };
          return payload.data?.status || payload.status || 'UNKNOWN';
        } catch {
          return 'UNAVAILABLE';
        }
      },
      {
        message: 'Wait for the application API proxy and backend to become ready before logging in.',
        timeout: 60_000,
        intervals: [250, 500, 1_000, 2_000],
      },
    )
    .toBe('UP');
};

const waitForLoginOutcome = async (page: Page): Promise<LoginOutcome> => {
  const forcedPasswordInput = page.getByTestId('forced-password-new-input');
  return Promise.race([
    forcedPasswordInput.waitFor({ state: 'visible', timeout: 10_000 }).then((): LoginOutcome => 'forced-change'),
    page.waitForURL((url) => !url.pathname.startsWith('/user/login'), { timeout: 10_000 }).then((): LoginOutcome => 'logged-in'),
  ]).catch((): LoginOutcome => 'stayed-on-login');
};

const submitPasswordLogin = async (page: Page, username: string, password: string): Promise<LoginOutcome> => {
  await waitForApiReady(page);
  await page.goto('/user/login');
  await ensurePasswordMode(page);
  await expect(page.getByTestId('login-account-input')).toBeVisible();
  await page.getByTestId('login-account-input').fill(username);
  await page.getByTestId('login-password-input').fill(password);
  await acceptAgreementIfPresent(page);
  await fillCaptchaIfPresent(page);
  await page.getByTestId('login-submit-button').click();
  return waitForLoginOutcome(page);
};

const completeForcedPasswordChange = async (page: Page, currentPassword: string, resolvedPassword: string) => {
  await page.getByTestId('forced-password-current-input').fill(currentPassword);
  await page.getByTestId('forced-password-new-input').fill(resolvedPassword);
  await page.getByTestId('forced-password-confirm-input').fill(resolvedPassword);
  await page.getByTestId('forced-password-submit').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/user/login'), { timeout: 20_000 });
};

const completeForcedPasswordChangeIfPresent = async (page: Page, currentPassword: string, resolvedPassword: string) => {
  const forcedPasswordInput = page.getByTestId('forced-password-new-input');
  if (await forcedPasswordInput.isVisible().catch(() => false)) {
    await completeForcedPasswordChange(page, currentPassword, resolvedPassword);
  }
};

export const loginWithPassword = async (page: Page, username: string, password: string) => {
  const outcome = await submitPasswordLogin(page, username, password);
  if (outcome !== 'logged-in') {
    throw new Error(`Unable to authenticate Playwright user ${username}.`);
  }
  await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
};

export const authenticateUser = async (page: Page, options: AuthenticateUserOptions) => {
  let passwordUsed = options.initialPassword || options.resolvedPassword;
  let outcome = await submitPasswordLogin(page, options.username, passwordUsed);

  if (outcome === 'stayed-on-login' && passwordUsed !== options.resolvedPassword) {
    passwordUsed = options.resolvedPassword;
    outcome = await submitPasswordLogin(page, options.username, options.resolvedPassword);
  }

  if (outcome === 'forced-change') {
    await completeForcedPasswordChange(page, passwordUsed, options.resolvedPassword);
    passwordUsed = options.resolvedPassword;
  } else if (outcome !== 'logged-in') {
    throw new Error(`Unable to authenticate Playwright user ${options.username}.`);
  }

  await page.goto(options.landingPath);
  await completeForcedPasswordChangeIfPresent(page, passwordUsed, options.resolvedPassword);
  await expect(page).toHaveURL(new RegExp(options.landingPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  await expect(page.getByTestId('top-user-menu-button')).toBeVisible();
  mkdirSync(path.dirname(options.storageStatePath), { recursive: true });
  await page.context().storageState({ path: options.storageStatePath });
};

export const authenticateAdmin = async (page: Page) => {
  await authenticateUser(page, {
    username: adminUser,
    initialPassword: initialAdminPassword,
    resolvedPassword: changedAdminPassword,
    landingPath: '/dashboard/home',
    storageStatePath: authFile,
  });
};
