#!/usr/bin/env node

import { execFileSync } from 'node:child_process';

const redisUrl = process.env.REDIS_RUNTIME_PEL_TEST_URL;
const streamPrefix = process.env.REDIS_RUNTIME_PEL_TEST_STREAM_PREFIX;
const confirmation = process.env.REDIS_RUNTIME_PEL_TEST_CONFIRM;
const runtimeUrl = process.env.REDIS_RUNTIME_URL;
const cacheUrl = process.env.REDIS_CACHE_URL;

if (!redisUrl || !streamPrefix || confirmation !== 'I_UNDERSTAND_ISOLATED_REDIS') {
  console.error(
    'Set REDIS_RUNTIME_PEL_TEST_URL, REDIS_RUNTIME_PEL_TEST_STREAM_PREFIX and REDIS_RUNTIME_PEL_TEST_CONFIRM=I_UNDERSTAND_ISOLATED_REDIS',
  );
  process.exit(2);
}
if (!streamPrefix.startsWith('test:lumira:pel:')) {
  console.error('REDIS_RUNTIME_PEL_TEST_STREAM_PREFIX must start with test:lumira:pel:');
  process.exit(2);
}
if (redisUrl === runtimeUrl || redisUrl === cacheUrl) {
  console.error('PEL recovery drill refuses REDIS_RUNTIME_URL or REDIS_CACHE_URL; use an isolated Redis URL');
  process.exit(2);
}

const suffix = `${Date.now()}-${process.pid}`;
const streamKey = `${streamPrefix}${suffix}`;
const groupName = `lumira-pel-recovery-${suffix}`;
const consumerA = `consumer-a-${suffix}`;
const consumerB = `consumer-b-${suffix}`;
let testMessageId;

function redis(args) {
  try {
    const output = execFileSync(
      'redis-cli',
      ['--json', '--no-auth-warning', '--url', redisUrl, ...args],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
    ).trim();
    return output ? JSON.parse(output) : null;
  } catch (error) {
    const detail = error?.stderr?.toString().trim() || error?.message || 'unknown redis-cli failure';
    throw new Error(`Redis command failed: ${detail}`);
  }
}

function pendingSummary() {
  const response = redis(['XPENDING', streamKey, groupName]);
  const consumers = Array.isArray(response?.[3])
    ? response[3].map(([name, count]) => ({ consumer: name, pending: Number(count) }))
    : [];
  return {
    pending: Number(response?.[0] ?? 0),
    consumers,
  };
}

function cleanup() {
  try {
    redis(['XGROUP', 'DESTROY', streamKey, groupName]);
  } catch {
    // Best-effort cleanup for the explicitly generated test key.
  }
  try {
    redis(['DEL', streamKey]);
  } catch {
    // Best-effort cleanup for the explicitly generated test key.
  }
}

try {
  testMessageId = redis([
    'XADD',
    streamKey,
    '*',
    'eventId',
    `pel-recovery-${suffix}`,
    'eventType',
    'PEL_RECOVERY_TEST',
    'payload',
    '{}',
  ]);
  if (typeof testMessageId !== 'string' || !testMessageId.includes('-')) {
    throw new Error(`XADD did not return a Stream ID: ${JSON.stringify(testMessageId)}`);
  }

  redis(['XGROUP', 'CREATE', streamKey, groupName, '0-0', 'MKSTREAM']);
  const read = redis([
    'XREADGROUP',
    'GROUP',
    groupName,
    consumerA,
    'COUNT',
    '1',
    'STREAMS',
    streamKey,
    '>',
  ]);
  const delivered = read?.[0]?.[1]?.[0];
  if (!Array.isArray(delivered) || delivered[0] !== testMessageId) {
    throw new Error(`consumer-A did not receive the test message: ${JSON.stringify(read)}`);
  }

  const beforeRecovery = pendingSummary();
  const ownerBeforeRecovery = beforeRecovery.consumers.find(item => item.consumer === consumerA);
  if (beforeRecovery.pending !== 1 || ownerBeforeRecovery?.pending !== 1) {
    throw new Error(`XPENDING did not show consumer-A ownership: ${JSON.stringify(beforeRecovery)}`);
  }

  const claimed = redis([
    'XAUTOCLAIM',
    streamKey,
    groupName,
    consumerB,
    '0',
    '0-0',
    'COUNT',
    '10',
  ]);
  const claimedIds = Array.isArray(claimed?.[1]) ? claimed[1].map(entry => entry[0]) : [];
  if (!claimedIds.includes(testMessageId)) {
    throw new Error(`consumer-B did not claim the test message: ${JSON.stringify(claimed)}`);
  }

  const afterClaim = pendingSummary();
  const ownerAfterClaim = afterClaim.consumers.find(item => item.consumer === consumerB);
  if (afterClaim.pending !== 1 || ownerAfterClaim?.pending !== 1) {
    throw new Error(`XPENDING did not move ownership to consumer-B: ${JSON.stringify(afterClaim)}`);
  }

  const acknowledged = Number(redis(['XACK', streamKey, groupName, testMessageId]));
  const afterRecovery = pendingSummary();
  if (acknowledged !== 1 || afterRecovery.pending !== 0) {
    throw new Error(`recovery ACK did not clear the PEL: ${JSON.stringify({ acknowledged, afterRecovery })}`);
  }

  console.log(JSON.stringify({
    streamKey,
    groupName,
    beforeRecovery: {
      pending: beforeRecovery.pending,
      consumer: consumerA,
    },
    afterRecovery: {
      pending: afterRecovery.pending,
      claimedBy: consumerB,
      acked: acknowledged,
    },
  }, null, 2));
  console.log('redis runtime PEL recovery drill passed');
} catch (error) {
  console.error(`redis runtime PEL recovery drill failed: ${error.message}`);
  process.exitCode = 1;
} finally {
  cleanup();
}
