import { history, useLocation } from '@umijs/max';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Divider, Form, Input, InputNumber, Select, Space, Switch, Tabs, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementPage } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { systemService } from '@/services/system';
import type {
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';

const TAB_KEYS = ['totp', 'sms', 'email', 'wechat'] as const;

type VerificationTabKey = (typeof TAB_KEYS)[number];
type SmsProviderCode = 'aliyun' | 'tencent' | 'mock' | 'custom';

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

const normalizeTabKey = (value?: string | null): VerificationTabKey => {
  if (value === 'sms' || value === 'email' || value === 'wechat') {
    return value;
  }
  return 'totp';
};

const verificationFormInitialValues: VerificationSettings = {
  enabled: true,
  emailLoginEnabled: false,
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
  const actionPermission = useActionPermission();
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
  const [providerDrafts, setProviderDrafts] = useState<Partial<Record<SmsProviderCode, SmsVerificationSettings>>>({});
  const [activeTab, setActiveTab] = useState<VerificationTabKey>(() => normalizeTabKey(new URLSearchParams(location.search).get('tab')));
  const [verificationSaving, setVerificationSaving] = useState(false);
  const [savingSmsSettings, setSavingSmsSettings] = useState(false);
  const [savingEmailSettings, setSavingEmailSettings] = useState(false);
  const [savingWechatSettings, setSavingWechatSettings] = useState(false);
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

  const currentProvider = Form.useWatch('provider', smsSettingsForm);
  const smsEnabled = Form.useWatch('enabled', smsSettingsForm) ?? false;
  const wechatEnabled = Form.useWatch('enabled', wechatSettingsForm) ?? false;

  const updateTabInUrl = useCallback(
    (nextTab: VerificationTabKey) => {
      const searchParams = new URLSearchParams(location.search);
      searchParams.set('tab', nextTab);
      history.replace({
        pathname: location.pathname,
        search: `?${searchParams.toString()}`,
      });
    },
    [location.pathname, location.search],
  );

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const normalizedTab = normalizeTabKey(searchParams.get('tab'));
    setActiveTab(normalizedTab);
    if (searchParams.get('tab') !== normalizedTab) {
      updateTabInUrl(normalizedTab);
    }
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

  const handleSaveVerificationSettings = async () => {
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
      const nextEmailLoginEnabled = Boolean(verificationForm.getFieldValue('emailLoginEnabled'));

      if (nextEmailLoginEnabled) {
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
      }

      const verificationValues = await verificationForm.validateFields();
      const result = await systemService.updateVerificationSettings(verificationValues, { autoRedirectOnUnauthorized: false });
      verificationForm.setFieldsValue(result);

      message.success(nextEmailLoginEnabled ? '邮箱验证码登录与 SMTP 配置已保存' : '邮箱验证码登录设置已保存');
      await Promise.all([verificationSettingsQuery.refetch(), smtpSettingsQuery.refetch()]);
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
          accessKeySecret,
        },
        { autoRedirectOnUnauthorized: false },
      );
      message.success(result.configured ? '短信验证码配置已保存' : '短信验证码配置已保存，当前仍未完全启用');
      await smsSettingsQuery.refetch();
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
    } finally {
      setSavingWechatSettings(false);
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

  const activeProvider = normalizeProviderCode(currentProvider);
  const providerSchema = SMS_PROVIDER_SCHEMAS[activeProvider];
  const verificationLoading =
    verificationSettingsQuery.isLoading || smsSettingsQuery.isLoading || smtpSettingsQuery.isLoading || wechatSettingsQuery.isLoading;
  const emailLoginEnabled = Form.useWatch('emailLoginEnabled', verificationForm) ?? false;
  const smsAccessKeySecretConfigured = smsSettingsQuery.data?.accessKeySecretConfigured ?? false;
  const wechatAppSecretConfigured = wechatSettingsQuery.data?.appSecretConfigured ?? false;

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
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" loading={verificationSaving} disabled={!canManageSettings} onClick={() => void handleSaveVerificationSettings()}>
            保存 2FA 设置
          </Button>
        </div>
      </Form>
    </Space>
  );

  const renderSmsTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...smsFormProps}>
        <Form.Item name="enabled" label="启用短信验证码" valuePropName="checked">
          <Switch disabled={!canManageSettings} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
        <Form.Item
          name="provider"
          label="服务商"
          rules={smsEnabled ? [{ required: true, message: '请选择短信服务商' }] : undefined}
        >
          <Select
            disabled={!canManageSettings || !smsEnabled}
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
            rules={smsEnabled && field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}
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
                disabled={!canManageSettings || !smsEnabled}
                placeholder={field.placeholder}
              />
            ) : (
              <Input disabled={!canManageSettings || !smsEnabled} placeholder={field.placeholder} />
            )}
          </Form.Item>
        ))}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" loading={savingSmsSettings} disabled={!canManageSettings || !smsEnabled} onClick={() => void handleSaveSmsSettings()}>
            保存配置
          </Button>
        </div>
      </Form>
    </Space>
  );

  const renderEmailTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="邮箱与 SMTP" loading={smtpSettingsQuery.isLoading || verificationSettingsQuery.isLoading}>
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
          <Form {...verificationFormProps}>
            <Form.Item
              name="emailLoginEnabled"
              label="启用邮箱验证码登录"
              valuePropName="checked"
            >
              <Switch disabled={!canManageSettings} checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
          </Form>

          <Divider style={{ margin: 0 }} />

          <div style={{ opacity: emailLoginEnabled ? 1 : 0.48, transition: 'opacity 0.2s ease' }}>
            <Form {...smtpFormProps}>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                SMTP 基础配置
              </Typography.Title>
              <Form.Item name="host" label="SMTP 主机" rules={[{ required: true, message: '请输入 SMTP 主机' }]}>
                <Input disabled={!canManageSettings || !emailLoginEnabled} placeholder="smtp.example.com" />
              </Form.Item>
              <Form.Item name="port" label="SMTP 端口" rules={[{ required: true, message: '请输入 SMTP 端口' }]}>
                <InputNumber disabled={!canManageSettings || !emailLoginEnabled} style={{ width: '100%' }} min={1} max={65535} />
              </Form.Item>
              <Form.Item name="username" label="SMTP 用户名" rules={[{ required: true, message: '请输入 SMTP 用户名' }]}>
                <Input disabled={!canManageSettings || !emailLoginEnabled} placeholder="username@example.com" />
              </Form.Item>
              <Form.Item
                name="password"
                label="SMTP 密码"
                extra={smtpSettingsQuery.data?.passwordConfigured ? '当前密码已脱敏显示，留空则保留现有密码' : '留空则保留现有密码'}
              >
                <Input.Password
                  disabled={!canManageSettings || !emailLoginEnabled}
                  placeholder="留空则保留现有密码"
                />
              </Form.Item>
              <Form.Item name="from" label="发件人地址" rules={[{ required: true, message: '请输入发件人地址' }]}>
                <Input disabled={!canManageSettings || !emailLoginEnabled} placeholder="noreply@example.com" />
              </Form.Item>
              <Form.Item name="authEnabled" label="启用认证" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailLoginEnabled} />
              </Form.Item>
              <Form.Item name="startTlsEnabled" label="启用 STARTTLS" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailLoginEnabled} />
              </Form.Item>
              <Form.Item name="sslEnabled" label="启用 SSL" valuePropName="checked">
                <Switch disabled={!canManageSettings || !emailLoginEnabled} />
              </Form.Item>
            </Form>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" loading={savingEmailSettings} disabled={!canManageSettings} onClick={() => void handleSaveEmailSettings()}>
              保存设置
            </Button>
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
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" loading={testingSmtpSettings} disabled={!canManageSettings} onClick={() => void handleTestSmtpSettings()}>
              发送测试邮件
            </Button>
          </div>
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
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" loading={savingWechatSettings} disabled={!canManageSettings} onClick={() => void handleSaveWechatSettings()}>
            保存配置
          </Button>
        </div>
      </Form>
    </Space>
  );

  return (
    <ManagementPage title="验证管理">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card loading={verificationLoading}>
          <Tabs
            activeKey={activeTab}
            items={[
              { key: 'totp', label: '2FA', children: renderVerificationTab() },
              { key: 'sms', label: '短信验证码', children: renderSmsTab() },
              { key: 'email', label: '邮箱与 SMTP', children: renderEmailTab() },
              { key: 'wechat', label: '微信登录', children: renderWechatTab() },
            ]}
            onChange={(key) => {
              const nextTab = normalizeTabKey(key);
              setActiveTab(nextTab);
              updateTabInUrl(nextTab);
            }}
            destroyInactiveTabPane
          />
        </Card>
      </Space>
    </ManagementPage>
  );
};

export default SystemVerificationPage;
