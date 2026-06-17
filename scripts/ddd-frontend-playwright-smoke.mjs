#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const outputDir = process.env.DDD_FRONTEND_SMOKE_DIR
  ? path.resolve(process.env.DDD_FRONTEND_SMOKE_DIR)
  : path.join(repoRoot, "artifacts", "ddd", "frontend");
const outputFile = process.env.DDD_FRONTEND_PLAYWRIGHT_JSON
  ? path.resolve(process.env.DDD_FRONTEND_PLAYWRIGHT_JSON)
  : path.join(outputDir, "playwright-smoke-results.json");
const grep = process.env.DDD_FRONTEND_PLAYWRIGHT_GREP || "@smoke";
const timeoutMs = process.env.DDD_FRONTEND_PLAYWRIGHT_TIMEOUT_MS || "";
const baseUrl = process.env.PLAYWRIGHT_BASE_URL || "";

function writeFailureReport(message, result = null) {
  const report = {
    config: {
      projects: [
        {
          name: "chromium",
          use: {
            baseURL: baseUrl || null,
          },
        },
      ],
    },
    suites: [
      {
        title: "ddd frontend smoke launcher",
        specs: [
          {
            title: "playwright smoke command completed",
            tests: [
              {
                projectName: "launcher",
                status: "failed",
                results: [
                  {
                    status: "failed",
                    duration: 0,
                    errors: [
                      {
                        message,
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
    errors: [
      {
        message,
        exitCode: result?.status ?? null,
        signal: result?.signal ?? null,
      },
    ],
  };
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, `${JSON.stringify(report, null, 2)}\n`);
}

fs.mkdirSync(path.dirname(outputFile), { recursive: true });

const args = [
  "pnpm",
  "--dir",
  "frontend",
  "exec",
  "playwright",
  "test",
  "--grep",
  grep,
  "--reporter=json",
];

if (timeoutMs) {
  args.push("--timeout", timeoutMs);
}

const result = spawnSync("corepack", args, {
  cwd: repoRoot,
  encoding: "utf8",
  maxBuffer: 50 * 1024 * 1024,
  env: {
    ...process.env,
    PLAYWRIGHT_BASE_URL: baseUrl || process.env.PLAYWRIGHT_BASE_URL,
  },
});

if (result.error) {
  writeFailureReport(`failed to start Playwright smoke: ${result.error.message}`, result);
  console.error(`[ddd-frontend-playwright-smoke] failed to start Playwright smoke: ${result.error.message}`);
  process.exit(1);
}

const stdout = String(result.stdout || "").trim();
if (!stdout) {
  writeFailureReport("Playwright did not emit JSON report on stdout", result);
  console.error("[ddd-frontend-playwright-smoke] Playwright did not emit JSON report on stdout");
  process.exit(1);
}

try {
  JSON.parse(stdout);
} catch (error) {
  writeFailureReport(`Playwright emitted invalid JSON: ${error.message}`, result);
  console.error(`[ddd-frontend-playwright-smoke] Playwright emitted invalid JSON: ${error.message}`);
  process.exit(1);
}

fs.writeFileSync(outputFile, `${stdout}\n`);

if (result.status !== 0) {
  console.error(`[ddd-frontend-playwright-smoke] Playwright smoke failed; exitCode=${result.status}; report=${outputFile}`);
  process.exit(result.status || 1);
}

console.log(`[ddd-frontend-playwright-smoke] Playwright smoke passed; grep=${grep}; report=${outputFile}`);
