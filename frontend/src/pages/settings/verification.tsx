import { DeleteOutlined, DownOutlined, PlusOutlined, HolderOutlined, CheckOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { history, useLocation } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import type { MenuProps } from 'antd';
import { Button, Card, Dropdown, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Tag, Typography, message, theme } from 'antd';
import type { DragEvent, Key } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import type { ManagementDrawerAction } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { systemService } from '@/services/system';
import type {
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
  PasskeySettings,
} from '@/types/api';

const TAB_KEYS = ['totp', 'sms', 'email', 'wechat', 'passkey'] as const;

type VerificationTabKey = (typeof TAB_KEYS)[number];
type SmsProviderCode = 'aliyun' | 'tencent' | 'mock' | 'custom';
type LoginModeCode = 'passkey' | 'sms' | 'email' | 'password';
type AuthenticatorCode = 'passkey_login' | 'sms_login' | 'email_login' | 'password_login';
type ConfigDrawerMode = VerificationTabKey | 'basic';

interface SmsProviderFieldConfig {
  name: keyof SmsVerificationSettings;
  label: string;
  placeholder?: string;
  required?: boolean;
  password?: boolean;
}

interface SmsProviderSchema {
  fields: SmsProviderFieldConfig[];
}

interface AuthenticatorRecord {
  key: AuthenticatorCode;
  order: number;
  identifier: string;
  type: string;
  title: string;
  description: string;
  enabled: boolean;
}

const SMS_PROVIDER_OPTIONS: Array<{ label: string; value: SmsProviderCode }> = [
  { label: '阿里云短信', value: 'aliyun' },
  { label: '腾讯云短信', value: 'tencent' },
  { label: '本地模拟', value: 'mock' },
  { label: '自定义网关', value: 'custom' },
];

const SMS_PROVIDER_SCHEMAS: Record<SmsProviderCode, SmsProviderSchema> = {
  aliyun: {
    fields: [
      { name: 'signName', label: '短信签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板编码', placeholder: '例如：SMS_123456789', required: true },
      { name: 'accessKeyId', label: 'Access Key ID', placeholder: '短信服务访问密钥 ID', required: true },
      { name: 'accessKeySecret', label: 'Access Key Secret', placeholder: '留空则保持现有密钥', password: true },
      { name: 'endpoint', label: '服务地址', placeholder: '例如：https://dysmsapi.aliyuncs.com' },
      { name: 'region', label: '地域', placeholder: '例如：cn-hangzhou' },
    ],
  },
  tencent: {
    fields: [
      { name: 'signName', label: '短信签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板 ID', placeholder: '例如：1234567', required: true },
      { name: 'accessKeyId', label: 'SecretId', placeholder: '腾讯云 SecretId', required: true },
      { name: 'accessKeySecret', label: 'SecretKey', placeholder: '留空则保持现有密钥', password: true, required: true },
      { name: 'endpoint', label: 'API 地址', placeholder: '例如：https://sms.tencentcloudapi.com' },
      { name: 'region', label: '地域', placeholder: '例如：ap-guangzhou' },
    ],
  },
  mock: {
    fields: [
      { name: 'signName', label: '模拟签名', placeholder: '例如：测试短信' },
      { name: 'templateCode', label: '模拟模板编码', placeholder: '例如：MOCK_SMS_001' },
    ],
  },
  custom: {
    fields: [
      { name: 'endpoint', label: '网关地址', placeholder: '例如：https://sms.example.com/api', required: true },
      { name: 'accessKeyId', label: '网关账号', placeholder: '例如：gateway-user', required: true },
      { name: 'accessKeySecret', label: '网关密钥', placeholder: '留空则保持现有密钥', password: true, required: true },
      { name: 'signName', label: '签名', placeholder: '例如：宏翔商道', required: true },
      { name: 'templateCode', label: '模板编码', placeholder: '例如：SMS_123456789', required: true },
      { name: 'region', label: '地域', placeholder: '按网关要求填写' },
    ],
  },
};

const normalizeProviderCode = (value?: string | null): SmsProviderCode => {
  if (value === 'tencent' || value === 'mock' || value === 'custom') {
    return value;
  }
  return 'aliyun';
};

const normalizeDrawerMode = (value?: string | null): ConfigDrawerMode | null => {
  if (value === 'basic') {
    return 'basic';
  }
  if (value === 'totp' || value === 'sms' || value === 'email' || value === 'wechat' || value === 'passkey') {
    return value;
  }
  return null;
};

const resolveDrawerTitle = (mode: ConfigDrawerMode | null) => {
  if (mode === 'sms') {
    return '配置短信认证器';
  }
  if (mode === 'email') {
    return '配置邮箱认证';
  }
  if (mode === 'wechat') {
    return '配置微信登录';
  }
  if (mode === 'passkey') {
    return '配置通行密钥';
  }
  if (mode === 'totp') {
    return '配置 2FA';
  }
  return '配置密码认证器';
};

const verificationFormInitialValues: VerificationSettings = {
  enabled: true,
  emailLoginEnabled: false,
  passwordLoginEnabled: true,
  loginModeOrder: ['passkey', 'sms', 'email', 'password'],
};

const smtpFormInitialValues: SmtpSettings = {
  host: '',
  port: 25,
  username: '',
  password: '',
  from: '',
  authEnabled: true,
  startTlsEnabled: true,
  sslEnabled: false,
};

const smtpTestInitialValues: SmtpTestPayload = {
  subject: 'SMTP 测试邮件',
  content: '这是一封来自系统的 SMTP 测试邮件。',
  toEmail: '',
};

const SMS_ACCESS_KEY_SECRET_MASK = '********';
const SMTP_PASSWORD_MASK = '********';
const WECHAT_APP_SECRET_MASK = '********';

const SystemVerificationPage = () => {
  const { token } = theme.useToken();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const canViewVerification =
    actionPermission.can('system:verification:view') ||
    actionPermission.can('system:verification:manage') ||
    actionPermission.can('system:config:view');
  const canManageSettings = actionPermission.can('system:verification:manage') || actionPermission.can('system:config:update');
  const location = useLocation();

  const [verificationForm] = Form.useForm<VerificationSettings>();
  const [smsSettingsForm] = Form.useForm<SmsVerificationSettings>();
  const [smtpSettingsForm] = Form.useForm<SmtpSettings>();
  const [smtpTestForm] = Form.useForm<SmtpTestPayload>();
  const [wechatSettingsForm] = Form.useForm<WechatLoginSettings>();
  const [passkeySettingsForm] = Form.useForm<PasskeySettings & { allowedOriginsText?: string }>();
  const [providerDrafts, setProviderDrafts] = useState<Partial<Record<SmsProviderCode, SmsVerificationSettings>>>({});
  const [configDrawerMode, setConfigDrawerMode] = useState<ConfigDrawerMode | null>(() =>
    normalizeDrawerMode(new URLSearchParams(location.search).get('tab')),
  );
  const [selectedAuthenticatorKeys, setSelectedAuthenticatorKeys] = useState<Key[]>([]);
  const [draggedAuthenticatorKey, setDraggedAuthenticatorKey] = useState<AuthenticatorCode | null>(null);
  const [reorderingAuthenticators, setReorderingAuthenticators] = useState(false);
  const [verificationSaving, setVerificationSaving] = useState(false);
  const [savingSmsSettings, setSavingSmsSettings] = useState(false);
  const [savingEmailSettings, setSavingEmailSettings] = useState(false);
  const [savingWechatSettings, setSavingWechatSettings] = useState(false);
  const [savingPasskeySettings, setSavingPasskeySettings] = useState(false);
  const [testingSmtpSettings, setTestingSmtpSettings] = useState(false);

  const verificationSettingsQuery = useQuery({
    queryKey: ['verification-settings'],
    queryFn: async () => systemService.verificationSettings({ autoRedirectOnUnauthorized: false }),
    enabled: canViewVerification,
  });
  const smsSettingsQuery = useQuery({
    queryKey: ['sms-verification-settings'],
    queryFn: async () => systemService.smsVerificationSettings({ autoRedirectOnUnauthorized: false }),
    enabled: canViewVerification,
  });
  const smtpSettingsQuery = useQuery({
    queryKey: ['smtp-settings'],
    queryFn: async () => systemService.smtpSettings({ autoRedirectOnUnauthorized: false }),
    enabled: canViewVerification,
  });
  const wechatSettingsQuery = useQuery({
    queryKey: ['wechat-login-settings'],
    queryFn: async () => systemService.wechatLoginSettings({ autoRedirectOnUnauthorized: false }),
    enabled: canViewVerification,
  });
  const passkeySettingsQuery = useQuery({
    queryKey: ['passkey-settings'],
    queryFn: async () => systemService.passkeySettings({ autoRedirectOnUnauthorized: false }),
    enabled: canViewVerification,
  });
  const verificationFormProps = useStandardFormProps({
    form: verificationForm,
    initialValues: verificationFormInitialValues,
  });
  const smsFormProps = useStandardFormProps({
    form: smsSettingsForm,
    initialValues: {
      enabled: false,
      provider: 'aliyun',
    },
  });
  const smtpFormProps = useStandardFormProps({
    form: smtpSettingsForm,
    initialValues: smtpFormInitialValues,
  });
  const smtpTestFormProps = useStandardFormProps({
    form: smtpTestForm,
    initialValues: smtpTestInitialValues,
  });
  const wechatFormProps = useStandardFormProps({
    form: wechatSettingsForm,
    initialValues: {
      enabled: false,
      appId: '',
      appSecret: '',
      redirectUri: '',
      stateExpireMinutes: 10,
    },
  });
  const passkeyFormProps = useStandardFormProps({
    form: passkeySettingsForm,
    initialValues: {
      enabled: true,
      passwordlessEnabled: true,
      selfBindingEnabled: true,
      rpId: 'elexvx.com',
      rpName: '宏翔商道后台管理系统',
      allowedOrigins: ['https://test.elexvx.com'],
      allowedOriginsText: 'https://test.elexvx.com',
      challengeTtlSeconds: 120,
    },
  });

  const currentProvider = Form.useWatch('provider', smsSettingsForm);
  const wechatEnabled = Form.useWatch('enabled', wechatSettingsForm) ?? false;

  const updateTabInUrl = useCallback(
    (nextTab?: ConfigDrawerMode | null) => {
      const searchParams = new URLSearchParams(location.search);
      if (nextTab) {
        searchParams.set('tab', nextTab);
      } else {
        searchParams.delete('tab');
      }
      const nextSearch = searchParams.toString();
      history.replace({
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
      });
    },
    [location.pathname, location.search],
  );

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    setConfigDrawerMode(normalizeDrawerMode(searchParams.get('tab')));
  }, [location.search, updateTabInUrl]);

  useEffect(() => {
    if (verificationSettingsQuery.data) {
      verificationForm.setFieldsValue(verificationSettingsQuery.data);
    }
  }, [verificationForm, verificationSettingsQuery.data]);

  useEffect(() => {
    if (!smsSettingsQuery.data) {
      return;
    }
    const providerCode = normalizeProviderCode(smsSettingsQuery.data.provider);
    const accessKeySecret = smsSettingsQuery.data.accessKeySecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '';
    setProviderDrafts((drafts) => ({
      ...drafts,
      [providerCode]: {
        ...smsSettingsQuery.data,
        accessKeySecret,
      },
    }));
    smsSettingsForm.setFieldsValue({
      ...smsSettingsQuery.data,
      accessKeySecret,
    });
  }, [smsSettingsForm, smsSettingsQuery.data]);

  useEffect(() => {
    if (smtpSettingsQuery.data) {
      smtpSettingsForm.setFieldsValue({
        ...smtpSettingsQuery.data,
        password: smtpSettingsQuery.data.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });
    }
  }, [smtpSettingsForm, smtpSettingsQuery.data]);

  useEffect(() => {
    if (wechatSettingsQuery.data) {
      wechatSettingsForm.setFieldsValue({
        ...wechatSettingsQuery.data,
        appSecret: wechatSettingsQuery.data.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
    }
  }, [wechatSettingsForm, wechatSettingsQuery.data]);

  useEffect(() => {
    if (passkeySettingsQuery.data) {
      passkeySettingsForm.setFieldsValue({
        ...passkeySettingsQuery.data,
        allowedOriginsText: passkeySettingsQuery.data.allowedOrigins?.join('\n') || '',
      });
    }
  }, [passkeySettingsForm, passkeySettingsQuery.data]);

  const handleSmsProviderChange = (nextProvider: string) => {
    const currentValues = smsSettingsForm.getFieldsValue(true) as Partial<SmsVerificationSettings>;
    const previousProvider = normalizeProviderCode(currentValues.provider);
    const nextProviderCode = normalizeProviderCode(nextProvider);
    const nextDraft = providerDrafts[nextProviderCode] || {
      enabled: currentValues.enabled ?? false,
      provider: nextProviderCode,
      signName: '',
      templateCode: '',
      accessKeyId: '',
      accessKeySecret: '',
      endpoint: '',
      region: '',
    };

    setProviderDrafts((drafts) => ({
      ...drafts,
      [previousProvider]: {
        ...(drafts[previousProvider] || {}),
        ...currentValues,
        provider: previousProvider,
      },
      [nextProviderCode]: {
        ...(drafts[nextProviderCode] || {}),
        ...nextDraft,
        provider: nextProviderCode,
      },
    }));

    smsSettingsForm.setFieldsValue({
      ...nextDraft,
      provider: nextProviderCode,
      accessKeySecret: nextDraft.accessKeySecret || '',
    });
  };

  const handleSaveVerificationSettings = async (options?: { closeDrawer?: boolean }) => {
    if (!canManageSettings) {
      return;
    }
    setVerificationSaving(true);
    try {
      const values = await verificationForm.validateFields();
      const result = await systemService.updateVerificationSettings(values, { autoRedirectOnUnauthorized: false });
      verificationForm.setFieldsValue(result);
      message.success('验证设置已保存');
      await verificationSettingsQuery.refetch();
      if (options?.closeDrawer) {
        closeConfigDrawer({ resetDraft: false });
      }
    } finally {
      setVerificationSaving(false);
    }
  };

  const handleSaveEmailSettings = async () => {
    if (!canManageSettings) {
      return;
    }

    setSavingEmailSettings(true);
    try {
      verificationForm.setFieldValue('emailLoginEnabled', true);
      const smtpValues = await smtpSettingsForm.validateFields();
      const smtpPayload = {
        ...smtpValues,
        password: smtpValues.password === SMTP_PASSWORD_MASK ? undefined : smtpValues.password,
      };
      const smtpResult = await systemService.updateSmtpSettings(smtpPayload, { autoRedirectOnUnauthorized: false });
      smtpSettingsForm.setFieldsValue({
        ...smtpResult,
        password: smtpResult.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });

      const verificationValues = await verificationForm.validateFields();
      const result = await systemService.updateVerificationSettings(
        {
          ...verificationValues,
          emailLoginEnabled: true,
        },
        { autoRedirectOnUnauthorized: false },
      );
      verificationForm.setFieldsValue(result);

      message.success('邮箱验证码登录与 SMTP 配置已保存');
      await Promise.all([verificationSettingsQuery.refetch(), smtpSettingsQuery.refetch()]);
      closeConfigDrawer({ resetDraft: false });
    } finally {
      setSavingEmailSettings(false);
    }
  };

  const handleSaveSmsSettings = async () => {
    if (!canManageSettings) {
      return;
    }
    setSavingSmsSettings(true);
    try {
      smsSettingsForm.setFieldValue('enabled', true);
      const values = await smsSettingsForm.validateFields();
      const providerCode = normalizeProviderCode(values.provider);
      const accessKeySecret = values.accessKeySecret === SMS_ACCESS_KEY_SECRET_MASK ? undefined : values.accessKeySecret;
      setProviderDrafts((drafts) => ({
        ...drafts,
        [providerCode]: values,
      }));
      const result = await systemService.updateSmsVerificationSettings(
        {
          ...values,
          enabled: true,
          accessKeySecret,
        },
        { autoRedirectOnUnauthorized: false },
      );
      message.success(result.configured ? '短信验证码配置已保存' : '短信验证码配置已保存，当前仍未完全启用');
      await smsSettingsQuery.refetch();
      closeConfigDrawer({ resetDraft: false });
    } finally {
      setSavingSmsSettings(false);
    }
  };

  const handleSaveWechatSettings = async () => {
    if (!canManageSettings) {
      return;
    }
    setSavingWechatSettings(true);
    try {
      const values = await wechatSettingsForm.validateFields();
      const appSecret = values.appSecret === WECHAT_APP_SECRET_MASK ? undefined : values.appSecret;
      const result = await systemService.updateWechatLoginSettings(
        {
          ...values,
          appSecret,
        },
        { autoRedirectOnUnauthorized: false },
      );
      wechatSettingsForm.setFieldsValue({
        ...result,
        appSecret: result.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
      message.success(result.configured ? '微信登录配置已保存' : '微信登录配置已保存，当前仍未完全启用');
      await wechatSettingsQuery.refetch();
      closeConfigDrawer({ resetDraft: false });
    } finally {
      setSavingWechatSettings(false);
    }
  };

  const handleSavePasskeySettings = async (options?: { forceEnabled?: boolean; closeDrawer?: boolean }) => {
    if (!canManageSettings) {
      return;
    }
    setSavingPasskeySettings(true);
    try {
      const forceEnabled = options?.forceEnabled ?? true;
      if (forceEnabled) {
        passkeySettingsForm.setFieldsValue({ enabled: true, passwordlessEnabled: true });
      }
      const values = await passkeySettingsForm.validateFields();
      const result = await systemService.updatePasskeySettings(
        {
          ...values,
          enabled: forceEnabled ? true : values.enabled,
          allowedOrigins: values.allowedOriginsText?.split('\n').map((item) => item.trim()).filter(Boolean) || [],
        },
        { autoRedirectOnUnauthorized: false },
      );
      passkeySettingsForm.setFieldsValue({
        ...result,
        allowedOriginsText: result.allowedOrigins?.join('\n') || '',
      });
      message.success('通行密钥配置已保存');
      await passkeySettingsQuery.refetch();
      if (options?.closeDrawer !== false) {
        closeConfigDrawer({ resetDraft: false });
      }
    } finally {
      setSavingPasskeySettings(false);
    }
  };

  const handleTestSmtpSettings = async () => {
    if (!canManageSettings) {
      return;
    }
    setTestingSmtpSettings(true);
    try {
      const values = await smtpTestForm.validateFields();
      const result = await systemService.testSmtpSettings(values, { autoRedirectOnUnauthorized: false });
      message.success(result.message || '测试邮件已发送');
    } finally {
      setTestingSmtpSettings(false);
    }
  };

  const resetConfigDrafts = useCallback(() => {
    if (verificationSettingsQuery.data) {
      verificationForm.setFieldsValue(verificationSettingsQuery.data);
    }
    if (smsSettingsQuery.data) {
      const providerCode = normalizeProviderCode(smsSettingsQuery.data.provider);
      const accessKeySecret = smsSettingsQuery.data.accessKeySecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '';
      setProviderDrafts((drafts) => ({
        ...drafts,
        [providerCode]: {
          ...smsSettingsQuery.data,
          accessKeySecret,
        },
      }));
      smsSettingsForm.setFieldsValue({
        ...smsSettingsQuery.data,
        accessKeySecret,
      });
    }
    if (smtpSettingsQuery.data) {
      smtpSettingsForm.setFieldsValue({
        ...smtpSettingsQuery.data,
        password: smtpSettingsQuery.data.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });
    }
    if (wechatSettingsQuery.data) {
      wechatSettingsForm.setFieldsValue({
        ...wechatSettingsQuery.data,
        appSecret: wechatSettingsQuery.data.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
    }
    if (passkeySettingsQuery.data) {
      passkeySettingsForm.setFieldsValue({
        ...passkeySettingsQuery.data,
        allowedOriginsText: passkeySettingsQuery.data.allowedOrigins?.join('\n') || '',
      });
    }
  }, [
    passkeySettingsForm,
    passkeySettingsQuery.data,
    smsSettingsForm,
    smsSettingsQuery.data,
    smtpSettingsForm,
    smtpSettingsQuery.data,
    verificationForm,
    verificationSettingsQuery.data,
    wechatSettingsForm,
    wechatSettingsQuery.data,
  ]);

  const openConfigDrawer = useCallback((mode: ConfigDrawerMode) => {
    setConfigDrawerMode(mode);
    updateTabInUrl(mode);
  }, [updateTabInUrl]);

  const closeConfigDrawer = useCallback((options?: { resetDraft?: boolean }) => {
    if (options?.resetDraft !== false) {
      resetConfigDrafts();
    }
    setConfigDrawerMode(null);
    updateTabInUrl(null);
  }, [resetConfigDrafts, updateTabInUrl]);

  const disableSmsAuthenticator = useCallback(async () => {
    if (!canManageSettings) {
      return;
    }
    setSavingSmsSettings(true);
    try {
      const currentValues = smsSettingsForm.getFieldsValue(true) as SmsVerificationSettings;
      const accessKeySecret = currentValues.accessKeySecret === SMS_ACCESS_KEY_SECRET_MASK ? undefined : currentValues.accessKeySecret;
      await systemService.updateSmsVerificationSettings(
        {
          ...currentValues,
          enabled: false,
          accessKeySecret,
        },
        { autoRedirectOnUnauthorized: false },
      );
      message.success('短信认证器已停用');
      setSelectedAuthenticatorKeys((keys) => keys.filter((key) => key !== 'sms_login'));
      await smsSettingsQuery.refetch();
    } finally {
      setSavingSmsSettings(false);
    }
  }, [canManageSettings, smsSettingsForm, smsSettingsQuery]);

  const handleDeleteAuthenticator = useCallback(async (key: AuthenticatorCode) => {
    const enabledKeys = [
      passkeySettingsQuery.data?.enabled ? 'passkey_login' : null,
      smsSettingsQuery.data?.enabled ? 'sms_login' : null,
      verificationSettingsQuery.data?.emailLoginEnabled ? 'email_login' : null,
      (verificationSettingsQuery.data?.passwordLoginEnabled ?? true) ? 'password_login' : null,
    ].filter(Boolean) as AuthenticatorCode[];
    if (enabledKeys.length <= 1 && enabledKeys.includes(key)) {
      message.warning('至少需要保留一种可用登录方式');
      return;
    }
    if (key === 'sms_login') {
      await disableSmsAuthenticator();
      return;
    }
    if (key === 'email_login') {
      verificationForm.setFieldValue('emailLoginEnabled', false);
      await handleSaveVerificationSettings();
      return;
    }
    if (key === 'passkey_login') {
      passkeySettingsForm.setFieldValue('enabled', false);
      await handleSavePasskeySettings({ forceEnabled: false, closeDrawer: false });
      return;
    }
    if (key === 'password_login') {
      verificationForm.setFieldValue('passwordLoginEnabled', false);
      await handleSaveVerificationSettings();
    }
  }, [disableSmsAuthenticator, handleSavePasskeySettings, handleSaveVerificationSettings, passkeySettingsForm, passkeySettingsQuery.data?.enabled, smsSettingsQuery.data?.enabled, verificationForm, verificationSettingsQuery.data?.emailLoginEnabled, verificationSettingsQuery.data?.passwordLoginEnabled]);

  const handleDeleteSelectedAuthenticators = useCallback(async () => {
    if (!selectedAuthenticatorKeys.length) {
      message.info('请先选择要删除的认证器');
      return;
    }
    const selectedKeys = new Set(selectedAuthenticatorKeys);
    const enabledKeys = [
      passkeySettingsQuery.data?.enabled ? 'passkey_login' : null,
      smsSettingsQuery.data?.enabled ? 'sms_login' : null,
      verificationSettingsQuery.data?.emailLoginEnabled ? 'email_login' : null,
      (verificationSettingsQuery.data?.passwordLoginEnabled ?? true) ? 'password_login' : null,
    ].filter(Boolean) as AuthenticatorCode[];
    if (enabledKeys.length > 0 && enabledKeys.every((key) => selectedKeys.has(key))) {
      message.warning('至少需要保留一种可用登录方式');
      return;
    }
    if (selectedAuthenticatorKeys.includes('sms_login')) {
      await disableSmsAuthenticator();
    }
    if (selectedAuthenticatorKeys.includes('email_login')) {
      verificationForm.setFieldValue('emailLoginEnabled', false);
      await handleSaveVerificationSettings();
    }
    if (selectedAuthenticatorKeys.includes('passkey_login')) {
      passkeySettingsForm.setFieldValue('enabled', false);
      await handleSavePasskeySettings({ forceEnabled: false, closeDrawer: false });
    }
    if (selectedAuthenticatorKeys.includes('password_login')) {
      verificationForm.setFieldValue('passwordLoginEnabled', false);
      await handleSaveVerificationSettings();
    }
  }, [disableSmsAuthenticator, handleSavePasskeySettings, handleSaveVerificationSettings, passkeySettingsForm, passkeySettingsQuery.data?.enabled, selectedAuthenticatorKeys, smsSettingsQuery.data?.enabled, verificationForm, verificationSettingsQuery.data?.emailLoginEnabled, verificationSettingsQuery.data?.passwordLoginEnabled]);

  const handleEnableAuthenticator = useCallback((mode: LoginModeCode) => {
    if (!canManageSettings) {
      return;
    }
    if (mode === 'sms') {
      smsSettingsForm.setFieldValue('enabled', true);
      openConfigDrawer('sms');
      return;
    }
    if (mode === 'email') {
      verificationForm.setFieldValue('emailLoginEnabled', true);
      openConfigDrawer('email');
      return;
    }
    if (mode === 'passkey') {
      passkeySettingsForm.setFieldsValue({ enabled: true, passwordlessEnabled: true });
      openConfigDrawer('passkey');
      return;
    }
    verificationForm.setFieldValue('passwordLoginEnabled', true);
    openConfigDrawer('basic');
  }, [canManageSettings, openConfigDrawer, passkeySettingsForm, smsSettingsForm, verificationForm]);

  const activeProvider = normalizeProviderCode(currentProvider);
  const providerSchema = SMS_PROVIDER_SCHEMAS[activeProvider];
  const verificationLoading =
    verificationSettingsQuery.isLoading || smsSettingsQuery.isLoading || smtpSettingsQuery.isLoading || wechatSettingsQuery.isLoading || passkeySettingsQuery.isLoading;
  const persistedEmailLoginEnabled = Boolean(verificationSettingsQuery.data?.emailLoginEnabled);
  const persistedPasswordLoginEnabled = verificationSettingsQuery.data?.passwordLoginEnabled ?? true;
  const smsConfigEnabled = configDrawerMode === 'sms';
  const emailConfigEnabled = configDrawerMode === 'email';
  const passkeyConfigEnabled = configDrawerMode === 'passkey';
  const smsAccessKeySecretConfigured = smsSettingsQuery.data?.accessKeySecretConfigured ?? false;
  const wechatAppSecretConfigured = wechatSettingsQuery.data?.appSecretConfigured ?? false;
  const configuredLoginModeOrder = verificationSettingsQuery.data?.loginModeOrder || verificationForm.getFieldValue('loginModeOrder') || ['passkey', 'sms', 'email', 'password'];
  const normalizeLoginModeOrder = useCallback((order?: string[]) => {
    const result: LoginModeCode[] = [];
    (order || []).forEach((item) => {
      if ((item === 'passkey' || item === 'sms' || item === 'email' || item === 'password') && !result.includes(item)) {
        result.push(item);
      }
    });
    (['passkey', 'sms', 'email', 'password'] as LoginModeCode[]).forEach((item) => {
      if (!result.includes(item)) {
        result.push(item);
      }
    });
    return result;
  }, []);
  const authenticatorKeyToMode = useCallback((key: AuthenticatorCode): LoginModeCode => {
    if (key === 'passkey_login') {
      return 'passkey';
    }
    if (key === 'sms_login') {
      return 'sms';
    }
    if (key === 'email_login') {
      return 'email';
    }
    return 'password';
  }, []);
  const authenticatorRows = useMemo<AuthenticatorRecord[]>(
    () => {
      const rowsByMode: Record<LoginModeCode, Omit<AuthenticatorRecord, 'order'>> = {
        passkey: {
          key: 'passkey_login',
          identifier: '通行密钥',
          type: '通行密钥',
          title: '通行密钥',
          description: '使用系统钥匙串或密码管理器进行 WebAuthn 验证',
          enabled: Boolean(passkeySettingsQuery.data?.enabled),
        },
        sms: {
          key: 'sms_login',
          identifier: '短信',
          type: '短信',
          title: '短信验证',
          description: '使用短信验证码登录',
          enabled: Boolean(smsSettingsQuery.data?.enabled),
        },
        email: {
          key: 'email_login',
          identifier: '邮箱',
          type: '邮箱',
          title: '邮箱验证码',
          description: '使用邮箱验证码登录',
          enabled: persistedEmailLoginEnabled,
        },
        password: {
          key: 'password_login',
          identifier: '密码',
          type: '密码',
          title: '账号密码登录',
          description: '使用账号密码登录',
          enabled: Boolean(persistedPasswordLoginEnabled),
        },
      };
      return normalizeLoginModeOrder(configuredLoginModeOrder)
        .filter((mode) => rowsByMode[mode].enabled)
        .map((mode, index) => ({
          ...rowsByMode[mode],
          order: index + 1,
        }));
    },
    [configuredLoginModeOrder, normalizeLoginModeOrder, passkeySettingsQuery.data?.enabled, persistedEmailLoginEnabled, persistedPasswordLoginEnabled, smsSettingsQuery.data?.enabled],
  );

  const addAuthenticatorItems = useMemo<MenuProps['items']>(
    () =>
      [
        { key: 'passkey', label: '通行密钥', enabled: Boolean(passkeySettingsQuery.data?.enabled), mode: 'passkey' as const },
        { key: 'sms', label: '短信', enabled: Boolean(smsSettingsQuery.data?.enabled), mode: 'sms' as const },
        { key: 'email', label: '邮箱', enabled: persistedEmailLoginEnabled, mode: 'email' as const },
        { key: 'password', label: '密码', enabled: Boolean(persistedPasswordLoginEnabled), mode: 'password' as const },
      ]
        .filter((item) => !item.enabled)
        .map((item) => ({
          key: item.key,
          label: item.label,
          onClick: () => void handleEnableAuthenticator(item.mode),
        })),
    [handleEnableAuthenticator, passkeySettingsQuery.data?.enabled, persistedEmailLoginEnabled, persistedPasswordLoginEnabled, smsSettingsQuery.data?.enabled],
  );

  const persistLoginModeOrder = useCallback(async (nextRows: AuthenticatorRecord[]) => {
    if (!canManageSettings) {
      return;
    }
    const loginModeOrder = nextRows.map((row) => authenticatorKeyToMode(row.key));
    setReorderingAuthenticators(true);
    try {
      const result = await systemService.updateVerificationSettings(
        { loginModeOrder },
        { autoRedirectOnUnauthorized: false },
      );
      verificationForm.setFieldsValue(result);
      message.success('登录方式顺序已更新');
      await verificationSettingsQuery.refetch();
    } finally {
      setReorderingAuthenticators(false);
    }
  }, [authenticatorKeyToMode, canManageSettings, verificationForm, verificationSettingsQuery]);

  const handleAuthenticatorDragStart = (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
    if (!canManageSettings || reorderingAuthenticators) {
      event.preventDefault();
      return;
    }
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', record.key);
    setDraggedAuthenticatorKey(record.key);
  };

  const handleAuthenticatorDragOver = (record: AuthenticatorRecord) => (event: DragEvent<HTMLTableRowElement>) => {
    if (!draggedAuthenticatorKey || draggedAuthenticatorKey === record.key || !canManageSettings || reorderingAuthenticators) {
      return;
    }
    event.preventDefault();
  };

  const handleAuthenticatorDrop = (record: AuthenticatorRecord) => async (event: DragEvent<HTMLTableRowElement>) => {
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
    await persistLoginModeOrder(nextRows);
  };

  const handleAuthenticatorDragEnd = () => {
    setDraggedAuthenticatorKey(null);
  };

  const authenticatorColumns = useMemo<ProColumns<AuthenticatorRecord>[]>(
    () => [
      {
        title: '',
        dataIndex: 'order',
        width: 96,
        search: false,
        render: (_, record) => (
          <Space size={16}>
            <HolderOutlined style={{ color: token.colorTextSecondary }} />
            <Typography.Text>{record.order}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '认证标识',
        dataIndex: 'identifier',
        width: 180,
        search: false,
      },
      {
        title: '认证类型',
        dataIndex: 'type',
        width: 160,
        search: false,
        render: (_, record) => <Tag>{record.type}</Tag>,
      },
      {
        title: '标题',
        dataIndex: 'title',
        width: 180,
        search: false,
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        width: 120,
        align: 'center',
        search: false,
        render: (_, record) => (record.enabled ? <CheckOutlined style={{ color: '#52c41a' }} /> : null),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 160,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              {
                key: 'config',
                label: '配置',
                disabled: !canManageSettings,
                onClick: () => openConfigDrawer(record.key === 'sms_login' ? 'sms' : record.key === 'passkey_login' ? 'passkey' : record.key === 'email_login' ? 'email' : 'basic'),
              },
              {
                key: 'delete',
                label: '删除',
                danger: true,
                disabled: !canManageSettings,
                onClick: () => void handleDeleteAuthenticator(record.key),
              },
            ]}
          />
        ),
      },
    ],
    [canManageSettings, handleDeleteAuthenticator, openConfigDrawer, responsive.isMobile],
  );

  const renderVerificationTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...verificationFormProps}>
        <Form.Item
          name="enabled"
          label="启用 2FA"
          valuePropName="checked"
          extra="关闭后，系统中的高危操作二次确认将不再要求 2FA。"
        >
          <Switch disabled={!canManageSettings} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
      </Form>
    </Space>
  );

  const renderSmsTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...smsFormProps}>
        <Form.Item
          name="provider"
          label="服务商"
          rules={smsConfigEnabled ? [{ required: true, message: '请选择短信服务商' }] : undefined}
        >
          <Select
            disabled={!canManageSettings || !smsConfigEnabled}
            options={SMS_PROVIDER_OPTIONS}
            placeholder="请选择短信服务商"
            onChange={handleSmsProviderChange}
          />
        </Form.Item>
        {providerSchema.fields.map((field) => (
          <Form.Item
            key={String(field.name)}
            name={field.name}
            label={field.label}
            rules={smsConfigEnabled && field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}
            extra={
              field.password && field.name === 'accessKeySecret'
                ? smsAccessKeySecretConfigured
                  ? '当前密钥已脱敏显示，留空则保持现有密钥'
                  : '留空则保持现有密钥'
                : undefined
            }
          >
            {field.password ? (
              <Input.Password
                disabled={!canManageSettings || !smsConfigEnabled}
                placeholder={field.placeholder}
              />
            ) : (
              <Input disabled={!canManageSettings || !smsConfigEnabled} placeholder={field.placeholder} />
            )}
          </Form.Item>
        ))}
      </Form>
    </Space>
  );

  const renderEmailTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="邮箱与 SMTP" loading={smtpSettingsQuery.isLoading || verificationSettingsQuery.isLoading}>
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
          <div style={{ opacity: emailConfigEnabled ? 1 : 0.48, transition: 'opacity 0.2s ease' }}>
            <Form {...smtpFormProps}>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                SMTP 基础配置
              </Typography.Title>
              <Form.Item name="host" label="SMTP 主机" rules={[{ required: true, message: '请输入 SMTP 主机' }]}>
                <Input disabled={!canManageSettings || !emailConfigEnabled} placeholder="smtp.example.com" />
              </Form.Item>
              <Form.Item name="port" label="SMTP 端口" rules={[{ required: true, message: '请输入 SMTP 端口' }]}>
                <InputNumber disabled={!canManageSettings || !emailConfigEnabled} style={{ width: '100%' }} min={1} max={65535} />
              </Form.Item>
              <Form.Item name="username" label="SMTP 用户名" rules={[{ required: true, message: '请输入 SMTP 用户名' }]}>
                <Input disabled={!canManageSettings || !emailConfigEnabled} placeholder="username@example.com" />
              </Form.Item>
              <Form.Item
                name="password"
                label="SMTP 密码"
                extra={smtpSettingsQuery.data?.passwordConfigured ? '当前密码已脱敏显示，留空则保留现有密码' : '留空则保留现有密码'}
              >
                <Input.Password
                  disabled={!canManageSettings || !emailConfigEnabled}
                  placeholder="留空则保留现有密码"
                />
              </Form.Item>
              <Form.Item name="from" label="发件人地址" rules={[{ required: true, message: '请输入发件人地址' }]}>
                <Input disabled={!canManageSettings || !emailConfigEnabled} placeholder="noreply@example.com" />
              </Form.Item>
              <Form.Item name="authEnabled" label="启用认证" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailConfigEnabled} />
              </Form.Item>
              <Form.Item name="startTlsEnabled" label="启用 STARTTLS" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailConfigEnabled} />
              </Form.Item>
              <Form.Item name="sslEnabled" label="启用 SSL" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailConfigEnabled} />
              </Form.Item>
            </Form>
          </div>
        </Space>
      </Card>

      <Card title="SMTP 测试发送" loading={smtpSettingsQuery.isLoading}>
        <Form {...smtpTestFormProps}>
          <Form.Item
            name="toEmail"
            label="收件人邮箱"
            rules={[{ required: true, message: '请输入收件人邮箱' }, { type: 'email', message: '请输入有效邮箱地址' }]}
          >
            <Input disabled={!canManageSettings} placeholder="recipient@example.com" />
          </Form.Item>
          <Form.Item name="subject" label="邮件主题">
            <Input disabled={!canManageSettings} />
          </Form.Item>
          <Form.Item name="content" label="邮件内容">
            <Input.TextArea disabled={!canManageSettings} rows={6} />
          </Form.Item>
        </Form>
      </Card>

    </Space>
  );

  const renderWechatTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...wechatFormProps}>
        <Form.Item name="enabled" label="启用微信登录" valuePropName="checked">
          <Switch disabled={!canManageSettings} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
        <Form.Item
          name="appId"
          label="AppID"
          rules={wechatEnabled ? [{ required: true, message: '请输入微信 AppID' }] : undefined}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder="微信开放平台网站应用 AppID" />
        </Form.Item>
        <Form.Item
          name="appSecret"
          label="AppSecret"
          rules={wechatEnabled && !wechatAppSecretConfigured ? [{ required: true, message: '请输入微信 AppSecret' }] : undefined}
          extra={wechatAppSecretConfigured ? '当前密钥已脱敏显示，留空则保持现有密钥' : '留空则保持现有密钥'}
        >
          <Input.Password disabled={!canManageSettings || !wechatEnabled} placeholder="留空则保持现有密钥" />
        </Form.Item>
        <Form.Item
          name="redirectUri"
          label="回调地址"
          rules={[
            ...(wechatEnabled ? [{ required: true, message: '请输入微信回调地址' }] : []),
            { type: 'url', message: '请输入有效 URL' },
          ]}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder="https://你的域名/api/v1/auth/wechat/callback" />
        </Form.Item>
        <Form.Item
          name="stateExpireMinutes"
          label="状态有效期"
          rules={wechatEnabled ? [{ required: true, message: '请输入状态有效期' }] : undefined}
        >
          <InputNumber disabled={!canManageSettings || !wechatEnabled} style={{ width: '100%' }} min={1} max={60} addonAfter="分钟" />
        </Form.Item>
      </Form>
    </Space>
  );

  const renderPasskeyTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...passkeyFormProps}>
        <Form.Item name="passwordlessEnabled" label="允许无账号登录" valuePropName="checked" extra="开启后，登录页可直接唤起密码管理器或系统钥匙串选择通行密钥。">
          <Switch disabled={!canManageSettings || !passkeyConfigEnabled} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
        <Form.Item name="selfBindingEnabled" label="允许用户自助绑定" valuePropName="checked">
          <Switch disabled={!canManageSettings || !passkeyConfigEnabled} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
        <Form.Item name="rpId" label="RP ID" rules={passkeyConfigEnabled ? [{ required: true, message: '请输入 RP ID' }] : undefined}>
          <Input disabled={!canManageSettings || !passkeyConfigEnabled} placeholder="elexvx.com" />
        </Form.Item>
        <Form.Item name="rpName" label="RP 名称" rules={passkeyConfigEnabled ? [{ required: true, message: '请输入 RP 名称' }] : undefined}>
          <Input disabled={!canManageSettings || !passkeyConfigEnabled} placeholder="宏翔商道后台管理系统" />
        </Form.Item>
        <Form.Item name="allowedOriginsText" label="允许的 Origin" rules={passkeyConfigEnabled ? [{ required: true, message: '请输入允许的 Origin' }] : undefined} extra="每行一个 HTTPS Origin。Vercel Preview 域名不会默认放行。">
          <Input.TextArea disabled={!canManageSettings || !passkeyConfigEnabled} rows={4} placeholder="https://test.elexvx.com" />
        </Form.Item>
        <Form.Item name="challengeTtlSeconds" label="Challenge 有效期">
          <InputNumber disabled={!canManageSettings || !passkeyConfigEnabled} style={{ width: '100%' }} min={30} max={600} addonAfter="秒" />
        </Form.Item>
      </Form>
    </Space>
  );

  const renderBasicConfig = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Paragraph style={{ marginBottom: 0 }}>密码复杂度、验证码和登录防御阈值请在安全设置中统一维护。</Typography.Paragraph>
      <Button type="primary" onClick={() => history.push('/settings/security')}>
        前往安全设置
      </Button>
    </Space>
  );

  const resolveDrawerFooterActions = (): ManagementDrawerAction[] => {
    const cancelAction: ManagementDrawerAction = { key: 'cancel', label: '取消', onClick: () => closeConfigDrawer() };

    if (configDrawerMode === 'sms') {
      return [
        cancelAction,
        {
          key: 'save',
          label: '保存配置',
          type: 'primary',
          loading: savingSmsSettings,
          disabled: !canManageSettings,
          onClick: () => void handleSaveSmsSettings(),
        },
      ];
    }

    if (configDrawerMode === 'email') {
      return [
        {
          key: 'test',
          label: '发送测试邮件',
          loading: testingSmtpSettings,
          disabled: !canManageSettings,
          onClick: () => void handleTestSmtpSettings(),
        },
        cancelAction,
        {
          key: 'save',
          label: '保存设置',
          type: 'primary',
          loading: savingEmailSettings,
          disabled: !canManageSettings,
          onClick: () => void handleSaveEmailSettings(),
        },
      ];
    }

    if (configDrawerMode === 'wechat') {
      return [
        cancelAction,
        {
          key: 'save',
          label: '保存配置',
          type: 'primary',
          loading: savingWechatSettings,
          disabled: !canManageSettings,
          onClick: () => void handleSaveWechatSettings(),
        },
      ];
    }

    if (configDrawerMode === 'passkey') {
      return [
        cancelAction,
        {
          key: 'save',
          label: '保存配置',
          type: 'primary',
          loading: savingPasskeySettings,
          disabled: !canManageSettings,
          onClick: () => void handleSavePasskeySettings(),
        },
      ];
    }

    if (configDrawerMode === 'totp') {
      return [
        cancelAction,
        {
          key: 'save',
          label: '保存 2FA 设置',
          type: 'primary',
          loading: verificationSaving,
          disabled: !canManageSettings,
          onClick: () => void handleSaveVerificationSettings({ closeDrawer: true }),
        },
      ];
    }

    if (configDrawerMode === 'basic') {
      return [
        cancelAction,
        {
          key: 'save',
          label: '保存设置',
          type: 'primary',
          loading: verificationSaving,
          disabled: !canManageSettings,
          onClick: () => {
            verificationForm.setFieldValue('passwordLoginEnabled', true);
            void handleSaveVerificationSettings({ closeDrawer: true });
          },
        },
      ];
    }

    return [];
  };

  const renderConfigDrawerContent = () => {
    if (configDrawerMode === 'sms') {
      return renderSmsTab();
    }
    if (configDrawerMode === 'email') {
      return renderEmailTab();
    }
    if (configDrawerMode === 'wechat') {
      return renderWechatTab();
    }
    if (configDrawerMode === 'passkey') {
      return renderPasskeyTab();
    }
    if (configDrawerMode === 'totp') {
      return renderVerificationTab();
    }
    return renderBasicConfig();
  };

  return (
    <ManagementPage title="验证管理">
      <ManagementTable<AuthenticatorRecord>
        rowKey="key"
        columns={authenticatorColumns}
        isMobile={responsive.isMobile}
        search={false}
        loading={verificationLoading}
        dataSource={authenticatorRows}
        pagination={{ pageSize: 50, showSizeChanger: true }}
        tableAlertRender={false}
        onRow={(record) => ({
          draggable: canManageSettings && !reorderingAuthenticators,
          onDragStart: handleAuthenticatorDragStart(record),
          onDragOver: handleAuthenticatorDragOver(record),
          onDrop: handleAuthenticatorDrop(record),
          onDragEnd: handleAuthenticatorDragEnd,
          style: {
            cursor: canManageSettings && !reorderingAuthenticators ? 'grab' : undefined,
            opacity: draggedAuthenticatorKey === record.key ? 0.45 : 1,
          },
        })}
        rowSelection={{
          selectedRowKeys: selectedAuthenticatorKeys,
          onChange: setSelectedAuthenticatorKeys,
        }}
        toolBarRender={() => [
          <Popconfirm
            key="delete"
            title="删除认证器"
            description="可删除的认证器会被停用，基础密码认证器会保留。"
            okText="确认"
            cancelText="取消"
            onConfirm={() => void handleDeleteSelectedAuthenticators()}
          >
            <Button disabled={!canManageSettings} icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>,
          <Dropdown key="add" trigger={['click']} menu={{ items: addAuthenticatorItems }} placement="bottomRight">
            <Button type="primary" disabled={!canManageSettings || !addAuthenticatorItems?.length} icon={<PlusOutlined />}>
              添加 <DownOutlined />
            </Button>
          </Dropdown>,
        ]}
      />

      <ManagementDrawer
        title={resolveDrawerTitle(configDrawerMode)}
        open={Boolean(configDrawerMode)}
        onClose={() => closeConfigDrawer()}
        footerActions={resolveDrawerFooterActions()}
      >
        {configDrawerMode ? renderConfigDrawerContent() : null}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default SystemVerificationPage;
