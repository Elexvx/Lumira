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
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { SmsConfigTab, EmailConfigTab, WechatConfigTab, PasskeyConfigTab } from './components/verification/ConfigTabs';
import {
  SMS_PROVIDER_OPTIONS,
  SMS_PROVIDER_SCHEMAS,
  normalizeProviderCode,
  normalizeDrawerMode,
  resolveDrawerTitle,
  verificationFormInitialValues,
  resolveLoginModeFromAuthenticatorKey,
  smtpFormInitialValues,
  smtpTestInitialValues,
  SMS_ACCESS_KEY_SECRET_MASK,
  SMTP_PASSWORD_MASK,
  WECHAT_APP_SECRET_MASK,
} from './components/verification/config';
import type {
  AuthenticatorRecord,
  SmsProviderCode,
  AuthenticatorCode,
  LoginModeCode,
  ConfigDrawerMode,
} from './components/verification/config';

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
  const [togglingAuthenticatorKey, setTogglingAuthenticatorKey] = useState<AuthenticatorCode | null>(null);

  const verificationSettingsQuery = useQuery({
    queryKey: ['verification-settings'],
    queryFn: async () => systemService.verificationSettings(API_OPTS.NO_REDIRECT),
    enabled: canViewVerification,
  });
  const smsSettingsQuery = useQuery({
    queryKey: ['sms-verification-settings'],
    queryFn: async () => systemService.smsVerificationSettings(API_OPTS.NO_REDIRECT),
    enabled: canViewVerification,
  });
  const smtpSettingsQuery = useQuery({
    queryKey: ['smtp-settings'],
    queryFn: async () => systemService.smtpSettings(API_OPTS.NO_REDIRECT),
    enabled: canViewVerification,
  });
  const wechatSettingsQuery = useQuery({
    queryKey: ['wechat-login-settings'],
    queryFn: async () => systemService.wechatLoginSettings(API_OPTS.NO_REDIRECT),
    enabled: canViewVerification,
  });
  const passkeySettingsQuery = useQuery({
    queryKey: ['passkey-settings'],
    queryFn: async () => systemService.passkeySettings(API_OPTS.NO_REDIRECT),
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
      const result = await systemService.updateVerificationSettings(values, API_OPTS.NO_REDIRECT);
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
      const smtpResult = await systemService.updateSmtpSettings(smtpPayload, API_OPTS.NO_REDIRECT);
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
        API_OPTS.NO_REDIRECT,
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
        API_OPTS.NO_REDIRECT,
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
        API_OPTS.NO_REDIRECT,
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
        API_OPTS.NO_REDIRECT,
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
      const result = await systemService.testSmtpSettings(values, API_OPTS.NO_REDIRECT);
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
        API_OPTS.NO_REDIRECT,
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
      wechatSettingsQuery.data?.enabled ? 'wechat_login' : null,
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
    if (key === 'wechat_login') {
      wechatSettingsForm.setFieldValue('enabled', false);
      await handleSaveWechatSettings();
      return;
    }
    if (key === 'password_login') {
      verificationForm.setFieldValue('passwordLoginEnabled', false);
      await handleSaveVerificationSettings();
    }
  }, [disableSmsAuthenticator, handleSavePasskeySettings, handleSaveVerificationSettings, handleSaveWechatSettings, passkeySettingsForm, passkeySettingsQuery.data?.enabled, smsSettingsQuery.data?.enabled, verificationForm, verificationSettingsQuery.data?.emailLoginEnabled, verificationSettingsQuery.data?.passwordLoginEnabled, wechatSettingsForm, wechatSettingsQuery.data?.enabled]);

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
      wechatSettingsQuery.data?.enabled ? 'wechat_login' : null,
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
    if (selectedAuthenticatorKeys.includes('wechat_login')) {
      wechatSettingsForm.setFieldValue('enabled', false);
      await handleSaveWechatSettings();
    }
    if (selectedAuthenticatorKeys.includes('password_login')) {
      verificationForm.setFieldValue('passwordLoginEnabled', false);
      await handleSaveVerificationSettings();
    }
  }, [disableSmsAuthenticator, handleSavePasskeySettings, handleSaveVerificationSettings, handleSaveWechatSettings, passkeySettingsForm, passkeySettingsQuery.data?.enabled, selectedAuthenticatorKeys, smsSettingsQuery.data?.enabled, verificationForm, verificationSettingsQuery.data?.emailLoginEnabled, verificationSettingsQuery.data?.passwordLoginEnabled, wechatSettingsForm, wechatSettingsQuery.data?.enabled]);

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
    if (mode === 'wechat') {
      wechatSettingsForm.setFieldValue('enabled', true);
      openConfigDrawer('wechat');
      return;
    }
    verificationForm.setFieldValue('passwordLoginEnabled', true);
    openConfigDrawer('basic');
  }, [canManageSettings, openConfigDrawer, passkeySettingsForm, smsSettingsForm, verificationForm, wechatSettingsForm]);

  const handleToggleAuthenticator = useCallback(async (record: AuthenticatorRecord) => {
    if (!canManageSettings) {
      return;
    }
    setTogglingAuthenticatorKey(record.key);
    try {
      if (record.enabled) {
        await handleDeleteAuthenticator(record.key);
        return;
      }
      handleEnableAuthenticator(resolveLoginModeFromAuthenticatorKey(record.key));
    } finally {
      setTogglingAuthenticatorKey(null);
    }
  }, [canManageSettings, handleDeleteAuthenticator, handleEnableAuthenticator]);

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
  const configuredLoginModeOrder = verificationSettingsQuery.data?.loginModeOrder || verificationForm.getFieldValue('loginModeOrder') || ['passkey', 'sms', 'email', 'wechat', 'password'];
  const normalizeLoginModeOrder = useCallback((order?: string[]) => {
    const result: LoginModeCode[] = [];
    (order || []).forEach((item) => {
      if ((item === 'passkey' || item === 'sms' || item === 'email' || item === 'wechat' || item === 'password') && !result.includes(item)) {
        result.push(item);
      }
    });
    (['passkey', 'sms', 'email', 'wechat', 'password'] as LoginModeCode[]).forEach((item) => {
      if (!result.includes(item)) {
        result.push(item);
      }
    });
    return result;
  }, []);
  const authenticatorKeyToMode = useCallback(resolveLoginModeFromAuthenticatorKey, []);
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
        wechat: {
          key: 'wechat_login',
          identifier: '微信',
          type: '微信',
          title: '微信扫码登录',
          description: '使用微信扫码登录，未注册用户可自动创建账号',
          enabled: Boolean(wechatSettingsQuery.data?.enabled),
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
    [configuredLoginModeOrder, normalizeLoginModeOrder, passkeySettingsQuery.data?.enabled, persistedEmailLoginEnabled, persistedPasswordLoginEnabled, smsSettingsQuery.data?.enabled, wechatSettingsQuery.data?.enabled],
  );

  const addAuthenticatorItems = useMemo<MenuProps['items']>(
    () =>
      [
        { key: 'passkey', label: '通行密钥', enabled: Boolean(passkeySettingsQuery.data?.enabled), mode: 'passkey' as const },
        { key: 'sms', label: '短信', enabled: Boolean(smsSettingsQuery.data?.enabled), mode: 'sms' as const },
        { key: 'email', label: '邮箱', enabled: persistedEmailLoginEnabled, mode: 'email' as const },
        { key: 'wechat', label: '微信', enabled: Boolean(wechatSettingsQuery.data?.enabled), mode: 'wechat' as const },
        { key: 'password', label: '密码', enabled: Boolean(persistedPasswordLoginEnabled), mode: 'password' as const },
      ]
        .filter((item) => !item.enabled)
        .map((item) => ({
          key: item.key,
          label: item.label,
          onClick: () => void handleEnableAuthenticator(item.mode),
        })),
    [handleEnableAuthenticator, passkeySettingsQuery.data?.enabled, persistedEmailLoginEnabled, persistedPasswordLoginEnabled, smsSettingsQuery.data?.enabled, wechatSettingsQuery.data?.enabled],
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
        API_OPTS.NO_REDIRECT,
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
        render: (_, record) => (record.enabled ? <CheckOutlined style={{ color: token.colorSuccess }} /> : null),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 240,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            inlineCount={responsive.isMobile ? 0 : 3}
            items={[
              {
                key: 'toggle',
                label: record.enabled ? '禁用' : '启用',
                danger: record.enabled,
                disabled: !canManageSettings,
                loading: togglingAuthenticatorKey === record.key,
                onClick: () => void handleToggleAuthenticator(record),
              },
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
    [canManageSettings, handleDeleteAuthenticator, handleToggleAuthenticator, openConfigDrawer, responsive.isDesktop, responsive.isMobile, token.colorTextSecondary, togglingAuthenticatorKey],
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
      return <SmsConfigTab 
      smsFormProps={smsFormProps}
      canManageSettings={canManageSettings}
      smsConfigEnabled={smsConfigEnabled}
      handleSmsProviderChange={handleSmsProviderChange}
      smsAccessKeySecretConfigured={smsAccessKeySecretConfigured}
      providerDrafts={providerDrafts}
      provider={smsSettingsForm.getFieldValue('provider') || 'aliyun'}
    />;
    }
    if (configDrawerMode === 'email') {
      return <EmailConfigTab 
      smtpFormProps={smtpFormProps}
      smtpTestFormProps={{ form: smtpTestForm, onFinish: handleTestSmtp }}
      canManageSettings={canManageSettings}
      emailConfigEnabled={emailConfigEnabled}
      smtpSettingsQuery={smtpSettingsQuery}
      verificationSettingsQuery={verificationSettingsQuery}
      smtpPasswordConfigured={smtpPasswordConfigured}
      testingSmtpSettings={testingSmtpSettings}
      handleTestSmtp={handleTestSmtp}
    />;
    }
    if (configDrawerMode === 'wechat') {
      return <WechatConfigTab 
      wechatFormProps={wechatFormProps}
      canManageSettings={canManageSettings}
      wechatAppSecretConfigured={wechatAppSecretConfigured}
    />;
    }
    if (configDrawerMode === 'passkey') {
      return <PasskeyConfigTab 
      passkeyFormProps={passkeyFormProps}
      canManageSettings={canManageSettings}
      passkeyConfigEnabled={passkeyConfigEnabled}
    />;
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
