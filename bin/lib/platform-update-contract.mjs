export const UPDATER_PROTOCOL_VERSION = 3;
export const UPDATE_STRATEGY = 'single-host-release-set-blue-green';
export const LEGACY_UPDATE_STRATEGY = 'single-host-blue-green';
export const UPDATE_PHASES = Object.freeze([
  'PREFLIGHT',
  'BACKUP',
  'PULLING',
  'MIGRATING',
  'PREPARING_WORKERS',
  'DRAINING_WORKERS',
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
const fullCommitPattern = /^[0-9a-f]{40}$/i;
const releaseIdPattern = /^v[A-Za-z0-9][A-Za-z0-9._-]{0,126}$/u;
const maintenanceModes = new Set(['NORMAL', 'WRITE_DRAIN', 'READ_ONLY', 'FULL_MAINTENANCE']);
const frontendModes = new Set(['local-blue-green', 'external-managed']);

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

export function parseRuntimeVersionIdentity(value) {
  try {
    const root = typeof value === 'string' ? JSON.parse(value) : value;
    const data = root?.data && typeof root.data === 'object' ? root.data : root;
    if (!data || typeof data !== 'object') {
      return null;
    }
    return {
      serviceName: String(data.serviceName || '').trim(),
      artifact: String(data.artifact || '').trim(),
      commitId: String(data.commitId || '').trim(),
    };
  } catch {
    return null;
  }
}

function assertDistinctRuntimeImageDigests(images) {
  const rolesByDigest = new Map();
  for (const [role, image] of Object.entries(images)) {
    if (!image) continue;
    const digest = image.slice(image.lastIndexOf('@sha256:') + 1).toLowerCase();
    const previousRole = rolesByDigest.get(digest);
    if (previousRole) {
      throw new Error(`images.${previousRole} and images.${role} must use distinct image digests`);
    }
    rolesByDigest.set(digest, role);
  }
}

export function normalizeReleaseManifest(rawManifest) {
  const raw = rawManifest && typeof rawManifest === 'object' ? rawManifest : {};
  const images = raw.images && typeof raw.images === 'object' ? raw.images : {};
  const update = raw.update && typeof raw.update === 'object' ? raw.update : {};
  const compatibility = raw.compatibility && typeof raw.compatibility === 'object' ? raw.compatibility : {};
  const database = compatibility.database && typeof compatibility.database === 'object'
    ? compatibility.database
    : update.database && typeof update.database === 'object'
    ? update.database
    : raw.database && typeof raw.database === 'object'
      ? raw.database
      : {};
  const commit = String(raw.commit || raw.commitId || '').trim();
  if (!commitPattern.test(commit)) {
    throw new Error('release manifest commit is invalid');
  }

  const schemaVersion = Number(raw.schemaVersion || 1);
  if (schemaVersion >= 3 && !fullCommitPattern.test(commit)) {
    throw new Error('schemaVersion 3 release manifest commit must contain exactly 40 hexadecimal characters');
  }
  const releaseId = String(raw.releaseId || (schemaVersion < 3 ? `v${raw.version || commit}` : '')).trim();
  if (schemaVersion >= 3 && !releaseIdPattern.test(releaseId)) {
    throw new Error('release manifest releaseId is invalid');
  }
  const serverImage = assertDigestPinnedImage(images.server || raw.serverImage, 'images.server');
  const frontendImage = assertDigestPinnedImage(images.frontend || raw.frontendImage, 'images.frontend', { optional: schemaVersion < 3 });
  const asyncImage = assertDigestPinnedImage(images.async || raw.asyncImage, 'images.async', { optional: schemaVersion < 2 });
  const jobExecutorImage = assertDigestPinnedImage(images.jobExecutor || raw.jobExecutorImage, 'images.jobExecutor', { optional: schemaVersion < 2 });
  const migratorImage = assertDigestPinnedImage(images.migrator || raw.migratorImage, 'images.migrator', { optional: schemaVersion < 2 });
  if (schemaVersion >= 2) {
    assertDistinctRuntimeImageDigests({ server: serverImage, async: asyncImage, jobExecutor: jobExecutorImage });
  }

  const migrationMode = String(database.migrationMode || database.mode || (raw.migrationRequired ? 'expand-only' : 'none'));
  const databaseCompatibility = {
    targetVersion: String(database.targetVersion || raw.databaseVersion || ''),
    minReadableVersion: String(database.minReadableVersion || ''),
    maxReadableVersion: String(database.maxReadableVersion || ''),
    migrationMode,
    rollbackMode: String(database.rollbackMode || 'forward-compatible'),
  };
  const normalizedCompatibility = {
    database: databaseCompatibility,
    event: normalizeIntegerCompatibility(compatibility.event, { readMin: 1, readMax: 1, writeVersion: 1 }),
    session: normalizeVersionSetCompatibility(compatibility.session, 1),
    permissionSnapshot: normalizeVersionSetCompatibility(compatibility.permissionSnapshot, 1),
    pluginApi: normalizeVersionSetCompatibility(compatibility.pluginApi, 1),
  };
  const frontend = raw.frontend && typeof raw.frontend === 'object' ? raw.frontend : {};
  const frontendMode = String(frontend.mode || (frontendImage ? 'local-blue-green' : 'external-managed'));
  if (schemaVersion >= 3 && !frontendModes.has(frontendMode)) {
    throw new Error('frontend.mode must be local-blue-green or external-managed');
  }
  const databaseRequiredRuntimeMode = String(update.databaseRequiredRuntimeMode || 'NORMAL');
  if (!maintenanceModes.has(databaseRequiredRuntimeMode)) {
    throw new Error('update.databaseRequiredRuntimeMode is invalid');
  }
  const rollback = raw.rollback && typeof raw.rollback === 'object' ? raw.rollback : {};

  return {
    schemaVersion,
    app: String(raw.app || 'lumira'),
    releaseId,
    channel: String(raw.channel || 'stable'),
    version: String(raw.version || commit),
    commit,
    releasedAt: String(raw.releasedAt || ''),
    releaseNotes: String(raw.releaseNotes || ''),
    expiresAt: String(raw.expiresAt || ''),
    strategy: String(update.strategy || (schemaVersion >= 3 ? UPDATE_STRATEGY : schemaVersion >= 2 ? LEGACY_UPDATE_STRATEGY : 'legacy-recreate')),
    minUpdaterProtocol: Number(update.minUpdaterProtocol || (schemaVersion >= 2 ? UPDATER_PROTOCOL_VERSION : 1)),
    drainTimeoutSeconds: boundedInteger(update.drainTimeoutSeconds, 60, 5, 600),
    rollbackWindowSeconds: boundedInteger(update.rollbackWindowSeconds, 1800, 60, 86400),
    databaseRequiredRuntimeMode,
    database: { ...databaseCompatibility, mode: migrationMode },
    compatibility: normalizedCompatibility,
    frontend: { mode: frontendMode },
    rollback: {
      supported: rollback.supported ?? raw.rollbackSupported !== false,
      applicationRollbackSupported: rollback.applicationRollbackSupported ?? raw.rollbackSupported !== false,
      databaseRestoreRequired: rollback.databaseRestoreRequired === true,
    },
    rollbackSupported: rollback.supported ?? raw.rollbackSupported !== false,
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
    frontendVersion: String(values.frontendVersion || ''),
    backendVersion: String(values.backendVersion || values.buildVersion || values.version || ''),
    buildTime: String(values.buildTime || ''),
    branch: String(values.branch || ''),
    databaseVersion: String(values.databaseVersion || ''),
    serverImage: String(values.serverImage || ''),
    activatedAt: values.activatedAt || new Date().toISOString(),
  };
  const currentRelease = {
    releaseId: String(values.releaseId || (current.version ? `v${current.version}` : '')),
    manifestDigest: String(values.manifestDigest || ''),
    commit: current.commit,
    version: current.version,
    databaseVersion: current.databaseVersion,
    images: {
      server: current.serverImage,
      frontend: String(values.frontendImage || ''),
      async: String(values.asyncImage || ''),
      jobExecutor: String(values.jobExecutorImage || ''),
      migrator: String(values.migratorImage || ''),
    },
    compatibility: values.compatibility || {},
    activatedAt: current.activatedAt,
  };
  return {
    schemaVersion: 3,
    operationEpoch: boundedInteger(values.operationEpoch, 0, 0, Number.MAX_SAFE_INTEGER),
    strategy: UPDATE_STRATEGY,
    activeSlot,
    status: 'HEALTHY',
    currentRelease,
    previousRelease: null,
    candidateRelease: null,
    rollbackExpiresAt: null,
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

export function repairDeploymentWorkerState(state, workerImages = {}) {
  const workers = { ...(state?.workers || {}) };
  let changed = !state?.workers;
  if (!workers.asyncImage && workerImages.asyncImage) {
    workers.asyncImage = String(workerImages.asyncImage);
    changed = true;
  }
  if (!workers.jobExecutorImage && workerImages.jobExecutorImage) {
    workers.jobExecutorImage = String(workerImages.jobExecutorImage);
    changed = true;
  }
  return {
    changed,
    state: changed ? { ...state, workers, updatedAt: new Date().toISOString() } : state,
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

  const supportedStrategy = release.schemaVersion >= 3 ? UPDATE_STRATEGY : LEGACY_UPDATE_STRATEGY;
  if (release.schemaVersion < 2 || release.strategy !== supportedStrategy) {
    blockers.push('The release does not support the required single-host blue-green deployment strategy.');
  }
  if (release.minUpdaterProtocol > UPDATER_PROTOCOL_VERSION) {
    blockers.push(`Updater protocol ${release.minUpdaterProtocol} is required; this agent supports ${UPDATER_PROTOCOL_VERSION}.`);
  }
  if ((release.schemaVersion >= 3 && !release.images.frontend) || !release.images.async || !release.images.jobExecutor || !release.images.migrator) {
    blockers.push('The release must contain digest-pinned frontend, async, job executor, and migrator images.');
  }
  if (release.schemaVersion >= 3 && release.frontend.mode !== 'local-blue-green') {
    blockers.push('External-managed frontend has no atomic deployment and rollback callback; Release Set install is blocked.');
  }
  if (release.database.mode !== 'none' && release.database.mode !== 'expand-only') {
    blockers.push('Only expand-only database migrations are allowed during online updates.');
  }
  if (!dockerAvailable) blockers.push('Docker is unavailable.');
  if (!composeAvailable) blockers.push('Docker Compose is unavailable.');
  if (!proxyAvailable) blockers.push('The API proxy is unavailable.');
  if (Number(freeMemoryBytes || 0) < minimumMemoryBytes) blockers.push('At least 1 GiB of free memory is required for blue-green overlap.');
  if (Number(freeDiskBytes || 0) < minimumDiskBytes) blockers.push('At least 3 GiB of free disk space is required for images and backups.');
  const compatibility = evaluateReleaseCompatibility({
    currentRelease: state?.currentRelease,
    previousRelease: state?.previousRelease,
    targetRelease: release,
    databaseVersion: state?.currentRelease?.databaseVersion || state?.slots?.[activeSlot]?.databaseVersion,
    frontendManaged: release.frontend.mode === 'local-blue-green',
  });
  blockers.push(...compatibility.installBlockers);
  warnings.push(...compatibility.warnings);

  return {
    ready: blockers.length === 0,
    strategy: UPDATE_STRATEGY,
    activeSlot,
    targetSlot,
    targetCommit: release.commit,
    targetVersion: release.version,
    migrationMode: release.database.mode,
    databaseTargetVersion: release.database.targetVersion,
    releaseId: release.releaseId,
    compatibility,
    maintenanceMode: release.databaseRequiredRuntimeMode,
    blockers,
    warnings,
    checkedAt: new Date().toISOString(),
  };
}

export function migrateDeploymentState(rawState, bootstrap = {}) {
  if (!rawState || typeof rawState !== 'object') return createInitialDeploymentState(bootstrap);
  if (Number(rawState.schemaVersion) >= 3 && rawState.currentRelease) {
    return {
      ...rawState,
      schemaVersion: 3,
      operationEpoch: boundedInteger(rawState.operationEpoch, 0, 0, Number.MAX_SAFE_INTEGER),
      status: String(rawState.status || 'HEALTHY'),
      candidateRelease: rawState.candidateRelease || null,
      previousRelease: rawState.previousRelease || null,
      rollbackExpiresAt: rawState.rollbackExpiresAt || rawState.rollbackDeadline || null,
    };
  }
  const activeSlot = normalizeSlot(rawState.activeSlot || bootstrap.activeSlot);
  const active = rawState.slots?.[activeSlot] || {};
  const previousSlot = rawState.previousSlot ? normalizeSlot(rawState.previousSlot) : null;
  const previous = previousSlot ? rawState.slots?.[previousSlot] : null;
  const currentRelease = legacyReleaseSnapshot(active, rawState.workers, bootstrap);
  return {
    ...rawState,
    schemaVersion: 3,
    operationEpoch: 0,
    strategy: UPDATE_STRATEGY,
    activeSlot,
    status: 'HEALTHY',
    currentRelease,
    previousRelease: previous ? legacyReleaseSnapshot(previous, rawState.previousWorkers, {}) : null,
    candidateRelease: null,
    rollbackExpiresAt: rawState.rollbackDeadline || null,
    updatedAt: new Date().toISOString(),
  };
}

export function assertOperationFence(state, { taskId, operationEpoch, candidateReleaseId } = {}) {
  if (!state || Number(state.operationEpoch) !== Number(operationEpoch)) throw new Error('operation epoch does not match current deployment state');
  if (String(state.candidateRelease?.taskId || '') !== String(taskId || '')) throw new Error('taskId does not own the candidate release');
  if (String(state.candidateRelease?.releaseId || '') !== String(candidateReleaseId || '')) throw new Error('candidate release does not match the fenced operation');
  return true;
}

export function reconcileReleaseState(state, actual = {}) {
  const migrated = migrateDeploymentState(state);
  const expected = migrated.currentRelease || {};
  const roles = ['server', 'frontend', 'async', 'jobExecutor', 'migrator'];
  const components = {};
  let mismatchCount = 0;
  let unhealthyCount = 0;
  for (const role of roles) {
    const observed = actual.components?.[role] || {};
    const expectedImage = expected.images?.[role] || '';
    const actualImage = String(observed.image || '');
    const imageMatches = Boolean(expectedImage) && expectedImage === actualImage;
    const releaseMatches = !expected.releaseId || expected.releaseId === String(observed.releaseId || '');
    const healthy = observed.healthy === true;
    if (!imageMatches || !releaseMatches) mismatchCount += 1;
    if (!healthy) unhealthyCount += 1;
    components[role] = { expectedImage, actualImage, expectedReleaseId: expected.releaseId || '', actualReleaseId: observed.releaseId || '', healthy, managed: observed.managed !== false, status: imageMatches && releaseMatches && healthy ? 'MATCHED' : 'MISMATCHED' };
  }
  const databaseMatches = !expected.databaseVersion || String(actual.databaseVersion || '') === String(expected.databaseVersion);
  if (!databaseMatches) mismatchCount += 1;
  const activeSlotMatches = !actual.activeSlot || normalizeSlot(actual.activeSlot) === normalizeSlot(migrated.activeSlot);
  if (!activeSlotMatches) mismatchCount += 1;
  const status = mismatchCount === 0 && unhealthyCount === 0
    ? 'HEALTHY'
    : unhealthyCount > 0 || !activeSlotMatches ? 'DEGRADED' : 'PARTIALLY_DEPLOYED';
  return { ...migrated, status, reconciliation: { components, databaseVersion: actual.databaseVersion || '', databaseMatches, activeSlot: actual.activeSlot || '', activeSlotMatches, checkedAt: new Date().toISOString() } };
}

export function evaluateReleaseCompatibility({ currentRelease, targetRelease, previousRelease, databaseVersion, frontendManaged = true } = {}) {
  const target = targetRelease?.compatibility ? targetRelease : normalizeReleaseManifest(targetRelease);
  const current = currentRelease || {};
  const previous = previousRelease || current;
  const installBlockers = [];
  const rollbackBlockers = [];
  const warnings = [];
  if (current.releaseId && compareReleaseVersions(target.version || target.releaseId, current.version || current.releaseId) < 0) {
    installBlockers.push(`Release downgrade from ${current.releaseId} to ${target.releaseId} is not allowed during install.`);
  }
  const currentEventWrite = Number(current.compatibility?.event?.writeVersion);
  if (Number.isInteger(currentEventWrite) && !integerRangeContains(target.compatibility.event, currentEventWrite)) {
    installBlockers.push('Target Async cannot read the Event Schema currently written by the active Server.');
  }
  const targetEventWrite = Number(target.compatibility.event.writeVersion);
  if (previous?.compatibility?.event && !integerRangeContains(previous.compatibility.event, targetEventWrite)) {
    rollbackBlockers.push('Previous Async cannot read the Event Schema written by the target Server.');
  }
  for (const [key, label] of [['session', 'Session'], ['permissionSnapshot', 'Permission Snapshot'], ['pluginApi', 'Plugin API']]) {
    const currentWrite = Number(current.compatibility?.[key]?.writeVersion);
    if (Number.isInteger(currentWrite) && !target.compatibility[key].readVersions.includes(currentWrite)) installBlockers.push(`Target release cannot read the current ${label} format.`);
    const targetWrite = Number(target.compatibility[key].writeVersion);
    if (previous?.compatibility?.[key] && !previous.compatibility[key].readVersions?.includes(targetWrite)) rollbackBlockers.push(`Previous release cannot read the target ${label} format.`);
  }
  const targetDatabase = target.compatibility.database;
  if (databaseVersion && !versionInRange(databaseVersion, targetDatabase.minReadableVersion, targetDatabase.maxReadableVersion)) installBlockers.push('Current database version is outside the target release readable range.');
  const previousDatabase = previous?.compatibility?.database;
  if (previousDatabase && targetDatabase.targetVersion && !versionInRange(targetDatabase.targetVersion, previousDatabase.minReadableVersion, previousDatabase.maxReadableVersion)) rollbackBlockers.push('Previous release cannot read the target database version.');
  if (!frontendManaged || (target.schemaVersion >= 3 && target.frontend?.mode !== 'local-blue-green')) rollbackBlockers.push('Frontend is externally managed and no atomic rollback callback is configured.');
  if (target.rollback?.databaseRestoreRequired) rollbackBlockers.push('Release declares that database restore is required; application fast rollback is forbidden.');
  return { installCompatible: installBlockers.length === 0, rollbackCompatible: rollbackBlockers.length === 0, installBlockers, rollbackBlockers, warnings };
}

export function renderActiveUpstreams(activeSlot) {
  const slot = normalizeSlot(activeSlot);
  const activeServer = `lumira-server-${slot}:8080`;
  const activeFrontend = `lumira-ui-${slot}:80`;
  return [
    `set $frontend_upstream ${activeFrontend};`,
    `set $gateway_upstream ${activeServer};`,
    `set $system_upstream ${activeServer};`,
    `set $auth_upstream ${activeServer};`,
    `set $file_upstream ${activeServer};`,
    `set $message_upstream ${activeServer};`,
    `set $plugin_upstream ${activeServer};`,
    `set $payment_upstream ${activeServer};`,
    `set $localization_upstream ${activeServer};`,
    `set $team_upstream ${activeServer};`,
    `set $ai_upstream ${activeServer};`,
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

function normalizeIntegerCompatibility(rawValue, defaults) {
  const value = rawValue && typeof rawValue === 'object' ? rawValue : {};
  const readMin = boundedInteger(value.readMin, defaults.readMin, 1, 1_000_000);
  const readMax = boundedInteger(value.readMax, defaults.readMax, readMin, 1_000_000);
  const writeVersion = boundedInteger(value.writeVersion, defaults.writeVersion, 1, 1_000_000);
  if (writeVersion < readMin || writeVersion > readMax) throw new Error('event writeVersion must be inside the readable range');
  return { readMin, readMax, writeVersion };
}

function normalizeVersionSetCompatibility(rawValue, defaultVersion) {
  const value = rawValue && typeof rawValue === 'object' ? rawValue : {};
  const writeVersion = boundedInteger(value.writeVersion, defaultVersion, 1, 1_000_000);
  const readVersions = [...new Set((Array.isArray(value.readVersions) ? value.readVersions : [writeVersion])
    .map(Number).filter((item) => Number.isInteger(item) && item > 0))].sort((left, right) => left - right);
  if (!readVersions.includes(writeVersion)) throw new Error('writeVersion must be included in readVersions');
  return { readVersions, writeVersion };
}

function legacyReleaseSnapshot(slot, workers, bootstrap) {
  return {
    releaseId: String(slot.releaseId || bootstrap.releaseId || (slot.version ? `v${slot.version}` : '')),
    manifestDigest: String(slot.manifestDigest || ''),
    commit: String(slot.commit || bootstrap.commit || ''),
    version: String(slot.version || bootstrap.version || ''),
    databaseVersion: String(slot.databaseVersion || bootstrap.databaseVersion || ''),
    images: {
      server: String(slot.serverImage || bootstrap.serverImage || ''),
      frontend: String(slot.frontendImage || bootstrap.frontendImage || ''),
      async: String(workers?.asyncImage || bootstrap.asyncImage || ''),
      jobExecutor: String(workers?.jobExecutorImage || bootstrap.jobExecutorImage || ''),
      migrator: String(slot.migratorImage || bootstrap.migratorImage || ''),
    },
    compatibility: slot.compatibility || bootstrap.compatibility || {},
    activatedAt: slot.activatedAt || new Date().toISOString(),
  };
}

function integerRangeContains(range, value) {
  return Number.isInteger(value) && value >= Number(range?.readMin) && value <= Number(range?.readMax);
}

function versionInRange(value, minimum, maximum) {
  if (minimum && compareReleaseVersions(value, minimum) < 0) return false;
  if (maximum && compareReleaseVersions(value, maximum) > 0) return false;
  return true;
}

function compareReleaseVersions(left, right) {
  const normalize = (value) => String(value || '').replace(/^v/u, '').split(/[^0-9A-Za-z]+/u).filter(Boolean);
  const a = normalize(left);
  const b = normalize(right);
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    const leftPart = a[index] || '0';
    const rightPart = b[index] || '0';
    const numeric = /^\d+$/u.test(leftPart) && /^\d+$/u.test(rightPart);
    const comparison = numeric ? Number(leftPart) - Number(rightPart) : leftPart.localeCompare(rightPart);
    if (comparison !== 0) return comparison < 0 ? -1 : 1;
  }
  return 0;
}

function boundedInteger(value, fallback, minimum, maximum) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) return fallback;
  return Math.min(maximum, Math.max(minimum, parsed));
}
