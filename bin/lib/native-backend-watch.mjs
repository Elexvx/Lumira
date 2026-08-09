const ignoredSegments = new Set([
  '.git',
  '.idea',
  '.mvn',
  '.vscode',
  'node_modules',
  'target',
]);

export function normalizeWatchPath(filePath) {
  return String(filePath || '')
    .replaceAll('\\', '/')
    .replace(/^\.\//, '');
}

export function isBackendSourcePath(filePath) {
  const normalized = normalizeWatchPath(filePath);
  if (!normalized) {
    return false;
  }

  const segments = normalized.split('/');
  if (segments.some((segment) => ignoredSegments.has(segment))) {
    return false;
  }

  if (segments.at(-1) === 'pom.xml') {
    return true;
  }

  return normalized.includes('/src/main/java/')
    || normalized.startsWith('src/main/java/')
    || normalized.includes('/src/main/resources/')
    || normalized.startsWith('src/main/resources/');
}

export function createChangeBatcher({ delayMs = 850, onBatch }) {
  if (!Number.isFinite(delayMs) || delayMs < 0) {
    throw new TypeError('delayMs must be a non-negative number.');
  }
  if (typeof onBatch !== 'function') {
    throw new TypeError('onBatch must be a function.');
  }

  const pending = new Set();
  let timer;
  let closed = false;

  const flush = () => {
    timer = undefined;
    if (closed || pending.size === 0) {
      return;
    }
    const files = [...pending].sort();
    pending.clear();
    onBatch(files);
  };

  return {
    add(filePath) {
      if (closed || !isBackendSourcePath(filePath)) {
        return false;
      }
      pending.add(normalizeWatchPath(filePath));
      clearTimeout(timer);
      timer = setTimeout(flush, delayMs);
      return true;
    },
    close() {
      closed = true;
      clearTimeout(timer);
      timer = undefined;
      pending.clear();
    },
    flush,
  };
}
