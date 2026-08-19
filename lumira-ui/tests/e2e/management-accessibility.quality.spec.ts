import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const pages = [
  { path: '/dashboard/home', label: 'dashboard' },
  { path: '/competitions/management', label: 'competition management' },
  { path: '/payments/management', label: 'payment management' },
];

for (const pageCase of pages) {
  test(`${pageCase.label} has no serious or critical accessibility violations @quality`, async ({ page }, testInfo) => {
    await page.goto(pageCase.path);
    await expect(page).not.toHaveURL(/\/user\/login/);
    const content = page.locator('.ant-pro-layout-content').first();
    await expect(content).toBeVisible();
    await expect(content.locator('.ant-spin-spinning')).toHaveCount(0);

    const results = await new AxeBuilder({ page })
      .include('.ant-pro-layout-content')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const blocking = results.violations.filter((violation) =>
      violation.impact === 'serious' || violation.impact === 'critical');

    await testInfo.attach(`${pageCase.label}-axe.json`, {
      body: Buffer.from(JSON.stringify({ violations: blocking }, null, 2)),
      contentType: 'application/json',
    });
    expect(blocking, blocking.map((item) => `${item.id}: ${item.help}`).join('\n')).toEqual([]);
  });
}
