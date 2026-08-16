import { expect, test } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

const success = (data: unknown) => ({
  code: '0',
  message: 'success',
  data,
  requestId: 'e2e-certificate-award',
});

test('cross-competition certificate generation is no longer exposed in data management', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('umi_locale', 'zh-CN');
    window.localStorage.setItem('auth_tokens', JSON.stringify({
      accessToken: 'e2e-certificate-access-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      expiresAt: Date.now() + 3_600_000,
    }));
  });

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v2/auth/bootstrap') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(success({
          currentUser: {
            userId: 1,
            username: 'admin',
            nickname: '端到端管理员',
            locale: 'zh-CN',
            sessionId: 'e2e-certificate-session',
            sessionVersion: 1,
            permissionsVersion: 'e2e-certificate-permissions-v1',
            permissions: ['*'],
            roleIds: [1],
            requiresPasswordChange: false,
            defaultHomePath: '/dashboard/home',
          },
          securitySettings: {
            idleTimeoutSeconds: 1800,
            accessTokenExpireSeconds: 1800,
            refreshTokenExpireSeconds: 604800,
            allowMultiDeviceLogin: true,
            captchaEnabled: false,
          },
          menuTree: [{
            id: 1100,
            menuCode: 'data.management.root',
            name: '数据管理',
            path: '/data-management',
            children: [
              { id: 1080, menuCode: 'certificate.templates', name: '证书模板', path: '/certificates/templates' },
              { id: 1082, menuCode: 'certificate.records', name: '全局证书记录', path: '/certificates/records' },
            ],
          }],
          availablePlugins: [],
          runtimeAppearanceSettings: {
            brandingSettings: {
              websiteName: 'Lumira E2E',
              companyName: 'Lumira E2E',
              copyrightStartYear: 2026,
              maintenanceModeEnabled: false,
            },
            watermarkSettings: { enabled: false },
            floatingWindowSettings: { apiDocsQrEnabled: false },
          },
        })),
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success(route.request().method() === 'GET' ? {} : null)),
    });
  });

  await page.goto('/data-management');
  await expect(page.getByText('跨赛事证书生成')).toHaveCount(0);
  await expect(page.getByText('证书模板')).toHaveCount(1);
  await expect(page.getByText('全局证书记录')).toHaveCount(1);
});
