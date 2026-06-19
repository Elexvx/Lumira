import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

export const authFile = path.join(process.cwd(), 'test-results', '.auth', 'admin.json');

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:8000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  outputDir: 'test-results',
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },
    {
      name: 'chromium-auth',
      testMatch: /.*auth\.spec\.ts/,
      grep: /@auth|@smoke/,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: undefined,
      },
    },
    {
      name: 'chromium',
      testIgnore: [/.*\.setup\.ts/, /.*auth\.spec\.ts/],
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: authFile,
      },
    },
  ],
});
