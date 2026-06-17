import { history, useLocation } from '@umijs/max';
import { getLocale } from '@umijs/max';
import type { FormInstance } from 'antd';
import { Button, Card, Form, Input, InputNumber, Select, Space, Switch, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  PasskeySettings,
  SmsVerificationSettings,
  SmtpSettings,
  SmtpTestPayload,
  SmtpTestResult,
  VerificationSettings,
  WechatLoginSettings,
} from '@/types/api';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { useStandardFormProps } from '@/features/form/config';
import type { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { normalizeLocale } from '@/i18n/locale';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type ConfigDrawerMode = 'totp' | 'sms' | 'email' | 'wechat' | 'passkey' | 'basic';
type SmsProviderCode = 'aliyun' | 'tencent' | 'mock' | 'custom';

interface SmsProviderFieldConfig {
  name: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  password?: boolean;
}

interface SmsProviderSchema {
  fields: SmsProviderFieldConfig[];
}

const verificationFormInitialValues: VerificationSettings = {
  enabled: true,
  emailLoginEnabled: false,
  passwordLoginEnabled: true,
  loginModeOrder: ['passkey', 'sms', 'email', 'wechat', 'password'],
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
  subject: t('SMTP 测试邮件', 'SMTP test email'),
  content: t('这是一封来自系统的 SMTP 测试邮件。', 'This is an SMTP test email from the system.'),
  toEmail: '',
};

const SMS_ACCESS_KEY_SECRET_MASK = '********';
const SMTP_PASSWORD_MASK = '********';
const WECHAT_APP_SECRET_MASK = '********';
const LEGACY_PASSKEY_RP_ID = 'saas.elexvx.com';
const LEGACY_PASSKEY_ORIGIN = 'https://saas.elexvx.com';
const LOCAL_PASSKEY_RP_IDS = new Set(['localhost', '127.0.0.1', '0.0.0.0', '::1']);

const resolveCurrentPasskeyOrigin = () => {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.location.origin;
};

const resolveCurrentPasskeyRpId = () => {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.location.hostname;
};

const isReplaceablePasskeyRpId = (rpId?: string) => {
  const normalized = rpId?.trim().toLowerCase();
  return !normalized || normalized === LEGACY_PASSKEY_RP_ID || LOCAL_PASSKEY_RP_IDS.has(normalized);
};

const isReplaceablePasskeyOrigin = (origin?: string) => {
  const normalized = origin?.trim();
  if (!normalized || normalized === LEGACY_PASSKEY_ORIGIN) {
    return true;
  }
  try {
    return LOCAL_PASSKEY_RP_IDS.has(new URL(normalized).hostname.toLowerCase());
  } catch {
    return false;
  }
};

const shouldUseDynamicPasskeyOrigins = (origins?: string[]) =>
  !origins?.length || origins.every(isReplaceablePasskeyOrigin);

const buildPasskeyDefaults = (rpName: string) => {
  const currentOrigin = resolveCurrentPasskeyOrigin();
  return {
    enabled: true,
    passwordlessEnabled: true,
    selfBindingEnabled: true,
    rpId: resolveCurrentPasskeyRpId(),
    rpName,
    allowedOrigins: currentOrigin ? [currentOrigin] : [],
    allowedOriginsText: currentOrigin,
    challengeTtlSeconds: 120,
  };
};

const SMS_PROVIDER_OPTIONS: Array<{ label: string; value: SmsProviderCode }> = [
  { label: t('阿里云短信', 'Alibaba Cloud SMS'), value: 'aliyun' },
  { label: t('腾讯云短信', 'Tencent Cloud SMS'), value: 'tencent' },
  { label: t('本地模拟', 'Local mock'), value: 'mock' },
  { label: t('自定义网关', 'Custom gateway'), value: 'custom' },
];

const SMS_PROVIDER_SCHEMAS: Record<SmsProviderCode, SmsProviderSchema> = {
  aliyun: {
    fields: [
      { name: 'signName', label: t('短信签名', 'SMS sign name'), placeholder: t('例如：宏翔商道', 'e.g. Hongxiang Shangdao'), required: true },
      { name: 'templateCode', label: t('模板编码', 'Template code'), placeholder: t('例如：SMS_123456789', 'e.g. SMS_123456789'), required: true },
      { name: 'accessKeyId', label: 'Access Key ID', placeholder: t('短信服务访问密钥 ID', 'SMS service access key ID'), required: true },
      { name: 'accessKeySecret', label: 'Access Key Secret', placeholder: t('留空则保持现有密钥', 'Leave blank to keep the existing secret'), password: true },
      { name: 'endpoint', label: t('服务地址', 'Endpoint'), placeholder: t('例如：https://dysmsapi.aliyuncs.com', 'e.g. https://dysmsapi.aliyuncs.com') },
      { name: 'region', label: t('地域', 'Region'), placeholder: t('例如：cn-hangzhou', 'e.g. cn-hangzhou') },
    ],
  },
  tencent: {
    fields: [
      { name: 'signName', label: t('短信签名', 'SMS sign name'), placeholder: t('例如：宏翔商道', 'e.g. Hongxiang Shangdao'), required: true },
      { name: 'templateCode', label: t('模板 ID', 'Template ID'), placeholder: t('例如：1234567', 'e.g. 1234567'), required: true },
      { name: 'accessKeyId', label: 'SecretId', placeholder: t('腾讯云 SecretId', 'Tencent Cloud SecretId'), required: true },
      { name: 'accessKeySecret', label: 'SecretKey', placeholder: t('留空则保持现有密钥', 'Leave blank to keep the existing secret'), password: true, required: true },
      { name: 'endpoint', label: t('API 地址', 'API endpoint'), placeholder: t('例如：https://sms.tencentcloudapi.com', 'e.g. https://sms.tencentcloudapi.com') },
      { name: 'region', label: t('地域', 'Region'), placeholder: t('例如：ap-guangzhou', 'e.g. ap-guangzhou') },
    ],
  },
  mock: {
    fields: [
      { name: 'signName', label: t('模拟签名', 'Mock sign name'), placeholder: t('例如：测试短信', 'e.g. test SMS') },
      { name: 'templateCode', label: t('模拟模板编码', 'Mock template code'), placeholder: t('例如：MOCK_SMS_001', 'e.g. MOCK_SMS_001') },
    ],
  },
  custom: {
    fields: [
      { name: 'endpoint', label: t('网关地址', 'Gateway URL'), placeholder: t('例如：https://sms.example.com/api', 'e.g. https://sms.example.com/api'), required: true },
      { name: 'accessKeyId', label: t('网关账号', 'Gateway account'), placeholder: t('例如：gateway-user', 'e.g. gateway-user'), required: true },
      { name: 'accessKeySecret', label: t('网关密钥', 'Gateway secret'), placeholder: t('留空则保持现有密钥', 'Leave blank to keep the existing secret'), password: true, required: true },
      { name: 'signName', label: t('签名', 'Sign name'), placeholder: t('例如：宏翔商道', 'e.g. Hongxiang Shangdao'), required: true },
      { name: 'templateCode', label: t('模板编码', 'Template code'), placeholder: t('例如：SMS_123456789', 'e.g. SMS_123456789'), required: true },
      { name: 'region', label: t('地域', 'Region'), placeholder: t('按网关要求填写', 'Fill in as required by the gateway') },
    ],
  },
} as const;

type DrawerFooterRouteParams = {
  canManageSettings: boolean;
  closeConfigDrawer: () => void;
  verificationLoading: boolean;
  savingSmsSettings: boolean;
  savingEmailSettings: boolean;
  savingWechatSettings: boolean;
  savingPasskeySettings: boolean;
  testingSmtpSettings: boolean;
  verificationForm: { setFieldValue: (name: 'passwordLoginEnabled', value: boolean) => void };
  handleSaveVerificationSettings: (options?: { closeDrawer?: boolean }) => Promise<void>;
  handleSaveEmailSettings: () => Promise<void>;
  handleSaveSmsSettings: () => Promise<void>;
  handleSaveWechatSettings: () => Promise<void>;
  handleSavePasskeySettings: () => Promise<void>;
  handleTestSmtpSettings: () => Promise<void>;
  onAuthenticatorSaved?: (mode: 'sms' | 'email' | 'wechat' | 'passkey' | 'password') => Promise<unknown>;
};

type DrawerContentRouteParams = {
  configDrawerMode: ConfigDrawerMode | null;
  canManageSettings: boolean;
  verificationFormProps: ReturnType<typeof useStandardFormProps>;
  smsFormProps: ReturnType<typeof useStandardFormProps>;
  smtpFormProps: ReturnType<typeof useStandardFormProps>;
  smtpTestFormProps: ReturnType<typeof useStandardFormProps>;
  wechatFormProps: ReturnType<typeof useStandardFormProps>;
  passkeyFormProps: ReturnType<typeof useStandardFormProps>;
  verificationLoading: boolean;
  smsSettingsData?: SmsVerificationSettings;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
  wechatSettingsData?: WechatLoginSettings;
  handleSmsProviderChange: (nextProvider: string) => void;
};

const BasicDrawerContent = () => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Typography.Paragraph style={{ marginBottom: 0 }}>
        {t('密码复杂度、验证码和登录防御阈值请在安全设置中统一维护。', 'Password complexity, verification codes, and login defense thresholds are managed centrally in Security Settings.')}
      </Typography.Paragraph>
      <Button type="primary" onClick={() => history.push('/settings/security')}>
        {t('前往安全设置', 'Go to Security Settings')}
      </Button>
    </Space>
  );
};

const TotpDrawerContent = ({
  canManageSettings,
  verificationFormProps,
}: {
  canManageSettings: boolean;
  verificationFormProps: ReturnType<typeof useStandardFormProps>;
}) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...verificationFormProps}>
        <Form.Item
          name="enabled"
          label={t('启用 2FA', 'Enable 2FA')}
          valuePropName="checked"
          extra={t('关闭后，系统中的高危操作二次确认将不再要求 2FA。', 'When disabled, high-risk operation confirmations will no longer require 2FA.')}
        >
          <Switch disabled={!canManageSettings} />
        </Form.Item>
      </Form>
    </Space>
  );
};

const SmsDrawerContent = ({
  canManageSettings,
  smsFormProps,
  smsSettingsData,
  handleSmsProviderChange,
}: {
  canManageSettings: boolean;
  smsFormProps: ReturnType<typeof useStandardFormProps>;
  smsSettingsData?: SmsVerificationSettings;
  handleSmsProviderChange: (nextProvider: string) => void;
}) => {
  const provider = (Form.useWatch('provider', smsFormProps.form) || 'aliyun') as SmsProviderCode;
  const providerSchema = SMS_PROVIDER_SCHEMAS[provider] ?? SMS_PROVIDER_SCHEMAS.aliyun;
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...smsFormProps}>
        <Form.Item
          name="provider"
          label={t('服务商', 'Provider')}
          rules={[{ required: true, message: t('请选择短信服务商', 'Please select an SMS provider') }]}
        >
          <Select
            disabled={!canManageSettings}
            options={SMS_PROVIDER_OPTIONS}
            placeholder={t('请选择短信服务商', 'Please select an SMS provider')}
            onChange={handleSmsProviderChange}
          />
        </Form.Item>
        {providerSchema.fields.map((field) => (
          <Form.Item
            key={String(field.name)}
            name={field.name}
            label={field.label}
            rules={field.required ? [{ required: true, message: t('请输入{label}', 'Please enter {label}').replace('{label}', field.label) }] : undefined}
            extra={
              field.password && field.name === 'accessKeySecret'
                ? smsSettingsData?.accessKeySecretConfigured
                  ? t('当前密钥已脱敏显示，留空则保持现有密钥', 'The current secret is masked. Leave blank to keep the existing one.')
                  : t('留空则保持现有密钥', 'Leave blank to keep the existing secret')
                : undefined
            }
          >
            {field.password ? (
              <Input.Password disabled={!canManageSettings} placeholder={field.placeholder} />
            ) : (
              <Input disabled={!canManageSettings} placeholder={field.placeholder} />
            )}
          </Form.Item>
        ))}
      </Form>
    </Space>
  );
};

  const EmailDrawerContent = ({
  canManageSettings,
  verificationLoading,
  smtpFormProps,
  smtpTestFormProps,
  smtpSettingsData,
}: {
  canManageSettings: boolean;
  verificationLoading: boolean;
  smtpFormProps: ReturnType<typeof useStandardFormProps>;
  smtpTestFormProps: ReturnType<typeof useStandardFormProps>;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
}) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Card title={t('邮箱与 SMTP', 'Email and SMTP')} loading={verificationLoading}>
        <div style={{ opacity: 1, transition: 'opacity 0.2s ease' }}>
      <Form {...smtpFormProps}>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {t('SMTP 基础配置', 'SMTP settings')}
            </Typography.Title>
            <Form.Item name="host" label={t('SMTP 主机', 'SMTP host')} rules={[{ required: true, message: t('请输入 SMTP 主机', 'Please enter the SMTP host') }]}>
              <Input disabled={!canManageSettings} placeholder="smtp.example.com" />
            </Form.Item>
            <Form.Item name="port" label={t('SMTP 端口', 'SMTP port')} rules={[{ required: true, message: t('请输入 SMTP 端口', 'Please enter the SMTP port') }]}>
              <InputNumber disabled={!canManageSettings} style={{ width: '100%' }} min={1} max={65535} />
            </Form.Item>
            <Form.Item name="username" label={t('SMTP 用户名', 'SMTP username')} rules={[{ required: true, message: t('请输入 SMTP 用户名', 'Please enter the SMTP username') }]}>
              <Input disabled={!canManageSettings} placeholder="username@example.com" />
            </Form.Item>
            <Form.Item
              name="password"
              label={t('SMTP 密码', 'SMTP password')}
              extra={smtpSettingsData?.passwordConfigured ? t('当前密码已脱敏显示，留空则保留现有密码', 'The current password is masked. Leave blank to keep the existing one.') : t('留空则保留现有密码', 'Leave blank to keep the existing password')}
            >
              <Input.Password disabled={!canManageSettings} placeholder={t('留空则保持现有密码', 'Leave blank to keep the existing password')} />
            </Form.Item>
            <Form.Item name="from" label={t('发件人地址', 'From address')} rules={[{ required: true, message: t('请输入发件人地址', 'Please enter the from address') }]}>
              <Input disabled={!canManageSettings} placeholder="noreply@example.com" />
            </Form.Item>
            <Form.Item name="authEnabled" label={t('启用认证', 'Enable authentication')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
            <Form.Item name="startTlsEnabled" label={t('启用 STARTTLS', 'Enable STARTTLS')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
            <Form.Item name="sslEnabled" label={t('启用 SSL', 'Enable SSL')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
          </Form>
        </div>
      </Card>
      <Card title={t('SMTP 测试发送', 'SMTP test send')} loading={verificationLoading}>
        <Form {...smtpTestFormProps}>
          <Form.Item
            name="toEmail"
            label={t('收件人邮箱', 'Recipient email')}
            rules={[{ required: true, message: t('请输入收件人邮箱', 'Please enter the recipient email') }, { type: 'email', message: t('请输入有效邮箱地址', 'Please enter a valid email address') }]}
          >
            <Input disabled={!canManageSettings} placeholder="recipient@example.com" />
          </Form.Item>
          <Form.Item name="subject" label={t('邮件主题', 'Email subject')}>
            <Input disabled={!canManageSettings} />
          </Form.Item>
          <Form.Item name="content" label={t('邮件内容', 'Email content')}>
            <Input.TextArea disabled={!canManageSettings} rows={6} />
          </Form.Item>
        </Form>
      </Card>
    </Space>
  );
};

const renderEmailDrawerContent = ({
  canManageSettings,
  verificationLoading,
  smtpFormProps,
  smtpTestFormProps,
  smtpSettingsData,
}: {
  canManageSettings: boolean;
  verificationLoading: boolean;
  smtpFormProps: ReturnType<typeof useStandardFormProps>;
  smtpTestFormProps: ReturnType<typeof useStandardFormProps>;
  smtpSettingsData?: SmtpSettings & { passwordConfigured?: boolean };
}) => (
  <EmailDrawerContent
    smtpFormProps={smtpFormProps}
    smtpTestFormProps={smtpTestFormProps}
    canManageSettings={canManageSettings}
    verificationLoading={verificationLoading}
    smtpSettingsData={smtpSettingsData}
  />
);

const WechatDrawerContent = ({
  canManageSettings,
  wechatFormProps,
  wechatSettingsData,
}: {
  canManageSettings: boolean;
  wechatFormProps: ReturnType<typeof useStandardFormProps>;
  wechatSettingsData?: WechatLoginSettings;
}) => {
  const wechatEnabled = Boolean(Form.useWatch('enabled', wechatFormProps.form));
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...wechatFormProps}>
        <Form.Item name="enabled" label={t('启用微信登录', 'Enable WeChat login')} valuePropName="checked">
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item
          name="appId"
          label={t('AppID', 'AppID')}
          rules={wechatEnabled ? [{ required: true, message: t('请输入 AppID', 'Please enter the AppID') }] : undefined}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder={t('微信开放平台网站应用 AppID', 'WeChat Open Platform website application AppID')} />
        </Form.Item>
        <Form.Item
          name="appSecret"
          label={t('AppSecret', 'AppSecret')}
          extra={wechatSettingsData?.appSecretConfigured ? t('当前密钥已脱敏显示，留空则保持现有密钥', 'The current secret is masked. Leave blank to keep the existing one.') : t('留空则保持现有密钥', 'Leave blank to keep the existing secret')}
        >
          <Input.Password disabled={!canManageSettings || !wechatEnabled} placeholder={t('留空则保持现有密钥', 'Leave blank to keep the existing secret')} />
        </Form.Item>
        <Form.Item
          name="redirectUri"
          label={t('回调地址', 'Callback URL')}
          rules={wechatEnabled ? [{ required: true, message: t('请输入回调地址', 'Please enter the callback URL') }] : undefined}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder={t('https://你的域名/api/v1/auth/wechat/callback', 'https://your-domain.com/api/v1/auth/wechat/callback')} />
        </Form.Item>
        <Form.Item
          name="stateExpireMinutes"
          label={t('状态有效期', 'State TTL')}
          rules={wechatEnabled ? [{ required: true, message: t('请输入状态有效期', 'Please enter the state TTL') }] : undefined}
        >
          <InputNumber disabled={!canManageSettings || !wechatEnabled} style={{ width: '100%' }} min={1} max={60} addonAfter={t('分钟', 'min')} />
        </Form.Item>
      </Form>
    </Space>
  );
};

  const PasskeyDrawerContent = ({
  canManageSettings,
  passkeyFormProps,
}: {
  canManageSettings: boolean;
  passkeyFormProps: ReturnType<typeof useStandardFormProps>;
}) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const currentRpId = resolveCurrentPasskeyRpId();
  const currentOrigin = resolveCurrentPasskeyOrigin();

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...passkeyFormProps}>
        <Form.Item
          name="passwordlessEnabled"
          label={t('允许无账号登录', 'Allow sign-in without an account')}
          valuePropName="checked"
          extra={t('开启后，登录页可直接唤起密码管理器或系统钥匙串选择通行密钥。', 'When enabled, the login page can directly open your password manager or system keychain to select a passkey.')}
        >
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item name="selfBindingEnabled" label={t('允许用户自助绑定', 'Allow self-service binding')} valuePropName="checked">
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item name="rpId" label={t('RP ID', 'RP ID')} rules={[{ required: true, message: t('请输入 RP ID', 'Please enter the RP ID') }]}>
          <Input disabled={!canManageSettings} placeholder={currentRpId || t('当前网站域名', 'Current website host')} />
        </Form.Item>
        <Form.Item name="rpName" label={t('RP 名称', 'RP name')} rules={[{ required: true, message: t('请输入 RP 名称', 'Please enter the RP name') }]}>
          <Input disabled={!canManageSettings} placeholder={t('宏翔商道后台管理系统', 'SaaS admin system')} />
        </Form.Item>
        <Form.Item
          name="allowedOriginsText"
          label={t('允许的 Origin', 'Allowed origins')}
          rules={[{ required: true, message: t('请输入允许的 Origin', 'Please enter the allowed origins') }]}
          extra={t('每行一个当前站点 Origin；生产环境应使用 HTTPS，localhost 调试除外。Vercel Preview 域名不会默认放行。', 'One current-site origin per line. Use HTTPS in production, except localhost development. Vercel Preview domains are not allowed by default.')}
        >
          <Input.TextArea disabled={!canManageSettings} rows={4} placeholder={currentOrigin || t('当前网站 Origin', 'Current website origin')} />
        </Form.Item>
        <Form.Item name="challengeTtlSeconds" label={t('Challenge 有效期', 'Challenge TTL')}>
          <InputNumber disabled={!canManageSettings} style={{ width: '100%' }} min={30} max={600} addonAfter={t('秒', 's')} />
        </Form.Item>
      </Form>
    </Space>
  );
};

const resolveDrawerContentRoute = (params: DrawerContentRouteParams) => {
  switch (params.configDrawerMode ?? 'basic') {
    case 'sms':
      return (
        <SmsDrawerContent
          canManageSettings={params.canManageSettings}
          smsFormProps={params.smsFormProps}
          smsSettingsData={params.smsSettingsData}
          handleSmsProviderChange={params.handleSmsProviderChange}
        />
      );
    case 'email':
      return renderEmailDrawerContent({
        canManageSettings: params.canManageSettings,
        verificationLoading: params.verificationLoading,
        smtpFormProps: params.smtpFormProps,
        smtpTestFormProps: params.smtpTestFormProps,
        smtpSettingsData: params.smtpSettingsData,
      });
    case 'wechat':
      return (
        <WechatDrawerContent
          canManageSettings={params.canManageSettings}
          wechatFormProps={params.wechatFormProps}
          wechatSettingsData={params.wechatSettingsData}
        />
      );
    case 'passkey':
      return <PasskeyDrawerContent canManageSettings={params.canManageSettings} passkeyFormProps={params.passkeyFormProps} />;
    case 'totp':
      return <TotpDrawerContent canManageSettings={params.canManageSettings} verificationFormProps={params.verificationFormProps} />;
    case 'basic':
    default:
      return <BasicDrawerContent />;
  }
};

const cancelAction = (closeConfigDrawer: () => void): ManagementDrawerAction => ({
  key: 'cancel',
  label: t('取消', 'Cancel'),
  onClick: () => closeConfigDrawer(),
});

const buildSaveModeActions = ({
  canManageSettings,
  closeConfigDrawer,
  loading,
  label,
  onSave,
}: {
  canManageSettings: boolean;
  closeConfigDrawer: () => void;
  loading: boolean;
  label: string;
  onSave: () => Promise<void> | void;
}): ManagementDrawerAction[] => [
  cancelAction(closeConfigDrawer),
  {
    key: 'save',
    label,
    type: 'primary',
    loading,
    disabled: !canManageSettings,
    onClick: () => void onSave(),
  },
];

const buildDrawerFooterActionsRoute = (configDrawerMode: ConfigDrawerMode | null, params: DrawerFooterRouteParams) => {
  switch (configDrawerMode ?? 'basic') {
    case 'sms':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.savingSmsSettings,
        label: t('保存配置', 'Save settings'),
        onSave: params.handleSaveSmsSettings,
      });
    case 'email':
      return [
        {
          key: 'test',
          label: t('发送测试邮件', 'Send test email'),
          loading: params.testingSmtpSettings,
          disabled: !params.canManageSettings,
          onClick: () => void params.handleTestSmtpSettings(),
        },
        cancelAction(params.closeConfigDrawer),
        {
          key: 'save',
          label: t('保存设置', 'Save settings'),
          type: 'primary',
          loading: params.savingEmailSettings,
          disabled: !params.canManageSettings,
          onClick: () => void params.handleSaveEmailSettings(),
        },
      ] satisfies ManagementDrawerAction[];
    case 'wechat':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.savingWechatSettings,
        label: t('保存配置', 'Save settings'),
        onSave: params.handleSaveWechatSettings,
      });
    case 'passkey':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.savingPasskeySettings,
        label: t('保存配置', 'Save settings'),
        onSave: params.handleSavePasskeySettings,
      });
    case 'totp':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.verificationLoading,
        label: t('保存 2FA 设置', 'Save 2FA settings'),
        onSave: () => params.handleSaveVerificationSettings({ closeDrawer: true }),
      });
    case 'basic':
    default:
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.verificationLoading,
        label: t('保存设置', 'Save settings'),
        onSave: async () => {
          params.verificationForm.setFieldValue('passwordLoginEnabled', true);
          await params.handleSaveVerificationSettings({ closeDrawer: true });
          await params.onAuthenticatorSaved?.('password');
        },
      });
  }
};

interface UseAuthenticatorConfigDrawerParams {
  canManageSettings: boolean;
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
  onAuthenticatorSaved?: (mode: 'sms' | 'email' | 'wechat' | 'passkey' | 'password') => Promise<unknown>;
}

export const useAuthenticatorConfigDrawer = ({
  canManageSettings,
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
  onAuthenticatorSaved,
}: UseAuthenticatorConfigDrawerParams) => {
  const { initialState } = useInitialStateModel();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const dynamicPasskeyDefaults = useMemo(
    () => buildPasskeyDefaults(t(`${brandingSettings.websiteName}后台管理系统`, `${brandingSettings.websiteName} admin system`)),
    [brandingSettings.websiteName],
  );

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

  const location = useLocation();
  const [configDrawerMode, setConfigDrawerMode] = useState<ConfigDrawerMode | null>(() =>
    normalizeDrawerMode(new URLSearchParams(location.search).get('tab')),
  );
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
    setConfigDrawerMode(normalizeDrawerMode(new URLSearchParams(location.search).get('tab')));
  }, [location.search]);

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
    initialValues: dynamicPasskeyDefaults,
  });
  useEffect(() => {
    if (verificationSettingsData) {
      verificationForm.setFieldsValue(verificationSettingsData);
    }
  }, [verificationForm, verificationSettingsData]);
  useEffect(() => {
    if (smtpSettingsData) {
      smtpSettingsForm.setFieldsValue({
        ...smtpSettingsData,
        password: smtpSettingsData.passwordConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
      });
    }
  }, [smtpSettingsData, smtpSettingsForm]);
  useEffect(() => {
    if (wechatSettingsData) {
      wechatSettingsForm.setFieldsValue({
        ...wechatSettingsData,
        appSecret: wechatSettingsData.appSecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
      });
    }
  }, [wechatSettingsData, wechatSettingsForm]);
  useEffect(() => {
    if (passkeySettingsData) {
      const shouldUseDynamicRpId = isReplaceablePasskeyRpId(passkeySettingsData.rpId);
      const shouldUseDynamicOrigins = shouldUseDynamicPasskeyOrigins(passkeySettingsData.allowedOrigins);
      const nextAllowedOrigins = shouldUseDynamicOrigins ? dynamicPasskeyDefaults.allowedOrigins : passkeySettingsData.allowedOrigins || [];
      passkeySettingsForm.setFieldsValue({
        ...passkeySettingsData,
        rpId: shouldUseDynamicRpId ? dynamicPasskeyDefaults.rpId : passkeySettingsData.rpId,
        rpName: passkeySettingsData.rpName || dynamicPasskeyDefaults.rpName,
        allowedOrigins: nextAllowedOrigins,
        allowedOriginsText: nextAllowedOrigins.join('\n'),
      });
    }
  }, [dynamicPasskeyDefaults, passkeySettingsData, passkeySettingsForm]);
  const resetVerificationDraft = useCallback(() => {
    if (verificationSettingsData) {
      verificationForm.setFieldsValue(verificationSettingsData);
    }
  }, [verificationForm, verificationSettingsData]);
  const resetSmtpDraft = useCallback(() => {
    if (smtpSettingsData) {
      smtpSettingsForm.setFieldsValue({
        ...smtpSettingsData,
        password: smtpSettingsData.passwordConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
      });
    }
  }, [smtpSettingsData, smtpSettingsForm]);
  const resetWechatDraft = useCallback(() => {
    if (wechatSettingsData) {
      wechatSettingsForm.setFieldsValue({
        ...wechatSettingsData,
        appSecret: wechatSettingsData.appSecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '',
      });
    }
  }, [wechatSettingsData, wechatSettingsForm]);
  const resetPasskeyDraft = useCallback(() => {
    if (passkeySettingsData) {
      const shouldUseDynamicRpId = isReplaceablePasskeyRpId(passkeySettingsData.rpId);
      const shouldUseDynamicOrigins = shouldUseDynamicPasskeyOrigins(passkeySettingsData.allowedOrigins);
      const nextAllowedOrigins = shouldUseDynamicOrigins ? dynamicPasskeyDefaults.allowedOrigins : passkeySettingsData.allowedOrigins || [];
      passkeySettingsForm.setFieldsValue({
        ...passkeySettingsData,
        rpId: shouldUseDynamicRpId ? dynamicPasskeyDefaults.rpId : passkeySettingsData.rpId,
        rpName: passkeySettingsData.rpName || dynamicPasskeyDefaults.rpName,
        allowedOrigins: nextAllowedOrigins,
        allowedOriginsText: nextAllowedOrigins.join('\n'),
      });
    }
  }, [dynamicPasskeyDefaults, passkeySettingsData, passkeySettingsForm]);
  const resetConfigDrafts = useMemo(
    () => () => {
      resetVerificationDraft();
      resetSmtpDraft();
      resetWechatDraft();
      resetPasskeyDraft();
    },
    [resetPasskeyDraft, resetSmtpDraft, resetVerificationDraft, resetWechatDraft],
  );

  const [providerDrafts, setProviderDrafts] = useState<Partial<Record<SmsProviderCode, SmsVerificationSettings>>>({});
  const [savingSmsSettings, setSavingSmsSettings] = useState(false);
  const [savingEmailSettings, setSavingEmailSettings] = useState(false);
  const [savingWechatSettings, setSavingWechatSettings] = useState(false);
  const [savingPasskeySettings, setSavingPasskeySettings] = useState(false);
  const [testingSmtpSettings, setTestingSmtpSettings] = useState(false);
  useEffect(() => {
    if (!smsSettingsData) {
      return;
    }
    const providerCode = normalizeProviderCode(smsSettingsData.provider);
    const accessKeySecret = smsSettingsData.accessKeySecretConfigured ? SMS_ACCESS_KEY_SECRET_MASK : '';
    setProviderDrafts((drafts) => ({
      ...drafts,
      [providerCode]: {
        ...smsSettingsData,
        accessKeySecret,
      },
    }));
    smsSettingsForm.setFieldsValue({
      ...smsSettingsData,
      accessKeySecret,
    });
  }, [smsSettingsData, smsSettingsForm]);

  const handleSmsProviderChange = useCallback(
    (nextProvider: string) => {
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
    },
    [providerDrafts, smsSettingsForm, setProviderDrafts],
  );

  const handleTestSmtpSettings = useCallback(async () => {
    if (!canManageSettings) {
      return;
    }
    setTestingSmtpSettings(true);
    try {
      const values = await smtpTestForm.validateFields();
      const result = await request<SmtpTestResult>('/v1/system/smtp-settings/test', {
        method: 'POST',
        data: values,
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(result.message || '测试邮件已发送');
    } finally {
      setTestingSmtpSettings(false);
    }
  }, [canManageSettings, smtpTestForm]);

  const openConfigDrawer = useCallback(
    (mode: ConfigDrawerMode) => {
      setConfigDrawerMode(mode);
      updateTabInUrl(mode);
    },
    [setConfigDrawerMode, updateTabInUrl],
  );
  const closeConfigDrawer = useCallback(
    (options?: { resetDraft?: boolean }) => {
      if (options?.resetDraft !== false) {
        resetConfigDrafts();
      }
      setConfigDrawerMode(null);
      updateTabInUrl(null);
    },
    [resetConfigDrafts, setConfigDrawerMode, updateTabInUrl],
  );

  const handleSaveVerificationSettings = useCallback(
    async (options?: { closeDrawer?: boolean }) => {
      if (!canManageSettings) {
        return;
      }
      const values = await verificationForm.validateFields();
      const result = await request<VerificationSettings>('/v1/system/verification/settings', {
        method: 'PUT',
        data: values,
        ...API_OPTS.NO_REDIRECT,
      });
      verificationForm.setFieldsValue(result);
      message.success('验证设置已保存');
      await onVerificationSettingsRefetch();
      if (options?.closeDrawer) {
        closeConfigDrawer();
      }
    },
    [canManageSettings, closeConfigDrawer, onVerificationSettingsRefetch, verificationForm],
  );

  const handleSaveEmailSettings = useCallback(async () => {
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
      const smtpResult = await request<SmtpSettings>('/v1/system/smtp-settings', {
        method: 'PUT',
        data: smtpPayload,
        ...API_OPTS.NO_REDIRECT,
      });
      smtpSettingsForm.setFieldsValue({
        ...smtpResult,
        password: smtpResult.passwordConfigured ? SMTP_PASSWORD_MASK : '',
      });

      const verificationValues = await verificationForm.validateFields();
      const result = await request<VerificationSettings>('/v1/system/verification/settings', {
        method: 'PUT',
        data: {
          ...verificationValues,
          emailLoginEnabled: true,
        },
        ...API_OPTS.NO_REDIRECT,
      });
      verificationForm.setFieldsValue(result);

      message.success('邮箱验证码登录与 SMTP 配置已保存');
      await Promise.all([onVerificationSettingsRefetch(), onSmtpSettingsRefetch()]);
      if (smtpResult.configured) {
        await onAuthenticatorSaved?.('email');
      }
      closeConfigDrawer();
    } finally {
      setSavingEmailSettings(false);
    }
  }, [
    canManageSettings,
    closeConfigDrawer,
    onSmtpSettingsRefetch,
    onVerificationSettingsRefetch,
    onAuthenticatorSaved,
    smtpSettingsForm,
    verificationForm,
  ]);

  const handleSaveSmsSettings = useCallback(async () => {
    if (!canManageSettings) {
      return;
    }
    setSavingSmsSettings(true);
    try {
      smsSettingsForm.setFieldValue('enabled', true);
      const values = await smsSettingsForm.validateFields();
      const result = await request<SmsVerificationSettings>('/v1/system/verification/sms-settings', {
        method: 'PUT',
        data: {
          ...values,
          enabled: true,
          accessKeySecret: values.accessKeySecret === SMS_ACCESS_KEY_SECRET_MASK ? undefined : values.accessKeySecret,
        },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(result.configured ? '短信验证码配置已保存' : '短信验证码配置已保存，当前仍未完全启用');
      await onSmsSettingsRefetch();
      if (result.configured) {
        await onAuthenticatorSaved?.('sms');
      }
      closeConfigDrawer();
    } finally {
      setSavingSmsSettings(false);
    }
  }, [canManageSettings, closeConfigDrawer, onAuthenticatorSaved, onSmsSettingsRefetch, smsSettingsForm]);

  const handleSaveWechatSettings = useCallback(async () => {
    if (!canManageSettings) {
      return;
    }
    setSavingWechatSettings(true);
    try {
      const values = await wechatSettingsForm.validateFields();
      const result = await request<WechatLoginSettings>('/v1/system/verification/wechat-settings', {
        method: 'PUT',
        data: {
          ...values,
          appSecret: values.appSecret === WECHAT_APP_SECRET_MASK ? undefined : values.appSecret,
        },
        ...API_OPTS.NO_REDIRECT,
      });
      wechatSettingsForm.setFieldsValue({
        ...result,
        appSecret: result.appSecretConfigured ? WECHAT_APP_SECRET_MASK : '',
      });
      message.success(result.configured ? '微信登录配置已保存' : '微信登录配置已保存，当前仍未完全启用');
      await onWechatSettingsRefetch();
      if (result.configured) {
        await onAuthenticatorSaved?.('wechat');
      }
      closeConfigDrawer();
    } finally {
      setSavingWechatSettings(false);
    }
  }, [canManageSettings, closeConfigDrawer, onAuthenticatorSaved, onWechatSettingsRefetch, wechatSettingsForm]);

  const handleSavePasskeySettings = useCallback(
    async (options?: { forceEnabled?: boolean; closeDrawer?: boolean }) => {
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
        const result = await request<PasskeySettings>('/v1/system/verification/passkey-settings', {
          method: 'PUT',
          data: {
            ...values,
            enabled: forceEnabled ? true : values.enabled,
            allowedOrigins: values.allowedOriginsText?.split('\n').map((item) => item.trim()).filter(Boolean) || [],
          },
          ...API_OPTS.NO_REDIRECT,
        });
        passkeySettingsForm.setFieldsValue({
          ...result,
          allowedOriginsText: result.allowedOrigins?.join('\n') || '',
        });
        message.success('通行密钥配置已保存');
        await onPasskeySettingsRefetch();
        await onAuthenticatorSaved?.('passkey');
        if (options?.closeDrawer !== false) {
          closeConfigDrawer();
        }
      } finally {
        setSavingPasskeySettings(false);
      }
    },
    [canManageSettings, closeConfigDrawer, onAuthenticatorSaved, onPasskeySettingsRefetch, passkeySettingsForm],
  );

  const resolveDrawerFooterActions = useCallback(
    () =>
      buildDrawerFooterActionsRoute(configDrawerMode, {
        canManageSettings,
        closeConfigDrawer,
        verificationLoading,
        savingSmsSettings,
        savingEmailSettings,
        savingWechatSettings,
        savingPasskeySettings,
        testingSmtpSettings,
        handleSaveVerificationSettings,
        handleSaveEmailSettings,
        handleSaveSmsSettings,
        handleSaveWechatSettings,
        handleSavePasskeySettings,
        handleTestSmtpSettings,
        verificationForm,
        onAuthenticatorSaved,
      }),
    [
      canManageSettings,
      closeConfigDrawer,
      configDrawerMode,
      handleSaveEmailSettings,
      handleSavePasskeySettings,
      handleSaveSmsSettings,
      handleSaveVerificationSettings,
      handleSaveWechatSettings,
      handleTestSmtpSettings,
      savingEmailSettings,
      savingPasskeySettings,
      savingSmsSettings,
      savingWechatSettings,
      testingSmtpSettings,
      verificationForm,
      verificationLoading,
      onAuthenticatorSaved,
    ],
  );

  const renderConfigDrawerContent = useCallback(
    () =>
      resolveDrawerContentRoute({
        configDrawerMode,
        canManageSettings,
        verificationFormProps,
        smsFormProps,
        smtpFormProps,
        smtpTestFormProps,
        wechatFormProps,
        passkeyFormProps,
        verificationLoading,
        smsSettingsData,
        smtpSettingsData,
        wechatSettingsData,
        handleSmsProviderChange,
      }),
    [
      canManageSettings,
      configDrawerMode,
      handleSmsProviderChange,
      passkeyFormProps,
      smsFormProps,
      smsSettingsData,
      smtpFormProps,
      smtpSettingsData,
      smtpTestFormProps,
      verificationFormProps,
      verificationLoading,
      wechatFormProps,
      wechatSettingsData,
    ],
  );

  return {
    drawerState: {
      configDrawerMode,
      openConfigDrawer,
      closeConfigDrawer,
    },
    saveState: {
      savingSmsSettings,
      savingEmailSettings,
      savingWechatSettings,
      savingPasskeySettings,
      testingSmtpSettings,
      handleSaveVerificationSettings,
      handleSaveEmailSettings,
      handleSaveSmsSettings,
      handleSaveWechatSettings,
      handleSavePasskeySettings,
      handleTestSmtpSettings,
    },
    drawerFooter: {
      resolveDrawerFooterActions,
    },
    drawerContent: {
      renderConfigDrawerContent,
    },
  };
};
