import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ownershipPath = path.join(repoRoot, 'docs', 'architecture', 'redis-key-ownership.yaml');

function parseScalar(value) {
  const normalized = value.trim();
  if ((normalized.startsWith('"') && normalized.endsWith('"')) || (normalized.startsWith("'") && normalized.endsWith("'"))) {
    return normalized.slice(1, -1);
  }
  if (normalized === 'true') return true;
  if (normalized === 'false') return false;
  return normalized;
}

/** Parse the intentionally small, reviewable subset used by the ownership registry. */
export function parseOwnershipRegistry(source) {
  const registry = { planes: {}, keys: [] };
  let section = null;
  let plane = null;
  let key = null;
  for (const rawLine of source.split(/\r?\n/u)) {
    const line = rawLine.replace(/\s+#.*$/u, '').trimEnd();
    if (!line.trim()) continue;
    const trimmed = line.trim();
    if (trimmed.startsWith('#')) continue;
    if (trimmed === 'planes:') {
      section = 'planes';
      continue;
    }
    if (trimmed === 'keys:') {
      section = 'keys';
      continue;
    }
    const top = line.match(/^(version|defaultPlane|unknownKeyPolicy):\s*(.+)$/u);
    if (top) {
      registry[top[1]] = parseScalar(top[2]);
      continue;
    }
    if (section === 'planes') {
      const planeHeader = line.match(/^\s{2}([a-z][a-z0-9-]*):\s*$/u);
      if (planeHeader) {
        plane = planeHeader[1];
        registry.planes[plane] = {};
        continue;
      }
      const planeField = line.match(/^\s{4}([A-Za-z][A-Za-z0-9]*):\s*(.+)$/u);
      if (planeField && plane) {
        registry.planes[plane][planeField[1]] = parseScalar(planeField[2]);
        continue;
      }
    }
    if (section === 'keys') {
      const keyHeader = line.match(/^\s{2}-\s+pattern:\s*(.+)$/u);
      if (keyHeader) {
        key = { pattern: parseScalar(keyHeader[1]) };
        registry.keys.push(key);
        continue;
      }
      const keyField = line.match(/^\s{4}([A-Za-z][A-Za-z0-9]*):\s*(.+)$/u);
      if (keyField && key) {
        key[keyField[1]] = parseScalar(keyField[2]);
        continue;
      }
    }
    throw new Error(`Unsupported ownership registry line: ${rawLine}`);
  }
  return registry;
}

function patternPrefix(pattern) {
  if (typeof pattern !== 'string' || pattern.length === 0) return null;
  if (pattern.includes('*') && !pattern.endsWith('*')) return null;
  if ((pattern.match(/\*/gu) ?? []).length > 1) return null;
  return pattern.endsWith('*') ? pattern.slice(0, -1) : pattern;
}

export function validateOwnershipRegistry(registry) {
  const errors = [];
  if (registry.version !== '1' && registry.version !== 1) errors.push('version must be 1');
  if (registry.defaultPlane !== 'runtime') errors.push('defaultPlane must be runtime');
  if (registry.unknownKeyPolicy !== 'runtime') errors.push('unknownKeyPolicy must be runtime');
  for (const plane of ['runtime', 'cache']) {
    const definition = registry.planes[plane];
    if (!definition) {
      errors.push(`missing plane ${plane}`);
      continue;
    }
    const expected = plane === 'runtime'
      ? { service: 'redis-runtime', evictionPolicy: 'noeviction', persistence: 'aof' }
      : { service: 'redis-cache', evictionPolicy: 'allkeys-lru', persistence: 'none' };
    for (const [field, value] of Object.entries(expected)) {
      if (definition[field] !== value) errors.push(`${plane}.${field} must be ${value}`);
    }
  }
  const seen = new Set();
  const prefixes = [];
  for (const entry of registry.keys) {
    if (!entry || !entry.pattern || !entry.plane || !entry.owner || !entry.semantics) {
      errors.push('every key entry must declare pattern, plane, owner, and semantics');
      continue;
    }
    if (seen.has(entry.pattern)) errors.push(`duplicate pattern ${entry.pattern}`);
    seen.add(entry.pattern);
    if (!['runtime', 'cache'].includes(entry.plane)) errors.push(`invalid plane ${entry.plane} for ${entry.pattern}`);
    const prefix = patternPrefix(entry.pattern);
    if (prefix === null) errors.push(`pattern must be an exact key or a single trailing * prefix: ${entry.pattern}`);
    else prefixes.push({ ...entry, prefix });
    if (entry.plane === 'cache' && entry.semantics !== 'rebuildable') {
      errors.push(`cache key must be rebuildable: ${entry.pattern}`);
    }
  }
  for (let left = 0; left < prefixes.length; left += 1) {
    for (let right = left + 1; right < prefixes.length; right += 1) {
      const a = prefixes[left];
      const b = prefixes[right];
      if (a.plane !== b.plane && (a.prefix.startsWith(b.prefix) || b.prefix.startsWith(a.prefix))) {
        errors.push(`overlapping key patterns cross planes: ${a.pattern} and ${b.pattern}`);
      }
    }
  }
  for (const required of [
    ['lumira:runtime:recovery-fence:*', 'runtime'],
    ['lumira.events.*', 'runtime'],
    ['saas:platform-events', 'runtime'],
    ['message:ws-ticket:*', 'cache'],
    ['message:unread-count:*', 'cache'],
    ['notification:wechat-official:access-token:*', 'cache'],
  ]) {
    const found = registry.keys.find((entry) => entry.pattern === required[0] && entry.plane === required[1]);
    if (!found) errors.push(`missing required ownership ${required[0]} -> ${required[1]}`);
  }
  return errors;
}

export function validateRedisKeyOwnership({ root = repoRoot } = {}) {
  assert.ok(existsSync(path.join(root, 'docs', 'architecture', 'redis-key-ownership.yaml')));
  const source = readFileSync(path.join(root, 'docs', 'architecture', 'redis-key-ownership.yaml'), 'utf8');
  const registry = parseOwnershipRegistry(source);
  const errors = validateOwnershipRegistry(registry);
  if (errors.length > 0) throw new Error(errors.join('\n'));
  return registry;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  validateRedisKeyOwnership();
  console.log(`validated ${ownershipPath}`);
}
