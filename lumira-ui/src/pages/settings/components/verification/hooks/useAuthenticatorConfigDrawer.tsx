import { history, useLocation } from '@umijs/max';
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
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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
  subject: t('ui.settings.verification.useauthenticatorconfig.smtpTestEmail'),
  content: t('ui.settings.verification.useauthenticatorconfig.thisIsAnSmtpTestEmailFromThe'),
  toEmail: '',
};

const SMS_ACCESS_KEY_SECRET_MASK = '********';
const SMTP_PASSWORD_MASK = '********';
const WECHAT_APP_SECRET_MASK = '********';
// Retain the retired host only as a migration sentinel for saved Passkey settings.
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
  { label: t('ui.settings.verification.useauthenticatorconfig.alibabaCloudSms'), value: 'aliyun' },
  { label: t('ui.settings.verification.useauthenticatorconfig.tencentCloudSms'), value: 'tencent' },
  { label: t('ui.settings.verification.useauthenticatorconfig.localMock'), value: 'mock' },
  { label: t('ui.settings.verification.useauthenticatorconfig.customGateway'), value: 'custom' },
];

const SMS_PROVIDER_SCHEMAS: Record<SmsProviderCode, SmsProviderSchema> = {
  aliyun: {
    fields: [
      { name: 'signName', label: t('ui.settings.verification.useauthenticatorconfig.smsSignName'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHongxiangShangdao'), required: true },
      { name: 'templateCode', label: t('ui.settings.verification.useauthenticatorconfig.templateCode'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGSms123456789'), required: true },
      { name: 'accessKeyId', label: 'Access Key ID', placeholder: t('ui.settings.verification.useauthenticatorconfig.smsServiceAccessKeyId'), required: true },
      { name: 'accessKeySecret', label: 'Access Key Secret', placeholder: t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret'), password: true },
      { name: 'endpoint', label: t('ui.settings.verification.useauthenticatorconfig.endpoint'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHttpsDysmsapiAliyuncsCom') },
      { name: 'region', label: t('ui.settings.verification.useauthenticatorconfig.region'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGCnHangzhou') },
    ],
  },
  tencent: {
    fields: [
      { name: 'signName', label: t('ui.settings.verification.useauthenticatorconfig.smsSignName'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHongxiangShangdao'), required: true },
      { name: 'templateCode', label: t('ui.settings.verification.useauthenticatorconfig.templateId'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eG1234567'), required: true },
      { name: 'accessKeyId', label: 'SecretId', placeholder: t('ui.settings.verification.useauthenticatorconfig.tencentCloudSecretid'), required: true },
      { name: 'accessKeySecret', label: 'SecretKey', placeholder: t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret'), password: true, required: true },
      { name: 'endpoint', label: t('ui.settings.verification.useauthenticatorconfig.apiEndpoint'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHttpsSmsTencentcloudapiCom') },
      { name: 'region', label: t('ui.settings.verification.useauthenticatorconfig.region'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGApGuangzhou') },
    ],
  },
  mock: {
    fields: [
      { name: 'signName', label: t('ui.settings.verification.useauthenticatorconfig.mockSignName'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGTestSms') },
      { name: 'templateCode', label: t('ui.settings.verification.useauthenticatorconfig.mockTemplateCode'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGMockSms001') },
    ],
  },
  custom: {
    fields: [
      { name: 'endpoint', label: t('ui.settings.verification.useauthenticatorconfig.gatewayUrl'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHttpsSmsExampleComApi'), required: true },
      { name: 'accessKeyId', label: t('ui.settings.verification.useauthenticatorconfig.gatewayAccount'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGGatewayUser'), required: true },
      { name: 'accessKeySecret', label: t('ui.settings.verification.useauthenticatorconfig.gatewaySecret'), placeholder: t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret'), password: true, required: true },
      { name: 'signName', label: t('ui.settings.verification.useauthenticatorconfig.signName'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGHongxiangShangdao'), required: true },
      { name: 'templateCode', label: t('ui.settings.verification.useauthenticatorconfig.templateCode'), placeholder: t('ui.settings.verification.useauthenticatorconfig.eGSms123456789'), required: true },
      { name: 'region', label: t('ui.settings.verification.useauthenticatorconfig.region'), placeholder: t('ui.settings.verification.useauthenticatorconfig.fillInAsRequiredByTheGateway') },
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Typography.Paragraph style={{ marginBottom: 0 }}>
        {t('ui.settings.verification.useauthenticatorconfig.passwordComplexityVerificationCodesAndLoginDefenseThresholds')}
      </Typography.Paragraph>
      <Button type="primary" onClick={() => history.push('/settings/security')}>
        {t('ui.settings.verification.useauthenticatorconfig.goToSecuritySettings')}
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...verificationFormProps}>
        <Form.Item
          name="enabled"
          label={t('ui.settings.verification.useauthenticatorconfig.enable2fa')}
          valuePropName="checked"
          extra={t('ui.settings.verification.useauthenticatorconfig.whenDisabledHighRiskOperationConfirmationsWillNo')}
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...smsFormProps}>
        <Form.Item
          name="provider"
          label={t('ui.settings.verification.useauthenticatorconfig.provider')}
          rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseSelectAnSmsProvider') }]}
        >
          <Select
            disabled={!canManageSettings}
            options={SMS_PROVIDER_OPTIONS}
            placeholder={t('ui.settings.verification.useauthenticatorconfig.pleaseSelectAnSmsProvider')}
            onChange={handleSmsProviderChange}
          />
        </Form.Item>
        {providerSchema.fields.map((field) => (
          <Form.Item
            key={String(field.name)}
            name={field.name}
            label={field.label}
            rules={field.required ? [{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnter').replace('{label}', field.label) }] : undefined}
            extra={
              field.password && field.name === 'accessKeySecret'
                ? smsSettingsData?.accessKeySecretConfigured
                  ? t('ui.settings.verification.useauthenticatorconfig.theCurrentSecretIsMaskedLeaveBlankTo')
                  : t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret')
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Card title={t('ui.settings.verification.useauthenticatorconfig.emailAndSmtp')} loading={verificationLoading}>
        <div style={{ opacity: 1, transition: 'opacity 0.2s ease' }}>
      <Form {...smtpFormProps}>
            <Typography.Title level={5} style={{ marginTop: 0 }}>
              {t('ui.settings.verification.useauthenticatorconfig.smtpSettings')}
            </Typography.Title>
            <Form.Item name="host" label={t('ui.settings.verification.useauthenticatorconfig.smtpHost')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheSmtpHost') }]}>
              <Input disabled={!canManageSettings} placeholder="smtp.example.com" />
            </Form.Item>
            <Form.Item name="port" label={t('ui.settings.verification.useauthenticatorconfig.smtpPort')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheSmtpPort') }]}>
              <InputNumber disabled={!canManageSettings} style={{ width: '100%' }} min={1} max={65535} />
            </Form.Item>
            <Form.Item name="username" label={t('ui.settings.verification.useauthenticatorconfig.smtpUsername')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheSmtpUsername') }]}>
              <Input disabled={!canManageSettings} placeholder="username@example.com" />
            </Form.Item>
            <Form.Item
              name="password"
              label={t('ui.settings.verification.useauthenticatorconfig.smtpPassword')}
              extra={smtpSettingsData?.passwordConfigured ? t('ui.settings.verification.useauthenticatorconfig.theCurrentPasswordIsMaskedLeaveBlankTo') : t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingPassword')}
            >
              <Input.Password disabled={!canManageSettings} placeholder={t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingPassword.64d7267a')} />
            </Form.Item>
            <Form.Item name="from" label={t('ui.settings.verification.useauthenticatorconfig.fromAddress')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheFromAddress') }]}>
              <Input disabled={!canManageSettings} placeholder="noreply@example.com" />
            </Form.Item>
            <Form.Item name="authEnabled" label={t('ui.settings.verification.useauthenticatorconfig.enableAuthentication')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
            <Form.Item name="startTlsEnabled" label={t('ui.settings.verification.useauthenticatorconfig.enableStarttls')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
            <Form.Item name="sslEnabled" label={t('ui.settings.verification.useauthenticatorconfig.enableSsl')} valuePropName="checked">
              <Switch disabled={!canManageSettings} />
            </Form.Item>
          </Form>
        </div>
      </Card>
      <Card title={t('ui.settings.verification.useauthenticatorconfig.smtpTestSend')} loading={verificationLoading}>
        <Form {...smtpTestFormProps}>
          <Form.Item
            name="toEmail"
            label={t('ui.settings.verification.useauthenticatorconfig.recipientEmail')}
            rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheRecipientEmail') }, { type: 'email', message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterAValidEmailAddress') }]}
          >
            <Input disabled={!canManageSettings} placeholder="recipient@example.com" />
          </Form.Item>
          <Form.Item name="subject" label={t('ui.settings.verification.useauthenticatorconfig.emailSubject')}>
            <Input disabled={!canManageSettings} />
          </Form.Item>
          <Form.Item name="content" label={t('ui.settings.verification.useauthenticatorconfig.emailContent')}>
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...wechatFormProps}>
        <Form.Item name="enabled" label={t('ui.settings.verification.useauthenticatorconfig.enableWechatLogin')} valuePropName="checked">
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item
          name="appId"
          label={t('ui.settings.verification.useauthenticatorconfig.appid')}
          rules={wechatEnabled ? [{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheAppid') }] : undefined}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder={t('ui.settings.verification.useauthenticatorconfig.wechatOpenPlatformWebsiteApplicationAppid')} />
        </Form.Item>
        <Form.Item
          name="appSecret"
          label={t('ui.settings.verification.useauthenticatorconfig.appsecret')}
          extra={wechatSettingsData?.appSecretConfigured ? t('ui.settings.verification.useauthenticatorconfig.theCurrentSecretIsMaskedLeaveBlankTo') : t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret')}
        >
          <Input.Password disabled={!canManageSettings || !wechatEnabled} placeholder={t('ui.settings.verification.useauthenticatorconfig.leaveBlankToKeepTheExistingSecret')} />
        </Form.Item>
        <Form.Item
          name="redirectUri"
          label={t('ui.settings.verification.useauthenticatorconfig.callbackUrl')}
          rules={wechatEnabled ? [{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheCallbackUrl') }] : undefined}
        >
          <Input disabled={!canManageSettings || !wechatEnabled} placeholder={t('ui.settings.verification.useauthenticatorconfig.httpsYourDomainComApiV1AuthWechat')} />
        </Form.Item>
        <Form.Item
          name="stateExpireMinutes"
          label={t('ui.settings.verification.useauthenticatorconfig.stateTtl')}
          rules={wechatEnabled ? [{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheStateTtl') }] : undefined}
        >
          <InputNumber disabled={!canManageSettings || !wechatEnabled} style={{ width: '100%' }} min={1} max={60} addonAfter={t('ui.settings.verification.useauthenticatorconfig.min')} />
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
    <Space orientation="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...passkeyFormProps}>
        <Form.Item
          name="passwordlessEnabled"
          label={t('ui.settings.verification.useauthenticatorconfig.allowSignInWithoutAnAccount')}
          valuePropName="checked"
          extra={t('ui.settings.verification.useauthenticatorconfig.whenEnabledTheLoginPageCanDirectlyOpen')}
        >
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item name="selfBindingEnabled" label={t('ui.settings.verification.useauthenticatorconfig.allowSelfServiceBinding')} valuePropName="checked">
          <Switch disabled={!canManageSettings} />
        </Form.Item>
        <Form.Item name="rpId" label={t('ui.settings.verification.useauthenticatorconfig.rpId')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheRpId') }]}>
          <Input disabled={!canManageSettings} placeholder={currentRpId || t('ui.settings.verification.useauthenticatorconfig.currentWebsiteHost')} />
        </Form.Item>
        <Form.Item name="rpName" label={t('ui.settings.verification.useauthenticatorconfig.rpName')} rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheRpName') }]}>
          <Input disabled={!canManageSettings} placeholder={t('ui.settings.verification.useauthenticatorconfig.saasAdminSystem')} />
        </Form.Item>
        <Form.Item
          name="allowedOriginsText"
          label={t('ui.settings.verification.useauthenticatorconfig.allowedOrigins')}
          rules={[{ required: true, message: t('ui.settings.verification.useauthenticatorconfig.pleaseEnterTheAllowedOrigins') }]}
          extra={t('ui.settings.verification.useauthenticatorconfig.oneCurrentSiteOriginPerLineUseHttps')}
        >
          <Input.TextArea disabled={!canManageSettings} rows={4} placeholder={currentOrigin || t('ui.settings.verification.useauthenticatorconfig.currentWebsiteOrigin')} />
        </Form.Item>
        <Form.Item name="challengeTtlSeconds" label={t('ui.settings.verification.useauthenticatorconfig.challengeTtl')}>
          <InputNumber disabled={!canManageSettings} style={{ width: '100%' }} min={30} max={600} addonAfter={t('ui.settings.verification.useauthenticatorconfig.s')} />
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
  label: t('ui.settings.verification.useauthenticatorconfig.cancel'),
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
        label: t('ui.settings.verification.useauthenticatorconfig.saveSettings'),
        onSave: params.handleSaveSmsSettings,
      });
    case 'email':
      return [
        {
          key: 'test',
          label: t('ui.settings.verification.useauthenticatorconfig.sendTestEmail'),
          loading: params.testingSmtpSettings,
          disabled: !params.canManageSettings,
          onClick: () => void params.handleTestSmtpSettings(),
        },
        cancelAction(params.closeConfigDrawer),
        {
          key: 'save',
          label: t('ui.settings.verification.useauthenticatorconfig.saveSettings.63fceb75'),
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
        label: t('ui.settings.verification.useauthenticatorconfig.saveSettings'),
        onSave: params.handleSaveWechatSettings,
      });
    case 'passkey':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.savingPasskeySettings,
        label: t('ui.settings.verification.useauthenticatorconfig.saveSettings'),
        onSave: params.handleSavePasskeySettings,
      });
    case 'totp':
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.verificationLoading,
        label: t('ui.settings.verification.useauthenticatorconfig.save2faSettings'),
        onSave: () => params.handleSaveVerificationSettings({ closeDrawer: true }),
      });
    case 'basic':
    default:
      return buildSaveModeActions({
        canManageSettings: params.canManageSettings,
        closeConfigDrawer: params.closeConfigDrawer,
        loading: params.verificationLoading,
        label: t('ui.settings.verification.useauthenticatorconfig.saveSettings.63fceb75'),
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
    () => buildPasskeyDefaults(t('ui.settings.verification.useauthenticatorconfig.adminSystem', { websiteName: brandingSettings.websiteName })),
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
