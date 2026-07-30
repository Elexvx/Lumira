import { expect, test, type Page } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

const success = (data: unknown) => ({
  code: '0',
  message: 'success',
  data,
  requestId: 'e2e-certificate-award',
});

const securitySettings = {
  idleTimeoutSeconds: 1800,
  accessTokenExpireSeconds: 1800,
  refreshTokenExpireSeconds: 604800,
  allowMultiDeviceLogin: true,
  captchaEnabled: false,
  captchaType: 'IMAGE',
  loginDefenseWindowMinutes: 5,
  loginMaxValidationAttempts: 100,
  loginMaxFailureCount: 10,
  verificationCodeExpireSeconds: 300,
  verificationCodeCooldownSeconds: 60,
  passwordMinLength: 6,
  passwordRequireUppercase: false,
  passwordRequireLowercase: false,
  passwordRequireSpecialCharacter: false,
  passwordAllowConsecutiveCharacters: true,
};

const authBootstrap = {
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
  securitySettings,
  menuTree: [{
    id: 1,
    menuCode: 'certificate.root',
    name: '证书管理',
    path: '/certificates',
    children: [{
      id: 2,
      menuCode: 'certificate.generate',
      name: '证书生成',
      path: '/certificates/generate',
    }],
  }],
  availablePlugins: [],
  runtimeAppearanceSettings: {
    brandingSettings: {
      websiteName: 'Lumira E2E',
      companyName: 'Lumira E2E',
      copyrightStartYear: 2026,
      maintenanceModeEnabled: false,
    },
    watermarkSettings: {
      enabled: false,
      mode: 'TEXT',
      textLines: [],
      imageUrl: '',
      fontColor: 'rgba(0,0,0,0.15)',
      fontSize: 14,
      fontWeight: 'normal',
      rotate: -22,
      gapX: 100,
      gapY: 100,
      offsetX: 0,
      offsetY: 0,
      zIndex: 9,
      opacity: 0.15,
    },
    floatingWindowSettings: {
      apiDocsQrEnabled: false,
      apiDocsQrTitle: '',
      apiDocsQrImageUrl: '',
    },
  },
};

const chooseSelectOption = async (page: Page, label: string, optionText: string) => {
  await page.getByRole('combobox', { name: label }).click();
  const option = page
    .locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: optionText });
  await expect(option).toHaveCount(1);
  await option.click();
};

const awardGrant = (issued: boolean) => ({
  id: 501,
  publicationId: 401,
  publicationVersion: 2,
  reviewBatchId: 301,
  competitionId: 101,
  stageId: 201,
  candidateId: 601,
  registrationId: 701,
  projectId: 801,
  teamId: 901,
  userId: 1001,
  recipientName: '星河创新团队',
  competitionTitle: '2026 创新应用大赛',
  projectName: '智能评审助手',
  teamName: '星河创新团队',
  awardName: '一等奖',
  rankNo: 1,
  decision: 'PASS',
  status: issued ? 'ISSUED' : 'GRANTED',
  certificateRecordId: issued ? 1101 : undefined,
  grantedAt: '2026-07-31T00:00:00',
});

test('published result can be selected, awarded idempotently, and issued @smoke', async ({ page }) => {
  let granted = false;
  let issued = false;
  const grantPayloads: unknown[] = [];

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
        body: JSON.stringify(success(authBootstrap)),
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success(route.request().method() === 'GET' ? {} : null)),
    });
  });
  await page.route('**/api/v2/aiadc/certificate-templates?**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(success({
      records: [{
        id: 11,
        templateCode: 'AWARD-2026',
        templateName: '获奖证书模板',
        templateType: 'DESIGN',
        sceneType: 'COMPETITION_AWARD',
        latestVersion: 1,
        status: 'PUBLISHED',
      }],
      total: 1,
      pageNo: 1,
      pageSize: 100,
    })),
  }));
  await page.route('**/api/v2/aiadc/certificate-templates/11/versions', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(success([{
      id: 12,
      templateId: 11,
      version: 1,
      pageWidth: 3508,
      pageHeight: 2480,
      orientation: 'LANDSCAPE',
      unit: 'PX',
      dpi: 300,
      canvasJson: '{}',
      status: 'PUBLISHED',
    }])),
  }));
  await page.route('**/api/v2/aiadc/certificate-award-sources', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(success([{
      reviewBatchId: 301,
      batchNo: 'REVIEW-2026-FINAL',
      batchName: '决赛评审批次',
      competitionId: 101,
      competitionTitle: '2026 创新应用大赛',
      stageId: 201,
      stageName: '决赛',
      candidateCount: 10,
      publicationVersion: 2,
      publishedAt: '2026-07-30T20:00:00',
      grantCount: granted ? 1 : 0,
      issuedCount: issued ? 1 : 0,
    }])),
  }));
  await page.route('**/api/v2/aiadc/certificate-awards**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'POST' && url.pathname.endsWith('/grant')) {
      grantPayloads.push(request.postDataJSON());
      granted = true;
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(success([awardGrant(issued)])),
      });
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success(granted ? [awardGrant(issued)] : [])),
    });
  });
  await page.route('**/api/v2/aiadc/certificate-batches/from-awards', async (route) => {
    expect(route.request().postDataJSON()).toMatchObject({
      templateId: 11,
      templateVersionId: 12,
      grantIds: [501],
    });
    issued = true;
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(success({
        batch: {
          id: 1201,
          batchNo: 'CERT-2026-FINAL',
          batchName: '2026 创新应用大赛 - 获奖证书',
          templateId: 11,
          templateVersionId: 12,
          sourceRefId: 301,
          totalCount: 1,
          successCount: 1,
          failedCount: 0,
          status: 'COMPLETED',
        },
        records: [{
          id: 1101,
          certificateNo: 'CERT-2026-0001',
          verificationCode: '123456',
          publicToken: 'public-token-1101',
          templateId: 11,
          templateVersionId: 12,
          recipientName: '星河创新团队',
          recipientType: 'TEAM',
          competitionTitle: '2026 创新应用大赛',
          projectName: '智能评审助手',
          awardName: '一等奖',
          certificateFileUrl: '/uploads/certificates/1101.png',
          status: 'ISSUED',
        }],
      })),
    });
  });

  await page.goto('/certificates/generate');
  await expect(page.getByText('评审结果授奖与制证')).toBeVisible();

  await chooseSelectOption(page, '证书模板', '获奖证书模板');
  await chooseSelectOption(page, '模板版本', 'v1');

  await chooseSelectOption(page, '赛事', '2026 创新应用大赛');
  await chooseSelectOption(page, '阶段', '决赛');
  await chooseSelectOption(page, '已发布评审批次', '决赛评审批次');

  await expect(page.locator('input[value="一等奖"]')).toBeVisible();
  await expect(page.locator('input[value="二等奖"]')).toBeVisible();
  await expect(page.locator('input[value="三等奖"]')).toBeVisible();

  await page.getByRole('button', { name: '应用规则并加载授奖记录' }).click();
  await expect(page.getByText('星河创新团队')).toBeVisible();
  await expect(page.getByText(/已新增 1 条授奖记录/)).toBeVisible();
  expect(grantPayloads[0]).toEqual({
    reviewBatchId: 301,
    rules: [
      { awardName: '一等奖', minRank: 1, maxRank: 1 },
      { awardName: '二等奖', minRank: 2, maxRank: 3 },
      { awardName: '三等奖', minRank: 4, maxRank: 10 },
    ],
  });

  await page.getByRole('button', { name: '应用规则并加载授奖记录' }).click();
  await expect(page.getByText(/未重复建档/)).toBeVisible();

  await page.getByRole('button', { name: '为所选授奖记录生成证书' }).click();
  await expect(page.getByText(/已从授奖记录生成 1 张证书/)).toBeVisible();
  await expect(page.getByText('已制证').last()).toBeVisible();
  await expect(page.getByText(/待制证 0 条，已制证 1 条/)).toBeVisible();
});
