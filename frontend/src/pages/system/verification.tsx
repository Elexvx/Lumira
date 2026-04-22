import { PageContainer } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Button, Card, Form, Input, Modal, Select, Space, Switch, Tabs, message } from 'antd';
import { useEffect, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { BindSecondFactorModal } from '@/pages/profile/center/components/BindSecondFactorModal';
import { BoundProviderCard } from '@/pages/profile/center/components/BoundProviderCard';
import { secondFactorService } from '@/services/secondFactor';
import { systemService } from '@/services/system';
import type { SecondFactorChallenge, SecondFactorProviderStatus, SmsVerificationSettings, VerificationSettings } from '@/types/api';

const TAB_ORDER = ['totp', 'sms'] as const;

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
      { name: 'signName', label: '模拟签名', placeholder: '例如：测试短信', required: false },
      { name: 'templateCode', label: '模拟模板编码', placeholder: '例如：MOCK_SMS_001', required: false },
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

const SystemVerificationPage = () => {
  const actionPermission = useActionPermission();
  const canViewVerification = actionPermission.can('system:verification:view') || actionPermission.can('system:verification:manage');
  const canManageVerification = actionPermission.can('system:verification:manage');

  const verificationSettingsQuery = useRequest(
    async () =>
      ({ data: await systemService.verificationSettings({ autoRedirectOnUnauthorized: false }) }) as {
        data: VerificationSettings;
      },
    {
      ready: canViewVerification,
    },
  );
  const smsSettingsQuery = useRequest(
    async () =>
      ({ data: await systemService.smsVerificationSettings({ autoRedirectOnUnauthorized: false }) }) as {
        data: SmsVerificationSettings;
      },
    {
      ready: canViewVerification,
    },
  );
  const providersQuery = useRequest(
    async () =>
      ({ data: await secondFactorService.providers({ autoRedirectOnUnauthorized: false }) }) as {
        data: SecondFactorProviderStatus[];
      },
    {
      ready: canViewVerification,
    },
  );

  const [verificationForm] = Form.useForm<VerificationSettings>();
  const [smsSettingsForm] = Form.useForm<SmsVerificationSettings>();
  const currentProvider = Form.useWatch('provider', smsSettingsForm);
  const smsEnabled = Form.useWatch('enabled', smsSettingsForm) ?? false;
  const [providerDrafts, setProviderDrafts] = useState<Partial<Record<SmsProviderCode, SmsVerificationSettings>>>({});
  const [verificationSaving, setVerificationSaving] = useState(false);
  const [savingSmsSettings, setSavingSmsSettings] = useState(false);
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();
  const smsFormProps = useStandardFormProps({
    form: smsSettingsForm,
    initialValues: {
      enabled: false,
      provider: 'aliyun',
    },
  });

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
    setProviderDrafts((drafts) => ({
      ...drafts,
      [providerCode]: smsSettingsQuery.data,
    }));
    smsSettingsForm.setFieldsValue({
      ...smsSettingsQuery.data,
      accessKeySecret: '',
    });
  }, [smsSettingsForm, smsSettingsQuery.data]);

  const handleSaveVerificationSettings = async () => {
    if (!canManageVerification) {
      return;
    }
    setVerificationSaving(true);
    try {
      const values = await verificationForm.validateFields();
      const result = await systemService.updateVerificationSettings(values, { autoRedirectOnUnauthorized: false });
      verificationForm.setFieldsValue(result);
      message.success('2FA 设置已保存');
    } finally {
      setVerificationSaving(false);
    }
  };

  const handleSaveSmsSettings = async () => {
    setSavingSmsSettings(true);
    try {
      const values = await smsSettingsForm.validateFields();
      const providerCode = normalizeProviderCode(values.provider);
      setProviderDrafts((drafts) => ({
        ...drafts,
        [providerCode]: values,
      }));
      const result = await systemService.updateSmsVerificationSettings(values, { autoRedirectOnUnauthorized: false });
      message.success(result.configured ? '短信验证码服务配置已保存' : '短信验证码服务配置已保存，当前仍未完全启用');
      await smsSettingsQuery.refresh();
    } finally {
      setSavingSmsSettings(false);
    }
  };

  const resetBindState = () => {
    setBindingProvider(null);
    setBindingChallenge(null);
    setBindingLoading(false);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
  };

  const closeBindModal = () => {
    if (bindingSubmitting) {
      return;
    }
    setBindModalOpen(false);
    window.setTimeout(() => {
      resetBindState();
    }, 0);
  };

  const openBindModal = async (provider: SecondFactorProviderStatus) => {
    setBindingProvider(provider);
    setBindingChallenge(null);
    setBindingLoading(true);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    setBindModalOpen(true);
    try {
      const challenge = await secondFactorService.bind(provider.factorCode, {
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      setBindingChallenge(challenge);
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : '获取绑定信息失败，请稍后重试',
      });
    } finally {
      setBindingLoading(false);
    }
  };

  const retryBindChallenge = async () => {
    if (!bindingProvider) {
      return;
    }
    setBindingChallenge(null);
    await openBindModal(bindingProvider);
  };

  const handleUnbind = (provider: SecondFactorProviderStatus) => {
    Modal.confirm({
      title: `解绑 ${provider.factorName || provider.factorCode}`,
      content: '解绑后该验证方式将立即失效，确认继续吗？',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await secondFactorService.unbind(provider.factorCode, { autoRedirectOnUnauthorized: false });
        message.success('已解绑');
        await providersQuery.refresh();
      },
    });
  };

  const handleVerifyBind = async (values: { verificationCode?: string }) => {
    if (!bindingProvider || !bindingChallenge) {
      setBindingAlert({
        type: 'warning',
        message: '绑定信息已失效，请重新发起绑定。',
      });
      return false;
    }
    if (!values.verificationCode) {
      setBindingAlert({
        type: 'warning',
        message: '请输入验证码。',
      });
      return false;
    }

    setBindingSubmitting(true);
    setBindingAlert(undefined);
    try {
      const result = await secondFactorService.verify(
        bindingProvider.factorCode,
        {
          factorCode: bindingProvider.factorCode,
          challengeId: bindingChallenge.challengeId,
          verificationCode: values.verificationCode,
        },
        {
          autoRedirectOnUnauthorized: false,
          silent: true,
        },
      );

      if (!result.verified) {
        setBindingAlert({
          type: 'warning',
          message: result.message || '验证码校验失败，请重试。',
        });
        return false;
      }

      message.success('绑定已完成');
      setBindingCompleted(true);
      await providersQuery.refresh();
      return true;
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : '绑定失败，请稍后重试',
      });
      return false;
    } finally {
      setBindingSubmitting(false);
    }
  };

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

  const renderTotpTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form form={verificationForm} layout="vertical" initialValues={{ enabled: true }}>
        <Form.Item name="enabled" label="启用 2FA" valuePropName="checked">
          <Switch disabled={!canManageVerification} checkedChildren="开启" unCheckedChildren="关闭" />
        </Form.Item>
      </Form>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="primary" loading={verificationSaving} disabled={!canManageVerification} onClick={() => void handleSaveVerificationSettings()}>
          保存设置
        </Button>
      </div>
    </Space>
  );

  const renderSmsTab = () => {
    const activeProvider = normalizeProviderCode(currentProvider);
    const providerSchema = SMS_PROVIDER_SCHEMAS[activeProvider];
    const smsFieldsDisabled = !canManageVerification || !smsEnabled;

    return (
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Form {...smsFormProps}>
          <Form.Item name="enabled" label="启用短信验证码" valuePropName="checked">
            <Switch disabled={!canManageVerification} checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>
          <Form.Item
            name="provider"
            label="服务商"
            rules={smsEnabled ? [{ required: true, message: '请选择短信服务商' }] : undefined}
          >
            <Select
              disabled={smsFieldsDisabled}
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
            >
              {field.password ? (
                <Input.Password disabled={smsFieldsDisabled} placeholder={field.placeholder} />
              ) : (
                <Input disabled={smsFieldsDisabled} placeholder={field.placeholder} />
              )}
            </Form.Item>
          ))}
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" loading={savingSmsSettings} disabled={smsFieldsDisabled} onClick={() => void handleSaveSmsSettings()}>
              保存配置
            </Button>
          </div>
        </Form>
      </Space>
    );
  };

  return (
    <PageContainer className="saas-management-page" title="验证管理">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card loading={verificationSettingsQuery.loading || smsSettingsQuery.loading}>
          <Tabs
            defaultActiveKey="totp"
            items={TAB_ORDER.map((factorCode) => ({
              key: factorCode,
              label: factorCode === 'totp' ? '2FA' : '短信验证码',
              children: factorCode === 'totp' ? renderTotpTab() : renderSmsTab(),
            }))}
          />
        </Card>
        <BoundProviderCard
          title="可绑定的验证方式"
          canManageSecondFactor={canManageVerification}
          loading={providersQuery.loading}
          providers={providersQuery.data || []}
          bindingLoading={bindingLoading}
          bindingSubmitting={bindingSubmitting}
          emailBindingSubmitting={false}
          onBind={(provider) => void openBindModal(provider)}
          onUnbind={handleUnbind}
        />
      </Space>
      <BindSecondFactorModal
        open={bindModalOpen}
        bindingProvider={bindingProvider}
        bindingChallenge={bindingChallenge}
        bindingCompleted={bindingCompleted}
        bindingIsSms={false}
        bindingLoading={bindingLoading}
        bindingSubmitting={bindingSubmitting}
        bindingAlert={bindingAlert}
        singleColumnDescriptionsProps={{ column: 1 }}
        onCancel={closeBindModal}
        onRetry={() => void retryBindChallenge()}
        onFinish={closeBindModal}
        onVerify={handleVerifyBind}
      />
    </PageContainer>
  );
};

export default SystemVerificationPage;
