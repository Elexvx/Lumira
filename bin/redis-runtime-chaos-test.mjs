#!/usr/bin/env node

import { execFileSync } from 'node:child_process';

const runtimeUrl = process.env.REDIS_RUNTIME_URL;
const cacheUrl = process.env.REDIS_CACHE_URL;
const streamKey = process.env.REDIS_RUNTIME_STREAM_KEY;
const streamGroup = process.env.REDIS_RUNTIME_STREAM_GROUP;
const expectedMaxLen = optionalPositiveInteger(process.env.REDIS_RUNTIME_STREAM_MAXLEN);

if (!runtimeUrl || !cacheUrl) {
  console.error('REDIS_RUNTIME_URL and REDIS_CACHE_URL are required');
  process.exit(2);
}

function optionalPositiveInteger(value) {
  if (value == null || value === '') return null;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`Expected a positive integer, got ${value}`);
  }
  return parsed;
}

function redis(url, args) {
  try {
    return execFileSync('redis-cli', ['--raw', '--no-auth-warning', '--url', url, ...args], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
  } catch (error) {
    const detail = error?.stderr?.toString().trim() || error?.message || 'unknown redis-cli failure';
    throw new Error(`Redis command failed: ${detail}`);
  }
}

function info(url, section) {
  const output = redis(url, ['INFO', section]);
  const values = new Map();
  for (const line of output.split(/\r?\n/u)) {
    if (!line || line.startsWith('#')) continue;
    const separator = line.indexOf(':');
    if (separator > 0) values.set(line.slice(0, separator), line.slice(separator + 1));
  }
  return values;
}

function config(url, key) {
  const values = redis(url, ['CONFIG', 'GET', key]).split(/\r?\n/u).filter(Boolean);
  return values.length >= 2 ? values[1] : null;
}

function assertEqual(actual, expected, message) {
  if (actual !== expected) throw new Error(`${message}: expected ${expected}, got ${actual ?? 'unknown'}`);
}

function assertRuntime(url) {
  const stats = info(url, 'stats');
  const persistence = info(url, 'persistence');
  const policy = config(url, 'maxmemory-policy');
  const appendOnly = config(url, 'appendonly');
  const evictedKeys = Number(stats.get('evicted_keys'));
  if (!Number.isFinite(evictedKeys)) throw new Error('runtime evicted_keys is unavailable');
  assertEqual(policy, 'noeviction', 'runtime maxmemory-policy');
  if (appendOnly !== 'yes') throw new Error(`runtime appendonly must be yes, got ${appendOnly ?? 'unknown'}`);
  if (evictedKeys !== 0) throw new Error(`runtime evicted_keys must be 0, got ${evictedKeys}`);
  console.log(JSON.stringify({
    plane: 'runtime',
    policy,
    appendOnly,
    aofEnabled: persistence.get('aof_enabled'),
    evictedKeys,
    usedMemory: Number(info(url, 'memory').get('used_memory')),
  }));
}

function inspectCache(url) {
  const stats = info(url, 'stats');
  const policy = config(url, 'maxmemory-policy');
  assertEqual(policy, 'allkeys-lru', 'cache maxmemory-policy');
  console.log(JSON.stringify({
    plane: 'cache',
    policy,
    evictedKeys: Number(stats.get('evicted_keys')),
    usedMemory: Number(info(url, 'memory').get('used_memory')),
  }));
}

function inspectStream(url) {
  if (!streamKey && !streamGroup && expectedMaxLen == null) return;
  if (!streamKey || !streamGroup || expectedMaxLen == null) {
    throw new Error('stream validation requires REDIS_RUNTIME_STREAM_KEY, REDIS_RUNTIME_STREAM_GROUP and REDIS_RUNTIME_STREAM_MAXLEN together');
  }
  const pending = redis(url, ['XPENDING', streamKey, streamGroup]).split(/\r?\n/u).filter(Boolean);
  const pendingCount = Number(pending[0] ?? 0);
  const length = Number(redis(url, ['XLEN', streamKey]));
  if (!Number.isFinite(pendingCount) || !Number.isFinite(length)) {
    throw new Error('stream pending count or length is unavailable');
  }
  if (length > Math.ceil(expectedMaxLen * 1.1)) {
    throw new Error(`stream ${streamKey} exceeds approximate MAXLEN budget: ${length} > ${expectedMaxLen}`);
  }
  console.log(JSON.stringify({ streamKey, streamGroup, pendingCount, length, expectedMaxLen }));
}

try {
  assertRuntime(runtimeUrl);
  inspectCache(cacheUrl);
  inspectStream(runtimeUrl);
  console.log('redis runtime validation passed (read-only)');
} catch (error) {
  console.error(`redis runtime validation failed: ${error.message}`);
  process.exitCode = 1;
}
