import { createHash } from 'node:crypto';
import {
  closeSync,
  existsSync,
  lstatSync,
  openSync,
  readFileSync,
  readSync,
  realpathSync,
  statSync,
} from 'node:fs';
import path from 'node:path';

const SHA256_PATTERN = /^[a-f0-9]{64}$/u;

function requireRegularFile(file, label) {
  if (!existsSync(file)) throw new Error(`${label} is missing: ${file}`);
  const info = lstatSync(file);
  if (info.isSymbolicLink() || !info.isFile()) {
    throw new Error(`${label} must be a regular non-symlink file: ${file}`);
  }
  return info;
}

function resolveContainedFile(directory, relativePath, label) {
  if (typeof relativePath !== 'string' || !relativePath.trim() || path.isAbsolute(relativePath)) {
    throw new Error(`${label} must be a non-empty relative path.`);
  }
  const root = realpathSync(directory);
  const candidate = path.resolve(root, relativePath);
  const prefix = `${root}${path.sep}`;
  if (candidate === root || !candidate.startsWith(prefix)) {
    throw new Error(`${label} escapes the backup directory.`);
  }
  requireRegularFile(candidate, label);
  const realCandidate = realpathSync(candidate);
  if (!realCandidate.startsWith(prefix)) throw new Error(`${label} resolves outside the backup directory.`);
  return realCandidate;
}

function sha256File(file) {
  const hash = createHash('sha256');
  const descriptor = openSync(file, 'r');
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  try {
    let bytesRead;
    do {
      bytesRead = readSync(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead > 0) hash.update(buffer.subarray(0, bytesRead));
    } while (bytesRead > 0);
  } finally {
    closeSync(descriptor);
  }
  return hash.digest('hex');
}

function parseChecksumLines(content) {
  const checksums = new Map();
  for (const line of String(content).split(/\r?\n/u)) {
    const match = line.match(/^([a-fA-F0-9]{64})\s+\*?(.+?)\s*$/u);
    if (!match) continue;
    checksums.set(match[2].replaceAll('\\', '/'), match[1].toLowerCase());
  }
  return checksums;
}

export function validateBackupEvidence(backupDirectory, {
  now = Date.now(),
  maxAgeMs = null,
  minimumDumpBytes = 1024,
  expectedDatabaseName = null,
} = {}) {
  if (typeof backupDirectory !== 'string' || !backupDirectory.trim()) {
    throw new Error('Backup directory was not reported by the backup command.');
  }
  const directory = path.resolve(backupDirectory);
  if (!existsSync(directory) || !statSync(directory).isDirectory()) {
    throw new Error(`Backup directory does not exist: ${directory}`);
  }
  if (existsSync(path.join(directory, 'deploy.env.snapshot'))) {
    throw new Error('Backup contains a plaintext deploy.env.snapshot and is unsafe to use.');
  }

  const manifestPath = path.join(directory, 'manifest.json');
  const checksumPath = path.join(directory, 'SHA256SUMS');
  const completionPath = path.join(directory, '.complete');
  requireRegularFile(manifestPath, 'Backup manifest');
  requireRegularFile(checksumPath, 'Backup checksum file');
  requireRegularFile(completionPath, 'Backup completion marker');

  let manifest;
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch (error) {
    throw new Error(`Backup manifest is not valid JSON: ${error.message}`);
  }
  if (manifest?.schemaVersion !== 1 || manifest?.status !== 'complete') {
    throw new Error('Backup manifest must declare schemaVersion=1 and status=complete.');
  }
  if (manifest?.secretsIncluded !== false) {
    throw new Error('Backup manifest must explicitly declare secretsIncluded=false.');
  }
  if (typeof manifest?.backupId !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/u.test(manifest.backupId)) {
    throw new Error('Backup manifest is missing backupId.');
  }
  if (typeof manifest?.databaseName !== 'string' || !/^[A-Za-z0-9_]{1,64}$/u.test(manifest.databaseName)) {
    throw new Error('Backup manifest is missing databaseName.');
  }
  if (expectedDatabaseName !== null
      && manifest.databaseName.trim().toLowerCase() !== String(expectedDatabaseName).trim().toLowerCase()) {
    throw new Error(`Backup databaseName does not match the deployment database (${expectedDatabaseName}).`);
  }
  const createdAt = Date.parse(manifest?.createdAt);
  if (!Number.isFinite(createdAt)) throw new Error('Backup manifest createdAt is invalid.');
  if (createdAt > now + 5 * 60_000) throw new Error('Backup manifest createdAt is unexpectedly in the future.');
  if (maxAgeMs !== null && now - createdAt > maxAgeMs) {
    throw new Error(`Backup evidence is older than the allowed ${maxAgeMs}ms window.`);
  }

  const relativeDumpPath = manifest?.mysql?.path;
  for (const field of ['serverVersion', 'serverUuid', 'gtidExecuted', 'binlogFile', 'binlogPosition', 'databaseVersion']) {
    if (manifest?.mysql?.[field] !== null && typeof manifest?.mysql?.[field] !== 'string') {
      throw new Error(`MySQL backup metadata ${field} must be a string or null.`);
    }
  }
  if (!Number.isSafeInteger(manifest?.mysql?.tableCount) || manifest.mysql.tableCount < 0) {
    throw new Error('MySQL backup metadata tableCount is invalid or missing.');
  }
  if (typeof manifest?.mysql?.schemaFingerprint !== 'string'
      || !SHA256_PATTERN.test(manifest.mysql.schemaFingerprint)) {
    throw new Error('MySQL backup metadata schemaFingerprint is invalid or missing.');
  }
  const dumpPath = resolveContainedFile(directory, relativeDumpPath, 'MySQL dump');
  const dumpInfo = statSync(dumpPath);
  if (!Number.isInteger(manifest?.mysql?.size) || manifest.mysql.size !== dumpInfo.size) {
    throw new Error('MySQL dump size does not match the backup manifest.');
  }
  if (dumpInfo.size < minimumDumpBytes) {
    throw new Error(`MySQL dump is unexpectedly small (${dumpInfo.size} bytes).`);
  }
  const expectedHash = String(manifest?.mysql?.sha256 || '').toLowerCase();
  if (!SHA256_PATTERN.test(expectedHash)) throw new Error('MySQL dump SHA-256 is invalid or missing.');
  const actualHash = sha256File(dumpPath);
  if (actualHash !== expectedHash) throw new Error('MySQL dump SHA-256 does not match the backup manifest.');

  const checksumLines = parseChecksumLines(readFileSync(checksumPath, 'utf8'));
  const normalizedDumpPath = String(relativeDumpPath).replaceAll('\\', '/');
  if (checksumLines.get(normalizedDumpPath) !== actualHash) {
    throw new Error('SHA256SUMS does not contain the verified MySQL dump checksum.');
  }

  return {
    backupId: manifest.backupId,
    createdAt: manifest.createdAt,
    databaseName: manifest.databaseName,
    manifestPath,
    dumpPath,
    dumpSize: dumpInfo.size,
    dumpSha256: actualHash,
    serverVersion: manifest.mysql.serverVersion ?? null,
    serverUuid: manifest.mysql.serverUuid ?? null,
    gtidExecuted: manifest.mysql.gtidExecuted ?? null,
    binlogFile: manifest.mysql.binlogFile ?? null,
    binlogPosition: manifest.mysql.binlogPosition ?? null,
    databaseVersion: manifest.mysql.databaseVersion ?? null,
    tableCount: manifest.mysql.tableCount,
    schemaFingerprint: manifest.mysql.schemaFingerprint,
  };
}
