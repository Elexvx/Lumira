import { spawnSync } from 'node:child_process';
import { createHash, randomBytes, randomInt } from 'node:crypto';
import {
  chmodSync,
  closeSync,
  existsSync,
  linkSync,
  lstatSync,
  mkdirSync,
  openSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';

const UPPERCASE = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const LOWERCASE = 'abcdefghijkmnopqrstuvwxyz';
const DIGITS = '23456789';
const SPECIAL = '!@#$%*-_=+?';
const ALL_CHARACTERS = `${UPPERCASE}${LOWERCASE}${DIGITS}${SPECIAL}`;
export const LOCAL_DEFAULT_ADMIN_PASSWORD = '123456';
const BOOTSTRAP_REQUIRED_MESSAGE = 'Built-in administrator is pending initialization';
const OUTCOME_PATTERN = /Administrator credential bootstrap outcome: (INITIALIZED|ALREADY_INITIALIZED|ADOPTED_EXISTING_CREDENTIAL)/;

export function parseJdbcEndpoint(url) {
  const match = String(url ?? '').match(
    /^jdbc:mysql:\/\/(\[[^\]]+\]|[^\s,:/?#@\[\]]+)(?::(\d+))?(?=\/|\?|#|$)/i,
  );
  if (!match) {
    throw new Error('DB_URL must be a single-host jdbc:mysql:// URL.');
  }
  const port = Number(match[2] || 3306);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('DB_URL port must be an integer between 1 and 65535.');
  }
  return {
    host: match[1].replace(/^\[|\]$/g, ''),
    port,
  };
}

function pick(characters, randomIndex) {
  return characters[randomIndex(characters.length)];
}

export function generateLocalAdminPassword(length = 28, randomIndex = randomInt) {
  if (!Number.isInteger(length) || length < 12 || length > 128) {
    throw new Error('Local administrator password length must be between 12 and 128 characters.');
  }

  const characters = [
    pick(UPPERCASE, randomIndex),
    pick(LOWERCASE, randomIndex),
    pick(DIGITS, randomIndex),
    pick(SPECIAL, randomIndex),
  ];
  while (characters.length < length) {
    characters.push(pick(ALL_CHARACTERS, randomIndex));
  }
  for (let index = characters.length - 1; index > 0; index -= 1) {
    const swapIndex = randomIndex(index + 1);
    [characters[index], characters[swapIndex]] = [characters[swapIndex], characters[index]];
  }
  return characters.join('');
}

export function resolveLocalAdminSecretPath(repoRoot, databaseUrl, databaseUsername) {
  const databaseKey = createHash('sha256')
    .update(String(databaseUrl))
    .update('\0')
    .update(String(databaseUsername))
    .digest('hex')
    .slice(0, 12);
  return path.join(repoRoot, 'runtime-secrets', `local-admin-${databaseKey}.password`);
}

export function ensureLocalAdminSecret(secretPath, passwordFactory = generateLocalAdminPassword) {
  const secretDirectory = path.dirname(secretPath);
  mkdirSync(secretDirectory, { recursive: true, mode: 0o700 });
  if (process.platform !== 'win32') {
    chmodSync(secretDirectory, 0o700);
  }

  if (existsSync(secretPath)) {
    assertSafeSecretFile(secretPath);
    return { createdThisRun: false };
  }

  const password = passwordFactory();
  const candidatePath = `${secretPath}.${process.pid}.${randomBytes(8).toString('hex')}.candidate`;
  let descriptor;
  try {
    descriptor = openSync(candidatePath, 'wx', 0o600);
    writeFileSync(descriptor, password, { encoding: 'utf8' });
    closeSync(descriptor);
    descriptor = undefined;
    if (process.platform !== 'win32') {
      chmodSync(candidatePath, 0o600);
    }
    try {
      linkSync(candidatePath, secretPath);
    } catch (error) {
      if (error?.code !== 'EEXIST') {
        throw error;
      }
      assertSafeSecretFile(secretPath);
      return { createdThisRun: false };
    }
    return { createdThisRun: true };
  } finally {
    if (descriptor !== undefined) {
      closeSync(descriptor);
    }
    if (existsSync(candidatePath)) {
      unlinkSync(candidatePath);
    }
  }
}

function assertSafeSecretFile(secretPath) {
  const metadata = lstatSync(secretPath);
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`Administrator password path must be a regular file: ${secretPath}`);
  }
  if (metadata.size <= 0) {
    throw new Error(`Administrator password file is empty: ${secretPath}`);
  }
  if (process.platform !== 'win32' && (metadata.mode & 0o077) !== 0) {
    throw new Error(`Administrator password file must not be accessible by group or other users: ${secretPath}`);
  }
}

export function runLocalAdminBootstrap({
  javaCommand = 'java',
  jarPath,
  databaseEnv,
  passwordFile,
  initializationSource,
  commandRunner = spawnSync,
  inheritedEnv = process.env,
}) {
  const childEnv = { ...inheritedEnv };
  delete childEnv.LUMIRA_LOCAL_BOOTSTRAP_ADMIN_PASSWORD_FILE;
  delete childEnv.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE;
  delete childEnv.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE;
  Object.assign(childEnv, {
    DB_URL: databaseEnv.DB_URL,
    DB_USERNAME: databaseEnv.DB_USERNAME,
    DB_PASSWORD: databaseEnv.DB_PASSWORD,
  });
  if (passwordFile) {
    childEnv.LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE = passwordFile;
  }
  if (initializationSource) {
    childEnv.LUMIRA_BOOTSTRAP_ADMIN_INITIALIZATION_SOURCE = initializationSource;
  }

  const result = commandRunner(javaCommand, ['-jar', jarPath], {
    encoding: 'utf8',
    env: childEnv,
    stdio: 'pipe',
  });
  return {
    status: result.status ?? 1,
    output: `${result.stdout || ''}\n${result.stderr || ''}`.trim(),
  };
}

function parseOutcome(output) {
  return String(output || '').match(OUTCOME_PATTERN)?.[1];
}

function bootstrapFailure(result) {
  const detail = result.output || `process exited with status ${result.status}`;
  return new Error(`Administrator credential bootstrap failed: ${detail}`);
}

export function ensureLocalAdminCredential({
  repoRoot,
  jarPath,
  databaseEnv,
  configuredSecretPath,
  commandRunner,
  passwordFactory,
}) {
  const runBootstrap = ({ passwordFile, initializationSource } = {}) =>
    runLocalAdminBootstrap({
      jarPath,
      databaseEnv,
      passwordFile,
      initializationSource,
      commandRunner,
    });

  const probe = runBootstrap();
  const probeOutcome = parseOutcome(probe.output);
  if (probe.status === 0) {
    if (!probeOutcome) {
      throw bootstrapFailure(probe);
    }
    return { outcome: probeOutcome, createdThisRun: false };
  }
  if (!probe.output.includes(BOOTSTRAP_REQUIRED_MESSAGE)) {
    throw bootstrapFailure(probe);
  }

  const secretPath = configuredSecretPath
    ? path.resolve(configuredSecretPath)
    : resolveLocalAdminSecretPath(repoRoot, databaseEnv.DB_URL, databaseEnv.DB_USERNAME);
  let createdThisRun = false;
  let initializationSource = 'LOCAL_SECRET_FILE';
  if (configuredSecretPath) {
    if (!existsSync(secretPath)) {
      throw new Error(`Configured local administrator password file does not exist: ${secretPath}`);
    }
    assertSafeSecretFile(secretPath);
  } else {
    const useLocalDefault = passwordFactory === undefined;
    const secret = ensureLocalAdminSecret(
      secretPath,
      passwordFactory ?? (() => LOCAL_DEFAULT_ADMIN_PASSWORD),
    );
    createdThisRun = secret.createdThisRun;
    initializationSource = useLocalDefault ? 'LOCAL_DEFAULT' : 'LOCAL_RANDOM';
  }

  const initialized = runBootstrap({ passwordFile: secretPath, initializationSource });
  if (initialized.status !== 0) {
    throw bootstrapFailure(initialized);
  }
  const outcome = parseOutcome(initialized.output);
  if (!outcome) {
    throw bootstrapFailure(initialized);
  }
  return {
    outcome,
    secretPath: outcome === 'INITIALIZED' ? secretPath : undefined,
    createdThisRun,
  };
}

export function formatLocalAdminNotice(result, repoRoot) {
  if (result.outcome === 'ALREADY_INITIALIZED') {
    return ['[local] Built-in administrator credential is already initialized.'];
  }
  if (result.outcome === 'ADOPTED_EXISTING_CREDENTIAL') {
    return ['[local] Existing built-in administrator credential was preserved.'];
  }
  if (result.outcome !== 'INITIALIZED' || !result.secretPath) {
    throw new Error(`Unexpected administrator credential bootstrap outcome: ${result.outcome || 'unknown'}`);
  }

  const displayPath = path.relative(repoRoot, result.secretPath) || result.secretPath;
  return [
    '[local] Built-in administrator initialized.',
    '[local] Username: admin',
    `[local] One-time password saved to: ${displayPath}`,
    '[local] First login requires changing to a different strong password.',
    `[local] Delete the secret file after changing the password: ${displayPath}`,
  ];
}
