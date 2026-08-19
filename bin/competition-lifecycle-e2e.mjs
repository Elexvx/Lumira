#!/usr/bin/env node

import crypto from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const baseUrl = process.env.LIFECYCLE_BASE_URL || 'http://127.0.0.1:8000';
const organizerUsername = process.env.LIFECYCLE_ORGANIZER_USERNAME || 'admin';
const expertUsername = process.env.LIFECYCLE_EXPERT_USERNAME || 'e2e_lifecycle_expert';
const expertName = process.env.LIFECYCLE_EXPERT_NAME || '赛事全流程验收专家';
const password = process.env.LIFECYCLE_PASSWORD;
const round = process.env.LIFECYCLE_ROUND || '1';
const timeoutMs = Number(process.env.LIFECYCLE_TIMEOUT_MS || 15_000);
const smtpSinkUrl = process.env.LIFECYCLE_SMTP_SINK_URL || 'http://127.0.0.1:2526';

if (!password) {
  throw new Error('Set LIFECYCLE_PASSWORD');
}

const base = new URL(baseUrl);
if (!['localhost', '127.0.0.1', '::1'].includes(base.hostname)) {
  throw new Error(`Lifecycle E2E only accepts a loopback base URL, got ${base.origin}`);
}
const smtpSink = new URL(smtpSinkUrl);
if (!['localhost', '127.0.0.1', '::1'].includes(smtpSink.hostname)) {
  throw new Error(`Lifecycle E2E only accepts a loopback SMTP capture URL, got ${smtpSink.origin}`);
}

const runId = `${new Date().toISOString().replace(/[-:.TZ]/g, '')}-${round}-${crypto.randomBytes(3).toString('hex')}`;
const summary = { runId, baseUrl, steps: [], ids: {} };
const summaryFile = process.env.LIFECYCLE_SUMMARY_FILE;

process.on('exit', () => {
  if (!summaryFile) return;
  const absolute = path.resolve(summaryFile);
  mkdirSync(path.dirname(absolute), { recursive: true });
  writeFileSync(absolute, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
});

function localDateTime(offsetMinutes = 0, separator = ' ') {
  const value = new Date(Date.now() + (offsetMinutes * 60_000));
  const pad = (part) => String(part).padStart(2, '0');
  return [
    value.getFullYear(),
    pad(value.getMonth() + 1),
    pad(value.getDate()),
  ].join('-') + separator + [
    pad(value.getHours()),
    pad(value.getMinutes()),
    pad(value.getSeconds()),
  ].join(':');
}

function scheduleForPhase(phase) {
  const windows = {
    PRELIMINARY_MATERIAL: {
      preliminary: [-2_880, 60, 120, 1_440],
      final: [2_880, 4_320, 4_380, 5_760],
    },
    PRELIMINARY_REVIEW: {
      preliminary: [-2_880, -60, -30, 1_440],
      final: [2_880, 4_320, 4_380, 5_760],
    },
    FINAL_MATERIAL: {
      preliminary: [-4_320, -2_880, -2_820, -1_440],
      final: [-30, 60, 120, 1_440],
    },
    FINAL_REVIEW: {
      preliminary: [-4_320, -2_880, -2_820, -1_440],
      final: [-2_880, -60, -30, 1_440],
    },
  }[phase];
  assert(windows, `Unknown lifecycle phase: ${phase}`);
  const stage = (stageCode, title, values) => ({
    stageCode,
    title,
    timeMode: 'CONFIRMED',
    materialStart: localDateTime(values[0]),
    materialEnd: localDateTime(values[1]),
    reviewStart: localDateTime(values[2]),
    reviewEnd: localDateTime(values[3]),
  });
  return [
    stage('PRELIMINARY', '初赛', windows.preliminary),
    stage('FINAL', '决赛', windows.final),
  ];
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function safeBody(body) {
  if (typeof body === 'string') return body.slice(0, 1500);
  try {
    return JSON.stringify(body)?.slice(0, 1500);
  } catch {
    return String(body).slice(0, 1500);
  }
}

async function rawRequest(pathname, { token, method = 'GET', body, accept = 'application/json' } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = {
    accept,
    'x-request-id': crypto.randomUUID(),
  };
  if (body !== undefined) headers['content-type'] = 'application/json';
  if (token) headers.authorization = `Bearer ${token}`;
  try {
    const response = await fetch(new URL(pathname, baseUrl), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
      redirect: 'manual',
    });
    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('application/json')
      ? await response.json()
      : Buffer.from(await response.arrayBuffer());
    return { response, payload };
  } finally {
    clearTimeout(timer);
  }
}

async function api(token, pathname, options = {}) {
  const { response, payload } = await rawRequest(pathname, { ...options, token });
  const applicationCode = payload && !Buffer.isBuffer(payload) && typeof payload === 'object' ? payload.code : undefined;
  const applicationOk = applicationCode === undefined || String(applicationCode) === '0';
  if (!response.ok || !applicationOk) {
    throw new Error(`${options.method || 'GET'} ${pathname} failed: HTTP ${response.status}, body=${safeBody(payload)}`);
  }
  return payload && !Buffer.isBuffer(payload) && Object.hasOwn(payload, 'data') ? payload.data : payload;
}

async function step(name, task) {
  const startedAt = performance.now();
  try {
    const value = await task();
    const elapsedMs = Math.round(performance.now() - startedAt);
    summary.steps.push({ name, ok: true, elapsedMs });
    console.log(`[PASS] ${name} (${elapsedMs}ms)`);
    return value;
  } catch (error) {
    const elapsedMs = Math.round(performance.now() - startedAt);
    const message = error instanceof Error ? error.message : String(error);
    summary.steps.push({ name, ok: false, elapsedMs, message });
    console.error(`[FAIL] ${name} (${elapsedMs}ms): ${message}`);
    throw error;
  }
}

function decodeEmailBody(raw) {
  const parts = String(raw).split(/\r?\n\r?\n/);
  const headers = parts.shift() || '';
  const encodedBody = parts.join('\n\n');
  if (/content-transfer-encoding:\s*base64/i.test(headers)) {
    return Buffer.from(encodedBody.replace(/\s+/g, ''), 'base64').toString('utf8');
  }
  if (/content-transfer-encoding:\s*quoted-printable/i.test(headers)) {
    return encodedBody
      .replace(/=\r?\n/g, '')
      .replace(/=([0-9A-F]{2})/gi, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)));
  }
  return encodedBody;
}

async function resetSmtpSink() {
  const response = await fetch(new URL('/reset', smtpSinkUrl), { method: 'POST' });
  assert(response.ok, `SMTP sink reset failed: HTTP ${response.status}`);
}

async function waitForInvitationToken() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const response = await fetch(new URL('/messages', smtpSinkUrl));
    assert(response.ok, `SMTP sink read failed: HTTP ${response.status}`);
    const messages = await response.json();
    for (const message of messages.toReversed()) {
      const candidates = [String(message.raw || '').replace(/=\r?\n/g, ''), decodeEmailBody(message.raw)];
      for (const content of candidates) {
        const match = content.match(/[?&]token=([A-Za-z0-9_-]{20,512})/);
        if (match) return match[1];
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('Review invitation email was not captured by the SMTP sink');
}

async function login(username) {
  const keyResult = await api(undefined, '/api/v1/auth/login-encryption-key');
  assert(keyResult?.publicKey, 'Login encryption public key is missing');
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(keyResult.publicKey, 'base64'),
    format: 'der',
    type: 'spki',
  });
  const encryptedPassword = crypto.publicEncrypt({
    key: publicKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: 'sha256',
  }, Buffer.from(password)).toString('base64');
  const data = await api(undefined, '/api/v1/auth/login', {
    method: 'POST',
    body: { username, account: username, password: encryptedPassword },
  });
  assert(data?.accessToken, `Login token is missing for ${username}`);
  assert(!data.requiresSecondFactor && !data.requiresPasswordChange, `${username} requires an interactive login step`);
  return data.accessToken;
}

const organizerToken = await step('组织者登录', () => login(organizerUsername));
const expertToken = await step('专家登录', () => login(expertUsername));

const organizerCurrentUser = await step('读取组织者权限快照', () => api(organizerToken, '/api/v2/auth/current-user'));
summary.organizerUserId = organizerCurrentUser?.id || organizerCurrentUser?.userId;
summary.organizerRoleCodes = organizerCurrentUser?.roleCodes || organizerCurrentUser?.roles;
summary.organizerPermissionCount = Array.isArray(organizerCurrentUser?.permissions)
  ? organizerCurrentUser.permissions.length
  : undefined;
console.log(`[INFO] organizer user=${summary.organizerUserId ?? 'unknown'}, roles=${safeBody(summary.organizerRoleCodes)}, permissions=${summary.organizerPermissionCount ?? 'unknown'}`);
console.log(`[INFO] plugin permissions=${safeBody((organizerCurrentUser?.permissions || []).filter((item) => String(item).startsWith('plugin:')))}`);

await step('验证内置模拟支付插件可用', async () => {
  const available = await api(organizerToken, '/api/v2/plugins/current/available');
  const current = available.find((item) => item.pluginCode === 'builtin-mock-payment');
  assert(current, `Built-in mock payment plugin is not available: ${safeBody(available)}`);
});

const competitionPayload = {
  code: `E2E-${runId}`.slice(0, 64),
  locale: 'zh',
  title: `赛事全流程验收-${runId}`.slice(0, 128),
  shortName: `全流程-${round}`,
  category: 'INNOVATION',
  level: 'NATIONAL',
  competitionLevel: 'NATIONAL',
  organizer: '主办方：Lumira E2E',
  organizersJson: JSON.stringify([{ role: '主办方', name: 'Lumira E2E' }]),
  registrationStart: localDateTime(-10_080),
  registrationEnd: localDateTime(10_080),
  competitionStart: localDateTime(-60),
  competitionEnd: localDateTime(10_080),
  location: '线上',
  participationScope: '全国高校学生与指导教师',
  participationRequirement: '1-4 名学生、1-2 名指导教师',
  scheduleJson: JSON.stringify(scheduleForPhase('PRELIMINARY_MATERIAL')),
  description: '自动化创建的完整生命周期验收赛事。',
  contactName: '验收联系人',
  tags: 'E2E,生命周期',
  status: 'draft',
  feeMode: 'TEAM',
  entryFeeMinor: 100,
  currency: 'CNY',
  featured: false,
  sort: 999,
};

const competition = await step('创建赛事草稿', () => api(organizerToken, '/api/v2/aiadc/competitions', {
  method: 'POST', body: competitionPayload,
}));
assert(competition?.id && competition?.uuid, 'Created competition is missing id or uuid');
summary.ids.competitionId = competition.id;
summary.ids.competitionUuid = competition.uuid;
summary.competitionTitle = competition.title;

const configModules = {
  documents: [
    { itemType: 'AGREEMENT', itemKey: 'terms', title: '参赛协议', contentText: '我已阅读并同意参赛协议。', sortOrder: 10, requiredFlag: true, enabled: true },
    { itemType: 'CONSENT', itemKey: 'privacy-consent', title: '隐私授权书', contentText: '我同意在赛事流程中处理报名信息。', sortOrder: 20, requiredFlag: true, enabled: true },
  ],
  fields: [
    { itemType: 'TEAM_SETTINGS', itemKey: 'team-size-limits', title: '参赛人员数量限制', contentJson: JSON.stringify({ studentMinMembers: 1, studentMaxMembers: 4, teacherMinMembers: 1, teacherMaxMembers: 2, teamMinMembers: 1, teamMaxMembers: 4 }), sortOrder: 0, requiredFlag: false, enabled: true },
    { itemType: 'REGISTRATION_FIELD', itemKey: 'contactEmail', title: '联系邮箱', contentJson: JSON.stringify({ fieldType: 'EMAIL', validationRule: 'EMAIL' }), sortOrder: 10, requiredFlag: true, enabled: true },
    { itemType: 'TEAM_FIELD', itemKey: 'schoolName', title: '学校名称', contentJson: JSON.stringify({ fieldType: 'TEXT' }), sortOrder: 20, requiredFlag: true, enabled: true },
    { itemType: 'MEMBER_FIELD', itemKey: 'grade', title: '年级', contentJson: JSON.stringify({ fieldType: 'TEXT' }), sortOrder: 30, requiredFlag: true, enabled: true },
    { itemType: 'TEACHER_FIELD', itemKey: 'discipline', title: '指导方向', contentJson: JSON.stringify({ fieldType: 'TEXT' }), sortOrder: 40, requiredFlag: true, enabled: true },
    { itemType: 'PROJECT_FIELD', itemKey: 'track', title: '参赛赛道', contentJson: JSON.stringify({ fieldType: 'SELECT', options: '人工智能\n数据科学' }), sortOrder: 50, requiredFlag: true, enabled: true },
    { itemType: 'EXPERT_FIELD', itemKey: 'expertiseProof', title: '专家资质说明', contentJson: JSON.stringify({ fieldType: 'TEXT' }), sortOrder: 60, requiredFlag: false, enabled: true },
  ],
  payments: [
    { itemType: 'PAYMENT_SETTINGS', itemKey: 'builtin_mock', title: '内置模拟支付', contentJson: JSON.stringify({ providerCode: 'builtin_mock', environment: 'SANDBOX' }), sortOrder: 10, requiredFlag: false, enabled: true },
  ],
  files: [
    { itemType: 'REQUIRED_FILE', itemKey: 'commitment-file', title: '赛事承诺材料', contentJson: JSON.stringify({ stageCode: 'PRELIMINARY', stageName: '初赛', materialType: 'COMMITMENT', fileFormat: 'ANY', maxSizeMb: 20, storageKey: competition.storageKey }), sortOrder: 10, requiredFlag: false, enabled: true },
  ],
  'stage-materials': [
    { itemType: 'STAGE_MATERIAL', itemKey: 'proposal-file', title: '初赛方案材料', contentJson: JSON.stringify({ stageCode: 'PRELIMINARY', stageName: '初赛', materialType: 'WORK_FILE', fileFormat: 'ANY', maxSizeMb: 20, storageKey: competition.storageKey }), sortOrder: 10, requiredFlag: false, enabled: true },
    { itemType: 'STAGE_MATERIAL', itemKey: 'final-file', title: '决赛成果材料', contentJson: JSON.stringify({ stageCode: 'FINAL', stageName: '决赛', materialType: 'WORK_FILE', fileFormat: 'ANY', maxSizeMb: 20, storageKey: competition.storageKey }), sortOrder: 20, requiredFlag: false, enabled: true },
  ],
  timeline: [
    { itemType: 'TIMELINE', itemKey: 'registration-window', title: '报名期', contentJson: JSON.stringify({ timelineKind: 'REGISTRATION', start: competitionPayload.registrationStart, end: competitionPayload.registrationEnd }), sortOrder: 10, requiredFlag: false, enabled: true },
    { itemType: 'TIMELINE', itemKey: 'competition-window', title: '比赛期', contentJson: JSON.stringify({ timelineKind: 'COMPETITION', start: competitionPayload.competitionStart, end: competitionPayload.competitionEnd }), sortOrder: 20, requiredFlag: false, enabled: true },
  ],
  awards: [
    {
      itemType: 'AWARD_SETTINGS',
      itemKey: 'award-rules',
      title: '获奖设置',
      contentJson: JSON.stringify({
        rules: [
          { awardName: '一等奖', quotaType: 'FIXED', quota: 1 },
          { awardName: '二等奖', quotaType: 'FIXED', quota: 1 },
          { awardName: '三等奖', quotaType: 'FIXED', quota: 1 },
          { awardName: '优秀奖', quotaType: 'FIXED', quota: 1 },
        ],
      }),
      sortOrder: 10,
      requiredFlag: false,
      enabled: true,
    },
  ],
};

for (const [module, items] of Object.entries(configModules)) {
  await step(`保存赛事配置-${module}`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/settings/${module}`,
    { method: 'PUT', body: { items } },
  ));
}

await step('发布赛事配置', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/settings/publish`,
  { method: 'POST' },
));

const publishedCompetition = await step('发布赛事', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${competition.id}`,
  { method: 'PUT', body: { ...competitionPayload, status: 'published' } },
));
assert(publishedCompetition.status === 'published', `Unexpected competition status: ${publishedCompetition.status}`);

const stages = await step('读取初赛与决赛阶段', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${competition.id}/stages`,
));
const preliminaryStage = stages.find((item) => item.stageCode === 'PRELIMINARY');
const finalStage = stages.find((item) => item.stageCode === 'FINAL');
assert(preliminaryStage?.id && finalStage?.id, 'Preliminary or final stage is missing');
summary.ids.preliminaryStageId = preliminaryStage.id;
summary.ids.finalStageId = finalStage.id;

await step('配置初赛文本材料表单', () => api(organizerToken, `/api/v2/aiadc/stages/${preliminaryStage.id}/form`, {
  method: 'PUT',
  body: {
    formName: '初赛材料表单',
    formSchemaJson: JSON.stringify({ fields: [{ key: 'proposalSummary', label: '方案摘要', type: 'input', required: true }] }),
    version: 2,
    status: 'ENABLED',
  },
}));

await step('配置决赛文本材料表单', () => api(organizerToken, `/api/v2/aiadc/stages/${finalStage.id}/form`, {
  method: 'PUT',
  body: {
    formName: '决赛材料表单',
    formSchemaJson: JSON.stringify({ fields: [{ key: 'finalSummary', label: '成果摘要', type: 'textarea', required: true }] }),
    version: 2,
    status: 'ENABLED',
  },
}));

const registrationPayload = {
  competitionId: competition.id,
  registrationExtraValues: { contactEmail: 'contestant@example.invalid' },
  teamSnapshot: {
    teamName: `验收战队-${runId}`.slice(0, 128),
    teamType: 'STUDENT',
    visibility: 'PRIVATE',
    joinMode: 'INVITE_ONLY',
    description: '生命周期测试团队',
    extraValues: { schoolName: 'Lumira 测试大学' },
  },
  projectSnapshot: {
    title: `智能项目-${runId}`.slice(0, 128),
    category: 'INNOVATION',
    description: '用于验证完整赛事流程的项目。',
    extraValues: { track: '人工智能' },
  },
  members: [
    { participantType: 'STUDENT', memberName: '测试学生', employeeNo: `S${round}001`, departmentName: '计算机学院', role: 'CAPTAIN', extraValues: { grade: '大三' } },
    { participantType: 'TEACHER', memberName: '测试教师', employeeNo: `T${round}001`, departmentName: '计算机学院', role: 'ADVISOR', extraValues: { discipline: '人工智能' } },
  ],
};

const registration = await step('原子确认报名并提交初赛材料', () => api(organizerToken, '/api/v2/aiadc/registrations/confirm', {
  method: 'POST',
  body: {
    registration: registrationPayload,
    project: {
      title: registrationPayload.projectSnapshot.title,
      category: registrationPayload.projectSnapshot.category,
      description: registrationPayload.projectSnapshot.description,
    },
    materials: {
      stageId: preliminaryStage.id,
      values: [{ fieldKey: 'proposalSummary', fieldType: 'input', textValue: '初赛方案已完成并通过自动化校验。' }],
    },
  },
}));
assert(registration?.id, 'Registration id is missing');
assert(registration.status === 'PENDING_PAYMENT', `Registration should await payment, got ${registration.status}`);
summary.ids.registrationId = registration.id;
summary.registrationNo = registration.registrationNo;

const options = await step('读取桌面支付方式', () => api(
  organizerToken,
  `/api/v2/aiadc/registrations/${registration.id}/payment-options?clientType=DESKTOP`,
));
assert(options.some((option) => option.providerCode === 'builtin_mock'), 'Built-in mock payment option is missing');

const initialOrder = await step('创建模拟支付订单', () => api(
  organizerToken,
  `/api/v2/aiadc/registrations/${registration.id}/payment-order`,
  { method: 'POST', body: { providerCode: 'builtin_mock', clientType: 'DESKTOP', returnUrl: `http://127.0.0.1:8000/competitions/register/payment-result?registrationId=${registration.id}` } },
));
const order = initialOrder?.orderNo ? initialOrder : await step('等待异步支付订单就绪', async () => {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const current = await api(organizerToken, `/api/v2/aiadc/registrations/${registration.id}/payment-status`);
    if (current?.orderNo) return current;
    if (['FAILED', 'CANCELLED', 'EXPIRED', 'CLOSED'].includes(String(current?.status || '').toUpperCase())) {
      throw new Error(`Payment order entered terminal state: ${safeBody(current)}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Payment order did not become ready: ${safeBody(initialOrder)}`);
});
assert(order?.orderNo, `Payment order number is missing: ${safeBody(order)}`);
summary.ids.paymentOrderNo = order.orderNo;

await step('打开模拟收银台', async () => {
  const checkout = await api(organizerToken, `/api/v2/payment/builtin-mock/orders/${encodeURIComponent(order.orderNo)}/checkout`);
  assert(checkout?.order?.orderNo === order.orderNo || checkout?.orderNo === order.orderNo, 'Checkout order mismatch');
  return checkout;
});

await step('模拟支付成功并回调', () => api(
  organizerToken,
  `/api/v2/payment/builtin-mock/orders/${encodeURIComponent(order.orderNo)}/simulate`,
  { method: 'POST', body: { outcome: 'SUCCESS', callbackDelaySeconds: 0 } },
));

const paidRegistration = await step('确认报名支付完成', async () => {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const current = await api(organizerToken, `/api/v2/aiadc/registrations/${registration.id}`);
    if (current.status === 'CONFIRMED') return current;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error('Registration did not become CONFIRMED after mock payment');
});
assert(paidRegistration.participantNo, 'Confirmed registration is missing participant number');
summary.participantNo = paidRegistration.participantNo;

const experts = await step('定位已审批评审专家', async () => {
  const rosterCandidates = await api(organizerToken, `/api/v2/experts?pageNo=1&pageSize=100&keyword=${encodeURIComponent(expertName)}`);
  const records = rosterCandidates?.records || rosterCandidates || [];
  const match = records.find((item) => item.name === expertName && item.approvalStatus === 'APPROVED');
  assert(match?.id, `Lifecycle expert was not returned by expert management API: ${safeBody(rosterCandidates)}`);
  return match;
});
summary.ids.expertId = experts.id;

async function advanceCompetitionTo(label, phase) {
  const scheduleJson = JSON.stringify(scheduleForPhase(phase));
  const updated = await step(`推进赛事到${label}`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${competition.id}`,
    { method: 'PUT', body: { ...competitionPayload, scheduleJson, status: 'published' } },
  ));
  competitionPayload.scheduleJson = scheduleJson;
  assert(updated.status === 'published', `Competition left published state while entering ${label}`);
}

async function runReviewCycle({ stage, label, decision }) {
  const idPrefix = stage.stageCode === 'PRELIMINARY' ? 'preliminaryReview' : 'finalReview';
  const reviewPlan = await step(`${label}创建评审方案`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/plans`,
    {
      method: 'POST',
      body: {
        competitionId: competition.id,
        stageId: stage.id,
        planName: `${label}评审方案-${runId}`.slice(0, 255),
        blindMode: 'DOUBLE_BLIND',
        requiredReviewerCount: 1,
        minimumSubmittedCount: 1,
        aggregateMethod: 'AVERAGE',
        scoreScale: 100,
        trimHighestCount: 0,
        trimLowestCount: 0,
        criteria: [
          { code: 'INNOVATION', name: '创新性', description: '方案创新程度', weight: 0.6, maximumScore: 100, required: true, sortOrder: 10 },
          { code: 'FEASIBILITY', name: '可行性', description: '落地可行程度', weight: 0.4, maximumScore: 100, required: true, sortOrder: 20 },
        ],
      },
    },
  ));
  summary.ids[`${idPrefix}PlanId`] = reviewPlan.id;

  await step(`${label}激活评审方案`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/plans/${reviewPlan.id}/activate`,
    { method: 'POST' },
  ));

  const reviewBatch = await step(`${label}创建评审批次`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches`,
    {
      method: 'POST',
      body: {
        planId: reviewPlan.id,
        batchName: `${label}评审批次-${runId}`.slice(0, 255),
        assignmentStrategy: 'BALANCED',
        reviewerCountPerCandidate: 1,
        expertMinAssignments: 0,
        expertTargetAssignments: 1,
        expertMaxAssignments: 10,
        reviewDeadline: localDateTime(1_440, 'T'),
      },
    },
  ));
  summary.ids[`${idPrefix}BatchId`] = reviewBatch.id;

  await step(`${label}冻结评审候选集`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/freeze`,
    { method: 'POST', body: { registrationIds: [registration.id] } },
  ));

  const candidates = await step(`${label}读取评审候选项`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/candidates`,
  ));
  assert(candidates.length === 1, `Expected one ${label} review candidate, got ${candidates.length}`);
  summary.ids[`${idPrefix}CandidateId`] = candidates[0].id;

  await step(`${label}保存评审专家名册`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/roster`,
    { method: 'PUT', body: { expertIds: [experts.id] } },
  ));

  await step(`${label}自动分配评审任务`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/auto-assign`,
    { method: 'POST', body: { expertIds: [experts.id], dueAt: localDateTime(1_440, 'T'), reviewerWeight: 1 } },
  ));

  await step(`${label}确认评审分配`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/assignments/confirm`,
    { method: 'POST' },
  ));

  await step(`${label}清空隔离邮件接收器`, resetSmtpSink);
  const invitations = await step(`${label}发送评审邀请邮件`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/invitations`,
    { method: 'POST' },
  ));
  assert(invitations.some((item) => item.expertId === experts.id && item.invitationStatus === 'SENT'),
    `${label} invitation was not delivered: ${safeBody(invitations)}`);

  const invitationToken = await step(`${label}接收评审邀请邮件`, waitForInvitationToken);
  const invitation = await step(`${label}专家打开邀请并生成签到码`, () => api(
    undefined,
    `/api/v2/reviews/invitations/${encodeURIComponent(invitationToken)}`,
  ));
  assert(invitation?.qrValue, `${label} invitation did not issue a check-in QR token`);

  await step(`${label}组织者扫码签到专家`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/check-ins`,
    { method: 'POST', body: { qrToken: invitation.qrValue } },
  ));

  await step(`${label}启动评审`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/start`,
    { method: 'POST' },
  ));

  await step(`${label}验证邀请入口可读取任务`, async () => {
    const tasks = await api(undefined, `/api/v2/reviews/invitations/${encodeURIComponent(invitationToken)}/assignments`);
    assert(tasks.some((item) => item.batchId === reviewBatch.id), `Invitation assignment is missing: ${safeBody(tasks)}`);
  });

  const assignment = await step(`${label}专家读取本人评审任务`, async () => {
    const tasks = await api(expertToken, '/api/v2/reviews/assignments/mine');
    const match = tasks.find((item) => item.batchId === reviewBatch.id);
    assert(match?.assignmentId, `Expert assignment is missing: ${safeBody(tasks)}`);
    return match;
  });
  summary.ids[`${idPrefix}AssignmentId`] = assignment.assignmentId;

  await step(`${label}专家接受评审任务`, () => api(
    expertToken,
    `/api/v2/reviews/assignments/${assignment.assignmentId}/accept`,
    { method: 'POST' },
  ));

  const scorePayload = {
    reviewComment: `${label}项目创新性与可行性表现良好，建议通过。`,
    scores: assignment.criteria.map((criterion) => ({
      criterionId: criterion.id,
      score: criterion.criterionCode === 'INNOVATION' ? 92 : 88,
      comment: `${label}自动化评审意见`,
    })),
  };

  await step(`${label}专家保存评审草稿`, () => api(
    expertToken,
    `/api/v2/reviews/assignments/${assignment.assignmentId}/sheet`,
    { method: 'PUT', body: scorePayload },
  ));

  await step(`${label}专家提交评审结果`, () => api(
    expertToken,
    `/api/v2/reviews/assignments/${assignment.assignmentId}/submit`,
    { method: 'POST', body: scorePayload },
  ));

  const aggregates = await step(`${label}汇总评审结果`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/aggregate`,
    { method: 'POST' },
  ));
  assert(aggregates.length === 1 && aggregates[0].aggregateScore > 0, `Unexpected ${label} review aggregates: ${safeBody(aggregates)}`);

  await step(`${label}确认候选项${decision === 'ADVANCED' ? '晋级' : '通过'}`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/candidates/${candidates[0].id}/decision`,
    { method: 'PUT', body: { decision, reason: `${label}综合评分达到通过标准` } },
  ));

  await step(`${label}定稿评审批次`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/finalize`,
    { method: 'POST' },
  ));

  const publication = await step(`${label}发布评审结果`, () => api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/batches/${reviewBatch.id}/publish`,
    { method: 'POST' },
  ));
  assert(publication?.id, `${label} review publication id is missing`);
  summary.ids[`${idPrefix}PublicationId`] = publication.id;
  return { reviewPlan, reviewBatch, candidates, assignment, aggregates, publication };
}

await advanceCompetitionTo('初赛评审期', 'PRELIMINARY_REVIEW');
const preliminaryReview = await runReviewCycle({ stage: preliminaryStage, label: '初赛', decision: 'ADVANCED' });

await step('验证初赛晋级结果已发布', async () => {
  const results = await api(organizerToken, '/api/v2/reviews/results/mine');
  const match = results.find((item) => item.batchId === preliminaryReview.reviewBatch.id && item.registrationId === registration.id);
  assert(match?.decision === 'ADVANCED', `Published preliminary advance is missing: ${safeBody(results)}`);
});

await advanceCompetitionTo('决赛材料期', 'FINAL_MATERIAL');
await step('晋级后提交决赛材料', () => api(organizerToken, `/api/v2/aiadc/registrations/${registration.id}/materials`, {
  method: 'POST',
  body: {
    stageId: finalStage.id,
    values: [{ fieldKey: 'finalSummary', fieldType: 'textarea', textValue: '决赛成果已完成，指标与演示均通过。' }],
  },
}));

await step('验证赛事工作台报名与材料', async () => {
  const workspace = await api(organizerToken, `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/workspace`);
  const registrations = await api(organizerToken, `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/registrations?pageNo=1&pageSize=20&includeSnapshots=true`);
  const materials = await api(organizerToken, `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/registrations/${registration.id}/materials`);
  assert(workspace?.competitionUuid === competition.uuid, `Workspace competition mismatch: ${safeBody(workspace)}`);
  assert((registrations?.records || []).some((item) => item.id === registration.id), 'Workspace registration is missing');
  assert(materials.length >= 2, `Expected at least two material submissions, got ${materials.length}`);
});

await advanceCompetitionTo('决赛评审期', 'FINAL_REVIEW');
const finalReview = await runReviewCycle({ stage: finalStage, label: '决赛', decision: 'PASS' });
const reviewBatch = finalReview.reviewBatch;
summary.ids.reviewPlanId = finalReview.reviewPlan.id;
summary.ids.reviewBatchId = reviewBatch.id;
summary.ids.reviewCandidateId = finalReview.candidates[0].id;
summary.ids.reviewAssignmentId = finalReview.assignment.assignmentId;
summary.ids.reviewPublicationId = finalReview.publication.id;

const myResult = await step('参赛者读取本人赛果', async () => {
  const results = await api(organizerToken, '/api/v2/reviews/results/mine');
  const match = results.find((item) => item.batchId === reviewBatch.id && item.registrationId === registration.id);
  assert(match?.publicationId, `Published result is missing: ${safeBody(results)}`);
  return match;
});

const appeal = await step('参赛者提交申诉', () => api(
  organizerToken,
  `/api/v2/reviews/publications/${myResult.publicationId}/registrations/${registration.id}/appeals`,
  { method: 'POST', body: { reason: '自动化验证申诉提交流程，结果本身无异议。' } },
));
summary.ids.appealId = appeal.id;

await step('组织者处理申诉', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/reviews/appeals/${appeal.id}/resolution`,
  { method: 'PUT', body: { decision: 'REJECTED', resolution: '流程验证完成，原评审结果有效。' } },
));

const template = await step('创建证书模板', () => api(organizerToken, '/api/v2/aiadc/certificate-templates', {
  method: 'POST',
  body: {
    templateCode: `E2E-CERT-${runId}`.slice(0, 64),
    templateName: `赛事证书-${runId}`.slice(0, 128),
    sceneType: 'COMPETITION_AWARD',
    description: '自动化证书模板',
  },
}));
summary.ids.certificateTemplateId = template.id;

const versions = await step('读取证书模板版本', () => api(
  organizerToken,
  `/api/v2/aiadc/certificate-templates/${template.id}/versions`,
));
assert(versions.length > 0, 'Certificate template version is missing');
const templateVersion = versions[0];
summary.ids.certificateTemplateVersionId = templateVersion.id;

await step('保存证书画布', () => api(
  organizerToken,
  `/api/v2/aiadc/certificate-template-versions/${templateVersion.id}/canvas`,
  {
    method: 'PUT',
    body: {
      pageWidth: 1123,
      pageHeight: 794,
      orientation: 'LANDSCAPE',
      unit: 'PX',
      dpi: 96,
      canvasJson: JSON.stringify({
        page: { width: 1123, height: 794, dpi: 96, orientation: 'LANDSCAPE' },
        elements: [
          { id: 'title', type: 'text', text: '获奖证书', x: 300, y: 100, width: 523, height: 80, fontSize: 42, textAlign: 'center' },
          { id: 'recipient', type: 'text', fieldKey: 'recipientName', x: 300, y: 250, width: 523, height: 60, fontSize: 30, textAlign: 'center' },
          { id: 'award', type: 'text', fieldKey: 'awardName', x: 300, y: 350, width: 523, height: 60, fontSize: 30, textAlign: 'center' },
          { id: 'competition', type: 'text', fieldKey: 'competitionTitle', x: 300, y: 440, width: 523, height: 50, fontSize: 24, textAlign: 'center' },
          { id: 'issue-date', type: 'text', fieldKey: 'issueDate', x: 300, y: 510, width: 523, height: 40, fontSize: 18, textAlign: 'center' },
          { id: 'qr', type: 'qrcode', fieldKey: 'verificationUrl', x: 900, y: 600, width: 120, height: 120 },
        ],
      }),
      variableSchemaJson: JSON.stringify({ fields: ['recipientName', 'awardName', 'competitionTitle', 'issueDate', 'verificationUrl'] }),
    },
  },
));

const publishedTemplateVersion = await step('发布证书模板', () => api(
  organizerToken,
  `/api/v2/aiadc/certificate-templates/${template.id}/publish`,
  { method: 'POST' },
));

await step('保存获奖规则', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificate-award-rules`,
  { method: 'PUT', body: { reviewBatchId: reviewBatch.id, rules: [{ awardName: '一等奖', minRank: 1, maxRank: 1 }] } },
));

const grants = await step('按已发布赛果授予奖项', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificate-awards/grant`,
  { method: 'POST', body: { reviewBatchId: reviewBatch.id, rules: [{ awardName: '一等奖', minRank: 1, maxRank: 1 }] } },
));
assert(grants.length === 1 && grants[0].id, `Unexpected award grants: ${safeBody(grants)}`);
summary.ids.awardGrantId = grants[0].id;

const certificateResult = await step('从奖项生成证书', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificate-batches/from-awards`,
  {
    method: 'POST',
    body: {
      batchName: `获奖证书批次-${runId}`.slice(0, 128),
      templateId: template.id,
      templateVersionId: publishedTemplateVersion?.id || templateVersion.id,
      grantIds: grants.map((item) => item.id),
    },
  },
));
assert(certificateResult?.records?.length === 1, `Unexpected certificate result: ${safeBody(certificateResult)}`);
const certificate = certificateResult.records[0];
summary.ids.certificateId = certificate.id;
summary.certificateNo = certificate.certificateNo;

await step('下载并公开验证证书', async () => {
  const { response, payload } = await rawRequest(
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificates/${certificate.id}/download`,
    { token: organizerToken, accept: 'application/pdf,application/octet-stream' },
  );
  assert(response.ok && Buffer.isBuffer(payload) && payload.length > 100, `Certificate download failed: HTTP ${response.status}, bytes=${payload?.length || 0}`);
  const byNo = await api(undefined, `/api/public/certificates/verify?certificateNo=${encodeURIComponent(certificate.certificateNo)}&verificationCode=${encodeURIComponent(certificate.verificationCode)}`);
  const byToken = await api(undefined, `/api/public/certificates/verify/${encodeURIComponent(certificate.publicToken)}`);
  assert(byNo?.result === 'VALID' && byToken?.result === 'VALID', `Certificate public verification failed: ${safeBody({ byNo, byToken })}`);
});

await step('重新生成证书', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificates/${certificate.id}/regenerate`,
  { method: 'POST' },
));

await step('撤销并验证证书状态', async () => {
  await api(
    organizerToken,
    `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/certificates/${certificate.id}/revoke`,
    { method: 'POST', body: { reason: '生命周期验收完成后的撤销验证' } },
  );
  const verification = await api(undefined, `/api/public/certificates/verify/${encodeURIComponent(certificate.publicToken)}`);
  assert(verification?.result === 'REVOKED', `Revoked certificate should verify as REVOKED: ${safeBody(verification)}`);
});

const archivedCompetition = await step('结束并归档赛事', () => api(
  organizerToken,
  `/api/v2/aiadc/competitions/${competition.id}`,
  { method: 'PUT', body: { ...competitionPayload, status: 'archived' } },
));
assert(archivedCompetition.status === 'archived', `Unexpected archived status: ${archivedCompetition.status}`);

await step('核验工作台审计链路', async () => {
  const audit = await api(organizerToken, `/api/v2/aiadc/competitions/${encodeURIComponent(competition.uuid)}/audit?pageNo=1&pageSize=100`);
  const records = audit?.records || [];
  assert(records.length >= 5, `Expected audit records, got ${records.length}`);
  const current = await api(organizerToken, `/api/v2/aiadc/competitions/${competition.id}`);
  assert(current.status === 'archived', 'Archived competition was not persisted');
});

summary.ok = true;
summary.completedAt = new Date().toISOString();
summary.passed = summary.steps.filter((item) => item.ok).length;
summary.failed = summary.steps.filter((item) => !item.ok).length;
console.log(`LIFECYCLE_SUMMARY ${JSON.stringify(summary)}`);
