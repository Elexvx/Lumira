#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(import.meta.dirname, "..");
const args = new Set(process.argv.slice(2));
const scriptPath = path.join(repoRoot, "artifacts", "ddd", "release", "release-final-owner-queue-env-init.sh");
const checkOnly = args.has("--check");

function printHelp() {
  console.log(`DDD release env initializer

Usage:
  node scripts/ddd-release-env-init.mjs [--check]

Runs artifacts/ddd/release/release-final-owner-queue-env-init.sh through a temporary LF-normalized copy.
This keeps the generated bash initializer usable from Windows worktrees where .sh files may be checked out with CRLF.

Options:
  --check                                  Check bash/script/target readiness without creating files.
  --help, -h                               Show this help.

Environment:
  DDD_FINAL_OWNER_QUEUE_ENV_TARGET        Override initialized env file target.
  DDD_RELEASE_ENV_FILE                    Default env file target when DDD_FINAL_OWNER_QUEUE_ENV_TARGET is unset.
  DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT  Override receipt path.
  DDD_FINAL_OWNER_QUEUE_ENV_FORCE         Allow overwriting the target only after backing it up.
  DDD_BASH_COMMAND                        Override bash executable; default is bash.
`);
}

if (args.has("--help") || args.has("-h")) {
  printHelp();
  process.exit(0);
}

if (!fs.existsSync(scriptPath)) {
  console.error(`[ddd-release-env-init] missing initializer: ${path.relative(repoRoot, scriptPath).replaceAll("\\", "/")}`);
  process.exit(1);
}

const tmpDir = path.join(repoRoot, "tmp");
const tmpScriptPath = path.join(tmpDir, `ddd-release-env-init-${process.pid}-${Date.now()}.sh`);
const tmpShimDir = path.join(tmpDir, `ddd-release-env-init-bin-${process.pid}-${Date.now()}`);
const tmpScriptRef = path.relative(repoRoot, tmpScriptPath).replaceAll("\\", "/");
const bashCommand = process.env.DDD_BASH_COMMAND || "bash";
const defaultReceiptPath = path.join(repoRoot, "artifacts", "ddd", "release", "release-final-owner-queue-env-init-receipt.json");
const defaultTargetPath = path.join(repoRoot, process.env.DDD_RELEASE_ENV_FILE || ".env.release.local");

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

function fallbackBashPath(value) {
  const normalized = String(value).replaceAll("\\", "/");
  const driveMatch = normalized.match(/^([A-Za-z]):\/(.*)$/);
  if (driveMatch) {
    const drive = driveMatch[1].toLowerCase();
    return isWslBash() ? `/mnt/${drive}/${driveMatch[2]}` : `/${drive}/${driveMatch[2]}`;
  }
  return normalized;
}

let wslBash;
function isWslBash() {
  if (wslBash !== undefined) return wslBash;
  const result = spawnSync(bashCommand, ["-lc", "uname -r"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  wslBash = result.status === 0 && /microsoft|wsl/i.test(result.stdout);
  return wslBash;
}

function toBashPath(value) {
  if (!value || !path.isAbsolute(value)) return value;
  if (process.platform === "win32" && /^[A-Za-z]:[\\/]/.test(value)) {
    return fallbackBashPath(value);
  }
  const result = spawnSync(bashCommand, ["-lc", "if command -v wslpath >/dev/null 2>&1; then wslpath -u \"$DDD_PATH_TO_CONVERT\"; elif command -v cygpath >/dev/null 2>&1; then cygpath -u \"$DDD_PATH_TO_CONVERT\"; else exit 1; fi"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      DDD_PATH_TO_CONVERT: value,
    },
  });
  if (result.status === 0 && result.stdout.trim()) {
    return result.stdout.trim();
  }
  return fallbackBashPath(value);
}

function exportLine(key, value, { pathLike = false } = {}) {
  if (!value) return null;
  const normalizedValue = pathLike ? toBashPath(path.resolve(repoRoot, value)) : value;
  return `export ${key}=${shellQuote(normalizedValue)}`;
}

function envInitReceiptPath() {
  const value = process.env.DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT;
  return value ? path.resolve(repoRoot, value) : defaultReceiptPath;
}

function envInitTargetPath() {
  const value = process.env.DDD_FINAL_OWNER_QUEUE_ENV_TARGET || process.env.DDD_RELEASE_ENV_FILE;
  return value ? path.resolve(repoRoot, value) : defaultTargetPath;
}

function reportReceiptDiagnostics(receiptPath) {
  if (!fs.existsSync(receiptPath)) return;
  let receipt;
  try {
    receipt = JSON.parse(fs.readFileSync(receiptPath, "utf8"));
  } catch {
    return;
  }
  if (receipt.permissionSafe === false) {
    const target = receipt.targetPath || process.env.DDD_FINAL_OWNER_QUEUE_ENV_TARGET || process.env.DDD_RELEASE_ENV_FILE || ".env.release.local";
    console.error(`[ddd-release-env-init] warning: initialized env target is not owner-only (mode=${receipt.targetModeOctal || "unknown"}): ${target}`);
    console.error("[ddd-release-env-init] warning: use this file as a local fill template only unless the target filesystem enforces chmod 600.");
  }
}

function runCheck() {
  const bashVersion = spawnSync(bashCommand, ["--version"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  const targetPath = envInitTargetPath();
  const targetExists = fs.existsSync(targetPath);
  const forceEnabled = ["1", "true"].includes(String(process.env.DDD_FINAL_OWNER_QUEUE_ENV_FORCE || "").toLowerCase());
  const issues = [];
  if (bashVersion.status !== 0) {
    issues.push(`bash unavailable: ${bashVersion.error?.message || bashVersion.stderr.trim() || bashVersion.status}`);
  }
  if (!fs.existsSync(scriptPath)) {
    issues.push(`missing initializer: ${path.relative(repoRoot, scriptPath).replaceAll("\\", "/")}`);
  }
  if (targetExists && !forceEnabled) {
    issues.push(`target exists and force is disabled: ${path.relative(repoRoot, targetPath).replaceAll("\\", "/")}`);
  }
  const report = {
    status: issues.length === 0 ? "PASS" : "BLOCKED",
    bashCommand,
    bashKind: bashVersion.status === 0 ? (isWslBash() ? "wsl" : "bash") : "unavailable",
    initializer: path.relative(repoRoot, scriptPath).replaceAll("\\", "/"),
    initializerExists: fs.existsSync(scriptPath),
    target: path.relative(repoRoot, targetPath).replaceAll("\\", "/"),
    targetExists,
    forceEnabled,
    receipt: path.relative(repoRoot, envInitReceiptPath()).replaceAll("\\", "/"),
    willWriteFiles: false,
    nextCommand: "node scripts/ddd-release-env-init.mjs",
    issues,
  };
  console.log(JSON.stringify(report, null, 2));
  process.exit(issues.length === 0 ? 0 : 1);
}

if (checkOnly) {
  runCheck();
}

try {
  fs.mkdirSync(tmpDir, { recursive: true });
  fs.mkdirSync(tmpShimDir, { recursive: true });
  const nodeShimPath = path.join(tmpShimDir, "node");
  fs.writeFileSync(
    nodeShimPath,
    [
      "#!/usr/bin/env bash",
      "set -euo pipefail",
      "converted_args=()",
      "for arg in \"$@\"; do",
      "  case \"$arg\" in",
      "    /mnt/[A-Za-z]/*)",
      "      drive=\"${arg#/mnt/}\"",
      "      drive=\"${drive%%/*}\"",
      "      rest=\"${arg#/mnt/${drive}/}\"",
      "      drive_upper=$(printf '%s' \"$drive\" | tr '[:lower:]' '[:upper:]')",
      "      rest_windows=${rest//\\//\\\\}",
      "      converted_args+=(\"${drive_upper}:\\\\${rest_windows}\")",
      "      ;;",
      "    /[A-Za-z]/*)",
      "      drive=\"${arg#/}\"",
      "      drive=\"${drive%%/*}\"",
      "      rest=\"${arg#/${drive}/}\"",
      "      drive_upper=$(printf '%s' \"$drive\" | tr '[:lower:]' '[:upper:]')",
      "      rest_windows=${rest//\\//\\\\}",
      "      converted_args+=(\"${drive_upper}:\\\\${rest_windows}\")",
      "      ;;",
      "    *)",
      "      converted_args+=(\"$arg\")",
      "      ;;",
      "  esac",
      "done",
      `exec ${shellQuote(toBashPath(process.execPath))} "\${converted_args[@]}"`,
      "",
    ].join("\n"),
    { mode: 0o700 },
  );
  const prelude = [
    `export PATH=${shellQuote(toBashPath(tmpShimDir))}:"$PATH"`,
    exportLine("DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE", process.env.DDD_FINAL_OWNER_QUEUE_ENV_TEMPLATE, { pathLike: true }),
    exportLine("DDD_FINAL_OWNER_QUEUE_ENV_TARGET", process.env.DDD_FINAL_OWNER_QUEUE_ENV_TARGET, { pathLike: true }),
    exportLine("DDD_RELEASE_ENV_FILE", process.env.DDD_RELEASE_ENV_FILE, { pathLike: true }),
    exportLine("DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT", process.env.DDD_FINAL_OWNER_QUEUE_ENV_INIT_RECEIPT, { pathLike: true }),
    exportLine("DDD_FINAL_OWNER_QUEUE_ENV_FORCE", process.env.DDD_FINAL_OWNER_QUEUE_ENV_FORCE),
    "",
  ].filter((line) => line !== null).join("\n");
  const normalizedScript = `${prelude}${fs.readFileSync(scriptPath, "utf8").replace(/\r\n?/g, "\n")}`;
  fs.writeFileSync(tmpScriptPath, normalizedScript, { mode: 0o700 });

  const result = spawnSync(bashCommand, [tmpScriptRef], {
    cwd: repoRoot,
    env: process.env,
    encoding: "utf8",
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);

  if (result.error) {
    console.error(`[ddd-release-env-init] failed to start ${bashCommand}: ${result.error.message}`);
    process.exitCode = 1;
  } else {
    reportReceiptDiagnostics(envInitReceiptPath());
    process.exitCode = result.status ?? 1;
  }
} finally {
  fs.rmSync(tmpScriptPath, { force: true });
  fs.rmSync(tmpShimDir, { recursive: true, force: true });
}
