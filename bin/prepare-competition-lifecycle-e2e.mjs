#!/usr/bin/env node

import crypto from 'node:crypto';

const baseUrl = process.env.LIFECYCLE_BASE_URL || 'http://127.0.0.1:8000';
const organizerUsername = process.env.LIFECYCLE_ORGANIZER_USERNAME || 'admin';
const participantUsername = process.env.LIFECYCLE_PARTICIPANT_USERNAME || 'e2e_lifecycle_participant';
const expertUsername = process.env.LIFECYCLE_EXPERT_USERNAME || 'e2e_lifecycle_expert';
const finalPassword = process.env.LIFECYCLE_PASSWORD;
const adminInitialPassword = process.env.LIFECYCLE_ADMIN_INITIAL_PASSWORD;
const participantInitialPassword = process.env.LIFECYCLE_PARTICIPANT_INITIAL_PASSWORD;
const expertInitialPassword = process.env.LIFECYCLE_EXPERT_INITIAL_PASSWORD;
const smtpHost = process.env.LIFECYCLE_SMTP_SERVER_HOST || '127.0.0.1';
const smtpPort = Number(process.env.LIFECYCLE_SMTP_SERVER_PORT || 2525);
const timeoutMs = Number(process.env.LIFECYCLE_TIMEOUT_MS || 15_000);
const expertName = process.env.LIFECYCLE_EXPERT_NAME || '赛事全流程验收专家';
const participantName = process.env.LIFECYCLE_PARTICIPANT_NAME || '赛事全流程验收参赛者';

if (!finalPassword) {
  throw new Error('Set LIFECYCLE_PASSWORD');
}

const base = new URL(baseUrl);
if (!['localhost', '127.0.0.1', '::1'].includes(base.hostname)) {
  throw new Error(`Lifecycle fixture only accepts a loopback base URL, got ${base.origin}`);
}
if (!Number.isInteger(smtpPort) || smtpPort <= 0 || smtpPort > 65_535) {
  throw new Error(`Invalid LIFECYCLE_SMTP_SERVER_PORT: ${smtpPort}`);
}

function safeBody(body) {
  if (typeof body === 'string') return body.slice(0, 1_000);
  try {
    return JSON.stringify(body)?.slice(0, 1_000);
  } catch {
    return String(body).slice(0, 1_000);
  }
}

async function rawRequest(pathname, { token, method = 'GET', body } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = {
    accept: 'application/json',
    'x-request-id': crypto.randomUUID(),
  };
  if (body !== undefined) headers['content-type'] = 'application/json';
  if (token) headers.authorization = `Bearer ${token}`;
  try {
    const response = await fetch(new URL(pathname, base), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('application/json') ? await response.json() : await response.text();
    return { response, payload };
  } finally {
    clearTimeout(timer);
  }
}

async function api(token, pathname, options = {}) {
  const result = await rawRequest(pathname, { ...options, token });
  const applicationCode = result.payload && typeof result.payload === 'object' ? result.payload.code : undefined;
  const applicationOk = applicationCode === undefined || String(applicationCode) === '0';
  if (!result.response.ok || !applicationOk) {
    const error = new Error(`${options.method || 'GET'} ${pathname} failed: HTTP ${result.response.status}, body=${safeBody(result.payload)}`);
    error.status = result.response.status;
    throw error;
  }
  return result.payload && typeof result.payload === 'object' && Object.hasOwn(result.payload, 'data')
    ? result.payload.data
    : result.payload;
}

async function encryptedPassword(value) {
  const keyResult = await api(undefined, '/api/v1/auth/login-encryption-key');
  if (!keyResult?.publicKey) throw new Error('Login encryption public key is missing');
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(keyResult.publicKey, 'base64'),
    format: 'der',
    type: 'spki',
  });
  return crypto.publicEncrypt({
    key: publicKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: 'sha256',
  }, Buffer.from(value)).toString('base64');
}

async function login(username, password) {
  const encrypted = await encryptedPassword(password);
  const data = await api(undefined, '/api/v1/auth/login', {
    method: 'POST',
    body: { username, account: username, password: encrypted },
  });
  if (!data?.accessToken) throw new Error(`Login token is missing for ${username}`);
  if (data.requiresSecondFactor) throw new Error(`${username} requires a second factor and cannot be used by lifecycle E2E`);
  return data;
}

async function tryLogin(username, password) {
  if (!password) return null;
  try {
    return await login(username, password);
  } catch {
    return null;
  }
}

async function resolvePassword(username, initialPassword, resolvedPassword) {
  const resolvedLogin = await tryLogin(username, resolvedPassword);
  if (resolvedLogin && !resolvedLogin.requiresPasswordChange) return resolvedLogin;

  const initialLogin = resolvedLogin || await tryLogin(username, initialPassword);
  const currentPassword = resolvedLogin ? resolvedPassword : initialPassword;
  if (!initialLogin) {
    throw new Error(`Unable to authenticate ${username}; provide its initial password through the lifecycle fixture environment`);
  }
  if (currentPassword === resolvedPassword && !initialLogin.requiresPasswordChange) return initialLogin;
  if (currentPassword === resolvedPassword) {
    throw new Error(`${username} requires a password change; configure distinct initial and resolved lifecycle passwords`);
  }

  await api(initialLogin.accessToken, '/api/v1/profile/password', {
    method: 'PUT',
    body: {
      currentPassword,
      newPassword: resolvedPassword,
      confirmPassword: resolvedPassword,
    },
  });
  const relogin = await login(username, resolvedPassword);
  if (relogin.requiresPasswordChange) throw new Error(`${username} still requires a password change after fixture preparation`);
  return relogin;
}

async function waitFor(predicate, description, attempts = 80) {
  let lastError;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const value = await predicate();
      if (value) return value;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${description} did not become ready${lastError ? `: ${lastError.message}` : ''}`);
}

console.log('[fixture] Resolving organizer administrator credential.');
const organizerLogin = await resolvePassword(organizerUsername, adminInitialPassword, finalPassword);
const organizerToken = organizerLogin.accessToken;

console.log('[fixture] Configuring the isolated SMTP capture endpoint.');
await api(organizerToken, '/api/v1/system/smtp-settings', {
  method: 'PUT',
  body: {
    enabled: true,
    host: smtpHost,
    port: smtpPort,
    username: '',
    password: '',
    from: 'lumira-e2e@example.invalid',
    authEnabled: false,
    startTlsEnabled: false,
    sslEnabled: false,
  },
});

console.log('[fixture] Enabling the built-in mock payment provider when needed.');
let availablePlugins = await api(organizerToken, '/api/v2/plugins/current/available');
if (!availablePlugins.some((item) => item.pluginCode === 'builtin-mock-payment')) {
  await api(organizerToken, '/api/v2/plugins/enable', {
    method: 'POST',
    body: { pluginCode: 'builtin-mock-payment', version: '1.0.0', configJson: '{}' },
  });
  availablePlugins = await waitFor(
    async () => {
      const plugins = await api(organizerToken, '/api/v2/plugins/current/available');
      return plugins.some((item) => item.pluginCode === 'builtin-mock-payment') ? plugins : null;
    },
    'built-in mock payment provider',
  );
}

console.log('[fixture] Resolving the lifecycle participant account.');
const participantUsers = await api(
  organizerToken,
  `/api/v2/iam/users?username=${encodeURIComponent(participantUsername)}&pageNo=1&pageSize=20`,
);
let participantUser = (participantUsers?.records || []).find((item) => item.username === participantUsername);
if (!participantUser) {
  if (!participantInitialPassword) {
    throw new Error('Set LIFECYCLE_PARTICIPANT_INITIAL_PASSWORD to create the isolated participant account');
  }
  const roles = await api(organizerToken, '/api/v2/iam/roles?roleCode=commonuser&pageNo=1&pageSize=20');
  const participantRole = (roles?.records || []).find((item) => item.roleCode === 'commonuser');
  if (!participantRole?.id) throw new Error('The commonuser role is missing from the E2E database');
  participantUser = await api(organizerToken, '/api/v2/iam/users', {
    method: 'POST',
    body: {
      username: participantUsername,
      password: participantInitialPassword,
      nickname: participantName,
      realName: participantName,
      email: 'e2e-participant@example.invalid',
      status: 'ENABLED',
      roleIds: [participantRole.id],
      deptIds: [],
    },
  });
}

await resolvePassword(participantUsername, participantInitialPassword, finalPassword);

console.log('[fixture] Resolving the lifecycle expert account.');
const users = await api(
  organizerToken,
  `/api/v2/iam/users?username=${encodeURIComponent(expertUsername)}&pageNo=1&pageSize=20`,
);
let expertUser = (users?.records || []).find((item) => item.username === expertUsername);
if (!expertUser) {
  if (!expertInitialPassword) {
    throw new Error('Set LIFECYCLE_EXPERT_INITIAL_PASSWORD to create the isolated expert account');
  }
  const roles = await api(organizerToken, '/api/v2/iam/roles?roleCode=EXPERT&pageNo=1&pageSize=20');
  const expertRole = (roles?.records || []).find((item) => item.roleCode === 'EXPERT');
  if (!expertRole?.id) throw new Error('The EXPERT role is missing from the E2E database');
  expertUser = await api(organizerToken, '/api/v2/iam/users', {
    method: 'POST',
    body: {
      username: expertUsername,
      password: expertInitialPassword,
      nickname: expertName,
      realName: expertName,
      email: 'e2e-expert@example.invalid',
      status: 'ENABLED',
      roleIds: [expertRole.id],
      deptIds: [],
    },
  });
}

const expertLogin = await resolvePassword(expertUsername, expertInitialPassword, finalPassword);
const expertToken = expertLogin.accessToken;
const expertUserId = expertUser.id ?? expertUser.userId;
if (!expertUserId) throw new Error(`Lifecycle expert user id is missing: ${safeBody(expertUser)}`);

console.log('[fixture] Ensuring the expert application is approved.');
let expertPage = await api(
  organizerToken,
  `/api/v2/experts?keyword=${encodeURIComponent(expertName)}&pageNo=1&pageSize=100`,
);
let expert = (expertPage?.records || []).find((item) => item.name === expertName && item.userId === expertUserId);
if (!expert) {
  expert = await api(expertToken, '/api/v2/experts', {
    method: 'POST',
    body: {
      name: expertName,
      title: '高级评审',
      organization: 'Lumira E2E',
      position: '评审专家',
      expertise: '软件工程,产品设计',
      email: 'e2e-expert@example.invalid',
      tags: '评审专家',
      status: 'active',
      sort: 1,
    },
  });
}

if (expert.approvalStatus !== 'APPROVED') {
  const task = await waitFor(async () => {
    const tasks = await api(organizerToken, '/api/v2/workflows/tasks/my?status=PENDING&pageNo=1&pageSize=100');
    return (tasks?.records || []).find(
      (item) => item.businessType === 'EXPERT_APPLICATION' && item.businessId === expert.id,
    );
  }, 'expert approval task');
  await api(organizerToken, `/api/v2/workflows/tasks/${task.id}/approve`, {
    method: 'POST',
    body: { comment: 'Lumira lifecycle E2E fixture approval' },
  });
  expert = await waitFor(async () => {
    const current = await api(organizerToken, `/api/v2/experts/${expert.id}`);
    return current.approvalStatus === 'APPROVED' && current.status === 'active' ? current : null;
  }, 'approved expert projection');
}

console.log(`LIFECYCLE_FIXTURE_READY ${JSON.stringify({
  baseUrl: base.origin,
  organizerUsername,
  participantUsername,
  expertUsername,
  expertId: expert.id,
  pluginCount: availablePlugins.length,
  smtpHost,
  smtpPort,
})}`);
