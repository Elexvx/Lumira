#!/usr/bin/env node

import { spawnSync } from "node:child_process";

const password = process.env.MYSQL_PASSWORD || "";
const args = [
  "bin/ddd-mysql-docker-exec-wrapper.mjs",
  "--batch",
  "--raw",
  "--skip-column-names",
  `--host=${process.env.MYSQL_HOST || "mysql"}`,
  `--port=${process.env.MYSQL_PORT || "3306"}`,
  `--user=${process.env.MYSQL_USER || "root"}`,
  `--password=${password}`,
  process.env.DDD_EXPLAIN_DATABASE || process.env.MYSQL_DATABASE || "saas",
  "--execute",
  "SELECT 1",
];

const result = spawnSync(process.execPath, args, {
  encoding: "utf8",
  windowsHide: true,
});

console.log(JSON.stringify({
  status: result.status === 0 ? "PASS" : "FAIL",
  exitCode: result.status,
  signal: result.signal || null,
  error: result.error?.message || null,
  stdout: (result.stdout || "").trim(),
  stderr: (result.stderr || "").trim().replaceAll(password, "<redacted>"),
}, null, 2));

process.exit(result.status === 0 ? 0 : 1);
