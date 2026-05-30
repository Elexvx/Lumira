import { Button, Card, Form, Input, InputNumber, Select, Space, Switch, Typography, theme } from 'antd';
import type { FormProps } from 'antd';
import {
  SMS_PROVIDER_OPTIONS,
  SMS_PROVIDER_SCHEMAS,
  SMS_ACCESS_KEY_SECRET_MASK,
  SMTP_PASSWORD_MASK,
  WECHAT_APP_SECRET_MASK,
} from './config';
import type { SmsProviderCode } from './config';

interface SmsTabProps {
  smsFormProps: FormProps;
  canManageSettings: boolean;
  smsConfigEnabled: boolean;
  handleSmsProviderChange: (value: SmsProviderCode) => void;
  smsAccessKeySecretConfigured: boolean;
  providerDrafts: any;
  provider: SmsProviderCode;
}

export const SmsConfigTab = ({
  smsFormProps,
  canManageSettings,
  smsConfigEnabled,
  handleSmsProviderChange,
  smsAccessKeySecretConfigured,
  providerDrafts,
  provider
}: SmsTabProps) => {
  const providerSchema = SMS_PROVIDER_SCHEMAS[provider] ?? SMS_PROVIDER_SCHEMAS.aliyun;
  return (
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
};

interface EmailTabProps {
  smtpFormProps: FormProps;
  smtpTestFormProps: FormProps;
  canManageSettings: boolean;
  emailConfigEnabled: boolean;
  smtpSettingsQuery: any;
  verificationSettingsQuery: any;
  smtpPasswordConfigured: boolean;
  testingSmtpSettings: boolean;
  handleTestSmtp: () => void;
}

export const EmailConfigTab = ({
  smtpFormProps,
  smtpTestFormProps,
  canManageSettings,
  emailConfigEnabled,
  smtpSettingsQuery,
  verificationSettingsQuery,
  smtpPasswordConfigured,
  testingSmtpSettings,
  handleTestSmtp
}: EmailTabProps) => {
  return (
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
};

interface WechatTabProps {
  wechatFormProps: FormProps;
  canManageSettings: boolean;
  wechatAppSecretConfigured: boolean;
}

export const WechatConfigTab = ({
  wechatFormProps,
  canManageSettings,
  wechatAppSecretConfigured
}: WechatTabProps) => {
  return (
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
};

interface PasskeyTabProps {
  passkeyFormProps: FormProps;
  canManageSettings: boolean;
  passkeyConfigEnabled: boolean;
}

export const PasskeyConfigTab = ({
  passkeyFormProps,
  canManageSettings,
  passkeyConfigEnabled
}: PasskeyTabProps) => {
  const { token } = theme.useToken();
  return (
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
};
