import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const lifecycle = readFileSync(path.join(repoRoot, 'bin', 'competition-lifecycle-e2e.mjs'), 'utf8');
const fixture = readFileSync(path.join(repoRoot, 'bin', 'prepare-competition-lifecycle-e2e.mjs'), 'utf8');
const smtpSink = readFileSync(path.join(repoRoot, 'bin', 'lib', 'smtp-sink.mjs'), 'utf8');

test('repository lifecycle E2E covers the complete competition business chain', () => {
  const requiredContracts = [
    '创建赛事草稿',
    '发布赛事',
    '原子确认报名并提交初赛材料',
    '模拟支付成功并回调',
    '确认报名支付完成',
    '参赛者提交申诉',
    '组织者处理申诉',
    '按已发布赛果授予奖项',
    '从奖项生成证书',
    '撤销并验证证书状态',
    '结束并归档赛事',
    '核验工作台审计链路',
  ];
  requiredContracts.forEach((contract) => assert.match(lifecycle, new RegExp(contract)));
  assert.match(lifecycle, /\$\{label\}创建评审方案/);
  assert.match(lifecycle, /label: '初赛'/);
  assert.match(lifecycle, /label: '决赛'/);
  assert.match(lifecycle, /LIFECYCLE_SUMMARY_FILE/);
  assert.match(lifecycle, /only accepts a loopback base URL/);
  assert.doesNotMatch(lifecycle, /const password\s*=\s*['"][^'"]+['"]/);
});

test('lifecycle fixture owns every prerequisite without embedding credentials', () => {
  assert.match(fixture, /LIFECYCLE_ADMIN_INITIAL_PASSWORD/);
  assert.match(fixture, /LIFECYCLE_PARTICIPANT_INITIAL_PASSWORD/);
  assert.match(fixture, /LIFECYCLE_EXPERT_INITIAL_PASSWORD/);
  assert.match(fixture, /roleCode=commonuser/);
  assert.match(fixture, /\/api\/v2\/iam\/users/);
  assert.match(fixture, /\/api\/v2\/workflows\/tasks\/\$\{task\.id\}\/approve/);
  assert.match(fixture, /builtin-mock-payment/);
  assert.match(fixture, /\/api\/v1\/system\/smtp-settings/);
  assert.match(fixture, /\/api\/v1\/system\/dict-items\?dictCode=/);
  for (const dictCode of ['aiadc_expert_title', 'aiadc_expert_position', 'aiadc_expert_expertise', 'aiadc_expert_tag']) {
    assert.match(fixture, new RegExp(dictCode));
  }
  assert.doesNotMatch(fixture, /高级评审|软件工程,产品设计/);
  assert.match(fixture, /only accepts a loopback base URL/);
  assert.doesNotMatch(fixture, /(?:Password|password)\s*=\s*['"][^'"]+['"]/);
});

test('SMTP sink exposes health, capture, and reset endpoints for isolated CI', () => {
  assert.match(smtpSink, /request\.url === '\/health'/);
  assert.match(smtpSink, /request\.url === '\/messages'/);
  assert.match(smtpSink, /request\.url === '\/reset'/);
  assert.match(smtpSink, /SMTP_SINK_HOST/);
});
