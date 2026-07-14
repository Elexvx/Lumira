export const UPDATER_PROTOCOL_VERSION = 2;
export const UPDATE_STRATEGY = 'single-host-blue-green';
export const UPDATE_PHASES = Object.freeze([
  'PREFLIGHT',
  'BACKUP',
  'PULLING',
  'MIGRATING',
  'STARTING_INACTIVE',
  'VERIFYING_INACTIVE',
  'SWITCHING_TRAFFIC',
  'VERIFYING_ACTIVE',
  'DRAINING_OLD',
  'UPDATING_WORKERS',
  'FINALIZING',
]);

export const TERMINAL_UPDATE_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'ROLLED_BACK', 'CANCELLED']);

const digestPinnedImagePattern = /^[A-Za-z0-9][A-Za-z0-9._/:@-]+@sha256:[0-9a-f]{64}$/i;
const commitPattern = /^[0-9a-f]{7,64}$/i;

export function inactiveSlot(activeSlot) {
  return normalizeSlot(activeSlot) === 'blue' ? 'green' : 'blue';
}

export function normalizeSlot(value, fallback = 'blue') {
  const normalized = String(value || '').trim().toLowerCase();
  return normalized === 'blue' || normalized === 'green' ? normalized : fallback;
}

export function assertDigestPinnedImage(value, fieldName, { optional = false } = {}) {
  const image = String(value || '').trim();
  if (!image && optional) {
    return '';
  }
  if (!digestPinnedImagePattern.test(image)) {
    throw new Error(`${fieldName} must be pinned to a sha256 digest`);
  }
  return image;
}

export function normalizeReleaseManifest(rawManifest) {
  const raw = rawManifest && typeof rawManifest === 'object' ? rawManifest : {};
  const images = raw.images && typeof raw.images === 'object' ? raw.images : {};
  const update = raw.update && typeof raw.update === 'object' ? raw.update : {};
  const database = update.database && typeof update.database === 'object' ? update.database : {};
  const commit = String(raw.commit || raw.commitId || '').trim();
  if (!commitPattern.test(commit)) {
    throw new Error('release manifest commit is invalid');
  }

  const schemaVersion = Number(raw.schemaVersion || 1);
  const serverImage = assertDigestPinnedImage(images.server || raw.serverImage, 'images.server');
  const frontendImage = assertDigestPinnedImage(images.frontend || raw.frontendImage, 'images.frontend', { optional: true });
  const asyncImage = assertDigestPinnedImage(images.async || raw.asyncImage, 'images.async', { optional: schemaVersion < 2 });
  const jobExecutorImage = assertDigestPinnedImage(images.jobExecutor || raw.jobExecutorImage, 'images.jobExecutor', { optional: schemaVersion < 2 });
  const migratorImage = assertDigestPinnedImage(images.migrator || raw.migratorImage, 'images.migrator', { optional: schemaVersion < 2 });

  return {
    schemaVersion,
    app: String(raw.app || 'lumira'),
    channel: String(raw.channel || 'stable'),
    version: String(raw.version || commit),
    commit,
    releasedAt: String(raw.releasedAt || ''),
    releaseNotes: String(raw.releaseNotes || ''),
    strategy: String(update.strategy || (schemaVersion >= 2 ? UPDATE_STRATEGY : 'legacy-recreate')),
    minUpdaterProtocol: Number(update.minUpdaterProtocol || (schemaVersion >= 2 ? UPDATER_PROTOCOL_VERSION : 1)),
    drainTimeoutSeconds: boundedInteger(update.drainTimeoutSeconds, 60, 5, 600),
    rollbackWindowSeconds: boundedInteger(update.rollbackWindowSeconds, 1800, 60, 86400),
    database: {
      mode: String(database.mode || (raw.migrationRequired ? 'expand-only' : 'none')),
      targetVersion: String(database.targetVersion || raw.databaseVersion || ''),
      rollbackMode: String(database.rollbackMode || 'forward-compatible'),
    },
    rollbackSupported: raw.rollbackSupported !== false,
    images: {
      server: serverImage,
      frontend: frontendImage,
      async: asyncImage,
      jobExecutor: jobExecutorImage,
      migrator: migratorImage,
    },
  };
}

export function createInitialDeploymentState(values = {}) {
  const activeSlot = normalizeSlot(values.activeSlot);
  const current = {
    commit: String(values.commit || ''),
    version: String(values.version || ''),
    buildVersion: String(values.buildVersion || values.version || ''),
    buildTime: String(values.buildTime || ''),
    databaseVersion: String(values.databaseVersion || ''),
    serverImage: String(values.serverImage || ''),
    activatedAt: values.activatedAt || new Date().toISOString(),
  };
  return {
    schemaVersion: 1,
    strategy: UPDATE_STRATEGY,
    activeSlot,
    previousSlot: null,
    rollbackDeadline: null,
    slots: {
      blue: activeSlot === 'blue' ? current : null,
      green: activeSlot === 'green' ? current : null,
    },
    workers: {
      asyncImage: String(values.asyncImage || ''),
      jobExecutorImage: String(values.jobExecutorImage || ''),
    },
    previousWorkers: null,
    lastSuccessfulTaskId: null,
    lastSuccessfulPlatformTaskId: null,
    updatedAt: new Date().toISOString(),
  };
}

export function buildPreflightReport({ manifest, state, freeMemoryBytes, freeDiskBytes, dockerAvailable = true, composeAvailable = true, proxyAvailable = true }) {
  const release = normalizeReleaseManifest(manifest);
  const activeSlot = normalizeSlot(state?.activeSlot);
  const targetSlot = inactiveSlot(activeSlot);
  const blockers = [];
  const warnings = [];
  const minimumMemoryBytes = 1024 * 1024 * 1024;
  const minimumDiskBytes = 3 * 1024 * 1024 * 1024;

  if (release.schemaVersion < 2 || release.strategy !== UPDATE_STRATEGY) {
    blockers.push('The release does not support single-host blue-green deployment.');
  }
  if (release.minUpdaterProtocol > UPDATER_PROTOCOL_VERSION) {
    blockers.push(`Updater protocol ${release.minUpdaterProtocol} is required; this agent supports ${UPDATER_PROTOCOL_VERSION}.`);
  }
  if (!release.images.async || !release.images.jobExecutor) {
    blockers.push('The release must contain digest-pinned async and job executor images.');
  }
  if (release.database.mode !== 'none' && release.database.mode !== 'expand-only') {
    blockers.push('Only expand-only database migrations are allowed during online updates.');
  }
  if (!dockerAvailable) blockers.push('Docker is unavailable.');
  if (!composeAvailable) blockers.push('Docker Compose is unavailable.');
  if (!proxyAvailable) blockers.push('The API proxy is unavailable.');
  if (Number(freeMemoryBytes || 0) < minimumMemoryBytes) blockers.push('At least 1 GiB of free memory is required for blue-green overlap.');
  if (Number(freeDiskBytes || 0) < minimumDiskBytes) blockers.push('At least 3 GiB of free disk space is required for images and backups.');

  return {
    ready: blockers.length === 0,
    strategy: UPDATE_STRATEGY,
    activeSlot,
    targetSlot,
    targetCommit: release.commit,
    targetVersion: release.version,
    migrationMode: release.database.mode,
    databaseTargetVersion: release.database.targetVersion,
    blockers,
    warnings,
    checkedAt: new Date().toISOString(),
  };
}

export function renderActiveUpstreams(activeSlot, env = {}) {
  const slot = normalizeSlot(activeSlot);
  const activeServer = `lumira-server-${slot}:8080`;
  const value = (name) => {
    const configured = String(env[name] || '').trim();
    return !configured || configured === 'lumira-server:8080' ? activeServer : configured;
  };
  return [
    `set $gateway_upstream ${value('GATEWAY_UPSTREAM')};`,
    `set $system_upstream ${value('SYSTEM_SERVICE_UPSTREAM')};`,
    `set $auth_upstream ${value('AUTH_SERVICE_UPSTREAM')};`,
    `set $file_upstream ${value('FILE_SERVICE_UPSTREAM')};`,
    `set $message_upstream ${value('MESSAGE_SERVICE_UPSTREAM')};`,
    `set $plugin_upstream ${value('PLUGIN_SERVICE_UPSTREAM')};`,
    `set $payment_upstream ${value('PAYMENT_SERVICE_UPSTREAM')};`,
    `set $localization_upstream ${value('LOCALIZATION_SERVICE_UPSTREAM')};`,
    `set $team_upstream ${value('TEAM_SERVICE_UPSTREAM')};`,
    `set $ai_upstream ${value('AI_SERVICE_UPSTREAM')};`,
    '',
  ].join('\n');
}

export function migrateLegacyApiProxyConfig(config) {
  const source = String(config || '');
  const includeDirective = 'include /etc/nginx/lumira-upstreams/active-upstreams.conf;';
  const staticUpstreamPattern = /^[\t ]*set \$(?:gateway_upstream|system_upstream|auth_upstream|file_upstream|message_upstream|plugin_upstream|payment_upstream|localization_upstream|team_upstream|ai_upstream)[\t ]+[^;]+;[\t ]*\r?\n/gm;
  const withoutStaticUpstreams = source.replace(staticUpstreamPattern, '');
  if (withoutStaticUpstreams.includes(includeDirective)) return withoutStaticUpstreams;

  const lineEnding = source.includes('\r\n') ? '\r\n' : '\n';
  const migrated = withoutStaticUpstreams.replace(
    /^([\t ]*resolver[\t ]+127\.0\.0\.11[^;]*;[\t ]*\r?\n)/m,
    `$1    ${includeDirective}${lineEnding}`,
  );
  if (!migrated.includes(includeDirective)) {
    throw new Error('Legacy API proxy resolver was not found');
  }
  return migrated;
}

export function phaseProgress(phase) {
  const index = UPDATE_PHASES.indexOf(phase);
  if (index < 0) return 0;
  return Math.round(((index + 1) / (UPDATE_PHASES.length + 1)) * 100);
}

function boundedInteger(value, fallback, minimum, maximum) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) return fallback;
  return Math.min(maximum, Math.max(minimum, parsed));
}
