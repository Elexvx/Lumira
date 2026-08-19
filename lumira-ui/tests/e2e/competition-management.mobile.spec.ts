import { expect, test } from '@playwright/test';

test('competition management keeps the four essential columns at 390px @mobile', async ({ page }) => {
  await page.goto('/competitions/management');

  await expect(page).not.toHaveURL(/\/user\/login/);
  const table = page.locator('.ant-table').first();
  await expect(table).toBeVisible();
  await expect(table.locator('.ant-spin-spinning')).toHaveCount(0);

  const visibleHeaders = await table.locator('.ant-table-thead th').evaluateAll((cells) => cells
    .filter((cell) => {
      const style = window.getComputedStyle(cell);
      const rect = cell.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0;
    })
    .map((cell) => (cell.textContent || '').trim())
    .filter(Boolean));

  expect(visibleHeaders).toEqual(expect.arrayContaining(['编号', '赛事', '状态', '操作']));
  expect(visibleHeaders).not.toEqual(expect.arrayContaining(['类别', '级别', '收费', '组织者']));

  const layoutOverflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(layoutOverflow).toBeLessThanOrEqual(1);

  const tableScroll = table.locator('.ant-table-content').first();
  await expect(tableScroll).toBeVisible();
  const scrollWidths = await tableScroll.evaluate((element) => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth,
  }));
  expect(scrollWidths.scrollWidth).toBeGreaterThan(scrollWidths.clientWidth);
});
