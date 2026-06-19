#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import fs from "node:fs";

const container = process.env.DDD_MYSQL_CONTAINER || "lumira-mysql";
const dockerCli = process.env.DOCKER_CLI || "docker";
const dockerCliPs = process.env.DOCKER_CLI_PS || "";

function normalizeMysqlArgs(args) {
  const options = [];
  const positionals = [];
  let stdinSql = null;
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] === "--host=mysql" ? "--host=localhost" : args[index];
    if (arg === "--execute") {
      stdinSql = args[index + 1] || "";
      index += 1;
      continue;
    }
    if (arg.startsWith("--execute=")) {
      stdinSql = arg.slice("--execute=".length);
      continue;
    }
    if (arg.startsWith("--")) {
      options.push(arg);
    } else {
      positionals.push(arg);
    }
  }
  return {
    args: [...options, ...positionals],
    stdinSql,
  };
}

const normalized = normalizeMysqlArgs(process.argv.slice(2));
const stdinSql = normalized.stdinSql ?? (process.env.MYSQL_STDIN_FILE ? fs.readFileSync(process.env.MYSQL_STDIN_FILE, "utf8") : null);

function quoteCmdArg(arg) {
  const text = String(arg);
  if (!/[()\s^&|<>"]/u.test(text)) return text;
  return `"${text.replaceAll('"', '\\"')}"`;
}

const dockerArgs = ["exec", ...(stdinSql ? ["-i"] : []), container, "mysql", ...normalized.args];
const isWindowsCmd = process.platform === "win32" && /\.cmd$/iu.test(dockerCli);
const windowsCommand = [quoteCmdArg(dockerCli), ...dockerArgs.map(quoteCmdArg)].join(" ");
const result = dockerCliPs
  ? spawnSync("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", dockerCliPs, ...dockerArgs], {
    encoding: "utf8",
    input: stdinSql || undefined,
    stdio: [stdinSql ? "pipe" : "ignore", "pipe", "pipe"],
    windowsHide: true,
  })
  : isWindowsCmd
  ? spawnSync("cmd.exe", ["/d", "/c", windowsCommand], {
    encoding: "utf8",
    input: stdinSql || undefined,
    stdio: [stdinSql ? "pipe" : "ignore", "pipe", "pipe"],
    windowsHide: true,
  })
  : spawnSync(dockerCli, dockerArgs, {
  encoding: "utf8",
  input: stdinSql || undefined,
  stdio: [stdinSql ? "pipe" : "ignore", "pipe", "pipe"],
  windowsHide: true,
  });

if (result.stdout) process.stdout.write(result.stdout);
if (result.stderr) process.stderr.write(result.stderr);
if (result.error) process.stderr.write(`${result.error.message}\n`);
process.exit(result.error ? 1 : (typeof result.status === "number" ? result.status : 1));
