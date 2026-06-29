#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';

const repoRoot = path.resolve(import.meta.dirname, '..');
const args = new Set(process.argv.slice(2));
const includeIgnored = args.has('--include-ignored');

const allowedSqlFiles = new Set([
  'lumira-backend/sql/saas.sql',
  'lumira-backend/services/lumira-plugin/src/main/resources/builtin-plugins/sensitive-words/migrations/up/V1__sys_sensitive_word.sql',
  'lumira-backend/services/lumira-plugin/src/main/resources/builtin-plugins/sensitive-words/migrations/down/V1__sys_sensitive_word.sql',
]);

const prohibitedPathPrefixes = [
  'runtime-logs/',
  'artifacts/db-backups/',
  'deploy/.backup/',
  'tmp/db-backups/',
];

const dumpPatterns = [
  { name: 'mysql-dump-header', pattern: /MySQL dump|Dump completed|Dumping data for table|LOCK TABLES/i },
];

const hashPatterns = [
  { name: 'bcrypt-hash', pattern: /\$2[aby]\$\d{2}\$/ },
  {
    name: 'xxl-job-default-sha256',
    pattern: /8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92|e10adc3949ba59abbe56e057f20f883e/i,
  },
];

function runGit(argsForGit) {
  const result = spawnSync('git', argsForGit, {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.status !== 0) {
    process.stderr.write(result.stderr || result.stdout);
    process.exit(result.status || 1);
  }
  return result.stdout.split('\0').filter(Boolean);
}

function toRepoPath(filePath) {
  return filePath.replaceAll('\\', '/').replace(/^\.\//, '');
}

function collectSqlFiles() {
  const tracked = runGit(['ls-files', '-z', '--', '*.sql']).map(toRepoPath);
  const visibleUntracked = runGit(['ls-files', '-z', '--others', '--exclude-standard', '--', '*.sql']).map(toRepoPath);
  const files = new Set([...tracked, ...visibleUntracked]);

  if (includeIgnored) {
    const ignored = runGit(['ls-files', '-z', '--others', '--ignored', '--exclude-standard', '--', '*.sql']).map(toRepoPath);
    for (const file of ignored) {
      files.add(file);
    }
  }

  return [...files]
    .filter((file) => existsSync(path.join(repoRoot, ...file.split('/'))))
    .sort();
}

function isProhibitedPath(repoPath) {
  return prohibitedPathPrefixes.some((prefix) => repoPath.startsWith(prefix));
}

function scanFile(repoPath) {
  const absolutePath = path.join(repoRoot, ...repoPath.split('/'));
  if (!existsSync(absolutePath)) {
    return [];
  }

  const findings = [];
  const content = readFileSync(absolutePath, 'utf8');
  const allowed = allowedSqlFiles.has(repoPath);

  if (isProhibitedPath(repoPath)) {
    findings.push({ file: repoPath, reason: 'prohibited-sql-dump-path' });
  }

  for (const check of dumpPatterns) {
    if (check.pattern.test(content) && !allowed) {
      findings.push({ file: repoPath, reason: check.name });
    }
  }

  for (const check of hashPatterns) {
    if (check.pattern.test(content) && !allowed) {
      findings.push({ file: repoPath, reason: check.name });
    }
  }

  return findings;
}

const sqlFiles = collectSqlFiles();
const findings = sqlFiles.flatMap(scanFile);

const summary = {
  includeIgnored,
  sqlFiles: sqlFiles.length,
  findings: findings.length,
  allowedSqlFiles: allowedSqlFiles.size,
};

console.log(JSON.stringify(summary, null, 2));

if (findings.length > 0) {
  for (const finding of findings) {
    console.error(`[sensitive-sql-dumps] ${finding.file}: ${finding.reason}`);
  }
  process.exit(1);
}
