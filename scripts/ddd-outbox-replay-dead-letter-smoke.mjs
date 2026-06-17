#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { collectProvenanceIssues } from './ddd-release-evidence-utils.mjs';
import { redactLocalPaths } from './ddd-release-evidence-utils.mjs';
import { requiredOutboxReplayTestClasses } from './ddd-outbox-job-evidence-contract.mjs';

const root = resolve(new URL('..', import.meta.url).pathname);
const artifactPath = resolve(root, 'artifacts/ddd/outbox/outbox-replay-dead-letter-test-evidence.json');
const strictEvidence = process.env.DDD_RELEASE_EVIDENCE_STRICT === 'true'
  || process.env.DDD_OUTBOX_SMOKE_STRICT === 'true';
const sourceEnvironment = process.env.DDD_EVIDENCE_ENVIRONMENT || process.env.DDD_RELEASE_ENVIRONMENT || '';
const releaseCandidate = process.env.DDD_RELEASE_CANDIDATE || process.env.GITHUB_SHA || '';
const evidenceOperator = process.env.DDD_EVIDENCE_OPERATOR || process.env.GITHUB_ACTOR || '';
const homeDir = process.env.HOME || '';

const testClasses = requiredOutboxReplayTestClasses;

const modules = [
  'services/message-service',
  'services/system-service',
  'services/file-service',
  'services/payment-service',
  'services/plugin-service',
].join(',');

const args = [
  '-pl',
  modules,
  '-am',
  `-Dtest=${testClasses.join(',')}`,
  '-Dsurefire.failIfNoSpecifiedTests=false',
  'test',
];

const provenanceIssues = strictEvidence
  ? collectProvenanceIssues({ sourceEnvironment, releaseCandidate, evidenceOperator })
  : [];
if (provenanceIssues.length > 0) {
  writeEvidence({
    status: 'FAIL',
    generatedAt: new Date().toISOString(),
    sourceEnvironment,
    releaseCandidate,
    evidenceOperator,
    strictEvidence,
    command: `./mvnw ${args.map(quoteArg).join(' ')}`,
    blockers: provenanceIssues.map((issue) => `outbox-replay-dead-letter-provenance: ${issue}`),
    testedContracts: [],
    reports: [],
    stdoutTail: '',
    stderrTail: '',
  });
  console.error(`Outbox replay/dead-letter smoke missing strict provenance. Evidence: ${artifactPath}`);
  process.exit(1);
}

const startedAt = new Date();
const result = spawnSync('./mvnw', args, {
  cwd: root,
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe'],
});
const finishedAt = new Date();

const reports = collectReports();
const evidence = {
  status: result.status === 0 ? 'PASS' : 'FAIL',
  generatedAt: finishedAt.toISOString(),
  sourceEnvironment,
  releaseCandidate,
  evidenceOperator,
  strictEvidence,
  command: `./mvnw ${args.map(quoteArg).join(' ')}`,
  startedAt: startedAt.toISOString(),
  finishedAt: finishedAt.toISOString(),
  elapsedMs: finishedAt.getTime() - startedAt.getTime(),
  testedContracts: [
    'outbox claim prevents duplicate dispatch',
    'successful dispatch marks delivered',
    'dispatcher failure increments retry_count',
    'retry_count >= 8 moves event to DEAD_LETTER',
    'manual replay resets event to pending/recorded state before redispatch',
    'relay disabled still allows explicit replay',
  ],
  reports,
  stdoutTail: tail(result.stdout),
  stderrTail: tail(result.stderr),
};

writeEvidence(evidence);

if (result.status !== 0) {
  console.error(`Outbox replay/dead-letter smoke failed. Evidence: ${artifactPath}`);
  process.exit(result.status ?? 1);
}

console.log(`Outbox replay/dead-letter smoke passed. Evidence: ${artifactPath}`);

function collectReports() {
  return testClasses.map((className) => {
    const service = inferService(className);
    const reportPath = join(root, service, 'target/surefire-reports', `TEST-${className}.xml`);
    if (!existsSync(reportPath)) {
      return { className, reportPath: portablePath(reportPath), present: false };
    }
    const xml = readFileSync(reportPath, 'utf8');
    const attrs = parseTestsuiteAttributes(xml);
    return {
      className,
      reportPath: portablePath(reportPath),
      present: true,
      tests: Number(attrs.tests ?? 0),
      failures: Number(attrs.failures ?? 0),
      errors: Number(attrs.errors ?? 0),
      skipped: Number(attrs.skipped ?? 0),
      timeSeconds: Number(attrs.time ?? 0),
    };
  });
}

function writeEvidence(evidence) {
  mkdirSync(dirname(artifactPath), { recursive: true });
  writeFileSync(artifactPath, `${JSON.stringify(evidence, null, 2)}\n`);
}

function inferService(className) {
  if (className.includes('.message.')) {
    return 'services/message-service';
  }
  if (className.includes('.file.')) {
    return 'services/file-service';
  }
  if (className.includes('.payment.')) {
    return 'services/payment-service';
  }
  if (className.includes('.plugin.')) {
    return 'services/plugin-service';
  }
  return 'services/system-service';
}

function parseTestsuiteAttributes(xml) {
  const match = xml.match(/<testsuite\s+([^>]+)>/);
  if (!match) {
    return {};
  }
  return Object.fromEntries([...match[1].matchAll(/(\w+)="([^"]*)"/g)].map((entry) => [entry[1], entry[2]]));
}

function tail(value, maxLines = 80) {
  return redactLocalPaths((value || '').split(/\r?\n/).slice(-maxLines).join('\n'), {
    repoRoot: root,
    homeDir,
  });
}

function portablePath(value) {
  const absolute = resolve(value);
  if (absolute === root) {
    return '.';
  }
  if (absolute.startsWith(`${root}/`)) {
    return absolute.slice(root.length + 1);
  }
  if (homeDir && absolute === resolve(homeDir)) {
    return '~';
  }
  if (homeDir && absolute.startsWith(`${resolve(homeDir)}/`)) {
    return `~/${absolute.slice(resolve(homeDir).length + 1)}`;
  }
  return value;
}

function quoteArg(arg) {
  return /^[A-Za-z0-9_./:=,-]+$/.test(arg) ? arg : JSON.stringify(arg);
}
