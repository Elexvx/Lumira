import type { FormInstance } from 'antd';
import { useCallback, useMemo, useState, type DragEvent, type Key } from 'react';
import { Space, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { CheckOutlined, HolderOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  PasskeySettings,
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';
import { TableActionBar } from '@/features/table/TableActionBar';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { useAuthenticatorConfigDrawer } from './useAuthenticatorConfigDrawer';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type AuthenticatorCode = 'passkey_login' | 'sms_login' | 'email_login' | 'wechat_login' | 'password_login';
type ConfigDrawerMode = 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | 'basic';
type LoginMode = 'passkey' | 'sms' | 'email' | 'wechat' | 'password';

interface AuthenticatorRecord {
  key: AuthenticatorCode;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
  configured: boolean;
}

const SMS_ACCESS_KEY_SECRET_MASK = '********';
const WECHAT_APP_SECRET_MASK = '********';
const DEFAULT_LOGIN_MODE_ORDER: LoginMode[] = ['passkey', 'sms', 'email', 'wechat', 'password'];

type AuthenticatorDeletionDeps = {
  canManageSettings: boolean;
  verificationForm: FormInstance<VerificationSettings>;
  smsSettingsForm: FormInstance<SmsVerificationSettings>;
  passkeySettingsForm: FormInstance<PasskeySettings & { allowedOriginsText?: string }>;
  wechatSettingsForm: FormInstance<WechatLoginSettings>;
  passkeySettingsData?: PasskeySettings;
  smsSettingsData?: SmsVerificationSettings;
  verificationSettingsData?: VerificationSettings;
  wechatSettingsData?: WechatLoginSettings;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
  onVerificationSettingsRefetch: () => Promise<unknown>;
  onSmsSettingsRefetch: () => Promise<unknown>;
  onSmtpSettingsRefetch: () => Promise<unknown>;
  onWechatSettingsRefetch: () => Promise<unknown>;
  onPasskeySettingsRefetch: () => Promise<unknown>;
};

const getEnabledAuthenticatorKeys = (
  passkeySettingsData?: PasskeySettings,
  smsSettingsData?: SmsVerificationSettings,
  verificationSettingsData?: VerificationSettings,
  wechatSettingsData?: WechatLoginSettings,
) => [
  passkeySettingsData?.enabled ? 'passkey_login' : null,
  smsSettingsData?.enabled ? 'sms_login' : null,
  verificationSettingsData?.emailLoginEnabled ? 'email_login' : null,
  wechatSettingsData?.enabled ? 'wechat_login' : null,
  (verificationSettingsData?.passwordLoginEnabled ?? true) ? 'password_login' : null,
].filter(Boolean) as AuthenticatorCode[];

const canRemoveAuthenticators = (selectedKeys: AuthenticatorCode[], enabledKeys: AuthenticatorCode[]) => {
  if (enabledKeys.length <= 1 && enabledKeys.some((key) => selectedKeys.includes(key))) {
    return false;
  }
  if (enabledKeys.length > 0 && enabledKeys.every((key) => selectedKeys.includes(key))) {
    return false;
  }
  return true;
};

const disableSmsAuthenticator = async ({
  canManageSettings,
  smsSettingsForm,
  onSmsSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'smsSettingsForm' | 'onSmsSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
    method: 'PUT',
    data: { enabled: false },
    ...API_OPTS.NO_REDIRECT,
  });
  smsSettingsForm.setFieldsValue({
    ...result,
    accessKeySecret: result.accessKeySecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
  });
  message.success(t('短信认证器已停用', 'SMS authenticator disabled'));
  await onSmsSettingsRefetch();
};

const disableVerificationSettingsAuthenticator = async (
  key: Extract<AuthenticatorCode, 'email_login' | 'password_login'>,
  {
    canManageSettings,
    verificationForm,
    onVerificationSettingsRefetch,
  }: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'verificationForm' | 'onVerificationSettingsRefetch'>,
) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<VerificationSettings>('/v1/system/verification/settings', {
    method: 'PUT',
    data: key === 'email_login' ? { emailLoginEnabled: false } : { passwordLoginEnabled: false },
    ...API_OPTS.NO_REDIRECT,
  });
  verificationForm.setFieldsValue(result);
  message.success(key === 'email_login' ? t('邮箱认证器已停用', 'Email authenticator disabled') : t('密码登录已停用', 'Password sign-in disabled'));
  await onVerificationSettingsRefetch();
};

const disablePasskeyAuthenticator = async ({
  canManageSettings,
  passkeySettingsForm,
  onPasskeySettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'passkeySettingsForm' | 'onPasskeySettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<PasskeySettings>('/v1/system/verification/passkey-settings', {
    method: 'PUT',
    data: { enabled: false },
    ...API_OPTS.NO_REDIRECT,
  });
  passkeySettingsForm.setFieldsValue({
    ...result,
    allowedOriginsText: result.allowedOrigins?.join('\n') || '',
  });
  message.success(t('通行密钥已停用', 'Passkey disabled'));
  await onPasskeySettingsRefetch();
};

const disableWechatAuthenticator = async ({
  canManageSettings,
  wechatSettingsForm,
  onWechatSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'wechatSettingsForm' | 'onWechatSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
    method: 'PUT',
    data: { enabled: false },
    ...API_OPTS.NO_REDIRECT,
  });
  wechatSettingsForm.setFieldsValue({
    ...result,
    appSecret: result.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
  });
  message.success(t('微信认证器已停用', 'WeChat authenticator disabled'));
  await onWechatSettingsRefetch();
};

const resetSmsAuthenticator = async ({
  canManageSettings,
  smsSettingsForm,
  onSmsSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'smsSettingsForm' | 'onSmsSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
    method: 'DELETE',
    ...API_OPTS.NO_REDIRECT,
  });
  smsSettingsForm.setFieldsValue({
    ...result,
    accessKeySecret: '',
  });
  await onSmsSettingsRefetch();
};

const resetEmailAuthenticator = async ({
  canManageSettings,
  verificationForm,
  onVerificationSettingsRefetch,
  onSmtpSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'verificationForm' | 'onVerificationSettingsRefetch' | 'onSmtpSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const verificationResult = await request<VerificationSettings>('/v1/system/verification/settings', {
    method: 'PUT',
    data: { emailLoginEnabled: false },
    ...API_OPTS.NO_REDIRECT,
  });
  verificationForm.setFieldsValue(verificationResult);
  await request<SmtpSettings>('/v1/system/smtp-settings', {
    method: 'DELETE',
    ...API_OPTS.NO_REDIRECT,
  });
  await Promise.all([onVerificationSettingsRefetch(), onSmtpSettingsRefetch()]);
};

const resetPasskeyAuthenticator = async ({
  canManageSettings,
  passkeySettingsForm,
  onPasskeySettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'passkeySettingsForm' | 'onPasskeySettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<PasskeySettings>('/v1/system/verification/passkey-settings', {
    method: 'DELETE',
    ...API_OPTS.NO_REDIRECT,
  });
  passkeySettingsForm.setFieldsValue({
    ...result,
    allowedOriginsText: result.allowedOrigins?.join('\n') || '',
  });
  await onPasskeySettingsRefetch();
};

const resetWechatAuthenticator = async ({
  canManageSettings,
  wechatSettingsForm,
  onWechatSettingsRefetch,
}: Pick<AuthenticatorDeletionDeps, 'canManageSettings' | 'wechatSettingsForm' | 'onWechatSettingsRefetch'>) => {
  if (!canManageSettings) {
    return;
  }
  const result = await request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
    method: 'DELETE',
    ...API_OPTS.NO_REDIRECT,
  });
  wechatSettingsForm.setFieldsValue({
    ...result,
    appSecret: '',
  });
  await onWechatSettingsRefetch();
};

const resolveLoginModeFromAuthenticatorKey = (key: AuthenticatorCode) => {
  if (key === 'passkey_login') {
    return 'passkey';
  }
  if (key === 'sms_login') {
    return 'sms';
  }
  if (key === 'email_login') {
    return 'email';
  }
  if (key === 'wechat_login') {
    return 'wechat';
  }
  return 'password';
};

const isLoginMode = (mode?: string | null): mode is LoginMode =>
  mode === 'passkey' || mode === 'sms' || mode === 'email' || mode === 'wechat' || mode === 'password';

const normalizeExistingLoginModeOrder = (order?: string[]): LoginMode[] => {
  const result: LoginMode[] = [];
  (order?.length ? order : DEFAULT_LOGIN_MODE_ORDER).forEach((item) => {
    if (isLoginMode(item) && !result.includes(item)) {
      result.push(item);
    }
  });
  return result.length ? result : DEFAULT_LOGIN_MODE_ORDER;
};

const appendLoginMode = (order: string[] | undefined, mode: LoginMode) => {
  const existing = normalizeExistingLoginModeOrder(order);
  return existing.includes(mode) ? existing : [...existing, mode];
};

const removeLoginMode = (order: string[] | undefined, mode: LoginMode) =>
  normalizeExistingLoginModeOrder(order).filter((item) => item !== mode);

const updateLoginModeOrder = async (loginModeOrder: LoginMode[], deps: Pick<AuthenticatorDeletionDeps, 'verificationForm' | 'onVerificationSettingsRefetch'>) => {
  const result = await request<VerificationSettings>('/v1/system/verification/settings', {
    method: 'PUT',
    data: { loginModeOrder },
    ...API_OPTS.NO_REDIRECT,
  });
  deps.verificationForm.setFieldsValue(result);
  await deps.onVerificationSettingsRefetch();
  return result;
};

const enableConfiguredAuthenticator = async (record: AuthenticatorRecord, deps: AuthenticatorDeletionDeps) => {
  if (!deps.canManageSettings) {
    return false;
  }
  if (!record.configured && record.key !== 'password_login' && record.key !== 'passkey_login') {
    return false;
  }

  if (record.key === 'sms_login') {
    const result = await request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
      method: 'PUT',
      data: { enabled: true },
      ...API_OPTS.NO_REDIRECT,
    });
    deps.smsSettingsForm.setFieldsValue({
      ...result,
      accessKeySecret: result.accessKeySecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
    });
    await deps.onSmsSettingsRefetch();
    message.success(t('短信认证器已启用', 'SMS authenticator enabled'));
    return true;
  }

  if (record.key === 'email_login' || record.key === 'password_login') {
    const result = await request<VerificationSettings>('/v1/system/verification/settings', {
      method: 'PUT',
      data: record.key === 'email_login' ? { emailLoginEnabled: true } : { passwordLoginEnabled: true },
      ...API_OPTS.NO_REDIRECT,
    });
    deps.verificationForm.setFieldsValue(result);
    await deps.onVerificationSettingsRefetch();
    message.success(record.key === 'email_login' ? t('邮箱认证器已启用', 'Email authenticator enabled') : t('密码登录已启用', 'Password sign-in enabled'));
    return true;
  }

  if (record.key === 'passkey_login') {
    const result = await request<PasskeySettings>('/v1/system/verification/passkey-settings', {
      method: 'PUT',
      data: { enabled: true, passwordlessEnabled: true },
      ...API_OPTS.NO_REDIRECT,
    });
    deps.passkeySettingsForm.setFieldsValue({
      ...result,
      allowedOriginsText: result.allowedOrigins?.join('\n') || '',
    });
    await deps.onPasskeySettingsRefetch();
    message.success(t('通行密钥已启用', 'Passkey enabled'));
    return true;
  }

  const result = await request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
    method: 'PUT',
    data: { enabled: true },
    ...API_OPTS.NO_REDIRECT,
  });
  deps.wechatSettingsForm.setFieldsValue({
    ...result,
    appSecret: result.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
  });
  await deps.onWechatSettingsRefetch();
  message.success(t('微信认证器已启用', 'WeChat authenticator enabled'));
  return true;
};

const buildAddAuthenticatorItems = ({
  canManageSettings,
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
  existingLoginModes = [],
  onEnableAuthenticator,
}: {
  canManageSettings: boolean;
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
  existingLoginModes: LoginMode[];
  onEnableAuthenticator: (mode: 'passkey' | 'sms' | 'email' | 'wechat' | 'password') => void;
}) =>
  [
    { key: 'passkey', label: t('通行密钥', 'Passkey'), enabled: passkeyEnabled, mode: 'passkey' as const },
    { key: 'sms', label: t('短信', 'SMS'), enabled: smsEnabled, mode: 'sms' as const },
    { key: 'email', label: t('邮箱', 'Email'), enabled: emailEnabled, mode: 'email' as const },
    { key: 'wechat', label: t('微信', 'WeChat'), enabled: wechatEnabled, mode: 'wechat' as const },
    { key: 'password', label: t('密码', 'Password'), enabled: passwordEnabled, mode: 'password' as const },
  ]
    .filter((item) => !existingLoginModes.includes(item.mode))
    .map((item) => ({
      key: item.key,
      label: item.label,
      disabled: !canManageSettings,
      onClick: () => void onEnableAuthenticator(item.mode),
    }));

const disableAuthenticatorByKey = async (key: AuthenticatorCode, deps: AuthenticatorDeletionDeps) => {
  const enabledKeys = getEnabledAuthenticatorKeys(deps.passkeySettingsData, deps.smsSettingsData, deps.verificationSettingsData, deps.wechatSettingsData);
  if (!canRemoveAuthenticators([key], enabledKeys)) {
    message.warning(t('至少需要保留一种可用登录方式', 'At least one login method must remain enabled'));
    return false;
  }

  if (key === 'sms_login') {
    await disableSmsAuthenticator({
      canManageSettings: deps.canManageSettings,
      smsSettingsForm: deps.smsSettingsForm,
      onSmsSettingsRefetch: deps.onSmsSettingsRefetch,
    });
    return true;
  }
  if (key === 'email_login') {
    await disableVerificationSettingsAuthenticator(key, {
      canManageSettings: deps.canManageSettings,
      verificationForm: deps.verificationForm,
      onVerificationSettingsRefetch: deps.onVerificationSettingsRefetch,
    });
    return true;
  }
  if (key === 'passkey_login') {
    await disablePasskeyAuthenticator({
      canManageSettings: deps.canManageSettings,
      passkeySettingsForm: deps.passkeySettingsForm,
      onPasskeySettingsRefetch: deps.onPasskeySettingsRefetch,
    });
    return true;
  }
  if (key === 'wechat_login') {
    await disableWechatAuthenticator({
      canManageSettings: deps.canManageSettings,
      wechatSettingsForm: deps.wechatSettingsForm,
      onWechatSettingsRefetch: deps.onWechatSettingsRefetch,
    });
    return true;
  }
  await disableVerificationSettingsAuthenticator(key, {
    canManageSettings: deps.canManageSettings,
    verificationForm: deps.verificationForm,
    onVerificationSettingsRefetch: deps.onVerificationSettingsRefetch,
  });
  return true;
};

const deleteAuthenticatorByKey = async (key: AuthenticatorCode, deps: AuthenticatorDeletionDeps) => {
  const enabledKeys = getEnabledAuthenticatorKeys(deps.passkeySettingsData, deps.smsSettingsData, deps.verificationSettingsData, deps.wechatSettingsData);
  if (!canRemoveAuthenticators([key], enabledKeys)) {
    message.warning(t('至少需要保留一种可用登录方式', 'At least one login method must remain enabled'));
    return;
  }
  if (key === 'sms_login') {
    await resetSmsAuthenticator({
      canManageSettings: deps.canManageSettings,
      smsSettingsForm: deps.smsSettingsForm,
      onSmsSettingsRefetch: deps.onSmsSettingsRefetch,
    });
  } else if (key === 'email_login') {
    await resetEmailAuthenticator({
      canManageSettings: deps.canManageSettings,
      verificationForm: deps.verificationForm,
      onVerificationSettingsRefetch: deps.onVerificationSettingsRefetch,
      onSmtpSettingsRefetch: deps.onSmtpSettingsRefetch,
    });
  } else if (key === 'passkey_login') {
    await resetPasskeyAuthenticator({
      canManageSettings: deps.canManageSettings,
      passkeySettingsForm: deps.passkeySettingsForm,
      onPasskeySettingsRefetch: deps.onPasskeySettingsRefetch,
    });
  } else if (key === 'wechat_login') {
    await resetWechatAuthenticator({
      canManageSettings: deps.canManageSettings,
      wechatSettingsForm: deps.wechatSettingsForm,
      onWechatSettingsRefetch: deps.onWechatSettingsRefetch,
    });
  } else {
    await disableVerificationSettingsAuthenticator(key, {
      canManageSettings: deps.canManageSettings,
      verificationForm: deps.verificationForm,
      onVerificationSettingsRefetch: deps.onVerificationSettingsRefetch,
    });
  }
  const nextOrder = removeLoginMode(deps.verificationForm.getFieldValue('loginModeOrder') as string[] | undefined, resolveLoginModeFromAuthenticatorKey(key));
  await updateLoginModeOrder(nextOrder, deps);
  message.success(t('认证器已删除', 'Authenticator deleted'));
};

const deleteSelectedAuthenticators = async (selectedAuthenticatorKeys: Key[], deps: AuthenticatorDeletionDeps) => {
  if (!selectedAuthenticatorKeys.length) {
    message.info(t('请先选择要删除的认证器', 'Please select authenticators to delete first'));
    return;
  }

  const selectedKeys = new Set(selectedAuthenticatorKeys as AuthenticatorCode[]);
  const enabledKeys = getEnabledAuthenticatorKeys(deps.passkeySettingsData, deps.smsSettingsData, deps.verificationSettingsData, deps.wechatSettingsData);
  if (!canRemoveAuthenticators(Array.from(selectedKeys), enabledKeys)) {
    message.warning(t('至少需要保留一种可用登录方式', 'At least one login method must remain enabled'));
    return;
  }

  const deletionOrder: AuthenticatorCode[] = ['sms_login', 'email_login', 'passkey_login', 'wechat_login', 'password_login'];
  const keysToDelete = deletionOrder.filter((key): key is AuthenticatorCode => selectedKeys.has(key));
  for (const key of keysToDelete) {
    await deleteAuthenticatorByKey(key, deps);
  }
};

const resolveAuthenticatorDrawerMode = (key: AuthenticatorCode): ConfigDrawerMode => {
  if (key === 'sms_login') {
    return 'sms';
  }
  if (key === 'passkey_login') {
    return 'passkey';
  }
  if (key === 'email_login') {
    return 'email';
  }
  if (key === 'wechat_login') {
    return 'wechat';
  }
  return 'basic';
};

const buildAuthenticatorRowMap = ({
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
  passkeyConfigured,
  smsConfigured,
  emailConfigured,
  wechatConfigured,
  passwordConfigured,
}: {
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
  passkeyConfigured: boolean;
  smsConfigured: boolean;
  emailConfigured: boolean;
  wechatConfigured: boolean;
  passwordConfigured: boolean;
}): Record<LoginMode, Omit<AuthenticatorRecord, 'order'>> => ({
  passkey: {
    key: 'passkey_login',
    identifier: t('通行密钥', 'Passkey'),
    type: t('通行密钥', 'Passkey'),
    title: t('通行密钥', 'Passkey'),
    description: t('使用系统钥匙串或密码管理器进行 WebAuthn 验证', 'Use your system keychain or password manager for WebAuthn verification'),
    enabled: passkeyEnabled,
    configured: passkeyConfigured,
  },
  sms: {
    key: 'sms_login',
    identifier: t('短信', 'SMS'),
    type: t('短信', 'SMS'),
    title: t('短信验证', 'SMS verification'),
    description: t('使用短信验证码登录', 'Sign in with an SMS code'),
    enabled: smsEnabled,
    configured: smsConfigured,
  },
  email: {
    key: 'email_login',
    identifier: t('邮箱', 'Email'),
    type: t('邮箱', 'Email'),
    title: t('邮箱验证码', 'Email verification code'),
    description: t('使用邮箱验证码登录', 'Sign in with an email code'),
    enabled: emailEnabled,
    configured: emailConfigured,
  },
  wechat: {
    key: 'wechat_login',
    identifier: t('微信', 'WeChat'),
    type: t('微信', 'WeChat'),
    title: t('微信扫码登录', 'WeChat QR sign-in'),
    description: t('使用微信扫码登录，未注册用户可自动创建账号', 'Sign in with WeChat QR code; unregistered users can be auto-created'),
    enabled: wechatEnabled,
    configured: wechatConfigured,
  },
  password: {
    key: 'password_login',
    identifier: t('密码', 'Password'),
    type: t('密码', 'Password'),
    title: t('账号密码登录', 'Username/password sign-in'),
    description: t('使用账号密码登录', 'Sign in with username and password'),
    enabled: passwordEnabled,
    configured: passwordConfigured,
  },
});

const buildAuthenticatorRows = ({
  configuredLoginModeOrder,
  passkeyEnabled,
  smsEnabled,
  emailEnabled,
  wechatEnabled,
  passwordEnabled,
  passkeyConfigured,
  smsConfigured,
  emailConfigured,
  wechatConfigured,
  passwordConfigured,
}: {
  configuredLoginModeOrder?: string[];
  passkeyEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  wechatEnabled: boolean;
  passwordEnabled: boolean;
  passkeyConfigured: boolean;
  smsConfigured: boolean;
  emailConfigured: boolean;
  wechatConfigured: boolean;
  passwordConfigured: boolean;
}): AuthenticatorRecord[] => {
  const rowsByMode = buildAuthenticatorRowMap({
    passkeyEnabled,
    smsEnabled,
    emailEnabled,
    wechatEnabled,
    passwordEnabled,
    passkeyConfigured,
    smsConfigured,
    emailConfigured,
    wechatConfigured,
    passwordConfigured,
  });

  return normalizeExistingLoginModeOrder(configuredLoginModeOrder).map((mode, index) => ({
    ...rowsByMode[mode],
    order: index + 1,
  }));
};

const buildAuthenticatorOrderColumn = ({
  tokenColorTextSecondary,
  isMobile,
}: {
  tokenColorTextSecondary: string;
  isMobile: boolean;
}): ProColumns<AuthenticatorRecord> => ({
  title: '',
  dataIndex: 'order',
  width: 'var(--saas-spacing-96)',
  search: false,
  render: (_, record) => (
    <Space size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)}>
      <HolderOutlined style={{ color: tokenColorTextSecondary }} />
      <Typography.Text>{record.order}</Typography.Text>
    </Space>
  ),
});

const buildAuthenticatorIdentityColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: t('认证标识', 'Authenticator ID'),
  dataIndex: 'identifier',
  width: 'var(--saas-spacing-180)',
  search: false,
});

const buildAuthenticatorTypeColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: t('认证类型', 'Authenticator type'),
  dataIndex: 'type',
  width: 'var(--saas-spacing-160)',
  search: false,
  render: (_, record) => <Tag>{record.type}</Tag>,
});

const buildAuthenticatorTitleColumn = (): ProColumns<AuthenticatorRecord> => ({
  title: t('标题', 'Title'),
  dataIndex: 'title',
  width: 'var(--saas-spacing-180)',
  search: false,
});

const buildAuthenticatorStatusColumn = ({
  tokenColorSuccess,
}: {
  tokenColorSuccess: string;
}): ProColumns<AuthenticatorRecord> => ({
  title: t('启用', 'Enabled'),
  dataIndex: 'enabled',
  width: 'var(--saas-spacing-120)',
  align: 'center',
  search: false,
  render: (_, record) => (record.enabled ? <CheckOutlined style={{ color: tokenColorSuccess }} /> : null),
});

type BuildAuthenticatorColumnsParams = {
  canManageSettings: boolean;
  isMobile: boolean;
  tokenColorSuccess: string;
  tokenColorTextSecondary: string;
  togglingAuthenticatorKey: AuthenticatorCode | null;
  onToggleAuthenticator: (record: AuthenticatorRecord) => void;
  onOpenConfigDrawer: (mode: ConfigDrawerMode) => void;
  onDeleteAuthenticator: (key: AuthenticatorCode) => void;
};

type AuthenticatorColumnsActionsParams = Pick<
  BuildAuthenticatorColumnsParams,
  'canManageSettings' | 'isMobile' | 'togglingAuthenticatorKey' | 'onToggleAuthenticator' | 'onOpenConfigDrawer' | 'onDeleteAuthenticator'
>;

type AuthenticatorActionItem = {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  loading?: boolean;
  onClick: () => void;
};

type BuildAuthenticatorActionItemsParams = {
  canManageSettings: boolean;
  togglingAuthenticatorKey: AuthenticatorCode | null;
  onToggleAuthenticator: (record: AuthenticatorRecord) => void;
  onOpenConfigDrawer: (mode: ConfigDrawerMode) => void;
  onDeleteAuthenticator: (key: AuthenticatorCode) => void;
};

const buildAuthenticatorToggleActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'toggle',
  label: record.enabled ? t('禁用', 'Disable') : t('启用', 'Enable'),
  danger: record.enabled,
  disabled: !params.canManageSettings,
  loading: params.togglingAuthenticatorKey === record.key,
  onClick: () => void params.onToggleAuthenticator(record),
});

const buildAuthenticatorConfigActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'config',
  label: t('配置', 'Configure'),
  disabled: !params.canManageSettings,
  onClick: () => params.onOpenConfigDrawer(resolveAuthenticatorDrawerMode(record.key)),
});

const buildAuthenticatorDeleteActionItem = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem => ({
  key: 'delete',
  label: t('删除', 'Delete'),
  danger: true,
  disabled: !params.canManageSettings,
  onClick: () => void params.onDeleteAuthenticator(record.key),
});

const buildAuthenticatorActionItems = (
  record: AuthenticatorRecord,
  params: BuildAuthenticatorActionItemsParams,
): AuthenticatorActionItem[] => [
  buildAuthenticatorToggleActionItem(record, params),
  buildAuthenticatorConfigActionItem(record, params),
  buildAuthenticatorDeleteActionItem(record, params),
];

const buildAuthenticatorActionColumn = ({
  canManageSettings,
  isMobile,
  togglingAuthenticatorKey,
  onToggleAuthenticator,
  onOpenConfigDrawer,
  onDeleteAuthenticator,
}: AuthenticatorColumnsActionsParams): ProColumns<AuthenticatorRecord> => ({
  title: t('操作', 'Actions'),
  valueType: 'option',
  fixed: 'right',
  width: 'var(--saas-spacing-240)',
  render: (_, record) => (
    <TableActionBar
      isMobile={isMobile}
      inlineCount={isMobile ? 0 : 3}
      items={buildAuthenticatorActionItems(record, {
        canManageSettings,
        togglingAuthenticatorKey,
        onToggleAuthenticator,
        onOpenConfigDrawer,
        onDeleteAuthenticator,
      })}
    />
  ),
});

const buildAuthenticatorColumns = ({
  canManageSettings,
  isMobile,
  tokenColorSuccess,
  tokenColorTextSecondary,
  togglingAuthenticatorKey,
  onToggleAuthenticator,
  onOpenConfigDrawer,
  onDeleteAuthenticator,
}: BuildAuthenticatorColumnsParams): ProColumns<AuthenticatorRecord>[] => [
  buildAuthenticatorOrderColumn({ tokenColorTextSecondary, isMobile }),
  buildAuthenticatorIdentityColumn(),
  buildAuthenticatorTypeColumn(),
  buildAuthenticatorTitleColumn(),
  buildAuthenticatorStatusColumn({ tokenColorSuccess }),
  buildAuthenticatorActionColumn({
    canManageSettings,
    isMobile,
    togglingAuthenticatorKey,
    onToggleAuthenticator,
    onOpenConfigDrawer,
    onDeleteAuthenticator,
  }),
];

interface UseAuthenticatorManagementParams {
  canManageSettings: boolean;
  isMobile: boolean;
  tokenColorSuccess: string;
  tokenColorTextSecondary: string;
  verificationForm: FormInstance<VerificationSettings>;
  smsSettingsForm: FormInstance<SmsVerificationSettings>;
  smtpSettingsForm: FormInstance<SmtpSettings>;
  smtpTestForm: FormInstance<SmtpTestPayload>;
  wechatSettingsForm: FormInstance<WechatLoginSettings>;
  passkeySettingsForm: FormInstance<PasskeySettings & { allowedOriginsText?: string }>;
  verificationSettingsData?: VerificationSettings;
  smsSettingsData?: SmsVerificationSettings;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
  wechatSettingsData?: WechatLoginSettings;
  passkeySettingsData?: PasskeySettings;
  verificationLoading: boolean;
  onVerificationSettingsRefetch: () => Promise<unknown>;
  onSmsSettingsRefetch: () => Promise<unknown>;
  onSmtpSettingsRefetch: () => Promise<unknown>;
  onWechatSettingsRefetch: () => Promise<unknown>;
  onPasskeySettingsRefetch: () => Promise<unknown>;
}

export const useAuthenticatorManagement = ({
  canManageSettings,
  isMobile,
  tokenColorSuccess,
  tokenColorTextSecondary,
  verificationForm,
  smsSettingsForm,
  smtpSettingsForm,
  smtpTestForm,
  wechatSettingsForm,
  passkeySettingsForm,
  verificationSettingsData,
  smsSettingsData,
  smtpSettingsData,
  wechatSettingsData,
  passkeySettingsData,
  verificationLoading,
  onVerificationSettingsRefetch,
  onSmsSettingsRefetch,
  onSmtpSettingsRefetch,
  onWechatSettingsRefetch,
  onPasskeySettingsRefetch,
}: UseAuthenticatorManagementParams) => {
  const [selectedAuthenticatorKeys, setSelectedAuthenticatorKeys] = useState<Key[]>([]);
  const [reorderingAuthenticators, setReorderingAuthenticators] = useState(false);
  const handleAuthenticatorSaved = useCallback(
    async (mode: LoginMode) => {
      const nextOrder = appendLoginMode(verificationForm.getFieldValue('loginModeOrder') as string[] | undefined, mode);
      await updateLoginModeOrder(nextOrder, { verificationForm, onVerificationSettingsRefetch });
    },
    [onVerificationSettingsRefetch, verificationForm],
  );

  const {
    drawerState,
    saveState: _saveState,
    drawerFooter,
    drawerContent,
  } = useAuthenticatorConfigDrawer({
    canManageSettings,
    verificationForm,
    smsSettingsForm,
    wechatSettingsForm,
    passkeySettingsForm,
    verificationSettingsData,
    smsSettingsData,
    wechatSettingsData,
    passkeySettingsData,
    verificationLoading,
    onVerificationSettingsRefetch,
    onSmsSettingsRefetch,
    smtpSettingsForm,
    smtpTestForm,
    smtpSettingsData,
    onSmtpSettingsRefetch,
    onWechatSettingsRefetch,
    onPasskeySettingsRefetch,
    onAuthenticatorSaved: handleAuthenticatorSaved,
  });
  const configuredLoginModeOrder = useMemo(
    () =>
      verificationSettingsData?.loginModeOrder ??
      (verificationForm.getFieldValue('loginModeOrder') as string[] | undefined) ??
      ['passkey', 'sms', 'email', 'wechat', 'password'],
    [verificationForm, verificationSettingsData?.loginModeOrder],
  );
  const authenticatorRows = useMemo<AuthenticatorRecord[]>(
    () =>
      buildAuthenticatorRows({
        configuredLoginModeOrder,
        passkeyEnabled: Boolean(passkeySettingsData?.enabled),
        smsEnabled: Boolean(smsSettingsData?.enabled),
        emailEnabled: Boolean(verificationSettingsData?.emailLoginEnabled),
        wechatEnabled: Boolean(wechatSettingsData?.enabled),
        passwordEnabled: Boolean(verificationSettingsData?.passwordLoginEnabled ?? true),
        passkeyConfigured: Boolean(passkeySettingsData?.rpId || passkeySettingsData?.rpName || passkeySettingsData?.allowedOrigins?.length),
        smsConfigured: Boolean(smsSettingsData?.configured),
        emailConfigured: Boolean(smtpSettingsData?.configured),
        wechatConfigured: Boolean(wechatSettingsData?.configured),
        passwordConfigured: true,
      }),
    [
      configuredLoginModeOrder,
      passkeySettingsData?.allowedOrigins,
      passkeySettingsData?.enabled,
      passkeySettingsData?.rpId,
      passkeySettingsData?.rpName,
      smsSettingsData?.configured,
      smsSettingsData?.enabled,
      smtpSettingsData?.configured,
      verificationSettingsData?.emailLoginEnabled,
      verificationSettingsData?.passwordLoginEnabled,
      wechatSettingsData?.configured,
      wechatSettingsData?.enabled,
    ],
  );

  const deletionDeps = useMemo(
    () => ({
      canManageSettings,
      verificationForm,
      smsSettingsForm,
      passkeySettingsForm,
      wechatSettingsForm,
      passkeySettingsData,
      smsSettingsData,
      verificationSettingsData,
      wechatSettingsData,
      smtpSettingsData,
      onVerificationSettingsRefetch,
      onSmsSettingsRefetch,
      onSmtpSettingsRefetch,
      onWechatSettingsRefetch,
      onPasskeySettingsRefetch,
    }),
    [
      canManageSettings,
      onPasskeySettingsRefetch,
      onSmsSettingsRefetch,
      onSmtpSettingsRefetch,
      onVerificationSettingsRefetch,
      onWechatSettingsRefetch,
      passkeySettingsData,
      passkeySettingsForm,
      smsSettingsData,
      smsSettingsForm,
      verificationForm,
      verificationSettingsData,
      wechatSettingsData,
      wechatSettingsForm,
      smtpSettingsData,
    ],
  );
  const handleDeleteAuthenticator = useCallback(
    async (key: Parameters<typeof deleteAuthenticatorByKey>[0]) => {
      await deleteAuthenticatorByKey(key, deletionDeps);
    },
    [deletionDeps],
  );
  const handleDeleteSelectedAuthenticators = useCallback(async () => {
    await deleteSelectedAuthenticators(selectedAuthenticatorKeys, deletionDeps);
  }, [deletionDeps, selectedAuthenticatorKeys]);

  const [draggedAuthenticatorKey, setDraggedAuthenticatorKey] = useState<AuthenticatorCode | null>(null);
  const handleAuthenticatorDragStart = useCallback(
    (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
      if (!canManageSettings || reorderingAuthenticators) {
        event.preventDefault();
        return;
      }
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', record.key);
      setDraggedAuthenticatorKey(record.key);
    },
    [canManageSettings, reorderingAuthenticators],
  );
  const handleAuthenticatorDragOver = useCallback(
    (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
      if (!draggedAuthenticatorKey || draggedAuthenticatorKey === record.key || !canManageSettings || reorderingAuthenticators) {
        return;
      }
      event.preventDefault();
    },
    [canManageSettings, draggedAuthenticatorKey, reorderingAuthenticators],
  );
  const handleAuthenticatorDrop = useCallback(
    (record: AuthenticatorRecord) => async (event: DragEvent<HTMLTableRowElement>) => {
      event.preventDefault();
      if (!draggedAuthenticatorKey || draggedAuthenticatorKey === record.key || !canManageSettings || reorderingAuthenticators) {
        setDraggedAuthenticatorKey(null);
        return;
      }
      const currentIndex = authenticatorRows.findIndex((row) => row.key === draggedAuthenticatorKey);
      const targetIndex = authenticatorRows.findIndex((row) => row.key === record.key);
      if (currentIndex < 0 || targetIndex < 0) {
        setDraggedAuthenticatorKey(null);
        return;
      }
      const nextRows = [...authenticatorRows];
      const [dragged] = nextRows.splice(currentIndex, 1);
      nextRows.splice(targetIndex, 0, dragged);
      setDraggedAuthenticatorKey(null);
      const loginModeOrder = nextRows.map((row) => resolveLoginModeFromAuthenticatorKey(row.key));
      setReorderingAuthenticators(true);
      try {
        const result = await request<VerificationSettings>('/v1/system/verification/settings', {
          method: 'PUT',
          data: { loginModeOrder },
          ...API_OPTS.NO_REDIRECT,
        });
        verificationForm.setFieldsValue(result);
        message.success('登录方式顺序已更新');
        await onVerificationSettingsRefetch();
      } finally {
        setReorderingAuthenticators(false);
      }
    },
    [authenticatorRows, canManageSettings, draggedAuthenticatorKey, onVerificationSettingsRefetch, reorderingAuthenticators, verificationForm],
  );
  const handleAuthenticatorDragEnd = useCallback(() => {
    setDraggedAuthenticatorKey(null);
  }, []);

  const [togglingAuthenticatorKey, setTogglingAuthenticatorKey] = useState<AuthenticatorCode | null>(null);
  const handleEnableAuthenticator = useCallback(
    (mode: 'sms' | 'email' | 'passkey' | 'wechat' | 'password') => {
      if (!canManageSettings) {
        return;
      }
      if (mode === 'sms') {
        smsSettingsForm.setFieldValue('enabled', true);
        drawerState.openConfigDrawer('sms');
        return;
      }
      if (mode === 'email') {
        verificationForm.setFieldValue('emailLoginEnabled', true);
        drawerState.openConfigDrawer('email');
        return;
      }
      if (mode === 'passkey') {
        passkeySettingsForm.setFieldsValue({ enabled: true, passwordlessEnabled: true });
        drawerState.openConfigDrawer('passkey');
        return;
      }
      if (mode === 'wechat') {
        wechatSettingsForm.setFieldValue('enabled', true);
        drawerState.openConfigDrawer('wechat');
        return;
      }
      verificationForm.setFieldValue('passwordLoginEnabled', true);
      drawerState.openConfigDrawer('basic');
    },
    [canManageSettings, drawerState, passkeySettingsForm, smsSettingsForm, verificationForm, wechatSettingsForm],
  );
  const handleToggleAuthenticator = useCallback(
    async (record: AuthenticatorRecord) => {
      if (!canManageSettings) {
        return;
      }
      setTogglingAuthenticatorKey(record.key);
      try {
        if (record.enabled) {
          await disableAuthenticatorByKey(record.key, deletionDeps);
          return;
        }
        const enabled = await enableConfiguredAuthenticator(record, deletionDeps);
        if (enabled) {
          return;
        }
        handleEnableAuthenticator(resolveLoginModeFromAuthenticatorKey(record.key));
      } finally {
        setTogglingAuthenticatorKey(null);
      }
    },
    [canManageSettings, deletionDeps, handleEnableAuthenticator],
  );
  const existingLoginModes = useMemo(
    () => authenticatorRows.map((row) => resolveLoginModeFromAuthenticatorKey(row.key)),
    [authenticatorRows],
  );

  const addAuthenticatorItems = useMemo(
    () =>
      buildAddAuthenticatorItems({
        canManageSettings,
        passkeyEnabled: Boolean(passkeySettingsData?.enabled),
        smsEnabled: Boolean(smsSettingsData?.enabled),
        emailEnabled: Boolean(verificationSettingsData?.emailLoginEnabled),
        wechatEnabled: Boolean(wechatSettingsData?.enabled),
        passwordEnabled: Boolean(verificationSettingsData?.passwordLoginEnabled ?? true),
        existingLoginModes,
        onEnableAuthenticator: handleEnableAuthenticator,
      }),
    [
      canManageSettings,
      existingLoginModes,
      handleEnableAuthenticator,
      passkeySettingsData?.enabled,
      smsSettingsData?.enabled,
      verificationSettingsData?.emailLoginEnabled,
      verificationSettingsData?.passwordLoginEnabled,
      wechatSettingsData?.enabled,
    ],
  );

  const authenticatorColumns = useMemo(
    () =>
      buildAuthenticatorColumns({
        canManageSettings,
        isMobile,
        tokenColorSuccess,
        tokenColorTextSecondary,
        togglingAuthenticatorKey,
        onToggleAuthenticator: handleToggleAuthenticator,
        onOpenConfigDrawer: drawerState.openConfigDrawer,
        onDeleteAuthenticator: handleDeleteAuthenticator,
      }),
    [
      canManageSettings,
      drawerState.openConfigDrawer,
      handleDeleteAuthenticator,
      handleToggleAuthenticator,
      isMobile,
      tokenColorSuccess,
      tokenColorTextSecondary,
      togglingAuthenticatorKey,
    ],
  );

  const tableProps = {
    canManageSettings,
    isMobile,
    verificationLoading,
    selectedAuthenticatorKeys,
    setSelectedAuthenticatorKeys,
    draggedAuthenticatorKey,
    reorderingAuthenticators,
    authenticatorRows,
    authenticatorColumns,
    onAuthenticatorDragStart: handleAuthenticatorDragStart,
    onAuthenticatorDragOver: handleAuthenticatorDragOver,
    onAuthenticatorDrop: handleAuthenticatorDrop,
    onAuthenticatorDragEnd: handleAuthenticatorDragEnd,
  };
  const toolbarProps = {
    addAuthenticatorItems,
    onDeleteSelectedAuthenticators: handleDeleteSelectedAuthenticators,
  };

  return {
    tablePack: {
      tableProps,
      toolbarProps,
      selectionPack: {
        selectedAuthenticatorKeys,
        setSelectedAuthenticatorKeys,
      },
      orderingPack: {
        draggedAuthenticatorKey,
        reorderingAuthenticators,
        authenticatorRows,
        handleAuthenticatorDragStart,
        handleAuthenticatorDragOver,
        handleAuthenticatorDrop,
        handleAuthenticatorDragEnd,
      },
      overviewPack: {
        togglingAuthenticatorKey,
        authenticatorColumns,
        addAuthenticatorItems,
      },
    },
    drawerPack: {
      drawerProps: {
        ...drawerState,
        ...drawerFooter,
        ...drawerContent,
      },
    },
  };
};
